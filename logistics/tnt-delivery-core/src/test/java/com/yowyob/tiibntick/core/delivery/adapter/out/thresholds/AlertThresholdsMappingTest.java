package com.yowyob.tiibntick.core.delivery.adapter.out.thresholds;

import com.yowyob.tiibntick.core.administration.domain.model.thresholds.DeliveryThreshold;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.DeliveryThresholdValues;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.EffectiveDeliveryThresholds;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.OrganizationDeliveryThresholds;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.PlatformDeliveryThresholds;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryThresholdsPort.Alerts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Lot L3: the alert thresholds of a delivery are the administration's (QO-18, D-131 to D-133). */
class AlertThresholdsMappingTest {

    @Test
    @DisplayName("platform defaults of the administration = Alerts.DEFAULT")
    void platformDefaults() {
        EffectiveDeliveryThresholds platform = EffectiveDeliveryThresholds.of(PlatformDeliveryThresholds.defaults(),
                null, false, null);
        assertThat(AdministrationDeliveryThresholdsAdapter.alerts(platform)).isEqualTo(Alerts.DEFAULT);
    }

    @Test
    @DisplayName("an agency override is applied, the other values stay the platform's")
    void agencyOverride() {
        UUID org = UUID.randomUUID();
        OrganizationDeliveryThresholds override = new OrganizationDeliveryThresholds(org,
                DeliveryThresholdValues.of(Map.of(DeliveryThreshold.GPS_SIGNAL_LOST_MINUTES, 25,
                        DeliveryThreshold.DELIVERY_LATE_FALLBACK_HOURS_EXPRESS, 6)),
                null, Instant.EPOCH, 0L);
        Alerts alerts = AdministrationDeliveryThresholdsAdapter.alerts(EffectiveDeliveryThresholds.of(
                PlatformDeliveryThresholds.defaults(), org, true, override));
        assertThat(alerts.gpsSignalLost()).isEqualTo(Duration.ofMinutes(25));
        assertThat(alerts.deliveryLateFallbackExpress()).isEqualTo(Duration.ofHours(6));
        assertThat(alerts.pickupLateGrace()).isEqualTo(Duration.ofMinutes(15));
        assertThat(alerts.deliveryLateFallbackSameDay()).isEqualTo(Duration.ofHours(8));
    }
}
