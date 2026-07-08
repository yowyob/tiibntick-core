package com.yowyob.tiibntick.core.auth.adapter.in.web;

import com.yowyob.tiibntick.core.auth.application.port.in.ProxyKernelAuthUseCase;
import com.yowyob.tiibntick.core.auth.domain.model.KernelRawResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Proxies the Kernel's {@code auth-oidc-controller} — OIDC discovery + OAuth2
 * token/introspect/userinfo — byte-for-byte, at the same root-relative paths the
 * Kernel itself uses. Registered as public in {@code TntSecurityConfig}, matching
 * their semantics on the Kernel (discovery must be public; token/introspect/userinfo
 * are self-secured via whatever the caller puts in {@code Authorization}, not by
 * TiiBnTick Core's own security chain).
 *
 * <p>Responses are NOT wrapped in TiiBnTick's {@code ApiResponse} envelope — these are
 * standard OAuth2 (RFC 6749/7662) / OpenID Connect Discovery (RFC 8414) JSON documents
 * that must reach the caller unmodified for any standard OIDC/OAuth2 client library to
 * parse them correctly.
 *
 * <p>Platforms that configure TiiBnTick Core as their JWT issuer point
 * {@code JWT_JWK_SET_URI}/{@code JWT_ISSUER_URI} at these endpoints instead of the
 * Kernel's — see {@code CORE_KERNEL_GATEWAY_SPEC.md} §9.
 *
 * @author MANFOUO Braun
 */
@RestController
@Tag(name = "Platform Auth Gateway (OIDC)", description = "Kernel auth-oidc-controller proxy — raw OAuth2/OIDC passthrough")
public class PlatformAuthOidcController {

    private final ProxyKernelAuthUseCase useCase;

    public PlatformAuthOidcController(ProxyKernelAuthUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping("/.well-known/openid-configuration")
    @Operation(summary = "OpenID Connect discovery document")
    public Mono<ResponseEntity<byte[]>> openidConfiguration() {
        return proxy(HttpMethod.GET, "/.well-known/openid-configuration", null, null, null);
    }

    @GetMapping("/.well-known/oauth-authorization-server")
    @Operation(summary = "OAuth2 authorization server metadata (RFC 8414)")
    public Mono<ResponseEntity<byte[]>> oauthAuthorizationServer() {
        return proxy(HttpMethod.GET, "/.well-known/oauth-authorization-server", null, null, null);
    }

    @PostMapping("/oauth2/token")
    @Operation(summary = "OAuth2 token endpoint (refresh, token-exchange, ...)")
    public Mono<ResponseEntity<byte[]>> token(
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return withBody(exchange, (contentType, body) -> proxy(HttpMethod.POST, "/oauth2/token", contentType, body, authorization));
    }

    @PostMapping("/oauth2/introspect")
    @Operation(summary = "OAuth2 token introspection (RFC 7662)")
    public Mono<ResponseEntity<byte[]>> introspect(
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return withBody(exchange, (contentType, body) -> proxy(HttpMethod.POST, "/oauth2/introspect", contentType, body, authorization));
    }

    @GetMapping("/oauth2/userinfo")
    @Operation(summary = "OIDC UserInfo endpoint (GET)")
    public Mono<ResponseEntity<byte[]>> userInfoGet(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/oauth2/userinfo", null, null, authorization);
    }

    @PostMapping("/oauth2/userinfo")
    @Operation(summary = "OIDC UserInfo endpoint (POST)")
    public Mono<ResponseEntity<byte[]>> userInfoPost(
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return withBody(exchange, (contentType, body) -> proxy(HttpMethod.POST, "/oauth2/userinfo", contentType, body, authorization));
    }

    // ── Shared proxy helpers ─────────────────────────────────────────────────

    /**
     * Reads the raw request body + its content type (defaulting to
     * {@code application/x-www-form-urlencoded}, the OAuth2 token endpoint's
     * conventional encoding) before delegating to the Kernel call.
     */
    private Mono<ResponseEntity<byte[]>> withBody(
            ServerWebExchange exchange,
            java.util.function.BiFunction<MediaType, byte[], Mono<ResponseEntity<byte[]>>> onBody) {
        MediaType contentType = exchange.getRequest().getHeaders().getContentType() != null
                ? exchange.getRequest().getHeaders().getContentType()
                : MediaType.APPLICATION_FORM_URLENCODED;
        return exchange.getRequest().getBody()
                .reduce(DataBuffer::write)
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    org.springframework.core.io.buffer.DataBufferUtils.release(buffer);
                    return bytes;
                })
                .defaultIfEmpty(new byte[0])
                .flatMap(bytes -> onBody.apply(contentType, bytes));
    }

    private Mono<ResponseEntity<byte[]>> proxy(HttpMethod method, String kernelPath, MediaType contentType, byte[] body, String authorization) {
        return useCase.callOidc(method, kernelPath, contentType, body, authorization)
                .map(this::toResponseEntity);
    }

    private ResponseEntity<byte[]> toResponseEntity(KernelRawResponse response) {
        return ResponseEntity.status(response.status())
                .contentType(response.contentType())
                .body(response.body());
    }
}
