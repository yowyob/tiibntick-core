package com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Table("gofp.payments")
public class PaymentEntity {

    @Id private UUID id;

    @Column("delivery_id")          private UUID    deliveryId;
    @Column("freelancer_actor_id")  private UUID    freelancerActorId;
    @Column("client_actor_id")      private UUID    clientActorId;

    @Column("gross_amount")         private Double  grossAmount;
    @Column("commission_amount")    private Double  commissionAmount;
    @Column("net_amount")           private Double  netAmount;
    @Column("commission_percent")   private Double  commissionPercent;
    @Column("subscription_type")    private String  subscriptionType;
    @Column("payment_method")       private String  paymentMethod;

    private String  status;

    @Column("transaction_reference") private String  transactionReference;
    @Column("paid_at")               private Instant paidAt;
    @Column("created_at")            private Instant createdAt;
    @Column("updated_at")            private Instant updatedAt;
}
