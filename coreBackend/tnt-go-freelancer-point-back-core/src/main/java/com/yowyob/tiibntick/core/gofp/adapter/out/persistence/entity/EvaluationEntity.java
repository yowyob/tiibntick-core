package com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity;

import com.yowyob.tiibntick.common.persistence.TntPersistableEntity;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Table("gofp.evaluations")
public class EvaluationEntity implements Persistable<UUID>, TntPersistableEntity {

    @Transient @Builder.Default private boolean isNew = true;

    @Override
    public boolean isNew() { return isNew; }

    @Override
    public void markNotNew() { this.isNew = false; }

    @Id private UUID id;

    @Column("delivery_id")          private UUID    deliveryId;
    @Column("evaluator_actor_id")   private UUID    evaluatorActorId;
    @Column("evaluated_actor_id")   private UUID    evaluatedActorId;
    @Column("evaluation_type")      private String  evaluationType;

    private Integer rating;
    private String  comment;

    @Column("created_at") private Instant createdAt;
}
