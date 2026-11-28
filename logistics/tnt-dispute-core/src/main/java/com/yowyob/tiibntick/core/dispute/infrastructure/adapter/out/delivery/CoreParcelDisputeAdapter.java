package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.delivery;

import com.yowyob.tiibntick.common.kafka.TntEventActor;
import com.yowyob.tiibntick.core.delivery.application.port.in.ParcelDisputeUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.ParcelUseCase;
import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryDomainException;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DisputeParcelOutcome;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ResponsibleParty;
import com.yowyob.tiibntick.core.dispute.application.port.outbound.IDisputeParcelPort;
import com.yowyob.tiibntick.core.dispute.domain.enums.DisputeResponsibleParty;
import com.yowyob.tiibntick.core.dispute.domain.exception.DisputeStateException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@link IDisputeParcelPort} over delivery-core's {@link ParcelDisputeUseCase} (direct call in the dispute's
 * transaction, spec §1.4). A refusal of the parcel (already under dispute, filing window over) becomes a 409 of the
 * dispute.
 */
@Slf4j
@Component
public class CoreParcelDisputeAdapter implements IDisputeParcelPort {

    private final ParcelDisputeUseCase parcels;
    private final ParcelUseCase parcelQuery;

    public CoreParcelDisputeAdapter(ParcelDisputeUseCase parcels, ParcelUseCase parcelQuery) {
        this.parcels = parcels;
        this.parcelQuery = parcelQuery;
    }

    @Override
    public Mono<String> resolveParcel(String tenantId, String packageId, String missionId) {
        UUID parcel = uuid(packageId);
        UUID delivery = uuid(missionId);
        if (parcel == null && delivery == null) {
            return Mono.empty();
        }
        return parcels.resolveParcelId(uuid(tenantId), parcel, delivery).map(UUID::toString);
    }

    @Override
    public Mono<Parties> parties(String tenantId, String parcelId) {
        return parcels.disputeParties(uuid(tenantId), uuid(parcelId)).map(p -> new Parties(p.parcelId().toString(),
                strings(p.senderIds()), strings(p.recipientActorIds()), strings(p.courierActorIds()),
                p.courierOrgIds(), strings(p.relayOrgIds())));
    }

    @Override
    public Mono<Void> openDispute(String tenantId, String parcelId, String disputeId, boolean freezesDelivery,
                                  TntEventActor actor) {
        return parcels.openDispute(uuid(tenantId), UUID.fromString(parcelId), UUID.fromString(disputeId),
                        freezesDelivery, actor, null)
                .onErrorMap(DeliveryDomainException.class, e -> new DisputeStateException(
                        "The parcel " + parcelId + " cannot be put under dispute: " + e.getMessage()))
                .then();
    }

    @Override
    public Mono<Void> applyOutcome(String tenantId, String parcelId, String disputeId, String outcome, String trigger,
                                   BigDecimal compensationAmount, DisputeResponsibleParty responsibleParty,
                                   TntEventActor actor) {
        UUID dispute = UUID.fromString(disputeId);
        return parcelQuery.findParcelById(UUID.fromString(parcelId))
                .filter(parcel -> dispute.equals(parcel.getOpenDisputeId()))
                .flatMap(parcel -> parcels.applyDisputeOutcome(uuid(tenantId), parcel.getId(),
                        DisputeParcelOutcome.valueOf(outcome), trigger,
                        compensationAmount != null ? compensationAmount.toPlainString() : null,
                        responsibleParty != null ? ResponsibleParty.valueOf(responsibleParty.name()) : null,
                        actor, null))
                .switchIfEmpty(Mono.fromRunnable(() -> log.info(
                        "Parcel {} is not under dispute {}: outcome {} not applied", parcelId, disputeId, outcome)))
                .onErrorMap(DeliveryDomainException.class, e -> new DisputeStateException(
                        "The outcome " + outcome + " cannot be applied to parcel " + parcelId + ": " + e.getMessage()))
                .then();
    }

    private static Set<String> strings(Set<UUID> ids) {
        return ids.stream().map(UUID::toString).collect(Collectors.toUnmodifiableSet());
    }

    private static UUID uuid(String value) {
        try {
            return value == null || value.isBlank() ? null : UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
