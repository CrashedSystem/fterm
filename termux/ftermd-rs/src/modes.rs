//! The session's terminal-mode state, ported from `Modes` in `termux/ftermd`.
//!
//! A program declares "send me mouse events", "tell me about focus changes" once at startup
//! with DECSET and never repeats it. When the app's process is recreated the emulator is new
//! and only the ring is replayed, so a declaration that has been evicted is gone: the shell
//! still believes mouse reporting is on while the emulator has it off, swipes scroll the
//! history and taps never reach the program. So the daemon tracks the last state of those
//! declarations per session and hands them to a reattaching client *ahead of the ring*.
//!
//! Two boundaries matter and both are pinned by the tests below: a sequence split across two
//! reads (real on a pty, where a write is not a read), and a parameter run that never
//! terminates (broken output, not a mode - the carry must not grow with the stream).

use std::collections::HashMap;

/// Restore order. The screen-clearing alt screen (47/1047/1049) has to be last, so the ring
/// that follows it is drawn on top of the screen the program expects.
pub const RESTORE_ORDER: [u32; 17] = [
    7, 6, 25, 5, 1, 66, 2004, 1004, 1000, 1002, 1003, 1005, 1006, 1015, 47, 1047, 1049,
];

/// Modes that mean a full-screen program owns the display and must be asked to repaint.
pub const INTERACTIVE: [u32; 6] = [47, 1047, 1049, 1000, 1002, 1003];

/// Longest unterminated parameter run worth holding for the next read. A real DECSET is short
/// (`ESC[?1006;1000h`); anything longer that has not ended is broken output, and holding it
/// makes the carry grow with the stream (measured: 600 bytes and climbing).
pub const MAX_PENDING: usize = 64;

const SEQ: &[u8] = b"\x1b[?";

pub struct Modes {
    state: HashMap<u32, bool>,
    carry: Vec<u8>,
}

impl Default for Modes {
    fn default() -> Self {
        Self::new()
    }
}

impl Modes {
    pub fn new() -> Self {
        Modes {
            state: HashMap::new(),
            carry: Vec::new(),
        }
    }

    pub fn feed(&mut self, data: &[u8]) {
        let mut buf = std::mem::take(&mut self.carry);
        buf.extend_from_slice(data);
        let n = buf.len();
        let mut i = 0usize;
        loop {
            let j = match find_from(&buf, i) {
                Some(j) => j,
                None => {
                    // A cut ESC or ESC[ continues in the next chunk.
                    self.carry.clear();
                    for k in [2usize, 1] {
                        if buf.len() >= k && &buf[buf.len() - k..] == &SEQ[..k] {
                            self.carry = buf[buf.len() - k..].to_vec();
                            break;
                        }
                    }
                    return;
                }
            };
            let start = j + 3;
            let mut k = start;
            while k < n && (0x30..=0x3f).contains(&buf[k]) {
                k += 1;
            }
            if k >= n {
                // Not finished yet. Hold it for the next chunk, unless it is already too long
                // to be a declaration - then those bytes were never a mode.
                let tail = &buf[j..];
                self.carry.clear();
                if tail.len() <= MAX_PENDING {
                    self.carry = tail.to_vec();
                }
                return;
            }
            let fin = buf[k];
            if fin == b'h' || fin == b'l' {
                for raw in buf[start..k].split(|b| *b == b';') {
                    let p = match param(raw) {
                        Some(p) => p,
                        None => continue,
                    };
                    if RESTORE_ORDER.contains(&p) {
                        self.state.insert(p, fin == b'h');
                    }
                }
            }
            i = k + 1;
        }
    }

    /// The bytes to send ahead of the ring: only the modes still on.
    pub fn restore(&self) -> Vec<u8> {
        let mut out = Vec::new();
        for p in RESTORE_ORDER {
            if self.state.get(&p).copied().unwrap_or(false) {
                out.extend_from_slice(b"\x1b[?");
                out.extend_from_slice(p.to_string().as_bytes());
                out.push(b'h');
            }
        }
        out
    }

