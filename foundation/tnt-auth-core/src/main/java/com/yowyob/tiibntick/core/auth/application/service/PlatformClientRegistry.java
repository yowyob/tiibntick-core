package com.yowyob.tiibntick.core.auth.application.service;

import com.yowyob.tiibntick.core.auth.config.TntPlatformGatewayProperties;
import com.yowyob.tiibntick.core.auth.domain.model.PlatformClientApplication;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * In-memory lookup of registered platform backends, keyed by {@code X-Client-Id} —
 * see {@link TntPlatformGatewayProperties} for how entries are configured.
 *
 * @author MANFOUO Braun
 */
public class PlatformClientRegistry {

    private final Map<String, PlatformClientApplication> byClientId;

    public PlatformClientRegistry(TntPlatformGatewayProperties properties) {
        // Entries with a blank client-id/api-key are unconfigured placeholders (e.g. a
        // platform env var left unset in dev) — skip them rather than let several blank
        // client-ids collide as duplicate map keys.
        this.byClientId = properties.getClients().stream()
                .filter(e -> e.getClientId() != null && !e.getClientId().isBlank()
                        && e.getApiKey() != null && !e.getApiKey().isBlank())
                .collect(Collectors.toUnmodifiableMap(
                        TntPlatformGatewayProperties.ClientEntry::getClientId,
                        e -> new PlatformClientApplication(e.getPlatformCode(), e.getClientId(), e.getApiKey(), e.isEnabled()),
                        (first, duplicate) -> first));
    }

    /**
     * Returns the registered client matching {@code clientId} + {@code apiKey}, only if
     * both match and the client is enabled — never returns a client on a partial match.
     */
    public Optional<PlatformClientApplication> authenticate(String clientId, String apiKey) {
        if (clientId == null || apiKey == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byClientId.get(clientId))
                .filter(client -> client.enabled())
                .filter(client -> constantTimeEquals(client.apiKey(), apiKey));
    }

    /**
     * Constant-time comparison to avoid leaking the correct API key length/prefix through
     * response-time side channels.
     */
    private boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return java.security.MessageDigest.isEqual(
                expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                actual.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
