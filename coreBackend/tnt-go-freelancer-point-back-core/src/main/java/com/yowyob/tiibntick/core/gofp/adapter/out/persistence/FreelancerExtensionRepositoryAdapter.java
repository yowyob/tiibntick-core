package com.yowyob.tiibntick.core.gofp.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.FreelancerExtensionEntity;
import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.repository.R2dbcFreelancerExtensionRepository;
import com.yowyob.tiibntick.core.gofp.application.port.out.IFreelancerExtensionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FreelancerExtensionRepositoryAdapter implements IFreelancerExtensionRepository {

    private final R2dbcFreelancerExtensionRepository r2dbc;

    @Override public Mono<FreelancerExtensionEntity> save(FreelancerExtensionEntity e)              { return r2dbc.save(e); }
    @Override public Mono<FreelancerExtensionEntity> findByFreelancerActorId(UUID freelancerActorId) { return r2dbc.findByFreelancerActorId(freelancerActorId); }
}
