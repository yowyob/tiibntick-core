package com.yowyob.tiibntick.core.roles.adapter.out.kernel;

import com.yowyob.tiibntick.common.kernel.KernelResponses;
import com.yowyob.tiibntick.core.roles.application.port.out.ITntRoleProvisioningPort;
import com.yowyob.tiibntick.core.roles.domain.exception.TntRoleException;
import com.yowyob.tiibntick.core.roles.domain.model.TntRoleDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Adapter implementing {@link ITntRoleProvisioningPort} by calling the Kernel's
 * Role API over HTTP (REST).
 *
 * <p>Architecture rule: TiiBnTick Core NEVER injects beans from the Kernel's Spring context.
 * Communication with the Kernel is exclusively via HTTP (synchronous) or Kafka (async).
 * This adapter uses the pre-configured {@code kernelWebClient} bean from
 * {@link com.yowyob.tiibntick.common.config.KernelWebClientConfig}.
 *
 * <p>All operations are idempotent: a 409 CONFLICT response from the Kernel
 * (role already exists) is silently swallowed.
 *
 * @author MANFOUO Braun
 */
public class KernelRoleProvisioningAdapter implements ITntRoleProvisioningPort {

    private static final Logger log = LoggerFactory.getLogger(KernelRoleProvisioningAdapter.class);

    private final WebClient kernelWebClient;

    public KernelRoleProvisioningAdapter(WebClient kernelWebClient) {
        this.kernelWebClient = kernelWebClient;
    }

    @Override
    public Mono<Void> provisionRole(UUID tenantId, TntRoleDefinition definition) {
        return roleExists(tenantId, definition.code())
                .flatMap(exists -> {
                    if (exists) {
                        log.debug("Role '{}' already exists for tenant {} — skipping.", definition.code(), tenantId);
                        return Mono.<Void>empty();
                    }
                    return doProvision(tenantId, definition);
                });
    }

    @Override
    public Flux<String> provisionAll(UUID tenantId, List<TntRoleDefinition> definitions) {
        return Flux.fromIterable(definitions)
                .concatMap(definition ->
                        provisionRole(tenantId, definition)
                                .thenReturn(definition.code())
                                .onErrorResume(e -> {
                                    log.error("Failed to provision role '{}' for tenant {}: {}",
                                            definition.code(), tenantId, e.getMessage());
                                    return Mono.empty();
                                })
                );
    }

    @Override
    public Mono<Boolean> roleExists(UUID tenantId, String roleCode) {
        // The Kernel does not expose a /roles/exists?code= endpoint.
        // Instead, fetch the full role list and test whether the code is present.
        var responseSpec = kernelWebClient
                .get()
                .uri("/api/roles")
                .header("X-Tenant-Id", tenantId.toString())
                .retrieve();
        return KernelResponses.unwrapList(responseSpec, KernelRoleResponse.class, log, "roleExists " + roleCode)
                .any(r -> roleCode.equals(r.code()))
                .onErrorResume(WebClientResponseException.class, e -> {
                    log.warn("Could not check role existence for '{}': HTTP {} — assuming not found",
                            roleCode, e.getStatusCode());
                    return Mono.just(false);
                })
                .onErrorResume(e -> {
                    log.warn("Kernel unreachable while checking role '{}': {} — assuming not found",
                            roleCode, e.getMessage());
                    return Mono.just(false);
                });
    }

    @Override
    public Mono<Void> invalidatePermissionCache(UUID tenantId, UUID userId) {
        // The Kernel does not expose a permission-cache invalidation REST endpoint.
        // Cache invalidation on the Kernel side happens automatically on role/permission changes.
        log.debug("Permission cache invalidation requested for user {} (tenant {}) — delegated to Kernel internal eviction.",
                userId, tenantId);
        return Mono.empty();
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private Mono<Void> doProvision(UUID tenantId, TntRoleDefinition definition) {
        KernelCreateRoleRequest request = KernelCreateRoleRequest.from(definition);
        return kernelWebClient
                .post()
                .uri("/api/roles")
                .header("X-Tenant-Id", tenantId.toString())
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.info("Provisioned TiiBnTick role '{}' for tenant {}", definition.code(), tenantId))
                .onErrorResume(WebClientResponseException.class, e -> {
                    if (e.getStatusCode() == HttpStatus.CONFLICT) {
                        // Role already exists on the Kernel side — idempotent, skip
                        log.debug("Role '{}' already exists in Kernel (409 CONFLICT) — skipping.", definition.code());
                        return Mono.empty();
                    }
                    return Mono.error(TntRoleException.provisioningFailed(definition.code(), e));
                })
                .onErrorResume(e -> !(e instanceof TntRoleException),
                        e -> Mono.error(TntRoleException.provisioningFailed(definition.code(), e)));
    }

    /**
     * Internal DTO matching the Kernel's POST /api/roles {@code CreateRoleRequest} schema.
     * Fields: code, name, scopeType, permissions — no tenantId (sent as X-Tenant-Id header).
     */
    record KernelCreateRoleRequest(
            String code,
            String name,
            String scopeType,
            List<String> permissions
    ) {
        static KernelCreateRoleRequest from(TntRoleDefinition definition) {
            return new KernelCreateRoleRequest(
                    definition.code(),
                    definition.name(),
                    definition.scopeTypeCode(),
                    List.copyOf(definition.defaultPermissions())
            );
        }
    }

    /**
     * Minimal projection of the Kernel's {@code RoleResponse} — used only for
     * existence checks in {@link #roleExists(UUID, String)}.
     */
    record KernelRoleResponse(
            UUID id,
            String code,
            String name,
            String scopeType
    ) {}
}