package com.yowyob.tiibntick.core.notify.application.service;

import com.yowyob.tiibntick.core.notify.application.port.in.IInAppInboxUseCase;
import com.yowyob.tiibntick.core.notify.application.port.out.IInAppInboxPort;
import com.yowyob.tiibntick.core.notify.domain.model.InAppMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * In-app inbox use case (decision D-157). A message needs a recipient and a non-blank body (the Kernel refuses an
 * empty one); the title defaults to the body.
 */
public class InAppInboxService implements IInAppInboxUseCase {

    private final IInAppInboxPort inbox;

    public InAppInboxService(IInAppInboxPort inbox) {
        this.inbox = Objects.requireNonNull(inbox);
    }

    @Override
    public Mono<InAppMessage> send(UUID recipientUserId, String application, String title, String body,
                                   Map<String, String> metadata) {
        if (recipientUserId == null) {
            return Mono.error(new IllegalArgumentException("An in-app message needs a recipient"));
        }
        if (body == null || body.isBlank()) {
            return Mono.error(new IllegalArgumentException("An in-app message needs a body"));
        }
        String subject = title == null || title.isBlank() ? body : title;
        return inbox.send(recipientUserId, application, subject, body, metadata);
    }

    private com.yowyob.tiibntick.core.notify.application.port.out.ITranslationPort translations;
    private com.yowyob.tiibntick.core.notify.application.port.out.IRecipientLanguagePort languages;

    /** Wired by the auto-configuration; without them {@link #sendLocalized} fails explicitly. */
    public void setLocalization(com.yowyob.tiibntick.core.notify.application.port.out.ITranslationPort translations,
                                com.yowyob.tiibntick.core.notify.application.port.out.IRecipientLanguagePort languages) {
        this.translations = translations;
        this.languages = languages;
    }

    @Override
    public Mono<InAppMessage> sendLocalized(UUID recipientUserId, String application, String templateKey,
                                            Map<String, Object> params, String languageHint,
                                            Map<String, String> metadata) {
        if (recipientUserId == null || templateKey == null) {
            return Mono.error(new IllegalArgumentException("A localized in-app message needs a recipient and a key"));
        }
        if (translations == null || languages == null) {
            return Mono.error(new IllegalStateException("In-app localization is not wired"));
        }
        return languages.languageOf(recipientUserId.toString(), languageHint)
                .defaultIfEmpty(languageHint != null ? languageHint : "fr")
                .flatMap(language -> {
                    var model = com.yowyob.tiibntick.core.notify.domain.vo.NotificationModel.of(templateKey, language,
                            params == null ? Map.of() : params);
                    var inApp = com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel.IN_APP_WEBSOCKET;
                    var email = com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel.EMAIL;
                    Map<String, String> meta = new java.util.LinkedHashMap<>(metadata == null ? Map.of() : metadata);
                    meta.put("templateKey", templateKey);
                    meta.put("language", language);
                    return translations.translate(model, inApp).flatMap(body ->
                            translations.translateSubject(model, email).defaultIfEmpty(body)
                                    .flatMap(title -> send(recipientUserId, application, title, body, meta)));
                });
    }

    @Override
    public Flux<InAppMessage> mine(String application) {
        return inbox.mine(application);
    }

    @Override
    public Mono<InAppMessage> markRead(UUID messageId) {
        if (messageId == null) {
            return Mono.error(new IllegalArgumentException("messageId is required"));
        }
        return inbox.markRead(messageId);
    }
}
