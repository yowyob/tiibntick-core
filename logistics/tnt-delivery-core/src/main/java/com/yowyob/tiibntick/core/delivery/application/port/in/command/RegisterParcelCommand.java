package com.yowyob.tiibntick.core.delivery.application.port.in.command;

import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import jakarta.validation.constraints.NotNull;

/**
 * Command to register a parcel on its own, before any announcement or delivery refers to it
 * (a request that describes the goods first, then is published or assigned later).
 *
 * @param specification physical characteristics (weight in kg, dimensions in cm)
 * @param photoUrl      optional photo of the goods
 * @param tenantId      tenant of the registration, copied on the {@code ParcelCreated} event (lot evt-l2);
 *                      {@code null}: the parcel belongs to the system tenant and the event is published (D-150)
 * @param senderId      sender holding the parcel, may be {@code null}
 */
public record RegisterParcelCommand(
        @NotNull PackageSpecification specification,
        String photoUrl,
        java.util.UUID tenantId,
        java.util.UUID senderId
) {
    public RegisterParcelCommand(PackageSpecification specification, String photoUrl) {
        this(specification, photoUrl, null, null);
    }
}
