package com.termux.terminal;

import static com.termux.terminal.Protocol.C_FONT_FETCH;
import static com.termux.terminal.Protocol.C_FONT_GET;
import static com.termux.terminal.Protocol.C_FONT_LIST;
import static com.termux.terminal.Protocol.C_HELLO;
import static com.termux.terminal.Protocol.C_SYNC;
import static com.termux.terminal.Protocol.MAX_FRAME;
import static com.termux.terminal.Protocol.S_ERROR;
import static com.termux.terminal.Protocol.S_FONT_DATA;
import static com.termux.terminal.Protocol.S_FONT_FETCHED;
import static com.termux.terminal.Protocol.S_FONT_LIST;
import static com.termux.terminal.Protocol.S_HELLO_OK;
import static com.termux.terminal.Protocol.S_SYNCED;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A one-shot question to the daemon: "which sessions do you still have?"
 *
 * Used at startup so a relaunched app reopens the shells it left behind instead of
 * starting a fresh one and abandoning the old. Runs on the caller's thread, which must
 * not be the UI thread.
 */
public final class DaemonClient {


    private DaemonClient() {}

    /** Thrown for a wrong token or protocol version, with a message fit for a toast. */
    public static final class Failure extends Exception {
        public Failure(String message) {
            super(message);
        }
    }

    public static List<Integer> liveSessions(String host, int port, String token, int timeoutMs)
            throws Failure {
        Socket s = new Socket();
        try {
            s.setTcpNoDelay(true);
            s.connect(new InetSocketAddress(host, port), timeoutMs);
            s.setSoTimeout(timeoutMs);
            OutputStream out = s.getOutputStream();
            DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream(), 4096));

            byte[] tok = token.getBytes(StandardCharsets.UTF_8);
            java.io.ByteArrayOutputStream hello = new java.io.ByteArrayOutputStream();
            hello.write(1);
            hello.write(tok.length & 0xff);
            hello.write((tok.length >> 8) & 0xff);
            hello.write(tok, 0, tok.length);
            send(out, C_HELLO, hello.toByteArray());

