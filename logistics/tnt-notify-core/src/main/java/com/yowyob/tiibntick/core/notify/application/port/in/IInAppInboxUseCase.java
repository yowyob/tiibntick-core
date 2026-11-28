package com.yowyob.tiibntick.core.notify.application.port.in;

import com.yowyob.tiibntick.core.notify.domain.model.InAppMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * The users' in-app inbox, held by the Kernel (decision D-157): a module writes a message for a user, and the
 * caller reads and marks <b>their own</b> inbox (the Kernel resolves the user from the request's token).
 */
public interface IInAppInboxUseCase {

    /** Writes one message in {@code recipientUserId}'s inbox, tagged with {@code application}. */
    Mono<InAppMessage> send(UUID recipientUserId, String application, String title, String body,
                            Map<String, String> metadata);

    /**
     * Writes one message rendered from the catalog in the <b>recipient's own language</b> (the per-account
     * preference; {@code languageHint} only when the account has none): the {@code IN_APP} variant of
     * {@code templateKey} as body, its {@code EMAIL_SUBJECT} variant as title.
     */
    Mono<InAppMessage> sendLocalized(UUID recipientUserId, String application, String templateKey,
                                     Map<String, Object> params, String languageHint, Map<String, String> metadata);

    /** The calling user's inbox for this application (null = every application). */
    Flux<InAppMessage> mine(String application);

    /** Marks one message of the calling user's inbox as read. */
    Mono<InAppMessage> markRead(UUID messageId);
}
