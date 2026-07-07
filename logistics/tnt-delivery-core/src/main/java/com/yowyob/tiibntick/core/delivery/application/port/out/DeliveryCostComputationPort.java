package com.yowyob.tiibntick.core.delivery.application.port.out;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.LogisticsType;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryCost;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates;
import reactor.core.publisher.Mono;

/**
 * Outbound port for delivery cost computation.
 * The default implementation delegates to {@code DeliveryCostPolicy} in the domain
 * and can be overridden by a remote pricing service in platform-specific adapters.
 *
 * @author MANFOUO Braun
 */
public interface DeliveryCostComputationPort {

    /**
     * Computes the estimated delivery cost using the multi-criteria formula.
     *
     * @param origin        pickup coordinates
     * @param destination   delivery coordinates
     * @param logisticsType vehicle type (affects base rate)
     * @param urgency       urgency multiplier
     * @return reactive cost breakdown in XAF
     */
    Mono<DeliveryCost> compute(GeoCoordinates origin,
                                GeoCoordinates destination,
                                LogisticsType logisticsType,
                                DeliveryUrgency urgency);
}
