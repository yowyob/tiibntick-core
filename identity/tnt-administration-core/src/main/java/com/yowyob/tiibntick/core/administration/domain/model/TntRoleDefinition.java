package com.yowyob.tiibntick.core.administration.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Domain aggregate tracking the provisioning of a TiiBnTick role template for a specific tenant.
 *
 * <p>When {@code provisionForTenant} is called, TiiBnTick creates a {@code TntRoleDefinition}
 * for each {@link TntRoleTemplateRegistry.TntRoleTemplate} provisioned. The
 * {@code kernelRoleId} field stores the UUID of the role created in the Yowyob Kernel
 * (RT-comops-roles-core), establishing a logical cross-database link.
 *
 * <p>Logical integration: {@code kernelRoleId} → yow_kernel_db.roles.id (no physical FK).
 *
 * @author MANFOUO Braun
 */
public final class TntRoleDefinition {

    private final UUID id;

    /** Tenant this definition belongs to. */
    private final UUID tenantId;

    /** The TNT role template code this definition was created from (e.g., "TNT_DISPATCHER"). */
    private final String templateCode;

    /** Human-readable name for this role definition. */
    private final String name;

    /** Scope type: TENANT, ORGANIZATION, AGENCY. */
    private final String scopeType;

    /** Permission codes assigned to this role. */
    private final Set<String> permissionCodes;

    /** Whether this is a protected (system) role definition that cannot be deleted. */
    private final boolean protectedDefinition;

    /**
     * Integration key → yow_kernel_db.roles.id (RT-comops-roles-core).
     * Populated once the Kernel has confirmed creation of the corresponding role.
     * Null until provisioning is confirmed.
     * Logical reference only — no physical FK cross-database.
     */
    private final UUID kernelRoleId;

    /** Whether the Kernel has confirmed this role definition as active. */
    private final boolean kernelSynced;

    private final Instant createdAt;
    private final Instant updatedAt;

    private TntRoleDefinition(UUID id, UUID tenantId, String templateCode, String name,
                               String scopeType, Set<String> permissionCodes,
                               boolean protectedDefinition, UUID kernelRoleId,
                               boolean kernelSynced, Instant createdAt, Instant updatedAt) {
        this.id                  = Objects.requireNonNull(id, "id must not be null");
        this.tenantId            = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.templateCode        = Objects.requireNonNull(templateCode, "templateCode must not be null");
        this.name                = Objects.requireNonNull(name, "name must not be null");
        this.scopeType           = Objects.requireNonNull(scopeType, "scopeType must not be null");
        this.permissionCodes     = permissionCodes != null ? Set.copyOf(permissionCodes) : Set.of();
        this.protectedDefinition = protectedDefinition;
        this.kernelRoleId        = kernelRoleId;
        this.kernelSynced        = kernelSynced;
        this.createdAt           = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt           = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    /**
     * Creates a new TntRoleDefinition from a provisioned role template.
     * The kernelRoleId is initially null — call {@link #withKernelRoleId} once the Kernel confirms.
     */
    public static TntRoleDefinition provision(UUID tenantId, String templateCode, String name,
                                               String scopeType, Set<String> permissionCodes,
                                               boolean protectedDefinition) {
        Instant now = Instant.now();
        return new TntRoleDefinition(UUID.randomUUID(), tenantId, templateCode, name,
                scopeType, permissionCodes, protectedDefinition, null, false, now, now);
    }

    /**
     * Rehydrates a TntRoleDefinition from persistent storage.
     */
    public static TntRoleDefinition rehydrate(UUID id, UUID tenantId, String templateCode,
                                               String name, String scopeType,
                                               Set<String> permissionCodes,
                                               boolean protectedDefinition, UUID kernelRoleId,
                                               boolean kernelSynced,
                                               Instant createdAt, Instant updatedAt) {
        return new TntRoleDefinition(id, tenantId, templateCode, name, scopeType,
                permissionCodes, protectedDefinition, kernelRoleId, kernelSynced,
                createdAt, updatedAt);
    }

    /**
     * Returns a copy of this definition with the Kernel role UUID set and synced flag true.
     * Called once the Kernel confirms creation of the corresponding role.
     */
    public TntRoleDefinition withKernelRoleId(UUID kernelRoleId) {
        return new TntRoleDefinition(id, tenantId, templateCode, name, scopeType,
                permissionCodes, protectedDefinition, kernelRoleId, true,
                createdAt, Instant.now());
    }

    public UUID getId()                  { return id; }
    public UUID getTenantId()            { return tenantId; }
    public String getTemplateCode()      { return templateCode; }
    public String getName()              { return name; }
    public String getScopeType()         { return scopeType; }
    public Set<String> getPermissionCodes() { return permissionCodes; }
    public boolean isProtectedDefinition() { return protectedDefinition; }
    public UUID getKernelRoleId()        { return kernelRoleId; }
    public boolean isKernelSynced()      { return kernelSynced; }
    public Instant getCreatedAt()        { return createdAt; }
    public Instant getUpdatedAt()        { return updatedAt; }
}
