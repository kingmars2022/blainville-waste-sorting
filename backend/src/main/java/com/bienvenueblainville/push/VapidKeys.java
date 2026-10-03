package com.bienvenueblainville.push;

import java.security.KeyPair;

/**
 * The application server's identity key pair, held once rather than rebuilt
 * per message.
 *
 * <p>Both halves are configured. The public half also travels to the browser,
 * which uses it as {@code applicationServerKey} when subscribing, and a
 * subscription is bound to it - rotating the key invalidates every existing
 * subscription, so it is configuration rather than something generated on
 * startup.
 */
public record VapidKeys(KeyPair keyPair, String publicKeyBase64Url) {
    public static VapidKeys of(String publicKeyBase64Url, String privateKeyBase64Url) {
        return new VapidKeys(
                WebPushCrypto.keyPair(
                        WebPushCrypto.fromBase64Url(publicKeyBase64Url),
                        WebPushCrypto.fromBase64Url(privateKeyBase64Url)),
                publicKeyBase64Url);
    }
}
