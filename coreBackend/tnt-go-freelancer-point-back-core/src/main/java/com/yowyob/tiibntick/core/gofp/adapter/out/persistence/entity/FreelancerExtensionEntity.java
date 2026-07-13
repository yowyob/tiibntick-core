package com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Table("gofp.freelancer_extensions")
public class FreelancerExtensionEntity {

    @Id private UUID id;

    @Column("freelancer_actor_id")   private UUID    freelancerActorId;
    @Column("commercial_register")   private String  commercialRegister;
    @Column("commercial_name")       private String  commercialName;
    @Column("taxpayer_number")       private String  taxpayerNumber;
    private String  nui;
    private String  siret;
    @Column("cni_front_photo")       private String  cniFrontPhoto;
    @Column("cni_back_photo")        private String  cniBackPhoto;
    @Column("profile_photo")         private String  profilePhoto;
    @Column("subscription_id")       private UUID    subscriptionId;
    @Column("remaining_deliveries")  private Integer remainingDeliveries;
    @Column("failed_deliveries")     private Integer failedDeliveries;
    @Column("total_deliveries")      private Integer totalDeliveries;
    @Column("is_active")             private Boolean isActive;

    @Column("created_at") private Instant createdAt;
    @Column("updated_at") private Instant updatedAt;
}
