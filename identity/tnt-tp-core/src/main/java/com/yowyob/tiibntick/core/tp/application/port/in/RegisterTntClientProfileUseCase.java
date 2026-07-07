package com.yowyob.tiibntick.core.tp.application.port.in;

import com.yowyob.tiibntick.core.tp.application.port.in.command.RegisterTntClientProfileCommand;
import com.yowyob.tiibntick.core.tp.domain.model.TntClientProfile;
import reactor.core.publisher.Mono;

/**
 * Use case: Register a new TiiBnTick client profile for an existing kernel ThirdParty.
 *
 * @author MANFOUO Braun
 */
public interface RegisterTntClientProfileUseCase {

    Mono<TntClientProfile> register(RegisterTntClientProfileCommand command);
}
