-- Proofs of a delivery's hand-overs: photo at pickup, photo / signature at drop-off.
-- delivery_id : gofp delivery (deliveries.id); no FK so a proof survives a purge of the working row
-- file_id     : id returned by POST /api/v1/media/evidence (Kernel file storage); the bytes never transit here
-- uploaded_by : actor id (JWT) of the delivery person who captured it, never taken from the request body
-- captured_at : when the photo/signature was taken on the device (may be hours before it reaches the Core: offline queue)
-- (delivery_id, file_id) is unique so replaying an offline queue never duplicates a proof.
CREATE TABLE IF NOT EXISTS gofp_delivery_proofs (
    id           UUID PRIMARY KEY,
    delivery_id  UUID         NOT NULL,
    kind         VARCHAR(20)  NOT NULL,
    file_id      UUID         NOT NULL,
    note         TEXT,
    captured_at  TIMESTAMP    NOT NULL,
    uploaded_by  UUID         NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_gofp_delivery_proof_kind CHECK (kind IN ('PICKUP_PHOTO', 'DELIVERY_PHOTO', 'SIGNATURE', 'OTHER')),
    CONSTRAINT uq_gofp_delivery_proof_file UNIQUE (delivery_id, file_id)
);
CREATE INDEX IF NOT EXISTS idx_gofp_delivery_proofs_delivery
    ON gofp_delivery_proofs (delivery_id, captured_at);
