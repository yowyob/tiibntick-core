package com.yowyob.tiibntick.core.notify.infrastructure.persistence.entity;

import com.yowyob.tiibntick.common.persistence.TntPersistableEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * R2DBC entity for user notification preferences.
 * Canal list serialized as CSV string for simplicity at this kernel layer.
 *
 * @author MANFOUO Braun
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("tnt_preference_notifications")
public class NotificationPreferenceEntity implements Persistable<String>, TntPersistableEntity {

    @Transient @Builder.Default private boolean isNew = true;

    @Override
    public boolean isNew() { return isNew; }

    @Override
    public void markNotNew() { this.isNew = false; }

    /** {@code @Id} is the natural key {@link #userId}, not a field literally named "id". */
    @Override
    public String getId() { return userId; }

    @Id
    @Column("utilisateur_id")
    private String userId;
    @Column("tenant_id")
    private String tenantId;
    @Column("organization_id")
    private String organizationId;
    @Column("canals_actifs_csv")
    private String activeChannelsCsv;
    @Column("langue_preferee")
    private String preferredLanguage;
    @Column("notifications_activees")
    private Boolean notificationsEnabled;
}
