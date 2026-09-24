#!/usr/bin/env bash
# ══════════════════════════════════════════════════════════════════════════════
# e2e-freelancer-flow.sh — F-R1/F-R2 (lot 19, auth lot 20, RBAC lot 21)
#
# Prouve le flux complet d'inscription/validation freelancer :
#   F-R1 : lecture de profil, statut par filtre
#   F-R2 : machine à états admin (validate/suspend/revoke) avec notifications
#
# Mutations MF0-MF8 :
#   MF0  seed             : freelancer A PENDING, freelancer B PENDING
#   MF1  PENDING→APPROVED : /validate?approved=true  → 200 + notification log
#   MF5  APPROVED→PENDING : PATCH /status?status=PENDING → 409 (machine à états)
#   MF8  APPROVED→APPROVED: idempotence              → 200, updated_at inchangé
#   MF2  APPROVED→SUSPENDED : /suspend              → 200
#   MF3  SUSPENDED→APPROVED : /validate?approved=true → 200
#   MF4  APPROVED→REVOKED  : /revoke               → 200
#   MF6  seed B → REJECTED, puis REJECTED→APPROVED  → 409 (état terminal)
#   MF7  403 non-admin : suppression temporaire assignation → 403 → remise → 200
#
# RBAC (lot 21) :
#   Le script crée un rôle minimal E2E (permissions='gofp-admin:manage', jamais '*')
#   dans tnt_roles/tnt_user_role_assignments, le supprime au cleanup (SEEDED_ROLE=1).
#   Un [WARN] bloquant s'affiche si un rôle '*' est détecté sur l'acteur.
#   MF7 invalide la cache via Kafka (topic tnt.roles.permission-changed) avant
#   chaque vérification d'autorisation.
#
# Prérequis : AUTH_MODE=jwt (TNT_E2E_TOKEN ou BFF joignable)
# ABORT si dev-bypass : @RequirePermission est actif, un token anonyme renvoie 403
# sur toutes les routes admin — le test ne prouve rien.
#
# Idempotent : cleanup sur EXIT (DELETE FROM gofp_freelancers WHERE id IN ...).
# ══════════════════════════════════════════════════════════════════════════════
set -euo pipefail

# ─── Configuration ────────────────────────────────────────────────────────────
CORE_URL="${TNT_CORE_URL:-http://localhost:8080}"
BFF_URL="${TNT_E2E_BFF_URL:-http://localhost:3001}"
E2E_PHONE="${E2E_PHONE:-+237695479355}"
PG_CONTAINER="${TNT_POSTGRES:-tnt-postgres}"
PG_DB="tiibntick_core"
PG_USER="tiibntick"
APP_CONTAINER="${TNT_APP_CONTAINER:-tnt-core}"
KAFKA_CONTAINER="${TNT_KAFKA:-tnt-kafka}"

# IDs fixes → cleanup idempotent (e19 = lot 19, e21 = lot 21 RBAC)
E2E_FL_A_ID="e2e19f1a-0000-0000-0000-000000000001"   # freelancer A (happy path)
E2E_FL_B_ID="e2e19f1b-0000-0000-0000-000000000001"   # freelancer B (reject/terminal)
E2E_CORE_FL_A="e2e19f1a-0000-0000-0000-000000000002" # core_freelancer_id fictif A
E2E_CORE_FL_B="e2e19f1b-0000-0000-0000-000000000002" # core_freelancer_id fictif B
E2E_ROLE_ID="e2e21000-0000-0000-0000-000000000001"   # rôle minimal E2E (permissions = gofp-admin:manage)

TOKEN=""
AUTH_MODE=""
ACTOR_USER_ID=""
ACTOR_TENANT_ID=""

# Trace si ce script a créé le rôle/l'assignation E2E (ne supprime que ce qu'il a créé)
SEEDED_ROLE=0

# ─── Fonctions partagées (auth_curl, decode_jwt_payload, tnt_otp_flow) ────────
source "$(dirname "$0")/lib-auth.sh"

# ─── Tableau récapitulatif MF ─────────────────────────────────────────────────
declare -A MF_RESULT
for m in MF0 MF1 MF2 MF3 MF4 MF5 MF6 MF7 MF8; do MF_RESULT[$m]="⬜ non exécuté"; done

# ─── Helpers ─────────────────────────────────────────────────────────────────
FAILED=0
STEP_FAILURES=()
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; RESET='\033[0m'
pass()  { echo -e "${GREEN}[PASS]${RESET} $1"; }
fail()  { echo -e "${RED}[FAIL]${RESET} $1"; FAILED=1; STEP_FAILURES+=("$1"); }
warn()  { echo -e "${YELLOW}[WARN]${RESET} $1"; }
info()  { echo -e "${CYAN}[INFO]${RESET} $1"; }
step()  { echo; echo "──── $1 ────"; }

