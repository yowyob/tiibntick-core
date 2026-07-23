package com.yowyob.tiibntick.core.roles.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.roles.adapter.in.web.TntPermissionAspect;
import com.yowyob.tiibntick.core.roles.adapter.out.kernel.KernelRoleAssignmentAdapter;
import com.yowyob.tiibntick.core.roles.adapter.out.kernel.KernelRoleProvisioningAdapter;
import com.yowyob.tiibntick.core.roles.adapter.out.permission.CachingReactivePermissionResolverDecorator;
import com.yowyob.tiibntick.core.roles.adapter.out.permission.HybridReactivePermissionResolver;
import com.yowyob.tiibntick.core.roles.adapter.out.permission.LocalReactivePermissionResolver;
import com.yowyob.tiibntick.core.roles.adapter.out.permission.PermissionCache;
import com.yowyob.tiibntick.core.roles.adapter.out.permission.RemoteReactivePermissionResolver;
import com.yowyob.tiibntick.core.roles.adapter.out.persistence.InMemoryRoleRepository;
import com.yowyob.tiibntick.core.roles.adapter.out.persistence.InMemoryUserRoleAssignmentRepository;
import com.yowyob.tiibntick.core.roles.adapter.out.persistence.R2dbcRoleEntityRepository;
import com.yowyob.tiibntick.core.roles.adapter.out.persistence.R2dbcUserRoleAssignmentEntityRepository;
import com.yowyob.tiibntick.core.roles.adapter.out.persistence.RoleRepositoryAdapter;
import com.yowyob.tiibntick.core.roles.adapter.out.persistence.RoleSyncOutboxRepositoryAdapter;
import com.yowyob.tiibntick.core.roles.adapter.out.persistence.UserRoleAssignmentRepositoryAdapter;
import com.yowyob.tiibntick.core.roles.application.port.in.AssignTntRoleUseCase;
import com.yowyob.tiibntick.core.roles.application.port.in.CheckPermissionUseCase;
import com.yowyob.tiibntick.core.roles.application.port.in.ManageTntRoleUseCase;
import com.yowyob.tiibntick.core.roles.application.port.in.ResolveUserRolesUseCase;
import com.yowyob.tiibntick.core.roles.application.port.in.RevokeTntRoleUseCase;
import com.yowyob.tiibntick.core.roles.application.port.out.IPermissionChangeNotifier;
import com.yowyob.tiibntick.core.roles.application.port.out.ITntRoleAssignmentPort;
import com.yowyob.tiibntick.core.roles.application.port.out.ITntRoleProvisioningPort;
import com.yowyob.tiibntick.core.roles.application.port.out.ReactivePermissionResolver;
import com.yowyob.tiibntick.core.roles.application.port.out.RoleRepository;
import com.yowyob.tiibntick.core.roles.application.port.out.RoleSyncOutboxRepository;
import com.yowyob.tiibntick.core.roles.application.port.out.UserRoleAssignmentRepository;
import com.yowyob.tiibntick.core.roles.application.service.KernelRoleReconciliationJob;
import com.yowyob.tiibntick.core.roles.application.service.KernelRoleSyncWorker;
import com.yowyob.tiibntick.core.roles.application.service.TntPermissionEvaluator;
import com.yowyob.tiibntick.core.roles.application.service.TntRoleAssignmentService;
import com.yowyob.tiibntick.core.roles.application.service.TntRoleDefinitionRegistry;
import com.yowyob.tiibntick.core.roles.application.service.TntRoleInitializationService;
import com.yowyob.tiibntick.core.roles.application.service.TntRoleManagementService;
import com.yowyob.tiibntick.core.roles.application.service.TntRoleRevocationService;
import com.yowyob.tiibntick.core.roles.application.service.TntRoleService;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.reactive.function.client.WebClient;


