package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AddressDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AddressUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.query.NearbyCriteria;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

/**
 * D'où regarde un livreur. Les paramètres explicites de la requête gagnent ; sinon la
 * dernière position GPS connue du profil gofp ({@code PATCH /api/freelancers/{id}/location}) et la
 * ville de sa base opérationnelle (à défaut, de sa résidence). Rien de connu → critères vides →
 * toutes les annonces ouvertes.
 *
 * <p>Rayon par défaut : {@code tnt.gofp.announcements.nearby-radius-km} (15 km).
 */
@Slf4j
@Service
public class FreelancerWhereaboutsResolver {

    private final GofpFreelancerRepository freelancers;
    private final AddressUseCase addresses;
    private final double defaultRadiusKm;

    public FreelancerWhereaboutsResolver(
            GofpFreelancerRepository freelancers,
            AddressUseCase addresses,
            @Value("${tnt.gofp.announcements.nearby-radius-km:15}") double defaultRadiusKm) {
        if (!(defaultRadiusKm > 0)) {
            throw new IllegalArgumentException("tnt.gofp.announcements.nearby-radius-km must be > 0");
        }
        this.freelancers = freelancers;
        this.addresses = addresses;
        this.defaultRadiusKm = defaultRadiusKm;
    }

    public Mono<NearbyCriteria> resolve(Double lat, Double lng, Double radiusKm, String city, UUID callerUserId) {
        if ((lat == null) != (lng == null)) {
            return Mono.error(new IllegalArgumentException("lat and lng must be given together"));
        }
        if (radiusKm != null && !(radiusKm > 0)) {
            return Mono.error(new IllegalArgumentException("radiusKm must be > 0"));
        }
        double radius = radiusKm != null ? radiusKm : defaultRadiusKm;
        if (lat != null && city != null && !city.isBlank()) {
            return Mono.just(new NearbyCriteria(lat, lng, radius, city));
        }
        return storedProfile(callerUserId)
                .flatMap(profile -> storedCity(profile).map(storedCity -> {
                    Double useLat = lat != null ? lat : asDouble(profile.getLatitudeGps());
                    Double useLng = lng != null ? lng : asDouble(profile.getLongitudeGps());
                    if (useLat == null || useLng == null) { useLat = null; useLng = null; }
                    String useCity = city != null && !city.isBlank() ? city : storedCity.orElse(null);
                    return new NearbyCriteria(useLat, useLng, radius, useCity);
                }))
                .defaultIfEmpty(new NearbyCriteria(lat, lng, radius, city));
    }

    private Mono<GofpFreelancer> storedProfile(UUID callerUserId) {
        if (callerUserId == null) return Mono.empty();
        return freelancers.findByCoreUserId(callerUserId)
                .switchIfEmpty(Mono.defer(() -> freelancers.findById(callerUserId)))
                .onErrorResume(e -> {
                    log.warn("[nearby] freelancer profile lookup failed for {}: {}", callerUserId, e.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<Optional<String>> storedCity(GofpFreelancer profile) {
        UUID addressId = profile.getOperationalBaseAddressId() != null
                ? profile.getOperationalBaseAddressId() : profile.getResidenceAddressId();
        if (addressId == null) return Mono.just(Optional.empty());
        return addresses.getAddressById(addressId)
                .map(AddressDTO::getAddress)
                .map(a -> Optional.ofNullable(a.getCity()))
                .defaultIfEmpty(Optional.empty())
                .onErrorReturn(Optional.empty());
    }

    private static Double asDouble(Float f) {
        return f != null ? f.doubleValue() : null;
    }
}
