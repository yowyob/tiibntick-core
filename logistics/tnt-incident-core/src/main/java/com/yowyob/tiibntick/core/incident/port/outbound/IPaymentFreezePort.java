package com.yowyob.tiibntick.core.incident.port.outbound;
import reactor.core.publisher.Mono;
import java.util.UUID;
/**
 * Outbound port: freeze or unfreeze mission payments via tnt-billing-wallet.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

public interface IPaymentFreezePort {
    Mono<Void> freezePayment(UUID missionId, String reason);
    Mono<Void> unfreezePayment(UUID missionId, String reason);
}
