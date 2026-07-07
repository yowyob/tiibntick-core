package com.yowyob.tiibntick.core.delivery.adapter.out.persistence.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC persistence entity for {@code AnnouncementResponse}.
 *
 * @author MANFOUO Braun
 */
@Table("tnt_announcement_responses")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnnouncementResponseEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;

    @Column("announcement_id")
    private UUID announcementId;

    @Column("delivery_person_id")
    private UUID deliveryPersonId;

    @Column("estimated_arrival_time")
    private Instant estimatedArrivalTime;

    @Column("note")
    private String note;

    @Column("status")
    private String status;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    @Version
    @Column("version")
    private Long version;
}
