package com.yowyob.tiibntick.core.gofreelancer.adapter.out.delivery;

import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IAnnouncementRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryAnnouncementPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementExtras;
import com.yowyob.tiibntick.core.gofreelancer.application.service.AnnouncementViews;
import com.yowyob.tiibntick.core.gofreelancer.application.service.TenantContextHolder;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Announcement;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Reads announcements from delivery-core in the shape of the legacy {@link Announcement} view. */
@Component
@RequiredArgsConstructor
public class CoreAnnouncementReadAdapter implements IAnnouncementRepository {

    private final IDeliveryAnnouncementPort port;
    private final TenantContextHolder tenantContextHolder;

    @Override
    public Mono<Announcement> findById(UUID id) {
        return tenantContextHolder.currentTenantId()
                .flatMap(tenantId -> port.findById(tenantId, id))
                .map(AnnouncementViews::toLocal);
    }

    @Override
    public Mono<Announcement> findByDeliveryNeedId(UUID deliveryNeedId) {
        return tenantContextHolder.currentTenantId()
                .flatMap(tenantId -> port.findByOrigin(tenantId, AnnouncementExtras.ORIGIN_DELIVERY_NEED, deliveryNeedId))
                .map(AnnouncementViews::toLocal);
    }
}
