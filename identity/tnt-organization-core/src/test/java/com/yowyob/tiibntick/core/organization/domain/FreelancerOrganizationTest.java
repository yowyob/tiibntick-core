package com.yowyob.tiibntick.core.organization.domain;

import com.yowyob.tiibntick.core.organization.domain.enums.AssociationStatus;
import com.yowyob.tiibntick.core.organization.domain.enums.FreelancerRegStatus;
import com.yowyob.tiibntick.core.organization.domain.enums.KycLevel;
import com.yowyob.tiibntick.core.organization.domain.model.FreelancerOrganization;
import com.yowyob.tiibntick.core.organization.domain.vo.AssociatedDelivererRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link FreelancerOrganization} aggregate root.
 *
 * <p>Pure domain tests — no Spring context, no database.
 *
 * @author MANFOUO Braun
 */
class FreelancerOrganizationTest {

    private UUID ownerActorId;
    private FreelancerOrganization org;

    @BeforeEach
    void setUp() {
        ownerActorId = UUID.randomUUID();
        org = FreelancerOrganization.register(null, ownerActorId, "Moto Express Test");
    }

    // ─── Registration ─────────────────────────────────────────────────────

    @Test
    @DisplayName("register() creates org with REGISTRATION_PENDING status and KYC NONE")
    void register_createsWithPendingStatus() {
        assertThat(org.getRegistrationStatus()).isEqualTo(FreelancerRegStatus.REGISTRATION_PENDING);
        assertThat(org.getKycLevel()).isEqualTo(KycLevel.NONE);
        assertThat(org.getTradeName()).isEqualTo("Moto Express Test");
        assertThat(org.getOwnerActorId()).isEqualTo(ownerActorId);
        assertThat(org.getTenantId()).startsWith("FRL-");
        assertThat(org.getTrustScore()).isEqualTo(0.0);
        assertThat(org.getSubDeliverers()).isEmpty();
        assertThat(org.getOperationalZones()).isEmpty();
    }

