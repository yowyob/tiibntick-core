package com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Table("gofp.announcement_subscriptions")
public class AnnouncementSubscriptionEntity {

    @Id private UUID id;

    @Column("announcement_id")     private UUID    announcementId;
    @Column("freelancer_actor_id") private UUID    freelancerActorId;

    private String  status;
    @Column("proposed_price") private Double  proposedPrice;
    private String  message;

    @Column("created_at") private Instant createdAt;
    @Column("updated_at") private Instant updatedAt;
}
