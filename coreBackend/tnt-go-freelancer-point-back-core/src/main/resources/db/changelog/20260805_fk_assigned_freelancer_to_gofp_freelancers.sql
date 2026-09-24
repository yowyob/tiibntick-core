-- liquibase formatted sql

-- changeset TiiBnTickTeam:20260805-fk-assigned-freelancer-to-gofp-freelancers
-- Le changeset 069 a créé delivery_needs.assigned_freelancer_id sans contrainte, faute de
-- savoir dans quel espace d'identité vivait la valeur. C'est désormais tranché :
-- assignFreelancer() reçoit le même id que celui produit par le matching, à savoir
-- gofp_freelancers.id (voir FreelancerProviderAdapter#fromGofp -> candidate.freelancerId).
--
-- Les valeurs orphelines (id ne correspondant à aucun freelancer réel — jeux de test manuels
-- écrits avant cette contrainte) sont remises à NULL : elles ne désignent rien d'exploitable,
-- et la colonne est nullable par construction (un besoin PENDING n'a pas de freelancer).
UPDATE delivery_needs dn
SET assigned_freelancer_id = NULL
WHERE dn.assigned_freelancer_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM gofp_freelancers f WHERE f.id = dn.assigned_freelancer_id);

-- ON DELETE SET NULL : supprimer un freelancer ne doit jamais supprimer le besoin du client,
-- le besoin repasse simplement sans assignation.
ALTER TABLE delivery_needs
    DROP CONSTRAINT IF EXISTS fk_delivery_needs_assigned_freelancer;

ALTER TABLE delivery_needs
    ADD CONSTRAINT fk_delivery_needs_assigned_freelancer
        FOREIGN KEY (assigned_freelancer_id) REFERENCES gofp_freelancers (id) ON DELETE SET NULL;
