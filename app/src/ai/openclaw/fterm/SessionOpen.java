package ai.openclaw.fterm;

import android.app.AlertDialog;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import com.termux.terminal.DaemonClient;
import com.termux.terminal.SocketTransport;
import com.termux.terminal.TerminalSession;

import java.util.List;

/**
 * Every way a tab gets a shell: the daemon, the token, and the session on the socket.
 *
 * <p>The daemon owns the shells, so the app never creates one locally - it asks what is still
 * alive, attaches to that, and only opens a fresh session when there is nothing to attach to.
 * Getting that far needs a token, and there are two ways in: ask the keeper to start the daemon
 * and read the token it publishes ({@link #provisioning}), or - when the keeper is not there at
 * all - let the user paste one ({@link #manualToken}).
 *
 * <p>What is left in the activity is the tab: this class is handed a {@link Tab}, fills in its
 * session, and reports progress through it. That is the boundary that keeps "open a shell" from
 * being two hundred lines of the activity, and it is why the reattach path, the retry after a
 * stale token, and the profile command all sit in one readable place.
 */
final class SessionOpen {

    private final MainActivity a;

    SessionOpen(MainActivity activity) {
        this.a = activity;
    }

    /**
     * Bring the terminal up.
     *
     * <p>The daemon owns the shells, so the first question is "what is still alive?". A shell
     * left over from a previous launch keeps its screen and its running process; only when
     * the daemon has none do we open a fresh one. That is the whole point of keeping the
     * PTY outside the app: closing the app must not end a build.
     */
    void start() {
        final String token = a.mPrefs.token();
        if (token.isEmpty()) {
            // First run (or after a reinstall): start the daemon ourselves and read the token
            // it publishes, instead of telling the user to go and run two commands by hand.
            provisioning();
            return;
        }

        new Thread(() -> {
            List<Integer> live = null;
            String failure = null;
            try {
                live = DaemonClient.liveSessions(a.mPrefs.host(), a.mPrefs.port(), token, 2500);
            } catch (DaemonClient.Failure f) {
                failure = f.getMessage();
            }
            final List<Integer> ids = live;
            final String err = failure;
            a.mUi.post(() -> {
                if (err != null) {
                    // The token exists but the daemon did not answer. This is the
                    // "swiped the app, came back, nothing works" hole: provisioning (which
                    // would ask the keeper to revive the daemon) only runs when the token is
                    // MISSING, so a stale token meant the app sat on an error dialog forever.
                    // Re-provisioning is safe here - the keeper publishes the same token
                    // unless the daemon regenerated it, and if it did, the new token lands
                    // and the retry below connects.
                    provisioning();
                    return;
                }
                for (int id : ids) {
                    Tab tab = new Tab();
                    tab.id = id;
                    a.mTabs.add(tab);
                    a.addTabChip(tab);
                    openSession(tab, false);
                }
                if (a.mTabs.isEmpty()) {
                    a.newTab(true);
                } else {
                    a.activate(a.mTabs.get(0));
                    a.showKeyboardSoon();
                }
            });
        }, "fterm-start").start();
    }

    /**
     * Bring the daemon up without the user leaving the app.
     *
     * <p>Writes the request file the Termux-side keeper watches, then polls for the token it
     * publishes ({@link DaemonBoot}). If the keeper is not running (never installed, or Termux
     * was killed) the app says so once, with the single command that fixes it - instead of the
     * old flow, which made the user open Termux and copy a token by hand on first run.
     */
    void provisioning() {
        a.mBoot.start(new DaemonBoot.Host() {
            @Override public void ready(String token) {
                a.mPrefs.setToken(token);
                a.mBoot.restore();
                a.newTab(true);
            }

            @Override public void keeperMissing() {
                a.mUi.post(() -> DaemonDialogs.keeperMissing(a, SessionOpen.this::manualToken));
            }

            @Override public void daemonSlow() {
                a.mUi.post(() -> DaemonDialogs.daemonSlow(a, SessionOpen.this::provisioning,
                        SessionOpen.this::manualToken));
            }

            @Override public void storageBlocked() {
                a.mUi.post(() -> DaemonDialogs.storageBlocked(a, SessionOpen.this::manualToken));
            }
        });
    }

    /** Last resort: let the user paste a token they obtained themselves. */
    void manualToken() {
        Tab tab = new Tab();
        tab.id = 1;
        a.mTabs.add(tab);
        a.addTabChip(tab);
        a.activate(tab);
        requestToken(tab);
    }

