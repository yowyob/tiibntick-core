package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence;

import com.yowyob.tiibntick.core.billing.pricing.domain.port.in.IBillingPolicyUseCase;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.FreelancerEligibilityDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.FreelancerEligibilityDTO.Slot;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.FreelancerPricingDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.FreelancerEligibilityR2dbcRepository;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.FreelancerPricingR2dbcRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.FreelancerEligibilityUseCase.Parcel;
import com.yowyob.tiibntick.core.gofreelancer.application.service.FreelancerEligibilityService;
import com.yowyob.tiibntick.core.gofreelancer.application.service.FreelancerOwnershipGuard;
import com.yowyob.tiibntick.core.gofreelancer.application.service.FreelancerPricingPolicyApplicationService;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.r2dbc.autoconfigure.DataR2dbcAutoConfiguration;
import org.springframework.boot.r2dbc.autoconfigure.R2dbcAutoConfiguration;
import org.springframework.boot.r2dbc.autoconfigure.R2dbcTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The two storage mappings of the freelancer pricing work against a real Postgres, created by the
 * module's own changelog SQL files: the legacy {@code delivery_person_pricing} mirror and the new
 * {@code freelancer_eligibility} table (rows inserted with a null id and the DB-generated UUID, TEXT JSON
 * columns, one row per delivery person).
 */
@SpringBootTest(classes = FreelancerPricingPersistenceIntegrationTest.TestConfig.class)
@Testcontainers
@Tag("integration")
class FreelancerPricingPersistenceIntegrationTest {

    private static final UUID DP = UUID.randomUUID();
    private static final String CHANGELOG = "src/main/resources/db/changelog/";

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tnt_gofp_pricing_test").withUsername("tnt_test").withPassword("tnt_test_secret");

    @BeforeAll
    static void createSchema() throws Exception {
        try (Connection c = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement st = c.createStatement()) {
            st.execute("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"");
            // the two parents the changelog files point their foreign keys at
            st.execute("CREATE TABLE delivery_persons (id UUID PRIMARY KEY)");
            st.execute("CREATE TABLE logistics (id UUID PRIMARY KEY)");
            st.execute("INSERT INTO delivery_persons (id) VALUES ('" + DP + "')");
            for (String file : List.of("20260707_create_pricing_policies.sql", "20261111_create_freelancer_eligibility.sql")) {
                st.execute(Files.readString(Path.of(CHANGELOG + file)));
            }
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.r2dbc.url", () -> "r2dbc:postgresql://" + POSTGRES.getHost() + ":"
                + POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT) + "/" + POSTGRES.getDatabaseName());
        r.add("spring.r2dbc.username", POSTGRES::getUsername);
        r.add("spring.r2dbc.password", POSTGRES::getPassword);
    }

    @Autowired FreelancerEligibilityService eligibility;
    @Autowired FreelancerPricingPolicyApplicationService pricing;
    @Autowired DatabaseClient db;

    private long count(String table) {
        return db.sql("SELECT count(*) AS n FROM " + table + " WHERE delivery_person_id = :id")
                .bind("id", DP).map((row, m) -> row.get("n", Long.class)).one().block();
    }

    @Test
    @DisplayName("eligibility: insert with a DB-generated id, update in place, JSON survives, rules apply")
    void eligibilityRoundTripsThroughPostgres() {
        FreelancerEligibilityDTO prefs = FreelancerEligibilityDTO.builder()
                .availability(List.of(Slot.builder().day("MONDAY").start("08:00").end("18:00").active(true).build()))
                .acceptedPackages(List.of("standard", "fragile")).minWeightKg(1.0).maxWeightKg(20.0).build();

        eligibility.replaceAsCaller(DP, prefs, null, true).block();
        eligibility.replaceAsCaller(DP, prefs.toBuilder().maxWeightKg(8.0).build(), null, true).block();

        assertThat(count("freelancer_eligibility")).isEqualTo(1);
        FreelancerEligibilityDTO read = eligibility.get(DP).block();
        assertThat(read.getMaxWeightKg()).isEqualTo(8.0);
        assertThat(read.getAcceptedPackages()).containsExactly("STANDARD", "FRAGILE");
        assertThat(read.getAvailability()).singleElement().satisfies(s -> {
            assertThat(s.getDay()).isEqualTo("MONDAY");
            assertThat(s.getStart()).isEqualTo("08:00");
            assertThat(s.isActive()).isTrue();
        });

        assertThat(eligibility.isEligible(List.of(UUID.randomUUID(), DP), new Parcel(5, true, false, java.time.DayOfWeek.MONDAY))
                .block()).isTrue();
        assertThat(eligibility.isEligible(List.of(DP), new Parcel(9, false, false, null)).block())
                .as("above the stored max weight").isFalse();
    }

