#!/usr/bin/env bash
# ══════════════════════════════════════════════════════════════════════════════
# sql-state-freelancer.sh — Lot C-19.2 : état SQL d'un compte, table par table.
#
# Lecture seule. Imprime, pour le sub Kernel donné, les lignes des tables du
# parcours freelancer (profil, rôle, projections GOFP, wallet, transactions).
#
# Usage : bash scripts/e2e/sql-state-freelancer.sh <sub-kernel> [label]
# ══════════════════════════════════════════════════════════════════════════════
set -uo pipefail
SUB="${1:?sub Kernel requis}"
LABEL="${2:-}"
PG_CONTAINER="${TNT_POSTGRES:-tnt-postgres}"
# owner_id est un varchar : on compare au texte de l'id de profil.
PROFILE_IDS="(SELECT id::text FROM tnt_actor.freelancer_profiles WHERE actor_id='${SUB}'"
q() { docker exec "$PG_CONTAINER" psql -U tiibntick -d tiibntick_core -P pager=off -c "$1" 2>&1; }

echo "═══ état SQL — sub=${SUB} ${LABEL} — $(date -u +%Y-%m-%dT%H:%M:%SZ) ═══"
echo "── comptes ──"
q "SELECT
  (SELECT count(*) FROM tnt_actor.freelancer_profiles WHERE actor_id='${SUB}')            AS freelancer_profiles,
  (SELECT count(*) FROM tnt_user_role_assignments WHERE user_id='${SUB}')                 AS role_assignments,
  (SELECT count(*) FROM gofp_users WHERE core_user_id='${SUB}')                           AS gofp_users,
  (SELECT count(*) FROM gofp_freelancers WHERE core_user_id='${SUB}')                     AS gofp_freelancers,
  (SELECT count(*) FROM tnt_delivery_persons WHERE actor_id='${SUB}')                     AS tnt_delivery_persons,
  (SELECT count(*) FROM billing.wallet_wallets w
     WHERE w.owner_id IN ${PROFILE_IDS}) OR w.owner_id='${SUB}' OR w.user_id::text='${SUB}')  AS wallet_wallets,
  (SELECT count(*) FROM billing.wallet_wallets)                                            AS wallet_wallets_total;"
echo "── invariant des trois id ──"
q "SELECT fp.id AS freelancer_profiles_id, gf.id AS gofp_freelancers_id,
          gf.core_freelancer_id, dp.id AS tnt_delivery_persons_id,
          gf.remaining_deliveries AS gofp_quota, dp.remaining_deliveries AS dp_quota
     FROM tnt_actor.freelancer_profiles fp
     LEFT JOIN gofp_freelancers gf ON gf.core_user_id = fp.actor_id
     LEFT JOIN tnt_delivery_persons dp ON dp.actor_id = fp.actor_id
    WHERE fp.actor_id='${SUB}';"
echo "── rôles ──"
q "SELECT r.code, a.tenant_id AS assignment_tenant, r.tenant_id AS role_tenant
     FROM tnt_user_role_assignments a JOIN tnt_roles r ON r.id=a.role_id WHERE a.user_id='${SUB}';"
echo "── wallet + transactions ──"
q "SELECT w.id, w.owner_id, w.balance, w.currency, w.created_at
     FROM billing.wallet_wallets w
    WHERE w.owner_id IN ${PROFILE_IDS}) OR w.owner_id='${SUB}' OR w.user_id::text='${SUB}';"
q "SELECT t.type, t.amount, t.created_at
     FROM billing.wallet_transactions t JOIN billing.wallet_wallets w ON w.id=t.wallet_id
    WHERE w.owner_id IN ${PROFILE_IDS}) OR w.owner_id='${SUB}' OR w.user_id::text='${SUB}'
    ORDER BY t.created_at;"