            while (true) {
                int type = in.readUnsignedByte();
                int len = Protocol.readInt(in);
                if (len < 0 || len > (1 << 20)) throw new Failure(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0001));
                byte[] p = new byte[len];
                in.readFully(p);
                if (type == S_ERROR) {
                    int code = p.length >= 1 ? (p[0] & 0xff) : 0;
                    throw new Failure(code == 3
                            ? io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0002)
                            : io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0003) + code + ")");
                }
                if (type == S_HELLO_OK) {
                    send(out, C_SYNC, new byte[0]);
                    continue;
                }
                if (type == S_SYNCED) {
                    int n = p.length >= 2 ? ((p[0] & 0xff) | ((p[1] & 0xff) << 8)) : 0;
                    List<Integer> ids = new ArrayList<>();
                    for (int i = 0; i < n; i++) {
                        int off = 2 + i * 2;
                        if (off + 1 < p.length) {
                            ids.add((p[off] & 0xff) | ((p[off + 1] & 0xff) << 8));
                        }
                    }
                    return ids;
                }
            }
        } catch (IOException e) {
            throw new Failure(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0004) + e.getMessage());
        } finally {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
    }

    /** One font the daemon can serve, as it appears in the picker. */
    public static final class RemoteFont {
        public final String path;
        public final long size;

        RemoteFont(String path, long size) {
            this.path = path;
            this.size = size;
        }

        /** The file name alone, which is what a list row shows. */
        public String name() {
            int slash = path.lastIndexOf('/');
            return slash >= 0 ? path.substring(slash + 1) : path;
        }
    }

    /**
     * The fonts the Termux side can hand over.
     *
     * <p>This is the answer to "installing a font is too hard". The app cannot read
     * {@code /sdcard} since Android 11 and has no URL fetcher, but Termux can do both - so the
     * daemon, which already runs there, lists what it sees in {@code ~/.fonts} and the shared
     * download folder. A user runs {@code curl -O ~/.fonts/...} once and the font appears here.
     */
    public static List<RemoteFont> fonts(String host, int port, String token, int timeoutMs)
            throws Failure {
        Socket s = new Socket();
        try {
            s.setTcpNoDelay(true);
            s.connect(new InetSocketAddress(host, port), timeoutMs);
            s.setSoTimeout(timeoutMs);
            OutputStream out = s.getOutputStream();
            DataInputStream in = new DataInputStream(
                    new BufferedInputStream(s.getInputStream(), 8192));
            hello(out, in, token);
            send(out, C_FONT_LIST, new byte[0]);
            while (true) {
                int type = in.readUnsignedByte();
                int len = Protocol.readInt(in);
                if (len < 0 || len > (1 << 20)) throw new Failure(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0001));
                byte[] p = new byte[len];
                in.readFully(p);
                if (type == S_ERROR) throw errorOf(p);
                if (type == S_FONT_LIST) return parseFontList(p);
            }
        } catch (IOException e) {
            throw new Failure(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0004) + e.getMessage());
        } finally {
            close(s);
        }
    }

    /**
     * One font's bytes, reassembled from the daemon's chunks.
     *
     * <p>Chunked because a frame is capped at 1 MiB and Iosevka is larger than that. The
     * chunks carry their own offset, so a duplicate or a reorder is detectable rather than
     * silently producing a corrupt font file.
     */
    public static byte[] font(String host, int port, String token, String path, int timeoutMs)
            throws Failure {
        Socket s = new Socket();
        try {
            s.setTcpNoDelay(true);
            s.connect(new InetSocketAddress(host, port), timeoutMs);
            // A larger read timeout than the listing: a 4 MiB font is four frames and the
            // daemon writes each one whole, but a starved device can still pause between them.
            s.setSoTimeout(Math.max(timeoutMs, 15000));
            OutputStream out = s.getOutputStream();
            DataInputStream in = new DataInputStream(
                    new BufferedInputStream(s.getInputStream(), 65536));
            hello(out, in, token);
            byte[] pb = path.getBytes(StandardCharsets.UTF_8);
            java.io.ByteArrayOutputStream req = new java.io.ByteArrayOutputStream();
            req.write(pb.length & 0xff);
            req.write((pb.length >> 8) & 0xff);
            req.write(pb, 0, pb.length);
            send(out, C_FONT_GET, req.toByteArray());

            byte[] data = null;
            int got = 0;
            while (true) {
                int type = in.readUnsignedByte();
                int len = Protocol.readInt(in);
                if (len < 0 || len > (1 << 20)) throw new Failure(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0001));
                byte[] p = new byte[len];
                in.readFully(p);
                if (type == S_ERROR) throw errorOf(p);
                if (type != S_FONT_DATA) continue;
                if (p.length < 10) throw new Failure(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0005));
                int total = (int) le32(p, 0);
                int off = (int) le32(p, 4);
                int nlen = (p[8] & 0xff) | ((p[9] & 0xff) << 8);
                int start = 10 + nlen;
                if (total <= 0 || total > (32 << 20) || start > p.length) {
                    throw new Failure(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0005));
                }
                if (data == null) data = new byte[total];
                int n = Math.min(p.length - start, total - off);
                if (n < 0 || off + n > total) throw new Failure(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0005));
                System.arraycopy(p, start, data, off, n);
                got += n;
                if (got >= total) return data;
            }
        } catch (IOException e) {
            throw new Failure(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0006) + e.getMessage());
        } finally {
            close(s);
        }
    }

    /**
     * Ask the daemon to download a font, and return where it saved it.
     *
     * <p>The download happens on the Termux side on purpose: that process has a network stack
     * and a writable home directory, and this app has neither a URL client nor a folder it may
     * open. One URL in, one path out, and the path is then fetched with {@link #font}.
     *
     * @return the absolute path the daemon wrote, or a {@link Failure} whose message is the
     *         daemon's own refusal (io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0007))
     */
    public static String fetchFont(String host, int port, String token, String url, int timeoutMs)
            throws Failure {
        Socket s = new Socket();
        try {
            s.setTcpNoDelay(true);
            s.connect(new InetSocketAddress(host, port), timeoutMs);
            // A download is bounded by the remote server, not by this device, so the wait is
            // longer than a local listing.
            s.setSoTimeout(Math.max(timeoutMs, 45000));
            OutputStream out = s.getOutputStream();
            DataInputStream in = new DataInputStream(
                    new BufferedInputStream(s.getInputStream(), 8192));
            hello(out, in, token);
            byte[] ub = url.getBytes(StandardCharsets.UTF_8);
            java.io.ByteArrayOutputStream req = new java.io.ByteArrayOutputStream();
            req.write(ub.length & 0xff);
            req.write((ub.length >> 8) & 0xff);
            req.write(ub, 0, ub.length);
            send(out, C_FONT_FETCH, req.toByteArray());
            while (true) {
                int type = in.readUnsignedByte();
                int len = Protocol.readInt(in);
                if (len < 0 || len > (1 << 20)) throw new Failure(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0001));
                byte[] p = new byte[len];
                in.readFully(p);
                if (type == S_ERROR) throw errorOf(p);
                if (type != S_FONT_FETCHED) continue;
                if (p.length < 2) throw new Failure(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0008));
                int plen = (p[0] & 0xff) | ((p[1] & 0xff) << 8);
                if (plen == 0) {
                    if (p.length < 4) throw new Failure(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0009));
                    int mlen = (p[2] & 0xff) | ((p[3] & 0xff) << 8);
                    int at = 4;
                    String msg = mlen > 0 && at + mlen <= p.length
                            ? new String(p, at, mlen, StandardCharsets.UTF_8) : io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0009);
                    throw new Failure(msg);
                }
                if (2 + plen > p.length) throw new Failure(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0008));
                return new String(p, 2, plen, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new Failure(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0006) + e.getMessage());
        } finally {
            close(s);
        }
    }

    private static void hello(OutputStream out, DataInputStream in, String token)
            throws IOException, Failure {
        send(out, C_HELLO, Protocol.hello(token));
        int type = in.readUnsignedByte();
        int len = Protocol.readInt(in);
        if (len < 0 || len > (1 << 20)) throw new Failure(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0001));
        byte[] p = new byte[len];
        in.readFully(p);
        if (type == S_ERROR) throw errorOf(p);
        if (type != S_HELLO_OK) throw new Failure(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0010) + type + ")");
    }

    private static Failure errorOf(byte[] p) {
        int code = p.length >= 1 ? (p[0] & 0xff) : 0;
        return new Failure(code == 3
                ? io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0002)
                : io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0003) + code + ")");
    }

    private static List<RemoteFont> parseFontList(byte[] p) throws Failure {
        List<RemoteFont> out = new ArrayList<>();
        if (p.length < 2) return out;
        int count = (p[0] & 0xff) | ((p[1] & 0xff) << 8);
        int at = 2;
        for (int i = 0; i < count; i++) {
            if (at + 2 > p.length) break;
            int nlen = (p[at] & 0xff) | ((p[at + 1] & 0xff) << 8);
            at += 2;
            if (at + nlen + 4 + 2 > p.length) break;
            String name = new String(p, at, nlen, StandardCharsets.UTF_8);
            at += nlen;
            long size = le32(p, at);
            at += 4;
            int plen = (p[at] & 0xff) | ((p[at + 1] & 0xff) << 8);
            at += 2;
            if (at + plen > p.length) break;
            String path = new String(p, at, plen, StandardCharsets.UTF_8);
            at += plen;
            out.add(new RemoteFont(path, size));
            if (name.isEmpty()) { /* name is derived from the path; nothing to do */ }
        }
        return out;
    }

    private static long le32(byte[] b, int off) {
        return (b[off] & 0xffL) | ((b[off + 1] & 0xffL) << 8)
                | ((b[off + 2] & 0xffL) << 16) | ((b[off + 3] & 0xffL) << 24);
    }

    private static void close(Socket s) {
        try {
            s.close();
        } catch (IOException ignored) {
        }
    }

    private static void send(OutputStream out, int type, byte[] payload) throws IOException {
        byte[] head = new byte[5];
        head[0] = (byte) type;
        Protocol.putInt(head, 1, payload.length);
        out.write(head);
        out.write(payload);
        out.flush();
    }

    private static int readIntLE(DataInputStream in) throws IOException {
        return in.readUnsignedByte() | (in.readUnsignedByte() << 8)
                | (in.readUnsignedByte() << 16) | (in.readUnsignedByte() << 24);
    }

    private static void putInt(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xff);
        b[off + 1] = (byte) ((v >> 8) & 0xff);
        b[off + 2] = (byte) ((v >> 16) & 0xff);
        b[off + 3] = (byte) ((v >> 24) & 0xff);
    }
}
