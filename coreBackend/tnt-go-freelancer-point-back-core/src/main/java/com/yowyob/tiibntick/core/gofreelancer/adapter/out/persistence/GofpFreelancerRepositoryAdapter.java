package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.GofpFreelancerR2dbcRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.freelancer.FreelancerStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.GofpFreelancerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound adapter: bridges the domain GofpFreelancerRepository port to the R2DBC repository.
 */
@Component
@RequiredArgsConstructor
public class GofpFreelancerRepositoryAdapter implements GofpFreelancerRepository {

    private final GofpFreelancerR2dbcRepository r2dbcRepository;

    @Override public Mono<GofpFreelancer> save(GofpFreelancer f) { return r2dbcRepository.save(f); }

    @Override public Mono<GofpFreelancer> findById(UUID id) { return r2dbcRepository.findById(id); }

    @Override public Mono<GofpFreelancer> findByCoreFreelancerId(UUID id) { return r2dbcRepository.findByCoreFreelancerId(id); }

    @Override public Mono<GofpFreelancer> findByCoreUserId(UUID id) { return r2dbcRepository.findByCoreUserId(id); }

    @Override public Flux<GofpFreelancer> findAllByStatus(FreelancerStatus s) { return r2dbcRepository.findAllByStatus(s); }

    @Override public Flux<GofpFreelancer> findAllByIsActive(Boolean a) { return r2dbcRepository.findAllByIsActive(a); }

    @Override public Flux<GofpFreelancer> findAllByStatusAndIsActive(FreelancerStatus s, Boolean a) { return r2dbcRepository.findAllByStatusAndIsActive(s, a); }

    @Override public Flux<GofpFreelancer> findAll() { return r2dbcRepository.findAll(); }

    @Override public Mono<Void> deleteById(UUID id) { return r2dbcRepository.deleteById(id); }
}
