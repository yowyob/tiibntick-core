package com.yowyob.tiibntick.core.billing.wallet.domain.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MoMoPayload — value object that builds the request body for MTN or Orange MoMo API.
 * Encapsulates the provider-specific serialization logic inside the domain.
 *
 * @author MANFOUO Braun
 */
public record MoMoPayload(
        String externalId,
        String amount,
        String currency,
        String payerPhone,
        String partyIdType,
        String payerMessage,
        String payeeNote
) {

    public static MoMoPayload fromRequest(PaymentRequest request, String externalId) {
        return new MoMoPayload(
                externalId,
                request.amount().amount().toPlainString(),
                request.amount().currencyCode(),
                request.payerPhone(),
                "MSISDN",
                request.description() != null ? request.description() : "TiiBnTick payment",
                "TiiBnTick"
        );
    }

    /**
     * Builds the request body map for the MTN MoMo Collections API v2
     * (POST /collection/v1_0/requesttopay).
     */
    public Map<String, Object> toMtnRequestBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amount);
        body.put("currency", currency);
        body.put("externalId", externalId);
        body.put("payer", Map.of(
                "partyIdType", partyIdType,
                "partyId", payerPhone
        ));
        body.put("payerMessage", payerMessage);
        body.put("payeeNote", payeeNote);
        return body;
    }

    /**
     * Builds the request body map for the Orange Money Cameroon payment API.
     */
    public Map<String, Object> toOrangeRequestBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("merchant_key", "");  // injected by adapter from config
        body.put("currency", currency);
        body.put("order_id", externalId);
        body.put("amount", amount);
        body.put("return_url", "");    // injected by adapter from config
        body.put("cancel_url", "");    // injected by adapter from config
        body.put("notif_url", "");     // injected by adapter from config
        body.put("lang", "fr");
        body.put("reference", payerMessage);
        return body;
    }
}
