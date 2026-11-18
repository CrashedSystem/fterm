//! A lock for the tests that mutate the process environment.
//!
//! `std::env::set_var` is process-wide and the test harness runs tests on several threads, so
//! two tests that each set `FTERM_FONT_DIRS` would see each other's value. Serialising them is
//! the smallest honest fix; the alternative (passing the environment in everywhere) would
//! change the shape of the daemon to suit its tests.

#![cfg(test)]

use std::sync::{Mutex, MutexGuard, OnceLock};

fn lock() -> &'static Mutex<()> {
    static LOCK: OnceLock<Mutex<()>> = OnceLock::new();
    LOCK.get_or_init(|| Mutex::new(()))
}

pub fn env_lock() -> MutexGuard<'static, ()> {
    // A test that panicked while holding the lock must not fail every later test.
    lock().lock().unwrap_or_else(|e| e.into_inner())
}
