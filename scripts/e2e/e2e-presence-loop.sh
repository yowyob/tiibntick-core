#!/usr/bin/env bash
# ══════════════════════════════════════════════════════════════════════════════
# e2e-presence-loop.sh — F-R3 lot 16
#
# Prouve la boucle GPS end-to-end avec authentification JWT réelle ou bypass dev :
#   JWT réel  → flux OTP via BFF → local core gateway → Kernel de production
#               token validé par le core local contre le JWKS de production
#   Dev-bypass → TNT_AUTH_ALLOW_ANONYMOUS=true côté serveur (WARN affiché)
#
# Ordre de détection :
#   1. TNT_E2E_TOKEN fourni  → AUTH_MODE=jwt  (rejouer sans OTP)
#   2. BFF joignable          → AUTH_MODE=jwt  + flux OTP
#   3. Core répond sans token → AUTH_MODE=dev-bypass  (WARN : JWT non testé)
#   4. Aucun               → ERREUR avec les deux remèdes
#
# Identité JWT :
#   ACTOR_USER_ID   = claim "sub"        (validé par FreelancerLocationController)
#   ACTOR_TENANT_ID = claim "tid"        (validé par TntSecurityConfig)
#   Redis key = tnt:presence:{ACTOR_TENANT_ID}:{ACTOR_USER_ID}
#
# Seed users : uniquement en mode dev-bypass (SEEDED_USER=1 si créé par ce script).
#   En mode jwt, l'utilisateur existe par construction — son absence est un bug de
#   provisioning à signaler, pas à masquer par un INSERT.
#
# Garde-fou jar périmé : compare le jar à la date du dernier commit src/main.
# M5 (régression guard) : COLD_CHECK + upsert intacts, appliqués à ACTOR_*.
#
# Idempotent : peut être relancé sans nettoyage manuel.
# ══════════════════════════════════════════════════════════════════════════════
set -euo pipefail

# ─── Configuration ────────────────────────────────────────────────────────────
CORE_URL="${TNT_CORE_URL:-http://localhost:8080}"
BFF_URL="${TNT_E2E_BFF_URL:-http://localhost:3001}"
E2E_PHONE="${E2E_PHONE:-+237695479355}"
REDIS_CONTAINER="${TNT_REDIS:-tnt-redis}"
PG_CONTAINER="${TNT_POSTGRES:-tnt-postgres}"
PG_DB="tiibntick_core"
PG_USER="tiibntick"

# Constantes dev-bypass (TntSecurityConfig defaults, injectées par devAuthFilter)
DEV_USER_ID="709f0069-c5ed-4d50-8ad0-b6c67a9eb630"
DEV_TENANT_ID="43427172-b6ee-4dbf-9148-96682702ffc9"

# Identité runtime — surchargée en mode jwt (§3.3)
TOKEN=""
AUTH_MODE=""
ACTOR_USER_ID="$DEV_USER_ID"
ACTOR_TENANT_ID="$DEV_TENANT_ID"
PRESENCE_KEY=""   # initialisé après détection auth

# Coordonnées GPS fixées — relues dans les assertions
TEST_LAT="3.8800012"
TEST_LON="11.5180034"

# IDs de données de test (fixes → cleanup idempotent)
E2E_FL_ID="e2e0f1ee-0000-0000-0000-e2e000000001"          # gofp_freelancers.id
E2E_CORE_FL_ID="e2e0f1ee-0000-0000-0000-e2e000000002"     # core_freelancer_id (fictif)
E2E_NEED_ID="e2e0deed-0000-0000-0000-e2e000000001"        # delivery_needs.id
E2E_ADDR1="55555555-5555-5555-5555-555555555501"           # pickup address
E2E_ADDR2="55555555-5555-5555-5555-555555555502"           # delivery address

# Trace si ce script a seedé la ligne users (pour ne supprimer que ce qu'on a créé)
SEEDED_USER=0

# Flag pour le [WARN] §2 lot 17 : repli 11111111-… sur delivery_need.user_id
DELIVERY_USER_FALLBACK=0  # 1 si le delivery_need.user_id ≠ acteur authentifié (repli 11111111-…)

# ─── État global ──────────────────────────────────────────────────────────────
FAILED=0
STEP_FAILURES=()

# ─── Helpers couleur / assert ─────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RESET='\033[0m'
pass()  { echo -e "${GREEN}[PASS]${RESET} $1"; }
fail()  { echo -e "${RED}[FAIL]${RESET} $1"; FAILED=1; STEP_FAILURES+=("$1"); }
warn()  { echo -e "${YELLOW}[WARN]${RESET} $1"; }
step()  { echo; echo "──── $1 ────"; }

