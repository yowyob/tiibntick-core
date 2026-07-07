package com.yowyob.tiibntick.core.incident.domain.model;

import com.yowyob.tiibntick.core.incident.domain.enums.HandoverStatus;
import com.yowyob.tiibntick.core.incident.domain.valueobject.PricingAdjustment;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Coordinated driver substitution process requiring dual confirmation before blockchain anchoring.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IncidentDriverReplacement {

    private UUID id;
    private UUID incidentId;
    private UUID originalDriverId;
    private UUID originalVehicleId;
    private UUID replacementDriverId;
    private UUID replacementVehicleId;
    private UUID replacementAgencyId;
    private double handoverLatitude;
    private double handoverLongitude;
    private String handoverAddress;
    private Instant handoverScheduledAt;
    private Instant handoverAt;
    private HandoverStatus handoverStatus;
    private Instant originalDriverConfirmedAt;
    private Instant replacementDriverConfirmedAt;
    private PricingAdjustment pricingAdjustment;
    private String blockchainTxHash;

    /**
     * Creates a new driver replacement record in PENDING handover status.
     *
     * @param incidentId       the related incident
     * @param originalDriverId the driver being replaced
     * @param originalVehicleId the original vehicle
     * @param lat              handover latitude
     * @param lng              handover longitude
     * @param address          human-readable handover address
     * @return a new replacement record
     */
    public static IncidentDriverReplacement initiate(UUID incidentId, UUID originalDriverId,
                                                      UUID originalVehicleId,
                                                      double lat, double lng, String address) {
        return IncidentDriverReplacement.builder()
                .id(UUID.randomUUID())
                .incidentId(incidentId)
                .originalDriverId(originalDriverId)
                .originalVehicleId(originalVehicleId)
                .handoverLatitude(lat)
                .handoverLongitude(lng)
                .handoverAddress(address)
                .handoverStatus(HandoverStatus.PENDING)
                .build();
    }

    /**
     * Assigns the replacement driver and vehicle with pricing adjustment.
     *
     * @param replacementDriverId  the replacement driver
     * @param replacementVehicleId the replacement vehicle
     * @param replacementAgencyId  the replacement agency
     * @param pricing              the pricing adjustment for the replacement
     * @return updated replacement record
     */
    public IncidentDriverReplacement assignReplacement(UUID replacementDriverId,
                                                        UUID replacementVehicleId,
                                                        UUID replacementAgencyId,
                                                        PricingAdjustment pricing) {
        return toBuilder()
                .replacementDriverId(replacementDriverId)
                .replacementVehicleId(replacementVehicleId)
                .replacementAgencyId(replacementAgencyId)
                .pricingAdjustment(pricing)
                .handoverScheduledAt(Instant.now())
                .build();
    }

    /**
     * Records the original driver's confirmation of the handover.
     * If the replacement driver has already confirmed, the handover is marked BOTH_CONFIRMED.
     *
     * @return updated replacement record
     */
    public IncidentDriverReplacement confirmByOriginalDriver() {
        HandoverStatus next = this.handoverStatus == HandoverStatus.REPLACEMENT_CONFIRMED
                ? HandoverStatus.BOTH_CONFIRMED : HandoverStatus.ORIGINAL_CONFIRMED;
        return toBuilder()
                .originalDriverConfirmedAt(Instant.now())
                .handoverStatus(next)
                .handoverAt(HandoverStatus.BOTH_CONFIRMED == next ? Instant.now() : handoverAt)
                .build();
    }

    /**
     * Records the replacement driver's confirmation of the handover.
     * If the original driver has already confirmed, the handover is marked BOTH_CONFIRMED.
     *
     * @return updated replacement record
     */
    public IncidentDriverReplacement confirmByReplacementDriver() {
        HandoverStatus next = this.handoverStatus == HandoverStatus.ORIGINAL_CONFIRMED
                ? HandoverStatus.BOTH_CONFIRMED : HandoverStatus.REPLACEMENT_CONFIRMED;
        return toBuilder()
                .replacementDriverConfirmedAt(Instant.now())
                .handoverStatus(next)
                .handoverAt(HandoverStatus.BOTH_CONFIRMED == next ? Instant.now() : handoverAt)
                .build();
    }

    public IncidentDriverReplacement withBlockchainProof(String txHash) {
        return toBuilder().blockchainTxHash(txHash).build();
    }

    public IncidentDriverReplacement markTimedOut() {
        return toBuilder().handoverStatus(HandoverStatus.TIMED_OUT).build();
    }

    /**
     * Returns {@code true} when both drivers have confirmed the handover.
     *
     * @return {@code true} if handover status is BOTH_CONFIRMED
     */
    public boolean isHandoverComplete() {
        return handoverStatus == HandoverStatus.BOTH_CONFIRMED;
    }

    public IncidentDriverReplacementBuilder toBuilder() {
        return IncidentDriverReplacement.builder().id(id).incidentId(incidentId)
                .originalDriverId(originalDriverId).originalVehicleId(originalVehicleId)
                .replacementDriverId(replacementDriverId).replacementVehicleId(replacementVehicleId)
                .replacementAgencyId(replacementAgencyId)
                .handoverLatitude(handoverLatitude).handoverLongitude(handoverLongitude)
                .handoverAddress(handoverAddress).handoverScheduledAt(handoverScheduledAt)
                .handoverAt(handoverAt).handoverStatus(handoverStatus)
                .originalDriverConfirmedAt(originalDriverConfirmedAt)
                .replacementDriverConfirmedAt(replacementDriverConfirmedAt)
                .pricingAdjustment(pricingAdjustment).blockchainTxHash(blockchainTxHash);
    }
}
