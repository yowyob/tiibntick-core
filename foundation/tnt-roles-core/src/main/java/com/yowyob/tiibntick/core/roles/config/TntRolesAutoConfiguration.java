package com.yowyob.tiibntick.core.roles.config;

import com.yowyob.tiibntick.core.roles.adapter.in.web.TntPermissionAspect;
import com.yowyob.tiibntick.core.roles.adapter.out.kernel.KernelRoleProvisioningAdapter;
import com.yowyob.tiibntick.core.roles.adapter.out.permission.CachingReactivePermissionResolverDecorator;
import com.yowyob.tiibntick.core.roles.adapter.out.permission.HybridReactivePermissionResolver;
import com.yowyob.tiibntick.core.roles.adapter.out.permission.LocalReactivePermissionResolver;
import com.yowyob.tiibntick.core.roles.adapter.out.permission.PermissionCache;
import com.yowyob.tiibntick.core.roles.adapter.out.permission.RemoteReactivePermissionResolver;
import com.yowyob.tiibntick.core.roles.application.port.in.CheckPermissionUseCase;
import com.yowyob.tiibntick.core.roles.application.port.in.ResolveUserRolesUseCase;
import com.yowyob.tiibntick.core.roles.adapter.out.persistence.InMemoryRoleRepository;
import com.yowyob.tiibntick.core.roles.adapter.out.persistence.InMemoryUserRoleAssignmentRepository;
import com.yowyob.tiibntick.core.roles.application.port.out.ITntRoleProvisioningPort;
import com.yowyob.tiibntick.core.roles.application.port.out.ReactivePermissionResolver;
import com.yowyob.tiibntick.core.roles.application.port.out.RoleRepository;
import com.yowyob.tiibntick.core.roles.application.port.out.UserRoleAssignmentRepository;
import com.yowyob.tiibntick.core.roles.application.service.TntPermissionEvaluator;
import com.yowyob.tiibntick.core.roles.application.service.TntRoleDefinitionRegistry;
import com.yowyob.tiibntick.core.roles.application.service.TntRoleInitializationService;
import com.yowyob.tiibntick.core.roles.application.service.TntRoleService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.beans.factory.annotation.Qualifier;


/**
 * Spring Boot auto-configuration for tnt-roles-core.
 *
 * <p>Wires the TiiBnTick RBAC infrastructure (local by default, with an HTTP-backed
 * REMOTE/HYBRID strategy calling the Kernel — see {@code RemoteReactivePermissionResolver}).
 * All beans are conditional — modules can override any bean if needed.
 *
 * <p>Bean registration order:
 * <ol>
 *   <li>{@link TntRoleDefinitionRegistry} — in-memory registry of TiiBnTick role definitions</li>
 *   <li>{@link KernelRoleProvisioningAdapter} — bridge to Kernel's CreateRoleUseCase</li>
 *   <li>{@link TntPermissionEvaluator} — DSL implementing {@link CheckPermissionUseCase}</li>
 *   <li>{@link TntRoleService} — implements {@link ResolveUserRolesUseCase}</li>
 *   <li>{@link TntRoleInitializationService} — startup provisioning (conditional)</li>
 *   <li>{@link TntPermissionAspect} — AOP enforcement (conditional)</li>
 * </ol>
 *
 * @author MANFOUO Braun
 */
