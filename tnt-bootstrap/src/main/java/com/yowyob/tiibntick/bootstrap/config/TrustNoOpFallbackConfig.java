package com.yowyob.tiibntick.bootstrap.config;

import com.yowyob.tiibntick.bootstrap.config.trustnoop.NoOpActorDidAnchorPort;
import com.yowyob.tiibntick.bootstrap.config.trustnoop.NoOpBadgeAnchorPort;
import com.yowyob.tiibntick.bootstrap.config.trustnoop.NoOpBillingPolicyAnchorPort;
import com.yowyob.tiibntick.bootstrap.config.trustnoop.NoOpBlockchainAuditPort;
import com.yowyob.tiibntick.bootstrap.config.trustnoop.NoOpBlockchainProofPort;
import com.yowyob.tiibntick.bootstrap.config.trustnoop.NoOpDeliveryProofAnchorPort;
import com.yowyob.tiibntick.bootstrap.config.trustnoop.NoOpFreelancerOrgDidAnchorPort;
import com.yowyob.tiibntick.bootstrap.config.trustnoop.NoOpGeofenceAnchorPort;
import com.yowyob.tiibntick.bootstrap.config.trustnoop.NoOpPaymentAnchorPort;
import com.yowyob.tiibntick.core.actor.application.port.out.IActorDidAnchorPort;
import com.yowyob.tiibntick.core.actor.application.port.out.IBadgeAnchorPort;
import com.yowyob.tiibntick.core.billing.pricing.domain.port.out.BillingPolicyAnchorPort;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IPaymentAnchorPort;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryProofAnchorPort;
import com.yowyob.tiibntick.core.dispute.application.port.outbound.IBlockchainProofPort;
import com.yowyob.tiibntick.core.incident.port.outbound.IBlockchainAuditPort;
import com.yowyob.tiibntick.core.organization.application.port.out.FreelancerOrgDidAnchorPort;
import com.yowyob.tiibntick.core.realtime.application.port.out.IGeofenceAnchorPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Trust blockchain-anchoring no-op fallbacks — active only when
 * {@code tnt.trust.enabled=false}.
 *
 * <p>{@code tnt-trust-core}'s {@code TntTrustAutoConfiguration} is entirely
 * switched off by {@code tnt.trust.enabled=false} (§15.1 of
 * {@code TNT_CORE_Connexion_Trust_Module.md}), so none of its port adapters
 * (e.g. {@code DeliveryProofAnchorAdapter}) exist. The 8 calling modules below
 * inject their outbound trust port as a required constructor dependency —
 * without a fallback, disabling trust makes {@code tnt-bootstrap} fail to
 * start with {@code UnsatisfiedDependencyException} instead of degrading
 * gracefully as the design doc promises.
 *
 * <p>Each {@code @Bean} is {@link ConditionalOnMissingBean} so a real
 * {@code tnt-trust-core} adapter (when {@code tnt.trust.enabled=true}) always
 * takes precedence — these only fill the gap left by disabling the module.
 * Gating the whole class on {@code tnt.trust.enabled=false} (rather than
 * relying on {@code @ConditionalOnMissingBean} alone) is deliberate: if
 * trust is enabled but its adapter bean is missing for some other, unrelated
 * reason, that is a real bug that should fail loudly, not be silently
 * papered over by a no-op.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "tnt.trust", name = "enabled", havingValue = "false")
public class TrustNoOpFallbackConfig {

    public TrustNoOpFallbackConfig() {
        log.warn("tnt.trust.enabled=false — tnt-trust-core is disabled, all blockchain "
                + "anchoring ports are wired to no-op fallbacks (no anchoring will occur)");
    }

    @Bean
    @ConditionalOnMissingBean
    public DeliveryProofAnchorPort deliveryProofAnchorPort() {
        return new NoOpDeliveryProofAnchorPort();
    }

    @Bean
    @ConditionalOnMissingBean
    public IBlockchainAuditPort blockchainAuditPort() {
        return new NoOpBlockchainAuditPort();
    }

    @Bean
    @ConditionalOnMissingBean
    public IBlockchainProofPort blockchainProofPort() {
        return new NoOpBlockchainProofPort();
    }

    @Bean
    @ConditionalOnMissingBean
    public BillingPolicyAnchorPort billingPolicyAnchorPort() {
        return new NoOpBillingPolicyAnchorPort();
    }

    @Bean
    @ConditionalOnMissingBean
    public IPaymentAnchorPort paymentAnchorPort() {
        return new NoOpPaymentAnchorPort();
    }

    @Bean
    @ConditionalOnMissingBean
    public IGeofenceAnchorPort geofenceAnchorPort() {
        return new NoOpGeofenceAnchorPort();
    }

    @Bean
    @ConditionalOnMissingBean
    public FreelancerOrgDidAnchorPort freelancerOrgDidAnchorPort() {
        return new NoOpFreelancerOrgDidAnchorPort();
    }

    @Bean
    @ConditionalOnMissingBean
    public IActorDidAnchorPort actorDidAnchorPort() {
        return new NoOpActorDidAnchorPort();
    }

    @Bean
    @ConditionalOnMissingBean
    public IBadgeAnchorPort badgeAnchorPort() {
        return new NoOpBadgeAnchorPort();
    }
}
