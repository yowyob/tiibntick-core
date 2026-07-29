package com.yowyob.tiibntick.core.gofreelancer.application.service;

import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

/**
 * Service responsible for generating and verifying 6-digit OTP codes
 * used to confirm parcel pickup and delivery.
 *
 * <p>Codes are stored as BCrypt hashes — the plain-text code is only
 * communicated once (via notification) and never persisted.</p>
 *
 * @author François-Charles ATANGA
 */
@Service
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int OTP_LENGTH = 6;

    /**
     * Generates a random numeric OTP of {@value OTP_LENGTH} digits.
     *
     * @return plain-text OTP string (e.g. "483920")
     */
    public String generateOtp() {
        int bound = (int) Math.pow(10, OTP_LENGTH);
        int code = RANDOM.nextInt(bound);
        // Zero-pad to always produce exactly OTP_LENGTH digits
        return String.format("%0" + OTP_LENGTH + "d", code);
    }

    /**
     * Hashes a plain-text OTP using BCrypt.
     *
     * @param otp plain-text OTP
     * @return BCrypt hash to store in the database
     */
    public String hashOtp(String otp) {
        return BCrypt.hashpw(otp, BCrypt.gensalt());
    }

    /**
     * Verifies a plain-text OTP against a stored BCrypt hash.
     *
     * @param otp  the plain-text code provided by the delivery person
     * @param hash the stored BCrypt hash
     * @return {@code true} if the code matches, {@code false} otherwise
     */
    public boolean verifyOtp(String otp, String hash) {
        if (otp == null || hash == null) {
            return false;
        }
        return BCrypt.checkpw(otp, hash);
    }
}
