package com.yowyob.tiibntick.core.gofreelancer.domain.model;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.payment.PaymentMethod;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.payment.PaymentStatus;
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
@Table("payments")
public class Payment implements Persistable<UUID>, TntPersistableEntity {
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
    @Column("delivery_id")
    private UUID deliveryId;

    @NotNull
    @Column("amount")
    private Double amount;

    @Column("currency")
    private String currency;

    @NotNull
    @Column("payment_method")
    private PaymentMethod paymentMethod;

    @NotNull
    @Column("status")
    private PaymentStatus status;

    @Column("transaction_reference")
    private String transactionReference;

    @Column("paid_at")
    private Instant paidAt;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    @Column("commission_amount")
    private Double commissionAmount;

    @Column("net_amount")
    private Double netAmount;
}
