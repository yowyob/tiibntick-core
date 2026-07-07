package com.yowyob.tiibntick.core.billing.pricing.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

@AutoConfiguration
@ComponentScan(basePackages = "com.yowyob.tiibntick.core.billing.pricing")
@EnableR2dbcRepositories(
        basePackages = "com.yowyob.tiibntick.core.billing.pricing.infrastructure.persistence.repository"
)
public class BillingPricingAutoConfiguration {

    @Bean
    public ObjectMapper pricingObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
