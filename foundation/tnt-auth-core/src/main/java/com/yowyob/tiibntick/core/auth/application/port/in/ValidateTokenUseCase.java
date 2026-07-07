package com.yowyob.tiibntick.core.auth.application.port.in;

import com.yowyob.tiibntick.core.auth.domain.model.TntTokenClaims;
import reactor.core.publisher.Mono;

/**
 * Primary (inbound) port: validates a raw bearer token and extracts its TiiBnTick claims.
 *
 * <p>Delegates the actual cryptographic validation to the Kernel's
 * {@code UserSessionTokenService} — no JWT processing is performed in tnt-auth-core.
 *
 * <p>Used by inter-service callers (e.g. service-to-service webhooks) that need to
 * validate a token without going through the HTTP filter chain.
 *
 * @author MANFOUO Braun
 */
public interface ValidateTokenUseCase {

    /**
     * Validates the bearer token and returns its extracted TiiBnTick claims.
     * Emits {@link com.yowyob.tiibntick.core.auth.domain.exception.TntAuthException#tokenInvalid(String)}
     * or {@link com.yowyob.tiibntick.core.auth.domain.exception.TntAuthException#tokenExpired()}
     * on failure.
     *
     * @param bearerToken raw JWT string (without "Bearer " prefix)
     */
    Mono<TntTokenClaims> validateAndExtract(String bearerToken);

    /**
     * Synchronous convenience check — returns false for any invalid or expired token.
     * Non-throwing: catches and discards verification errors.
     */
    boolean isValid(String bearerToken);
}
