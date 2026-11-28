-- gdd-l3-304 : profile averages recomputed from the tp-core ratings (after gdd-l3-302).
--
-- The average shown on a profile is now the actor-core ActorRating (client_profiles / freelancer_profiles
-- rating_score, rating_total), which the Go evaluations never fed. Recomputed per subject_kind:
--   CLIENT          -> tnt_actor.client_profiles     (same tenant, actor_id = rated_third_party_id)
--   DELIVERY_PERSON -> tnt_actor.freelancer_profiles (same tenant, actor_id = rated_third_party_id)
--   RELAY_OPERATOR  -> gofp_relay_points.rating of the relay points of the owner freelancer (Go-owned column,
--                      same rule as EvaluationApplicationService: average of the owner's relay ratings, 1 decimal)
-- A profile that already holds an actor-core average (rating_total > 0, ratings given directly to actor-core)
-- is left unchanged and counted, so that nothing is counted twice; scores rounded to 2 decimals as ActorRating.
-- Idempotent: a recomputed profile has rating_total > 0 and is skipped; the relay rating is recomputed to the
-- same value.
DO $$
DECLARE
    n_client     BIGINT;
    n_freelancer BIGINT;
    n_kept       BIGINT;
    n_relay      BIGINT;
BEGIN
    CREATE TEMP TABLE _gdd3_avg ON COMMIT DROP AS
    SELECT tenant_id, rated_third_party_id AS actor_id, subject_kind,
           round(avg(score)::numeric, 2)::double precision AS score, count(*)::int AS total,
           max(created_at) AS last_at
      FROM tnt_tp_ratings
     WHERE subject_kind IN ('CLIENT', 'DELIVERY_PERSON', 'RELAY_OPERATOR')
     GROUP BY tenant_id, rated_third_party_id, subject_kind;

    SELECT count(*) INTO n_kept
      FROM _gdd3_avg a
     WHERE (a.subject_kind = 'CLIENT' AND EXISTS (SELECT 1 FROM tnt_actor.client_profiles p
                WHERE p.tenant_id = a.tenant_id AND p.actor_id = a.actor_id AND p.rating_total > 0))
        OR (a.subject_kind = 'DELIVERY_PERSON' AND EXISTS (SELECT 1 FROM tnt_actor.freelancer_profiles p
                WHERE p.tenant_id = a.tenant_id AND p.actor_id = a.actor_id AND p.rating_total > 0));

    UPDATE tnt_actor.client_profiles p
       SET rating_score = a.score, rating_total = a.total, rating_updated_at = a.last_at
      FROM _gdd3_avg a
     WHERE a.subject_kind = 'CLIENT' AND p.tenant_id = a.tenant_id AND p.actor_id = a.actor_id
       AND p.rating_total = 0;
    GET DIAGNOSTICS n_client = ROW_COUNT;

    UPDATE tnt_actor.freelancer_profiles p
       SET rating_score = a.score, rating_total = a.total, rating_updated_at = a.last_at
      FROM _gdd3_avg a
     WHERE a.subject_kind = 'DELIVERY_PERSON' AND p.tenant_id = a.tenant_id AND p.actor_id = a.actor_id
       AND p.rating_total = 0;
    GET DIAGNOSTICS n_freelancer = ROW_COUNT;

    UPDATE gofp_relay_points r
       SET rating = round(a.score::numeric, 1)::double precision
      FROM tnt_actor.freelancer_profiles fp, _gdd3_avg a
     WHERE fp.id = r.core_freelancer_id
       AND a.subject_kind = 'RELAY_OPERATOR' AND a.tenant_id = fp.tenant_id AND a.actor_id = fp.actor_id;
    GET DIAGNOSTICS n_relay = ROW_COUNT;

    RAISE NOTICE 'gdd-l3-304: % client and % freelancer averages recomputed, % profiles kept (actor-core average already present), % relay points',
        n_client, n_freelancer, n_kept, n_relay;
END $$;
