//! The command line: `start`, `stop`, `status`, `token`, `reset-token`, `fonts`, `font`, `env`.
//!
//! `termux/install.sh` and `termux/fterm-keeper` drive the daemon through these, so the
//! subcommands, their output and their exit codes have to match `termux/ftermd` exactly:
//! `status` answers with JSON and 0 only when the socket actually accepts a connection, and
//! `token` prints just the token because the keeper writes that straight to a file.

use std::io::{self, Write};
use std::net::{SocketAddr, TcpStream};
use std::time::Duration;

use crate::daemon::{self, Daemon};
use crate::env;
use crate::fonts;
use crate::instance;
use crate::protocol::PROTO_VERSION;

fn port() -> u16 {
    std::env::var("FTERM_PORT")
        .ok()
        .and_then(|p| p.parse().ok())
        .unwrap_or(8791)
}

pub const USAGE: &str = "ftermd — fterm 앱을 위한 PTY 데몬 (Rust)

  ftermd start            포그라운드 데몬 (FTERM_PORT 기본 8791)
  ftermd stop             pidfile + SIGTERM
  ftermd status           소켓/pid 확인 (JSON)
  ftermd token            토큰 출력(없으면 생성)
  ftermd reset-token      토큰 교체 (앱 재입력 필요)
  ftermd fonts            앱에 줄 수 있는 글꼴 목록 (크기\\t경로)
  ftermd font <url>       글꼴을 ~/.fonts 로 내려받고 경로 출력
  ftermd env              ART 환경 변수를 shell 문법으로 출력 (rish 같은 래퍼가 source)

환경 변수: FTERM_PORT, FTERM_SHELL, FTERM_RUNDIR, FTERM_HISTORY_BYTES, FTERM_FONT_DIRS
";

pub fn cmd_start() -> i32 {
    let dir = env::rundir();
    if let Err(e) = env::ensure_rundir(&dir) {
        eprintln!("ftermd: {e}");
        return 1;
    }
    match instance::InstanceLock::acquire(&dir) {
        Ok(Some(lock)) => {
            // Nothing below trusts the old pidfile: the lock is what says "not already running",
            // and a pidfile left behind by a crash is simply replaced.
            let daemon = Daemon::new();
            let rt = match tokio::runtime::Builder::new_multi_thread()
                .worker_threads(2)
                .enable_all()
                .build()
            {
                Ok(rt) => rt,
                Err(e) => {
                    eprintln!("ftermd: {e}");
                    return 1;
                }
            };
            match rt.block_on(daemon::run(std::sync::Arc::new(daemon), lock)) {
                Ok(()) => 0,
                Err(e) => {
                    eprintln!("ftermd: {e}");
                    instance::remove_pid(&dir);
                    1
                }
            }
        }
        Ok(None) => {
            // Say which pid holds it, so the message can be checked against `ps`.
            let pid = instance::read_pid(&dir)
                .or_else(|| instance::lock_holder(&dir))
                .unwrap_or(0);
            println!("ftermd already running (pid {pid})");
            1
        }
        Err(e) => {
            eprintln!("ftermd: {e}");
            1
        }
    }
}

pub fn cmd_stop() -> i32 {
    let dir = env::rundir();
    let Some(pid) = instance::read_pid(&dir) else {
        println!("no pidfile");
        return 1;
    };
    // The whole point of the check: a pid that the kernel reused, or a daemon that has exited but
    // not been reaped, is not something to signal.
    if !instance::alive(pid) || !instance::pid_is_ftermd(pid) {
        println!("no daemon (stale pidfile: {pid} is not ftermd)");
        instance::remove_pid(&dir);
        return 1;
    }
    if unsafe { libc::kill(pid, libc::SIGTERM) } != 0 {
        println!("kill failed: {}", io::Error::last_os_error());
        return 1;
    }
    for _ in 0..100 {
        if !(instance::alive(pid) && instance::pid_is_ftermd(pid)) {
            println!("stopped");
            instance::remove_pid(&dir);
            return 0;
        }
        std::thread::sleep(Duration::from_millis(50));
    }
    println!("still running (pid {pid})");
    1
}

