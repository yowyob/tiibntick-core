package com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Table("gofp.relay_hub_extensions")
public class RelayHubExtensionEntity {

    @Id private UUID id;

    @Column("relay_hub_id")           private UUID   relayHubId;
    @Column("plate_number")           private String plateNumber;
    private String color;
    @Column("logistics_type")         private String logisticsType;
    @Column("logistics_class")        private String logisticsClass;
    @Column("logistic_image")         private String logisticImage;
    @Column("tank_capacity")          private Double tankCapacity;
    @Column("luggage_max_capacity")   private Double luggageMaxCapacity;
    @Column("total_seat_number")      private Integer totalSeatNumber;
    private Double rating;
    @Column("address_street")         private String addressStreet;
    @Column("address_city")           private String addressCity;
    @Column("address_district")       private String addressDistrict;
    @Column("address_country")        private String addressCountry;
    @Column("shop_photo")             private String shopPhoto;

    @Column("created_at") private Instant createdAt;
    @Column("updated_at") private Instant updatedAt;
}
