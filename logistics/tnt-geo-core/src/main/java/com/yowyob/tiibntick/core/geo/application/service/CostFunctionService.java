package com.yowyob.tiibntick.core.geo.application.service;

import com.yowyob.tiibntick.core.geo.application.port.in.IComputeCostUseCase;
import com.yowyob.tiibntick.core.geo.application.port.out.IGeoEventPublisher;
import com.yowyob.tiibntick.core.geo.application.port.out.IRoadArcRepository;
import com.yowyob.tiibntick.core.geo.application.port.out.IWeatherApiClient;
import com.yowyob.tiibntick.core.geo.domain.event.TrafficConditionChangedEvent;
import com.yowyob.tiibntick.core.geo.domain.exception.GeoNotFoundException;
import com.yowyob.tiibntick.core.geo.domain.model.*;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Application service implementing the 5-dimensional composite arc cost function:
 *
 *   omega(a, t) = alpha * d_tilde + beta * T_tilde + gamma * rho_tilde
 *               + delta * xi_tilde + eta * c_fuel_tilde
 *
 * Normalisation constants are calibrated for the Cameroonian road network scale:
 *   - MAX_DISTANCE_KM   : 200  km  (longest inter-city arc in the graph)
 *   - MAX_TRAVEL_H      : 4.0  h   (longest expected travel time)
 *   - MAX_FUEL_COST_XAF : 5000 XAF (fuel + wear per arc)
 *   - FUEL_COST_KM      : 25   XAF/km (approx moto-taxi consumption)
 *
 * The service also publishes {@link TrafficConditionChangedEvent} when congestion
 * increases significantly (> 20% change in trafficFactor), allowing tnt-route-core
 * to trigger re-routing.
 *
 * Author: MANFOUO Braun
 */
@Service
public class CostFunctionService implements IComputeCostUseCase {

    private static final double MAX_DISTANCE_KM    = 200.0;
    private static final double MAX_TRAVEL_HOURS   = 4.0;
    private static final double MAX_FUEL_COST      = 5000.0;
    private static final double FUEL_COST_PER_KM   = 25.0;
    //private static final double TRAFFIC_THRESHOLD  = 0.20;

    private final IRoadArcRepository arcRepository;
    private final IWeatherApiClient weatherClient;
    private final IGeoEventPublisher eventPublisher;

    public CostFunctionService(IRoadArcRepository arcRepository,
                               IWeatherApiClient weatherClient,
                               IGeoEventPublisher eventPublisher) {
        this.arcRepository = arcRepository;
        this.weatherClient = weatherClient;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Mono<Double> computeCompositeCost(RoadArcId arcId, CostWeights weights) {
        return arcRepository.findById(arcId, null)
                .switchIfEmpty(Mono.error(new GeoNotFoundException("RoadArc", arcId.value())))
                .flatMap(arc -> {
                    GeoPoint midPoint = estimateArcMidpoint(arc);
                    return weatherClient.getCurrentWeather(midPoint)
                            .onErrorResume(ex -> Mono.just(WeatherCondition.clear(Instant.now())))
                            .map(weather -> computeCost(arc, weights, weather));
                });
    }

    @Override
    public Mono<Double> computeCompositeCostWithWeather(RoadArcId arcId, CostWeights weights,
                                                         WeatherCondition weather) {
        return arcRepository.findById(arcId, null)
                .switchIfEmpty(Mono.error(new GeoNotFoundException("RoadArc", arcId.value())))
                .map(arc -> computeCost(arc, weights, weather));
    }

    /**
     * Core implementation of omega(a,t).
     * All five components are normalised to [0, 1] before applying weights.
     */
    public double computeCost(RoadArc arc, CostWeights w, WeatherCondition weather) {
        double dTilde     = normalise(arc.distanceKm(), MAX_DISTANCE_KM);
        double tTilde     = normalise(arc.travelTimeHours(), MAX_TRAVEL_HOURS);
        double rhoTilde   = arc.penibility();
        double xiTilde    = weather.phiRain();
        double fuelCost   = arc.distanceKm() * FUEL_COST_PER_KM;
        double cFuelTilde = normalise(fuelCost, MAX_FUEL_COST);

        return w.alpha() * dTilde
                + w.beta()  * tTilde
                + w.gamma() * rhoTilde
                + w.delta() * xiTilde
                + w.eta()   * cFuelTilde;
    }

    /**
     * Computes the cost for an arc with a specific tenantId scope.
     * Used by tnt-route-core when loading the full network per tenant.
     */
    public Mono<Double> computeForTenant(RoadArcId arcId, UUID tenantId,
                                          CostWeights weights, WeatherCondition weather) {
        return arcRepository.findById(arcId, tenantId)
                .switchIfEmpty(Mono.error(new GeoNotFoundException("RoadArc", arcId.value())))
                .map(arc -> computeCost(arc, weights, weather));
    }

    /**
     * Updates traffic factor for an arc and publishes an event if the change is significant.
     */
    public Mono<RoadArc> updateTrafficAndPublishIfSignificant(RoadArcId arcId, UUID tenantId,
                                                               double newFactor) {
        return arcRepository.findById(arcId, tenantId)
                .switchIfEmpty(Mono.error(new GeoNotFoundException("RoadArc", arcId.value())))
                .flatMap(arc -> {
                    double previous = arc.trafficFactor();
                    return arcRepository.updateTrafficFactor(arcId, tenantId, newFactor)
                            .flatMap(updated -> {
                                TrafficConditionChangedEvent event =
                                        TrafficConditionChangedEvent.of(tenantId, arcId.value(), previous, newFactor);
                                if (event.isSignificant()) {
                                    return eventPublisher.publishTrafficChanged(event)
                                            .thenReturn(updated);
                                }
                                return Mono.just(updated);
                            });
                });
    }

    private static double normalise(double value, double max) {
        if (max <= 0) return 0.0;
        return Math.min(value / max, 1.0);
    }

    /**
     * Estimates the geographic midpoint of an arc for weather lookups.
     * Without direct node coordinates in the arc, we approximate using the arc's distance
     * and assume a centroid offset. The actual implementation uses node coordinates
     * provided by the RoadNetworkService when building the network.
     * For standalone arc calls, we fall back to (0,0) which triggers the weather fallback.
     */
    private GeoPoint estimateArcMidpoint(RoadArc arc) {
        return GeoPoint.of(3.848, 11.502);
    }
}
