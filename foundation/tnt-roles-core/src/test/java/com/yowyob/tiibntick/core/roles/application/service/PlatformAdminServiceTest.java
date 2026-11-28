package com.yowyob.tiibntick.core.roles.application.service;

import com.yowyob.tiibntick.core.roles.application.port.in.ManagePlatformAdminsUseCase.Caller;
import com.yowyob.tiibntick.core.roles.application.port.out.IPermissionChangeNotifier;
import com.yowyob.tiibntick.core.roles.application.port.out.IPlatformAdminAlertPort;
import com.yowyob.tiibntick.core.roles.domain.model.PlatformGrantKind;
import com.yowyob.tiibntick.core.roles.domain.model.PlatformRoleGrant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Rules of the TNT_ADMIN registry, decisions D-159 to D-175
 * ({@code docs/architecture/chantiers/registre-administrateurs-plateforme.md}).
 */
class PlatformAdminServiceTest {

    static final UUID SYSTEM = UUID.fromString("fbd90da6-629d-4d78-9a36-8735ff1762cb");
    static final UUID OTHER_TENANT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final UUID OWNER = UUID.fromString("0000000a-0000-0000-0000-000000000001");
    static final UUID ALICE = UUID.fromString("0000000a-0000-0000-0000-000000000002");
    static final UUID BOB = UUID.fromString("0000000a-0000-0000-0000-000000000003");
    static final UUID STRANGER = UUID.fromString("0000000a-0000-0000-0000-000000000004");

    InMemoryPlatformRoleGrants repo;
    PlatformAdminCache cache;
    IPermissionChangeNotifier notifier;
    List<String> alerts;
    AtomicReference<Instant> now;
    PlatformAdminService service;

    @BeforeEach
    void setUp() {
        repo = new InMemoryPlatformRoleGrants();
        cache = new PlatformAdminCache(60);
        notifier = mock(IPermissionChangeNotifier.class);
        alerts = new ArrayList<>();
        now = new AtomicReference<>(Instant.parse("2026-11-27T10:00:00Z"));
        service = service(SYSTEM);
    }

    PlatformAdminService service(UUID systemTenant) {
        TransactionalOperator tx = mock(TransactionalOperator.class);
        when(tx.transactional(any(Mono.class))).thenAnswer(i -> i.getArgument(0));
        IPlatformAdminAlertPort recorder = new IPlatformAdminAlertPort() {
            @Override public Mono<Void> granted(PlatformRoleGrant g, UUID owner) {
                return Mono.fromRunnable(() -> alerts.add("GRANTED " + g.userId() + " owner=" + owner));
            }
            @Override public Mono<Void> revoked(PlatformRoleGrant g, UUID owner) {
                return Mono.fromRunnable(() -> alerts.add("REVOKED " + g.userId() + " owner=" + owner));
            }
        };
        IPlatformAdminAlertPort broken = new IPlatformAdminAlertPort() {
            @Override public Mono<Void> granted(PlatformRoleGrant g, UUID o) { return Mono.error(new IllegalStateException("smtp down")); }
            @Override public Mono<Void> revoked(PlatformRoleGrant g, UUID o) { return Mono.error(new IllegalStateException("smtp down")); }
        };
        Clock clock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
        return new PlatformAdminService(repo, cache, notifier, tx, List.of(broken, recorder), systemTenant, clock);
    }

    static Caller ownerToken() { return new Caller(OWNER, SYSTEM, true); }
    static Caller as(UUID user, UUID tenant) { return new Caller(user, tenant, false); }

    static int status(Throwable e) {
        return ((ResponseStatusException) e).getStatusCode().value();
    }

    @Nested
    @DisplayName("SYSTEM_OWNER (D-161, D-163)")
    class Owner {

        @Test
        void theOwnerGrantIsRecordedOnceProtectedAndFromTheSystemTenant() {
            PlatformRoleGrant first = service.ensureSystemOwner(OWNER).block();
            PlatformRoleGrant again = service.ensureSystemOwner(OWNER).block();
            assertThat(again.id()).isEqualTo(first.id());
            assertThat(repo.rows).hasSize(1);
            assertThat(first.kind()).isEqualTo(PlatformGrantKind.SYSTEM_OWNER);
            assertThat(first.grantedBy()).isEqualTo(OWNER);
            assertThat(first.sourceTenantId()).isEqualTo(SYSTEM);
            assertThat(first.protectedFor(SYSTEM)).isTrue();
            assertThat(alerts).containsExactly("GRANTED " + OWNER + " owner=" + OWNER);
        }

