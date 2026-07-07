package com.yowyob.tiibntick.core.resource.application.port.in;

import com.yowyob.tiibntick.core.resource.domain.model.FreelancerEquipment;
import reactor.core.publisher.Mono;

/**
 * Inbound port: adds specialized equipment to a FreelancerOrganization's inventory.
 * @author MANFOUO Braun
 */
public interface AddFreelancerEquipmentUseCase {
    Mono<FreelancerEquipment> addEquipment(AddFreelancerEquipmentCommand cmd);
}
