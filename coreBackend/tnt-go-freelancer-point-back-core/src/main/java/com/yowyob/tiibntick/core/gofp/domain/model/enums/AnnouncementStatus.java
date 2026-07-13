package com.yowyob.tiibntick.core.gofp.domain.model.enums;

/**
 * Cycle de vie d'une annonce TiiBnPick dans le produit Market.
 */
public enum AnnouncementStatus {

    /** Créée mais pas encore publiée. */
    DRAFT,

    /** Publiée et visible des livreurs. */
    PUBLISHED,

    /** Au moins un livreur a candidaté, négociation en cours. */
    IN_NEGOTIATION,

    /** Un livreur a été assigné, livraison en attente de démarrage. */
    ASSIGNED,

    /** Livraison en cours. */
    IN_PROGRESS,

    /** Livraison terminée avec succès. */
    DELIVERED,

    /** Annulée par le client ou le système. */
    CANCELLED,

    /** Délai d'expiration dépassé sans assignation. */
    EXPIRED;

    public static AnnouncementStatus fromValue(String value) {
        for (AnnouncementStatus s : values()) {
            if (s.name().equalsIgnoreCase(value)) return s;
        }
        throw new IllegalArgumentException("Unknown AnnouncementStatus: " + value);
    }
}
