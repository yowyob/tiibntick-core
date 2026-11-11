package com.yowyob.tiibntick.core.actor.adapter.in.web;

import com.yowyob.tiibntick.common.session.AuthenticatedSessionListener;
import com.yowyob.tiibntick.common.session.AuthenticatedSessionPublisher;
import com.yowyob.tiibntick.core.actor.adapter.in.web.dto.CreateClientRequest;
import com.yowyob.tiibntick.core.actor.application.port.in.ICreateClientProfileUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.IFindClientUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.IResolveActorIdentityUseCase;
import com.yowyob.tiibntick.core.actor.domain.model.ClientProfile;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Profile creation notifies the session listeners (wallet auto-provisioning) without ever failing")
class ProfileCreationSessionHookTest {

    private final UUID tenant = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();
    private final TntUserIdentity identity = new TntUserIdentity(user, tenant, null, null, null, Set.of(), false);

    private ClientController controller(AuthenticatedSessionListener listener) {
        ICreateClientProfileUseCase create = mock(ICreateClientProfileUseCase.class);
        IResolveActorIdentityUseCase resolve = mock(IResolveActorIdentityUseCase.class);
        when(create.createClientProfile(any())).thenReturn(Mono.just(ClientProfile.create(tenant, user)));
        when(resolve.resolveCurrent()).thenReturn(Mono.empty());
        ClientController c = new ClientController(create, mock(IFindClientUseCase.class), resolve);
        if (listener != null) {
            c.setSessionPublisher(new AuthenticatedSessionPublisher(() -> List.of(listener)));
        }
        return c;
    }

    private static AuthenticatedSessionListener counting(AtomicInteger counter, boolean fail) {
        return new AuthenticatedSessionListener() {
            @Override public Mono<Void> onTokenIssued(String t) { return Mono.empty(); }
            @Override public Mono<Void> onProfileCreated() {
                counter.incrementAndGet();
                return fail ? Mono.error(new IllegalStateException("wallet down")) : Mono.empty();
            }
        };
    }

    @Test
    @DisplayName("creating a client profile triggers onProfileCreated once and still answers 201")
    void clientProfileCreationTriggersHook() {
        AtomicInteger hits = new AtomicInteger();
        StepVerifier.create(controller(counting(hits, false))
                        .createClientProfile(identity, Mono.just(new CreateClientRequest(null)), null))
                .assertNext(r -> assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED))
                .verifyComplete();
        assertThat(hits).hasValue(1);
    }

    @Test
    @DisplayName("a failing listener does not turn the profile creation into an error")
    void failingListenerDoesNotBreakCreation() {
        AtomicInteger hits = new AtomicInteger();
        StepVerifier.create(controller(counting(hits, true))
                        .createClientProfile(identity, Mono.just(new CreateClientRequest(null)), null))
                .assertNext(r -> assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED))
                .verifyComplete();
        assertThat(hits).hasValue(1);
    }

    @Test
    @DisplayName("without a publisher (hand-built controller) creation works as before")
    void worksWithoutPublisher() {
        StepVerifier.create(controller(null)
                        .createClientProfile(identity, Mono.just(new CreateClientRequest(null)), null))
                .assertNext(r -> assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED))
                .verifyComplete();
    }
}
