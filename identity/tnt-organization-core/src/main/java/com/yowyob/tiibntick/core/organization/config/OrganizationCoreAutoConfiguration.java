package com.yowyob.tiibntick.core.organization.config;

import com.yowyob.tiibntick.core.auth.adapter.in.web.ReactiveSecurityContextExtractor;
import com.yowyob.tiibntick.core.organization.application.port.in.ManageAgencyUseCase;
import com.yowyob.tiibntick.core.organization.application.port.in.ManageBranchUseCase;
import com.yowyob.tiibntick.core.organization.application.port.in.ManageFreelancerOrgUseCase;
import com.yowyob.tiibntick.core.organization.application.port.in.ManageHubUseCase;
import com.yowyob.tiibntick.core.organization.application.port.out.AgencyRepositoryPort;
import com.yowyob.tiibntick.core.organization.application.port.out.BranchRepositoryPort;
import com.yowyob.tiibntick.core.organization.application.port.out.FreelancerOrgDidAnchorPort;
import com.yowyob.tiibntick.core.organization.application.port.out.FreelancerOrgEventPublisherPort;
import com.yowyob.tiibntick.core.organization.application.port.out.FreelancerOrgRepositoryPort;
import com.yowyob.tiibntick.core.organization.application.port.out.HubRepositoryPort;
import com.yowyob.tiibntick.core.organization.application.port.out.KernelOrganizationPort;
import com.yowyob.tiibntick.core.organization.application.service.AgencyService;
import com.yowyob.tiibntick.core.organization.application.service.BranchService;
import com.yowyob.tiibntick.core.organization.application.service.FreelancerOrgService;
import com.yowyob.tiibntick.core.organization.application.service.HubRelaisService;
import com.yowyob.tiibntick.core.organization.infrastructure.adapter.out.kernel.KernelOrganizationAdapter;
import com.yowyob.tiibntick.core.organization.infrastructure.adapter.out.messaging.FreelancerOrgEventPublisherAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Spring Boot auto-configuration for the {@code tnt-organization-core} module.
 *
 * <p>Wires together:
 * <ul>
 *   <li>Application services ({@link AgencyService}, {@link BranchService},
 *       {@link HubRelaisService}, {@link FreelancerOrgService})</li>
 *   <li>Kernel integration adapter ({@link KernelOrganizationAdapter})</li>
 *   <li>Messaging adapter ({@link FreelancerOrgEventPublisherAdapter})</li>
 * </ul>
 *
 * <p>Infrastructure adapters (AgencyRepositoryAdapter, BranchRepositoryAdapter,
 * HubRepositoryAdapter, FreelancerOrgRepositoryAdapter) are registered as
 * {@code @Component} directly and picked up by component scanning in the
 * bootstrap module.
 *
 * <p>The {@code @ConditionalOnMissingBean} guard on each use case allows downstream
 * consumers (e.g., tnt-bootstrap) to override beans for testing or customization.
 *
 * <h3>tnt-auth-core integration ()</h3>
 * <p>{@link ReactiveSecurityContextExtractor} is available as an auto-configured bean
 * from {@code tnt-auth-core}. Service classes can inject it for programmatic
 * tenant/actor resolution without manual {@code ReactiveSecurityContextHolder} calls.
 *
 * <h3>tnt-roles-core integration ()</h3>
 * <p>{@code @RequirePermission} annotations on service methods are enforced reactively
 * by the {@code TntPermissionAspect} registered by {@code tnt-roles-core}'s
 * auto-configuration. No additional wiring needed here.
 * <ul>
 *   <li>{@code agency:read/write}                — enforced on {@link AgencyService}</li>
 *   <li>{@code branch:read/write/manage}          — enforced on {@link BranchService}</li>
 *   <li>{@code relay:read/write/operate}          — enforced on {@link HubRelaisService}</li>
 *   <li>{@code freelancer_org:read/write/admin}   — enforced on {@link FreelancerOrgService}</li>
 * </ul>
 *
 * <h3> additions — FreelancerOrganization</h3>
 * <p>Added wiring for:
 * <ul>
 *   <li>{@link FreelancerOrgService} — manages FreelancerOrganization lifecycle.</li>
 *   <li>{@link FreelancerOrgEventPublisherAdapter} — publishes domain events via
 *       Spring ApplicationEventPublisher (forwarded to Kafka by bootstrap).</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@AutoConfiguration
@EnableR2dbcRepositories(
        basePackages = "com.yowyob.tiibntick.core.organization.infrastructure.adapter.out.persistence.repository",
        entityOperationsRef = "r2dbcEntityTemplate"
)
public class OrganizationCoreAutoConfiguration {

