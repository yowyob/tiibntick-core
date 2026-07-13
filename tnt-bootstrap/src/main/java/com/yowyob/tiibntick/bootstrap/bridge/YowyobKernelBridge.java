package com.yowyob.tiibntick.bootstrap.bridge;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reactive bridge to the Yowyob Kernel (RT-comops managed by TSAFACK Savio).
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Ping the kernel to verify connectivity at startup and periodically</li>
 *   <li>Retrieve the kernel version string</li>
 *   <li>Check YowAuth0 (comops-auth-core) reachability</li>
 *   <li>Check the kernel event bus (yow-event-kernel) reachability</li>
 *   <li>Maintain a live {@link KernelConnectionStatus} for health checks</li>
 * </ul>
 * <p>
 * When the kernel is unreachable, TiiBnTick Core continues with degraded status
 * (authentication-dependent features will fail gracefully).
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component
public class YowyobKernelBridge {

    private final String kernelBaseUrl;
    private final String kernelApiKey;
    private final WebClient webClient;
    private final AtomicReference<KernelConnectionStatus> connectionStatus =
            new AtomicReference<>(KernelConnectionStatus.unknown());

    public YowyobKernelBridge(
            @Value("${tnt.kernel.base-url:https://kernel-core.yowyob.com/kernel-api}") String kernelBaseUrl,
            @Value("${tnt.kernel.api-key:changeme}") String kernelApiKey,
            @Value("${tnt.kernel.client-id:tibntick-backend}") String kernelClientId,
            WebClient.Builder webClientBuilder) {
        this.kernelBaseUrl = kernelBaseUrl;
        this.kernelApiKey = kernelApiKey;
        this.webClient = webClientBuilder
                .baseUrl(kernelBaseUrl)
                .defaultHeader("X-Api-Key", kernelApiKey)
                .defaultHeader("X-Client-Id", kernelClientId)
                .defaultHeader("X-Solution-Code", "TNT")
                .build();
        log.info("YowyobKernelBridge configured → {}", kernelBaseUrl);
    }

    /**
     * Pings the kernel and updates {@link KernelConnectionStatus}.
     *
     * <p>Targets {@code /.well-known/openid-configuration} rather than
     * {@code /actuator/health} — the Kernel's actuator is exposed on a separate
     * management port, not on the public host this bridge talks to, so
     * {@code /actuator/health} never resolves correctly here (confirmed by
     * TSAFACK Savio, Kernel maintainer, 2026-07-12). The OIDC discovery document is
     * public, lightweight, and already used elsewhere in {@code KernelBridgeConfig}
     * as a reachability signal.
     *
     * @return {@code true} if the kernel responded successfully
     */
    public Mono<Boolean> ping() {
        long start = System.currentTimeMillis();
        return webClient.get()
                .uri("/.well-known/openid-configuration")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(java.time.Duration.ofSeconds(5))
                .map(response -> {
                    long latency = System.currentTimeMillis() - start;
                    log.debug("Kernel ping OK — latency={}ms", latency);
                    connectionStatus.set(connectionStatus.get().toBuilder()
                            .connected(true)
                            .lastCheckedAt(LocalDateTime.now())
                            .latencyMs(latency)
                            .errorMessage(null)
                            .build());
                    return true;
                })
                .onErrorResume(ex -> {
                    log.warn("Kernel ping failed: {}", ex.getMessage());
                    connectionStatus.set(KernelConnectionStatus.disconnected(ex.getMessage()));
                    return Mono.just(false);
                });
    }

    /**
     * Retrieves the kernel version string from the kernel's actuator/info endpoint.
     *
     * @return kernel version, or "unknown" on failure
     */
    public Mono<String> getKernelVersion() {
        return webClient.get()
                .uri("/actuator/info")
                .retrieve()
                .bodyToMono(java.util.Map.class)
                .timeout(java.time.Duration.ofSeconds(5))
                .map(info -> {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> build = (java.util.Map<String, Object>) info.get("build");
                    String version = build != null ? (String) build.get("version") : "unknown";
                    connectionStatus.set(connectionStatus.get().toBuilder()
                            .kernelVersion(version)
                            .build());
                    return version;
                })
                .onErrorReturn("unknown");
    }

    /**
     * Checks whether comops-auth-core (YowAuth0) is reachable.
     *
     * @return {@code true} if auth is reachable
     */
    public Mono<Boolean> checkYowAuthStatus() {
        return webClient.get()
                .uri("/auth/health")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(java.time.Duration.ofSeconds(5))
                .map(r -> {
                    connectionStatus.set(connectionStatus.get().toBuilder()
                            .yowAuthReachable(true).build());
                    return true;
                })
                .onErrorResume(ex -> {
                    log.warn("YowAuth0 unreachable: {}", ex.getMessage());
                    connectionStatus.set(connectionStatus.get().toBuilder()
                            .yowAuthReachable(false).build());
                    return Mono.just(false);
                });
    }

    /**
     * Checks whether the Kernel event bus (yow-event-kernel / Kafka topics) is reachable.
     *
     * @return {@code true} if event bus is reachable
     */
    public Mono<Boolean> checkKernelEventBus() {
        return webClient.get()
                .uri("/events/health")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(java.time.Duration.ofSeconds(5))
                .map(r -> {
                    connectionStatus.set(connectionStatus.get().toBuilder()
                            .eventBusReachable(true).build());
                    return true;
                })
                .onErrorResume(ex -> {
                    log.warn("Kernel event bus unreachable: {}", ex.getMessage());
                    connectionStatus.set(connectionStatus.get().toBuilder()
                            .eventBusReachable(false).build());
                    return Mono.just(false);
                });
    }

    public KernelConnectionStatus getConnectionStatus() {
        return connectionStatus.get();
    }
}
