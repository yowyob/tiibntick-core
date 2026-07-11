package com.yowyob.tiibntick.bootstrap.config.trustnoop;

import com.yowyob.tiibntick.core.actor.application.port.out.ActorDidAnchorPayload;
import com.yowyob.tiibntick.core.actor.application.port.out.IActorDidAnchorPort;
import reactor.core.publisher.Mono;

/**
 * No-op fallback for {@link IActorDidAnchorPort}, wired only when
 * {@code tnt.trust.enabled=false} — see {@code TrustNoOpFallbackConfig}.
 *
 * @author MANFOUO Braun
 */
public class NoOpActorDidAnchorPort implements IActorDidAnchorPort {

    @Override
    public Mono<String> issueDid(final ActorDidAnchorPayload payload) {
        return Mono.empty();
    }
}
