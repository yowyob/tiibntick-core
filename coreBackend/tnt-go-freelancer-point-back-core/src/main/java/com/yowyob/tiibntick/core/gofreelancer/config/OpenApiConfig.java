package com.yowyob.tiibntick.core.gofreelancer.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for OpenAPI (Swagger) documentation.
 * Configures the API metadata and UI settings.
 *
 * @author François-Charles ATANGA
 * @date 05/02/2026
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ticBnPickOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("TiiBnTick API")
                        .description("TiiBnTick Delivery Platform API")
                        .version("v1.0.0"));
    }
}
