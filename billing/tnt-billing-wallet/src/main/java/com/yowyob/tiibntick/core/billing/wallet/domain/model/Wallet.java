package com.yowyob.tiibntick.core.billing.wallet.domain.model;

import com.yowyob.tiibntick.core.billing.wallet.domain.enums.PaymentChannel;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.TransactionStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.TransactionType;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.WalletOwnerType;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.WalletStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.event.WalletCredited;
import com.yowyob.tiibntick.core.billing.wallet.domain.event.WalletDebited;
import com.yowyob.tiibntick.core.billing.wallet.domain.exception.InsufficientBalanceException;
import com.yowyob.tiibntick.core.billing.wallet.domain.exception.WalletFrozenException;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

/**
 * Wallet — aggregate root for the billing wallet bounded context.
 * Manages the available balance, reserved balance, and transaction history
 * for a single user within a specific tenant.
 *
 * @author MANFOUO Braun
 */
@Getter
@Builder
public class Wallet {

    private final WalletId id;
    private final UUID userId;
    private final UUID tenantId;

    // ── : Multi-owner wallet support ─────────────────────────────────

    /**
     * Type of entity that owns this wallet.
     * ACTOR = individual user, FREELANCER_ORG = org wallet, AGENCY = agency wallet.
     * Default: ACTOR (for backward compatibility).
     */
    @Builder.Default
    private final WalletOwnerType ownerType = WalletOwnerType.ACTOR;

    /**
     * UUID of the owning entity.
     * For ACTOR: the user's UUID (= userId). For FREELANCER_ORG / AGENCY: the org UUID.
     * References tnt-organization-core UUID — pure integration key (no join).
     */
    private final String ownerId;

    /** Available liquid balance — excludes reserved amounts. */
    private Money balance;
    /** Amount held in reserve (pre-authorization). */
    private Money reservedBalance;
    private final Currency currency;
    private WalletStatus status;
    @Builder.Default
    private final List<WalletCredited> pendingCreditEvents = new ArrayList<>();
    @Builder.Default
    private final List<WalletDebited> pendingDebitEvents = new ArrayList<>();
    @Builder.Default
    private final List<WalletTransaction> transactions = new ArrayList<>();
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private long version;

