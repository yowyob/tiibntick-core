package com.yowyob.tiibntick.bootstrap.bridge;

import lombok.Builder;
import lombok.Getter;
import lombok.With;

import java.time.LocalDateTime;

/**
 * Value object capturing the live connectivity status of the Yowyob Kernel
 * (RT-comops / comops-auth-core). Updated periodically by {@link YowyobKernelBridge}.
 *
 * @author MANFOUO Braun
 */
@Getter
@Builder(toBuilder = true)
@With
public final class KernelConnectionStatus {

    private final boolean connected;
    private final String kernelVersion;
    private final boolean yowAuthReachable;
    private final boolean eventBusReachable;
    private final LocalDateTime lastCheckedAt;
    private final Long latencyMs;
    private final String errorMessage;

    public boolean isFullyOperational() {
        return connected && yowAuthReachable && eventBusReachable;
    }

    public static KernelConnectionStatus disconnected(String reason) {
        return KernelConnectionStatus.builder()
                .connected(false)
                .yowAuthReachable(false)
                .eventBusReachable(false)
                .lastCheckedAt(LocalDateTime.now())
                .kernelVersion("unknown")
                .errorMessage(reason)
                .build();
    }

    public static KernelConnectionStatus unknown() {
        return disconnected("not yet checked");
    }
}
