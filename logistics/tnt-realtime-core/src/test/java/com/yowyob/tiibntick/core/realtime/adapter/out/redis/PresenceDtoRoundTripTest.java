package com.yowyob.tiibntick.core.realtime.adapter.out.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.realtime.domain.model.GeoCoordinates;
import com.yowyob.tiibntick.core.realtime.domain.model.PresenceRecord;
import com.yowyob.tiibntick.core.realtime.domain.model.enums.PresenceStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Verifies that PresenceDto serialisation/deserialisation preserves lastSeenAt and firstSeenAt.
 * These tests would fail if toRecord() resets timestamps to now() instead of reading stored values.
 */
class PresenceDtoRoundTripTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    // ── round-trip ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("round-trip preserves lastSeenAt and firstSeenAt — timestamps clearly in the past")
    void roundTrip_preservesLastSeenAtAndFirstSeenAt() throws Exception {
        // Timestamps well in the past — if toRecord() returned now(), assertions would fail.
        LocalDateTime firstSeen = LocalDateTime.now().minusHours(2);
        LocalDateTime lastSeen  = LocalDateTime.now().minusMinutes(30);

        PresenceRecord original = PresenceRecord.reconstitute(
                "user-round", "tenant-A", null,
                PresenceStatus.ONLINE_AVAILABLE,
                GeoCoordinates.of(3.880, 11.518),
                null,
                firstSeen, lastSeen);

        String json = objectMapper.writeValueAsString(
                RedisPresenceRepository.PresenceDto.from(original));

        PresenceRecord reconstituted = objectMapper
                .readValue(json, RedisPresenceRepository.PresenceDto.class)
                .toRecord();

        assertThat(reconstituted.getFirstSeenAt())
                .isCloseTo(firstSeen, within(1, ChronoUnit.SECONDS));
        assertThat(reconstituted.getLastSeenAt())
                .isCloseTo(lastSeen, within(1, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("round-trip preserves coordinates and status")
    void roundTrip_preservesCoordinatesAndStatus() throws Exception {
        LocalDateTime firstSeen = LocalDateTime.now().minusHours(1);
        LocalDateTime lastSeen  = LocalDateTime.now().minusMinutes(5);

        PresenceRecord original = PresenceRecord.reconstitute(
                "user-coords", "tenant-B", null,
                PresenceStatus.ONLINE_ON_MISSION,
                GeoCoordinates.of(4.061, 9.756),
                "MISSION-99",
                firstSeen, lastSeen);

        String json = objectMapper.writeValueAsString(
                RedisPresenceRepository.PresenceDto.from(original));

        PresenceRecord reconstituted = objectMapper
                .readValue(json, RedisPresenceRepository.PresenceDto.class)
                .toRecord();

        assertThat(reconstituted.getStatus()).isEqualTo(PresenceStatus.ONLINE_ON_MISSION);
        assertThat(reconstituted.getCurrentCoordinates()).isNotNull();
        assertThat(reconstituted.getCurrentCoordinates().latitude()).isEqualTo(4.061);
        assertThat(reconstituted.getActiveMissionId()).isEqualTo("MISSION-99");
    }

    // ── isStale after round-trip ──────────────────────────────────────────────

    @Test
    @DisplayName("isStale() returns true after round-trip when lastSeenAt is in the past beyond staleDuration")
    void roundTrip_isStaleReturnsTrueWhenLastSeenAtIsPast() throws Exception {
        LocalDateTime firstSeen = LocalDateTime.now().minusMinutes(15);
        // lastSeenAt is 10 min ago — stale threshold is 5 min, so record is stale
        LocalDateTime lastSeen  = LocalDateTime.now().minusMinutes(10);

        PresenceRecord original = PresenceRecord.reconstitute(
                "user-stale", "tenant-C", null,
                PresenceStatus.ONLINE_AVAILABLE, null, null,
                firstSeen, lastSeen);

        String json = objectMapper.writeValueAsString(
                RedisPresenceRepository.PresenceDto.from(original));

        PresenceRecord reconstituted = objectMapper
                .readValue(json, RedisPresenceRepository.PresenceDto.class)
                .toRecord();

        assertThat(reconstituted.isStale(Duration.ofMinutes(5))).isTrue();
    }

    @Test
    @DisplayName("isStale() returns false after round-trip when lastSeenAt is recent")
    void roundTrip_isStaleReturnsFalseWhenLastSeenAtIsRecent() throws Exception {
        LocalDateTime firstSeen = LocalDateTime.now().minusSeconds(30);
        LocalDateTime lastSeen  = LocalDateTime.now().minusSeconds(10);

        PresenceRecord original = PresenceRecord.reconstitute(
                "user-fresh", "tenant-D", null,
                PresenceStatus.ONLINE_AVAILABLE, null, null,
                firstSeen, lastSeen);

        String json = objectMapper.writeValueAsString(
                RedisPresenceRepository.PresenceDto.from(original));

        PresenceRecord reconstituted = objectMapper
                .readValue(json, RedisPresenceRepository.PresenceDto.class)
                .toRecord();

        assertThat(reconstituted.isStale(Duration.ofMinutes(5))).isFalse();
    }
}
