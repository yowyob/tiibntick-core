package com.yowyob.tiibntick.core.actor.application.port.in;

import com.yowyob.tiibntick.core.actor.application.command.CreateDelivererProfileCommand;
import com.yowyob.tiibntick.core.actor.domain.model.DelivererProfile;
import reactor.core.publisher.Mono;

public interface ICreateDelivererProfileUseCase {
    Mono<DelivererProfile> createDelivererProfile(CreateDelivererProfileCommand command);
}
