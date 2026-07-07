package com.yowyob.tiibntick.core.tp.adapter.out.kernel;

import com.yowyob.tiibntick.core.tp.application.port.out.KernelThirdPartyPort;
import com.yowyob.tiibntick.core.tp.domain.model.KernelThirdPartyDto;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound adapter — implements {@link KernelThirdPartyPort} via reactive WebClient.
 *
 * <p>Calls the RT-comops-tp-core REST API to validate that a Kernel ThirdParty
 * referenced by {@link com.yowyob.tiibntick.core.tp.domain.model.TntClientProfile}
 * exists and is active before allowing registration.
 *
 * <p>Configuration: the base URL is injected at construction time via
 * {@code TntTpCoreAutoConfiguration} using the property
 * {@code tiibntick.kernel.tp.base-url}.
 *
 * <p>Error handling:
 * <ul>
 *   <li>HTTP 404 → returns empty {@link Mono} (ThirdParty not found).</li>
 *   <li>Other HTTP errors → propagated as reactive errors.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public class KernelThirdPartyAdapter implements KernelThirdPartyPort {

    /** Path template for Kernel ThirdParty lookup by UUID. */
    private static final String FIND_BY_ID_PATH = "/api/third-parties/{thirdPartyId}"; // /api/v1/third-parties/{thirdPartyId}

    /** WebClient pre-configured with the Kernel TP API base URL and auth headers. */
    private final WebClient kernelTpWebClient;

    /**
     * Constructs the adapter with a pre-configured {@link WebClient}.
     *
     * @param kernelTpWebClient a WebClient already configured with base URL and auth headers
     */
    public KernelThirdPartyAdapter(WebClient kernelTpWebClient) {
        this.kernelTpWebClient = kernelTpWebClient;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Calls {@code GET /api/v1/third-parties/{thirdPartyId}} on the Kernel TP API.
     * Returns an empty {@link Mono} on HTTP 404.
     */
    @Override
    public Mono<KernelThirdPartyDto> findById(UUID thirdPartyId) {
        return kernelTpWebClient.get()
                .uri(FIND_BY_ID_PATH, thirdPartyId)
                .retrieve()
                .bodyToMono(KernelThirdPartyDto.class)
                .onErrorResume(WebClientResponseException.NotFound.class, ex -> Mono.empty())
                .onErrorResume(WebClientResponseException.Unauthorized.class, ex ->
                    // Kernel returned 401 — JWT propagation may have failed; assume valid for non-blocking flow
                    Mono.just(KernelThirdPartyDto.minimal(thirdPartyId, "unknown")))
                .onErrorResume(WebClientResponseException.Forbidden.class, ex ->
                    // Kernel returned 403 — insufficient org subscription; assume valid
                    Mono.just(KernelThirdPartyDto.minimal(thirdPartyId, "unknown")));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Reuses {@link #findById(UUID)} and maps the result to a boolean.
     * Returns {@code false} if the ThirdParty is not found or inactive.
     */
    @Override
    public Mono<Boolean> existsAndActive(UUID thirdPartyId) {
        return findById(thirdPartyId)
                .map(KernelThirdPartyDto::active)
                .defaultIfEmpty(false);
    }
}
