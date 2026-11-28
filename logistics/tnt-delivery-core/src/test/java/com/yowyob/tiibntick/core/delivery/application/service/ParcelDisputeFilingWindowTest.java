package com.yowyob.tiibntick.core.delivery.application.service;

import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Lot L4: a closed parcel accepts a dispute until statusChangedAt + the tenant's filing window. */
class ParcelDisputeFilingWindowTest {

    @Test
    @DisplayName("inside the window yes, after it no")
    void window() {
        Instant now = Instant.parse("2026-11-26T12:00:00Z");
        Parcel parcel = mock(Parcel.class);
        when(parcel.getStatusChangedAt()).thenReturn(now.minus(Duration.ofDays(6)));
        assertThat(ParcelDisputeService.withinWindow(parcel, Duration.ofDays(7), now)).isTrue();
        when(parcel.getStatusChangedAt()).thenReturn(now.minus(Duration.ofDays(8)));
        assertThat(ParcelDisputeService.withinWindow(parcel, Duration.ofDays(7), now)).isFalse();
        assertThat(ParcelDisputeService.withinWindow(parcel, Duration.ofDays(30), now)).isTrue();
    }
}
