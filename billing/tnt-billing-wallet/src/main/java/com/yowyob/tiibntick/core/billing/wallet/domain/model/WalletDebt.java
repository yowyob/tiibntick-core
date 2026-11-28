package com.yowyob.tiibntick.core.billing.wallet.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * What the Kernel compensation fund paid because of a third party, owed back by that party (decisions QO-22 / D-115:
 * return at the courier's expense; QO-26 / D-120: compensation of a dispute where a courier, relay or agency is
 * responsible). Recovered like unpaid cash: a Kernel cash collection opened on the debtor's wallet, remitted by the
 * debtor with his code ({@code kernelCollectionId}). The administrator follows the debts and decides on a suspension.
 *
 * @param debtorId   actor id of a courier, organisation id of a relay or agency; {@code null} until known
 * @param reference  the Kernel fund reference that created the debt ({@code DISPUTE:<id>}, {@code RETURN:<id>})
 */
public record WalletDebt(UUID id, UUID tenantId, DebtorType debtorType, UUID debtorId, Reason reason, String reference,
                         UUID deliveryId, BigDecimal amount, String currency, Status status, UUID debtorWalletId,
                         UUID kernelCollectionId, String note, Instant createdAt, Instant updatedAt) {

    public enum DebtorType {
        COURIER, RELAY, AGENCY;

        /** @return the debtor type of a responsible party, {@code null} when the party owes nothing back */
        public static DebtorType ofResponsibleParty(String party) {
            if (party == null) {
                return null;
            }
            return switch (party) {
                case "COURIER" -> COURIER;
                case "RELAY" -> RELAY;
                case "AGENCY" -> AGENCY;
                default -> null;
            };
        }
    }

    /** {@code COURIER_REPLACEMENT}: the fund paid a replacement courier (QO-11 / D-153), the first courier owes it. */
    public enum Reason { DISPUTE_COMPENSATION, RETURN_COST, COURIER_REPLACEMENT }

    /**
     * PENDING: the fund has not paid yet (a return at the courier's expense, until it is delivered); OPEN: owed;
     * REPAID: remitted to the fund; CANCELLED: written off by the administrator, or the return never happened.
     */
    public enum Status {
        PENDING, OPEN, REPAID, CANCELLED;

        public boolean isFinal() {
            return this == REPAID || this == CANCELLED;
        }
    }

    public static String disputeReference(String disputeId) {
        return "DISPUTE:" + disputeId;
    }

    public static String returnReference(UUID returnDeliveryId) {
        return "RETURN:" + returnDeliveryId;
    }

    /** QO-11: the fund's payment to the {@code index}-th replacement courier of a delivery (at most 64 characters). */
    public static String replacementReference(UUID deliveryId, int index) {
        return WalletCompensation.REPLACEMENT_PREFIX + deliveryId + ":" + index;
    }

    public WalletDebt with(Status newStatus, BigDecimal newAmount, String newNote, Instant at) {
        return new WalletDebt(id, tenantId, debtorType, debtorId, reason, reference, deliveryId,
                newAmount != null ? newAmount : amount, currency, newStatus, debtorWalletId, kernelCollectionId,
                newNote != null ? newNote : note, createdAt, at);
    }

    public WalletDebt withDebtor(UUID newDebtorId, Instant at) {
        return new WalletDebt(id, tenantId, debtorType, newDebtorId, reason, reference, deliveryId, amount, currency,
                status, null, null, note, createdAt, at);
    }

    public WalletDebt withRecovery(UUID walletId, UUID collectionId, Instant at) {
        return new WalletDebt(id, tenantId, debtorType, debtorId, reason, reference, deliveryId, amount, currency,
                status, walletId, collectionId, note, createdAt, at);
    }
}
