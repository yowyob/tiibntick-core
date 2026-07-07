package com.yowyob.tiibntick.core.billing.templates.application.command;

import com.yowyob.tiibntick.core.billing.templates.domain.model.PolicyOwnerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * Command to request a price preview for a given template and sample pricing scenario.
 *
 * <p>The preview computes an estimated total price using the template's parameters
 * (with any provided custom overrides) applied to the given sample scenario. No
 * BillingPolicy is created — this is a read-only calculation for UI feedback.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@Value
@Builder
public class PreviewPriceCommand {

    /** The catalog template code to simulate. */
    @NotBlank
    String templateCode;

    /** The type of the requesting actor (for applicability verification). */
    @NotNull
    PolicyOwnerType ownerType;

    /**
     * Optional parameter overrides for the simulation.
     * Parameters not overridden use the template's default values.
     */
    @Builder.Default
    Map<String, String> customizedParameters = Map.of();

    // ─── Sample pricing scenario ────────────────────────────────────────

    /** Distance of the simulated delivery (km). */
    @PositiveOrZero
    double distanceKm;

    /** Weight of the simulated package (kg). */
    @PositiveOrZero
    double weightKg;

    /** Package type for surcharge simulation (e.g. FRAGILE, PERISHABLE, STANDARD). */
    @Builder.Default
    String packageType = "STANDARD";

    /** Priority level (e.g. STANDARD, EXPRESS, URGENT). */
    @Builder.Default
    String priority = "STANDARD";

    /** Number of previous transactions of the simulated client (for loyalty rules). */
    @Builder.Default
    int clientTransactionCount = 0;

    /** Delivery zone type (URBAN, PERI_URBAN, RURAL, DIPLOMATIC). */
    @Builder.Default
    String deliveryZoneType = "URBAN";

    /** Zone access difficulty (LOW, MEDIUM, HIGH, VERY_HIGH). */
    @Builder.Default
    String zoneAccessDifficulty = "LOW";

    /** Weather condition (CLEAR, RAIN_LIGHT, RAIN_HEAVY, FLOOD, STORM). */
    @Builder.Default
    String weatherCondition = "CLEAR";

    /** Payment method (PREPAID, CASH_ON_DELIVERY, WALLET, MOBILE_MONEY). */
    @Builder.Default
    String paymentMethod = "PREPAID";

    /** Whether the package requires active refrigeration. */
    @Builder.Default
    boolean requiresRefrigeration = false;

    /** Whether assembly/installation is required at delivery. */
    @Builder.Default
    boolean requiresAssembly = false;

    /** Whether recipient identity verification is required. */
    @Builder.Default
    boolean requiresIDCheck = false;

    /** Time of day in HH:mm format (affects time-slot templates). */
    @Builder.Default
    String timeOfDay = "10:00";

    /** Day of week (MONDAY..SUNDAY) for time-slot and weekend surcharges. */
    @Builder.Default
    String dayOfWeek = "TUESDAY";

    /** Whether today is a public holiday. */
    @Builder.Default
    boolean isPublicHoliday = false;

    /** Storage duration in hours (for HUB templates only). */
    @Builder.Default
    int storageHours = 0;

    /** Number of network hops (for LINK / NETWORK templates only). */
    @Builder.Default
    int networkHopCount = 1;

    /** Declared value of the package in XAF (for high-value surcharge). */
    @Builder.Default
    double declaredValueXaf = 0.0;

    /** Number of delivery attempt (1 = first attempt, 2 = re-delivery, etc.). */
    @Builder.Default
    int deliveryAttemptNumber = 1;

    /** Number of parcels in the mission (for multi-parcel discount). */
    @Builder.Default
    int packageCount = 1;
}
