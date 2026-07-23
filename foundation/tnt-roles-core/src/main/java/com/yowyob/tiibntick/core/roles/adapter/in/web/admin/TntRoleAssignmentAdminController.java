package com.yowyob.tiibntick.core.roles.adapter.in.web.admin;

import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import com.yowyob.tiibntick.core.roles.adapter.in.web.admin.dto.AssignTntRoleRequest;
import com.yowyob.tiibntick.core.roles.adapter.in.web.admin.dto.TntRoleAssignmentResponse;
import com.yowyob.tiibntick.core.roles.application.port.in.AssignTntRoleUseCase;
import com.yowyob.tiibntick.core.roles.application.port.in.RevokeTntRoleUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Grants one of TiiBnTick's canonical roles (see {@code TntRole}, incl. {@code TNT_ADMIN})
 * to a Kernel user. Gated by {@code system:admin}
 * (see {@code TntPermission.SYSTEM_ADMIN}), a permission deliberately not included in
 * any non-admin {@code TntRole}'s default set — only the {@code TNT_ADMIN} wildcard
 * satisfies it, same construction as {@code PlatformClientAdminController}'s
 * {@code platform:clients} gate.
 *
 * <p>Any caller already holding {@code TNT_ADMIN} may use this endpoint, whether they
 * obtained it by owning TiiBnTick's system tenant in the Kernel (see
 * {@code TntSecurityConfig#tntJwtAuthenticationConverter}) or via a prior assignment
 * made through this same endpoint — there is no additional restriction on who may grant
 * {@code TNT_ADMIN} to someone else.
 *
 * @author MANFOUO Braun
 */
@RestController
@RequestMapping("/api/v1/admin/roles")
@RequirePermission(resource = "system", action = "admin")
@Tag(name = "Role Assignment Admin", description = "TNT_ADMIN-only granting of TiiBnTick canonical roles")
public class TntRoleAssignmentAdminController {

    private final AssignTntRoleUseCase assignUseCase;
    private final RevokeTntRoleUseCase revokeUseCase;

    public TntRoleAssignmentAdminController(AssignTntRoleUseCase assignUseCase, RevokeTntRoleUseCase revokeUseCase) {
        this.assignUseCase = assignUseCase;
        this.revokeUseCase = revokeUseCase;
    }

    @PostMapping("/assignments")
    @Operation(summary = "Assign a TiiBnTick canonical role (e.g. TNT_ADMIN) to a Kernel user")
    public Mono<ResponseEntity<ApiResponse<TntRoleAssignmentResponse>>> assign(
            @RequestBody AssignTntRoleRequest request) {
        return assignUseCase.assignRole(request.tenantId(), request.targetUserId(), request.roleCode(), request.scopeId())
                .map(TntRoleAssignmentResponse::from)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response)));
    }

    @DeleteMapping("/assignments/{assignmentId}")
    @Operation(summary = "Revoke a previously granted role assignment")
    public Mono<ResponseEntity<Void>> revoke(@PathVariable UUID assignmentId, @RequestParam UUID tenantId) {
        return revokeUseCase.revokeAssignment(tenantId, assignmentId)
                .thenReturn(ResponseEntity.noContent().<Void>build());
    }
}
