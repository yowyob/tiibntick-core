package com.yowyob.tiibntick.core.resource.application.port.in;

import com.yowyob.tiibntick.core.resource.domain.model.Equipment;
import reactor.core.publisher.Mono;

/**
 * Inbound port: assign equipment to a user.
 * @author MANFOUO Braun.
 */
public interface AssignEquipmentUseCase {
    Mono<Equipment> assignEquipment(AssignEquipmentCommand command);
}
