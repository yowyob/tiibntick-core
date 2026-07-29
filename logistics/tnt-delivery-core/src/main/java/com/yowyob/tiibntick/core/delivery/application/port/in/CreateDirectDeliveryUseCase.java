package com.yowyob.tiibntick.core.delivery.application.port.in;

import com.yowyob.tiibntick.core.delivery.application.port.in.command.CreateDirectDeliveryCommand;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import reactor.core.publisher.Mono;

/**
 * Use case for creating a {@code Delivery} directly (no announcement/marketplace step).
 *
 * @author MANFOUO Braun
 */
public interface CreateDirectDeliveryUseCase {

    Mono<Delivery> createDirect(CreateDirectDeliveryCommand cmd);
}
