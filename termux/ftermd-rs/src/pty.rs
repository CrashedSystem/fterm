//! One PTY session, and the reason this daemon exists.
//!
//! In `termux/ftermd` every tab shares one `select()` loop, so a blocked `os.write` to a full
//! PTY, a `redraw()`'s 50 ms sleep, or a 32 MiB font read stops *every* tab for as long as it
//! takes. Here each session owns a blocking reader thread and a blocking writer thread - the
//! only place a PTY is allowed to block - and everything else is a task on bounded channels.
//! One tab that a program refuses to read applies backpressure to that tab and nothing else.

use std::collections::HashMap;
use std::ffi::CString;
use std::io;
use std::os::unix::io::RawFd;
use std::path::PathBuf;
use std::sync::{mpsc as stdmpsc, Arc, Mutex};
use std::time::Duration;

use tokio::sync::mpsc;

use crate::client::ClientHandle;
use crate::log_line;
use crate::modes::Modes;
use crate::protocol;
use crate::ring::Ring;

/// How many chunks a writer thread may hold before input to a program that is not reading is
/// dropped. Input is small (keystrokes), so this only fills when the program itself has stopped
/// reading - and at that point queuing more would just be memory.
const INPUT_QUEUE: usize = 256;

/// How much output one session may buffer before it stops reading its PTY. This is the other
/// half of backpressure: the program blocks on write, exactly as it would on a real terminal.
const OUTPUT_QUEUE: usize = 64;

pub enum SessionCmd {
    /// Bytes read from the PTY.
    Output(Vec<u8>),
    /// Bytes to write to the PTY.
    Input(Vec<u8>),
    Resize(u16, u16),
    /// A client wants this session; the actor sends it OPENED, the modes and the ring itself,
    /// in that order, so no frame from the redraw can overtake them.
    Attach(ClientHandle),
    Detach(u64),
    /// The reader thread saw EOF/EIO.
    ReaderEof,
    /// The reaper saw the child exit.
    ChildExited(i32),
    /// C_CLOSE: kill the shell and forget the session.
    Close,
}

#[derive(Clone)]
pub struct SessionHandle {
    /// Kept for logs and for callers that hold a handle without the id: the registry key is
    /// the same value.
    #[allow(dead_code)]
    pub sid: u16,
    pub pid: i32,
    pub tx: mpsc::Sender<SessionCmd>,
}

pub struct SessionSpec {
    pub sid: u16,
    pub cols: u16,
    pub rows: u16,
    pub shell: String,
    pub cwd: PathBuf,
    pub env: Vec<(String, String)>,
    pub history_bytes: usize,
}

/// Start a shell on a new PTY and the actor that owns it.
pub fn start(
    spec: SessionSpec,
    sessions: Arc<Mutex<HashMap<u16, SessionHandle>>>,
) -> io::Result<SessionHandle> {
    let (pid, master) = spawn_pty(&spec)?;
    let read_fd = dup_fd(master)?;
    let write_fd = dup_fd(master)?;

    let (out_tx, out_rx) = mpsc::channel::<SessionCmd>(OUTPUT_QUEUE);
    let (in_tx, in_rx) = stdmpsc::sync_channel::<Vec<u8>>(INPUT_QUEUE);

    // Reader: the one place a PTY read blocks. `blocking_send` is the backpressure - when the
    // actor is behind, this thread stops reading and the program blocks on write.
    {
        let tx = out_tx.clone();
        std::thread::Builder::new()
            .name(format!("ftermd-read-{}", spec.sid))
            .spawn(move || reader_loop(read_fd, tx))?;
    }
    // Writer: the one place a PTY write blocks, for the same reason.
    std::thread::Builder::new()
        .name(format!("ftermd-write-{}", spec.sid))
        .spawn(move || writer_loop(write_fd, in_rx))?;

    let handle = SessionHandle {
        sid: spec.sid,
        pid,
        tx: out_tx.clone(),
    };

    let actor = Session {
        sid: spec.sid,
        pid,
        io_fd: master,
        cols: clamp_size(spec.cols),
        rows: clamp_size(spec.rows),
        ring: Ring::new(spec.history_bytes),
        modes: Modes::new(),
        attached: HashMap::new(),
        writer: in_tx,
        sessions,
        closed: false,
        pending_redraw: false,
    };
    tokio::spawn(actor.run(out_rx));
    Ok(handle)
}

