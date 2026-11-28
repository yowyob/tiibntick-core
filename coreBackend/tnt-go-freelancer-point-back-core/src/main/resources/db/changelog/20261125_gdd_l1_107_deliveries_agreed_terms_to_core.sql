-- gdd-l1-107: the agreed price and windows of each gofp delivery are recorded on the tnt-delivery-core
-- delivery with the same id (the gofp row id IS the core delivery id). A gofp row with no core counterpart
-- is left as is (counted by Gdd1DeliveryMigrationIntegrationTest). The legacy windows are TIMESTAMP
-- WITHOUT TIME ZONE written from UTC instants: they are read as UTC. The currency is the one of the core
-- announcement the delivery came from, when it fits the 3-letter column. A negative price is not copied.
-- Re-runnable: a value already present in the core is never overwritten (COALESCE on the core side).
UPDATE tnt_deliveries d
   SET agreed_amount         = COALESCE(d.agreed_amount,
                                        CASE WHEN g.tarif >= 0 THEN CAST(g.tarif AS NUMERIC(14, 2)) END),
       agreed_currency       = COALESCE(d.agreed_currency,
                                        (SELECT a.currency FROM tnt_delivery_announcements a
                                          WHERE a.id = d.announcement_id AND length(a.currency) <= 3)),
       pickup_window_start   = COALESCE(d.pickup_window_start,   g.pickup_min_time   AT TIME ZONE 'UTC'),
       pickup_window_end     = COALESCE(d.pickup_window_end,     g.pickup_max_time   AT TIME ZONE 'UTC'),
       delivery_window_start = COALESCE(d.delivery_window_start, g.delivery_min_time AT TIME ZONE 'UTC'),
       delivery_window_end   = COALESCE(d.delivery_window_end,   g.delivery_max_time AT TIME ZONE 'UTC')
  FROM deliveries g
 WHERE g.id = d.id;
