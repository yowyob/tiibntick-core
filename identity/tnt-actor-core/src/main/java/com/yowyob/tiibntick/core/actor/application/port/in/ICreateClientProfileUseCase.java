package com.yowyob.tiibntick.core.actor.application.port.in;

import com.yowyob.tiibntick.core.actor.application.command.CreateClientProfileCommand;
import com.yowyob.tiibntick.core.actor.domain.model.ClientProfile;
import reactor.core.publisher.Mono;

public interface ICreateClientProfileUseCase {
    Mono<ClientProfile> createClientProfile(CreateClientProfileCommand command);
}
