package com.yowyob.tiibntick.core.auth.domain.model;

/**
 * Represents a pair of tokens (access + refresh) returned when a client
 * authenticates or refreshes its session through the Kernel (YowAuth0).
 *
 * <p>tnt-auth-core does not issue tokens — it only wraps what the Kernel returns.
 * This record provides a clean domain type for passing token pairs across modules.
 *
 * @author MANFOUO Braun
 */
public record TntTokenPair(
        String accessToken,
        String refreshToken,
        long expiresInSeconds,
        String tokenType
) {

    private static final String DEFAULT_TOKEN_TYPE = "Bearer";

    public static TntTokenPair of(String accessToken, String refreshToken, long expiresInSeconds) {
        return new TntTokenPair(accessToken, refreshToken, expiresInSeconds, DEFAULT_TOKEN_TYPE);
    }

    public String authorizationHeaderValue() {
        return tokenType + " " + accessToken;
    }
}