    /**
     * Factory method — creates a new ACTIVE wallet with zero balance.
     *
     * @param userId   owner identifier
     * @param tenantId tenant context
     * @param currency wallet currency (e.g. XAF)
     * @return new Wallet aggregate
     */
    public static Wallet createNew(UUID userId, UUID tenantId, Currency currency) {
        Money zero = Money.zero(currency);
        return Wallet.builder()
                .id(WalletId.generate())
                .userId(userId)
                .tenantId(tenantId)
                .ownerType(WalletOwnerType.ACTOR)
                .ownerId(userId != null ? userId.toString() : null)
                .balance(zero)
                .reservedBalance(zero)
                .currency(currency)
                .status(WalletStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .version(0L)
                .build();
    }

    /**
     * Creates a new ACTIVE wallet for a FreelancerOrg or Agency ().
     *
     * @param ownerType  FREELANCER_ORG or AGENCY
     * @param ownerId    UUID of the org (from tnt-organization-core)
     * @param tenantId   tenant context
     * @param currency   wallet currency (XAF)
     * @return new Wallet aggregate for the org
     */
    public static Wallet createForOrg(WalletOwnerType ownerType, String ownerId,
                                       UUID tenantId, Currency currency) {
        if (ownerType == WalletOwnerType.ACTOR) {
            throw new IllegalArgumentException("Use createNew() for ACTOR wallets");
        }
        Money zero = Money.zero(currency);
        return Wallet.builder()
                .id(WalletId.generate())
                .userId(null)   // No individual user for org wallets
                .tenantId(tenantId)
                .ownerType(ownerType)
                .ownerId(ownerId)
                .balance(zero)
                .reservedBalance(zero)
                .currency(currency)
                .status(WalletStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .version(0L)
                .build();
    }

    /**
     * Credits the wallet — increases available balance.
     * Allowed for ACTIVE wallets only.
     *
     * @param amount      positive money to credit
     * @param referenceId business reference (e.g. commission ID, refund ID)
     * @param description human-readable description
     * @return the created WalletTransaction
     */
    public WalletTransaction credit(Money amount, String referenceId, String description) {
        requireActive();
        requirePositiveAmount(amount);
        this.balance = this.balance.add(amount);
        this.updatedAt = LocalDateTime.now();

        WalletTransaction tx = WalletTransaction.builder()
                .id(TransactionId.generate())
                .walletId(this.id)
                .type(TransactionType.CREDIT)
                .amount(amount)
                .balanceAfter(this.balance)
                .channel(PaymentChannel.WALLET)
                .referenceId(referenceId)
                .status(TransactionStatus.CONFIRMED)
                .description(description)
                .idempotencyKey("CREDIT-" + referenceId)
                .createdAt(LocalDateTime.now())
                .processedAt(LocalDateTime.now())
                .build();
        this.transactions.add(tx);

        this.pendingCreditEvents.add(new WalletCredited(
                this.id.value(), this.userId, this.tenantId, amount, this.balance, referenceId));
        return tx;
    }

    /**
     * Credits a commission amount from a completed mission.
     *
     * @param amount      commission amount
     * @param missionId   source mission identifier
     * @return the created WalletTransaction
     */
    public WalletTransaction creditCommission(Money amount, String missionId) {
        requireActive();
        requirePositiveAmount(amount);
        this.balance = this.balance.add(amount);
        this.updatedAt = LocalDateTime.now();

        WalletTransaction tx = WalletTransaction.builder()
                .id(TransactionId.generate())
                .walletId(this.id)
                .type(TransactionType.COMMISSION_CREDIT)
                .amount(amount)
                .balanceAfter(this.balance)
                .channel(PaymentChannel.WALLET)
                .referenceId(missionId)
                .status(TransactionStatus.CONFIRMED)
                .description("Commission for mission " + missionId)
                .idempotencyKey("COMMISSION-" + missionId)
                .createdAt(LocalDateTime.now())
                .processedAt(LocalDateTime.now())
                .build();
        this.transactions.add(tx);

        this.pendingCreditEvents.add(new WalletCredited(
                this.id.value(), this.userId, this.tenantId, amount, this.balance, missionId));
        return tx;
    }

    /**
     * Debits the wallet synchronously (e.g. cash payment, wallet-to-wallet).
     * Requires sufficient available balance.
     *
     * @param amount      positive money to debit
     * @param referenceId business reference (e.g. invoiceId)
     * @param channel     payment channel used
     * @param description human-readable description
     * @param idempotencyKey idempotency key to prevent double-debit
     * @return the created (CONFIRMED) WalletTransaction
     */
    public WalletTransaction debit(Money amount, String referenceId, PaymentChannel channel,
                                    String description, String idempotencyKey) {
        requireActive();
        requirePositiveAmount(amount);
        requireSufficientBalance(amount);

        this.balance = this.balance.subtract(amount);
        this.updatedAt = LocalDateTime.now();

        WalletTransaction tx = WalletTransaction.builder()
                .id(TransactionId.generate())
                .walletId(this.id)
                .type(TransactionType.DEBIT)
                .amount(amount)
                .balanceAfter(this.balance)
                .channel(channel)
                .referenceId(referenceId)
                .status(TransactionStatus.CONFIRMED)
                .description(description)
                .idempotencyKey(idempotencyKey)
                .createdAt(LocalDateTime.now())
                .processedAt(LocalDateTime.now())
                .build();
        this.transactions.add(tx);

        this.pendingDebitEvents.add(new WalletDebited(
                this.id.value(), this.userId, this.tenantId, amount, this.balance, referenceId));
        return tx;
    }

    /**
     * Creates a PENDING debit transaction for asynchronous payment (e.g. MoMo USSD).
     * Balance is NOT yet decreased — it is frozen only if a reservation was made first.
     *
     * @param amount         positive money to eventually debit
     * @param referenceId    business reference (e.g. invoiceId)
     * @param channel        MoMo or Stripe channel
     * @param description    human-readable description
     * @param idempotencyKey idempotency key for the payment
     * @return the created PENDING WalletTransaction
     */
    public WalletTransaction createPendingDebit(Money amount, String referenceId, PaymentChannel channel,
                                                 String description, String idempotencyKey) {
        requireActive();
        requirePositiveAmount(amount);

        WalletTransaction tx = WalletTransaction.builder()
                .id(TransactionId.generate())
                .walletId(this.id)
                .type(TransactionType.DEBIT)
                .amount(amount)
                .balanceAfter(this.balance) // balance not yet changed
                .channel(channel)
                .referenceId(referenceId)
                .status(TransactionStatus.PENDING)
                .description(description)
                .idempotencyKey(idempotencyKey)
                .createdAt(LocalDateTime.now())
                .build();
        this.transactions.add(tx);
        this.updatedAt = LocalDateTime.now();
        return tx;
    }

    /**
     * Confirms a previously pending debit transaction.
     * Decreases the available balance upon MoMo/Stripe webhook confirmation.
     *
     * @param transactionId transaction to confirm
     * @param externalRef   provider's financial transaction ID
     * @return the updated WalletTransaction
     */
    public WalletTransaction confirmDebit(TransactionId transactionId, String externalRef) {
        WalletTransaction tx = findTransaction(transactionId);
        if (tx.getType() != TransactionType.DEBIT) {
            throw new IllegalStateException("Transaction is not a DEBIT: " + transactionId);
        }
        this.balance = this.balance.subtract(tx.getAmount());
        tx.confirm(externalRef);
        tx.setBalanceAfterConfirmation(this.balance);
        this.updatedAt = LocalDateTime.now();

        this.pendingDebitEvents.add(new WalletDebited(
                this.id.value(), this.userId, this.tenantId, tx.getAmount(), this.balance,
                tx.getReferenceId()));
        return tx;
    }

    /**
     * Reserves an amount (pre-authorization) without debiting the available balance.
     * Moves funds from balance → reservedBalance.
     */
    public WalletTransaction reserve(Money amount, String referenceId) {
        requireActive();
        requirePositiveAmount(amount);
        requireSufficientBalance(amount);

        this.balance = this.balance.subtract(amount);
        this.reservedBalance = this.reservedBalance.add(amount);
        this.updatedAt = LocalDateTime.now();

        WalletTransaction tx = WalletTransaction.builder()
                .id(TransactionId.generate())
                .walletId(this.id)
                .type(TransactionType.RESERVE)
                .amount(amount)
                .balanceAfter(this.balance)
                .channel(PaymentChannel.WALLET)
                .referenceId(referenceId)
                .status(TransactionStatus.CONFIRMED)
                .description("Reserve for " + referenceId)
                .idempotencyKey("RESERVE-" + referenceId)
                .createdAt(LocalDateTime.now())
                .processedAt(LocalDateTime.now())
                .build();
        this.transactions.add(tx);
        return tx;
    }

    /**
     * Releases a previously reserved amount back to available balance.
     */
    public WalletTransaction releaseReservation(Money amount, String referenceId) {
        requireActive();
        if (reservedBalance.isLessThan(amount)) {
            throw new IllegalStateException("Reserved balance insufficient to release: " + amount);
        }
        this.reservedBalance = this.reservedBalance.subtract(amount);
        this.balance = this.balance.add(amount);
        this.updatedAt = LocalDateTime.now();

        WalletTransaction tx = WalletTransaction.builder()
                .id(TransactionId.generate())
                .walletId(this.id)
                .type(TransactionType.RELEASE)
                .amount(amount)
                .balanceAfter(this.balance)
                .channel(PaymentChannel.WALLET)
                .referenceId(referenceId)
                .status(TransactionStatus.CONFIRMED)
                .description("Release reservation for " + referenceId)
                .idempotencyKey("RELEASE-" + referenceId)
                .createdAt(LocalDateTime.now())
                .processedAt(LocalDateTime.now())
                .build();
        this.transactions.add(tx);
        return tx;
    }

    /**
     * Initiates a PaymentIntent for this wallet.
     * Does NOT modify the balance yet — that happens upon webhook confirmation.
     *
     * @param request  payment request details
     * @param ttlMinutes duration until the intent expires
     * @return new PaymentIntent (must be persisted by the application layer)
     */
    public PaymentIntent initiatePayment(PaymentRequest request, int ttlMinutes) {
        requireActive();
        return PaymentIntent.builder()
                .id(PaymentIntentId.generate())
                .walletId(this.id)
                .invoiceId(request.invoiceId())
                .amount(request.amount())
                .channel(request.channel())
                .status(com.yowyob.tiibntick.core.billing.wallet.domain.enums.PaymentIntentStatus.PENDING)
                .idempotencyKey(request.idempotencyKey())
                .callbackUrl(request.callbackUrl())
                .expiresAt(LocalDateTime.now().plusMinutes(ttlMinutes))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /** Freezes the wallet — prevents debit and payment operations. */
    public void freeze() {
        this.freeze(null);
    }

    /** 
     * Freezes the wallet with a specific reason.
     * @param reason human-readable reason for the freeze
     */
    public void freeze(String reason) {
        if (this.status == WalletStatus.CLOSED) {
            throw new IllegalStateException("Cannot freeze a CLOSED wallet");
        }
        this.status = WalletStatus.FROZEN;
        this.updatedAt = LocalDateTime.now();
    }

    /** Unfreezes the wallet — restores normal operations. */
    public void unfreeze() {
        this.unfreeze(null);
    }

    /** 
     * Unfreezes the wallet with a specific reason.
     * @param reason human-readable reason for the unfreeze
     */
    public void unfreeze(String reason) {
        if (this.status != WalletStatus.FROZEN) {
            throw new IllegalStateException("Wallet is not FROZEN");
        }
        this.status = WalletStatus.ACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    /** Permanently closes the wallet. */
    public void close() {
        if (!balance.isZero()) {
            throw new IllegalStateException("Cannot close wallet with non-zero balance: " + balance);
        }
        this.status = WalletStatus.CLOSED;
        this.updatedAt = LocalDateTime.now();
    }

    /** Returns the total funds available for spending: balance only (excludes reserved). */
    public Money availableBalance() {
        return this.balance;
    }

    /** Returns the total balance including reserved amounts. */
    public Money totalBalance() {
        return this.balance.add(this.reservedBalance);
    }

    public List<WalletTransaction> getTransactions() {
        return Collections.unmodifiableList(transactions);
    }

    // ── domain guard helpers ───────────────────────────────────────────────

    private void requireActive() {
        if (this.status == WalletStatus.FROZEN) {
            throw new WalletFrozenException("Wallet " + this.id + " is FROZEN");
        }
        if (this.status == WalletStatus.CLOSED) {
            throw new WalletFrozenException("Wallet " + this.id + " is CLOSED");
        }
    }

    private void requirePositiveAmount(Money amount) {
        if (amount == null || !amount.isPositive()) {
            throw new IllegalArgumentException("Amount must be positive, got: " + amount);
        }
    }

    private void requireSufficientBalance(Money amount) {
        if (this.balance.isLessThan(amount)) {
            throw new InsufficientBalanceException(
                    String.format("Insufficient balance: available=%s, requested=%s", this.balance, amount));
        }
    }

    private WalletTransaction findTransaction(TransactionId transactionId) {
        return transactions.stream()
                .filter(t -> t.getId().equals(transactionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Transaction not found: " + transactionId));
    }
}
