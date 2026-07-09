package com.yowyob.tiibntick.core.platformgateway.adapter.in.web.admin;

import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.platformgateway.adapter.in.web.admin.dto.IssuedApiKeyResponse;
import com.yowyob.tiibntick.core.platformgateway.adapter.in.web.admin.dto.RevokeApiKeyRequest;
import com.yowyob.tiibntick.core.platformgateway.adapter.in.web.admin.dto.RotateApiKeyRequest;
import com.yowyob.tiibntick.core.platformgateway.application.port.in.PlatformClientAdminUseCase;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

/**
 * API-key rotation and revocation — usable ONLY by {@code TNT_ADMIN} (see
 * {@code docs/auth/platform-client-management-design.md} §4/§5.3). Same
 * JWT-authenticated (not platform-gateway) security perimeter as
 * {@code PlatformClientAdminController}.
 *
 * @author MANFOUO Braun
 */
@RestController
@RequestMapping("/api/v1/admin/api-keys")
@RequirePermission(resource = "platform", action = "clients")
@Tag(name = "Platform Client Admin", description = "TNT_ADMIN-only API key rotation/revocation")
public class ApiKeyAdminController {

    private final PlatformClientAdminUseCase useCase;

    public ApiKeyAdminController(PlatformClientAdminUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping("/{keyId}/rotate")
    @Operation(summary = "Issue a replacement key; old key stays valid (ROTATING) for the grace window")
    public Mono<ResponseEntity<ApiResponse<IssuedApiKeyResponse>>> rotate(
            @PathVariable UUID keyId,
            @RequestBody(required = false) RotateApiKeyRequest request,
            @CurrentUser TntSecurityContext admin) {
        Duration grace = request != null && request.graceHours() != null
                ? Duration.ofHours(request.graceHours()) : Duration.ofHours(24);
        String reason = request != null ? request.reason() : null;
        return useCase.rotateApiKey(keyId, grace, reason, actorId(admin))
                .map(IssuedApiKeyResponse::from)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response)));
    }

    @PostMapping("/{keyId}/revoke")
    @Operation(summary = "Immediate revocation — incident response, no grace window")
    public Mono<ResponseEntity<Void>> revoke(
            @PathVariable UUID keyId,
            @RequestBody RevokeApiKeyRequest request,
            @CurrentUser TntSecurityContext admin) {
        return useCase.revokeApiKey(keyId, request.reason(), actorId(admin))
                .thenReturn(ResponseEntity.ok().<Void>build());
    }

    private static String actorId(TntSecurityContext admin) {
        return admin != null && admin.userId() != null ? admin.userId().toString() : null;
    }
}
