-- Go-Freelancer de-duplication, lot L4 (gdd-l4-414): rows of the unused legacy `payments` table, if any, become
-- billing settlements of their delivery (architecture-cible 3.12): reference_type 'DELIVERY', reference_id =
-- delivery_id, same id, tenant of the core delivery (same id as the Go delivery). Only PAID rows are copied and they
-- stay paid:
--   * CASH                                  -> method CASH,              status CASH_COLLECTED (the paid state of cash
--                                              in the settlement model: SettlementService pairs CASH with it);
--   * WALLET / KERNEL_WALLET / WALLET_TRANSFER -> method WALLET_TRANSFER, status PAID;
--   * any other method (MOBILE_MONEY, ORANGE_MONEY, card...) -> method EXTERNAL_PROVIDER, status PAID.
-- paid_at = the legacy paid_at (creation date when it is missing), for cash too. Commission rate 5 % (H5), status as
-- SettlementService computes it for money received: DUE when the commission is > 0, otherwise NONE.
-- Rows not PAID, and rows whose delivery is unknown to tnt_deliveries, are counted in a NOTICE.
-- Re-runnable (ON CONFLICT DO NOTHING).
DO $$
DECLARE
    v_skipped BIGINT;
BEGIN
    IF to_regclass('public.payments') IS NULL OR to_regclass('billing.wallet_settlements') IS NULL
            OR to_regclass('public.tnt_deliveries') IS NULL THEN
        RETURN;
    END IF;

    INSERT INTO billing.wallet_settlements (id, tenant_id, reference_type, reference_id, payer_user_id, payee_user_id,
                                            amount, currency, method, status, kernel_reference,
                                            commission_rate, commission_amount, commission_status,
                                            created_at, paid_at, updated_at)
    SELECT p.id, d.tenant_id, 'DELIVERY', p.delivery_id, NULL, NULL,
           round(p.amount::numeric, 4),
           CASE WHEN length(btrim(p.currency)) BETWEEN 1 AND 3 THEN upper(btrim(p.currency)) ELSE 'XAF' END,
           CASE WHEN m.method = 'CASH' THEN 'CASH'
                WHEN m.method IN ('WALLET', 'KERNEL_WALLET', 'WALLET_TRANSFER') THEN 'WALLET_TRANSFER'
                ELSE 'EXTERNAL_PROVIDER' END,
           CASE WHEN m.method = 'CASH' THEN 'CASH_COLLECTED' ELSE 'PAID' END,
           NULLIF(btrim(p.transaction_reference), ''),
           0.05,
           round(p.amount::numeric * 0.05, 2),
           CASE WHEN round(p.amount::numeric * 0.05, 2) > 0 THEN 'DUE' ELSE 'NONE' END,
           COALESCE(p.created_at, p.paid_at, now()),
           COALESCE(p.paid_at, p.created_at, now()),
           now()
      FROM payments p
      JOIN tnt_deliveries d ON d.id = p.delivery_id
      CROSS JOIN LATERAL (SELECT upper(btrim(p.payment_method)) AS method) m
     WHERE upper(p.status) = 'PAID'
     ORDER BY COALESCE(p.paid_at, p.created_at) DESC NULLS LAST
    ON CONFLICT DO NOTHING;

    SELECT count(*) INTO v_skipped
      FROM payments p
     WHERE NOT EXISTS (SELECT 1 FROM billing.wallet_settlements s WHERE s.id = p.id);
    IF v_skipped > 0 THEN
        RAISE NOTICE 'gdd-l4-414: % legacy payment row(s) not copied (not PAID, unknown core delivery, or already settled)',
            v_skipped;
    END IF;
END $$;