        @Test
        void afterTheClaimTheOwnerIsAnAdministratorFromAnyTenant() {
            service.claimOwnership(ownerToken()).block();
            cache.invalidateAll();
            StepVerifier.create(service.isPlatformAdmin(OWNER)).expectNext(true).verifyComplete();
            // logged in another tenant: no owner marker, still allowed to grant
            StepVerifier.create(service.grant(as(OWNER, OTHER_TENANT), ALICE)).expectNextCount(1).verifyComplete();
        }

        @Test
        void onlyAnOwnerTokenCanClaim() {
            StepVerifier.create(service.claimOwnership(as(ALICE, SYSTEM)))
                    .expectErrorMatches(e -> status(e) == 403).verify();
        }

        @Test
        void anAssignedGrantOfTheOwnerIsSupersededByTheProtectedOne() {
            service.ensureSystemOwner(BOB).block();
            PlatformRoleGrant assigned = service.grant(as(BOB, SYSTEM), OWNER).block();
            PlatformRoleGrant owner = service.ensureSystemOwner(OWNER).block();
            assertThat(owner.kind()).isEqualTo(PlatformGrantKind.SYSTEM_OWNER);
            assertThat(repo.rows.get(assigned.id()).revokedAt()).isNotNull();
            assertThat(repo.rows.get(assigned.id()).revokeReason()).isEqualTo(PlatformAdminService.SUPERSEDED_BY_OWNER);
        }

        @Test
        @DisplayName("D-177: a new Kernel owner takes the protection over; the former one stays TNT_ADMIN, revocable")
        void ownershipTransferredInTheKernel() {
            PlatformRoleGrant former = service.ensureSystemOwner(BOB).block();
            alerts.clear();

            PlatformRoleGrant owner = service.ensureSystemOwner(OWNER).block();

            assertThat(owner.protectedFor(SYSTEM)).isTrue();
            PlatformRoleGrant closed = repo.rows.get(former.id());
            assertThat(closed.revokedAt()).isNotNull();
            assertThat(closed.revokedBy()).isEqualTo(OWNER);
            assertThat(closed.revokeReason()).isEqualTo(PlatformAdminService.OWNERSHIP_TRANSFERRED);
            PlatformRoleGrant kept = repo.findActive(PlatformRoleGrant.TNT_ADMIN, BOB).block();
            assertThat(kept.kind()).isEqualTo(PlatformGrantKind.ASSIGNED);
            assertThat(kept.grantedBy()).isEqualTo(OWNER);
            assertThat(kept.sourceTenantId()).isEqualTo(SYSTEM);
            assertThat(kept.protectedFor(SYSTEM)).isFalse();
            // the two changes are announced to the former owner and to the new one, then the new owner grant
            assertThat(alerts).containsExactly("REVOKED " + BOB + " owner=" + OWNER, "GRANTED " + BOB + " owner=" + OWNER,
                    "GRANTED " + OWNER + " owner=" + OWNER);
            // the former owner is now revocable by any administrator, and out of the mass revocation (system tenant)
            StepVerifier.create(service.revoke(as(OWNER, OTHER_TENANT), kept.id(), "left")).expectNextCount(1)
                    .verifyComplete();
            StepVerifier.create(service.isPlatformAdmin(BOB)).expectNext(false).verifyComplete();
            // the new owner logging in again changes nothing
            alerts.clear();
            service.ensureSystemOwner(OWNER).block();
            assertThat(alerts).isEmpty();
        }

        @Test
        void nobodyRevokesTheProtectedGrantNotEvenItsHolder() {
            PlatformRoleGrant owner = service.ensureSystemOwner(OWNER).block();
            service.grant(ownerToken(), ALICE).block();
            StepVerifier.create(service.revoke(as(ALICE, OTHER_TENANT), owner.id(), "x"))
                    .expectErrorMatches(e -> status(e) == 409).verify();
            StepVerifier.create(service.revoke(ownerToken(), owner.id(), "x"))
                    .expectErrorMatches(e -> status(e) == 409).verify();
            assertThat(repo.rows.get(owner.id()).active()).isTrue();
        }
    }

    @Nested
    @DisplayName("grant and revoke (D-164, D-166)")
    class GrantRevoke {

