package com.yowyob.tiibntick.core.gofreelancer.application.port.in;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.FreelancerEligibilityDTO;
import reactor.core.publisher.Mono;

import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;

/**
 * What a delivery person accepts to carry and when — stored in the Core so the "Politiques" screen
 * survives a browser change and candidate matching can honour it.
 *
 * @author MANFOUO BRAUN
 */
public interface FreelancerEligibilityUseCase {

    /** The stored eligibility, or empty when the freelancer never configured any (no constraint). */
    Mono<FreelancerEligibilityDTO> get(UUID deliveryPersonId);

    /** Replaces the eligibility; the caller must be the freelancer (or an admin). */
    Mono<FreelancerEligibilityDTO> replaceAsCaller(UUID deliveryPersonId, FreelancerEligibilityDTO request,
                                                   UUID callerId, boolean admin);

    /**
     * Whether the freelancer, known under any of {@code ids}, may be offered this parcel. A freelancer
     * with no stored eligibility, or whose lookup fails, is eligible: matching never drops someone
     * because of a missing or unreadable preference.
     */
    Mono<Boolean> isEligible(List<UUID> ids, Parcel parcel);

    /**
     * @param weightKg 0 when unknown (a weight range never excludes an unweighed parcel)
     * @param day      weekday of the requested pickup, {@code null} when the need has no deadline
     */
    record Parcel(double weightKg, boolean fragile, boolean perishable, DayOfWeek day) { }
}
