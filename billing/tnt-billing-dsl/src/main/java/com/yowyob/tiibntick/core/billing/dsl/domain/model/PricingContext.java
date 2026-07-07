package com.yowyob.tiibntick.core.billing.dsl.domain.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable context passed to every DSL rule evaluation.
 *
 * <p>Contains the runtime values of all variables that the DSL mini-language
 * may reference. New fields are nullable/defaultable to maintain backward
 * compatibility with existing rule sets authored before .</p>
 *
 * <h3>Original variables (v1.0)</h3>
 * <ul>
 *   <li>{@code weightKg}         — parcel weight in kilograms</li>
 *   <li>{@code distanceKm}       — estimated route distance in kilometres</li>
 *   <li>{@code packageTypes}     — list of package type classifications</li>
 *   <li>{@code priority}         — mission priority level</li>
 *   <li>{@code clientTxCount}    — number of past completed transactions (loyalty)</li>
 *   <li>{@code timeOfDay}        — time at which the delivery will start</li>
 *   <li>{@code weatherCondition} — current/forecast weather at pickup zone</li>
 *   <li>{@code roadType}         — dominant road surface type on the planned route</li>
 *   <li>{@code tenantId}         — tenant identifier for multi-tenant isolation</li>
 *   <li>{@code agencyId}         — agency identifier within the tenant</li>
 *   <li>{@code missionId}        — optional mission identifier for traceability</li>
 * </ul>
 *
 * <h3> additions — FreelancerOrganization context</h3>
 * <ul>
 *   <li>{@code selectedVehicleType}       — vehicle type code (e.g. "MOTO", "VOITURE")</li>
 *   <li>{@code activeEquipmentTypeCodes}  — set of active equipment codes (e.g. "REFRIGERATED_BOX")</li>
 *   <li>{@code activatedSpecialization}   — freelancer specialization code</li>
 *   <li>{@code isSubDelivererAssigned}    — whether the mission is delegated to a sub-deliverer</li>
 * </ul>
 *
 * <h3> additions — Enriched parcel context</h3>
 * <ul>
 *   <li>{@code packageCount}        — number of parcels in the delivery</li>
 *   <li>{@code declaredValue}       — declared monetary value (for insurance surcharges)</li>
 *   <li>{@code requiresRefrigeration} — cold-chain delivery required</li>
 *   <li>{@code requiresAssembly}    — assembly at destination required</li>
 *   <li>{@code requiresIDCheck}     — ID verification at delivery required</li>
 *   <li>{@code deliveryAttemptNumber} — nth delivery attempt (retry surcharges)</li>
 * </ul>
 *
 * <h3> additions — Geographic context</h3>
 * <ul>
 *   <li>{@code deliveryZoneType}      — zone urbanisation type (URBAN, RURAL …)</li>
 *   <li>{@code zoneAccessDifficulty}  — road access difficulty level (LOW … VERY_HIGH)</li>
 * </ul>
 *
 * <h3> additions — Client context</h3>
 * <ul>
 *   <li>{@code paymentMethod}   — payment method code (MTN_MOMO, ORANGE_MONEY, CASH …)</li>
 *   <li>{@code clientSegment}   — client segment code (INDIVIDUAL, BUSINESS, PREMIUM …)</li>
 *   <li>{@code isRecurringClient} — whether the client is a known recurring customer</li>
 * </ul>
 *
 * <h3> additions — Extended temporal context</h3>
 * <ul>
 *   <li>{@code dayOfWeek}     — day of the week (for weekend/weekday surcharges)</li>
 *   <li>{@code isPublicHoliday} — whether the delivery falls on a public holiday</li>
 * </ul>
 *
 * <h3> additions — Policy owner context</h3>
 * <ul>
 *   <li>{@code policyOwnerType} — type of policy owner: AGENCY, FREELANCER_ORG …</li>
 *   <li>{@code ownerActorId}    — UUID string of the policy owner actor</li>
 * </ul>
 *
 * <h3> additions — Hub Point context</h3>
 * <ul>
 *   <li>{@code storageHours} — hours the parcel has been stored at the hub</li>
 * </ul>
 *
 * <h3> additions — Link Network context</h3>
 * <ul>
 *   <li>{@code networkHopCount} — number of relay hops in the network route</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@Value
@Builder(toBuilder = true)
public class PricingContext {

    // ── v1.0 — Original fields ────────────────────────────────────────────────

    /** Parcel weight in kilograms. */
    double weightKg;

    /** Estimated route distance in kilometres. */
    double distanceKm;

    /** Package type classifications. A delivery may carry FRAGILE + PERISHABLE. */
    List<PackageType> packageTypes;

    /** Mission priority level. */
    DeliveryPriority priority;

    /**
     * Number of completed transactions from this client.
     * Used to evaluate loyalty discount rules (e.g. txCount >= 10 → -5%).
     */
    int clientTxCount;

    /** Time of day at which the mission is scheduled to start. */
    LocalTime timeOfDay;

    /** Weather condition at the pickup location. */
    WeatherCondition weatherCondition;

    /** Dominant road surface type on the planned route. */
    RoadType roadType;

    /** Tenant (agency group) identifier — used for multi-tenant rule isolation. */
    UUID tenantId;

    /** Agency identifier within the tenant. */
    UUID agencyId;

    /** Optional mission identifier for traceability. */
    String missionId;

    // FreelancerOrganization context ─────────────────────────────────

    /**
     * Vehicle type code selected for this mission.
     * Values: "MOTO", "VELO", "VELO_CARGO", "VOITURE", "CAMIONNETTE".
     * Null when not a FreelancerOrg delivery.
     */
    String selectedVehicleType;

