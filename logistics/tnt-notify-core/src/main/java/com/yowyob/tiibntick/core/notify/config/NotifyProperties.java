package com.yowyob.tiibntick.core.notify.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the tnt-notify-core module.
 * Bound under the "tnt.notify" prefix in application.yaml.
 *
 * @author MANFOUO Braun
 */
@ConfigurationProperties(prefix = "tnt.notify")
public class NotifyProperties {

    /**
     * FCM server key for push notification delivery.
     */
    private String fcmServerKey = "";

    /**
     * MTN/Orange SMS API base URL.
     */
    private String smsApiBaseUrl = "https://api.sms.local/v1";

    /**
     * SMS API authentication token.
     */
    private String smsApiToken = "";

    /**
     * WhatsApp Business API base URL.
     */
    private String whatsappApiBaseUrl = "https://graph.facebook.com/v19.0";

    /**
     * WhatsApp Business access token.
     */
    private String whatsappAccessToken = "";

    /**
     * WhatsApp Business phone number ID.
     */
    private String whatsappPhoneNumberId = "";

    /**
     * SMTP email sender address.
     */
    private String emailFrom = "noreply@tiibntick.com";

    /**
     * Maximum number of delivery retry attempts.
     */
    private int maxRetries = 3;

    /**
     * Kafka topic for sent notification events.
     */
    private String kafkaTopicSent = "tnt.notifications.sent";

    /**
     * Kafka topic for failed notification events.
     */
    private String kafkaTopicFailed = "tnt.notifications.failed";

    /**
     * Kernel (RT-comops) notification engine bridge settings.
     */
    private Kernel kernel = new Kernel();

    public Kernel getKernel() {
        return kernel;
    }

    public void setKernel(Kernel v) {
        this.kernel = v;
    }

    /**
     * Toggles delegation of notification delivery and preferences to the
     * Kernel's generic notification engine ({@code /api/notifications/*}).
     * Defaults to enabled — set to {@code false} only to fall back to the
     * direct-vendor adapters (SMTP/MTN-Orange/Meta/FCM) and local preference
     * persistence, e.g. if the Kernel notification module is unavailable.
     */
    public static class Kernel {
        private boolean enabled = true;

        /**
         * Fallback tenantId used when a caller has no HTTP request context to read
         * X-Tenant-Id from — currently the Kafka-triggered
         * {@code FreelancerOrgKafkaEventConsumer} and {@code IncidentNotificationPortAdapter}.
         * The Kernel notification engine rejects calls with no tenant context at all,
         * so one of {defaultTenantId, or a tenantId embedded in the event payload}
         * must be present. Should be set to a tenant that is actually subscribed to
         * the Kernel's NOTIFICATION service.
         */
        private String defaultTenantId;

        /**
         * Fallback organizationId paired with {@link #defaultTenantId}. Optional —
         * some Kernel deployments may allow a tenant-wide (no org) scope.
         */
        private String defaultOrganizationId;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean v) {
            this.enabled = v;
        }

        public String getDefaultTenantId() {
            return defaultTenantId;
        }

        public void setDefaultTenantId(String v) {
            this.defaultTenantId = v;
        }

        public String getDefaultOrganizationId() {
            return defaultOrganizationId;
        }

        public void setDefaultOrganizationId(String v) {
            this.defaultOrganizationId = v;
        }
    }

    public String getFcmServerKey() {
        return fcmServerKey;
    }

    public void setFcmServerKey(String v) {
        this.fcmServerKey = v;
    }

    public String getSmsApiBaseUrl() {
        return smsApiBaseUrl;
    }

    public void setSmsApiBaseUrl(String v) {
        this.smsApiBaseUrl = v;
    }

    public String getSmsApiToken() {
        return smsApiToken;
    }

    public void setSmsApiToken(String v) {
        this.smsApiToken = v;
    }

    public String getWhatsappApiBaseUrl() {
        return whatsappApiBaseUrl;
    }

    public void setWhatsappApiBaseUrl(String v) {
        this.whatsappApiBaseUrl = v;
    }

    public String getWhatsappAccessToken() {
        return whatsappAccessToken;
    }

    public void setWhatsappAccessToken(String v) {
        this.whatsappAccessToken = v;
    }

    public String getWhatsappPhoneNumberId() {
        return whatsappPhoneNumberId;
    }

    public void setWhatsappPhoneNumberId(String v) {
        this.whatsappPhoneNumberId = v;
    }

    public String getEmailFrom() {
        return emailFrom;
    }

    public void setEmailFrom(String v) {
        this.emailFrom = v;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int v) {
        this.maxRetries = v;
    }

    public String getKafkaTopicSent() {
        return kafkaTopicSent;
    }

    public void setKafkaTopicSent(String v) {
        this.kafkaTopicSent = v;
    }

    public String getKafkaTopicFailed() {
        return kafkaTopicFailed;
    }

    public void setKafkaTopicFailed(String v) {
        this.kafkaTopicFailed = v;
    }
}
