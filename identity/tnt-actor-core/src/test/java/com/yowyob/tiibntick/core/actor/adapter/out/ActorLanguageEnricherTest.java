package com.yowyob.tiibntick.core.actor.adapter.out;

import com.yowyob.tiibntick.core.actor.adapter.in.web.dto.ClientProfileResponse;
import com.yowyob.tiibntick.core.actor.adapter.in.web.dto.FreelancerProfileResponse;
import com.yowyob.tiibntick.core.actor.adapter.out.persistence.ActorLanguageEnricher;
import com.yowyob.tiibntick.core.actor.domain.model.ClientProfile;
import com.yowyob.tiibntick.core.actor.domain.model.FreelancerProfile;
import com.yowyob.tiibntick.core.actor.domain.model.RelayOperatorProfile;
import com.yowyob.tiibntick.core.i18n.application.port.in.ResolveRecipientLanguageUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** One language per account: every profile of the same actor reads the same value. */
class ActorLanguageEnricherTest {

    private static ObjectProvider<ResolveRecipientLanguageUseCase> provider(Map<String, String> byActor) {
        ResolveRecipientLanguageUseCase use = (id, hint) -> Mono.just(byActor.getOrDefault(id, "fr"));
        @SuppressWarnings("unchecked")
        ObjectProvider<ResolveRecipientLanguageUseCase> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(use);
        return p;
    }

    @Test
    void aProfileWithoutAttachedLanguageReadsTheDefault() {
        assertThat(ClientProfile.create(UUID.randomUUID(), UUID.randomUUID()).preferredLanguage()).isEqualTo("fr");
    }

    @Test
    void theClientAndTheFreelancerProfilesOfOneAccountShowTheSameLanguage() {
        UUID tenant = UUID.randomUUID();
        UUID account = UUID.randomUUID();
        ActorLanguageEnricher enricher = new ActorLanguageEnricher(provider(Map.of(account.toString(), "en")));
        ClientProfile client = ClientProfile.create(tenant, account);
        FreelancerProfile freelancer = FreelancerProfile.create(tenant, account, List.of(), List.of());

        StepVerifier.create(enricher.enrich(Mono.just(client))).expectNextCount(1).verifyComplete();
        StepVerifier.create(enricher.enrich(Mono.just(freelancer))).expectNextCount(1).verifyComplete();

        assertThat(client.preferredLanguage()).isEqualTo("en").isEqualTo(freelancer.preferredLanguage());
        assertThat(ClientProfileResponse.from(client).preferredLanguage()).isEqualTo("en");
        assertThat(FreelancerProfileResponse.from(freelancer).preferredLanguage()).isEqualTo("en");
    }

    @Test
    void differentAccountsKeepTheirOwnLanguage() {
        UUID tenant = UUID.randomUUID();
        UUID clientAccount = UUID.randomUUID();
        UUID driverAccount = UUID.randomUUID();
        ActorLanguageEnricher enricher = new ActorLanguageEnricher(
                provider(Map.of(clientAccount.toString(), "en", driverAccount.toString(), "fr")));
        ClientProfile a = ClientProfile.create(tenant, clientAccount);
        ClientProfile b = ClientProfile.create(tenant, driverAccount);

        StepVerifier.create(enricher.enrich(Flux.just(a, b))).expectNextCount(2).verifyComplete();

        assertThat(a.preferredLanguage()).isEqualTo("en");
        assertThat(b.preferredLanguage()).isEqualTo("fr");
    }

    @Test
    void withoutI18nWiringProfilesPassThroughUntouched() {
        ClientProfile p = ClientProfile.create(UUID.randomUUID(), UUID.randomUUID());
        StepVerifier.create(ActorLanguageEnricher.none().enrich(Mono.just(p))).expectNext(p).verifyComplete();
        assertThat(p.preferredLanguage()).isEqualTo("fr");
    }

    @Test
    void anEmptyLookupResultKeepsTheProfile() {
        UUID tenant = UUID.randomUUID();
        RelayOperatorProfile p = RelayOperatorProfile.create(tenant, UUID.randomUUID(), UUID.randomUUID(), List.of(), 5);
        ResolveRecipientLanguageUseCase empty = (id, hint) -> Mono.empty();
        @SuppressWarnings("unchecked")
        ObjectProvider<ResolveRecipientLanguageUseCase> prov = mock(ObjectProvider.class);
        when(prov.getIfAvailable()).thenReturn(empty);
        StepVerifier.create(new ActorLanguageEnricher(prov).enrich(Mono.just(p))).expectNext(p).verifyComplete();
    }
}
