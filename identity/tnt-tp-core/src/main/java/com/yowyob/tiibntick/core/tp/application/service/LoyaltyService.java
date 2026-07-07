package com.yowyob.tiibntick.core.tp.application.service;

import com.yowyob.tiibntick.core.tp.application.port.in.command.EarnLoyaltyPointsCommand;
import com.yowyob.tiibntick.core.tp.application.port.in.command.RedeemLoyaltyPointsCommand;
import com.yowyob.tiibntick.core.tp.application.port.out.LoyaltyAccountRepository;
import com.yowyob.tiibntick.core.tp.application.port.out.TntTpEventPublisher;
import com.yowyob.tiibntick.core.tp.domain.event.TntTpDomainEvents.LoyaltyPointsEarnedEvent;
import com.yowyob.tiibntick.core.tp.domain.event.TntTpDomainEvents.LoyaltyPointsRedeemedEvent;
import com.yowyob.tiibntick.core.tp.domain.model.LoyaltyAccount;
import com.yowyob.tiibntick.core.tp.domain.model.enums.LoyaltyTransactionType;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Application service for loyalty program management.
 * Creates accounts on first interaction (lazy initialization).
 *
 * @author MANFOUO Braun
 */
@Service
public class LoyaltyService {

    private final LoyaltyAccountRepository loyaltyAccountRepository;
    private final TntClientProfileService profileService;
    private final TntTpEventPublisher eventPublisher;

    public LoyaltyService(
            LoyaltyAccountRepository loyaltyAccountRepository,
            TntClientProfileService profileService,
            TntTpEventPublisher eventPublisher) {
        this.loyaltyAccountRepository = loyaltyAccountRepository;
        this.profileService = profileService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Credits loyalty points after a successful delivery.
     * Creates the loyalty account if it does not exist yet.
     */
    @RequirePermission(resource = "billing", action = "write")
    public Mono<LoyaltyAccount> earn(EarnLoyaltyPointsCommand command) {
        return getOrCreateAccount(command.tenantId(), command.thirdPartyId())
                .flatMap(account -> {
                    LoyaltyAccount updated = account.credit(
                            command.points(),
                            LoyaltyTransactionType.EARNED_FROM_DELIVERY,
                            command.missionId());
                    return loyaltyAccountRepository.save(updated)
                            .flatMap(saved -> {
                                var event = new LoyaltyPointsEarnedEvent(
                                        saved.getId(), saved.getTenantId(), saved.getThirdPartyId(),
                                        command.points(), saved.getAvailablePoints(),
                                        command.missionId(), Instant.now());
                                return eventPublisher.publish(event)
                                        .then(profileService.updateLoyaltyTierOnProfile(
                                                command.tenantId(), command.thirdPartyId(),
                                                saved.getLifetimePoints()))
                                        .thenReturn(saved);
                            });
                });
    }

    /**
     * Redeems loyalty points for a discount on a delivery invoice.
     */
    @RequirePermission(resource = "billing", action = "write")
    public Mono<LoyaltyAccount> redeem(RedeemLoyaltyPointsCommand command) {
        return loyaltyAccountRepository.findByThirdPartyId(command.tenantId(), command.thirdPartyId())
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "No loyalty account for thirdPartyId=" + command.thirdPartyId())))
                .flatMap(account -> {
                    LoyaltyAccount updated = account.redeem(command.points(), command.invoiceId());
                    return loyaltyAccountRepository.save(updated)
                            .flatMap(saved -> {
                                var event = new LoyaltyPointsRedeemedEvent(
                                        saved.getId(), saved.getTenantId(), saved.getThirdPartyId(),
                                        command.points(), saved.getAvailablePoints(),
                                        command.invoiceId(), Instant.now());
                                return eventPublisher.publish(event).thenReturn(saved);
                            });
                });
    }

    @RequirePermission(resource = "billing", action = "read")
    public Mono<LoyaltyAccount> getByThirdPartyId(UUID tenantId, UUID thirdPartyId) {
        return getOrCreateAccount(tenantId, thirdPartyId);
    }

    private Mono<LoyaltyAccount> getOrCreateAccount(UUID tenantId, UUID thirdPartyId) {
        return loyaltyAccountRepository.existsByThirdPartyId(tenantId, thirdPartyId)
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return loyaltyAccountRepository.findByThirdPartyId(tenantId, thirdPartyId);
                    }
                    LoyaltyAccount newAccount = LoyaltyAccount.create(tenantId, thirdPartyId);
                    return loyaltyAccountRepository.save(newAccount);
                });
    }
}
