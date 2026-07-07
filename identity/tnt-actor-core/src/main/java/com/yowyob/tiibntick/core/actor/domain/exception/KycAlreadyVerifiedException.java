package com.yowyob.tiibntick.core.actor.domain.exception;

import java.util.UUID;

public class KycAlreadyVerifiedException extends RuntimeException {

    public KycAlreadyVerifiedException(UUID actorId) {
        super("KYC is already verified for actor: " + actorId);
    }
}
