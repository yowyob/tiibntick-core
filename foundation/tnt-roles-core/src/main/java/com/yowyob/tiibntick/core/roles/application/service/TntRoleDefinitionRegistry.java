package com.yowyob.tiibntick.core.roles.application.service;

import com.yowyob.tiibntick.core.roles.domain.model.TntRole;
import com.yowyob.tiibntick.core.roles.domain.model.TntRoleDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Singleton registry holding all TiiBnTick role definitions as value objects.
 *
 * <p>Built from {@link TntRole} enum constants at construction time.
 * This registry is the authoritative, in-memory source of truth for TiiBnTick
 * role definitions. It does NOT persist to DB — persistence is the Kernel's
 * responsibility via {@link com.yowyob.tiibntick.core.roles.application.port.out.ITntRoleProvisioningPort}.
 *
 * <p>Consumers:
 * <ul>
 *   <li>{@link TntRoleInitializationService} — seeds roles at startup</li>
 *   <li>{@code tnt-administration-core} — provisions roles per-tenant on onboarding</li>
 *   <li>Any module needing to enumerate valid TiiBnTick roles</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public class TntRoleDefinitionRegistry {

    private static final Logger log = LoggerFactory.getLogger(TntRoleDefinitionRegistry.class);

    /**
     * Role hierarchy for {@code resolveHighestRole}: index 0 = highest privilege.
     */
    private static final List<TntRole> ROLE_HIERARCHY = List.of(
            TntRole.TNT_ADMIN,
            TntRole.ORG_ADMIN,
            TntRole.AGENCY_MANAGER,
            TntRole.BRANCH_MANAGER,
            TntRole.SUPPORT_AGENT,
            TntRole.PERMANENT_DELIVERER,
            TntRole.RELAY_OPERATOR,
            TntRole.FREELANCER,
            TntRole.CLIENT
    );

    private final Map<String, TntRoleDefinition> definitionsByCode;

    public TntRoleDefinitionRegistry() {
        this.definitionsByCode = Arrays.stream(TntRole.values())
                .collect(Collectors.toUnmodifiableMap(
                        TntRole::code,
                        TntRoleDefinition::from
                ));
        log.info("TntRoleDefinitionRegistry initialized with {} role definitions: {}",
                definitionsByCode.size(),
                definitionsByCode.keySet());
    }

    /**
     * Returns all TiiBnTick role definitions.
     */
    public List<TntRoleDefinition> getAllDefinitions() {
        return List.copyOf(definitionsByCode.values());
    }

    /**
     * Returns the definition for the given role code, or empty if unknown.
     *
     * @param roleCode TiiBnTick role code (case-insensitive)
     */
    public Optional<TntRoleDefinition> findByCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitionsByCode.get(roleCode.toUpperCase()));
    }

    /**
     * Returns the definition for the given role code.
     *
     * @throws com.yowyob.tiibntick.core.roles.domain.exception.TntRoleException if not found
     */
    public TntRoleDefinition getByCode(String roleCode) {
        return findByCode(roleCode).orElseThrow(
                () -> com.yowyob.tiibntick.core.roles.domain.exception.TntRoleException.unknownRole(roleCode)
        );
    }

    /**
     * Returns the role hierarchy list (highest privilege first).
     */
    public List<TntRole> getRoleHierarchy() {
        return ROLE_HIERARCHY;
    }

    /**
     * Returns the hierarchy index of a role code (lower = higher privilege).
     * Returns {@link Integer#MAX_VALUE} for unknown roles.
     */
    public int hierarchyIndex(String roleCode) {
        for (int i = 0; i < ROLE_HIERARCHY.size(); i++) {
            if (ROLE_HIERARCHY.get(i).code().equalsIgnoreCase(roleCode)) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    /**
     * Returns true if the given code is a recognized TiiBnTick role.
     */
    public boolean isKnownRole(String code) {
        return code != null && definitionsByCode.containsKey(code.toUpperCase());
    }

    /**
     * Returns only the system roles (cannot be modified by tenants).
     */
    public List<TntRoleDefinition> getSystemRoles() {
        return definitionsByCode.values().stream()
                .filter(TntRoleDefinition::systemRole)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Returns only the editable (non-system) roles for tenant customization.
     */
    public List<TntRoleDefinition> getEditableRoles() {
        return definitionsByCode.values().stream()
                .filter(TntRoleDefinition::editable)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Total number of known TiiBnTick role definitions.
     */
    public int size() {
        return definitionsByCode.size();
    }
}
