package com.yowyob.tiibntick.core.gofreelancer.domain.model;

import com.yowyob.tiibntick.common.persistence.TntPersistableEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.time.Instant;
import java.util.UUID;

/** A photo or signature captured by the delivery person as evidence of a hand-over. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("gofp_delivery_proofs")
public class DeliveryProof implements Persistable<UUID>, TntPersistableEntity {

    public enum Kind { PICKUP_PHOTO, DELIVERY_PHOTO, SIGNATURE, OTHER }

    @Transient @Builder.Default private boolean isNew = true;

    @Override public boolean isNew() { return isNew; }
    @Override public void markNotNew() { this.isNew = false; }

    @Id
    @Column("id")
    private UUID id;

    @Column("delivery_id")
    private UUID deliveryId;

    @Column("kind")
    private String kind;

    /** Kernel file id returned by the media evidence upload. */
    @Column("file_id")
    private UUID fileId;

    @Column("note")
    private String note;

    @Column("captured_at")
    private Instant capturedAt;

    @Column("uploaded_by")
    private UUID uploadedBy;

    @Column("created_at")
    private Instant createdAt;
}
