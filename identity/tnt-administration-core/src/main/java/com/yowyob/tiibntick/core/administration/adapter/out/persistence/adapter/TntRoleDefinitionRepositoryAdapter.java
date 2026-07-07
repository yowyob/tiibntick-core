package com.yowyob.tiibntick.core.administration.adapter.out.persistence.adapter;

import com.yowyob.tiibntick.core.administration.adapter.out.persistence.entity.TntRoleDefinitionEntity;
import com.yowyob.tiibntick.core.administration.adapter.out.persistence.repository.TntRoleDefinitionR2dbcRepository;
import com.yowyob.tiibntick.core.administration.application.port.out.TntRoleDefinitionRepository;
import com.yowyob.tiibntick.core.administration.domain.model.TntRoleDefinition;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * R2DBC adapter implementing {@link TntRoleDefinitionRepository}.
 *
 * <p>Handles serialization of permission codes (domain Set{@code <}String{@code >}
 * ↔ comma-separated DB column) and mapping between domain aggregates and R2DBC entities.
 *
 * @author MANFOUO Braun
 */
@Component
public class TntRoleDefinitionRepositoryAdapter implements TntRoleDefinitionRepository {

    private final TntRoleDefinitionR2dbcRepository repository;
    private final R2dbcEntityTemplate entityTemplate;

    public TntRoleDefinitionRepositoryAdapter(TntRoleDefinitionR2dbcRepository repository,
                                              R2dbcEntityTemplate entityTemplate) {
        this.repository = repository;
        this.entityTemplate = entityTemplate;
    }

    @Override
    public Mono<TntRoleDefinition> save(TntRoleDefinition definition) {
        var entity = toEntity(definition);
        return repository.existsById(entity.id())
                .flatMap(exists -> exists
                        ? entityTemplate.update(entity)
                        : entityTemplate.insert(entity))
                .map(this::toDomain);
    }

    @Override
    public Mono<TntRoleDefinition> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Flux<TntRoleDefinition> findAllByTenantId(UUID tenantId) {
        return repository.findAllByTenantId(tenantId).map(this::toDomain);
    }

    @Override
    public Mono<TntRoleDefinition> findByTenantIdAndTemplateCode(UUID tenantId, String templateCode) {
        return repository.findByTenantIdAndTemplateCode(tenantId, templateCode).map(this::toDomain);
    }

    @Override
    public Mono<Boolean> existsByTenantIdAndTemplateCode(UUID tenantId, String templateCode) {
        return repository.existsByTenantIdAndTemplateCode(tenantId, templateCode);
    }

    @Override
    public Flux<TntRoleDefinition> findAllPendingKernelSync() {
        return repository.findAllPendingKernelSync().map(this::toDomain);
    }

    // ─── Mapping ────────────────────────────────────────────────────────────────

    private TntRoleDefinitionEntity toEntity(TntRoleDefinition d) {
        String permCodesStr = d.getPermissionCodes() == null ? ""
                : String.join(",", d.getPermissionCodes());
        return new TntRoleDefinitionEntity(
                d.getId(), d.getTenantId(), d.getTemplateCode(), d.getName(),
                d.getScopeType(), permCodesStr, d.isProtectedDefinition(),
                d.getKernelRoleId(), d.isKernelSynced(),
                d.getCreatedAt(), d.getUpdatedAt());
    }

    private TntRoleDefinition toDomain(TntRoleDefinitionEntity e) {
        // Deserialize comma-separated permission codes back to a Set
        Set<String> permCodes = (e.permissionCodes() == null || e.permissionCodes().isBlank())
                ? Set.of()
                : Arrays.stream(e.permissionCodes().split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toUnmodifiableSet());
        return TntRoleDefinition.rehydrate(
                e.id(), e.tenantId(), e.templateCode(), e.name(),
                e.scopeType(), permCodes, e.protectedDefinition(),
                e.kernelRoleId(), e.kernelSynced(),
                e.createdAt(), e.updatedAt());
    }
}
