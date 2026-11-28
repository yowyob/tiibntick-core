package com.yowyob.tiibntick.core.roles.application.service;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Decision D-176: the confirmation is cached per tenant and account, and emptied on a permission change. */
class ScopedRoleConfirmationServiceTest {

    final UUID tenant = UUID.randomUUID();
    final UUID user = UUID.randomUUID();
    final AtomicInteger reads = new AtomicInteger();
    final AtomicReference<String> stored = new AtomicReference<>("AGENCY_MANAGER");
    final ScopedRoleConfirmationService service = new ScopedRoleConfirmationService((t, u) -> {
        reads.incrementAndGet();
        return stored.get() == null ? Flux.empty() : Flux.just(stored.get());
    }, 60);

    @Test
    void cachedThenInvalidated() {
        StepVerifier.create(service.confirmedScopedRoles(tenant, user))
                .expectNextMatches(s -> s.contains("AGENCY_MANAGER")).verifyComplete();
        StepVerifier.create(service.confirmedScopedRoles(tenant, user)).expectNextCount(1).verifyComplete();
        org.assertj.core.api.Assertions.assertThat(reads).hasValue(1);

        stored.set(null); // revoked
        service.invalidate(tenant, user);
        StepVerifier.create(service.confirmedScopedRoles(tenant, user))
                .expectNextMatches(java.util.Set::isEmpty).verifyComplete();

        service.invalidateTenant(tenant);
        service.confirmedScopedRoles(tenant, user).block();
        org.assertj.core.api.Assertions.assertThat(reads).hasValue(3);
    }

    @Test
    void nothingWithoutTenantOrUser() {
        StepVerifier.create(service.confirmedScopedRoles(null, user)).expectNextMatches(java.util.Set::isEmpty)
                .verifyComplete();
        org.assertj.core.api.Assertions.assertThat(reads).hasValue(0);
    }
}
