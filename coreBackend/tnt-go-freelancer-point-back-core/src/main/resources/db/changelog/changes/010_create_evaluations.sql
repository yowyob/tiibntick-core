-- liquibase formatted sql
-- changeset francois:gofp-010 labels:tnt-go-freelancer-point-back-core
-- comment: Évaluations post-livraison (client note livreur, livreur note client)

CREATE TABLE IF NOT EXISTS gofp.evaluations (
    id                      UUID        NOT NULL DEFAULT gen_random_uuid(),
    delivery_id             UUID        NOT NULL,  -- → gofp.deliveries.id
    evaluator_actor_id      UUID        NOT NULL,  -- → tnt-actor-core (client ou livreur)
    evaluated_actor_id      UUID        NOT NULL,  -- → tnt-actor-core (livreur ou client)
    evaluation_type         VARCHAR(50) NOT NULL,  -- CLIENT_RATES_FREELANCER | FREELANCER_RATES_CLIENT
    rating                  INTEGER     NOT NULL,
    comment                 TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_gofp_evaluations          PRIMARY KEY (id),
    CONSTRAINT fk_gofp_eval_delivery        FOREIGN KEY (delivery_id) REFERENCES gofp.deliveries(id),
    CONSTRAINT uq_gofp_eval_per_delivery    UNIQUE (delivery_id, evaluation_type),
    CONSTRAINT chk_gofp_eval_rating         CHECK (rating >= 1 AND rating <= 5),
    CONSTRAINT chk_gofp_eval_type           CHECK (evaluation_type IN (
        'CLIENT_RATES_FREELANCER', 'FREELANCER_RATES_CLIENT'
    ))
);
