package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.in.rest;

import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.dispute.application.port.inbound.IDisputeCommandUseCase;
import com.yowyob.tiibntick.core.dispute.application.port.inbound.IDisputeQueryUseCase;
import com.yowyob.tiibntick.core.dispute.application.port.outbound.IDisputeParcelPort;
import com.yowyob.tiibntick.core.dispute.domain.enums.ClaimantType;
import com.yowyob.tiibntick.core.roles.domain.exception.TntRoleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Lot L4 ([E], spec §1.4): who may open a dispute on a core parcel — the sender as CLIENT, an assigned courier (or his
 * organisation), the relay holding it; never SYSTEM unless support; a mediator always.
 */
class DisputeClaimantGuardL4Test {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID SENDER = UUID.randomUUID();
    private static final UUID COURIER = UUID.randomUUID();
    private static final UUID RELAY_ORG = UUID.randomUUID();
    private static final UUID FLEET_ORG = UUID.randomUUID();
    private static final String PARCEL = UUID.randomUUID().toString();

    private final IDisputeParcelPort parcels = mock(IDisputeParcelPort.class);
    private DisputeController controller;

    @BeforeEach
    void setUp() {
        controller = new DisputeController(mock(IDisputeCommandUseCase.class), mock(IDisputeQueryUseCase.class));
        controller.setParcels(parcels);
        controller.setParticipation((tenant, mission, actor, org) -> Mono.just(false));
        when(parcels.resolveParcel(TENANT.toString(), PARCEL, null)).thenReturn(Mono.just(PARCEL));
        when(parcels.parties(TENANT.toString(), PARCEL)).thenReturn(Mono.just(new IDisputeParcelPort.Parties(PARCEL,
                Set.of(SENDER.toString()), Set.of(), Set.of(COURIER.toString()),
                Set.of(FLEET_ORG.toString().toLowerCase()), Set.of(RELAY_ORG.toString()))));
    }

    private static TntUserIdentity user(UUID actor, UUID org, String... permissions) {
        return new TntUserIdentity(UUID.randomUUID(), TENANT, actor, org, null, Set.of(permissions), false);
    }

    private Mono<String> open(TntUserIdentity user, ClaimantType type) {
        return controller.ifClaimantMayOpen(user, type, PARCEL, null, () -> Mono.just("opened"));
    }

    @Test
    @DisplayName("parties open for the claimant type they really are")
    void partiesMayOpen() {
        assertThat(open(user(SENDER, null, "dispute:create"), ClaimantType.CLIENT).block()).isEqualTo("opened");
        assertThat(open(user(COURIER, null, "dispute:create"), ClaimantType.FREELANCER).block()).isEqualTo("opened");
        assertThat(open(user(UUID.randomUUID(), FLEET_ORG, "dispute:create"), ClaimantType.PERMANENT_DELIVERER)
                .block()).isEqualTo("opened");
        assertThat(open(user(UUID.randomUUID(), RELAY_ORG, "dispute:create"), ClaimantType.HUB_OPERATOR).block())
                .isEqualTo("opened");
        assertThat(open(user(UUID.randomUUID(), null, "dispute:resolve"), ClaimantType.SYSTEM).block())
                .as("support").isEqualTo("opened");
    }

    @Test
    @DisplayName("a stranger, a wrong claimant type, SYSTEM or a recipient not yet known are refused")
    void othersAreRefused() {
        StepVerifier.create(open(user(UUID.randomUUID(), null, "dispute:create"), ClaimantType.CLIENT))
                .expectError(TntRoleException.class).verify();
        StepVerifier.create(open(user(COURIER, null, "dispute:create"), ClaimantType.CLIENT))
                .expectError(TntRoleException.class).verify();
        StepVerifier.create(open(user(SENDER, null, "dispute:create"), ClaimantType.SYSTEM))
                .expectError(TntRoleException.class).verify();
        StepVerifier.create(open(user(UUID.randomUUID(), null, "dispute:create"), ClaimantType.RECIPIENT))
                .expectError(TntRoleException.class).verify();
    }

    @Test
    @DisplayName("not a core parcel: the mission participation check decides")
    void notACoreParcel() {
        when(parcels.resolveParcel(TENANT.toString(), "market-order-1", "m-1")).thenReturn(Mono.empty());
        controller.setParticipation((tenant, mission, actor, org) -> Mono.just("m-1".equals(mission)));
        assertThat(controller.ifClaimantMayOpen(user(SENDER, null, "dispute:create"), ClaimantType.CLIENT,
                "market-order-1", "m-1", () -> Mono.just("opened")).block()).isEqualTo("opened");
    }
}
