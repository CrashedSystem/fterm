package ai.openclaw.fterm;

import android.widget.LinearLayout;
import android.widget.TextView;

import com.termux.terminal.SocketTransport;
import com.termux.terminal.TerminalSession;

/**
 * One tab: the session it shows, the plate in the strip, and what its title should read.
 *
 * <p>Nothing here does anything - it is the record the strip, the session and the close path
 * all pass around - but it is named by all three, so it is not any one of them. It used to be
 * an inner class of the activity, which made every other class that wanted a tab depend on the
 * activity as well.
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
}
