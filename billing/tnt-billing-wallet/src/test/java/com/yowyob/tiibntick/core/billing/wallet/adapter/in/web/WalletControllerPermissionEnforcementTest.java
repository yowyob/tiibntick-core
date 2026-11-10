package com.yowyob.tiibntick.core.billing.wallet.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IWalletUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.CreditWalletCommand;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.InitiatePaymentCommand;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.SplitMissionRevenueCommand;
import com.yowyob.tiibntick.core.billing.wallet.application.service.WalletService;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.Money;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.PaymentSplitResult;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.Wallet;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import com.yowyob.tiibntick.core.roles.adapter.in.web.TntPermissionAspect;
import com.yowyob.tiibntick.core.roles.adapter.in.web.TntRoleExceptionHandler;
import com.yowyob.tiibntick.core.roles.application.port.out.ReactivePermissionResolver;
import com.yowyob.tiibntick.core.roles.application.service.TntPermissionEvaluator;
import com.yowyob.tiibntick.core.roles.application.service.TntRoleDefinitionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Currency;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Security regression tests for {@link WalletController} (wallet hardening).
 *
 * <p>Proves, end-to-end through the real {@code @RequirePermission} AOP aspect and a fixed
 * {@code @CurrentUser} resolver, that:
 * <ul>
 *   <li>a caller holding only the permissions of a {@code FREELANCER}/{@code CLIENT}
 *       ({@code wallet:read}, {@code wallet:write}, {@code payment:process}) can no longer credit
 *       a wallet, split revenue, freeze, or read an arbitrary owner's wallet (403);</li>
 *   <li>the tenant always comes from the JWT — a spoofed {@code tenantId} query parameter or body
 *       field is ignored;</li>
 *   <li>self-service endpoints can only reach the caller's own wallet.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
class WalletControllerPermissionEnforcementTest {

    private static final UUID JWT_USER = UUID.randomUUID();
    private static final UUID JWT_TENANT = UUID.randomUUID();
    private static final UUID OTHER_USER = UUID.randomUUID();
    private static final UUID OTHER_TENANT = UUID.randomUUID();

    /** What FREELANCER / CLIENT roles hold: enough for self-service, never enough for admin ops. */
    private static final List<String> USER_AUTHORITIES =
            List.of("wallet:read", "wallet:write", "payment:process");
    private static final List<String> ADMIN_AUTHORITIES = List.of("wallet:admin", "wallet:read", "wallet:write");

