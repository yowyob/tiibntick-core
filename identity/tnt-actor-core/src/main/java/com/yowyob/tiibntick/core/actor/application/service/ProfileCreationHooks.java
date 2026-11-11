package com.yowyob.tiibntick.core.actor.application.service;

import com.yowyob.tiibntick.core.actor.application.port.in.IResolveActorIdentityUseCase;
import com.yowyob.tiibntick.core.actor.application.port.out.IProfileWelcomePort;
import com.yowyob.tiibntick.core.actor.domain.model.ActorIdentitySummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * What happens once a profile has really been created (not when an existing one is returned): the
 * welcome notice and e-mail. Never delays or fails the creation — the welcome runs on its own
 * subscription, keeping the caller's Reactor context (bearer token) so the identity can be read.
 */
@Component
public class ProfileCreationHooks {

    private static final Logger log = LoggerFactory.getLogger(ProfileCreationHooks.class);

    private final ObjectProvider<IProfileWelcomePort> welcomePort;
    private final ObjectProvider<IResolveActorIdentityUseCase> identity;

    public ProfileCreationHooks(ObjectProvider<IProfileWelcomePort> welcomePort,
                                ObjectProvider<IResolveActorIdentityUseCase> identity) {
        this.welcomePort = welcomePort;
        this.identity = identity;
    }

    /** Does nothing: for services built by hand in tests. */
    public static ProfileCreationHooks none() {
        return new ProfileCreationHooks(null, null);
    }

    /** Completes immediately; the welcome is sent in the background. */
    public Mono<Void> profileCreated(IProfileWelcomePort.Kind kind, UUID tenantId, UUID actorId) {
        IProfileWelcomePort port = welcomePort == null ? null : welcomePort.getIfAvailable();
        if (port == null) {
            return Mono.empty();
        }
        return Mono.deferContextual(ctx -> {
            Mono<Void> send = resolveIdentity(actorId)
                    .map(i -> new IProfileWelcomePort.Welcome(tenantId, actorId, kind, i.displayName(), i.email()))
                    .defaultIfEmpty(new IProfileWelcomePort.Welcome(tenantId, actorId, kind, null, null))
                    .flatMap(port::welcome)
                    .onErrorResume(e -> {
                        log.warn("Welcome of the {} profile of actor {} could not be sent: {}", kind, actorId, e.getMessage());
                        return Mono.empty();
                    });
            send.contextWrite(ctx).subscribe(null, e -> log.warn("Welcome failed for actor {}: {}", actorId, e.getMessage()));
            return Mono.empty();
        });
    }

    private Mono<ActorIdentitySummary> resolveIdentity(UUID actorId) {
        IResolveActorIdentityUseCase use = identity == null ? null : identity.getIfAvailable();
        if (use == null) {
            return Mono.empty();
        }
        return use.resolveCurrent().onErrorResume(e -> {
            log.debug("Identity of actor {} not available for the welcome: {}", actorId, e.getMessage());
            return Mono.empty();
        });
    }
}
