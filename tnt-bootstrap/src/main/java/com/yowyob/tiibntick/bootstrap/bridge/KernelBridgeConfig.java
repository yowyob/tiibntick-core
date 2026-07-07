package com.yowyob.tiibntick.bootstrap.bridge;

import com.yowyob.tiibntick.core.organization.application.port.out.KernelOrganizationPort;
import com.yowyob.tiibntick.core.organization.domain.model.KernelOrganizationDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Single KernelBridge configuration — the ONLY place in the project where WebClient
 * beans targeting the Yowyob Kernel (RT-comops) are defined.
 *
 * <p>All kernel adapters ({@code KernelRoleProvisioningAdapter}, {@code KernelOrganizationAdapter},
 * {@code KernelThirdPartyAdapter}, {@code KernelPermissionAdapter}, etc.) must use one of the
 * three beans defined here. Module-level {@code @ConditionalOnMissingBean} fallbacks in
 * {@code tnt-common-core}, {@code tnt-administration-core}, {@code tnt-product-core} are
 * superseded by these definitions and are effectively dead code.
 *
 * <p>Imported by {@link com.yowyob.tiibntick.bootstrap.TiiBnTickApplication} via {@code @Import}.
 *
 * <p>Properties (override via env vars or application-prod.yml):
 * <pre>
 * tnt.kernel.base-url    = TNT_KERNEL_BASE_URL   (default: https://kernel-core.yowyob.com)
 * tnt.kernel.api-key     = TNT_KERNEL_API_KEY
 * tnt.kernel.client-id   = TNT_KERNEL_CLIENT_ID  (default: tibntick-backend)
 * </pre>
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Configuration
public class KernelBridgeConfig {

    @Value("${tnt.kernel.base-url:https://kernel-core.yowyob.com}")
    private String kernelBaseUrl;

    @Value("${tnt.kernel.api-key:changeme-kernel-api-key}")
    private String kernelApiKey;

    @Value("${tnt.kernel.client-id:tibntick-backend}")
    private String kernelClientId;

    /**
     * Primary Kernel WebClient — used by the majority of adapters.
     * Qualified as "kernelWebClient"; all module-level fallback beans defer to this.
     */
    @Bean("kernelWebClient")
    public WebClient kernelWebClient(WebClient.Builder builder) {
        log.info("KernelBridge → {} (client-id: {})", kernelBaseUrl, kernelClientId);
        return builder.clone()
                .baseUrl(kernelBaseUrl)
                .defaultHeader("X-Api-Key", kernelApiKey)
                .defaultHeader("X-Client-Id", kernelClientId)
                .defaultHeader("X-Solution-Code", "TNT")
                .filter(logRequest())
                .filter(logResponse())
                .build();
    }

    /**
     * Kernel WebClient for Organization calls ({@code tnt-organization-core}).
     * Points to the same Kernel base URL with the same credentials.
     * Overrides {@code OrganizationCoreAutoConfiguration#kernelOrganizationWebClient()}.
     */
    @Bean("kernelOrganizationWebClient")
    public WebClient kernelOrganizationWebClient(WebClient.Builder builder) {
        return builder.clone()
                .baseUrl(kernelBaseUrl)
                .defaultHeader("X-Api-Key", kernelApiKey)
                .defaultHeader("X-Client-Id", kernelClientId)
                .defaultHeader("X-Solution-Code", "TNT")
                .filter(logRequest())
                .filter(logResponse())
                .build();
    }

    /**
     * Kernel WebClient for ThirdParty calls ({@code tnt-tp-core}).
     * Propagates the caller's Bearer JWT so the Kernel can apply user-scoped access control
     * on endpoints like GET /api/third-parties/{id} that require user context.
     * Overrides {@code TntTpCoreAutoConfiguration#kernelTpWebClient()}.
     */
    @Bean("kernelTpWebClient")
    public WebClient kernelTpWebClient(WebClient.Builder builder) {
        return builder.clone()
                .baseUrl(kernelBaseUrl)
                .defaultHeader("X-Api-Key", kernelApiKey)
                .defaultHeader("X-Client-Id", kernelClientId)
                .defaultHeader("X-Solution-Code", "TNT")
                .filter(propagateBearerToken())
                .filter(logRequest())
                .filter(logResponse())
                .build();
    }

    /**
     * Dev-mode no-op: when anonymous context is enabled (no real Kernel available),
     * bypass the Kernel organization validation by always returning active=true.
     * The real {@link KernelOrganizationPort} bean in OrganizationCoreAutoConfiguration
     * is @ConditionalOnMissingBean and will be skipped when this bean is registered.
     */
    @Bean
    @ConditionalOnProperty(name = "tnt.auth.allow-anonymous-context", havingValue = "true")
    public KernelOrganizationPort noOpKernelOrganizationPort() {
        log.warn("KernelOrganizationPort → NO-OP (allow-anonymous-context=true). All org checks return active=true.");
        return new KernelOrganizationPort() {
            @Override
            public Mono<KernelOrganizationDto> findById(UUID organizationId) {
                return Mono.just(KernelOrganizationDto.minimal(organizationId, "dev-mode-org"));
            }

            @Override
            public Mono<Boolean> existsAndActive(UUID organizationId) {
                return Mono.just(true);
            }
        };
    }

    // ── Request/Response logging filters ──────────────────────────────────────

    /**
     * Reads the current user's JWT from the reactive security context and forwards it
     * as an {@code Authorization: Bearer} header on outbound Kernel requests.
     * Falls back silently (no auth header added) when no JWT is present in context.
     */
    private ExchangeFilterFunction propagateBearerToken() {
        return (request, next) -> ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(auth -> auth.getToken().getTokenValue())
                .map(token -> ClientRequest.from(request)
                        .header("Authorization", "Bearer " + token)
                        .build())
                .defaultIfEmpty(request)
                .flatMap(next::exchange);
    }

    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            log.debug("Kernel → {} {}", request.method(), request.url());
            return Mono.just(request);
        });
    }

    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(response -> {
            log.debug("Kernel ← {}", response.statusCode());
            return Mono.just(response);
        });
    }
}
