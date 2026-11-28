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
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
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

        freelancers = mock(GofpFreelancerRepository.class);
        GofpFreelancer f = new GofpFreelancer();
        f.setCoreFreelancerId(DELIVERY_PERSON);
        f.setCoreUserId(USER);
        when(freelancers.findByCoreFreelancerId(DELIVERY_PERSON)).thenReturn(Mono.just(f));
        when(freelancers.findByCoreFreelancerId(org.mockito.ArgumentMatchers.argThat(
                id -> id != null && !id.equals(DELIVERY_PERSON)))).thenReturn(Mono.empty());
        when(freelancers.findById(any(UUID.class))).thenReturn(Mono.empty());

        policyService = new FreelancerPricingPolicyApplicationService(billing, new FreelancerOwnershipGuard(freelancers));
        PricingEngineService engine = new PricingEngineService(null,
                new DslCompilerService(new DslLexer(), new DslParser()), new DslEvaluator());
        quoteService = new FreelancerPriceQuoteService(billing, engine);
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
    @DisplayName("C-56 — a parcel both fragile AND perishable pays both surcharges (personal grid)")
    void fragileAndPerishableBothBilled() {
        policyService.upsertPolicy(DELIVERY_PERSON, grid(), TENANT).block();

        Quote q = quote(new QuoteInput(0, 1, 0, true, true, null, null, null, null, null));

        // 500 + 1×50 = 550 → 1000 minimum (inherited from the moto platform grid, D-058) + 500 fragile + 300 perishable
        assertThat(q.totalPrice()).isEqualTo(1800.0);
        assertThat(q.breakdown()).contains("Supplément fragile").contains("Supplément périssable");
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "fragile={0} perishable={1} → {2} XAF")
    @org.junit.jupiter.params.provider.CsvSource({"false,false,1000", "true,false,1500", "false,true,1500", "true,true,2000"})
    @DisplayName("C-56b — moto platform grid, 0 km, 1 kg: the 4 fragile × perishable combinations")
    void motoPlatformGridFourCombinations(boolean fragile, boolean perishable, double expected) {
        // Moto grid of D-058: 600 + 1×75 = 675 → 1000 minimum, then +500 fragile and/or +500 perishable.
        // Liquid and insured have no input in QuoteInput: they cannot change the price (product rule, C-46).
        Quote q = quoteService.platformQuote("moto",
                new QuoteInput(0, 1, 0, fragile, perishable, null, null, null, null, null)).block();
        assertThat(q.totalPrice()).isEqualTo(expected);
    }

    @Test
    @DisplayName("C-56 — platform grid of each vehicle: fragile AND perishable surcharges both billed")
    void platformGridBillsBothSurcharges() {
        Quote moto = quoteService.platformQuote("moto", new QuoteInput(0, 1, 0, true, true, null, null, null, null, null)).block();
        Quote taxi = quoteService.platformQuote("taxi", new QuoteInput(0, 1, 0, true, true, null, null, null, null, null)).block();

        // D-058 grids: moto 600 + 1×75 → 1000 minimum + 500 + 500; taxi 1000 + 1×25 → 2000 minimum + 750 + 750
        assertThat(moto.totalPrice()).isEqualTo(2000.0);
        assertThat(taxi.totalPrice()).isEqualTo(3500.0);
        assertThat(moto.breakdown()).contains("Supplément périssable");
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
    @DisplayName("without any grid, the platform default barème is priced by the same engine")
    void platformDefaultWhenNothingPublished() {
        Quote q = quote(QuoteInput.of(10, 2, 0, true, false));

        // moto grid (D-058): 600 + 10×100 + 2×75 + 500 fragile
        assertThat(q.totalPrice()).isEqualTo(2250.0);
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
        assertThat(q.totalPrice()).isEqualTo(600 + 500 + 75);
    }

    @Test
    @DisplayName("inactive and archived policies are ignored")
    void inactivePolicyIgnored() {
        policyService.upsertPolicy(DELIVERY_PERSON, grid(), TENANT).block();
        BillingPolicy p = store.values().iterator().next();
        store.put(p.getId(), p.deactivate());

        assertThat(quote(QuoteInput.of(1, 1, 0, false, false)).source()).isEqualTo("PLATFORM_DEFAULT");
    }

    // ── D-058: platform grid as the starting proposal, minimum price ────────

    @Test
    @DisplayName("first PUT starts from the platform grid of the courier's vehicle: unsent fields keep the proposal")
    void firstGridStartsFromThePlatformGridOfTheVehicle() {
        var vehicles = mock(com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.FreelancerVehicleRepository.class);
        var van = new com.yowyob.tiibntick.core.gofreelancer.domain.model.FreelancerVehicle();
        van.setVehicleType("VAN");
        when(vehicles.findByFreelancerId(DELIVERY_PERSON)).thenReturn(Mono.just(van));
        policyService.setVehicleRepository(vehicles);

        FreelancerPricingDTO after = policyService.upsertPolicy(DELIVERY_PERSON,
                FreelancerPricingDTO.builder().pricePerKm(250.0).currency(null).build(), TENANT).block();

        assertThat(after.getPricePerKm()).isEqualTo(250.0);
        assertThat(after.getBaseFee()).isEqualTo(2500.0);
        assertThat(after.getPricePerKg()).isEqualTo(10.0);
        assertThat(after.getMinimumFee()).isEqualTo(5000.0);
        assertThat(after.getWaitingFeePerMinute()).isEqualTo(75.0);
        assertThat(after.getFragileSurcharge()).isEqualTo(1000.0);
    }

    @Test
    @DisplayName("without a known vehicle the first PUT starts from the moto platform grid")
    void firstGridWithoutVehicleStartsFromMoto() {
        FreelancerPricingDTO after = policyService.upsertPolicy(DELIVERY_PERSON,
                FreelancerPricingDTO.builder().pricePerKm(120.0).currency(null).build(), TENANT).block();

        assertThat(after.getBaseFee()).isEqualTo(600.0);
        assertThat(after.getPricePerKg()).isEqualTo(75.0);
        assertThat(after.getMinimumFee()).isEqualTo(1000.0);
    }

    @Test
    @DisplayName("the minimum price raises a short delivery; a courier may drop it with minimumFee = 0")
    void minimumPriceRaisesShortDeliveries() {
        // moto platform grid: 600 + 1 × 100 + 0 kg = 700 → raised to the 1000 minimum
        assertThat(quote(QuoteInput.of(1, 0, 0, false, false)).totalPrice()).isEqualTo(1000.0);

        FreelancerPricingDTO own = grid();
        own.setMinimumFee(800.0);
        policyService.upsertPolicy(DELIVERY_PERSON, own, TENANT).block();
        // own grid: 500 + 1 × 100 = 600 → raised to the courier's own 800 minimum
        assertThat(quote(QuoteInput.of(1, 0, 0, false, false)).totalPrice()).isEqualTo(800.0);

        policyService.upsertPolicy(DELIVERY_PERSON,
                FreelancerPricingDTO.builder().minimumFee(0.0).currency(null).build(), TENANT).block();
        assertThat(quote(QuoteInput.of(1, 0, 0, false, false)).totalPrice()).isEqualTo(600.0);
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
    @DisplayName("without a tenant the grid policy is created in the system tenant (the legacy table is not written)")
    void noTenantUsesSystemTenant() {
        FreelancerPricingDTO dto = policyService.upsertPolicy(DELIVERY_PERSON, grid(), null).block();

        assertThat(dto.getSource()).isEqualTo("POLICY");
        assertThat(store.values()).singleElement().satisfies(p -> {
            assertThat(p.getTenantId()).isEqualTo(TenantContextHolder.SYSTEM_TENANT);
            assertThat(p.getStatus()).isEqualTo(PolicyStatus.ACTIVE);
        });
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
