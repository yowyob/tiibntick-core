package com.yowyob.tiibntick.core.gofreelancer.domain.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import com.yowyob.tiibntick.common.persistence.TntPersistableEntity;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entité de domaine locale stockant les attributs visuels et les messages 
 * d'un Point Relais pour l'application Go-Freelancer.
 * Remplace l'ancien BffRelayPointEntity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("gofreelancer_relay_point_visuals")
public class RelayPointVisuals implements Persistable<UUID>, TntPersistableEntity {
    @Transient @Builder.Default private boolean isNew = true;

    @Override
    public boolean isNew() { return isNew; }

    @Override
    public void markNotNew() { this.isNew = false; }


    @Id
    @Column("id")
    private UUID id;

    // L'ID du RelayHub dans tnt-geo-core (coreRelayPointId)
    @Column("core_relay_point_id")
    private UUID coreRelayPointId;

    // Photo de la devanture
    @Column("storefront_photo_url")
    private String storefrontPhotoUrl;

    // Photo de l'intérieur du magasin
    @Column("shop_photo_url")
    private String shopPhotoUrl;

    // Message laissé par le gérant (ex: "Fermeture exceptionnelle ce midi")
    @Column("absence_message")
    private String absenceMessage;
}
