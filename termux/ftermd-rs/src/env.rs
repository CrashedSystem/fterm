//! The environment a session's shell gets, and the ART variables it is easy to lose.
//!
//! The bug this encodes: the daemon used to hand the shell a hardcoded list of eleven names.
//! Termux's own app shell also gives bash `BOOTCLASSPATH` and the `ANDROID_*_ROOT` variables,
//! and `am` - which every termux-api command goes through - is `app_process` loading an APK's
//! dex. ART builds its boot class path from `BOOTCLASSPATH`, so without it `am` exits 0 having
//! printed nothing, and `termux-battery-status` waits for a callback that was never sent. From
//! outside that reads as "termux-api is broken", not "the terminal dropped a variable".
//!
//! So: inherit, then curate the list of what the shell may *not* inherit (the daemon's own
//! parent identity - an ssh session, a debugger), and find the ART variables from a live
//! process when they are missing, caching the answer.

use std::collections::HashMap;
use std::fs;
use std::io::{self, Write};
use std::os::unix::fs::{OpenOptionsExt, PermissionsExt};
use std::path::{Path, PathBuf};

/// Variables Termux's app shell hands to bash, which `am` and most of `/system/bin` need.
/// Measured by removing one at a time: dropping any of the first five breaks `am broadcast`;
/// dropping `ANDROID_DATA`, `ANDROID_ROOT` or `TERMUX_APP__*` does not.
pub const ANDROID_ENV: [&str; 7] = [
    "BOOTCLASSPATH",
    "DEX2OATBOOTCLASSPATH",
    "ANDROID_ART_ROOT",
    "ANDROID_I18N_ROOT",
    "ANDROID_TZDATA_ROOT",
    "ANDROID_DATA",
    "ANDROID_ROOT",
];

/// Identity variables worth keeping for programs that ask "am I inside Termux".
pub const TERMUX_ENV: [&str; 11] = [
    "TERMUX_VERSION",
    "TERMUX_MAIN_PACKAGE_FORMAT",
    "TERMUX_APK_RELEASE",
    "TERMUX_API_VERSION",
    "TERMUX_IS_DEBUGGABLE_BUILD",
    "TERMUX_APP_PID",
    "TERMUX__PREFIX",
    "TERMUX__HOME",
    "TERMUX__ROOTFS_DIR",
    "TERMUX_APP__DATA_DIR",
    "TERMUX_APP__LEGACY_DATA_DIR",
];

/// Not inherited even when the daemon has them: these describe the daemon's own parent (an ssh
/// session, the keeper, a debugger), and a shell that inherits `SSH_CONNECTION` believes it is
/// an ssh login and skips the parts of `.bashrc` meant for a local terminal.
pub const NEVER_INHERIT: [&str; 20] = [
    "SSH_CLIENT",
    "SSH_CONNECTION",
    "SSH_TTY",
    "SSH_AUTH_SOCK",
    "LD_PRELOAD",
    "LD_LIBRARY_PATH",
    "LD_INSERT_LIBRARIES",
    "TERM",
    "COLORTERM",
    "TERM_PROGRAM",
    "TERM_PROGRAM_VERSION",
    "FTERM_NOTIFY",
    "FTERM_PORT",
    "FTERM_RUNDIR",
    "FTERM_SHELL",
    "FTERM_HISTORY_BYTES",
    "FTERM_FONT_DIRS",
    "FTERM_HOME",
    "FTERM_TMPDIR",
    "FTERM_EXPORT_TOKENS",
];

pub const SHELL_PREFIXES: [&str; 5] = [
    "/data/data/com.termux/files/usr/bin/",
    "/data/data/com.termux/files/usr/local/bin/",
    "/system/bin/sh",
    "/bin/",
    // On Debian and Ubuntu /bin is a symlink to /usr/bin, so the same file answers to both
    // names. The allowlist is checked against the requested path and the resolved one.
    "/usr/bin/",
];

pub const FONT_MAX_BYTES: u64 = 32 * 1024 * 1024;

