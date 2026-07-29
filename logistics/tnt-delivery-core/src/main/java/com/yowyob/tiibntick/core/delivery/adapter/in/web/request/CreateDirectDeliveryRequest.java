package com.yowyob.tiibntick.core.delivery.adapter.in.web.request;

import com.yowyob.tiibntick.core.delivery.application.port.in.command.CreateDirectDeliveryCommand;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RecipientInfo;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * HTTP request body for creating a delivery directly, bypassing the announcement/
 * response-selection marketplace flow — used by callers that already know who's
 * sending/receiving/carrying the parcel (e.g. an agency dispatching from a client
 * intake) and don't need a bidding step.
 *
 * <p>Addresses are accepted as informal landmark/district/city (no structured street or
 * geocoded coordinates) since callers like agency intake only have a free-text address —
 * see {@link DeliveryAddress#informal}. Package dimensions default to a generic small-parcel
 * box when the caller only knows the weight (again, the agency-intake case).
 *
 * @author MANFOUO Braun
 */
public record CreateDirectDeliveryRequest(
        @NotNull UUID senderId,
        UUID agencyId,

        @NotNull String pickupLandmark,
        String pickupDistrict,
        @NotNull String pickupCity,

        @NotNull String deliveryLandmark,
        String deliveryDistrict,
        @NotNull String deliveryCity,

        @NotNull String recipientName,
        @NotNull String recipientPhone,

        /** {@link DeliveryUrgency} name; defaults to STANDARD if null or unrecognized. */
        String urgency,

        Double weightKg,
        Double widthCm,
        Double heightCm,
        Double lengthCm,
        Boolean fragile,
        Boolean perishable,
        String packageDescription,

        Instant scheduledPickupTime,
        String notes
) {
    private static final double DEFAULT_WEIGHT_KG = 1.0;
    private static final double DEFAULT_WIDTH_CM = 30.0;
    private static final double DEFAULT_HEIGHT_CM = 20.0;
    private static final double DEFAULT_LENGTH_CM = 15.0;

    public CreateDirectDeliveryCommand toCommand(UUID tenantId) {
        return new CreateDirectDeliveryCommand(
                tenantId,
                senderId,
                agencyId,
                DeliveryAddress.informal(pickupLandmark, pickupDistrict, pickupCity, null),
                DeliveryAddress.informal(deliveryLandmark, deliveryDistrict, deliveryCity, null),
                new RecipientInfo(recipientName, recipientPhone, null),
                parseUrgency(),
                new PackageSpecification(
                        weightKg != null ? weightKg : DEFAULT_WEIGHT_KG,
                        widthCm != null ? widthCm : DEFAULT_WIDTH_CM,
                        heightCm != null ? heightCm : DEFAULT_HEIGHT_CM,
                        lengthCm != null ? lengthCm : DEFAULT_LENGTH_CM,
                        fragile != null && fragile,
                        perishable != null && perishable,
                        packageDescription != null ? packageDescription : "Parcel"),
                scheduledPickupTime,
                notes);
    }

    private DeliveryUrgency parseUrgency() {
        if (urgency == null) {
            return DeliveryUrgency.STANDARD;
        }
        try {
            return DeliveryUrgency.valueOf(urgency);
        } catch (IllegalArgumentException e) {
            return DeliveryUrgency.STANDARD;
        }
    }
}
