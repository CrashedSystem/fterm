"""Unit checks for the daemon's shell environment, run without a daemon or a pty.

The bug this pins down: `ftermd` built the session environment from a hardcoded list of
eleven names. Termux's own app shell also hands bash BOOTCLASSPATH and the ANDROID_*_ROOT
variables, and `am` - which every termux-api command goes through - is `app_process`
loading an APK's dex. ART builds its boot class path from BOOTCLASSPATH, so without it `am`
exits 0 having printed nothing. `termux-battery-status` then waits forever for a callback
that was never going to arrive. From the outside that looks like "termux-api is broken",
not "the terminal dropped an environment variable". Measured by removing one variable at a
time from a working shell: BOOTCLASSPATH, DEX2OATBOOTCLASSPATH, ANDROID_ART_ROOT,
ANDROID_I18N_ROOT and ANDROID_TZDATA_ROOT each break `am broadcast`.

The second failure is quieter. A discovery function that caches its answer must not cache
a stale one: after a system update the jars in an old BOOTCLASSPATH no longer exist, and
`am` dies again - this time on a device where nothing changed in the terminal.

  python3 test_env.py
"""
import os
import re
import sys
import tempfile

SRC = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "termux", "ftermd")
fails = []


def check(name, cond, detail=""):
    print(("PASS " if cond else "FAIL ") + name + ("" if cond else "  <- " + detail))
    if not cond:
        fails.append(name)


def load(names):
    """Pull top-level names out of the daemon without running its main()."""
    import ast
    src = open(SRC).read()
    tree = ast.parse(src)
    g = {"__name__": "env_under_test", "os": os, "sys": sys}
    found = set()
    for node in tree.body:
        target = None
        if isinstance(node, (ast.FunctionDef, ast.ClassDef)):
            target = node.name
        elif isinstance(node, ast.Assign) and len(node.targets) == 1 \
                and isinstance(node.targets[0], ast.Name):
            target = node.targets[0].id
        if target in names:
            seg = ast.get_source_segment(src, node)
            exec(compile(seg + "\n", "ftermd:%s" % target, "exec"), g)
            found.add(target)
    missing = [n for n in names if n not in found]
    if missing:
        raise SystemExit("%s not found - did it get renamed?" % ", ".join(missing))
    return g


# The daemon reads RUNDIR at import time, so the tests set it before calling write_cache.
TMP = tempfile.mkdtemp(prefix="fterm-env-")
g = load(["ANDROID_ENV", "TERMUX_ENV", "NEVER_INHERIT", "_read_environ",
          "_bootclasspath_valid", "_read_cache", "write_cache", "shell_env",
          "_discover_android_env", "env_cache_path", "ensure_rundir", "default_shell",
          "SHELL"])

# 1. The ART variables are on the list at all. This is the whole bug in one assertion: if
#    BOOTCLASSPATH is not named here, no amount of curation elsewhere can pass it through.
for name in ("BOOTCLASSPATH", "DEX2OATBOOTCLASSPATH", "ANDROID_ART_ROOT",
             "ANDROID_I18N_ROOT", "ANDROID_TZDATA_ROOT"):
    check("ANDROID_ENV names %s" % name, name in g["ANDROID_ENV"])

# 2. Curating means dropping the parent's identity, not the device's identity. An ssh login
#    variable leaking into a local session makes .bashrc think it is remote.
for name in ("SSH_CONNECTION", "SSH_TTY", "LD_PRELOAD", "TERM", "TERM_PROGRAM"):
    check("NEVER_INHERIT drops %s" % name, name in g["NEVER_INHERIT"])

# 3. A boot class path is only usable if its jars exist. This is what makes the cache
#    self-healing after a system update instead of permanently broken.
check("empty path is invalid", not g["_bootclasspath_valid"](""))
check("missing jar is invalid", not g["_bootclasspath_valid"]("/nonexistent/a.jar"))
check("present jar is valid", g["_bootclasspath_valid"]("/system/framework/framework.jar"))

# 4. Caching round-trips, including a value with a single quote, which would break a naive
#    `export K='v'` writer.
fake = {
    "BOOTCLASSPATH": "/system/framework/framework.jar",
    "DEX2OATBOOTCLASSPATH": "/system/framework/framework.jar",
    "ANDROID_ART_ROOT": "/apex/com.android.art",
    "ANDROID_I18N_ROOT": "/apex/com.android.i18n",
    "ANDROID_TZDATA_ROOT": "/apex/com.android.tzdata",
    "ANDROID_DATA": "/data",
    "ANDROID_ROOT": "/system",
}
g["RUNDIR"] = TMP
g["write_cache"](fake)
back = g["_read_cache"]()
check("cache round-trips BOOTCLASSPATH",
      back.get("BOOTCLASSPATH") == fake["BOOTCLASSPATH"], repr(back.get("BOOTCLASSPATH")))
check("cache keeps every key", all(k in back for k in fake), repr(sorted(back)))

st = os.stat(os.path.join(TMP, "android.env"))
check("cache is 0600", (st.st_mode & 0o777) == 0o600, oct(st.st_mode & 0o777))

# 5. A stale cache must not be trusted: after a system update the cached jars are gone.
#    Discovery has to notice and go looking again, not hand back a dead path.
stale = dict(fake, BOOTCLASSPATH="/nonexistent/after-update/core-oj.jar")
g["write_cache"](stale)
found = g["_discover_android_env"]()
check("discovery ignores a stale cache",
      g["_bootclasspath_valid"](found.get("BOOTCLASSPATH", "")),
      repr(found.get("BOOTCLASSPATH", ""))[:80])
check("discovery replaces the stale cache",
      g["_bootclasspath_valid"](g["_read_cache"]().get("BOOTCLASSPATH", "")),
      repr(g["_read_cache"]().get("BOOTCLASSPATH", ""))[:80])

# 6. shell_env keeps the device's identity but not the parent's. Run it with a borrowed
#    environment so the result does not depend on how the tests were launched.
os.environ["SSH_CONNECTION"] = "1.2.3.4 5 6.7.8.9 22"
os.environ["TERM"] = "dumb"
env = g["shell_env"]()
check("shell_env drops the parent's SSH identity", "SSH_CONNECTION" not in env)
check("shell_env sets TERM for the terminal", env.get("TERM") == "xterm-256color", env.get("TERM"))
check("shell_env sets TERM_PROGRAM", env.get("TERM_PROGRAM") == "fterm")
check("shell_env keeps the ART variables",
      g["_bootclasspath_valid"](env.get("BOOTCLASSPATH", "")),
      repr(env.get("BOOTCLASSPATH", ""))[:80])
check("shell_env has HOME", bool(env.get("HOME")))
check("shell_env has PATH", bool(env.get("PATH")))
os.environ.pop("SSH_CONNECTION", None)
os.environ["TERM"] = "xterm-256color"

# 7. A dead TERMUX_APP_PID is not passed on; a live one is kept only if it exists. A stale
#    pid makes `termux-apps-info` style lookups talk about the wrong process.
os.environ["TERMUX_APP_PID"] = "999999"
env = g["shell_env"]()
check("shell_env drops a dead TERMUX_APP_PID", "TERMUX_APP_PID" not in env)
os.environ["TERMUX_APP_PID"] = str(os.getpid())
env = g["shell_env"]()
check("shell_env keeps a live TERMUX_APP_PID", env.get("TERMUX_APP_PID") == str(os.getpid()))
os.environ.pop("TERMUX_APP_PID", None)

print()
if fails:
    print("FAILED: %d" % len(fails))
    sys.exit(1)
print("ALL ENV TESTS OK")
