package com.yowyob.tiibntick.core.administration.domain;

import com.yowyob.tiibntick.core.administration.domain.model.TntPlatformOptions;
import com.yowyob.tiibntick.core.administration.domain.service.TntRoleTemplateRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for FreelancerOrg and billing template  extensions.
 *
 * @author MANFOUO Braun
 */
class TntPlatformOptionsFreelancerOrgTest {

    @Nested
    @DisplayName("TntPlatformOptions defaults")
    class Defaults {

        @Test
        @DisplayName("defaults() should have freelancerOrgMode enabled")
        void defaultsHaveFreelancerOrgModeEnabled() {
            TntPlatformOptions opts = TntPlatformOptions.defaults(UUID.randomUUID());
            assertThat(opts.isFreelancerOrgModeEnabled()).isTrue();
            assertThat(opts.getMaxFreelancerOrgFleetSize()).isEqualTo(3);
        }

        @Test
        @DisplayName("defaults() should have billing templates enabled")
        void defaultsHaveBillingTemplatesEnabled() {
            TntPlatformOptions opts = TntPlatformOptions.defaults(UUID.randomUUID());
            assertThat(opts.isBillingTemplatesEnabled()).isTrue();
            assertThat(opts.getMaxBillingTemplateDslLevel()).isEqualTo("SIMPLIFIED");
        }
    }

    @Nested
    @DisplayName("TntRoleTemplateRegistry — FreelancerOrg roles")
    class FreelancerOrgRoles {

        @Test
        @DisplayName("Should contain FREELANCER_ORG_OWNER role template")
        void shouldContainFreelancerOrgOwner() {
            TntRoleTemplateRegistry registry = new TntRoleTemplateRegistry();
            boolean found = registry.getTemplates().stream()
                    .anyMatch(t -> "FREELANCER_ORG_OWNER".equals(t.code()));
            assertThat(found).isTrue();
        }

        @Test
        @DisplayName("Should contain FREELANCER_SUB_DELIVERER role template")
        void shouldContainFreelancerSubDeliverer() {
            TntRoleTemplateRegistry registry = new TntRoleTemplateRegistry();
            boolean found = registry.getTemplates().stream()
                    .anyMatch(t -> "FREELANCER_SUB_DELIVERER".equals(t.code()));
            assertThat(found).isTrue();
        }

        @Test
        @DisplayName("FREELANCER_ORG_OWNER should have fleet management permissions")
        void ownerHasFleetPermissions() {
            TntRoleTemplateRegistry registry = new TntRoleTemplateRegistry();
            var owner = registry.getTemplates().stream()
                    .filter(t -> "FREELANCER_ORG_OWNER".equals(t.code()))
                    .findFirst().orElseThrow();
            assertThat(owner.permissions()).contains("freelancer_org:manage_fleet");
            assertThat(owner.permissions()).contains("billing_policy:define_own");
            assertThat(owner.permissions()).contains("billing_templates:use");
        }

        @Test
        @DisplayName("FREELANCER_SUB_DELIVERER should only have mission execution permissions")
        void subDelivererHasMissionPermissions() {
            TntRoleTemplateRegistry registry = new TntRoleTemplateRegistry();
            var sub = registry.getTemplates().stream()
                    .filter(t -> "FREELANCER_SUB_DELIVERER".equals(t.code()))
                    .findFirst().orElseThrow();
            assertThat(sub.permissions()).contains("mission:execute");
            assertThat(sub.permissions()).contains("wallet:view_own_earnings");
            // Sub-deliverer should NOT have fleet management
            assertThat(sub.permissions()).doesNotContain("freelancer_org:manage_fleet");
        }
    }
}
