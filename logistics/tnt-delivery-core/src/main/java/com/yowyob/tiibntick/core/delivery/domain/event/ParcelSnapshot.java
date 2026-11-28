package com.yowyob.tiibntick.core.delivery.domain.event;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.ParcelStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ResponsibleParty;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.ParcelCustody;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Unmasked state of a parcel after a change (spec §3.6 {@code parcel.payload}); masked by the publisher. */
public record ParcelSnapshot(
        UUID parcelId,
        String trackingCode,
        ParcelStatus status,
        ParcelStatus previousStatusBeforeDispute,
        UUID openDisputeId,
        UUID currentDeliveryId,
        List<UUID> deliveryIds,
        ParcelCustody custody,
        PackageSpecification specification,
        boolean hasPhoto,
        String returnCause,
        ResponsibleParty returnCostPayer,
        String closedReason,
        int failedReturnCount,
        Instant createdAt,
        Instant updatedAt,
        Instant statusChangedAt) {

    public ParcelSnapshot {
        deliveryIds = deliveryIds == null ? List.of() : List.copyOf(deliveryIds);
    }
}
