package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.NeedInvitation;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface NeedInvitationR2dbcRepository extends ReactiveCrudRepository<NeedInvitation, UUID> {

    Flux<NeedInvitation> findAllByDeliveryNeedIdOrderByCreatedAtDesc(UUID deliveryNeedId);

    Flux<NeedInvitation> findAllByFreelancerIdAndStatus(UUID freelancerId, String status);

    Flux<NeedInvitation> findAllByStatus(String status);
}
