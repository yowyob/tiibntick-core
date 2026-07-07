package com.yowyob.tiibntick.core.billing.dsl.infrastructure.adapter.in.web;

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
 * Inbound REST DTO for evaluating a billing policy against a pricing context.
 *
 * <h3> additions</h3>
 * <p>New optional fields for FreelancerOrg context, enriched parcel context,
 * geographic context, client context, extended temporal context, and
 * infrastructure context (Hub storage, Link network).</p>
 *
 * @author MANFOUO Braun
 */
public record EvaluateRequest(

        // ── Required fields ─────────────────────────────────────────────────
        @NotNull UUID policyId,

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
        String missionId,

        // FreelancerOrg context ─────────────────────────────────
        /** Vehicle type code: MOTO, VELO, VOITURE, CAMIONNETTE … */
        String selectedVehicleType,

        /** Set of active equipment type codes on the vehicle. */
        Set<String> activeEquipmentTypeCodes,

        /** Freelancer specialization code. */
        String activatedSpecialization,

        /** Whether mission is delegated to a sub-deliverer. */
        Boolean isSubDelivererAssigned,

        // Enriched parcel context ────────────────────────────────
        @PositiveOrZero Integer packageCount,
        BigDecimal declaredValue,
        Boolean requiresRefrigeration,
        Boolean requiresAssembly,
        Boolean requiresIDCheck,
        @PositiveOrZero Integer deliveryAttemptNumber,

        // Geographic context ──────────────────────────────────────
        /** Zone type: URBAN, PERI_URBAN, RURAL, DIPLOMATIC, PORT_ZONE */
        String deliveryZoneType,

        /** Zone access difficulty: LOW, MEDIUM, HIGH, VERY_HIGH */
        String zoneAccessDifficulty,

        // Client context ─────────────────────────────────────────
        /** Payment method: MTN_MOMO, ORANGE_MONEY, CASH, STRIPE, WALLET */
        String paymentMethod,

        /** Client segment: INDIVIDUAL, BUSINESS, PREMIUM, SME */
        String clientSegment,

        Boolean isRecurringClient,

        // Extended temporal context ─────────────────────────────
        DayOfWeek dayOfWeek,
        Boolean isPublicHoliday,

        // Policy owner context ───────────────────────────────────
        /** Policy owner type: AGENCY, FREELANCER_ORG, POINT, LINK, ADMIN */
        String policyOwnerType,
        String ownerActorId,

        // Hub Point context ──────────────────────────────────────
        @PositiveOrZero Integer storageHours,

        // Link Network context ───────────────────────────────────
        @PositiveOrZero Integer networkHopCount
) {}
