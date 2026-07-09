package com.yowyob.tiibntick.core.platformgateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.platformgateway.adapter.in.web.PlatformScopeAspect;
import com.yowyob.tiibntick.core.platformgateway.adapter.out.kernel.KernelAuthGatewayAdapter;
import com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence.ApiKeyR2dbcRepository;
import com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence.ApiKeyRepositoryAdapter;
import com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence.ApiKeyRotationHistoryR2dbcRepository;
import com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence.ApiKeyRotationRepositoryAdapter;
import com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence.ClientAuditLogR2dbcRepository;
import com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence.ClientAuditLogRepositoryAdapter;
import com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence.ClientPermissionR2dbcRepository;
import com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence.ClientPermissionRepositoryAdapter;
import com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence.PlatformClientR2dbcRepository;
import com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence.PlatformClientRepositoryAdapter;
import com.yowyob.tiibntick.core.platformgateway.application.port.in.PlatformClientAdminUseCase;
import com.yowyob.tiibntick.core.platformgateway.application.port.in.ProxyKernelAuthUseCase;
import com.yowyob.tiibntick.core.platformgateway.application.port.in.ProxyKernelSsoUseCase;
import com.yowyob.tiibntick.core.platformgateway.application.port.out.IApiKeyRepository;
import com.yowyob.tiibntick.core.platformgateway.application.port.out.IApiKeyRotationRepository;
import com.yowyob.tiibntick.core.platformgateway.application.port.out.IClientAuditLogRepository;
import com.yowyob.tiibntick.core.platformgateway.application.port.out.IClientPermissionRepository;
import com.yowyob.tiibntick.core.platformgateway.application.port.out.IKernelAuthGatewayPort;
import com.yowyob.tiibntick.core.platformgateway.application.port.out.IPlatformClientRepository;
import com.yowyob.tiibntick.core.platformgateway.application.service.ApiKeyHashingService;
import com.yowyob.tiibntick.core.platformgateway.application.service.KernelAuthGatewayService;
import com.yowyob.tiibntick.core.platformgateway.application.service.KernelSsoGatewayService;
import com.yowyob.tiibntick.core.platformgateway.application.service.PlatformClientAdminService;
import com.yowyob.tiibntick.core.platformgateway.application.service.PlatformClientAuditRecorder;
import com.yowyob.tiibntick.core.platformgateway.application.service.PlatformClientAuthenticationService;
import com.yowyob.tiibntick.core.platformgateway.application.service.PlatformScopeRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Spring Boot auto-configuration for tnt-platform-gateway-core — wires the persistence
 * adapters, the DB-backed authentication service, the admin use-case, the scope AOP
 * aspect, and the Bloc A/B Kernel-proxy services (moved here from tnt-auth-core, see
 * {@code docs/auth/platform-client-management-design.md} §2.0).
 *
 * @author MANFOUO Braun
 */
