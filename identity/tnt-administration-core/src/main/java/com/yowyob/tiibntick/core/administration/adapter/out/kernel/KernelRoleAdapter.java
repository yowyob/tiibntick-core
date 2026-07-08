package com.yowyob.tiibntick.core.administration.adapter.out.kernel;

import com.yowyob.tiibntick.common.kernel.KernelResponses;
import com.yowyob.tiibntick.core.administration.application.port.out.KernelRolePort;
import com.yowyob.tiibntick.core.administration.domain.model.KernelRoleDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * WebClient-based adapter implementing {@link KernelRolePort}.
 *
 * <p>Communicates with the Yowyob Kernel (RT-comops-roles-core) via HTTP REST through
 * the KernelBridge WebClient configured in
 * {@link com.yowyob.tiibntick.core.administration.infrastructure.config.TntAdministrationCoreConfiguration}.
 *
 * <p>All calls are non-blocking and Reactor-based. Errors from the Kernel are logged
 * and translated to empty signals so that provisioning can proceed gracefully.
 *
 * @author MANFOUO Braun
 */
@Component
public class KernelRoleAdapter implements KernelRolePort {

    private static final Logger log = LoggerFactory.getLogger(KernelRoleAdapter.class);

    /** Base path for the Kernel roles endpoint. */
    private static final String ROLES_BASE_PATH = "/api/roles";

    private final WebClient kernelWebClient;

    public KernelRoleAdapter(WebClient kernelWebClient) {
        this.kernelWebClient = kernelWebClient;
    }

    @Override
    public Mono<KernelRoleDto> findByRoleId(UUID kernelRoleId) {
        var responseSpec = kernelWebClient.get()
                .uri(ROLES_BASE_PATH + "/{roleId}", kernelRoleId)
                .retrieve();
        return KernelResponses.unwrapObject(responseSpec, KernelRoleDto.class, log,
                "findByRoleId " + kernelRoleId);
    }

    @Override
    public Mono<KernelRoleDto> findByCodeAndTenant(String code, UUID tenantId) {
        // GET /api/roles accepts no query params — fetch the tenant's list and filter by code.
        return findAllByTenant(tenantId)
                .filter(r -> code.equals(r.code()))
                .next()
                .doOnSuccess(r -> {
                    if (r == null) log.debug("Kernel role not found for code={}, tenantId={}", code, tenantId);
                })
                .onErrorResume(Exception.class, e -> {
                    log.warn("Failed to fetch Kernel role by code {} for tenant {}: {}",
                            code, tenantId, e.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Boolean> existsByCodeAndTenant(String code, UUID tenantId) {
        // The Kernel has no /roles/exists endpoint — derive from the list.
        return findAllByTenant(tenantId)
                .any(r -> code.equals(r.code()))
                .onErrorReturn(false);
    }

    @Override
    public Flux<KernelRoleDto> findAllByTenant(UUID tenantId) {
        // GET /api/roles — tenant is identified by the X-Tenant-Id header.
        var responseSpec = kernelWebClient.get()
                .uri(ROLES_BASE_PATH)
                .header("X-Tenant-Id", tenantId.toString())
                .retrieve();
        return KernelResponses.unwrapList(responseSpec, KernelRoleDto.class, log,
                "findAllByTenant " + tenantId);
    }
}
