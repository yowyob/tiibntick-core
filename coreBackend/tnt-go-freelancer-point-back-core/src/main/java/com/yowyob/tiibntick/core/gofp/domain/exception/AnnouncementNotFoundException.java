package com.yowyob.tiibntick.core.gofp.domain.exception;

import java.util.UUID;

public class AnnouncementNotFoundException extends GofpException {
    public AnnouncementNotFoundException(UUID id) {
        super("GOFP_ANN_NOT_FOUND", "Annonce introuvable : " + id);
    }
}
