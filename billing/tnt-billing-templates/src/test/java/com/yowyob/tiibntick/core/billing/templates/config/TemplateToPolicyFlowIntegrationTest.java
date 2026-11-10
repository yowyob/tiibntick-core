package com.yowyob.tiibntick.core.billing.templates.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yowyob.tiibntick.core.billing.dsl.application.service.DslCompilerService;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.DeliveryPriority;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PackageType;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PricingContext;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.evaluator.DslEvaluator;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.lexer.DslLexer;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.parser.DslParser;
import com.yowyob.tiibntick.core.billing.pricing.application.service.BillingPolicyService;
import com.yowyob.tiibntick.core.billing.pricing.application.service.PricingEngineService;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.BillingPolicy;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.PolicyStatus;
import com.yowyob.tiibntick.core.billing.pricing.infrastructure.adapter.out.BillingPolicyRepositoryAdapter;
import com.yowyob.tiibntick.core.billing.pricing.infrastructure.persistence.mapper.BillingPolicyMapper;
import com.yowyob.tiibntick.core.billing.pricing.infrastructure.persistence.repository.BillingPolicyR2dbcRepository;
import com.yowyob.tiibntick.core.billing.templates.adapter.outbound.messaging.BillingPolicyCreationAdapter;
import com.yowyob.tiibntick.core.billing.templates.adapter.outbound.persistence.CustomTemplateRepositoryAdapter;
import com.yowyob.tiibntick.core.billing.templates.adapter.outbound.persistence.PolicyTemplateRepositoryAdapter;
import com.yowyob.tiibntick.core.billing.templates.adapter.outbound.persistence.mapper.PolicyTemplateMapper;
import com.yowyob.tiibntick.core.billing.templates.adapter.outbound.persistence.repository.CustomTemplateR2dbcRepository;
import com.yowyob.tiibntick.core.billing.templates.adapter.outbound.persistence.repository.PolicyTemplateR2dbcRepository;
import com.yowyob.tiibntick.core.billing.templates.adapter.outbound.persistence.repository.TemplateParameterR2dbcRepository;
import com.yowyob.tiibntick.core.billing.templates.application.command.ApplyTemplateCommand;
import com.yowyob.tiibntick.core.billing.templates.application.service.DslRuleGeneratorService;
import com.yowyob.tiibntick.core.billing.templates.application.service.TemplateApplicabilityService;
import com.yowyob.tiibntick.core.billing.templates.application.service.TemplateParameterValidationService;
import com.yowyob.tiibntick.core.billing.templates.application.service.TemplatePolicyMaterializer;
import com.yowyob.tiibntick.core.billing.templates.application.usecase.ApplyTemplateUseCase;
import com.yowyob.tiibntick.core.billing.templates.domain.model.PolicyOwnerType;
import com.yowyob.tiibntick.core.billing.templates.port.outbound.ITemplateEventPublisher;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The whole template business flow on a real Postgres with the real migrations of the pricing and templates
 * modules: apply a catalog template → the billing policy and its rules are PERSISTED (as a draft) → activate →
 * read back from the database → price a delivery with it. Also checks that a personal template is stored.
 */
