package com.yowyob.tiibntick.core.administration.adapter.out.kernel;

import com.yowyob.tiibntick.common.kernel.KernelResponses;
import com.yowyob.tiibntick.core.administration.application.port.out.KernelPermissionPort;
import com.yowyob.tiibntick.core.administration.domain.model.KernelPermissionDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * WebClient-based adapter implementing {@link KernelPermissionPort}.
 *
 * <p>Communicates with the Yowyob Kernel (RT-comops-roles-core) via HTTP REST through
 * the KernelBridge WebClient configured in
 * {@link com.yowyob.tiibntick.core.administration.infrastructure.config.TntAdministrationCoreConfiguration}.
 *
 * <p>The Kernel exposes a single permissions endpoint: {@code GET /api/administration/permissions}
 * which returns the full catalog. There is no by-id or by-code filter endpoint.
 * {@code findByPermissionId} and {@code findByCode} both derive their result from
 * the list. TNT-exclusive permissions will simply be absent from the Kernel catalog —
 * the adapter returns empty signals for these, which is correct behavior
 * ({@code kernelPermissionId} stays null).
 *
 * @author MANFOUO Braun
 */
@Component
public class KernelPermissionAdapter implements KernelPermissionPort {

    private static final Logger log = LoggerFactory.getLogger(KernelPermissionAdapter.class);

    /** Base path for the Kernel permissions catalog endpoint. */
    private static final String PERMISSIONS_BASE_PATH = "/api/administration/permissions";

    private final WebClient kernelWebClient;

    public KernelPermissionAdapter(WebClient kernelWebClient) {
        this.kernelWebClient = kernelWebClient;
    }

    @Override
    public Mono<KernelPermissionDto> findByPermissionId(UUID kernelPermissionId) {
        // The Kernel does not expose GET /api/administration/permissions/{id}.
        // Derive from the full catalog list.
        return listAll()
                .filter(p -> kernelPermissionId.equals(p.permissionId()))
                .next()
                .doOnSuccess(p -> {
                    if (p == null) log.debug("Kernel permission not found by id: {}", kernelPermissionId);
                })
                .onErrorResume(Exception.class, e -> {
                    log.warn("Failed to fetch Kernel permission {}: {}", kernelPermissionId, e.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public Mono<KernelPermissionDto> findByCode(String code) {
        // The Kernel does not expose a ?code= filter on GET /api/administration/permissions.
        // Derive from the full catalog list.
        return listAll()
                .filter(p -> code.equals(p.code()))
                .next()
                .doOnSuccess(p -> {
                    if (p == null)
                        log.debug("Permission code {} not found in Kernel catalog (may be TNT-exclusive)", code);
                })
                .onErrorResume(Exception.class, e -> {
                    log.warn("Failed to resolve Kernel permission code {}: {}", code, e.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public Flux<KernelPermissionDto> listAll() {
        var responseSpec = kernelWebClient.get()
                .uri(PERMISSIONS_BASE_PATH)
                .retrieve();
        return KernelResponses.unwrapList(responseSpec, KernelPermissionDto.class, log, "listAll permissions");
    }
}
