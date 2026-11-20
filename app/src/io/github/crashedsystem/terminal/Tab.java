package io.github.crashedsystem.terminal;

import android.widget.LinearLayout;
import android.widget.TextView;

import com.termux.terminal.SocketTransport;
import com.termux.terminal.TerminalSession;

/**
 * One tab: a session, the views that show it, and the two pieces of state the strip needs.
 *
 * <p>It is a record, not a class with behaviour - the activity owns what a tab means (which
 * one is active, what closing one does) and {@link TabStrip} owns how it looks. It lives in
 * its own file because it was the largest thing in the activity that was neither a
 * coordinator nor a rule.
 */
final class Tab {

    TerminalSession session;
    SocketTransport transport;
    TextView chip;
    LinearLayout plate;
    TextView closeBox;
    /** The plate, title and close box as one object; see TabStrip. */
    TabStrip.Chip chipView;
    String title = Profiles.DEFAULT_NAME;
    /** Set once the user renames the tab; the shell's OSC title stops winning. */
    boolean userTitled;
    /** The profile this tab was opened from, or null for the default profile. */
    Profiles.Profile profile;
    /** Typed into the shell once it has printed something; see openSession(). */
    String pendingCommand;
    int id;
    boolean running;
    /** A colour chosen for this tab's plate, or 0 to follow the active/inactive default. */
    int color;
}