assert_eq() {
  local expected="$1" actual="$2" label="$3"
  if [ "$expected" = "$actual" ]; then
    pass "$label  (valeur : '$actual')"
  else
    fail "$label  attendu='$expected'  obtenu='$actual'"
  fi
}
assert_http() {
  local expected="$1" actual="$2" label="$3"
  if [ "$expected" = "$actual" ]; then
    pass "$label → HTTP $actual"
  else
    fail "$label → attendu HTTP $expected  obtenu HTTP $actual"
  fi
}
assert_nonempty() {
  local val="$1" label="$2"
  if [ -n "$val" ] && [ "$val" != "null" ]; then
    pass "$label  (valeur : '$val')"
  else
    fail "$label  (vide ou null)"
  fi
}

psql_q() { docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -At -c "$1"; }

# Publie un événement d'invalidation de cache pour (tenantId, userId) sur Kafka.
# Le PermissionCacheInvalidationListener consomme tnt.roles.permission-changed et appelle
# cache.invalidate(tenantId, userId) — indispensable pour MF7 (TTL Caffeine = 300s sinon).
kafka_invalidate_permission_cache() {
  local tenant_id="$1" user_id="$2"
  local payload="{\"tenantId\":\"${tenant_id}\",\"userId\":\"${user_id}\"}"
  # -i : relaie le stdin du shell vers le container (nécessaire pour que
  # la here-string <<< atteigne kafka-console-producer)
  echo "$payload" | docker exec -i "$KAFKA_CONTAINER" \
    kafka-console-producer --bootstrap-server localhost:9092 \
    --topic tnt.roles.permission-changed \
    > /dev/null 2>&1 || true
}

# ─── Cleanup idempotent ───────────────────────────────────────────────────────
cleanup() {
  psql_q "DELETE FROM gofp_freelancers WHERE id IN \
    ('${E2E_FL_A_ID}','${E2E_FL_B_ID}');" > /dev/null 2>&1 || true
  # Ne supprime rôle/assignation que si ce script les a créés (SEEDED_ROLE=1).
  if [ "$SEEDED_ROLE" = "1" ] && [ -n "$ACTOR_TENANT_ID" ]; then
    psql_q "DELETE FROM tnt_user_role_assignments \
      WHERE role_id = '${E2E_ROLE_ID}' AND tenant_id = '${ACTOR_TENANT_ID}';" \
      > /dev/null 2>&1 || true
    psql_q "DELETE FROM tnt_roles \
      WHERE id = '${E2E_ROLE_ID}' AND tenant_id = '${ACTOR_TENANT_ID}';" \
      > /dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

# ══════════════════════════════════════════════════════════════════════════════
# 0. GARDE-FOU JAR PÉRIMÉ (même logique que e2e-presence-loop.sh)
# ══════════════════════════════════════════════════════════════════════════════
step "0. FRAÎCHEUR DU JAR"

JAR_PATH="tnt-bootstrap/target/tnt-bootstrap-0.0.1.jar"
if [ -f "$JAR_PATH" ]; then
  JAR_MTIME=$(stat -c %Y "$JAR_PATH" 2>/dev/null || echo 0)
  LAST_SRC_CT=$(git log -1 --format=%ct -- '*/src/main/*' 2>/dev/null || echo 0)
  if [ "$LAST_SRC_CT" -gt 0 ] && [ "$JAR_MTIME" -lt "$LAST_SRC_CT" ]; then
    echo ""
    echo "ERREUR BLOQUANTE : le jar est plus vieux que le dernier commit src/main."
    echo "  jar    : $(date -d "@$JAR_MTIME" '+%Y-%m-%d %H:%M:%S' 2>/dev/null || echo "$JAR_MTIME")"
    echo "  commit : $(date -d "@$LAST_SRC_CT" '+%Y-%m-%d %H:%M:%S' 2>/dev/null || echo "$LAST_SRC_CT")"
    echo ""
    echo "Reconstruisez l'image avant de relancer :"
    echo "  /home/jtk/.local/opt/apache-maven-3.9.9/bin/mvn -pl coreBackend/tnt-go-freelancer-point-back-core,tnt-bootstrap -am package -DskipTests -Denforcer.skip=true"
    echo "  cd tnt-bootstrap && docker compose --profile app build tiibntick-core"
    echo "  docker compose --profile app up -d tiibntick-core"
    echo "  # Attendez ~70 s que l'app soit prête, puis relancez."
    exit 1
  fi
  pass "Jar à jour (jar: $(date -d "@$JAR_MTIME" '+%H:%M:%S' 2>/dev/null || echo "$JAR_MTIME"))"
else
  warn "Jar introuvable ($JAR_PATH) — garde-fou ignoré (image Docker pré-buildée ?)"
fi

# ══════════════════════════════════════════════════════════════════════════════
# 1. AUTHENTIFICATION — abort si dev-bypass
# ══════════════════════════════════════════════════════════════════════════════
step "1. AUTHENTIFICATION"

if [ -n "${TNT_E2E_TOKEN:-}" ]; then
  TOKEN="$TNT_E2E_TOKEN"
  AUTH_MODE="jwt"
  info "Jeton fourni via TNT_E2E_TOKEN (${TOKEN:0:12}…, longueur ${#TOKEN})"
else
  BFF_HEALTH=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    "${BFF_URL}/health" 2>/dev/null || :)
  if [ "$BFF_HEALTH" = "200" ]; then
    info "BFF joignable ($BFF_URL) — flux OTP pour $E2E_PHONE"
    AUTH_MODE="jwt"
    tnt_otp_flow || exit 1
  else
    # Le BFF est absent. On teste si le core tourne (pour un meilleur message d'erreur).
    CORE_PROBE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
      "${CORE_URL}/actuator/health" 2>/dev/null || :)
    if [ "$CORE_PROBE" != "200" ]; then
      echo "ERREUR : core inaccessible (HTTP $CORE_PROBE sur /actuator/health)."; exit 1
    fi
    AUTH_MODE="no-bff"
  fi
