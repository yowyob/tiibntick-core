package com.yowyob.tiibntick.core.resource.adapter.in.web;

import com.yowyob.tiibntick.core.resource.application.port.in.AssignEquipmentCommand;
import com.yowyob.tiibntick.core.resource.application.port.in.AssignEquipmentUseCase;
import com.yowyob.tiibntick.core.resource.application.port.in.CreateEquipmentCommand;
import com.yowyob.tiibntick.core.resource.application.port.in.CreateEquipmentUseCase;
import com.yowyob.tiibntick.core.resource.application.port.in.GetEquipmentUseCase;
import com.yowyob.tiibntick.core.resource.application.port.in.ListEquipmentByBranchUseCase;
import com.yowyob.tiibntick.core.resource.application.port.in.UnassignEquipmentUseCase;
import com.yowyob.tiibntick.core.resource.domain.model.EquipmentType;
import com.yowyob.tiibntick.core.resource.domain.model.EquipmentStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

@Component
public class EquipmentHandler {

    private final CreateEquipmentUseCase createEquipmentUseCase;
    private final GetEquipmentUseCase getEquipmentUseCase;
    private final ListEquipmentByBranchUseCase listEquipmentByBranchUseCase;
    private final AssignEquipmentUseCase assignEquipmentUseCase;
    private final UnassignEquipmentUseCase unassignEquipmentUseCase;

    public EquipmentHandler(CreateEquipmentUseCase createEquipmentUseCase,
                            GetEquipmentUseCase getEquipmentUseCase,
                            ListEquipmentByBranchUseCase listEquipmentByBranchUseCase,
                            AssignEquipmentUseCase assignEquipmentUseCase,
                            UnassignEquipmentUseCase unassignEquipmentUseCase) {
        this.createEquipmentUseCase = createEquipmentUseCase;
        this.getEquipmentUseCase = getEquipmentUseCase;
        this.listEquipmentByBranchUseCase = listEquipmentByBranchUseCase;
        this.assignEquipmentUseCase = assignEquipmentUseCase;
        this.unassignEquipmentUseCase = unassignEquipmentUseCase;
    }

    public Mono<ServerResponse> createEquipment(ServerRequest request) {
        return request.bodyToMono(CreateEquipmentRequest.class)
                .flatMap(body -> {
                    UUID tenantId = UUID.fromString(request.headers().firstHeader("X-Tenant-Id"));
                    CreateEquipmentCommand cmd = new CreateEquipmentCommand(
                            tenantId,
                            body.organizationId() != null ? UUID.fromString(body.organizationId()) : null,
                            body.agencyId() != null ? UUID.fromString(body.agencyId()) : null,
                            EquipmentType.valueOf(body.type()),
                            body.serialNumber(),
                            body.description(),
                            body.purchasedAt() != null ? LocalDate.parse(body.purchasedAt()) : null,
                            body.warrantyExpiresAt() != null ? LocalDate.parse(body.warrantyExpiresAt()) : null
                    );
                    return createEquipmentUseCase.createEquipment(cmd);
                })
                .flatMap(equipment -> ServerResponse.status(HttpStatus.CREATED).bodyValue(equipment))
                .onErrorResume(IllegalArgumentException.class,
                        e -> ServerResponse.badRequest().bodyValue(new ErrorResponse(e.getMessage())));
    }

    public Mono<ServerResponse> getEquipment(ServerRequest request) {
        UUID tenantId = UUID.fromString(request.headers().firstHeader("X-Tenant-Id"));
        UUID equipmentId = UUID.fromString(request.pathVariable("id"));
        return getEquipmentUseCase.getEquipment(tenantId, equipmentId)
                .flatMap(equipment -> ServerResponse.ok().bodyValue(equipment))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> listEquipmentByBranch(ServerRequest request) {
        UUID tenantId = UUID.fromString(request.headers().firstHeader("X-Tenant-Id"));
        UUID branchId = UUID.fromString(request.pathVariable("branchId"));
        EquipmentStatus status = request.queryParam("status")
                .map(EquipmentStatus::valueOf).orElse(null);
        return listEquipmentByBranchUseCase.listByBranch(tenantId, branchId, status)
                .collectList()
                .flatMap(list -> ServerResponse.ok().bodyValue(list));
    }

    public Mono<ServerResponse> assignEquipment(ServerRequest request) {
        UUID tenantId = UUID.fromString(request.headers().firstHeader("X-Tenant-Id"));
        UUID equipmentId = UUID.fromString(request.pathVariable("id"));
        return request.bodyToMono(AssignEquipmentRequest.class)
                .flatMap(body -> {
                    AssignEquipmentCommand cmd = new AssignEquipmentCommand(
                            tenantId,
                            equipmentId,
                            body.userId() != null ? UUID.fromString(body.userId()) : null
                    );
                    return assignEquipmentUseCase.assignEquipment(cmd);
                })
                .then(ServerResponse.noContent().build())
                .onErrorResume(IllegalStateException.class,
                        e -> ServerResponse.status(HttpStatus.CONFLICT).bodyValue(new ErrorResponse(e.getMessage())));
    }

    public Mono<ServerResponse> unassignEquipment(ServerRequest request) {
        UUID tenantId = UUID.fromString(request.headers().firstHeader("X-Tenant-Id"));
        UUID equipmentId = UUID.fromString(request.pathVariable("id"));
        return unassignEquipmentUseCase.unassignEquipment(tenantId, equipmentId)
                .then(ServerResponse.noContent().build())
                .onErrorResume(IllegalStateException.class,
                        e -> ServerResponse.status(HttpStatus.CONFLICT).bodyValue(new ErrorResponse(e.getMessage())));
    }

    record CreateEquipmentRequest(
            String organizationId,
            String agencyId,
            String type,
            String serialNumber,
            String description,
            String purchasedAt,
            String warrantyExpiresAt
    ) {}

    record AssignEquipmentRequest(String userId, String branchId) {}

    record ErrorResponse(String message) {}
}
