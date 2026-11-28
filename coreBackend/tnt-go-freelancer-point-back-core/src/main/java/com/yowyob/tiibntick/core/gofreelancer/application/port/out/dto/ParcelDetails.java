package com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto;

import java.util.UUID;

/**
 * What gofp knows about a parcel, as exchanged with {@link com.yowyob.tiibntick.core.gofreelancer.application.port.out.ParcelPort}.
 * Weight is in kilograms, every dimension in centimetres. Any field may be {@code null} when registering:
 * the port then applies the neutral values the announcement path has always used (1 kg, 10 cm).
 *
 * @param id          the parcel id in the core ({@code null} before registration)
 * @param designation commercial label of the goods
 */
public record ParcelDetails(
        UUID id,
        String designation,
        String description,
        Double weightKg,
        Double widthCm,
        Double heightCm,
        Double lengthCm,
        Double thicknessCm,
        Boolean fragile,
        Boolean perishable,
        String photoUrl) {
}
