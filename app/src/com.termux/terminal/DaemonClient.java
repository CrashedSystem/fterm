package com.termux.terminal;

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

    private static final int C_HELLO = 0x01;
    private static final int C_SYNC = 0x09;
    private static final int S_HELLO_OK = 0x80;
    private static final int S_SYNCED = 0x9E;
    private static final int S_ERROR = 0x9F;

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
                int len = readIntLE(in);
                if (len < 0 || len > (1 << 20)) throw new Failure("데몬 프레임 오류");
                byte[] p = new byte[len];
                in.readFully(p);
                if (type == S_ERROR) {
                    int code = p.length >= 1 ? (p[0] & 0xff) : 0;
                    throw new Failure(code == 3
                            ? "토큰이 틀렸습니다 — Termux에서 ftermd token 을 확인하세요"
                            : "데몬 오류 (code " + code + ")");
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
            throw new Failure("Termux 데몬에 연결할 수 없습니다: " + e.getMessage());
        } finally {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static void send(OutputStream out, int type, byte[] payload) throws IOException {
        byte[] head = new byte[5];
        head[0] = (byte) type;
        putInt(head, 1, payload.length);
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
