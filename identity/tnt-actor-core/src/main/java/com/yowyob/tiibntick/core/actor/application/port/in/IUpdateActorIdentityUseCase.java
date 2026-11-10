package com.yowyob.tiibntick.core.actor.application.port.in;

import com.yowyob.tiibntick.core.actor.domain.model.ActorIdentitySummary;
import com.yowyob.tiibntick.core.actor.domain.model.UpdateActorIdentityCommand;
import reactor.core.publisher.Mono;

/**
 * Persists the authenticated caller's personal identity (first/last name, phone) on their
 * Kernel actor. Identity is owned by the Kernel — nothing is duplicated in the Core database.
 */
public interface IUpdateActorIdentityUseCase {

    Mono<ActorIdentitySummary> updateCurrent(UpdateActorIdentityCommand command);
}
