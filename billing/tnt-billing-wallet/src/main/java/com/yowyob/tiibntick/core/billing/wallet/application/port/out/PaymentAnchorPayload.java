package com.yowyob.tiibntick.core.billing.wallet.application.port.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Wallet-owned payload for {@link IPaymentAnchorPort#anchor}.
 *
 * <p>Deliberately independent from any {@code tnt-trust-core} domain type — the
 * implementing adapter (in {@code tnt-trust-core}) maps this into a
 * {@code LogisticTrustEvent}, keeping the hexagonal boundary between the two modules.
 *
 * @param paymentIntentId the payment intent identifier
 * @param walletId        the wallet identifier that was debited/credited
 * @param userId          the wallet owner's actor id
 * @param invoiceId       the invoice being paid, nullable for top-up flows
 * @param amount          the committed amount
 * @param currency        the ISO currency code (e.g., "XAF")
 * @param channel         the payment channel (e.g., MTN_MOMO, ORANGE_MONEY, STRIPE)
 * @param externalRef     the provider's financial transaction id
 * @author MANFOUO Braun
 */
public record PaymentAnchorPayload(
        UUID tenantId,
        UUID paymentIntentId,
        UUID walletId,
        UUID userId,
        String invoiceId,
        BigDecimal amount,
        String currency,
        String channel,
        String externalRef,
        Instant confirmedAt) {
}
