package com.yowyob.tiibntick.common.domain.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link TntDomainEventMetadata}.
 *
 * Author: MANFOUO Braun
 */
class TntDomainEventMetadataTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID AGGREGATE_ID = UUID.randomUUID();

    @Test
    @DisplayName("of creates metadata with generated eventId and current timestamp")
    void of_createsMetadataWithDefaults() {
        Instant before = Instant.now();
        TntDomainEventMetadata meta = TntDomainEventMetadata.of(TENANT_ID, AGGREGATE_ID, "Mission");
        Instant after = Instant.now();

        assertThat(meta.eventId()).isNotNull();
        assertThat(meta.tenantId()).isEqualTo(TENANT_ID);
        assertThat(meta.aggregateId()).isEqualTo(AGGREGATE_ID);
        assertThat(meta.aggregateType()).isEqualTo("Mission");
        assertThat(meta.occurredAt()).isBetween(before, after);
        assertThat(meta.correlationId()).isNull();
        assertThat(meta.sequenceNumber()).isEqualTo(0L);
    }

    @Test
    @DisplayName("withCorrelationId returns new metadata with correlation ID set")
    void withCorrelationId_returnsNewMetadata() {
        TntDomainEventMetadata original = TntDomainEventMetadata.of(TENANT_ID, AGGREGATE_ID, "Mission");
        String correlationId = UUID.randomUUID().toString();

        TntDomainEventMetadata enriched = original.withCorrelationId(correlationId);

        assertThat(enriched.correlationId()).isEqualTo(correlationId);
        assertThat(enriched.eventId()).isEqualTo(original.eventId()); // unchanged
        assertThat(original.correlationId()).isNull();                 // original unchanged
    }

    @Test
    @DisplayName("withSequenceNumber returns new metadata with sequence set")
    void withSequenceNumber_returnsNewMetadata() {
        TntDomainEventMetadata original = TntDomainEventMetadata.of(TENANT_ID, AGGREGATE_ID, "Package");
        TntDomainEventMetadata versioned = original.withSequenceNumber(42L);

        assertThat(versioned.sequenceNumber()).isEqualTo(42L);
        assertThat(original.sequenceNumber()).isEqualTo(0L); // original unchanged
    }

    @Test
    @DisplayName("refreshed returns new metadata with fresh eventId and timestamp")
    void refreshed_returnsFreshMetadata() throws InterruptedException {
        TntDomainEventMetadata original = TntDomainEventMetadata.of(TENANT_ID, AGGREGATE_ID, "Mission");
        Thread.sleep(2); // ensure timestamp difference
        TntDomainEventMetadata refreshed = original.refreshed();

        assertThat(refreshed.eventId()).isNotEqualTo(original.eventId());
        assertThat(refreshed.occurredAt()).isAfterOrEqualTo(original.occurredAt());
        assertThat(refreshed.tenantId()).isEqualTo(original.tenantId()); // preserved
    }

    @Test
    @DisplayName("compact constructor rejects null required fields")
    void constructor_rejectsNullFields() {
        assertThatThrownBy(() -> new TntDomainEventMetadata(
            null, TENANT_ID, AGGREGATE_ID, "Mission", Instant.now(), null, 0L))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("eventId");

        assertThatThrownBy(() -> new TntDomainEventMetadata(
            UUID.randomUUID(), TENANT_ID, AGGREGATE_ID, "", Instant.now(), null, 0L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("aggregateType");

        assertThatThrownBy(() -> new TntDomainEventMetadata(
            UUID.randomUUID(), TENANT_ID, AGGREGATE_ID, "Mission", Instant.now(), null, -1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("sequenceNumber");
    }

    @Test
    @DisplayName("two metadata with same values are equal (record equality)")
    void recordEquality() {
        Instant now = Instant.now();
        UUID eventId = UUID.randomUUID();
        TntDomainEventMetadata m1 = new TntDomainEventMetadata(
            eventId, TENANT_ID, AGGREGATE_ID, "Mission", now, "corr-123", 1L);
        TntDomainEventMetadata m2 = new TntDomainEventMetadata(
            eventId, TENANT_ID, AGGREGATE_ID, "Mission", now, "corr-123", 1L);

        assertThat(m1).isEqualTo(m2);
        assertThat(m1.hashCode()).isEqualTo(m2.hashCode());
    }
}
