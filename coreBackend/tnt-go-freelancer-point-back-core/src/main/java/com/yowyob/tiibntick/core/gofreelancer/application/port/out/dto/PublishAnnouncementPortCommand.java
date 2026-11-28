package com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto;

import com.yowyob.tiibntick.common.vo.Address;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Gofp-owned command to publish a new announcement through
 * {@link com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryAnnouncementPort}.
 *
 * <p>{@code pricingMode} follows the same string convention as {@link AnnouncementSnapshot}
 * ({@code "FIXED_PRICE"} / {@code "QUOTE_REQUEST"}, case-insensitive, defaults to
 * {@code FIXED_PRICE} when blank — the adapter is responsible for parsing it).
 *
 * <p>{@code parcelId}, when given, names a parcel already registered in the core (see
 * {@link com.yowyob.tiibntick.core.gofreelancer.application.port.out.ParcelPort}): the announcement and the
 * delivery created from it then carry that parcel and its id; the package fields are still required.
 * {@code extras} carries the optional complements (sender, recipient e-mail, preferences, origin...).
 *
 * @author MANFOUO BRAUN
 */
public record PublishAnnouncementPortCommand(
        UUID tenantId,
        UUID clientId,
        String title,
        String description,
        BigDecimal offeredAmount,
        String currency,
        String pricingMode,
        double packageWeightKg,
        double packageWidthCm,
        double packageHeightCm,
        double packageLengthCm,
        boolean fragile,
        boolean perishable,
        String packageDescription,
        Address pickupAddress,
        Address deliveryAddress,
        String recipientName,
        String recipientPhone,
        UUID parcelId,
        AnnouncementExtras extras) {

    public PublishAnnouncementPortCommand {
        extras = extras != null ? extras : AnnouncementExtras.EMPTY;
    }

    /** Command for goods that are not registered beforehand: the core creates the parcel. */
    public PublishAnnouncementPortCommand(
            UUID tenantId, UUID clientId, String title, String description, BigDecimal offeredAmount,
            String currency, String pricingMode, double packageWeightKg, double packageWidthCm,
            double packageHeightCm, double packageLengthCm, boolean fragile, boolean perishable,
            String packageDescription, Address pickupAddress, Address deliveryAddress,
            String recipientName, String recipientPhone) {
        this(tenantId, clientId, title, description, offeredAmount, currency, pricingMode,
                packageWeightKg, packageWidthCm, packageHeightCm, packageLengthCm, fragile, perishable,
                packageDescription, pickupAddress, deliveryAddress, recipientName, recipientPhone, null, null);
    }

    /** Goods registered beforehand as parcel {@code parcelId}, no complements. */
    public PublishAnnouncementPortCommand(
            UUID tenantId, UUID clientId, String title, String description, BigDecimal offeredAmount,
            String currency, String pricingMode, double packageWeightKg, double packageWidthCm,
            double packageHeightCm, double packageLengthCm, boolean fragile, boolean perishable,
            String packageDescription, Address pickupAddress, Address deliveryAddress,
            String recipientName, String recipientPhone, UUID parcelId) {
        this(tenantId, clientId, title, description, offeredAmount, currency, pricingMode,
                packageWeightKg, packageWidthCm, packageHeightCm, packageLengthCm, fragile, perishable,
                packageDescription, pickupAddress, deliveryAddress, recipientName, recipientPhone, parcelId, null);
    }

    /** The same command for goods already registered in the core as parcel {@code id}. */
    public PublishAnnouncementPortCommand withParcelId(UUID id) {
        return new PublishAnnouncementPortCommand(tenantId, clientId, title, description, offeredAmount,
                currency, pricingMode, packageWeightKg, packageWidthCm, packageHeightCm, packageLengthCm,
                fragile, perishable, packageDescription, pickupAddress, deliveryAddress,
                recipientName, recipientPhone, id, extras);
    }
}
