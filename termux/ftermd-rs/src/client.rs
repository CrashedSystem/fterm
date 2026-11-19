//! One connection from the app: framing in, frames out, and the backpressure that keeps a
//! stalled app from taking every other tab with it.
//!
//! In `termux/ftermd` a client's output lived in an unbounded `bytearray` that only shrank when
//! the socket accepted bytes. An app that went to the background while `cat` was printing grew
//! that buffer until the daemon was killed by the OOM killer - and the daemon owns every tab's
//! shell. Here each client has a byte budget. A client that falls behind it loses *its own*
//! connection; the session, the shell and the ring stay alive, and the reconnect replays what
//! it missed.

use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};
use std::sync::Arc;
use std::time::Duration;

use tokio::io::{AsyncReadExt, AsyncWrite, AsyncWriteExt};
use tokio::net::TcpStream;
use tokio::sync::{mpsc, Notify};

use crate::daemon::Daemon;
use crate::log_line;
use crate::protocol::{self, FrameReader};

/// How much unsent output one client may have queued before it is treated as gone.
pub const OUTBUF_CAP: usize = 4 * 1024 * 1024;

/// Channel depth. The byte budget above is the real limit; this only has to be deep enough that
/// the budget, not the depth, is what a slow client hits first.
const CHANNEL: usize = 512;

pub enum ClientMsg {
    Bytes(Vec<u8>),
}

#[derive(Clone)]
pub struct ClientHandle {
    id: u64,
    tx: mpsc::Sender<ClientMsg>,
    queued: Arc<AtomicUsize>,
    shutdown: Arc<Notify>,
    closed: Arc<AtomicBool>,
}

impl ClientHandle {
    pub fn id(&self) -> u64 {
        self.id
    }

    pub fn is_closed(&self) -> bool {
        self.closed.load(Ordering::Relaxed)
    }

    /// Queue one frame. `false` means the client is gone or was just dropped for falling behind.
    pub fn send(&self, frame: Vec<u8>) -> bool {
        if self.is_closed() {
            return false;
        }
        let n = frame.len();
        let prev = self.queued.fetch_add(n, Ordering::Relaxed);
        if prev + n > OUTBUF_CAP {
            self.queued.fetch_sub(n, Ordering::Relaxed);
            log_line(&format!(
                "client {} is {n} bytes behind (over {OUTBUF_CAP}); dropping it, the sessions stay",
                self.id
            ));
            self.request_close();
            return false;
        }
        match self.tx.try_send(ClientMsg::Bytes(frame)) {
            Ok(()) => true,
            Err(_) => {
                // Closed, or the depth was reached before the byte budget: either way this
                // client is not reading, and it is the one that goes.
                self.queued.fetch_sub(n, Ordering::Relaxed);
                self.request_close();
                false
            }
        }
    }

    pub fn send_frame(&self, typ: u8, payload: &[u8]) -> bool {
        self.send(protocol::frame(typ, payload))
    }

    /// Ask the connection task to finish: it drains what is already queued, then closes.
    pub fn request_close(&self) {
        self.closed.store(true, Ordering::SeqCst);
        self.shutdown.notify_one();
    }
}

/// Serve one connection until it ends, on its own terms or the client's.
pub async fn serve(sock: TcpStream, daemon: Arc<Daemon>) {
    let id = daemon.next_client_id();
    daemon.client_joined();
    let (tx, rx) = mpsc::channel::<ClientMsg>(CHANNEL);
    let handle = ClientHandle {
        id,
        tx,
        queued: Arc::new(AtomicUsize::new(0)),
        shutdown: Arc::new(Notify::new()),
        closed: Arc::new(AtomicBool::new(false)),
    };
    let (mut rd, wr) = sock.into_split();
    let writer = tokio::spawn(writer_loop(wr, rx, handle.queued.clone(), handle.shutdown.clone()));

    let mut reader = FrameReader::new();
    let mut authed = false;
    let mut buf = vec![0u8; 65536];
    'conn: loop {
        let n = match rd.read(&mut buf).await {
            Ok(0) => break,
            Ok(n) => n,
            Err(_) => break,
        };
        reader.push(&buf[..n]);
        loop {
            match reader.next_frame() {
                Ok(Some((typ, payload))) => {
                    if !daemon
                        .handle(&handle, &mut authed, typ, &payload)
                        .await
                    {
                        break 'conn;
                    }
                }
                Ok(None) => break,
                Err(e) => {
                    let msg = match e {
                        protocol::FrameError::TooLarge(len) => format!(
                            "frame too large: {len} ({} bytes buffered)",
                            reader.buffered()
                        ),
                    };
                    log_line(&format!("client {id}: {msg}"));
                    handle.send_frame(protocol::E_BAD_FRAME, &error_payload(protocol::E_BAD_FRAME, &msg));
                    break 'conn;
                }
            }
        }
    }

    // Leave every session this connection was attached to. The sessions themselves are not
    // touched: that is the whole point of the daemon owning the PTY.
    for h in daemon.snapshot() {
        let _ = tokio::time::timeout(
            Duration::from_millis(200),
            h.tx.send(crate::pty::SessionCmd::Detach(id)),
        )
        .await;
    }
    handle.request_close();
    let _ = tokio::time::timeout(Duration::from_secs(2), writer).await;
    daemon.client_left();
    log_line(&format!("client {id} dropped"));
}

