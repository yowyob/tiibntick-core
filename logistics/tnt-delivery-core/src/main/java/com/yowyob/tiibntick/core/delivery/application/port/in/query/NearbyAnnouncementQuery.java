package com.yowyob.tiibntick.core.delivery.application.port.in.query;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Ce que l'on sait de l'endroit où se trouve un livreur, pour lui montrer les
 * annonces ouvertes de sa zone.
 *
 * @param latitude  dernière position connue, ou {@code null}
 * @param longitude dernière position connue, ou {@code null}
 * @param radiusKm  rayon de visibilité autour de la position (km, &gt; 0)
 * @param city      ville du livreur (profil), ou {@code null} — sert de repli quand une position
 *                  manque (côté livreur ou côté annonce)
 */
public record NearbyAnnouncementQuery(Double latitude, Double longitude, double radiusKm, String city) {

    public NearbyAnnouncementQuery {
        if (!(radiusKm > 0)) {
            throw new IllegalArgumentException("radiusKm must be > 0, got " + radiusKm);
        }
        if ((latitude == null) != (longitude == null)) {
            throw new IllegalArgumentException("latitude and longitude go together");
        }
        if (latitude != null && (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180)) {
            throw new IllegalArgumentException("coordinates out of range");
        }
    }

    public boolean hasPoint() {
        return latitude != null;
    }

    /** Ville normalisée pour comparaison : minuscules, sans accents, sans espaces de bord ; {@code ""} si inconnue. */
    public String normalizedCity() {
        return normalizeCity(city);
    }

    /** Ni position ni ville : rien ne permet de filtrer, toutes les annonces ouvertes sont visibles. */
    public boolean knowsNothing() {
        return !hasPoint() && normalizedCity().isEmpty();
    }

    public static String normalizeCity(String raw) {
        if (raw == null) return "";
        String stripped = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return stripped.toLowerCase(Locale.ROOT);
    }
}
