package com.yowyob.tiibntick.core.notify.application.port.out;

import com.yowyob.tiibntick.core.notify.domain.vo.NotificationModel;
import reactor.core.publisher.Mono;

/**
 * Port isolating tnt-notify-core from yow-i18n-kernel dependency details.
 *
 * @author Dilane PAFE
 * @author MANFOUO Braun
 */
public interface ITranslationPort {

    /**
     * Translates a notification template into its final displayable string.
     *
     * @param model the template with key, locale, and interpolation parameters
     * @return the ready-to-send translated message
     */
    Mono<String> translate(NotificationModel model);

    /**
     * Returns the untranslated pattern of a template key (its {@code {{variable}}} placeholders left
     * intact) for the given language, falling back to the platform default language. Used to register
     * the TiiBnTick templates in the Kernel notification engine.
     *
     * @return the pattern, or empty when the key exists in no language pack
     */
    Mono<String> rawTemplate(String templateKey, String language);
}
