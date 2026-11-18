//! The daemon: the listener, the session registry, and the protocol handlers.
//!
//! Everything a client sends ends up in one of two places - a session actor, or a reply on the
//! client's own queue. Nothing the daemon does while serving a client holds the runtime for
//! longer than a lock: the PTY reads and writes live in their threads, and the two genuinely
//! slow operations (scanning font directories, downloading one) are moved off the workers.

use std::collections::HashMap;
use std::path::PathBuf;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex};
use std::time::Duration;

use tokio::net::TcpListener;
use tokio::signal::unix::{signal, SignalKind};

use crate::client::{self, ClientHandle};
use crate::env;
use crate::fonts;
use crate::instance::{self, InstanceLock};
use crate::log_line;
use crate::protocol::*;
use crate::pty::{self, SessionCmd, SessionHandle, SessionSpec};

pub struct Daemon {
    pub token: String,
    pub rundir: PathBuf,
    pub port: u16,
    pub shell: String,
    pub base_env: Vec<(String, String)>,
    pub history_bytes: usize,
    sessions: Arc<Mutex<HashMap<u16, SessionHandle>>>,
    next_client: AtomicU64,
    clients: AtomicU64,
}

impl Daemon {
    pub fn new() -> Daemon {
        let rundir = env::rundir();
        let _ = env::ensure_rundir(&rundir);
        let port = std::env::var("FTERM_PORT")
            .ok()
            .and_then(|p| p.parse::<u16>().ok())
            .unwrap_or(8791);
        let history_bytes = std::env::var("FTERM_HISTORY_BYTES")
            .ok()
            .and_then(|v| v.parse::<usize>().ok())
            .unwrap_or(262_144);
        let token = env::read_token(&rundir, true)
            .ok()
            .flatten()
            .unwrap_or_default();
        let mut base_env: Vec<(String, String)> = env::shell_env(&rundir).into_iter().collect();
        base_env.sort();
        Daemon {
            token,
            rundir,
            port,
            shell: env::default_shell(),
            base_env,
            history_bytes,
            sessions: Arc::new(Mutex::new(HashMap::new())),
            next_client: AtomicU64::new(1),
            clients: AtomicU64::new(0),
        }
    }

    pub fn next_client_id(&self) -> u64 {
        self.next_client.fetch_add(1, Ordering::Relaxed)
    }

    pub fn client_joined(&self) {
        self.clients.fetch_add(1, Ordering::Relaxed);
    }

    pub fn client_left(&self) {
        self.clients.fetch_sub(1, Ordering::Relaxed);
    }

    pub fn session(&self, sid: u16) -> Option<SessionHandle> {
        self.sessions.lock().ok().and_then(|m| m.get(&sid).cloned())
    }

    pub fn snapshot(&self) -> Vec<SessionHandle> {
        self.sessions
            .lock()
            .map(|m| m.values().cloned().collect())
            .unwrap_or_default()
    }

    pub fn live_ids(&self) -> Vec<u16> {
        self.sessions
            .lock()
            .map(|m| {
                let mut ids: Vec<u16> = m.keys().copied().collect();
                ids.sort();
                ids
            })
            .unwrap_or_default()
    }

    pub fn live_count(&self) -> usize {
        self.sessions.lock().map(|m| m.len()).unwrap_or(0)
    }

    /// OPEN a session: attach to it if it is already there, spawn it if not.
    fn open_session(
        &self,
        sid: u16,
        cols: u16,
        rows: u16,
        shell_req: &str,
        cwd_req: &str,
    ) -> Result<SessionHandle, String> {
        let shell = env::resolve_shell(shell_req).unwrap_or_else(|| self.shell.clone());
        let spec = SessionSpec {
            sid,
            cols,
            rows,
            shell,
            cwd: env::resolve_cwd(cwd_req),
            env: self.base_env.clone(),
            history_bytes: self.history_bytes,
        };
        let spawned = pty::start(spec, self.sessions.clone())
            .map_err(|e| format!("spawn failed: {e}"))?;
        // Two clients can race to open the same new tab. Whoever loses the insert hands its own
        // shell back rather than leaving an orphan the registry does not know about.
        let mut map = self.sessions.lock().map_err(|_| "session table poisoned".to_string())?;
        if let Some(existing) = map.get(&sid) {
            let existing = existing.clone();
            drop(map);
            let _ = spawned.tx.try_send(SessionCmd::Close);
            return Ok(existing);
        }
        map.insert(sid, spawned.clone());
        Ok(spawned)
    }

