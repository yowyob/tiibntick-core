package com.yowyob.tiibntick.core.actor.application.port.out;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Tells the account that a TiiBnTick profile has just been created: a notice on screen and an e-mail,
 * both written in the account language. Owned by tnt-actor-core (L2); the notification module (L3)
 * provides the implementation, so actor-core never depends on it.
 */
public interface IProfileWelcomePort {

    enum Kind {
        CLIENT("client"),
        DELIVERER("deliverer"),
        FREELANCER("freelancer"),
        RELAY_OPERATOR("relay_operator");

        private final String key;

        Kind(String key) {
            this.key = key;
        }

        /** Suffix of the message keys {@code notify.profile.welcome.<key>} / {@code notify.profile.created.<key>}. */
        public String key() {
            return key;
        }
    }

    /**
     * @param displayName how to greet the person, may be {@code null}
     * @param email       where to send the e-mail, {@code null} when unknown (the e-mail is then skipped)
     */
    record Welcome(UUID tenantId, UUID actorId, Kind kind, String displayName, String email) {
    }

    Mono<Void> welcome(Welcome welcome);
}
