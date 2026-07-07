package com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.ast;

import com.yowyob.tiibntick.core.billing.dsl.domain.exception.DslEvaluationException;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PricingContext;
import lombok.RequiredArgsConstructor;

/**
 * AST node that resolves a named variable from the {@link PricingContext}.
 *
 * <h3>Supported variable names</h3>
 * <table>
 *   <tr><th>DSL Name</th><th>Java field</th><th>Type returned</th></tr>
 *   <tr><td>weight</td><td>weightKg</td><td>Double</td></tr>
 *   <tr><td>distance</td><td>distanceKm</td><td>Double</td></tr>
 *   <tr><td>packageType</td><td>packageTypes[0]</td><td>String (enum name)</td></tr>
 *   <tr><td>priority</td><td>priority</td><td>String (enum name)</td></tr>
 *   <tr><td>clientTxCount / txCount</td><td>clientTxCount</td><td>Integer</td></tr>
 *   <tr><td>timeOfDay</td><td>timeOfDay</td><td>LocalTime</td></tr>
 *   <tr><td>weather</td><td>weatherCondition</td><td>String (enum name)</td></tr>
 *   <tr><td>roadType</td><td>roadType</td><td>String (enum name)</td></tr>
 *   <tr><td>isRaining</td><td>isRaining()</td><td>Boolean</td></tr>
 *   <tr><td>isRoadDegraded</td><td>isRoadDegraded()</td><td>Boolean</td></tr>
 *   <tr><td colspan="3"><em> additions</em></td></tr>
 *   <tr><td>vehicleType</td><td>selectedVehicleType</td><td>String</td></tr>
 *   <tr><td>activeEquipmentTypes</td><td>activeEquipmentTypeCodes</td><td>Set&lt;String&gt;</td></tr>
 *   <tr><td>specialization</td><td>activatedSpecialization</td><td>String</td></tr>
 *   <tr><td>isSubDeliverer</td><td>isSubDelivererAssigned</td><td>Boolean</td></tr>
 *   <tr><td>packageCount</td><td>packageCount</td><td>Integer</td></tr>
 *   <tr><td>declaredValue</td><td>declaredValue</td><td>BigDecimal</td></tr>
 *   <tr><td>requiresRefrigeration</td><td>requiresRefrigeration</td><td>Boolean</td></tr>
 *   <tr><td>requiresAssembly</td><td>requiresAssembly</td><td>Boolean</td></tr>
 *   <tr><td>requiresIdCheck</td><td>requiresIDCheck</td><td>Boolean</td></tr>
 *   <tr><td>deliveryAttempt</td><td>deliveryAttemptNumber</td><td>Integer</td></tr>
 *   <tr><td>zoneType</td><td>deliveryZoneType</td><td>String</td></tr>
 *   <tr><td>zoneDifficulty</td><td>zoneAccessDifficulty</td><td>String</td></tr>
 *   <tr><td>paymentMethod</td><td>paymentMethod</td><td>String</td></tr>
 *   <tr><td>clientSegment</td><td>clientSegment</td><td>String</td></tr>
 *   <tr><td>isRecurringClient</td><td>isRecurringClient</td><td>Boolean</td></tr>
 *   <tr><td>dayOfWeek</td><td>dayOfWeek</td><td>String (enum name)</td></tr>
 *   <tr><td>isHoliday</td><td>isPublicHoliday</td><td>Boolean</td></tr>
 *   <tr><td>isWeekend</td><td>isWeekend()</td><td>Boolean</td></tr>
 *   <tr><td>isWeekday</td><td>isWeekday()</td><td>Boolean</td></tr>
 *   <tr><td>policyOwnerType</td><td>policyOwnerType</td><td>String</td></tr>
 *   <tr><td>storageHours</td><td>storageHours</td><td>Integer</td></tr>
 *   <tr><td>networkHops</td><td>networkHopCount</td><td>Integer</td></tr>
 * </table>
 *
 * @author MANFOUO Braun
 */
@RequiredArgsConstructor
public class VariableNode extends AstNode {

    private final String variableName;

