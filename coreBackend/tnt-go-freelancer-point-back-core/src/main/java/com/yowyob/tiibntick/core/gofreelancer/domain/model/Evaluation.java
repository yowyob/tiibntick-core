package com.yowyob.tiibntick.core.gofreelancer.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import com.yowyob.tiibntick.common.persistence.TntPersistableEntity;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("evaluations")
public class Evaluation implements Persistable<UUID>, TntPersistableEntity {
    @Transient @Builder.Default private boolean isNew = true;

    @Override
    public boolean isNew() { return isNew; }

    @Override
    public void markNotNew() { this.isNew = false; }


    @Id
    private UUID id;

    @Column("delivery_id")
    private UUID deliveryId;

    @Column("evaluator_id")
    private UUID evaluatorId;

    @Column("evaluated_id")
    private UUID evaluatedId;

    @Column("rating")
    private Integer rating;

    @Column("comment")
    private String comment;

    @Column("type")
    private String type;

    @Column("created_at")
    private LocalDateTime createdAt;
}
