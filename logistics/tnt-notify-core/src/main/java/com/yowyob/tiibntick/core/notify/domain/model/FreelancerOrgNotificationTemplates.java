package com.yowyob.tiibntick.core.notify.domain.model;

import java.util.Map;

/**
 * Catalog of notification template keys and their default i18n message
 * templates
 * for FreelancerOrg-related events ().
 *
 * <p>
 * These template keys are referenced by
 * {@link NotificationModel#templateKey()}
 * and resolved by the {@code ITranslationPort} implementation.
 *
 * <p>
 * Template variable convention: {@code {variableName}} placeholders are
 * replaced
 * by the parameters map at render time.
 *
 * @author MANFOUO Braun
 */
public final class FreelancerOrgNotificationTemplates {

        private FreelancerOrgNotificationTemplates() {
        }

        // ── Template keys ─────────────────────────────────────────────────────────

        public static final String FREELANCER_ORG_VERIFIED = "notify.freelancer_org.verified";
        public static final String FREELANCER_ORG_KYC_LEVEL_UPGRADED = "notify.freelancer_org.kyc_level_upgraded";
        public static final String FREELANCER_ORG_SUSPENDED = "notify.freelancer_org.suspended";
        public static final String FREELANCER_ORG_UNSUSPENDED = "notify.freelancer_org.unsuspended";
        public static final String FREELANCER_ORG_KYC_REJECTED = "notify.freelancer_org.kyc_rejected";
        public static final String FREELANCER_ORG_MISSION_ASSIGNED = "notify.freelancer_org.mission_assigned";
        public static final String SUB_DELIVERER_INVITED = "notify.sub_deliverer.invited";
        public static final String SUB_DELIVERER_INVITATION_ACCEPTED = "notify.sub_deliverer.invitation_accepted";
        public static final String SUB_DELIVERER_MISSION_ASSIGNED = "notify.sub_deliverer.mission_assigned";
        public static final String BILLING_TEMPLATE_APPLIED = "notify.billing.template_applied";
        public static final String SURCHARGE_TRIGGERED = "notify.billing.surcharge_triggered";

        // ── Default French templates ──────────────────────────────────────────────

        /**
         * Default French message templates, keyed by template code.
         * Used as fallback when the translation service is unavailable.
         */
        public static final Map<String, String> DEFAULT_FR_TEMPLATES = Map.ofEntries(
                        Map.entry(FREELANCER_ORG_VERIFIED,
                                        "🎉 Félicitations ! Votre organisation FreelancerOrg a été vérifiée. Vous pouvez maintenant accepter des missions sur TiiBnTick."),
                        Map.entry(FREELANCER_ORG_KYC_LEVEL_UPGRADED,
                                        "✅ Niveau KYC amélioré : votre organisation est maintenant certifiée niveau {kycLevel}. Nouvelles fonctionnalités disponibles."),
                        Map.entry(FREELANCER_ORG_SUSPENDED,
                                        "⚠️ Votre organisation a été suspendue. Raison : {reason}. Contactez le support pour plus d'informations."),
                        Map.entry(FREELANCER_ORG_UNSUSPENDED,
                                        "✅ La suspension de votre organisation a été levée. Vous pouvez reprendre vos activités."),
                        Map.entry(FREELANCER_ORG_KYC_REJECTED,
                                        "❌ Votre demande de vérification KYC a été rejetée. Raison : {reason}. Veuillez soumettre de nouveaux documents."),
                        Map.entry(FREELANCER_ORG_MISSION_ASSIGNED,
                                        "📦 Mission assignée à votre organisation. Référence : {deliveryId}."),
                        Map.entry(SUB_DELIVERER_INVITED,
                                        "📩 Vous avez été invité(e) à rejoindre l'organisation {orgName} comme sous-livreur. Acceptez ou refusez via l'application."),
                        Map.entry(SUB_DELIVERER_INVITATION_ACCEPTED,
                                        "✅ {subDelivererName} a accepté votre invitation et rejoint votre organisation."),
                        Map.entry(SUB_DELIVERER_MISSION_ASSIGNED,
                                        "🏍️ Nouvelle mission : {deliveryId} vous a été assignée. Préparez-vous pour le ramassage."),
                        Map.entry(BILLING_TEMPLATE_APPLIED,
                                        "💼 Politique tarifaire créée à partir du template {templateCode}. ID politique : {policyId}."),
                        Map.entry(SURCHARGE_TRIGGERED,
                                        "ℹ️ Un supplément tarifaire a été appliqué à votre livraison ({deliveryId}) : {surchargeName} = {surchargeAmount} XAF."));

        // ── Default English templates ─────────────────────────────────────────────

        /**
         * Default English message templates (for international users).
         */
        public static final Map<String, String> DEFAULT_EN_TEMPLATES = Map.ofEntries(
                        Map.entry(FREELANCER_ORG_VERIFIED,
                                        "🎉 Congratulations! Your FreelancerOrg has been verified. You can now accept missions on TiiBnTick."),
                        Map.entry(FREELANCER_ORG_KYC_LEVEL_UPGRADED,
                                        "✅ KYC Level Upgraded: your organization is now certified at level {kycLevel}. New features unlocked."),
                        Map.entry(FREELANCER_ORG_SUSPENDED,
                                        "⚠️ Your organization has been suspended. Reason: {reason}. Contact support for more information."),
                        Map.entry(FREELANCER_ORG_UNSUSPENDED,
                                        "✅ Your organization suspension has been lifted. You can resume your activities."),
                        Map.entry(FREELANCER_ORG_KYC_REJECTED,
                                        "❌ Your KYC verification has been rejected. Reason: {reason}. Please submit updated documents."),
                        Map.entry(FREELANCER_ORG_MISSION_ASSIGNED,
                                        "📦 Mission assigned to your organization. Reference: {deliveryId}."),
                        Map.entry(SUB_DELIVERER_INVITED,
                                        "📩 You have been invited to join {orgName} as a sub-deliverer. Accept or decline in the app."),
                        Map.entry(SUB_DELIVERER_INVITATION_ACCEPTED,
                                        "✅ {subDelivererName} has accepted your invitation and joined your organization."),
                        Map.entry(SUB_DELIVERER_MISSION_ASSIGNED,
                                        "🏍️ New mission: {deliveryId} has been assigned to you. Prepare for pickup."),
                        Map.entry(BILLING_TEMPLATE_APPLIED,
                                        "💼 Billing policy created from template {templateCode}. Policy ID: {policyId}."),
                        Map.entry(SURCHARGE_TRIGGERED,
                                        "ℹ️ A billing surcharge was applied to your delivery ({deliveryId}): {surchargeName} = {surchargeAmount} XAF."));
}
