package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.payment.PaymentMethod;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.relaypoint.RelayPointSubscriptionType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelayPointSubscriptionRequestDTO {
    @NotNull
    private RelayPointSubscriptionType subscriptionType;
    @NotNull
    private PaymentMethod paymentMethod;
}
