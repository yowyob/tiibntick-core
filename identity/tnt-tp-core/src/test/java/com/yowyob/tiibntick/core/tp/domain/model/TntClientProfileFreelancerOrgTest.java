package com.yowyob.tiibntick.core.tp.domain.model;

import com.yowyob.tiibntick.core.tp.domain.model.enums.TntThirdPartyRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for FreelancerOrg  extensions to TntClientProfile.
 *
 * @author MANFOUO Braun
 */
class TntClientProfileFreelancerOrgTest {

    private TntClientProfile profile;

    @BeforeEach
    void setUp() {
        profile = TntClientProfile.create(
                UUID.randomUUID(), UUID.randomUUID(),
                Set.of(TntThirdPartyRole.SENDER), "fr", "XAF");
    }

    @Nested
    @DisplayName("TntThirdPartyRole.FREELANCER_ORG_CLIENT")
    class FreelancerOrgClientRole {

        @Test
        @DisplayName("FREELANCER_ORG_CLIENT enum value must exist")
        void freelancerOrgClientExists() {
            assertThat(TntThirdPartyRole.valueOf("FREELANCER_ORG_CLIENT"))
                    .isEqualTo(TntThirdPartyRole.FREELANCER_ORG_CLIENT);
        }

        @Test
        @DisplayName("All original roles must be preserved")
        void allOriginalRolesExist() {
            assertThat(TntThirdPartyRole.SENDER).isNotNull();
            assertThat(TntThirdPartyRole.RECIPIENT).isNotNull();
            assertThat(TntThirdPartyRole.DELIVERER).isNotNull();
            assertThat(TntThirdPartyRole.AGENCY_CLIENT).isNotNull();
            assertThat(TntThirdPartyRole.FREELANCER).isNotNull();
            assertThat(TntThirdPartyRole.RELAY_POINT_OPERATOR).isNotNull();
        }
    }

    @Nested
    @DisplayName("providerLinks — default")
    class DefaultProviderLinks {

        @Test
        @DisplayName("New profile should have empty providerLinks")
        void newProfileHasEmptyLinks() {
            assertThat(profile.getProviderLinks()).isEmpty();
            assertThat(profile.isLinkedToFreelancerOrg()).isFalse();
            assertThat(profile.getLinkedFreelancerOrgId()).isNull();
        }
    }

    @Nested
    @DisplayName("linkToFreelancerOrg")
    class LinkToFreelancerOrg {

        @Test
        @DisplayName("Should add FreelancerOrg to providerLinks")
        void shouldLinkToFreelancerOrg() {
            String orgId = "FRL-ORG-001";
            TntClientProfile linked = profile.linkToFreelancerOrg(orgId);

            assertThat(linked.getProviderLinks()).containsEntry("FREELANCER_ORG", orgId);
            assertThat(linked.isLinkedToFreelancerOrg()).isTrue();
            assertThat(linked.getLinkedFreelancerOrgId()).isEqualTo(orgId);
        }

        @Test
        @DisplayName("Should add FREELANCER_ORG_CLIENT role when linked")
        void shouldAddClientRole() {
            TntClientProfile linked = profile.linkToFreelancerOrg("FRL-001");
            assertThat(linked.getTntRoles()).contains(TntThirdPartyRole.FREELANCER_ORG_CLIENT);
        }

        @Test
        @DisplayName("Original roles should be preserved after linking")
        void shouldPreserveExistingRoles() {
            TntClientProfile linked = profile.linkToFreelancerOrg("FRL-001");
            assertThat(linked.getTntRoles()).contains(TntThirdPartyRole.SENDER);
        }

        @Test
        @DisplayName("linkToFreelancerOrg should be immutable — profile unchanged")
        void linkingShouldBeImmutable() {
            profile.linkToFreelancerOrg("FRL-001");
            assertThat(profile.isLinkedToFreelancerOrg()).isFalse();
        }

        @Test
        @DisplayName("Should reject null orgId")
        void shouldRejectNullOrgId() {
            assertThatThrownBy(() -> profile.linkToFreelancerOrg(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("unlinkFromFreelancerOrg")
    class UnlinkFromFreelancerOrg {

        @Test
        @DisplayName("Should remove FreelancerOrg from providerLinks")
        void shouldUnlink() {
            TntClientProfile linked = profile.linkToFreelancerOrg("FRL-001");
            TntClientProfile unlinked = linked.unlinkFromFreelancerOrg("FRL-001");

            assertThat(unlinked.isLinkedToFreelancerOrg()).isFalse();
            assertThat(unlinked.getProviderLinks()).doesNotContainKey("FREELANCER_ORG");
        }

        @Test
        @DisplayName("Should revoke FREELANCER_ORG_CLIENT role after unlinking")
        void shouldRevokeClientRole() {
            TntClientProfile linked = profile.linkToFreelancerOrg("FRL-001");
            TntClientProfile unlinked = linked.unlinkFromFreelancerOrg("FRL-001");
            assertThat(unlinked.getTntRoles()).doesNotContain(TntThirdPartyRole.FREELANCER_ORG_CLIENT);
        }
    }
}
