package com.yowyob.tiibntick.core.gofreelancer.application.port.out;

import reactor.core.publisher.Mono;

/**
 * Outbound port for email notification operations.
 * Decouples the application layer from the SMTP/mail infrastructure.
 */
public interface EmailPort {

    void sendRegistrationReceived(String to);

    void sendAccountApproved(String to, String loginUrl);

    void sendAccountRejected(String to, String reason, String loginUrl);

    void sendAccountSuspended(String to, String loginUrl);

    void sendAccountRevoked(String to, String loginUrl);

    void sendDeliveryAssigned(String to, String announcementTitle);

    Mono<Void> sendSimpleMessageReactive(String to, String subject, String text);

    /**
     * An e-mail written once per language in the catalog (subject and body variants), rendered with
     * {@code params} in the language of the recipient's account when {@code recipientUserId} is known, in the
     * platform default language otherwise.
     */
    Mono<Void> sendLocalized(String to, java.util.UUID recipientUserId, String templateKey,
                             java.util.Map<String, Object> params);
}
