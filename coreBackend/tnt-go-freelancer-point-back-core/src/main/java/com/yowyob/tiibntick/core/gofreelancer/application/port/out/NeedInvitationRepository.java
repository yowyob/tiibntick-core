package com.yowyob.tiibntick.core.gofreelancer.application.port.out;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.NeedInvitation;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Outbound port for {@link NeedInvitation} persistence. */
public interface NeedInvitationRepository {

    Mono<NeedInvitation> save(NeedInvitation invitation);

    Mono<NeedInvitation> findById(UUID id);

    Flux<NeedInvitation> findAllByDeliveryNeedId(UUID deliveryNeedId);

    Flux<NeedInvitation> findAllByFreelancerIdAndStatus(UUID freelancerId, String status);
}
