package com.yowyob.tiibntick.core.geo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Spring @Configuration for the tnt-geo-core module.
 * Exported to tnt-bootstrap via component scanning of com.yowyob.tiibntick.core.geo.
 *
 * Beans declared here are conditionally created to avoid conflicts
 * when tnt-bootstrap provides its own (more fully-configured) versions.
 *
 * <p>The former {@code geoKafkaTemplate} producer bean was removed with the Chantier C ·
 * Audit n°3 · P5 outbox migration: {@code KafkaGeoEventPublisher} now enqueues events through
 * yow-event-kernel's transactional outbox instead of sending via {@code KafkaTemplate}.
 *
 * Author: MANFOUO Braun
 */
@Configuration
@ComponentScan(basePackages = "com.yowyob.tiibntick.core.geo")
public class GeoCoreConfig {

    @Bean
    @ConditionalOnMissingBean(name = "geoObjectMapper")   //ObjectMapper.class)
    public ObjectMapper geoObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
