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
 * Entité de domaine locale stockant les références vers les documents
 * d'inscription (CNI, NUI, etc.) du Freelancer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("gofreelancer_onboarding_docs")
public class FreelancerOnboardingDocs implements Persistable<UUID>, TntPersistableEntity {
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

    @Column("cni_recto_url")
    private String cniRectoUrl;

    @Column("cni_verso_url")
    private String cniVersoUrl;

    @Column("photo_card_url")
    private String photoCardUrl;

    @Column("commercial_register_url")
    private String commercialRegisterUrl;

    @Column("nui_photo_url")
    private String nuiPhotoUrl;
}