    @Test
    @DisplayName("the CHECK constraint rejects an inverted weight range even if the service were bypassed")
    void databaseRejectsInvertedWeightRange() {
        UUID other = UUID.randomUUID();
        db.sql("INSERT INTO delivery_persons (id) VALUES (:id)").bind("id", other).fetch().rowsUpdated().block();
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
                db.sql("INSERT INTO freelancer_eligibility (delivery_person_id, min_weight_kg, max_weight_kg) "
                        + "VALUES (:id, 10, 5)").bind("id", other).fetch().rowsUpdated().block());
    }

    @Test
    @DisplayName("grid mirror: FCFA is stored as XAF and repeated updates keep a single row")
    void legacyMirrorStaysSingleRow() {
        FreelancerPricingDTO grid = FreelancerPricingDTO.builder().baseFee(700.0).pricePerKm(80.0)
                .fragileSurcharge(250.0).currency("FCFA").build();

        // no tenant → only the legacy mirror is written (the billing use case is not part of this context)
        pricing.upsertPolicy(DP, grid).block();
        pricing.upsertPolicy(DP, FreelancerPricingDTO.builder().pricePerKm(90.0).build()).block();

        assertThat(count("delivery_person_pricing")).isEqualTo(1);
        FreelancerPricingDTO read = pricing.getPolicy(DP).block();
        assertThat(read.getBaseFee()).isEqualTo(700.0);
        assertThat(read.getPricePerKm()).isEqualTo(90.0);
        assertThat(read.getFragileSurcharge()).isEqualTo(250.0);
        assertThat(read.getCurrency()).isEqualTo("XAF");
    }

    @Configuration
    @Import({R2dbcAutoConfiguration.class, R2dbcTransactionManagerAutoConfiguration.class,
            DataR2dbcAutoConfiguration.class})
    @EnableR2dbcRepositories(basePackageClasses = FreelancerEligibilityR2dbcRepository.class,
            includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                    classes = {FreelancerEligibilityR2dbcRepository.class, FreelancerPricingR2dbcRepository.class}))
    static class TestConfig {

        @Bean FreelancerEligibilityRepositoryAdapter eligibilityRepo(FreelancerEligibilityR2dbcRepository r) {
            return new FreelancerEligibilityRepositoryAdapter(r);
        }

        @Bean FreelancerPricingRepositoryAdapter pricingRepo(FreelancerPricingR2dbcRepository r) {
            return new FreelancerPricingRepositoryAdapter(r);
        }

        @Bean FreelancerOwnershipGuard guard() {
            return new FreelancerOwnershipGuard(mock(GofpFreelancerRepository.class));
        }

        @Bean FreelancerEligibilityService eligibilityService(FreelancerEligibilityRepositoryAdapter repo,
                                                              FreelancerOwnershipGuard guard) {
            return new FreelancerEligibilityService(repo, guard);
        }

        @Bean FreelancerPricingPolicyApplicationService pricingService(FreelancerPricingRepositoryAdapter repo,
                                                                       FreelancerOwnershipGuard guard) {
            // billing is mocked: findByOwnerActorId → null would NPE, so give an empty flux
            IBillingPolicyUseCase billing = mock(IBillingPolicyUseCase.class);
            org.mockito.Mockito.when(billing.findByOwnerActorId(org.mockito.ArgumentMatchers.anyString()))
                    .thenReturn(reactor.core.publisher.Flux.empty());
            return new FreelancerPricingPolicyApplicationService(repo, billing, guard);
        }
    }
}
