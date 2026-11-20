package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Lot C-52 — who may write a courier's position, and the courier's availability switch. */
class CourierPresenceServiceTest {

    private final GofpFreelancerRepository repo = mock(GofpFreelancerRepository.class);
    private final CourierPresenceService service = new CourierPresenceService(repo);

    private final UUID meUser = UUID.randomUUID();
    private final UUID meCore = UUID.randomUUID();
    private final UUID otherUser = UUID.randomUUID();
    private final UUID otherCore = UUID.randomUUID();
    private GofpFreelancer me;
    private GofpFreelancer other;

    @BeforeEach
    void setUp() {
        me = GofpFreelancer.builder().id(UUID.randomUUID()).coreUserId(meUser).coreFreelancerId(meCore).build();
        other = GofpFreelancer.builder().id(UUID.randomUUID()).coreUserId(otherUser).coreFreelancerId(otherCore).build();
        when(repo.findByCoreUserId(meUser)).thenReturn(Mono.just(me));
        when(repo.findByCoreUserId(otherUser)).thenReturn(Mono.just(other));
        when(repo.findByCoreFreelancerId(meCore)).thenReturn(Mono.just(me));
        when(repo.findByCoreFreelancerId(otherCore)).thenReturn(Mono.just(other));
        when(repo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    private static TntSecurityContext user(UUID id, String... roles) {
        return TntSecurityContext.builder().userId(id).roles(Set.of(roles)).authenticated(true).build();
    }

    @Test
    @DisplayName("/me/location : le profil vient du jeton, la position est écrite")
    void myLocation_writesOwnProfile() {
        StepVerifier.create(service.updateMyLocation(user(meUser), 4.05, 9.76)).verifyComplete();
        assertThat(me.getLatitudeGps()).isEqualTo(4.05f);
        assertThat(me.getLongitudeGps()).isEqualTo(9.76f);
        assertThat(other.getLatitudeGps()).isNull();
    }

    @Test
    @DisplayName("route historique : soi-même → 200")
    void legacyRoute_self_ok() {
        StepVerifier.create(service.updateLocationOf(meCore, user(meUser), 4.0, 9.0)).verifyComplete();
        assertThat(me.getLatitudeGps()).isEqualTo(4.0f);
    }

    @Test
    @DisplayName("route historique : la position d'un autre livreur → 403, rien n'est écrit")
    void legacyRoute_other_forbidden() {
        StepVerifier.create(service.updateLocationOf(otherCore, user(meUser), 4.0, 9.0))
                .expectError(AccessDeniedException.class).verify();
        assertThat(other.getLatitudeGps()).isNull();
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("route historique : le personnel (TNT_ADMIN, SUPPORT_AGENT) → 200")
    void legacyRoute_staff_ok() {
        UUID admin = UUID.randomUUID();
        StepVerifier.create(service.updateLocationOf(otherCore, user(admin, "TNT_ADMIN"), 4.0, 9.0)).verifyComplete();
        StepVerifier.create(service.updateLocationOf(meCore, user(admin, "SUPPORT_AGENT"), 3.0, 9.0)).verifyComplete();
        assertThat(other.getLatitudeGps()).isEqualTo(4.0f);
        assertThat(me.getLatitudeGps()).isEqualTo(3.0f);
    }

    @Test
    @DisplayName("sans identité → 403")
    void anonymous_forbidden() {
        StepVerifier.create(service.updateLocationOf(meCore, null, 4.0, 9.0))
                .expectError(AccessDeniedException.class).verify();
        StepVerifier.create(service.updateMyLocation(null, 4.0, 9.0))
                .expectError(AccessDeniedException.class).verify();
    }

    @ParameterizedTest(name = "lat={0} lng={1} → 400")
    @CsvSource({"91,9", "-91,9", "4,181", "4,-181", "NaN,9", "4,NaN", "Infinity,9"})
    void bounds_rejected(double lat, double lng) {
        StepVerifier.create(service.updateMyLocation(user(meUser), lat, lng))
                .expectErrorMatches(e -> e instanceof ResponseStatusException r && r.getStatusCode().value() == 400)
                .verify();
        StepVerifier.create(service.updateLocationOf(meCore, user(meUser), lat, lng))
                .expectErrorMatches(e -> e instanceof ResponseStatusException r && r.getStatusCode().value() == 400)
                .verify();
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("statut : AVAILABLE par défaut, PAUSED puis AVAILABLE, valeur inconnue → 400")
    void availability_roundTrip() {
        StepVerifier.create(service.myAvailability(user(meUser))).expectNext("AVAILABLE").verifyComplete();
        StepVerifier.create(service.setMyAvailability(user(meUser), "paused")).expectNext("PAUSED").verifyComplete();
        assertThat(me.isPaused()).isTrue();
        StepVerifier.create(service.myAvailability(user(meUser))).expectNext("PAUSED").verifyComplete();
        StepVerifier.create(service.setMyAvailability(user(meUser), "BUSY"))
                .expectErrorMatches(e -> e instanceof ResponseStatusException r && r.getStatusCode().value() == 400)
                .verify();
        StepVerifier.create(service.setMyAvailability(user(meUser), "AVAILABLE")).expectNext("AVAILABLE").verifyComplete();
        assertThat(me.isPaused()).isFalse();
        assertThat(other.isPaused()).isFalse();
    }
}
