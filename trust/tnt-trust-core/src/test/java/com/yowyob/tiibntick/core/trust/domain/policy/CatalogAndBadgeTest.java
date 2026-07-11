package com.yowyob.tiibntick.core.trust.domain.policy;

import com.yowyob.tiibntick.core.trust.domain.model.enums.LogisticTrustEventType;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.ActorBadge;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.BillingPolicyRecord;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.LogisticTrustEvent;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.MissionRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link LogisticEventCatalog} and {@link ActorBadge}.
 *
 * @author MANFOUO Braun
 */
@DisplayName("tnt-trust — Catalog and Badge Domain Tests")
class CatalogAndBadgeTest {

    @Test
    @DisplayName("LogisticEventCatalog should have an entry for every LogisticTrustEventType")
    void shouldCoverAllEventTypes() {
        for (final LogisticTrustEventType type : LogisticTrustEventType.values()) {
            assertThatNoException()
                    .as("Catalog entry missing for: " + type)
                    .isThrownBy(() -> LogisticEventCatalog.getEntry(type));
        }
    }

    @Test
    @DisplayName("getChaincodeFunction() should return non-blank function name")
    void shouldReturnChaincodeFunctionName() {
        final String fn = LogisticEventCatalog.getChaincodeFunction(
                LogisticTrustEventType.DELIVERY_PROOF_RECORDED);
        assertThat(fn).isEqualTo("recordDeliveryProof");
    }

    @Test
    @DisplayName("getEntityType() should return correct entity type")
    void shouldReturnEntityType() {
        assertThat(LogisticEventCatalog.getEntityType(LogisticTrustEventType.DELIVERER_DID_ISSUED))
                .isEqualTo("DID_DOCUMENT");
        assertThat(LogisticEventCatalog.getEntityType(LogisticTrustEventType.BADGE_AWARDED))
                .isEqualTo("BADGE");
        assertThat(LogisticEventCatalog.getEntityType(LogisticTrustEventType.DAO_RULE_ACTIVATED))
                .isEqualTo("DAO_RULE");
        assertThat(LogisticEventCatalog.getEntityType(LogisticTrustEventType.BILLING_POLICY_ACTIVATED))
                .isEqualTo("BILLING_POLICY");
    }

    @Test
    @DisplayName("ActorBadge.award() should create a non-verified badge initially")
    void shouldCreateNonVerifiedBadge() {
        final ActorBadge badge = ActorBadge.award("actor-001", "tenant-001", "100_DELIVERIES", 100);
        assertThat(badge.isVerifiable()).isFalse();
        assertThat(badge.isRevoked()).isFalse();
        assertThat(badge.getPoints()).isEqualTo(100);
    }

    @Test
    @DisplayName("ActorBadge.confirmOnChain() should make badge verifiable")
    void shouldBecomeVerifiableAfterConfirmation() {
        final ActorBadge badge = ActorBadge.award("actor-001", "tenant-001", "TOP_RATED", 200);
        badge.confirmOnChain("b".repeat(64));
        assertThat(badge.isVerifiable()).isTrue();
    }

    @Test
    @DisplayName("ActorBadge.revoke() should make badge non-verifiable")
    void shouldNotBeVerifiableAfterRevocation() {
        final ActorBadge badge = ActorBadge.award("actor-001", "tenant-001", "ZERO_CLAIM", 150);
        badge.confirmOnChain("b".repeat(64));
        badge.revoke();
        assertThat(badge.isVerifiable()).isFalse();
        assertThat(badge.isRevoked()).isTrue();
    }

    @Test
    @DisplayName("MissionRecord.created() should have correct event type")
    void shouldCreateMissionRecord() {
        final MissionRecord record = MissionRecord.created("m-001", "tenant-001", "actor-001", 3);
        assertThat(record.getEventType()).isEqualTo(LogisticTrustEventType.MISSION_CREATED_ON_CHAIN);
        assertThat(record.isOnChain()).isFalse();
        record.confirmOnChain("b".repeat(64));
        assertThat(record.isOnChain()).isTrue();
    }

    @Test
    @DisplayName("BillingPolicyRecord.activate() should mark wasRecordedOnChain=false initially")
    void shouldCreateBillingPolicyRecord() {
        final BillingPolicyRecord record = BillingPolicyRecord.activate(
                "policy-001", "agency-001", "tenant-001", "{\"basePrice\":\"1000 XAF\"}");
        assertThat(record.wasRecordedOnChain()).isFalse();
        record.confirmOnChain("b".repeat(64));
        assertThat(record.wasRecordedOnChain()).isTrue();
    }

    @Test
    @DisplayName("LogisticTrustEvent should support all new mission factory methods")
    void shouldSupportMissionFactoryMethods() {
        final LogisticTrustEvent created = LogisticTrustEvent.forMissionCreated(
                "m-001", "actor-001", "tenant-001", 5);
        assertThat(created.getLogisticEventType())
                .isEqualTo(LogisticTrustEventType.MISSION_CREATED_ON_CHAIN);
        assertThat(created.getMissionId()).isEqualTo("m-001");

        final LogisticTrustEvent completed = LogisticTrustEvent.forMissionCompleted(
                "m-001", "actor-001", "tenant-001");
        assertThat(completed.getLogisticEventType())
                .isEqualTo(LogisticTrustEventType.MISSION_COMPLETED_ON_CHAIN);

        final LogisticTrustEvent cancelled = LogisticTrustEvent.forMissionCancelled(
                "m-001", "tenant-001", "Client request");
        assertThat(cancelled.getLogisticEventType())
                .isEqualTo(LogisticTrustEventType.MISSION_CANCELLED_ON_CHAIN);
        assertThat(cancelled.toKafkaPayload()).contains("Client request");
    }
}
