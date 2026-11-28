-- gdd-l1-104: every legacy `packets` row becomes a tnt-delivery-core parcel with the SAME id.
-- tnt_parcels requires a positive weight and positive dimensions: a missing or non-positive value takes
-- the neutral value the announcement path has always sent to the core (1 kg, 10 cm). A non-positive
-- thickness is dropped (the core only accepts a positive one). Units are copied as stored: kg and cm.
-- Re-runnable: rows already present are left untouched (ON CONFLICT DO NOTHING).
INSERT INTO tnt_parcels (id, weight_kg, width_cm, height_cm, length_cm, thickness_cm,
                         fragile, perishable, description, designation, photo_url,
                         created_at, updated_at, version)
SELECT p.id,
       CASE WHEN p.weight > 0 THEN p.weight ELSE 1.0 END,
       CASE WHEN p.width  > 0 THEN p.width  ELSE 10.0 END,
       CASE WHEN p.height > 0 THEN p.height ELSE 10.0 END,
       CASE WHEN p.length > 0 THEN p.length ELSE 10.0 END,
       CASE WHEN p.thickness > 0 THEN p.thickness END,
       COALESCE(p.is_fragile, FALSE),
       COALESCE(p.is_perishable, FALSE),
       p.description,
       COALESCE(p.designation, p.title),
       p.photo_url,
       now(), now(), 0
  FROM packets p
ON CONFLICT (id) DO NOTHING;
