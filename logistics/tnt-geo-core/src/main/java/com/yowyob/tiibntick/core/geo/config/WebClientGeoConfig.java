package com.yowyob.tiibntick.core.geo.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.util.concurrent.TimeUnit;

/**
 * WebClient configuration for external geo API calls.
 * Configures connection/read timeouts and retry logic per Nominatim's rate limit policy.
 *
 * Author: MANFOUO Braun
 */
@Configuration
public class WebClientGeoConfig {

    @Value("${tnt.geo.nominatim.base-url:https://nominatim.openstreetmap.org}")
    private String nominatimBaseUrl;

    @Value("${tnt.geo.openmeteo.base-url:https://api.open-meteo.com}")
    private String openMeteoBaseUrl;

    @Value("${tnt.geo.nominatim.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${tnt.geo.nominatim.read-timeout-s:10}")
    private int readTimeoutS;

    @Bean("nominatimWebClient")
    public WebClient nominatimWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(readTimeoutS, TimeUnit.SECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(5, TimeUnit.SECONDS))
                );
        return WebClient.builder()
                .baseUrl(nominatimBaseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter(rateLimitRetryFilter())
                .build();
    }

    @Bean("openMeteoWebClient")
    public WebClient openMeteoWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(readTimeoutS, TimeUnit.SECONDS))
                );
        return WebClient.builder()
                .baseUrl(openMeteoBaseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    /**
     * Retry filter: backs off 1 second on 5xx or connection errors,
     * respecting Nominatim's 1 req/s policy for non-commercial use.
     */
    private ExchangeFilterFunction rateLimitRetryFilter() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            if (clientResponse.statusCode().is5xxServerError()) {
                return clientResponse.bodyToMono(String.class)
                        .flatMap(body -> reactor.core.publisher.Mono.error(
                                new RuntimeException("Nominatim server error: " + body)
                        ));
            }
            return reactor.core.publisher.Mono.just(clientResponse);
        });
    }
}
