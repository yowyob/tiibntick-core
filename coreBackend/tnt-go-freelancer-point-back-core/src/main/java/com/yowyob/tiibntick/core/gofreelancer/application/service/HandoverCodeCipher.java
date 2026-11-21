package com.yowyob.tiibntick.core.gofreelancer.application.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts the handover codes of a delivery (pickup, delivery) so that the owner of the delivery
 * need can read them again. AES-256-GCM, random 96-bit IV per value, stored as
 * {@code v1:<base64(iv || ciphertext+tag)>}.
 *
 * <p>The key is derived from the existing QR signing secret ({@code tnt.media.hmac-secret}):
 * {@code HMAC-SHA256(secret, "tnt-handover-codes-v1")}. No new mandatory setting: when the secret is
 * absent (dev, tests), a fixed development key is used and a single warning is logged.
 */
@Slf4j
@Component
public class HandoverCodeCipher {

    static final String KEY_LABEL = "tnt-handover-codes-v1";
    private static final String PREFIX = "v1:";
    private static final String DEV_SECRET = "tnt-dev-only-handover-codes-secret";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public HandoverCodeCipher(@Value("${tnt.media.hmac-secret:}") String secret) {
        boolean absent = secret == null || secret.isBlank();
        if (absent) {
            log.warn("Handover codes: no server secret configured (tnt.media.hmac-secret) — "
                    + "using a development key. Configure the secret on any shared environment.");
        } else if (secret.startsWith("changeme")) {
            log.warn("Handover codes: tnt.media.hmac-secret still has its default value — "
                    + "set a random secret on any shared environment.");
        }
        this.key = new SecretKeySpec(derive(absent ? DEV_SECRET : secret), "AES");
    }

    static byte[] derive(String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(KEY_LABEL.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    public String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array();
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Handover code encryption failed", e);
        }
    }

    /** The plain code, or {@code null} when the value is absent, malformed or was sealed with another key. */
    public String decrypt(String sealed) {
        if (sealed == null || !sealed.startsWith(PREFIX)) {
            return null;
        }
        try {
            byte[] in = Base64.getDecoder().decode(sealed.substring(PREFIX.length()));
            if (in.length <= IV_BYTES) {
                return null;
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, in, 0, IV_BYTES));
            byte[] plain = cipher.doFinal(in, IV_BYTES, in.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            log.warn("Handover code could not be decrypted (key changed or value altered)");
            return null;
        }
    }
}
