package com.yowyob.tiibntick.core.i18n.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * The language of one account. It belongs to the <b>account</b> (the Kernel user), not to a profile: the
 * same person who is a client and a delivery person reads TiiBnTick in one language.
 *
 * @param language a public code of the platform ({@code fr} or {@code en})
 * @param source   {@code USER} when chosen explicitly, {@code DETECTED} when inferred from the browser at
 *                 first login (an explicit choice always replaces a detected one, never the reverse)
 */
public record UserLanguagePreference(UUID userId, String language, String source, Instant updatedAt) {

    public static final String SOURCE_USER = "USER";
    public static final String SOURCE_DETECTED = "DETECTED";
}
