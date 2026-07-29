package com.yowyob.tiibntick.bootstrap.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
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
        // Jackson 2 codecs — application DTOs/proxies use com.fasterxml JsonNode, not tools.jackson.
        configurer.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(mapper));
        configurer.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(mapper));
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
                .visibility(PropertyAccessor.FIELD, Visibility.ANY)
                .visibility(PropertyAccessor.GETTER, Visibility.NONE)
                .visibility(PropertyAccessor.IS_GETTER, Visibility.NONE)
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
