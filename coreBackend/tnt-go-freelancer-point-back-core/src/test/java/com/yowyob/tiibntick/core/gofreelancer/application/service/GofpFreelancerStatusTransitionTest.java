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

import static org.assertj.core.api.Assertions.assertThat;
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
    @Mock private com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryPersonRepository deliveryPersons;
    @Mock private TenantContextHolder tenantContextHolder;
    private static final UUID TENANT = UUID.randomUUID();

    private GofpFreelancerService service;

    @BeforeEach
    void setUp() {
        service = new GofpFreelancerService(freelancerRepository, userRepository, deliveryPersons, tenantContextHolder);
        // defaults of the D-182 look-up: caller in TENANT, no delivery person there, not a platform administrator
        lenient().when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(TENANT));
        lenient().when(deliveryPersons.findById(eq(TENANT), any())).thenReturn(Mono.empty());
        lenient().when(tenantContextHolder.callerIsPlatformAdmin()).thenReturn(Mono.just(false));
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

    /** The delivery-core delivery person (same id) that owns the status (lot L3). */
    private com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryPerson stubCore(UUID id, FreelancerStatus status) {
        var dp = com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryPerson.builder()
                .id(id).tenantId(TENANT).status(status.toCore()).build();
        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(TENANT));
        when(deliveryPersons.findById(TENANT, id)).thenReturn(Mono.just(dp));
        when(deliveryPersons.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        return dp;
    }

    /** The delivery person, read only (the transition is refused or idempotent before any write). */
    private void stubCoreRead(UUID id, FreelancerStatus status) {
        when(deliveryPersons.findById(TENANT, id)).thenReturn(Mono.just(
                com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryPerson.builder()
                        .id(id).tenantId(TENANT).status(status.toCore()).build()));
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
        var dp = stubCore(id, from);

        StepVerifier.create(service.updateStatus(id, to))
                .expectNextMatches(saved -> saved.getStatus() == to)
                .verifyComplete();

        verify(freelancerRepository).save(any());
        // the status is written in delivery-core, not in gofp_freelancers
        verify(deliveryPersons).save(dp);
        org.assertj.core.api.Assertions.assertThat(dp.getStatus()).isEqualTo(to.toCore());
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
        stubCoreRead(id, from);

        StepVerifier.create(service.updateStatus(id, to))
                .expectErrorMatches(e -> e instanceof InvalidFreelancerStatusTransitionException
                        && e.getMessage().contains(from.name())
                        && e.getMessage().contains(to.name()))
                .verify();

        verify(freelancerRepository, never()).save(any());
        verify(deliveryPersons, never()).save(any());
    }

    @Test
    void updateStatus_withoutDeliveryPersonInTheCore_isNotFound() {
        UUID id = UUID.randomUUID();
        stubFind(id, freelancer(id, FreelancerStatus.PENDING));
        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(TENANT));
        when(deliveryPersons.findById(TENANT, id)).thenReturn(Mono.empty());
        when(tenantContextHolder.callerIsPlatformAdmin()).thenReturn(Mono.just(false));

        StepVerifier.create(service.updateStatus(id, FreelancerStatus.APPROVED))
                .expectError(FreelancerNotFoundException.class)
                .verify();
        verify(freelancerRepository, never()).save(any());
    }

    // ── Same-state is idempotent (no save) ────────────────────────────────

    @Test
    void updateStatus_sameState_isIdempotentNeverSaves() {
        UUID id = UUID.randomUUID();
        GofpFreelancer f = freelancer(id, FreelancerStatus.APPROVED);
        stubFind(id, f);
        stubCoreRead(id, FreelancerStatus.APPROVED);
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
        stubCoreRead(id, FreelancerStatus.APPROVED);

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
        stubCore(id, FreelancerStatus.APPROVED);

        StepVerifier.create(service.updateStatus(id, FreelancerStatus.SUSPENDED))
                .expectNextMatches(saved -> !saved.getIsActive())
                .verifyComplete();
    }

    // ── D-182: platform administrator, courier of another tenant ──────────

    @Test
    void updateStatus_platformAdmin_findsTheCourierInItsOwnTenant() {
        UUID id = UUID.randomUUID();
        UUID courierTenant = UUID.randomUUID();
        stubFind(id, freelancer(id, FreelancerStatus.PENDING)); // what the token tenant sees (no delivery person)
        var dp = com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryPerson.builder()
                .id(id).tenantId(courierTenant).status(FreelancerStatus.PENDING.toCore()).build();
        // the token tenant is TENANT; inside underTenant(courierTenant) the current tenant becomes the courier's
        when(tenantContextHolder.currentTenantId()).thenReturn(
                Mono.deferContextual(ctx -> Mono.just(ctx.getOrDefault(TenantContextHolder.TENANT_OVERRIDE, TENANT))));
        when(tenantContextHolder.callerIsPlatformAdmin()).thenReturn(Mono.just(true));
        when(deliveryPersons.findByIdInAnyTenant(id)).thenReturn(Mono.just(dp));
        when(deliveryPersons.findById(courierTenant, id)).thenReturn(Mono.just(dp));
        when(deliveryPersons.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        stubSave();

        StepVerifier.create(service.updateStatus(id, FreelancerStatus.APPROVED))
                .expectNextMatches(f -> f.getStatus() == FreelancerStatus.APPROVED)
                .verifyComplete();
        var saved = org.mockito.ArgumentCaptor.forClass(
                com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryPerson.class);
        verify(deliveryPersons).save(saved.capture());
        assertThat(saved.getValue().getTenantId()).as("acts in the courier's tenant").isEqualTo(courierTenant);
    }

    @Test
    void updateStatus_notAdmin_neverLooksInOtherTenants() {
        UUID id = UUID.randomUUID();
        stubFind(id, freelancer(id, FreelancerStatus.PENDING));
        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(TENANT));
        when(deliveryPersons.findById(TENANT, id)).thenReturn(Mono.empty());
        when(tenantContextHolder.callerIsPlatformAdmin()).thenReturn(Mono.just(false));

        StepVerifier.create(service.updateStatus(id, FreelancerStatus.APPROVED))
                .expectError(FreelancerNotFoundException.class).verify();
        verify(deliveryPersons, never()).findByIdInAnyTenant(any());
    }

    // ── D-181: startup catch-up of PENDING couriers ───────────────────────

    @Test
    void approvePendingCouriers_approvesOnlyPendingDeliveryPersons() {
        UUID pending = UUID.randomUUID();
        UUID rejected = UUID.randomUUID();
        when(freelancerRepository.findAllByStatus(FreelancerStatus.PENDING))
                .thenReturn(reactor.core.publisher.Flux.just(freelancer(pending, FreelancerStatus.PENDING),
                        freelancer(rejected, FreelancerStatus.PENDING)));
        when(deliveryPersons.findByIdInAnyTenant(pending)).thenReturn(Mono.just(
                com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryPerson.builder()
                        .id(pending).tenantId(TENANT).status(FreelancerStatus.PENDING.toCore()).build()));
        when(deliveryPersons.findByIdInAnyTenant(rejected)).thenReturn(Mono.just(
                com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryPerson.builder()
                        .id(rejected).tenantId(TENANT).status(FreelancerStatus.REJECTED.toCore()).build()));
        when(deliveryPersons.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.approvePendingCouriers()).expectNext(1L).verifyComplete();
        var saved = org.mockito.ArgumentCaptor.forClass(
                com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryPerson.class);
        verify(deliveryPersons).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(pending);
        assertThat(saved.getValue().getStatus()).isEqualTo(FreelancerStatus.APPROVED.toCore());
    }

    @Test
    void updateStatus_withoutDeliveryPersonHere_isNotFoundNotAMisleadingConflict() {
        UUID id = UUID.randomUUID();
        // the projection reads PENDING by default when the delivery person is not in this tenant
        stubFind(id, freelancer(id, FreelancerStatus.PENDING));

        StepVerifier.create(service.updateStatus(id, FreelancerStatus.SUSPENDED))
                .expectError(FreelancerNotFoundException.class).verify();
    }
}
