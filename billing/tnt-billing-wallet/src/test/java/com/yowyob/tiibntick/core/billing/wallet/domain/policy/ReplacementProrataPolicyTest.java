package com.yowyob.tiibntick.core.billing.wallet.domain.policy;

import com.yowyob.tiibntick.core.billing.wallet.domain.model.CourierLeg;
import com.yowyob.tiibntick.core.billing.wallet.domain.policy.ReplacementProrataPolicy.Outcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("QO-11 / D-153: replacement couriers paid by the fund at the prorata of the distance")
class ReplacementProrataPolicyTest {

    static final UUID A = UUID.randomUUID();
    static final UUID B = UUID.randomUUID();
    static final UUID C = UUID.randomUUID();
    static final BigDecimal PART = new BigDecimal("2850");

    static CourierLeg leg(long version, UUID replaced, UUID replacement, String legKm, String remainingKm) {
        return new CourierLeg(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), version,
                replaced, replacement, legKm == null ? null : new BigDecimal(legKm),
                remainingKm == null ? null : new BigDecimal(remainingKm), null);
    }

    @Test
    @DisplayName("one hand-over: 3 km by A, 7 km left to B → the fund pays B 70 %")
    void oneHandover() {
        Outcome o = ReplacementProrataPolicy.split(PART, A, List.of(leg(5, A, B, "3.000", "7.000")));
        assertThat(o.decided()).isTrue();
        assertThat(o.payments()).singleElement().satisfies(p -> {
            assertThat(p.index()).isEqualTo(1);
            assertThat(p.courierActorId()).isEqualTo(B);
            assertThat(p.amount()).isEqualByComparingTo("1995");
        });
    }

    @Test
    @DisplayName("two hand-overs (A→B→C), given out of order: B and C paid their legs, ranks 1 and 2, sum within the part")
    void twoHandovers() {
        Outcome o = ReplacementProrataPolicy.split(PART, A, List.of(leg(9, B, C, "2.000", "5.000"),
                leg(5, A, B, "3.000", "7.000")));
        assertThat(o.payments()).extracting(ReplacementProrataPolicy.Payment::courierActorId).containsExactly(B, C);
        assertThat(o.payments()).extracting(ReplacementProrataPolicy.Payment::amount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("570"), new BigDecimal("1425"));
        assertThat(o.payments()).extracting(ReplacementProrataPolicy.Payment::index).containsExactly(1, 2);
    }

    @Test
    @DisplayName("A hands to B then takes the parcel back: A keeps his own legs, B is paid his")
    void courierComesBack() {
        Outcome o = ReplacementProrataPolicy.split(PART, A, List.of(leg(5, A, B, "2.000", "8.000"),
                leg(9, B, A, "3.000", "5.000")));
        assertThat(o.payments()).singleElement().satisfies(p -> {
            assertThat(p.courierActorId()).isEqualTo(B);
            assertThat(p.amount()).isEqualByComparingTo("855");
        });
    }

    @Test
    @DisplayName("replaced before the pickup (0 km): the replacement gets the whole part, even without remaining distance")
    void beforePickup() {
        Outcome o = ReplacementProrataPolicy.split(PART, A, List.of(leg(5, A, B, "0.000", null)));
        assertThat(o.payments()).singleElement().satisfies(p -> {
            assertThat(p.courierActorId()).isEqualTo(B);
            assertThat(p.amount()).isEqualByComparingTo("2850");
        });
    }

    @Test
    @DisplayName("rounding never pays more than the part")
    void roundingCapped() {
        Outcome o = ReplacementProrataPolicy.split(new BigDecimal("100"), A, List.of(
                leg(1, A, B, "0.000", "1.000"), leg(2, B, C, "1.000", "1.000")));
        BigDecimal sum = o.payments().stream().map(ReplacementProrataPolicy.Payment::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isLessThanOrEqualTo(new BigDecimal("100"));
        assertThat(o.payments()).hasSize(2);
    }

    @Test
    @DisplayName("undecided: unknown distance, broken chain, payee not first, nothing covered; no change: no payment")
    void undecided() {
        assertThat(ReplacementProrataPolicy.split(PART, A, List.of(leg(5, A, B, null, "7.000"))).undecidedReason())
                .isEqualTo(ReplacementProrataPolicy.DISTANCE_UNKNOWN);
        assertThat(ReplacementProrataPolicy.split(PART, A, List.of(leg(5, A, B, "3.000", null))).undecidedReason())
                .isEqualTo(ReplacementProrataPolicy.DISTANCE_UNKNOWN);
        assertThat(ReplacementProrataPolicy.split(PART, A, List.of(leg(5, A, B, "3.000", "7.000"),
                leg(9, C, A, "1.000", "1.000"))).undecidedReason()).isEqualTo(ReplacementProrataPolicy.LEGS_NOT_CHAINED);
        assertThat(ReplacementProrataPolicy.split(PART, C, List.of(leg(5, A, B, "3.000", "7.000")))
                .undecidedReason()).isEqualTo(ReplacementProrataPolicy.PAYEE_NOT_FIRST_COURIER);
        assertThat(ReplacementProrataPolicy.split(PART, A, List.of(leg(5, A, B, "1.000", "0.000"),
                leg(6, B, C, "0.000", "0.000"))).decided()).isTrue();
        assertThat(ReplacementProrataPolicy.split(PART, A, List.of(leg(5, A, B, "0.000", "0.000"),
                leg(6, B, A, "0.000", "0.000"))).payments()).isEmpty();
        Outcome none = ReplacementProrataPolicy.split(PART, A, List.of());
        assertThat(none.decided()).isTrue();
        assertThat(none.payments()).isEmpty();
    }
}
