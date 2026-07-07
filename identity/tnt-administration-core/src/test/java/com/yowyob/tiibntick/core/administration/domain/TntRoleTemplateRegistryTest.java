package com.yowyob.tiibntick.core.administration.domain;

import com.yowyob.tiibntick.core.administration.domain.service.TntRoleTemplateRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for TntRoleTemplateRegistry, verifying that all templates carry a null
 * kernelRoleId in the static catalog and can be linked post-provisioning.
 *
 * @author MANFOUO Braun
 */
class TntRoleTemplateRegistryTest {

    private final TntRoleTemplateRegistry registry = new TntRoleTemplateRegistry();

    @Test
    void all_static_templates_should_have_null_kernel_role_id() {
        List<TntRoleTemplateRegistry.TntRoleTemplate> templates = registry.getTemplates();
        assertThat(templates).isNotEmpty();
        templates.forEach(t -> assertThat(t.kernelRoleId())
                .as("Template %s should have null kernelRoleId in static catalog", t.code())
                .isNull());
    }

    @Test
    void should_have_expected_protected_templates() {
        List<TntRoleTemplateRegistry.TntRoleTemplate> templates = registry.getTemplates();
        long protectedCount = templates.stream().filter(TntRoleTemplateRegistry.TntRoleTemplate::protectedTemplate).count();
        assertThat(protectedCount).isGreaterThanOrEqualTo(2);

        TntRoleTemplateRegistry.TntRoleTemplate superAdmin = templates.stream()
                .filter(t -> "TNT_SUPER_ADMIN".equals(t.code()))
                .findFirst()
                .orElseThrow();
        assertThat(superAdmin.protectedTemplate()).isTrue();
        assertThat(superAdmin.scopeType()).isEqualTo("TENANT");
    }

    @Test
    void should_link_kernel_role_id_via_withKernelRoleId() {
        TntRoleTemplateRegistry.TntRoleTemplate template = registry.getTemplates().getFirst();
        UUID kernelId = UUID.randomUUID();

        TntRoleTemplateRegistry.TntRoleTemplate linked = template.withKernelRoleId(kernelId);

        assertThat(linked.kernelRoleId()).isEqualTo(kernelId);
        // Original remains null (record is immutable)
        assertThat(template.kernelRoleId()).isNull();
        assertThat(linked.code()).isEqualTo(template.code());
    }

    @Test
    void should_have_freelancer_templates_with_correct_scope() {
        TntRoleTemplateRegistry.TntRoleTemplate freelancer = registry.getTemplates().stream()
                .filter(t -> "TNT_FREELANCER".equals(t.code()))
                .findFirst()
                .orElseThrow();

        assertThat(freelancer.scopeType()).isEqualTo("AGENCY");
        assertThat(freelancer.permissions()).contains("freelancer:mission:take");
    }
}