/**
 * Spring Boot auto-configuration for tnt-roles-core.
 *
 * <p>Wires the TiiBnTick RBAC infrastructure: local R2DBC persistence is the source of
 * truth for TiiBnTick's own {@code @RequirePermission} checks (default {@code LOCAL}
 * permission-resolution mode), with an asynchronous, outbox-driven sync to the Kernel for
 * whatever must be visible ecosystem-wide (Chantier D · Audit n°6 · S5 — see
 * {@code docs/audits/remediation/rbac-s5-outbox-architecture.md}). All beans are
 * conditional — modules can override any bean if needed.
 *
 * <p>Bean registration order:
 * <ol>
 *   <li>{@link TntRoleDefinitionRegistry} — in-memory registry of TiiBnTick role definitions</li>
 *   <li>{@link RoleRepository}/{@link UserRoleAssignmentRepository}/{@link RoleSyncOutboxRepository}
 *       — R2DBC-backed by default (in-memory as a last-resort, non-persistent fallback)</li>
 *   <li>{@link KernelRoleProvisioningAdapter}/{@link KernelRoleAssignmentAdapter} — HTTP
 *       clients to the Kernel's role-controller, called only by the sync worker/reconciliation
 *       job below, never directly by the application services</li>
 *   <li>{@link TntRoleAssignmentService}/{@link TntRoleRevocationService}/
 *       {@link TntRoleManagementService} — local-first RBAC writes + outbox enqueueing</li>
 *   <li>{@link KernelRoleSyncWorker}/{@link KernelRoleReconciliationJob} — drain the outbox
 *       against the Kernel asynchronously</li>
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
@Import({TntRolesR2dbcConfig.class, TntRolesTransactionConfig.class})
public class TntRolesAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TntRoleDefinitionRegistry tntRoleDefinitionRegistry() {
        return new TntRoleDefinitionRegistry();
    }

    /**
     * R2DBC-backed {@link UserRoleAssignmentRepository} — the durable local source of truth
     * for RBAC assignments (Chantier D · Audit n°6 · S5). Registered ahead of the in-memory
     * fallback below so it wins the {@code @ConditionalOnMissingBean} race whenever the
     * R2DBC infrastructure ({@link TntRolesR2dbcConfig}) is on the classpath, which it always
     * is in this module — the in-memory fallback only ever activates if another module
     * explicitly overrides this bean.
     */
    @Bean
    @ConditionalOnMissingBean(UserRoleAssignmentRepository.class)
    public UserRoleAssignmentRepository userRoleAssignmentRepository(R2dbcUserRoleAssignmentEntityRepository repository) {
        return new UserRoleAssignmentRepositoryAdapter(repository);
    }

    /**
     * R2DBC-backed {@link RoleRepository} — see {@link #userRoleAssignmentRepository}.
     */
    @Bean
    @ConditionalOnMissingBean(RoleRepository.class)
    public RoleRepository roleRepository(R2dbcRoleEntityRepository repository) {
        return new RoleRepositoryAdapter(repository);
    }

    /**
     * R2DBC-backed {@link RoleSyncOutboxRepository} — queues local RBAC writes for
     * asynchronous replay against the Kernel by {@link KernelRoleSyncWorker}.
     */
    @Bean
    @ConditionalOnMissingBean(RoleSyncOutboxRepository.class)
    public RoleSyncOutboxRepository roleSyncOutboxRepository(
            org.springframework.r2dbc.core.DatabaseClient databaseClient,
            org.springframework.data.r2dbc.core.R2dbcEntityTemplate r2dbcEntityTemplate) {
        return new RoleSyncOutboxRepositoryAdapter(databaseClient, r2dbcEntityTemplate);
    }

    /**
     * Last-resort, process-lifetime fallback for {@link UserRoleAssignmentRepository} — only
     * activates if nothing (including {@link #userRoleAssignmentRepository}) provides the
     * port, e.g. a lightweight test slice that excludes {@link TntRolesR2dbcConfig}.
     */
    @Bean
    @ConditionalOnMissingBean(UserRoleAssignmentRepository.class)
    public InMemoryUserRoleAssignmentRepository inMemoryUserRoleAssignmentRepository() {
        return new InMemoryUserRoleAssignmentRepository();
    }

    /**
     * Last-resort fallback for {@link RoleRepository} — see
     * {@link #inMemoryUserRoleAssignmentRepository()}.
     */
    @Bean
    @ConditionalOnMissingBean(RoleRepository.class)
    public InMemoryRoleRepository inMemoryRoleRepository() {
        return new InMemoryRoleRepository();
    }

    /**
     * Fail-fast guard (Chantier D · Audit n°6 · S5): refuses to start under the {@code prod}
     * profile if either RBAC repository above ended up being the in-memory, process-lifetime
     * fallback — i.e. no persistent adapter was supplied. Booting anyway would silently run
     * production with role assignments that vanish on restart and diverge across every
     * instance in a multi-instance deployment (each holds its own independent
     * {@code ConcurrentHashMap}), which is exactly the failure mode this chantier closes.
     *
     * <p>Dev/test/staging are unaffected — {@code tnt.auth.allow-anonymous-context}-style
     * profiles are expected to run without a persistent RBAC store.
     */
    @Bean
    public InitializingBean rbacPersistentAdapterProdGuard(
            Environment environment,
            UserRoleAssignmentRepository userRoleAssignmentRepository,
            RoleRepository roleRepository) {
        return () -> {
            if (!environment.acceptsProfiles(org.springframework.core.env.Profiles.of("prod"))) {
                return;
            }
            java.util.List<String> inMemoryPortsStillActive = new java.util.ArrayList<>();
            if (userRoleAssignmentRepository instanceof InMemoryUserRoleAssignmentRepository) {
                inMemoryPortsStillActive.add("UserRoleAssignmentRepository");
            }
            if (roleRepository instanceof InMemoryRoleRepository) {
                inMemoryPortsStillActive.add("RoleRepository");
            }
            if (!inMemoryPortsStillActive.isEmpty()) {
                throw new IllegalStateException(
                        "RBAC fail-fast (Chantier D · Audit n°6 · S5): profile 'prod' is active but no "
                        + "persistent adapter was supplied for: " + inMemoryPortsStillActive
                        + " — the in-memory, process-lifetime fallback is still active. Role assignments "
                        + "would vanish on every restart and diverge across every instance in a "
                        + "multi-instance deployment. Provide a persistent RoleRepository/"
                        + "UserRoleAssignmentRepository bean before deploying to prod.");
            }
        };
    }

    /**
     * Provisions TiiBnTick roles into the Kernel via HTTP. Called only by
     * {@link KernelRoleSyncWorker}/{@link KernelRoleReconciliationJob} now — application
     * services write locally and enqueue an outbox entry instead of calling this directly.
     * Uses the shared {@code kernelWebClient} bean — no Kernel Spring beans injected here.
     */
    @Bean
    @ConditionalOnMissingBean(ITntRoleProvisioningPort.class)
    public KernelRoleProvisioningAdapter kernelRoleProvisioningAdapter(
            @Qualifier("kernelWebClient") WebClient kernelWebClient) {
        return new KernelRoleProvisioningAdapter(kernelWebClient);
    }

    /**
     * Assigns/revokes TiiBnTick canonical roles against the Kernel on behalf of the sync
     * worker/reconciliation job. Uses {@code kernelTpWebClient} so the request is
     * authenticated as the calling admin (bearer forwarding) captured at enqueue time —
     * see {@code KernelRoleAssignmentAdapter}.
     */
    @Bean
    @ConditionalOnMissingBean(ITntRoleAssignmentPort.class)
    public KernelRoleAssignmentAdapter kernelRoleAssignmentAdapter(
            @Qualifier("kernelTpWebClient") WebClient kernelTpWebClient,
            ITntRoleProvisioningPort provisioningPort,
            TntRolesProperties properties) {
        return new KernelRoleAssignmentAdapter(kernelTpWebClient, provisioningPort, properties.getSystemTenantId());
    }

    @Bean
    @ConditionalOnMissingBean(AssignTntRoleUseCase.class)
    public TntRoleAssignmentService tntRoleAssignmentService(
            RoleRepository roleRepository,
            UserRoleAssignmentRepository assignmentRepository,
            RoleSyncOutboxRepository outboxRepository,
            TransactionalOperator transactionalOperator,
            ObjectMapper objectMapper,
            TntRolesProperties properties,
            IPermissionChangeNotifier permissionChangeNotifier) {
        return new TntRoleAssignmentService(roleRepository, assignmentRepository, outboxRepository,
                transactionalOperator, objectMapper, properties.getSystemTenantId(), permissionChangeNotifier);
    }

    @Bean
    @ConditionalOnMissingBean(RevokeTntRoleUseCase.class)
    public TntRoleRevocationService tntRoleRevocationService(
            UserRoleAssignmentRepository assignmentRepository,
            RoleSyncOutboxRepository outboxRepository,
            TransactionalOperator transactionalOperator,
            ObjectMapper objectMapper,
            IPermissionChangeNotifier permissionChangeNotifier) {
        return new TntRoleRevocationService(assignmentRepository, outboxRepository, transactionalOperator,
                objectMapper, permissionChangeNotifier);
    }

    @Bean
    @ConditionalOnMissingBean(ManageTntRoleUseCase.class)
    public TntRoleManagementService tntRoleManagementService(
            RoleRepository roleRepository,
            RoleSyncOutboxRepository outboxRepository,
            TransactionalOperator transactionalOperator,
            ObjectMapper objectMapper) {
        return new TntRoleManagementService(roleRepository, outboxRepository, transactionalOperator, objectMapper);
    }

    /**
     * Drains {@code tnt_role_sync_outbox} and replays each entry against the Kernel
     * (Chantier D · Audit n°6 · S5). ShedLock-guarded internally — genuinely runs across
     * every instance in a multi-instance deployment, unlike {@code yow-event-kernel}'s
     * poller which only started mattering once Chantier C · P1 reactivated it.
     */
    @Bean
    @ConditionalOnMissingBean
    public KernelRoleSyncWorker kernelRoleSyncWorker(
            RoleSyncOutboxRepository outboxRepository,
            RoleRepository roleRepository,
            UserRoleAssignmentRepository assignmentRepository,
            ITntRoleProvisioningPort provisioningPort,
            ITntRoleAssignmentPort assignmentPort,
            ObjectMapper objectMapper,
            @Value("${tnt.roles.outbox.batch-size:50}") int batchSize) {
        return new KernelRoleSyncWorker(outboxRepository, roleRepository, assignmentRepository,
                provisioningPort, assignmentPort, objectMapper, batchSize);
    }

    /**
     * Hourly spot-check that TiiBnTick's own already-synced roles/assignments are still
     * present Kernel-side — never enumerates the Kernel's full role list (see the job's own
     * Javadoc for the scope constraint).
     */
    @Bean
    @ConditionalOnMissingBean
    public KernelRoleReconciliationJob kernelRoleReconciliationJob(
            RoleSyncOutboxRepository outboxRepository,
            RoleRepository roleRepository,
            UserRoleAssignmentRepository assignmentRepository,
            ITntRoleProvisioningPort provisioningPort,
            ITntRoleAssignmentPort assignmentPort,
            ObjectMapper objectMapper) {
        return new KernelRoleReconciliationJob(outboxRepository, roleRepository, assignmentRepository,
                provisioningPort, assignmentPort, objectMapper);
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
     * (default LOCAL) and wraps it with {@link PermissionCache}. LOCAL — resolved from the
     * now R2DBC-backed {@link UserRoleAssignmentRepository} + {@link RoleRepository} +
     * {@link TntRoleDefinitionRegistry} — is TiiBnTick's own authoritative, Kernel-independent
     * path for every {@code @RequirePermission} check; REMOTE/HYBRID remain available for
     * other Yowyob backends that need TiiBnTick's Kernel-visible view instead.
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

    /**
     * Seeds the 9 canonical {@code TntRole} definitions into the local schema at startup
     * (upsert, idempotent) and enqueues a {@code PROVISION_ROLE} outbox entry for any that
     * didn't already exist locally — no direct, synchronous Kernel call anymore.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "tnt.roles", name = "provision-on-startup", havingValue = "true", matchIfMissing = true)
    public TntRoleInitializationService tntRoleInitializationService(
            TntRoleDefinitionRegistry registry,
            RoleRepository roleRepository,
            RoleSyncOutboxRepository outboxRepository,
            TransactionalOperator transactionalOperator,
            ObjectMapper objectMapper,
            TntRolesProperties properties) {
        return new TntRoleInitializationService(registry, roleRepository, outboxRepository,
                transactionalOperator, objectMapper, properties.getSystemTenantId());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "tnt.roles", name = "aop-enabled", havingValue = "true", matchIfMissing = true)
    public TntPermissionAspect tntPermissionAspect(TntPermissionEvaluator permissionEvaluator) {
        return new TntPermissionAspect(permissionEvaluator);
    }
}