    /**
     * First run: there is no token. Kick off `ftermd token` inside Termux via
     * RUN_COMMAND and ask the user to paste it once. Everything after that is
     * remembered, so this happens exactly once per install.
     */
    void requestToken(Tab tab) {
        a.mUi.post(() -> {
            final EditText input = new EditText(a);
            input.setHint("Termux에서 `ftermd token` 출력값");
            input.setSingleLine(true);
            input.setInputType(InputType.TYPE_CLASS_TEXT);
            input.setTextColor(0xffe6e6e6);
            input.setHintTextColor(0xff888888);

            new AlertDialog.Builder(a)
                    .setTitle("Termux 연결")
                    .setMessage("Termux에서 다음을 한 번 실행하세요:\n\n"
                            + "  ~/fterm/ftermd start\n"
                            + "  ftermd token\n\n"
                            + "출력된 토큰을 붙여넣으세요.")
                    .setView(input)
                    .setCancelable(false)
                    .setPositiveButton("저장", (d, w) -> {
                        String t = input.getText().toString().trim();
                        if (t.isEmpty()) {
                            a.finish();
                            return;
                        }
                        a.mPrefs.setToken(t);
                        openSession(tab, true);
                    })
                    .setNegativeButton("Termux 명령 복사", (d, w) -> {
                        a.copyToClipboard("ftermd start && ftermd token");
                        Toast.makeText(a, "복사됨 — Termux에 붙여넣고 실행하세요",
                                Toast.LENGTH_LONG).show();
                        a.finish();
                    })
                    .show();
        });
    }

    void openSession(Tab tab, boolean showKeyboard) {
        String token = a.mPrefs.token();
        if (token.isEmpty()) {
            requestToken(tab);
            return;
        }

        Profiles.Profile profile = tab.profile;
        String shell = profile != null && !profile.shell.isEmpty()
                ? profile.shell : a.mPrefs.startShell();
        String dir = profile != null && !profile.dir.isEmpty()
                ? profile.dir : a.mPrefs.startDir();

        TerminalSession session = new TerminalSession("", null, null, null, 8000, a);
        SocketTransport transport = new SocketTransport(
                a.mPrefs.host(), a.mPrefs.port(), token, tab.id, shell, dir);
        session.setTransport(transport);
        tab.session = session;
        tab.transport = transport;
        tab.running = true;
        tab.pendingCommand = profile != null && Profiles.commandIsOneLine(profile.command)
                && !profile.command.isEmpty() ? profile.command : null;
        session.mSessionName = profile != null && !profile.name.isEmpty()
                ? profile.name : Profiles.DEFAULT_NAME + " " + tab.id;

        if (a.mActive == tab) {
            a.mBoot.restore();   // remove any "connecting…" splash still in the frame
            a.mTermView.attachSession(session);
            a.mTermView.setTextSize(a.fontPx());
            a.applySchemeTo(session);
            a.mTermView.requestFocus();
        }

        if (showKeyboard) {
            // The transport connects on its own thread; the keyboard follows the window,
            // not the socket. Showing it immediately is right: the shell is one round trip away.
            a.showKeyboardSoon();
        }
        // A profile's command has to wait for the shell either way - a fresh session has not
        // printed its prompt yet, and a reattached one replays its ring asynchronously. The
        // poll is what tells those apart, so it runs in both cases.
        pollForPrompt(tab);
    }

    /**
     * Type the profile's command into the shell that is now running.
     *
     * <p>Sent as bytes to the PTY, not executed by the daemon: an ssh profile has to inherit
     * the login shell's environment to find its keys and its config, and the daemon's
     * allowlist stays the only thing that decides what binary a session may run.
     */
    private void runPendingCommand(Tab tab) {
        String command = tab.pendingCommand;
        if (command == null) return;
        tab.pendingCommand = null;
        TerminalSession s = tab.session;
        if (s == null) return;
        // A short pause so the shell has finished printing its prompt; a command written
        // mid-prompt is not lost by bash, but it reads as if the user typed over it.
        a.mUi.postDelayed(() -> {
            if (tab.session != null && tab.running) tab.session.write(command + "\r");
        }, 120);
    }

    private void pollForPrompt(Tab tab) {
        final long deadline = System.currentTimeMillis() + 4000;
        Runnable r = new Runnable() {
            @Override
            public void run() {
                TerminalSession s = tab.session;
                boolean ready = s != null && s.getDeliveredBytes() > 0;
                if (ready) {
                    // Clear whatever the reattach left typed, so the shell prompt is not
                    // prefixed with a stale line.
                    if (a.mActive == tab) a.showKeyboardSoon();
                    runPendingCommand(tab);
                    return;
                }
                if (System.currentTimeMillis() < deadline) a.mUi.postDelayed(this, 120);
            }
        };
        a.mUi.postDelayed(r, 120);
    }
}
