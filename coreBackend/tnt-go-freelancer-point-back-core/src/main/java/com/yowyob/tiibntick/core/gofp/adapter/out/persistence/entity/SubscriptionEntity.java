package com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity;

import com.yowyob.tiibntick.common.persistence.TntPersistableEntity;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Table("gofp.subscriptions")
public class SubscriptionEntity implements Persistable<UUID>, TntPersistableEntity {

    @Transient @Builder.Default private boolean isNew = true;

    @Override
    public boolean isNew() { return isNew; }

    @Override
    public void markNotNew() { this.isNew = false; }

    @Id private UUID id;

    @Column("freelancer_actor_id") private UUID    freelancerActorId;
    @Column("subscription_type")   private String  subscriptionType;

    private String  status;

    @Column("start_date")       private Instant startDate;
    @Column("end_date")         private Instant endDate;

    private Double  price;

    @Column("payment_method")   private String  paymentMethod;
    @Column("monthly_quota")    private Integer monthlyQuota;
    @Column("deliveries_used")  private Integer deliveriesUsed;
    @Column("reset_date")       private Instant resetDate;

    @Column("created_at") private Instant createdAt;
    @Column("updated_at") private Instant updatedAt;
}
