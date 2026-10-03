package com.bienvenueblainville.push;

import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;

/**
 * Prints a VAPID key pair for {@code app.push.public-key} and
 * {@code app.push.private-key}.
 *
 * <p>A main rather than an endpoint. Generating the application server's
 * identity is something an operator does once, by hand, before the feature is
 * switched on - not something a running server should be able to do to itself.
 *
 * <pre>mvn -q exec:java -Dexec.mainClass=com.bienvenueblainville.push.VapidKeyGenerator</pre>
 */
public final class VapidKeyGenerator {
    private VapidKeyGenerator() {
    }

    public static void main(String[] args) {
        KeyPair pair = WebPushCrypto.generateKeyPair();
        byte[] publicKey = WebPushCrypto.encodePublicKey((ECPublicKey) pair.getPublic());
        byte[] privateKey = toFixed32(((ECPrivateKey) pair.getPrivate()).getS().toByteArray());

        System.out.println("app.push.public-key  = " + WebPushCrypto.base64Url(publicKey));
        System.out.println("app.push.private-key = " + WebPushCrypto.base64Url(privateKey));
        System.out.println();
        System.out.println("The private key is a secret. The public key is also the browser's");
        System.out.println("applicationServerKey, and every existing subscription is bound to it:");
        System.out.println("replacing this pair makes every device re-subscribe.");
    }

    private static byte[] toFixed32(byte[] raw) {
        if (raw.length == 32) {
            return raw;
        }
        byte[] fixed = new byte[32];
        if (raw.length > 32) {
            System.arraycopy(raw, raw.length - 32, fixed, 0, 32);
        } else {
            System.arraycopy(raw, 0, fixed, 32 - raw.length, raw.length);
        }
        return fixed;
    }
}
