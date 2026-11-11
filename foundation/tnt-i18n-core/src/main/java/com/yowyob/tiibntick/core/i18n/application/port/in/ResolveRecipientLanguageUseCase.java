package com.yowyob.tiibntick.core.i18n.application.port.in;

import reactor.core.publisher.Mono;

/**
 * Answers "in which language must this person read this message?". Used by every module that produces
 * text for someone other than the caller: notifications, PDF documents, system lines of a chat or
 * dispute timeline, error messages.
 */
public interface ResolveRecipientLanguageUseCase {

    /**
     * The recipient's own language when they have chosen or been assigned one; otherwise {@code hint}
     * (e.g. the language of the request being served) when it is supported; otherwise the platform
     * default. Never empty, never errors: a lookup failure degrades to the hint/default.
     *
     * @param recipientId the Kernel user id as text; anything that is not a UUID is treated as unknown
     */
    Mono<String> languageOf(String recipientId, String hint);
}
