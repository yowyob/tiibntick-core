package com.yowyob.tiibntick.core.actor.application.port.in;

import com.yowyob.tiibntick.core.actor.application.command.CreateFreelancerProfileCommand;
import com.yowyob.tiibntick.core.actor.domain.model.FreelancerProfile;
import reactor.core.publisher.Mono;

public interface ICreateFreelancerProfileUseCase {
    Mono<FreelancerProfile> createFreelancerProfile(CreateFreelancerProfileCommand command);
}
