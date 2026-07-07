package com.yowyob.tiibntick.core.billing.templates.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

/**
 * Spring configuration for the tnt-billing-templates module.
 *
 * <p>Defines module-level beans that are not auto-configured, such as
 * the shared {@link ObjectMapper} configured for the billing templates context.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@Configuration
@EnableR2dbcRepositories(basePackages = "com.yowyob.tiibntick.core.billing.templates.adapter.outbound.persistence.repository")
public class BillingTemplatesConfig {

    /**
     * Provides a shared {@link ObjectMapper} configured with:
     * <ul>
     *   <li>Java 8 Time module (for {@code Instant}, {@code LocalDate}, etc.)</li>
     *   <li>ISO-8601 date format instead of timestamps</li>
     * </ul>
     *
     * <p>Used by {@code PolicyTemplateMapper} for JSON serialization of embedded collections,
     * and by {@code KafkaTemplateEventPublisher} for event payload serialization.
     *
     * @return configured ObjectMapper instance
     */
    @Bean
    public ObjectMapper billingTemplatesObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
