//! ftermd, in Rust.
//!
//! 같은 프로토콜, 같은 파일 이름, 같은 명령줄. 앱은 어느 데몬에 붙었는지 알 수 없어야 하고,
//! `termux/install.sh` 와 `termux/fterm-keeper` 도 마찬가지입니다. 바뀐 것은 데몬 안쪽입니다:
//! 탭 전체가 공유하던 select() 루프를 세션별·클라이언트별 태스크로 나누고, 느린 클라이언트의
//! 출력 큐에 상한을 두고, pidfile 대신 flock 으로 "이미 실행 중"을 판정합니다.
//!
//! 자세한 배경과 남은 작업 순서는 `docs/ROADMAP.md` 에 있습니다.

mod cli;
mod client;
mod daemon;
mod env;
mod fonts;
mod instance;
mod modes;
mod protocol;
mod pty;
mod ring;

#[cfg(test)]
mod testutil;

/// stderr only; the daemon's stdout carries the one-line startup banner.
pub fn log_line(msg: &str) {
    let now = unsafe { libc::time(std::ptr::null_mut()) };
    let mut tm: libc::tm = unsafe { std::mem::zeroed() };
    unsafe {
        libc::localtime_r(&now, &mut tm);
    }
    eprintln!(
        "[ftermd {:02}:{:02}:{:02}] {}",
        tm.tm_hour, tm.tm_min, tm.tm_sec, msg
    );
}

fn main() {
    let args: Vec<String> = std::env::args().collect();
    let code = if args.len() < 2 {
        print!("{}", cli::USAGE);
        2
    } else {
        match args[1].as_str() {
            "start" => cli::cmd_start(),
            "stop" => cli::cmd_stop(),
            "status" => cli::cmd_status(),
            "token" => cli::cmd_token(),
            "reset-token" => cli::cmd_reset_token(),
            "fonts" => cli::cmd_fonts(),
            "font" => cli::cmd_font(args.get(2).map(|s| s.as_str()).unwrap_or("")),
            "env" => cli::cmd_env(),
            "version" | "--version" | "-V" => cli::version(),
            "--help" | "-h" | "help" => {
                print!("{}", cli::USAGE);
                0
            }
            other => {
                eprintln!("ftermd: unknown command {other}");
                2
            }
        }
    };
    cli::flush_stdout();
    std::process::exit(code);
}
