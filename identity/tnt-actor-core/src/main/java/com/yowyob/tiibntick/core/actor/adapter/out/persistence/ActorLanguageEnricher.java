package com.yowyob.tiibntick.core.actor.adapter.out.persistence;

import com.yowyob.tiibntick.core.actor.domain.model.TntActorProfile;
import com.yowyob.tiibntick.core.i18n.application.port.in.ResolveRecipientLanguageUseCase;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Puts the account language on every profile that is loaded, so a client, a freelancer, a deliverer
 * and a relay operator profile of the same account always show the same language. The value is read from
 * the single per-account preference (cached in tnt-i18n-core); it is never copied into the profile tables.
 *
 * <p>Without tnt-i18n-core wiring (unit slices) profiles are returned untouched and read as the default.
 */
@Component
public class ActorLanguageEnricher {

    private final ObjectProvider<ResolveRecipientLanguageUseCase> resolver;

    public ActorLanguageEnricher(ObjectProvider<ResolveRecipientLanguageUseCase> resolver) {
        this.resolver = resolver;
    }

    /** Pass-through instance for adapters built by hand (tests). */
    public static ActorLanguageEnricher none() {
        return new ActorLanguageEnricher(null);
    }

    public <P extends TntActorProfile> Mono<P> enrich(Mono<P> profile) {
        return profile.flatMap(this::attach);
    }

    public <P extends TntActorProfile> Flux<P> enrich(Flux<P> profiles) {
        return profiles.concatMap(this::attach);
    }

    private <P extends TntActorProfile> Mono<P> attach(P profile) {
        ResolveRecipientLanguageUseCase use = resolver == null ? null : resolver.getIfAvailable();
        if (use == null) {
            return Mono.just(profile);
        }
        return use.languageOf(profile.actorId().toString(), null)
                .map(language -> {
                    profile.attachPreferredLanguage(language);
                    return profile;
                })
                .defaultIfEmpty(profile);
    }
}
