package com.yowyob.tiibntick.core.roles.adapter.in.web.admin;

import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import com.yowyob.tiibntick.core.roles.adapter.in.web.admin.dto.CreateTntRoleRequest;
import com.yowyob.tiibntick.core.roles.adapter.in.web.admin.dto.TntRoleResponse;
import com.yowyob.tiibntick.core.roles.adapter.in.web.admin.dto.UpdateTntRoleRequest;
import com.yowyob.tiibntick.core.roles.application.port.in.ManageTntRoleUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * CRUD for tenant-defined custom (non-system) TiiBnTick roles (Chantier D · Audit n°6 ·
 * S5). Gated by {@code system:admin}, same construction as
 * {@link TntRoleAssignmentAdminController}. The 9 canonical {@code TntRole}s are not
 * manageable here — {@link ManageTntRoleUseCase} rejects any code that collides with one.
 *
 * @author MANFOUO Braun
 */
@RestController
@RequestMapping("/api/v1/admin/roles")
@RequirePermission(resource = "system", action = "admin")
@Tag(name = "Role Management Admin", description = "TNT_ADMIN-only CRUD for tenant-defined custom roles")
public class TntRoleAdminController {

    private final ManageTntRoleUseCase useCase;

    public TntRoleAdminController(ManageTntRoleUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping
    @Operation(summary = "Create a tenant-defined custom role")
    public Mono<ResponseEntity<ApiResponse<TntRoleResponse>>> create(@RequestBody CreateTntRoleRequest request) {
        return useCase.createRole(request.tenantId(), request.code(), request.name(), request.scopeType(), request.permissions())
                .map(TntRoleResponse::from)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response)));
    }

    @PatchMapping("/{roleId}")
    @Operation(summary = "Update a tenant-defined custom role's name/permissions (local-only, no Kernel sync)")
    public Mono<ResponseEntity<ApiResponse<TntRoleResponse>>> update(
            @PathVariable UUID roleId, @RequestBody UpdateTntRoleRequest request) {
        return useCase.updateRole(request.tenantId(), roleId, request.name(), request.permissions())
                .map(TntRoleResponse::from)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    @DeleteMapping("/{roleId}")
    @Operation(summary = "Delete a tenant-defined custom role")
    public Mono<ResponseEntity<Void>> delete(@PathVariable UUID roleId, @RequestParam UUID tenantId) {
        return useCase.deleteRole(tenantId, roleId)
                .thenReturn(ResponseEntity.noContent().<Void>build());
    }

    @GetMapping
    @Operation(summary = "List every role (canonical + custom) provisioned for a tenant")
    public Flux<TntRoleResponse> list(@RequestParam UUID tenantId) {
        return useCase.listRoles(tenantId).map(TntRoleResponse::from);
    }
}
