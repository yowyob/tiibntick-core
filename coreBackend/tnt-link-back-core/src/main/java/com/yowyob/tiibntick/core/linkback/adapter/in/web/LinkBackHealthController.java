package com.yowyob.tiibntick.core.linkback.adapter.in.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * Scaffolding probe confirming tnt-link-back-core is compiled into tnt-bootstrap
 * and reachable at runtime. Exposed under {@code /api/v1/platform/link/**}, the
 * path prefix {@code tnt-platform-gateway-core} already reserves for curated
 * business-module proxies (see {@code TntPlatformGatewaySecurityConfig}) — no
 * shared security configuration change was needed to wire this module in.
 *
 * @author Dilane PAFE
 */
@RestController
public class LinkBackHealthController {

    @GetMapping("/api/v1/platform/link/health")
    public Mono<Map<String, Object>> health() {
        return Mono.just(Map.of(
                "module", "tnt-link-back-core",
                "status", "UP",
                "timestamp", Instant.now().toString()
        ));
    }
}
