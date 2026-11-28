package com.yowyob.tiibntick.core.delivery.application.service;

import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryAnnouncementRepository;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryThresholdsPort;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryAnnouncement;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementCreatorRole;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryKind;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ParcelStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RecipientInfo;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.SenderInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Opens the return of a parcel that must go back to its sender (decision QO-4): a RETURN announcement is created as a
 * <b>draft</b> for the payer of the return — always the sender: the relay never pays a return, a parcel not collected
 * at a relay is paid by the sender's return guarantee (D-147, D-151) — never published automatically. A cancelled
 * return opens a new draft (D-148). After {@code maxFailedReturns} failed or cancelled returns (QO-8, D-131) no new draft is
 * opened: the parcel stays {@code RETURN_REQUESTED} in storage and is reported to the support.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReturnDeliveryService {

    private final DeliveryAnnouncementRepository announcementRepository;
    private final DeliveryThresholdsPort thresholds;

    /**
     * @param delivery the delivery whose transition just made the parcel {@code RETURN_REQUESTED}
     * @return the saved draft, empty when the return limit is reached
     */
    public Mono<DeliveryAnnouncement> openReturnDraft(Delivery delivery) {
        Parcel parcel = delivery.getParcel();
        if (parcel == null || parcel.getStatus() != ParcelStatus.RETURN_REQUESTED) {
            return Mono.empty();
        }
        return thresholds.forAgency(delivery.getAgencyId())
                .map(DeliveryThresholdsPort.Thresholds::maxFailedReturns)
                .defaultIfEmpty(3)
                .flatMap(maxFailedReturns -> {
                    if (parcel.getFailedReturnCount() >= maxFailedReturns) {
                        // QO-8 / D-131: stop opening returns; storage + report to the support (admin spec §5.4).
                        log.warn("Parcel {} reached {} failed returns (max {}): kept in storage, support to be "
                                        + "notified (no new return draft)", parcel.getId(),
                                parcel.getFailedReturnCount(), maxFailedReturns);
                        return Mono.empty();
                    }
                    return originSender(delivery).map(java.util.Optional::of)
                            .defaultIfEmpty(java.util.Optional.empty())
                            .flatMap(sender -> announcementRepository.save(draftFor(delivery, parcel,
                                    sender.orElse(null))));
                });
    }

    private DeliveryAnnouncement draftFor(Delivery delivery, Parcel parcel, SenderInfo sender) {
        // D-147, D-151: the sender pays every return, a parcel not collected at a relay included.
        UUID creatorId = delivery.getSenderId();
        RecipientInfo backToSender = sender != null && sender.name() != null && sender.phoneNumber() != null
                ? RecipientInfo.of(sender.name(), sender.phoneNumber(), sender.email()) : null;
        UUID parent = delivery.getKind() == DeliveryKind.RETURN && delivery.getParentDeliveryId() != null
                ? delivery.getParentDeliveryId() : delivery.getId();
        return DeliveryAnnouncement.createReturnDraft(delivery.getTenantId(), creatorId,
                AnnouncementCreatorRole.CLIENT, parcel,
                delivery.getDeliveryAddress(), delivery.getPickupAddress(), backToSender,
                delivery.getAgreedCurrency() != null ? delivery.getAgreedCurrency() : "XAF", parent);
    }

    /** Sender contact of the original announcement, when known. */
    private Mono<SenderInfo> originSender(Delivery delivery) {
        if (delivery.getAnnouncementId() == null) {
            return Mono.empty();
        }
        return announcementRepository.findById(delivery.getTenantId(), delivery.getAnnouncementId())
                .flatMap(a -> Mono.justOrEmpty(a.getSender()));
    }
}