pub fn expand_tilde(p: &str) -> PathBuf {
    if let Some(rest) = p.strip_prefix("~/") {
        let home = home();
        return home.join(rest);
    }
    if p == "~" {
        return home();
    }
    PathBuf::from(p)
}

pub fn home() -> PathBuf {
    match std::env::var_os("HOME") {
        Some(h) if !h.is_empty() => PathBuf::from(h),
        _ => PathBuf::from("/"),
    }
}

pub fn rundir() -> PathBuf {
    match std::env::var("FTERM_RUNDIR") {
        Ok(d) if !d.is_empty() => expand_tilde(&d),
        _ => home().join(".fterm"),
    }
}

pub fn ensure_rundir(dir: &Path) -> io::Result<()> {
    fs::create_dir_all(dir)?;
    // A daemon rundir holds the token: it must not be readable by other uids.
    let _ = fs::set_permissions(dir, fs::Permissions::from_mode(0o700));
    Ok(())
}

pub fn token_path(dir: &Path) -> PathBuf {
    dir.join("token")
}

pub fn cache_path(dir: &Path) -> PathBuf {
    dir.join("android.env")
}

pub fn read_token(dir: &Path, create: bool) -> io::Result<Option<String>> {
    let p = token_path(dir);
    match fs::read_to_string(&p) {
        Ok(t) => {
            let t = t.trim().to_string();
            if !t.is_empty() {
                return Ok(Some(t));
            }
        }
        Err(e) if e.kind() == io::ErrorKind::NotFound => {
            if !create {
                return Ok(None);
            }
        }
        Err(e) => return Err(e),
    }
    if !create {
        return Ok(None);
    }
    let t = random_token();
    write_private(&p, &t)?;
    Ok(Some(t))
}

fn random_token() -> String {
    // 24 bytes of /dev/urandom, hex - the same shape the first (Python) daemon wrote, so an app that
    // has a token saved keeps working if the daemon is swapped underneath it.
    let mut buf = [0u8; 24];
    if let Ok(mut f) = fs::File::open("/dev/urandom") {
        use std::io::Read;
        let _ = f.read_exact(&mut buf);
    } else {
        // No urandom is not a thing on Android or Linux; fall back rather than panic.
        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.as_nanos())
            .unwrap_or(0);
        for (i, b) in buf.iter_mut().enumerate() {
            *b = ((now.wrapping_mul(i as u128 + 1) >> 3) & 0xff) as u8;
        }
    }
    buf.iter().map(|b| format!("{b:02x}")).collect()
}

/// Write a file only the owner can read, atomically.
pub fn write_private(path: &Path, contents: &str) -> io::Result<()> {
    let mut tmp_os = std::ffi::OsString::from(path.as_os_str());
    tmp_os.push(".tmp");
    let tmp = PathBuf::from(tmp_os);
    {
        let mut f = fs::OpenOptions::new()
            .write(true)
            .create(true)
            .truncate(true)
            .mode(0o600)
            .open(&tmp)?;
        f.write_all(contents.as_bytes())?;
    }
    let _ = fs::set_permissions(&tmp, fs::Permissions::from_mode(0o600));
    fs::rename(&tmp, path)
}

pub fn write_token_file(dir: &Path, name: &str, value: &str) -> io::Result<()> {
    write_private(&dir.join(name), value)
}

/// One process's environment, or `None` if it cannot be read (another uid, already gone).
pub fn read_environ(pid: &str) -> Option<HashMap<String, String>> {
    let raw = fs::read(format!("/proc/{pid}/environ")).ok()?;
    let mut env = HashMap::new();
    for item in raw.split(|b| *b == 0) {
        if let Some(eq) = item.iter().position(|b| *b == b'=') {
            if eq == 0 {
                continue;
            }
            let k = String::from_utf8_lossy(&item[..eq]).into_owned();
            let v = String::from_utf8_lossy(&item[eq + 1..]).into_owned();
            env.insert(k, v);
        }
    }
    Some(env)
}

