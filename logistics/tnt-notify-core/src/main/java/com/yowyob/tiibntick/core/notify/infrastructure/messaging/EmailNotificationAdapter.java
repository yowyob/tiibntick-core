package com.yowyob.tiibntick.core.notify.infrastructure.messaging;

import com.yowyob.tiibntick.core.notify.application.port.out.IMessageProviderPort;
import com.yowyob.tiibntick.core.notify.config.NotifyProperties;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Email notification adapter using Spring Mail (JavaMailSender).
 * Wraps the blocking send in a bounded-elastic scheduler for non-blocking
 * integration.
 *
 * <p>Only registered when a {@link JavaMailSender} bean is configured
 * (e.g. {@code spring-boot-starter-mail} + {@code spring.mail.*} properties).
 * Without it, {@link com.yowyob.tiibntick.core.notify.application.service.NotificationService}
 * simply receives one fewer {@code IMessageProviderPort} in its injected list.
 *
 * @author MANFOUO Braun
 */
@Component
@ConditionalOnBean(JavaMailSender.class)
public class EmailNotificationAdapter implements IMessageProviderPort {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationAdapter.class);

    private final JavaMailSender mailSender;
    private final NotifyProperties properties;

    public EmailNotificationAdapter(JavaMailSender mailSender, NotifyProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public boolean supports(NotificationChannel channel) {
        return NotificationChannel.EMAIL.equals(channel);
    }

    @Override
    public Mono<Void> sendMessage(String emailDestinataire, String content) {
        log.info("Sending email to {}", emailDestinataire);
        return Mono.fromRunnable(() -> {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(properties.getEmailFrom());
            message.setTo(emailDestinataire);
            message.setSubject("TiiBnTick — Notification");
            message.setText(content);
            mailSender.send(message);
        })
                .subscribeOn(Schedulers.boundedElastic())
                .then()
                .doOnSuccess(v -> log.debug("Email delivered to {}", emailDestinataire))
                .onErrorResume(e -> {
                    log.error("Email delivery failed to {}: {}", emailDestinataire, e.getMessage());
                    return Mono.error(e);
                });
    }
}
