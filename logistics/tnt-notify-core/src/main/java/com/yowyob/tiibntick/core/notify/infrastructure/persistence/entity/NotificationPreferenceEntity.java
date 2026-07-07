package com.yowyob.tiibntick.core.notify.infrastructure.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
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
public class NotificationPreferenceEntity {

    @Id
    @Column("utilisateur_id")
    private String userId;
    @Column("canals_actifs_csv")
    private String activeChannelsCsv;
    @Column("langue_preferee")
    private String preferredLanguage;
    @Column("notifications_activees")
    private Boolean notificationsEnabled;
}
