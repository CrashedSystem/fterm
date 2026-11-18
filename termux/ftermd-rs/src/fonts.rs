//! Fonts the app can pick from, and the one download the daemon does on its behalf.
//!
//! The app has no URL client of its own and no storage a download could land in that it is
//! allowed to open, so the daemon - the side with Termux's reach - fetches the file and hands
//! over a path. `curl` does the transfer: it is what `pkg install curl` gives on Termux and it
//! is already on any Linux runner, and writing an HTTP client with TLS into this binary for one
//! feature would be the larger risk.

use std::fs;
use std::path::{Path, PathBuf};
use std::process::Command;

use crate::env::{expand_tilde, home, FONT_MAX_BYTES};

/// A font is served in chunks: one frame is capped at 1 MiB and a real Iosevka build is larger.
pub const FONT_CHUNK: usize = 256 * 1024;

pub fn font_dirs() -> Vec<PathBuf> {
    if let Ok(env) = std::env::var("FTERM_FONT_DIRS") {
        if !env.is_empty() {
            return env.split(':').filter(|d| !d.is_empty()).map(expand_tilde).collect();
        }
    }
    let home = home();
    let prefix = std::env::var("PREFIX").unwrap_or_else(|_| "/data/data/com.termux/files/usr".into());
    vec![
        home.join(".fonts"),
        home.join(".local/share/fonts"),
        PathBuf::from(prefix).join("share/fonts/TTF"),
        home.join("storage/downloads/fonts"),
        PathBuf::from("/sdcard/Download/fonts"),
    ]
}

/// Every `.ttf`/`.otf` the daemon can see, as (real path, size), deduplicated.
///
/// Deduplicated by real path: `~/storage/downloads/fonts` and `/sdcard/Download/fonts` are the
/// same directory twice, and listing one file twice makes the picker look broken.
pub fn font_scan() -> Vec<(PathBuf, u64)> {
    let mut out: Vec<(PathBuf, u64)> = Vec::new();
    let mut seen: Vec<PathBuf> = Vec::new();
    for dir in font_dirs() {
        let Ok(rd) = fs::read_dir(&dir) else { continue };
        let mut names: Vec<String> = rd
            .flatten()
            .filter_map(|e| e.file_name().to_str().map(|s| s.to_string()))
            .collect();
        names.sort();
        for name in names {
            let path = dir.join(&name);
            if !is_font_path(&path) {
                continue;
            }
            let Ok(real) = fs::canonicalize(&path) else { continue };
            if !real.is_file() || seen.contains(&real) {
                continue;
            }
            let Ok(meta) = fs::metadata(&real) else { continue };
            let size = meta.len();
            if size == 0 || size > FONT_MAX_BYTES {
                continue;
            }
            seen.push(real.clone());
            out.push((real, size));
        }
    }
    out
}

/// Download one font into `~/.fonts` and return its path.
///
/// The rules - http(s) only, `.ttf`/`.otf` only, 32 MB cap - live here so the CLI, the keeper
/// and the socket all accept exactly the same thing.
pub fn fetch_font(url: &str) -> Result<PathBuf, String> {
    let url = url.trim();
    if !(url.starts_with("http://") || url.starts_with("https://")) {
        return Err("http:// 또는 https:// 주소만 받습니다".into());
    }
    let name = url
        .split('?')
        .next()
        .unwrap_or("")
        .trim_end_matches('/')
        .rsplit('/')
        .next()
        .unwrap_or("");
    let low = name.to_ascii_lowercase();
    if !(low.ends_with(".ttf") || low.ends_with(".otf")) {
        return Err(format!("주소가 .ttf/.otf로 끝나지 않습니다: {name}"));
    }
    let name: String = name
        .chars()
        .map(|c| {
            if c.is_ascii_alphanumeric() || c == '.' || c == '_' || c == '-' {
                c
            } else {
                '_'
            }
        })
        .collect();
    let dir = home().join(".fonts");
    fs::create_dir_all(&dir).map_err(|e| format!("~/.fonts 를 만들 수 없습니다: {e}"))?;
    let dest = dir.join(&name);
    let mut tmp_os = std::ffi::OsString::from(dest.as_os_str());
    tmp_os.push(".part");
    let tmp = PathBuf::from(tmp_os);

    let out = Command::new("curl")
        .args([
            "-fsSL",
            "--max-time",
            "30",
            "--max-filesize",
            &FONT_MAX_BYTES.to_string(),
            "-o",
        ])
        .arg(&tmp)
        .arg(url)
        .output();
    let result = match out {
        Ok(o) if o.status.success() => Ok(()),
        Ok(o) => {
            let why = String::from_utf8_lossy(&o.stderr).trim().to_string();
            Err(if why.is_empty() {
                format!("내려받기 실패 (curl)").to_string()
            } else {
                format!("내려받기 실패 (curl): {why}")
            })
        }
        Err(e) => Err(format!("curl 을 실행할 수 없습니다: {e}")),
    };
    if let Err(e) = result {
        let _ = fs::remove_file(&tmp);
        return Err(e);
    }
    let size = fs::metadata(&tmp).map(|m| m.len()).unwrap_or(0);
    if size == 0 {
        let _ = fs::remove_file(&tmp);
        return Err("빈 파일입니다".into());
    }
    if size > FONT_MAX_BYTES {
        let _ = fs::remove_file(&tmp);
        return Err("파일이 너무 큽니다 (32MB 초과)".into());
    }
    fs::rename(&tmp, &dest).map_err(|e| format!("저장 실패: {e}"))?;
    Ok(dest)
}