@AutoConfiguration
@EnableConfigurationProperties(TntPlatformGatewayProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@Import(TntPlatformGatewayR2dbcConfig.class)
public class TntPlatformGatewayAutoConfiguration {

    /** Dedicated ObjectMapper — deliberately not tnt-auth-core's {@code tntAuthObjectMapper}, keeping the two modules decoupled (§2.0). */
    @Bean("platformGatewayObjectMapper")
    @ConditionalOnMissingBean(name = "platformGatewayObjectMapper")
    public ObjectMapper platformGatewayObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    @ConditionalOnMissingBean(ApiKeyHashingService.class)
    public ApiKeyHashingService apiKeyHashingService() {
        return new ApiKeyHashingService();
    }

    @Bean
    @ConditionalOnMissingBean(PlatformScopeRegistry.class)
    public PlatformScopeRegistry platformScopeRegistry() {
        return new PlatformScopeRegistry();
    }

    // ── Persistence adapters ─────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(IPlatformClientRepository.class)
    public PlatformClientRepositoryAdapter platformClientRepositoryAdapter(
            PlatformClientR2dbcRepository repository, R2dbcEntityTemplate template) {
        return new PlatformClientRepositoryAdapter(repository, template);
    }

    @Bean
    @ConditionalOnMissingBean(IApiKeyRepository.class)
    public ApiKeyRepositoryAdapter apiKeyRepositoryAdapter(ApiKeyR2dbcRepository repository) {
        return new ApiKeyRepositoryAdapter(repository);
    }

    @Bean
    @ConditionalOnMissingBean(IClientPermissionRepository.class)
    public ClientPermissionRepositoryAdapter clientPermissionRepositoryAdapter(ClientPermissionR2dbcRepository repository) {
        return new ClientPermissionRepositoryAdapter(repository);
    }

    @Bean
    @ConditionalOnMissingBean(IApiKeyRotationRepository.class)
    public ApiKeyRotationRepositoryAdapter apiKeyRotationRepositoryAdapter(ApiKeyRotationHistoryR2dbcRepository repository) {
        return new ApiKeyRotationRepositoryAdapter(repository);
    }

    @Bean
    @ConditionalOnMissingBean(IClientAuditLogRepository.class)
    public ClientAuditLogRepositoryAdapter clientAuditLogRepositoryAdapter(
            ClientAuditLogR2dbcRepository repository, R2dbcEntityTemplate template) {
        return new ClientAuditLogRepositoryAdapter(repository, template);
    }

    // ── Authentication / audit / admin ───────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(PlatformClientAuthenticationService.class)
    public PlatformClientAuthenticationService platformClientAuthenticationService(
            IPlatformClientRepository clientRepository,
            IApiKeyRepository apiKeyRepository,
            IClientPermissionRepository permissionRepository,
            ApiKeyHashingService hashingService,
            TntPlatformGatewayProperties properties) {
        return new PlatformClientAuthenticationService(
                clientRepository, apiKeyRepository, permissionRepository, hashingService, properties);
    }

    @Bean
    @ConditionalOnMissingBean(PlatformClientAuditRecorder.class)
    public PlatformClientAuditRecorder platformClientAuditRecorder(IClientAuditLogRepository auditLogRepository) {
        return new PlatformClientAuditRecorder(auditLogRepository);
    }

    @Bean
    @ConditionalOnMissingBean(PlatformClientAdminUseCase.class)
    public PlatformClientAdminService platformClientAdminService(
            IPlatformClientRepository clientRepository,
            IApiKeyRepository apiKeyRepository,
            IClientPermissionRepository permissionRepository,
            IApiKeyRotationRepository rotationRepository,
            IClientAuditLogRepository auditLogRepository,
            ApiKeyHashingService hashingService,
            PlatformScopeRegistry scopeRegistry,
            PlatformClientAuthenticationService authenticationService) {
        return new PlatformClientAdminService(
                clientRepository, apiKeyRepository, permissionRepository, rotationRepository, auditLogRepository,
                hashingService, scopeRegistry, authenticationService);
    }

    @Bean
    @ConditionalOnMissingBean(PlatformScopeAspect.class)
    public PlatformScopeAspect platformScopeAspect() {
        return new PlatformScopeAspect();
    }

    // ── Bloc A/B — Kernel auth/SSO proxy (moved from tnt-auth-core, §2.0) ─────

    @Bean
    @ConditionalOnMissingBean(IKernelAuthGatewayPort.class)
    public KernelAuthGatewayAdapter kernelAuthGatewayAdapter(@Qualifier("kernelWebClient") WebClient kernelWebClient) {
        return new KernelAuthGatewayAdapter(kernelWebClient);
    }

    @Bean
    @ConditionalOnMissingBean(ProxyKernelAuthUseCase.class)
    public KernelAuthGatewayService kernelAuthGatewayService(IKernelAuthGatewayPort kernelAuthGatewayPort) {
        return new KernelAuthGatewayService(kernelAuthGatewayPort);
    }

    @Bean
    @ConditionalOnMissingBean(ProxyKernelSsoUseCase.class)
    public KernelSsoGatewayService kernelSsoGatewayService(
            ProxyKernelAuthUseCase kernelAuthUseCase,
            @Qualifier("platformGatewayObjectMapper") ObjectMapper objectMapper,
            TntPlatformGatewayProperties properties) {
        return new KernelSsoGatewayService(kernelAuthUseCase, objectMapper, properties);
    }
}
