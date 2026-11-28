package com.yowyob.tiibntick.core.geo.application.service;

import com.yowyob.tiibntick.core.geo.application.port.out.IRelayHubCustodyPort;
import com.yowyob.tiibntick.core.geo.application.port.out.IRelayHubRepository;
import com.yowyob.tiibntick.core.geo.application.port.out.IRelayHubRevocationListener;
import com.yowyob.tiibntick.core.geo.domain.exception.GeoNotFoundException;
import com.yowyob.tiibntick.core.geo.domain.exception.RelayHubStateException;
import com.yowyob.tiibntick.core.geo.domain.model.HubCustodyItem;
import com.yowyob.tiibntick.core.geo.domain.model.HubApprovalStatus;
import com.yowyob.tiibntick.core.geo.domain.model.HubStatus;
import com.yowyob.tiibntick.core.geo.domain.model.RelayHub;
import com.yowyob.tiibntick.core.geo.domain.model.RelayHubRevocation;
import com.yowyob.tiibntick.core.geo.domain.model.RoadNodeId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RelayHubRevocationServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID();

    private final IRelayHubRepository repository = mock(IRelayHubRepository.class);
    private final List<RelayHubRevocation> heard = new ArrayList<>();

    private RelayHub stored(HubStatus status) {
        Instant t = Instant.parse("2026-11-01T00:00:00Z");
        RelayHub hub = RelayHub.rehydrate(UUID.randomUUID(), TENANT, null, RoadNodeId.generate(), 4, 2, "op", status, t, t);
        when(repository.findById(hub.id(), TENANT)).thenReturn(Mono.just(hub));
        doAnswer(inv -> Mono.just(inv.getArgument(0))).when(repository).save(any());
        return hub;
    }

    private RelayHubRevocationService service(IRelayHubCustodyPort custody, IRelayHubRevocationListener... listeners) {
        StaticListableBeanFactory custodyBeans = new StaticListableBeanFactory();
        if (custody != null) custodyBeans.addBean("custody", custody);
        StaticListableBeanFactory listenerBeans = new StaticListableBeanFactory();
        for (int i = 0; i < listeners.length; i++) listenerBeans.addBean("l" + i, listeners[i]);
        return new RelayHubRevocationService(repository, custodyBeans.getBeanProvider(IRelayHubCustodyPort.class),
                listenerBeans.getBeanProvider(IRelayHubRevocationListener.class));
    }

    private final IRelayHubRevocationListener recorder = r -> Mono.fromRunnable(() -> heard.add(r));

    @Test
    @DisplayName("revoked and saved, parcels in custody listed, listeners told")
    void revokes() {
        RelayHub hub = stored(HubStatus.ACTIVE);
        HubCustodyItem item = new HubCustodyItem(UUID.randomUUID(), UUID.randomUUID(), "ABC", UUID.randomUUID(), Instant.now());
        IRelayHubCustodyPort custody = id -> id.equals(hub.id()) ? Flux.just(item) : Flux.empty();

        StepVerifier.create(service(custody, recorder).revoke(TENANT, hub.id(), "Fraude", ADMIN))
                .assertNext(r -> {
                    assertThat(r.hub().approvalStatus()).isEqualTo(HubApprovalStatus.REVOKED);
                    assertThat(r.hub().status()).as("operating status kept").isEqualTo(HubStatus.ACTIVE);
                    assertThat(r.hub().revocationReason()).isEqualTo("Fraude");
                    assertThat(r.custodyKnown()).isTrue();
                    assertThat(r.parcelsInCustody()).containsExactly(item);
                })
                .verifyComplete();
        verify(repository).save(argThat(h -> h.approvalStatus() == HubApprovalStatus.REVOKED && ADMIN.equals(h.revokedByActorId())));
        assertThat(heard).hasSize(1);
    }

    @Test
    @DisplayName("without inventory adapter, or when it fails, the custody is unknown and the revocation stands")
    void custodyUnknown() {
        RelayHub hub = stored(HubStatus.FULL);
        StepVerifier.create(service(null, recorder).revoke(TENANT, hub.id(), "r", ADMIN))
                .assertNext(r -> assertThat(r.custodyKnown()).isFalse())
                .verifyComplete();

        RelayHub other = stored(HubStatus.ACTIVE);
        IRelayHubCustodyPort failing = id -> Flux.error(new RuntimeException("db down"));
        StepVerifier.create(service(failing).revoke(TENANT, other.id(), "r", ADMIN))
                .assertNext(r -> {
                    assertThat(r.custodyKnown()).isFalse();
                    assertThat(r.parcelsInCustody()).isEmpty();
                    assertThat(r.hub().isRevoked()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("a failing listener does not cancel the revocation nor the next listeners")
    void listenerFailureSwallowed() {
        RelayHub hub = stored(HubStatus.ACTIVE);
        IRelayHubRevocationListener failing = r -> Mono.error(new RuntimeException("notify down"));
        StepVerifier.create(service(id -> Flux.empty(), failing, recorder).revoke(TENANT, hub.id(), "r", ADMIN))
                .assertNext(r -> assertThat(r.hub().isRevoked()).isTrue())
                .verifyComplete();
        assertThat(heard).hasSize(1);
    }

    @Test
    void unknownHub() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id, TENANT)).thenReturn(Mono.empty());
        StepVerifier.create(service(null, recorder).revoke(TENANT, id, "r", ADMIN))
                .expectError(GeoNotFoundException.class).verify();
        assertThat(heard).isEmpty();
    }

    @Test
    @DisplayName("already revoked: refused, nothing saved, nobody told")
    void alreadyRevoked() {
        RelayHub hub = stored(HubStatus.ACTIVE);
        hub.revoke("first", ADMIN);
        StepVerifier.create(service(null, recorder).revoke(TENANT, hub.id(), "again", ADMIN))
                .expectError(RelayHubStateException.class).verify();
        verify(repository, never()).save(any());
        assertThat(heard).isEmpty();
    }

    @Test
    @DisplayName("D-135: a pending hub is not revoked (it is rejected): 409, nothing saved, nobody told")
    void pendingRefused() {
        Instant t = Instant.parse("2026-11-01T00:00:00Z");
        RelayHub hub = RelayHub.rehydrate(UUID.randomUUID(), TENANT, null, RoadNodeId.generate(), 4, 0, "op",
                HubStatus.ACTIVE, HubApprovalStatus.PENDING, t, t, null, null, null);
        when(repository.findById(hub.id(), TENANT)).thenReturn(Mono.just(hub));
        StepVerifier.create(service(null, recorder).revoke(TENANT, hub.id(), "r", ADMIN))
                .expectError(RelayHubStateException.class).verify();
        verify(repository, never()).save(any());
        assertThat(heard).isEmpty();
    }

    @Test
    void blankReasonRefused() {
        RelayHub hub = stored(HubStatus.ACTIVE);
        StepVerifier.create(service(null, recorder).revoke(TENANT, hub.id(), " ", ADMIN))
                .expectError(IllegalArgumentException.class).verify();
        verify(repository, never()).save(any());
    }
}
