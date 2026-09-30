package com.yowyob.tiibntick.core.marketback.adapter.in.web;

import com.yowyob.tiibntick.core.marketback.application.port.in.IManageMarketOrderUseCase;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.ProcessPaymentCommand;
import com.yowyob.tiibntick.core.marketback.domain.model.PaymentMethod;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import com.yowyob.tiibntick.core.roles.adapter.in.web.TntPermissionAspect;
import com.yowyob.tiibntick.core.roles.application.port.out.ReactivePermissionResolver;
import com.yowyob.tiibntick.core.roles.application.service.TntPermissionEvaluator;
import com.yowyob.tiibntick.core.roles.application.service.TntRoleDefinitionRegistry;
import com.yowyob.tiibntick.core.roles.domain.exception.TntRoleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.test.StepVerifier;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Non-regression architectural test (Lot C-10) — proves that
 * {@code POST /api/v1/platform/market/orders/{id}/payment} requires
 * {@code payment:process} permission.
 *
 * <p>Root cause: Lot C-9 removed {@code @RequirePermission(payment:process)} from
 * {@code WalletService.creditCommission} without adding the guard on
 * {@code MarketOrderController.processPayment}, leaving the path
 * MarketOrderController → MarketOrderApplicationService → walletUseCase.creditCommission
 * accessible to any authenticated caller.
 *
 * <p>Two tests:
 * <ol>
 *   <li>Structural: verify the annotation is present on the method (reflection).</li>
 *   <li>Functional: AOP proxy + StepVerifier — verify a caller lacking payment:process gets
 *       TntRoleException before the method body runs (before @CurrentUser is resolved).</li>
 * </ol>
 */
@DisplayName("MarketOrderController — processPayment requires payment:process")
class MarketOrderControllerPermissionGuardTest {

    @Test
    @DisplayName("processPayment has @RequirePermission(resource=payment, action=process)")
    void processPayment_hasRequirePermissionAnnotation() {
        Method method = Arrays.stream(MarketOrderController.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("processPayment"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("processPayment method not found"));

        RequirePermission perm = method.getAnnotation(RequirePermission.class);
        assertThat(perm)
                .as("processPayment must carry @RequirePermission — without it, any authenticated "
                        + "caller can trigger walletUseCase.creditCommission")
                .isNotNull();
        assertThat(perm.resource()).isEqualTo("payment");
        assertThat(perm.action()).isEqualTo("process");
    }

    @Test
    @DisplayName("processPayment throws TntRoleException for authenticated caller without payment:process")
    void processPayment_withoutPaymentProcessPermission_throwsTntRoleException() {
        IManageMarketOrderUseCase orderUseCase = mock(IManageMarketOrderUseCase.class);

        TntRoleDefinitionRegistry registry = new TntRoleDefinitionRegistry();
        TntPermissionEvaluator evaluator = new TntPermissionEvaluator(
                mock(ReactivePermissionResolver.class), registry);
        TntPermissionAspect aspect = new TntPermissionAspect(evaluator);

        MarketOrderController controller = new MarketOrderController(orderUseCase);
        AspectJProxyFactory factory = new AspectJProxyFactory(controller);
        factory.setProxyTargetClass(true);
        factory.addAspect(aspect);
        MarketOrderController proxiedController = factory.getProxy();

        // Authenticated but missing payment:process authority
        var noPaymentAuth = new TestingAuthenticationToken("user", "n/a", List.of());
        noPaymentAuth.setAuthenticated(true);

        ProcessPaymentCommand cmd = new ProcessPaymentCommand(
                PaymentMethod.WALLET, "TXN-E2E-001", 5000L, null);

        // AOP intercepts before the method body runs — @CurrentUser null never reached
        StepVerifier.create(
                proxiedController.processPayment(UUID.randomUUID(), cmd, null)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(noPaymentAuth))
        )
        .expectErrorMatches(e -> e instanceof TntRoleException
                && e.getMessage().contains("payment:process"))
        .verify();
    }
}
