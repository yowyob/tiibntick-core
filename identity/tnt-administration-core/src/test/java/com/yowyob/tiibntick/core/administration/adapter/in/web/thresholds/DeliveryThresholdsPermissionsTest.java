package com.yowyob.tiibntick.core.administration.adapter.in.web.thresholds;

import com.yowyob.tiibntick.core.administration.domain.model.TntPermissionCatalog;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import com.yowyob.tiibntick.core.roles.domain.model.TntPermission;
import com.yowyob.tiibntick.core.roles.domain.model.TntRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Delivery thresholds — every route has an explicit permission, roles grant them as decided")
class DeliveryThresholdsPermissionsTest {

    private static List<Method> routes(Class<?> controller) {
        return Arrays.stream(controller.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(GetMapping.class) || m.isAnnotationPresent(PutMapping.class)
                        || m.isAnnotationPresent(DeleteMapping.class))
                .toList();
    }

    private static String permission(Method m) {
        RequirePermission p = m.getAnnotation(RequirePermission.class);
        return p == null ? null : p.resource() + ":" + p.action();
    }

    @Test
    @DisplayName("platform routes: tnt:platform:admin on each, never a bare isAuthenticated()")
    void platformRoutes() {
        List<Method> routes = routes(PlatformDeliveryThresholdsController.class);
        assertThat(routes).hasSize(3);
        for (Method m : routes) {
            assertThat(permission(m)).as(m.getName()).isEqualTo(TntPermission.TNT_PLATFORM_ADMIN);
            assertThat(m.getAnnotation(RequirePermission.class).required()).isTrue();
            assertThat(m.isAnnotationPresent(PreAuthorize.class)).isFalse();
        }
    }

    @Test
    @DisplayName("organization routes: write for get/put/delete of the override, read for the effective values")
    void organizationRoutes() {
        Map<String, String> byMethod = routes(OrganizationDeliveryThresholdsController.class).stream()
                .collect(Collectors.toMap(Method::getName, DeliveryThresholdsPermissionsTest::permission));
        assertThat(byMethod).containsExactlyInAnyOrderEntriesOf(Map.of(
                "get", TntPermission.ORGANIZATION_DELIVERY_SETTINGS_WRITE,
                "put", TntPermission.ORGANIZATION_DELIVERY_SETTINGS_WRITE,
                "delete", TntPermission.ORGANIZATION_DELIVERY_SETTINGS_WRITE,
                "effective", TntPermission.ORGANIZATION_DELIVERY_SETTINGS_READ));
    }

    @Test
    @DisplayName("write: the agency's managers (AGENCY_MANAGER, ORG_ADMIN) by default; read: every agency-scoped role")
    void roleGrants() {
        for (TntRole role : TntRole.values()) {
            boolean write = role.defaultPermissions().contains(TntPermission.ORGANIZATION_DELIVERY_SETTINGS_WRITE);
            assertThat(write).as(role.code())
                    .isEqualTo(role == TntRole.AGENCY_MANAGER || role == TntRole.ORG_ADMIN);
            assertThat(role.defaultPermissions()).as(role.code()).doesNotContain(TntPermission.TNT_PLATFORM_ADMIN);
        }
        for (TntRole role : List.of(TntRole.AGENCY_MANAGER, TntRole.BRANCH_MANAGER, TntRole.PERMANENT_DELIVERER,
                TntRole.RELAY_OPERATOR, TntRole.AGENCY_HUB_OPERATOR, TntRole.ORG_ADMIN)) {
            assertThat(role.defaultPermissions()).as(role.code())
                    .contains(TntPermission.ORGANIZATION_DELIVERY_SETTINGS_READ);
        }
        assertThat(TntRole.CLIENT.defaultPermissions()).doesNotContain(TntPermission.ORGANIZATION_DELIVERY_SETTINGS_READ);
        assertThat(TntRole.FREELANCER.defaultPermissions()).doesNotContain(TntPermission.ORGANIZATION_DELIVERY_SETTINGS_READ);
    }

    @Test
    @DisplayName("catalog: the organization permissions are assignable (granted by the agency's manager)")
    void catalog() {
        var catalog = TntPermissionCatalog.buildCatalog();
        assertThat(catalog.get(TntPermission.ORGANIZATION_DELIVERY_SETTINGS_WRITE).assignable()).isTrue();
        assertThat(catalog.get(TntPermission.ORGANIZATION_DELIVERY_SETTINGS_READ).assignable()).isTrue();
        assertThat(TntPermissionCatalog.TNT_PROTECTED).contains(TntPermission.TNT_PLATFORM_ADMIN);
    }
}
