package com.yowyob.tiibntick.core.gofreelancer.application.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Service for password hashing using BCrypt.
 *
 * <p>Provides secure password encoding and verification using Spring Security's
 * BCryptPasswordEncoder with default strength (cost factor of 10).
 *
 * @author Kengfack Lagrange
 * @date 19/12/2025
 */
@Service
public class PasswordHasherService {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * Encodes a raw password using BCrypt.
     *
     * <p>Applies a secure one-way hash to the password for storage.
     * Each call produces a different hash due to automatic salt generation.
     *
     * @param rawPassword the plain text password
     * @return the BCrypt hashed password string
     */
    public String encode(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * Verifies if a raw password matches the encoded password.
     *
     * <p>Uses BCrypt's secure comparison to check validity.
     * This comparison is timing-attack safe.
     *
     * @param rawPassword the plain text password to check
     * @param encodedPassword the stored hashed password
     * @return true if passwords match, false otherwise
     */
    public boolean matches(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}
