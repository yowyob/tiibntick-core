package com.yowyob.tiibntick.core.platformgateway.application.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates and verifies platform API keys.
 *
 * <p>Hashing: BCrypt via {@link BCryptPasswordEncoder} (decided 2026-07-08 — already a
 * transitive dependency through {@code spring-boot-starter-security}, no new dependency;
 * sufficient for this threat model of a handful of trusted, ops-managed platform
 * credentials, unlike Argon2's memory-hardness which targets internet-scale password
 * stores). {@link BCryptPasswordEncoder#matches} is timing-safe internally.
 *
 * <p>Raw key shape: {@code tnt_<standard-base64(32 random bytes)>} — same alphabet and
 * length as {@code openssl rand -base64 32} (44 chars incl. padding), just prefixed with
 * {@code tnt_} so a leaked key is recognizable at a glance as a TiiBnTick platform
 * credential (e.g. {@code tnt_9Iu01WuhBgm3pzlnM0oqd8nDBZscFVa3imab7z9M0YA=}, 48 chars total).
 *
 * @author MANFOUO Braun
 */
public class ApiKeyHashingService {

    private static final String KEY_PREFIX = "tnt_";
    /** Long enough to disambiguate candidates for display/audit; short enough to be useless for brute-forcing the secret. */
    private static final int PREFIX_LENGTH = 12;
    private static final int SECRET_BYTES = 32;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a brand-new raw secret. This is the ONLY moment the plaintext is
     * available — callers must hash it via {@link #hash} for storage and return the
     * plaintext to the admin caller exactly once.
     */
    public String generateRawKey() {
        byte[] randomBytes = new byte[SECRET_BYTES];
        secureRandom.nextBytes(randomBytes);
        String encoded = Base64.getEncoder().encodeToString(randomBytes);
        return KEY_PREFIX + encoded;
    }

    /**
     * Non-secret prefix stored alongside the hash for display truncation (e.g.
     * {@code tnt_9Iu01WuhBgm3...}) — never enough characters to be useful in a
     * brute-force attempt.
     */
    public String prefixOf(String rawKey) {
        return rawKey.substring(0, Math.min(rawKey.length(), PREFIX_LENGTH));
    }

    public String hash(String rawKey) {
        return passwordEncoder.encode(rawKey);
    }

    /** Timing-safe comparison, delegated to {@link BCryptPasswordEncoder}. */
    public boolean matches(String rawKey, String hash) {
        return passwordEncoder.matches(rawKey, hash);
    }
}
