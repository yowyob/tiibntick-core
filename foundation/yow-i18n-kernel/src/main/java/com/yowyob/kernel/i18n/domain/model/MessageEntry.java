package com.yowyob.kernel.i18n.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * One catalog row: the text of {@code namespace.key} in one language and channel variant.
 *
 * @param text      the <b>live</b> text, {@code null} while the entry has never been published
 * @param draftText an edit waiting for publication, {@code null} when there is none
 * @param version   incremented on every publication (0 = never published)
 */
public record MessageEntry(
        UUID id,
        String namespace,
        String key,
        String language,
        String variant,
        String text,
        String draftText,
        int version,
        Instant updatedAt,
        String updatedBy) {

    public boolean isLive() {
        return text != null;
    }

    public boolean hasDraft() {
        return draftText != null;
    }
}
