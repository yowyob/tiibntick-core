package com.yowyob.tiibntick.core.notify.application.service;

import com.yowyob.tiibntick.core.notify.domain.enums.NotificationType;
import com.yowyob.tiibntick.core.notify.domain.model.FreelancerOrgNotificationTemplates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for FreelancerOrg  notification extensions.
 *
 * @author MANFOUO Braun
 */
class NotificationFreelancerOrgTest {

    @Nested
    @DisplayName("NotificationType — FreelancerOrg values")
    class FreelancerOrgNotificationTypes {

        @Test
        @DisplayName("All FreelancerOrg notification types should exist")
        void allFreelancerOrgTypesExist() {
            assertThat(NotificationType.valueOf("FREELANCER_ORG_VERIFIED")).isNotNull();
            assertThat(NotificationType.valueOf("FREELANCER_ORG_SUSPENDED")).isNotNull();
            assertThat(NotificationType.valueOf("SUB_DELIVERER_INVITED")).isNotNull();
            assertThat(NotificationType.valueOf("SUB_DELIVERER_INVITATION_ACCEPTED")).isNotNull();
            assertThat(NotificationType.valueOf("SUB_DELIVERER_MISSION_ASSIGNED")).isNotNull();
            assertThat(NotificationType.valueOf("KYC_LEVEL_UPGRADED")).isNotNull();
            assertThat(NotificationType.valueOf("BILLING_POLICY_TEMPLATE_APPLIED")).isNotNull();
            assertThat(NotificationType.valueOf("SURCHARGE_TRIGGERED")).isNotNull();
        }

        @Test
        @DisplayName("All original notification types must be preserved")
        void allOriginalTypesPreserved() {
            assertThat(NotificationType.MISSION_ASSIGNED).isNotNull();
            assertThat(NotificationType.INCIDENT_CREATED).isNotNull();
            assertThat(NotificationType.INVOICE_GENERATED).isNotNull();
            assertThat(NotificationType.KYC_APPROVED).isNotNull();
            assertThat(NotificationType.DISPUTE_OPENED).isNotNull();
        }
    }

    @Nested
    @DisplayName("FreelancerOrgNotificationTemplates")
    class TemplatesCatalog {

        @Test
        @DisplayName("All template keys must have French default messages")
        void allTemplatesHaveFrenchMessages() {
            assertThat(FreelancerOrgNotificationTemplates.DEFAULT_FR_TEMPLATES)
                    .containsKey(FreelancerOrgNotificationTemplates.FREELANCER_ORG_VERIFIED)
                    .containsKey(FreelancerOrgNotificationTemplates.FREELANCER_ORG_SUSPENDED)
                    .containsKey(FreelancerOrgNotificationTemplates.SUB_DELIVERER_INVITED)
                    .containsKey(FreelancerOrgNotificationTemplates.SUB_DELIVERER_INVITATION_ACCEPTED)
                    .containsKey(FreelancerOrgNotificationTemplates.SUB_DELIVERER_MISSION_ASSIGNED)
                    .containsKey(FreelancerOrgNotificationTemplates.FREELANCER_ORG_KYC_LEVEL_UPGRADED)
                    .containsKey(FreelancerOrgNotificationTemplates.BILLING_TEMPLATE_APPLIED)
                    .containsKey(FreelancerOrgNotificationTemplates.SURCHARGE_TRIGGERED);
        }

        @Test
        @DisplayName("All template keys must have English default messages")
        void allTemplatesHaveEnglishMessages() {
            assertThat(FreelancerOrgNotificationTemplates.DEFAULT_EN_TEMPLATES)
                    .containsKey(FreelancerOrgNotificationTemplates.FREELANCER_ORG_VERIFIED)
                    .containsKey(FreelancerOrgNotificationTemplates.FREELANCER_ORG_SUSPENDED)
                    .containsKey(FreelancerOrgNotificationTemplates.SUB_DELIVERER_INVITED);
        }

        @Test
        @DisplayName("French templates should not be empty")
        void frenchTemplatesShouldNotBeEmpty() {
            FreelancerOrgNotificationTemplates.DEFAULT_FR_TEMPLATES.values()
                    .forEach(msg -> assertThat(msg).isNotBlank());
        }

        @Test
        @DisplayName("SURCHARGE_TRIGGERED message should contain amount placeholder")
        void surchargeTemplateShouldHaveAmountPlaceholder() {
            String msg = FreelancerOrgNotificationTemplates.DEFAULT_FR_TEMPLATES
                    .get(FreelancerOrgNotificationTemplates.SURCHARGE_TRIGGERED);
            assertThat(msg).contains("{surchargeAmount}");
        }
    }
}
