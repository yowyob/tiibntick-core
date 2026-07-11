package com.yowyob.tiibntick.core.trust.adapter.out.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.ActorBadge;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.CustodyTransferRecord;
import com.yowyob.tiibntick.core.trust.domain.model.enums.CustodyTransferType;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.DIDDocument;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.DeliveryProofRecord;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for persistence adapters using Mockito.
 * Integration tests with Testcontainers are configured separately
 * in the {@code integration-test} Maven profile.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("tnt-trust — Persistence Adapter Tests")
class PersistenceAdapterTest {

    // ── DIDRepositoryAdapter ───────────────────────────────────────────────────

    @Nested
    @DisplayName("DIDRepositoryAdapter")
    class DIDRepositoryAdapterTest {

        @Mock private DIDDocumentR2dbcRepository r2dbcRepo;

        @Test
        @DisplayName("save() should map domain to entity and back")
        void shouldSaveAndMap() {
            final DIDDocument doc = DIDDocument.issue(
                    "actor-001", "tenant-001",
                    "-----BEGIN CERTIFICATE-----\nMock\n-----END CERTIFICATE-----", null);

            final DIDDocumentEntity entity = DIDDocumentEntity.fromDomain(doc);
            when(r2dbcRepo.save(any())).thenReturn(Mono.just(entity));

            final DIDRepositoryAdapter adapter = new DIDRepositoryAdapter(r2dbcRepo);

            StepVerifier.create(adapter.save(doc))
                    .assertNext(saved -> {
                        assertThat(saved.getDid()).isEqualTo(doc.getDid());
                        assertThat(saved.getActorId()).isEqualTo("actor-001");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("findByActorId() should return empty when no active DID")
        void shouldReturnEmptyWhenNoActiveDID() {
            when(r2dbcRepo.findActiveByActorId("actor-999", "tenant-001"))
                    .thenReturn(Mono.empty());

            final DIDRepositoryAdapter adapter = new DIDRepositoryAdapter(r2dbcRepo);

            StepVerifier.create(adapter.findByActorId("actor-999", "tenant-001"))
                    .verifyComplete();
        }
    }

    // ── ActorBadgeRepositoryAdapter ────────────────────────────────────────────

    @Nested
    @DisplayName("ActorBadgeRepositoryAdapter")
    class ActorBadgeRepositoryAdapterTest {

        @Mock private ActorBadgeR2dbcRepository r2dbcRepo;

        @Test
        @DisplayName("save() should persist badge and return domain object")
        void shouldSaveBadge() {
            final ActorBadge badge = ActorBadge.award(
                    "actor-001", "tenant-001", "100_DELIVERIES", 100);
            final ActorBadgeEntity entity = ActorBadgeEntity.fromDomain(badge);
            when(r2dbcRepo.save(any())).thenReturn(Mono.just(entity));

            final ActorBadgeRepositoryAdapter adapter = new ActorBadgeRepositoryAdapter(r2dbcRepo);

            StepVerifier.create(adapter.save(badge))
                    .assertNext(saved -> {
                        assertThat(saved.getBadgeType()).isEqualTo("100_DELIVERIES");
                        assertThat(saved.getPoints()).isEqualTo(100);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("existsByActorAndType() should return false when badge not found")
        void shouldReturnFalseWhenBadgeNotFound() {
            when(r2dbcRepo.existsActiveByActorAndType("actor-001", "TOP_RATED", "tenant-001"))
                    .thenReturn(Mono.just(false));

            final ActorBadgeRepositoryAdapter adapter = new ActorBadgeRepositoryAdapter(r2dbcRepo);

            StepVerifier.create(adapter.existsByActorAndType("actor-001", "TOP_RATED", "tenant-001"))
                    .expectNext(false)
                    .verifyComplete();
        }
    }

    // ── DeliveryProofCacheRepositoryAdapter ────────────────────────────────────

    @Nested
    @DisplayName("DeliveryProofCacheRepositoryAdapter")
    class DeliveryProofCacheRepositoryAdapterTest {

        @Mock private DeliveryProofR2dbcRepository r2dbcRepo;

        @Test
        @DisplayName("findByMissionId() should return proofs in order")
        void shouldFindByMissionId() {
            final DeliveryProofRecord proof = buildProof("proof-001");
            final DeliveryProofEntity entity = DeliveryProofEntity.fromDomain(proof);

            when(r2dbcRepo.findByMissionId("mission-001", "tenant-001"))
                    .thenReturn(Flux.just(entity));

            final DeliveryProofCacheRepositoryAdapter adapter =
                    new DeliveryProofCacheRepositoryAdapter(r2dbcRepo);

            StepVerifier.create(adapter.findByMissionId("mission-001", "tenant-001"))
                    .assertNext(p -> assertThat(p.getMissionId()).isEqualTo("mission-001"))
                    .verifyComplete();
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private DeliveryProofRecord buildProof(final String proofId) {
        return new DeliveryProofRecord(proofId, "mission-001", "package-001",
                "actor-001", "tenant-001", "a".repeat(64), null,
                3.848, 11.502, LocalDateTime.now());
    }
}
