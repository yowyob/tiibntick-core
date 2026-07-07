package com.yowyob.tiibntick.core.realtime.adapter.out.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yowyob.tiibntick.core.realtime.domain.model.DeviceInfo;
import com.yowyob.tiibntick.core.realtime.domain.model.GeoCoordinates;
import com.yowyob.tiibntick.core.realtime.domain.model.PresenceRecord;
import com.yowyob.tiibntick.core.realtime.domain.model.enums.DeviceType;
import com.yowyob.tiibntick.core.realtime.domain.model.enums.PresenceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link RedisPresenceRepository} using Testcontainers.
 * Verifies actual Redis persistence, TTL behavior, and query correctness.
 *
 * <p>These tests are tagged with {@code @Tag("integration")} and require
 * Docker to be available. They are excluded from the default unit test suite
 * and run separately via the Maven Failsafe plugin (integration-tests profile).</p>
 *
 * @author MANFOUO Braun
 */
@Testcontainers
@Tag("integration")
class RedisPresenceRepositoryIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS_CONTAINER =
            new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
                    .withExposedPorts(6379);

    private RedisPresenceRepository repository;
    private ReactiveStringRedisTemplate redisTemplate;

    private static final String USER_ID = "user-test-001";
    private static final String TENANT_ID = "tenant-test";
    private static final DeviceInfo DEVICE_INFO = DeviceInfo.of(DeviceType.ANDROID, "2.0", "Android 14");

    @BeforeEach
    void setUp() {
        String host = REDIS_CONTAINER.getHost();
        int port = REDIS_CONTAINER.getMappedPort(6379);

        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(host, port);
        connectionFactory.afterPropertiesSet();

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        redisTemplate = new ReactiveStringRedisTemplate(connectionFactory);
        repository = new RedisPresenceRepository(redisTemplate, objectMapper);

        // Flush Redis before each test
        ((ReactiveRedisConnectionFactory) connectionFactory).getReactiveConnection()
                .serverCommands()
                .flushAll()
                .block();
    }

    @Test
    @DisplayName("save() persists presence record and findByUserAndTenant() retrieves it")
    void saveAndFindPresenceRecord() {
        PresenceRecord record = new PresenceRecord(USER_ID, TENANT_ID, DEVICE_INFO);

        StepVerifier.create(
                repository.save(record)
                        .then(repository.findByUserAndTenant(USER_ID, TENANT_ID))
        )
        .assertNext(found -> {
            assertThat(found.getUserId()).isEqualTo(USER_ID);
            assertThat(found.getTenantId()).isEqualTo(TENANT_ID);
            assertThat(found.getStatus()).isEqualTo(PresenceStatus.ONLINE_AVAILABLE);
            assertThat(found.isOnline()).isTrue();
        })
        .verifyComplete();
    }

    @Test
    @DisplayName("findByUserAndTenant() returns empty Mono for non-existent record")
    void findNonExistentReturnsEmpty() {
        StepVerifier.create(repository.findByUserAndTenant("ghost-user", TENANT_ID))
                .verifyComplete();
    }

    @Test
    @DisplayName("save() with updated status persists the new status")
    void saveUpdatedStatusPersisted() {
        PresenceRecord record = new PresenceRecord(USER_ID, TENANT_ID, DEVICE_INFO);
        record.assignMission("M-001");

        StepVerifier.create(
                repository.save(record)
                        .then(repository.findByUserAndTenant(USER_ID, TENANT_ID))
        )
        .assertNext(found -> {
            assertThat(found.getStatus()).isEqualTo(PresenceStatus.ONLINE_ON_MISSION);
            assertThat(found.getActiveMissionId()).isEqualTo("M-001");
        })
        .verifyComplete();
    }

    @Test
    @DisplayName("save() persists GeoCoordinates and retrieves them correctly")
    void saveAndFindCoordinates() {
        PresenceRecord record = new PresenceRecord(USER_ID, TENANT_ID, DEVICE_INFO);
        record.updateLocation(GeoCoordinates.of(3.848, 11.502));

        StepVerifier.create(
                repository.save(record)
                        .then(repository.findByUserAndTenant(USER_ID, TENANT_ID))
        )
        .assertNext(found -> {
            assertThat(found.getCurrentCoordinates()).isNotNull();
            assertThat(found.getCurrentCoordinates().latitude()).isEqualTo(3.848);
            assertThat(found.getCurrentCoordinates().longitude()).isEqualTo(11.502);
        })
        .verifyComplete();
    }

    @Test
    @DisplayName("findAllByTenant() returns all records for the tenant")
    void findAllByTenantReturnsAllRecords() {
        PresenceRecord r1 = new PresenceRecord("u1", TENANT_ID, DEVICE_INFO);
        PresenceRecord r2 = new PresenceRecord("u2", TENANT_ID, DEVICE_INFO);
        PresenceRecord r3 = new PresenceRecord("u3", "other-tenant", DEVICE_INFO);

        StepVerifier.create(
                repository.save(r1)
                        .then(repository.save(r2))
                        .then(repository.save(r3))
                        .thenMany(repository.findAllByTenant(TENANT_ID))
        )
        .expectNextCount(2)
        .verifyComplete();
    }

    @Test
    @DisplayName("deleteByUserAndTenant() removes the record from Redis")
    void deleteRemovesRecord() {
        PresenceRecord record = new PresenceRecord(USER_ID, TENANT_ID, DEVICE_INFO);

        StepVerifier.create(
                repository.save(record)
                        .then(repository.deleteByUserAndTenant(USER_ID, TENANT_ID))
                        .then(repository.findByUserAndTenant(USER_ID, TENANT_ID))
        )
        .verifyComplete(); // Empty Mono after deletion
    }

    @Test
    @DisplayName("findAllStale() returns records older than the stale duration")
    void findAllStaleReturnsOldRecords() {
        PresenceRecord record = new PresenceRecord(USER_ID, TENANT_ID, DEVICE_INFO);

        StepVerifier.create(
                repository.save(record)
                        .thenMany(repository.findAllStale(Duration.ZERO)) // Zero duration = all are stale
        )
        .expectNextCount(1)
        .verifyComplete();
    }
}
