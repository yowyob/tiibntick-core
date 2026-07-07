package com.yowyob.tiibntick.bootstrap.actuator;

import com.yowyob.tiibntick.bootstrap.bridge.KernelConnectionStatus;
import com.yowyob.tiibntick.bootstrap.bridge.YowyobKernelBridge;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Custom Spring Boot Actuator endpoint exposing the Yowyob Kernel connectivity status.
 * <p>
 * Accessible at:
 * <ul>
 *   <li>{@code GET /actuator/tnt-kernel} — current connectivity status (cached)</li>
 *   <li>{@code POST /actuator/tnt-kernel} — forces a live re-ping of the kernel</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@Component
@Endpoint(id = "tnt-kernel")
@RequiredArgsConstructor
public class TntKernelStatusEndpoint {

    private final YowyobKernelBridge bridge;

    /**
     * Returns the last known kernel connectivity status (no network call).
     *
     * @return {@link KernelConnectionStatus}
     */
    @ReadOperation
    public Mono<KernelConnectionStatus> read() {
        return Mono.just(bridge.getConnectionStatus());
    }

    /**
     * Forces a live ping to the Yowyob Kernel and returns the updated status.
     * Useful for ops to verify connectivity after a network event.
     *
     * @return updated {@link KernelConnectionStatus}
     */
    @WriteOperation
    public Mono<KernelConnectionStatus> refresh() {
        return bridge.ping()
                .flatMap(ok -> bridge.getKernelVersion().thenReturn(bridge.getConnectionStatus()))
                .onErrorReturn(bridge.getConnectionStatus());
    }
}
