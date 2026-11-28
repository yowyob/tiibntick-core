package com.yowyob.tiibntick.core.billing.wallet.adapter.out.kernel;

import com.yowyob.tiibntick.common.kernel.KernelResponses;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IKernelSettlementPort;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.CashCollection;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.CashCollectionRequest;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.Challenge;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.Compensation;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.CompensationRequest;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.Escrow;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.EscrowHoldRequest;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.Fund;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelWalletDto;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * {@link IKernelSettlementPort} over HTTP (lot L5, spec §4). {@code user} forwards the caller's bearer
 * ({@code kernelPaymentWebClient}); {@code system} carries the Core's service-account token
 * ({@code kernelSettlementWebClient}, never retried by the transport). The {@code {success, data}} envelope is unwrapped; HTTP errors propagate
 * except a 404 on a lookup, which is an empty result.
 */
public class KernelSettlementAdapter implements IKernelSettlementPort {

    private static final String ESCROWS = "/api/payments/escrows";
    private static final String CASH = "/api/payments/cash-collections";

    private final WebClient user;
    private final WebClient system;

    public KernelSettlementAdapter(WebClient user, WebClient system) {
        this.user = user;
        this.system = system;
    }

    @Override
    public Mono<Challenge> escrowChallenge(EscrowHoldRequest request) {
        return post(user, ESCROWS + "/challenge", request, Challenge.class);
    }

    @Override
    public Mono<Escrow> holdEscrow(EscrowHoldRequest request) {
        return post(user, ESCROWS, request, Escrow.class);
    }

    @Override
    public Mono<Escrow> findEscrow(String subjectType, UUID subjectId) {
        return lookup(system.get().uri(b -> b.path(ESCROWS).queryParam("subjectType", subjectType)
                .queryParam("subjectId", subjectId).build()).retrieve(), Escrow.class);
    }

    @Override
    public Mono<Escrow> getEscrow(UUID escrowId) {
        return lookup(system.get().uri(ESCROWS + "/{id}", escrowId).retrieve(), Escrow.class);
    }

    @Override
    public Mono<Escrow> releaseEscrow(UUID escrowId) {
        return KernelResponses.unwrapObjectOrPropagate(
                system.post().uri(ESCROWS + "/{id}/release", escrowId).retrieve(), Escrow.class);
    }

    @Override
    public Mono<Escrow> refundEscrow(UUID escrowId, String reason) {
        return post(system, ESCROWS + "/" + escrowId + "/refund", reason(reason), Escrow.class);
    }

    @Override
    public Mono<Escrow> freezeEscrow(UUID escrowId, String reason) {
        return post(system, ESCROWS + "/" + escrowId + "/freeze", reason(reason), Escrow.class);
    }

    @Override
    public Mono<Escrow> unfreezeEscrow(UUID escrowId, String reason) {
        return post(system, ESCROWS + "/" + escrowId + "/unfreeze", reason(reason), Escrow.class);
    }

    @Override
    public Mono<CashCollection> openCashCollection(CashCollectionRequest request) {
        return post(system, CASH, request, CashCollection.class);
    }

    @Override
    public Mono<CashCollection> getCashCollection(UUID collectionId) {
        return lookup(system.get().uri(CASH + "/{id}", collectionId).retrieve(), CashCollection.class);
    }

    @Override
    public Mono<CashCollection> markCollected(UUID collectionId, BigDecimal amount) {
        return post(system, CASH + "/" + collectionId + "/collected", Map.of("amount", amount), CashCollection.class);
    }

    @Override
    public Mono<CashCollection> cancelCashCollection(UUID collectionId) {
        return KernelResponses.unwrapObjectOrPropagate(
                system.post().uri(CASH + "/{id}/cancel", collectionId).retrieve(), CashCollection.class);
    }

    @Override
    public Mono<Challenge> remitChallenge(UUID collectionId) {
        return KernelResponses.unwrapObjectOrPropagate(
                user.post().uri(CASH + "/{id}/remit/challenge", collectionId).retrieve(), Challenge.class);
    }

    @Override
    public Mono<CashCollection> remit(UUID collectionId, String challengeToken, String code) {
        return post(user, CASH + "/" + collectionId + "/remit",
                Map.of("challengeToken", challengeToken, "code", code), CashCollection.class);
    }

    @Override
    public Mono<Fund> compensationFund() {
        return KernelResponses.unwrapObjectOrPropagate(
                system.get().uri("/api/payments/compensation-fund").retrieve(), Fund.class);
    }

    @Override
    public Mono<Compensation> compensate(CompensationRequest request) {
        return post(system, "/api/payments/compensations", request, Compensation.class);
    }

    @Override
    public Mono<KernelWalletDto> walletOfOwner(UUID ownerId) {
        return lookup(system.get().uri("/api/payments/wallets/owner/{ownerId}", ownerId).retrieve(),
                KernelWalletDto.class);
    }

    private static Map<String, String> reason(String reason) {
        return reason == null ? Map.of() : Map.of("reason", reason);
    }

    private static <T> Mono<T> post(WebClient client, String path, Object body, Class<T> type) {
        return KernelResponses.unwrapObjectOrPropagate(client.post().uri(path).bodyValue(body).retrieve(), type);
    }

    private static <T> Mono<T> lookup(WebClient.ResponseSpec spec, Class<T> type) {
        return KernelResponses.unwrapObjectOrPropagate(spec, type)
                .onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.empty());
    }
}
