//! The wire protocol, byte for byte the one `termux/ftermd` speaks.
//!
//! Nothing here is new: the app is the other end, and it cannot tell which daemon it is
//! talking to. A frame is `<u8 type><u32 little-endian length><payload>`; frames larger than
//! `MAX_FRAME` are refused rather than buffered, which is what keeps a broken client from
//! growing the daemon's memory with one length field.

pub const PROTO_VERSION: u8 = 1;
pub const MAX_FRAME: u32 = 1 << 20;

// client -> daemon
pub const C_HELLO: u8 = 0x01;
pub const C_SYNC: u8 = 0x09;
pub const C_OPEN: u8 = 0x10;
pub const C_CLOSE: u8 = 0x11;
pub const C_INPUT: u8 = 0x12;
pub const C_RESIZE: u8 = 0x13;
pub const C_PING: u8 = 0x18;
pub const C_FONT_LIST: u8 = 0x1A;
pub const C_FONT_GET: u8 = 0x1B;
pub const C_FONT_FETCH: u8 = 0x1C;

// daemon -> client
pub const S_HELLO_OK: u8 = 0x80;
pub const S_OPENED: u8 = 0x90;
pub const S_OUTPUT: u8 = 0x91;
pub const S_EXIT: u8 = 0x92;
pub const S_REPLAY: u8 = 0x93;
pub const S_PONG: u8 = 0x98;
pub const S_FONT_LIST: u8 = 0x9A;
pub const S_FONT_DATA: u8 = 0x9B;
pub const S_FONT_FETCHED: u8 = 0x9C;
pub const S_SYNCED: u8 = 0x9E;
pub const S_ERROR: u8 = 0x9F;

// Both of these exist for parity with `termux/ftermd`, which also defines them without ever
// sending them. The app has the same table; a constant missing here would be a silent gap.
#[allow(dead_code)]
pub const E_BAD_MAGIC: u8 = 1;
pub const E_BAD_PROTO: u8 = 2;
pub const E_BAD_TOKEN: u8 = 3;
pub const E_BAD_FRAME: u8 = 4;
#[allow(dead_code)]
pub const E_NO_SESSION: u8 = 5;
pub const E_SPAWN: u8 = 6;
pub const E_TOO_MANY: u8 = 7;

pub const MAX_SESSIONS: usize = 16;

pub fn frame(typ: u8, payload: &[u8]) -> Vec<u8> {
    let mut out = Vec::with_capacity(5 + payload.len());
    out.push(typ);
    out.extend_from_slice(&(payload.len() as u32).to_le_bytes());
    out.extend_from_slice(payload);
    out
}

pub fn frame2(typ: u8, a: &[u8], b: &[u8]) -> Vec<u8> {
    // Most daemon frames are a small header plus a body; building them through this saves the
    // extra copy that `[a, b].concat()` would make.
    let mut out = Vec::with_capacity(5 + a.len() + b.len());
    out.push(typ);
    out.extend_from_slice(&((a.len() + b.len()) as u32).to_le_bytes());
    out.extend_from_slice(a);
    out.extend_from_slice(b);
    out
}

pub fn u16b(v: u16) -> [u8; 2] {
    v.to_le_bytes()
}

pub fn u32b(v: u32) -> [u8; 4] {
    v.to_le_bytes()
}

pub fn get_u16(b: &[u8], off: usize) -> Option<u16> {
    b.get(off..off + 2)
        .map(|s| u16::from_le_bytes([s[0], s[1]]))
}

pub fn get_u32(b: &[u8], off: usize) -> Option<u32> {
    b.get(off..off + 4)
        .map(|s| u32::from_le_bytes([s[0], s[1], s[2], s[3]]))
}

/// A framing error that the daemon answers with `S_ERROR` and a disconnect.
#[derive(Debug, PartialEq, Eq)]
pub enum FrameError {
    TooLarge(u32),
}

/// Incremental frame decoder.
///
/// Reads arrive in whatever chunks TCP hands over, so a frame can be split anywhere - in the
/// length field, in the payload, in the five-byte header. This buffers only until one whole
/// frame is present and never interprets a length it has not validated.
#[derive(Default)]
pub struct FrameReader {
    buf: Vec<u8>,
}

impl FrameReader {
    pub fn new() -> Self {
        Self { buf: Vec::new() }
    }

    pub fn push(&mut self, data: &[u8]) {
        self.buf.extend_from_slice(data);
    }

    pub fn buffered(&self) -> usize {
        self.buf.len()
    }

