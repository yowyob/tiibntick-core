package com.yowyob.tiibntick.core.geo.adapter.in.web;

import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.geo.adapter.in.web.RelayHubAdminController.RevokeRelayHubRequest;
import com.yowyob.tiibntick.core.geo.application.port.in.IRevokeRelayHubUseCase;
import com.yowyob.tiibntick.core.geo.domain.model.HubApprovalStatus;
import com.yowyob.tiibntick.core.geo.domain.model.HubStatus;
import com.yowyob.tiibntick.core.geo.domain.model.RelayHub;
import com.yowyob.tiibntick.core.geo.domain.model.RelayHubRevocation;
import com.yowyob.tiibntick.core.geo.domain.model.RoadNodeId;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import com.yowyob.tiibntick.core.roles.domain.model.TntPermission;
import jakarta.validation.Validation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RelayHubAdminControllerTest {

    private final IRevokeRelayHubUseCase useCase = mock(IRevokeRelayHubUseCase.class);
    private final RelayHubAdminController controller = new RelayHubAdminController(useCase, mock(com.yowyob.tiibntick.core.geo.application.port.in.IManageHubApprovalUseCase.class));

    @Test
    @DisplayName("the route POST /api/v1/geo/tenants/{tenantId}/hubs/{hubId}/revoke requires the explicit relay:revoke")
    void routeAndPermission() throws Exception {
        var method = RelayHubAdminController.class.getMethod("revoke", UUID.class, UUID.class,
                RevokeRelayHubRequest.class, TntSecurityContext.class);
        RequirePermission permission = method.getAnnotation(RequirePermission.class);
        assertThat(permission.resource() + ":" + permission.action()).isEqualTo(TntPermission.RELAY_REVOKE);
        assertThat(permission.required()).isTrue();
        assertThat(RelayHubAdminController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/api/v1/geo/tenants/{tenantId}/hubs/{hubId}");
        assertThat(method.getAnnotation(PostMapping.class).value()).containsExactly("/revoke");
        for (String route : new String[] {"approve", "reject", "suspend", "reactivate"}) {
            var m = java.util.Arrays.stream(RelayHubAdminController.class.getMethods())
                    .filter(x -> x.getName().equals(route)).findFirst().orElseThrow();
            RequirePermission p = m.getAnnotation(RequirePermission.class);
            assertThat(p.resource() + ":" + p.action()).as(route).isEqualTo(TntPermission.RELAY_APPROVE);
            assertThat(m.getAnnotation(PostMapping.class).value()).containsExactly("/" + route);
        }
    }

    @Test
    @DisplayName("the reason is mandatory and bounded (request validation)")
    void reasonValidated() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        assertThat(validator.validate(new RevokeRelayHubRequest(" "))).isNotEmpty();
        assertThat(validator.validate(new RevokeRelayHubRequest(null))).isNotEmpty();
        assertThat(validator.validate(new RevokeRelayHubRequest("x".repeat(RelayHub.MAX_REASON_LENGTH + 1)))).isNotEmpty();
        assertThat(validator.validate(new RevokeRelayHubRequest("Fraude"))).isEmpty();
    }

    @Test
    @DisplayName("the administrator is the authenticated user; the response carries the revocation")
    void delegatesWithTheAdministrator() {
        UUID tenant = UUID.randomUUID(), admin = UUID.randomUUID();
        Instant t = Instant.parse("2026-11-26T08:00:00Z");
        RelayHub hub = RelayHub.rehydrate(UUID.randomUUID(), tenant, null, RoadNodeId.generate(), 4, 0, "op",
                HubStatus.ACTIVE, HubApprovalStatus.REVOKED, t, t, "Fraude", t, admin);
        when(useCase.revoke(tenant, hub.id(), "Fraude", admin))
                .thenReturn(Mono.just(new RelayHubRevocation(hub, List.of(), true)));
        TntSecurityContext ctx = TntSecurityContext.builder().userId(admin).tenantId(tenant).authenticated(true).build();

        StepVerifier.create(controller.revoke(tenant, hub.id(), new RevokeRelayHubRequest("Fraude"), ctx))
                .assertNext(r -> {
                    assertThat(r.approvalStatus()).isEqualTo("REVOKED");
                    assertThat(r.operatingStatus()).isEqualTo("ACTIVE");
                    assertThat(r.reason()).isEqualTo("Fraude");
                    assertThat(r.revokedByActorId()).isEqualTo(admin);
                    assertThat(r.custodyKnown()).isTrue();
                    assertThat(r.parcelsInCustody()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void anonymousRefused() {
        StepVerifier.create(controller.revoke(UUID.randomUUID(), UUID.randomUUID(), new RevokeRelayHubRequest("r"), null))
                .expectError(AccessDeniedException.class).verify();
        verify(useCase, never()).revoke(any(), any(), any(), any());
    }
}
