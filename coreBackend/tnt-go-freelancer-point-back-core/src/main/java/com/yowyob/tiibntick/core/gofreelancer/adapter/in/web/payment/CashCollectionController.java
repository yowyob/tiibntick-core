package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.payment;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.ICashCollectionUseCase;
import com.yowyob.tiibntick.core.billing.wallet.domain.exception.WalletSettlementException;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.CashCollectionMirror;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.dto.DeliveryPaymentDtos.CashCollectionView;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.dto.DeliveryPaymentDtos.ConfirmPaymentRequest;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.dto.DeliveryPaymentDtos.PaymentChallengeResponse;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Cash collected by the courier (lot L5, spec §4.2 K4): the courier remits the platform's part from his Kernel
 * wallet with a code ({@code /remit/challenge} then {@code /remit}), and lists what he still owes. Only the courier of
 * the collection (JWT subject) acts on it.
 */
@RestController
@RequestMapping("/api/v1/gofp/cash-collections")
@RequiredArgsConstructor
public class CashCollectionController {

    private final ICashCollectionUseCase cash;

    @GetMapping("/mine")
    @RequirePermission(resource = "wallet", action = "read")
    public Flux<CashCollectionView> mine(@CurrentUser(required = false) TntSecurityContext ctx) {
        return SelectionPaymentController.caller(ctx).flatMapMany(cash::outstanding)
                .map(CashCollectionController::view);
    }

    @PostMapping("/{deliveryId}/remit/challenge")
    @RequirePermission(resource = "payment", action = "process")
    public Mono<PaymentChallengeResponse> remitChallenge(@PathVariable UUID deliveryId,
                                                         @CurrentUser(required = false) TntSecurityContext ctx) {
        return SelectionPaymentController.caller(ctx)
                .flatMap(user -> cash.remitChallenge(deliveryId, user))
                .map(ch -> new PaymentChallengeResponse(deliveryId, null, null, ch.challengeToken(),
                        ch.expiresInSeconds(), ch.codePreview()));
    }

    @PostMapping("/{deliveryId}/remit")
    @RequirePermission(resource = "payment", action = "process")
    public Mono<CashCollectionView> remit(@PathVariable UUID deliveryId,
                                          @Valid @RequestBody ConfirmPaymentRequest body,
                                          @CurrentUser(required = false) TntSecurityContext ctx) {
        return SelectionPaymentController.caller(ctx)
                .flatMap(user -> cash.remit(deliveryId, user, body.challengeToken(), body.code()))
                .map(CashCollectionController::view);
    }

    static CashCollectionView view(CashCollectionMirror c) {
        return new CashCollectionView(c.deliveryId(), c.status().name(), c.expectedAmount(), c.collectedAmount(),
                c.amountToRemit(), c.currency());
    }

    @ExceptionHandler(WalletSettlementException.class)
    public Mono<ProblemDetail> onSettlement(WalletSettlementException ex) {
        return Mono.just(PaymentProblems.of(ex));
    }

    @ExceptionHandler(org.springframework.web.reactive.function.client.WebClientResponseException.class)
    public Mono<ProblemDetail> onKernelError(
            org.springframework.web.reactive.function.client.WebClientResponseException ex) {
        return new com.yowyob.tiibntick.core.billing.wallet.adapter.in.web.WalletExceptionHandler()
                .handleKernelError(ex);
    }
}