    @Test
    @DisplayName("register() rejects null ownerActorId")
    void register_rejectsNullOwner() {
        assertThatThrownBy(() -> FreelancerOrganization.register(null, null, "Test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ownerActorId");
    }

    // ─── KYC lifecycle ────────────────────────────────────────────────────

    @Test
    @DisplayName("upgradeKycToBasic() transitions from NONE to BASIC")
    void upgradeKycToBasic_fromNone() {
        org.upgradeKycToBasic();
        assertThat(org.getKycLevel()).isEqualTo(KycLevel.BASIC);
    }

    @Test
    @DisplayName("upgradeKycToBasic() throws if already BASIC")
    void upgradeKycToBasic_alreadyBasic() {
        org.upgradeKycToBasic();
        assertThatThrownBy(org::upgradeKycToBasic)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("upgradeKycToFull() transitions from BASIC to FULL")
    void upgradeKycToFull_fromBasic() {
        org.upgradeKycToBasic();
        org.upgradeKycToFull();
        assertThat(org.getKycLevel()).isEqualTo(KycLevel.FULL);
    }

    @Test
    @DisplayName("upgradeKycToFull() throws if not BASIC")
    void upgradeKycToFull_fromNone() {
        assertThatThrownBy(org::upgradeKycToFull)
                .isInstanceOf(IllegalStateException.class);
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────

    @Test
    @DisplayName("verify() → activate() happy path")
    void verifyAndActivate_happyPath() {
        org.submitForReview();
        org.verify();
        assertThat(org.getRegistrationStatus()).isEqualTo(FreelancerRegStatus.VERIFIED);
        org.activate();
        assertThat(org.getRegistrationStatus()).isEqualTo(FreelancerRegStatus.ACTIVE);
        assertThat(org.isOperational()).isTrue();
    }

    @Test
    @DisplayName("suspend() → unsuspend() round trip")
    void suspendAndUnsuspend() {
        org.submitForReview();
        org.verify();
        org.activate();
        org.suspend();
        assertThat(org.getRegistrationStatus()).isEqualTo(FreelancerRegStatus.SUSPENDED);
        org.unsuspend();
        assertThat(org.getRegistrationStatus()).isEqualTo(FreelancerRegStatus.ACTIVE);
    }

    @Test
    @DisplayName("blacklist() prevents unsuspend")
    void blacklist_preventsUnsuspend() {
        org.submitForReview();
        org.verify();
        org.activate();
        org.blacklist();
        assertThatThrownBy(org::unsuspend)
                .isInstanceOf(IllegalStateException.class);
    }

    // ─── Sub-deliverer management ─────────────────────────────────────────

    @Test
    @DisplayName("inviteSubDeliverer() creates PENDING_ACCEPTANCE ref")
    void inviteSubDeliverer_createsPendingRef() {
        UUID subId = UUID.randomUUID();
        AssociatedDelivererRef ref = org.inviteSubDeliverer(subId, new BigDecimal("0.70"));

        assertThat(ref.status()).isEqualTo(AssociationStatus.PENDING_ACCEPTANCE);
        assertThat(ref.delivererActorId()).isEqualTo(subId);
        assertThat(ref.commissionRate()).isEqualByComparingTo("0.70");
        assertThat(org.getSubDeliverers()).hasSize(1);
    }

    @Test
    @DisplayName("acceptSubDeliverer() transitions to ACTIVE")
    void acceptSubDeliverer_becomesActive() {
        UUID subId = UUID.randomUUID();
        org.inviteSubDeliverer(subId, new BigDecimal("0.65"));
        org.acceptSubDeliverer(subId);

        AssociatedDelivererRef ref = org.getSubDeliverers().stream()
                .filter(r -> r.delivererActorId().equals(subId)).findFirst().orElseThrow();
        assertThat(ref.status()).isEqualTo(AssociationStatus.ACTIVE);
        assertThat(ref.associatedSince()).isNotNull();
    }

    @Test
    @DisplayName("revokeSubDeliverer() transitions to TERMINATED")
    void revokeSubDeliverer_becomesTerminated() {
        UUID subId = UUID.randomUUID();
        org.inviteSubDeliverer(subId, new BigDecimal("0.60"));
        org.acceptSubDeliverer(subId);
        org.revokeSubDeliverer(subId);

        AssociatedDelivererRef ref = org.getSubDeliverers().stream()
                .filter(r -> r.delivererActorId().equals(subId)).findFirst().orElseThrow();
        assertThat(ref.status()).isEqualTo(AssociationStatus.TERMINATED);
        assertThat(ref.terminatedAt()).isNotNull();
    }

    @Test
    @DisplayName("inviteSubDeliverer() enforces MAX_SUB_DELIVERERS limit")
    void inviteSubDeliverer_maxLimitEnforced() {
        for (int i = 0; i < FreelancerOrganization.MAX_SUB_DELIVERERS; i++) {
            org.inviteSubDeliverer(UUID.randomUUID(), new BigDecimal("0.60"));
        }
        assertThatThrownBy(() -> org.inviteSubDeliverer(UUID.randomUUID(), new BigDecimal("0.60")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot have more than");
    }

    @Test
    @DisplayName("inviteSubDeliverer() rejects duplicate active actor")
    void inviteSubDeliverer_rejectsDuplicate() {
        UUID subId = UUID.randomUUID();
        org.inviteSubDeliverer(subId, new BigDecimal("0.60"));
        assertThatThrownBy(() -> org.inviteSubDeliverer(subId, new BigDecimal("0.70")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already active or pending");
    }

    // ─── Trust score ──────────────────────────────────────────────────────

    @Test
    @DisplayName("updateTrustScore() validates range [0.0, 5.0]")
    void updateTrustScore_validatesRange() {
        org.updateTrustScore(4.8);
        assertThat(org.getTrustScore()).isEqualTo(4.8);

        assertThatThrownBy(() -> org.updateTrustScore(-0.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> org.updateTrustScore(5.1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
