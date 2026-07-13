package com.yowyob.tiibntick.core.agency.commission.adapter.out.clients;

import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

public interface WalletCorePort {

    Mono<PaymentResult> pay(PaymentRequest request);

    enum PaymentStatus { PENDING, SUCCESS, FAILED, CANCELLED }

    record PaymentRequest(
            UUID tenantId,
            UUID userId,
            UUID commissionId,
            String recipientPhone,
            BigDecimal amount,
            String currency,
            String description,
            String transactionRef) {}

    record PaymentResult(String transactionRef, String externalRef, PaymentStatus status) {}
}
