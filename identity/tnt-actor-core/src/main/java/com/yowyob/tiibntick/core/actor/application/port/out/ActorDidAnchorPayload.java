package com.yowyob.tiibntick.core.actor.application.port.out;

import java.util.UUID;

/**
 * Actor-owned payload for {@link IActorDidAnchorPort#issueDid}.
 *
 * <p>Deliberately independent from any {@code tnt-trust-core} domain type — the
 * implementing adapter (in {@code tnt-trust-core}) maps this into its own
 * {@code DIDDocument}, keeping the hexagonal boundary between the two modules.
 *
 * @author MANFOUO Braun
 */
public record ActorDidAnchorPayload(
        UUID tenantId,
        UUID actorId) {
}
