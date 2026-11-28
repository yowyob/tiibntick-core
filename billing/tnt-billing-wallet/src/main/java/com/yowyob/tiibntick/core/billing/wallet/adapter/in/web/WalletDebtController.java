package com.yowyob.tiibntick.core.billing.wallet.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IDebtUseCase;
import com.yowyob.tiibntick.core.billing.wallet.domain.exception.WalletSettlementException;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletDebt;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

/**
 * Debts towards the compensation fund (lot L5, QO-22 / QO-26). The debtor — a courier (JWT subject) or the
 * organisation he acts for (relay, agency) — lists his debts and repays one with a Kernel code
 * ({@code /repay/challenge} then {@code /repay}); the administrator ({@code wallet:admin}) lists them, names the
 * debtor of a relay/agency debt, retries a recovery that could not be opened, or writes a debt off.
 */
@RestController
@RequestMapping("/api/v1/billing/debts")
@RequiredArgsConstructor
public class WalletDebtController {

    private final IDebtUseCase debts;

    public record DebtView(UUID id, String debtorType, UUID debtorId, String reason, String reference,
                           UUID deliveryId, BigDecimal amount, String currency, String status, boolean recoveryOpen,
                           String note, Instant createdAt, Instant updatedAt) {
        static DebtView of(WalletDebt d) {
            return new DebtView(d.id(), d.debtorType().name(), d.debtorId(), d.reason().name(), d.reference(),
                    d.deliveryId(), d.amount(), d.currency(), d.status().name(), d.kernelCollectionId() != null,
                    d.note(), d.createdAt(), d.updatedAt());
        }
    }

    public record ChallengeView(UUID debtId, String challengeToken, Long expiresInSeconds, String codePreview) {
    }

    public record RepayRequest(@NotBlank String challengeToken, @NotBlank String code) {
    }

    public record AssignRequest(@NotNull UUID debtorId) {
    }

    public record WriteOffRequest(String note) {
    }

    // ── debtor ──────────────────────────────────────────────────────────────

    @GetMapping("/mine")
    @RequirePermission(resource = "wallet", action = "read")
    public Flux<DebtView> mine(@CurrentUser(required = false) TntSecurityContext ctx) {
        return caller(ctx).flatMapMany(c -> debts.mine(c, ctx.organizationId())).map(DebtView::of);
    }

    @PostMapping("/{debtId}/repay/challenge")
    @RequirePermission(resource = "payment", action = "process")
    public Mono<ChallengeView> repayChallenge(@PathVariable UUID debtId,
                                              @CurrentUser(required = false) TntSecurityContext ctx) {
        return caller(ctx).flatMap(c -> debts.repayChallenge(debtId, c, ctx.organizationId()))
                .map(ch -> new ChallengeView(debtId, ch.challengeToken(), ch.expiresInSeconds(), ch.codePreview()));
    }

    @PostMapping("/{debtId}/repay")
    @RequirePermission(resource = "payment", action = "process")
    public Mono<DebtView> repay(@PathVariable UUID debtId, @Valid @RequestBody RepayRequest body,
                                @CurrentUser(required = false) TntSecurityContext ctx) {
        return caller(ctx).flatMap(c -> debts.repay(debtId, c, ctx.organizationId(), body.challengeToken(),
                body.code())).map(DebtView::of);
    }

    // ── administrator ───────────────────────────────────────────────────────

    @GetMapping
    @RequirePermission(resource = "wallet", action = "admin")
    public Flux<DebtView> list(@RequestParam(required = false) WalletDebt.Status status,
                               @RequestParam(defaultValue = "100") int limit) {
        return debts.list(status, limit).map(DebtView::of);
    }

    @PostMapping("/{debtId}/assign")
    @RequirePermission(resource = "wallet", action = "admin")
    public Mono<DebtView> assign(@PathVariable UUID debtId, @Valid @RequestBody AssignRequest body) {
        return debts.assign(debtId, body.debtorId()).map(DebtView::of);
    }

    @PostMapping("/{debtId}/recovery")
    @RequirePermission(resource = "wallet", action = "admin")
    public Mono<DebtView> retryRecovery(@PathVariable UUID debtId) {
        return debts.retryRecovery(debtId).map(DebtView::of);
    }

    @PostMapping("/{debtId}/write-off")
    @RequirePermission(resource = "wallet", action = "admin")
    public Mono<DebtView> writeOff(@PathVariable UUID debtId, @RequestBody(required = false) WriteOffRequest body) {
        return debts.writeOff(debtId, body != null ? body.note() : null).map(DebtView::of);
    }

    private static Mono<UUID> caller(TntSecurityContext ctx) {
        return ctx == null || ctx.userId() == null
                ? Mono.error(new AccessDeniedException("Authentication required")) : Mono.just(ctx.userId());
    }

    @ExceptionHandler(WalletSettlementException.class)
    public Mono<ProblemDetail> onSettlement(WalletSettlementException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getHttpStatus());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status != null ? status : HttpStatus.CONFLICT,
                ex.getMessage());
        pd.setType(URI.create("urn:tiibntick:payment:" + ex.getCode().toLowerCase()));
        pd.setTitle("Debt operation refused");
        pd.setProperty("code", ex.getCode());
        return Mono.just(pd);
    }
}
