package com.yowyob.tiibntick.core.administration.domain.thresholds;

import com.yowyob.tiibntick.common.exception.TntValidationException;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.DeliveryThreshold;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.DeliveryThresholdValues;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.OrganizationDeliveryThresholds;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.PlatformDeliveryThresholds;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.RelayStorageBounds;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import static com.yowyob.tiibntick.core.administration.domain.model.thresholds.DeliveryThreshold.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Delivery thresholds — defaults and validation guards")
class DeliveryThresholdsDomainTest {

    private static final UUID ORG = UUID.randomUUID();

    @Test
    @DisplayName("defaults are the user's decisions: [B.5] 3/7/J-1, QO-8 3 returns, QO-18 15/0/30/10/20 min, SLA 48/24/8 h")
    void defaultsAreTheDecidedValues() {
        PlatformDeliveryThresholds p = PlatformDeliveryThresholds.defaults();
        assertThat(p.get(MAX_DELIVERY_ATTEMPTS)).isEqualTo(3);
        assertThat(p.get(RELAY_STORAGE_DAYS)).isEqualTo(7);
        assertThat(p.get(RELAY_STORAGE_REMINDER_DAYS_BEFORE)).isEqualTo(1);
        assertThat(p.get(MAX_FAILED_RETURNS)).isEqualTo(3);
        assertThat(p.get(PICKUP_LATE_GRACE_MINUTES)).isEqualTo(15);
        assertThat(p.get(DELIVERY_LATE_GRACE_MINUTES)).isZero();
        assertThat(p.get(DELIVERY_LATE_FALLBACK_HOURS_STANDARD)).isEqualTo(48);
        assertThat(p.get(DELIVERY_LATE_FALLBACK_HOURS_EXPRESS)).isEqualTo(24);
        assertThat(p.get(DELIVERY_LATE_FALLBACK_HOURS_SAME_DAY)).isEqualTo(8);
        assertThat(p.get(ETA_EXCEEDED_GRACE_MINUTES)).isEqualTo(30);
        assertThat(p.get(GPS_SIGNAL_LOST_MINUTES)).isEqualTo(10);
        assertThat(p.get(PROLONGED_STOP_MINUTES)).isEqualTo(20);
        assertThat(p.independentRelayStorage()).isEqualTo(new RelayStorageBounds(3, 30)); // D-130
        assertThatCode(p::validate).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(DeliveryThreshold.class)
    @DisplayName("platform: each threshold accepts its min and max and refuses min-1 and max+1")
    void platformRangePerThreshold(DeliveryThreshold t) {
        for (int ok : new int[]{t.min(), t.max()}) {
            assertThatCode(() -> platform(with(t, ok)).validate()).as(t + "=" + ok).doesNotThrowAnyException();
        }
        for (int ko : new int[]{t.min() - 1, t.max() + 1}) {
            assertThatThrownBy(() -> platform(with(t, ko)).validate()).as(t + "=" + ko)
                    .isInstanceOf(TntValidationException.class)
                    .satisfies(e -> assertThat(((TntValidationException) e).getViolations())
                            .anyMatch(v -> v.field().equals(t.field())));
        }
    }

    /** The other thresholds at their default, {@code t} at {@code value}, storage/reminder kept consistent. */
    private static DeliveryThresholdValues with(DeliveryThreshold t, int value) {
        Map<DeliveryThreshold, Integer> map = new EnumMap<>(DeliveryThresholdValues.defaults().asMap());
        map.put(t, value);
        if (t == RELAY_STORAGE_DAYS) {
            map.put(RELAY_STORAGE_REMINDER_DAYS_BEFORE, 0);
        }
        if (t == RELAY_STORAGE_REMINDER_DAYS_BEFORE) {
            map.put(RELAY_STORAGE_DAYS, 60);
        }
        return DeliveryThresholdValues.of(map);
    }

    private static PlatformDeliveryThresholds platform(DeliveryThresholdValues values) {
        return platform(values, RelayStorageBounds.DEFAULT);
    }

    private static PlatformDeliveryThresholds platform(DeliveryThresholdValues values, RelayStorageBounds bounds) {
        return new PlatformDeliveryThresholds(values, bounds, null, Instant.now(), 1);
    }

    @Test
    @DisplayName("platform: the reminder must come strictly before the end of storage")
    void platformReminderBeforeStorage() {
        Map<DeliveryThreshold, Integer> map = new EnumMap<>(DeliveryThresholdValues.defaults().asMap());
        map.put(RELAY_STORAGE_DAYS, 2);
        map.put(RELAY_STORAGE_REMINDER_DAYS_BEFORE, 1);
        assertThatCode(() -> platform(DeliveryThresholdValues.of(map)).validate()).doesNotThrowAnyException();
        map.put(RELAY_STORAGE_REMINDER_DAYS_BEFORE, 2);
        assertThatThrownBy(() -> platform(DeliveryThresholdValues.of(map)).validate())
                .isInstanceOf(TntValidationException.class).hasMessageContaining("platform");
    }

    @Test
    @DisplayName("platform: every threshold is required")
    void platformMissingValue() {
        Map<DeliveryThreshold, Integer> map = new EnumMap<>(DeliveryThresholdValues.defaults().asMap());
        map.remove(PROLONGED_STOP_MINUTES);
        assertThatThrownBy(() -> platform(DeliveryThresholdValues.of(map)).validate())
                .isInstanceOf(TntValidationException.class)
                .satisfies(e -> assertThat(((TntValidationException) e).getViolations())
                        .anyMatch(v -> v.field().equals("prolongedStopMinutes")));
    }

    @Test
    @DisplayName("platform: QO-29 relay bounds within the 1..60 input range (D-131), min <= max")
    void platformRelayBounds() {
        DeliveryThresholdValues d = DeliveryThresholdValues.defaults();
        assertThatCode(() -> platform(d, new RelayStorageBounds(3, 14)).validate()).doesNotThrowAnyException();
        assertThatCode(() -> platform(d, new RelayStorageBounds(5, 5)).validate()).doesNotThrowAnyException();
        assertThatCode(() -> platform(d, new RelayStorageBounds(1, 60)).validate()).doesNotThrowAnyException();
        assertThatThrownBy(() -> platform(d, new RelayStorageBounds(10, 3)).validate())
                .isInstanceOf(TntValidationException.class);
        assertThatThrownBy(() -> platform(d, new RelayStorageBounds(0, 30)).validate())
                .isInstanceOf(TntValidationException.class);
        assertThatThrownBy(() -> platform(d, new RelayStorageBounds(3, 61)).validate())
                .isInstanceOf(TntValidationException.class);
    }

    @Test
    @DisplayName("relay bounds: 3..30 days by default (D-130), no 1..60 fallback; clamp brings a value back in range")
    void relayBoundsClamp() {
        assertThat(RelayStorageBounds.DEFAULT).isEqualTo(new RelayStorageBounds(3, 30));
        assertThat(RelayStorageBounds.DEFAULT.contains(2)).isFalse();
        assertThat(RelayStorageBounds.DEFAULT.contains(3)).isTrue();
        assertThat(RelayStorageBounds.DEFAULT.contains(30)).isTrue();
        assertThat(RelayStorageBounds.DEFAULT.contains(31)).isFalse();
        assertThat(RelayStorageBounds.DEFAULT.clamp(60)).isEqualTo(30);
        RelayStorageBounds b = new RelayStorageBounds(3, 10);
        assertThat(b.contains(2)).isFalse();
        assertThat(b.contains(3)).isTrue();
        assertThat(b.contains(10)).isTrue();
        assertThat(b.contains(11)).isFalse();
        assertThat(b.clamp(1)).isEqualTo(3);
        assertThat(b.clamp(7)).isEqualTo(7);
        assertThat(b.clamp(30)).isEqualTo(10);
    }

    @ParameterizedTest
    @EnumSource(DeliveryThreshold.class)
    @DisplayName("organization: each overridden value accepts its min and refuses max+1")
    void organizationRangePerThreshold(DeliveryThreshold t) {
        PlatformDeliveryThresholds wide = platform(with(RELAY_STORAGE_DAYS, 60));
        int ok = t == RELAY_STORAGE_DAYS ? 2 : t.min();
        assertThatCode(() -> org(Map.of(t, ok)).validateAgainst(wide)).doesNotThrowAnyException();
        assertThatThrownBy(() -> org(Map.of(t, t.max() + 1)).validateAgainst(wide))
                .isInstanceOf(TntValidationException.class);
    }

    @Test
    @DisplayName("organization: reminder checked against the effective storage (override, else platform)")
    void organizationReminderAgainstEffectiveStorage() {
        PlatformDeliveryThresholds p = PlatformDeliveryThresholds.defaults(); // 7 days, J-1
        assertThatCode(() -> org(Map.of(RELAY_STORAGE_REMINDER_DAYS_BEFORE, 6)).validateAgainst(p))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> org(Map.of(RELAY_STORAGE_REMINDER_DAYS_BEFORE, 7)).validateAgainst(p))
                .isInstanceOf(TntValidationException.class);
        // storage shortened to the platform reminder: refused (1 day, J-1)
        assertThatThrownBy(() -> org(Map.of(RELAY_STORAGE_DAYS, 1)).validateAgainst(p))
                .isInstanceOf(TntValidationException.class);
        assertThatCode(() -> org(Map.of(RELAY_STORAGE_DAYS, 1, RELAY_STORAGE_REMINDER_DAYS_BEFORE, 0))
                .validateAgainst(p)).doesNotThrowAnyException();
        // a threshold unrelated to storage never triggers the reminder check
        assertThatCode(() -> org(Map.of(MAX_DELIVERY_ATTEMPTS, 5)).validateAgainst(p)).doesNotThrowAnyException();
    }

    private static OrganizationDeliveryThresholds org(Map<DeliveryThreshold, Integer> values) {
        return new OrganizationDeliveryThresholds(ORG, DeliveryThresholdValues.of(values), null, Instant.now(), 0);
    }

    @Test
    @DisplayName("values: null entries mean not set; field map lists every threshold")
    void valuesSemantics() {
        Map<DeliveryThreshold, Integer> map = new EnumMap<>(DeliveryThreshold.class);
        map.put(MAX_DELIVERY_ATTEMPTS, 4);
        map.put(RELAY_STORAGE_DAYS, null);
        DeliveryThresholdValues v = DeliveryThresholdValues.of(map);
        assertThat(v.isSet(MAX_DELIVERY_ATTEMPTS)).isTrue();
        assertThat(v.isSet(RELAY_STORAGE_DAYS)).isFalse();
        assertThat(v.isComplete()).isFalse();
        assertThat(DeliveryThresholdValues.defaults().isComplete()).isTrue();
        assertThat(v.toFieldMap()).hasSize(DeliveryThreshold.values().length)
                .containsEntry("maxDeliveryAttempts", 4).containsEntry("relayStorageDays", null);
    }
}