assert_eq() {
  local expected="$1" actual="$2" label="$3"
  if [ "$expected" = "$actual" ]; then
    pass "$label  (valeur : '$actual')"
  else
    fail "$label  attendu : '$expected'  obtenu : '$actual'"
  fi
}
assert_in_range() {
  local lo="$1" hi="$2" val="$3" label="$4"
  if [ "$val" -gt "$lo" ] 2>/dev/null && [ "$val" -le "$hi" ] 2>/dev/null; then
    pass "$label  (valeur : $val)"
  else
    fail "$label  attendu : $lo < x ≤ $hi  obtenu : $val"
  fi
}
assert_nonempty() {
  local val="$1" label="$2"
  if [ -n "$val" ] && [ "$val" != "null" ]; then
    pass "$label  (valeur : '$val')"
  else
    fail "$label  (valeur vide ou null)"
  fi
}
assert_approx_eq() {
  local expected="$1" actual="$2" tol="$3" label="$4"
  if [ -z "$actual" ] || [ "$actual" = "null" ]; then
    fail "$label  attendu : ≈$expected  obtenu : <vide>"
    return
  fi
  local ok
  ok=$(awk "BEGIN {d=$actual - $expected; if (d<0) d=-d; print (d<=$tol)?\"ok\":\"fail\"}")
  if [ "$ok" = "ok" ]; then
    pass "$label  (attendu : ≈$expected, obtenu : $actual, Δ≤$tol)"
  else
    fail "$label  attendu : ≈$expected  obtenu : $actual  (Δ>$tol)"
  fi
}
assert_null() {
  local val="$1" label="$2"
  if [ -z "$val" ] || [ "$val" = "null" ]; then
    pass "$label  (valeur : null)"
  else
    fail "$label  attendu : null  obtenu : '$val'"
  fi
}

# ─── Fonctions d'authentification partagées ──────────────────────────────────
# auth_curl, decode_jwt_payload, tnt_otp_flow
source "$(dirname "$0")/lib-auth.sh"

# ─── Helpers infra ────────────────────────────────────────────────────────────
redis_cli() { docker exec "$REDIS_CONTAINER" redis-cli "$@"; }
psql_q()    { docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -At -c "$1"; }

# Vide le cache tracking — force relecture depuis Redis
clear_tracking_cache() {
  redis_cli DEL "tracking:need:${ACTOR_TENANT_ID}:${E2E_NEED_ID}" > /dev/null
}

# Extrait un champ numérique de la réponse JSON du tracking DTO
tracking_field() {
  local json="$1" field="$2"
  echo "$json" | jq -r ".$field // empty" 2>/dev/null || echo ""
}

# ─── Cleanup (trap) ───────────────────────────────────────────────────────────
cleanup() {
  echo
  echo "──── NETTOYAGE ────"
  psql_q "DELETE FROM delivery_needs WHERE id = '${E2E_NEED_ID}';" > /dev/null 2>&1 || true
  psql_q "DELETE FROM gofp_freelancers WHERE id = '${E2E_FL_ID}';" > /dev/null 2>&1 || true
  # Ne supprime la ligne users que si c'est CE script qui l'a créée (SEEDED_USER=1).
  # Un nettoyage qui supprime ce qu'il n'a pas créé est un nettoyage dangereux.
  if [ "$SEEDED_USER" = "1" ]; then
    psql_q "DELETE FROM users WHERE id = '${ACTOR_USER_ID}' AND last_name = 'e2e-test' AND first_name = 'actor';" \
      > /dev/null 2>&1 || true
  fi
  [ -n "$PRESENCE_KEY" ] && redis_cli DEL "$PRESENCE_KEY" > /dev/null 2>&1 || true
  redis_cli DEL "tracking:need:${ACTOR_TENANT_ID}:${E2E_NEED_ID}" > /dev/null 2>&1 || true
  redis_cli DEL "tnt:presence:${ACTOR_TENANT_ID}:${E2E_FL_ID}" > /dev/null 2>&1 || true
  echo "Données de test supprimées."
}
trap cleanup EXIT

# ══════════════════════════════════════════════════════════════════════════════
# 0. GARDE-FOU JAR PÉRIMÉ
#    Compare la date du jar à celle du dernier commit touchant src/main.
#    Évite de tester contre une image Docker vieille de plusieurs heures.
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
    echo "  cd tnt-bootstrap"
    echo "  mvn -pl .. -am package -DskipTests -Denforcer.skip=true"
    echo "  docker compose --profile app build tiibntick-core"
    echo "  docker compose --profile app up -d tiibntick-core"
    echo "  # Attendez ~70 s que l'app soit prête, puis relancez."
    exit 1
  fi
  pass "Jar à jour (jar: $(date -d "@$JAR_MTIME" '+%H:%M:%S' 2>/dev/null || echo "$JAR_MTIME"))"
else
  warn "Jar introuvable ($JAR_PATH) — garde-fou ignoré (image Docker pré-buildée ?)"
fi

# ══════════════════════════════════════════════════════════════════════════════
# 1. PRÉREQUIS
# ══════════════════════════════════════════════════════════════════════════════
step "1. PRÉREQUIS"

# tnt-core joignable
# || echo "000" : protège la substitution contre set -e si curl échoue (code 56 observé)
HTTP_LIVENESS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    "${CORE_URL}/actuator/health/liveness" 2>/dev/null || echo "000")
if [ "$HTTP_LIVENESS" != "200" ]; then
  echo "ERREUR : tnt-core inaccessible (${CORE_URL}/actuator/health/liveness → $HTTP_LIVENESS)"
  exit 1
fi
pass "tnt-core joignable ($CORE_URL)"

# Redis joignable
if ! docker exec "$REDIS_CONTAINER" redis-cli PING > /dev/null 2>&1; then
  echo "ERREUR : Redis inaccessible (conteneur $REDIS_CONTAINER)"
  exit 1
fi
pass "Redis joignable ($REDIS_CONTAINER)"

# Postgres joignable
if ! psql_q "SELECT 1" > /dev/null 2>&1; then
  echo "ERREUR : Postgres inaccessible (conteneur $PG_CONTAINER)"
  exit 1
fi
pass "Postgres joignable ($PG_CONTAINER)"

