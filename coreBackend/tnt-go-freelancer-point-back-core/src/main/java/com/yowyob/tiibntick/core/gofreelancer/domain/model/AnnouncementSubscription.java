package com.yowyob.tiibntick.core.gofreelancer.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import com.yowyob.tiibntick.common.persistence.TntPersistableEntity;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("announcement_subscriptions")
public class AnnouncementSubscription implements Persistable<UUID>, TntPersistableEntity {
    @Transient
    private boolean isNew = true;

    @Override
    public boolean isNew() { return isNew; }

    @Override
    public void markNotNew() { this.isNew = false; }


    @Id
    @Column("id")
    private UUID id;

    @Column("announcement_id")
    private UUID announcementId;

    /** Legacy DB column is {@code delivery_person_id}; keep Java name for domain clarity. */
    @Column("delivery_person_id")
    private UUID freelancerId;

    @Column("status")
    private String status;

    @Column("created_at")
    private Instant createdAt;
}
