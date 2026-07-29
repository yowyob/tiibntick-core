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
 * Entité de domaine locale stockant les attributs spécifiques aux véhicules 
 * liés à l'onboarding Go-Freelancer (qui ne sont pas dans le core logistique).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("gofreelancer_vehicles")
public class FreelancerVehicle implements Persistable<UUID>, TntPersistableEntity {
    @Transient @Builder.Default private boolean isNew = true;

    @Override
    public boolean isNew() { return isNew; }

    @Override
    public void markNotNew() { this.isNew = false; }

    @Id
    @Column("id")
    private UUID id;

    @Column("freelancer_id")
    private UUID freelancerId;

    @Column("core_vehicle_id")
    private UUID coreVehicleId;

    @Column("front_photo_url")
    private String frontPhotoUrl;

    @Column("back_photo_url")
    private String backPhotoUrl;

    @Column("color_hex")
    private String colorHex;

    @Column("trunk_length")
    private Double trunkLength;

    @Column("trunk_width")
    private Double trunkWidth;

    @Column("trunk_height")
    private Double trunkHeight;

    @Column("trunk_dimension_unit")
    private String trunkDimensionUnit;
}