# ══════════════════════════════════════════════════════════════════════════════
# 2. MODE D'AUTHENTIFICATION
#
# Détection dans l'ordre (§3.1) :
#   1. TNT_E2E_TOKEN  → jwt (rejouer sans OTP)
#   2. BFF joignable  → jwt + flux OTP BFF
#   3. Bypass actif   → dev-bypass (WARN)
#   4. Aucun          → ERREUR
# ══════════════════════════════════════════════════════════════════════════════
step "2. DÉTECTION DU MODE AUTH"

if [ -n "${TNT_E2E_TOKEN:-}" ]; then
  # ── Priorité 1 : jeton fourni explicitement ─────────────────────────────────
  AUTH_MODE="jwt"
  TOKEN="$TNT_E2E_TOKEN"
  echo "  Jeton fourni via TNT_E2E_TOKEN (${TOKEN:0:12}…, longueur ${#TOKEN})"

else
  # ── Priorité 2 : BFF joignable → flux OTP ──────────────────────────────────
  BFF_HEALTH_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
      "${BFF_URL}/health" 2>/dev/null || echo "000")

  if [ "$BFF_HEALTH_CODE" = "200" ]; then
    AUTH_MODE="jwt"
    echo "  BFF joignable ($BFF_URL) — flux OTP pour $E2E_PHONE"
    tnt_otp_flow || exit 1

  else
    # ── Priorité 3 : sonder le bypass ──────────────────────────────────────────
    PROBE_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
        "${CORE_URL}/api/v1/deliveries" 2>/dev/null || echo "000")

    if [ "$PROBE_CODE" != "401" ] && [ "$PROBE_CODE" != "000" ]; then
      AUTH_MODE="dev-bypass"
      warn "Mode dev bypass actif (TNT_AUTH_ALLOW_ANONYMOUS=true côté serveur)."
      warn "La chaîne JWT (signature Kernel, claims sub/tid) N'est PAS testée."
      warn "Ce script prouve le câblage GPS→Redis→lecture, pas l'authentification."
      pass "Bypass détecté (réponse sans jeton : HTTP $PROBE_CODE)"

    else
      echo ""
      echo "ERREUR : Aucun mode d'authentification disponible."
      echo "  BFF    : non joignable ($BFF_URL — HTTP $BFF_HEALTH_CODE)"
      echo "  Bypass : désactivé (${CORE_URL}/api/v1/deliveries → $PROBE_CODE)"
      echo ""
      echo "Remèdes :"
      echo "  1. Démarrer le BFF (port 3001) :"
      echo "     cd /home/jtk/projets/tiibntick-bff && npm start"
      echo "     # Puis relancez ce script."
      echo "  2. Réutiliser un jeton existant (valable ~15 min) :"
      echo "     export TNT_E2E_TOKEN=<votre_access_token>"
      echo "     bash scripts/e2e/e2e-presence-loop.sh"
      echo "  3. (Bypass — JWT non testé) :"
      echo "     TNT_AUTH_ALLOW_ANONYMOUS=true docker compose --profile app up -d tiibntick-core"
      exit 1
    fi
  fi
fi

# ── Résolution de l'identité à partir du jeton ou des constantes bypass ────────
if [ "$AUTH_MODE" = "jwt" ]; then
  JWT_PAYLOAD=$(decode_jwt_payload "$TOKEN")
  echo ""
  echo "  Payload JWT décodé :"
  echo "$JWT_PAYLOAD" | jq '.' 2>/dev/null || echo "  $JWT_PAYLOAD"
  echo ""
  ACTOR_USER_ID=$(echo "$JWT_PAYLOAD" | jq -r '.sub // empty' 2>/dev/null || echo "")
  ACTOR_TENANT_ID=$(echo "$JWT_PAYLOAD" | jq -r '.tid // empty' 2>/dev/null || echo "")
  if [ -z "$ACTOR_USER_ID" ] || [ -z "$ACTOR_TENANT_ID" ]; then
    echo "ERREUR : claims 'sub' ou 'tid' absents du payload JWT."
    echo "  sub        : '${ACTOR_USER_ID:-<absent>}'"
    echo "  tid        : '${ACTOR_TENANT_ID:-<absent>}'"
    echo "Payload brut : $JWT_PAYLOAD"
    exit 1
  fi
else
  # dev-bypass : identité = constantes injectées par devAuthFilter
  ACTOR_USER_ID="$DEV_USER_ID"
  ACTOR_TENANT_ID="$DEV_TENANT_ID"
fi

PRESENCE_KEY="tnt:presence:${ACTOR_TENANT_ID}:${ACTOR_USER_ID}"

echo "  AUTH_MODE    : $AUTH_MODE"
echo "  actorUserId  : $ACTOR_USER_ID"
echo "  tenantId     : $ACTOR_TENANT_ID"
echo "  presenceKey  : $PRESENCE_KEY"

# Provenance du tid (lot 18, expérience contrôlée) :
# L'UUID dbae6615-… a été tiré localement le 2026-08-06, puis déclaré au Kernel
# en Phase 3 comme tenant du compte +237695479355. Le Kernel le restitue dans tid.
# KernelAuthGatewayAdapter ne transmet pas X-Tenant-Id au Kernel (prouvé : console.log
# confirmant X-Tenant-Id=43427172-…, tid JWT=dbae6615-… inchangé). Le tid est donc
# un vrai tenant Kernel, aligné délibérément avec l'UUID du client local.

