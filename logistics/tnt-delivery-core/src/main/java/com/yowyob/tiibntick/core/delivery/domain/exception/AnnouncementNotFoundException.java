package com.yowyob.tiibntick.core.delivery.domain.exception;

import java.util.UUID;

/**
 * Thrown when a delivery announcement cannot be found.
 *
 * @author MANFOUO Braun
 */
public class AnnouncementNotFoundException extends DeliveryDomainException {

    public AnnouncementNotFoundException(UUID id) {
        super("Announcement not found for id: " + id);
    }
}
