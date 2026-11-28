package com.yowyob.tiibntick.core.roles.adapter.in.web.admin;

import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import com.yowyob.tiibntick.core.roles.adapter.in.web.admin.dto.ForeignTenantRevocationRequest;
import com.yowyob.tiibntick.core.roles.adapter.in.web.admin.dto.GrantPlatformAdminRequest;
import com.yowyob.tiibntick.core.roles.adapter.in.web.admin.dto.PlatformAdminGrantResponse;
import com.yowyob.tiibntick.core.roles.application.port.in.ManagePlatformAdminsUseCase;
import com.yowyob.tiibntick.core.roles.application.service.PlatformAdminCallers;
import com.yowyob.tiibntick.core.roles.config.TntRolesProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * TiiBnTick platform administrators (TNT_ADMIN), held by the Core registry whatever the tenant of the login
 * (decisions D-159 to D-175, {@code docs/architecture/chantiers/registre-administrateurs-plateforme.md}).
 * Gated by {@code system:admin}, which only an administrator holds; the finer rules (protected owner grant, mass
 * revocation reserved to the owner) are enforced by the service.
 *
 * @author MANFOUO Braun
 */
@RestController
@RequestMapping("/api/v1/admin/platform-admins")
@RequirePermission(resource = "system", action = "admin")
@Tag(name = "Platform Admins", description = "TNT_ADMIN registry: grant, revoke, owner, foreign-tenant revocation")
public class PlatformAdminController {

    private final ManagePlatformAdminsUseCase admins;
    private final UUID systemTenantId;

    public PlatformAdminController(ManagePlatformAdminsUseCase admins, TntRolesProperties properties) {
        this.admins = admins;
        this.systemTenantId = properties.getSystemTenantId();
    }

    @GetMapping
    @Operation(summary = "List the TNT_ADMIN grants (active, or the whole history)")
    public Mono<List<PlatformAdminGrantResponse>> list(
            @RequestParam(name = "includeRevoked", defaultValue = "false") boolean includeRevoked) {
        return admins.list(includeRevoked).map(this::view).collectList();
    }

    @GetMapping("/{grantId}")
    @Operation(summary = "One TNT_ADMIN grant")
    public Mono<PlatformAdminGrantResponse> get(@PathVariable UUID grantId) {
        return admins.find(grantId).map(this::view);
    }

    @PostMapping
    @Operation(summary = "Grant TNT_ADMIN to a Kernel user (no membership of the system tenant needed)")
    public Mono<ResponseEntity<PlatformAdminGrantResponse>> grant(@RequestBody GrantPlatformAdminRequest body) {
        return caller().flatMap(c -> admins.grant(c, body == null ? null : body.targetUserId()))
                .map(g -> ResponseEntity.status(HttpStatus.CREATED).body(view(g)));
    }

    @DeleteMapping("/{grantId}")
    @Operation(summary = "Revoke an ASSIGNED TNT_ADMIN grant (the system-tenant owner's grant answers 409)")
    public Mono<PlatformAdminGrantResponse> revoke(@PathVariable UUID grantId,
                                                   @RequestParam(name = "reason", required = false) String reason) {
        return caller().flatMap(c -> admins.revoke(c, grantId, reason)).map(this::view);
    }

    @PostMapping("/claim-ownership")
    @Operation(summary = "Record the protected SYSTEM_OWNER grant of the system-tenant owner (also automatic at login)")
    public Mono<PlatformAdminGrantResponse> claimOwnership() {
        return caller().flatMap(admins::claimOwnership).map(this::view);
    }

    @PostMapping("/revocations/foreign-tenant/preview")
    @Operation(summary = "Preview the revocation of every TNT_ADMIN obtained from another tenant (owner only)")
    public Mono<Map<String, Object>> preview() {
        return caller().flatMap(admins::previewForeignTenantRevocation).map(p -> Map.of(
                "grants", p.grants().stream().map(this::view).toList(),
                "confirmationToken", p.confirmationToken(),
                "expiresAt", (Object) p.expiresAt()));
    }

    @PostMapping("/revocations/foreign-tenant")
    @Operation(summary = "Revoke exactly the previewed TNT_ADMIN grants (owner only)")
    public Mono<Map<String, Object>> revokeForeignTenant(@RequestBody ForeignTenantRevocationRequest body) {
        if (body == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Body required"));
        }
        return caller().flatMap(c -> admins.revokeForeignTenant(c, body.confirmationToken(), body.reason()))
                .map(r -> Map.of("batchId", r.batchId(),
                        "revoked", r.revoked().stream().map(this::view).toList(),
                        "at", (Object) Instant.now()));
    }

    private Mono<ManagePlatformAdminsUseCase.Caller> caller() {
        return PlatformAdminCallers.current().switchIfEmpty(Mono.error(
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required")));
    }

    private PlatformAdminGrantResponse view(com.yowyob.tiibntick.core.roles.domain.model.PlatformRoleGrant g) {
        return PlatformAdminGrantResponse.of(g, systemTenantId);
    }
}
