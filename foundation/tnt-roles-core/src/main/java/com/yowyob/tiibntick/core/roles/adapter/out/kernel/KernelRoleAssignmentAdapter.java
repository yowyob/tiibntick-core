package com.yowyob.tiibntick.core.roles.adapter.out.kernel;

import com.yowyob.tiibntick.common.kernel.KernelResponses;
import com.yowyob.tiibntick.core.roles.application.port.out.ITntRoleAssignmentPort;
import com.yowyob.tiibntick.core.roles.application.port.out.ITntRoleProvisioningPort;
import com.yowyob.tiibntick.core.roles.domain.exception.TntRoleException;
import com.yowyob.tiibntick.core.roles.domain.model.TntRoleDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Adapter implementing {@link ITntRoleAssignmentPort} by calling the Kernel's
 * {@code POST /api/roles/assignments} over HTTP.
 *
 * <p>Uses {@code kernelTpWebClient} (defined in {@code tnt-bootstrap}'s
 * {@code KernelBridgeConfig}) rather than the plain {@code kernelWebClient} used by
 * {@link KernelRoleProvisioningAdapter} — its {@code propagateBearerToken} filter
 * forwards the calling admin's own JWT to the Kernel, so the assignment is attributed
 * to them in the Kernel's audit trail rather than to TiiBnTick's service identity.
 *
 * @author MANFOUO Braun
 */
public class KernelRoleAssignmentAdapter implements ITntRoleAssignmentPort {

    private static final Logger log = LoggerFactory.getLogger(KernelRoleAssignmentAdapter.class);

    private final WebClient kernelTpWebClient;
    private final ITntRoleProvisioningPort provisioningPort;
    private final UUID systemTenantId;

    public KernelRoleAssignmentAdapter(WebClient kernelTpWebClient,
                                        ITntRoleProvisioningPort provisioningPort,
                                        UUID systemTenantId) {
        this.kernelTpWebClient = kernelTpWebClient;
        this.provisioningPort = provisioningPort;
        this.systemTenantId = systemTenantId;
    }

    @Override
    public Mono<UUID> assignRole(UUID targetUserId, String roleCode, String scopeType, UUID scopeId) {
        // Role definitions always live under the system tenant (see TntRoleInitializationService),
        // regardless of which tenant/org/agency the assignment itself is scoped to.
        return provisioningPort.findRoleId(systemTenantId, roleCode)
                .switchIfEmpty(Mono.error(TntRoleException.roleNotFoundInKernel(roleCode)))
                .flatMap(roleId -> doAssign(targetUserId, roleId, scopeType, scopeId));
    }

