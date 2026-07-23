package com.yowyob.tiibntick.core.resource.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.resource.application.port.in.*;
import com.yowyob.tiibntick.core.resource.domain.model.Equipment;
import com.yowyob.tiibntick.core.resource.domain.model.EquipmentStatus;
import com.yowyob.tiibntick.core.resource.domain.model.EquipmentType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

/**
 * REST controller for equipment lifecycle management.
 * Path aligned with Kernel Core's resource management convention at {@code /api/resources/equipment}.
 *
 * @author MANFOUO Braun
 */
@Tag(name = "Equipment", description = "Equipment registration, assignment and tracking")
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class EquipmentController {

    private final CreateEquipmentUseCase createEquipmentUseCase;
    private final GetEquipmentUseCase getEquipmentUseCase;
    private final ListEquipmentByBranchUseCase listEquipmentByBranchUseCase;
    private final AssignEquipmentUseCase assignEquipmentUseCase;
    private final UnassignEquipmentUseCase unassignEquipmentUseCase;

    @Operation(summary = "Register new equipment")
    @PostMapping("/api/resources/equipment")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public Mono<Equipment> createEquipment(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestBody CreateEquipmentRequest body) {
        CreateEquipmentCommand cmd = new CreateEquipmentCommand(
                currentUser.tenantId(),
                body.organizationId() != null ? UUID.fromString(body.organizationId()) : null,
                body.agencyId() != null ? UUID.fromString(body.agencyId()) : null,
                EquipmentType.valueOf(body.type()),
                body.serialNumber(),
                body.description(),
                body.purchasedAt() != null ? LocalDate.parse(body.purchasedAt()) : null,
                body.warrantyExpiresAt() != null ? LocalDate.parse(body.warrantyExpiresAt()) : null);
        return createEquipmentUseCase.createEquipment(cmd);
    }

    @Operation(summary = "Get equipment by ID")
    @GetMapping("/api/resources/equipment/{equipmentId}")
    public Mono<Equipment> getEquipment(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID equipmentId) {
        return getEquipmentUseCase.getEquipment(currentUser.tenantId(), equipmentId);
    }

    @Operation(summary = "List equipment for a branch")
    @GetMapping("/api/resources/branches/{branchId}/equipment")
    public Flux<Equipment> listByBranch(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID branchId,
            @RequestParam(required = false) String status) {
        EquipmentStatus statusFilter = status != null ? EquipmentStatus.valueOf(status) : null;
        return listEquipmentByBranchUseCase.listByBranch(currentUser.tenantId(), branchId, statusFilter);
    }

    @Operation(summary = "Assign equipment to a user or branch")
    @PostMapping("/api/resources/equipment/{equipmentId}/assign")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    public Mono<Void> assignEquipment(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID equipmentId,
            @RequestBody AssignEquipmentRequest body) {
        AssignEquipmentCommand cmd = new AssignEquipmentCommand(
                currentUser.tenantId(),
                equipmentId,
                body.userId() != null ? UUID.fromString(body.userId()) : null);
        return assignEquipmentUseCase.assignEquipment(cmd).then();
    }

    @Operation(summary = "Unassign equipment from its current user")
    @DeleteMapping("/api/resources/equipment/{equipmentId}/assign")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    public Mono<Void> unassignEquipment(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID equipmentId) {
        return unassignEquipmentUseCase.unassignEquipment(currentUser.tenantId(), equipmentId).then();
    }

    // ── Request DTOs ──────────────────────────────────────────────────────────

    public record CreateEquipmentRequest(
            String organizationId,
            String agencyId,
            String type,
            String serialNumber,
            String description,
            String purchasedAt,
            String warrantyExpiresAt) {}

    public record AssignEquipmentRequest(String userId, String branchId) {}
}
