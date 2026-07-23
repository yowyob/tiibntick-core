package com.yowyob.tiibntick.core.billing.wallet.adapter.in.web;

import com.yowyob.tiibntick.core.billing.wallet.application.port.out.*;
import com.yowyob.tiibntick.core.billing.wallet.application.service.WalletService;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.Wallet;
import com.yowyob.tiibntick.core.roles.adapter.in.web.TntPermissionAspect;
import com.yowyob.tiibntick.core.roles.adapter.in.web.TntRoleExceptionHandler;
import com.yowyob.tiibntick.core.roles.application.port.out.ReactivePermissionResolver;
import com.yowyob.tiibntick.core.roles.application.service.TntPermissionEvaluator;
import com.yowyob.tiibntick.core.roles.application.service.TntRoleDefinitionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression test for Audit n°7 · #6 — same verification as
 * {@code InvoiceControllerPermissionEnforcementTest} (tnt-billing-invoice), applied to
 * {@link WalletService}: proves {@code @RequirePermission(resource = "wallet", action = "write")}
 * on {@code creditWallet} is enforced end-to-end (not just present in source) and that a
 * denied call now surfaces as 403 thanks to {@code TntRoleExceptionHandler} (tnt-roles-core) —
 * previously it would have been an unmapped 500, see that class's javadoc.
 *
 * @author MANFOUO Braun
 */
class WalletControllerPermissionEnforcementTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    @Test
    @DisplayName("credit is rejected with 403 for an authenticated caller lacking wallet:write")
    void creditWallet_withoutPermission_returns403() {
        IWalletRepository walletRepository = mock(IWalletRepository.class);

        var noPermissionAuth = new TestingAuthenticationToken("user", "n/a", List.of());
        noPermissionAuth.setAuthenticated(true);

        WebTestClient client = buildClient(walletRepository, noPermissionAuth);

        client.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/billing/wallet/{userId}/credit")
                        .queryParam("tenantId", TENANT_ID)
                        .build(USER_ID))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"amount": 1000, "currency": "XAF", "referenceId": "ref-1", "description": "top-up"}
                        """)
                .exchange()
                .expectStatus().isForbidden();

        org.mockito.Mockito.verify(walletRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("credit is NOT blocked by the AOP guard once wallet:write is granted")
    void creditWallet_withPermission_isNotBlockedByAop() {
        IWalletRepository walletRepository = mock(IWalletRepository.class);
        Wallet wallet = Wallet.createNew(USER_ID, TENANT_ID, Currency.getInstance("XAF"));
        when(walletRepository.findByUserId(USER_ID, TENANT_ID)).thenReturn(Mono.just(wallet));
        when(walletRepository.save(any())).thenReturn(Mono.just(wallet));
        when(walletRepository.saveTransaction(any())).thenReturn(Mono.empty());

        var withPermissionAuth = new TestingAuthenticationToken(
                "user", "n/a", List.of(new SimpleGrantedAuthority("wallet:write")));
        withPermissionAuth.setAuthenticated(true);

        WebTestClient client = buildClient(walletRepository, withPermissionAuth);

        HttpStatusCode status = client.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/billing/wallet/{userId}/credit")
                        .queryParam("tenantId", TENANT_ID)
                        .build(USER_ID))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"amount": 1000, "currency": "XAF", "referenceId": "ref-1", "description": "top-up"}
                        """)
                .exchange()
                .returnResult(Void.class)
                .getStatus();

        assertThat(status)
                .as("permission granted -> AOP guard must let the call through (whatever the domain outcome)")
                .isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    private WebTestClient buildClient(IWalletRepository walletRepository, Authentication authentication) {
        WalletService realService = new WalletService(
                walletRepository,
                mock(IPaymentIntentRepository.class),
                mock(IIdempotencyStore.class),
                mock(IWalletEventPublisher.class, invocation -> Mono.empty()),
                mock(IWalletNotificationPort.class),
                mock(IPaymentAnchorPort.class),
                mock(IKernelPaymentGatewayPort.class));

        TntRoleDefinitionRegistry registry = new TntRoleDefinitionRegistry();
        TntPermissionEvaluator evaluator = new TntPermissionEvaluator(mock(ReactivePermissionResolver.class), registry);
        TntPermissionAspect aspect = new TntPermissionAspect(evaluator);

        AspectJProxyFactory factory = new AspectJProxyFactory(realService);
        factory.setProxyTargetClass(true);
        factory.addAspect(aspect);
        WalletService proxiedService = factory.getProxy();

        WalletController controller = new WalletController(proxiedService);

        return WebTestClient.bindToController(controller)
                .controllerAdvice(new TntRoleExceptionHandler())
                .webFilter((exchange, chain) -> chain.filter(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)))
                .build();
    }
}