    /// One client frame. `false` means: send what is queued and close this connection.
    pub async fn handle(
        &self,
        cl: &ClientHandle,
        authed: &mut bool,
        typ: u8,
        p: &[u8],
    ) -> bool {
        if typ == C_HELLO {
            if p.is_empty() {
                cl.send_frame(S_ERROR, &client::error_payload(E_BAD_FRAME, "short hello"));
                return false;
            }
            let proto = p[0];
            let tokl = get_u16(p, 1).unwrap_or(0) as usize;
            let tok = if p.len() >= 3 + tokl {
                String::from_utf8_lossy(&p[3..3 + tokl]).into_owned()
            } else {
                String::new()
            };
            if proto != PROTO_VERSION {
                let msg = format!("protocol {proto} != {PROTO_VERSION}");
                cl.send_frame(S_ERROR, &client::error_payload(E_BAD_PROTO, &msg));
                return false;
            }
            if tok != self.token {
                cl.send_frame(S_ERROR, &client::error_payload(E_BAD_TOKEN, "bad token"));
                return false;
            }
            *authed = true;
            log_line(&format!("hello ok (client {})", cl.id()));
            cl.send_frame(S_HELLO_OK, &[PROTO_VERSION]);
            return true;
        }

        if !*authed {
            cl.send_frame(S_ERROR, &client::error_payload(E_BAD_TOKEN, "hello first"));
            return false;
        }

        match typ {
            C_PING => {
                cl.send_frame(S_PONG, p);
            }
            C_FONT_LIST => {
                let fonts = tokio::task::spawn_blocking(fonts::font_scan)
                    .await
                    .unwrap_or_default();
                let mut out = Vec::new();
                out.extend_from_slice(&u16b(fonts.len() as u16));
                for (path, size) in fonts {
                    let name = path
                        .file_name()
                        .map(|n| n.to_string_lossy().into_owned())
                        .unwrap_or_default();
                    let p = path.to_string_lossy().into_owned();
                    out.extend_from_slice(&u16b(name.len() as u16));
                    out.extend_from_slice(name.as_bytes());
                    out.extend_from_slice(&u32b(size as u32));
                    out.extend_from_slice(&u16b(p.len() as u16));
                    out.extend_from_slice(p.as_bytes());
                }
                cl.send_frame(S_FONT_LIST, &out);
            }
            C_FONT_FETCH => {
                let Some(n) = get_u16(p, 0) else {
                    cl.send_frame(S_ERROR, &client::error_payload(E_BAD_FRAME, "short fetch request"));
                    return true;
                };
                let url = String::from_utf8_lossy(&p[2..(2 + n as usize).min(p.len())]).into_owned();
                let url2 = url.clone();
                let fetched = tokio::task::spawn_blocking(move || fonts::fetch_font(&url2))
                    .await
                    .unwrap_or_else(|e| Err(format!("{e}")));
                match fetched {
                    Ok(dest) => {
                        log_line(&format!("font fetched {}", dest.display()));
                        let pb = dest.to_string_lossy().into_owned();
                        let mut out = Vec::with_capacity(2 + pb.len());
                        out.extend_from_slice(&u16b(pb.len() as u16));
                        out.extend_from_slice(pb.as_bytes());
                        cl.send_frame(S_FONT_FETCHED, &out);
                    }
                    Err(e) => {
                        log_line(&format!("font fetch failed: {url} ({e})"));
                        let msg = e.as_bytes();
                        let mut out = Vec::with_capacity(4 + msg.len());
                        out.extend_from_slice(&u16b(0));
                        out.extend_from_slice(&u16b(msg.len() as u16));
                        out.extend_from_slice(msg);
                        cl.send_frame(S_FONT_FETCHED, &out);
                    }
                }
            }
            C_FONT_GET => {
                let Some(n) = get_u16(p, 0) else {
                    cl.send_frame(S_ERROR, &client::error_payload(E_BAD_FRAME, "short font request"));
                    return true;
                };
                let path = String::from_utf8_lossy(&p[2..(2 + n as usize).min(p.len())]).into_owned();
                let read = tokio::task::spawn_blocking(move || fonts::read_font(&path))
                    .await
                    .unwrap_or_else(|e| Err(format!("{e}")));
                match read {
                    Ok((wanted, data)) => {
                        let total = data.len();
                        let pb = wanted.to_string_lossy().into_owned();
                        let mut off = 0;
                        while off < total {
                            let end = (off + fonts::FONT_CHUNK).min(total);
                            let chunk = &data[off..end];
                            let mut out = Vec::with_capacity(10 + pb.len() + chunk.len());
                            out.extend_from_slice(&u32b(total as u32));
                            out.extend_from_slice(&u32b(off as u32));
                            out.extend_from_slice(&u16b(pb.len() as u16));
                            out.extend_from_slice(pb.as_bytes());
                            out.extend_from_slice(chunk);
                            if !cl.send_frame(S_FONT_DATA, &out) {
                                break;
                            }
                            off = end;
                        }
                        log_line(&format!(
                            "font sent {} ({total} bytes)",
                            wanted.file_name().unwrap_or_default().to_string_lossy()
                        ));
                    }
                    Err(e) => {
                        cl.send_frame(S_ERROR, &client::error_payload(E_BAD_FRAME, &e));
                    }
                }
            }
            C_OPEN => {
                if p.len() < 6 {
                    cl.send_frame(S_ERROR, &client::error_payload(E_BAD_FRAME, "short open"));
                    return true;
                }
                let sid = get_u16(p, 0).unwrap_or(0);
                let cols = get_u16(p, 2).unwrap_or(0);
                let rows = get_u16(p, 4).unwrap_or(0);
                let (shell_req, cwd_req) = parse_open_strings(p, 6);
                let handle = match self.session(sid) {
                    Some(h) => {
                        // An existing session is resized, not respawned: the app reopening a tab
                        // it already has must not kill the shell it was running.
                        let _ = h.tx.send(SessionCmd::Resize(cols, rows)).await;
                        h
                    }
                    None => {
                        if self.live_count() >= MAX_SESSIONS {
                            cl.send_frame(S_ERROR, &client::error_payload(E_TOO_MANY, "too many sessions"));
                            return true;
                        }
                        match self.open_session(sid, cols, rows, &shell_req, &cwd_req) {
                            Ok(h) => h,
                            Err(e) => {
                                log_line(&format!("spawn failed for sid={sid}: {e}"));
                                cl.send_frame(S_ERROR, &client::error_payload(E_SPAWN, &e));
                                return true;
                            }
                        }
                    }
                };
                let pid = handle.pid;
                log_line(&format!(
                    "open sid={sid} cols={cols} rows={rows} pid={pid} (clients={})",
                    self.clients.load(Ordering::Relaxed)
                ));
                // The actor sends OPENED, then the modes, then the ring, then repaints.
                let _ = handle.tx.send(SessionCmd::Attach(cl.clone())).await;
            }
            C_CLOSE => {
                let Some(sid) = get_u16(p, 0) else {
                    return true;
                };
                let h = self.sessions.lock().ok().and_then(|mut m| m.remove(&sid));
                match h {
                    Some(h) => {
                        log_line(&format!("close sid={sid}"));
                        let _ = h.tx.send(SessionCmd::Close).await;
                    }
                    None => log_line(&format!("close sid={sid} (no such session)")),
                }
            }
            C_INPUT => {
                if p.len() < 6 {
                    return true;
                }
                let sid = get_u16(p, 0).unwrap_or(0);
                let l = get_u32(p, 2).unwrap_or(0) as usize;
                let end = (6 + l).min(p.len());
                if end <= 6 {
                    return true;
                }
                let data = p[6..end].to_vec();
                log_line(&format!(
                    "input sid={sid} {} bytes {:?}",
                    data.len(),
                    String::from_utf8_lossy(&data[..data.len().min(40)])
                ));
                if let Some(h) = self.session(sid) {
                    let _ = h.tx.send(SessionCmd::Input(data)).await;
                }
            }
            C_RESIZE => {
                if p.len() < 6 {
                    return true;
                }
                let sid = get_u16(p, 0).unwrap_or(0);
                let cols = get_u16(p, 2).unwrap_or(0);
                let rows = get_u16(p, 4).unwrap_or(0);
                if let Some(h) = self.session(sid) {
                    let _ = h.tx.send(SessionCmd::Resize(cols, rows)).await;
                }
            }
            C_SYNC => {
                let ids = self.live_ids();
                let mut out = Vec::with_capacity(2 + ids.len() * 2);
                out.extend_from_slice(&u16b(ids.len() as u16));
                for id in ids {
                    out.extend_from_slice(&u16b(id));
                }
                cl.send_frame(S_SYNCED, &out);
            }
            other => {
                let msg = format!("unknown type {other}");
                cl.send_frame(S_ERROR, &client::error_payload(E_BAD_FRAME, &msg));
            }
        }
        true
    }
}

