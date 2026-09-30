-- wallet-ledger-reconciliation.sql
-- Lists every wallet whose balance diverges from the algebraic sum of its
-- wallet_transactions rows (CREDIT adds, anything else subtracts).
-- A non-empty result set means the ledger is inconsistent and requires
-- investigation before the discrepancy grows further.
--
-- Columns:
--   wallet_id  — billing.wallet_wallets.id
--   owner_id   — entity (user/org) that owns the wallet
--   balance    — current balance as stored in wallet_wallets
--   ledger_sum — sum(CREDIT) − sum(non-CREDIT) from wallet_transactions
--   drift      — balance − ledger_sum  (positive = balance overstated)
--
-- Usage:
--   docker exec tnt-postgres psql -U tiibntick -d tiibntick_core \
--     -f scripts/sql/wallet-ledger-reconciliation.sql

SELECT
    ww.id              AS wallet_id,
    ww.owner_id,
    ww.balance,
    COALESCE(
        SUM(CASE WHEN wt.type = 'CREDIT' THEN wt.amount ELSE -wt.amount END),
        0
    )                  AS ledger_sum,
    ww.balance - COALESCE(
        SUM(CASE WHEN wt.type = 'CREDIT' THEN wt.amount ELSE -wt.amount END),
        0
    )                  AS drift
FROM billing.wallet_wallets ww
LEFT JOIN billing.wallet_transactions wt ON wt.wallet_id = ww.id
GROUP BY ww.id, ww.owner_id, ww.balance
HAVING ww.balance <> COALESCE(
    SUM(CASE WHEN wt.type = 'CREDIT' THEN wt.amount ELSE -wt.amount END),
    0
)
ORDER BY ABS(
    ww.balance - COALESCE(
        SUM(CASE WHEN wt.type = 'CREDIT' THEN wt.amount ELSE -wt.amount END),
        0
    )
) DESC;

-- ── Query 2: non-monotone balance_after ────────────────────────────────────
-- Detects transaction rows where balance_after is not equal to the previous
-- balance_after ± the signed amount of the current row (chronological order
-- within each wallet).  A non-empty result means the audit trail cannot be
-- replayed as a consistent running balance.
--
-- signed_amount: +amount for CREDIT, -amount for everything else.
-- expected_balance_after: prev_balance_after + signed_amount.
-- Non-monotone when balance_after != expected AND there IS a previous row
-- (first row in a wallet has no predecessor, so it is always skipped).
SELECT
    wallet_id,
    id          AS transaction_id,
    reference_id,
    type,
    amount,
    balance_after,
    prev_balance_after,
    (CASE WHEN type = 'CREDIT' THEN amount ELSE -amount END) AS signed_amount,
    prev_balance_after + (CASE WHEN type = 'CREDIT' THEN amount ELSE -amount END)
                        AS expected_balance_after,
    created_at
FROM (
    SELECT
        id,
        wallet_id,
        reference_id,
        type,
        amount,
        balance_after,
        LAG(balance_after) OVER (
            PARTITION BY wallet_id ORDER BY created_at, id
        ) AS prev_balance_after,
        created_at
    FROM billing.wallet_transactions
) ranked
WHERE prev_balance_after IS NOT NULL
  AND balance_after <> prev_balance_after
                      + (CASE WHEN type = 'CREDIT' THEN amount ELSE -amount END)
ORDER BY wallet_id, created_at, id;
