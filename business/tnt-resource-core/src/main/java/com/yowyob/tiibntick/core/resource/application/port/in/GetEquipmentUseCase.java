package com.yowyob.tiibntick.core.resource.application.port.in;

import com.yowyob.tiibntick.core.resource.domain.model.Equipment;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Inbound port: retrieve a single piece of equipment by id.
 * @author MANFOUO Braun.
 */
public interface GetEquipmentUseCase {
    Mono<Equipment> getEquipment(UUID tenantId, UUID equipmentId);
}
