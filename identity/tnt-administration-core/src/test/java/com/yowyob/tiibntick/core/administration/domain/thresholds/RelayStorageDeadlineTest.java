package com.yowyob.tiibntick.core.administration.domain.thresholds;

import com.yowyob.tiibntick.core.administration.domain.model.thresholds.RelayStorageDeadline;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Relay storage deadline — QO-30: min(client request, relay duration)")
class RelayStorageDeadlineTest {

    private static final Instant DEPOSIT = Instant.parse("2026-11-26T10:00:00Z");

    @Test
    @DisplayName("a shorter client request shortens the storage")
    void shorterRequestApplies() {
        RelayStorageDeadline d = RelayStorageDeadline.compute(DEPOSIT, 7, 3, 1);
        assertThat(d.effectiveStorageDays()).isEqualTo(3);
        assertThat(d.clientRequestApplied()).isTrue();
        assertThat(d.expiresAt()).isEqualTo(DEPOSIT.plus(Duration.ofDays(3)));
        assertThat(d.reminderAt()).isEqualTo(DEPOSIT.plus(Duration.ofDays(2)));
    }

    @Test
    @DisplayName("a longer request never lengthens the storage for free")
    void longerRequestIgnored() {
        RelayStorageDeadline d = RelayStorageDeadline.compute(DEPOSIT, 7, 10, 1);
        assertThat(d.effectiveStorageDays()).isEqualTo(7);
        assertThat(d.clientRequestApplied()).isFalse();
        assertThat(d.expiresAt()).isEqualTo(DEPOSIT.plus(Duration.ofDays(7)));
        assertThat(RelayStorageDeadline.compute(DEPOSIT, 7, 7, 1).clientRequestApplied()).isFalse();
    }

    @Test
    @DisplayName("no request, zero or negative request: the relay duration applies")
    void missingRequest() {
        for (Integer request : new Integer[]{null, 0, -2}) {
            RelayStorageDeadline d = RelayStorageDeadline.compute(DEPOSIT, 7, request, 1);
            assertThat(d.effectiveStorageDays()).as("request %s", request).isEqualTo(7);
            assertThat(d.clientRequestApplied()).isFalse();
            assertThat(d.requestedStorageDays()).isEqualTo(request);
        }
    }

    @Test
    @DisplayName("reminder: J-n before the end; none when 0; due at deposit when the storage is that short")
    void reminder() {
        assertThat(RelayStorageDeadline.compute(DEPOSIT, 7, null, 0).reminderAt()).isNull();
        assertThat(RelayStorageDeadline.compute(DEPOSIT, 7, null, 2).reminderAt())
                .isEqualTo(DEPOSIT.plus(Duration.ofDays(5)));
        assertThat(RelayStorageDeadline.compute(DEPOSIT, 7, 1, 1).reminderAt()).isEqualTo(DEPOSIT);
    }

    @Test
    @DisplayName("refuses a storage under one day, a negative reminder and a missing deposit time")
    void invalidArguments() {
        assertThatThrownBy(() -> RelayStorageDeadline.compute(DEPOSIT, 0, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RelayStorageDeadline.compute(DEPOSIT, 7, null, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RelayStorageDeadline.compute(null, 7, null, 1))
                .isInstanceOf(NullPointerException.class);
    }
}