        @Test
        void aGrantRecordsWhoGrantedAndTheGrantersTenantEvenForTheOwnerLoggedElsewhere() {
            service.ensureSystemOwner(OWNER).block();
            PlatformRoleGrant g = service.grant(as(OWNER, OTHER_TENANT), ALICE).block();
            assertThat(g.kind()).isEqualTo(PlatformGrantKind.ASSIGNED);
            assertThat(g.grantedBy()).isEqualTo(OWNER);
            assertThat(g.sourceTenantId()).isEqualTo(OTHER_TENANT);
            assertThat(g.systemTenantAtGrant()).isEqualTo(SYSTEM);
            assertThat(alerts).contains("GRANTED " + ALICE + " owner=" + OWNER);
            verify(notifier).notifyChanged(SYSTEM, ALICE);
        }

        @Test
        void theBeneficiaryNeedsNoMembershipAndTheGrantIsIdempotent() {
            service.ensureSystemOwner(OWNER).block();
            PlatformRoleGrant a = service.grant(ownerToken(), STRANGER).block();
            PlatformRoleGrant b = service.grant(ownerToken(), STRANGER).block();
            assertThat(b.id()).isEqualTo(a.id());
            StepVerifier.create(service.isPlatformAdmin(STRANGER)).expectNext(true).verifyComplete();
        }

        @Test
        void aNonAdministratorCannotGrant() {
            StepVerifier.create(service.grant(as(STRANGER, OTHER_TENANT), ALICE))
                    .expectErrorMatches(e -> status(e) == 403).verify();
            assertThat(repo.rows).isEmpty();
        }

        @Test
        void theSourceTenantMustBeKnown() {
            service.ensureSystemOwner(OWNER).block();
            StepVerifier.create(service.grant(new Caller(OWNER, null, false), ALICE))
                    .expectErrorMatches(e -> status(e) == 400).verify();
        }

        @Test
        void anyAdministratorRevokesAnAssignedGrantAndTheHistoryStays() {
            service.ensureSystemOwner(OWNER).block();
            PlatformRoleGrant alice = service.grant(ownerToken(), ALICE).block();
            service.grant(ownerToken(), BOB).block();
            PlatformRoleGrant revoked = service.revoke(as(BOB, OTHER_TENANT), alice.id(), "left the team").block();
            assertThat(revoked.revokedBy()).isEqualTo(BOB);
            assertThat(revoked.revokeReason()).isEqualTo("left the team");
            assertThat(repo.rows).containsKey(alice.id());
            StepVerifier.create(service.isPlatformAdmin(ALICE)).expectNext(false).verifyComplete();
            assertThat(service.list(true).collectList().block()).hasSize(3);
            assertThat(service.list(false).collectList().block()).hasSize(2);
            assertThat(alerts).contains("REVOKED " + ALICE + " owner=" + OWNER);
        }

        @Test
        void aRevokedAdministratorCanNoLongerAct() {
            service.ensureSystemOwner(OWNER).block();
            PlatformRoleGrant alice = service.grant(ownerToken(), ALICE).block();
            service.revoke(ownerToken(), alice.id(), null).block();
            StepVerifier.create(service.grant(as(ALICE, SYSTEM), BOB))
                    .expectErrorMatches(e -> status(e) == 403).verify();
        }

        @Test
        void aFailingAlertNeverUndoesTheGrant() {
            service.ensureSystemOwner(OWNER).block();
            StepVerifier.create(service.grant(ownerToken(), ALICE)).expectNextCount(1).verifyComplete();
            assertThat(repo.findActive(PlatformRoleGrant.TNT_ADMIN, ALICE).block()).isNotNull();
        }

        @Test
        void unknownGrant404() {
            service.ensureSystemOwner(OWNER).block();
            StepVerifier.create(service.revoke(ownerToken(), UUID.randomUUID(), null))
                    .expectErrorMatches(e -> status(e) == 404).verify();
        }
    }

    @Nested
    @DisplayName("mass revocation of the grants obtained from another tenant (D-165, D-166)")
    class Mass {

        PlatformRoleGrant fromSystem;
        PlatformRoleGrant fromOther;

        @BeforeEach
        void grants() {
            service.ensureSystemOwner(OWNER).block();
            fromSystem = service.grant(as(OWNER, SYSTEM), ALICE).block();
            fromOther = service.grant(as(OWNER, OTHER_TENANT), BOB).block();
        }

