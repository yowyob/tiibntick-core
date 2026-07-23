package com.yowyob.tiibntick.core.trust.application.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import com.yowyob.tiibntick.core.trust.application.port.out.ActorBadgeRepository;
import com.yowyob.tiibntick.core.trust.application.port.out.BillingPolicyRecordRepository;
import com.yowyob.tiibntick.core.trust.application.port.out.DaoRuleRepository;
import com.yowyob.tiibntick.core.trust.application.port.out.GeofenceCrossingRepository;
import com.yowyob.tiibntick.core.trust.application.port.out.PolVerificationRepository;
import com.yowyob.tiibntick.core.trust.application.port.out.TrustEventPublisherPort;
import com.yowyob.tiibntick.core.trust.application.port.out.TrustProofQueryPort;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.ActorBadge;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.BillingPolicyRecord;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.DaoRuleRecord;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.GeofenceCrossingRecord;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.PolVerificationRecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@code BillingPolicyChainService}, {@code MissionChainService},
 * {@code PolChainService}, {@code BadgeChainService}, {@code GeofenceChainService},
 * and {@code DaoRuleChainService}.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("tnt-trust — Chain Services Tests")
class ChainServicesTest {

    @Mock private TrustEventPublisherPort publisherPort;
    @Mock private TrustProofQueryPort trustProofQueryPort;
    @Mock private ActorBadgeRepository actorBadgeRepository;
    @Mock private BillingPolicyRecordRepository billingPolicyRecordRepository;
    @Mock private GeofenceCrossingRepository geofenceCrossingRepository;
    @Mock private DaoRuleRepository daoRuleRepository;
    @Mock private PolVerificationRepository polVerificationRepository;

    private LogisticEventPublisherService publisherService;
    private BillingPolicyChainService billingPolicyChainService;
    private MissionChainService missionChainService;
    private PolChainService polChainService;
    private BadgeChainService badgeChainService;
    private DisputeEvidenceChainService disputeEvidenceChainService;
    private GeofenceChainService geofenceChainService;
    private DaoRuleChainService daoRuleChainService;

    @BeforeEach
    void setUp() {
        publisherService = new LogisticEventPublisherService(
                publisherPort, new SimpleMeterRegistry());
        billingPolicyChainService = new BillingPolicyChainService(
                publisherService, billingPolicyRecordRepository, trustProofQueryPort, new SimpleMeterRegistry());
        missionChainService = new MissionChainService(
                publisherService, trustProofQueryPort, new SimpleMeterRegistry());
        polChainService = new PolChainService(
                publisherService, polVerificationRepository, new SimpleMeterRegistry());
        badgeChainService = new BadgeChainService(
                publisherService, actorBadgeRepository, new SimpleMeterRegistry());
        disputeEvidenceChainService = new DisputeEvidenceChainService(publisherService, new SimpleMeterRegistry());
        geofenceChainService = new GeofenceChainService(
                publisherService, geofenceCrossingRepository, new SimpleMeterRegistry());
        daoRuleChainService = new DaoRuleChainService(
                publisherService, daoRuleRepository, new SimpleMeterRegistry());
    }

    // ── BillingPolicyChainService ──────────────────────────────────────────────

    @Nested
    @DisplayName("BillingPolicyChainService")
    class BillingPolicyChainServiceTest {

