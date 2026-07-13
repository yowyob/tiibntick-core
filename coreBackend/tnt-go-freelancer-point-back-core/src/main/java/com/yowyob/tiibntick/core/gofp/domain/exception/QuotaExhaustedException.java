package com.yowyob.tiibntick.core.gofp.domain.exception;

import java.util.UUID;

public class QuotaExhaustedException extends GofpException {
    public QuotaExhaustedException(UUID freelancerActorId) {
        super("GOFP_QUOTA_EXHAUSTED",
              "Quota mensuel épuisé pour le livreur : " + freelancerActorId);
    }
}
