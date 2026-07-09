package com.yowyob.tiibntick.core.platformgateway.domain.model;

import org.springframework.http.MediaType;

/**
 * Raw byte-for-byte HTTP response captured from a Kernel {@code auth-oidc-controller}
 * call (OIDC discovery / OAuth2 token / introspect / userinfo).
 *
 * <p>These endpoints return standard OAuth2/OIDC JSON (RFC 8414, OpenID Connect
 * Discovery) that must reach the caller byte-for-byte — wrapping it in TiiBnTick's
 * {@code ApiResponse} envelope would break any standard OIDC/OAuth2 client library
 * consuming it, so the gateway proxies status/content-type/body opaquely instead of
 * re-declaring these as typed DTOs.
 *
 * @author MANFOUO Braun
 */
public record KernelRawResponse(
        int status,
        MediaType contentType,
        byte[] body
) {
}
