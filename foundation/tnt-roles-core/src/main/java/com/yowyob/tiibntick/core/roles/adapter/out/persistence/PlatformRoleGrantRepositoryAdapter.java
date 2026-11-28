package com.yowyob.tiibntick.core.roles.adapter.out.persistence;

import com.yowyob.tiibntick.core.roles.application.port.out.PlatformRoleGrantRepository;
import com.yowyob.tiibntick.core.roles.domain.model.PlatformGrantKind;
import com.yowyob.tiibntick.core.roles.domain.model.PlatformRoleGrant;
import io.r2dbc.spi.Readable;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * R2DBC adapter of {@link PlatformRoleGrantRepository} on {@code tnt_platform_role_grants} (changeset 005).
 * The partial unique index {@code ux_tprg_active} makes a second active grant impossible, also under
 * concurrent first logins of the owner.
 *
 * @author MANFOUO Braun
 */
public class PlatformRoleGrantRepositoryAdapter implements PlatformRoleGrantRepository {

    private static final String COLUMNS = "id, role_code, user_id, grant_kind, granted_by, source_tenant_id, "
            + "system_tenant_at_grant, granted_at, revoked_at, revoked_by, revoke_reason, revoke_batch_id";

    private final DatabaseClient db;

    public PlatformRoleGrantRepositoryAdapter(DatabaseClient db) {
        this.db = db;
    }

    @Override
    public Mono<PlatformRoleGrant> findActive(String roleCode, UUID userId) {
        return db.sql("SELECT " + COLUMNS + " FROM tnt_platform_role_grants "
                        + "WHERE role_code = :role AND user_id = :user AND revoked_at IS NULL")
                .bind("role", roleCode).bind("user", userId)
                .map(PlatformRoleGrantRepositoryAdapter::map).one();
    }

    @Override
    public Mono<PlatformRoleGrant> findById(UUID id) {
        return db.sql("SELECT " + COLUMNS + " FROM tnt_platform_role_grants WHERE id = :id")
                .bind("id", id).map(PlatformRoleGrantRepositoryAdapter::map).one();
    }

    @Override
    public Flux<PlatformRoleGrant> findAll(String roleCode, boolean includeRevoked) {
        return db.sql("SELECT " + COLUMNS + " FROM tnt_platform_role_grants WHERE role_code = :role"
                        + (includeRevoked ? "" : " AND revoked_at IS NULL") + " ORDER BY granted_at DESC, id")
                .bind("role", roleCode).map(PlatformRoleGrantRepositoryAdapter::map).all();
    }

    @Override
    public Flux<PlatformRoleGrant> findActiveFromOtherTenants(String roleCode, UUID systemTenantId) {
        return db.sql("SELECT " + COLUMNS + " FROM tnt_platform_role_grants WHERE role_code = :role "
                        + "AND revoked_at IS NULL AND source_tenant_id <> :system ORDER BY id")
                .bind("role", roleCode).bind("system", systemTenantId)
                .map(PlatformRoleGrantRepositoryAdapter::map).all();
    }

    @Override
    public Mono<PlatformRoleGrant> insertIfNoActive(PlatformRoleGrant g) {
        return db.sql("INSERT INTO tnt_platform_role_grants (id, role_code, user_id, grant_kind, granted_by, "
                        + "source_tenant_id, system_tenant_at_grant, granted_at) "
                        + "VALUES (:id, :role, :user, :kind, :by, :source, :system, :at) "
                        + "ON CONFLICT (role_code, user_id) WHERE revoked_at IS NULL DO NOTHING "
                        + "RETURNING " + COLUMNS)
                .bind("id", g.id()).bind("role", g.roleCode()).bind("user", g.userId())
                .bind("kind", g.kind().name()).bind("by", g.grantedBy()).bind("source", g.sourceTenantId())
                .bind("system", g.systemTenantAtGrant()).bind("at", g.grantedAt())
                .map(PlatformRoleGrantRepositoryAdapter::map).one();
    }

    @Override
    public Mono<Boolean> revoke(UUID id, UUID revokedBy, String reason, UUID batchId, Instant at) {
        DatabaseClient.GenericExecuteSpec spec = db.sql("UPDATE tnt_platform_role_grants SET revoked_at = :at, "
                        + "revoked_by = :by, revoke_reason = :reason, revoke_batch_id = :batch "
                        + "WHERE id = :id AND revoked_at IS NULL")
                .bind("at", at).bind("by", revokedBy).bind("id", id);
        spec = reason == null ? spec.bindNull("reason", String.class) : spec.bind("reason", reason);
        spec = batchId == null ? spec.bindNull("batch", UUID.class) : spec.bind("batch", batchId);
        return spec.fetch().rowsUpdated().map(n -> n > 0);
    }

    private static PlatformRoleGrant map(Readable row) {
        return new PlatformRoleGrant(
                row.get("id", UUID.class),
                row.get("role_code", String.class),
                row.get("user_id", UUID.class),
                PlatformGrantKind.valueOf(row.get("grant_kind", String.class)),
                row.get("granted_by", UUID.class),
                row.get("source_tenant_id", UUID.class),
                row.get("system_tenant_at_grant", UUID.class),
                instant(row.get("granted_at", OffsetDateTime.class)),
                instant(row.get("revoked_at", OffsetDateTime.class)),
                row.get("revoked_by", UUID.class),
                row.get("revoke_reason", String.class),
                row.get("revoke_batch_id", UUID.class));
    }

    private static Instant instant(OffsetDateTime t) {
        return t == null ? null : t.toInstant();
    }
}
