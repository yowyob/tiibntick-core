package com.yowyob.tiibntick.core.linkback.application.service;

import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.linkback.application.port.in.AwardNodeReputationUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.ConfirmNetworkAlertUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.QueryNetworkAlertsUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.ReportNetworkAlertUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.ResolveNetworkAlertUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.command.ReportNetworkAlertCommand;
import com.yowyob.tiibntick.core.linkback.application.port.out.NetworkAlertRepository;
import com.yowyob.tiibntick.core.linkback.domain.exception.NetworkAlertDomainException;
import com.yowyob.tiibntick.core.linkback.domain.model.NetworkAlert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Genuinely new Link business logic — no equivalent exists in L2-L5 for
 * community-reported network alerts, so this is real domain orchestration,
 * not a thin pass-through.
 *
 * @author Dilane PAFE
 */
@Service
@RequiredArgsConstructor
public class NetworkAlertApplicationService implements
        ReportNetworkAlertUseCase, ConfirmNetworkAlertUseCase,
        ResolveNetworkAlertUseCase, QueryNetworkAlertsUseCase {

    private final NetworkAlertRepository repository;
    private final AwardNodeReputationUseCase reputationUseCase;

    @Override
    public Mono<NetworkAlert> report(ReportNetworkAlertCommand command) {
        NetworkAlert alert = NetworkAlert.report(
                command.tenantId(), command.reporterId(), command.type(),
                command.description(), command.location(), command.severity());
        return repository.save(alert);
    }

    @Override
    public Mono<NetworkAlert> confirm(UUID tenantId, UUID alertId) {
        return findOrError(tenantId, alertId)
                .flatMap(alert -> {
                    alert.confirm();
                    return repository.save(alert);
                })
                // Community-validated report → the reporter's node (if any) earns trust.
                // A no-op when the reporter has no registered NetworkNode yet.
                .flatMap(saved -> reputationUseCase
                        .awardTrust(tenantId, saved.getReporterId(), 1.0)
                        .thenReturn(saved));
    }

    @Override
    public Mono<NetworkAlert> resolve(UUID tenantId, UUID alertId) {
        return findOrError(tenantId, alertId)
                .flatMap(alert -> {
                    alert.resolve();
                    return repository.save(alert);
                });
    }

    @Override
    public Mono<NetworkAlert> findById(UUID tenantId, UUID alertId) {
        return repository.findById(tenantId, alertId);
    }

    @Override
    public Flux<NetworkAlert> findActiveNearby(UUID tenantId, GeoPoint center, double radiusKm) {
        // Bounding box is a cheap DB-side pre-filter (a superset of the circle); the exact
        // haversine check below only runs on that narrowed candidate set, not the whole tenant.
        double latDelta = radiusKm / 111.0;
        double lngDelta = radiusKm / (111.0 * Math.cos(Math.toRadians(center.latitude())));
        return repository.findActiveWithinBoundingBox(tenantId,
                        center.latitude() - latDelta, center.latitude() + latDelta,
                        center.longitude() - lngDelta, center.longitude() + lngDelta)
                .filter(alert -> alert.getLocation().haversineDistanceTo(center) <= radiusKm);
    }

    private Mono<NetworkAlert> findOrError(UUID tenantId, UUID alertId) {
        return repository.findById(tenantId, alertId)
                .switchIfEmpty(Mono.error(new NetworkAlertDomainException("Alert not found: " + alertId)));
    }
}
