package com.yowyob.tiibntick.core.notify.application.service;

import com.yowyob.tiibntick.core.notify.application.port.in.ISendNotificationUseCase;
import com.yowyob.tiibntick.core.notify.application.port.out.IMessageProviderPort;
import com.yowyob.tiibntick.core.notify.application.port.out.IPublishNotificationEventPort;
import com.yowyob.tiibntick.core.notify.application.port.out.INotificationRepositoryPort;
import com.yowyob.tiibntick.core.notify.application.port.out.ITranslationPort;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import com.yowyob.tiibntick.core.notify.domain.model.Notification;
import com.yowyob.tiibntick.core.notify.domain.vo.NotificationId;
import com.yowyob.tiibntick.core.notify.domain.vo.NotificationModel;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Core application service orchestrating the full notification delivery
 * pipeline.
 * Pure service — no Spring annotations. Wired via AutoConfiguration.
 *
 * @author Dilane PAFE
 * @author MANFOUO Braun
 */
public class NotificationService implements ISendNotificationUseCase {

        private final INotificationRepositoryPort repository;
        private final ITranslationPort translationPort;
        private final List<IMessageProviderPort> providers;
        private final IPublishNotificationEventPort eventPort;

        public NotificationService(INotificationRepositoryPort repository,
                        ITranslationPort translationPort,
                        List<IMessageProviderPort> providers,
                        IPublishNotificationEventPort eventPort) {
                this.repository = repository;
                this.translationPort = translationPort;
                this.providers = providers;
                this.eventPort = eventPort;
        }

        @Override
        public Mono<Notification> send(String tenantId,
                        String organizationId,
                        String recipientId,
                        String targetDestination,
                        NotificationModel model,
                        NotificationChannel channel) {
                return translationPort.translate(model)
                                .map(content -> new Notification(
                                                NotificationId.generate(),
                                                tenantId,
                                                organizationId,
                                                recipientId,
                                                channel,
                                                content,
                                                model.priority()))
                                .flatMap(repository::save)
                                .flatMap(notification -> routerVersFournisseur(notification, targetDestination));
        }

        private Mono<Notification> routerVersFournisseur(Notification notification,
                        String targetDestination) {
                IMessageProviderPort provider = providers.stream()
                                .filter(f -> f.supports(notification.getChannel()))
                                .findFirst()
                                .orElse(null);

                if (provider == null) {
                        notification.markAsFailed(
                                        "No provider configured for channel: " + notification.getChannel());
                        return repository.save(notification)
                                        .flatMap(n -> eventPort.publishNotificationFailed(n).thenReturn(n));
                }

                return provider.sendMessage(notification.getChannel(), notification.getTenantId(),
                                notification.getOrganizationId(), targetDestination, notification.getContent())
                                .then(Mono.defer(() -> {
                                        notification.markAsSent();
                                        return repository.save(notification);
                                }))
                                .flatMap(n -> eventPort.publishNotificationSent(n).thenReturn(n))
                                .onErrorResume(e -> {
                                        notification.markAsFailed(e.getMessage());
                                        return repository.save(notification)
                                                        .flatMap(n -> eventPort.publishNotificationFailed(n)
                                                                        .thenReturn(n));
                                });
        }
}