pub fn error_payload(code: u8, msg: &str) -> Vec<u8> {
    // S_ERROR is <u8 code><u16 len><utf-8 message>, exactly as the first (Python) daemon sent it.
    let b = msg.as_bytes();
    let mut out = Vec::with_capacity(3 + b.len());
    out.push(code);
    out.extend_from_slice(&(b.len() as u16).to_le_bytes());
    out.extend_from_slice(b);
    out
}

async fn writer_loop<W: AsyncWrite + Unpin>(
    mut w: W,
    mut rx: mpsc::Receiver<ClientMsg>,
    queued: Arc<AtomicUsize>,
    shutdown: Arc<Notify>,
) {
    loop {
        tokio::select! {
            biased;
            _ = shutdown.notified() => break,
            msg = rx.recv() => match msg {
                Some(ClientMsg::Bytes(b)) => {
                    let n = b.len();
                    if w.write_all(&b).await.is_err() {
                        queued.fetch_sub(n, Ordering::Relaxed);
                        break;
                    }
                    queued.fetch_sub(n, Ordering::Relaxed);
                }
                None => break,
            },
        }
    }
    // An error frame sent just before closing has to reach the client: drain what is queued
    // rather than dropping the socket with the explanation still inside the process.
    while let Ok(ClientMsg::Bytes(b)) = rx.try_recv() {
        if w.write_all(&b).await.is_err() {
            break;
        }
        queued.fetch_sub(b.len(), Ordering::Relaxed);
    }
    let _ = w.flush().await;
}

#[cfg(test)]
mod tests {
    use super::*;

    fn handle() -> (ClientHandle, mpsc::Receiver<ClientMsg>) {
        let (tx, rx) = mpsc::channel::<ClientMsg>(CHANNEL);
        (
            ClientHandle {
                id: 1,
                tx,
                queued: Arc::new(AtomicUsize::new(0)),
                shutdown: Arc::new(Notify::new()),
                closed: Arc::new(AtomicBool::new(false)),
            },
            rx,
        )
    }

    #[test]
    fn a_client_that_keeps_up_is_never_dropped() {
        let (h, mut rx) = handle();
        for _ in 0..100 {
            assert!(h.send(vec![0u8; 1024]));
            // Stand in for the writer draining the queue.
            if let Ok(Some(ClientMsg::Bytes(_))) = rx.try_recv().map(Some) {
                h.queued.fetch_sub(1024, Ordering::Relaxed);
            }
        }
        assert!(!h.is_closed());
    }

    #[test]
    fn a_client_that_stops_reading_is_dropped_and_nothing_else_is() {
        let (h, _rx) = handle();
        let chunk = vec![0u8; 64 * 1024];
        let mut accepted = 0;
        // Nothing drains this queue: this is an app that went to the background.
        while h.send(chunk.clone()) {
            accepted += 1;
            if accepted > 1000 {
                break;
            }
        }
        assert!(h.is_closed(), "the slow client must be dropped");
        assert!(
            accepted * chunk.len() < OUTBUF_CAP + 2 * chunk.len(),
            "queued {accepted} chunks, which is past the cap"
        );
        // A second client of the same daemon is untouched by the first one's death.
        let (other, _rx2) = handle();
        assert!(other.send(vec![1, 2, 3]));
        assert!(!other.is_closed());
    }

    #[test]
    fn the_error_payload_is_code_then_length_then_text() {
        let p = error_payload(protocol::E_BAD_TOKEN, "bad token");
        assert_eq!(p[0], protocol::E_BAD_TOKEN);
        assert_eq!(&p[1..3], &9u16.to_le_bytes());
        assert_eq!(&p[3..], b"bad token");
    }
}
