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
