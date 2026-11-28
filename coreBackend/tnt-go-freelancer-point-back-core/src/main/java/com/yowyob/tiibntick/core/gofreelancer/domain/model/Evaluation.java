package com.yowyob.tiibntick.core.gofreelancer.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Evaluation as exposed by {@code /api/v1/evaluations}.
 *
 * <p>Lot L3 de-duplication: no longer a persisted entity. An evaluation is a tp-core third-party rating
 * ({@code tnt_tp_ratings}): {@code id} is the rating id, {@code deliveryId} its mission, {@code evaluatorId} and
 * {@code evaluatedId} are actor ids (core ids), {@code type} its rating context. The legacy {@code evaluations}
 * table is only read by the migration and dropped at closure.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Evaluation {

    private UUID id;

    private UUID deliveryId;

    /** Actor id of the rater. */
    private UUID evaluatorId;

    /** Actor id of the rated party. */
    private UUID evaluatedId;

    /** Score rounded to a whole star (1-5), as before. */
    private Integer rating;

    /** Exact score held by the core (1.0-5.0). Addition, optional. */
    private Double score;

    private String comment;

    private String type;

    /** Kind of the rated party: CLIENT, DELIVERY_PERSON or RELAY_OPERATOR. Addition, optional. */
    private String subjectKind;

    private LocalDateTime createdAt;
}
