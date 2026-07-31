package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import com.yowyob.tiibntick.common.persistence.TntPersistableEntity;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.UUID;

/**
 * Entité de persistance pour les adresses physiques.
 * Mappe le Value Object Address du Core vers la table 'addresses'.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("addresses")
public class AddressEntity implements Persistable<UUID>, TntPersistableEntity {
    @Transient @Builder.Default private boolean isNew = true;

    @Override
    public boolean isNew() { return isNew; }

    @Override
    public void markNotNew() { this.isNew = false; }


    @Id
    @Column("id")
    private UUID id;

    // Le type peut être null si c'est une adresse générique,
    // ou "HOME", "WORK" etc. si défini par l'utilisateur.
    @Column("type")
    private String type;

    @Column("street")
    private String street;

    /** Mapped to legacy {@code description} column (human landmark / free text). */
    @Column("description")
    private String landmark;

    /** Mapped to legacy {@code district} column (quarter / neighborhood). */
    @Column("district")
    private String quarter;

    @Column("city")
    private String city;

    @Column("region")
    private String region;

    @Column("country")
    private String country;

    @Column("postal_code")
    private String postalCode;

    @Column("latitude")
    private Double latitude;

    @Column("longitude")
    private Double longitude;
}