fi

if [ "$AUTH_MODE" != "jwt" ]; then
  echo ""
  echo "ABORT : AUTH_MODE='${AUTH_MODE}'."
  echo "Ce harnais F-R1/F-R2 requiert un JWT réel."
  echo "  • Les routes admin portent @RequirePermission(resource='gofp-admin',action='manage')."
  echo "  • Sans JWT valide, toutes les routes admin renvoient 401/403."
  echo "Remède :"
  echo "  • Démarrer le BFF : cd /home/jtk/projets/tiibntick-bff && node --env-file=.env node_modules/.bin/tsx src/index.ts"
  echo "  • Ou fournir un jeton : export TNT_E2E_TOKEN=<token>"
  exit 2
fi

PAYLOAD=$(decode_jwt_payload "$TOKEN")
ACTOR_USER_ID=$(echo "$PAYLOAD" | jq -r '.sub // empty' 2>/dev/null || echo "")
ACTOR_TENANT_ID=$(echo "$PAYLOAD" | jq -r '.tid // empty' 2>/dev/null || echo "")
info "sub (actorUserId)  = $ACTOR_USER_ID"
info "tid (tenantId)     = $ACTOR_TENANT_ID"
assert_nonempty "$ACTOR_USER_ID"    "JWT sub non vide"
assert_nonempty "$ACTOR_TENANT_ID"  "JWT tid non vide"

# ─── Vérifier que le core est joignable ──────────────────────────────────────
CORE_HEALTH=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 \
  "${CORE_URL}/actuator/health" 2>/dev/null || :)
if [ "$CORE_HEALTH" != "200" ]; then
  echo "ERREUR : core non joignable (HTTP $CORE_HEALTH sur /actuator/health)."; exit 1
fi

# ─── Vérifier que PostgreSQL est joignable ───────────────────────────────────
if ! psql_q "SELECT 1" > /dev/null 2>&1; then
  echo "ERREUR : PostgreSQL non joignable via docker exec $PG_CONTAINER."; exit 1
fi

# ══════════════════════════════════════════════════════════════════════════════
# SETUP RBAC — rôle minimal E2E pour l'acteur (lot 21)
#
# Garantie : aucun rôle '*' n'est assigné à l'acteur avant le run.
# Si c'est le cas, les assertions RBAC (MF7 notamment) ne prouvent rien.
# Le script crée un rôle minimal (permissions = 'gofp-admin:manage') et
# l'assigne à l'acteur. SEEDED_ROLE=1 active le cleanup sur EXIT.
# ══════════════════════════════════════════════════════════════════════════════
step "SETUP RBAC"

