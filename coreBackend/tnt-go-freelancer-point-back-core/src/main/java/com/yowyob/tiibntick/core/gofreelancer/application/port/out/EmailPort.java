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
}
