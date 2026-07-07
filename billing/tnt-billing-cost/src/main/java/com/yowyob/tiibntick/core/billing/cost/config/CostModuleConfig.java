package com.yowyob.tiibntick.core.billing.cost.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Spring auto-configuration for the tnt-billing-cost module.
 *
 * <p> — Added R2DBC repository scan for FleetCostParameters persistence.
 *
 * @author MANFOUO Braun
 */
@Configuration
@ComponentScan(basePackages = "com.yowyob.tiibntick.core.billing.cost")
@EnableR2dbcRepositories(
        basePackages = "com.yowyob.tiibntick.core.billing.cost.adapter.out.persistence.repository"
)
public class CostModuleConfig {

    /**
     * Calls tnt-route-core's REST API. Self-referential — in this monolithic
     * deployment all modules run in the same app instance, so the base URL
     * defaults to this app's own port.
     */
    @Bean("routeCoreWebClient")
    public WebClient routeCoreWebClient(
            @Value("${tnt.route-core.base-url:http://localhost:8080}") String baseUrl) {
        return WebClient.builder().baseUrl(baseUrl).build();
    }

    /**
     * Calls tnt-geo-core's REST API. Self-referential — see {@link #routeCoreWebClient}.
     */
    @Bean("geoCoreWebClient")
    public WebClient geoCoreWebClient(
            @Value("${tnt.geo-core.base-url:http://localhost:8080}") String baseUrl) {
        return WebClient.builder().baseUrl(baseUrl).build();
    }
}
