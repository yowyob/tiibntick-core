package com.yowyob.tiibntick.core.billing.dsl.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.evaluator.DslEvaluator;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.executor.ActionExecutor;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.lexer.DslLexer;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.parser.DslParser;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

/**
 * Spring Boot auto-configuration for the {@code tnt-billing-dsl} module.
 *
 * <p>This configuration is picked up by {@code tnt-bootstrap} via
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.</p>
 *
 * <h3> additions</h3>
 * <p>{@link com.yowyob.tiibntick.core.billing.dsl.application.service.DslAccessValidator}
 * is automatically registered via component scan (it is a {@code @Service}).</p>
 *
 * @author MANFOUO Braun
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.yowyob.tiibntick.core.billing.dsl")
@EnableR2dbcRepositories(
        basePackages = "com.yowyob.tiibntick.core.billing.dsl.infrastructure.persistence.repository"
)
public class DslBillingAutoConfiguration {

    @Bean
    public DslLexer dslLexer() {
        return new DslLexer();
    }

    @Bean
    public DslParser dslParser() {
        return new DslParser();
    }

    @Bean
    public DslEvaluator dslEvaluator() {
        return new DslEvaluator();
    }

    @Bean
    public ActionExecutor actionExecutor() {
        return new ActionExecutor();
    }

    @Bean
    public ObjectMapper billingObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
