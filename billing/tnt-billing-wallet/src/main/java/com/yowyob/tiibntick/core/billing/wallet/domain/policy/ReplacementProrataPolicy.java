package com.yowyob.tiibntick.core.billing.wallet.domain.policy;

import com.yowyob.tiibntick.core.billing.wallet.domain.model.CourierLeg;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Courier part of a delivery whose courier was replaced (QO-11 / D-153, pure domain). The Kernel escrow cannot change
 * its parts once held: it pays the whole courier part to the courier named at the hold (the payee). Each other courier
 * is paid by the compensation fund at the prorata of the distance he covered, and the payee owes the fund the same
 * amount.
 *
 * <p>Distances: the courier who leaves covered {@code replacedLegKm} of his leg; the last courier covers the
 * {@code remainingKm} of the last change. A courier who carried the parcel twice adds his legs. Replaced before the
 * pickup, the leaving courier covered 0 km: when every leg but the last is 0 km, the last courier gets the whole part
 * whatever the remaining distance. Amounts are rounded half-up to the unit (XAF); their sum never exceeds the part.
 *
 * <p>Undecided (left to the administrator, admin spec §5.2): a distance is unknown, the changes are not a chain, the
 * first courier is not the payee, or the distances add up to 0.
 */
public final class ReplacementProrataPolicy {

    public static final String DISTANCE_UNKNOWN = "DISTANCE_UNKNOWN";
    public static final String LEGS_NOT_CHAINED = "LEGS_NOT_CHAINED";
    public static final String PAYEE_NOT_FIRST_COURIER = "PAYEE_NOT_FIRST_COURIER";
    public static final String NO_DISTANCE = "NO_DISTANCE";

    /**
     * A payment of the fund to a replacement courier.
     *
     * @param index rank of the courier among the paid ones (1-based, stable): part of the payment reference
     */
    public record Payment(int index, UUID courierActorId, BigDecimal amount, BigDecimal km) {
    }

    /** The payments, or why none can be decided ({@code undecidedReason} non-null). */
    public record Outcome(List<Payment> payments, String undecidedReason) {

        public boolean decided() {
            return undecidedReason == null;
        }

        static Outcome undecided(String reason) {
            return new Outcome(List.of(), reason);
        }
    }

    private ReplacementProrataPolicy() {
    }

    public static Outcome split(BigDecimal courierPart, UUID payee, List<CourierLeg> changes) {
        Objects.requireNonNull(courierPart, "courierPart");
        if (changes == null || changes.isEmpty() || courierPart.signum() <= 0) {
            return new Outcome(List.of(), null);
        }
        List<CourierLeg> legs = new ArrayList<>(changes);
        legs.sort(Comparator.comparingLong(CourierLeg::aggregateVersion));
        if (payee == null || !payee.equals(legs.get(0).replacedCourierId())) {
            return Outcome.undecided(PAYEE_NOT_FIRST_COURIER);
        }
        for (int i = 1; i < legs.size(); i++) {
            if (!Objects.equals(legs.get(i).replacedCourierId(), legs.get(i - 1).replacementCourierId())) {
                return Outcome.undecided(LEGS_NOT_CHAINED);
            }
        }
        CourierLeg last = legs.get(legs.size() - 1);
        boolean allBeforeLastAreZero = legs.stream()
                .allMatch(l -> l.replacedLegKm() != null && l.replacedLegKm().signum() == 0);
        Map<UUID, BigDecimal> km = new LinkedHashMap<>();
        if (allBeforeLastAreZero) {
            km.put(last.replacementCourierId(), BigDecimal.ONE);
        } else {
            for (CourierLeg l : legs) {
                if (l.replacedLegKm() == null) {
                    return Outcome.undecided(DISTANCE_UNKNOWN);
                }
                km.merge(l.replacedCourierId(), l.replacedLegKm(), BigDecimal::add);
            }
            if (last.remainingKm() == null) {
                return Outcome.undecided(DISTANCE_UNKNOWN);
            }
            km.merge(last.replacementCourierId(), last.remainingKm(), BigDecimal::add);
        }
        BigDecimal total = km.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0) {
            return Outcome.undecided(NO_DISTANCE);
        }
        BigDecimal part = courierPart.setScale(0, RoundingMode.HALF_UP);
        List<Payment> payments = new ArrayList<>();
        BigDecimal paid = BigDecimal.ZERO;
        int index = 0;
        for (Map.Entry<UUID, BigDecimal> e : km.entrySet()) {
            if (e.getKey().equals(payee)) {
                continue;
            }
            BigDecimal amount = part.multiply(e.getValue()).divide(total, 0, RoundingMode.HALF_UP)
                    .min(part.subtract(paid));
            if (amount.signum() > 0) {
                payments.add(new Payment(++index, e.getKey(), amount, allBeforeLastAreZero ? null : e.getValue()));
                paid = paid.add(amount);
            }
        }
        return new Outcome(List.copyOf(payments), null);
    }
}
