package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yowyob.tiibntick.core.gofreelancer.application.service.GofpUserProvisioningService;
import com.yowyob.tiibntick.core.gofreelancer.config.GofpProvisioningProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Ensures a {@code gofp_users} row exists for every authenticated request.
 *
 * <p>Runs at order 0 — after Spring Security's {@code WebFilterChainProxy}
 * (order -100) which validates the JWT and populates
 * {@link ReactiveSecurityContextHolder}. Reads {@code authentication.getName()}
 * which equals the JWT {@code sub} claim (UUID string), then delegates to
 * {@link GofpUserProvisioningService#provisionIfAbsent}.
 *
 * <p>Unauthenticated requests (public paths, no JWT) produce an empty
 * security context — the filter skips provisioning transparently and passes
 * through to the handler.
 *
 * <p>Provisioning errors are logged and swallowed: failing to provision a
 * placeholder row is less harmful than blocking the entire request. Downstream
 * handlers that strictly need the row (e.g. {@code me()}) will fail with a
 * clear domain error if the row is absent.
 *
 * <p>Lot C-19: a user whose row is known to exist is remembered in a bounded
 * Caffeine cache (max size + TTL from {@link GofpProvisioningProperties}), so the
 * filter costs no database read after a user's first request on this instance,
 * instead of one read per authenticated request on every route of the monolith.
 *
 * @author François-Charles ATANGA
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class GofpUserProvisioningFilter implements WebFilter {

    private final GofpUserProvisioningService provisioningService;
    private final MeterRegistry meterRegistry;
    private final GofpProvisioningProperties properties;

    /** Counts silent provisioning errors — query at /actuator/metrics/gofp.provisioning.failures */
    private Counter provisioningFailures;

    private Cache<UUID, Boolean> provisioned;

    @PostConstruct
    void init() {
        provisioningFailures = Counter.builder("gofp.provisioning.failures")
                .description("Silent provisioning errors swallowed by GofpUserProvisioningFilter")
                .register(meterRegistry);
        provisioned = Caffeine.newBuilder()
                .maximumSize(properties.getCacheMaxSize())
                .expireAfterWrite(properties.getCacheTtl())
                .build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        Mono<Void> provision = ReactiveSecurityContextHolder.getContext()
                .flatMap(ctx -> {
                    Authentication auth = ctx.getAuthentication();
                    if (auth == null || !auth.isAuthenticated()) return Mono.empty();
                    String name = auth.getName();
                    if (name == null || name.isBlank()) return Mono.empty();
                    try {
                        UUID coreUserId = UUID.fromString(name);
                        if (provisioned.getIfPresent(coreUserId) != null) return Mono.empty();
                        return provisioningService.provisionIfAbsent(coreUserId)
                                .doOnNext(user -> provisioned.put(coreUserId, Boolean.TRUE))
                                .then();
                    } catch (IllegalArgumentException e) {
                        return Mono.empty();
                    }
                })
                .onErrorResume(e -> {
                    log.warn("GofpUser provisioning failed for {}: {}", exchange.getRequest().getPath(), e.getMessage());
                    provisioningFailures.increment();
                    return Mono.empty();
                });

        return provision.then(chain.filter(exchange));
    }
}
