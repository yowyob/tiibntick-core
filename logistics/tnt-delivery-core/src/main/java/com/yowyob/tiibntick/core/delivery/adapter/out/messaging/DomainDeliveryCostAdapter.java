package com.yowyob.tiibntick.core.delivery.adapter.out.messaging;

import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryCostComputationPort;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.LogisticsType;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryCost;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates;
import com.yowyob.tiibntick.core.delivery.domain.policy.DeliveryCostPolicy;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * In-process adapter for delivery cost computation.
 * Delegates to {@link DeliveryCostPolicy} in the domain layer.
 * Can be replaced by a remote pricing service adapter without changing any application code.
 *
 * @author MANFOUO Braun
 */
@Component
public class DomainDeliveryCostAdapter implements DeliveryCostComputationPort {

    // Average speed km/h — calibrated for Yaoundé urban context
    private static final double AVG_SPEED_KMH = 25.0;

    @Override
    public Mono<DeliveryCost> compute(GeoCoordinates origin,
                                       GeoCoordinates destination,
                                       LogisticsType logisticsType,
                                       DeliveryUrgency urgency) {
        GeoCoordinates from = origin != null ? origin : new GeoCoordinates(3.8480, 11.5021);
        GeoCoordinates to = destination != null ? destination : from;
        double distKm = from.haversineDistanceTo(to);
        if (distKm <= 0) {
            distKm = 1.0; // minimum urban hop when GPS is absent on both ends
        }
        int estimatedMinutes = (int) Math.ceil((distKm / AVG_SPEED_KMH) * 60);
        DeliveryCost cost = DeliveryCostPolicy.computeSimple(distKm, estimatedMinutes,
                logisticsType, urgency);
        return Mono.just(cost);
    }
}
