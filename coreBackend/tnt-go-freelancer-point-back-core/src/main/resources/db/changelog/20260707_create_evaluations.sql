CREATE TABLE evaluations (
    id UUID PRIMARY KEY,
    delivery_id UUID NOT NULL,
    evaluator_id UUID NOT NULL,
    evaluated_id UUID NOT NULL,
    rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
    comment TEXT,
    type VARCHAR(50) NOT NULL, -- e.g., 'CLIENT_RATING_DELIVERY_PERSON' or 'DELIVERY_PERSON_RATING_CLIENT'
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_evaluations_delivery FOREIGN KEY (delivery_id) REFERENCES deliveries(id),
    CONSTRAINT fk_evaluations_evaluator FOREIGN KEY (evaluator_id) REFERENCES persons(id),
    CONSTRAINT fk_evaluations_evaluated FOREIGN KEY (evaluated_id) REFERENCES persons(id),
    CONSTRAINT uq_evaluation_per_delivery_and_type UNIQUE (delivery_id, type)
);