# ── Sonde GPS : vérifie que l'endpoint est présent dans l'image ────────────────
GPS_PROBE=$(auth_curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    -X PATCH "${CORE_URL}/api/freelancers/${ACTOR_USER_ID}/location" \
    -H "Content-Type: application/json" \
    -d '{"latitude":3.87,"longitude":11.51}' 2>/dev/null || echo "000")
if [ "$GPS_PROBE" = "404" ]; then
  echo "ERREUR : PATCH /api/freelancers/{id}/location renvoie 404."
  echo "Le conteneur tourne peut-être sur une image antérieure à ce lot."
  echo "Reconstruisez l'image :"
  echo "  cd tnt-bootstrap && mvn -pl .. -am package -DskipTests -Denforcer.skip=true"
  echo "  docker compose --profile app build tiibntick-core"
  echo "  docker compose --profile app up -d tiibntick-core"
  exit 1
fi

# ══════════════════════════════════════════════════════════════════════════════
# 3. ÉTAT INITIAL
# ══════════════════════════════════════════════════════════════════════════════
step "3. ÉTAT INITIAL"

INIT_PRESENCE_COUNT=$(redis_cli --scan --pattern 'tnt:presence:*' 2>/dev/null \
    | grep -vc "^$" || echo 0)
INIT_GOFP_USERS=$(psql_q "SELECT COUNT(*) FROM gofp_users;" 2>/dev/null || echo "?")
INIT_GOFP_FL=$(psql_q "SELECT COUNT(*) FROM gofp_freelancers;" 2>/dev/null || echo "?")

echo "  clés tnt:presence:*     : $INIT_PRESENCE_COUNT"
echo "  lignes gofp_users       : $INIT_GOFP_USERS"
echo "  lignes gofp_freelancers : $INIT_GOFP_FL"

# Nettoyage préalable (idempotence)
psql_q "DELETE FROM delivery_needs WHERE id = '${E2E_NEED_ID}';" > /dev/null 2>&1 || true
psql_q "DELETE FROM gofp_freelancers WHERE id = '${E2E_FL_ID}';" > /dev/null 2>&1 || true
redis_cli DEL "$PRESENCE_KEY" > /dev/null 2>&1 || true

# ══════════════════════════════════════════════════════════════════════════════
# 4. PREMIER PING — déclenche GofpUserProvisioningFilter + initialise Kalman
#    Coordonnées Yaoundé (pas 0,0) pour ne pas polluer le cache Kalman.
#    Le cache in-process (GpsPingProcessor) est par delivererId.
# ══════════════════════════════════════════════════════════════════════════════
step "4. PREMIER PING (provisioning trigger + init Kalman Yaoundé)"

# COLD_CHECK : la clé ne doit pas exister avant le premier PATCH.
# Lot 14 : la présence naît exclusivement via PATCH /location (upsert domaine).
redis_cli DEL "$PRESENCE_KEY" > /dev/null 2>&1 || true
COLD_CHECK=$(redis_cli EXISTS "$PRESENCE_KEY")
assert_eq "0" "$COLD_CHECK" "Redis froid : clé de présence absente avant tout ping"

PING1_LAT="3.8790000"
PING1_LON="11.5170000"
PING1_CODE=$(auth_curl -s -o /dev/null -w "%{http_code}" --max-time 10 \
    -X PATCH "${CORE_URL}/api/freelancers/${ACTOR_USER_ID}/location" \
    -H "Content-Type: application/json" \
    -d "{\"latitude\":${PING1_LAT},\"longitude\":${PING1_LON}}" \
    2>/dev/null || echo "000")

assert_eq "200" "$PING1_CODE" "PATCH /api/freelancers/{actorUserId}/location → 200 (premier ping Yaoundé)"

# Vérifie que GofpUserProvisioningFilter a créé la ligne gofp_users
PROV_ROW=$(psql_q "SELECT COUNT(*) FROM gofp_users WHERE core_user_id = '${ACTOR_USER_ID}';" \
    2>/dev/null || echo "0")
assert_eq "1" "$PROV_ROW" "GofpUserProvisioningFilter a créé gofp_users pour actorUserId"

# ══════════════════════════════════════════════════════════════════════════════
# 5. SETUP DONNÉES DE TEST (gofp_freelancers + delivery_needs)
# ══════════════════════════════════════════════════════════════════════════════
step "5. SETUP DONNÉES DE TEST"

# Seed minimal dans users (FK de delivery_needs).
# En mode dev-bypass : DEV_USER_ID n'existe pas dans la table users du Kernel — on
#   l'insère, on mémorise SEEDED_USER=1, et on le supprime au cleanup.
# En mode jwt : l'utilisateur existe par construction (le jeton vient de son auth).
#   S'il manque, c'est un bug de provisioning à signaler, pas à masquer par un INSERT.
if [ "$AUTH_MODE" = "dev-bypass" ]; then
  EXISTING_USER=$(psql_q "SELECT COUNT(*) FROM users WHERE id='${ACTOR_USER_ID}';" 2>/dev/null || echo "0")
  if [ "$EXISTING_USER" = "0" ]; then
    # password : chaîne non-BCrypt → compte inutilisable pour toute tentative de login
    psql_q "
      INSERT INTO users (id, last_name, first_name, password)
      VALUES ('${ACTOR_USER_ID}', 'e2e-test', 'actor', 'NO-AUTH-BYPASS-ONLY')
      ON CONFLICT (id) DO NOTHING;
    " > /dev/null
    SEEDED_USER=1
  fi
  E2E_DELIVERY_USER_ID="$ACTOR_USER_ID"
