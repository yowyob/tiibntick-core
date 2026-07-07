package com.yowyob.tiibntick.core.auth.application.service;

import com.yowyob.tiibntick.core.auth.domain.exception.TntAuthException;
import com.yowyob.tiibntick.core.auth.domain.model.TntTokenClaims;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.security.PublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Validates JWT tokens locally using the Kernel's public key.
 *
 * <p>No dependency on any Kernel bean — the public key is fetched once at startup
 * via KernelPublicKeyProvider and cached for the lifetime of the application.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TntJwtValidator {

    private final KernelPublicKeyProvider publicKeyProvider;
    private PublicKey verificationKey;

    @PostConstruct
    public void init() {
        this.verificationKey = publicKeyProvider.getPublicKey();
        if (this.verificationKey == null) {
            log.warn("TntJwtValidator: Kernel RSA public key is not available — " +
                     "all JWT validation calls will return UNAUTHORIZED until the Kernel is reachable.");
        } else {
            log.info("TntJwtValidator initialized with Kernel RSA public key");
        }
    }

    public Mono<TntTokenClaims> validateAndExtract(String bearerToken) {
        if (verificationKey == null) {
            return Mono.error(TntAuthException.tokenInvalid(
                "JWT validation unavailable: Kernel public key not loaded (Kernel unreachable at startup)."
            ));
        }
        return Mono.fromCallable(() -> {
            String token = stripBearerPrefix(bearerToken);

            Claims claims = Jwts.parser()
                    .verifyWith(verificationKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Date expiration = claims.getExpiration();
            if (expiration != null && expiration.toInstant().isBefore(Instant.now())) {
                throw TntAuthException.tokenExpired();
            }

            Date notBefore = claims.getNotBefore();
            if (notBefore != null && notBefore.toInstant().isAfter(Instant.now())) {
                throw TntAuthException.tokenInvalid("Token not yet valid.");
            }

            return new TntTokenClaims(
                    extractUuid(claims, "sub"),
                    extractUuid(claims, "tenant_id"),
                    extractUuid(claims, "actor_id"),
                    extractUuid(claims, "organization_id"),
                    extractUuid(claims, "agency_id"),
                    extractPermissions(claims),
                    toInstant(claims.getIssuedAt()),
                    toInstant(expiration),
                    claims.getId()
            );
        }).onErrorMap(e -> {
            if (e instanceof TntAuthException) return e;
            log.warn("JWT validation failed: {}", e.getMessage());
            return TntAuthException.tokenInvalid("Token validation failed: " + e.getMessage());
        });
    }

    public boolean isValid(String bearerToken) {
        try {
            String token = stripBearerPrefix(bearerToken);
            Jwts.parser()
                    .verifyWith(verificationKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String stripBearerPrefix(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) {
            throw TntAuthException.tokenInvalid("Token is null or empty.");
        }
        String trimmed = bearerToken.trim();
        return trimmed.startsWith("Bearer ") ? trimmed.substring(7) : trimmed;
    }

    private static UUID extractUuid(Claims claims, String key) {
        Object value = claims.get(key);
        if (value == null) return null;
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID format for claim '{}': {}", key, value);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Set<String> extractPermissions(Claims claims) {
        Object perms = claims.get("permissions");
        if (perms == null) return Set.of();
        if (perms instanceof List) {
            return ((List<?>) perms).stream()
                    .map(Object::toString)
                    .collect(Collectors.toUnmodifiableSet());
        }
        if (perms instanceof String) {
            return Set.of(perms.toString().split(","));
        }
        return Set.of();
    }

    private static Instant toInstant(Date date) {
        return date != null ? date.toInstant() : null;
    }
}