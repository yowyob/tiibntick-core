package com.yowyob.tiibntick.core.gofreelancer.domain.model;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Read-only view of an announcement as {@code tnt-delivery-core} holds it, in the shape the
 * Go-Freelancer services historically read. It is NOT persisted: the former local mirror table
 * {@code announcements} is no longer written or read; every field comes from the core announcement
 * (see {@code CoreAnnouncementReadAdapter}).
 *
 * <p>Names are folded the way the core stores them: the core keeps one sender name and one recipient
 * name, so first/last names are the first word and the rest of that name.
 *
 * @author François-Charles ATANGA
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Announcement {

    private UUID id;

    /** The client who published this announcement. */
    private UUID clientId;

    private String title;
    private String description;
    private AnnouncementStatus status;

    private Integer duration;
    private Double distance;
    private String transportMethod;
    private String requiredVehicleType;

    /** The delivery person whose response was selected, once assigned. */
    private UUID assignedFreelancerId;

    /** Besoin de livraison Go dont cette annonce est la publication (or {@code null}). */
    private UUID deliveryNeedId;

    private UUID destinationRelayPointId;

    private String shipperFirstName;
    private String shipperLastName;
    private String shipperEmail;
    private String shipperPhone;

    private String recipientFirstName;
    private String recipientLastName;
    private String recipientEmail;
    private String recipientPhone;

    private Double amount;
    private String currency;
    private Double logisticsPrice;
    private String paymentMethod;
    private String signatureUrl;

    private Instant createdAt;
    private Instant updatedAt;
}
