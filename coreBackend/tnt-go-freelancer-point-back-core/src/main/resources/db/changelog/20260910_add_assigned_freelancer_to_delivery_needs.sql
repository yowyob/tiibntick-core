-- liquibase formatted sql

-- changeset TiiBnTickTeam:20260910-add-assigned-freelancer-to-delivery-needs
-- assignFreelancer() stockait le freelancer dans delivery_needs.delivery_id,
-- qui est une FK vers deliveries(id) — chaque appel réel échouait avec :
--   "violates foreign key constraint delivery_needs_delivery_id_fkey"
-- Le freelancer assigné dispose maintenant de sa propre colonne nullable, sans FK rigide,
-- calquée sur le pattern de announcements.assigned_freelancer_id.
-- delivery_id conserve son sens originel (la ligne deliveries créée au démarrage réel de la course).
ALTER TABLE delivery_needs ADD COLUMN IF NOT EXISTS assigned_freelancer_id UUID;

CREATE INDEX IF NOT EXISTS idx_delivery_needs_assigned_freelancer
    ON delivery_needs (assigned_freelancer_id);
