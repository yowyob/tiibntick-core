package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.actor.application.port.out.IFreelancerRepository;
import com.yowyob.tiibntick.core.actor.domain.model.FreelancerProfile;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FreelancerOwnershipGuardTest {

    private final GofpFreelancerRepository gofp = mock(GofpFreelancerRepository.class);
    private final IFreelancerRepository actors = mock(IFreelancerRepository.class);
    private final FreelancerOwnershipGuard guard = new FreelancerOwnershipGuard(gofp, actors);
    private final UUID profileId = UUID.randomUUID();
    private final UUID caller = UUID.randomUUID();

    private FreelancerProfile profile(UUID id, UUID actorId) {
        FreelancerProfile p = mock(FreelancerProfile.class);
        when(p.id()).thenReturn(id);
        when(p.actorId()).thenReturn(actorId);
        return p;
    }

    @Test
    void ownerPassesThroughTheProjectionRow() {
        GofpFreelancer row = GofpFreelancer.builder().id(profileId).coreFreelancerId(profileId).coreUserId(caller).build();
        when(gofp.findByCoreFreelancerId(profileId)).thenReturn(Mono.just(row));
        StepVerifier.create(guard.check(profileId, caller, false)).verifyComplete();
    }

    @Test
    void ownerWithoutProjectionRowPassesThroughTheirActorProfile() {
        when(gofp.findByCoreFreelancerId(profileId)).thenReturn(Mono.empty());
        when(gofp.findById(profileId)).thenReturn(Mono.empty());
        FreelancerProfile own = profile(profileId, caller);
        when(actors.findFirstByActorId(caller)).thenReturn(Mono.just(own));
        StepVerifier.create(guard.check(profileId, caller, false)).verifyComplete();
    }

    @Test
    void anotherFreelancersIdIsStillForbidden() {
        when(gofp.findByCoreFreelancerId(profileId)).thenReturn(Mono.empty());
        when(gofp.findById(profileId)).thenReturn(Mono.empty());
        FreelancerProfile other = profile(UUID.randomUUID(), caller);
        when(actors.findFirstByActorId(caller)).thenReturn(Mono.just(other));
        StepVerifier.create(guard.check(profileId, caller, false)).expectError(AccessDeniedException.class).verify();
    }

    @Test
    void aCallerWithoutAnyProfileIsForbidden() {
        when(gofp.findByCoreFreelancerId(profileId)).thenReturn(Mono.empty());
        when(gofp.findById(profileId)).thenReturn(Mono.empty());
        when(actors.findFirstByActorId(caller)).thenReturn(Mono.empty());
        StepVerifier.create(guard.check(profileId, caller, false)).expectError(AccessDeniedException.class).verify();
    }

    @Test
    void adminAndMissingIdentityRules() {
        StepVerifier.create(guard.check(profileId, null, true)).verifyComplete();
        StepVerifier.create(guard.check(profileId, null, false)).expectError(AccessDeniedException.class).verify();
    }
}
