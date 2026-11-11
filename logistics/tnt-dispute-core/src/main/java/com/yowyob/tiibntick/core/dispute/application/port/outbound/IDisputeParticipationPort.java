package com.yowyob.tiibntick.core.dispute.application.port.outbound;

import reactor.core.publisher.Mono;

/**
 * Secondary port answering "did this actor take part in this mission?" so that a dispute can only be
 * opened by someone who was actually involved (the sender/client, the deliverer, or the deliverer's
 * organisation). It is implemented by the layer that knows the delivery; a mission it does not know is
 * not judged and is accepted, so missions of other products are never blocked by it.
 *
 * @author MANFOUO Braun
 */
public interface IDisputeParticipationPort {

    /**
     * @param tenantId       tenant scope
     * @param missionId      mission / delivery identifier carried by the dispute (may be blank)
     * @param actorId        the actor who wants to open the dispute
     * @param organizationId organisation of the actor, or {@code null}
     * @return {@code true} when the actor took part in the mission, or when the mission is not known here
     */
    Mono<Boolean> isParticipant(String tenantId, String missionId, String actorId, String organizationId);
}
