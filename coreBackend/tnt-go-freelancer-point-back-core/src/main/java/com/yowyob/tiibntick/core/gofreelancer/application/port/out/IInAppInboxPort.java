package com.yowyob.tiibntick.core.gofreelancer.application.port.out;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.InAppNotification;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * Lot C-36 — the users' in-app inbox, held by the Kernel through tnt-notify-core (decision D-157): no notification
 * table in this module.
 */
public interface IInAppInboxPort {

    /** Application tag of the Go-Freelancer messages in the Kernel inbox. */
    String APPLICATION = "tiibntick-go";

    /** Metadata keys of a Go message in the Kernel inbox. */
    String META_TYPE = "type";
    String META_EVENT_KEY = "eventKey";
    String META_LINK_KIND = "linkKind";
    String META_LINK_ID = "linkId";
    String META_PARAMS = "params";

    /** Writes one message in {@code recipientUserId}'s inbox; {@code metadata} travels with it. */
    Mono<Void> send(UUID recipientUserId, String title, String message, Map<String, String> metadata);

    /** The calling user's Go-Freelancer messages (the Kernel resolves the user from the request's token). */
    Flux<InAppNotification> mine();

    /** Marks one message of the calling user's inbox as read. */
    Mono<InAppNotification> markRead(UUID notificationId);
}
