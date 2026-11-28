package com.yowyob.tiibntick.core.accounting.adapter.in.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.kafka.EventPayloads;
import com.yowyob.tiibntick.common.kafka.ReactiveListenerSupport;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.accounting.application.service.KernelBookkeepingService;
import com.yowyob.tiibntick.core.accounting.domain.model.kernel.BookkeepingFact;
import com.yowyob.tiibntick.core.accounting.domain.service.BookkeepingFactPolicy;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * Books the money events in the Kernel accounting service (lot L6, cible §4.6, decisions H.3, QO-20, QO-21). Blocking
 * listeners (§3.10): the listener thread waits for the entry, an unavailable Kernel fails the record (retried, then
 * {@code tnt.dlq}); the booking is idempotent per fact, so a replay never books twice.
 * <ul>
 *   <li>{@code tnt.billing.wallet.commission.calculated} — platform commission, once {@code SETTLED};</li>
 *   <li>{@code tnt.billing.compensation.paid} — dispute indemnity or return paid by the fund;</li>
 *   <li>{@code tnt.billing.invoice.paid} — in the book of the issuing organisation.</li>
 * </ul>
 * Removed in L5/L6: {@code payment.confirmed} (the courier's share is booked nowhere, H.3; no MERCHANT share exists
 * yet), {@code tnt.delivery.freelancer_org.assigned} (not an accounting fact), {@code split_executed} (topic removed).
 * The local journal is no longer written (QO-21).
 */
@Slf4j
@Component
public class BillingEventAccountingConsumer {

    static final String GROUP = "tnt-accounting-core";

    private final KernelBookkeepingService bookkeeping;
    private final ObjectMapper objectMapper;
    private final ReactiveListenerSupport listenerSupport;

    public BillingEventAccountingConsumer(KernelBookkeepingService bookkeeping,
                                          @Qualifier("tntObjectMapper") ObjectMapper objectMapper,
                                          ObjectProvider<ReactiveListenerSupport> listenerSupport) {
        this.bookkeeping = bookkeeping;
        this.objectMapper = objectMapper;
        this.listenerSupport = listenerSupport.getIfAvailable(ReactiveListenerSupport::withDefaultTimeout);
    }

    @KafkaListener(topics = TntTopics.BILLING_WALLET_COMMISSION_CALCULATED, groupId = GROUP,
            containerFactory = "accountingKafkaListenerContainerFactory")
    public void onCommissionCalculated(ConsumerRecord<String, String> record) {
        book(record, BookkeepingFactPolicy::commission);
    }

    @KafkaListener(topics = TntTopics.BILLING_COMPENSATION_PAID, groupId = GROUP,
            containerFactory = "accountingKafkaListenerContainerFactory")
    public void onCompensationPaid(ConsumerRecord<String, String> record) {
        book(record, BookkeepingFactPolicy::compensation);
    }

    @KafkaListener(topics = TntTopics.BILLING_INVOICE_PAID, groupId = GROUP,
            containerFactory = "accountingKafkaListenerContainerFactory")
    public void onInvoicePaid(ConsumerRecord<String, String> record) {
        book(record, BookkeepingFactPolicy::invoicePaid);
    }

    private void book(ConsumerRecord<String, String> record, Function<JsonNode, BookkeepingFact> policy) {
        JsonNode event;
        try {
            event = EventPayloads.parse(objectMapper, record.value());
        } catch (Exception e) {
            // a poison message cannot be booked by a retry: logged, skipped
            log.error("ACCOUNTING_EVENT_UNREADABLE topic={} offset={}: {}", record.topic(), record.offset(),
                    e.toString());
            return;
        }
        BookkeepingFact fact = policy.apply(event);
        if (fact == null) {
            log.warn("ACCOUNTING_EVENT_INCOMPLETE topic={} offset={} — no identifier, nothing booked",
                    record.topic(), record.offset());
            return;
        }
        listenerSupport.await(bookkeeping.book(fact));
    }
}