    /// The next complete frame, if one has arrived.
    pub fn next_frame(&mut self) -> Result<Option<(u8, Vec<u8>)>, FrameError> {
        if self.buf.len() < 5 {
            return Ok(None);
        }
        let typ = self.buf[0];
        let len = u32::from_le_bytes([self.buf[1], self.buf[2], self.buf[3], self.buf[4]]);
        if len > MAX_FRAME {
            return Err(FrameError::TooLarge(len));
        }
        let total = 5 + len as usize;
        if self.buf.len() < total {
            return Ok(None);
        }
        let payload = self.buf[5..total].to_vec();
        self.buf.drain(..total);
        Ok(Some((typ, payload)))
    }
}

/// The two optional strings at the end of an OPEN: (shell, cwd), either possibly empty.
///
/// Both are optional together with the whole tail, so a truncation means "use the defaults"
/// rather than an error: an older app sends six bytes and must still open a session.
pub fn parse_open_strings(p: &[u8], offset: usize) -> (String, String) {
    let mut out = [String::new(), String::new()];
    let mut off = Some(offset);
    for slot in 0..2 {
        let Some(o) = off else { break };
        let Some(ln) = get_u16(p, o) else { break };
        let start = o + 2;
        let end = start + ln as usize;
        if end > p.len() {
            break;
        }
        out[slot] = String::from_utf8_lossy(&p[start..end]).into_owned();
        off = Some(end);
    }
    (out[0].clone(), out[1].clone())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_frame_round_trips() {
        let f = frame(S_OUTPUT, b"hello");
        assert_eq!(f[0], S_OUTPUT);
        assert_eq!(&f[1..5], &5u32.to_le_bytes());
        let mut r = FrameReader::new();
        r.push(&f);
        assert_eq!(r.next_frame().unwrap(), Some((S_OUTPUT, b"hello".to_vec())));
        assert_eq!(r.next_frame().unwrap(), None);
    }

    #[test]
    fn a_frame_split_anywhere_still_arrives() {
        let f = frame2(C_INPUT, &[7, 0], b"typed bytes");
        for cut in 0..f.len() {
            let mut r = FrameReader::new();
            r.push(&f[..cut]);
            assert_eq!(r.next_frame().unwrap(), None, "cut {cut} must not yield yet");
            r.push(&f[cut..]);
            assert_eq!(
                r.next_frame().unwrap(),
                Some((C_INPUT, [7u8, 0].iter().chain(b"typed bytes").copied().collect())),
                "cut {cut}"
            );
        }
    }

    #[test]
    fn two_frames_in_one_read() {
        let mut both = frame(S_PONG, b"a");
        both.extend_from_slice(&frame(S_PONG, b"b"));
        let mut r = FrameReader::new();
        r.push(&both);
        assert_eq!(r.next_frame().unwrap(), Some((S_PONG, b"a".to_vec())));
        assert_eq!(r.next_frame().unwrap(), Some((S_PONG, b"b".to_vec())));
        assert_eq!(r.next_frame().unwrap(), None);
    }

    #[test]
    fn an_oversized_length_is_refused_before_it_is_buffered() {
        let mut r = FrameReader::new();
        r.push(&[C_INPUT, 0xff, 0xff, 0xff, 0xff]);
        assert_eq!(r.next_frame(), Err(FrameError::TooLarge(u32::MAX)));
    }

    #[test]
    fn an_unauthenticated_garbage_length_does_not_allocate() {
        // 1 MiB - 1 is legal; the reader must wait for it, not fake one.
        let mut r = FrameReader::new();
        r.push(&[C_INPUT]);
        r.push(&(MAX_FRAME - 1).to_le_bytes());
        assert_eq!(r.next_frame().unwrap(), None);
        assert_eq!(r.buffered(), 5);
    }

    #[test]
    fn open_strings_are_optional() {
        let mut p = vec![1, 0, 80, 0, 24, 0];
        assert_eq!(parse_open_strings(&p, 6), (String::new(), String::new()));
        p.extend_from_slice(&5u16.to_le_bytes());
        p.extend_from_slice(b"/bin/");
        p.extend_from_slice(&4u16.to_le_bytes());
        p.extend_from_slice(b"/tmp");
        assert_eq!(parse_open_strings(&p, 6), ("/bin/".to_string(), "/tmp".to_string()));
    }

    #[test]
    fn a_truncated_open_string_falls_back_to_defaults() {
        let mut p = vec![1, 0, 80, 0, 24, 0];
        p.extend_from_slice(&200u16.to_le_bytes());
        p.extend_from_slice(b"short");
        assert_eq!(parse_open_strings(&p, 6), (String::new(), String::new()));
    }
}
