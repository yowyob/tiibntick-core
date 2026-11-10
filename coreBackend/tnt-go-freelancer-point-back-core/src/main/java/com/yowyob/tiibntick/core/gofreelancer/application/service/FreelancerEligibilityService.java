package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.FreelancerEligibilityDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.FreelancerEligibilityUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.FreelancerEligibilityRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.exception.ValidationException;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.FreelancerEligibility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * @author MANFOUO BRAUN
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FreelancerEligibilityService implements FreelancerEligibilityUseCase {

    // A local mapper on purpose: the columns are an internal storage format, not the HTTP contract.
    private static final ObjectMapper JSON = new ObjectMapper();

    private final FreelancerEligibilityRepository repository;
    private final FreelancerOwnershipGuard ownershipGuard;

    @Override
    public Mono<FreelancerEligibilityDTO> get(UUID deliveryPersonId) {
        return repository.findByDeliveryPersonId(deliveryPersonId).map(this::toDto);
    }

    @Override
    public Mono<FreelancerEligibilityDTO> replaceAsCaller(UUID deliveryPersonId, FreelancerEligibilityDTO request,
                                                          UUID callerId, boolean admin) {
        return ownershipGuard.check(deliveryPersonId, callerId, admin)
                .then(Mono.defer(() -> {
                    validate(request);
                    return repository.findByDeliveryPersonId(deliveryPersonId)
                            .defaultIfEmpty(FreelancerEligibility.builder().deliveryPersonId(deliveryPersonId).build())
                            .flatMap(row -> {
                                row.setAvailabilityJson(write(request.getAvailability()));
                                row.setAcceptedPackagesJson(write(normalize(request.getAcceptedPackages())));
                                row.setMinWeightKg(request.getMinWeightKg());
                                row.setMaxWeightKg(request.getMaxWeightKg());
                                return repository.save(row);
                            })
                            .map(this::toDto);
                }));
    }

    @Override
    public Mono<Boolean> isEligible(List<UUID> ids, Parcel parcel) {
        return Flux.fromIterable(new LinkedHashSet<>(ids))
                .concatMap(repository::findByDeliveryPersonId)
                .next()
                .map(row -> FreelancerEligibilityRules.accepts(toDto(row), parcel))
                .defaultIfEmpty(true)
                .onErrorResume(e -> {
                    log.warn("Eligibility lookup failed for {} — keeping the freelancer eligible", ids, e);
                    return Mono.just(true);
                });
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static void validate(FreelancerEligibilityDTO r) {
        if (r.getMinWeightKg() != null && r.getMaxWeightKg() != null && r.getMinWeightKg() > r.getMaxWeightKg()) {
            throw new ValidationException("minWeightKg must not exceed maxWeightKg");
        }
        if (r.getAvailability() != null) {
            for (FreelancerEligibilityDTO.Slot s : r.getAvailability()) {
                if (s.getDay() == null || s.getStart() == null || s.getEnd() == null) {
                    throw new ValidationException("Every availability slot needs day, start and end");
                }
                if (s.isActive() && !LocalTime.parse(s.getStart()).isBefore(LocalTime.parse(s.getEnd()))) {
                    throw new ValidationException("Slot " + s.getDay() + ": start must be before end");
                }
            }
        }
    }

    private static List<String> normalize(List<String> codes) {
        if (codes == null) return null;
        return new ArrayList<>(new LinkedHashSet<>(codes.stream()
                .filter(c -> c != null && !c.isBlank()).map(c -> c.trim().toUpperCase(Locale.ROOT)).toList()));
    }

    private FreelancerEligibilityDTO toDto(FreelancerEligibility row) {
        return FreelancerEligibilityDTO.builder()
                .availability(read(row.getAvailabilityJson(), new TypeReference<List<FreelancerEligibilityDTO.Slot>>() { }))
                .acceptedPackages(read(row.getAcceptedPackagesJson(), new TypeReference<List<String>>() { }))
                .minWeightKg(row.getMinWeightKg())
                .maxWeightKg(row.getMaxWeightKg())
                .build();
    }

    private static String write(Object value) {
        if (value == null) return null;
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot serialise eligibility", e);
        }
    }

    private static <T> T read(String json, TypeReference<T> type) {
        if (json == null || json.isBlank()) return null;
        try {
            return JSON.readValue(json, type);
        } catch (Exception e) {
            log.warn("Unreadable eligibility JSON ignored: {}", e.getMessage());
            return null;
        }
    }
}
