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
 * Entité de domaine locale stockant les attributs purement visuels / UI 
 * propres à l'application Go-Freelancer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("gofreelancer_visual_profiles")
public class VisualProfile {
    @Id
    @Column("id")
    private UUID id;

    // Référence vers l'acteur du Core (Freelancer, Client, Person...)
    @Column("core_actor_id")
    private UUID coreActorId;

    @Column("avatar_url")
    private String avatarUrl;

    @Column("bio")
    private String bio;

    @Column("theme")
    private String theme;

    @Column("favorite_color_badge")
    private String favoriteColorBadge;

    @Column("last_login_ip")
    private String lastLoginIp;
}
