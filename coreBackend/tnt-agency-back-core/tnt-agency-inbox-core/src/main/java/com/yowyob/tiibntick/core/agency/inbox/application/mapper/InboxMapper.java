package com.yowyob.tiibntick.core.agency.inbox.application.mapper;

import com.yowyob.tiibntick.core.agency.inbox.adapter.in.web.dto.NotificationResponse;
import com.yowyob.tiibntick.core.agency.inbox.adapter.out.persistence.entity.NotificationEntity;
import com.yowyob.tiibntick.core.agency.inbox.domain.AgencyNotification;

public final class InboxMapper {

    private InboxMapper() {}

    public static NotificationResponse toResponse(AgencyNotification n) {
        return new NotificationResponse(
                n.getId(), n.getType(), n.getEventType(),
                n.getTitle(), n.getBody(), n.getHref(),
                n.isRead(), n.getCreatedAt());
    }

    public static NotificationEntity toEntity(AgencyNotification n) {
        NotificationEntity e = new NotificationEntity();
        e.setId(n.getId());
        e.setTenantId(n.getTenantId());
        e.setAgencyId(n.getAgencyId());
        e.setType(n.getType());
        e.setEventType(n.getEventType());
        e.setTitle(n.getTitle());
        e.setBody(n.getBody());
        e.setHref(n.getHref());
        e.setRead(n.isRead());
        e.setCreatedAt(n.getCreatedAt());
        return e;
    }

    public static AgencyNotification toDomain(NotificationEntity e) {
        return new AgencyNotification(
                e.getId(), e.getTenantId(), e.getAgencyId(),
                e.getType(), e.getEventType(),
                e.getTitle(), e.getBody(), e.getHref(),
                e.isRead(), e.getCreatedAt());
    }
}
