package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yowyob.tiibntick.core.gofreelancer.application.service.GofpFreelancerProjectionService;
import com.yowyob.tiibntick.core.gofreelancer.application.service.TenantContextHolder;
import com.yowyob.tiibntick.core.gofreelancer.config.GofpProvisioningProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.PathContainer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

/**
 * Ensures {@code gofp_freelancers} and {@code tnt_delivery_persons} rows exist before a
 * TiiBnTick freelancer reaches a handler that reads them.
 *
 * <p>Runs at {@code @Order(1)} — after {@link GofpUserProvisioningFilter} ({@code @Order(0)}).
 * Delegates to {@link GofpFreelancerProjectionService#projectIfAbsent}: non-freelancers
 * (users with no {@code FreelancerProfile} in tnt-actor-core) pass through transparently.
 *
 * <p><strong>Cost bounds (lot C-19)</strong> — this filter sits in front of the whole
 * monolith, so it must not tax requests that have nothing to do with the freelancer journey:
 * <ol>
 *   <li><em>Path predicate.</em> Only {@link #PROJECTION_ROUTES} trigger it: the routes whose
 *       handlers read the caller's projection rows (announcement subscribe/respond enforce the
 *       quota on {@code gofp_freelancers} and resolve {@code tnt_delivery_persons}; the GOFP
 *       freelancer read routes return them). Every other route — Go's {@code /api/delivery-needs},
 *       {@code /actuator}, the rest of the core — goes straight to the chain with no lookup.</li>
 *   <li><em>Positive cache.</em> Once a user's projection is known to exist, it is remembered
 *       per {@code (tenant, user)} in a bounded Caffeine cache (max size + TTL from
 *       {@link GofpProvisioningProperties}). A projected freelancer therefore pays zero database
 *       reads per request instead of four. Negative answers (not a freelancer) are not cached,
 *       so a profile created a second ago is projected on the very next matching request.</li>
 *   <li><em>Bounded wait.</em> On a cache miss the projection still runs before the handler —
 *       the only case where that is needed, because the handler reads the rows right after —
 *       but at most {@code projection-timeout}. Past that, the request proceeds and the next
 *       matching request retries (the projection is idempotent).</li>
 * </ol>
 *
 * <p>Best-effort: projection errors and timeouts are logged and swallowed; the request is
 * never failed by this filter. Monitor {@code gofp.freelancer.provisioning.failures}.
 *
 * @author KOUAM Kamdem
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class GofpFreelancerProvisioningFilter implements WebFilter {

    /** Routes whose handlers read the caller's {@code gofp_freelancers}/{@code tnt_delivery_persons}. */
    static final List<PathPattern> PROJECTION_ROUTES = List.of(
            PathPatternParser.defaultInstance.parse("/api/announcements/**"),
            PathPatternParser.defaultInstance.parse("/api/freelancers/**"),
            PathPatternParser.defaultInstance.parse("/api/v1/gofp/freelancer-profiles/**"));

    private final GofpFreelancerProjectionService projectionService;
    private final TenantContextHolder tenantContextHolder;
    private final MeterRegistry meterRegistry;
    private final GofpProvisioningProperties properties;

    private Counter provisioningFailures;
    private Cache<ProjectionKey, Boolean> projected;

    @PostConstruct
    void init() {
        provisioningFailures = Counter.builder("gofp.freelancer.provisioning.failures")
                .description("Silent freelancer projection errors swallowed by GofpFreelancerProvisioningFilter")
                .register(meterRegistry);
        projected = Caffeine.newBuilder()
                .maximumSize(properties.getCacheMaxSize())
                .expireAfterWrite(properties.getCacheTtl())
                .build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!requiresProjection(exchange.getRequest().getPath().pathWithinApplication())) {
            return chain.filter(exchange);
        }

        Mono<Void> provision = ReactiveSecurityContextHolder.getContext()
                .flatMap(ctx -> {
                    UUID coreUserId = coreUserId(ctx.getAuthentication());
                    if (coreUserId == null) return Mono.empty();
                    return tenantContextHolder.currentTenantId()
                            .flatMap(tenantId -> ensureProjected(new ProjectionKey(tenantId, coreUserId)));
                })
                .onErrorResume(e -> {
                    String reason = e instanceof TimeoutException
                            ? "timed out after " + properties.getProjectionTimeout()
                            : e.getMessage();
                    log.warn("Freelancer projection failed for {}: {}",
                            exchange.getRequest().getPath(), reason);
                    provisioningFailures.increment();
                    return Mono.empty();
                });

        return provision.then(chain.filter(exchange));
    }

    static boolean requiresProjection(PathContainer path) {
        for (PathPattern pattern : PROJECTION_ROUTES) {
            if (pattern.matches(path)) return true;
        }
        return false;
    }

    private Mono<Void> ensureProjected(ProjectionKey key) {
        if (projected.getIfPresent(key) != null) return Mono.empty();
        return projectionService.projectIfAbsent(key.coreUserId(), key.tenantId())
                .timeout(properties.getProjectionTimeout())
                .doOnNext(isFreelancer -> {
                    if (Boolean.TRUE.equals(isFreelancer)) projected.put(key, Boolean.TRUE);
                })
                .then();
    }

    private static UUID coreUserId(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return null;
        String name = auth.getName();
        if (name == null || name.isBlank()) return null;
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private record ProjectionKey(UUID tenantId, UUID coreUserId) {
    }
}