    private Mono<UUID> doAssign(UUID targetUserId, UUID roleId, String scopeType, UUID scopeId) {
        KernelAssignRoleRequest request = new KernelAssignRoleRequest(
                targetUserId, roleId, scopeType, scopeId, scopeType + ":" + scopeId);
        var responseSpec = kernelTpWebClient
                .post()
                .uri("/api/roles/assignments")
                .bodyValue(request)
                .retrieve();
        return KernelResponses.unwrapObjectOrPropagate(responseSpec, KernelUserRoleAssignmentResponse.class)
                .map(KernelUserRoleAssignmentResponse::id)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Kernel returned no assignment id for role {} → user {}", roleId, targetUserId);
                    return Mono.error(TntRoleException.assignmentFailed(roleId.toString(), targetUserId));
                }));
    }

    @Override
    public Mono<UUID> assignRoleInCallerTenant(UUID targetUserId, TntRoleDefinition definition,
                                               String scopeType, UUID scopeId) {
        return findOrCreateRoleInCallerTenant(definition)
                .flatMap(roleId -> existingAssignmentId(targetUserId, roleId)
                        .switchIfEmpty(Mono.defer(() -> doAssign(targetUserId, roleId, scopeType, scopeId))));
    }

    private Mono<UUID> findOrCreateRoleInCallerTenant(TntRoleDefinition definition) {
        return listCallerTenantRoles()
                .filter(r -> definition.code().equals(r.code()))
                .map(KernelRoleResponse::id)
                .next()
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("Role '{}' missing in the caller's tenant — provisioning it on demand", definition.code());
                    var request = new KernelCreateRoleRequest(definition.code(), definition.name(),
                            definition.scopeTypeCode(), java.util.List.copyOf(definition.defaultPermissions()));
                    var spec = kernelTpWebClient.post().uri("/api/roles").bodyValue(request).retrieve();
                    return readObject(spec, KernelRoleResponse.class)
                            .map(KernelRoleResponse::id)
                            .onErrorResume(WebClientResponseException.Conflict.class,
                                    e -> listCallerTenantRoles()
                                            .filter(r -> definition.code().equals(r.code()))
                                            .map(KernelRoleResponse::id)
                                            .next());
                }))
                .switchIfEmpty(Mono.error(TntRoleException.roleNotFoundInKernel(definition.code())));
    }

    private reactor.core.publisher.Flux<KernelRoleResponse> listCallerTenantRoles() {
        var spec = kernelTpWebClient.get().uri("/api/roles").retrieve();
        return readList(spec, KernelRoleResponse.class);
    }

    private Mono<UUID> existingAssignmentId(UUID userId, UUID roleId) {
        var spec = kernelTpWebClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/roles/assignments").queryParam("userId", userId).build())
                .retrieve();
        return readList(spec, KernelUserRoleAssignmentResponse.class)
                .filter(a -> roleId.equals(a.roleId()))
                .map(KernelUserRoleAssignmentResponse::id)
                .next();
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper LENIENT_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper()
                    .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * The Kernel's role endpoints are documented both as a bare array ({@code GET /api/roles} →
     * {@code RoleResponse[]}) and as an {@code ApiResponse} envelope: accept either, and let
     * HTTP errors propagate (a swallowed error here would read as "role missing" and trigger a
     * pointless create).
     */
    private static <T> reactor.core.publisher.Flux<T> readList(WebClient.ResponseSpec spec, Class<T> type) {
        return parseBody(spec)
                .flatMapMany(node -> {
                    com.fasterxml.jackson.databind.JsonNode items = node.isArray() ? node : node.path("data");
                    if (!items.isArray()) {
                        return reactor.core.publisher.Flux.empty();
                    }
                    java.util.List<T> out = new java.util.ArrayList<>();
                    items.forEach(n -> out.add(LENIENT_MAPPER.convertValue(n, type)));
                    return reactor.core.publisher.Flux.fromIterable(out);
                });
    }

    /** Reads the raw body and parses it here, so the result does not depend on the client's codecs. */
    private static Mono<com.fasterxml.jackson.databind.JsonNode> parseBody(WebClient.ResponseSpec spec) {
        return spec.bodyToMono(String.class).flatMap(raw -> {
            try {
                return raw.isBlank() ? Mono.empty() : Mono.just(LENIENT_MAPPER.readTree(raw));
            } catch (Exception e) {
                return Mono.error(e);
            }
        });
    }

    private static <T> Mono<T> readObject(WebClient.ResponseSpec spec, Class<T> type) {
        return parseBody(spec)
                .flatMap(node -> {
                    com.fasterxml.jackson.databind.JsonNode body = node.has("data") && node.get("data").isObject()
                            ? node.get("data") : node;
                    return body.isObject() && body.has("id")
                            ? Mono.just(LENIENT_MAPPER.convertValue(body, type)) : Mono.empty();
                });
    }

    @Override
    public Mono<Void> revokeAssignment(UUID kernelAssignmentId) {
        return kernelTpWebClient
                .delete()
                .uri("/api/roles/assignments/{id}", kernelAssignmentId)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.info("Revoked role assignment {}", kernelAssignmentId))
                .onErrorResume(WebClientResponseException.class, e -> {
                    if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                        // Assignment already revoked on the Kernel side — idempotent, treat as success
                        log.debug("Assignment {} not found in Kernel (404) — already revoked, skipping.", kernelAssignmentId);
                        return Mono.empty();
                    }
                    return Mono.error(TntRoleException.revocationFailed(kernelAssignmentId, e));
                })
                .onErrorResume(e -> !(e instanceof TntRoleException),
                        e -> Mono.error(TntRoleException.revocationFailed(kernelAssignmentId, e)));
    }

    @Override
    public Mono<Boolean> assignmentExists(UUID userId, UUID kernelAssignmentId) {
        var responseSpec = kernelTpWebClient
                .get()
                .uri(uriBuilder -> uriBuilder.path("/api/roles/assignments")
                        .queryParam("userId", userId)
                        .build())
                .retrieve();
        return KernelResponses.unwrapList(responseSpec, KernelUserRoleAssignmentResponse.class, log,
                        "assignmentExists " + kernelAssignmentId)
                .any(a -> kernelAssignmentId.equals(a.id()))
                .onErrorResume(WebClientResponseException.class, e -> {
                    log.warn("Could not check assignment existence for {}: HTTP {} — assuming it still exists " +
                            "(fail-safe: reconciliation must not re-provision on a transient error)",
                            kernelAssignmentId, e.getStatusCode());
                    return Mono.just(true);
                })
                .onErrorResume(e -> {
                    log.warn("Kernel unreachable while checking assignment {}: {} — assuming it still exists",
                            kernelAssignmentId, e.getMessage());
                    return Mono.just(true);
                });
    }

    /** Matches the Kernel's {@code CreateRoleRequest} schema. */
    record KernelCreateRoleRequest(String code, String name, String scopeType, java.util.List<String> permissions) {
    }

    /** Minimal projection of the Kernel's {@code RoleResponse}. */
    record KernelRoleResponse(UUID id, String code, String name, String scopeType) {
    }

    /** Matches the Kernel's {@code AssignRoleToUserRequest} schema. */
    record KernelAssignRoleRequest(UUID userId, UUID roleId, String scopeType, UUID scopeId, String scope) {
    }

    /** Matches the Kernel's {@code UserRoleAssignmentResponse} schema. */
    record KernelUserRoleAssignmentResponse(UUID id, UUID tenantId, UUID userId, UUID roleId,
                                             String scopeType, UUID scopeId, String scope) {
    }
}
