package com.yowyob.tiibntick.core.roles.adapter.out.kernel;

import com.yowyob.tiibntick.common.kernel.KernelResponses;
import com.yowyob.tiibntick.core.roles.application.port.out.ITntRoleProvisioningPort;
import com.yowyob.tiibntick.core.roles.domain.exception.TntRoleException;
import com.yowyob.tiibntick.core.roles.domain.model.TntRole;
import com.yowyob.tiibntick.core.roles.domain.model.TntRoleDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Locale;
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
                    return declarerVocabulaire(tenantId).then(doProvision(tenantId, definition));
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
        return KernelRoleLists.readList(responseSpec, KernelRoleResponse.class)
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
    public Mono<UUID> findRoleId(UUID tenantId, String roleCode) {
        var responseSpec = kernelWebClient
                .get()
                .uri("/api/roles")
                .header("X-Tenant-Id", tenantId.toString())
                .retrieve();
        // Accepte le tableau nu comme l'enveloppe, et laisse les erreurs remonter : ici une liste
        // vide fait conclure « role non provisionne », verdict qu'un echec de decodage ne doit pas
        // pouvoir prononcer.
        return KernelRoleLists.readList(responseSpec, KernelRoleResponse.class)
                .filter(r -> roleCode.equals(r.code()))
                .map(KernelRoleResponse::id)
                .next();
    }

    @Override
    public Mono<Void> invalidatePermissionCache(UUID tenantId, UUID userId) {
        // The Kernel does not expose a permission-cache invalidation REST endpoint.
        // Cache invalidation on the Kernel side happens automatically on role/permission changes.
        log.debug("Permission cache invalidation requested for user {} (tenant {}) — delegated to Kernel internal eviction.",
                userId, tenantId);
        return Mono.empty();
    }

    @Override
    public Mono<Void> deleteRole(UUID tenantId, UUID kernelRoleId) {
        return kernelWebClient
                .delete()
                .uri("/api/roles/{id}", kernelRoleId)
                .header("X-Tenant-Id", tenantId.toString())
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.info("Deleted TiiBnTick role {} for tenant {}", kernelRoleId, tenantId))
                .onErrorResume(WebClientResponseException.class, e -> {
                    if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                        // Role already gone on the Kernel side — idempotent, treat as success
                        log.debug("Role {} not found in Kernel (404) — already deleted, skipping.", kernelRoleId);
                        return Mono.empty();
                    }
                    return Mono.error(TntRoleException.deletionFailed(kernelRoleId, e));
                })
                .onErrorResume(e -> !(e instanceof TntRoleException),
                        e -> Mono.error(TntRoleException.deletionFailed(kernelRoleId, e)));
    }

    @Override
    public Mono<Boolean> roleExistsById(UUID tenantId, UUID kernelRoleId) {
        return kernelWebClient
                .get()
                .uri("/api/roles/{id}", kernelRoleId)
                .header("X-Tenant-Id", tenantId.toString())
                .retrieve()
                .bodyToMono(Void.class)
                .thenReturn(true)
                .onErrorResume(WebClientResponseException.NotFound.class, e -> {
                    log.debug("Role {} not found in Kernel (404) during reconciliation.", kernelRoleId);
                    return Mono.just(false);
                })
                .onErrorResume(e -> {
                    log.warn("Could not check role existence by id {} for tenant {}: {} — assuming it still " +
                                    "exists (fail-safe: reconciliation must not re-provision on a transient error)",
                            kernelRoleId, tenantId, e.getMessage());
                    return Mono.just(true);
                });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    /**
     * Déclare au noyau le vocabulaire de permissions de TiiBnTick, une fois pour toutes.
     *
     * <p>Le noyau refuse un rôle portant une permission qu'il ne connaît pas, et il a raison de le
     * faire : une permission inconnue ne lève aucune erreur ensuite, elle ne satisfait simplement
     * aucun contrôle, et son titulaire croit détenir un droit qu'il n'a pas. Mais le catalogue du
     * noyau décrit le noyau — il ignore {@code MISSION_CREATE}. Faute de cette déclaration, les
     * neuf rôles étaient refusés en 400, sans qu'aucune des deux parties n'ait tort.</p>
     *
     * <p>Mise en cache : les rôles se provisionnent un par un, et le vocabulaire est le même pour
     * tous. Le déclarer à chaque rôle ferait neuf appels identiques. {@code cache()} retient aussi
     * l'échec — un vocabulaire refusé le reste jusqu'au redémarrage, ce qui est le comportement
     * voulu : rien ne sert de rejouer une déclaration que le noyau vient d'interdire.</p>
     */
    private final java.util.Map<UUID, Mono<Void>> vocabulaireParTenant = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public Mono<Void> declareSolutionVocabulary(UUID tenantId) {
        return declarerVocabulaire(tenantId);
    }

    private Mono<Void> declarerVocabulaire(UUID tenantId) {
        // Un cache PAR tenant, et non un champ partagé : le tenant fait partie de la requête, et le
        // faire voyager dans un champ mutable rendrait le résultat dépendant de l'ordre des appels.
        return vocabulaireParTenant.computeIfAbsent(tenantId,
                tenant -> Mono.defer(() -> envoyerVocabulaire(tenant)).cache());
    }

    private Mono<Void> envoyerVocabulaire(UUID tenantId) {
        List<PermissionDeclaree> permissions = java.util.Arrays.stream(TntRole.values())
                .flatMap(role -> role.defaultPermissions().stream())
                .distinct()
                .sorted()
                .map(code -> new PermissionDeclaree(code, libelle(code),
                        "Permission métier TiiBnTick.", "TIIBNTICK", null))
                .toList();
        if (permissions.isEmpty()) {
            return Mono.empty();
        }
        return kernelWebClient
                .post()
                .uri("/api/permissions/solution")
                .header("X-Tenant-Id", tenantId.toString())
                .bodyValue(new DeclarationVocabulaire("TNT", permissions))
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.info("Vocabulaire TiiBnTick declare au noyau ({} permissions).",
                        permissions.size()))
                .onErrorMap(cause -> TntRoleException.provisioningFailed("<vocabulaire>", cause));
    }

    /** « MISSION_CREATE » → « Mission create » : un code nu est illisible en relecture de droits. */
    private static String libelle(String code) {
        String texte = code.replace('_', ' ').toLowerCase(Locale.ROOT).trim();
        return texte.isEmpty() ? code : Character.toUpperCase(texte.charAt(0)) + texte.substring(1);
    }

    /** Corps attendu par {@code POST /api/permissions/solution}. */
    record DeclarationVocabulaire(String solutionCode, List<PermissionDeclaree> permissions) {
    }

    record PermissionDeclaree(String code, String name, String description, String module, String scope) {
    }

    private Mono<Void> doProvision(UUID tenantId, TntRoleDefinition definition) {
        KernelCreateRoleRequest request = KernelCreateRoleRequest.from(definition);
        return kernelWebClient
                .post()
                // /api/roles exige un administrateur d'ORGANISATION cote noyau et rattache le role
                // a cette organisation. Un satellite n'a pas d'organisation : il declare des roles
                // d'ESPACE, via l'endpoint prevu pour cela.
                .uri("/api/roles/solution")
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