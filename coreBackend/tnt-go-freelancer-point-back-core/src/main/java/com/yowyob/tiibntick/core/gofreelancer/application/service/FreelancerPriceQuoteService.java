package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.billing.dsl.domain.model.DeliveryPriority;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PackageType;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PricingContext;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.BillingPolicy;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.PriceEvaluation;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.PriceLineItem;
import com.yowyob.tiibntick.core.billing.pricing.domain.port.in.IBillingPolicyUseCase;
import com.yowyob.tiibntick.core.billing.pricing.domain.port.in.IPricingUseCase;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.FreelancerPricingDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.FreelancerPriceQuoteUseCase;
import com.yowyob.tiibntick.core.gofreelancer.config.GofpCourierPricingProperties;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.vehicle.VehicleCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * {@link FreelancerPriceQuoteUseCase} on the billing engine.
 *
 * <p>Resolution order for a freelancer: (1) the most recently updated ACTIVE, currently valid
 * {@code BillingPolicy} owned under any of the given identities — whether created by the grid
 * endpoint or applied from a billing template; (2) the platform default grid. A policy the engine
 * cannot price (no rule matches the context) falls back to (2) rather than dropping the freelancer
 * from the candidate list. The legacy {@code delivery_person_pricing} grids are no longer read: they
 * were copied into billing policies once (legacy pricing backfill, lot L4; table dropped by gdd-final).
 *
 * @author MANFOUO BRAUN
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FreelancerPriceQuoteService implements FreelancerPriceQuoteUseCase {

    private final IBillingPolicyUseCase billingPolicyUseCase;
    private final IPricingUseCase pricingEngine;

    /**
     * Lot C-54 — platform grid of each vehicle. Optional (setter): absent, the defaults of
     * {@link GofpCourierPricingProperties} apply — the same values as {@code application.yml}.
     */
    private GofpCourierPricingProperties courierPricing = new GofpCourierPricingProperties();

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setCourierPricing(GofpCourierPricingProperties courierPricing) {
        if (courierPricing != null) this.courierPricing = courierPricing;
    }

    @Override
    public Mono<Quote> quote(List<UUID> ownerIds, QuoteInput input) {
        return quote(ownerIds, input, null);
    }

    @Override
    public Mono<Quote> platformQuote(String vehicleType, QuoteInput input) {
        return priceGrid(List.of(), platformGrid(vehicleType), toContext(input), "PLATFORM_DEFAULT");
    }

    /**
     * The platform grid of this vehicle (either vocabulary): the precise type's own grid (BIKE, SCOOTER, VAN), else its
     * category's grid; unknown or absent = moto (decision D-058).
     */
    FreelancerPricingDTO platformGrid(String vehicleType) {
        return courierPricing.gridForVehicle(vehicleType);
    }

    @Override
    public Mono<Quote> quote(List<UUID> ownerIds, QuoteInput input, String vehicleType) {
        List<UUID> owners = new ArrayList<>(new LinkedHashSet<>(ownerIds));
        PricingContext ctx = toContext(input);

        return policiesOf(owners).flatMap(all -> {
            BillingPolicy active = all.stream()
                    .filter(p -> p.isActive() && p.getValidFrom() != null && p.isValidNow())
                    .max(FreelancerGridPolicyMapper.BY_FRESHNESS).orElse(null);

            Mono<Quote> fromPolicy = active == null ? Mono.empty()
                    : price(active, ctx, "POLICY", active.getId())
                            .onErrorResume(e -> {
                                log.warn("Billing policy {} cannot price this delivery ({}) — falling back to the "
                                        + "platform grid", active.getId(), e.getMessage());
                                return Mono.empty();
                            });
            return fromPolicy
                    .switchIfEmpty(Mono.defer(() -> priceGrid(owners,
                            platformGrid(vehicleType), ctx, "PLATFORM_DEFAULT")));
        });
    }

    /** Every billing policy owned under any of the identities, whatever its status. */
    private Mono<List<BillingPolicy>> policiesOf(List<UUID> owners) {
        return Flux.fromIterable(owners)
                .concatMap(id -> billingPolicyUseCase.findByOwnerActorId(id.toString()))
                .collectList()
                .onErrorResume(e -> {
                    log.warn("Billing policy lookup failed for {} — falling back to the platform grid",
                            owners, e);
                    return Mono.just(List.of());
                });
    }

    private Mono<Quote> priceGrid(List<UUID> owners, FreelancerPricingDTO grid, PricingContext ctx, String source) {
        String owner = owners.isEmpty() ? "unknown" : owners.get(0).toString();
        return price(FreelancerGridPolicyMapper.ephemeralPolicy(owner, grid), ctx, source, null);
    }

    private Mono<Quote> price(BillingPolicy policy, PricingContext ctx, String source, UUID policyId) {
        return pricingEngine.evaluateWith(policy, ctx)
                .map(ev -> toQuote(ev, policy.currencyCode(), source, policyId));
    }

    // ── mapping ───────────────────────────────────────────────────────────────

    public static PricingContext toContext(QuoteInput in) {
        List<PackageType> types = new ArrayList<>();
        if (in.fragile()) types.add(PackageType.FRAGILE);
        if (in.perishable()) types.add(PackageType.PERISHABLE);
        if (types.isEmpty()) types.add(PackageType.STANDARD);
        LocalDateTime when = in.scheduledAt() != null ? in.scheduledAt() : LocalDateTime.now();
        DayOfWeek day = when.getDayOfWeek();
        PricingContext.PricingContextBuilder b = PricingContext.builder()
                .distanceKm(Math.max(0, in.distanceKm()))
                .weightKg(Math.max(0, in.weightKg()))
                .volumeCbm(in.volumeCbm() > 0 ? in.volumeCbm() : null)
                .waitingMinutes(in.waitingMinutes())
                .packageTypes(types)
                .priority(priorityOf(in.priority()))
                .timeOfDay(when.toLocalTime())
                .dayOfWeek(day)
                .packageCount(1)
                .paymentMethod(in.paymentMethod())
                .requiresRefrigeration(false);
        if (in.declaredValueXaf() != null) b.declaredValue(BigDecimal.valueOf(in.declaredValueXaf()));
        return b.build();
    }

    private static DeliveryPriority priorityOf(String raw) {
        if (raw == null || raw.isBlank()) return DeliveryPriority.NORMAL;
        return switch (raw.trim().toUpperCase()) {
            case "STANDARD" -> DeliveryPriority.NORMAL;
            case "EXPRESS", "PRIORITY" -> DeliveryPriority.HIGH;
            default -> {
                try {
                    yield DeliveryPriority.valueOf(raw.trim().toUpperCase());
                } catch (IllegalArgumentException e) {
                    yield DeliveryPriority.NORMAL;
                }
            }
        };
    }

    private static Quote toQuote(PriceEvaluation ev, String currency, String source, UUID policyId) {
        double total = ev.getSellingPrice().getAmount().setScale(2, RoundingMode.HALF_UP).doubleValue();
        return new Quote(total, currency, breakdown(ev, total, currency), source, policyId);
    }

    /** {@code Base 500 + 12.0 km × 100 XAF/km: 1200 + … = 1700 XAF} — always starts with "Base". */
    public static String breakdown(PriceEvaluation ev, double total, String currency) {
        StringBuilder sb = new StringBuilder();
        if (ev.getPriceBreakdown() != null) {
            for (PriceLineItem item : ev.getPriceBreakdown()) {
                if (item.getAmount() == null) continue;
                String amount = num(item.getAmount().getAmount());
                switch (item.getType()) {
                    case BASE -> sb.append("Base ").append(amount);
                    case PLATFORM_FEE, COMMISSION -> { /* not part of what the client pays the freelancer */ }
                    default -> {
                        if (sb.length() > 0) sb.append(item.isDiscount() ? " − " : " + ");
                        sb.append(item.getLabel()).append(": ").append(amount);
                    }
                }
            }
        }
        if (sb.length() == 0) sb.append("Base ").append(num(BigDecimal.valueOf(total)));
        return sb.append(" = ").append(num(BigDecimal.valueOf(total))).append(' ').append(currency).toString();
    }

    private static String num(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