    pub fn interactive(&self) -> bool {
        INTERACTIVE
            .iter()
            .any(|p| self.state.get(p).copied().unwrap_or(false))
    }

    #[cfg(test)]
    pub fn carry(&self) -> &[u8] {
        &self.carry
    }

    #[cfg(test)]
    pub fn state(&self) -> &HashMap<u32, bool> {
        &self.state
    }
}

fn find_from(buf: &[u8], from: usize) -> Option<usize> {
    if from > buf.len() {
        return None;
    }
    buf[from..]
        .windows(SEQ.len())
        .position(|w| w == SEQ)
        .map(|p| p + from)
}

fn param(raw: &[u8]) -> Option<u32> {
    if raw.is_empty() {
        return Some(0);
    }
    let s = std::str::from_utf8(raw).ok()?;
    s.parse::<u32>().ok()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn restore_after(chunks: &[&[u8]]) -> Vec<u8> {
        let mut m = Modes::new();
        for c in chunks {
            m.feed(c);
        }
        m.restore()
    }

    // 1. A plain declaration is remembered and replayed.
    #[test]
    fn a_decset_is_replayed() {
        let mut m = Modes::new();
        m.feed(b"\x1b[?1000h\x1b[?1006h");
        assert_eq!(m.restore(), b"\x1b[?1000h\x1b[?1006h");
        assert!(m.interactive());
    }

    // 2. A reset is honoured and not replayed: the program said it wants no events.
    #[test]
    fn a_decrst_is_not_replayed() {
        let mut m = Modes::new();
        m.feed(b"\x1b[?1000h\x1b[?1006h");
        m.feed(b"\x1b[?1000l");
        assert_eq!(m.restore(), b"\x1b[?1006h");
    }

    // 3. Split anywhere: every cut must give the same answer as the whole at once.
    #[test]
    fn every_split_matches_the_whole() {
        let seq = b"\x1b[?1000;1006;1049h";
        for cut in 1..seq.len() {
            assert_eq!(
                restore_after(&[&seq[..]]),
                restore_after(&[&seq[..cut], &seq[cut..]]),
                "split at {cut}"
            );
        }
    }

    // 4. Combined parameters are all applied.
    #[test]
    fn a_combined_decset_sets_every_parameter() {
        let mut m = Modes::new();
        m.feed(b"\x1b[?1000;1006;1049h");
        assert_eq!(m.state().len(), 3);
        assert_eq!(m.state().get(&1000), Some(&true));
        assert_eq!(m.state().get(&1006), Some(&true));
        assert_eq!(m.state().get(&1049), Some(&true));
    }

    // 5. Unrelated private sequences are ignored rather than guessed at.
    #[test]
    fn an_untracked_mode_stays_out() {
        let mut m = Modes::new();
        m.feed(b"\x1b[?1234h\x1b[?2026h");
        assert!(m.state().is_empty());
        assert_eq!(m.restore(), b"");
    }

    // 6. Bracketed paste is worth keeping: a reattached client must not paste a shell command
    //    without the guards the program turned on.
    #[test]
    fn bracketed_paste_is_restored() {
        assert_eq!(restore_after(&[b"\x1b[?2004h"]), b"\x1b[?2004h");
    }

    // 7. The alt screen goes last, so nothing after it can clear the screen the ring redraws.
    #[test]
    fn the_alt_screen_is_the_final_item() {
        let r = restore_after(&[b"\x1b[?1049h\x1b[?1000h\x1b[?1006h"]);
        assert!(r.ends_with(b"\x1b[?1049h"), "{r:?}");
        let mouse = r.windows(4).position(|w| w == b"1000").unwrap();
        let alt = r.windows(4).position(|w| w == b"1049").unwrap();
        assert!(mouse < alt);
    }

    // 8. A sequence cut mid-escape between two reads must not be lost.
    #[test]
    fn a_sequence_cut_at_each_byte_survives() {
        for cut in 1..4 {
            assert_eq!(
                restore_after(&[&b"\x1b[?1000h"[..cut], &b"\x1b[?1000h"[cut..]]),
                b"\x1b[?1000h",
                "cut after {cut}"
            );
        }
    }

    // 9. Ordinary text containing a question mark is not a mode.
    #[test]
    fn prose_is_not_a_mode() {
        assert_eq!(restore_after(&[b"what? 1000h is fine\n"]), b"");
    }

    // 10. Non-private CSI is untouched: the tracker only claims ESC[?.
    #[test]
    fn colour_and_clear_are_ignored() {
        assert_eq!(restore_after(&[b"\x1b[1;31m\x1b[2J\x1b[?1000h"]), b"\x1b[?1000h");
    }

    // 11. A late declaration wins: last state, not first.
    #[test]
    fn the_last_declaration_wins() {
        let mut m = Modes::new();
        m.feed(b"\x1b[?1000h\x1b[?1006h");
        m.feed(b"\x1b[?1000l\x1b[?1000h");
        assert_eq!(m.restore(), b"\x1b[?1000h\x1b[?1006h");
    }

    // 12. Large output with no modes must not accumulate the carry.
    #[test]
    fn plain_output_leaves_no_carry() {
        let mut m = Modes::new();
        m.feed(&[b'x'; 100_000]);
        assert!(m.carry().is_empty());
        assert!(m.state().is_empty());
    }

    // 12b. A parameter run that never ends is broken output: the carry stays bounded instead
    //     of growing with the stream.
    #[test]
    fn an_unterminated_parameter_run_does_not_grow_the_carry() {
        let mut m = Modes::new();
        m.feed(b"\x1b[?");
        m.feed(&[b'1'; 99]);
        for _ in 0..20 {
            m.feed(&[b'1'; 500]);
        }
        assert!(m.carry().len() <= MAX_PENDING, "{} bytes held", m.carry().len());
        assert!(m.state().is_empty());
    }

    // 12c. But a genuinely split sequence is still held: MAX_PENDING is far longer than any
    //     real declaration.
    #[test]
    fn a_real_partial_sequence_is_kept() {
        let mut m = Modes::new();
        m.feed(b"\x1b[?1000;10");
        assert_eq!(m.carry(), b"\x1b[?1000;10");
        m.feed(b"06h");
        assert_eq!(m.restore(), b"\x1b[?1000h\x1b[?1006h");
    }

    // 13. The interactive set is exactly the modes that make a program own the screen.
    #[test]
    fn only_screen_owning_modes_are_interactive() {
        let mut m = Modes::new();
        m.feed(b"\x1b[?2004h\x1b[?25l");
        assert!(!m.interactive());
        m.feed(b"\x1b[?1049h");
        assert!(m.interactive());
    }

    // The restore order is a decision, not an accident: it is the order the emulator can
    // absorb without a later byte undoing an earlier one.
    #[test]
    fn restore_order_is_stable() {
        let mut m = Modes::new();
        m.feed(b"\x1b[?1h\x1b[?25h\x1b[?1000h\x1b[?1004h\x1b[?2004h\x1b[?1049h\x1b[?7h");
        let r = String::from_utf8(m.restore()).unwrap();
        let idx = |p: &str| r.find(p).unwrap();
        assert!(idx("?7") < idx("?25"));
        assert!(idx("?2004") < idx("?1004"));
        assert!(idx("?1004") < idx("?1000"));
        assert!(idx("?1000") < idx("?1049"));
    }

    // An empty parameter is DECSET 0, which is not tracked, and must not poison the rest.
    #[test]
    fn empty_parameters_are_zero() {
        let mut m = Modes::new();
        m.feed(b"\x1b[?;1000h");
        assert_eq!(m.state().get(&1000), Some(&true));
        assert_eq!(m.state().len(), 1);
    }
}