else
  # Mode jwt : l'utilisateur doit exister (provisionné par le Kernel).
  # S'il manque, le FK delivery_needs.user_id serait violé ; on utilise un user de
  # repli (pré-seedé) pour ne pas bloquer le test GPS, et on enregistre le FAIL.
  JWT_USER_EXISTS=$(psql_q "SELECT COUNT(*) FROM users WHERE id='${ACTOR_USER_ID}';" 2>/dev/null || echo "0")
  if [ "$JWT_USER_EXISTS" = "0" ]; then
    fail "SETUP : actorUserId ($ACTOR_USER_ID) absent de la table users — la table locale ne synchronise pas le Kernel de prod"
    warn "SETUP : repli sur user 11111111-... pour la FK delivery_needs.user_id (n'affecte pas les assertions GPS)"
    warn "AUTORISATION : delivery_need.user_id (11111111-…) ≠ acteur authentifié ($ACTOR_USER_ID) — la lecture GET /tracking réussit sans contrôle de propriété (trou à corriger dans un lot dédié)"
    E2E_DELIVERY_USER_ID="11111111-1111-1111-1111-111111111111"
    DELIVERY_USER_FALLBACK=1
  else
    E2E_DELIVERY_USER_ID="$ACTOR_USER_ID"
  fi
fi

psql_q "
  INSERT INTO gofp_freelancers (id, core_freelancer_id, core_user_id, status, is_active)
  VALUES ('${E2E_FL_ID}', '${E2E_CORE_FL_ID}', '${ACTOR_USER_ID}', 'APPROVED', true)
  ON CONFLICT (id) DO NOTHING;
" > /dev/null

psql_q "
  INSERT INTO delivery_needs
    (id, user_id, title, status, pickup_address_id, delivery_address_id, assigned_freelancer_id)
  VALUES
    ('${E2E_NEED_ID}', '${E2E_DELIVERY_USER_ID}', 'E2E GPS loop test',
     'ASSIGNED', '${E2E_ADDR1}', '${E2E_ADDR2}', '${E2E_FL_ID}')
  ON CONFLICT (id) DO NOTHING;
" > /dev/null

FL_INSERTED=$(psql_q "SELECT core_user_id FROM gofp_freelancers WHERE id = '${E2E_FL_ID}';" \
    2>/dev/null || echo "")
NEED_INSERTED=$(psql_q "SELECT assigned_freelancer_id FROM delivery_needs WHERE id = '${E2E_NEED_ID}';" \
    2>/dev/null || echo "")

assert_eq "$ACTOR_USER_ID" "$FL_INSERTED" "gofp_freelancers.core_user_id == actorUserId"
assert_eq "$E2E_FL_ID"     "$NEED_INSERTED" "delivery_needs.assigned_freelancer_id == e2eFlId"

# ══════════════════════════════════════════════════════════════════════════════
# 6. PING GPS PRINCIPAL — coordonnées mémorisées
# ══════════════════════════════════════════════════════════════════════════════
step "6. PING GPS (lat=${TEST_LAT} lon=${TEST_LON})"

PING2_CODE=$(auth_curl -s -o /dev/null -w "%{http_code}" --max-time 10 \
    -X PATCH "${CORE_URL}/api/freelancers/${ACTOR_USER_ID}/location" \
    -H "Content-Type: application/json" \
    -d "{\"latitude\":${TEST_LAT},\"longitude\":${TEST_LON},\"speedKmh\":15.0,\"bearing\":90.0}" \
    2>/dev/null || echo "000")

assert_eq "200" "$PING2_CODE" "PATCH location → 200"

# ══════════════════════════════════════════════════════════════════════════════
# 7. ASSERTIONS SUR L'ÉCRITURE REDIS
# ══════════════════════════════════════════════════════════════════════════════
step "7. ASSERTIONS REDIS"

# 7a. La clé existe
KEY_EXISTS=$(redis_cli EXISTS "$PRESENCE_KEY")
assert_eq "1" "$KEY_EXISTS" "clé Redis existe : $PRESENCE_KEY"

# 7b. TTL strictement positif et ≤ 90
TTL_VAL=$(redis_cli TTL "$PRESENCE_KEY")
assert_in_range "0" "90" "$TTL_VAL" "TTL ∈ ]0, 90]"

# 7c. La clé est indexée sur actorUserId (sub du JWT, pas gofpLocalId)
KEY_SUFFIX="${PRESENCE_KEY##tnt:presence:${ACTOR_TENANT_ID}:}"
assert_eq "$ACTOR_USER_ID" "$KEY_SUFFIX" "userId dans la clé == actorUserId (claim sub, pas gofpLocalId)"

# 7d. JSON contient exactement les coordonnées envoyées
PRESENCE_JSON=$(redis_cli GET "$PRESENCE_KEY")
REDIS_LAT=$(echo "$PRESENCE_JSON" | jq -r '.latitude // empty' 2>/dev/null || echo "")
REDIS_LON=$(echo "$PRESENCE_JSON" | jq -r '.longitude // empty' 2>/dev/null || echo "")
assert_eq "$TEST_LAT" "$REDIS_LAT" "Redis.latitude == lat envoyée"
assert_eq "$TEST_LON" "$REDIS_LON" "Redis.longitude == lon envoyée"

