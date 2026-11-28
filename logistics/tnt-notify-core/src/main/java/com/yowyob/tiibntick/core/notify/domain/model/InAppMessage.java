package com.yowyob.tiibntick.core.notify.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One message of a user's in-app inbox, held by the Kernel (channel {@code IN_APP}, decision D-157). The Core keeps
 * no copy: {@code id} is the Kernel delivery id, {@code metadata} carries what the calling module needs to render
 * and link the message (type, event key, link, parameters).
 *
 * @param readAt null while unread
 */
public record InAppMessage(UUID id, UUID recipientUserId, String title, String body, Map<String, String> metadata,
                           Instant createdAt, Instant readAt) {

    public InAppMessage {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public String meta(String key) {
        return metadata.get(key);
    }
}
