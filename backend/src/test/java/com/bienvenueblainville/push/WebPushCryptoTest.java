package com.bienvenueblainville.push;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The implementation checked against somebody else's.
 *
 * <p>Push encryption has no useful self-test: an implementation that encrypts
 * and decrypts with its own code agrees with itself whatever it does wrong,
 * and the only consumer that matters - a browser - cannot be run here. So the
 * expected ciphertext below was produced by {@code http_ece}, the JavaScript
 * reference implementation that the {@code web-push} library uses, driven with
 * these exact inputs, and verified to decrypt back to the plaintext before it
 * was written down. If {@link WebPushCrypto} and that implementation ever
 * disagree by one byte, this fails.
 *
 * <p>Fixed salt and ephemeral key are what make the comparison possible.
 * Encryption is randomised by design, so they are arguments rather than
 * generated inside - the production path passes fresh ones for every message.
 */
class WebPushCryptoTest {
    private static final String PLAINTEXT = "Collecte deplacee en raison de Noel. 收集顺延。";
    private static final String RECEIVER_PUBLIC =
            "BAIX5hfwtkQ5KCePlpmeaaI6TywVK99tbN9m5bgCgtTtGUp968uXcS0t2jyoWqh2Wlb0X8dYWZZS8ol8ZTBuV5Q";
    private static final String SENDER_PUBLIC =
            "BNZak5d8qj0bCBhS_1ennkZfFmBXcwS66tUF3TpIWJzzUBheiVNy32Ih6joTdVfkc_3bZ1XwW9UHw8Uz_OnJEoU";
    private static final String SENDER_PRIVATE = "IiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiI";
    private static final String SALT = "MzMzMzMzMzMzMzMzMzMzMw";
    private static final String AUTH = "RERERERERERERERERERERA";
    private static final String EXPECTED_BODY =
            "MzMzMzMzMzMzMzMzMzMzMwAAEABBBNZak5d8qj0bCBhS_1ennkZfFmBXcwS66tUF3TpIWJzzUBheiVNy32Ih6"
            + "joTdVfkc_3bZ1XwW9UHw8Uz_OnJEoVWNlpH7vMnLega5NngI0mvsqoDFT6xbREirg1yuWbvhsgeuhphxa7R4i"
            + "BjKx--AD9u9PEqAQIFp3CrzutxwrhjjEXi5fA";

    @Test
    void producesTheSameBytesAsTheReferenceImplementation() {
        KeyPair ephemeral = WebPushCrypto.keyPair(
                WebPushCrypto.fromBase64Url(SENDER_PUBLIC),
                WebPushCrypto.fromBase64Url(SENDER_PRIVATE));

        byte[] body = WebPushCrypto.encrypt(
                WebPushCrypto.fromBase64Url(RECEIVER_PUBLIC),
                WebPushCrypto.fromBase64Url(AUTH),
                PLAINTEXT.getBytes(StandardCharsets.UTF_8),
                ephemeral,
                WebPushCrypto.fromBase64Url(SALT),
                4096);

        assertThat(WebPushCrypto.base64Url(body)).isEqualTo(EXPECTED_BODY);
    }

    @Test
    void theHeaderCarriesTheSaltAndTheSenderKeyWhereTheBrowserLooksForThem() {
        KeyPair ephemeral = WebPushCrypto.keyPair(
                WebPushCrypto.fromBase64Url(SENDER_PUBLIC),
                WebPushCrypto.fromBase64Url(SENDER_PRIVATE));

        byte[] body = WebPushCrypto.encrypt(
                WebPushCrypto.fromBase64Url(RECEIVER_PUBLIC),
                WebPushCrypto.fromBase64Url(AUTH),
                "x".getBytes(StandardCharsets.UTF_8),
                ephemeral, WebPushCrypto.fromBase64Url(SALT), 4096);

        // RFC 8188 section 2: salt(16) | rs(4) | idlen(1) | keyid.
        assertThat(java.util.Arrays.copyOfRange(body, 0, 16))
                .isEqualTo(WebPushCrypto.fromBase64Url(SALT));
        assertThat(java.util.Arrays.copyOfRange(body, 16, 20)).isEqualTo(new byte[]{0, 0, 0x10, 0});
        assertThat(body[20]).isEqualTo((byte) 65);
        assertThat(java.util.Arrays.copyOfRange(body, 21, 86))
                .isEqualTo(WebPushCrypto.fromBase64Url(SENDER_PUBLIC));
    }

    @Test
    void everyMessageGetsItsOwnSaltAndKey() {
        // Reusing either across messages reuses the AES-GCM nonce, which is the
        // failure that stops the encryption being encryption at all.
        byte[] receiver = WebPushCrypto.fromBase64Url(RECEIVER_PUBLIC);
        byte[] auth = WebPushCrypto.fromBase64Url(AUTH);
        byte[] plaintext = PLAINTEXT.getBytes(StandardCharsets.UTF_8);

        byte[] first = WebPushCrypto.encrypt(receiver, auth, plaintext,
                WebPushCrypto.generateKeyPair(), WebPushCrypto.randomBytes(16), 4096);
        byte[] second = WebPushCrypto.encrypt(receiver, auth, plaintext,
                WebPushCrypto.generateKeyPair(), WebPushCrypto.randomBytes(16), 4096);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void refusesAKeyThatIsNotAPointOnTheCurve() {
        byte[] notAKey = new byte[65];
        notAKey[0] = 4;

        assertThatThrownBy(() -> WebPushCrypto.decodePublicKey(notAKey))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesAKeyOfTheWrongShape() {
        assertThatThrownBy(() -> WebPushCrypto.decodePublicKey(new byte[64]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("65-byte");
    }
}
