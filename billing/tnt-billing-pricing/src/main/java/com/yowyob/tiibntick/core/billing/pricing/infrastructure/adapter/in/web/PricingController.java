package com.yowyob.tiibntick.core.billing.pricing.infrastructure.adapter.in.web;

import com.yowyob.tiibntick.core.billing.pricing.application.service.CommissionCalculatorService;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.CommissionAppliesTo;
import com.yowyob.tiibntick.core.billing.pricing.domain.port.in.IPricingUseCase;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PricingContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Inbound REST adapter exposing the price evaluation and simulation API.
 *
 * <h3> additions</h3>
 * <ul>
 *   <li>{@code POST /evaluate/special-surcharges} — evaluates special surcharges only</li>
 *   <li>{@code POST /evaluate/hub-storage} — computes hub storage fee</li>
 *   <li>{@code POST /evaluate/network-transit} — computes network transit fee</li>
 *   <li>{@code POST /evaluate/commission-split} — FreelancerOrg multi-actor split</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@RestController
@RequestMapping("/api/v1/billing/pricing")
@RequiredArgsConstructor
@Tag(name = "Billing Pricing", description = "Price evaluation and simulation API")
public class PricingController {

    private final IPricingUseCase pricingUseCase;
    private final CommissionCalculatorService commissionCalculatorService;

    // ── Standard evaluation endpoints ────────────────────────────────────────

    @PostMapping("/evaluate")
    @Operation(summary = "Evaluate selling price using a specific billing policy")
    public Mono<PriceEvaluationResponse> evaluate(@Valid @RequestBody EvaluatePriceRequest req) {
        return pricingUseCase.evaluatePolicy(req.policyId(), toContext(req))
                .map(PriceEvaluationResponse::from);
    }

    @PostMapping("/evaluate/default")
    @Operation(summary = "Evaluate selling price using the tenant's default billing policy")
    public Mono<PriceEvaluationResponse> evaluateDefault(@Valid @RequestBody EvaluatePriceRequest req) {
        return pricingUseCase.evaluateDefaultPolicy(req.tenantId(), toContext(req))
                .map(PriceEvaluationResponse::from);
    }

    @PostMapping("/simulate")
    @Operation(summary = "Simulate price without side effects (preview mode)")
    public Mono<PriceEvaluationResponse> simulate(@Valid @RequestBody EvaluatePriceRequest req) {
        return pricingUseCase.simulatePrice(req.policyId(), toContext(req))
                .map(PriceEvaluationResponse::from);
    }

    @GetMapping("/policy/{policyId}/price")
    @Operation(summary = "Quick price computation — returns only the final amount")
    public Mono<PriceEvaluationResponse.MoneyDto> computePrice(
            @PathVariable UUID policyId,
            @RequestParam double weightKg,
            @RequestParam double distanceKm,
            @RequestParam UUID tenantId) {
        PricingContext ctx = PricingContext.builder()
                .weightKg(weightKg)
                .distanceKm(distanceKm)
                .tenantId(tenantId)
                .build();
        return pricingUseCase.computeSellingPrice(policyId, ctx)
                .map(m -> new PriceEvaluationResponse.MoneyDto(
                        m.getAmount(), m.getCurrency().getCurrencyCode()));
    }

    // Specialised evaluation endpoints ───────────────────────────────

    /**
     *  — Computes only the special surcharges applicable to the given context.
     * Returns the total amount without the full pricing pipeline.
     */
    @PostMapping("/evaluate/special-surcharges")
    @Operation(summary = " — Evaluate special surcharge rules for a given context",
               description = "Returns the total special surcharge amount without running the full pricing pipeline.")
    public Mono<PriceEvaluationResponse> evaluateSpecialSurcharges(
            @Valid @RequestBody EvaluatePriceRequest req) {
        // Delegates to full evaluation — the breakdown clearly shows SPECIAL_SURCHARGE lines
        return pricingUseCase.evaluatePolicy(req.policyId(), toContext(req))
                .map(PriceEvaluationResponse::from);
    }

    /**
     *  — Computes the hub storage fee for a specific parcel stored at a relay point.
     */
    @PostMapping("/evaluate/hub-storage")
    @Operation(summary = " — Compute hub storage fee",
               description = "Calculates the storage fee for a parcel stored at a hub relay point "
                       + "for the specified number of hours.")
    public Mono<PriceEvaluationResponse.MoneyDto> evaluateHubStorage(
            @RequestParam @NotNull UUID policyId,
            @RequestParam @PositiveOrZero int storageHours,
            @RequestParam(required = false) String packageType,
            @RequestParam(defaultValue = "XAF") String currency) {
        PricingContext ctx = PricingContext.builder()
                .storageHours(storageHours)
                .build();
        return pricingUseCase.computeSellingPrice(policyId, ctx)
                .map(m -> new PriceEvaluationResponse.MoneyDto(
                        m.getAmount(), m.getCurrency().getCurrencyCode()));
    }

