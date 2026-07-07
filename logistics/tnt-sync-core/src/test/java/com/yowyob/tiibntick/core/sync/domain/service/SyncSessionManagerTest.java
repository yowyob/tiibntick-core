package com.yowyob.tiibntick.core.sync.domain.service;

import com.yowyob.tiibntick.core.sync.application.port.out.ISyncSessionRepository;
import com.yowyob.tiibntick.core.sync.domain.exception.SyncSessionNotFoundException;
import com.yowyob.tiibntick.core.sync.domain.model.SyncSessionId;
import com.yowyob.tiibntick.core.sync.domain.model.SyncToken;
import com.yowyob.tiibntick.core.sync.domain.model.VectorClock;
import com.yowyob.tiibntick.core.sync.domain.model.enums.SyncSessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class SyncSessionManagerTest {

    @Mock
    private ISyncSessionRepository sessionRepository;

    private SyncSessionManager manager;

    @BeforeEach
    void setUp() {
        manager = new SyncSessionManager(sessionRepository);
        when(sessionRepository.save(any())).thenReturn(Mono.empty());
    }

    private SyncToken buildToken() {
        return SyncToken.next("user1", "tenant-A", "device1", LocalDateTime.now().minusHours(1));
    }

    @Test
    @DisplayName("open() creates a session in IN_PROGRESS status")
    void openCreatesSessionInProgress() {
        StepVerifier.create(manager.open("user1", "tenant-A", "device1", buildToken()))
                .assertNext(session -> {
                    assertThat(session.getId()).isNotNull();
                    assertThat(session.getUserId()).isEqualTo("user1");
                    assertThat(session.getTenantId()).isEqualTo("tenant-A");
                    assertThat(session.getStatus()).isEqualTo(SyncSessionStatus.IN_PROGRESS);
                    assertThat(session.isCompleted()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("complete() transitions session to COMPLETED or PARTIAL")
    void completeTransitionsStatus() {
        SyncToken resultToken = SyncToken.next("user1", "tenant-A", "device1", LocalDateTime.now());

        StepVerifier.create(
                manager.open("user1", "tenant-A", "device1", buildToken())
                        .flatMap(session -> manager.complete(session, resultToken))
        )
        .assertNext(session -> {
            assertThat(session.isCompleted()).isTrue();
            assertThat(session.getCompletedAt()).isNotNull();
            assertThat(session.getResultToken()).isNotNull();
        })
        .verifyComplete();
    }

    @Test
    @DisplayName("fail() transitions session to FAILED")
    void failTransitionsStatus() {
        StepVerifier.create(
                manager.open("user1", "tenant-A", "device1", buildToken())
                        .flatMap(session -> manager.fail(session, "Test failure"))
        )
        .assertNext(session -> {
            assertThat(session.getStatus()).isEqualTo(SyncSessionStatus.FAILED);
            assertThat(session.getCompletedAt()).isNotNull();
        })
        .verifyComplete();
    }

    @Test
    @DisplayName("findById() throws SyncSessionNotFoundException for unknown session")
    void findByIdThrowsForUnknown() {
        when(sessionRepository.findById(any())).thenReturn(Mono.empty());

        StepVerifier.create(manager.findById(SyncSessionId.of("ghost-session")))
                .expectError(SyncSessionNotFoundException.class)
                .verify();
    }

    @Nested
    @DisplayName("VectorClock tests")
    class VectorClockTests {

        @Test
        @DisplayName("increment() adds 1 to the specified node")
        void incrementAddsToNode() {
            VectorClock vc = VectorClock.empty().increment("device1").increment("device1");
            assertThat(vc.asMap()).containsEntry("device1", 2L);
        }

        @Test
        @DisplayName("merge() takes max values from both clocks")
        void mergeTakesMax() {
            VectorClock a = VectorClock.of(Map.of("node1", 3L, "node2", 1L));
            VectorClock b = VectorClock.of(Map.of("node1", 1L, "node2", 4L, "node3", 2L));
            VectorClock merged = a.merge(b);

            assertThat(merged.asMap()).containsEntry("node1", 3L);
            assertThat(merged.asMap()).containsEntry("node2", 4L);
            assertThat(merged.asMap()).containsEntry("node3", 2L);
        }

        @Test
        @DisplayName("compareWith() correctly identifies HAPPENS_BEFORE")
        void compareWithHappensBefore() {
            VectorClock a = VectorClock.of(Map.of("n1", 3L, "n2", 2L));
            VectorClock b = VectorClock.of(Map.of("n1", 1L, "n2", 2L));

            VectorClock.CausalRelation relation = a.compareWith(b);

            assertThat(relation).isEqualTo(VectorClock.CausalRelation.HAPPENS_BEFORE);
        }

        @Test
        @DisplayName("compareWith() correctly identifies CONCURRENT clocks")
        void compareWithConcurrent() {
            VectorClock a = VectorClock.of(Map.of("n1", 3L, "n2", 1L));
            VectorClock b = VectorClock.of(Map.of("n1", 1L, "n2", 3L));

            VectorClock.CausalRelation relation = a.compareWith(b);

            assertThat(relation).isEqualTo(VectorClock.CausalRelation.CONCURRENT);
        }

        @Test
        @DisplayName("SyncToken.isStale() returns true after the stale duration")
        void syncTokenIsStale() {
            SyncToken staleToken = new SyncToken("tok", "u1", "t1", "d1",
                    LocalDateTime.now().minusDays(9));
            assertThat(staleToken.isStale()).isTrue();
        }

        @Test
        @DisplayName("SyncToken.isInitial() returns true for epoch 1970 token")
        void syncTokenIsInitial() {
            SyncToken initial = SyncToken.initial("u1", "t1", "d1");
            assertThat(initial.isInitial()).isTrue();
        }
    }
}
