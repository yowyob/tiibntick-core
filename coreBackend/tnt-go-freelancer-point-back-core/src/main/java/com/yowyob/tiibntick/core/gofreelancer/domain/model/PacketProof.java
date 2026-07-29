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
 * Entité de domaine locale stockant la preuve visuelle liée à un paquet 
 * dans le contexte de l'application Freelancer.
 * Remplace l'ancien BffPacketEntity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("gofreelancer_packet_proofs")
public class PacketProof implements Persistable<UUID>, TntPersistableEntity {
    @Transient @Builder.Default private boolean isNew = true;

    @Override
    public boolean isNew() { return isNew; }

    @Override
    public void markNotNew() { this.isNew = false; }


    @Id
    @Column("id")
    private UUID id;

    // L'ID du paquet officiel dans le Core (tnt-delivery-core)
    @Column("core_packet_id")
    private UUID corePacketId;

    // Photo du paquet prise par l'expéditeur lors de la création de l'annonce
    @Column("cover_image_url")
    private String coverImageUrl;
}
