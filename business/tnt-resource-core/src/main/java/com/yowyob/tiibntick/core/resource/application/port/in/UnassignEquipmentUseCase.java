package com.yowyob.tiibntick.core.resource.application.port.in;

import com.yowyob.tiibntick.core.resource.domain.model.Equipment;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Inbound port: release equipment from its current user.
 * @author MANFOUO Braun.
 */
public interface UnassignEquipmentUseCase {
    Mono<Equipment> unassignEquipment(UUID tenantId, UUID equipmentId);
}