# 7e. lastSeenAt présent
REDIS_LAST=$(echo "$PRESENCE_JSON" | jq -r '.lastSeenAt // empty' 2>/dev/null || echo "")
assert_nonempty "$REDIS_LAST" "Redis.lastSeenAt non-vide"

echo "  JSON Redis (extrait) :"
echo "$PRESENCE_JSON" | jq '{userId,tenantId,latitude,longitude,lastSeenAt,status}' 2>/dev/null \
    || echo "  $PRESENCE_JSON"

# ══════════════════════════════════════════════════════════════════════════════
# 8. ASSERTIONS SUR LA LECTURE (chemin métier delivery tracking)
# ══════════════════════════════════════════════════════════════════════════════
step "8. LECTURE VIA DELIVERY TRACKING"

TRACKING_RESP=$(auth_curl -s --max-time 10 \
    "${CORE_URL}/api/v1/deliveries/tracking/delivery-need/${E2E_NEED_ID}" \
    2>/dev/null || echo "{}")
TRACKING_CODE=$(auth_curl -s -o /dev/null -w "%{http_code}" --max-time 10 \
    "${CORE_URL}/api/v1/deliveries/tracking/delivery-need/${E2E_NEED_ID}" \
    2>/dev/null || echo "000")

assert_eq "200" "$TRACKING_CODE" "GET tracking/delivery-need → 200"

TRACK_LAT=$(tracking_field "$TRACKING_RESP" "freelancerLatitude")
TRACK_LON=$(tracking_field "$TRACKING_RESP" "freelancerLongitude")
TRACK_POS_AT=$(tracking_field "$TRACKING_RESP" "freelancerPositionAt")

# float32 cast dans le DTO : double→float perd 1-2 ULP → tolérance 0.0001°
assert_approx_eq "$TEST_LAT" "$TRACK_LAT" "0.0001" "tracking.freelancerLatitude ≈ lat envoyée"
assert_approx_eq "$TEST_LON" "$TRACK_LON" "0.0001" "tracking.freelancerLongitude ≈ lon envoyée"
assert_nonempty "$TRACK_POS_AT" "tracking.freelancerPositionAt non-null"

echo "  Réponse tracking (extrait) :"
echo "$TRACKING_RESP" | jq '{deliveryNeedId,freelancerLatitude,freelancerLongitude,freelancerPositionAt}' \
    2>/dev/null || echo "  $TRACKING_RESP"

# ══════════════════════════════════════════════════════════════════════════════
# 9. ASSERTION EXPIRATION (transition present → absent)
# ══════════════════════════════════════════════════════════════════════════════
step "9. TRANSITION PRESENT → ABSENT"

# Vide cache tracking ET clé présence.
# Le cache tracking (Redis, TTL~10s) doit être vidé pour forcer une vraie lecture.
clear_tracking_cache
redis_cli DEL "$PRESENCE_KEY" > /dev/null
KEY_GONE=$(redis_cli EXISTS "$PRESENCE_KEY")
assert_eq "0" "$KEY_GONE" "clé Redis absente après suppression"

ABSENT_RESP=$(auth_curl -s --max-time 10 \
    "${CORE_URL}/api/v1/deliveries/tracking/delivery-need/${E2E_NEED_ID}" \
    2>/dev/null || echo "{}")
ABSENT_CODE=$(auth_curl -s -o /dev/null -w "%{http_code}" --max-time 10 \
    "${CORE_URL}/api/v1/deliveries/tracking/delivery-need/${E2E_NEED_ID}" \
    2>/dev/null || echo "000")

assert_eq "200" "$ABSENT_CODE" "tracking → 200 (delivery-need existe, présence expirée)"

ABSENT_LAT=$(tracking_field "$ABSENT_RESP" "freelancerLatitude")
ABSENT_POS=$(tracking_field "$ABSENT_RESP" "freelancerPositionAt")
ABSENT_NEED_ID=$(tracking_field "$ABSENT_RESP" "deliveryNeedId")

assert_null "$ABSENT_LAT"  "tracking.freelancerLatitude == null (absent, pas position périmée)"
assert_null "$ABSENT_POS"  "tracking.freelancerPositionAt == null (absent)"
assert_nonempty "$ABSENT_NEED_ID" "tracking.deliveryNeedId présent (not notFound)"

# ══════════════════════════════════════════════════════════════════════════════
# 10. COMPTEUR PROVISIONING (observabilité §5)
# ══════════════════════════════════════════════════════════════════════════════
step "10. COMPTEUR PROVISIONING"

METRICS_RESP=$(auth_curl -s --max-time 5 \
    "${CORE_URL}/actuator/metrics/gofp.provisioning.failures" 2>/dev/null || echo "{}")
METRICS_CODE=$(auth_curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    "${CORE_URL}/actuator/metrics/gofp.provisioning.failures" 2>/dev/null || echo "000")

if [ "$METRICS_CODE" = "200" ]; then
  PROV_FAILURES=$(echo "$METRICS_RESP" \
      | jq -r '.measurements[0].value // "unavailable"' 2>/dev/null || echo "unavailable")
  assert_eq "0.0" "$PROV_FAILURES" "gofp.provisioning.failures == 0 (pas d'erreur silencieuse)"
else
  warn "Endpoint /actuator/metrics/gofp.provisioning.failures inaccessible ($METRICS_CODE)"
  warn "Conteneur peut-être sur une image antérieure à l'ajout du compteur Micrometer."
fi

