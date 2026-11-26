package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.NeedInvitationR2dbcRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.NeedInvitationRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.NeedInvitation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class NeedInvitationRepositoryAdapter implements NeedInvitationRepository {

    private final NeedInvitationR2dbcRepository r2dbc;

    @Override public Mono<NeedInvitation> save(NeedInvitation i) { return r2dbc.save(i); }

    @Override public Mono<NeedInvitation> findById(UUID id) { return r2dbc.findById(id); }

    @Override public Flux<NeedInvitation> findAllByDeliveryNeedId(UUID needId) {
        return r2dbc.findAllByDeliveryNeedIdOrderByCreatedAtDesc(needId);
    }

    @Override public Flux<NeedInvitation> findAllByFreelancerIdAndStatus(UUID freelancerId, String status) {
        return r2dbc.findAllByFreelancerIdAndStatus(freelancerId, status);
    }

    @Override public Flux<NeedInvitation> findAllByStatus(String status) {
        return r2dbc.findAllByStatus(status);
    }
}
