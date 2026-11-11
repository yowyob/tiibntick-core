package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.DeliveryProofRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.service.DeliveryProofService;
import com.yowyob.tiibntick.core.gofreelancer.application.service.DeliveryProofService.NewProof;
import com.yowyob.tiibntick.core.gofreelancer.application.service.DeliveryStatusApplicationService;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryProof.Kind;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Delivery proofs on a real Postgres built from the module's changelog SQL (constraints, uniqueness, Instant round trip). */
@SpringBootTest(classes = DeliveryProofPersistenceIntegrationTest.TestConfig.class)
@Testcontainers
@Tag("integration")
class DeliveryProofPersistenceIntegrationTest {

    static final UUID DRIVER = UUID.randomUUID();
    static final UUID SENDER = UUID.randomUUID();
    static final UUID STRANGER = UUID.randomUUID();

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tnt_gofp_proof_test").withUsername("tnt_test").withPassword("tnt_test_secret");

    @BeforeAll
    static void createSchema() throws Exception {
        try (Connection c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement st = c.createStatement()) {
            st.execute(Files.readString(Path.of("src/main/resources/db/changelog/20261113_create_delivery_proofs.sql")));
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.r2dbc.url", () -> "r2dbc:postgresql://" + POSTGRES.getHost() + ":"
                + POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT) + "/" + POSTGRES.getDatabaseName());
        r.add("spring.r2dbc.username", POSTGRES::getUsername);
        r.add("spring.r2dbc.password", POSTGRES::getPassword);
    }

    @Autowired DeliveryProofService proofs;
    @Autowired DeliveryProofRepository repository;

    @Test
    @DisplayName("proofs are stored, listed oldest capture first, and a replay does not duplicate them")
    void storeListReplay() {
        UUID delivery = UUID.randomUUID();
        UUID f1 = UUID.randomUUID();
        UUID f2 = UUID.randomUUID();
        Instant early = Instant.now().minus(5, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);
        Instant late = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);

        List<NewProof> queue = List.of(new NewProof(Kind.DELIVERY_PHOTO, f2, "à la porte", late),
                new NewProof(Kind.PICKUP_PHOTO, f1, null, early));
        StepVerifier.create(proofs.addAll(delivery, DRIVER, queue).collectList()).assertNext(l -> assertThat(l).hasSize(2)).verifyComplete();
        // the same offline queue pushed again
        StepVerifier.create(proofs.addAll(delivery, DRIVER, queue).collectList()).assertNext(l -> assertThat(l).hasSize(2)).verifyComplete();

        StepVerifier.create(repository.findByDeliveryIdOrderByCapturedAtAsc(delivery).collectList()).assertNext(l -> {
            assertThat(l).hasSize(2);
            assertThat(l).extracting("fileId").containsExactly(f1, f2);
            assertThat(l.get(0).getKind()).isEqualTo("PICKUP_PHOTO");
            assertThat(l.get(0).getCapturedAt()).isEqualTo(early);
            assertThat(l.get(0).getUploadedBy()).isEqualTo(DRIVER);
            assertThat(l.get(1).getNote()).isEqualTo("à la porte");
        }).verifyComplete();
    }

    @Test
    @DisplayName("the database itself rejects a duplicate (delivery, file) and an unknown kind")
    void constraints() {
        UUID delivery = UUID.randomUUID();
        UUID file = UUID.randomUUID();
        var base = com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryProof.builder()
                .deliveryId(delivery).kind("SIGNATURE").fileId(file).capturedAt(Instant.now()).uploadedBy(DRIVER).createdAt(Instant.now());
        repository.save(base.id(UUID.randomUUID()).build()).block();
        StepVerifier.create(repository.save(base.id(UUID.randomUUID()).isNew(true).build()))
                .expectError(org.springframework.dao.DataIntegrityViolationException.class).verify();
        StepVerifier.create(repository.save(base.id(UUID.randomUUID()).fileId(UUID.randomUUID()).kind("SELFIE").isNew(true).build()))
                .expectError(org.springframework.dao.DataIntegrityViolationException.class).verify();
    }

    @Test
    @DisplayName("a stranger can neither add nor read; the sender can read")
    void access() {
        UUID delivery = UUID.randomUUID();
        proofs.add(delivery, DRIVER, new NewProof(Kind.OTHER, UUID.randomUUID(), null, null)).block();
        StepVerifier.create(proofs.add(delivery, STRANGER, new NewProof(Kind.OTHER, UUID.randomUUID(), null, null)))
                .expectError(AccessDeniedException.class).verify();
        StepVerifier.create(proofs.list(delivery, STRANGER)).expectError(AccessDeniedException.class).verify();
        StepVerifier.create(proofs.list(delivery, SENDER)).expectNextCount(1).verifyComplete();
    }

    @Configuration
    @Import({R2dbcAutoConfiguration.class, R2dbcTransactionManagerAutoConfiguration.class, DataR2dbcAutoConfiguration.class})
    @EnableR2dbcRepositories(basePackageClasses = DeliveryProofRepository.class,
            includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = DeliveryProofRepository.class))
    static class TestConfig {

        @Bean DeliveryStatusApplicationService status() {
            DeliveryStatusApplicationService s = mock(DeliveryStatusApplicationService.class);
            when(s.assertCallerIsAssignedDeliveryPerson(any(), any())).thenAnswer(i ->
                    DRIVER.equals(i.getArgument(1)) ? Mono.empty() : Mono.error(new AccessDeniedException("not assigned")));
            when(s.assertCallerCanCancelDelivery(any(), any())).thenAnswer(i ->
                    DRIVER.equals(i.getArgument(1)) || SENDER.equals(i.getArgument(1))
                            ? Mono.empty() : Mono.error(new AccessDeniedException("not a party")));
            return s;
        }

        @Bean DeliveryProofService proofService(DeliveryProofRepository repo, DeliveryStatusApplicationService status) {
            return new DeliveryProofService(repo, status);
        }
    }
}
