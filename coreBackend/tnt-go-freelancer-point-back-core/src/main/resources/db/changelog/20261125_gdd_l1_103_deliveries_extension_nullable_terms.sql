-- gdd-l1-103: the gofp `deliveries` row becomes an extension of the tnt-delivery-core delivery.
-- The agreed windows are now recorded in tnt_deliveries (pickup_window_*/delivery_window_*); the gofp row
-- no longer writes them, so their NOT NULL constraints are lifted. The columns themselves stay until the
-- closure changeset (H7). Re-runnable: DROP NOT NULL on a nullable column is a no-op.
ALTER TABLE deliveries ALTER COLUMN pickup_min_time   DROP NOT NULL;
ALTER TABLE deliveries ALTER COLUMN pickup_max_time   DROP NOT NULL;
ALTER TABLE deliveries ALTER COLUMN delivery_min_time DROP NOT NULL;
ALTER TABLE deliveries ALTER COLUMN delivery_max_time DROP NOT NULL;