@AutoConfiguration
@EnableConfigurationProperties(TntRolesProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class TntRolesAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TntRoleDefinitionRegistry tntRoleDefinitionRegistry() {
        return new TntRoleDefinitionRegistry();
    }

    /**
     * Fallback for {@link UserRoleAssignmentRepository} — an in-memory, process-lifetime
     * implementation. Real deployments should provide a persistent adapter (R2DBC or an
     * HTTP adapter to the Kernel); this only activates when nothing else provides the port.
     */
    @Bean
    @ConditionalOnMissingBean(UserRoleAssignmentRepository.class)
    public UserRoleAssignmentRepository userRoleAssignmentRepository() {
        return new InMemoryUserRoleAssignmentRepository();
    }

    /**
     * Fallback for {@link RoleRepository} — see {@link #userRoleAssignmentRepository()}.
     */
    @Bean
    @ConditionalOnMissingBean(RoleRepository.class)
    public RoleRepository roleRepository() {
        return new InMemoryRoleRepository();
    }

    /**
     * Provisions TiiBnTick roles into the Kernel via HTTP.
     * Uses the shared {@code kernelWebClient} bean — no Kernel Spring beans injected here.
     */
    @Bean
    @ConditionalOnMissingBean(ITntRoleProvisioningPort.class)
    public KernelRoleProvisioningAdapter kernelRoleProvisioningAdapter(
            @Qualifier("kernelWebClient") WebClient kernelWebClient) {
        return new KernelRoleProvisioningAdapter(kernelWebClient);
    }

    /**
     * L1 in-process cache fronting whichever resolver strategy {@code tnt.roles.permission.mode}
     * selects below. Shared with {@link com.yowyob.tiibntick.core.roles.adapter.in.kafka.PermissionCacheInvalidationListener}
     * so role/permission-change events can evict it.
     */
    @Bean
    @ConditionalOnMissingBean
    public PermissionCache permissionCache(TntRolesProperties properties) {
        return new PermissionCache(properties.getPermissionCacheTtlSeconds());
    }

    /**
     * Selects the LOCAL / REMOTE / HYBRID strategy per {@code tnt.roles.permission.mode}
     * (default LOCAL) and wraps it with {@link PermissionCache}. The Kernel exposed no
     * permission-resolution REST endpoint as of the last {@code docs/kernel-api/} refresh,
     * so LOCAL — resolved from
     * {@link UserRoleAssignmentRepository} + {@link RoleRepository} + {@link TntRoleDefinitionRegistry} —
     * is the only strategy with real data behind it today; REMOTE/HYBRID are forward-compatible
     * and become fully functional the moment that endpoint ships, with no change to
     * {@code @RequirePermission} call sites.
     */
    @Bean
    @ConditionalOnMissingBean(ReactivePermissionResolver.class)
    public ReactivePermissionResolver reactivePermissionResolver(
            TntRolesProperties properties,
            PermissionCache permissionCache,
            UserRoleAssignmentRepository assignmentRepository,
            RoleRepository roleRepository,
            TntRoleDefinitionRegistry registry,
            @Qualifier("kernelWebClient") WebClient kernelWebClient) {

        LocalReactivePermissionResolver local =
                new LocalReactivePermissionResolver(assignmentRepository, roleRepository, registry);
        RemoteReactivePermissionResolver remote = new RemoteReactivePermissionResolver(kernelWebClient);

        ReactivePermissionResolver delegate = switch (properties.getPermission().getMode()) {
            case LOCAL -> local;
            case REMOTE -> remote;
            case HYBRID -> new HybridReactivePermissionResolver(local, remote);
        };

        return new CachingReactivePermissionResolverDecorator(delegate, permissionCache);
    }

    @Bean
    @ConditionalOnMissingBean({CheckPermissionUseCase.class, TntPermissionEvaluator.class})
    @ConditionalOnProperty(prefix = "tnt.roles", name = "aop-enabled", havingValue = "true", matchIfMissing = true)
    public TntPermissionEvaluator tntPermissionEvaluator(
            ReactivePermissionResolver reactivePermissionResolver,
            TntRoleDefinitionRegistry registry) {
        return new TntPermissionEvaluator(reactivePermissionResolver, registry);
    }

    @Bean
    @ConditionalOnMissingBean(ResolveUserRolesUseCase.class)
    public TntRoleService tntRoleService(
            UserRoleAssignmentRepository assignmentRepository,
            RoleRepository roleRepository,
            TntRoleDefinitionRegistry registry) {
        return new TntRoleService(assignmentRepository, roleRepository, registry);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "tnt.roles", name = "provision-on-startup", havingValue = "true", matchIfMissing = true)
    public TntRoleInitializationService tntRoleInitializationService(
            TntRoleDefinitionRegistry registry,
            ITntRoleProvisioningPort provisioningPort,
            TntRolesProperties properties) {
        return new TntRoleInitializationService(registry, provisioningPort, properties.getSystemTenantId());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "tnt.roles", name = "aop-enabled", havingValue = "true", matchIfMissing = true)
    public TntPermissionAspect tntPermissionAspect(TntPermissionEvaluator permissionEvaluator) {
        return new TntPermissionAspect(permissionEvaluator);
    }
}