/// Serve one scanned font. Only a path the scan itself returned is served.
///
/// The request carries a path, so without this check the frame would be a way to read any file
/// the daemon uid can open. Rescanning and matching the real path keeps the surface at "fonts
/// that are already visible in the picker".
pub fn read_font(requested: &str) -> Result<(PathBuf, Vec<u8>), String> {
    let real = fs::canonicalize(requested).unwrap_or_else(|_| PathBuf::from(requested));
    let scan = font_scan();
    let wanted = scan
        .iter()
        .find(|(p, _)| *p == real)
        .map(|(p, _)| p.clone())
        .ok_or_else(|| "font not found".to_string())?;
    let data = fs::read(&wanted).map_err(|e| format!("font unreadable: {e}"))?;
    if data.len() as u64 > FONT_MAX_BYTES {
        return Err("font too large".into());
    }
    Ok((wanted, data))
}

pub fn is_font_path(p: &Path) -> bool {
    let low = p.to_string_lossy().to_ascii_lowercase();
    low.ends_with(".ttf") || low.ends_with(".otf")
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::os::unix::fs::symlink;

    fn tmpdir(tag: &str) -> PathBuf {
        let d = std::env::temp_dir().join(format!(
            "ftermd-fonts-{tag}-{}-{}",
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
    fn the_scan_finds_fonts_and_skips_everything_else() {
        let _g = crate::testutil::env_lock();
        let dir = tmpdir("scan");
        fs::write(dir.join("a.ttf"), b"x").unwrap();
        fs::write(dir.join("b.OTF"), b"xy").unwrap();
        fs::write(dir.join("notes.txt"), b"x").unwrap();
        fs::write(dir.join("empty.ttf"), b"").unwrap();
        std::env::set_var("FTERM_FONT_DIRS", dir.to_string_lossy().to_string());
        let found = font_scan();
        std::env::remove_var("FTERM_FONT_DIRS");
        let names: Vec<String> = found
            .iter()
            .map(|(p, _)| p.file_name().unwrap().to_string_lossy().into_owned())
            .collect();
        assert_eq!(names, vec!["a.ttf".to_string(), "b.OTF".to_string()]);
        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn the_same_file_twice_is_listed_once() {
        let _g = crate::testutil::env_lock();
        let a = tmpdir("dedupe-a");
        let b = tmpdir("dedupe-b");
        fs::write(a.join("f.ttf"), b"xyz").unwrap();
        symlink(a.join("f.ttf"), b.join("f.ttf")).unwrap();
        std::env::set_var(
            "FTERM_FONT_DIRS",
            format!("{}:{}", a.display(), b.display()),
        );
        let found = font_scan();
        std::env::remove_var("FTERM_FONT_DIRS");
        assert_eq!(found.len(), 1);
        let _ = fs::remove_dir_all(&a);
        let _ = fs::remove_dir_all(&b);
    }

    #[test]
    fn only_a_scanned_font_is_served() {
        let _g = crate::testutil::env_lock();
        let dir = tmpdir("read");
        fs::write(dir.join("ok.ttf"), b"fontbytes").unwrap();
        std::env::set_var("FTERM_FONT_DIRS", dir.to_string_lossy().to_string());
        let ok = read_font(&dir.join("ok.ttf").to_string_lossy());
        let outside = read_font("/etc/hostname");
        std::env::remove_var("FTERM_FONT_DIRS");
        let (path, data) = ok.expect("a scanned font is served");
        assert_eq!(data, b"fontbytes");
        assert!(path.ends_with("ok.ttf"));
        assert!(outside.is_err(), "a file outside the scan must not be served");
        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn the_url_rules_are_the_same_as_the_cli() {
        assert!(fetch_font("ftp://example.com/a.ttf").is_err());
        assert!(fetch_font("https://example.com/a.zip").is_err());
        assert!(fetch_font("").is_err());
        // The message is what the app shows, so it names the rule rather than the failure.
        let e = fetch_font("https://example.com/a.zip").unwrap_err();
        assert!(e.contains(".ttf/.otf"), "{e}");
    }

    #[test]
    fn only_font_extensions_are_fonts() {
        assert!(is_font_path(Path::new("/x/a.ttf")));
        assert!(is_font_path(Path::new("/x/a.OTF")));
        assert!(!is_font_path(Path::new("/x/a.txt")));
    }
}
