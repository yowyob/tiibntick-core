package com.yowyob.tiibntick.core.gofreelancer.application.port.out;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.Announcement;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outgoing port — read-only view of an announcement, backed by {@code tnt-delivery-core}
 * ({@code CoreAnnouncementReadAdapter}). Go-Freelancer keeps no announcement table of its own any
 * more: nothing is written through this port.
 *
 * <p>The announcement is looked up in the current tenant (the tenant of the security context, the
 * system tenant outside a request).
 */
public interface IAnnouncementRepository {

    Mono<Announcement> findById(UUID id);

    /** The announcement published for this delivery need (its origin reference), or empty. */
    Mono<Announcement> findByDeliveryNeedId(UUID deliveryNeedId);
}
