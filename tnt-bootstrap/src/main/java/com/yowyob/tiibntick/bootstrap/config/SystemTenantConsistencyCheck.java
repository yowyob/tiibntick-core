package com.yowyob.tiibntick.bootstrap.config;

import com.yowyob.tiibntick.common.tenant.SystemTenant;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * TiiBnTick has exactly ONE system tenant. {@code static final} fields in several modules
 * (Go-Freelancer, accounting…) read it through {@link SystemTenant#id()} (system property /
 * {@code TNT_SYSTEM_TENANT_ID}), while the roles module reads {@code tnt.roles.system-tenant-id}
 * from Spring. This guard refuses to start if the two disagree — typically because the
 * property was set in a YAML file instead of through {@code TNT_SYSTEM_TENANT_ID}.
 *
 * @author MANFOUO Braun
 */
@Component
public class SystemTenantConsistencyCheck {

    private final String configured;

    public SystemTenantConsistencyCheck(
            @Value("${tnt.roles.system-tenant-id}") String configured) {
        this.configured = configured;
    }

    @PostConstruct
    void verify() {
        check(configured, SystemTenant.id());
    }

    static void check(String configured, UUID effective) {
        UUID springValue;
        try {
            springValue = UUID.fromString(configured.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("tnt.roles.system-tenant-id is not a UUID: '" + configured + "'", ex);
        }
        if (!springValue.equals(effective)) {
            throw new IllegalStateException("Two different system tenants configured: tnt.roles.system-tenant-id="
                    + springValue + " but the JVM-wide system tenant is " + effective + ". Set ONLY the environment "
                    + "variable " + SystemTenant.ENV_VARIABLE + " (or the JVM property " + SystemTenant.SYSTEM_PROPERTY
                    + "); do not hard-code the value in a YAML file.");
        }
    }
}
