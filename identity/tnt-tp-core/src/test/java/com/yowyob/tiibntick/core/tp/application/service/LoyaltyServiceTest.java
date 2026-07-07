package com.yowyob.tiibntick.core.tp.application.service;

import com.yowyob.tiibntick.core.tp.application.port.in.command.EarnLoyaltyPointsCommand;
import com.yowyob.tiibntick.core.tp.application.port.in.command.RedeemLoyaltyPointsCommand;
import com.yowyob.tiibntick.core.tp.application.port.out.LoyaltyAccountRepository;
import com.yowyob.tiibntick.core.tp.application.port.out.TntTpEventPublisher;
import com.yowyob.tiibntick.core.tp.domain.exception.InsufficientLoyaltyPointsException;
import com.yowyob.tiibntick.core.tp.domain.model.LoyaltyAccount;
import com.yowyob.tiibntick.core.tp.domain.model.enums.LoyaltyTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LoyaltyService.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
class LoyaltyServiceTest {

    @Mock private LoyaltyAccountRepository loyaltyAccountRepository;
    @Mock private TntClientProfileService profileService;
    @Mock private TntTpEventPublisher eventPublisher;

    private LoyaltyService loyaltyService;

    private final UUID TENANT_ID    = UUID.randomUUID();
    private final UUID TP_ID        = UUID.randomUUID();
    private final String MISSION_ID = "MISSION-001";

    @BeforeEach
    void setUp() {
        loyaltyService = new LoyaltyService(loyaltyAccountRepository, profileService, eventPublisher);
    }

    @Test
    void earn_shouldCreditPointsAndUpdateTier() {
        //LoyaltyAccount account = LoyaltyAccount.create(TENANT_ID, TP_ID);

        when(loyaltyAccountRepository.existsByThirdPartyId(TENANT_ID, TP_ID)).thenReturn(Mono.just(false));
        when(loyaltyAccountRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(eventPublisher.publish(any())).thenReturn(Mono.empty());
        when(profileService.updateLoyaltyTierOnProfile(any(), any(), anyInt())).thenReturn(Mono.empty());

        EarnLoyaltyPointsCommand command = new EarnLoyaltyPointsCommand(TENANT_ID, TP_ID, 50, MISSION_ID);

        StepVerifier.create(loyaltyService.earn(command))
                .assertNext(result -> {
                    assert result.getAvailablePoints() == 50;
                    assert result.getLifetimePoints() == 50;
                })
                .verifyComplete();
    }

    @Test
    void redeem_withInsufficientPoints_shouldFail() {
        LoyaltyAccount account = LoyaltyAccount.create(TENANT_ID, TP_ID);
        LoyaltyAccount accountWith10 = account.credit(10,
                com.yowyob.tiibntick.core.tp.domain.model.enums.LoyaltyTransactionType.EARNED_FROM_DELIVERY,
                MISSION_ID);

        when(loyaltyAccountRepository.findByThirdPartyId(TENANT_ID, TP_ID))
                .thenReturn(Mono.just(accountWith10));

        RedeemLoyaltyPointsCommand command = new RedeemLoyaltyPointsCommand(
                TENANT_ID, TP_ID, 100, "INV-001");

        StepVerifier.create(loyaltyService.redeem(command))
                .expectError(InsufficientLoyaltyPointsException.class)
                .verify();
    }

    @Test
    void loyaltyTier_shouldProgressCorrectly() {
        LoyaltyAccount account = LoyaltyAccount.create(TENANT_ID, TP_ID);

        assert account.getCurrentTier() == LoyaltyTier.BRONZE;

        LoyaltyAccount silver = account.credit(600,
                com.yowyob.tiibntick.core.tp.domain.model.enums.LoyaltyTransactionType.EARNED_FROM_DELIVERY,
                MISSION_ID);
        assert silver.getCurrentTier() == LoyaltyTier.SILVER;

        LoyaltyAccount gold = silver.credit(1500,
                com.yowyob.tiibntick.core.tp.domain.model.enums.LoyaltyTransactionType.EARNED_FROM_DELIVERY,
                MISSION_ID);
        assert gold.getCurrentTier() == LoyaltyTier.GOLD;
    }
}