# ══════════════════════════════════════════════════════════════════════════════
# SIMULATIONS DE MUTATIONS (sans rebuild serveur)
# ══════════════════════════════════════════════════════════════════════════════
step "SIMULATION MUTATIONS REDIS (sans rebuild)"

# Remettre la présence pour les mutations suivantes
auth_curl -s -o /dev/null \
    -X PATCH "${CORE_URL}/api/freelancers/${ACTOR_USER_ID}/location" \
    -H "Content-Type: application/json" \
    -d "{\"latitude\":${TEST_LAT},\"longitude\":${TEST_LON}}" \
    2>/dev/null || true

echo
echo "── Simulation M3 : JSON avec lat/lon en dur (valeur différente de celle envoyée) ──"
auth_curl -s -o /dev/null \
    -X PATCH "${CORE_URL}/api/freelancers/${ACTOR_USER_ID}/location" \
    -H "Content-Type: application/json" \
    -d "{\"latitude\":${TEST_LAT},\"longitude\":${TEST_LON}}" \
    2>/dev/null || true
FAKE_JSON=$(redis_cli GET "$PRESENCE_KEY" | jq ".latitude = 0.0 | .longitude = 0.0" 2>/dev/null || echo "")
if [ -n "$FAKE_JSON" ]; then
  CURRENT_TTL=$(redis_cli TTL "$PRESENCE_KEY")
  redis_cli SET "$PRESENCE_KEY" "$FAKE_JSON" EX "$CURRENT_TTL" > /dev/null
  clear_tracking_cache
  FAKE_TRACK=$(auth_curl -s --max-time 10 \
      "${CORE_URL}/api/v1/deliveries/tracking/delivery-need/${E2E_NEED_ID}" \
      2>/dev/null || echo "{}")
  FAKE_LAT=$(tracking_field "$FAKE_TRACK" "freelancerLatitude")
  if [ -z "$FAKE_LAT" ] || [ "$FAKE_LAT" = "null" ]; then
    warn "M3 simulé : lat=null (absence de clé ou désérialisation) — assertion applicable"
  else
    DIFF=$(awk "BEGIN {d=$FAKE_LAT - $TEST_LAT; if (d<0) d=-d; print (d>0.0001)?\"differ\":\"same\"}")
    if [ "$DIFF" = "differ" ]; then
      pass "M3 simulé : lat stockée ($FAKE_LAT) ≠ lat envoyée ($TEST_LAT) → assertion détecte la divergence → FAIL correct"
    else
      fail "M3 simulé : lat=$FAKE_LAT == lat envoyée (mutation non détectée)"
    fi
  fi
fi

echo
echo "── Simulation M4 : suppression clé → état absent ──"
clear_tracking_cache
redis_cli DEL "$PRESENCE_KEY" > /dev/null
M4_RESP=$(auth_curl -s --max-time 10 \
    "${CORE_URL}/api/v1/deliveries/tracking/delivery-need/${E2E_NEED_ID}" \
    2>/dev/null || echo "{}")
M4_LAT=$(tracking_field "$M4_RESP" "freelancerLatitude")
M4_POS=$(tracking_field "$M4_RESP" "freelancerPositionAt")
if [ -z "$M4_LAT" ] || [ "$M4_LAT" = "null" ]; then
  pass "M4 : clé supprimée → freelancerLatitude null (absent, pas notFound ni position périmée)"
else
  fail "M4 : clé supprimée mais freelancerLatitude=$M4_LAT (attendu null)"
fi
if [ -z "$M4_POS" ] || [ "$M4_POS" = "null" ]; then
  pass "M4 : clé supprimée → freelancerPositionAt null"
else
  fail "M4 : clé supprimée mais freelancerPositionAt=$M4_POS (attendu null)"
fi

# Simulation M1 : clé indexée sur gofpLocalId au lieu de actorUserId (coreUserId)
echo
echo "── Simulation M1 : clé indexée sur gofpLocalId au lieu de actorUserId ──"
auth_curl -s -o /dev/null \
    -X PATCH "${CORE_URL}/api/freelancers/${ACTOR_USER_ID}/location" \
    -H "Content-Type: application/json" \
    -d "{\"latitude\":${TEST_LAT},\"longitude\":${TEST_LON}}" \
    2>/dev/null || true
CORRECT_JSON=$(redis_cli GET "$PRESENCE_KEY")
CORRECT_TTL=$(redis_cli TTL "$PRESENCE_KEY")
WRONG_KEY="tnt:presence:${ACTOR_TENANT_ID}:${E2E_FL_ID}"
redis_cli SET "$WRONG_KEY" "$CORRECT_JSON" EX "$CORRECT_TTL" > /dev/null
redis_cli DEL "$PRESENCE_KEY" > /dev/null  # supprime la clé correcte
clear_tracking_cache
# Tracking cherche actorUserId (coreUserId) → clé absente → lat=null → FAIL correct
M1_RESP=$(auth_curl -s --max-time 10 \
    "${CORE_URL}/api/v1/deliveries/tracking/delivery-need/${E2E_NEED_ID}" \
    2>/dev/null || echo "{}")
M1_LAT=$(tracking_field "$M1_RESP" "freelancerLatitude")
if [ -z "$M1_LAT" ] || [ "$M1_LAT" = "null" ]; then
  pass "M1 simulé : lecture sous actorUserId (clé absente) → lat=null → assertion FAIL correct si mutation appliquée"
