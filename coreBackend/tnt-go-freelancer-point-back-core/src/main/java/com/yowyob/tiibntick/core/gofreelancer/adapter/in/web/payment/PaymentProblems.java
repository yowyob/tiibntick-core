package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.payment;

import com.yowyob.tiibntick.core.billing.wallet.domain.exception.WalletSettlementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.net.URI;

/** Problem detail of a refused escrow / cash collection operation (lot L5): its HTTP status and stable code. */
final class PaymentProblems {

    private PaymentProblems() {
    }

    static ProblemDetail of(WalletSettlementException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getHttpStatus());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status != null ? status : HttpStatus.CONFLICT,
                ex.getMessage());
        pd.setType(URI.create("urn:tiibntick:payment:" + ex.getCode().toLowerCase()));
        pd.setTitle("Payment refused");
        pd.setProperty("code", ex.getCode());
        return pd;
    }
}