        @Test
        void reservedToTheOwner() {
            StepVerifier.create(service.previewForeignTenantRevocation(as(ALICE, SYSTEM)))
                    .expectErrorMatches(e -> status(e) == 403).verify();
        }

        @Test
        void theOwnerLoggedInAnotherTenantMayRunIt() {
            StepVerifier.create(service.previewForeignTenantRevocation(as(OWNER, OTHER_TENANT)))
                    .assertNext(p -> assertThat(p.grants()).extracting(PlatformRoleGrant::id)
                            .containsExactly(fromOther.id()))
                    .verifyComplete();
        }

        @Test
        void previewThenConfirmRevokesExactlyTheList() {
            var preview = service.previewForeignTenantRevocation(ownerToken()).block();
            var result = service.revokeForeignTenant(ownerToken(), preview.confirmationToken(), "audit 2026-11").block();
            assertThat(result.revoked()).extracting(PlatformRoleGrant::id).containsExactly(fromOther.id());
            PlatformRoleGrant bob = repo.rows.get(fromOther.id());
            assertThat(bob.revokeBatchId()).isEqualTo(result.batchId());
            assertThat(bob.revokedBy()).isEqualTo(OWNER);
            assertThat(bob.revokeReason()).isEqualTo("audit 2026-11");
            assertThat(repo.rows.get(fromSystem.id()).active()).isTrue();
            assertThat(service.list(false).collectList().block()).extracting(PlatformRoleGrant::userId)
                    .containsExactlyInAnyOrder(OWNER, ALICE);
        }

        @Test
        void aListThatChangedSinceThePreviewIsRefused() {
            var preview = service.previewForeignTenantRevocation(ownerToken()).block();
            service.grant(as(OWNER, OTHER_TENANT), STRANGER).block();
            StepVerifier.create(service.revokeForeignTenant(ownerToken(), preview.confirmationToken(), "x"))
                    .expectErrorMatches(e -> status(e) == 409).verify();
            assertThat(repo.rows.get(fromOther.id()).active()).isTrue();
        }

        @Test
        void anExpiredOrForgedTokenOrNoReasonIsRefused() {
            var preview = service.previewForeignTenantRevocation(ownerToken()).block();
            StepVerifier.create(service.revokeForeignTenant(ownerToken(), preview.confirmationToken(), " "))
                    .expectErrorMatches(e -> status(e) == 400).verify();
            StepVerifier.create(service.revokeForeignTenant(ownerToken(), "v1.oops.zz", "x"))
                    .expectErrorMatches(e -> status(e) == 400).verify();
            now.set(now.get().plus(Duration.ofMinutes(6)));
            StepVerifier.create(service.revokeForeignTenant(ownerToken(), preview.confirmationToken(), "x"))
                    .expectErrorMatches(e -> status(e) == 400).verify();
        }

        @Test
        void afterAChangeOfSystemTenantTheFormerOwnerGrantIsNoLongerProtectedAndGoesWithTheMassRevocation() {
            UUID newSystem = UUID.fromString("33333333-3333-3333-3333-333333333333");
            UUID newOwner = UUID.fromString("0000000a-0000-0000-0000-000000000009");
            PlatformAdminService after = service(newSystem);
            after.ensureSystemOwner(newOwner).block();
            Caller newOwnerToken = new Caller(newOwner, newSystem, true);
            PlatformRoleGrant formerOwner = repo.findActive(PlatformRoleGrant.TNT_ADMIN, OWNER).block();
            assertThat(formerOwner.protectedFor(newSystem)).isFalse();
            // one by one: refused, it belongs to the mass revocation
            StepVerifier.create(after.revoke(newOwnerToken, formerOwner.id(), "x"))
                    .expectErrorMatches(e -> status(e) == 409).verify();
            var preview = after.previewForeignTenantRevocation(newOwnerToken).block();
            assertThat(preview.grants()).extracting(PlatformRoleGrant::userId)
                    .containsExactlyInAnyOrder(OWNER, ALICE, BOB);
            after.revokeForeignTenant(newOwnerToken, preview.confirmationToken(), "system tenant changed").block();
            assertThat(after.list(false).collectList().block()).extracting(PlatformRoleGrant::userId)
                    .containsExactly(newOwner);
        }
    }
}