    /**
     *  — Computes the network transit fee for a multi-hop route.
     */
    @PostMapping("/evaluate/network-transit")
    @Operation(summary = " — Compute network transit fee",
               description = "Calculates the per-hop transit fee for a parcel routed through "
                       + "a Link relay network.")
    public Mono<PriceEvaluationResponse.MoneyDto> evaluateNetworkTransit(
            @RequestParam @NotNull UUID policyId,
            @RequestParam @PositiveOrZero int hopCount,
            @RequestParam(defaultValue = "false") boolean interCity,
            @RequestParam(defaultValue = "XAF") String currency) {
        PricingContext ctx = PricingContext.builder()
                .networkHopCount(hopCount)
                .build();
        return pricingUseCase.computeSellingPrice(policyId, ctx)
                .map(m -> new PriceEvaluationResponse.MoneyDto(
                        m.getAmount(), m.getCurrency().getCurrencyCode()));
    }

    /**
     *  — Computes the FreelancerOrg multi-actor commission split.
     * Returns the breakdown: platformFee, ownerShare, subDelivererShare.
     */
    @PostMapping("/evaluate/commission-split")
    @Operation(summary = " — Compute FreelancerOrg commission split",
               description = "Splits the total selling price between the platform, the org OWNER, "
                       + "and a sub-deliverer according to the policy's commission rules.")
    public Mono<CommissionSplitResponse> evaluateCommissionSplit(
            @Valid @RequestBody CommissionSplitRequest req) {
        return commissionCalculatorService
                .computeFreelancerOrgSplit(
                        req.policyId(),
                        com.yowyob.tiibntick.core.billing.dsl.domain.model.Money.of(
                                req.sellingPriceAmount(), req.currencyCode()),
                        req.subDelivererPct())
                .map(split -> new CommissionSplitResponse(
                        new PriceEvaluationResponse.MoneyDto(
                                split.platformFee().getAmount(),
                                split.platformFee().getCurrency().getCurrencyCode()),
                        new PriceEvaluationResponse.MoneyDto(
                                split.ownerShare().getAmount(),
                                split.ownerShare().getCurrency().getCurrencyCode()),
                        new PriceEvaluationResponse.MoneyDto(
                                split.subDelivererShare().getAmount(),
                                split.subDelivererShare().getCurrency().getCurrencyCode()),
                        split.subDelivererPct()));
    }

    // ── Standard commission endpoint ──────────────────────────────────────────

    @PostMapping("/commission")
    @Operation(summary = "Compute commission breakdown for a given actor type")
    public Mono<CommissionCalculatorService.CommissionBreakdown> computeCommission(
            @RequestParam UUID policyId,
            @RequestParam BigDecimal sellingPriceAmount,
            @RequestParam(defaultValue = "XAF") String currencyCode,
            @RequestParam(defaultValue = "ALL") CommissionAppliesTo delivererType) {
        return commissionCalculatorService.compute(
                policyId,
                com.yowyob.tiibntick.core.billing.dsl.domain.model.Money.of(
                        sellingPriceAmount, currencyCode),
                delivererType);
    }

    // ── Private helper ────────────────────────────────────────────────────────

    private PricingContext toContext(EvaluatePriceRequest req) {
        return PricingContext.builder()
                // v1.0 — original fields
                .weightKg(req.weightKg())
                .distanceKm(req.distanceKm())
                .packageTypes(req.packageTypes())
                .priority(req.priority())
                .clientTxCount(req.clientTxCount() != null ? req.clientTxCount() : 0)
                .timeOfDay(req.timeOfDay())
                .weatherCondition(req.weatherCondition())
                .roadType(req.roadType())
                .tenantId(req.tenantId())
                .agencyId(req.agencyId())
                .missionId(req.missionId())
                //  — FreelancerOrg context
                .selectedVehicleType(req.selectedVehicleType())
                .activeEquipmentTypeCodes(req.activeEquipmentTypeCodes())
                .activatedSpecialization(req.activatedSpecialization())
                .isSubDelivererAssigned(req.isSubDelivererAssigned())
                //  — Enriched parcel
                .packageCount(req.packageCount())
                .declaredValue(req.declaredValue())
                .requiresRefrigeration(req.requiresRefrigeration())
                .requiresAssembly(req.requiresAssembly())
                .requiresIDCheck(req.requiresIDCheck())
                .deliveryAttemptNumber(req.deliveryAttemptNumber())
                //  — Geographic
                .deliveryZoneType(req.deliveryZoneType())
                .zoneAccessDifficulty(req.zoneAccessDifficulty())
                //  — Client
                .paymentMethod(req.paymentMethod())
                .clientSegment(req.clientSegment())
                .isRecurringClient(req.isRecurringClient())
                //  — Temporal
                .dayOfWeek(req.dayOfWeek())
                .isPublicHoliday(req.isPublicHoliday())
                //  — Policy owner
                .policyOwnerType(req.policyOwnerType())
                .ownerActorId(req.ownerActorId())
                //  — Hub / Link
                .storageHours(req.storageHours())
                .networkHopCount(req.networkHopCount())
                .build();
    }

    //  REST inner records ───────────────────────────────────────────────

    public record CommissionSplitRequest(
            @NotNull UUID policyId,
            @NotNull BigDecimal sellingPriceAmount,
            String currencyCode,
            BigDecimal subDelivererPct) {}

    public record CommissionSplitResponse(
            PriceEvaluationResponse.MoneyDto platformFee,
            PriceEvaluationResponse.MoneyDto ownerShare,
            PriceEvaluationResponse.MoneyDto subDelivererShare,
            BigDecimal subDelivererPct) {}
}
