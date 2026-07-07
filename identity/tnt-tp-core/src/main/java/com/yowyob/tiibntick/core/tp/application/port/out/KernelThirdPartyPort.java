package com.yowyob.tiibntick.core.tp.application.port.out;

import com.yowyob.tiibntick.core.tp.domain.model.KernelThirdPartyDto;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port — Kernel ThirdParty integration.
 *
 * <p>Defines the contract that the application layer uses to communicate with the
 * RT-comops-tp-core Kernel module. The adapter implementation
 * ({@link com.yowyob.tiibntick.core.tp.adapter.out.kernel.KernelThirdPartyAdapter})
 * uses a reactive WebClient to call the Kernel REST API.
 *
 * <p>This port is invoked <strong>before</strong> creating a {@link
 * com.yowyob.tiibntick.core.tp.domain.model.TntClientProfile} to ensure
 * that the referenced Kernel ThirdParty exists and is active.
 *
 * <p>Architecture note: this is a secondary port (driven port) in the hexagonal
 * architecture — it points outward from the domain toward the Kernel infrastructure.
 *
 * @author MANFOUO Braun
 */
public interface KernelThirdPartyPort {

    /**
     * Fetches the Kernel ThirdParty DTO for the given UUID.
     *
     * <p>Returns an empty {@link Mono} if the ThirdParty does not exist in the Kernel.
     *
     * @param thirdPartyId the Kernel ThirdParty UUID to look up
     * @return a {@link Mono} emitting the {@link KernelThirdPartyDto}, or empty if not found
     */
    Mono<KernelThirdPartyDto> findById(UUID thirdPartyId);

    /**
     * Checks whether a Kernel ThirdParty with the given UUID exists and is active.
     *
     * <p>Lightweight existence check — use when the full DTO is not needed.
     *
     * @param thirdPartyId the Kernel ThirdParty UUID to verify
     * @return a {@link Mono} emitting {@code true} if the ThirdParty exists and is active,
     *         {@code false} otherwise
     */
    Mono<Boolean> existsAndActive(UUID thirdPartyId);
}