pub fn cmd_status() -> i32 {
    let dir = env::rundir();
    let port = port();
    let pid = instance::read_pid(&dir).unwrap_or(0);
    // A pidfile that names a reused pid, or a zombie, does not make a daemon alive.
    let alive = instance::running_pid(&dir).is_some();
    let addr = SocketAddr::from(([127, 0, 0, 1], port));
    let reachable = TcpStream::connect_timeout(&addr, Duration::from_secs(1)).is_ok();
    println!(
        "{{\"pid\":{pid},\"alive\":{alive},\"port\":{port},\"reachable\":{reachable},\"shell\":\"{}\",\"rundir\":\"{}\",\"token_file\":\"{}\"}}",
        jesc(&env::default_shell()),
        jesc(&dir.to_string_lossy()),
        jesc(&env::token_path(&dir).to_string_lossy())
    );
    if alive && reachable {
        0
    } else {
        2
    }
}

pub fn cmd_token() -> i32 {
    let dir = env::rundir();
    match env::read_token(&dir, true) {
        Ok(Some(t)) => {
            println!("{t}");
            0
        }
        Ok(None) => 1,
        Err(e) => {
            eprintln!("ftermd: {e}");
            1
        }
    }
}

pub fn cmd_reset_token() -> i32 {
    let dir = env::rundir();
    let _ = std::fs::remove_file(env::token_path(&dir));
    cmd_token()
}

pub fn cmd_fonts() -> i32 {
    let found = fonts::font_scan();
    for (path, size) in &found {
        println!("{size}\t{}", path.display());
    }
    if found.is_empty() {
        1
    } else {
        0
    }
}

pub fn cmd_font(url: &str) -> i32 {
    if url.is_empty() {
        eprintln!("사용법: ftermd font <url>");
        return 2;
    }
    match fonts::fetch_font(url) {
        Ok(dest) => {
            println!("{}", dest.display());
            0
        }
        Err(e) => {
            eprintln!("ftermd font: {e}");
            1
        }
    }
}

pub fn cmd_env() -> i32 {
    let dir = env::rundir();
    let found = env::discover_android_env(&dir);
    let bc = found.get("BOOTCLASSPATH").map(|s| s.as_str()).unwrap_or("");
    if !env::bootclasspath_valid(bc) {
        eprintln!("ftermd env: could not find BOOTCLASSPATH in any process");
        return 1;
    }
    for k in env::ANDROID_ENV {
        if let Some(v) = found.get(k) {
            println!("export {k}='{}'", v.replace('\'', "'\\''"));
        }
    }
    0
}

pub fn version() -> i32 {
    println!("ftermd {PROTO_VERSION} (rust)");
    0
}

/// Escape the few characters that would break a one-line JSON string.
fn jesc(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    for c in s.chars() {
        match c {
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            '\n' => out.push_str("\\n"),
            c if (c as u32) < 0x20 => out.push_str(&format!("\\u{:04x}", c as u32)),
            c => out.push(c),
        }
    }
    out
}

pub fn flush_stdout() {
    let _ = io::stdout().flush();
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn json_strings_are_escaped() {
        assert_eq!(jesc("/a/b"), "/a/b");
        assert_eq!(jesc("a\"b"), "a\\\"b");
        assert_eq!(jesc("a\\b"), "a\\\\b");
        assert_eq!(jesc("a\nb"), "a\\nb");
    }

    #[test]
    fn the_usage_lists_every_subcommand_the_keeper_calls() {
        for cmd in ["start", "stop", "status", "token", "fonts", "font", "env"] {
            assert!(USAGE.contains(cmd), "{cmd} missing from usage");
        }
    }
}
