package com.yowyob.tiibntick.core.resource.application.service;

import com.yowyob.tiibntick.core.resource.application.port.in.*;
import com.yowyob.tiibntick.core.resource.application.port.out.EquipmentRepository;
import com.yowyob.tiibntick.core.resource.application.port.out.ResourceEventPublisherPort;
import com.yowyob.tiibntick.core.resource.domain.event.EquipmentAssignedEvent;
import com.yowyob.tiibntick.core.resource.domain.exception.EquipmentNotFoundException;
import com.yowyob.tiibntick.core.resource.domain.model.Equipment;
import com.yowyob.tiibntick.core.resource.domain.model.EquipmentStatus;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Application service managing the lifecycle of operational equipment in TiiBnTick.
 *
 * @author MANFOUO Braun
 */
@Service
public class EquipmentApplicationService implements
        CreateEquipmentUseCase,
        AssignEquipmentUseCase,
        UnassignEquipmentUseCase,
        GetEquipmentUseCase,
        ListEquipmentByBranchUseCase {

    private final EquipmentRepository equipmentRepository;
    private final ResourceEventPublisherPort eventPublisher;

    public EquipmentApplicationService(EquipmentRepository equipmentRepository,
            ResourceEventPublisherPort eventPublisher) {
        this.equipmentRepository = equipmentRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @RequirePermission(resource = "resource", action = "write")
    public Mono<Equipment> createEquipment(CreateEquipmentCommand cmd) {
        return equipmentRepository.existsBySerialNumber(cmd.tenantId(), cmd.serialNumber())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                                "Serial number already registered: " + cmd.serialNumber()));
                    }
                    Equipment eq = Equipment.register(cmd.tenantId(), cmd.organizationId(),
                            cmd.branchId(), cmd.type(), cmd.serialNumber(), cmd.description(),
                            cmd.purchasedAt(), cmd.warrantyExpiresAt());
                    return equipmentRepository.save(eq);
                });
    }

    @Transactional
    @Override
    @RequirePermission(resource = "resource", action = "reserve")
    public Mono<Equipment> assignEquipment(AssignEquipmentCommand cmd) {
        return loadEquipment(cmd.tenantId(), cmd.equipmentId())
                .map(eq -> eq.assign(cmd.userId()))
                .flatMap(equipmentRepository::save)
                .flatMap(saved -> eventPublisher.publish(EquipmentAssignedEvent.of(
                        saved.id(), saved.tenantId(), saved.branchId(),
                        saved.type(), cmd.userId()))
                        .thenReturn(saved));
    }

    @Override
    @RequirePermission(resource = "resource", action = "reserve")
    public Mono<Equipment> unassignEquipment(UUID tenantId, UUID equipmentId) {
        return loadEquipment(tenantId, equipmentId)
                .map(Equipment::unassign)
                .flatMap(equipmentRepository::save);
    }

    @Override
    @RequirePermission(resource = "resource", action = "read")
    public Mono<Equipment> getEquipment(UUID tenantId, UUID equipmentId) {
        return loadEquipment(tenantId, equipmentId);
    }

    @Override
    @RequirePermission(resource = "resource", action = "read")
    public Flux<Equipment> listByBranch(UUID tenantId, UUID branchId, EquipmentStatus statusFilter) {
        if (statusFilter != null) {
            return equipmentRepository.findByBranchAndStatus(tenantId, branchId, statusFilter);
        }
        return equipmentRepository.findByBranch(tenantId, branchId);
    }

    private Mono<Equipment> loadEquipment(UUID tenantId, UUID equipmentId) {
        return equipmentRepository.findById(tenantId, equipmentId)
                .switchIfEmpty(Mono.error(new EquipmentNotFoundException(equipmentId)));
    }
}
