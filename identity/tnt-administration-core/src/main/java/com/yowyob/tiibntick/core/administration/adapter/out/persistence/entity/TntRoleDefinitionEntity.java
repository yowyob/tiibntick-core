package com.yowyob.tiibntick.core.administration.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC persistence entity for {@link com.yowyob.tiibntick.core.administration.domain.model.TntRoleDefinition}.
 *
 * <p>Stores the linkage between a TNT role template and a Kernel role (RT-comops-roles-core).
 * The {@code kernel_role_id} column is the logical integration key to yow_kernel_db.roles.id.
 * No physical foreign key constraint exists across databases.
 *
 * <p>Permission codes are stored as a comma-separated string due to R2DBC's lack of
 * native array support in a cross-database manner. The adapter handles serialization.
 *
 * @author MANFOUO Braun
 */
@Table(schema = "administration", name = "tnt_role_definitions")
public record TntRoleDefinitionEntity(
        @Id @Column("id")                       UUID id,
        @Column("tenant_id")                    UUID tenantId,
        @Column("template_code")                String templateCode,
        @Column("name")                         String name,
        @Column("scope_type")                   String scopeType,
        /** Comma-separated permission codes. Serialized/deserialized by the adapter. */
        @Column("permission_codes")             String permissionCodes,
        @Column("protected_definition")         boolean protectedDefinition,
        /**
         * Integration key → yow_kernel_db.roles.id (RT-comops-roles-core).
         * Null until the Kernel confirms role creation and the UUID is stored here.
         * Logical reference only — no physical FK cross-database.
         */
        @Column("kernel_role_id")               UUID kernelRoleId,
        @Column("kernel_synced")                boolean kernelSynced,
        @Column("created_at")                   Instant createdAt,
        @Column("updated_at")                   Instant updatedAt
) {}
