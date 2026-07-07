package com.yowyob.tiibntick.core.actor.domain;

import com.yowyob.tiibntick.core.actor.domain.model.ActorStatus;
import com.yowyob.tiibntick.core.actor.domain.model.FreelancerProfile;
import com.yowyob.tiibntick.core.actor.domain.model.FreelancerRole;
import com.yowyob.tiibntick.core.actor.domain.model.KycStatus;
import com.yowyob.tiibntick.core.actor.domain.model.ServiceZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link FreelancerProfile} domain model.
 *
 * <p>Covers existing profile mutations +  FreelancerOrganization link mutations.
 *
 * @author MANFOUO Braun
 */
class FreelancerProfileTest {

    private UUID tenantId;
    private UUID actorId;
    private FreelancerProfile profile;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        profile = FreelancerProfile.create(tenantId, actorId,
                List.of(ServiceZoneId.of(UUID.randomUUID())), List.of());
    }

    // ── Creation ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create() initializes with INACTIVE status, KYC PENDING, no org link")
    void create_initialState() {
        assertThat(profile.actorStatus()).isEqualTo(ActorStatus.INACTIVE);
        assertThat(profile.kycStatus()).isEqualTo(KycStatus.PENDING);
        assertThat(profile.freelancerOrgId()).isNull();
        assertThat(profile.roleInOrg()).isNull();
        assertThat(profile.isOrgVerified()).isFalse();
        assertThat(profile.hasOrgLink()).isFalse();
        assertThat(profile.incidentHistoryCount()).isZero();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("activate() transitions to ACTIVE status")
    void activate_changesStatus() {
        FreelancerProfile activated = profile.activate();
        assertThat(activated.actorStatus()).isEqualTo(ActorStatus.ACTIVE);
        assertThat(activated.isActive()).isTrue();
        // immutable: original unchanged
        assertThat(profile.actorStatus()).isEqualTo(ActorStatus.INACTIVE);
    }

    @Test
    @DisplayName("suspend() transitions to SUSPENDED status")
    void suspend_changesStatus() {
        FreelancerProfile suspended = profile.suspend("investigation");
        assertThat(suspended.actorStatus()).isEqualTo(ActorStatus.SUSPENDED);
    }

    // ── Incident tracking ─────────────────────────────────────────────────────

    @Test
    @DisplayName("withIncrementedIncidentCount() increments counter")
    void incidentCount_increments() {
        FreelancerProfile after = profile.withIncrementedIncidentCount()
                .withIncrementedIncidentCount();
        assertThat(after.incidentHistoryCount()).isEqualTo(2);
        assertThat(profile.incidentHistoryCount()).isZero();
    }

    // FreelancerOrg link ─────────────────────────────────────────────

    @Test
    @DisplayName("withFreelancerOrgLink(OWNER) sets orgId, role, verification flag")
    void withFreelancerOrgLink_owner() {
        UUID orgId = UUID.randomUUID();
        FreelancerProfile linked = profile.withFreelancerOrgLink(orgId, FreelancerRole.OWNER, false);

        assertThat(linked.freelancerOrgId()).isEqualTo(orgId);
        assertThat(linked.roleInOrg()).isEqualTo(FreelancerRole.OWNER);
        assertThat(linked.isOrgVerified()).isFalse();
        assertThat(linked.hasOrgLink()).isTrue();
        assertThat(linked.isOrgOwner()).isTrue();
        assertThat(linked.isSubDeliverer()).isFalse();
    }

    @Test
    @DisplayName("withFreelancerOrgLink(SUB_DELIVERER) sets correct role")
    void withFreelancerOrgLink_subDeliverer() {
        UUID orgId = UUID.randomUUID();
        FreelancerProfile linked = profile.withFreelancerOrgLink(orgId,
                FreelancerRole.SUB_DELIVERER, true);

        assertThat(linked.roleInOrg()).isEqualTo(FreelancerRole.SUB_DELIVERER);
        assertThat(linked.isOrgVerified()).isTrue();
        assertThat(linked.isSubDeliverer()).isTrue();
        assertThat(linked.isOrgOwner()).isFalse();
    }

    @Test
    @DisplayName("withFreelancerOrgLink() is immutable — original unchanged")
    void withFreelancerOrgLink_immutable() {
        UUID orgId = UUID.randomUUID();
        profile.withFreelancerOrgLink(orgId, FreelancerRole.OWNER, false);
        assertThat(profile.freelancerOrgId()).isNull();
    }

    @Test
    @DisplayName("withFreelancerOrgLink() rejects null orgId")
    void withFreelancerOrgLink_rejectsNullOrgId() {
        assertThatThrownBy(() -> profile.withFreelancerOrgLink(null, FreelancerRole.OWNER, false))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("withOrgVerificationUpdate() updates isOrgVerified flag")
    void withOrgVerificationUpdate_updatesFlag() {
        UUID orgId = UUID.randomUUID();
        FreelancerProfile linked = profile.withFreelancerOrgLink(orgId, FreelancerRole.OWNER, false);
        assertThat(linked.isOrgVerified()).isFalse();

        FreelancerProfile verified = linked.withOrgVerificationUpdate(true);
        assertThat(verified.isOrgVerified()).isTrue();
        assertThat(verified.freelancerOrgId()).isEqualTo(orgId);
    }

    @Test
    @DisplayName("withOrgVerificationUpdate() throws when no org link")
    void withOrgVerificationUpdate_throwsWithoutLink() {
        assertThatThrownBy(() -> profile.withOrgVerificationUpdate(true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no FreelancerOrg link");
    }

    @Test
    @DisplayName("withoutFreelancerOrg() clears all org fields")
    void withoutFreelancerOrg_clearsLink() {
        UUID orgId = UUID.randomUUID();
        FreelancerProfile linked = profile.withFreelancerOrgLink(orgId, FreelancerRole.OWNER, true);
        FreelancerProfile unlinked = linked.withoutFreelancerOrg();

        assertThat(unlinked.freelancerOrgId()).isNull();
        assertThat(unlinked.roleInOrg()).isNull();
        assertThat(unlinked.isOrgVerified()).isFalse();
        assertThat(unlinked.hasOrgLink()).isFalse();
    }

    // ── Agency association (existing) ─────────────────────────────────────────

    @Test
    @DisplayName("associateWithAgency() adds agency to set")
    void associateWithAgency_adds() {
        UUID agencyId = UUID.randomUUID();
        FreelancerProfile updated = profile.associateWithAgency(agencyId);
        assertThat(updated.associatedAgencyIds()).contains(agencyId);
        assertThat(updated.isAssociatedWith(agencyId)).isTrue();
    }

    @Test
    @DisplayName("dissociateFromAgency() removes agency from set")
    void dissociateFromAgency_removes() {
        UUID agencyId = UUID.randomUUID();
        FreelancerProfile withAgency = profile.associateWithAgency(agencyId);
        FreelancerProfile without = withAgency.dissociateFromAgency(agencyId);
        assertThat(without.isAssociatedWith(agencyId)).isFalse();
    }
}
