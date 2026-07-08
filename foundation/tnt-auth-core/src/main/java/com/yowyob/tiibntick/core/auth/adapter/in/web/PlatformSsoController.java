package com.yowyob.tiibntick.core.auth.adapter.in.web;

import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.core.auth.application.port.in.ProxyKernelSsoUseCase;
import com.yowyob.tiibntick.core.auth.domain.model.ResolveSsoContextRequest;
import com.yowyob.tiibntick.core.auth.domain.model.ResolveSsoContextResponse;
import com.yowyob.tiibntick.core.auth.domain.model.SsoLaunchRequest;
import com.yowyob.tiibntick.core.auth.domain.model.SsoLaunchResponse;
import com.yowyob.tiibntick.core.auth.domain.model.SsoTokenExchangeRequest;
import com.yowyob.tiibntick.core.auth.domain.model.SsoTokenExchangeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * YowYob SSO handshake for platform backends — see {@code CORE_KERNEL_GATEWAY_SPEC.md} §7.
 * Registered public (platform {@code X-Client-Id}/{@code X-Api-Key} required, see
 * {@code TntAuthGatewaySecurityConfig}), no TiiBnTick JWT — the platform is mid-handshake
 * and only holds a Kernel shared-session token at this point.
 *
 * @author MANFOUO Braun
 */
@RestController
@RequestMapping("/api/v1/sso")
@Tag(name = "Platform SSO Gateway", description = "YowYob SSO handshake proxy for platform backends")
public class PlatformSsoController {

    private final ProxyKernelSsoUseCase useCase;

    public PlatformSsoController(ProxyKernelSsoUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping("/context/resolve")
    @Operation(summary = "Resolve the SSO context (contextId) for a Kernel organization")
    public Mono<ResponseEntity<ApiResponse<ResolveSsoContextResponse>>> resolveContext(
            @RequestBody ResolveSsoContextRequest request,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return useCase.resolveContext(request, authorization)
                .map(result -> ResponseEntity.ok(ApiResponse.success(result)));
    }

    @PostMapping("/token/exchange")
    @Operation(summary = "Exchange a resolved SSO context for a service-scoped access token")
    public Mono<ResponseEntity<ApiResponse<SsoTokenExchangeResponse>>> exchangeToken(
            @RequestBody SsoTokenExchangeRequest request) {
        return useCase.exchangeToken(request)
                .map(result -> ResponseEntity.ok(ApiResponse.success(result)));
    }

    @PostMapping("/yowyob/launch")
    @Operation(summary = "Composite: resolve context + exchange token + build the app's SSO redirect URL")
    public Mono<ResponseEntity<ApiResponse<SsoLaunchResponse>>> launch(
            @RequestBody SsoLaunchRequest request) {
        return useCase.launch(request)
                .map(result -> ResponseEntity.ok(ApiResponse.success(result)));
    }
}
