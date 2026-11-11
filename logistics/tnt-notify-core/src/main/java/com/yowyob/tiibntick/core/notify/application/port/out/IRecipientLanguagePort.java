package com.yowyob.tiibntick.core.notify.application.port.out;

import reactor.core.publisher.Mono;

/**
 * Which language must this recipient read? The answer is the recipient's own account language; the
 * language a caller put on the notification is only a hint, used when the recipient has none yet (or is
 * not a TiiBnTick account, e.g. a bare e-mail address).
 */
public interface IRecipientLanguagePort {

    /** Never empty and never an error: degrades to {@code hint}, then to the platform default. */
    Mono<String> languageOf(String recipientId, String hint);
}
