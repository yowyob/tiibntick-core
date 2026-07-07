package com.yowyob.tiibntick.core.actor.application.port.in;

import com.yowyob.tiibntick.core.actor.application.command.ValidateKycCommand;
import reactor.core.publisher.Mono;

public interface IValidateKycUseCase {
    Mono<Void> validateKyc(ValidateKycCommand command);
}
