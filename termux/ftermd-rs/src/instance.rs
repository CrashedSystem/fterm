//! Who is the daemon, and is it still me?
//!
//! The bug this replaces: the Python daemon decided "already running" from
//! `os.kill(pid, 0)` alone. After a crash the pidfile stays, and once the kernel hands that pid
//! to any other process the check succeeds forever - `start` prints "already running" and exits,
//! the app keeps failing to connect, and nothing in the log says why.
//!
//! Two answers here, and they cover different halves of it:
//!
//! * an `flock` on `~/.fterm/ftermd.lock`, held for the daemon's whole life. The kernel drops it
//!   when the process dies, however it dies, so a stale lockfile cannot lie. This is what
//!   `start` uses to decide whether another daemon is *really* running.
//! * `/proc/<pid>/cmdline`, which has to name an ftermd before a pid is believed. This is what
//!   `stop` and `status` use, so a reused pid is never signalled - and a zombie (empty cmdline)
//!   is not mistaken for a live daemon either.

use std::fs;
use std::io::{self, Seek, SeekFrom, Write};
use std::os::unix::fs::OpenOptionsExt;
use std::os::unix::io::AsRawFd;
use std::path::{Path, PathBuf};

use crate::env::ensure_rundir;

pub struct InstanceLock {
    /// Held, never read: the kernel owns the lock and drops it when this file closes.
    #[allow(dead_code)]
    file: fs::File,
    /// Where the lock lives, for diagnostics and tests.
    #[allow(dead_code)]
    pub path: PathBuf,
}

impl InstanceLock {
    /// `Ok(None)` means another daemon holds the lock, which is the only honest "already
    /// running".
    pub fn acquire(dir: &Path) -> io::Result<Option<InstanceLock>> {
        ensure_rundir(dir)?;
        let path = dir.join("ftermd.lock");
        let mut file = fs::OpenOptions::new()
            .read(true)
            .write(true)
            .create(true)
            .truncate(false)
            .mode(0o600)
            .open(&path)?;
        let rc = unsafe { libc::flock(file.as_raw_fd(), libc::LOCK_EX | libc::LOCK_NB) };
        if rc != 0 {
            let e = io::Error::last_os_error();
            if e.raw_os_error() == Some(libc::EWOULDBLOCK) {
                return Ok(None);
            }
            return Err(e);
        }
        // Record our pid so `status` has something to report even before the pidfile is read.
        let _ = file.set_len(0);
        let _ = file.seek(SeekFrom::Start(0));
        let _ = write!(file, "{}", std::process::id());
        let _ = file.flush();
        Ok(Some(InstanceLock { file, path }))
    }

}

/// The pid recorded in the lockfile by the running daemon, if one holds it.
pub fn lock_holder(dir: &Path) -> Option<i32> {
    let text = fs::read_to_string(dir.join("ftermd.lock")).ok()?;
    text.trim().parse().ok()
}

pub fn pidfile(dir: &Path) -> PathBuf {
    dir.join("ftermd.pid")
}

pub fn read_pid(dir: &Path) -> Option<i32> {
    let text = fs::read_to_string(pidfile(dir)).ok()?;
    let pid: i32 = text.trim().parse().ok()?;
    if pid > 0 {
        Some(pid)
    } else {
        None
    }
}

pub fn write_pid(dir: &Path, pid: i32) -> io::Result<()> {
    crate::env::write_token_file(dir, "ftermd.pid", &pid.to_string())
}

pub fn remove_pid(dir: &Path) {
    let _ = fs::remove_file(pidfile(dir));
}

/// Does this pid belong to an ftermd?
///
/// `/proc/<pid>/cmdline` is the process's own argv, NUL separated. A zombie's is empty, which is
/// exactly what makes this the right check for `stop`: a daemon that has exited but not been
/// reaped is not running, whatever `kill(pid, 0)` says.
pub fn pid_is_ftermd(pid: i32) -> bool {
    if pid <= 0 {
        return false;
    }
    let Ok(raw) = fs::read(format!("/proc/{pid}/cmdline")) else {
        return false;
    };
    if raw.is_empty() {
        return false;
    }
    raw.split(|b| *b == 0).any(|part| {
        let s = String::from_utf8_lossy(part);
        let base = s.rsplit('/').next().unwrap_or("");
        base.contains("ftermd")
    })
}

pub fn alive(pid: i32) -> bool {
    pid > 0 && unsafe { libc::kill(pid, 0) == 0 }
}

/// A daemon is running if its pidfile names a live ftermd.
pub fn running_pid(dir: &Path) -> Option<i32> {
    let pid = read_pid(dir)?;
    if alive(pid) && pid_is_ftermd(pid) {
        Some(pid)
    } else {
        None
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn tmpdir(tag: &str) -> PathBuf {
        let d = std::env::temp_dir().join(format!(
            "ftermd-lock-{tag}-{}-{}",
            std::process::id(),
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_nanos()
        ));
        fs::create_dir_all(&d).unwrap();
        d
    }

    #[test]
    fn only_one_instance_can_hold_the_lock() {
        let dir = tmpdir("one");
        let first = InstanceLock::acquire(&dir).unwrap();
        assert!(first.is_some(), "the first acquire must succeed");
        let second = InstanceLock::acquire(&dir).unwrap();
        assert!(second.is_none(), "the second must see the first");
        drop(first);
        // The kernel releases the lock with the file; a new daemon can start again.
        let third = InstanceLock::acquire(&dir).unwrap();
        assert!(third.is_some(), "the lock must be released when the holder goes");
        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn the_lock_records_the_holder() {
        let dir = tmpdir("holder");
        let lock = InstanceLock::acquire(&dir).unwrap().unwrap();
        // The pid is written into the lockfile so `start` can name the process holding it.
        assert_eq!(lock_holder(&dir), Some(std::process::id() as i32));
        assert!(lock.path.exists());
        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn a_reused_pid_is_not_an_ftermd() {
        // The test binary is target/debug/deps/ftermd-<hash>, so this is the true case.
        assert!(pid_is_ftermd(std::process::id() as i32));
        let mut child = std::process::Command::new("sleep")
            .arg("30")
            .spawn()
            .expect("sleep must exist");
        let pid = child.id() as i32;
        assert!(!pid_is_ftermd(pid), "a process that is not ftermd must not be believed");
        let _ = child.kill();
        let _ = child.wait();
        assert!(!pid_is_ftermd(999_999));
        assert!(!pid_is_ftermd(0));
        assert!(!pid_is_ftermd(-1));
    }

    #[test]
    fn a_stale_pidfile_is_not_running() {
        let dir = tmpdir("stale");
        let mut child = std::process::Command::new("sleep").arg("30").spawn().unwrap();
        write_pid(&dir, child.id() as i32).unwrap();
        // The pid is alive, but it is not the daemon: this is exactly the state that used to
        // make `start` refuse to run forever.
        assert!(alive(child.id() as i32));
        assert_eq!(running_pid(&dir), None);
        let _ = child.kill();
        let _ = child.wait();
        // And after it is gone the answer stays None rather than turning into an error.
        assert_eq!(running_pid(&dir), None);
        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn the_pidfile_round_trips() {
        let dir = tmpdir("pid");
        assert_eq!(read_pid(&dir), None);
        write_pid(&dir, 4242).unwrap();
        assert_eq!(read_pid(&dir), Some(4242));
        remove_pid(&dir);
        assert_eq!(read_pid(&dir), None);
        let _ = fs::remove_dir_all(&dir);
    }
}
