package com.yowyob.tiibntick.core.tp.domain.exception;

import java.util.UUID;

/**
 * Thrown when attempting a KYC action on an already verified profile.
 *
 * @author MANFOUO Braun
 */
public class KycAlreadyVerifiedException extends RuntimeException {

    public KycAlreadyVerifiedException(UUID thirdPartyId) {
        super("Third party with id=" + thirdPartyId + " is already KYC verified.");
    }
}