    // ── credit ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("credit is rejected with 403 for a user holding wallet:write + payment:process (self-credit fraud)")
    void credit_withUserPermissions_returns403() {
        IWalletUseCase useCase = mock(IWalletUseCase.class);

        client(useCase, USER_AUTHORITIES, identity(JWT_TENANT)).post()
                .uri("/billing/wallet/{userId}/credit", JWT_USER)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"amount": 1000000, "currency": "XAF", "referenceId": "ref-1", "description": "free money"}
                        """)
                .exchange()
                .expectStatus().isForbidden();

        verify(useCase, never()).creditWallet(any());
    }

    @Test
    @DisplayName("credit by an admin uses the JWT tenant, ignoring a spoofed tenantId query parameter")
    void credit_asAdmin_usesJwtTenant_notQueryParam() {
        IWalletUseCase useCase = mock(IWalletUseCase.class);
        Wallet wallet = Wallet.createNew(OTHER_USER, JWT_TENANT, Currency.getInstance("XAF"));
        when(useCase.creditWallet(any())).thenReturn(Mono.just(
                wallet.credit(Money.of(BigDecimal.valueOf(1000), "XAF"), "ref-1", "top-up")));

        client(useCase, ADMIN_AUTHORITIES, identity(JWT_TENANT)).post()
                .uri(b -> b.path("/billing/wallet/{userId}/credit")
                        .queryParam("tenantId", OTHER_TENANT).build(OTHER_USER))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"amount": 1000, "currency": "XAF", "referenceId": "ref-1", "description": "top-up"}
                        """)
                .exchange()
                .expectStatus().isCreated();

        ArgumentCaptor<CreditWalletCommand> captor = ArgumentCaptor.forClass(CreditWalletCommand.class);
        verify(useCase).creditWallet(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo(JWT_TENANT).isNotEqualTo(OTHER_TENANT);
        assertThat(captor.getValue().userId()).isEqualTo(OTHER_USER);
    }

    // ── reads ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /{userId}/balance of an arbitrary owner is 403 without wallet:admin")
    void balanceOfArbitraryOwner_withUserPermissions_returns403() {
        IWalletUseCase useCase = mock(IWalletUseCase.class);

        client(useCase, USER_AUTHORITIES, identity(JWT_TENANT)).get()
                .uri("/billing/wallet/{userId}/balance", OTHER_USER)
                .exchange()
                .expectStatus().isForbidden();

        verify(useCase, never()).findWallet(any(), any());
    }

    @Test
    @DisplayName("GET /{userId}/transactions of an arbitrary owner is 403 without wallet:admin")
    void transactionsOfArbitraryOwner_withUserPermissions_returns403() {
        IWalletUseCase useCase = mock(IWalletUseCase.class);

        client(useCase, USER_AUTHORITIES, identity(JWT_TENANT)).get()
                .uri("/billing/wallet/{userId}/transactions", OTHER_USER)
                .exchange()
                .expectStatus().isForbidden();

        verify(useCase, never()).getTransactionHistory(any(), any());
    }

    @Test
    @DisplayName("GET /me/balance reads the caller's own wallet, scoped to the JWT tenant")
    void myBalance_usesJwtUserAndTenant() {
        IWalletUseCase useCase = mock(IWalletUseCase.class);
        Wallet wallet = Wallet.createNew(JWT_USER, JWT_TENANT, Currency.getInstance("XAF"));
        when(useCase.findWallet(JWT_USER, JWT_TENANT)).thenReturn(Mono.just(wallet));

        client(useCase, USER_AUTHORITIES, identity(JWT_TENANT)).get()
                .uri(b -> b.path("/billing/wallet/me/balance")
                        .queryParam("tenantId", OTHER_TENANT).queryParam("userId", OTHER_USER).build())
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.availableBalance").isEqualTo(0);

        verify(useCase).findWallet(JWT_USER, JWT_TENANT);
        verify(useCase, never()).findWallet(OTHER_USER, OTHER_TENANT);
    }

    @Test
    @DisplayName("GET /me/balance — no wallet → 404, never creates one")
    void myBalance_noWallet_returns404() {
        IWalletUseCase useCase = mock(IWalletUseCase.class);
        when(useCase.findWallet(JWT_USER, JWT_TENANT)).thenReturn(Mono.empty());

        client(useCase, USER_AUTHORITIES, identity(JWT_TENANT)).get()
                .uri("/billing/wallet/me/balance")
                .exchange()
                .expectStatus().isNotFound();

        verify(useCase, never()).getOrCreateWallet(any(), any());
    }

    @Test
    @DisplayName("a JWT without tenant is rejected with 403 on self-service reads")
    void myBalance_withoutTenantInJwt_returns403() {
        IWalletUseCase useCase = mock(IWalletUseCase.class);

        client(useCase, USER_AUTHORITIES, identity(null)).get()
                .uri("/billing/wallet/me/balance")
                .exchange()
                .expectStatus().isForbidden();

        verify(useCase, never()).findWallet(any(), any());
    }

    // ── payments ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /pay always pays as the JWT user; legacy userId/tenantId query params are ignored")
    void pay_usesJwtIdentity_notQueryParams() {
        IWalletUseCase useCase = mock(IWalletUseCase.class);
        when(useCase.initiatePayment(any())).thenReturn(Mono.empty());

        client(useCase, USER_AUTHORITIES, identity(JWT_TENANT)).post()
                .uri(b -> b.path("/billing/wallet/pay")
                        .queryParam("userId", OTHER_USER).queryParam("tenantId", OTHER_TENANT).build())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"invoiceId":"INV-1","amount":5000,"currency":"XAF","channel":"MTN_MOMO",
                         "payerPhone":"+237670000000","callbackUrl":null,"description":"d"}
                        """)
                .exchange();

        ArgumentCaptor<InitiatePaymentCommand> captor = ArgumentCaptor.forClass(InitiatePaymentCommand.class);
        verify(useCase).initiatePayment(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(JWT_USER);
        assertThat(captor.getValue().tenantId()).isEqualTo(JWT_TENANT);
    }

    @Test
    @DisplayName("POST /{userId}/pay (pay on behalf of another owner) is 403 without wallet:admin")
    void payOnBehalf_withUserPermissions_returns403() {
        IWalletUseCase useCase = mock(IWalletUseCase.class);

        client(useCase, USER_AUTHORITIES, identity(JWT_TENANT)).post()
                .uri("/billing/wallet/{userId}/pay", OTHER_USER)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"invoiceId":"INV-1","amount":5000,"currency":"XAF","channel":"MTN_MOMO",
                         "payerPhone":"+237670000000","callbackUrl":null,"description":"d"}
                        """)
                .exchange()
                .expectStatus().isForbidden();

        verify(useCase, never()).initiatePayment(any());
    }

    // ── money movement / state change ──────────────────────────────────────────

    @Test
    @DisplayName("POST /split-revenue is 403 for payment:process holders (was reachable by any client)")
    void splitRevenue_withUserPermissions_returns403() {
        IWalletUseCase useCase = mock(IWalletUseCase.class);

        client(useCase, USER_AUTHORITIES, identity(JWT_TENANT)).post()
                .uri("/billing/wallet/split-revenue")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"missionId":"M-1","totalAmount":5000,"freelancerOrgId":"org-1","subDelivererId":null}
                        """)
                .exchange()
                .expectStatus().isForbidden();

        verify(useCase, never()).splitMissionRevenue(any());
    }

    @Test
    @DisplayName("POST /split-revenue by an admin uses the JWT tenant even if the body carries another tenantId")
    void splitRevenue_asAdmin_ignoresBodyTenant() {
        IWalletUseCase useCase = mock(IWalletUseCase.class);
        when(useCase.splitMissionRevenue(any())).thenReturn(Mono.just(new PaymentSplitResult(
                UUID.randomUUID(), "M-1", BigDecimal.valueOf(5000), "XAF",
                BigDecimal.valueOf(250), BigDecimal.valueOf(4750), BigDecimal.ZERO, null, "COMPLETED")));

        client(useCase, ADMIN_AUTHORITIES, identity(JWT_TENANT)).post()
                .uri("/billing/wallet/split-revenue")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"missionId":"M-1","totalAmount":5000,"freelancerOrgId":"org-1",
                         "tenantId":"%s","subDelivererId":null}
                        """.formatted(OTHER_TENANT))
                .exchange()
                .expectStatus().isOk();

        ArgumentCaptor<SplitMissionRevenueCommand> captor = ArgumentCaptor.forClass(SplitMissionRevenueCommand.class);
        verify(useCase).splitMissionRevenue(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo(JWT_TENANT);
    }

    @Test
    @DisplayName("POST /transfer-commission is 403 without wallet:admin")
    void transferCommission_withUserPermissions_returns403() {
        IWalletUseCase useCase = mock(IWalletUseCase.class);

        client(useCase, USER_AUTHORITIES, identity(JWT_TENANT)).post()
                .uri(b -> b.path("/billing/wallet/transfer-commission")
                        .queryParam("freelancerOrgId", "org-1").queryParam("subDelivererId", "sub-1")
                        .queryParam("amount", 100).queryParam("missionId", "M-1").build())
                .exchange()
                .expectStatus().isForbidden();

        verify(useCase, never()).transferSubDelivererCommission(any());
    }

    @Test
    @DisplayName("PUT freeze / unfreeze and POST freelancer-org are 403 without wallet:admin")
    void stateChangingEndpoints_withUserPermissions_return403() {
        IWalletUseCase useCase = mock(IWalletUseCase.class);
        WebTestClient c = client(useCase, USER_AUTHORITIES, identity(JWT_TENANT));

        c.put().uri("/billing/wallet/{userId}/freeze", OTHER_USER).exchange().expectStatus().isForbidden();
        c.put().uri("/billing/wallet/{userId}/unfreeze", OTHER_USER).exchange().expectStatus().isForbidden();
        c.post().uri(b -> b.path("/billing/wallet/freelancer-org")
                .queryParam("freelancerOrgId", "org-1").build()).exchange().expectStatus().isForbidden();

        verify(useCase, never()).freezeWallet(any(), any());
        verify(useCase, never()).unfreezeWallet(any(), any());
        verify(useCase, never()).createFreelancerOrgWallet(any());
    }

    // ── service layer stays free of RBAC for internal flows (Lot C-9) ──────────

    @Test
    @DisplayName("WalletService.splitMissionRevenue n'a pas @RequirePermission — flux internes non bloqués")
    void service_splitMissionRevenue_hasNoRequirePermissionAnnotation() {
        Method method = Arrays.stream(WalletService.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("splitMissionRevenue"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("splitMissionRevenue not found"));
        assertThat(method.isAnnotationPresent(RequirePermission.class))
                .as("authorization belongs at the web adapter boundary (WalletController)")
                .isFalse();
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static TntUserIdentity identity(UUID tenantId) {
        return new TntUserIdentity(JWT_USER, tenantId, JWT_USER, null, null, Set.of(), false);
    }

    private WebTestClient client(IWalletUseCase useCase, List<String> authorities, TntUserIdentity identity) {
        Authentication auth = new TestingAuthenticationToken("user", "n/a",
                authorities.stream().map(SimpleGrantedAuthority::new).toList());
        auth.setAuthenticated(true);

        TntRoleDefinitionRegistry registry = new TntRoleDefinitionRegistry();
        TntPermissionEvaluator evaluator =
                new TntPermissionEvaluator(mock(ReactivePermissionResolver.class), registry);
        AspectJProxyFactory factory = new AspectJProxyFactory(new WalletController(useCase));
        factory.setProxyTargetClass(true);
        factory.addAspect(new TntPermissionAspect(evaluator));
        WalletController proxied = factory.getProxy();

        return WebTestClient.bindToController(proxied)
                .controllerAdvice(new TntRoleExceptionHandler())
                .argumentResolvers(r -> r.addCustomResolver(new FixedIdentityResolver(identity)))
                .webFilter((exchange, chain) -> chain.filter(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
                .build();
    }

    /** Resolves {@code @CurrentUser} to a fixed identity, ignoring anything in the request. */
    private record FixedIdentityResolver(TntUserIdentity identity) implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(CurrentUser.class);
        }

        @Override
        public Mono<Object> resolveArgument(MethodParameter parameter, BindingContext bindingContext,
                                             ServerWebExchange exchange) {
            return Mono.just(identity);
        }
    }
}
