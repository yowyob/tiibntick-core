package com.yowyob.tiibntick.core.administration.application;

import com.yowyob.tiibntick.core.administration.application.port.out.*;
import com.yowyob.tiibntick.core.administration.application.service.TntAdministrationApplicationService;
import com.yowyob.tiibntick.core.administration.domain.model.TntRoleDefinition;
import com.yowyob.tiibntick.core.administration.domain.service.TntRoleTemplateRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TntAdministrationApplicationService using Mockito stubs.
 *
 * @author MANFOUO Braun
 */
class TntAdministrationApplicationServiceTest {

    private TntPlatformOptionsRepository   optionsRepository;
    private TntAdministrationEventPublisher eventPublisher;
    private TntRoleTemplateRegistry         roleTemplateRegistry;
    private TntRoleDefinitionRepository     roleDefinitionRepository;
    private KernelRolePort                  kernelRolePort;
    private KernelPermissionPort            kernelPermissionPort;

    private TntAdministrationApplicationService service;

    private static final int EXPECTED_PERMISSION_COUNT = 90;

    @BeforeEach
    void setUp() {
        optionsRepository       = mock(TntPlatformOptionsRepository.class);
        eventPublisher          = mock(TntAdministrationEventPublisher.class);
        roleTemplateRegistry    = mock(TntRoleTemplateRegistry.class);
        roleDefinitionRepository= mock(TntRoleDefinitionRepository.class);
        kernelRolePort          = mock(KernelRolePort.class);
        kernelPermissionPort    = mock(KernelPermissionPort.class);

        service = new TntAdministrationApplicationService(
                optionsRepository, eventPublisher, roleTemplateRegistry,
                roleDefinitionRepository, kernelRolePort, kernelPermissionPort);
    }

    @Test
    void listTntPermissions_should_return_full_catalog() {
        StepVerifier.create(service.listTntPermissions())
                .expectNextCount(EXPECTED_PERMISSION_COUNT) // All TNT permissions
                .verifyComplete();
    }

    @Test
    void listByModule_should_filter_by_module() {
        StepVerifier.create(service.listByModule("DELIVERY"))
                .recordWith(java.util.ArrayList::new)
                .thenConsumeWhile(p -> true)
                .consumeRecordedWith(list -> {
                    assert list.stream().allMatch(p -> "DELIVERY".equals(p.module()));
                    assert !list.isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void listByModule_null_should_return_all_permissions() {
        StepVerifier.create(service.listByModule(null))
                .expectNextCount(EXPECTED_PERMISSION_COUNT)
                .verifyComplete();
    }

    @Test
    void isProtectedPermission_should_return_true_for_system_permissions() {
        assert service.isProtectedPermission("tnt:platform:admin");
        assert service.isProtectedPermission("tnt:blockchain:mine");
        assert !service.isProtectedPermission("delivery:read");
        assert !service.isProtectedPermission("unknown:code");
    }

    @Test
    void provisionForTenant_should_skip_already_provisioned_templates() {
        UUID tenantId = UUID.randomUUID();
        UUID orgId    = UUID.randomUUID();
        UUID userId   = UUID.randomUUID();

        TntRoleTemplateRegistry.TntRoleTemplate template =
                new TntRoleTemplateRegistry.TntRoleTemplate(
                        "TNT_CLIENT", "Client", "AGENCY",
                        Set.of("delivery:track"), false);

        when(roleTemplateRegistry.getTemplates()).thenReturn(List.of(template));
        when(roleDefinitionRepository.existsByTenantIdAndTemplateCode(tenantId, "TNT_CLIENT"))
                .thenReturn(Mono.just(true));
        when(eventPublisher.publish(any(), any(), any(), any(), any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.provisionForTenant(tenantId, orgId, userId))
                .verifyComplete();

        // No save should have been called since template is already provisioned
        verify(roleDefinitionRepository, never()).save(any());
    }

    @Test
    void provisionForTenant_should_create_definition_for_new_template() {
        UUID tenantId = UUID.randomUUID();
        UUID orgId    = UUID.randomUUID();
        UUID userId   = UUID.randomUUID();

        TntRoleTemplateRegistry.TntRoleTemplate template =
                new TntRoleTemplateRegistry.TntRoleTemplate(
                        "TNT_DISPATCHER", "Dispatcher", "AGENCY",
                        Set.of("delivery:dispatch"), false);

        TntRoleDefinition savedDef = TntRoleDefinition.provision(
                tenantId, "TNT_DISPATCHER", "Dispatcher", "AGENCY",
                Set.of("delivery:dispatch"), false);

        when(roleTemplateRegistry.getTemplates()).thenReturn(List.of(template));
        when(roleDefinitionRepository.existsByTenantIdAndTemplateCode(tenantId, "TNT_DISPATCHER"))
                .thenReturn(Mono.just(false));
        // Kernel does not already have this role
        when(kernelRolePort.findByCodeAndTenant("TNT_DISPATCHER", tenantId))
                .thenReturn(Mono.empty());
        when(roleDefinitionRepository.save(any())).thenReturn(Mono.just(savedDef));
        when(eventPublisher.publish(any(), any(), any(), any(), any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.provisionForTenant(tenantId, orgId, userId))
                .verifyComplete();

        verify(roleDefinitionRepository, times(1)).save(any());
    }

    @Test
    void resolveKernelPermissionId_should_return_entry_unchanged_for_system_permissions() {
        StepVerifier.create(service.resolveKernelPermissionId("tnt:blockchain:mine"))
                .assertNext(entry -> {
                    assert entry.system();
                    assert entry.kernelPermissionId() == null;
                })
                .verifyComplete();
        // Kernel port should NOT be called for system permissions
        verifyNoInteractions(kernelPermissionPort);
    }
}
