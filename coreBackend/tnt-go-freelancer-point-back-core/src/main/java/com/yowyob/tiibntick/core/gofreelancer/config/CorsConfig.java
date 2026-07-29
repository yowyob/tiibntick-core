package com.yowyob.tiibntick.core.gofreelancer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.CorsWebFilter;

/**
 * CORS filter configuration for the Go-Freelancer-Point module.
 *
 * <p>Does NOT redeclare {@code corsConfigurationSource} — that bean is provided
 * by {@code TntSecurityConfig} in tnt-bootstrap and shared across all modules.
 * This class only wires the filter using the shared source.
 *
 * @author François-Charles ATANGA
 */
@Configuration
public class CorsConfig {

    /**
     * Registers the reactive CORS filter using the shared {@code corsConfigurationSource}
     * bean provided by {@code TntSecurityConfig}.
     */
    @Bean
    public CorsWebFilter gofpCorsWebFilter(CorsConfigurationSource corsConfigurationSource) {
        return new CorsWebFilter(corsConfigurationSource);
    }
}
