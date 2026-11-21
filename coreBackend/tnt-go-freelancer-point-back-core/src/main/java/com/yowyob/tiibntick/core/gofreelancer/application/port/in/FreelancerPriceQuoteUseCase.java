package com.yowyob.tiibntick.core.gofreelancer.application.port.in;

import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Prices a delivery for one freelancer with the billing engine ({@code tnt-billing-pricing}).
 *
 * <p>The freelancer's active {@code BillingPolicy} is looked up under every identity the freelancer
 * is known by (delivery-person id, Kernel user id…): a policy created by the legacy grid endpoint
 * and one applied from a billing template are both honoured. Without any policy the platform
 * default grid is priced by the same engine, so there is exactly one pricing code path.
 *
 * @author MANFOUO BRAUN
 */
public interface FreelancerPriceQuoteUseCase {

    /**
     * @param ownerIds identity ids to look the freelancer's policy up under, most specific first
     */
    Mono<Quote> quote(List<UUID> ownerIds, QuoteInput input);

    /**
     * Lot C-54 — same resolution, but the platform fallback is the grid of this vehicle (either
     * vocabulary; {@code null}/unknown = moto). A personal active policy still wins.
     */
    default Mono<Quote> quote(List<UUID> ownerIds, QuoteInput input, String vehicleType) {
        return quote(ownerIds, input);
    }

    /**
     * Lot C-54 — the platform price of this vehicle only (no personal policy): the indicative price
     * shown to a courier applying to an announcement.
     */
    default Mono<Quote> platformQuote(String vehicleType, QuoteInput input) {
        return quote(List.of(), input, vehicleType);
    }

    /** What is being delivered, and when. Every field is optional except the metrics (defaults to 0). */
    record QuoteInput(
            double distanceKm,
            double weightKg,
            double volumeCbm,
            boolean fragile,
            boolean perishable,
            Integer waitingMinutes,
            String priority,
            LocalDateTime scheduledAt,
            String paymentMethod,
            Double declaredValueXaf) {

        public static QuoteInput of(double distanceKm, double weightKg, double volumeCbm,
                                    boolean fragile, boolean perishable) {
            return new QuoteInput(distanceKm, weightKg, volumeCbm, fragile, perishable,
                    null, null, null, null, null);
        }
    }

    /**
     * @param source   {@code POLICY} (an active billing policy of the freelancer), {@code LEGACY_GRID}
     *                 (a pre-billing grid priced on the fly) or {@code PLATFORM_DEFAULT}
     * @param policyId the billing policy that produced the price; {@code null} for the two fallbacks
     */
    record Quote(double totalPrice, String currency, String breakdown, String source, UUID policyId) { }
}
