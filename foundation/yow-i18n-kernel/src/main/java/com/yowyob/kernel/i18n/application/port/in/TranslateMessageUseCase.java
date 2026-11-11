package com.yowyob.kernel.i18n.application.port.in;

import java.util.Map;
import java.util.Optional;

/**
 * Primary port for translating message keys into the appropriate locale.
 * All TiiBnTick modules that produce user-facing messages must go through this port.
 *
 * @author Dilane PAFE
 * @author MANFOUO Braun
 */
public interface TranslateMessageUseCase {

    /**
     * Translates a message key to the target language with no variable interpolation.
     *
     * @param key    the message template key (e.g., "notification.package.delivered")
     * @param language the BCP-47-like locale tag (e.g., "fr_CM", "pidgin_CM")
     * @return the translated string, or empty if the key is not found
     */
    Optional<String> translate(String key, String language);

    /**
     * Translates a message key and interpolates template variables.
     *
     * @param key        the message template key
     * @param language     the locale tag
     * @param parameters map of variable names to their values for {{variable}} substitution
     * @return the translated and interpolated string, or empty if the key is not found
     */
    Optional<String> translate(String key, String language, Map<String, Object> parameters);

    /**
     * Channel-aware variant: tries {@code variant} first in each language of the fallback chain and
     * falls back to the plain text of that language. Parameters, plural and typed formats are applied
     * in the requested language.
     */
    Optional<String> translate(String key, String language, Map<String, Object> parameters, String variant);
}
