package com.yowyob.tiibntick.core.platformgateway.adapter.in.web.admin;

import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.core.platformgateway.adapter.in.web.admin.dto.ScopeResourceResponse;
import com.yowyob.tiibntick.core.platformgateway.application.service.PlatformScopeRegistry;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Lists every known scope "resource" — the gateway blocks today (Bloc A/B/C), plus any
 * curated business-module proxy built later — so an admin UI can populate a checkbox
 * list instead of hand-typing {@code resource:action} scope strings (see
 * {@code docs/auth/platform-client-management-design.md} §4/§2.6).
 *
 * @author MANFOUO Braun
 */
@RestController
@RequestMapping("/api/v1/admin/scope-registry")
@RequirePermission(resource = "platform", action = "clients")
@Tag(name = "Platform Client Admin", description = "TNT_ADMIN-only registry of valid platform-client scopes")
public class ScopeRegistryController {

    private final PlatformScopeRegistry registry;

    public ScopeRegistryController(PlatformScopeRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    @Operation(summary = "List every known scope resource and its available actions")
    public Mono<ApiResponse<List<ScopeResourceResponse>>> list() {
        return Mono.just(ApiResponse.success(registry.listAll().stream().map(ScopeResourceResponse::from).toList()));
    }
}
