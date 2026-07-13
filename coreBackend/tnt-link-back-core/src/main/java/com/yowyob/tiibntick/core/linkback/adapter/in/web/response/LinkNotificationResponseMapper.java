package com.yowyob.tiibntick.core.linkback.adapter.in.web.response;

import com.yowyob.tiibntick.core.notify.domain.model.Notification;

public final class LinkNotificationResponseMapper {

    private LinkNotificationResponseMapper() {
    }

    public static LinkNotificationResponse toResponse(Notification notification) {
        return new LinkNotificationResponse(
                notification.getId().value(),
                notification.getChannel().name(),
                notification.getPriority().name(),
                notification.getContent(),
                notification.getStatus().name(),
                notification.getCreatedAt(),
                notification.getSentAt()
        );
    }
}
