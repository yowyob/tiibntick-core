package com.yowyob.tiibntick.core.trust.adapter.out.actor;

import com.yowyob.tiibntick.core.actor.application.port.out.ActorDidAnchorPayload;
import com.yowyob.tiibntick.core.actor.application.port.out.IActorDidAnchorPort;
import com.yowyob.tiibntick.core.trust.application.port.in.IssueDIDUseCase;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.DIDDocument;
import com.yowyob.tiibntick.core.trust.domain.service.DidKeyPairGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * tnt-trust-core implementation of {@link IActorDidAnchorPort} (outbound port
 * owned by tnt-actor-core).
 *
 * <p>tnt-trust-core depends on tnt-actor-core (one-directional, no Maven cycle —
 * actor-core never depends back on trust) purely to see this port and its payload
 * type; it delegates to the pre-existing {@link IssueDIDUseCase#issue}.
 *
 * <p>tnt-actor-core owns no PKI material for its profiles, so this adapter generates
 * the ECDSA P-256 keypair the DID is anchored with via {@link DidKeyPairGenerator} —
 * DID key custody is a trust-domain concern, not an actor-profile one.
 *
 * @author MANFOUO Braun
 * @see IActorDidAnchorPort
 */
@Component
@RequiredArgsConstructor
public class ActorDidAnchorAdapter implements IActorDidAnchorPort {

    private final IssueDIDUseCase issueDIDUseCase;

    @Override
    public Mono<String> issueDid(ActorDidAnchorPayload payload) {
        return issueDIDUseCase.issue(
                        payload.actorId().toString(), payload.tenantId().toString(),
                        DidKeyPairGenerator.generatePublicKeyPem())
                .map(DIDDocument::getDid);
    }
}
