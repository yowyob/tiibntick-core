package com.yowyob.tiibntick.core.organization.infrastructure.adapter.out.kernel;

import com.yowyob.tiibntick.core.organization.application.port.out.KernelOrganizationPort;
import com.yowyob.tiibntick.core.organization.domain.model.KernelOrganizationDto;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound adapter — implements {@link KernelOrganizationPort} via reactive WebClient.
 *
 * <p>Calls the RT-comops-organization-core REST API to validate that a Kernel
 * organization referenced by a TiiBnTick aggregate (Agency, Branch, HubRelais) exists
 * and is active.
 *
 * <p>Configuration: the base URL of the RT-comops API is injected at construction time
 * via {@code OrganizationCoreAutoConfiguration} using the property
 * {@code tiibntick.kernel.organization.base-url}.
 *
 * <p>Error handling:
 * <ul>
 *   <li>HTTP 404 → returns empty {@link Mono} (organization not found).</li>
 *   <li>Other HTTP errors → propagated as reactive errors.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public class KernelOrganizationAdapter implements KernelOrganizationPort {

    /** Path template for Kernel Organization lookup by UUID. */
    private static final String FIND_BY_ID_PATH = "/api/v1/organizations/{organizationId}";

    /** WebClient configured with the Kernel API base URL and authentication headers. */
    private final WebClient kernelWebClient;

    /**
     * Constructs the adapter with a pre-configured {@link WebClient}.
     *
     * @param kernelWebClient a WebClient already configured with base URL and auth headers
     */
    public KernelOrganizationAdapter(WebClient kernelWebClient) {
        this.kernelWebClient = kernelWebClient;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Calls {@code GET /api/v1/organizations/{organizationId}} on the Kernel API.
     * Returns an empty {@link Mono} on HTTP 404.
     */
    @Override
    public Mono<KernelOrganizationDto> findById(UUID organizationId) {
        return kernelWebClient.get()
                .uri(FIND_BY_ID_PATH, organizationId)
                .retrieve()
                .bodyToMono(KernelOrganizationDto.class)
                .onErrorResume(WebClientResponseException.class,
                        ex -> ex.getStatusCode().is4xxClientError() ? Mono.empty() : Mono.error(ex));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Reuses {@link #findById(UUID)} and maps the result to a boolean.
     * Returns {@code false} if the organization is not found (empty Mono) or inactive.
     */
    @Override
    public Mono<Boolean> existsAndActive(UUID organizationId) {
        return findById(organizationId)
                .map(KernelOrganizationDto::active)
                .defaultIfEmpty(false);
    }
}
