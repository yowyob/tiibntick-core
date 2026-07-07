package com.yowyob.tiibntick.bootstrap.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Hooks;

import java.util.UUID;

/**
 * Global WebFlux configuration for TiiBnTick Core.
 * Configures codecs, ObjectMapper, WebSocket adapter and reactive Hooks.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Configuration
@EnableWebFlux
public class TntWebFluxConfig implements WebFluxConfigurer {

    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        JsonMapper mapper = tntJsonMapper();
        configurer.defaultCodecs().jacksonJsonEncoder(new JacksonJsonEncoder(mapper));
        configurer.defaultCodecs().jacksonJsonDecoder(new JacksonJsonDecoder(mapper));
        configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024);
    }

    /**
     * Configures the Jackson {@link JsonMapper} to be used by Spring WebFlux.
     * Disables FAIL_ON_EMPTY_BEANS to avoid serialization errors for empty beans.
     *
     * @return the configured {@link JsonMapper}
     */
    //@Primary
    @Bean
    public JsonMapper tntJsonMapper() {
        return JsonMapper.builder()
                .findAndAddModules()
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .changeDefaultVisibility(vc -> vc
                        .withFieldVisibility(Visibility.ANY)
                        .withGetterVisibility(Visibility.NONE)
                        .withIsGetterVisibility(Visibility.NONE))
                .build();
    }


    @Bean
    public WebFilter requestIdFilter() {
        return (exchange, chain) -> {
            String requestId = exchange.getRequest().getHeaders()
                    .getFirst("X-Request-Id");
            if (requestId == null) {
                requestId = UUID.randomUUID().toString();
            }
            String finalRequestId = requestId;
            return chain.filter(exchange)
                    .contextWrite(ctx -> ctx.put("requestId", finalRequestId));
        };
    }

    /**
     * Enable reactor automatic context propagation (e.g., for MDC via Micrometer Tracing).
     * Must be called once at startup.
     */
    static {
        Hooks.enableAutomaticContextPropagation();
    }
}
