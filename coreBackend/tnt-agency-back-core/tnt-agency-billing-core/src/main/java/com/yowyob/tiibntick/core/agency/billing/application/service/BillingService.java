package com.yowyob.tiibntick.core.agency.billing.application.service;

import com.yowyob.tiibntick.common.exception.TntNotFoundException;
import com.yowyob.tiibntick.common.exception.TntValidationException;
import com.yowyob.tiibntick.core.agency.assignment.application.service.MissionService;
import com.yowyob.tiibntick.core.agency.assignment.domain.AgencyMission;
import com.yowyob.tiibntick.core.agency.assignment.domain.vo.MissionStatus;
import com.yowyob.tiibntick.core.agency.billing.adapter.out.clients.BillingEvaluatorPort;
import com.yowyob.tiibntick.core.agency.billing.adapter.out.clients.BillingPolicyCorePort;
import com.yowyob.tiibntick.core.agency.billing.adapter.out.clients.BillingPricingPort;
import com.yowyob.tiibntick.core.agency.billing.adapter.out.clients.InvoiceGenerationPort;
import com.yowyob.tiibntick.core.agency.billing.adapter.out.persistence.AgencyBillingPolicyR2dbcRepository;
import com.yowyob.tiibntick.core.agency.billing.adapter.out.persistence.InvoiceRecordR2dbcRepository;
import com.yowyob.tiibntick.core.agency.billing.application.mapper.BillingMapper;
import com.yowyob.tiibntick.core.agency.billing.domain.BillingPolicy;
import com.yowyob.tiibntick.core.agency.billing.domain.InvoiceRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillingService {

    private final AgencyBillingPolicyR2dbcRepository billingPolicyRepo;
    private final InvoiceRecordR2dbcRepository invoiceRepo;
    private final BillingPolicyCorePort billingPolicyCore;
    private final BillingPricingPort billingPricing;
    private final BillingEvaluatorPort billingEvaluator;
    private final InvoiceGenerationPort invoiceGeneration;
    private final MissionService missionService;

    @Transactional
    public Mono<BillingPolicy> createPolicy(CreatePolicyInput input) {
        Instant now = Instant.now();
        BillingPolicy policy = BillingPolicy.create(
                UUID.randomUUID(), input.tenantId(), input.agencyId(), input.name(),
                input.description(), input.currency(), input.basePrice(),
                input.pricePerKm(), input.pricePerKg(), input.minPrice(), now
        );
        return billingPolicyCore.createPolicy(new BillingPolicyCorePort.CreatePolicyRequest(
                        input.tenantId(), input.agencyId(), input.name(), input.description(),
                        input.currency(), input.basePrice(), input.pricePerKm(),
                        input.pricePerKg(), input.minPrice()
                ))
                .defaultIfEmpty(new BillingPolicyCorePort.PolicyView(null, input.name(), "DRAFT"))
                .map(view -> {
                    if (view.id() != null) {
                        policy.linkCorePolicy(view.id(), now);
                    }
                    return policy;
                })
                .flatMap(p -> billingPolicyRepo.save(BillingMapper.toEntity(p)))
                .map(BillingMapper::toDomain);
    }

    @Transactional
    public Mono<BillingPolicy> activatePolicy(UUID tenantId, UUID policyId) {
        return requirePolicy(tenantId, policyId)
                .flatMap(policy -> {
                    policy.activate(Instant.now());
                    UUID corePolicyId = resolveCorePolicyId(policy);
                    return billingPolicyCore.activatePolicy(corePolicyId)
                            .onErrorResume(e -> Mono.empty())
                            .then(billingPolicyRepo.save(BillingMapper.toEntity(policy)))
                            .map(BillingMapper::toDomain);
                });
    }

    @Transactional
    public Mono<BillingPolicy> archivePolicy(UUID tenantId, UUID policyId) {
        return requirePolicy(tenantId, policyId)
                .flatMap(policy -> {
                    policy.archive(Instant.now());
                    UUID corePolicyId = resolveCorePolicyId(policy);
                    return billingPolicyCore.archivePolicy(corePolicyId)
                            .onErrorResume(e -> Mono.empty())
                            .then(billingPolicyRepo.save(BillingMapper.toEntity(policy)))
                            .map(BillingMapper::toDomain);
                });
    }

    public Flux<BillingPolicy> listByAgency(UUID tenantId, UUID agencyId) {
        return billingPolicyRepo.findByAgencyIdAndTenantId(agencyId, tenantId)
                .map(BillingMapper::toDomain);
    }

    public Mono<BillingPolicy> getPolicy(UUID tenantId, UUID policyId) {
        return requirePolicy(tenantId, policyId);
    }

    public Mono<BillingPolicy> findActiveByAgency(UUID tenantId, UUID agencyId) {
        return billingPolicyRepo.findActiveByAgencyIdAndTenantId(agencyId, tenantId)
                .map(BillingMapper::toDomain)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "BILLING_POLICY_NOT_FOUND",
                        "Aucune politique tarifaire active pour cette agence"
                )));
    }

    public Mono<EstimateResult> estimate(UUID tenantId, UUID agencyId, double distanceKm, double weightKg) {
        return findActiveByAgency(tenantId, agencyId)
                .flatMap(policy -> billingPricing.evaluate(new BillingPricingPort.PricingRequest(
                                tenantId, agencyId, resolveCorePolicyId(policy), null, distanceKm, weightKg
                        ))
                        .map(pricing -> new EstimateResult(pricing.amount(), pricing.currency(), policy.getName()))
                        .switchIfEmpty(
                                billingEvaluator.evaluate(new BillingEvaluatorPort.BillingEvaluationRequest(
                                                tenantId, agencyId, resolveCorePolicyId(policy),
                                                distanceKm, weightKg, 0.0, null
                                        ))
                                        .map(result -> {
                                            BigDecimal amount = result.amount().compareTo(BigDecimal.ZERO) > 0
                                                    ? result.amount()
                                                    : policy.estimate(distanceKm, weightKg);
                                            String currency = result.currency() != null
                                                    ? result.currency()
                                                    : policy.getCurrency();
                                            return new EstimateResult(amount, currency, policy.getName());
                                        })
                        ));
    }

    @Transactional
    public Mono<InvoiceRecord> generateInvoice(UUID tenantId, UUID agencyId, UUID missionId) {
        Instant now = Instant.now();
        return missionService.getById(tenantId, missionId)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "MISSION_NOT_FOUND", "Mission introuvable: " + missionId)))
                .flatMap(mission -> {
                    if (MissionStatus.DELIVERED != mission.getStatus()) {
                        return Mono.error(new TntValidationException(
                                "Only a DELIVERED mission can be invoiced"
                        ));
                    }
                    if (!agencyId.equals(mission.getAgencyId())) {
                        return Mono.error(new TntValidationException(
                                "Mission does not belong to requested agency"
                        ));
                    }
                    return findActiveByAgency(tenantId, agencyId).defaultIfEmpty(null)
                            .flatMap(policy -> resolveAmount(tenantId, agencyId, mission, policy)
                                    .flatMap(amount -> {
                                        String currency = policy != null
                                                ? policy.getCurrency()
                                                : (mission.getQuotedCurrency() != null ? mission.getQuotedCurrency() : "XAF");
                                        return invoiceGeneration.generate(new InvoiceGenerationPort.InvoiceGenerationRequest(
                                                        tenantId, agencyId, missionId.toString(), null,
                                                        amount, currency, "Facture mission " + missionId
                                                ))
                                                .defaultIfEmpty(new InvoiceGenerationPort.GeneratedInvoice(
                                                        null,
                                                        "INV-" + missionId.toString().substring(0, 8).toUpperCase(),
                                                        amount,
                                                        currency,
                                                        "GENERATED",
                                                        null
                                                ))
                                                .flatMap(core -> {
                                                    InvoiceRecord invoice = InvoiceRecord.generate(
                                                            UUID.randomUUID(), tenantId, agencyId, missionId,
                                                            core.invoiceNumber() != null
                                                                    ? core.invoiceNumber()
                                                                    : "INV-" + missionId.toString().substring(0, 8).toUpperCase(),
                                                            core.amount() != null ? core.amount() : amount,
                                                            core.currency() != null ? core.currency() : currency,
                                                            parseCoreInvoiceId(core.coreInvoiceId()), now
                                                    );
                                                    return invoiceRepo.save(BillingMapper.toEntity(invoice))
                                                            .map(BillingMapper::toDomain);
                                                });
                                    }));
                });
    }

    public Flux<InvoiceRecord> listInvoices(UUID tenantId, UUID agencyId) {
        return invoiceRepo.findByAgencyIdAndTenantId(agencyId, tenantId)
                .map(BillingMapper::toDomain);
    }

    public Mono<InvoiceRecord> getInvoice(UUID tenantId, UUID invoiceId) {
        return invoiceRepo.findByIdAndTenantId(invoiceId, tenantId)
                .map(BillingMapper::toDomain)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "INVOICE_NOT_FOUND", "Facture introuvable: " + invoiceId
                )));
    }

    public Mono<InvoiceDownloadResult> downloadInvoice(UUID tenantId, UUID invoiceId) {
        return getInvoice(tenantId, invoiceId)
                .flatMap(invoice -> {
                    if (invoice.getCoreInvoiceId() == null) {
                        return Mono.error(new TntValidationException(
                                "PDF indisponible : cette facture n'est pas liée au moteur de facturation Core. "
                                        + "Régénérez la facture une fois le billing Core joignable."
                        ));
                    }
                    return invoiceGeneration.getPdfUrl(tenantId, invoice.getCoreInvoiceId().toString())
                            .switchIfEmpty(Mono.error(new TntNotFoundException(
                                    "PDF_UNAVAILABLE",
                                    "Le PDF de cette facture est temporairement indisponible côté facturation. Réessayez dans un instant."
                            )))
                            .map(url -> new InvoiceDownloadResult(invoice.getId(), url));
                });
    }

    private Mono<BillingPolicy> requirePolicy(UUID tenantId, UUID policyId) {
        return billingPolicyRepo.findByIdAndTenantId(policyId, tenantId)
                .map(BillingMapper::toDomain)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "BILLING_POLICY_NOT_FOUND",
                        "Politique tarifaire introuvable: " + policyId
                )));
    }

    private Mono<BigDecimal> resolveAmount(
            UUID tenantId,
            UUID agencyId,
            AgencyMission mission,
            BillingPolicy policy) {
        if (mission.getQuotedAmount() != null && mission.getQuotedAmount().compareTo(BigDecimal.ZERO) > 0) {
            return Mono.just(mission.getQuotedAmount());
        }
        if (policy == null) {
            return Mono.error(new TntValidationException(
                    "Impossible de facturer : ni montant devisé ni politique tarifaire active."
            ));
        }
        double weightKg = mission.getWeightKg() != null && mission.getWeightKg() > 0 ? mission.getWeightKg() : 1.0;
        if (mission.getDistanceKm() == null || mission.getDistanceKm() <= 0) {
            return Mono.error(new TntValidationException(
                    "Distance de livraison manquante pour calculer le montant. "
                            + "Renseignez la distance sur la mission ou un montant devisé."
            ));
        }
        double distanceKm = mission.getDistanceKm();
        UUID corePolicyId = resolveCorePolicyId(policy);
        return billingPricing.evaluate(new BillingPricingPort.PricingRequest(
                        tenantId, agencyId, corePolicyId, mission.getId(), distanceKm, weightKg
                ))
                .map(BillingPricingPort.PricingResult::amount)
                .switchIfEmpty(
                        billingEvaluator.evaluate(new BillingEvaluatorPort.BillingEvaluationRequest(
                                        tenantId, agencyId, corePolicyId, distanceKm, weightKg, 0.0, null
                                ))
                                .map(result -> result.amount().compareTo(BigDecimal.ZERO) > 0
                                        ? result.amount()
                                        : policy.estimate(distanceKm, weightKg))
                );
    }

    private UUID resolveCorePolicyId(BillingPolicy policy) {
        return policy.getCorePolicyId() != null ? policy.getCorePolicyId() : policy.getId();
    }

    private static UUID parseCoreInvoiceId(String coreInvoiceId) {
        if (coreInvoiceId == null || coreInvoiceId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(coreInvoiceId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public record CreatePolicyInput(
            UUID tenantId,
            UUID agencyId,
            String name,
            String description,
            String currency,
            BigDecimal basePrice,
            BigDecimal pricePerKm,
            BigDecimal pricePerKg,
            BigDecimal minPrice
    ) {}

    public record EstimateResult(BigDecimal amount, String currency, String policyName) {}

    public record InvoiceDownloadResult(UUID invoiceId, String pdfUrl) {}
}
