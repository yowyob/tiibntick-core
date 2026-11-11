package com.yowyob.tiibntick.core.notify.infrastructure.adapter.i18n;

import com.yowyob.tiibntick.core.i18n.application.port.in.ResolveRecipientLanguageUseCase;
import com.yowyob.tiibntick.core.notify.application.port.out.IRecipientLanguagePort;
import reactor.core.publisher.Mono;

/** Reads the recipient's language from the per-account preference held by tnt-i18n-core. */
public class AccountRecipientLanguageAdapter implements IRecipientLanguagePort {

    private final ResolveRecipientLanguageUseCase resolver;

    public AccountRecipientLanguageAdapter(ResolveRecipientLanguageUseCase resolver) {
        this.resolver = resolver;
    }

    @Override
    public Mono<String> languageOf(String recipientId, String hint) {
        return resolver.languageOf(recipientId, hint);
    }
}
