package com.yowyob.tiibntick.core.trust.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.TntCurrentUserArgumentResolver;
import com.yowyob.tiibntick.core.auth.application.port.in.ResolveCurrentUserUseCase;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.CustodyTransferRecord;
import com.yowyob.tiibntick.core.trust.domain.model.enums.CustodyTransferType;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.DeliveryProofRecord;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.DIDDocument;
import com.yowyob.tiibntick.core.trust.application.port.in.*;
import com.yowyob.tiibntick.core.trust.application.service.LogisticProofResolverService;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link TrustApiController} using {@link WebTestClient}.
 * Mocks all use case dependencies — no Spring context loaded (fast).
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TrustApiController — REST API Tests")
class TrustApiControllerTest {

    @Mock private RecordDeliveryProofUseCase recordDeliveryProof;
    @Mock private RecordCustodyTransferUseCase recordCustodyTransfer;
    @Mock private IssueDIDUseCase issueDID;
    @Mock private RecordPolVerificationUseCase recordPolVerification;
    @Mock private GetDeliveryAuditTrailUseCase getDeliveryAuditTrail;
    @Mock private GetActorDIDUseCase getActorDID;
    @Mock private GetCustodyChainUseCase getCustodyChainUseCase;
    @Mock private GetGeofenceCrossingsUseCase getGeofenceCrossingsUseCase;
    @Mock private GetDaoRulesUseCase getDaoRulesUseCase;
    @Mock private GetPolVerificationsUseCase getPolVerificationsUseCase;
    @Mock private LogisticProofResolverService proofResolver;

    private static final UUID TENANT_ID = UUID.randomUUID();

    private static final TntUserIdentity STUB_USER = new TntUserIdentity(
            UUID.randomUUID(), TENANT_ID, UUID.randomUUID(),
            UUID.randomUUID(), UUID.randomUUID(), Set.of("trust:anchor", "trust:read"), false);

    private static final ResolveCurrentUserUseCase STUB_RESOLVE_CURRENT_USER = new ResolveCurrentUserUseCase() {
        @Override
        public Mono<TntSecurityContext> resolveCurrentContext() {
            return Mono.empty();
        }

        @Override
        public Mono<TntUserIdentity> resolveCurrentIdentity() {
            return Mono.just(STUB_USER);
        }

        @Override
        public Mono<TntSecurityContext> resolveCurrentContextOrAnonymous() {
            return Mono.empty();
        }
    };

    private WebTestClient webTestClient() {
        return WebTestClient.bindToController(new TrustApiController(
                recordDeliveryProof, recordCustodyTransfer, issueDID,
                recordPolVerification, getDeliveryAuditTrail, getActorDID,
                getCustodyChainUseCase, getGeofenceCrossingsUseCase, getDaoRulesUseCase,
                getPolVerificationsUseCase, proofResolver))
                .argumentResolvers(configurer -> configurer.addCustomResolver(
                        new TntCurrentUserArgumentResolver(STUB_RESOLVE_CURRENT_USER)))
                .build();
    }

    // ── GET /tnt/trust/delivery/{missionId}/trail ──────────────────────────────

    @Nested
    @DisplayName("GET /tnt/trust/delivery/{missionId}/trail")
    class GetDeliveryTrailTest {

        @Test
        @DisplayName("should return 200 with audit trail events")
        void shouldReturn200WithTrail() {
            final DeliveryProofRecord proof = buildProof("proof-001");

            when(getDeliveryAuditTrail.getByMissionId("mission-001", TENANT_ID.toString()))
                    .thenReturn(Flux.just(proof));

            webTestClient().get()
                    .uri("/tnt/trust/delivery/mission-001/trail")
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().contentType(MediaType.APPLICATION_JSON)
                    .expectBody()
                    .jsonPath("$[0].eventType").isEqualTo("DELIVERY_PROOF_RECORDED")
                    .jsonPath("$[0].entityId").isEqualTo("proof-001");
        }

        @Test
        @DisplayName("should return empty array when no proofs")
        void shouldReturnEmptyArray() {
            when(getDeliveryAuditTrail.getByMissionId("mission-999", TENANT_ID.toString()))
                    .thenReturn(Flux.empty());

            webTestClient().get()
                    .uri("/tnt/trust/delivery/mission-999/trail")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody().json("[]");
        }
    }

    // ── GET /tnt/trust/verify ─────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /tnt/trust/verify")
    class VerifyProofTest {

        @Test
        @DisplayName("should return valid=true for confirmed proof")
        void shouldReturnValidTrue() {
            when(proofResolver.verifyProofOnChain("b".repeat(64), "a".repeat(64)))
                    .thenReturn(Mono.just(true));

            webTestClient().get()
                    .uri("/tnt/trust/verify?txHash=" + "b".repeat(64) + "&expectedHash=" + "a".repeat(64))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.valid").isEqualTo(true);
        }

        @Test
        @DisplayName("should return valid=false for unconfirmed proof")
        void shouldReturnValidFalse() {
            when(proofResolver.verifyProofOnChain("unknown", "hash"))
                    .thenReturn(Mono.just(false));

            webTestClient().get()
                    .uri("/tnt/trust/verify?txHash=unknown&expectedHash=hash")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.valid").isEqualTo(false);
        }
    }

    // ── GET /tnt/trust/actors/{actorId}/did ──────────────────────────────────

    @Nested
    @DisplayName("GET /tnt/trust/actors/{actorId}/did")
    class GetActorDIDTest {

        @Test
        @DisplayName("should return DID document for known actor")
        void shouldReturnDIDDocument() {
            final DIDDocument doc = DIDDocument.issue(
                    "actor-001", "tenant-001",
                    "-----BEGIN CERTIFICATE-----\nMock\n-----END CERTIFICATE-----", null);
            doc.confirmOnChain("b".repeat(64));

            when(getActorDID.getByActorId("actor-001", TENANT_ID.toString()))
                    .thenReturn(Mono.just(doc));

            webTestClient().get()
                    .uri("/tnt/trust/actors/actor-001/did")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.did").isEqualTo("did:tiibntick:tenant-001:actor-001")
                    .jsonPath("$.verifiable").isEqualTo(true);
        }
    }

    // ── POST /tnt/trust/delivery/proof ────────────────────────────────────────

    @Nested
    @DisplayName("POST /tnt/trust/delivery/proof")
    class PostDeliveryProofTest {

        @Test
        @DisplayName("should return 202 ACCEPTED with correlationId")
        void shouldReturn202WithCorrelationId() {
            when(recordDeliveryProof.record(any()))
                    .thenReturn(Mono.just("corr-001"));

            final String body = """
                    {
                      "proofId": "proof-001",
                      "missionId": "mission-001",
                      "packageId": "package-001",
                      "actorId": "actor-001",
                      "tenantId": "tenant-001",
                      "photoHash": "%s",
                      "gpsLat": 3.848,
                      "gpsLng": 11.502
                    }
                    """.formatted("a".repeat(64));

            webTestClient().post()
                    .uri("/tnt/trust/delivery/proof")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .exchange()
                    .expectStatus().isAccepted()
                    .expectBody()
                    .jsonPath("$.correlationId").isEqualTo("corr-001");
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private DeliveryProofRecord buildProof(final String proofId) {
        return new DeliveryProofRecord(proofId, "mission-001", "package-001",
                "actor-001", "tenant-001", "a".repeat(64), null,
                3.848, 11.502, LocalDateTime.now());
    }
}
