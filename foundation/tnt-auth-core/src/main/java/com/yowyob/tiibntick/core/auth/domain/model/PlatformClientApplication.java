package com.yowyob.tiibntick.core.auth.domain.model;

/**
 * A TiiBnTick platform backend (Agency, Go, Link, Market, Point Relais, ...) registered
 * to call the Core's platform gateway (see {@code PlatformAuthController},
 * {@code PlatformSsoController}). Identified by its own {@code X-Client-Id}/{@code X-Api-Key}
 * pair — distinct from the single Kernel-facing identity ({@code tibntick-backend}) Core
 * itself uses, so no platform ever sees or needs Kernel credentials
 * (see {@code CORE_KERNEL_GATEWAY_SPEC.md} §10-11).
 *
 * @author MANFOUO Braun
 */
public record PlatformClientApplication(
        String platformCode,
        String clientId,
        String apiKey,
        boolean enabled
) {
}
