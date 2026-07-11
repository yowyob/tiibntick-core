package com.yowyob.tiibntick.core.trust.domain.model.valueobject;

import com.yowyob.tiibntick.core.trust.domain.model.enums.LogisticTrustEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for FreelancerOrg  extensions to tnt-trust domain.
 *
 * @author MANFOUO Braun
 */
class FreelancerOrgTrustTest {

    @Nested
    @DisplayName("DIDDocument.issueForFreelancerOrg")
    class FreelancerOrgDID {

        @Test
        @DisplayName("Should set subjectType to FREELANCER_ORG")
        void shouldSetFreelancerOrgSubjectType() {
            DIDDocument did = DIDDocument.issueForFreelancerOrg(
                    "FRL-ORG-001", "tenant-1", "Moto Express Biyem", "-----BEGIN PUBLIC KEY-----\ntest\n-----END PUBLIC KEY-----");
            assertThat(did.getSubjectType()).isEqualTo("FREELANCER_ORG");
            assertThat(did.getOrgId()).isEqualTo("FRL-ORG-001");
            assertThat(did.isFreelancerOrgDID()).isTrue();
        }

        @Test
        @DisplayName("DID should use org: prefix format")
        void shouldUseOrgDIDFormat() {
            DIDDocument did = DIDDocument.issueForFreelancerOrg(
                    "FRL-ORG-001", "tenant-1", "Moto Express Biyem", "pubkey");
            assertThat(did.getDid()).startsWith("did:tiibntick:tenant-1:org:");
            assertThat(did.getDid()).contains("FRL-ORG-001");
        }

        @Test
        @DisplayName("FreelancerOrg DID should be valid for 2 years")
        void shouldBeValidFor2Years() {
            DIDDocument did = DIDDocument.issueForFreelancerOrg(
                    "FRL-001", "tenant-1", "Test Org", "pubkey");
            assertThat(did.getExpiresAt()).isAfter(did.getIssuedAt().plusYears(1).plusMonths(11));
        }
    }

    @Nested
    @DisplayName("DIDDocument.issue — backward compatibility")
    class LegacyDID {

        @Test
        @DisplayName("Standard issue() should set subjectType to ACTOR")
        void standardDIDShouldBeActor() {
            DIDDocument did = DIDDocument.issue("actor-1", "tenant-1", "pubkey", null);
            assertThat(did.getSubjectType()).isEqualTo("ACTOR");
            assertThat(did.getOrgId()).isNull();
            assertThat(did.isFreelancerOrgDID()).isFalse();
        }
    }

    @Nested
    @DisplayName("DeliveryProofRecord — FreelancerOrg context")
    class DeliveryProofFreelancerOrg {

        @Test
        @DisplayName("Should set executor org fields")
        void shouldSetExecutorOrgFields() {
            DeliveryProofRecord proof = new DeliveryProofRecord(
                    "proof-1", "mission-1", "pkg-1", "actor-1", "tenant-1",
                    "photo-hash", "sig-hash", 3.86, 11.50, LocalDateTime.now(),
                    "FRL-ORG-001", "FREELANCER_ORG", "SUB-001");

            assertThat(proof.getExecutorOrgId()).isEqualTo("FRL-ORG-001");
            assertThat(proof.getExecutorOrgType()).isEqualTo("FREELANCER_ORG");
            assertThat(proof.getSubDelivererId()).isEqualTo("SUB-001");
        }

        @Test
        @DisplayName("Legacy constructor should default to null executor org")
        void legacyConstructorShouldHaveNullOrg() {
            DeliveryProofRecord proof = new DeliveryProofRecord(
                    "proof-1", "mission-1", "pkg-1", "actor-1", "tenant-1",
                    "photo-hash", null, 3.86, 11.50, LocalDateTime.now());

            assertThat(proof.getExecutorOrgId()).isNull();
            assertThat(proof.getExecutorOrgType()).isNull();
            assertThat(proof.getSubDelivererId()).isNull();
        }
    }

    @Nested
    @DisplayName("LogisticTrustEventType — FreelancerOrg types")
    class FreelancerOrgEventTypes {

        @Test
        @DisplayName("FREELANCER_ORG_DID_ISSUED should exist")
        void freelancerOrgDIDIssuedExists() {
            assertThat(LogisticTrustEventType.valueOf("FREELANCER_ORG_DID_ISSUED"))
                    .isEqualTo(LogisticTrustEventType.FREELANCER_ORG_DID_ISSUED);
        }

        @Test
        @DisplayName("FREELANCER_ORG_DID_REVOKED should exist")
        void freelancerOrgDIDRevokedExists() {
            assertThat(LogisticTrustEventType.valueOf("FREELANCER_ORG_DID_REVOKED"))
                    .isEqualTo(LogisticTrustEventType.FREELANCER_ORG_DID_REVOKED);
        }

        @Test
        @DisplayName("Original event types should be preserved")
        void originalTypesPreserved() {
            assertThat(LogisticTrustEventType.DELIVERER_DID_ISSUED).isNotNull();
            assertThat(LogisticTrustEventType.BADGE_AWARDED).isNotNull();
            assertThat(LogisticTrustEventType.DELIVERY_PROOF_RECORDED).isNotNull();
        }
    }
}
