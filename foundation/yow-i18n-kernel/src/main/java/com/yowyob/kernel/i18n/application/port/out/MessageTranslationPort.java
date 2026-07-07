package com.yowyob.kernel.i18n.application.port.out;

import com.yowyob.kernel.i18n.domain.vo.LocalizedMessage;

import java.util.Optional;

/**
 * Secondary port (driven) for loading localized messages from an external store.
 * Implementations may load from JSON files, a database, or a remote service.
 *
 * @author Dilane PAFE
 * @author MANFOUO Braun
 */
public interface MessageTranslationPort {

    /**
     * Retrieves a localized message by its key and locale tag.
     *
     * @param key    the message template key
     * @param language the locale tag (e.g., "fr_CM", "pidgin_CM")
     * @return the localized message, or empty if not found
     */
    Optional<LocalizedMessage> findByKeyAndLanguage(String key, String language);
}
