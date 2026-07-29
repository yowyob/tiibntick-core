package com.yowyob.tiibntick.core.gofreelancer.adapter.out.geo;

import com.yowyob.tiibntick.core.geo.application.port.in.IGeocodeUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IGeolocationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Adaptateur de sortie géolocalisation.
 * Connecte le tnt-go-freelancer-point-back-core au module global tnt-geo-core.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeoCoreAdapter implements IGeolocationPort {

    private final IGeocodeUseCase geocodeUseCase;

    @Override
    public Mono<double[]> getCoordinatesFromAddress(String address) {
        log.debug("Appel à tnt-geo-core pour géocoder l'adresse: {}", address);
        
        return geocodeUseCase.geocode(address, null)
                .map(addressResult -> new double[]{
                        addressResult.coordinates().latitude(),
                        addressResult.coordinates().longitude()
                })
                .onErrorResume(e -> {
                    log.error("Erreur lors de la résolution de l'adresse: {}. Fallback sur les coordonnées par défaut.", address, e);
                    // Fallback coord pour éviter que le système ne crashe en cas de timeout
                    return Mono.just(new double[]{3.848, 11.502});
                });
    }
}
