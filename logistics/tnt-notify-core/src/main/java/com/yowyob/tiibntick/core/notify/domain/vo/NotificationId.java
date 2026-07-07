package com.yowyob.tiibntick.core.notify.domain.vo;

import java.util.UUID;

/**
 * @author Dilane PAFE
 * @author MANFOUO Braun
 */
public record NotificationId(String value) {
    public static NotificationId generate() {
        return new NotificationId(UUID.randomUUID().toString());
    }
}
