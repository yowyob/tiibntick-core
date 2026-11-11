package com.yowyob.tiibntick.core.notify.application.port.out;

import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
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
     * Same, in the flavour the channel needs (a short SMS, a push text, an e-mail body...). The default
     * ignores the channel so existing implementations keep working.
     */
    default Mono<String> translate(NotificationModel model, NotificationChannel channel) {
        return translate(model);
    }

    /** Subject line of the message on this channel (e-mail), empty when the channel has none or none is written. */
    default Mono<String> translateSubject(NotificationModel model, NotificationChannel channel) {
        return Mono.empty();
    }

    /**
     * Returns the untranslated pattern of a template key (its {@code {{variable}}} placeholders left
     * intact) for the given language, falling back to the platform default language. Used to register
     * the TiiBnTick templates in the Kernel notification engine.
     *
     * @return the pattern, or empty when the key exists in no language pack
     */
    Mono<String> rawTemplate(String templateKey, String language);
}
