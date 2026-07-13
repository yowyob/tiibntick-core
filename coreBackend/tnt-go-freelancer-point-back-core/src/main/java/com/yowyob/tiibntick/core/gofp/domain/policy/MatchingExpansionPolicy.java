package com.yowyob.tiibntick.core.gofp.domain.policy;

import java.util.List;

/**
 * Politique d'expansion géographique du matching Market.
 *
 * Si aucun livreur disponible n'est trouvé dans le rayon initial,
 * le rayon est élargi par paliers jusqu'au rayon maximum.
 *
 * Paliers : 1.5 km → 3 km → 5 km → 10 km
 * Au-delà de 10 km, on arrête et on retourne une liste vide.
 */
public final class MatchingExpansionPolicy {

    private MatchingExpansionPolicy() {}

    /** Rayon initial de recherche en km. */
    public static final double INITIAL_RADIUS_KM = 1.5;

    /** Rayon maximum de recherche en km. */
    public static final double MAX_RADIUS_KM = 10.0;

    /** Paliers d'expansion en km. */
    public static final List<Double> EXPANSION_STEPS_KM = List.of(1.5, 3.0, 5.0, 10.0);

    /** Nombre minimum de candidats souhaités avant d'arrêter l'expansion. */
    public static final int MIN_CANDIDATES_THRESHOLD = 3;

    /**
     * Retourne le prochain rayon à utiliser après un rayon donné.
     * Retourne null si le rayon maximum est déjà atteint.
     *
     * @param currentRadiusKm rayon courant
     * @return prochain rayon, ou null si on a épuisé tous les paliers
     */
    public static Double nextRadius(double currentRadiusKm) {
        for (int i = 0; i < EXPANSION_STEPS_KM.size() - 1; i++) {
            if (Math.abs(EXPANSION_STEPS_KM.get(i) - currentRadiusKm) < 0.01) {
                return EXPANSION_STEPS_KM.get(i + 1);
            }
        }
        return null; // rayon max atteint
    }

    /**
     * Retourne true si l'expansion doit se poursuivre.
     * On continue si le nombre de candidats trouvés est insuffisant
     * et qu'il reste des paliers disponibles.
     *
     * @param candidatesFound nombre de candidats trouvés au rayon courant
     * @param currentRadiusKm rayon courant
     */
    public static boolean shouldExpand(int candidatesFound, double currentRadiusKm) {
        if (candidatesFound >= MIN_CANDIDATES_THRESHOLD) return false;
        return nextRadius(currentRadiusKm) != null;
    }
}
