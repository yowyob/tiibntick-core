package com.yowyob.tiibntick.common.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SystemTenant — the single TiiBnTick system tenant, never hard-coded")
class SystemTenantTest {

    private static final UUID CONFIGURED = UUID.fromString("b8b17c6e-9bbd-4b4b-8d54-b162cabeea64");

    private static UUID resolve(Map<String, String> props, Map<String, String> env) {
        return SystemTenant.resolve(props::get, env::get);
    }

    @Test
    @DisplayName("with nothing configured there is NO default: it fails with an explicit message")
    void noDefault() {
        assertThatThrownBy(() -> resolve(Map.of(), Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TNT_SYSTEM_TENANT_ID")
                .hasMessageContaining("no default");
        assertThatThrownBy(() -> resolve(Map.of("tnt.roles.system-tenant-id", "  "), Map.of("TNT_SYSTEM_TENANT_ID", "")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("TNT_SYSTEM_TENANT_ID is the source of truth")
    void environment() {
        assertThat(resolve(Map.of(), Map.of("TNT_SYSTEM_TENANT_ID", CONFIGURED.toString()))).isEqualTo(CONFIGURED);
    }

    @Test
    @DisplayName("the JVM property wins over the environment; surrounding blanks are trimmed")
    void propertyWins() {
        UUID other = UUID.randomUUID();
        assertThat(resolve(Map.of("tnt.roles.system-tenant-id", other.toString()),
                Map.of("TNT_SYSTEM_TENANT_ID", CONFIGURED.toString()))).isEqualTo(other);
        assertThat(resolve(Map.of(), Map.of("TNT_SYSTEM_TENANT_ID", " " + CONFIGURED + " "))).isEqualTo(CONFIGURED);
    }

    @Test
    @DisplayName("a value that is not a UUID fails loudly")
    void invalidValue() {
        assertThatThrownBy(() -> resolve(Map.of(), Map.of("TNT_SYSTEM_TENANT_ID", "not-a-uuid")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("TNT_SYSTEM_TENANT_ID");
    }

    @Test
    @DisplayName("the system actor is derived from the system tenant: stable, and different from it")
    void systemActorIsDerived() {
        assertThat(SystemTenant.systemActorId()).isEqualTo(SystemTenant.systemActorId())
                .isNotEqualTo(SystemTenant.id());
    }
}
