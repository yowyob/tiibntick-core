package com.yowyob.tiibntick.core.actor.application.port.in;

import com.yowyob.tiibntick.core.actor.application.command.DissociateFreelancerCommand;
import com.yowyob.tiibntick.core.actor.domain.model.FreelancerProfile;
import reactor.core.publisher.Mono;

public interface IDissociateFreelancerUseCase {
    Mono<FreelancerProfile> dissociate(DissociateFreelancerCommand command);
}
