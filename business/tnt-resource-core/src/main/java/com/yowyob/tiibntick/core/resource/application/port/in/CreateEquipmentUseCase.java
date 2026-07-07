package com.yowyob.tiibntick.core.resource.application.port.in;

import com.yowyob.tiibntick.core.resource.domain.model.Equipment;
import reactor.core.publisher.Mono;

/**
 * Inbound port: register a new piece of equipment.
 * @author MANFOUO Braun.
 */
public interface CreateEquipmentUseCase {
    Mono<Equipment> createEquipment(CreateEquipmentCommand command);
}
