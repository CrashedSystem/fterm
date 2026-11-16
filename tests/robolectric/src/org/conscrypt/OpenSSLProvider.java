package org.conscrypt;

/**
 * Stub for the Robolectric runtime on Termux.
 *
 * The real conscrypt ships a glibc JNI library, which bionic cannot load. Robolectric only
 * wants the class to exist while it wires up security providers; SSL is never used by these
 * tests, so a Provider that announces nothing is enough.
 */
public class OpenSSLProvider extends java.security.Provider {
    public OpenSSLProvider() {
        super("Conscrypt", 1.0, "stub");
    }

    public OpenSSLProvider(String name) {
        super(name, 1.0, "stub");
    }
}
