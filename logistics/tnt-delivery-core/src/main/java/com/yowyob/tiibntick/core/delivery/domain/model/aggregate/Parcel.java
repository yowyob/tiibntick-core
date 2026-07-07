package com.yowyob.tiibntick.core.delivery.domain.model.aggregate;

import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryDomainException;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Aggregate root representing the physical parcel being delivered.
 * Encapsulates specifications and photo evidence for pickup/delivery proof.
 *
 * @author MANFOUO Braun
 */
@Getter
@Builder
public class Parcel {

    private final UUID id;
    private final PackageSpecification specification;
    private String photoUrl;
    private final Instant createdAt;
    private Instant updatedAt;
    private Long version;

    /**
     * Creates a new parcel with validation of required fields.
     */
    public static Parcel create(PackageSpecification specification) {
        if (specification == null) {
            throw new DeliveryDomainException("PackageSpecification is required to create a Parcel");
        }
        return Parcel.builder()
                .id(UUID.randomUUID())
                .specification(specification)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Attaches a photo URL proving physical existence of the parcel.
     */
    public void attachPhoto(String photoUrl) {
        if (photoUrl == null || photoUrl.isBlank()) {
            throw new DeliveryDomainException("Photo URL must not be blank");
        }
        this.photoUrl = photoUrl;
        this.updatedAt = Instant.now();
    }
}
