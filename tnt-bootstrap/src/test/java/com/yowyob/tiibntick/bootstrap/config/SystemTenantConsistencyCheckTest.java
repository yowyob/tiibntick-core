package com.yowyob.tiibntick.bootstrap.config;

import com.yowyob.tiibntick.common.tenant.SystemTenant;
import com.yowyob.tiibntick.core.gofreelancer.application.service.TenantContextHolder;
import com.yowyob.tiibntick.core.roles.config.TntRolesProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("One system tenant for the whole Core (roles, Go-Freelancer, accounting)")
class SystemTenantConsistencyCheckTest {

    @Test
    @DisplayName("roles module and Go-Freelancer resolve the very same system tenant")
    void sameTenantEverywhere() {
        UUID system = SystemTenant.id();
        assertThat(new TntRolesProperties().getSystemTenantId()).isEqualTo(system);
        assertThat(TenantContextHolder.SYSTEM_TENANT).isEqualTo(system);
        assertThat(TenantContextHolder.systemTenant()).isEqualTo(system);
    }

    @Test
    @DisplayName("agreeing Spring property passes")
    void agreeingPasses() {
        SystemTenantConsistencyCheck.check(SystemTenant.id().toString(), SystemTenant.id());
    }

    @Test
    @DisplayName("a Spring property that differs from the JVM-wide tenant stops the start-up with a clear message")
    void disagreementFailsFast() {
        assertThatThrownBy(() -> SystemTenantConsistencyCheck.check(UUID.randomUUID().toString(), SystemTenant.id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Two different system tenants")
                .hasMessageContaining("TNT_SYSTEM_TENANT_ID");
        assertThatThrownBy(() -> SystemTenantConsistencyCheck.check("zzz", SystemTenant.id()))
                .isInstanceOf(IllegalStateException.class);
    }
}
