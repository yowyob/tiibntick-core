package com.yowyob.tiibntick.core.gofreelancer.adapter.out.pricing;

import com.yowyob.tiibntick.core.gofreelancer.application.port.out.CommissionRatePort;
import com.yowyob.tiibntick.core.gofreelancer.application.service.DeliveryPaymentApplicationService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

/** The current commission rule (H5): a fixed 5 % for every delivery. Replace it with a {@code @Primary} bean. */
@Component
public class FixedCommissionRateAdapter implements CommissionRatePort {

    @Override
    public Mono<BigDecimal> deliveryCommissionRate(UUID tenantId, UUID deliveryId) {
        return Mono.just(DeliveryPaymentApplicationService.COMMISSION_RATE);
    }
}
