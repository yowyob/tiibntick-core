package com.yowyob.tiibntick.core.marketback.application.port.in.command;

import com.yowyob.tiibntick.core.marketback.domain.model.PaymentMethod;
import jakarta.validation.constraints.NotNull;

/**
 * Command — process payment for a MarketOrder.
 * @author MANFOUO Braun
 */
public record ProcessPaymentCommand(
        @NotNull PaymentMethod paymentMethod,
        @NotNull String transactionRef,
        long paidAmountXaf,
        String mobileMoneyPhone
) {}
