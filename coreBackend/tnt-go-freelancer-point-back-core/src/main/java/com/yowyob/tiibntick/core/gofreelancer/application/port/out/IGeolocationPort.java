package com.yowyob.tiibntick.core.gofreelancer.application.port.out;

import reactor.core.publisher.Mono;

/**
 * Port de sortie pour la géolocalisation.
 * Permet de récupérer les coordonnées exactes d'une adresse.
 */
public interface IGeolocationPort {
    
    /**
     * @param address l'adresse ou identifiant de lieu
     * @return un Mono contenant un tableau [latitude, longitude]
     */
    Mono<double[]> getCoordinatesFromAddress(String address);
}
