package com.yowyob.tiibntick.core.agency.org.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * HTTP client for tnt-trust (:8090). Bearer forwarded from the inbound request when present.
 */
@Configuration
public class TrustClientConfig {

    @Value("${tnt.trust.base-url:http://localhost:8090}")
    private String trustBaseUrl;

    @Value("${tnt.trust.timeout:10s}")
    private Duration trustTimeout;

    @Bean("trustWebClient")
    public WebClient trustWebClient(WebClient.Builder builder) {
        int readSec = (int) Math.max(1, trustTimeout.getSeconds());
        return builder.clone()
                .baseUrl(normalizeBaseUrl(trustBaseUrl))
                .filter(propagateBearerToken())
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create()
                                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, readSec * 1000)
                                .responseTimeout(trustTimeout)
                                .doOnConnected(conn -> conn
                                        .addHandlerLast(new ReadTimeoutHandler(readSec, TimeUnit.SECONDS))
                                        .addHandlerLast(new WriteTimeoutHandler(readSec, TimeUnit.SECONDS)))))
                .build();
    }

    private static ExchangeFilterFunction propagateBearerToken() {
        return (request, next) -> ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(auth -> auth.getToken().getTokenValue())
                .map(token -> ClientRequest.from(request)
                        .headers(h -> h.setBearerAuth(token))
                        .build())
                .defaultIfEmpty(request)
                .flatMap(next::exchange);
    }

    private static String normalizeBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            return "http://localhost:8090";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
