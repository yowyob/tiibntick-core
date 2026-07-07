package com.yowyob.tiibntick.core.billing.invoice.application.service;

import com.yowyob.tiibntick.core.billing.invoice.application.port.in.command.GenerateInvoiceCommand;
import com.yowyob.tiibntick.core.billing.invoice.application.port.out.*;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.*;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.enums.InvoiceStatus;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.enums.LineItemType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Unit tests for InvoiceService.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private CreditNoteRepository creditNoteRepository;
    @Mock private InvoiceEventPublisher eventPublisher;
    @Mock private InvoicePdfPort pdfPort;
    @Mock private InvoiceSequencePort sequencePort;

    private InvoiceService invoiceService;

    private final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        invoiceService = new InvoiceService(
                invoiceRepository, creditNoteRepository,
                eventPublisher, pdfPort, sequencePort);
    }

    @Test
    void generate_shouldCreateAndIssueInvoice() {
        Money unitPrice = Money.xaf(15_000);
        InvoiceLine line = InvoiceLine.of(1, "Standard delivery", 1.0,
                unitPrice, BigDecimal.ZERO, LineItemType.DELIVERY_FEE);

        GenerateInvoiceCommand command = GenerateInvoiceCommand.simple(
                TENANT_ID, "AGY001", "CM",
                "MISSION-123", null, "CLIENT-456",
                List.of(line), List.of(), "XAF", null);

        when(sequencePort.nextSequence(any(), anyInt())).thenReturn(Mono.just(1L));
        when(invoiceRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(eventPublisher.publishAll(any())).thenReturn(Mono.empty());
        when(pdfPort.generateAndStore(any())).thenReturn(Mono.just("invoices/TNT-FACT-AGY001-2026-000001.pdf"));

        StepVerifier.create(invoiceService.generate(command))
                .assertNext(invoice -> {
                    assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
                    assertThat(invoice.getNumber().value()).startsWith("TNT-FACT-AGY001-");
                    assertThat(invoice.getMissionId()).isEqualTo("MISSION-123");
                    assertThat(invoice.getNetAmount().currency()).isEqualTo("XAF");
                })
                .verifyComplete();
    }
}
