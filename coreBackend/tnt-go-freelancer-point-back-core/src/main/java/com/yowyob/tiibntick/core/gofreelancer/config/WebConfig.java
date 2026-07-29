package com.yowyob.tiibntick.core.gofreelancer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebFluxConfigurer {

    @Value("${tnt.gofp.kyc.verify-url:http://localhost:8080/api/v1/kyc/verify}")
    private String kycVerifyUrl;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadDir = "uploads";
        String path = Paths.get(uploadDir).toAbsolutePath().toUri().toString();

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(path);
    }

    /**
     * WebClient pre-configured with the KYC verification base URL.
     * Used by {@code KycVerificationAdapter} to forward documents to tnt-actor-core.
     */
    @Bean("kycWebClient")
    public WebClient kycWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl(kycVerifyUrl)
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(25 * 1024 * 1024)) // 25 MB — match codec limit
                .build();
    }
}