/// The listener, the reaper, and shutdown.
pub async fn run(daemon: Arc<Daemon>, lock: InstanceLock) -> std::io::Result<()> {
    let listener = TcpListener::bind(("127.0.0.1", daemon.port)).await?;
    instance::write_pid(&daemon.rundir, std::process::id() as i32)?;
    println!(
        "ftermd {} listening on 127.0.0.1:{} (shell {})",
        PROTO_VERSION, daemon.port, daemon.shell
    );

    // A child that exits while its pty stays open (a backgrounded process holding the slave)
    // would otherwise keep the session forever; the reader alone does not see it.
    {
        let d = daemon.clone();
        tokio::spawn(async move {
            let mut tick = tokio::time::interval(Duration::from_millis(500));
            loop {
                tick.tick().await;
                for h in d.snapshot() {
                    if let Some(code) = pty::try_reap(h.pid) {
                        let _ = h.tx.try_send(SessionCmd::ChildExited(code));
                    }
                }
            }
        });
    }

    let mut term = signal(SignalKind::terminate())?;
    let mut intr = signal(SignalKind::interrupt())?;
    loop {
        tokio::select! {
            accepted = listener.accept() => match accepted {
                Ok((sock, addr)) => {
                    let d = daemon.clone();
                    tokio::spawn(async move {
                        client::serve(sock, d).await;
                    });
                    log_line(&format!("connection from {addr}"));
                }
                Err(e) => {
                    log_line(&format!("accept error: {e}"));
                    tokio::time::sleep(Duration::from_millis(50)).await;
                }
            },
            _ = term.recv() => break,
            _ = intr.recv() => break,
        }
    }

    log_line("shutting down");
    drop(listener);
    for h in daemon.snapshot() {
        let _ = tokio::time::timeout(Duration::from_millis(300), h.tx.send(SessionCmd::Close)).await;
    }
    tokio::time::sleep(Duration::from_millis(100)).await;
    instance::remove_pid(&daemon.rundir);
    drop(lock);
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn the_daemon_reads_its_configuration_from_the_environment() {
        let _g = crate::testutil::env_lock();
        let dir = std::env::temp_dir().join(format!("ftermd-daemon-{}", std::process::id()));
        std::env::set_var("FTERM_RUNDIR", &dir);
        std::env::set_var("FTERM_PORT", "8899");
        std::env::set_var("FTERM_HISTORY_BYTES", "4096");
        let d = Daemon::new();
        std::env::remove_var("FTERM_RUNDIR");
        std::env::remove_var("FTERM_PORT");
        std::env::remove_var("FTERM_HISTORY_BYTES");
        assert_eq!(d.port, 8899);
        assert_eq!(d.history_bytes, 4096);
        assert_eq!(d.token.len(), 48, "a token is generated on first start");
        assert!(d.base_env.iter().any(|(k, _)| k == "TERM_PROGRAM"));
        let _ = std::fs::remove_dir_all(&dir);
    }
}
