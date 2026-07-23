package com.yowyob.tiibntick.bootstrap.bridge;

import com.yowyob.tiibntick.core.organization.application.port.out.KernelOrganizationPort;
import com.yowyob.tiibntick.core.organization.domain.model.KernelOrganizationDto;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.UUID;

/**
 * Single KernelBridge configuration — the ONLY place in the project where WebClient
 * beans targeting the Yowyob Kernel (RT-comops) are defined.
 *
 * <p>All kernel adapters ({@code KernelRoleProvisioningAdapter}, {@code KernelOrganizationAdapter},
 * {@code KernelThirdPartyAdapter}, {@code KernelPermissionAdapter}, {@code KernelPaymentGatewayAdapter},
 * etc.) must use one of the beans defined here. Module-level {@code @ConditionalOnMissingBean}
 * fallbacks in {@code tnt-common-core}, {@code tnt-administration-core}, {@code tnt-product-core},
 * {@code tnt-billing-wallet} are superseded by these definitions and are effectively dead code.
 *
 * <p>Imported by {@link com.yowyob.tiibntick.bootstrap.TiiBnTickApplication} via {@code @Import}.
 *
 * <p>Properties (override via env vars or application-prod.yml):
 * <pre>
 * tnt.kernel.base-url             = TNT_KERNEL_BASE_URL    (default: https://kernel-core.yowyob.com/kernel-api)
 * tnt.kernel.api-key              = TNT_KERNEL_API_KEY
 * tnt.kernel.client-id            = TNT_KERNEL_CLIENT_ID   (default: tibntick-backend)
 * tnt.kernel.connect-timeout-ms   = TNT_KERNEL_CONNECT_TIMEOUT_MS  (default: 2000)
 * tnt.kernel.response-timeout-ms  = TNT_KERNEL_RESPONSE_TIMEOUT_MS (default: 5000)
 * </pre>
 *
 * <h3>Resilience (Chantier D · Audit n°6 · S4)</h3>
 * <p>Every bean built here (auth, roles, organization, third-party/notifications — every
 * caller ultimately shares one of these three WebClients, see the class Javadoc above)
 * is guarded by a connect/response timeout on the underlying {@code reactor-netty}
 * {@link HttpClient}, plus a per-bean Resilience4j circuit breaker + time limiter + retry
 * triplet, following the same pattern as {@code tnt-trust-core}'s
 * {@code TrustEventRestClientAdapter} (§15.3 of {@code TNT_CORE_Connexion_Trust_Module.md}).
 * Unlike trust's adapter — where the triplet is applied via method-level annotations on a
 * Spring-managed bean — these WebClients are built once at context-refresh time inside a
 * {@code @Bean} factory method, so annotations (evaluated per-invocation via AOP) cannot
 * apply here; the equivalent behavior is instead composed directly into an
 * {@link ExchangeFilterFunction} using the resilience4j-reactor operators
 * ({@link CircuitBreakerOperator}, {@link TimeLimiterOperator}, {@link RetryOperator}),
 * so every call made through the WebClient — regardless of which module's adapter issues
 * it — is covered without having to touch each of the ~20 Kernel adapter classes
 * individually. Retry only re-attempts on transport-level failures (connection
 * refused/reset, timeout) — never on an already-open circuit ({@code CallNotPermittedException}
 * is explicitly ignored) — so a slow/down Kernel degrades to a fast, explicit error instead of
 * amplifying load with retries on genuine 4xx/5xx application responses.
 * Named instances: {@code kernelWebClient}, {@code kernelOrganizationWebClient},
 * {@code kernelTpWebClient} (see {@code resilience4j.*} in {@code application.yml}).
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Configuration
public class KernelBridgeConfig {

    @Value("${tnt.kernel.base-url:https://kernel-core.yowyob.com/kernel-api}")
    private String kernelBaseUrl;

    @Value("${tnt.kernel.api-key:changeme-kernel-api-key}")
    private String kernelApiKey;

    @Value("${tnt.kernel.client-id:tibntick-backend}")
    private String kernelClientId;

    @Value("${tnt.kernel.connect-timeout-ms:2000}")
    private int kernelConnectTimeoutMs;

    @Value("${tnt.kernel.response-timeout-ms:5000}")
    private int kernelResponseTimeoutMs;

    /**
     * Primary Kernel WebClient — used by the majority of adapters.
     * Qualified as "kernelWebClient"; all module-level fallback beans defer to this.
     */
    @Bean("kernelWebClient")
    public WebClient kernelWebClient(
            WebClient.Builder builder,
            CircuitBreakerRegistry circuitBreakerRegistry,
            TimeLimiterRegistry timeLimiterRegistry,
            RetryRegistry retryRegistry) {
        log.info("KernelBridge → {} (client-id: {})", kernelBaseUrl, kernelClientId);
        return builder.clone()
                .baseUrl(kernelBaseUrl)
                .defaultHeader("X-Api-Key", kernelApiKey)
                .defaultHeader("X-Client-Id", kernelClientId)
                .defaultHeader("X-Solution-Code", "TNT")
                .clientConnector(timeoutConnector())
                .filter(resilience("kernelWebClient", circuitBreakerRegistry, timeLimiterRegistry, retryRegistry))
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
    public WebClient kernelOrganizationWebClient(
            WebClient.Builder builder,
            CircuitBreakerRegistry circuitBreakerRegistry,
            TimeLimiterRegistry timeLimiterRegistry,
            RetryRegistry retryRegistry) {
        return builder.clone()
                .baseUrl(kernelBaseUrl)
                .defaultHeader("X-Api-Key", kernelApiKey)
                .defaultHeader("X-Client-Id", kernelClientId)
                .defaultHeader("X-Solution-Code", "TNT")
                .clientConnector(timeoutConnector())
                .filter(resilience("kernelOrganizationWebClient", circuitBreakerRegistry, timeLimiterRegistry, retryRegistry))
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
    public WebClient kernelTpWebClient(
            WebClient.Builder builder,
            CircuitBreakerRegistry circuitBreakerRegistry,
            TimeLimiterRegistry timeLimiterRegistry,
            RetryRegistry retryRegistry) {
        return builder.clone()
                .baseUrl(kernelBaseUrl)
                .defaultHeader("X-Api-Key", kernelApiKey)
                .defaultHeader("X-Client-Id", kernelClientId)
                .defaultHeader("X-Solution-Code", "TNT")
                .clientConnector(timeoutConnector())
                .filter(propagateBearerToken())
                .filter(resilience("kernelTpWebClient", circuitBreakerRegistry, timeLimiterRegistry, retryRegistry))
                .filter(logRequest())
                .filter(logResponse())
                .build();
    }

    /**
     * Kernel WebClient for Payment/Wallet calls ({@code tnt-billing-wallet}).
     * Points to the same Kernel base URL with the same credentials — no new network
     * configuration, per the payment/wallet delegation workstream (see
     * {@code docs/audits/remediation/workstream-payment-billing-kernel-delegation.md}).
     * Used by {@code KernelPaymentGatewayAdapter} to call the Kernel's
     * {@code payment-controller} ({@code /api/payments/wallets/**}).
     * Covered by the same connect/response timeout + circuit breaker/retry triplet as
     * every other Kernel WebClient (Chantier D · Audit n°6 · S4).
     */
    @Bean("kernelPaymentWebClient")
    public WebClient kernelPaymentWebClient(
            WebClient.Builder builder,
            CircuitBreakerRegistry circuitBreakerRegistry,
            TimeLimiterRegistry timeLimiterRegistry,
            RetryRegistry retryRegistry) {
        return builder.clone()
                .baseUrl(kernelBaseUrl)
                .defaultHeader("X-Api-Key", kernelApiKey)
                .defaultHeader("X-Client-Id", kernelClientId)
                .defaultHeader("X-Solution-Code", "TNT")
                .clientConnector(timeoutConnector())
                .filter(resilience("kernelPaymentWebClient", circuitBreakerRegistry, timeLimiterRegistry, retryRegistry))
                .filter(logRequest())
                .filter(logResponse())
                .build();
    }

    /**
     * Reactor-netty connector enforcing connect + response timeouts on every Kernel call.
     * Defaults: connect 2s / response 5s (see {@code tnt.kernel.*-timeout-ms}).
     */
    private ReactorClientHttpConnector timeoutConnector() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, kernelConnectTimeoutMs)
                .responseTimeout(Duration.ofMillis(kernelResponseTimeoutMs));
        return new ReactorClientHttpConnector(httpClient);
    }

    /**
     * Composes the circuit breaker / time limiter / retry triplet for the named Kernel
     * WebClient instance, applied around every request made through it.
     *
     * <p>Order (innermost to outermost): {@link TimeLimiterOperator} bounds a single attempt,
     * {@link CircuitBreakerOperator} records the outcome and short-circuits when open,
     * {@link RetryOperator} re-subscribes (triggering a fresh HTTP call) on transport
     * failures only — this is the same ordering Resilience4j recommends
     * (Retry ⊃ CircuitBreaker ⊃ TimeLimiter ⊃ call).
     */
    private ExchangeFilterFunction resilience(
            String instanceName,
            CircuitBreakerRegistry circuitBreakerRegistry,
            TimeLimiterRegistry timeLimiterRegistry,
            RetryRegistry retryRegistry) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(instanceName);
        TimeLimiter timeLimiter = timeLimiterRegistry.timeLimiter(instanceName);
        Retry retry = retryRegistry.retry(instanceName);

        return (request, next) -> next.exchange(request)
                .transformDeferred(TimeLimiterOperator.of(timeLimiter))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .transformDeferred(RetryOperator.of(retry))
                .doOnError(ex -> log.warn("Kernel call via {} failed: {} → {}: {}",
                        instanceName, request.method(), request.url(), ex.toString()));
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
