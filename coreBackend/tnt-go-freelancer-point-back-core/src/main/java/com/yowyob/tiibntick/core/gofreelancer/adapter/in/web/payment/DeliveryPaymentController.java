package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.payment;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.dto.DeliveryPaymentDtos.ConfirmPaymentRequest;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.dto.DeliveryPaymentDtos.PaymentChallengeResponse;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.dto.DeliveryPaymentDtos.PaymentStatusResponse;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.dto.DeliveryPaymentDtos.ReceiveCodeResponse;
import com.yowyob.tiibntick.core.gofreelancer.application.exception.DeliveryPaymentException;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.DeliveryPaymentUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.UUID;

/**
 * Pays a delivery from the client's Kernel wallet to the delivery person's Kernel wallet.
 * See {@link DeliveryPaymentUseCase} for the three-step flow. The identity is the JWT subject
 * ({@code ctx.userId()} — the Kernel wallet owner); nothing identifying comes from the body.
 */
@RestController
@RequestMapping("/api/v1/deliveries/{deliveryId}/payment")
@RequiredArgsConstructor
public class DeliveryPaymentController {

    private final DeliveryPaymentUseCase useCase;

    /** Same Kernel-error relay (status + Kernel message) as the /billing/kernel-wallet API. */
    private final com.yowyob.tiibntick.core.billing.wallet.adapter.in.web.WalletExceptionHandler kernelErrors =
            new com.yowyob.tiibntick.core.billing.wallet.adapter.in.web.WalletExceptionHandler();

    /** Delivery person: generate (with their own token) and store the temporary receive code. */
    @PostMapping("/receive-code")
    @RequirePermission(resource = "payment", action = "process")
    public Mono<ReceiveCodeResponse> generateReceiveCode(@PathVariable UUID deliveryId,
                                                         @CurrentUser(required = false) TntSecurityContext ctx) {
        return withCaller(ctx, userId -> useCase.generateReceiveCode(deliveryId, userId));
    }

    /** Client: ask the Kernel for a transfer challenge (the Kernel sends the confirmation code). */
    @PostMapping("/challenge")
    @RequirePermission(resource = "payment", action = "process")
    public Mono<PaymentChallengeResponse> startPayment(@PathVariable UUID deliveryId,
                                                       @CurrentUser(required = false) TntSecurityContext ctx) {
        return withCaller(ctx, userId -> useCase.startPayment(deliveryId, userId));
    }

    /** Client: confirm the transfer with the challenge token and the one-time code. */
    @PostMapping
    @RequirePermission(resource = "payment", action = "process")
    public Mono<PaymentStatusResponse> confirmPayment(@PathVariable UUID deliveryId,
                                                      @Valid @RequestBody ConfirmPaymentRequest body,
                                                      @CurrentUser(required = false) TntSecurityContext ctx) {
        return withCaller(ctx, userId ->
                useCase.confirmPayment(deliveryId, userId, body.challengeToken(), body.code()));
    }

    /** Client or assigned delivery person: settlement status of the delivery. */
    @GetMapping
    @RequirePermission(resource = "wallet", action = "read")
    public Mono<PaymentStatusResponse> status(@PathVariable UUID deliveryId,
                                              @CurrentUser(required = false) TntSecurityContext ctx) {
        return withCaller(ctx, userId -> useCase.getPaymentStatus(deliveryId, userId));
    }

    private static <T> Mono<T> withCaller(TntSecurityContext ctx,
                                          java.util.function.Function<UUID, Mono<T>> action) {
        if (ctx == null || ctx.userId() == null) {
            return Mono.error(new org.springframework.security.access.AccessDeniedException(
                    "Authentication required"));
        }
        return action.apply(ctx.userId());
    }

    @ExceptionHandler(DeliveryPaymentException.class)
    public Mono<ProblemDetail> onPaymentError(DeliveryPaymentException ex) {
        HttpStatus status = switch (ex.getReason()) {
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT, RECEIVE_CODE_MISSING -> HttpStatus.CONFLICT;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        pd.setType(URI.create("urn:tiibntick:gofp:delivery-payment:" + ex.getReason().name().toLowerCase()));
        pd.setTitle("Delivery payment refused");
        return Mono.just(pd);
    }

    /** Wrong confirmation code, insufficient funds, expired challenge… relayed with the Kernel's status. */
    @ExceptionHandler(org.springframework.web.reactive.function.client.WebClientResponseException.class)
    public Mono<ProblemDetail> onKernelError(
            org.springframework.web.reactive.function.client.WebClientResponseException ex) {
        return kernelErrors.handleKernelError(ex);
    }

    /** Spendable balance is reduced by funds held for an open incident or dispute. */
    @ExceptionHandler(com.yowyob.tiibntick.core.billing.wallet.domain.exception.WalletFrozenException.class)
    public Mono<ProblemDetail> onFrozen(
            com.yowyob.tiibntick.core.billing.wallet.domain.exception.WalletFrozenException ex) {
        return kernelErrors.handleWalletFrozen(ex);
    }

    @ExceptionHandler(com.yowyob.tiibntick.core.billing.wallet.domain.exception.WalletNotFoundException.class)
    public Mono<ProblemDetail> onWalletMissing(
            com.yowyob.tiibntick.core.billing.wallet.domain.exception.WalletNotFoundException ex) {
        return kernelErrors.handleWalletNotFound(ex);
    }
}
