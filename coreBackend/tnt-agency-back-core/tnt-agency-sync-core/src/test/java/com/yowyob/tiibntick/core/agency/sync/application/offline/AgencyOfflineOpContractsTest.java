package com.yowyob.tiibntick.core.agency.sync.application.offline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.exception.TntValidationException;
import com.yowyob.tiibntick.core.sync.domain.model.enums.OfflineOpType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgencyOfflineOpContractsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("requireExactType accepts exact enum names only")
    void exactTypeNames() {
        assertThat(AgencyOfflineOpContracts.requireExactType("MISSION_STATUS_UPDATE"))
                .isEqualTo(OfflineOpType.MISSION_STATUS_UPDATE);
        assertThatThrownBy(() -> AgencyOfflineOpContracts.requireExactType("mission_status_update"))
                .isInstanceOf(TntValidationException.class);
        assertThat(AgencyOfflineOpContracts.requireExactType("GPS_UPDATE"))
                .isEqualTo(OfflineOpType.GPS_UPDATE);
    }

    @Test
    @DisplayName("pickup payload requires delivererId UUID")
    void pickupPayload() {
        UUID delivererId = UUID.randomUUID();
        var parsed = AgencyOfflineOpContracts.parseAndValidatePayload(
                OfflineOpType.MISSION_STATUS_UPDATE,
                "{\"delivererId\":\"" + delivererId + "\"}",
                objectMapper);
        assertThat(parsed.delivererId()).isEqualTo(delivererId);

        assertThatThrownBy(() -> AgencyOfflineOpContracts.parseAndValidatePayload(
                OfflineOpType.MISSION_STATUS_UPDATE, "{}", objectMapper))
                .isInstanceOf(TntValidationException.class);
    }

    @Test
    @DisplayName("anomaly payload requires anomalyType and fatal boolean")
    void anomalyPayload() {
        UUID delivererId = UUID.randomUUID();
        var parsed = AgencyOfflineOpContracts.parseAndValidatePayload(
                OfflineOpType.ANOMALY_REPORT,
                "{\"delivererId\":\"" + delivererId + "\",\"anomalyType\":\"DAMAGED\",\"description\":\"x\",\"fatal\":true}",
                objectMapper);
        assertThat(parsed.anomalyType()).isEqualTo("DAMAGED");
        assertThat(parsed.fatal()).isTrue();

        assertThatThrownBy(() -> AgencyOfflineOpContracts.parseAndValidatePayload(
                OfflineOpType.ANOMALY_REPORT,
                "{\"delivererId\":\"" + delivererId + "\",\"anomalyType\":\"DAMAGED\"}",
                objectMapper))
                .isInstanceOf(TntValidationException.class)
                .hasMessageContaining("fatal");
    }

    @Test
    @DisplayName("hub deposit requires hubId")
    void hubDepositPayload() {
        UUID delivererId = UUID.randomUUID();
        UUID hubId = UUID.randomUUID();
        var parsed = AgencyOfflineOpContracts.parseAndValidatePayload(
                OfflineOpType.HUB_DEPOSIT,
                "{\"delivererId\":\"" + delivererId + "\",\"hubId\":\"" + hubId + "\",\"trackingCode\":\"T-1\"}",
                objectMapper);
        assertThat(parsed.hubId()).isEqualTo(hubId);
        assertThat(parsed.proofOrTracking()).isEqualTo("T-1");
    }

    @Test
    @DisplayName("GPS payload requires coordinates and timestamp")
    void gpsPayload() {
        UUID delivererId = UUID.randomUUID();
        var parsed = AgencyOfflineOpContracts.parseAndValidateGpsPayload(
                "{\"delivererId\":\"" + delivererId + "\",\"latitude\":3.8,\"longitude\":11.5,"
                        + "\"accuracyMeters\":12,\"speedKmh\":30,\"bearing\":90,"
                        + "\"timestamp\":\"2026-07-17T10:00:00Z\"}",
                objectMapper);
        assertThat(parsed.delivererId()).isEqualTo(delivererId);
        assertThat(parsed.latitude()).isEqualTo(3.8);
        assertThat(parsed.longitude()).isEqualTo(11.5);

        assertThatThrownBy(() -> AgencyOfflineOpContracts.parseAndValidateGpsPayload(
                "{\"delivererId\":\"" + delivererId + "\",\"latitude\":3.8,\"longitude\":11.5}",
                objectMapper))
                .isInstanceOf(TntValidationException.class)
                .hasMessageContaining("timestamp");
    }
}
