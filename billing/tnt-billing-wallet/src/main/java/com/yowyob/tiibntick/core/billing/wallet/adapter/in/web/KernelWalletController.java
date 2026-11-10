package com.yowyob.tiibntick.core.billing.wallet.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelChallengeDto;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelReceiveCodeDto;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelWalletDto;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelWalletRechargeDto;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IKernelWalletUseCase;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Self-service endpoints over the caller's own <strong>Kernel</strong> wallet (target model of
 * the Kernel wallet migration). Base path: {@code /billing/kernel-wallet}.
 *
 * <p>There is deliberately no wallet/owner id anywhere in these routes: the Kernel resolves the
 * wallet from the forwarded bearer token, so one user can never address another user's wallet.
 * Money movements (transfer, withdrawal) are two-step: {@code .../challenge} makes the Kernel
 * send a one-time code to the user, then the same operation is repeated with
 * {@code challengeToken} + {@code code}. Kernel 4xx errors (wrong code, insufficient funds…) are
 * relayed with their status and message by {@link WalletExceptionHandler}.
 *
 * @author MANFOUO Braun
 */
@RestController
@RequestMapping("/billing/kernel-wallet")
@RequiredArgsConstructor
public class KernelWalletController {

    private final IKernelWalletUseCase useCase;

    @GetMapping("/me")
    @RequirePermission(resource = "wallet", action = "read")
    public Mono<KernelWalletDto> getMyWallet() {
        return useCase.getMyWallet()
                .switchIfEmpty(Mono.error(new com.yowyob.tiibntick.core.billing.wallet.domain.exception
                        .WalletNotFoundException("No Kernel wallet yet — POST /billing/kernel-wallet/me to create it")));
    }

    @PostMapping("/me")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(resource = "wallet", action = "write")
    public Mono<KernelWalletDto> createMyWallet(@RequestBody(required = false) CreateWalletBody body) {
        return useCase.getOrCreateMyWallet(body != null ? body.label() : null);
    }

    @GetMapping("/me/transactions")
    @RequirePermission(resource = "wallet", action = "read")
    public Flux<JsonNode> getMyTransactions() {
        return useCase.getMyTransactions();
    }

    // ── top-up through a real provider ─────────────────────────────────────

    @PostMapping("/me/recharge")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @RequirePermission(resource = "payment", action = "process")
    public Mono<KernelWalletRechargeDto> recharge(@Valid @RequestBody RechargeBody body) {
        return useCase.recharge(body.amount(), body.currency(), body.provider(), body.method(),
                body.payerReference(), body.idempotencyKey());
    }

    @GetMapping("/me/recharge-orders")
    @RequirePermission(resource = "wallet", action = "read")
    public Flux<KernelWalletRechargeDto> listMyRechargeOrders(@RequestParam(required = false) Integer limit) {
        return useCase.listMyRechargeOrders(limit);
    }

    @GetMapping("/recharge-orders/{orderId}")
    @RequirePermission(resource = "wallet", action = "read")
    public Mono<KernelWalletRechargeDto> getRechargeOrder(@PathVariable UUID orderId) {
        return useCase.getRechargeOrder(orderId);
    }

    @PostMapping("/recharge-orders/{orderId}/refresh")
    @RequirePermission(resource = "wallet", action = "read")
    public Mono<KernelWalletRechargeDto> refreshRechargeOrder(@PathVariable UUID orderId) {
        return useCase.refreshRechargeOrder(orderId);
    }

    // ── receiving and sending money ────────────────────────────────────────

    @PostMapping("/me/receive-codes")
    @RequirePermission(resource = "wallet", action = "write")
    public Mono<KernelReceiveCodeDto> generateReceiveCode() {
        return useCase.generateReceiveCode();
    }

    @PostMapping("/me/transfers/challenge")
    @RequirePermission(resource = "payment", action = "process")
    public Mono<KernelChallengeDto> transferChallenge(@Valid @RequestBody TransferChallengeBody body) {
        return useCase.transferChallenge(body.recipientCode(), body.amount(), body.reference());
    }

    @PostMapping("/me/transfers")
    @RequirePermission(resource = "payment", action = "process")
    public Mono<JsonNode> transfer(@Valid @RequestBody TransferBody body) {
        return useCase.transfer(body.recipientCode(), body.amount(), body.reference(),
                body.challengeToken(), body.code());
    }

    // ── cash-out ───────────────────────────────────────────────────────────

    @GetMapping("/withdrawals/availability")
    @RequirePermission(resource = "wallet", action = "read")
    public Mono<AvailabilityBody> withdrawalsAvailable() {
        return useCase.withdrawalsAvailable().map(AvailabilityBody::new);
    }

    @PostMapping("/me/withdrawals/challenge")
    @RequirePermission(resource = "payment", action = "process")
    public Mono<KernelChallengeDto> withdrawalChallenge(@Valid @RequestBody WithdrawalChallengeBody body) {
        return useCase.withdrawalChallenge(body.amount(), body.phoneNumber());
    }

    @PostMapping("/me/withdrawals")
    @RequirePermission(resource = "payment", action = "process")
    public Mono<JsonNode> withdraw(@Valid @RequestBody WithdrawalBody body) {
        return useCase.withdraw(body.amount(), body.currency(), body.phoneNumber(), body.method(),
                body.recipientName(), body.challengeToken(), body.code());
    }

    // ── request / response bodies ──────────────────────────────────────────

    public record CreateWalletBody(String label) { }

    public record RechargeBody(@NotNull @Positive BigDecimal amount, @NotBlank String currency,
                               @NotBlank String provider, @NotBlank String method,
                               String payerReference, @NotBlank String idempotencyKey) { }

    public record TransferChallengeBody(@NotBlank String recipientCode, @NotNull @Positive BigDecimal amount,
                                        String reference) { }

    public record TransferBody(@NotBlank String recipientCode, @NotNull @Positive BigDecimal amount,
                               String reference, @NotBlank String challengeToken, @NotBlank String code) { }

    public record WithdrawalChallengeBody(@NotNull @Positive BigDecimal amount, @NotBlank String phoneNumber) { }

    public record WithdrawalBody(@NotNull @Positive BigDecimal amount, @NotBlank String currency,
                                 @NotBlank String phoneNumber, @NotBlank String method, String recipientName,
                                 @NotBlank String challengeToken, @NotBlank String code) { }

    public record AvailabilityBody(boolean available) { }
}
