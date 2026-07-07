package com.yowyob.tiibntick.core.sync.domain.service;

import com.yowyob.tiibntick.core.sync.domain.model.ConflictRecord;
import com.yowyob.tiibntick.core.sync.domain.model.EntityVersionRecord;
import com.yowyob.tiibntick.core.sync.domain.model.OfflineOpId;
import com.yowyob.tiibntick.core.sync.domain.model.OfflineOperation;
import com.yowyob.tiibntick.core.sync.domain.model.enums.ConflictResolution;
import com.yowyob.tiibntick.core.sync.domain.model.enums.DeltaOperation;
import com.yowyob.tiibntick.core.sync.domain.model.enums.OfflineOpType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ConflictResolverServiceTest {

    private ConflictResolverService resolver;

    private static final LocalDateTime BASE = LocalDateTime.of(2025, 1, 1, 12, 0, 0);

    @BeforeEach
    void setUp() {
        resolver = new ConflictResolverService(ConflictResolverService.Strategy.LWW);
    }

    private OfflineOperation buildOp(LocalDateTime localTs, String aggregateId) {
        return new OfflineOperation(
                OfflineOpId.generate(), "user1", "tenant-A", "device1",
                OfflineOpType.MISSION_STATUS_UPDATE,
                "MISSION", aggregateId, "{\"status\":\"PICKED_UP\"}",
                localTs, 1L
        );
    }

    private EntityVersionRecord buildServerVersion(LocalDateTime serverTs, String aggregateId) {
        return new EntityVersionRecord(
                "tenant-A", "MISSION", aggregateId,
                1000L, DeltaOperation.STATUS_CHANGED,
                "{\"status\":\"CREATED\"}",
                serverTs, "system"
        );
    }

    @Test
    @DisplayName("LWW: client wins when client timestamp is strictly newer")
    void lwwClientWinsWhenClientNewer() {
        OfflineOperation op = buildOp(BASE.plusSeconds(10), "M-001");
        EntityVersionRecord server = buildServerVersion(BASE, "M-001");

        ConflictRecord result = resolver.resolve(op, server);

        assertThat(result.resolution()).isEqualTo(ConflictResolution.CLIENT_WINS);
        assertThat(result.resolvedValue()).contains("PICKED_UP");
    }

    @Test
    @DisplayName("LWW: server wins when server timestamp is newer")
    void lwwServerWinsWhenServerNewer() {
        OfflineOperation op = buildOp(BASE, "M-001");
        EntityVersionRecord server = buildServerVersion(BASE.plusMinutes(5), "M-001");

        ConflictRecord result = resolver.resolve(op, server);

        assertThat(result.resolution()).isEqualTo(ConflictResolution.SERVER_WINS);
        assertThat(result.resolvedValue()).contains("CREATED");
    }

    @Test
    @DisplayName("LWW: server wins when timestamps are equal (conservative)")
    void lwwServerWinsOnTie() {
        OfflineOperation op = buildOp(BASE, "M-001");
        EntityVersionRecord server = buildServerVersion(BASE, "M-001");

        ConflictRecord result = resolver.resolve(op, server);

        assertThat(result.resolution()).isEqualTo(ConflictResolution.SERVER_WINS);
    }

    @Test
    @DisplayName("SERVER_ALWAYS_WINS strategy ignores timestamps")
    void serverAlwaysWinsStrategy() {
        resolver = new ConflictResolverService(ConflictResolverService.Strategy.SERVER_ALWAYS_WINS);

        OfflineOperation op = buildOp(BASE.plusDays(1), "M-002");
        EntityVersionRecord server = buildServerVersion(BASE, "M-002");

        ConflictRecord result = resolver.resolve(op, server);

        assertThat(result.resolution()).isEqualTo(ConflictResolution.SERVER_WINS);
    }

    @Test
    @DisplayName("CLIENT_ALWAYS_WINS strategy ignores timestamps")
    void clientAlwaysWinsStrategy() {
        resolver = new ConflictResolverService(ConflictResolverService.Strategy.CLIENT_ALWAYS_WINS);

        OfflineOperation op = buildOp(BASE, "M-003");
        EntityVersionRecord server = buildServerVersion(BASE.plusDays(1), "M-003");

        ConflictRecord result = resolver.resolve(op, server);

        assertThat(result.resolution()).isEqualTo(ConflictResolution.CLIENT_WINS);
    }

    @Test
    @DisplayName("Vector clock: client dominates when client clock advances server")
    void vectorClockClientDominates() {
        resolver = new ConflictResolverService(ConflictResolverService.Strategy.VECTOR_CLOCK);

        String clientPayload = "{\"__vc\":{\"device1\":3,\"server\":2},\"status\":\"IN_TRANSIT\"}";
        String serverPayload = "{\"__vc\":{\"device1\":2,\"server\":2},\"status\":\"PICKED_UP\"}";

        OfflineOperation op = new OfflineOperation(
                OfflineOpId.generate(), "user1", "tenant-A", "device1",
                OfflineOpType.MISSION_STATUS_UPDATE, "MISSION", "M-004",
                clientPayload, BASE.plusSeconds(5), 1L);

        EntityVersionRecord server = new EntityVersionRecord(
                "tenant-A", "MISSION", "M-004", 1000L,
                DeltaOperation.STATUS_CHANGED, serverPayload, BASE, "system");

        ConflictRecord result = resolver.resolve(op, server, ConflictResolverService.Strategy.VECTOR_CLOCK);

        assertThat(result.resolution()).isEqualTo(ConflictResolution.CLIENT_WINS);
    }

    @Test
    @DisplayName("Vector clock: falls back to LWW for concurrent modifications")
    void vectorClockConcurrentFallsBackToLww() {
        resolver = new ConflictResolverService(ConflictResolverService.Strategy.VECTOR_CLOCK);

        // Concurrent: both advanced their own clocks
        String clientPayload = "{\"__vc\":{\"device1\":3,\"server\":1},\"status\":\"FAILED\"}";
        String serverPayload = "{\"__vc\":{\"device1\":1,\"server\":3},\"status\":\"PICKED_UP\"}";

        OfflineOperation op = new OfflineOperation(
                OfflineOpId.generate(), "user1", "tenant-A", "device1",
                OfflineOpType.MISSION_STATUS_UPDATE, "MISSION", "M-005",
                clientPayload, BASE.plusHours(1), 1L);

        EntityVersionRecord server = new EntityVersionRecord(
                "tenant-A", "MISSION", "M-005", 1000L,
                DeltaOperation.STATUS_CHANGED, serverPayload, BASE, "system");

        ConflictRecord result = resolver.resolve(op, server, ConflictResolverService.Strategy.VECTOR_CLOCK);

        // Concurrent: falls back to LWW → client is newer
        assertThat(result.resolution()).isEqualTo(ConflictResolution.CLIENT_WINS);
    }

    @Test
    @DisplayName("ConflictRecord.isServerWins() and isClientWins() are mutually exclusive")
    void conflictRecordHelpers() {
        OfflineOperation op = buildOp(BASE.plusHours(1), "M-010");
        EntityVersionRecord server = buildServerVersion(BASE, "M-010");

        ConflictRecord result = resolver.resolve(op, server);

        if (result.isClientWins()) {
            assertThat(result.isServerWins()).isFalse();
        } else {
            assertThat(result.isClientWins()).isFalse();
        }
    }
}
