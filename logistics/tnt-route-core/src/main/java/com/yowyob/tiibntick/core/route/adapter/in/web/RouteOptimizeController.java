package com.yowyob.tiibntick.core.route.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * REST adapter for route optimisation consumed by tnt-agency ({@code RouteCoreClient}).
 */
@RestController
@RequestMapping("/api/v1/routes")
public class RouteOptimizeController {

    @PostMapping("/optimize")
    @ResponseStatus(HttpStatus.OK)
    public Mono<OptimizeResponse> optimize(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestBody OptimizeRequest request) {
        List<StopDto> ordered = nearestNeighborOrder(request.stops());
        double totalKm = estimateDistanceKm(ordered);
        int durationMin = ordered.stream()
                .mapToInt(StopDto::serviceTimeMinutes)
                .sum() + (int) Math.ceil(totalKm * 2.5);
        return Mono.just(new OptimizeResponse(ordered, totalKm, durationMin));
    }

    private static List<StopDto> nearestNeighborOrder(List<StopDto> stops) {
        if (stops == null || stops.isEmpty()) {
            return List.of();
        }
        List<StopDto> remaining = new ArrayList<>(stops);
        List<StopDto> ordered = new ArrayList<>();
        StopDto cursor = remaining.removeFirst();
        ordered.add(cursor);
        while (!remaining.isEmpty()) {
            final StopDto from = cursor;
            StopDto next = remaining.stream()
                    .min(Comparator.comparingDouble(s -> haversineKm(from, s)))
                    .orElseThrow();
            remaining.remove(next);
            ordered.add(next);
            cursor = next;
        }
        return ordered;
    }

    private static double estimateDistanceKm(List<StopDto> ordered) {
        if (ordered.size() < 2) {
            return 0.0;
        }
        double total = 0.0;
        for (int i = 1; i < ordered.size(); i++) {
            total += haversineKm(ordered.get(i - 1), ordered.get(i));
        }
        return Math.round(total * 100.0) / 100.0;
    }

    private static double haversineKm(StopDto a, StopDto b) {
        double lat1 = Math.toRadians(a.latitude());
        double lat2 = Math.toRadians(b.latitude());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b.longitude() - a.longitude());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371.0 * 2 * Math.asin(Math.sqrt(h));
    }

    public record OptimizeRequest(
            UUID coreMissionId,
            UUID agencyId,
            UUID vehicleId,
            List<StopDto> stops
    ) {}

    public record StopDto(
            String address,
            double latitude,
            double longitude,
            int serviceTimeMinutes
    ) {}

    public record OptimizeResponse(
            List<StopDto> orderedStops,
            double totalDistanceKm,
            int estimatedDurationMinutes
    ) {}
}
