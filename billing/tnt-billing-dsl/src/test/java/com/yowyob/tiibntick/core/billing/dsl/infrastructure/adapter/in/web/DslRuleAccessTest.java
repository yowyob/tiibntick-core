package com.yowyob.tiibntick.core.billing.dsl.infrastructure.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.DslRule;
import com.yowyob.tiibntick.core.billing.dsl.domain.port.in.IDslRuleUseCase;
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

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** DSL rules are managed by billing management only; plain syntax validation stays open. */
class DslRuleAccessTest {

    private static final UUID TENANT = UUID.randomUUID();
    private final IDslRuleUseCase useCase = mock(IDslRuleUseCase.class);

    private WebTestClient as(String... permissions) {
        TntUserIdentity who = new TntUserIdentity(UUID.randomUUID(), TENANT, null, null, null,
                Set.of(permissions), false);
        return WebTestClient.bindToController(new DslRuleController(useCase))
                .controllerAdvice(new Denied())
                .argumentResolvers(r -> r.addCustomResolver(new Resolver(who)))
                .build();
    }

    private static String ruleBody() {
        return "{\"name\":\"r\",\"conditionExpression\":\"weight > 1\",\"actionExpression\":\"ADD 100\","
                + "\"tenantId\":\"" + TENANT + "\",\"policyId\":\"" + UUID.randomUUID() + "\"}";
    }

    @Test
    @DisplayName("a driver (no billing:write) cannot create, read, activate or delete rules")
    void nonStaffRefused() {
        UUID id = UUID.randomUUID();
        as().post().uri("/api/v1/billing/dsl/rules").header("Content-Type", "application/json")
                .bodyValue(ruleBody()).exchange().expectStatus().isForbidden();
        as().get().uri("/api/v1/billing/dsl/rules/{id}", id).exchange().expectStatus().isForbidden();
        as().get().uri("/api/v1/billing/dsl/policies/{id}/rules", id).exchange().expectStatus().isForbidden();
        as().patch().uri("/api/v1/billing/dsl/rules/{id}/activate", id).exchange().expectStatus().isForbidden();
        as().delete().uri("/api/v1/billing/dsl/rules/{id}", id).exchange().expectStatus().isForbidden();
        verify(useCase, never()).createRule(any());
        verify(useCase, never()).deleteRule(any(), any());
    }

    @Test
    @DisplayName("billing management creates rules and lists a policy's rules")
    void staffAllowed() {
        when(useCase.createRule(any())).thenAnswer(i -> Mono.just((DslRule) i.getArgument(0)));
        when(useCase.findAllByPolicyId(any(), any())).thenReturn(Flux.<DslRule>empty());
        as("billing:write").post().uri("/api/v1/billing/dsl/rules").header("Content-Type", "application/json")
                .bodyValue(ruleBody()).exchange().expectStatus().isCreated();
        as("billing:write").get().uri("/api/v1/billing/dsl/policies/{id}/rules", UUID.randomUUID())
                .exchange().expectStatus().isOk();
    }

    @Test
    @DisplayName("expression validation is open to any authenticated caller")
    void validateIsOpen() {
        when(useCase.validateExpression(any())).thenReturn(Mono.just(List.of()));
        as().post().uri("/api/v1/billing/dsl/validate").header("Content-Type", "application/json")
                .bodyValue("{\"expression\":\"weight > 1\"}").exchange().expectStatus().isOk();
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