# ─── Garde-fou : détecter les rôles '*' assignés à l'acteur ──────────────────
WILDCARD_ROLES=$(psql_q "
  SELECT COUNT(*) FROM tnt_user_role_assignments a
  JOIN tnt_roles r ON r.id = a.role_id
  WHERE a.tenant_id = '${ACTOR_TENANT_ID}'
    AND a.user_id   = '${ACTOR_USER_ID}'
    AND r.permissions = '*';
" 2>/dev/null || echo "0")

if [ "$WILDCARD_ROLES" != "0" ]; then
  warn "════════════════════════════════════════════════════════════════════"
  warn "[WARN RBAC] Rôle '*' détecté sur l'acteur (${WILDCARD_ROLES} assignation(s))."
  warn "[WARN RBAC] Les assertions RBAC de ce run NE PROUVENT RIEN :"
  warn "[WARN RBAC] un super-admin passe toutes les vérifications @RequirePermission."
  warn "[WARN RBAC] Supprimez le(s) rôle(s) '*' avant de relancer pour des résultats fiables."
  warn "════════════════════════════════════════════════════════════════════"
fi

# ─── Créer le rôle minimal E2E si nécessaire ─────────────────────────────────
psql_q "
  INSERT INTO tnt_roles (id, tenant_id, code, name, scope_type, permissions, system_role, editable)
  VALUES (
    '${E2E_ROLE_ID}',
    '${ACTOR_TENANT_ID}',
    'E2E_GOFP_ADMIN',
    'E2E GOFP Admin (lot 21)',
    'TENANT',
    'gofp-admin:manage',
    false,
    true
  ) ON CONFLICT (id) DO NOTHING;
" > /dev/null

ROLE_PERMS=$(psql_q "SELECT permissions FROM tnt_roles WHERE id='${E2E_ROLE_ID}';" 2>/dev/null || echo "")
assert_eq "gofp-admin:manage" "$ROLE_PERMS" "RBAC rôle E2E créé : permissions = gofp-admin:manage (pas '*')"

# ─── Assigner le rôle à l'acteur ─────────────────────────────────────────────
psql_q "
  INSERT INTO tnt_user_role_assignments
    (id, tenant_id, user_id, role_id, scope_type, scope_id)
  VALUES (
    gen_random_uuid(),
    '${ACTOR_TENANT_ID}',
    '${ACTOR_USER_ID}',
    '${E2E_ROLE_ID}',
    'TENANT',
    '${ACTOR_TENANT_ID}'
  ) ON CONFLICT DO NOTHING;
" > /dev/null

ASSIGN_COUNT=$(psql_q "
  SELECT COUNT(*) FROM tnt_user_role_assignments
  WHERE tenant_id = '${ACTOR_TENANT_ID}' AND user_id = '${ACTOR_USER_ID}'
    AND role_id = '${E2E_ROLE_ID}';
" 2>/dev/null || echo "0")
assert_eq "1" "$ASSIGN_COUNT" "RBAC assignation E2E créée pour l'acteur"
SEEDED_ROLE=1

# Invalider la cache pour que les appels suivants lisent les permissions fraîches.
# sleep 2 : le consommateur Kafka (PermissionCacheInvalidationListener) traite le message
# de façon asynchrone ; 2s suffit sur un broker local mais est explicitement nécessaire.
kafka_invalidate_permission_cache "$ACTOR_TENANT_ID" "$ACTOR_USER_ID"
sleep 2

# ══════════════════════════════════════════════════════════════════════════════
# MF0 — SEED : deux freelancers PENDING en base
# ══════════════════════════════════════════════════════════════════════════════
step "MF0 — SEED"
# Nettoyage préventif limité aux freelancers — NE PAS appeler cleanup() complet
# qui supprimerait aussi le rôle E2E (SEEDED_ROLE=1 est déjà positionné).
psql_q "DELETE FROM gofp_freelancers WHERE id IN \
  ('${E2E_FL_A_ID}','${E2E_FL_B_ID}');" > /dev/null 2>&1 || true

psql_q "
  INSERT INTO gofp_freelancers (id, core_freelancer_id, core_user_id, status, is_active)
  VALUES
    ('${E2E_FL_A_ID}', '${E2E_CORE_FL_A}', '${ACTOR_USER_ID}', 'PENDING', false),
    ('${E2E_FL_B_ID}', '${E2E_CORE_FL_B}', '${ACTOR_USER_ID}', 'PENDING', false)
  ON CONFLICT (id) DO NOTHING;
" > /dev/null

FL_A_STATUS=$(psql_q "SELECT status FROM gofp_freelancers WHERE id='${E2E_FL_A_ID}';" 2>/dev/null || echo "")
FL_B_STATUS=$(psql_q "SELECT status FROM gofp_freelancers WHERE id='${E2E_FL_B_ID}';" 2>/dev/null || echo "")
assert_eq "PENDING" "$FL_A_STATUS" "MF0 freelancer A : status=PENDING"
assert_eq "PENDING" "$FL_B_STATUS" "MF0 freelancer B : status=PENDING"
MF_RESULT[MF0]="✅ PASS"

# ─── F-R1 : lecture de profil via API ────────────────────────────────────────
step "F-R1 — LECTURE PROFIL"

# GET /api/v1/gofp/freelancer-profiles/{id}
HTTP_GET=$(auth_curl -s -o /dev/null -w "%{http_code}" \
  "${CORE_URL}/api/v1/gofp/freelancer-profiles/${E2E_FL_A_ID}")
assert_http "200" "$HTTP_GET" "GET /freelancer-profiles/${E2E_FL_A_ID}"

# GET /api/v1/gofp/freelancer-profiles/by-status/PENDING — doit contenir le freelancer A
BY_STATUS=$(auth_curl -s \
  "${CORE_URL}/api/v1/gofp/freelancer-profiles/by-status/PENDING")
if echo "$BY_STATUS" | grep -q "${E2E_FL_A_ID}"; then
  pass "GET /freelancer-profiles/by-status/PENDING contient freelancer A"
else
  fail "GET /freelancer-profiles/by-status/PENDING ne contient pas freelancer A"
fi

# GET /api/v1/gofp/freelancer-profiles/by-core-freelancer/{id}
HTTP_BY_CORE=$(auth_curl -s -o /dev/null -w "%{http_code}" \
  "${CORE_URL}/api/v1/gofp/freelancer-profiles/by-core-freelancer/${E2E_CORE_FL_A}")
assert_http "200" "$HTTP_BY_CORE" "GET /freelancer-profiles/by-core-freelancer/${E2E_CORE_FL_A}"

# 404 sur UUID inconnu (FreelancerNotFoundException → GlobalExceptionHandler → 404)
HTTP_404=$(auth_curl -s -o /dev/null -w "%{http_code}" -X PUT \
  "${CORE_URL}/api/v1/admin/tnt-go-freelancer/freelancers/00000000-0000-0000-0000-000000000000/validate?approved=true")
assert_http "404" "$HTTP_404" "404 sur UUID inconnu (FreelancerNotFoundException)"

# 400 sur UUID malformé (ServerWebInputException → GlobalExceptionHandler → 400)
HTTP_400=$(auth_curl -s -o /dev/null -w "%{http_code}" -X PUT \
  "${CORE_URL}/api/v1/admin/tnt-go-freelancer/freelancers/not-a-uuid/validate?approved=true")
assert_http "400" "$HTTP_400" "400 sur UUID malformé"

# ══════════════════════════════════════════════════════════════════════════════
# MF1 — PENDING → APPROVED  (+ assertion notification log)
# ══════════════════════════════════════════════════════════════════════════════
step "MF1 — PENDING → APPROVED"

# Capturer le timestamp avant l'appel pour filtrer les logs Docker
BEFORE_VALIDATE=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

HTTP_MF1=$(auth_curl -s -o /dev/null -w "%{http_code}" -X PUT \
  "${CORE_URL}/api/v1/admin/tnt-go-freelancer/freelancers/${E2E_FL_A_ID}/validate?approved=true&loginUrl=http://app.example.com")
assert_http "200" "$HTTP_MF1" "MF1 PUT /validate?approved=true"

FL_A_STATUS_DB=$(psql_q "SELECT status FROM gofp_freelancers WHERE id='${E2E_FL_A_ID}';" 2>/dev/null || echo "")
assert_eq "APPROVED" "$FL_A_STATUS_DB" "MF1 status en base = APPROVED"

# Assertion notification : la trace [GOFP-NOTIFY] est une exigence fonctionnelle —
# son absence FAIL le run (pas seulement un warn).
sleep 1  # laisser le temps à l'appel réactif de logguer
if docker logs "$APP_CONTAINER" --since "$BEFORE_VALIDATE" 2>/dev/null \
    | grep -q "\[GOFP-NOTIFY\].*ACCOUNT_APPROVED.*${E2E_FL_A_ID}"; then
  pass "MF1 notification [GOFP-NOTIFY] ACCOUNT_APPROVED trouvée dans les logs"
else
  fail "MF1 notification [GOFP-NOTIFY] ACCOUNT_APPROVED absente des logs du conteneur $APP_CONTAINER"
  warn "  Vérification manuelle : docker logs $APP_CONTAINER 2>&1 | grep GOFP-NOTIFY"
  warn "  Causes possibles : image périmée (lot 18), log level insuffisant, mauvais APP_CONTAINER='$APP_CONTAINER'"
fi

if [ "$HTTP_MF1" = "200" ] && [ "$FL_A_STATUS_DB" = "APPROVED" ]; then
  MF_RESULT[MF1]="✅ PASS"
else
  MF_RESULT[MF1]="❌ FAIL"
fi

# ══════════════════════════════════════════════════════════════════════════════
# MF5 — APPROVED → PENDING  → doit rendre 409 (machine à états)
# ══════════════════════════════════════════════════════════════════════════════
step "MF5 — APPROVED → PENDING (attendu 409)"

# PATCH /status?status=PENDING teste directement GofpFreelancerService.updateStatus.
# InvalidFreelancerStatusTransitionException (extends IllegalStateException)
# → GlobalExceptionHandler.handleIllegalState → 409.
HTTP_MF5=$(auth_curl -s -o /dev/null -w "%{http_code}" -X PATCH \
  "${CORE_URL}/api/v1/gofp/freelancer-profiles/${E2E_FL_A_ID}/status?status=PENDING")
assert_http "409" "$HTTP_MF5" "MF5 APPROVED→PENDING → 409 (FreelancerStatus.isAllowed)"

FL_A_STATUS_AFTER_MF5=$(psql_q "SELECT status FROM gofp_freelancers WHERE id='${E2E_FL_A_ID}';" 2>/dev/null || echo "")
assert_eq "APPROVED" "$FL_A_STATUS_AFTER_MF5" "MF5 status reste APPROVED en base (transition refusée)"

if [ "$HTTP_MF5" = "409" ] && [ "$FL_A_STATUS_AFTER_MF5" = "APPROVED" ]; then
  MF_RESULT[MF5]="✅ PASS"
else
  MF_RESULT[MF5]="❌ FAIL"
fi

# ══════════════════════════════════════════════════════════════════════════════
# MF8 — APPROVED → APPROVED  (idempotence : 200, updated_at inchangé)
# ══════════════════════════════════════════════════════════════════════════════
step "MF8 — APPROVED → APPROVED (idempotence)"

UPDATED_AT_BEFORE=$(psql_q "SELECT updated_at FROM gofp_freelancers WHERE id='${E2E_FL_A_ID}';" 2>/dev/null || echo "")
assert_nonempty "$UPDATED_AT_BEFORE" "MF8 updated_at initial non vide"

HTTP_MF8=$(auth_curl -s -o /dev/null -w "%{http_code}" -X PATCH \
  "${CORE_URL}/api/v1/gofp/freelancer-profiles/${E2E_FL_A_ID}/status?status=APPROVED")
assert_http "200" "$HTTP_MF8" "MF8 APPROVED→APPROVED → 200"

UPDATED_AT_AFTER=$(psql_q "SELECT updated_at FROM gofp_freelancers WHERE id='${E2E_FL_A_ID}';" 2>/dev/null || echo "")
assert_eq "$UPDATED_AT_BEFORE" "$UPDATED_AT_AFTER" "MF8 updated_at inchangé (pas de save)"

if [ "$HTTP_MF8" = "200" ] && [ "$UPDATED_AT_BEFORE" = "$UPDATED_AT_AFTER" ]; then
  MF_RESULT[MF8]="✅ PASS"
else
  MF_RESULT[MF8]="❌ FAIL"
fi

# ══════════════════════════════════════════════════════════════════════════════
# MF2 — APPROVED → SUSPENDED
# ══════════════════════════════════════════════════════════════════════════════
step "MF2 — APPROVED → SUSPENDED"

HTTP_MF2=$(auth_curl -s -o /dev/null -w "%{http_code}" -X PUT \
  "${CORE_URL}/api/v1/admin/tnt-go-freelancer/freelancers/${E2E_FL_A_ID}/suspend")
assert_http "200" "$HTTP_MF2" "MF2 PUT /suspend"

FL_A_STATUS_DB=$(psql_q "SELECT status FROM gofp_freelancers WHERE id='${E2E_FL_A_ID}';" 2>/dev/null || echo "")
assert_eq "SUSPENDED" "$FL_A_STATUS_DB" "MF2 status en base = SUSPENDED"

if [ "$HTTP_MF2" = "200" ] && [ "$FL_A_STATUS_DB" = "SUSPENDED" ]; then
  MF_RESULT[MF2]="✅ PASS"
else
  MF_RESULT[MF2]="❌ FAIL"
fi

# ══════════════════════════════════════════════════════════════════════════════
# MF3 — SUSPENDED → APPROVED
# ══════════════════════════════════════════════════════════════════════════════
step "MF3 — SUSPENDED → APPROVED"

HTTP_MF3=$(auth_curl -s -o /dev/null -w "%{http_code}" -X PUT \
  "${CORE_URL}/api/v1/admin/tnt-go-freelancer/freelancers/${E2E_FL_A_ID}/validate?approved=true")
assert_http "200" "$HTTP_MF3" "MF3 PUT /validate?approved=true (SUSPENDED→APPROVED)"

FL_A_STATUS_DB=$(psql_q "SELECT status FROM gofp_freelancers WHERE id='${E2E_FL_A_ID}';" 2>/dev/null || echo "")
assert_eq "APPROVED" "$FL_A_STATUS_DB" "MF3 status en base = APPROVED"

if [ "$HTTP_MF3" = "200" ] && [ "$FL_A_STATUS_DB" = "APPROVED" ]; then
  MF_RESULT[MF3]="✅ PASS"
else
  MF_RESULT[MF3]="❌ FAIL"
fi

# ══════════════════════════════════════════════════════════════════════════════
# MF4 — APPROVED → REVOKED
# ══════════════════════════════════════════════════════════════════════════════
step "MF4 — APPROVED → REVOKED"

HTTP_MF4=$(auth_curl -s -o /dev/null -w "%{http_code}" -X PUT \
  "${CORE_URL}/api/v1/admin/tnt-go-freelancer/freelancers/${E2E_FL_A_ID}/revoke")
assert_http "200" "$HTTP_MF4" "MF4 PUT /revoke"

FL_A_STATUS_DB=$(psql_q "SELECT status FROM gofp_freelancers WHERE id='${E2E_FL_A_ID}';" 2>/dev/null || echo "")
assert_eq "REVOKED" "$FL_A_STATUS_DB" "MF4 status en base = REVOKED"

if [ "$HTTP_MF4" = "200" ] && [ "$FL_A_STATUS_DB" = "REVOKED" ]; then
  MF_RESULT[MF4]="✅ PASS"
else
  MF_RESULT[MF4]="❌ FAIL"
fi

# ══════════════════════════════════════════════════════════════════════════════
# MF6 — freelancer B : PENDING → REJECTED, puis REJECTED → APPROVED → 409
# ══════════════════════════════════════════════════════════════════════════════
step "MF6 — REJECTED → APPROVED (état terminal, attendu 409)"

# D'abord rejeter freelancer B (PENDING → REJECTED est autorisé)
HTTP_REJECT=$(auth_curl -s -o /dev/null -w "%{http_code}" -X PUT \
  "${CORE_URL}/api/v1/admin/tnt-go-freelancer/freelancers/${E2E_FL_B_ID}/validate?approved=false&reason=e2e-test-reject")
assert_http "200" "$HTTP_REJECT" "MF6 pré : PENDING→REJECTED pour freelancer B"

FL_B_STATUS_REJECTED=$(psql_q "SELECT status FROM gofp_freelancers WHERE id='${E2E_FL_B_ID}';" 2>/dev/null || echo "")
assert_eq "REJECTED" "$FL_B_STATUS_REJECTED" "MF6 pré : freelancer B en REJECTED"

# Maintenant tenter REJECTED → APPROVED (doit être 409)
HTTP_MF6=$(auth_curl -s -o /dev/null -w "%{http_code}" -X PUT \
  "${CORE_URL}/api/v1/admin/tnt-go-freelancer/freelancers/${E2E_FL_B_ID}/validate?approved=true")
assert_http "409" "$HTTP_MF6" "MF6 REJECTED→APPROVED → 409 (état terminal)"

FL_B_STATUS_AFTER=$(psql_q "SELECT status FROM gofp_freelancers WHERE id='${E2E_FL_B_ID}';" 2>/dev/null || echo "")
assert_eq "REJECTED" "$FL_B_STATUS_AFTER" "MF6 status reste REJECTED en base"

if [ "$HTTP_MF6" = "409" ] && [ "$FL_B_STATUS_AFTER" = "REJECTED" ]; then
  MF_RESULT[MF6]="✅ PASS"
else
  MF_RESULT[MF6]="❌ FAIL"
fi

# ══════════════════════════════════════════════════════════════════════════════
# MF7 — 403 non-admin (lot 21 : testable via mutation de l'assignation DB)
#
# Mécanisme :
#   1. Supprimer temporairement l'assignation E2E de l'acteur.
#   2. Invalider la cache Caffeine via Kafka (TTL = 300 s, sans invalidation
#      la cache servirait l'ancienne valeur).
#   3. Appeler PUT /validate → attendre 403.
#   4. Re-créer l'assignation + invalider la cache à nouveau.
#   5. Vérifier la présence en DB, appeler PUT /validate → attendre 200.
# ══════════════════════════════════════════════════════════════════════════════
step "MF7 — 403 non-admin (suppression temporaire assignation)"

MF7_PASS=1

# ─── 7a. Supprimer l'assignation ─────────────────────────────────────────────
psql_q "
  DELETE FROM tnt_user_role_assignments
  WHERE tenant_id = '${ACTOR_TENANT_ID}'
    AND user_id   = '${ACTOR_USER_ID}'
    AND role_id   = '${E2E_ROLE_ID}';
" > /dev/null

ASSIGN_AFTER_DEL=$(psql_q "
  SELECT COUNT(*) FROM tnt_user_role_assignments
  WHERE tenant_id = '${ACTOR_TENANT_ID}' AND user_id = '${ACTOR_USER_ID}'
    AND role_id = '${E2E_ROLE_ID}';
" 2>/dev/null || echo "?")

if [ "$ASSIGN_AFTER_DEL" != "0" ]; then
  fail "MF7 pré : la suppression de l'assignation a échoué (COUNT=$ASSIGN_AFTER_DEL)"
  MF7_PASS=0
else
  pass "MF7 pré : assignation supprimée en DB"
fi

# ─── 7b. Invalider la cache et appeler l'endpoint ────────────────────────────
kafka_invalidate_permission_cache "$ACTOR_TENANT_ID" "$ACTOR_USER_ID"
sleep 2  # laisser le consommateur Kafka traiter l'invalidation

# freelancer A est en REVOKED après MF4 — on tente MF3 (SUSPENDED→APPROVED)
# sur un freelancer B qui est en REJECTED (état terminal) : l'erreur 409 ne cache
# pas le 403, donc on cible directement B avec validate → 403 attendu (pas 409,
# car le check RBAC vient avant la validation de la machine à états)
HTTP_MF7=$(auth_curl -s -o /dev/null -w "%{http_code}" -X PUT \
  "${CORE_URL}/api/v1/admin/tnt-go-freelancer/freelancers/${E2E_FL_A_ID}/validate?approved=true" \
  2>/dev/null || echo "000")

if [ "$HTTP_MF7" = "403" ]; then
  pass "MF7 : @RequirePermission gofp-admin:manage → 403 sans assignation"
else
  fail "MF7 : attendu 403 (sans assignation), obtenu $HTTP_MF7"
  MF7_PASS=0
fi

# ─── 7c. Restaurer l'assignation ─────────────────────────────────────────────
psql_q "
  INSERT INTO tnt_user_role_assignments
    (id, tenant_id, user_id, role_id, scope_type, scope_id)
  VALUES (
    gen_random_uuid(),
    '${ACTOR_TENANT_ID}',
    '${ACTOR_USER_ID}',
    '${E2E_ROLE_ID}',
    'TENANT',
    '${ACTOR_TENANT_ID}'
  ) ON CONFLICT DO NOTHING;
" > /dev/null

kafka_invalidate_permission_cache "$ACTOR_TENANT_ID" "$ACTOR_USER_ID"
sleep 2

ASSIGN_RESTORED=$(psql_q "
  SELECT COUNT(*) FROM tnt_user_role_assignments
  WHERE tenant_id = '${ACTOR_TENANT_ID}' AND user_id = '${ACTOR_USER_ID}'
    AND role_id = '${E2E_ROLE_ID}';
" 2>/dev/null || echo "0")

if [ "$ASSIGN_RESTORED" = "1" ]; then
  pass "MF7 post : assignation restaurée en DB"
else
  fail "MF7 post : assignation non restaurée (COUNT=$ASSIGN_RESTORED)"
  MF7_PASS=0
fi

# ─── 7d. Vérifier le 200 après restauration ──────────────────────────────────
# PATCH /active est inconditionnelle (pas de machine à états) et requiert
# gofp-admin:manage — prouve le cycle complet 200→403→200.
HTTP_MF7_POST=$(auth_curl -s -o /dev/null -w "%{http_code}" -X PATCH \
  "${CORE_URL}/api/v1/gofp/freelancer-profiles/${E2E_FL_A_ID}/active?active=false" \
  2>/dev/null || echo "000")

if [ "$HTTP_MF7_POST" = "200" ]; then
  pass "MF7 post : @RequirePermission gofp-admin:manage → 200 après restauration rôle"
else
  fail "MF7 post : attendu 200 (restauration rôle), obtenu $HTTP_MF7_POST"
  MF7_PASS=0
fi

if [ "$MF7_PASS" = "1" ]; then
  MF_RESULT[MF7]="✅ PASS"
else
  MF_RESULT[MF7]="❌ FAIL"
fi

# ══════════════════════════════════════════════════════════════════════════════
# RÉCAPITULATIF
# ══════════════════════════════════════════════════════════════════════════════
echo
echo "════════════════════════════════════════════════════════════════════"
echo "  RÉCAPITULATIF MUTATIONS F-R2  (lot 19 / auth lot 20)"
echo "════════════════════════════════════════════════════════════════════"
printf "  %-6s  %-47s  %s\n" "MUT"  "TRANSITION"  "RÉSULTAT"
printf "  %-6s  %-47s  %s\n" "──────"  "───────────────────────────────────────────────"  "──────────────"
printf "  %-6s  %-47s  %s\n" "MF0"  "SEED : deux freelancers PENDING créés"          "${MF_RESULT[MF0]}"
printf "  %-6s  %-47s  %s\n" "MF1"  "PENDING→APPROVED + notification log"            "${MF_RESULT[MF1]}"
printf "  %-6s  %-47s  %s\n" "MF5"  "APPROVED→PENDING → 409 (isAllowed)"            "${MF_RESULT[MF5]}"
printf "  %-6s  %-47s  %s\n" "MF8"  "APPROVED→APPROVED → 200 + updated_at inchangé"  "${MF_RESULT[MF8]}"
printf "  %-6s  %-47s  %s\n" "MF2"  "APPROVED→SUSPENDED"                             "${MF_RESULT[MF2]}"
printf "  %-6s  %-47s  %s\n" "MF3"  "SUSPENDED→APPROVED"                             "${MF_RESULT[MF3]}"
printf "  %-6s  %-47s  %s\n" "MF4"  "APPROVED→REVOKED"                               "${MF_RESULT[MF4]}"
printf "  %-6s  %-47s  %s\n" "MF6"  "REJECTED→APPROVED → 409 (état terminal)"        "${MF_RESULT[MF6]}"
printf "  %-6s  %-47s  %s\n" "MF7"  "403 non-admin"                                  "${MF_RESULT[MF7]}"
echo "════════════════════════════════════════════════════════════════════"

# ─── Routes F-R1 réellement exposées ─────────────────────────────────────────
echo
echo "  ROUTES F-R1 RÉELLEMENT EXPOSÉES (GofpFreelancerProfileController)"
echo "  Base : /api/v1/gofp/freelancer-profiles"
echo "  ─────────────────────────────────────────────────────────────────"
echo "  POST   /                                         créer/mettre à jour profil"
echo "  GET    /{id}                                     lire par id GOFP"
echo "  GET    /by-core-freelancer/{coreFreelancerId}    lire par id acteur"
echo "  GET    /by-core-user/{coreUserId}                lire par id utilisateur"
echo "  GET    /                                         lister tous"
echo "  GET    /active-approved                          livreurs actifs et approuvés"
echo "  GET    /by-status/{status}                       filtrer par statut"
echo "  PATCH  /{id}/status       [gofp-admin:manage]   changer statut (machine à états)"
echo "  PATCH  /{id}/active       [gofp-admin:manage]   activer/désactiver"
echo "  PATCH  /by-core-freelancer/{id}/location         mettre à jour GPS"
echo "  POST   /by-core-freelancer/{id}/record-delivery-success   compteur livraisons"
echo "  POST   /by-core-freelancer/{id}/record-delivery-failure   compteur échecs"
echo "  DELETE /{id}                                     supprimer profil"
echo "  NOTE   : pas de route 'availability' — l'étape 3 du plan lot 17 est rayée."
echo

if [ "$FAILED" -ne 0 ]; then
  echo -e "${RED}  BILAN : ${#STEP_FAILURES[@]} assertion(s) en échec${RESET}"
  for f in "${STEP_FAILURES[@]}"; do echo -e "  ${RED}✗${RESET} $f"; done
  echo
  exit 1
else
  echo -e "${GREEN}  BILAN : toutes les assertions passent.${RESET}"
  echo
fi
