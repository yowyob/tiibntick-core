package com.yowyob.tiibntick.core.actor.application.port.in;

import com.yowyob.tiibntick.core.actor.application.command.AssociateFreelancerCommand;
import com.yowyob.tiibntick.core.actor.domain.model.FreelancerProfile;
import reactor.core.publisher.Mono;

public interface IAssociateFreelancerUseCase {
    Mono<FreelancerProfile> associate(AssociateFreelancerCommand command);
}
