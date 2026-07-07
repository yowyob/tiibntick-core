package com.yowyob.tiibntick.core.billing.pricing.infrastructure.adapter.in.web;

import com.yowyob.tiibntick.core.billing.dsl.domain.model.DeliveryPriority;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PackageType;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.RoadType;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.WeatherCondition;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Inbound REST DTO for price evaluation requests.
 *
 * <h3> additions</h3>
 * <p>All  {@link com.yowyob.tiibntick.core.billing.dsl.domain.model.PricingContext} fields
 * are added as optional parameters. Pre- clients that omit them will have null values
 * in the context, which the engine handles gracefully.</p>
 *
 * @author MANFOUO Braun
 */
public record EvaluatePriceRequest(

        @NotNull UUID policyId,
        String missionId,

        @PositiveOrZero double weightKg,
        @PositiveOrZero double distanceKm,

        List<PackageType> packageTypes,
        DeliveryPriority priority,
        Integer clientTxCount,
        LocalTime timeOfDay,
        WeatherCondition weatherCondition,
        RoadType roadType,

        @NotNull UUID tenantId,
        UUID agencyId,

        // FreelancerOrg context ─────────────────────────────────────
        String selectedVehicleType,
        Set<String> activeEquipmentTypeCodes,
        String activatedSpecialization,
        Boolean isSubDelivererAssigned,

        // Enriched parcel context ────────────────────────────────────
        @PositiveOrZero Integer packageCount,
        BigDecimal declaredValue,
        Boolean requiresRefrigeration,
        Boolean requiresAssembly,
        Boolean requiresIDCheck,
        @PositiveOrZero Integer deliveryAttemptNumber,

        // Geographic context ──────────────────────────────────────────
        String deliveryZoneType,
        String zoneAccessDifficulty,

        // Client context ─────────────────────────────────────────────
        String paymentMethod,
        String clientSegment,
        Boolean isRecurringClient,

        // Extended temporal context ─────────────────────────────────
        DayOfWeek dayOfWeek,
        Boolean isPublicHoliday,

        // Policy owner context ───────────────────────────────────────
        String policyOwnerType,
        String ownerActorId,

        // Hub Point context ──────────────────────────────────────────
        @PositiveOrZero Integer storageHours,

        // Link Network context ───────────────────────────────────────
        @PositiveOrZero Integer networkHopCount
) {}
