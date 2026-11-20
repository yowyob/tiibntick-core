package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Where a courier is and whether he takes new jobs (lot C-52).
 *
 * <ul>
 *   <li>Position: written by the courier himself ({@code /me/location}, profile resolved from the
 *       token) or, on the historical {@code by-core-freelancer/{id}} route, by that courier or staff
 *       ({@code TNT_ADMIN} / {@code SUPPORT_AGENT}); anyone else gets 403.</li>
 *   <li>Availability: {@code AVAILABLE} or {@code PAUSED}, chosen by the courier only.</li>
 * </ul>
 * The owner is always derived from the caller, never from the request.
 */
@Service
@RequiredArgsConstructor
public class CourierPresenceService {

    private final GofpFreelancerRepository freelancers;

    /** Position of the caller's own courier profile. */
    public Mono<Void> updateMyLocation(TntSecurityContext ctx, Double latitude, Double longitude) {
        return validated(latitude, longitude)
                .then(myProfile(ctx))
                .flatMap(f -> writeLocation(f, latitude, longitude));
    }

    /** Historical route: only that courier himself or staff. Unknown courier → 404 (after the 403 check). */
    public Mono<Void> updateLocationOf(UUID coreFreelancerId, TntSecurityContext ctx,
                                       Double latitude, Double longitude) {
        UUID caller = ctx != null ? ctx.userId() : null;
        if (caller == null) {
            return Mono.error(new AccessDeniedException("An authenticated courier is required"));
        }
        Mono<Void> allowed = RelayPointAccessGuard.isStaff(ctx)
                ? Mono.empty()
                : freelancers.findByCoreUserId(caller)
                        .filter(me -> coreFreelancerId.equals(me.getCoreFreelancerId())
                                || coreFreelancerId.equals(me.getId()))
                        .switchIfEmpty(Mono.error(new AccessDeniedException(
                                "A courier may only update his own position")))
                        .then();
        return validated(latitude, longitude)
                .then(allowed)
                .then(freelancers.findByCoreFreelancerId(coreFreelancerId)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException(
                                "GofpFreelancer not found for coreFreelancerId: " + coreFreelancerId))))
                .flatMap(f -> writeLocation(f, latitude, longitude));
    }

    /** The caller's availability: {@code AVAILABLE} or {@code PAUSED}. */
    public Mono<String> myAvailability(TntSecurityContext ctx) {
        return myProfile(ctx).map(f -> f.isPaused()
                ? GofpFreelancer.AVAILABILITY_PAUSED : GofpFreelancer.AVAILABILITY_AVAILABLE);
    }

    public Mono<String> setMyAvailability(TntSecurityContext ctx, String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(java.util.Locale.ROOT);
        if (!GofpFreelancer.AVAILABILITY_AVAILABLE.equals(normalized)
                && !GofpFreelancer.AVAILABILITY_PAUSED.equals(normalized)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "status must be AVAILABLE or PAUSED"));
        }
        return myProfile(ctx).flatMap(f -> {
            f.setAvailabilityStatus(normalized);
            f.setUpdatedAt(Instant.now());
            return freelancers.save(f);
        }).thenReturn(normalized);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    static Mono<Void> validated(Double latitude, Double longitude) {
        if (latitude == null || longitude == null
                || !Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "latitude must be within [-90, 90] and longitude within [-180, 180]"));
        }
        return Mono.empty();
    }

    private Mono<GofpFreelancer> myProfile(TntSecurityContext ctx) {
        UUID caller = ctx != null ? ctx.userId() : null;
        if (caller == null) {
            return Mono.error(new AccessDeniedException("An authenticated courier is required"));
        }
        return freelancers.findByCoreUserId(caller)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Courier profile not found for the caller")));
    }

    private Mono<Void> writeLocation(GofpFreelancer f, Double latitude, Double longitude) {
        f.setLatitudeGps(latitude.floatValue());
        f.setLongitudeGps(longitude.floatValue());
        f.setUpdatedAt(Instant.now());
        return freelancers.save(f).then();
    }
}
