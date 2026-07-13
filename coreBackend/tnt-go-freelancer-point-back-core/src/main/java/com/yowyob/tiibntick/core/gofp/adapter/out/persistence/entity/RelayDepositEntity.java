package com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Table("gofp.relay_deposits")
public class RelayDepositEntity {

    @Id private UUID id;

    @Column("packet_id")              private UUID    packetId;
    @Column("delivery_id")            private UUID    deliveryId;
    @Column("client_actor_id")        private UUID    clientActorId;
    @Column("relay_hub_id")           private UUID    relayHubId;
    @Column("freelancer_actor_id")    private UUID    freelancerActorId;

    private String  status;

    @Column("storage_fee")             private Double  storageFee;
    @Column("penalty_fee")             private Double  penaltyFee;
    @Column("grace_period_days")       private Integer gracePeriodDays;
    @Column("deposited_at")            private Instant depositedAt;
    @Column("expected_retrieval_at")   private Instant expectedRetrievalAt;
    @Column("retrieved_at")            private Instant retrievedAt;

    @Column("created_at") private Instant createdAt;
    @Column("updated_at") private Instant updatedAt;
}
