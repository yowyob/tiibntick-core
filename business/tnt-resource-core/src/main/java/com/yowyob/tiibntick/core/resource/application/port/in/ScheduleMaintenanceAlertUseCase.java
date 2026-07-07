package com.yowyob.tiibntick.core.resource.application.port.in;

import com.yowyob.tiibntick.core.resource.domain.model.Vehicle;
import reactor.core.publisher.Mono;

/**
 * Inbound port: schedule a maintenance alert on a specific vehicle.
 * @author MANFOUO Braun.
 */
public interface ScheduleMaintenanceAlertUseCase {
    Mono<Vehicle> scheduleMaintenanceAlert(ScheduleMaintenanceAlertCommand command);
}