/// A boot class path from another device build lists jars that no longer exist.
pub fn bootclasspath_valid(value: &str) -> bool {
    if value.is_empty() {
        return false;
    }
    let parts: Vec<&str> = value.split(':').filter(|p| !p.is_empty()).collect();
    if parts.is_empty() {
        return false;
    }
    parts.iter().all(|p| Path::new(p).exists())
}

pub fn read_cache(dir: &Path) -> HashMap<String, String> {
    let mut env = HashMap::new();
    let Ok(text) = fs::read_to_string(cache_path(dir)) else {
        return env;
    };
    for line in text.lines() {
        let line = line.trim();
        if let Some(rest) = line.strip_prefix("export ") {
            if let Some((k, v)) = rest.split_once('=') {
                env.insert(k.to_string(), unquote(v));
            }
        }
    }
    env
}

/// Undo one `export K='...'` value, including the `'\''` run that a single quote becomes.
///
/// The first (Python) daemon trimmed the outer quotes only, which is enough for a class path (they have no
/// quotes in them) but loses a value with one. Reading back exactly what was written is worth
/// the twenty lines: `ftermd env` and the cache are two views of the same fact.
fn unquote(v: &str) -> String {
    let v = v.trim();
    let mut out = String::with_capacity(v.len());
    let mut it = v.chars();
    while let Some(c) = it.next() {
        match c {
            '\'' => {
                for c2 in it.by_ref() {
                    if c2 == '\'' {
                        break;
                    }
                    out.push(c2);
                }
            }
            '"' => {
                for c2 in it.by_ref() {
                    if c2 == '"' {
                        break;
                    }
                    out.push(c2);
                }
            }
            '\\' => {
                if let Some(n) = it.next() {
                    out.push(n);
                }
            }
            other => out.push(other),
        }
    }
    out
}

/// Write the ART variables as shell syntax, so rish and other wrappers can source it.
pub fn write_cache(dir: &Path, env: &HashMap<String, String>) {
    if ensure_rundir(dir).is_err() {
        return;
    }
    let mut text = String::from("# Generated by ftermd. Source this to make app_process/am work.\n");
    for k in ANDROID_ENV {
        if let Some(v) = env.get(k) {
            // POSIX single-quote escaping: ' -> '\''
            let escaped = v.replace('\'', "'\\''");
            text.push_str(&format!("export {k}='{escaped}'\n"));
        }
    }
    text.push_str("export CLASSPATH=\n");
    let _ = write_private(&cache_path(dir), &text);
}

/// Find the ART variables by asking every process that has them.
///
/// The daemon may be started by the keeper (which inherited the app shell's environment), by
/// ssh, by cron, or by hand; only the first has `BOOTCLASSPATH`, and there is no system
/// service to ask - `/system/etc/classpaths/bootclasspath.pb` lists only the framework jars and
/// omits the ART core jars, so a shell built from it still dies. Reading a live process is the
/// only accurate source. The result is cached, so the scan happens once.
pub fn discover_android_env(dir: &Path) -> HashMap<String, String> {
    let own = read_environ("self").unwrap_or_default();
    if bootclasspath_valid(own.get("BOOTCLASSPATH").map(|s| s.as_str()).unwrap_or("")) {
        let found = take_android(&own);
        write_cache(dir, &found);
        return found;
    }
    let cached = read_cache(dir);
    if bootclasspath_valid(cached.get("BOOTCLASSPATH").map(|s| s.as_str()).unwrap_or("")) {
        return cached;
    }
    let mut pids: Vec<u32> = Vec::new();
    if let Ok(rd) = fs::read_dir("/proc") {
        for e in rd.flatten() {
            if let Some(name) = e.file_name().to_str() {
                if let Ok(pid) = name.parse::<u32>() {
                    pids.push(pid);
                }
            }
        }
    }
    // Our own pid first, then the rest in order; the app's shell is usually near the end.
    let me = std::process::id();
    pids.sort_by_key(|p| (*p != me, *p));
    for pid in pids {
        if let Some(env) = read_environ(&pid.to_string()) {
            if bootclasspath_valid(env.get("BOOTCLASSPATH").map(|s| s.as_str()).unwrap_or("")) {
                let found = take_android(&env);
                write_cache(dir, &found);
                return found;
            }
        }
    }
    HashMap::new()
}

