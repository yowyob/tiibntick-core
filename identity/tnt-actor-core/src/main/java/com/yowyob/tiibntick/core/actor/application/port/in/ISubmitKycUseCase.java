package com.yowyob.tiibntick.core.actor.application.port.in;

import com.yowyob.tiibntick.core.actor.application.command.SubmitKycCommand;
import reactor.core.publisher.Mono;

public interface ISubmitKycUseCase {
    Mono<Void> submitKyc(SubmitKycCommand command);
}
