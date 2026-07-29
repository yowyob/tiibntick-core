package com.yowyob.tiibntick.core.gofreelancer.domain.model;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.payment.PaymentMethod;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.subscription.SubscriptionStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.subscription.SubscriptionType;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import com.yowyob.tiibntick.common.persistence.TntPersistableEntity;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("subscriptions")
public class Subscription implements Persistable<UUID>, TntPersistableEntity {
    @Transient
    private boolean isNew = true;

    @Override
    public boolean isNew() { return isNew; }

    @Override
    public void markNotNew() { this.isNew = false; }


    @Id
    @Column("id")
    private UUID id;

    @NotNull
    @Column("freelancer_id")
    private UUID freelancerId;

    @NotNull
    @Column("subscription_type")
    private SubscriptionType subscriptionType;

    @NotNull
    @Column("status")
    private SubscriptionStatus status;

    @NotNull
    @Column("start_date")
    private Instant startDate;

    @Column("end_date")
    private Instant endDate;

    @NotNull
    @Column("price")
    private Float price;

    @Column("currency")
    private String currency;

    @NotNull
    @Column("payment_method")
    private PaymentMethod paymentMethod;

    @Column("deliveries_used")
    private Integer deliveriesUsed;

    @Column("reset_date")
    private Instant resetDate;
}
