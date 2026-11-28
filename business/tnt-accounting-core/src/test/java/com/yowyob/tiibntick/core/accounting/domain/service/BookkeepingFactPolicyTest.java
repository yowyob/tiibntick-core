package com.yowyob.tiibntick.core.accounting.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.accounting.domain.model.kernel.BookkeepingFact;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Lot L6: which entry a money event books, and in which book (QO-20, H.3)")
class BookkeepingFactPolicyTest {

    final ObjectMapper json = new ObjectMapper();

    @Test
    @DisplayName("commission SETTLED → platform D 4671 / C 706, balanced; DUE → skipped; no settlement id → not a fact")
    void commission() throws Exception {
        UUID s = UUID.randomUUID();
        BookkeepingFact f = BookkeepingFactPolicy.commission(json.readTree("{\"settlementId\":\"" + s
                + "\",\"referenceId\":\"d\",\"commission\":\"150.00\",\"commissionStatus\":\"SETTLED\","
                + "\"occurredAt\":\"2026-11-27T08:00:00Z\"}"));
        assertThat(f.sourceKey()).isEqualTo("COMMISSION:" + s);
        assertThat(f.ownerType()).isEqualTo(BookkeepingFact.PLATFORM);
        assertThat(f.skipped()).isFalse();
        assertThat(f.lines()).extracting(BookkeepingFact.Line::accountCode).containsExactly("4671", "706");
        assertThat(f.lines().stream().map(BookkeepingFact.Line::debit).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(f.lines().stream().map(BookkeepingFact.Line::credit)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
        assertThat(f.date().toString()).isEqualTo("2026-11-27T08:00:00Z");
        assertThat(BookkeepingFactPolicy.commission(json.readTree("{\"settlementId\":\"x\",\"commission\":\"1\","
                + "\"commissionStatus\":\"DUE\"}")).skipReason()).isEqualTo("COMMISSION_NOT_SETTLED");
        assertThat(BookkeepingFactPolicy.commission(json.readTree("{\"commission\":\"1\"}"))).isNull();
    }

    @Test
    @DisplayName("compensation: dispute → IND-<dispute>, return paid by the fund → RET-<delivery>; D 6788 / C 4672")
    void compensation() throws Exception {
        BookkeepingFact dispute = BookkeepingFactPolicy.compensation(json.readTree("{\"compensationId\":\"c1\","
                + "\"disputeId\":\"d1\",\"amount\":\"15000\",\"currency\":\"XAF\"}"));
        assertThat(dispute.reference()).isEqualTo("IND-d1");
        assertThat(dispute.lines()).extracting(BookkeepingFact.Line::accountCode).containsExactly("6788", "4672");
        BookkeepingFact ret = BookkeepingFactPolicy.compensation(json.readTree("{\"compensationId\":\"c2\","
                + "\"reason\":\"RETURN_COST\",\"returnDeliveryId\":\"r1\",\"amount\":\"2850\"}"));
        assertThat(ret.reference()).isEqualTo("RET-r1");
        assertThat(ret.lines().get(0).label()).startsWith("Coût de retour");
    }

    @Test
    @DisplayName("invoice paid: the issuer's book (money object amount); without a UUID issuer → skipped")
    void invoice() throws Exception {
        UUID issuer = UUID.randomUUID();
        BookkeepingFact f = BookkeepingFactPolicy.invoicePaid(json.readTree("{\"invoiceId\":\"i\",\"amount\":"
                + "{\"amount\":5000,\"currency\":\"EUR\"},\"issuerOrgId\":\"" + issuer + "\"}"));
        assertThat(f.ownerType()).isEqualTo(BookkeepingFact.ORGANIZATION);
        assertThat(f.ownerId()).isEqualTo(issuer);
        assertThat(f.amount()).isEqualByComparingTo("5000");
        assertThat(f.currency()).isEqualTo("EUR");
        assertThat(f.lines()).extracting(BookkeepingFact.Line::accountCode).containsExactly("4671", "411");
        assertThat(BookkeepingFactPolicy.invoicePaid(json.readTree("{\"invoiceId\":\"i\",\"issuerOrgId\":\"FRL-1\","
                + "\"amount\":{\"amount\":1}}")).skipReason()).isEqualTo("NO_ISSUER_ORGANIZATION");
    }
}
