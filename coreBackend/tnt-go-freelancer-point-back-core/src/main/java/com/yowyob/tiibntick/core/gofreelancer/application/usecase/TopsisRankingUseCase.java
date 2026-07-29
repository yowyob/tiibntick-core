package com.yowyob.tiibntick.core.gofreelancer.application.usecase;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.vehicle.VehicleType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * TOPSIS (Technique for Order of Preference by Similarity to Ideal Solution)
 * ranking service for freelancer candidates — Layer 6.
 *
 * Criteria weighted by AHP (aligned with ATANGA):
 *   C1 — distance to pickup          (weight 0.558) — minimise   (COST)
 *   C2 — rating                      (weight 0.264) — maximise   (BENEFIT)
 *   C3 — available trunk volume (m³) (weight 0.122) — maximise   (BENEFIT)
 *   C4 — vehicle type score          (weight 0.057) — maximise   (BENEFIT)
 *
 * The hard pre-filter (availableTrunkVolumeM3 >= packetVolumeM3) is applied
 * BEFORE calling rankCandidates() by MatchingUseCase, so every candidate
 * reaching TOPSIS already has enough space.
 *
 * Optional vehicle-type filter (requiredVehicleType):
 *   - null / absent  → all candidates ranked; vehicle score is relative
 *   - set            → candidates without that type are eliminated first;
 *                      survivors all get vehicleTypeScore = 1.0 (criterion neutral)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TopsisRankingUseCase {

    private static final double EARTH_RADIUS_KM = 6371.0;

    // AHP weights — aligned with ATANGA, must sum to 1.0
    private static final double W_DISTANCE     = 0.558;
    private static final double W_RATING       = 0.264;
    private static final double W_CAPACITY     = 0.122;  // available trunk volume in m³
    private static final double W_VEHICLE_TYPE = 0.057;  // ordinal score by vehicle type

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Ranks without vehicle-type filter — all types accepted.
     * Vehicle-type score is computed relatively among candidates.
     */
    public Mono<List<FreelancerCandidate>> rankCandidates(
            List<FreelancerCandidate> candidates,
            double pickupLat,
            double pickupLon) {
        return rankCandidates(candidates, pickupLat, pickupLon, null);
    }

    /**
     * Ranks with optional vehicle-type filter.
     *
     * @param candidates          list of spatially eligible candidates
     * @param pickupLat           pickup latitude
     * @param pickupLon           pickup longitude
     * @param requiredVehicleType type required by the client (null = any)
     * @return sorted list, best candidate first; empty if no eligible survivor
     */
    public Mono<List<FreelancerCandidate>> rankCandidates(
            List<FreelancerCandidate> candidates,
            double pickupLat,
            double pickupLon,
            String requiredVehicleType) {

        if (candidates == null || candidates.isEmpty()) {
            return Mono.just(List.of());
        }

        VehicleType requiredType = parseVehicleType(requiredVehicleType);

        // ── Vehicle-type pre-filter ────────────────────────────────────────────
        List<FreelancerCandidate> filtered = requiredType == null
                ? candidates
                : candidates.stream()
                        .filter(c -> {
                            VehicleType ct = parseVehicleType(c.getVehicleType());
                            boolean match = requiredType == ct;
                            if (!match) log.debug("TOPSIS: candidate {} eliminated — type {} required, found {}",
                                    c.getFreelancerId(), requiredType, ct);
                            return match;
                        })
                        .toList();

        if (filtered.isEmpty()) {
            log.warn("TOPSIS: no candidates match required vehicle type {} among {} candidates",
                    requiredType, candidates.size());
            return Mono.just(List.of());
        }

        if (filtered.size() == 1) {
            filtered.get(0).setTopsisScore(1.0);
            return Mono.just(filtered);
        }

        return Mono.fromCallable(() -> applyTopsis(filtered, pickupLat, pickupLon, requiredType));
    }

    // ── TOPSIS algorithm ───────────────────────────────────────────────────────

    private List<FreelancerCandidate> applyTopsis(
            List<FreelancerCandidate> candidates,
            double pickupLat, double pickupLon,
            VehicleType requiredType) {

        int n = candidates.size();

        // ── Step 1 : Build raw decision matrix ─────────────────────────────────
        double[][] matrix = new double[n][4];
        for (int i = 0; i < n; i++) {
            FreelancerCandidate c = candidates.get(i);
            matrix[i][0] = haversine(pickupLat, pickupLon, c.getLatitude(), c.getLongitude()); // C1
            matrix[i][1] = c.getRating() != null ? c.getRating() : 0.0;                        // C2
            matrix[i][2] = c.getAvailableTrunkVolumeM3() != null
                    ? c.getAvailableTrunkVolumeM3() : 0.0;                                     // C3
            // C4 — vehicle type ordinal score (same scale as ATANGA)
            // When a type is required and all survivors have it, score = 1.0 for all → neutral
            matrix[i][3] = requiredType != null ? 1.0 : vehicleTypeScore(c.getVehicleType());  // C4
        }

        // ── Step 2 : Normalise (vector normalisation) ──────────────────────
        double[] colNorm = new double[4];
        for (int j = 0; j < 4; j++) {
            double sumSq = 0;
            for (int i = 0; i < n; i++) sumSq += matrix[i][j] * matrix[i][j];
            colNorm[j] = Math.sqrt(sumSq);
        }

        double[][] norm = new double[n][4];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < 4; j++) {
                norm[i][j] = colNorm[j] == 0 ? 0 : matrix[i][j] / colNorm[j];
            }
        }

        // ── Step 3 : Weighted normalised matrix ────────────────────────────
        double[] weights = {W_DISTANCE, W_RATING, W_CAPACITY, W_VEHICLE_TYPE};
        double[][] weighted = new double[n][4];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < 4; j++) {
                weighted[i][j] = norm[i][j] * weights[j];
            }
        }

        // ── Step 4 : Ideal best / worst ────────────────────────────────────
        // C1 (distance) is a cost criterion (lower = better)
        // C2, C3, C4 are benefit criteria (higher = better)
        double[] idealBest  = new double[4];
        double[] idealWorst = new double[4];

        // C1 — cost
        idealBest[0]  = Double.MAX_VALUE;
        idealWorst[0] = Double.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            if (weighted[i][0] < idealBest[0])  idealBest[0]  = weighted[i][0];
            if (weighted[i][0] > idealWorst[0]) idealWorst[0] = weighted[i][0];
        }

        // C2, C3, C4 — benefit
        for (int j = 1; j < 4; j++) {
            idealBest[j]  = Double.MIN_VALUE;
            idealWorst[j] = Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                if (weighted[i][j] > idealBest[j])  idealBest[j]  = weighted[i][j];
                if (weighted[i][j] < idealWorst[j]) idealWorst[j] = weighted[i][j];
            }
        }

        // ── Step 5 : Separation measures & TOPSIS score ───────────────────
        List<ScoredCandidate> scored = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double dBest = 0, dWorst = 0;
            for (int j = 0; j < 4; j++) {
                dBest  += Math.pow(weighted[i][j] - idealBest[j],  2);
                dWorst += Math.pow(weighted[i][j] - idealWorst[j], 2);
            }
            dBest  = Math.sqrt(dBest);
            dWorst = Math.sqrt(dWorst);

            double score = (dBest + dWorst) == 0 ? 0 : dWorst / (dBest + dWorst);
            scored.add(new ScoredCandidate(candidates.get(i), score));
        }

        // ── Step 6 : Sort descending by TOPSIS score ──────────────────────
        scored.sort(Comparator.comparingDouble(ScoredCandidate::score).reversed());

        List<FreelancerCandidate> result = new ArrayList<>();
        for (ScoredCandidate sc : scored) {
            sc.candidate().setTopsisScore(sc.score());
            result.add(sc.candidate());
            log.debug("TOPSIS: candidate={} score={:.4f}", sc.candidate().getFreelancerId(), sc.score());
        }

        log.info("TOPSIS ranked {} candidates for pickup ({},{})", n, pickupLat, pickupLon);
        return result;
    }

    // ── Haversine distance (km) ────────────────────────────────────────────────
    public static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.pow(Math.sin(dLon / 2), 2);
        return EARTH_RADIUS_KM * 2 * Math.asin(Math.sqrt(a));
    }

    // ── Vehicle type ordinal score (same scale as ATANGA) ─────────────────────
    /**
     * Maps a vehicle type string to an ordinal score for TOPSIS C4.
     * TRUCK > VAN > CAR > MOTORBIKE/SCOOTER > BIKE.
     * Returns 0.0 for null / unknown.
     */
    public static double vehicleTypeScore(String vehicleType) {
        if (vehicleType == null) return 0.0;
        return switch (vehicleType.toUpperCase()) {
            case "TRUCK"    -> 4.0;
            case "VAN"      -> 3.0;
            case "CAR"      -> 2.0;
            case "MOTORBIKE", "SCOOTER" -> 1.5;
            case "BIKE"     -> 1.0;
            default         -> 1.0;
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Parses a vehicle type string into VehicleType; returns null if absent or invalid. */
    private VehicleType parseVehicleType(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return VehicleType.fromValue(value);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown vehicle type '{}' — filter ignored", value);
            return null;
        }
    }

    // ── Inner DTO for TOPSIS ───────────────────────────────────────────────────
    private record ScoredCandidate(FreelancerCandidate candidate, double score) {}

    // ── Candidate DTO ─────────────────────────────────────────────────────────
    /**
     * Lightweight projection of a freelancer used exclusively inside TOPSIS.
     * Populated by MatchingUseCase before calling rankCandidates().
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    public static class FreelancerCandidate {
        private java.util.UUID freelancerId;
        private double latitude;
        private double longitude;
        /** Real average rating from GofpFreelancer.rating. Null-safe: treated as 0.0. */
        private Double rating;
        private Integer totalDeliveries;
        /** Remaining deliveries quota from GofpFreelancer. */
        private Integer remainingDeliveries;
        /** Vehicle type string (e.g. "CAR", "VAN") — used for C4 score and optional pre-filter. */
        private String vehicleType;
        /**
         * Available trunk volume in m³ = trunkTotalM3 - volumeOccupiedByActiveDeliveries.
         * Computed before calling rankCandidates() using FreelancerVehicle dimensions
         * (length * width * height, already in m) minus the sum of active-delivery packet volumes.
         * Used as criterion C3. Null-safe: treated as 0.0 if absent.
         */
        private Double availableTrunkVolumeM3;
        private Double topsisScore;       // filled in after ranking
    }
}