        @Test
        @DisplayName("record() should publish BILLING_POLICY_ACTIVATED event and return correlationId")
        void shouldPublishBillingPolicyEvent() {
            when(billingPolicyRecordRepository.save(any(BillingPolicyRecord.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
            when(publisherPort.publish(any())).thenReturn(Mono.empty());

            StepVerifier.create(billingPolicyChainService.record(
                            "agency-001", "policy-001", "tenant-001",
                            "{\"basePrice\":\"1000 XAF\"}"))
                    .assertNext(correlationId -> assertThat(correlationId).isNotBlank())
                    .verifyComplete();

            verify(publisherPort, times(1)).publish(any());
        }

        @Test
        @DisplayName("isRecordedOnChain() should return true when tx hash found")
        void shouldReturnTrueWhenOnChain() {
            when(trustProofQueryPort.findTxHashByEntityId("policy-001", "BILLING_POLICY", "tenant-001"))
                    .thenReturn(Mono.just("b".repeat(64)));

            StepVerifier.create(billingPolicyChainService.isRecordedOnChain("policy-001", "tenant-001"))
                    .expectNext(true)
                    .verifyComplete();
        }

        @Test
        @DisplayName("isRecordedOnChain() should return false when not found")
        void shouldReturnFalseWhenNotOnChain() {
            when(trustProofQueryPort.findTxHashByEntityId("policy-001", "BILLING_POLICY", "tenant-001"))
                    .thenReturn(Mono.empty());

            StepVerifier.create(billingPolicyChainService.isRecordedOnChain("policy-001", "tenant-001"))
                    .expectNext(false)
                    .verifyComplete();
        }
    }

    // ── MissionChainService ────────────────────────────────────────────────────

    @Nested
    @DisplayName("MissionChainService")
    class MissionChainServiceTest {

        @Test
        @DisplayName("recordCreated() should publish MISSION_CREATED_ON_CHAIN event")
        void shouldPublishMissionCreatedEvent() {
            when(publisherPort.publish(any())).thenReturn(Mono.empty());

            StepVerifier.create(missionChainService.recordCreated(
                            "mission-001", "actor-001", "tenant-001", 3))
                    .assertNext(correlationId -> assertThat(correlationId).isNotBlank())
                    .verifyComplete();

            verify(publisherPort, times(1)).publish(argThat(event ->
                    event.getLogisticEventType() ==
                            com.yowyob.tiibntick.core.trust.domain.model.enums.LogisticTrustEventType.MISSION_CREATED_ON_CHAIN));
        }

        @Test
        @DisplayName("recordCompleted() should publish MISSION_COMPLETED_ON_CHAIN event")
        void shouldPublishMissionCompletedEvent() {
            when(publisherPort.publish(any())).thenReturn(Mono.empty());

            StepVerifier.create(missionChainService.recordCompleted(
                            "mission-001", "actor-001", "tenant-001"))
                    .assertNext(correlationId -> assertThat(correlationId).isNotBlank())
                    .verifyComplete();

            verify(publisherPort, times(1)).publish(argThat(event ->
                    event.getLogisticEventType() ==
                            com.yowyob.tiibntick.core.trust.domain.model.enums.LogisticTrustEventType.MISSION_COMPLETED_ON_CHAIN));
        }

        @Test
        @DisplayName("recordCancelled() should publish MISSION_CANCELLED_ON_CHAIN event")
        void shouldPublishMissionCancelledEvent() {
            when(publisherPort.publish(any())).thenReturn(Mono.empty());

            StepVerifier.create(missionChainService.recordCancelled(
                            "mission-001", "tenant-001", "Customer request"))
                    .assertNext(correlationId -> assertThat(correlationId).isNotBlank())
                    .verifyComplete();

            verify(publisherPort, times(1)).publish(argThat(event ->
                    event.getLogisticEventType() ==
                            com.yowyob.tiibntick.core.trust.domain.model.enums.LogisticTrustEventType.MISSION_CANCELLED_ON_CHAIN));
        }
    }

    // ── PolChainService ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PolChainService")
    class PolChainServiceTest {

        @Test
        @DisplayName("record() should publish PROOF_OF_LOCATION_VERIFIED event")
        void shouldPublishPolEvent() {
            when(polVerificationRepository.save(any(PolVerificationRecord.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
            when(publisherPort.publish(any())).thenReturn(Mono.empty());

            StepVerifier.create(polChainService.record(
                            "actor-001", 3.848, 11.502, "polhash123456", "tenant-001"))
                    .assertNext(correlationId -> assertThat(correlationId).isNotBlank())
                    .verifyComplete();

            verify(publisherPort, times(1)).publish(argThat(event ->
                    event.getLogisticEventType() ==
                            com.yowyob.tiibntick.core.trust.domain.model.enums.LogisticTrustEventType.PROOF_OF_LOCATION_VERIFIED
                    && "polhash123456".equals(event.getPolHash())));
        }
    }

    // ── GeofenceChainService ───────────────────────────────────────────────────

    @Nested
    @DisplayName("GeofenceChainService")
    class GeofenceChainServiceTest {

        @Test
        @DisplayName("record() should publish GEOFENCE_CROSSING_RECORDED event")
        void shouldPublishGeofenceCrossingEvent() {
            when(geofenceCrossingRepository.save(any(GeofenceCrossingRecord.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
            when(publisherPort.publish(any())).thenReturn(Mono.empty());

            StepVerifier.create(geofenceChainService.record(
                            "actor-001", "tenant-001", "zone-001", "Relay Hub Yaoundé",
                            "RELAY_HUB", "ENTER", 3.848, 11.502, "mission-001"))
                    .assertNext(correlationId -> assertThat(correlationId).isNotBlank())
                    .verifyComplete();

            verify(publisherPort, times(1)).publish(argThat(event ->
                    event.getLogisticEventType() ==
                            com.yowyob.tiibntick.core.trust.domain.model.enums.LogisticTrustEventType.GEOFENCE_CROSSING_RECORDED));
        }
    }

    // ── DaoRuleChainService ────────────────────────────────────────────────────

    @Nested
    @DisplayName("DaoRuleChainService")
    class DaoRuleChainServiceTest {

        @Test
        @DisplayName("record() should publish DAO_RULE_ACTIVATED event")
        void shouldPublishDaoRuleEvent() {
            when(daoRuleRepository.save(any(DaoRuleRecord.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
            when(publisherPort.publish(any())).thenReturn(Mono.empty());

            StepVerifier.create(daoRuleChainService.record(
                            "zone-001", "{\"votingThreshold\":0.6}", "tenant-001"))
                    .assertNext(correlationId -> assertThat(correlationId).isNotBlank())
                    .verifyComplete();

            verify(publisherPort, times(1)).publish(argThat(event ->
                    event.getLogisticEventType() ==
                            com.yowyob.tiibntick.core.trust.domain.model.enums.LogisticTrustEventType.DAO_RULE_ACTIVATED));
        }
    }

    // ── BadgeChainService ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("BadgeChainService")
    class BadgeChainServiceTest {

        @Test
        @DisplayName("record() should publish BADGE_AWARDED event with correct points")
        void shouldPublishBadgeEvent() {
            when(actorBadgeRepository.save(any(ActorBadge.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
            when(publisherPort.publish(any())).thenReturn(Mono.empty());

            StepVerifier.create(badgeChainService.record(
                            "actor-001", "100_DELIVERIES", 100, "tenant-001"))
                    .assertNext(correlationId -> assertThat(correlationId).isNotBlank())
                    .verifyComplete();

            verify(publisherPort, times(1)).publish(argThat(event ->
                    event.getLogisticEventType() ==
                            com.yowyob.tiibntick.core.trust.domain.model.enums.LogisticTrustEventType.BADGE_AWARDED
                    && event.toKafkaPayload().contains("100_DELIVERIES")));
        }
    }

    // ── DisputeEvidenceChainService ────────────────────────────────────────────

    @Nested
    @DisplayName("DisputeEvidenceChainService")
    class DisputeEvidenceChainServiceTest {

        @Test
        @DisplayName("record() should publish DISPUTE_EVIDENCE_ANCHORED event including the evidence hash")
        void shouldPublishDisputeEvidenceEventWithHash() {
            when(publisherPort.publish(any())).thenReturn(Mono.empty());

            StepVerifier.create(disputeEvidenceChainService.record(
                            "dispute-001", "evidence-001", "minio/photos/dmg-001.jpg",
                            "tenant-001", "a".repeat(64)))
                    .assertNext(correlationId -> assertThat(correlationId).isNotBlank())
                    .verifyComplete();

            verify(publisherPort, times(1)).publish(argThat(event ->
                    event.getLogisticEventType() ==
                            com.yowyob.tiibntick.core.trust.domain.model.enums.LogisticTrustEventType.DISPUTE_EVIDENCE_ANCHORED
                    && event.toKafkaPayload().contains("a".repeat(64))));
        }

        @Test
        @DisplayName("record() should still publish when evidenceHash is null")
        void shouldPublishDisputeEvidenceEventWithoutHash() {
            when(publisherPort.publish(any())).thenReturn(Mono.empty());

            StepVerifier.create(disputeEvidenceChainService.record(
                            "dispute-001", "evidence-001", "minio/photos/dmg-001.jpg",
                            "tenant-001", null))
                    .assertNext(correlationId -> assertThat(correlationId).isNotBlank())
                    .verifyComplete();

            verify(publisherPort, times(1)).publish(any());
        }
    }
}