struct Session {
    sid: u16,
    pid: i32,
    io_fd: RawFd,
    cols: u16,
    rows: u16,
    ring: Ring,
    modes: Modes,
    attached: HashMap<u64, ClientHandle>,
    writer: stdmpsc::SyncSender<Vec<u8>>,
    sessions: Arc<Mutex<HashMap<u16, SessionHandle>>>,
    closed: bool,
    pending_redraw: bool,
}

impl Session {
    async fn run(mut self, mut rx: mpsc::Receiver<SessionCmd>) {
        while let Some(cmd) = rx.recv().await {
            match cmd {
                SessionCmd::Output(data) => {
                    if self.closed {
                        continue;
                    }
                    self.ring.add(&data);
                    self.modes.feed(&data);
                    self.broadcast(protocol::S_OUTPUT, self.sid, &data);
                }
                SessionCmd::Input(data) => {
                    if self.closed {
                        continue;
                    }
                    if let Err(e) = self.writer.try_send(data) {
                        // A program that has stopped reading its terminal: its tab stalls,
                        // every other tab does not. Dropping the keystroke is the honest
                        // outcome - the alternative is unbounded memory.
                        log_line(&format!(
                            "sid={} input dropped: {e}",
                            self.sid
                        ));
                    }
                }
                SessionCmd::Resize(cols, rows) => {
                    if !self.closed {
                        self.set_size(cols, rows);
                    }
                }
                SessionCmd::Attach(client) => {
                    if self.closed {
                        continue;
                    }
                    self.attach(client);
                    // A full-screen program owns the screen and only paints when *it* decides
                    // to. Its last frame is in the ring, but the cursor and the row it thinks it
                    // is on are not, so it has to be asked to repaint. A plain shell is not
                    // asked: a SIGWINCH there just repaints the prompt line on top of the
                    // restored screen.
                    self.pending_redraw = self.modes.interactive();
                }
                SessionCmd::Detach(id) => {
                    self.attached.remove(&id);
                }
                SessionCmd::ReaderEof => {
                    let code = self.wait_exit_now();
                    self.finish(code, true);
                    break;
                }
                SessionCmd::ChildExited(code) => {
                    self.finish(code, true);
                    break;
                }
                SessionCmd::Close => {
                    self.finish(0, false);
                    break;
                }
            }
            if self.pending_redraw {
                self.pending_redraw = false;
                self.redraw().await;
            }
        }
        // The channels are gone as well; make sure nothing is left running under us.
        if !self.closed {
            self.finish(0, false);
        }
    }

    /// OPENED, then the modes, then the ring - and only then a repaint request.
    ///
    /// The order is the whole reconnect fix: the modes belong to the program that is still
    /// running, and the screen below them may be that program's own redraw of them.
    fn attach(&mut self, client: ClientHandle) {
        let id = client.id();
        self.attached.insert(id, client.clone());

        let mut opened = Vec::with_capacity(6);
        opened.extend_from_slice(&protocol::u16b(self.sid));
        opened.extend_from_slice(&self.pid.to_le_bytes());
        client.send_frame(protocol::S_OPENED, &opened);

        let modes = self.modes.restore();
        if !modes.is_empty() {
            self.broadcast_to(&client, protocol::S_OUTPUT, self.sid, &modes);
        }
        if !self.ring.is_empty() {
            let mut payload = Vec::with_capacity(2 + self.ring.len());
            let ring = self.ring.get();
            payload.extend_from_slice(&protocol::u16b(self.sid));
            payload.extend_from_slice(ring);
            client.send_frame(protocol::S_REPLAY, &payload);
        }
    }

    fn broadcast(&mut self, typ: u8, sid: u16, data: &[u8]) {
        let mut header = [0u8; 6];
        header[0..2].copy_from_slice(&sid.to_le_bytes());
        header[2..6].copy_from_slice(&(data.len() as u32).to_le_bytes());
        let frame = protocol::frame2(typ, &header, data);
        let mut dead = Vec::new();
        for (id, c) in self.attached.iter() {
            // A slow client is dropped here and only here: its own queue hit the cap, so its
            // connection goes while the session, the shell and the ring stay alive for the
            // reconnect that follows.
            if !c.send(frame.clone()) {
                dead.push(*id);
            }
        }
        for id in dead {
            self.attached.remove(&id);
        }
    }

