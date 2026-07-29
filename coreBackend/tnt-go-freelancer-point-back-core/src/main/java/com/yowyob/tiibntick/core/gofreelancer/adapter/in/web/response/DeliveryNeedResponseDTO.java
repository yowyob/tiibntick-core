package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.deliveryNeed.DeliveryNeedStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryNeedResponseDTO {
    private UUID id;
    private UUID userId;
    private UUID packetId;
    private UUID pickupAddressId;
    private UUID deliveryAddressId;
    private String title;
    private String description;
    private DeliveryNeedStatus status;
    private Integer duration;
    private String signatureUrl;
    private String paymentMethod;
    private String transportMethod;
    private Double distance;
    private UUID deliveryId;
    private Instant createdAt;
    private Instant updatedAt;
    private java.time.LocalDateTime pickupDeadline;
}
