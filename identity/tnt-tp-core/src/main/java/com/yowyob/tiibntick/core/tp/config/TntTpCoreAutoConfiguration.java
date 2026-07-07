package com.yowyob.tiibntick.core.tp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yowyob.tiibntick.core.tp.adapter.out.kernel.KernelThirdPartyAdapter;
import com.yowyob.tiibntick.core.tp.application.port.out.KernelThirdPartyPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Spring Boot auto-configuration for {@code tnt-tp-core}.
 *
 * <p>Activates component scanning for adapters and services within the
 * {@code com.yowyob.tiibntick.core.tp} package, enables R2DBC
 * repositories, and exposes shared beans.
 *
 * <p>Kernel integration beans wired here:
 * <ul>
 *   <li>{@link #kernelTpWebClient()} — reactive WebClient for RT-comops-tp-core REST API</li>
 *   <li>{@link #kernelThirdPartyPort(WebClient)} — {@link KernelThirdPartyPort} adapter</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.yowyob.tiibntick.core.tp")
@EnableR2dbcRepositories(
        basePackages = "com.yowyob.tiibntick.core.tp.adapter.out.persistence.r2dbc"
)
public class TntTpCoreAutoConfiguration {

    /**
     * Base URL of the RT-comops-tp-core REST API.
     * Configured via {@code tiibntick.kernel.tp.base-url}.
     * Defaults to localhost for local development.
     */
    @Value("${tiibntick.kernel.tp.base-url:http://localhost:8080}")
    private String kernelTpBaseUrl;

    /**
     * Reactive WebClient configured for calls to the Kernel ThirdParty API.
     *
     * <p>The {@code @ConditionalOnMissingBean} guard allows tnt-bootstrap or tests
     * to override this with a mocked or customized WebClient.
     *
     * @return a WebClient targeting the Kernel TP base URL
     */
    @Bean(name = "kernelTpWebClient")
    @ConditionalOnMissingBean(name = "kernelTpWebClient")
    public WebClient kernelTpWebClient() {
        return WebClient.builder()
                .baseUrl(kernelTpBaseUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * Kernel ThirdParty outbound adapter.
     *
     * <p>Validates ThirdParty references via the RT-comops-tp-core REST API.
     * Called by {@link com.yowyob.tiibntick.core.tp.application.service.TntClientProfileService}
     * before creating any {@link com.yowyob.tiibntick.core.tp.domain.model.TntClientProfile}.
     *
     * @param kernelTpWebClient the configured WebClient
     * @return the {@link KernelThirdPartyPort} implementation
     */
    @Bean
    @ConditionalOnMissingBean
    public KernelThirdPartyPort kernelThirdPartyPort(WebClient kernelTpWebClient) {
        return new KernelThirdPartyAdapter(kernelTpWebClient);
    }

    /**
     * ObjectMapper configured with Java 8 time support (ISO-8601 serialization).
     *
     * <p>The {@code @ConditionalOnMissingBean} guard prevents conflict if the
     * bootstrap module or another auto-configuration already defines an ObjectMapper.
     *
     * @return configured {@link ObjectMapper}
     */
    @Bean
    @ConditionalOnMissingBean(name = "tntTpObjectMapper")
    public ObjectMapper tntTpObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
