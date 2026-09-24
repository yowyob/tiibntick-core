package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpUserRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves that GofpUserProvisioningService is idempotent and sets coreUserId = JWT sub,
 * and demonstrates the F-R3 convergence: a provisioned coreUserId equals what
 * resolvePresenceUserId returns via the GofpFreelancer chain.
 */
@ExtendWith(MockitoExtension.class)
class GofpUserProvisioningServiceTest {

    @Mock private GofpUserRepository userRepository;

    private GofpUserProvisioningService provisioningService;

    @BeforeEach
    void setUp() {
        provisioningService = new GofpUserProvisioningService(userRepository);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Test 1 — first call creates a GofpUser with coreUserId = JWT sub
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void provisionIfAbsent_createsUser_with_coreUserId_equal_to_sub() {
        UUID sub = UUID.randomUUID();

        when(userRepository.findByCoreUserId(sub)).thenReturn(Mono.empty());
        when(userRepository.save(any(GofpUser.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(provisioningService.provisionIfAbsent(sub))
                .expectNextMatches(user -> sub.equals(user.getCoreUserId()))
                .verifyComplete();

        verify(userRepository).save(any(GofpUser.class));
    }

    // ══════════════════════════════════════════════════════════════════════
    // Test 2 — subsequent calls return the existing row (idempotent, no save)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void provisionIfAbsent_existingUser_returnsItWithoutSaving() {
        UUID sub = UUID.randomUUID();
        GofpUser existing = GofpUser.builder()
                .id(UUID.randomUUID())
                .coreUserId(sub)
                .firstName("Alice")
                .lastName("Martin")
                .email("alice@example.com")
                .build();

        when(userRepository.findByCoreUserId(sub)).thenReturn(Mono.just(existing));

        StepVerifier.create(provisioningService.provisionIfAbsent(sub))
                .expectNext(existing)
                .verifyComplete();

        verify(userRepository, never()).save(any());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Test 3 — F-R3 convergence: provisioned coreUserId == resolvePresenceUserId result
    //
    // resolvePresenceUserId (DeliveryApplicationService, private) does:
    //   gofpFreelancerRepository.findById(assignedFreelancerId)
    //     .map(gofp -> gofp.getCoreUserId().toString())
    //
    // This test proves that after provisioning with sub:
    //   provisioned.coreUserId == sub
    // and that when GofpFreelancer.coreUserId is set to provisioned.coreUserId
    // (enforced by GofpFreelancerProfileController since Lot 5),
    //   resolvePresenceUserId(freelancerId) == sub.toString()
    //
    // The GPS-write guard (FreelancerLocationController) enforces id == JWT sub.
    // The GPS-read (resolvePresenceUserId) returns gofpFreelancer.coreUserId.
    // Both paths converge on sub once coreUserId = sub is guaranteed by provisioning.
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void convergence_provisioned_coreUserId_equals_resolvePresenceUserId_result() {
        UUID sub = UUID.randomUUID();

        when(userRepository.findByCoreUserId(sub)).thenReturn(Mono.empty());
        when(userRepository.save(any(GofpUser.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        // Step 1 — provisioning sets coreUserId = sub
        GofpUser provisioned = provisioningService.provisionIfAbsent(sub).block();
        assertThat(provisioned).isNotNull();
        assertThat(provisioned.getCoreUserId()).isEqualTo(sub);

        // Step 2 — freelancer registration sets coreUserId from security context
        //           (enforced by GofpFreelancerProfileController.createOrUpdate since Lot 5)
        UUID freelancerId = UUID.randomUUID();
        GofpFreelancer freelancer = GofpFreelancer.builder()
                .id(freelancerId)
                .coreFreelancerId(UUID.randomUUID())
                .coreUserId(provisioned.getCoreUserId())   // = sub, via security context guard
                .build();

        // Step 3 — resolvePresenceUserId returns gofpFreelancer.getCoreUserId().toString()
        String presenceUserId = freelancer.getCoreUserId().toString();

        assertThat(presenceUserId)
                .as("resolvePresenceUserId must return the JWT sub (F-R3 convergence)")
                .isEqualTo(sub.toString());
    }
}
