package com.bienvenueblainville.push;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECFieldFp;
import java.security.spec.ECPoint;
import java.security.spec.EllipticCurve;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * Message encryption for Web Push: RFC 8291 over the aes128gcm content coding
 * of RFC 8188.
 *
 * <p>Written against the JDK rather than pulled in as a library, because the
 * JDK already has every primitive this needs - P-256 ECDH, HMAC-SHA256 and
 * AES-GCM - and the alternative was BouncyCastle for the sake of a hundred
 * lines. The thing that makes that defensible is not the line count, it is
 * that it can be checked: {@code WebPushCryptoTest} encrypts a fixed input and
 * compares it byte for byte against output captured from {@code http_ece}, the
 * JavaScript reference implementation the {@code web-push} library uses. If
 * this file and that one disagree anywhere, the test fails.
 *
 * <p>The salt and the ephemeral key are arguments rather than generated
 * inside, which is what makes that comparison possible at all: encryption is
 * randomised, so an implementation that generates them privately can only ever
 * be tested against itself.
 */
public final class WebPushCrypto {
    private static final String CURVE = "secp256r1";
    private static final int KEY_LENGTH = 16;
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final int UNCOMPRESSED_POINT_LENGTH = 65;

    /** RFC 8291 section 3.4. The trailing NUL is part of the string. */
    private static final byte[] KEY_INFO_PREFIX = "WebPush: info\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CEK_INFO = "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] NONCE_INFO = "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII);

    private WebPushCrypto() {
    }

    /**
     * @param receiverPublicKey the subscription's {@code p256dh}, an uncompressed P-256 point
     * @param authSecret        the subscription's {@code auth}, 16 bytes
     * @param ephemeral         a fresh key pair per message; reusing one across messages
     *                          reuses the nonce, which is how AES-GCM stops being secure
     * @param salt              16 random bytes, fresh per message, for the same reason
     * @return the body to POST, header and single record together
     */
    public static byte[] encrypt(
            byte[] receiverPublicKey, byte[] authSecret, byte[] plaintext,
            KeyPair ephemeral, byte[] salt, int recordSize
    ) {
        try {
            byte[] senderPublicKey = encodePublicKey((ECPublicKey) ephemeral.getPublic());
            byte[] shared = agree(ephemeral.getPrivate(), decodePublicKey(receiverPublicKey));

            // RFC 8291 section 3.4: the auth secret salts the ECDH output, and
            // both public keys are bound into the info string, so a key swapped
            // in transit derives a different key rather than a working one.
            byte[] prkKey = hmac(authSecret, shared);
            byte[] keyInfo = concat(KEY_INFO_PREFIX, receiverPublicKey, senderPublicKey);
            byte[] ikm = hkdfExpand(prkKey, keyInfo, 32);

            byte[] prk = hmac(salt, ikm);
            byte[] cek = hkdfExpand(prk, CEK_INFO, KEY_LENGTH);
            byte[] nonce = hkdfExpand(prk, NONCE_INFO, NONCE_LENGTH);

            // One record, so the padding delimiter is 0x02 ("last"). A 0x01
            // here would tell the browser another record follows and leave it
            // waiting for bytes that never come.
            byte[] padded = concat(plaintext, new byte[]{2});

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(padded);

            ByteArrayOutputStream body = new ByteArrayOutputStream();
            body.write(salt);
            body.write(ByteBuffer.allocate(4).putInt(recordSize).array());
            body.write(senderPublicKey.length);
            body.write(senderPublicKey);
            body.write(ciphertext);
            return body.toByteArray();
        } catch (GeneralSecurityException | java.io.IOException e) {
            throw new IllegalStateException("Could not encrypt the push message", e);
        }
    }

    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec(CURVE));
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("P-256 is not available", e);
        }
    }

    /**
     * Rebuilds a stored key pair, so VAPID keys survive a restart.
     *
     * <p>Both halves are arguments. Deriving the public key from the private
     * scalar means multiplying a point on P-256, and the JDK exposes no API
     * for that - the alternative was writing curve arithmetic by hand, which
     * is a bad place to be subtly wrong. A key pair is generated once and both
     * halves are stored, so the derivation is never needed.
     */
    public static KeyPair keyPair(byte[] publicKey, byte[] privateScalar) {
        return new KeyPair(decodePublicKey(publicKey), privateKeyFromScalar(privateScalar));
    }

    public static byte[] encodePublicKey(ECPublicKey key) {
        byte[] x = toFixedLength(key.getW().getAffineX(), 32);
        byte[] y = toFixedLength(key.getW().getAffineY(), 32);
        return concat(new byte[]{4}, x, y);
    }

    public static ECPublicKey decodePublicKey(byte[] uncompressed) {
        if (uncompressed.length != UNCOMPRESSED_POINT_LENGTH || uncompressed[0] != 4) {
            throw new IllegalArgumentException("Expected a 65-byte uncompressed P-256 point");
        }
        try {
            BigInteger x = new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 33));
            BigInteger y = new BigInteger(1, Arrays.copyOfRange(uncompressed, 33, 65));
            ECParameterSpec params = namedCurveParameters();
            requireOnCurve(x, y, params);
            KeyFactory factory = KeyFactory.getInstance("EC");
            return (ECPublicKey) factory.generatePublic(
                    new ECPublicKeySpec(new ECPoint(x, y), params));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Not a valid P-256 public key", e);
        }
    }

    /**
     * Rejects a point that is not on P-256.
     *
     * <p>The JDK does not do this. {@code KeyFactory.generatePublic} accepted
     * (0, 0) quite happily, which a test caught; the subscription's p256dh
     * arrives from a client, so an off-curve point is something a caller can
     * choose. Running ECDH against one is the invalid-curve attack, and it
     * leaks information about the private key it is run with. The ephemeral
     * key here is fresh per message and thrown away, which limits the damage
     * rather than removing the bug - and the check is four lines.
     */
    private static void requireOnCurve(BigInteger x, BigInteger y, ECParameterSpec params) {
        EllipticCurve curve = params.getCurve();
        BigInteger p = ((ECFieldFp) curve.getField()).getP();
        if (x.signum() < 0 || x.compareTo(p) >= 0 || y.signum() < 0 || y.compareTo(p) >= 0) {
            throw new IllegalArgumentException("Public key coordinates are outside the field");
        }
        // y^2 == x^3 + ax + b (mod p)
        BigInteger left = y.modPow(BigInteger.TWO, p);
        BigInteger right = x.modPow(BigInteger.valueOf(3), p)
                .add(curve.getA().multiply(x))
                .add(curve.getB())
                .mod(p);
        if (!left.equals(right)) {
            throw new IllegalArgumentException("Public key is not a point on P-256");
        }
    }

    public static ECParameterSpec namedCurveParameters() {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec(CURVE));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("P-256 parameters are not available", e);
        }
    }

    public static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    public static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static byte[] fromBase64Url(String text) {
        return Base64.getUrlDecoder().decode(text.replace('+', '-').replace('/', '_').replace("=", ""));
    }

    private static byte[] agree(PrivateKey priv, PublicKey pub) throws GeneralSecurityException {
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(priv);
        agreement.doPhase(pub, true);
        return agreement.generateSecret();
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    /** HKDF-Expand for one block, which is all any of these outputs need. */
    private static byte[] hkdfExpand(byte[] prk, byte[] info, int length) throws GeneralSecurityException {
        byte[] block = hmac(prk, concat(info, new byte[]{1}));
        return Arrays.copyOf(block, length);
    }

    private static byte[] toFixedLength(BigInteger value, int length) {
        byte[] raw = value.toByteArray();
        if (raw.length == length) {
            return raw;
        }
        byte[] fixed = new byte[length];
        if (raw.length > length) {
            // BigInteger prepends a zero byte when the high bit is set.
            System.arraycopy(raw, raw.length - length, fixed, 0, length);
        } else {
            System.arraycopy(raw, 0, fixed, length - raw.length, raw.length);
        }
        return fixed;
    }

    private static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] part : parts) {
            total += part.length;
        }
        byte[] joined = new byte[total];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, joined, offset, part.length);
            offset += part.length;
        }
        return joined;
    }

    static ECPrivateKey privateKeyFromScalar(byte[] scalar) {
        try {
            return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(
                    new ECPrivateKeySpec(new BigInteger(1, scalar), namedCurveParameters()));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Not a valid P-256 scalar", e);
        }
    }
}