fn take_android(env: &HashMap<String, String>) -> HashMap<String, String> {
    let mut out = HashMap::new();
    for k in ANDROID_ENV {
        if let Some(v) = env.get(k) {
            out.insert(k.to_string(), v.clone());
        }
    }
    out
}

pub fn pid_alive(pid: i32) -> bool {
    pid > 0 && unsafe { libc::kill(pid, 0) == 0 }
}

pub fn default_shell() -> String {
    if let Ok(s) = std::env::var("FTERM_SHELL") {
        if !s.is_empty() {
            return s;
        }
    }
    for cand in [
        "/data/data/com.termux/files/usr/bin/bash",
        "/data/data/com.termux/files/usr/bin/sh",
        "/bin/bash",
        "/bin/sh",
    ] {
        if access_x(cand) {
            return cand.to_string();
        }
    }
    "/system/bin/sh".to_string()
}

pub fn access_x(path: &str) -> bool {
    use std::ffi::CString;
    let Ok(c) = CString::new(path) else {
        return false;
    };
    unsafe { libc::access(c.as_ptr(), libc::X_OK) == 0 }
}

fn access_rx(path: &str) -> bool {
    use std::ffi::CString;
    let Ok(c) = CString::new(path) else {
        return false;
    };
    unsafe { libc::access(c.as_ptr(), libc::R_OK | libc::X_OK) == 0 }
}

/// The shell to exec: the requested one if its real path is allowed, else `None`.
///
/// The app may name a shell; the daemon is the one that runs it, so the daemon decides whether
/// that is allowed. Without this the app could ask for `/system/bin/anything`, which is a
/// privilege the terminal does not need.
pub fn resolve_shell(requested: &str) -> Option<String> {
    if requested.is_empty() {
        return None;
    }
    let real = fs::canonicalize(requested).unwrap_or_else(|_| PathBuf::from(requested));
    if !real.is_absolute() {
        return None;
    }
    let real_str = real.to_string_lossy().into_owned();
    // `..` is collapsed before the check, so a traversal out of an allowed prefix is refused
    // even when the file it lands on would have been allowed.
    let asked = normalise(requested);
    let allowed = |p: &str| {
        SHELL_PREFIXES
            .iter()
            .any(|pre| p == *pre || p.starts_with(pre))
    };
    if !(allowed(&real_str) || allowed(&asked)) {
        return None;
    }
    if !(real.is_file() && access_x(&real_str)) {
        return None;
    }
    Some(real_str)
}

/// Collapse `.` and `..` without touching the filesystem.
fn normalise(p: &str) -> String {
    let mut parts: Vec<&str> = Vec::new();
    for seg in p.split('/') {
        match seg {
            "" | "." => {}
            ".." => {
                parts.pop();
            }
            s => parts.push(s),
        }
    }
    format!("/{}", parts.join("/"))
}

/// The directory to start in: the requested one if it exists, else the home directory.
pub fn resolve_cwd(requested: &str) -> PathBuf {
    if requested.is_empty() {
        return home();
    }
    let expanded = expand_tilde(requested);
    let abs = if expanded.is_absolute() {
        expanded
    } else {
        std::env::current_dir()
            .unwrap_or_else(|_| home())
            .join(expanded)
    };
    let s = abs.to_string_lossy().into_owned();
    if abs.is_dir() && access_rx(&s) {
        abs
    } else {
        home()
    }
}

