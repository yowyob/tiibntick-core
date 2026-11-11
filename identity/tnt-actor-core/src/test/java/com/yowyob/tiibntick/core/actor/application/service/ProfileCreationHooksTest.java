package com.yowyob.tiibntick.core.actor.application.service;

import com.yowyob.tiibntick.core.actor.application.port.in.IResolveActorIdentityUseCase;
import com.yowyob.tiibntick.core.actor.application.port.out.IProfileWelcomePort;
import com.yowyob.tiibntick.core.actor.application.port.out.IProfileWelcomePort.Kind;
import com.yowyob.tiibntick.core.actor.application.port.out.IProfileWelcomePort.Welcome;
import com.yowyob.tiibntick.core.actor.domain.model.ActorIdentitySummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProfileCreationHooksTest {

    private final UUID tenant = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();
    private final List<Welcome> sent = new ArrayList<>();

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T bean) {
        ObjectProvider<T> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(bean);
        return p;
    }

    private ProfileCreationHooks hooks(IResolveActorIdentityUseCase identity, boolean portFails) {
        IProfileWelcomePort port = w -> {
            if (portFails) {
                return Mono.error(new IllegalStateException("notify down"));
            }
            sent.add(w);
            return Mono.empty();
        };
        return new ProfileCreationHooks(provider(port), provider(identity));
    }

    private static IResolveActorIdentityUseCase identityOf(ActorIdentitySummary summary) {
        IResolveActorIdentityUseCase use = mock(IResolveActorIdentityUseCase.class);
        when(use.resolveCurrent()).thenReturn(summary == null ? Mono.empty() : Mono.just(summary));
        return use;
    }

    @Test
    void welcomesWithTheNameAndAddressOfTheAccount() {
        var summary = new ActorIdentitySummary(actor, "Jo Mballa", null, "jo@x.cm");
        StepVerifier.create(hooks(identityOf(summary), false).profileCreated(Kind.CLIENT, tenant, actor)).verifyComplete();
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(sent).hasSize(1));
        assertThat(sent.get(0)).isEqualTo(new Welcome(tenant, actor, Kind.CLIENT, "Jo Mballa", "jo@x.cm"));
    }

    @Test
    void welcomesEvenWhenTheIdentityCannotBeRead() {
        StepVerifier.create(hooks(identityOf(null), false).profileCreated(Kind.DELIVERER, tenant, actor)).verifyComplete();
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(sent).hasSize(1));
        assertThat(sent.get(0).email()).isNull();
    }

    @Test
    void aFailingWelcomeNeverFailsTheCreation() {
        var summary = new ActorIdentitySummary(actor, "Jo", null, "jo@x.cm");
        StepVerifier.create(hooks(identityOf(summary), true).profileCreated(Kind.FREELANCER, tenant, actor)).verifyComplete();
    }

    @Test
    void doesNothingWithoutAWelcomePort() {
        StepVerifier.create(ProfileCreationHooks.none().profileCreated(Kind.CLIENT, tenant, actor)).verifyComplete();
    }
}
