package org.conscrypt;

/** Stub: Robolectric's platform shim references this class but these tests never verify TLS. */
public final class OkHostnameVerifier implements javax.net.ssl.HostnameVerifier {
    public static final OkHostnameVerifier INSTANCE = new OkHostnameVerifier();

    @Override
    public boolean verify(String host, javax.net.ssl.SSLSession session) {
        return true;
    }
}
