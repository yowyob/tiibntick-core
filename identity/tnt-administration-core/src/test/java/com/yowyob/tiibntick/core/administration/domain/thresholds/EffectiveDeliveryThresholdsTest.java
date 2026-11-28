package com.yowyob.tiibntick.core.administration.domain.thresholds;

import com.yowyob.tiibntick.core.administration.domain.model.thresholds.DeliveryThreshold;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.DeliveryThresholdValues;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.EffectiveDeliveryThresholds;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.OrganizationDeliveryThresholds;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.PlatformDeliveryThresholds;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.RelayStorageBounds;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.ResolvedThreshold;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.ThresholdSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static com.yowyob.tiibntick.core.administration.domain.model.thresholds.DeliveryThreshold.*;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Effective thresholds — platform → organization, with the source of each value")
class EffectiveDeliveryThresholdsTest {

    private static final UUID ORG = UUID.randomUUID();
    private static final PlatformDeliveryThresholds PLATFORM = new PlatformDeliveryThresholds(
            DeliveryThresholdValues.defaults(), new RelayStorageBounds(3, 10), null, Instant.now(), 4);
    private static final OrganizationDeliveryThresholds OVERRIDE = new OrganizationDeliveryThresholds(ORG,
            DeliveryThresholdValues.of(Map.of(MAX_DELIVERY_ATTEMPTS, 5, RELAY_STORAGE_DAYS, 14,
                    PICKUP_LATE_GRACE_MINUTES, 45)), null, Instant.now(), 2);

    @Test
    @DisplayName("no organization: every value is the platform's")
    void platformOnly() {
        EffectiveDeliveryThresholds e = EffectiveDeliveryThresholds.of(PLATFORM, null, true, OVERRIDE);
        assertThat(e.agencyOrganization()).isFalse();
        for (DeliveryThreshold t : DeliveryThreshold.values()) {
            assertThat(e.get(t)).isEqualTo(new ResolvedThreshold(t.defaultValue(), ThresholdSource.PLATFORM));
        }
        assertThat(e.independentRelayStorage()).isEqualTo(new RelayStorageBounds(3, 10));
    }

    @Test
    @DisplayName("agency with an override: each threshold resolved separately (set → ORGANIZATION, unset → PLATFORM)")
    void agencyOverridePerField() {
        EffectiveDeliveryThresholds e = EffectiveDeliveryThresholds.of(PLATFORM, ORG, true, OVERRIDE);
        assertThat(e.agencyOrganization()).isTrue();
        assertThat(e.get(MAX_DELIVERY_ATTEMPTS)).isEqualTo(new ResolvedThreshold(5, ThresholdSource.ORGANIZATION));
        assertThat(e.get(RELAY_STORAGE_DAYS)).isEqualTo(new ResolvedThreshold(14, ThresholdSource.ORGANIZATION));
        assertThat(e.get(PICKUP_LATE_GRACE_MINUTES)).isEqualTo(new ResolvedThreshold(45, ThresholdSource.ORGANIZATION));
        assertThat(e.get(RELAY_STORAGE_REMINDER_DAYS_BEFORE)).isEqualTo(new ResolvedThreshold(1, ThresholdSource.PLATFORM));
        assertThat(e.maxFailedReturns()).isEqualTo(3);
        assertThat(e.get(PROLONGED_STOP_MINUTES).source()).isEqualTo(ThresholdSource.PLATFORM);
        assertThat(e.maxDeliveryAttempts()).isEqualTo(5);
    }

    @Test
    @DisplayName("organization that is not an agency: its override is ignored")
    void nonAgencyIgnored() {
        EffectiveDeliveryThresholds e = EffectiveDeliveryThresholds.of(PLATFORM, ORG, false, OVERRIDE);
        assertThat(e.agencyOrganization()).isFalse();
        assertThat(e.get(MAX_DELIVERY_ATTEMPTS)).isEqualTo(new ResolvedThreshold(3, ThresholdSource.PLATFORM));
    }

    @Test
    @DisplayName("relay storage: agency relay → agency value, its own value ignored")
    void relayStorageAgency() {
        EffectiveDeliveryThresholds e = EffectiveDeliveryThresholds.of(PLATFORM, ORG, true, OVERRIDE);
        assertThat(e.relayStorageDays(4)).isEqualTo(new ResolvedThreshold(14, ThresholdSource.ORGANIZATION));
        EffectiveDeliveryThresholds noOverride = EffectiveDeliveryThresholds.of(PLATFORM, ORG, true, null);
        assertThat(noOverride.relayStorageDays(4)).isEqualTo(new ResolvedThreshold(7, ThresholdSource.PLATFORM));
    }

    @Test
    @DisplayName("relay storage: independent relay (QO-29) → its own value within the admin's bounds, else platform")
    void relayStorageIndependent() {
        EffectiveDeliveryThresholds e = EffectiveDeliveryThresholds.of(PLATFORM, ORG, false, null);
        assertThat(e.relayStorageDays(5)).isEqualTo(new ResolvedThreshold(5, ThresholdSource.RELAY));
        assertThat(e.relayStorageDays(1)).isEqualTo(new ResolvedThreshold(3, ThresholdSource.RELAY));
        assertThat(e.relayStorageDays(30)).isEqualTo(new ResolvedThreshold(10, ThresholdSource.RELAY));
        assertThat(e.relayStorageDays(null)).isEqualTo(new ResolvedThreshold(7, ThresholdSource.PLATFORM));
        EffectiveDeliveryThresholds noOrg = EffectiveDeliveryThresholds.of(PLATFORM, null, false, null);
        assertThat(noOrg.relayStorageDays(8)).isEqualTo(new ResolvedThreshold(8, ThresholdSource.RELAY));

        // D-130 default bounds 3..30: no 1..60 fallback any more
        PlatformDeliveryThresholds defaults = PlatformDeliveryThresholds.defaults();
        assertThat(EffectiveDeliveryThresholds.of(defaults, null, false, null).relayStorageDays(45))
                .isEqualTo(new ResolvedThreshold(30, ThresholdSource.RELAY));
        assertThat(EffectiveDeliveryThresholds.of(defaults, null, false, null).relayStorageDays(1))
                .isEqualTo(new ResolvedThreshold(3, ThresholdSource.RELAY));
    }
}
