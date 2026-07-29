package com.yowyob.tiibntick.core.gofreelancer.domain.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entité de domaine locale stockant les favoris des utilisateurs
 * et l'état d'affichage personnalisé d'une annonce (UI Display Status).
 * Remplace les propriétés d'affichage de l'ancien BffAnnouncementEntity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("gofreelancer_user_bookmarks")
public class UserBookmark {

    @Id
    @Column("id")
    private UUID id;

    // L'identifiant de l'utilisateur qui a mis l'annonce en favori
    @Column("user_id")
    private UUID userId;

    // Référence vers l'annonce officielle du Core
    @Column("core_announcement_id")
    private UUID coreAnnouncementId;

    // Statut d'affichage personnalisé (ex: "En transit vers vous", "Bientôt là")
    @Column("ui_display_status")
    private String uiDisplayStatus;

    // Indique explicitement s'il est favori (peut être omis si la simple présence du record indique le favori)
    @Column("is_bookmarked")
    private Boolean isBookmarked;
}
