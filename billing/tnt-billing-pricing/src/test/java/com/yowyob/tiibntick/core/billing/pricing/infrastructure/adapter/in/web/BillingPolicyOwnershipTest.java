package com.yowyob.tiibntick.core.billing.pricing.infrastructure.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.BillingPolicy;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.PolicyStatus;
import com.yowyob.tiibntick.core.billing.pricing.domain.port.in.IBillingPolicyUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A billing policy can only be changed by its owner or by billing management. Without these rules any
 * authenticated user of a tenant could activate, archive or delete another driver's tariff.
 */
class BillingPolicyOwnershipTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID ME = UUID.randomUUID();
    private static final UUID SOMEONE_ELSE = UUID.randomUUID();

    private IBillingPolicyUseCase useCase;
    private BillingPolicy mine;
    private BillingPolicy theirs;

    @BeforeEach
    void setUp() {
        useCase = mock(IBillingPolicyUseCase.class);
        mine = policy(ME);
        theirs = policy(SOMEONE_ELSE);
        for (BillingPolicy p : List.of(mine, theirs)) {
            when(useCase.findById(p.getId(), TENANT)).thenReturn(Mono.just(p));
            when(useCase.activatePolicy(p.getId(), TENANT)).thenReturn(Mono.just(p.activate()));
            when(useCase.deletePolicy(p.getId(), TENANT)).thenReturn(Mono.empty());
        }
        when(useCase.findByTenantId(TENANT)).thenReturn(Flux.just(mine, theirs));
        when(useCase.findByOwnerActorId(ME.toString())).thenReturn(Flux.just(mine));
        when(useCase.findByOwnerActorId(SOMEONE_ELSE.toString())).thenReturn(Flux.just(theirs));
    }

    private static BillingPolicy policy(UUID owner) {
        return BillingPolicy.builder().id(UUID.randomUUID()).tenantId(TENANT).name("p")
                .pricingRules(List.of()).surchargeRules(List.of()).promotions(List.of())
                .loyaltyRules(List.of()).commissionRules(List.of()).status(PolicyStatus.DRAFT)
                .validFrom(LocalDate.now()).ownerActorId(owner.toString()).build();
    }

    private WebTestClient as(String... permissions) {
        TntUserIdentity who = new TntUserIdentity(ME, TENANT, null, null, null, Set.of(permissions), false);
        return WebTestClient.bindToController(new BillingPolicyController(useCase))
                .controllerAdvice(new Denied())
                .argumentResolvers(r -> r.addCustomResolver(new Resolver(who)))
                .build();
    }

    @Test
    @DisplayName("the owner activates and deletes their own policy")
    void ownerCanManageOwnPolicy() {
        as().patch().uri("/api/v1/billing/policies/{id}/activate", mine.getId()).exchange().expectStatus().isOk();
        as().delete().uri("/api/v1/billing/policies/{id}", mine.getId()).exchange().expectStatus().isNoContent();
        verify(useCase).activatePolicy(mine.getId(), TENANT);
    }

    @Test
    @DisplayName("nobody else can read, activate or delete it, and nothing is mutated")
    void strangerIsRefused() {
        as().get().uri("/api/v1/billing/policies/{id}", theirs.getId()).exchange().expectStatus().isForbidden();
        as().patch().uri("/api/v1/billing/policies/{id}/activate", theirs.getId()).exchange().expectStatus().isForbidden();
        as().patch().uri("/api/v1/billing/policies/{id}/archive", theirs.getId()).exchange().expectStatus().isForbidden();
        as().delete().uri("/api/v1/billing/policies/{id}", theirs.getId()).exchange().expectStatus().isForbidden();
        verify(useCase, never()).activatePolicy(any(), any());
        verify(useCase, never()).archivePolicy(any(), any());
        verify(useCase, never()).deletePolicy(any(), any());
    }

    @Test
    @DisplayName("billing management (billing:write) manages any policy of the tenant")
    void staffCanManageAnyPolicy() {
        as("billing:write").patch().uri("/api/v1/billing/policies/{id}/activate", theirs.getId())
                .exchange().expectStatus().isOk();
        as("billing:write").get().uri("/api/v1/billing/policies?tenantId={t}", TENANT)
                .exchange().expectStatus().isOk();
    }

    @Test
    @DisplayName("the whole-tenant listing is management-only; owners list their own policies")
    void listings() {
        as().get().uri("/api/v1/billing/policies?tenantId={t}", TENANT).exchange().expectStatus().isForbidden();
        as().get().uri("/api/v1/billing/policies/active?tenantId={t}", TENANT).exchange().expectStatus().isForbidden();
        as().get().uri("/api/v1/billing/policies/owner/{o}", ME).exchange().expectStatus().isOk();
        as().get().uri("/api/v1/billing/policies/owner/{o}", SOMEONE_ELSE).exchange().expectStatus().isForbidden();
    }

    @Test
    @DisplayName("creating a policy for someone else's tariff is refused")
    void cannotCreateForSomeoneElse() {
        as().post().uri("/api/v1/billing/policies/from-template?templateCode=T&ownerType=FREELANCER_ORG"
                        + "&ownerActorId={o}&tenantId={t}", SOMEONE_ELSE, TENANT)
                .exchange().expectStatus().isForbidden();
        verify(useCase, never()).createPolicyFromTemplate(any(), any(), any(), any(), any());
    }

    @RestControllerAdvice
    static class Denied {
        @ExceptionHandler(AccessDeniedException.class)
        @ResponseStatus(HttpStatus.FORBIDDEN)
        void denied() { }
    }

    private record Resolver(TntUserIdentity who) implements HandlerMethodArgumentResolver {
        @Override public boolean supportsParameter(MethodParameter p) {
            return p.hasParameterAnnotation(CurrentUser.class);
        }

        @Override public Mono<Object> resolveArgument(MethodParameter p, BindingContext c, ServerWebExchange e) {
            return Mono.just(who);
        }
    }
}
