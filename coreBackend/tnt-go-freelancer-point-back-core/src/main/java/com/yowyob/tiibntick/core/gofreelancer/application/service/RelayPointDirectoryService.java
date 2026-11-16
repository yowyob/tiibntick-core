package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.RelayExpectedParcelView;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.RelayPointPublicView;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.entity.AddressEntity;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.AddressReactiveRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IAnnouncementRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.RelayDepositRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Announcement;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryNeed;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpRelayPoint;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.RelayDeposit;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.deliveryNeed.DeliveryNeedStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Read models around relay points: the public view a sender chooses from, and the parcels a relay
 * point owner should receive.
 */
@Service
@RequiredArgsConstructor
public class RelayPointDirectoryService {

    private final AddressReactiveRepository addresses;
    private final IDeliveryNeedRepository deliveryNeeds;
    private final RelayDepositRepository deposits;
    private final IAnnouncementRepository announcements;

    /** Public view: place and storage, never the owner's identity or contact. */
    public Mono<RelayPointPublicView> publicView(GofpRelayPoint rp) {
        UUID addressId = rp.getPhysicalAccessAddressId() != null
                ? rp.getPhysicalAccessAddressId() : rp.getPostalAddressId();
        Mono<AddressEntity> address = addressId == null ? Mono.empty()
                : addresses.findById(addressId).onErrorResume(e -> Mono.empty());
        return address
                .map(a -> view(rp, a))
                .defaultIfEmpty(view(rp, null));
    }

    public Flux<RelayPointPublicView> publicViews(Flux<GofpRelayPoint> relayPoints) {
        return relayPoints.concatMap(this::publicView);
    }

    /** Delivery needs targeting this relay point (by either of its ids), not cancelled, not yet deposited. */
    public Flux<RelayExpectedParcelView> expectedParcels(GofpRelayPoint rp) {
        Set<UUID> ids = new LinkedHashSet<>();
        if (rp.getId() != null) ids.add(rp.getId());
        if (rp.getCoreRelayPointId() != null) ids.add(rp.getCoreRelayPointId());
        if (ids.isEmpty()) return Flux.empty();
        return deposits.findByRelayPointId(rp.getCoreRelayPointId())
                .map(RelayDeposit::getPacketId)
                .collect(java.util.stream.Collectors.toSet())
                .flatMapMany(deposited -> deliveryNeeds.findAllByTargetRelayPointIdIn(ids)
                        .filter(n -> n.getStatus() != DeliveryNeedStatus.CANCELLED)
                        .filter(n -> !deposited.contains(n.getId())))
                .concatMap(this::toExpected);
    }

    private Mono<RelayExpectedParcelView> toExpected(DeliveryNeed need) {
        return announcements.findByDeliveryNeedId(need.getId())
                .onErrorResume(e -> Mono.empty())
                .map(a -> expected(need, a))
                .defaultIfEmpty(expected(need, null));
    }

    private static RelayExpectedParcelView expected(DeliveryNeed n, Announcement a) {
        return new RelayExpectedParcelView(n.getId(), n.getDeliveryId(), n.getTitle(),
                n.getStatus() != null ? n.getStatus().name() : null,
                a != null ? a.getRecipientFirstName() : null,
                a != null ? a.getRecipientLastName() : null,
                n.getCreatedAt());
    }

    private static RelayPointPublicView view(GofpRelayPoint rp, AddressEntity a) {
        return new RelayPointPublicView(rp.getId(), rp.getCoreRelayPointId(), rp.getName(), rp.getRating(),
                a != null ? a.getStreet() : null,
                a != null ? a.getQuarter() : null,
                a != null ? a.getCity() : null,
                a != null ? a.getLandmark() : null,
                a != null ? a.getLatitude() : null,
                a != null ? a.getLongitude() : null,
                rp.getStorageLength(), rp.getStorageWidth(), rp.getStorageHeight(), rp.getStorageDimensionUnit());
    }
}
