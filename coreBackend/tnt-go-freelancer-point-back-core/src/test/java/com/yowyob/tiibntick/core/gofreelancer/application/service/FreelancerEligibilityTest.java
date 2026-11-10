package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.FreelancerEligibilityDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.FreelancerEligibilityDTO.Slot;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.FreelancerEligibilityUseCase.Parcel;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.FreelancerEligibilityRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.exception.ValidationException;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.FreelancerEligibility;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FreelancerEligibilityTest {

    private static final UUID DP = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    private final Map<UUID, FreelancerEligibility> rows = new ConcurrentHashMap<>();
    private FreelancerEligibilityService service;

    @BeforeEach
    void setUp() {
        FreelancerEligibilityRepository repo = mock(FreelancerEligibilityRepository.class);
        when(repo.findByDeliveryPersonId(any())).thenAnswer(i -> Mono.justOrEmpty(rows.get((UUID) i.getArgument(0))));
        when(repo.save(any())).thenAnswer(i -> {
            FreelancerEligibility r = i.getArgument(0);
            rows.put(r.getDeliveryPersonId(), r);
            return Mono.just(r);
        });
        GofpFreelancerRepository freelancers = mock(GofpFreelancerRepository.class);
        GofpFreelancer f = new GofpFreelancer();
        f.setCoreFreelancerId(DP);
        f.setCoreUserId(USER);
        when(freelancers.findByCoreFreelancerId(DP)).thenReturn(Mono.just(f));
        when(freelancers.findByCoreFreelancerId(org.mockito.ArgumentMatchers.argThat(
                id -> id != null && !id.equals(DP)))).thenReturn(Mono.empty());
        when(freelancers.findById(any(UUID.class))).thenReturn(Mono.empty());
        service = new FreelancerEligibilityService(repo, new FreelancerOwnershipGuard(freelancers));
    }

    private static FreelancerEligibilityDTO prefs() {
        return FreelancerEligibilityDTO.builder()
                .availability(List.of(
                        Slot.builder().day("MONDAY").start("08:00").end("18:00").active(true).build(),
                        Slot.builder().day("SATURDAY").start("09:00").end("13:00").active(false).build()))
                .acceptedPackages(List.of("standard", "fragile"))
                .minWeightKg(1.0).maxWeightKg(20.0).build();
    }

    // ── persistence ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("the stored eligibility round-trips, package codes upper-cased")
    void roundTrip() {
        StepVerifier.create(service.replaceAsCaller(DP, prefs(), USER, false)).expectNextCount(1).verifyComplete();

        FreelancerEligibilityDTO read = service.get(DP).block();
        assertThat(read.getAcceptedPackages()).containsExactly("STANDARD", "FRAGILE");
        assertThat(read.getMinWeightKg()).isEqualTo(1.0);
        assertThat(read.getMaxWeightKg()).isEqualTo(20.0);
        assertThat(read.getAvailability()).hasSize(2);
        assertThat(read.getAvailability().get(0).getDay()).isEqualTo("MONDAY");
        assertThat(read.getAvailability().get(1).isActive()).isFalse();
    }

    @Test
    @DisplayName("a second PUT replaces the first on the same row")
    void replaceKeepsOneRow() {
        service.replaceAsCaller(DP, prefs(), USER, false).block();
        service.replaceAsCaller(DP, FreelancerEligibilityDTO.builder().maxWeightKg(5.0).build(), USER, false).block();

        assertThat(rows).hasSize(1);
        FreelancerEligibilityDTO read = service.get(DP).block();
        assertThat(read.getMaxWeightKg()).isEqualTo(5.0);
        assertThat(read.getAcceptedPackages()).isNull();
    }

    @Test
    @DisplayName("nothing stored: GET is empty")
    void emptyWhenNeverConfigured() {
        assertThat(service.get(DP).block()).isNull();
    }

    @Test
    @DisplayName("only the owning user or an admin may write")
    void ownership() {
        StepVerifier.create(service.replaceAsCaller(DP, prefs(), UUID.randomUUID(), false))
                .expectError(AccessDeniedException.class).verify();
        StepVerifier.create(service.replaceAsCaller(DP, prefs(), null, false))
                .expectError(AccessDeniedException.class).verify();
        assertThat(rows).isEmpty();
        StepVerifier.create(service.replaceAsCaller(DP, prefs(), UUID.randomUUID(), true))
                .expectNextCount(1).verifyComplete();
    }

    @Test
    @DisplayName("inconsistent weight range or a slot ending before it starts is rejected")
    void validation() {
        assertThatThrownBy(() -> service.replaceAsCaller(DP,
                FreelancerEligibilityDTO.builder().minWeightKg(10.0).maxWeightKg(5.0).build(), USER, false).block())
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.replaceAsCaller(DP, FreelancerEligibilityDTO.builder()
                .availability(List.of(Slot.builder().day("MONDAY").start("18:00").end("08:00").active(true).build()))
                .build(), USER, false).block())
                .isInstanceOf(ValidationException.class);
        assertThat(rows).isEmpty();
    }

    // ── matching rules ───────────────────────────────────────────────────────

    private boolean eligible(double kg, boolean fragile, boolean perishable, DayOfWeek day) {
        service.replaceAsCaller(DP, prefs(), USER, false).block();
        return service.isEligible(List.of(DP), new Parcel(kg, fragile, perishable, day)).block();
    }

    @Test
    @DisplayName("accepted packages: fragile needs FRAGILE, perishable needs PERISHABLE, plain needs STANDARD/DOCUMENT")
    void packageRules() {
        assertThat(eligible(5, false, false, null)).isTrue();
        assertThat(eligible(5, true, false, null)).isTrue();
        assertThat(eligible(5, false, true, null)).as("PERISHABLE not accepted").isFalse();
        assertThat(eligible(5, true, true, null)).as("perishable part not accepted").isFalse();
    }

    @Test
    @DisplayName("a freelancer who only takes fragile parcels does not get plain ones")
    void onlyFragile() {
        service.replaceAsCaller(DP, FreelancerEligibilityDTO.builder().acceptedPackages(List.of("FRAGILE")).build(),
                USER, false).block();

        assertThat(service.isEligible(List.of(DP), new Parcel(2, false, false, null)).block()).isFalse();
        assertThat(service.isEligible(List.of(DP), new Parcel(2, true, false, null)).block()).isTrue();
    }

    @Test
    @DisplayName("weight range is inclusive, and an unweighed parcel is never excluded")
    void weightRules() {
        assertThat(eligible(0.5, false, false, null)).isFalse();
        assertThat(eligible(1.0, false, false, null)).isTrue();
        assertThat(eligible(20.0, false, false, null)).isTrue();
        assertThat(eligible(20.1, false, false, null)).isFalse();
        assertThat(eligible(0.0, false, false, null)).isTrue();
    }

    @Test
    @DisplayName("working days: an inactive or unlisted day excludes, an unknown pickup day never does")
    void dayRules() {
        assertThat(eligible(5, false, false, DayOfWeek.MONDAY)).isTrue();
        assertThat(eligible(5, false, false, DayOfWeek.SATURDAY)).as("inactive slot").isFalse();
        assertThat(eligible(5, false, false, DayOfWeek.WEDNESDAY)).as("not listed").isFalse();
        assertThat(eligible(5, false, false, null)).as("no deadline on the need").isTrue();
    }

    @Test
    @DisplayName("no stored preference means no constraint")
    void noPreferenceMeansEligible() {
        assertThat(service.isEligible(List.of(DP), new Parcel(500, true, true, DayOfWeek.SUNDAY)).block()).isTrue();
    }

    @Test
    @DisplayName("an unreadable preference never drops the freelancer")
    void failureKeepsEligible() {
        FreelancerEligibilityRepository broken = mock(FreelancerEligibilityRepository.class);
        when(broken.findByDeliveryPersonId(any())).thenReturn(Mono.error(new IllegalStateException("db down")));
        FreelancerEligibilityService s = new FreelancerEligibilityService(broken, mock(FreelancerOwnershipGuard.class));

        assertThat(s.isEligible(List.of(DP), new Parcel(500, true, true, null)).block()).isTrue();
    }

    @Test
    @DisplayName("corrupt JSON in a column is ignored rather than failing the read")
    void corruptJsonIgnored() {
        rows.put(DP, FreelancerEligibility.builder().deliveryPersonId(DP)
                .availabilityJson("not json").acceptedPackagesJson("[").maxWeightKg(3.0).build());

        FreelancerEligibilityDTO read = service.get(DP).block();
        assertThat(read.getAvailability()).isNull();
        assertThat(read.getAcceptedPackages()).isNull();
        assertThat(read.getMaxWeightKg()).isEqualTo(3.0);
    }
}
