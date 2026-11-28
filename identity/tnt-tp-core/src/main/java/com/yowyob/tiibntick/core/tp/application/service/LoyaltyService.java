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
import org.springframework.transaction.annotation.Transactional;

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
    @Transactional
    public Mono<LoyaltyAccount> earn(EarnLoyaltyPointsCommand command) {
        return credit(command);
    }

    /**
     * Credits the points earned by a completed delivery on behalf of the platform itself.
     *
     * <p>Trusted system path, without {@code @RequirePermission}: it is called by the Kafka consumer of
     * {@code DeliveryDelivered} ({@code tnt.delivery.events}), where no user security context exists (the annotated
     * {@link #earn} would always be refused there). Never expose it behind a web route.
     *
     * <p>Idempotent per delivery (lot L3): the movement is written first in {@code tnt_loyalty_transactions} with
     * {@code external_ref = missionId}, unique per account and type. A redelivered message finds it and credits
     * nothing: the result is then empty.
     */
    @Transactional
    public Mono<LoyaltyAccount> creditCompletedDelivery(EarnLoyaltyPointsCommand command) {
        if (command.deliveryId() == null || command.deliveryId().isBlank()) {
            return Mono.error(new IllegalArgumentException("missionId is required to credit a completed delivery"));
        }
        return getOrCreateAccount(command.tenantId(), command.thirdPartyId())
                .flatMap(account -> loyaltyAccountRepository.recordTransactionOnce(account.getId(), command.points(),
                        LoyaltyTransactionType.EARNED_FROM_DELIVERY.name(), command.deliveryId()))
                .flatMap(recorded -> Boolean.TRUE.equals(recorded) ? credit(command) : Mono.empty());
    }

    private Mono<LoyaltyAccount> credit(EarnLoyaltyPointsCommand command) {
        return getOrCreateAccount(command.tenantId(), command.thirdPartyId())
                .flatMap(account -> {
                    LoyaltyAccount updated = account.credit(
                            command.points(),
                            LoyaltyTransactionType.EARNED_FROM_DELIVERY,
                            command.deliveryId());
                    return loyaltyAccountRepository.save(updated)
                            .flatMap(saved -> {
                                var event = new LoyaltyPointsEarnedEvent(
                                        saved.getId(), saved.getTenantId(), saved.getThirdPartyId(),
                                        command.points(), saved.getAvailablePoints(),
                                        command.deliveryId(), Instant.now());
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
    @Transactional
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

    @RequirePermission(resource = "loyalty", action = "read")
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