/// The environment for a session's shell.
pub fn shell_env(dir: &Path) -> HashMap<String, String> {
    let mut env: HashMap<String, String> = std::env::vars()
        .filter(|(k, _)| !NEVER_INHERIT.contains(&k.as_str()))
        .collect();
    for k in TERMUX_ENV {
        if let Ok(v) = std::env::var(k) {
            env.insert(k.to_string(), v);
        }
    }
    for (k, v) in discover_android_env(dir) {
        env.insert(k, v);
    }
    // A pid borrowed from another process is meaningless once that process is gone.
    if let Some(pid) = env.get("TERMUX_APP_PID").and_then(|p| p.parse::<i32>().ok()) {
        if !pid_alive(pid) {
            env.remove("TERMUX_APP_PID");
        }
    }
    env.insert("HOME".into(), home().to_string_lossy().into_owned());
    env.insert("PWD".into(), home().to_string_lossy().into_owned());
    env.insert(
        "PATH".into(),
        std::env::var("PATH").unwrap_or_else(|_| {
            "/data/data/com.termux/files/usr/bin:/system/bin:/usr/bin:/bin".to_string()
        }),
    );
    env.insert(
        "PREFIX".into(),
        std::env::var("PREFIX").unwrap_or_else(|_| "/data/data/com.termux/files/usr".to_string()),
    );
    env.insert(
        "TMPDIR".into(),
        std::env::var("TMPDIR").unwrap_or_else(|_| "/data/data/com.termux/files/usr/tmp".to_string()),
    );
    env.insert(
        "LANG".into(),
        std::env::var("LANG").unwrap_or_else(|_| "en_US.UTF-8".to_string()),
    );
    env.insert("SHELL".into(), default_shell());
    env.insert("TERM".into(), "xterm-256color".into());
    env.insert("COLORTERM".into(), "truecolor".into());
    env.insert("TERM_PROGRAM".into(), "fterm".into());
    env.insert("TERM_PROGRAM_VERSION".into(), "0.2.0".into());
    // Programs that can notify a terminal check this. fterm parses OSC 9 and OSC 777 itself,
    // which is what lets a tool raise a real Android notification with no helper process.
    env.insert("FTERM_NOTIFY".into(), "1".into());
    env
}

#[cfg(test)]
mod tests {
    use super::*;

