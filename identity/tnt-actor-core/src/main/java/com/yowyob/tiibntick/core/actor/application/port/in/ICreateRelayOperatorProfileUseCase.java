package com.yowyob.tiibntick.core.actor.application.port.in;

import com.yowyob.tiibntick.core.actor.application.command.CreateRelayOperatorProfileCommand;
import com.yowyob.tiibntick.core.actor.domain.model.RelayOperatorProfile;
import reactor.core.publisher.Mono;

public interface ICreateRelayOperatorProfileUseCase {
    Mono<RelayOperatorProfile> createRelayOperatorProfile(CreateRelayOperatorProfileCommand command);
}