    /**
     * Base URL of the RT-comops-organization-core REST API.
     * Configured via {@code tiibntick.kernel.organization.base-url} property.
     * Defaults to localhost for development.
     */
    @Value("${tiibntick.kernel.organization.base-url:https://kernel-core.yowyob.com/}")
    private String kernelOrganizationBaseUrl;

    /**
     * Reactive WebClient pre-configured for calls to the Kernel organization API.
     *
     * @return a WebClient targeting the Kernel organization base URL
     */
    @Bean
    @ConditionalOnMissingBean(name = "kernelOrganizationWebClient")
    public WebClient kernelOrganizationWebClient() {
        return WebClient.builder()
                .baseUrl(kernelOrganizationBaseUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * Kernel organization outbound adapter.
     * Validates organization references via the RT-comops REST API.
     *
     * @param kernelOrganizationWebClient the configured WebClient
     * @return the {@link KernelOrganizationPort} implementation
     */
    @Bean
    @ConditionalOnMissingBean
    public KernelOrganizationPort kernelOrganizationPort(WebClient kernelOrganizationWebClient) {
        return new KernelOrganizationAdapter(kernelOrganizationWebClient);
    }

    /**
     * Agency use case — creates, queries, and manages Agency aggregates.
     *
     * @param agencyRepositoryPort   persistence port
     * @param kernelOrganizationPort Kernel validation port
     * @return the {@link ManageAgencyUseCase} implementation
     */
    @Bean
    @ConditionalOnMissingBean
    public ManageAgencyUseCase manageAgencyUseCase(AgencyRepositoryPort agencyRepositoryPort,
                                                    KernelOrganizationPort kernelOrganizationPort) {
        return new AgencyService(agencyRepositoryPort, kernelOrganizationPort);
    }

    /**
     * Branch use case — creates, queries, and manages Branch aggregates.
     *
     * @param branchRepositoryPort   persistence port
     * @param kernelOrganizationPort Kernel validation port
     * @return the {@link ManageBranchUseCase} implementation
     */
    @Bean
    @ConditionalOnMissingBean
    public ManageBranchUseCase manageBranchUseCase(BranchRepositoryPort branchRepositoryPort,
                                                    KernelOrganizationPort kernelOrganizationPort) {
        return new BranchService(branchRepositoryPort, kernelOrganizationPort);
    }

    /**
     * Hub use case — creates, queries, and manages HubRelais aggregates.
     *
     * @param hubRepositoryPort      persistence port
     * @param kernelOrganizationPort Kernel validation port
     * @return the {@link ManageHubUseCase} implementation
     */
    @Bean
    @ConditionalOnMissingBean
    public ManageHubUseCase manageHubUseCase(HubRepositoryPort hubRepositoryPort,
                                              KernelOrganizationPort kernelOrganizationPort) {
        return new HubRelaisService(hubRepositoryPort, kernelOrganizationPort);
    }

    // ─FreelancerOrganization beans ────────────────────────────────

    /**
     * FreelancerOrganization event publisher adapter.
     *
     * <p>Uses Spring's {@link ApplicationEventPublisher} as the event bus.
     * The tnt-bootstrap module registers a listener that forwards events to Kafka
     * via {@code yow-event-kernel}.
     *
     * @param applicationEventPublisher Spring application event publisher
     * @return the {@link FreelancerOrgEventPublisherPort} implementation
     */
    @Bean
    @ConditionalOnMissingBean
    public FreelancerOrgEventPublisherPort freelancerOrgEventPublisherPort(
            ApplicationEventPublisher applicationEventPublisher) {
        return new FreelancerOrgEventPublisherAdapter(applicationEventPublisher);
    }

    /**
     * FreelancerOrganization use case — manages the full FreelancerOrganization lifecycle:
     * registration, KYC upgrade, verification, suspension, sub-deliverer management,
     * capabilities, zones, and billing policy assignment.
     *
     * <p>Requires permission {@code freelancer_org:read/write/admin} enforced via
     * {@code @RequirePermission} (tnt-roles-core AOP aspect).
     *
     * @param repository     persistence port for FreelancerOrganization aggregates
     * @param eventPublisher messaging port for domain events
     * @param didAnchorPort  outbound port for anchoring the org's blockchain DID,
     *                       implemented by {@code tnt-trust-core}
     * @return the {@link ManageFreelancerOrgUseCase} implementation
     */
    @Bean
    @ConditionalOnMissingBean
    public ManageFreelancerOrgUseCase manageFreelancerOrgUseCase(
            FreelancerOrgRepositoryPort repository,
            FreelancerOrgEventPublisherPort eventPublisher,
            FreelancerOrgDidAnchorPort didAnchorPort) {
        return new FreelancerOrgService(repository, eventPublisher, didAnchorPort);
    }
}
