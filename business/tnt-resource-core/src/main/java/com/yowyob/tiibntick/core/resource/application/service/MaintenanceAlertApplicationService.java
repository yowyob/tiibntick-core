package com.yowyob.tiibntick.core.resource.application.service;

import com.yowyob.tiibntick.core.resource.application.port.in.CheckMaintenanceDueUseCase;
import com.yowyob.tiibntick.core.resource.application.port.out.ResourceEventPublisherPort;
import com.yowyob.tiibntick.core.resource.application.port.out.VehicleRepository;
import com.yowyob.tiibntick.core.resource.domain.event.MaintenanceAlertTriggeredEvent;
import com.yowyob.tiibntick.core.resource.domain.model.Vehicle;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Application service that identifies vehicles with overdue maintenance
 * and emits {@link MaintenanceAlertTriggeredEvent} for each.
 * Consumed by tnt-notify-core to send SMS/push alerts to fleet managers.
 *
 * Triggered by a scheduled task in tnt-bootstrap.
 *
 * @author MANFOUO Braun.
 */
@Service
public class MaintenanceAlertApplicationService implements CheckMaintenanceDueUseCase {

    private final VehicleRepository vehicleRepository;
    private final ResourceEventPublisherPort eventPublisher;

    public MaintenanceAlertApplicationService(VehicleRepository vehicleRepository,
            ResourceEventPublisherPort eventPublisher) {
        this.vehicleRepository = vehicleRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Flux<Vehicle> findVehiclesWithMaintenanceDue(UUID tenantId, UUID agencyId) {
        return vehicleRepository.findByAgency(tenantId, agencyId)
                .filter(Vehicle::isMaintenanceDue)
                .flatMap(v -> {
                    MaintenanceAlertTriggeredEvent event = MaintenanceAlertTriggeredEvent.of(
                            v.id(), v.tenantId(), v.agencyId(),
                            v.registrationNumber(),
                            v.nextMaintenance().type(),
                            v.nextMaintenance().scheduledDate(),
                            v.odometerKm());
                    return eventPublisher.publish(event).thenReturn(v);
                });
    }
}
