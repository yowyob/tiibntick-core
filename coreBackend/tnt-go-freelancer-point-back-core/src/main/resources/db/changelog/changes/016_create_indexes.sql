-- liquibase formatted sql
-- changeset francois:gofp-016 labels:tnt-go-freelancer-point-back-core
-- comment: Index de performance pour les requêtes Market fréquentes

-- Annonces
CREATE INDEX IF NOT EXISTS idx_gofp_ann_client       ON gofp.announcements (client_actor_id);
CREATE INDEX IF NOT EXISTS idx_gofp_ann_status       ON gofp.announcements (status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_gofp_ann_relay_hub    ON gofp.announcements (relay_hub_id) WHERE relay_hub_id IS NOT NULL;

-- Livraisons
CREATE INDEX IF NOT EXISTS idx_gofp_del_ann          ON gofp.deliveries (announcement_id);
CREATE INDEX IF NOT EXISTS idx_gofp_del_freelancer   ON gofp.deliveries (freelancer_actor_id);
CREATE INDEX IF NOT EXISTS idx_gofp_del_status       ON gofp.deliveries (status, created_at DESC);

-- Candidatures
CREATE INDEX IF NOT EXISTS idx_gofp_ann_subs_ann     ON gofp.announcement_subscriptions (announcement_id, status);
CREATE INDEX IF NOT EXISTS idx_gofp_ann_subs_fl      ON gofp.announcement_subscriptions (freelancer_actor_id, status);

-- Abonnements
CREATE INDEX IF NOT EXISTS idx_gofp_sub_freelancer   ON gofp.subscriptions (freelancer_actor_id);
CREATE INDEX IF NOT EXISTS idx_gofp_sub_status       ON gofp.subscriptions (status, reset_date);

-- Paiements
CREATE INDEX IF NOT EXISTS idx_gofp_pay_delivery     ON gofp.payments (delivery_id);
CREATE INDEX IF NOT EXISTS idx_gofp_pay_freelancer   ON gofp.payments (freelancer_actor_id, created_at DESC);

-- Dépôts relais
CREATE INDEX IF NOT EXISTS idx_gofp_rd_hub           ON gofp.relay_deposits (relay_hub_id, status);
CREATE INDEX IF NOT EXISTS idx_gofp_rd_client        ON gofp.relay_deposits (client_actor_id);

-- Évaluations
CREATE INDEX IF NOT EXISTS idx_gofp_eval_delivery    ON gofp.evaluations (delivery_id);
CREATE INDEX IF NOT EXISTS idx_gofp_eval_evaluated   ON gofp.evaluations (evaluated_actor_id);

-- Tarification
CREATE INDEX IF NOT EXISTS idx_gofp_dp_pricing       ON gofp.delivery_person_pricing (freelancer_actor_id);
CREATE INDEX IF NOT EXISTS idx_gofp_log_pricing      ON gofp.logistics_pricing (relay_hub_id);

-- Disponibilité
CREATE INDEX IF NOT EXISTS idx_gofp_dp_avail         ON gofp.delivery_person_availability (freelancer_actor_id, is_available);
CREATE INDEX IF NOT EXISTS idx_gofp_dp_avail_loc     ON gofp.delivery_person_availability (current_lat, current_lon)
    WHERE current_lat IS NOT NULL AND is_available = TRUE;

-- Extensions
CREATE INDEX IF NOT EXISTS idx_gofp_fl_ext           ON gofp.freelancer_extensions (freelancer_actor_id);
CREATE INDEX IF NOT EXISTS idx_gofp_rh_ext           ON gofp.relay_hub_extensions (relay_hub_id);
