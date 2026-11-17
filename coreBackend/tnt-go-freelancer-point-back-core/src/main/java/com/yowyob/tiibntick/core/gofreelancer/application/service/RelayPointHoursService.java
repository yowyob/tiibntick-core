package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.OpeningHoursDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.RelayPointVisualsRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IOpeningHoursRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.OpeningHours;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.RelayPointVisuals;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.DayOfWeek;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Opening hours and photos of a relay point, keyed by its hub id. The hours are replaced as a whole:
 * at most one slot per day, and an open day closes after it opens.
 */
@Service
@RequiredArgsConstructor
public class RelayPointHoursService {

    private final IOpeningHoursRepository hours;
    private final RelayPointVisualsRepository visuals;

    public Flux<OpeningHours> hoursOf(UUID hubId) {
        return hours.findAllByRelayPointId(hubId)
                .sort(Comparator.comparing(OpeningHours::getDayOfWeek,
                        Comparator.nullsLast(Comparator.<DayOfWeek>naturalOrder())));
    }

    public Flux<OpeningHours> replaceHours(UUID hubId, List<OpeningHoursDTO> week) {
        List<OpeningHoursDTO> slots = week == null ? List.of() : week;
        Set<DayOfWeek> seen = EnumSet.noneOf(DayOfWeek.class);
        for (OpeningHoursDTO slot : slots) {
            if (slot == null || slot.getDayOfWeek() == null) {
                return Flux.error(new IllegalArgumentException("Each opening slot needs a day of the week"));
            }
            if (!seen.add(slot.getDayOfWeek())) {
                return Flux.error(new IllegalArgumentException("One opening slot per day: " + slot.getDayOfWeek()));
            }
            boolean closed = Boolean.TRUE.equals(slot.getIsClosed());
            if (!closed && (slot.getOpenTime() == null || slot.getCloseTime() == null
                    || !slot.getOpenTime().isBefore(slot.getCloseTime()))) {
                return Flux.error(new IllegalArgumentException(
                        "An open day needs an opening time before its closing time: " + slot.getDayOfWeek()));
            }
        }
        return hours.deleteAllByRelayPointId(hubId)
                .thenMany(Flux.fromIterable(slots).concatMap(slot -> hours.save(OpeningHours.builder()
                        .id(UUID.randomUUID())
                        .relayPointId(hubId)
                        .dayOfWeek(slot.getDayOfWeek())
                        .openTime(Boolean.TRUE.equals(slot.getIsClosed()) ? null : slot.getOpenTime())
                        .closeTime(Boolean.TRUE.equals(slot.getIsClosed()) ? null : slot.getCloseTime())
                        .isClosed(Boolean.TRUE.equals(slot.getIsClosed()))
                        .build())))
                .thenMany(Flux.defer(() -> hoursOf(hubId)));
    }

    public Mono<RelayPointVisuals> visualsOf(UUID hubId) {
        return visuals.findByCoreRelayPointId(hubId);
    }
}