    /**
     * Set of active equipment type codes on the selected vehicle for this mission.
     * Example values: "REFRIGERATED_BOX", "THERMAL_BAG", "GPS_TRACKER".
     * Used with the DSL {@code CONTAINS} operator:
     * <pre>activeEquipmentTypes CONTAINS REFRIGERATED_BOX</pre>
     * Null or empty when no special equipment is used.
     */
    Set<String> activeEquipmentTypeCodes;

    /**
     * Freelancer specialization code activated for this mission.
     * Example: "MEDICAL_DELIVERY", "REFRIGERATED", "RURAL_ZONE".
     * Null when not applicable.
     */
    String activatedSpecialization;

    /**
     * Whether this mission is delegated to a sub-deliverer of the FreelancerOrg.
     * Used to apply different commission-based pricing rules.
     */
    Boolean isSubDelivererAssigned;

    // Enriched parcel context ────────────────────────────────────────

    /** Number of parcels included in this delivery (for multi-package surcharges). */
    Integer packageCount;

    /** Declared monetary value of the parcel contents (for insurance surcharges). */
    BigDecimal declaredValue;

    /** Whether the parcel requires cold-chain (refrigerated) delivery. */
    Boolean requiresRefrigeration;

    /** Whether assembly at the destination is required. */
    Boolean requiresAssembly;

    /** Whether ID verification of the recipient is required at delivery. */
    Boolean requiresIDCheck;

    /**
     * Delivery attempt number (1 = first attempt, 2+ = retry).
     * Used to apply retry/re-delivery surcharges.
     */
    Integer deliveryAttemptNumber;

    // Geographic context ─────────────────────────────────────────────

    /**
     * Zone urbanisation type of the delivery destination.
     * Values: "URBAN", "PERI_URBAN", "RURAL", "DIPLOMATIC", "PORT_ZONE".
     * Null when not available from tnt-geo-core.
     */
    String deliveryZoneType;

    /**
     * Road access difficulty level of the delivery zone.
     * Values: "LOW", "MEDIUM", "HIGH", "VERY_HIGH".
     * Null when not available from tnt-geo-core.
     */
    String zoneAccessDifficulty;

    // Client context ─────────────────────────────────────────────────

    /**
     * Payment method code chosen by the client.
     * Example values: "MTN_MOMO", "ORANGE_MONEY", "CASH", "STRIPE", "WALLET".
     */
    String paymentMethod;

    /**
     * Client segment code.
     * Example values: "INDIVIDUAL", "BUSINESS", "PREMIUM", "SME".
     */
    String clientSegment;

    /**
     * Whether the client is a known recurring customer.
     * Used to unlock loyalty pricing rules without checking txCount.
     */
    Boolean isRecurringClient;

    // Extended temporal context ─────────────────────────────────────

    /**
     * Day of the week on which the delivery is scheduled.
     * Used with DSL {@code DAY_IS} operator:
     * <pre>dayOfWeek DAY_IS WEEKEND</pre>
     */
    DayOfWeek dayOfWeek;

    /**
     * Whether the delivery is scheduled on a public holiday.
     * Enables holiday surcharge rules.
     */
    Boolean isPublicHoliday;

    // Policy owner context ───────────────────────────────────────────

    /**
     * Type of the billing policy owner.
     * Values: "AGENCY", "FREELANCER_ORG", "POINT", "LINK", "ADMIN", "MARKET".
     */
    String policyOwnerType;

    /** UUID string of the billing policy owner actor. */
    String ownerActorId;

    // Hub Point context ──────────────────────────────────────────────

    /**
     * Number of hours the parcel has been stored at the hub relay point.
     * Used to compute hub storage fees.
     */
    Integer storageHours;

    // Link Network context ───────────────────────────────────────────

    /**
     * Number of relay hops in the network route (for Link network billing).
     * Used to apply per-hop transit fees.
     */
    Integer networkHopCount;

    // ── Convenience query methods ─────────────────────────────────────────────

    /** @return {@code true} if any of the given package types are present */
    public boolean hasPackageType(PackageType type) {
        return packageTypes != null && packageTypes.contains(type);
    }

    /** @return {@code true} if the weather is rainy (RAIN_LIGHT, RAIN_HEAVY, or STORM) */
    public boolean isRaining() {
        return weatherCondition == WeatherCondition.RAIN_LIGHT
                || weatherCondition == WeatherCondition.RAIN_HEAVY
                || weatherCondition == WeatherCondition.STORM;
    }

    /** @return {@code true} if the road is degraded (UNPAVED or MUD) */
    public boolean isRoadDegraded() {
        return roadType == RoadType.UNPAVED || roadType == RoadType.MUD;
    }

    /**
     * Checks whether the active equipment set contains the given equipment type code.
     *
     * @param equipmentTypeCode the equipment type code to check (case-insensitive)
     * @return {@code true} if the equipment is active for this mission
     */
    public boolean hasEquipment(String equipmentTypeCode) {
        if (activeEquipmentTypeCodes == null || activeEquipmentTypeCodes.isEmpty()) return false;
        return activeEquipmentTypeCodes.stream()
                .anyMatch(code -> code.equalsIgnoreCase(equipmentTypeCode));
    }

    /**
     * @return {@code true} if the delivery is on a weekend (Saturday or Sunday)
     */
    public boolean isWeekend() {
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
    }

    /**
     * @return {@code true} if the delivery is on a weekday (Monday–Friday)
     */
    public boolean isWeekday() {
        return dayOfWeek != null && !isWeekend();
    }

    /**
     * @return {@code true} if the delivery is on a public holiday or weekend
     */
    public boolean isNonWorkingDay() {
        return Boolean.TRUE.equals(isPublicHoliday) || isWeekend();
    }
}
