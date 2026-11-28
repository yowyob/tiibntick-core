-- gdd-l3-302 : Go evaluations -> tp-core ratings (3.9 e). The evaluations table stays until the closure.
--
-- id kept, mission_id = delivery_id, rating_context = type, score = rating, comment and creation date kept.
-- Evaluator and evaluated are converted to actor ids, as the former service resolved them:
--   freelancer (gofp_freelancers.id or core_freelancer_id)  -> core_user_id, subject DELIVERY_PERSON
--   client     (gofp_clients.id or core_client_id)           -> core_user_id, subject CLIENT
--   relay point(gofp_relay_points.id or core_relay_point_id) -> core_user_id of its owner freelancer,
--                                                               subject RELAY_OPERATOR
--   user       (gofp_users.id or core_user_id)                -> core_user_id (kind taken from the type)
-- Tenant: the one of the evaluated party's core profile, else of the evaluator's (single tenant only).
-- Rows that cannot be converted are left in evaluations and counted (closure precondition).
-- The core keeps one rating per (mission, rater, subject kind) (unique index of gdd-l3-305, tp-core, applied
-- before this script): a client keeps both its CLIENT_TO_DP and CLIENT_TO_RP of a delivery. The extra rows
-- (two types naming the same kind, e.g. CLIENT_TO_DP and CLIENT_RATING_DELIVERY_PERSON) are skipped by
-- ON CONFLICT DO NOTHING and counted (RAISE NOTICE); the oldest evaluation of such a triple is kept. Idempotent (id kept, ON CONFLICT).
DO $$
DECLARE
    n_total      BIGINT;
    n_resolvable BIGINT;
    n_present    BIGINT;
    n_inserted   BIGINT;
BEGIN
    CREATE TEMP TABLE _gdd3_party ON COMMIT DROP AS
    WITH ids AS (SELECT evaluator_id AS pid FROM evaluations UNION SELECT evaluated_id FROM evaluations)
    SELECT i.pid,
           COALESCE(fr.actor, cl.actor, rp.actor, us.actor) AS actor_id,
           CASE WHEN fr.actor IS NOT NULL THEN 'DELIVERY_PERSON'
                WHEN cl.actor IS NOT NULL THEN 'CLIENT'
                WHEN rp.actor IS NOT NULL THEN 'RELAY_OPERATOR' END AS kind,
           COALESCE(fr.tenant, cl.tenant, rp.tenant, us.tenant) AS tenant_id
      FROM ids i
      LEFT JOIN LATERAL (
            SELECT f.core_user_id AS actor,
                   (SELECT fp.tenant_id FROM tnt_actor.freelancer_profiles fp WHERE fp.id = f.core_freelancer_id) AS tenant
              FROM gofp_freelancers f
             WHERE f.id = i.pid OR f.core_freelancer_id = i.pid
             LIMIT 1) fr ON true
      LEFT JOIN LATERAL (
            SELECT c.core_user_id AS actor,
                   COALESCE((SELECT cp.tenant_id FROM tnt_actor.client_profiles cp WHERE cp.id = c.core_client_id),
                            (SELECT cp.tenant_id FROM tnt_actor.client_profiles cp WHERE cp.id = c.id)) AS tenant
              FROM gofp_clients c
             WHERE c.id = i.pid OR c.core_client_id = i.pid
             LIMIT 1) cl ON true
      LEFT JOIN LATERAL (
            SELECT f.core_user_id AS actor,
                   (SELECT fp.tenant_id FROM tnt_actor.freelancer_profiles fp WHERE fp.id = f.core_freelancer_id) AS tenant
              FROM gofp_relay_points r
              JOIN gofp_freelancers f ON f.core_freelancer_id = r.core_freelancer_id
             WHERE r.id = i.pid OR r.core_relay_point_id = i.pid
             LIMIT 1) rp ON true
      LEFT JOIN LATERAL (
            SELECT u.core_user_id AS actor,
                   (SELECT min(t.tenant_id::text)::uuid
                      FROM (SELECT tenant_id FROM tnt_actor.client_profiles WHERE actor_id = u.core_user_id
                            UNION
                            SELECT tenant_id FROM tnt_actor.freelancer_profiles WHERE actor_id = u.core_user_id) t
                    HAVING count(*) = 1) AS tenant
              FROM gofp_users u
             WHERE u.id = i.pid OR u.core_user_id = i.pid
             LIMIT 1) us ON true;

    SELECT count(*) INTO n_total FROM evaluations;
    SELECT count(*) INTO n_resolvable
      FROM evaluations e
      JOIN _gdd3_party pe ON pe.pid = e.evaluated_id
      JOIN _gdd3_party pr ON pr.pid = e.evaluator_id
     WHERE pe.actor_id IS NOT NULL AND pr.actor_id IS NOT NULL
       AND COALESCE(pe.tenant_id, pr.tenant_id) IS NOT NULL;
    SELECT count(*) INTO n_present FROM evaluations e JOIN tnt_tp_ratings r ON r.id = e.id;

    INSERT INTO tnt_tp_ratings (id, tenant_id, rated_third_party_id, rater_actor_id, mission_id, score,
                                comment, created_at, rating_context, subject_kind)
    SELECT e.id,
           COALESCE(pe.tenant_id, pr.tenant_id),
           pe.actor_id,
           pr.actor_id,
           e.delivery_id::text,
           e.rating,
           e.comment,
           COALESCE(e.created_at::timestamptz, now()),
           left(e.type, 40),
           COALESCE(pe.kind,
                    CASE WHEN e.type ILIKE '%TO_DP' OR e.type ILIKE '%DELIVERY_PERSON' THEN 'DELIVERY_PERSON'
                         WHEN e.type ILIKE '%TO_CLIENT' OR e.type ILIKE '%RATING_CLIENT' THEN 'CLIENT'
                         WHEN e.type ILIKE '%TO_RP' OR e.type ILIKE '%RELAY%' THEN 'RELAY_OPERATOR' END)
      FROM evaluations e
      JOIN _gdd3_party pe ON pe.pid = e.evaluated_id
      JOIN _gdd3_party pr ON pr.pid = e.evaluator_id
     WHERE pe.actor_id IS NOT NULL AND pr.actor_id IS NOT NULL
       AND COALESCE(pe.tenant_id, pr.tenant_id) IS NOT NULL
     ORDER BY e.created_at NULLS LAST, e.id
    ON CONFLICT DO NOTHING;
    GET DIAGNOSTICS n_inserted = ROW_COUNT;

    RAISE NOTICE 'gdd-l3-302: % evaluations, % not convertible (left), % already migrated, % inserted, % skipped as duplicates (mission, rater, kind)',
        n_total, n_total - n_resolvable, n_present, n_inserted, n_resolvable - n_present - n_inserted;
END $$;
