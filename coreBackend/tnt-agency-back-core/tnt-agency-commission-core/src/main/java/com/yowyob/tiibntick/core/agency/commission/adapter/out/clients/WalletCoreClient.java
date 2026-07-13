package com.yowyob.tiibntick.core.agency.commission.adapter.out.clients;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@Component
public class WalletCoreClient implements WalletCorePort {

    private static final Logger log = LoggerFactory.getLogger(WalletCoreClient.class);
    private static final String PAY_PATH = "/billing/wallet/pay";

    private final WebClient webClient;

    public WalletCoreClient(@Qualifier("agencyPlatformWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Mono<PaymentResult> pay(PaymentRequest request) {
        UUID userId = request.userId() != null ? request.userId() : request.commissionId();

        return webClient.post()
                .uri(uri -> uri.path(PAY_PATH)
                        .queryParam("tenantId", request.tenantId())
                        .queryParam("userId", userId)
                        .build())
                .header("X-Tenant-Id", request.tenantId().toString())
                .bodyValue(Map.of(
                        "invoiceId", request.commissionId().toString(),
                        "amount", request.amount(),
                        "currency", request.currency(),
                        "channel", resolveChannel(request.recipientPhone()),
                        "payerPhone", nullToEmpty(request.recipientPhone()),
                        "description", nullToEmpty(request.description()),
                        "callbackUrl", ""
                ))
                .retrieve()
                .bodyToMono(CorePaymentIntentResponse.class)
                .map(r -> new PaymentResult(
                        request.transactionRef(),
                        r.externalRef(),
                        mapStatus(r.status())))
                .onErrorResume(e -> {
                    log.warn("[Wallet] pay failed commissionId={}: {}", request.commissionId(), e.getMessage());
                    return Mono.just(new PaymentResult(request.transactionRef(), null, PaymentStatus.FAILED));
                });
    }

    private static String resolveChannel(String phone) {
        if (phone != null && phone.startsWith("+2376")) {
            return "MTN_MOMO";
        }
        return "ORANGE_MONEY";
    }

    private static PaymentStatus mapStatus(String status) {
        if (status == null) {
            return PaymentStatus.PENDING;
        }
        return switch (status.toUpperCase()) {
            case "SUCCESS", "COMPLETED", "CONFIRMED" -> PaymentStatus.SUCCESS;
            case "FAILED", "REJECTED", "EXPIRED" -> PaymentStatus.FAILED;
            case "CANCELLED", "CANCELED" -> PaymentStatus.CANCELLED;
            default -> PaymentStatus.PENDING;
        };
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    private record CorePaymentIntentResponse(UUID id, String invoiceId, String status, String externalRef) {}
}
