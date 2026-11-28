-- gdd-l3-320 : the vetting status of a freelancer moves to delivery-core (3.8 e), by id (H9, after gdd-l3-312).
-- Only the statuses the projection could not carry (it creates delivery persons APPROVED) are copied.
-- Diagnostic (RAISE NOTICE): freelancers without delivery person (read as PENDING from now on) whose Go
-- status was not PENDING, and freelancers whose Go status still differs from the core after the copy.
DO $$
DECLARE
    n_copied     BIGINT;
    n_no_core    BIGINT;
    n_mismatch   BIGINT;
BEGIN
    UPDATE tnt_delivery_persons p
       SET status = f.status, updated_at = now()
      FROM gofp_freelancers f
     WHERE f.id = p.id
       AND f.status IN ('REJECTED', 'REVOKED', 'SUSPENDED')
       AND p.status <> f.status;
    GET DIAGNOSTICS n_copied = ROW_COUNT;

    SELECT count(*) INTO n_no_core
      FROM gofp_freelancers f
     WHERE f.status <> 'PENDING'
       AND NOT EXISTS (SELECT 1 FROM tnt_delivery_persons p WHERE p.id = f.id);
    SELECT count(*) INTO n_mismatch
      FROM gofp_freelancers f JOIN tnt_delivery_persons p ON p.id = f.id
     WHERE p.status <> f.status;
    RAISE NOTICE 'gdd-l3-320: % statuses copied; % non-PENDING freelancers without delivery person; % statuses still different (core kept)',
        n_copied, n_no_core, n_mismatch;
END $$;