@Testcontainers
@Tag("integration")
class TemplateToPolicyFlowIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tnt_template_flow").withUsername("tnt_test").withPassword("tnt_test_secret");

    private static final UUID TENANT = UUID.randomUUID();
    private static ApplyTemplateUseCase apply;
    private static BillingPolicyService policies;
    private static PricingEngineService engine;
    private static CustomTemplateRepositoryAdapter customs;

    @BeforeAll
    static void wire() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            Database db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
            for (String changelog : List.of("db/changelog/tnt-billing-pricing-master.yaml",
                    "db/changelog/tnt-billing-templates-master.yaml")) {
                new Liquibase(changelog, new ClassLoaderResourceAccessor(), db).update(new Contexts(), new LabelExpression());
            }
        }
        ConnectionFactory cf = new PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder()
                .host(POSTGRES.getHost()).port(POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT))
                .database(POSTGRES.getDatabaseName()).username(POSTGRES.getUsername()).password(POSTGRES.getPassword())
                .build());
        R2dbcRepositoryFactory factory = new R2dbcRepositoryFactory(new R2dbcEntityTemplate(cf));
        ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        var templateMapper = new PolicyTemplateMapper(om);
        var templateRepo = new PolicyTemplateRepositoryAdapter(
                factory.getRepository(PolicyTemplateR2dbcRepository.class),
                factory.getRepository(TemplateParameterR2dbcRepository.class), templateMapper);
        customs = new CustomTemplateRepositoryAdapter(factory.getRepository(CustomTemplateR2dbcRepository.class), templateMapper);

        var policyRepo = new BillingPolicyRepositoryAdapter(
                factory.getRepository(BillingPolicyR2dbcRepository.class), new BillingPolicyMapper(om));
        policies = new BillingPolicyService(policyRepo, payload -> Mono.empty(), om);
        engine = new PricingEngineService(policyRepo, new DslCompilerService(new DslLexer(), new DslParser()), new DslEvaluator());

        ITemplateEventPublisher events = mock(ITemplateEventPublisher.class);
        when(events.publishTemplateApplied(any())).thenReturn(Mono.empty());
        apply = new ApplyTemplateUseCase(templateRepo, customs, new TemplateApplicabilityService(),
                new TemplateParameterValidationService(), new DslRuleGeneratorService(),
                new BillingPolicyCreationAdapter(policies, new TemplatePolicyMaterializer()), events);

        new TemplateCatalogSeeder(templateRepo).syncCatalog().block(Duration.ofSeconds(60));
    }

    private static ApplyTemplateCommand.ApplyTemplateCommandBuilder command(String code, String owner) {
        return ApplyTemplateCommand.builder().templateCode(code).ownerActorId(owner)
                .ownerType(PolicyOwnerType.FREELANCER_ORG).tenantId(TENANT.toString());
    }

    private static PricingContext.PricingContextBuilder ctx() {
        return PricingContext.builder().tenantId(TENANT).distanceKm(10).weightKg(2)
                .packageTypes(List.of(PackageType.STANDARD)).priority(DeliveryPriority.NORMAL)
                .timeOfDay(LocalTime.NOON).dayOfWeek(DayOfWeek.TUESDAY).deliveryZoneType("URBAN")
                .packageCount(1).clientTxCount(0);
    }

    @Test
    @DisplayName("applying TPL-BASE-STD persists a DRAFT policy with its rules; activating it persists ACTIVE; the engine prices from the database")
    void baseTemplateEndToEnd() {
        String owner = UUID.randomUUID().toString();
        UUID id = apply.apply(command("TPL-BASE-STD", owner)
                .customizedParameters(Map.of("basePrice", "700", "perKmRate", "60", "perKgRate", "0")).build())
                .block(Duration.ofSeconds(20));

        BillingPolicy stored = policies.findById(id, TENANT).block();
        assertThat(stored.getStatus()).isEqualTo(PolicyStatus.DRAFT);
        assertThat(stored.isFromTemplate()).isTrue();
        assertThat(stored.getTemplateCode()).isEqualTo("TPL-BASE-STD");
        assertThat(stored.getOwnerActorId()).isEqualTo(owner);
        assertThat(stored.getPricingRules()).hasSize(1);
        assertThat(policies.findByOwnerActorId(owner).collectList().block()).extracting(BillingPolicy::getId).containsExactly(id);

        // a draft is not used for pricing
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> engine.evaluatePolicy(id, ctx().build()).block())
                .hasMessageContaining("not active");

        assertThat(policies.activatePolicy(id, TENANT).block().getStatus()).isEqualTo(PolicyStatus.ACTIVE);
        assertThat(policies.findById(id, TENANT).block().getStatus()).as("re-read from the database").isEqualTo(PolicyStatus.ACTIVE);

        // 700 + 10 km × 60 (customised parameters survived the round trip through the database)
        assertThat(engine.evaluatePolicy(id, ctx().build()).block().getSellingPrice().getAmount().longValue()).isEqualTo(1300);
    }

    @Test
    @DisplayName("adjustment templates persist their surcharge, loyalty and commission rules and the engine applies them from the database")
    void adjustmentTemplatesEndToEnd() {
        String owner = UUID.randomUUID().toString();

        UUID timeSlots = apply.apply(command("TPL-TIME_SLOTS", owner).build()).block(Duration.ofSeconds(20));
        UUID loyalty = apply.apply(command("TPL-LOYALTY_PROGRESSIVE", owner).build()).block(Duration.ofSeconds(20));
        UUID commission = apply.apply(command("TPL-COMMISSION_DELIVERER", owner)
                .customizedParameters(Map.of("base_commission_pct", "70")).build()).block(Duration.ofSeconds(20));

        BillingPolicy ts = policies.findById(timeSlots, TENANT).block();
        assertThat(ts.getSurchargeRules()).as("persisted time-slot surcharges").hasSizeGreaterThanOrEqualTo(6);
        BillingPolicy lo = policies.findById(loyalty, TENANT).block();
        assertThat(lo.getLoyaltyRules()).as("persisted loyalty tiers").hasSize(4);
        BillingPolicy co = policies.findById(commission, TENANT).block();
        assertThat(co.getCommissionRules()).hasSize(1);

        for (UUID p : List.of(timeSlots, loyalty, commission)) policies.activatePolicy(p, TENANT).block();

        long base = 1060; // default grid 500 + 10×50 + 2×30
        assertThat(engine.evaluatePolicy(timeSlots, ctx().build()).block().getSellingPrice().getAmount().longValue()).isEqualTo(base);
        assertThat(engine.evaluatePolicy(timeSlots, ctx().timeOfDay(LocalTime.of(2, 0)).build()).block()
                .getSellingPrice().getAmount().longValue()).isEqualTo(Math.round(base * 1.5));
        assertThat(engine.evaluatePolicy(loyalty, ctx().clientTxCount(20).build()).block()
                .getSellingPrice().getAmount().longValue()).isLessThan(base);
        assertThat(engine.evaluatePolicy(commission, ctx().build()).block()
                .getDelivererCommission().getAmount().longValue()).isEqualTo(742);
    }

    @Test
    @DisplayName("a personal template saved while applying is stored in the database and found again by its owner")
    void personalTemplateIsPersisted() {
        String owner = UUID.randomUUID().toString();
        apply.apply(command("TPL-FRAGILE", owner).saveAsCustomTemplate(true).customTemplateName("Mon tarif fragile")
                .customizedParameters(Map.of("fragile_surcharge_pct", "35")).build()).block(Duration.ofSeconds(20));

        var mine = customs.findByOwnerActorId(owner).collectList().block();
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).getName()).isEqualTo("Mon tarif fragile");
        assertThat(mine.get(0).getSourceTemplateCode()).isEqualTo("TPL-FRAGILE");
        assertThat(mine.get(0).getCustomizedParameters()).containsEntry("fragile_surcharge_pct", "35");
        assertThat(customs.existsByOwnerAndName(owner, "Mon tarif fragile").block()).isTrue();
    }
}
