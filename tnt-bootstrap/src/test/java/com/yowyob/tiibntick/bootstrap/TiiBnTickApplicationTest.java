package com.yowyob.tiibntick.bootstrap;

import org.junit.jupiter.api.Test;
//import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
//import org.springframework.boot.r2dbc.autoconfigure.R2dbcAutoConfiguration;
//import org.springframework.boot.autoconfigure.data.r2dbc.R2dbcDataAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test verifying that the Spring Boot application context loads successfully
 * with the {@code test} profile.
 * <p>
 * Uses Testcontainers (via application-test.yml) to spin up PostgreSQL, Redis and Kafka.
 *
 * @author MANFOUO Braun
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "r2dbc"})
//@ImportAutoConfiguration({
//    R2dbcAutoConfiguration.class,
//    R2dbcDataAutoConfiguration.class
//})
class TiiBnTickApplicationTest {

    @Test
    void contextLoads() {
        // If the application context starts without throwing an exception, the test passes.
        // This validates that all auto-configurations, Liquibase migrations and bean wiring
        // are correct for the assembled module set.
    }
}
