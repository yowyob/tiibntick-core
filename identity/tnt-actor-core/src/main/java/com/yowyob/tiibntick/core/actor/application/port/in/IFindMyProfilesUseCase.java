package com.yowyob.tiibntick.core.actor.application.port.in;

import com.yowyob.tiibntick.core.actor.domain.model.ProfileRef;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Every TiiBnTick profile held by one account, whichever space (tenant) it was created in.
 * Discovery relies on the account identity only — never on the roles carried by the token, which do not
 * reflect TiiBnTick profiles.
 */
public interface IFindMyProfilesUseCase {

    Flux<ProfileRef> findAll(UUID tenantId, UUID actorId);
}