else
  warn "M1 simulé : tracking.freelancerLatitude=$M1_LAT — cache hit possible, relancez après cleanup"
fi
redis_cli DEL "$WRONG_KEY" > /dev/null  # nettoyage clé fausse

echo
echo "── M5 : regression guard — PATCH depuis Redis froid doit créer la présence ──"
# Ce bloc prouve que le harnais détecte la régression introduite par lot 13B.
# Avec l'ancien code (switchIfEmpty(Mono.empty()) dans updateCoordinates),
# le PATCH renverrait 200 mais ne créerait aucune clé Redis.
redis_cli DEL "$PRESENCE_KEY" > /dev/null 2>&1 || true
M5_ABSENT=$(redis_cli EXISTS "$PRESENCE_KEY")
assert_eq "0" "$M5_ABSENT" "M5 : clé absente avant le PATCH (Redis froid)"
M5_CODE=$(auth_curl -s -o /dev/null -w "%{http_code}" --max-time 10 \
    -X PATCH "${CORE_URL}/api/freelancers/${ACTOR_USER_ID}/location" \
    -H "Content-Type: application/json" \
    -d "{\"latitude\":${TEST_LAT},\"longitude\":${TEST_LON}}" \
    2>/dev/null || echo "000")
assert_eq "200" "$M5_CODE" "M5 : PATCH → 200"
M5_EXISTS=$(redis_cli EXISTS "$PRESENCE_KEY")
assert_eq "1" "$M5_EXISTS" "M5 : PATCH crée la présence en Redis (upsert lot 14 — échouerait avec switchIfEmpty(Mono.empty()))"

echo
echo "── Simulation M2 : TTL=0 (pas d'expiration) ──"
auth_curl -s -o /dev/null \
    -X PATCH "${CORE_URL}/api/freelancers/${ACTOR_USER_ID}/location" \
    -H "Content-Type: application/json" \
    -d "{\"latitude\":${TEST_LAT},\"longitude\":${TEST_LON}}" \
    2>/dev/null || true
REAL_JSON=$(redis_cli GET "$PRESENCE_KEY")
redis_cli SET "$PRESENCE_KEY" "$REAL_JSON" > /dev/null  # SET sans EXPIRE → TTL=-1
M2_TTL=$(redis_cli TTL "$PRESENCE_KEY")
if [ "$M2_TTL" = "-1" ] || ([ "$M2_TTL" -lt 0 ] 2>/dev/null); then
  pass "M2 simulé : TTL=-1 (pas d'expiration) → assertion ]0,90] renverrait FAIL"
  if [ "$M2_TTL" -gt 0 ] 2>/dev/null && [ "$M2_TTL" -le 90 ] 2>/dev/null; then
    fail "M2 simulé : assertion TTL passe à tort (TTL=$M2_TTL)"
  else
    pass "M2 simulé : assertion TTL échouerait (TTL=$M2_TTL, hors ]0,90]) → FAIL correct"
  fi
fi
# Restore la clé avec TTL correct
auth_curl -s -o /dev/null \
    -X PATCH "${CORE_URL}/api/freelancers/${ACTOR_USER_ID}/location" \
    -H "Content-Type: application/json" \
    -d "{\"latitude\":${TEST_LAT},\"longitude\":${TEST_LON}}" \
    2>/dev/null || true

# ══════════════════════════════════════════════════════════════════════════════
# ÉTAT FINAL
# ══════════════════════════════════════════════════════════════════════════════
step "ÉTAT FINAL"
echo "  clés tnt:presence:* : $(redis_cli --scan --pattern 'tnt:presence:*' 2>/dev/null \
    | grep -vc "^$" || echo 0)"
echo "  lignes gofp_users   : $(psql_q "SELECT COUNT(*) FROM gofp_users;" 2>/dev/null || echo "?")"

# ══════════════════════════════════════════════════════════════════════════════
# RÉCAPITULATIF
# ══════════════════════════════════════════════════════════════════════════════
echo
echo "════════════════════════════════════════"
echo "  RÉCAPITULATIF"
echo "════════════════════════════════════════"
echo "  AUTH_MODE    : $AUTH_MODE"
if [ "$AUTH_MODE" = "jwt" ]; then
  echo "  actorUserId  : $ACTOR_USER_ID"
  echo "  tenantId     : $ACTOR_TENANT_ID"
  echo "  jeton        : ${TOKEN:0:12}… (longueur ${#TOKEN})"
else
  echo "  userId       : $ACTOR_USER_ID  (constante bypass)"
  echo "  tenantId     : $ACTOR_TENANT_ID  (constante bypass)"
fi
if [ "$FAILED" = "0" ]; then
  echo -e "${GREEN}  TOUT PASSÉ${RESET} — boucle GPS prouvée de bout en bout."
else
  echo -e "${RED}  ÉCHEC(S) :${RESET}"
  for f in "${STEP_FAILURES[@]}"; do
    echo -e "  ${RED}•${RESET} $f"
  done
fi
# ── Avertissements structurels — visibles même quand tout passe ───────────────
if [ "$DELIVERY_USER_FALLBACK" = "1" ]; then
  echo -e "${YELLOW}  [WARN]${RESET} AUTORISATION : delivery_need.user_id=11111111-… ≠ acteur JWT"
  echo -e "${YELLOW}  [WARN]${RESET} AUTORISATION : GET /tracking réussit sans contrôle de propriété (trou de sécurité à traiter)"
fi
echo "════════════════════════════════════════"
exit "$FAILED"
