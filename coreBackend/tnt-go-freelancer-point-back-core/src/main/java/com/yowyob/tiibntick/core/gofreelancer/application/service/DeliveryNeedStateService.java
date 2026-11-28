package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryAnnouncementPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryLifecyclePort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementExtras;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementResponseSnapshot;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementSnapshot;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryNeed;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.deliveryNeed.DeliveryNeedStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.deliveryNeed.DeliveryNeedStatusPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fills the derived fields of a delivery need ({@code status}, {@code deliveryId},
 * {@code assignedFreelancerId}) from the announcement and delivery delivery-core holds for it.
 * A need has no stored state: this is the only place the derivation is wired.
 *
 * <p>Lists are resolved in one announcement query per call (by origin), plus one delivery lookup per
 * assigned need.
 *
 * <p>The read fallback of lot L2 on the stored columns ({@code status}, {@code delivery_id},
 * {@code assigned_freelancer_id}) was removed with the columns (closure gdd-final-905, D-154): the closure
 * stops if a need assigned before lot L2 still has no delivery in the core.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryNeedStateService {

    private final IDeliveryAnnouncementPort announcements;
    private final IDeliveryLifecyclePort deliveries;
    private final TenantContextHolder tenantContextHolder;

    /** The same need, derived fields filled. */
    public Mono<DeliveryNeed> hydrate(DeliveryNeed need) {
        return hydrateAll(Flux.just(need)).next();
    }

    /** The same needs, in the same order, derived fields filled. */
    public Flux<DeliveryNeed> hydrateAll(Flux<DeliveryNeed> needs) {
        return needs.collectList().flatMapMany(list -> list.isEmpty() ? Flux.<DeliveryNeed>empty()
                : tenantContextHolder.currentTenantId().flatMapMany(tenantId -> hydrate(tenantId, list)));
    }

    private Flux<DeliveryNeed> hydrate(UUID tenantId, List<DeliveryNeed> list) {
        List<UUID> ids = list.stream().map(DeliveryNeed::getId).toList();
        return announcements.findByOrigins(tenantId, AnnouncementExtras.ORIGIN_DELIVERY_NEED, ids)
                .filter(a -> a.extras().originRefId() != null)
                .collectMap(a -> a.extras().originRefId())
                .flatMapMany(byNeed -> Flux.fromIterable(list)
                        .concatMap(need -> apply(tenantId, need, byNeed.get(need.getId()))));
    }

    private Mono<DeliveryNeed> apply(UUID tenantId, DeliveryNeed need, AnnouncementSnapshot a) {
        if (a == null) {
            need.setStatus(DeliveryNeedStatusPolicy.derive(null, null));
            need.setOutcome(null);
            need.setDeliveryId(null);
            need.setAssignedFreelancerId(null);
            return Mono.just(need);
        }
        need.setAnnouncementId(a.id());
        need.setDeliveryId(a.createdDeliveryId());
        need.setAssignedFreelancerId(selectedDeliveryPerson(a));
        Mono<DeliveryStatus> deliveryStatus = a.createdDeliveryId() == null ? Mono.empty()
                : deliveries.findState(tenantId, a.createdDeliveryId())
                        .mapNotNull(s -> s.status() == null ? null : DeliveryStatus.fromValue(s.status()))
                        .onErrorResume(e -> {
                            log.warn("Delivery {} of need {} unreadable ({}): need status derived without it",
                                    a.createdDeliveryId(), need.getId(), e.getMessage());
                            return Mono.empty();
                        });
        return deliveryStatus.map(java.util.Optional::of).defaultIfEmpty(java.util.Optional.empty())
                .flatMap(d -> {
                    need.setStatus(DeliveryNeedStatusPolicy.derive(a.status(), d.orElse(null)));
                    need.setOutcome(DeliveryNeedStatusPolicy.outcome(a.status(), d.orElse(null)));
                    return Mono.just(need);
                });
    }

    private static UUID selectedDeliveryPerson(AnnouncementSnapshot a) {
        if (a.selectedResponseId() == null) {
            return null;
        }
        Map<UUID, AnnouncementResponseSnapshot> byId = new HashMap<>();
        a.responses().forEach(r -> byId.put(r.id(), r));
        AnnouncementResponseSnapshot r = byId.get(a.selectedResponseId());
        return r != null ? r.deliveryPersonId() : null;
    }
}
