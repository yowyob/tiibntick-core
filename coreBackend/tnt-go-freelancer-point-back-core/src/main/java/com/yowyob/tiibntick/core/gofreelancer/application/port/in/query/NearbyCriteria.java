package com.yowyob.tiibntick.core.gofreelancer.application.port.in.query;

/**
 * Où chercher les annonces d'un livreur : position (ou {@code null}), rayon, ville (ou
 * {@code null}). Sans position ni ville, toutes les annonces ouvertes sont visibles.
 */
public record NearbyCriteria(Double latitude, Double longitude, double radiusKm, String city) {
}
