package com.yowyob.tiibntick.core.billing.wallet.application.port.in;

import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.*;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.PaymentSplitResult;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.Money;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.PaymentIntent;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.Wallet;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletTransaction;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * IWalletUseCase — primary port (inbound) for all wallet operations.
 * Implemented by WalletService and PaymentIntentService.
 *
 * @author MANFOUO Braun
 */
public interface IWalletUseCase {

    /**
     * Returns the wallet for a given user, creating it if it does not exist.
     * Default currency is XAF for Cameroon tenants.
     *
     * @param userId   user identifier
     * @param tenantId tenant context
     * @return existing or newly created Wallet
     */
    Mono<Wallet> getOrCreateWallet(UUID userId, UUID tenantId);

    /**
     * Returns the current available balance.
     *
     * @param userId   user identifier
     * @param tenantId tenant context
     * @return available Money balance
     */
    Mono<Money> getBalance(UUID userId, UUID tenantId);

    /**
     * Credits the wallet (top-up, refund credit, admin credit).
     *
     * @param command credit command
     * @return created WalletTransaction
     */
    Mono<WalletTransaction> creditWallet(CreditWalletCommand command);

    /**
     * Debits the wallet synchronously (wallet-to-wallet transfer, cash).
     *
     * @param command debit command
     * @return created WalletTransaction
     */
    Mono<WalletTransaction> debitWallet(DebitWalletCommand command);

    /**
     * Credits a commission amount to a deliverer's wallet.
     *
     * @param command commission credit command
     * @return created WalletTransaction
     */
    Mono<WalletTransaction> creditCommission(CreditCommissionCommand command);

    /**
     * Initiates an asynchronous payment via Mobile Money or Stripe.
     * Creates a PaymentIntent and sends the USSD push or payment request.
     *
     * @param command payment initiation command
     * @return created PaymentIntent (status: PENDING)
     */
    Mono<PaymentIntent> initiatePayment(InitiatePaymentCommand command);

    /**
     * Confirms or fails a payment based on the provider's webhook callback.
     * Called by the webhook adapter after HMAC signature verification.
     *
     * @param command confirm/fail command
     * @return updated PaymentIntent
     */
    Mono<PaymentIntent> handlePaymentCallback(ConfirmPaymentCommand command);

    /**
     * Refunds a confirmed payment.
     *
     * @param command refund command
     * @return created refund WalletTransaction
     */
    Mono<WalletTransaction> refundPayment(RefundPaymentCommand command);

    /**
     * Returns the transaction history for a user's wallet, most recent first.
     *
     * @param userId   user identifier
     * @param tenantId tenant context
     * @return stream of WalletTransactions
     */
    Flux<WalletTransaction> getTransactionHistory(UUID userId, UUID tenantId);

    /**
     * Freezes the wallet for the given user.
     *
     * @param userId   user identifier
     * @param tenantId tenant context
     * @return updated Wallet
     */
    Mono<Wallet> freezeWallet(UUID userId, UUID tenantId);

    /**
     * Unfreezes the wallet for the given user.
     *
     * @param userId   user identifier
     * @param tenantId tenant context
     * @return updated Wallet
     */
    Mono<Wallet> unfreezeWallet(UUID userId, UUID tenantId);

    // ── : FreelancerOrg wallet operations ────────────────────────────

    /**
     * Creates a dedicated wallet for a FreelancerOrganization.
     *
     * <p>The org wallet receives mission revenue (orgRevenue portion of PaymentSplit).
     * It is separate from the OWNER actor's personal wallet.
     *
     * @param command creation command with orgId and tenant
     * @return the newly created org Wallet
     */
    Mono<Wallet> createFreelancerOrgWallet(CreateFreelancerOrgWalletCommand command);

    /**
     * Splits mission revenue between platform, FreelancerOrg, and optional sub-deliverer.
     *
     * <p>Execution sequence:
     * <ol>
     *   <li>Compute split amounts (platform + org + sub).</li>
     *   <li>Credit org wallet with orgRevenue.</li>
     *   <li>If subDelivererId present: credit sub-deliverer personal wallet with commission.</li>
     *   <li>Persist PaymentSplit record (EXECUTED).</li>
     * </ol>
     *
     * @param command split command
     * @return summary of the executed split
     */
    Mono<PaymentSplitResult> splitMissionRevenue(SplitMissionRevenueCommand command);

    /**
     * Transfers the sub-deliverer commission from the FreelancerOrg wallet
     * to the sub-deliverer's personal wallet.
     *
     * <p>Called when the commission transfer was deferred (e.g. pending reconciliation).
     *
     * @param command transfer command
     * @return the WalletTransaction recording the transfer
     */
    Mono<WalletTransaction> transferSubDelivererCommission(TransferSubDelivererCommissionCommand command);
}
