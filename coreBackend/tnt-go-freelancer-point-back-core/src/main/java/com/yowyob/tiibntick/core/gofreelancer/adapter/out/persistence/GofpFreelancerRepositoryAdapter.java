package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence;

import com.yowyob.tiibntick.core.actor.application.port.out.IFreelancerRepository;
import com.yowyob.tiibntick.core.actor.domain.model.FreelancerProfile;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryPersonRepository;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryPerson;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.GofpFreelancerR2dbcRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.service.TenantContextHolder;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.freelancer.FreelancerStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound adapter: bridges the domain GofpFreelancerRepository port to the R2DBC repository.
 *
 * <p>Lot L3 de-duplication: the vetting status and the rating of a freelancer are no longer stored in
 * {@code gofp_freelancers}. Every loaded row is completed from the core, keyed by the shared profile id
 * ({@code gofp_freelancers.id == freelancer_profiles.id == tnt_delivery_persons.id}):
 * <ul>
 *   <li>{@code status} ← delivery-core {@code tnt_delivery_persons.status} (PENDING when the row has no
 *       delivery person, i.e. it cannot take deliveries);</li>
 *   <li>{@code rating} ← actor-core freelancer profile {@code ActorRating} ({@code null} without rating).</li>
 * </ul>
 * The tenant is the one of the current request (H12), the system tenant outside a request.
 */
@Component
@RequiredArgsConstructor
public class GofpFreelancerRepositoryAdapter implements GofpFreelancerRepository {

    private final GofpFreelancerR2dbcRepository r2dbcRepository;
    private final DeliveryPersonRepository deliveryPersonRepository;
    private final IFreelancerRepository freelancerProfileRepository;
    private final TenantContextHolder tenantContextHolder;

    @Override public Mono<GofpFreelancer> save(GofpFreelancer f) { return r2dbcRepository.save(f); }
    @Override public Mono<GofpFreelancer> findById(UUID id) { return r2dbcRepository.findById(id).flatMap(this::fromCore); }
    @Override public Mono<GofpFreelancer> findByCoreFreelancerId(UUID id) { return r2dbcRepository.findByCoreFreelancerId(id).flatMap(this::fromCore); }
    @Override public Mono<GofpFreelancer> findByCoreUserId(UUID id) { return r2dbcRepository.findByCoreUserId(id).flatMap(this::fromCore); }
    @Override public Flux<GofpFreelancer> findAllByIsActive(Boolean a) { return r2dbcRepository.findAllByIsActive(a).concatMap(this::fromCore); }
    @Override public Flux<GofpFreelancer> findAll() { return r2dbcRepository.findAll().concatMap(this::fromCore); }
    @Override public Mono<Void> deleteById(UUID id) { return r2dbcRepository.deleteById(id); }

    @Override
    public Flux<GofpFreelancer> findAllByStatus(FreelancerStatus s) {
        return findAll().filter(f -> f.getStatus() == s);
    }

    @Override
    public Flux<GofpFreelancer> findAllByStatusAndIsActive(FreelancerStatus s, Boolean a) {
        return findAllByIsActive(a).filter(f -> f.getStatus() == s);
    }

    /** Completes a loaded row with the status and the rating owned by the core. */
    Mono<GofpFreelancer> fromCore(GofpFreelancer f) {
        return tenantContextHolder.currentTenantId().flatMap(tenantId -> Mono.zip(
                        deliveryPersonRepository.findById(tenantId, f.getId())
                                .map(Optional::of).defaultIfEmpty(Optional.empty()),
                        freelancerProfileRepository.findById(tenantId, f.getId())
                                .map(Optional::of).defaultIfEmpty(Optional.empty()))
                .map(core -> {
                    Optional<DeliveryPerson> dp = core.getT1();
                    Optional<FreelancerProfile> profile = core.getT2();
                    f.setStatus(dp.map(DeliveryPerson::getStatus).map(FreelancerStatus::fromCore)
                            .orElse(FreelancerStatus.PENDING));
                    f.setRating(profile.map(FreelancerProfile::rating)
                            .filter(r -> r.hasRatings()).map(r -> r.score()).orElse(null));
                    return f;
                }));
    }
}
