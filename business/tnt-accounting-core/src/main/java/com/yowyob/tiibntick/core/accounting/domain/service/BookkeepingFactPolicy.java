package com.yowyob.tiibntick.core.accounting.domain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.yowyob.tiibntick.core.accounting.domain.model.kernel.BookkeepingFact;
import com.yowyob.tiibntick.core.accounting.domain.model.kernel.BookkeepingFact.Line;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Which entry a money event books, and in which book (lot L6, cible §4.6, decisions H.3 and QO-20). Pure: from the
 * event's fields to a {@link BookkeepingFact}; {@code null} when the event is not an accounting fact at all.
 *
 * <table>
 *   <tr><th>event</th><th>book</th><th>entry</th></tr>
 *   <tr><td>{@code commission.calculated}, commission {@code SETTLED}</td><td>PLATFORM</td>
 *       <td>D 4671 Wallet Kernel plateforme / C 706 Commissions</td></tr>
 *   <tr><td>{@code compensation.paid} (dispute indemnity or return paid by the fund)</td><td>PLATFORM</td>
 *       <td>D 6788 Indemnisations litiges (or Coût de retour) / C 4672 Fonds d'indemnisation</td></tr>
 *   <tr><td>{@code invoice.paid}</td><td>ORGANIZATION issuing the invoice</td><td>D 4671 / C 411 Clients</td></tr>
 * </table>
 * The courier's share of a delivery is booked nowhere (H.3: an independent courier keeps no books here); a commission
 * still {@code DUE} (cash collected, not remitted) waits for its {@code SETTLED} event.
 */
public final class BookkeepingFactPolicy {

    public static final String WALLET = "4671";
    public static final String FUND = "4672";
    public static final String REVENUE = "706";
    public static final String COMPENSATIONS = "6788";
    public static final String CUSTOMERS = "411";

    /** Kernel account / journal types (K14: the Kernel does not enumerate them; these values are assumed). */
    public static final String ASSET = "ASSET";
    public static final String INCOME = "REVENUE";
    public static final String EXPENSE = "EXPENSE";
    public static final String JOURNAL_TYPE = "GENERAL";

    public static final String COMMISSION_CALCULATED = "COMMISSION_CALCULATED";
    public static final String COMPENSATION_PAID = "COMPENSATION_PAID";
    public static final String INVOICE_PAID = "INVOICE_PAID";

    public static final String COMMISSION_NOT_SETTLED = "COMMISSION_NOT_SETTLED";
    public static final String NO_ISSUER_ORGANIZATION = "NO_ISSUER_ORGANIZATION";
    public static final String ZERO_AMOUNT = "ZERO_AMOUNT";

    private BookkeepingFactPolicy() {
    }

    /** {@code tnt.billing.wallet.commission.calculated}; {@code null} without a settlement id. */
    public static BookkeepingFact commission(JsonNode e) {
        String settlement = text(e, "settlementId");
        if (settlement == null) {
            return null;
        }
        BigDecimal amount = decimal(e, "commission");
        String delivery = text(e, "referenceId");
        String skip = !"SETTLED".equals(text(e, "commissionStatus")) ? COMMISSION_NOT_SETTLED
                : amount.signum() <= 0 ? ZERO_AMOUNT : null;
        return new BookkeepingFact("COMMISSION:" + settlement, COMMISSION_CALCULATED, text(e, "eventId"),
                BookkeepingFact.PLATFORM, null, "TNT-COM", "Commissions TiiBnTick",
                "COM-" + (delivery != null ? delivery : settlement), instant(e), amount, currency(e),
                List.of(new Line(WALLET, "Wallet Kernel plateforme", ASSET, amount, BigDecimal.ZERO,
                                "Commission livraison " + delivery),
                        new Line(REVENUE, "Commissions", INCOME, BigDecimal.ZERO, amount,
                                "Commission livraison " + delivery)),
                skip);
    }

    /** {@code tnt.billing.compensation.paid}: a dispute's indemnity or a return paid by the fund (QO-22). */
    public static BookkeepingFact compensation(JsonNode e) {
        String compensation = text(e, "compensationId");
        if (compensation == null) {
            return null;
        }
        boolean ret = "RETURN_COST".equals(text(e, "reason"));
        boolean replacement = "COURIER_REPLACEMENT".equals(text(e, "reason"));
        String subject = ret ? text(e, "returnDeliveryId") : replacement ? text(e, "deliveryId") : text(e, "disputeId");
        String label = (ret ? "Coût de retour " : replacement ? "Livreur remplaçant, livraison "
                : "Indemnisation litige ") + subject;
        BigDecimal amount = decimal(e, "amount");
        return new BookkeepingFact("COMPENSATION:" + compensation, COMPENSATION_PAID, text(e, "eventId"),
                BookkeepingFact.PLATFORM, null, "TNT-IND", "Indemnisations et retours",
                (ret ? "RET-" : replacement ? "REP-" : "IND-") + subject, instant(e), amount, currency(e),
                List.of(new Line(COMPENSATIONS, "Indemnisations litiges", EXPENSE, amount, BigDecimal.ZERO, label),
                        new Line(FUND, "Fonds d'indemnisation (wallet Kernel)", ASSET, BigDecimal.ZERO, amount,
                                label)),
                amount.signum() <= 0 ? ZERO_AMOUNT : null);
    }

    /**
     * {@code tnt.billing.invoice.paid} ({@code InvoicePaid}: {@code amount} is a money object
     * {@code {amount, currency}}); booked in the issuing organisation's book.
     */
    public static BookkeepingFact invoicePaid(JsonNode e) {
        String invoice = text(e, "invoiceId");
        if (invoice == null) {
            return null;
        }
        JsonNode money = e.path("amount");
        BigDecimal amount = money.isObject() ? decimal(money, "amount") : decimal(e, "amount");
        String currency = money.isObject() && text(money, "currency") != null ? text(money, "currency")
                : currency(e);
        UUID issuer = uuid(text(e, "issuerOrgId"));
        String label = "Facture " + invoice + " payée";
        return new BookkeepingFact("INVOICE_PAID:" + invoice, INVOICE_PAID, text(e, "eventId"),
                issuer != null ? BookkeepingFact.ORGANIZATION : null, issuer, "TNT-VTE", "Ventes",
                "FAC-" + invoice, instant(e), amount, currency,
                List.of(new Line(WALLET, "Wallet Kernel organisation", ASSET, amount, BigDecimal.ZERO, label),
                        new Line(CUSTOMERS, "Clients", ASSET, BigDecimal.ZERO, amount, label)),
                issuer == null ? NO_ISSUER_ORGANIZATION : amount.signum() <= 0 ? ZERO_AMOUNT : null);
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isMissingNode() || v.isNull() || v.asText().isBlank() ? null : v.asText();
    }

    private static BigDecimal decimal(JsonNode n, String field) {
        String v = text(n, field);
        try {
            return v == null ? BigDecimal.ZERO : new BigDecimal(v);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static String currency(JsonNode n) {
        String c = text(n, "currency");
        return c != null ? c : "XAF";
    }

    private static Instant instant(JsonNode n) {
        for (String f : List.of("occurredAt", "paidAt")) {
            String v = text(n, f);
            if (v != null) {
                try {
                    return Instant.parse(v);
                } catch (RuntimeException ignored) {
                    // next candidate
                }
            }
        }
        return Instant.now();
    }

    private static UUID uuid(String v) {
        try {
            return v == null ? null : UUID.fromString(v);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
