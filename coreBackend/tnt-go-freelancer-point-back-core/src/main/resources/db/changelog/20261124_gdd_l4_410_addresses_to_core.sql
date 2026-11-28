-- Go-Freelancer de-duplication, lot L4 (gdd-l4-410): addresses move to the core address book.
-- The UUIDs are kept, so every *_address_id column of the module stays valid unchanged.
-- Re-runnable: ON CONFLICT DO NOTHING. A coordinate pair that is incomplete or out of range is stored as
-- no coordinates rather than failing the whole migration; nothing is deleted from the source tables.
INSERT INTO tnt_geography.tnt_addresses
    (id, tenant_id, street, landmark, district, city, region, country, postal_code, latitude, longitude,
     created_at, updated_at)
SELECT a.id, NULL,
       NULLIF(btrim(a.street), ''), NULLIF(btrim(a.description), ''), NULLIF(btrim(a.district), ''),
       a.city, NULLIF(btrim(a.region), ''), a.country, NULLIF(btrim(a.postal_code), ''),
       CASE WHEN a.latitude BETWEEN -90 AND 90 AND a.longitude BETWEEN -180 AND 180 THEN a.latitude END,
       CASE WHEN a.latitude BETWEEN -90 AND 90 AND a.longitude BETWEEN -180 AND 180 THEN a.longitude END,
       now(), now()
  FROM addresses a
ON CONFLICT (id) DO NOTHING;
