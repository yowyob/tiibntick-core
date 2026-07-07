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
}
