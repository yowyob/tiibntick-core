package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpUserRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.exception.FreelancerNotFoundException;
import com.yowyob.tiibntick.core.gofreelancer.domain.exception.InvalidFreelancerStatusTransitionException;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.freelancer.FreelancerStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the FreelancerStatus state machine wired into GofpFreelancerService.updateStatus.
 *
 * <p>Machine (lot 18):
 * <pre>
 *   PENDING   → APPROVED, REJECTED
 *   APPROVED  → SUSPENDED, REVOKED
 *   SUSPENDED → APPROVED, REVOKED
 *   REJECTED  → (terminal)
 *   REVOKED   → (terminal)
 * </pre>
 * Same-state transition is idempotent (no save, returns entity unchanged).
 */
@ExtendWith(MockitoExtension.class)
class GofpFreelancerStatusTransitionTest {

    @Mock private GofpFreelancerRepository freelancerRepository;
    @Mock private GofpUserRepository userRepository;

    private GofpFreelancerService service;

    @BeforeEach
    void setUp() {
        service = new GofpFreelancerService(freelancerRepository, userRepository);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private GofpFreelancer freelancer(UUID id, FreelancerStatus status) {
        GofpFreelancer f = new GofpFreelancer();
        f.setId(id);
        f.setStatus(status);
        f.setIsActive(status == FreelancerStatus.APPROVED);
        return f;
    }

    private void stubFind(UUID id, GofpFreelancer f) {
        when(freelancerRepository.findById(id)).thenReturn(Mono.just(f));
    }

    private void stubSave() {
        when(freelancerRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    private void stubNoUser(UUID coreUserId) {
        if (coreUserId != null) {
            when(userRepository.findByCoreUserId(coreUserId)).thenReturn(Mono.empty());
        }
    }

    // ── 404 on unknown freelancer ──────────────────────────────────────────

    @Test
    void updateStatus_notFound_emitsFreelancerNotFoundException() {
        UUID id = UUID.randomUUID();
        when(freelancerRepository.findById(id)).thenReturn(Mono.empty());

        StepVerifier.create(service.updateStatus(id, FreelancerStatus.APPROVED))
                .expectErrorMatches(e -> e instanceof FreelancerNotFoundException
                        && e.getMessage().contains("not found"))
                .verify();

        verify(freelancerRepository, never()).save(any());
    }

    // ── Valid transitions → status persisted ──────────────────────────────

    @ParameterizedTest
    @CsvSource({
        "PENDING,APPROVED",
        "PENDING,REJECTED",
        "APPROVED,SUSPENDED",
        "APPROVED,REVOKED",
        "SUSPENDED,APPROVED",
        "SUSPENDED,REVOKED"
    })
    void updateStatus_validTransition_persistsNewStatus(FreelancerStatus from, FreelancerStatus to) {
        UUID id = UUID.randomUUID();
        GofpFreelancer f = freelancer(id, from);
        stubFind(id, f);
        stubSave();
        stubNoUser(f.getCoreUserId());

        StepVerifier.create(service.updateStatus(id, to))
                .expectNextMatches(saved -> saved.getStatus() == to)
                .verifyComplete();

        verify(freelancerRepository).save(any());
    }

    // ── Invalid transitions → 409 (InvalidFreelancerStatusTransitionException) ──

    @ParameterizedTest
    @CsvSource({
        "PENDING,SUSPENDED",
        "PENDING,REVOKED",
        "APPROVED,PENDING",
        "REJECTED,APPROVED",
        "REJECTED,SUSPENDED",
        "REVOKED,APPROVED",
        "REVOKED,SUSPENDED"
    })
    void updateStatus_invalidTransition_emitsTransitionException(FreelancerStatus from, FreelancerStatus to) {
        UUID id = UUID.randomUUID();
        stubFind(id, freelancer(id, from));

        StepVerifier.create(service.updateStatus(id, to))
                .expectErrorMatches(e -> e instanceof InvalidFreelancerStatusTransitionException
                        && e.getMessage().contains(from.name())
                        && e.getMessage().contains(to.name()))
                .verify();

        verify(freelancerRepository, never()).save(any());
    }

    // ── Same-state is idempotent (no save) ────────────────────────────────

    @Test
    void updateStatus_sameState_isIdempotentNeverSaves() {
        UUID id = UUID.randomUUID();
        GofpFreelancer f = freelancer(id, FreelancerStatus.APPROVED);
        stubFind(id, f);
        stubNoUser(f.getCoreUserId());

        StepVerifier.create(service.updateStatus(id, FreelancerStatus.APPROVED))
                .expectNextMatches(returned -> returned.getStatus() == FreelancerStatus.APPROVED)
                .verifyComplete();

        verify(freelancerRepository, never()).save(any());
    }

    // ── MF5 regression guard (lot 19 baseline) ────────────────────────────
    // Proves the machine à états mords : if isAllowed() is removed, APPROVED → PENDING
    // would return 200 instead of 409. This test catches that regression.

    @Test
    void updateStatus_approvedToPending_isRejected() {
        UUID id = UUID.randomUUID();
        stubFind(id, freelancer(id, FreelancerStatus.APPROVED));

        StepVerifier.create(service.updateStatus(id, FreelancerStatus.PENDING))
                .expectError(InvalidFreelancerStatusTransitionException.class)
                .verify();

        verify(freelancerRepository, never()).save(any());
    }

    // ── Suspension deactivates account ────────────────────────────────────

    @Test
    void updateStatus_suspend_setsIsActiveFalse() {
        UUID id = UUID.randomUUID();
        GofpFreelancer f = freelancer(id, FreelancerStatus.APPROVED);
        f.setIsActive(true);
        stubFind(id, f);
        stubSave();
        stubNoUser(f.getCoreUserId());

        StepVerifier.create(service.updateStatus(id, FreelancerStatus.SUSPENDED))
                .expectNextMatches(saved -> !saved.getIsActive())
                .verifyComplete();
    }
}
