-- gdd-l3-303 : Go order counts -> tp-core loyalty accounts (3.7 e, H3).
--
-- For every Go client with total_orders > 0: points = total_orders * 10 (LoyaltyAccount.POINTS_PER_DELIVERY),
-- key third_party_id = actor id (core_user_id, H1), tier recomputed with the core thresholds
-- (LoyaltyTier: BRONZE 0-499, SILVER 500-1999, GOLD 2000-4999, PLATINUM >= 5000).
-- Tenant: the one of the client profile (id = core_client_id, else id = gofp_clients.id, else the single
-- tenant of the actor's client profiles). Clients without tenant are counted and skipped.
-- Existing accounts are completed, never double counted: lifetime points rise to at least the Go value and
-- the available points grow by the same difference. Idempotent (a second run changes nothing).
DO $$
DECLARE
    n_candidates BIGINT;
    n_no_tenant  BIGINT;
    n_written    BIGINT;
BEGIN
    CREATE TEMP TABLE _gdd3_loyalty ON COMMIT DROP AS
    SELECT x.tenant_id, x.actor_id, max(x.points) AS points
      FROM (SELECT c.core_user_id AS actor_id,
                   c.total_orders * 10 AS points,
                   COALESCE((SELECT cp.tenant_id FROM tnt_actor.client_profiles cp WHERE cp.id = c.core_client_id),
                            (SELECT cp.tenant_id FROM tnt_actor.client_profiles cp WHERE cp.id = c.id),
                            (SELECT min(cp.tenant_id::text)::uuid FROM tnt_actor.client_profiles cp
                              WHERE cp.actor_id = c.core_user_id
                             HAVING count(DISTINCT cp.tenant_id) = 1)) AS tenant_id
              FROM gofp_clients c
             WHERE c.total_orders > 0) x
     GROUP BY x.tenant_id, x.actor_id;

    SELECT count(*) INTO n_candidates FROM _gdd3_loyalty;
    SELECT count(*) INTO n_no_tenant FROM _gdd3_loyalty WHERE tenant_id IS NULL;

    INSERT INTO tnt_loyalty_accounts AS a (id, tenant_id, third_party_id, available_points, lifetime_points,
                                           redeemed_points, expired_points, current_tier, created_at, updated_at)
    SELECT gen_random_uuid(), l.tenant_id, l.actor_id, l.points, l.points, 0, 0,
           CASE WHEN l.points >= 5000 THEN 'PLATINUM'
                WHEN l.points >= 2000 THEN 'GOLD'
                WHEN l.points >= 500  THEN 'SILVER'
                ELSE 'BRONZE' END,
           now(), now()
      FROM _gdd3_loyalty l
     WHERE l.tenant_id IS NOT NULL
    ON CONFLICT (tenant_id, third_party_id) DO UPDATE
       SET available_points = a.available_points + (EXCLUDED.lifetime_points - a.lifetime_points),
           lifetime_points  = EXCLUDED.lifetime_points,
           current_tier     = CASE WHEN EXCLUDED.lifetime_points >= 5000 THEN 'PLATINUM'
                                   WHEN EXCLUDED.lifetime_points >= 2000 THEN 'GOLD'
                                   WHEN EXCLUDED.lifetime_points >= 500  THEN 'SILVER'
                                   ELSE 'BRONZE' END,
           updated_at       = now()
     WHERE a.lifetime_points < EXCLUDED.lifetime_points;
    GET DIAGNOSTICS n_written = ROW_COUNT;

    RAISE NOTICE 'gdd-l3-303: % client actors with orders, % without tenant (skipped), % accounts created or completed',
        n_candidates, n_no_tenant, n_written;
END $$;
