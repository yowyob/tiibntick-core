package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.billing.dsl.application.service.DslCompilerService;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.Money;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.evaluator.DslEvaluator;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.lexer.DslLexer;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.parser.DslParser;
import com.yowyob.tiibntick.core.billing.pricing.application.service.PricingEngineService;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.BillingPolicy;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.PricingRule;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.PolicyStatus;
import com.yowyob.tiibntick.core.billing.pricing.domain.port.in.IBillingPolicyUseCase;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.FreelancerPricingDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.FreelancerPriceQuoteUseCase.Quote;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.FreelancerPriceQuoteUseCase.QuoteInput;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.FreelancerPricingRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.FreelancerPricingPolicy;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The freelancer grid is a {@link BillingPolicy} priced by the REAL pricing engine and DSL parser;
 * only persistence is faked (in-memory), so what is asserted here is the number a client would see.
 */
class FreelancerBillingPricingTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID DELIVERY_PERSON = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    private final Map<UUID, BillingPolicy> store = new ConcurrentHashMap<>();
    private final Map<UUID, FreelancerPricingPolicy> legacyStore = new ConcurrentHashMap<>();
    private FreelancerPricingRepository legacyRepo;
    private GofpFreelancerRepository freelancers;
    private IBillingPolicyUseCase billing;
    private FreelancerPricingPolicyApplicationService policyService;
    private FreelancerPriceQuoteService quoteService;

    @BeforeEach
    void setUp() {
        billing = mock(IBillingPolicyUseCase.class);
        when(billing.findByOwnerActorId(anyString())).thenAnswer(inv ->
                Flux.fromStream(store.values().stream().filter(p -> inv.getArgument(0).equals(p.getOwnerActorId()))));
        when(billing.createPolicy(any())).thenAnswer(inv -> {
            BillingPolicy p = ((BillingPolicy) inv.getArgument(0)).toBuilder()
                    .status(PolicyStatus.DRAFT).createdAt(Instant.now()).updatedAt(Instant.now()).build();
            store.put(p.getId(), p);
            return Mono.just(p);
        });
        when(billing.updatePolicy(any(), any())).thenAnswer(inv -> {
            BillingPolicy in = inv.getArgument(0);
            BillingPolicy p = store.get(in.getId()).toBuilder()
                    .pricingRules(in.getPricingRules()).surchargeRules(in.getSurchargeRules())
                    .updatedAt(Instant.now()).build();
            store.put(p.getId(), p);
            return Mono.just(p);
        });
        when(billing.activatePolicy(any(), any())).thenAnswer(inv -> {
            BillingPolicy p = store.get(inv.getArgument(0)).activate();
            store.put(p.getId(), p);
            return Mono.just(p);
        });

        legacyRepo = mock(FreelancerPricingRepository.class);
        when(legacyRepo.findByDeliveryPersonId(any())).thenAnswer(inv ->
                Mono.justOrEmpty(legacyStore.get((UUID) inv.getArgument(0))));
        when(legacyRepo.save(any())).thenAnswer(inv -> {
            FreelancerPricingPolicy row = inv.getArgument(0);
            legacyStore.put(row.getDeliveryPersonId(), row);
            return Mono.just(row);
        });

        freelancers = mock(GofpFreelancerRepository.class);
        GofpFreelancer f = new GofpFreelancer();
        f.setCoreFreelancerId(DELIVERY_PERSON);
        f.setCoreUserId(USER);
        when(freelancers.findByCoreFreelancerId(DELIVERY_PERSON)).thenReturn(Mono.just(f));
        when(freelancers.findByCoreFreelancerId(org.mockito.ArgumentMatchers.argThat(
                id -> id != null && !id.equals(DELIVERY_PERSON)))).thenReturn(Mono.empty());
        when(freelancers.findById(any(UUID.class))).thenReturn(Mono.empty());

        policyService = new FreelancerPricingPolicyApplicationService(legacyRepo, billing, new FreelancerOwnershipGuard(freelancers));
        PricingEngineService engine = new PricingEngineService(null,
                new DslCompilerService(new DslLexer(), new DslParser()), new DslEvaluator());
        quoteService = new FreelancerPriceQuoteService(billing, engine, legacyRepo);
    }

    private static FreelancerPricingDTO grid() {
        return FreelancerPricingDTO.builder().baseFee(500.0).pricePerKm(100.0).pricePerKg(50.0)
                .pricePerCbm(2000.0).fragileSurcharge(500.0).perishableSurcharge(300.0)
                .waitingFeePerMinute(10.0).currency("FCFA").build();
    }

    private Quote quote(QuoteInput in) {
        return quoteService.quote(List.of(DELIVERY_PERSON, USER), in).block();
    }

    @Test
    @DisplayName("PUT creates an ACTIVE billing policy owned by the delivery person, in the caller's tenant")
    void upsertCreatesActiveGridPolicy() {
        StepVerifier.create(policyService.upsertPolicy(DELIVERY_PERSON, grid(), TENANT))
                .assertNext(dto -> {
                    assertThat(dto.getPolicyId()).isNotNull();
                    assertThat(dto.getSource()).isEqualTo("POLICY");
                    assertThat(dto.getCurrency()).isEqualTo("XAF");
                    assertThat(dto.getBaseFee()).isEqualTo(500.0);
                    assertThat(dto.getFragileSurcharge()).isEqualTo(500.0);
                    assertThat(dto.getWaitingFeePerMinute()).isEqualTo(10.0);
                }).verifyComplete();

        assertThat(store.values()).singleElement().satisfies(p -> {
            assertThat(p.getStatus()).isEqualTo(PolicyStatus.ACTIVE);
            assertThat(p.getTenantId()).isEqualTo(TENANT);
            assertThat(p.getOwnerActorId()).isEqualTo(DELIVERY_PERSON.toString());
            assertThat(p.getTemplateCode()).isEqualTo(FreelancerGridPolicyMapper.GRID_TEMPLATE_CODE);
            assertThat(p.getSurchargeRules()).hasSize(2);
        });
        assertThat(legacyStore.get(DELIVERY_PERSON).getBaseFee()).isEqualTo(500.0);
    }

    @Test
    @DisplayName("a second PUT updates the same policy and keeps the fields it does not mention")
    void partialUpdateMergesIntoSamePolicy() {
        policyService.upsertPolicy(DELIVERY_PERSON, grid(), TENANT).block();
        FreelancerPricingDTO patch = FreelancerPricingDTO.builder().pricePerKm(120.0).currency(null).build();

        FreelancerPricingDTO after = policyService.upsertPolicy(DELIVERY_PERSON, patch, TENANT).block();

        assertThat(store).hasSize(1);
        assertThat(after.getPricePerKm()).isEqualTo(120.0);
        assertThat(after.getBaseFee()).isEqualTo(500.0);
        assertThat(after.getFragileSurcharge()).isEqualTo(500.0);
        assertThat(after.getWaitingFeePerMinute()).isEqualTo(10.0);
    }

    @Test
    @DisplayName("quote = base + km + kg + m³ + fragile surcharge, computed by the engine")
    void quoteCoversEveryGridComponent() {
        policyService.upsertPolicy(DELIVERY_PERSON, grid(), TENANT).block();

        Quote q = quote(new QuoteInput(12, 3, 0.5, true, false, null, null, null, null, null));

        // 500 + 12×100 + 3×50 + 0.5×2000 + 500 fragile
        assertThat(q.totalPrice()).isEqualTo(3350.0);
        assertThat(q.source()).isEqualTo("POLICY");
        assertThat(q.policyId()).isNotNull();
        assertThat(q.currency()).isEqualTo("XAF");
        assertThat(q.breakdown()).startsWith("Base 500").endsWith("= 3350 XAF").contains("Supplément fragile");
    }

    @Test
    @DisplayName("perishable surcharge and waiting time are billed")
    void quoteBillsPerishableAndWaiting() {
        policyService.upsertPolicy(DELIVERY_PERSON, grid(), TENANT).block();

        Quote q = quote(new QuoteInput(10, 0, 0, false, true, 15, null, null, null, null));

        // 500 + 10×100 + 15 min×10 + 300 perishable
        assertThat(q.totalPrice()).isEqualTo(1950.0);
    }

    @Test
    @DisplayName("a delivery that is neither fragile nor perishable pays no surcharge")
    void noSurchargeForStandardParcel() {
        policyService.upsertPolicy(DELIVERY_PERSON, grid(), TENANT).block();

        assertThat(quote(QuoteInput.of(10, 2, 0, false, false)).totalPrice()).isEqualTo(500 + 1000 + 100);
    }

    @Test
    @DisplayName("a policy applied from a billing template under the user's id is honoured")
    void templatePolicyUnderUserIdIsUsed() {
        BillingPolicy tpl = BillingPolicy.builder().id(UUID.randomUUID()).tenantId(TENANT).name("tpl")
                .pricingRules(List.of(PricingRule.builder().id(UUID.randomUUID()).name("flat").priority(1)
                        .basePrice(Money.of(BigDecimal.valueOf(2500), "XAF")).build()))
                .surchargeRules(List.of()).promotions(List.of()).loyaltyRules(List.of()).commissionRules(List.of())
                .specialSurcharges(List.of()).hubStorageRules(List.of()).networkTransitRules(List.of())
                .status(PolicyStatus.ACTIVE).validFrom(LocalDate.now()).ownerActorId(USER.toString())
                .templateCode("FREELANCER_STANDARD").updatedAt(Instant.now()).build();
        store.put(tpl.getId(), tpl);

        Quote q = quote(QuoteInput.of(50, 5, 0, false, false));

        assertThat(q.totalPrice()).isEqualTo(2500.0);
        assertThat(q.policyId()).isEqualTo(tpl.getId());
    }

    @Test
    @DisplayName("a grid saved before the billing migration is still priced (legacy fallback)")
    void legacyGridStillPriced() {
        FreelancerPricingPolicy row = FreelancerPricingPolicy.builder().deliveryPersonId(DELIVERY_PERSON)
                .baseFee(1000.0).pricePerKm(10.0).pricePerKg(0.0).pricePerCbm(0.0)
                .fragileSurcharge(0.0).perishableSurcharge(0.0).currency("FCFA").build();
        legacyStore.put(DELIVERY_PERSON, row);

        Quote q = quote(QuoteInput.of(20, 1, 0, false, false));

        assertThat(q.totalPrice()).isEqualTo(1200.0);
        assertThat(q.source()).isEqualTo("LEGACY_GRID");
        assertThat(policyService.getPolicy(DELIVERY_PERSON).block().getSource()).isEqualTo("LEGACY");

        // the next update migrates it: a billing policy now exists and keeps the legacy values
        FreelancerPricingDTO migrated = policyService.upsertPolicy(DELIVERY_PERSON,
                FreelancerPricingDTO.builder().pricePerKm(15.0).build(), TENANT).block();
        assertThat(migrated.getBaseFee()).isEqualTo(1000.0);
        assertThat(migrated.getSource()).isEqualTo("POLICY");
        assertThat(store).hasSize(1);
        assertThat(quote(QuoteInput.of(20, 1, 0, false, false)).source()).isEqualTo("POLICY");
    }

    @Test
    @DisplayName("without any grid, the platform default barème is priced by the same engine")
    void platformDefaultWhenNothingPublished() {
        Quote q = quote(QuoteInput.of(10, 2, 0, true, false));

        // 500 + 10×100 + 2×50 + 500 fragile
        assertThat(q.totalPrice()).isEqualTo(2100.0);
        assertThat(q.source()).isEqualTo("PLATFORM_DEFAULT");
        assertThat(q.policyId()).isNull();
    }

    @Test
    @DisplayName("a policy the engine cannot match falls back instead of dropping the freelancer")
    void unmatchedPolicyFallsBack() {
        BillingPolicy never = BillingPolicy.builder().id(UUID.randomUUID()).tenantId(TENANT).name("never")
                .pricingRules(List.of(PricingRule.builder().id(UUID.randomUUID()).name("x").priority(1)
                        .conditionExpression("distance >= 100000")
                        .basePrice(Money.of(BigDecimal.valueOf(1), "XAF")).build()))
                .surchargeRules(List.of()).promotions(List.of()).loyaltyRules(List.of()).commissionRules(List.of())
                .specialSurcharges(List.of()).hubStorageRules(List.of()).networkTransitRules(List.of())
                .status(PolicyStatus.ACTIVE).validFrom(LocalDate.now()).ownerActorId(DELIVERY_PERSON.toString())
                .templateCode("X").updatedAt(Instant.now()).build();
        store.put(never.getId(), never);

        Quote q = quote(QuoteInput.of(5, 1, 0, false, false));

        assertThat(q.source()).isEqualTo("PLATFORM_DEFAULT");
        assertThat(q.totalPrice()).isEqualTo(500 + 500 + 50);
    }

    @Test
    @DisplayName("inactive and archived policies are ignored")
    void inactivePolicyIgnored() {
        policyService.upsertPolicy(DELIVERY_PERSON, grid(), TENANT).block();
        BillingPolicy p = store.values().iterator().next();
        store.put(p.getId(), p.deactivate());

        assertThat(quote(QuoteInput.of(1, 1, 0, false, false)).source()).isEqualTo("PLATFORM_DEFAULT");
    }

    // ── ownership guard on the facade ────────────────────────────────────────

    @Test
    @DisplayName("only the owning user (or an admin) may change the grid")
    void ownershipGuard() {
        FreelancerPricingDTO req = grid();

        StepVerifier.create(policyService.upsertPolicyAsCaller(DELIVERY_PERSON, req, TENANT, UUID.randomUUID(), false))
                .expectError(AccessDeniedException.class).verify();
        StepVerifier.create(policyService.upsertPolicyAsCaller(DELIVERY_PERSON, req, TENANT, null, false))
                .expectError(AccessDeniedException.class).verify();
        assertThat(store).isEmpty();

        StepVerifier.create(policyService.upsertPolicyAsCaller(DELIVERY_PERSON, req, TENANT, USER, false))
                .expectNextCount(1).verifyComplete();
        StepVerifier.create(policyService.upsertPolicyAsCaller(DELIVERY_PERSON, req, TENANT, UUID.randomUUID(), true))
                .expectNextCount(1).verifyComplete();
    }

    @Test
    @DisplayName("without a tenant the grid stays in the legacy table and no billing policy is invented")
    void noTenantKeepsLegacyOnly() {
        FreelancerPricingDTO dto = policyService.upsertPolicy(DELIVERY_PERSON, grid(), null).block();

        assertThat(dto.getSource()).isEqualTo("LEGACY");
        assertThat(store).isEmpty();
        assertThat(legacyStore.get(DELIVERY_PERSON).getBaseFee()).isEqualTo(500.0);
    }

    @Test
    @DisplayName("currency aliases normalise to XAF and unknown codes never reach Currency.getInstance")
    void currencyNormalisation() {
        assertThat(FreelancerGridPolicyMapper.normalizeCurrency("FCFA")).isEqualTo("XAF");
        assertThat(FreelancerGridPolicyMapper.normalizeCurrency("F CFA")).isEqualTo("XAF");
        assertThat(FreelancerGridPolicyMapper.normalizeCurrency(null)).isEqualTo("XAF");
        assertThat(FreelancerGridPolicyMapper.normalizeCurrency("nonsense")).isEqualTo("XAF");
        assertThat(FreelancerGridPolicyMapper.normalizeCurrency("eur")).isEqualTo("EUR");
    }
}
