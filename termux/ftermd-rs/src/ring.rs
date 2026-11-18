//! The per-session output ring: the recent bytes a reattaching client is replaying.
//!
//! `FTERM_HISTORY_BYTES` (256 KiB by default) of raw output, trimmed at a UTF-8 sequence
//! boundary rather than at an arbitrary byte - otherwise the first character restored to the
//! emulator is a stray continuation byte and the screen opens with a replacement glyph.

pub struct Ring {
    limit: usize,
    buf: Vec<u8>,
}

impl Ring {
    pub fn new(limit: usize) -> Self {
        Ring {
            limit,
            buf: Vec::new(),
        }
    }

    pub fn add(&mut self, data: &[u8]) {
        self.buf.extend_from_slice(data);
        if self.buf.len() > self.limit {
            let mut cut = self.buf.len() - self.limit;
            while cut < self.buf.len() && (self.buf[cut] & 0xC0) == 0x80 {
                cut += 1;
            }
            self.buf.drain(..cut);
        }
    }

    pub fn get(&self) -> &[u8] {
        &self.buf
    }

    pub fn len(&self) -> usize {
        self.buf.len()
    }

    pub fn is_empty(&self) -> bool {
        self.buf.is_empty()
    }
}

#[cfg(test)]
mod tests {
    use super::Ring;

    #[test]
    fn output_below_the_limit_is_kept_whole() {
        let mut r = Ring::new(16);
        r.add(b"hello");
        assert_eq!(r.get(), b"hello");
    }

    #[test]
    fn the_ring_keeps_the_newest_bytes() {
        let mut r = Ring::new(8);
        r.add(b"0123456789abcdef");
        assert_eq!(r.get(), b"89abcdef");
        assert_eq!(r.len(), 8);
    }

    #[test]
    fn trimming_never_splits_a_character() {
        // "한" is three bytes; a ring of 4 would otherwise start in the middle of one.
        let text = "가나다라".as_bytes(); // 12 bytes
        let mut r = Ring::new(5);
        r.add(text);
        // The cut lands inside a character, so it is moved forward to the next boundary.
        let restored = std::str::from_utf8(r.get());
        assert!(restored.is_ok(), "ring begins mid-character: {:?}", r.get());
        assert!(restored.unwrap().chars().all(|c| "가나다라".contains(c)));
    }

    #[test]
    fn a_huge_write_does_not_grow_past_the_limit() {
        let mut r = Ring::new(1024);
        for _ in 0..100 {
            r.add(&[b'x'; 4096]);
        }
        assert_eq!(r.len(), 1024);
    }

    #[test]
    fn exactly_the_limit_is_kept() {
        let mut r = Ring::new(4);
        r.add(b"abcd");
        assert_eq!(r.get(), b"abcd");
        r.add(b"e");
        assert_eq!(r.get(), b"bcde");
    }
}
