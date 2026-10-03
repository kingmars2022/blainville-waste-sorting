package com.bienvenueblainville.push;

/**
 * One browser on one device, as it described itself when subscribing.
 *
 * @param endpoint     the URL its push service listens on; identifies the device
 * @param p256dh       the subscription's public key, base64url, uncompressed P-256
 * @param auth         the 16-byte authentication secret, base64url
 * @param languageCode which of fr/en/zh this device asked to be told in
 */
public record PushSubscription(
        Long id,
        Long userId,
        String endpoint,
        String p256dh,
        String auth,
        String languageCode
) {
}
