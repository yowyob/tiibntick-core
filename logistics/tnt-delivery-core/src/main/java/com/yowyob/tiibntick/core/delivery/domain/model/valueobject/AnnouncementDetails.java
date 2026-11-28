package com.yowyob.tiibntick.core.delivery.domain.model.valueobject;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Optional, generic complements of a delivery announcement. Every component is nullable; a null
 * component means "not specified" (and, in a patch, "leave unchanged").
 *
 * <ul>
 *   <li>{@code requiredVehicleType}, {@code transportMethod}: what the sender asks for;</li>
 *   <li>{@code paymentMethod}: how the sender intends to settle (free label);</li>
 *   <li>{@code distanceKm}, {@code estimatedDurationMin}: route estimate known to the publisher;</li>
 *   <li>{@code destinationHubId}: relay hub where the parcel is to be left;</li>
 *   <li>{@code handoverSignatureRef}: reference of the sender's handover signature;</li>
 *   <li>{@code serviceFee}: fee charged on top of the offered amount;</li>
 *   <li>{@code originRefType} / {@code originRefId}: the request this announcement publishes
 *       (e.g. {@code DELIVERY_NEED}); at most one announcement per origin.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public record AnnouncementDetails(
        String requiredVehicleType,
        String paymentMethod,
        String transportMethod,
        BigDecimal distanceKm,
        Integer estimatedDurationMin,
        UUID destinationHubId,
        String handoverSignatureRef,
        BigDecimal serviceFee,
        String originRefType,
        UUID originRefId
) {

    public static final AnnouncementDetails EMPTY =
            new AnnouncementDetails(null, null, null, null, null, null, null, null, null, null);

    /** Non-null components of {@code patch} replace the ones of this instance. */
    public AnnouncementDetails merge(AnnouncementDetails patch) {
        if (patch == null) {
            return this;
        }
        return new AnnouncementDetails(
                pick(patch.requiredVehicleType, requiredVehicleType),
                pick(patch.paymentMethod, paymentMethod),
                pick(patch.transportMethod, transportMethod),
                pick(patch.distanceKm, distanceKm),
                pick(patch.estimatedDurationMin, estimatedDurationMin),
                pick(patch.destinationHubId, destinationHubId),
                pick(patch.handoverSignatureRef, handoverSignatureRef),
                pick(patch.serviceFee, serviceFee),
                // the origin is fixed at publication
                originRefType != null ? originRefType : patch.originRefType,
                originRefId != null ? originRefId : patch.originRefId);
    }

    private static <T> T pick(T candidate, T current) {
        return candidate != null ? candidate : current;
    }
}
