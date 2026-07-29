package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.domain.exception.InvalidTokenException;
import com.yowyob.tiibntick.core.gofreelancer.domain.exception.ResourceNotFoundException;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.PasswordToken;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.PasswordTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Service for token management.
 *
 * @author Kengfack Lagrange
 * @date 19/12/2025
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    private final PasswordTokenRepository passwordTokenRepository;
    private static final int EXPIRATION_HOURS = 48;

    /**
     * Generates a new password reset token for a user.
     *
     * Creates a secure random token, persists it with an expiration time,
     * and associates it with the given person ID.
     *
     * @param personId the UUID of the person requesting the token
     * @return a Mono containing the generated PasswordToken entity
     */
    public Mono<PasswordToken> generateToken(UUID personId) {
        PasswordToken token = new PasswordToken();
        token.setPersonId(personId);
        token.setToken(UUID.randomUUID().toString());
        token.setExpiryDate(Instant.now().plus(EXPIRATION_HOURS, ChronoUnit.HOURS));
        token.setUsed(false);
        return passwordTokenRepository.save(token);
    }

    public Mono<Boolean> validateToken(String token) {
        return passwordTokenRepository.findByToken(token)
                .map(passwordToken -> {
                    boolean isExpired = passwordToken.getExpiryDate().isBefore(Instant.now());
                    boolean isUsed = Boolean.TRUE.equals(passwordToken.getUsed());
                    return !isExpired && !isUsed;
                })
                .defaultIfEmpty(false);
    }

    /**
     * Retrieve a token entity by its string value.
     *
     * @param token the token string
     * @return Mono of PasswordToken
     */
    public Mono<PasswordToken> getToken(String token) {
        return passwordTokenRepository.findByToken(token)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Token not found")));
    }

    /**
     * Marks a token as used/expired.
     *
     * Updates the token status in the database to prevent reuse.
     * Should be called immediately after a successful password reset.
     *
     * @param token the token string to expire
     * @return a Mono<Void> signaling completion
     * @throws com.yowyob.tiibntick.core.gofreelancer.domain.exception.InvalidTokenException if token not found
     */
    public Mono<Void> expireToken(String token) {
        return passwordTokenRepository.findByToken(token)
                .switchIfEmpty(Mono.error(new InvalidTokenException("Invalid token")))
                .flatMap(passwordToken -> {
                    passwordToken.setUsed(true);
                    return passwordTokenRepository.save(passwordToken);
                })
                .then();
    }
}