    fn broadcast_to(&self, client: &ClientHandle, typ: u8, sid: u16, data: &[u8]) {
        let mut header = [0u8; 6];
        header[0..2].copy_from_slice(&sid.to_le_bytes());
        header[2..6].copy_from_slice(&(data.len() as u32).to_le_bytes());
        client.send(protocol::frame2(typ, &header, data));
    }

    fn set_size(&mut self, cols: u16, rows: u16) {
        self.cols = clamp_size(cols);
        self.rows = clamp_size(rows);
        self.apply_size(self.cols, self.rows);
    }

    /// Ask the program to repaint by delivering a SIGWINCH.
    ///
    /// The PTY coalesces size changes, so setting the same size twice is a no-op. Shrinking by
    /// one column and restoring it is two real changes, and it is what makes a full-screen
    /// program - opencode, vim, less - repaint a screen the client lost.
    async fn redraw(&mut self) {
        if self.closed || self.cols < 2 {
            return;
        }
        let (cols, rows) = (self.cols, self.rows);
        self.apply_size(cols - 1, rows);
        tokio::time::sleep(Duration::from_millis(50)).await;
        self.apply_size(cols, rows);
    }

    fn apply_size(&self, cols: u16, rows: u16) {
        let ws = libc::winsize {
            ws_row: rows,
            ws_col: cols,
            ws_xpixel: 0,
            ws_ypixel: 0,
        };
        unsafe {
            libc::ioctl(self.io_fd, libc::TIOCSWINSZ, &ws);
        }
    }

    fn finish(&mut self, code: i32, notify: bool) {
        if self.closed {
            return;
        }
        self.closed = true;
        if notify {
            let mut payload = Vec::with_capacity(6);
            payload.extend_from_slice(&protocol::u16b(self.sid));
            payload.extend_from_slice(&code.to_le_bytes());
            let frame = protocol::frame(protocol::S_EXIT, &payload);
            for c in self.attached.values() {
                c.send(frame.clone());
            }
        }
        self.attached.clear();
        kill_group(self.pid);
        unsafe {
            libc::close(self.io_fd);
        }
        self.io_fd = -1;
        if let Ok(mut map) = self.sessions.lock() {
            map.remove(&self.sid);
        }
    }

    fn wait_exit_now(&self) -> i32 {
        wait_status(self.pid)
    }
}

fn clamp_size(v: u16) -> u16 {
    v.clamp(1, 2000)
}

/// Reap one child if it has exited. `None` means it is still running (or already reaped).
pub fn try_reap(pid: i32) -> Option<i32> {
    let mut status: libc::c_int = 0;
    loop {
        let r = unsafe { libc::waitpid(pid, &mut status, libc::WNOHANG) };
        if r == pid {
            if libc::WIFEXITED(status) {
                return Some(libc::WEXITSTATUS(status));
            }
            if libc::WIFSIGNALED(status) {
                return Some(-libc::WTERMSIG(status));
            }
            return Some(-1);
        }
        if r == 0 {
            return None;
        }
        let e = io::Error::last_os_error();
        if e.raw_os_error() == Some(libc::EINTR) {
            continue;
        }
        return None;
    }
}

/// Reap one child if it has exited, returning its exit code or -1 when it is still running.
pub fn wait_status(pid: i32) -> i32 {
    let mut status: libc::c_int = 0;
    loop {
        let r = unsafe { libc::waitpid(pid, &mut status, libc::WNOHANG) };
        if r == pid {
            if libc::WIFEXITED(status) {
                return libc::WEXITSTATUS(status);
            }
            if libc::WIFSIGNALED(status) {
                return -libc::WTERMSIG(status);
            }
            return -1;
        }
        if r == 0 {
            return -1; // still running
        }
        let e = io::Error::last_os_error();
        if e.raw_os_error() == Some(libc::EINTR) {
            continue;
        }
        return -1; // already reaped by someone else
    }
}

/// Kill the shell and everything it started, in the same three steps as the first (Python) daemon.
fn kill_group(pid: i32) {
    if pid <= 0 {
        return;
    }
    for sig in [libc::SIGHUP, libc::SIGTERM, libc::SIGKILL] {
        let mut sent = unsafe { libc::killpg(pid, sig) } == 0;
        if !sent {
            sent = unsafe { libc::kill(pid, sig) } == 0;
        }
        if !sent {
            break;
        }
        std::thread::sleep(Duration::from_millis(20));
    }
}

fn dup_fd(fd: RawFd) -> io::Result<RawFd> {
    let n = unsafe { libc::dup(fd) };
    if n < 0 {
        return Err(io::Error::last_os_error());
    }
    Ok(n)
}