    fn tmpdir(tag: &str) -> PathBuf {
        let d = std::env::temp_dir().join(format!(
            "ftermd-env-{}-{}-{}",
            tag,
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
    fn the_art_variables_are_on_the_list() {
        for name in [
            "BOOTCLASSPATH",
            "DEX2OATBOOTCLASSPATH",
            "ANDROID_ART_ROOT",
            "ANDROID_I18N_ROOT",
            "ANDROID_TZDATA_ROOT",
        ] {
            assert!(ANDROID_ENV.contains(&name), "{name} missing");
        }
    }

    #[test]
    fn the_parents_identity_is_dropped() {
        for name in ["SSH_CONNECTION", "SSH_TTY", "LD_PRELOAD", "TERM", "TERM_PROGRAM"] {
            assert!(NEVER_INHERIT.contains(&name), "{name} not filtered");
        }
    }

    #[test]
    fn a_boot_class_path_needs_its_jars() {
        assert!(!bootclasspath_valid(""));
        assert!(!bootclasspath_valid("/nonexistent/a.jar"));
        let real = std::env::current_exe().unwrap();
        assert!(bootclasspath_valid(&real.to_string_lossy()));
        assert!(!bootclasspath_valid(&format!("{}:/nonexistent/x.jar", real.display())));
    }

    #[test]
    fn the_cache_round_trips_including_a_quote() {
        let dir = tmpdir("cache");
        let mut env = HashMap::new();
        let real = std::env::current_exe().unwrap();
        let real_s = real.to_string_lossy().into_owned();
        env.insert("BOOTCLASSPATH".to_string(), real_s.clone());
        env.insert("ANDROID_DATA".to_string(), "/data".to_string());
        env.insert("ANDROID_ART_ROOT".to_string(), "/apex/it's".to_string());
        write_cache(&dir, &env);
        let back = read_cache(&dir);
        assert_eq!(back.get("BOOTCLASSPATH"), Some(&real_s));
        assert_eq!(back.get("ANDROID_DATA").map(|s| s.as_str()), Some("/data"));
        assert_eq!(back.get("ANDROID_ART_ROOT").map(|s| s.as_str()), Some("/apex/it's"));
        let mode = fs::metadata(cache_path(&dir)).unwrap().permissions().mode();
        assert_eq!(mode & 0o777, 0o600, "{mode:o}");
        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn a_stale_cache_is_not_handed_back() {
        let dir = tmpdir("stale");
        let mut stale = HashMap::new();
        stale.insert(
            "BOOTCLASSPATH".to_string(),
            "/nonexistent/after-update/core-oj.jar".to_string(),
        );
        write_cache(&dir, &stale);
        let found = discover_android_env(&dir);
        let bc = found.get("BOOTCLASSPATH").map(|s| s.as_str()).unwrap_or("");
        assert_ne!(bc, "/nonexistent/after-update/core-oj.jar");
        // Whatever it found (nothing, on a machine that is not a phone) must be usable.
        assert!(bc.is_empty() || bootclasspath_valid(bc));
        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn shell_env_carries_the_terminal_identity() {
        let _g = crate::testutil::env_lock();
        let dir = tmpdir("shell");
        std::env::set_var("SSH_CONNECTION", "1.2.3.4 5 6.7.8.9 22");
        std::env::set_var("TERM", "dumb");
        let env = shell_env(&dir);
        assert!(!env.contains_key("SSH_CONNECTION"));
        assert_eq!(env.get("TERM").map(|s| s.as_str()), Some("xterm-256color"));
        assert_eq!(env.get("TERM_PROGRAM").map(|s| s.as_str()), Some("fterm"));
        assert_eq!(env.get("COLORTERM").map(|s| s.as_str()), Some("truecolor"));
        assert_eq!(env.get("FTERM_NOTIFY").map(|s| s.as_str()), Some("1"));
        assert!(env.get("HOME").map(|h| !h.is_empty()).unwrap_or(false));
        assert!(env.get("PATH").is_some());
        std::env::remove_var("SSH_CONNECTION");
        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn a_dead_termux_app_pid_is_dropped_and_a_live_one_kept() {
        let _g = crate::testutil::env_lock();
        let dir = tmpdir("pid");
        std::env::set_var("TERMUX_APP_PID", "999999");
        assert!(!shell_env(&dir).contains_key("TERMUX_APP_PID"));
        let me = std::process::id().to_string();
        std::env::set_var("TERMUX_APP_PID", &me);
        assert_eq!(shell_env(&dir).get("TERMUX_APP_PID"), Some(&me));
        std::env::remove_var("TERMUX_APP_PID");
        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn the_shell_allowlist_holds() {
        // Whatever shell this machine actually has under /bin is allowed.
        if Path::new("/bin/sh").exists() {
            assert!(resolve_shell("/bin/sh").is_some());
        }
        // /etc/hostname exists and is readable but is not a shell we will exec.
        assert!(resolve_shell("/etc/hostname").is_none());
        assert!(resolve_shell("").is_none());
        assert!(resolve_shell("sh").is_none(), "bare names are not absolute paths");
    }

    #[test]
    fn a_missing_directory_falls_back_to_home() {
        assert_eq!(resolve_cwd("/nonexistent/dir/xyz"), home());
        assert_eq!(resolve_cwd(""), home());
        // An existing directory the process can actually read+enter passes through. Pick one
        // at runtime: /tmp is 0771 shell:shell on this device, so Termux uid fails the check
        // and falls back to home - which is the behaviour under test, not a failure of it.
        if let Ok(cwd) = std::env::current_dir() {
            if cwd.is_dir() && access_rx(cwd.to_string_lossy().as_ref()) {
                assert_eq!(resolve_cwd(cwd.to_string_lossy().as_ref()), cwd);
            }
        }
    }

    #[test]
    fn the_token_is_private_and_stable() {
        let dir = tmpdir("token");
        let a = read_token(&dir, true).unwrap().unwrap();
        assert_eq!(a.len(), 48);
        let b = read_token(&dir, true).unwrap().unwrap();
        assert_eq!(a, b, "the token must not be regenerated on every start");
        let mode = fs::metadata(token_path(&dir)).unwrap().permissions().mode();
        assert_eq!(mode & 0o777, 0o600);
        assert!(read_token(&tmpdir("none"), false).unwrap().is_none());
        let _ = fs::remove_dir_all(&dir);
    }
}
