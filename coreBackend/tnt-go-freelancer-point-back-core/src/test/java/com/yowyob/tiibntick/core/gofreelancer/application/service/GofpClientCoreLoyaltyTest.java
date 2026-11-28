package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.actor.application.port.out.IClientProfileRepository;
import com.yowyob.tiibntick.core.actor.domain.model.ClientProfile;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpClientRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpUserRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpClient;
import com.yowyob.tiibntick.core.tp.application.port.in.command.EarnLoyaltyPointsCommand;
import com.yowyob.tiibntick.core.tp.application.service.LoyaltyService;
import com.yowyob.tiibntick.core.tp.domain.model.LoyaltyAccount;
import com.yowyob.tiibntick.core.tp.domain.model.enums.LoyaltyTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Lot L3 (H3): the loyalty tier, the order count and the rating of a Go client come from the core. */
@ExtendWith(MockitoExtension.class)
class GofpClientCoreLoyaltyTest {

    @Mock private GofpClientRepository clientRepository;
    @Mock private GofpUserRepository userRepository;
    @Mock private LoyaltyService loyaltyService;
    @Mock private IClientProfileRepository clientProfiles;
    @Mock private TenantContextHolder tenantContextHolder;

    private GofpClientService service;
    private static final UUID TENANT = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();
    private GofpClient client;

    @BeforeEach
    void setUp() {
        service = new GofpClientService(clientRepository, userRepository, loyaltyService, clientProfiles,
                tenantContextHolder, null);
        client = new GofpClient();
        client.setId(UUID.randomUUID());
        client.setCoreClientId(UUID.randomUUID());
        client.setCoreUserId(actor);
        lenient().when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(TENANT));
        lenient().when(userRepository.findByCoreUserId(actor)).thenReturn(Mono.empty());
    }

    private static LoyaltyAccount account(UUID actor, int lifetime) {
        return LoyaltyAccount.reconstitute(UUID.randomUUID(), TENANT, actor, lifetime, lifetime, 0, 0,
                LoyaltyTier.fromPoints(lifetime), List.of(), Instant.now(), Instant.now());
    }

    @Test
    void read_tenOrdersAreOneHundredPoints_bronzeWithCoreThresholds() {
        when(clientRepository.findById(client.getId())).thenReturn(Mono.just(client));
        when(loyaltyService.getByThirdPartyId(TENANT, actor)).thenReturn(Mono.just(account(actor, 100)));
        ClientProfile profile = ClientProfile.create(TENANT, actor);
        profile = profile.withRating(profile.rating().addRating(4.0));
        when(clientProfiles.findByActorId(TENANT, actor)).thenReturn(Mono.just(profile));

        StepVerifier.create(service.findById(client.getId()))
                .assertNext(c -> {
                    assertThat(c.getLoyaltyStatus()).isEqualTo(LoyaltyTier.BRONZE); // was SILVER (>=10) in Go
                    assertThat(c.getTotalOrders()).isEqualTo(10);
                    assertThat(c.getRating()).isEqualTo(4.0);
                })
                .verifyComplete();
    }

    @Test
    void read_whenTheCoreRefusesTheLoyaltyRead_theClientIsStillReturned() {
        when(clientRepository.findById(client.getId())).thenReturn(Mono.just(client));
        when(loyaltyService.getByThirdPartyId(TENANT, actor)).thenReturn(Mono.error(new SecurityException("denied")));
        when(clientProfiles.findByActorId(TENANT, actor)).thenReturn(Mono.empty());

        StepVerifier.create(service.findById(client.getId()))
                .assertNext(c -> assertThat(c.getLoyaltyStatus()).isNull())
                .verifyComplete();
    }

    /** Lot L3 (C4): deprecated no-op, nothing is credited (the core credits the completed delivery once). */
    @Test
    void recordOrder_isANoOpThatCreditsNothing() {
        when(clientRepository.findByCoreClientId(client.getCoreClientId())).thenReturn(Mono.just(client));
        when(loyaltyService.getByThirdPartyId(TENANT, actor)).thenReturn(Mono.just(account(actor, 10)));
        when(clientProfiles.findByActorId(TENANT, actor)).thenReturn(Mono.empty());

        StepVerifier.create(service.recordOrder(client.getCoreClientId()))
                .assertNext(c -> assertThat(c.getTotalOrders()).isEqualTo(1))
                .verifyComplete();

        verify(loyaltyService, never()).earn(any());
        verify(clientRepository, never()).save(any());
    }

    @Test
    void manualTier_isRefused() {
        when(clientRepository.findById(client.getId())).thenReturn(Mono.just(client));

        StepVerifier.create(service.updateLoyaltyStatus(client.getId(), LoyaltyTier.GOLD))
                .expectError(IllegalStateException.class)
                .verify();
        verifyNoInteractions(loyaltyService);
    }
}