    @Override
    public Object evaluate(PricingContext ctx) {
        return switch (variableName.toLowerCase()) {
            // ── v1.0 — Original variables ─────────────────────────────────────
            case "weight"                    -> ctx.getWeightKg();
            case "distance"                  -> ctx.getDistanceKm();
            case "packagetype"               -> ctx.getPackageTypes() != null
                                                 && !ctx.getPackageTypes().isEmpty()
                                                 ? ctx.getPackageTypes().get(0).name() : "";
            case "priority"                  -> ctx.getPriority() != null
                                                 ? ctx.getPriority().name() : "";
            case "clienttxcount",
                 "txcount"                   -> ctx.getClientTxCount();
            case "timeofday"                 -> ctx.getTimeOfDay();
            case "weather"                   -> ctx.getWeatherCondition() != null
                                                 ? ctx.getWeatherCondition().name() : "";
            case "roadtype"                  -> ctx.getRoadType() != null
                                                 ? ctx.getRoadType().name() : "";
            case "israining"                 -> ctx.isRaining();
            case "isroaddegraded"            -> ctx.isRoadDegraded();
            // FreelancerOrg context ──────────────────────────────────
            case "vehicletype"               -> ctx.getSelectedVehicleType() != null
                                                 ? ctx.getSelectedVehicleType().toUpperCase() : "";
            case "activeequipmenttypes",
                 "equipmenttypes"            -> ctx.getActiveEquipmentTypeCodes();
            case "specialization"            -> ctx.getActivatedSpecialization() != null
                                                 ? ctx.getActivatedSpecialization().toUpperCase() : "";
            case "issubdeliverer"            -> Boolean.TRUE.equals(ctx.getIsSubDelivererAssigned());
            // Enriched parcel context ────────────────────────────────
            case "packagecount"              -> ctx.getPackageCount() != null
                                                 ? ctx.getPackageCount() : 0;
            case "declaredvalue"             -> ctx.getDeclaredValue();
            case "requiresrefrigeration"     -> Boolean.TRUE.equals(ctx.getRequiresRefrigeration());
            case "requiresassembly"          -> Boolean.TRUE.equals(ctx.getRequiresAssembly());
            case "requiresidcheck"           -> Boolean.TRUE.equals(ctx.getRequiresIDCheck());
            case "deliveryattempt",
                 "attemptNumber"             -> ctx.getDeliveryAttemptNumber() != null
                                                 ? ctx.getDeliveryAttemptNumber() : 1;
            // Geographic context ──────────────────────────────────────
            case "zonetype",
                 "deliveryzonetype"          -> ctx.getDeliveryZoneType() != null
                                                 ? ctx.getDeliveryZoneType().toUpperCase() : "";
            case "zonedifficulty",
                 "zoneaccessdifficulty"      -> ctx.getZoneAccessDifficulty() != null
                                                 ? ctx.getZoneAccessDifficulty().toUpperCase() : "";
            // Client context ──────────────────────────────────────────
            case "paymentmethod"             -> ctx.getPaymentMethod() != null
                                                 ? ctx.getPaymentMethod().toUpperCase() : "";
            case "clientsegment"             -> ctx.getClientSegment() != null
                                                 ? ctx.getClientSegment().toUpperCase() : "";
            case "isrecurringclient"         -> Boolean.TRUE.equals(ctx.getIsRecurringClient());
            // Extended temporal context ─────────────────────────────
            case "dayofweek"                 -> ctx.getDayOfWeek() != null
                                                 ? ctx.getDayOfWeek().name() : "";
            case "isholiday",
                 "ispublicholiday"           -> Boolean.TRUE.equals(ctx.getIsPublicHoliday());
            case "isweekend"                 -> ctx.isWeekend();
            case "isweekday"                 -> ctx.isWeekday();
            case "isnonworkingday"           -> ctx.isNonWorkingDay();
            // Policy owner context ───────────────────────────────────
            case "policyownertype"           -> ctx.getPolicyOwnerType() != null
                                                 ? ctx.getPolicyOwnerType().toUpperCase() : "";
            // Hub Point context ───────────────────────────────────────
            case "storagehours"              -> ctx.getStorageHours() != null
                                                 ? ctx.getStorageHours() : 0;
            // Link Network context ────────────────────────────────────
            case "networkhops",
                 "networkhopcount"           -> ctx.getNetworkHopCount() != null
                                                 ? ctx.getNetworkHopCount() : 0;
            default -> throw new DslEvaluationException(
                    "Unknown DSL variable: '" + variableName + "'. "
                    + "Supported (): weight, distance, packageType, priority, "
                    + "clientTxCount, timeOfDay, weather, roadType, isRaining, isRoadDegraded, "
                    + "vehicleType, activeEquipmentTypes, specialization, isSubDeliverer, "
                    + "packageCount, declaredValue, requiresRefrigeration, requiresAssembly, "
                    + "requiresIdCheck, deliveryAttempt, zoneType, zoneDifficulty, "
                    + "paymentMethod, clientSegment, isRecurringClient, "
                    + "dayOfWeek, isHoliday, isWeekend, isWeekday, "
                    + "policyOwnerType, storageHours, networkHops");
        };
    }

    @Override
    public String nodeType() {
        return "Var(" + variableName + ")";
    }

    public String getVariableName() {
        return variableName;
    }
}