fn reader_loop(fd: RawFd, tx: mpsc::Sender<SessionCmd>) {
    let mut buf = vec![0u8; 65536];
    loop {
        let n = unsafe { libc::read(fd, buf.as_mut_ptr() as *mut libc::c_void, buf.len()) };
        if n > 0 {
            let data = buf[..n as usize].to_vec();
            if tx.blocking_send(SessionCmd::Output(data)).is_err() {
                break;
            }
            continue;
        }
        if n < 0 {
            let e = io::Error::last_os_error();
            if e.raw_os_error() == Some(libc::EINTR) {
                continue;
            }
        }
        // 0 or EIO: the shell side is gone.
        let _ = tx.blocking_send(SessionCmd::ReaderEof);
        break;
    }
    unsafe {
        libc::close(fd);
    }
}

fn writer_loop(fd: RawFd, rx: stdmpsc::Receiver<Vec<u8>>) {
    while let Ok(chunk) = rx.recv() {
        let mut off = 0;
        while off < chunk.len() {
            let n = unsafe {
                libc::write(
                    fd,
                    chunk[off..].as_ptr() as *const libc::c_void,
                    chunk.len() - off,
                )
            };
            if n < 0 {
                let e = io::Error::last_os_error();
                if e.raw_os_error() == Some(libc::EINTR) {
                    continue;
                }
                break;
            }
            off += n as usize;
        }
    }
    unsafe {
        libc::close(fd);
    }
}

/// fork + exec on a pty whose winning size the daemon owns.
///
/// Everything the child needs is built before the fork: after it, in a process with tokio's
/// worker threads, the only safe things left to call are the ones that do not allocate.
fn spawn_pty(spec: &SessionSpec) -> io::Result<(i32, RawFd)> {
    let shell = CString::new(spec.shell.as_bytes())
        .map_err(|_| io::Error::new(io::ErrorKind::InvalidInput, "shell path has a NUL"))?;
    let argv0 = shell.clone();
    let login = CString::new("-l").unwrap();
    let argv_ptrs = vec![
        argv0.as_ptr(),
        login.as_ptr(),
        std::ptr::null(),
    ];
    let mut env_c: Vec<CString> = Vec::with_capacity(spec.env.len());
    for (k, v) in spec.env.iter() {
        let joined = format!("{k}={v}");
        // A malformed pair is dropped rather than aborting the session.
        if let Ok(c) = CString::new(joined) {
            env_c.push(c);
        }
    }
    let mut env_ptrs: Vec<*const libc::c_char> = env_c.iter().map(|c| c.as_ptr()).collect();
    env_ptrs.push(std::ptr::null());

    let cwd = CString::new(spec.cwd.to_string_lossy().as_bytes()).ok();

    let mut master: libc::c_int = -1;
    let pid = unsafe {
        libc::forkpty(
            &mut master,
            std::ptr::null_mut(),
            std::ptr::null(),
            std::ptr::null(),
        )
    };
    if pid < 0 {
        return Err(io::Error::last_os_error());
    }
    if pid == 0 {
        // Child: only libc calls from here, then exec.
        unsafe {
            if let Some(cwd) = &cwd {
                libc::chdir(cwd.as_ptr());
            }
            libc::execve(shell.as_ptr(), argv_ptrs.as_ptr(), env_ptrs.as_ptr());
            libc::_exit(127);
        }
    }

    let ws = libc::winsize {
        ws_row: clamp_size(spec.rows),
        ws_col: clamp_size(spec.cols),
        ws_xpixel: 0,
        ws_ypixel: 0,
    };
    unsafe {
        libc::ioctl(master, libc::TIOCSWINSZ, &ws);
    }
    Ok((pid, master))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sizes_are_clamped_to_something_a_terminal_can_be() {
        assert_eq!(clamp_size(0), 1);
        assert_eq!(clamp_size(80), 80);
        assert_eq!(clamp_size(5000), 2000);
    }

    #[test]
    fn a_dead_pid_reports_minus_one_rather_than_blocking() {
        // Nothing to wait for: waitpid fails and the actor must not hang on it.
        assert_eq!(wait_status(999_999), -1);
        assert_eq!(try_reap(999_999), None);
    }

    #[test]
    fn killing_a_group_that_does_not_exist_is_harmless() {
        kill_group(999_999);
        kill_group(0);
        kill_group(-1);
    }
}
