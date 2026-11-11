package com.yowyob.tiibntick.core.notify.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration of {@code tnt-notify-core} ({@code tnt.notify.*}). TiiBnTick delivers nothing itself:
 * provider credentials (SMTP, SMS, WhatsApp, Firebase) live in the Kernel notification engine, so only
 * the Kernel context and the outcome topics are configured here.
 *
 * @author Dilane PAFE
 * @author MANFOUO Braun
 */
@ConfigurationProperties(prefix = "tnt.notify")
public class NotifyProperties {

    private String kafkaTopicSent = "tnt.notifications.sent";

    private String kafkaTopicFailed = "tnt.notifications.failed";

    private Kernel kernel = new Kernel();

    public Kernel getKernel() {
        return kernel;
    }

    public void setKernel(Kernel v) {
        this.kernel = v;
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

    /** Kernel notification engine context. */
    public static class Kernel {

        /**
         * Tenant used when the caller has none (event consumers, system notifications). Must be a tenant
         * subscribed to the Kernel NOTIFICATION service.
         */
        private String defaultTenantId;

        /** Organization used when the caller has none; the Kernel rejects calls without one. */
        private String defaultOrganizationId;

        /**
         * Send {@code templateCode} + variables so the Kernel renders its registered template, and register
         * the TiiBnTick templates in the Kernel on first use. When false (default) only the body rendered for
         * the recipient, in their own language, is sent.
         */
        private boolean templatesEnabled = false;

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

        public boolean isTemplatesEnabled() {
            return templatesEnabled;
        }

        public void setTemplatesEnabled(boolean v) {
            this.templatesEnabled = v;
        }
    }
}
