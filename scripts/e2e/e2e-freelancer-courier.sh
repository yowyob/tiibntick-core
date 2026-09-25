#!/usr/bin/env bash
# ══════════════════════════════════════════════════════════════════════════════
# e2e-freelancer-courier.sh — Lot B : boucle coursier end-to-end
#
# Prouve la chaîne que le testeur APK va parcourir :
#   voir les missions → accepter → consulter le portefeuille → tenter retrait
#
# Étapes et résultats attendus :
#   C0  Empreinte du binaire   CORE_URL/actuator/info → git.commit.id affiché
#   C1  Auth freelancer        jeton JWT obtenu via BFF OTP
#   C2  Missions disponibles   GET /v1/freelancer/jobs/available → 200 + annonce semée présente
#   C3  Accepter               POST /v1/freelancer/jobs/:id/accept → 200 + status=accepted
#   C4  Récupérer colis        [BLOQUÉ — OTP pickup non récupérable par API]
#   C5  Livrer                 [BLOQUÉ — OTP deliver non récupérable par API]
#   C6  Wallet                 GET /v1/freelancer/wallet → 200 + withdraw_available PRÉSENT
#   C7  Tentative retrait      POST /v1/freelancer/wallet/withdraw → 503 FEATURE_UNAVAILABLE
#   C8  Double accept (garde)  POST /v1/freelancer/jobs/:id/accept x2 → 409 INVALID_STATE
#
# C4/C5 bloqués — preuve par le code core :
#   DeliveryOtpService.java l.67-76  : OTP haché BCrypt, jamais stocké en clair
#   GofpDeliveryController.java l.93-100 : POST /{id}/init-otp → Void, OTP absent
#   ≠ OTP de connexion (lib-auth.sh l.82-84 : previewCode renvoyé en PREVIEW_ONLY)
#
# Usage :
#   bash scripts/e2e/e2e-freelancer-courier.sh
#
#   Variables d'environnement :
#     TNT_CORE_URL      URL du core  (défaut : http://localhost:8080)
#     TNT_E2E_BFF_URL   URL du BFF   (défaut : http://localhost:3001)
#     E2E_PHONE         Numéro du compte freelancer E2E (défaut : +237695479355)
#     TNT_E2E_TOKEN     Jeton existant (évite le flux OTP ; prioritaire)
#     TNT_POSTGRES      Conteneur PostgreSQL (défaut : tnt-postgres)
#
#   Ligne de commande standard (contre le core local) :
#     bash scripts/e2e/e2e-freelancer-courier.sh
#
#   Ligne de commande contre le core de Yowyob :
#     TNT_CORE_URL=https://tiibntick-core.yowyob.com \
#     TNT_E2E_BFF_URL=http://localhost:3001 \
#     bash scripts/e2e/e2e-freelancer-courier.sh
# ══════════════════════════════════════════════════════════════════════════════
set -euo pipefail

# ── Configuration ─────────────────────────────────────────────────────────────
CORE_URL="${TNT_CORE_URL:-http://localhost:8080}"
BFF_URL="${TNT_E2E_BFF_URL:-http://localhost:3001}"
E2E_PHONE="${E2E_PHONE:-+237695479355}"
PG_CONTAINER="${TNT_POSTGRES:-tnt-postgres}"
PG_DB="tiibntick_core"
PG_USER="tiibntick"

# ID fixe pour le seed psql (cleanup idempotent) — UUIDs hex stricts
E2E_ANN_ID_PSQL="e2ebb000-0000-0000-0000-e2ebb0000099"
E2E_PARCEL_ID_PSQL="e2e0ba5e-0000-0000-0000-e2e0ba5e0001"

# ── État global ────────────────────────────────────────────────────────────────
TOKEN=""
TOKEN_FREELANCER=""
FREELANCER_USER_ID=""
CORE_SHA=""
E2E_ANN_ID=""     # fixé par la méthode de seed
SEED_METHOD=""    # "psql" ou "api"
FAILED=0
STEP_FAILURES=()

declare -A CR
for c in C0 C1 C2 C3 C4 C5 C6 C7 C8; do CR[$c]="⬜ non exécuté"; done

# ── Couleurs / helpers ─────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

pass()    { echo -e "${GREEN}[PASS]${RESET} $1"; }
fail()    { echo -e "${RED}[FAIL]${RESET} $1"; FAILED=1; STEP_FAILURES+=("$1"); }
warn()    { echo -e "${YELLOW}[WARN]${RESET} $1"; }
info()    { echo -e "${CYAN}[INFO]${RESET} $1"; }
blocked() { echo -e "${YELLOW}[BLOQUÉ]${RESET} $1"; }
step()    { echo; echo "──── $1 ────"; }

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
    fail "$label → attendu HTTP $expected, obtenu HTTP $actual"
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

# ── Fonctions infra ────────────────────────────────────────────────────────────
psql_available() { docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -At -c "SELECT 1" > /dev/null 2>&1; }
psql_q()         { docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -At -c "$1"; }

# ── Auth partagée ──────────────────────────────────────────────────────────────
source "$(dirname "$0")/lib-auth.sh"

# ── Cleanup idempotent ─────────────────────────────────────────────────────────
cleanup() {
  echo
  echo "──── NETTOYAGE ────"
  if [ "$SEED_METHOD" = "psql" ]; then
    psql_q "DELETE FROM tnt_announcement_responses WHERE announcement_id = '${E2E_ANN_ID_PSQL}';" > /dev/null 2>&1 || true
    psql_q "DELETE FROM tnt_deliveries WHERE announcement_id = '${E2E_ANN_ID_PSQL}';" > /dev/null 2>&1 || true
    psql_q "DELETE FROM tnt_delivery_announcements WHERE id = '${E2E_ANN_ID_PSQL}';" > /dev/null 2>&1 || true
    psql_q "DELETE FROM tnt_parcels WHERE id = '${E2E_PARCEL_ID_PSQL}';" > /dev/null 2>&1 || true
    echo "Annonce + colis + réponses psql supprimés (${E2E_ANN_ID_PSQL})."
  elif [ "$SEED_METHOD" = "api" ] && [ -n "$E2E_ANN_ID" ]; then
    curl -s -o /dev/null -X DELETE \
      -H "Authorization: Bearer ${TOKEN_FREELANCER}" \
      "${CORE_URL}/api/announcements/${E2E_ANN_ID}" 2>/dev/null || true
    echo "Annonce API supprimée (${E2E_ANN_ID})."
  else
    echo "Aucune donnée à supprimer (méthode de seed : '${SEED_METHOD:-aucune}')."
  fi
}
trap cleanup EXIT

# ══════════════════════════════════════════════════════════════════════════════
# C0 — EMPREINTE DU BINAIRE CORE
# ══════════════════════════════════════════════════════════════════════════════
step "C0 — EMPREINTE DU BINAIRE"

INFO_HTTP=$(curl -s -o /tmp/tnt_c0_info.json -w "%{http_code}" --max-time 10 \
  "${CORE_URL}/actuator/info" 2>/dev/null || echo "000")
INFO_BODY=$(cat /tmp/tnt_c0_info.json 2>/dev/null || echo "{}")
CORE_SHA=$(echo "$INFO_BODY" | jq -r '.git.commit.id // empty' 2>/dev/null || echo "")

if [ "$INFO_HTTP" != "200" ]; then
  echo "ERREUR : ${CORE_URL}/actuator/info inaccessible (HTTP $INFO_HTTP)" >&2
  exit 1
fi

if [ -z "$CORE_SHA" ]; then
  fail "C0 : git.commit.id absent de /actuator/info"
  echo "" >&2
  echo "ERREUR BLOQUANTE : sans empreinte du binaire, le rapport ne prouve rien." >&2
  echo "Assurez-vous que le lot infra-1 est déployé sur ce core." >&2
  exit 1
fi

pass "C0 : /actuator/info → HTTP 200, git.commit.id présent"
echo
echo -e "${BOLD}  ┌────────────────────────────────────────────────────────────────┐"
echo    "  │ RAPPORT E2E — Lot B : boucle coursier freelancer                  │"
printf  "  │ Core testé    : %-50s│\n" "$CORE_URL"
printf  "  │ BFF testé     : %-50s│\n" "$BFF_URL"
printf  "  │ git.commit.id : %-50s│\n" "$CORE_SHA"
printf  "  │ Horodatage    : %-50s│\n" "$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
echo -e "  └────────────────────────────────────────────────────────────────┘${RESET}"
echo
CR[C0]="✅ PASS — commit ${CORE_SHA}"

# ══════════════════════════════════════════════════════════════════════════════
# C1 — AUTHENTIFICATION FREELANCER
# ══════════════════════════════════════════════════════════════════════════════
step "C1 — AUTHENTIFICATION FREELANCER"

if [ -n "${TNT_E2E_TOKEN:-}" ]; then
  TOKEN="$TNT_E2E_TOKEN"
  info "Jeton fourni via TNT_E2E_TOKEN (${TOKEN:0:12}…, longueur ${#TOKEN})"
else
  BFF_HEALTH=$(curl -s -o /tmp/tnt_bff_health.json -w "%{http_code}" --max-time 5 \
    "${BFF_URL}/health" 2>/dev/null || echo "000")
  if [ "$BFF_HEALTH" != "200" ]; then
    echo "ERREUR : BFF inaccessible (${BFF_URL}/health → HTTP $BFF_HEALTH)" >&2
    echo "Remèdes :" >&2
    echo "  1. Démarrer le BFF : cd /home/jtk/projets/tiibntick-bff && node --env-file=.env node_modules/.bin/tsx src/index.ts" >&2
    echo "  2. Fournir un jeton : export TNT_E2E_TOKEN=<token>" >&2
    exit 1
  fi
  BFF_ADAPTER=$(cat /tmp/tnt_bff_health.json 2>/dev/null | jq -r '.coreAdapter // "unknown"' 2>/dev/null || echo "unknown")
  info "BFF joignable ($BFF_URL, coreAdapter=$BFF_ADAPTER) — flux OTP pour $E2E_PHONE"
  tnt_otp_flow || exit 1
fi
TOKEN_FREELANCER="$TOKEN"

JWT_PAYLOAD=$(decode_jwt_payload "$TOKEN_FREELANCER")
FREELANCER_USER_ID=$(echo "$JWT_PAYLOAD" | jq -r '.sub // empty' 2>/dev/null || echo "")
assert_nonempty "$FREELANCER_USER_ID" "C1 JWT sub (FREELANCER_USER_ID) non vide"

if [ -n "$FREELANCER_USER_ID" ]; then
  CR[C1]="✅ PASS — userId ${FREELANCER_USER_ID:0:8}…"
else
  CR[C1]="❌ FAIL — userId absent du JWT"
fi

# ══════════════════════════════════════════════════════════════════════════════
# SEED — créer une annonce PUBLISHED visible du freelancer
#
# BFF real : listAvailableJobs → GET /api/announcements → filtre
#   status == "PUBLISHED" && !assignedFreelancerId
#
# Stratégie :
#   1. psql disponible → INSERT direct (id fixe → cleanup idempotent)
#   2. API core → POST /api/announcements (id dynamique → nettoyé via DELETE)
# ══════════════════════════════════════════════════════════════════════════════
step "SEED — annonce de mission E2E"

if psql_available 2>/dev/null; then
  # Nettoyage préventif pour l'idempotence
  # La table lue par GET /api/announcements est tnt_delivery_announcements (tnt-delivery-core).
  # La table announcements (ancien module GOFP plat) n'est PAS interrogée par le BFF.
  psql_q "DELETE FROM tnt_delivery_announcements WHERE id = '${E2E_ANN_ID_PSQL}';" > /dev/null 2>&1 || true
  psql_q "DELETE FROM tnt_parcels WHERE id = '${E2E_PARCEL_ID_PSQL}';" > /dev/null 2>&1 || true

  # Extraire le tenant_id depuis le JWT (claim 'tid') — utilisé comme tenant de l'annonce
  E2E_TENANT_ID=$(decode_jwt_payload "$TOKEN_FREELANCER" 2>/dev/null | jq -r '.tid // empty' 2>/dev/null || echo "")
  if [ -z "$E2E_TENANT_ID" ]; then
    # Tenant par défaut en mode anonyme (TntSecurityConfig.devTenantId)
    E2E_TENANT_ID="43427172-b6ee-4dbf-9148-96682702ffc9"
  fi

  psql_q "
    INSERT INTO tnt_parcels
      (id, weight_kg, width_cm, height_cm, length_cm, fragile, perishable,
       created_at, updated_at, version)
    VALUES
      ('${E2E_PARCEL_ID_PSQL}', 1.0, 20.0, 15.0, 10.0, false, false, NOW(), NOW(), 0)
    ON CONFLICT (id) DO NOTHING;
  " > /dev/null

  psql_q "
    INSERT INTO tnt_delivery_announcements
      (id, tenant_id, client_id, title, description,
       offered_amount, currency, parcel_id, status, urgency,
       pickup_city, delivery_city, recipient_name, recipient_phone,
       created_at, updated_at, version, pricing_mode)
    VALUES
      ('${E2E_ANN_ID_PSQL}',
       '${E2E_TENANT_ID}',
       '${FREELANCER_USER_ID}',
       'E2E Lot B — boucle coursier',
       'Annonce de test automatisé — supprimer si orpheline',
       3000.0, 'XAF', '${E2E_PARCEL_ID_PSQL}', 'PUBLISHED', 'STANDARD',
       'Yaounde', 'Douala', 'Dest E2E', '+237600000000',
       NOW(), NOW(), 0, 'FIXED_PRICE')
    ON CONFLICT (id) DO UPDATE
      SET status = 'PUBLISHED', updated_at = NOW();
  " > /dev/null

  SEED_STATUS=$(psql_q "SELECT status FROM tnt_delivery_announcements WHERE id='${E2E_ANN_ID_PSQL}';" 2>/dev/null || echo "")
  if [ "$SEED_STATUS" = "PUBLISHED" ]; then
    pass "SEED psql : annonce ${E2E_ANN_ID_PSQL} créée (status=PUBLISHED, tenant=${E2E_TENANT_ID:0:8}…)"
    E2E_ANN_ID="$E2E_ANN_ID_PSQL"
    SEED_METHOD="psql"
  else
    fail "SEED psql : annonce non créée (status_lu='$SEED_STATUS')"
    exit 1
  fi

  # Seed la réponse dans tnt_announcement_responses : le BFF passe par subscribe+assign,
  # et le cache subscribedJobs du BFF peut sauter le subscribe entre deux runs.
  # Pour rendre le test idempotent indépendamment de l'état du cache BFF, on pré-seed
  # la réponse que subscribe aurait créée. La réponse DOIT avoir le même FL_ID que
  # ce que /api/v1/freelancers/me retourne (= id dans tnt_actor.freelancer_profiles).
  psql_q "
    DELETE FROM tnt_announcement_responses WHERE announcement_id='${E2E_ANN_ID_PSQL}';
    INSERT INTO tnt_announcement_responses (
      id, announcement_id, delivery_person_id,
      estimated_arrival_time, status, created_at, updated_at, version,
      proposed_currency
    )
    SELECT gen_random_uuid(), '${E2E_ANN_ID_PSQL}', id,
      NOW() + INTERVAL '30 minutes', 'SENT', NOW(), NOW(), 0, 'XAF'
    FROM tnt_actor.freelancer_profiles
    WHERE actor_id='${FREELANCER_USER_ID}'
    LIMIT 1;
  " > /dev/null 2>&1 || warn "SEED : impossible de pré-seeder la réponse (optionnel en mode psql)"

else
  # psql absent → créer via API core (mode remote ou Docker absent)
  info "psql non disponible → seed via POST /api/announcements"

  SEED_HTTP=$(curl -s \
    -o /tmp/tnt_seed_ann.json \
    -w "%{http_code}" \
    --max-time 15 \
    -X POST "${CORE_URL}/api/announcements" \
    -H "Authorization: Bearer ${TOKEN_FREELANCER}" \
    -H "Content-Type: application/json" \
    -d "{
      \"clientId\":\"${FREELANCER_USER_ID}\",
      \"title\":\"E2E Lot B — boucle coursier\",
      \"description\":\"Annonce de test automatisé\",
      \"amount\":3000,
      \"currency\":\"XAF\",
      \"paymentMethod\":\"CASH\",
      \"autoPublish\":true
    }" \
    2>/dev/null || echo "000")
  SEED_BODY=$(cat /tmp/tnt_seed_ann.json 2>/dev/null || echo "{}")

  if [ "$SEED_HTTP" != "201" ] && [ "$SEED_HTTP" != "200" ]; then
    fail "SEED API : POST /api/announcements → HTTP $SEED_HTTP"
    warn "Corps : $(echo "$SEED_BODY" | jq -c '.' 2>/dev/null || echo "$SEED_BODY")"
    echo "" >&2
    echo "ERREUR : impossible de créer l'annonce de seed." >&2
    echo "Remèdes :" >&2
    echo "  1. Lancer avec psql accessible (mode local — conteneur $PG_CONTAINER)" >&2
    echo "  2. Créer l'annonce manuellement et passer TNT_E2E_ANN_ID=<uuid>" >&2
    exit 1
  fi

  E2E_ANN_ID=$(echo "$SEED_BODY" | jq -r '.id // empty' 2>/dev/null || echo "")
  if [ -z "$E2E_ANN_ID" ] || [ "$E2E_ANN_ID" = "null" ]; then
    fail "SEED API : id absent dans la réponse de création"
    exit 1
  fi
  pass "SEED API : annonce ${E2E_ANN_ID} créée (HTTP $SEED_HTTP)"
  SEED_METHOD="api"

  # Publier si pas encore PUBLISHED (autoPublish non garanti)
  ANN_STATUS=$(echo "$SEED_BODY" | jq -r '.status // empty' 2>/dev/null || echo "")
  if [ "$ANN_STATUS" != "PUBLISHED" ]; then
    PUB_HTTP=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 \
      -X PATCH "${CORE_URL}/api/announcements/${E2E_ANN_ID}/publish" \
      -H "Authorization: Bearer ${TOKEN_FREELANCER}" 2>/dev/null || echo "000")
    if [ "$PUB_HTTP" != "200" ]; then
      fail "SEED API : PATCH /publish → HTTP $PUB_HTTP"
      exit 1
    fi
    pass "SEED API : annonce publiée (PATCH /publish → HTTP $PUB_HTTP)"
  fi
fi

info "Annonce de seed : $E2E_ANN_ID (méthode : $SEED_METHOD)"

# ══════════════════════════════════════════════════════════════════════════════
# C2 — MISSIONS DISPONIBLES
# ══════════════════════════════════════════════════════════════════════════════
step "C2 — GET /v1/freelancer/jobs/available"

C2_HTTP=$(curl -s \
  -o /tmp/tnt_c2_jobs.json \
  -w "%{http_code}" \
  --max-time 15 \
  -H "Authorization: Bearer ${TOKEN_FREELANCER}" \
  "${BFF_URL}/v1/freelancer/jobs/available" \
  2>/dev/null || echo "000")
C2_BODY=$(cat /tmp/tnt_c2_jobs.json 2>/dev/null || echo "[]")

assert_http "200" "$C2_HTTP" "C2 GET /v1/freelancer/jobs/available"

IS_ARRAY=$(echo "$C2_BODY" | jq -r 'if type == "array" then "yes" else "no" end' 2>/dev/null || echo "no")
assert_eq "yes" "$IS_ARRAY" "C2 réponse est un tableau JSON"

JOB_COUNT=$(echo "$C2_BODY" | jq 'length' 2>/dev/null || echo "0")
info "C2 : $JOB_COUNT mission(s) disponible(s) retournée(s)"

if echo "$C2_BODY" | jq -e --arg id "$E2E_ANN_ID" 'any(.[]; .id == $id)' > /dev/null 2>&1; then
  pass "C2 : mission semée (${E2E_ANN_ID:0:8}…) présente dans la liste"
else
  fail "C2 : mission semée (${E2E_ANN_ID:0:8}…) ABSENTE de la liste"
  SAMPLE_IDS=$(echo "$C2_BODY" | jq -r '.[0:3] | .[].id' 2>/dev/null | tr '\n' ' ' || echo "(vide)")
  warn "  IDs reçus (3 premiers) : $SAMPLE_IDS"
  warn "  Cause probable en mode mock : le BFF mock retourne job-001/002/003 — jamais un UUID E2E réel."
fi

if [ "$C2_HTTP" = "200" ] && [ "$IS_ARRAY" = "yes" ] && \
   echo "$C2_BODY" | jq -e --arg id "$E2E_ANN_ID" 'any(.[]; .id == $id)' > /dev/null 2>&1; then
  CR[C2]="✅ PASS ($JOB_COUNT missions, annonce semée trouvée)"
else
  CR[C2]="❌ FAIL"
fi

# ══════════════════════════════════════════════════════════════════════════════
# C3 — ACCEPTER LA MISSION
# BFF real → POST /api/announcements/{id}/subscribe + /assign
# ══════════════════════════════════════════════════════════════════════════════
step "C3 — POST /v1/freelancer/jobs/${E2E_ANN_ID:0:8}…/accept"

C3_HTTP=$(curl -s \
  -o /tmp/tnt_c3_accept.json \
  -w "%{http_code}" \
  --max-time 60 \
  -X POST \
  -H "Authorization: Bearer ${TOKEN_FREELANCER}" \
  "${BFF_URL}/v1/freelancer/jobs/${E2E_ANN_ID}/accept" \
  2>/dev/null || echo "000")
C3_BODY=$(cat /tmp/tnt_c3_accept.json 2>/dev/null || echo "{}")

assert_http "200" "$C3_HTTP" "C3 POST /v1/freelancer/jobs/:id/accept"

C3_STATUS=$(echo "$C3_BODY" | jq -r '.status // empty' 2>/dev/null || echo "")
assert_eq "accepted" "$C3_STATUS" "C3 response.status == 'accepted'"

if [ "$C3_HTTP" = "200" ] && [ "$C3_STATUS" = "accepted" ]; then
  CR[C3]="✅ PASS (status=accepted)"
else
  CR[C3]="❌ FAIL (HTTP $C3_HTTP, status='$C3_STATUS')"
  warn "Corps C3 : $(echo "$C3_BODY" | jq -c '.' 2>/dev/null || echo "$C3_BODY")"
fi

# ══════════════════════════════════════════════════════════════════════════════
# C4 — BLOQUÉ : récupération colis (pickup OTP non récupérable)
# ══════════════════════════════════════════════════════════════════════════════
step "C4 — POST /v1/freelancer/jobs/:id/pickup  [BLOQUÉ]"

blocked "C4 pickup — OTP de livraison non récupérable via l'API du core."
echo "  Raison (lue dans le code, pas inventée) :"
echo "  1. DeliveryOtpService.java l.67-76 :"
echo "       String pickupOtp  = otpService.generateOtp();"
echo "       String deliveryOtp = otpService.generateOtp();"
echo "       delivery.setPickupOtpHash(otpService.hashOtp(pickupOtp));   // seul le hash est sauvé"
echo "     → le code en clair est passé à sendOtpNotifications() (push + email) puis jeté."
echo "  2. GofpDeliveryController.java l.93-100 :"
echo "       @PostMapping(\"/{id}/init-otp\")"
echo "       public Mono<ResponseEntity<Void>> initOtp(...)"
echo "     → Void : aucun corps de réponse, aucun OTP dans la réponse HTTP."
echo "  3. Contrairement à l'OTP de connexion (lib-auth.sh l.82-84 : previewCode"
echo "     présent en mode PREVIEW_ONLY du kernel), il n'existe aucun mécanisme"
echo "     de previewCode pour les OTP de livraison."
echo "  Ce que validerait C4 si l'OTP était disponible :"
echo "    PATCH /api/v1/deliveries/{id}/status → {status:'PICKED_UP', confirmationCode: otp}"
echo "    via realCoreFreelancer.ts l.291-298 → la garde BCrypt du core."
CR[C4]="🔴 BLOQUÉ — OTP pickup non récupérable par API (DeliveryOtpService.java l.67-76)"

# ══════════════════════════════════════════════════════════════════════════════
# C5 — BLOQUÉ : livraison (même raison que C4)
# ══════════════════════════════════════════════════════════════════════════════
step "C5 — POST /v1/freelancer/jobs/:id/deliver  [BLOQUÉ]"

blocked "C5 deliver — même blocage que C4 (deliveryOtp, DeliveryOtpService.java l.69)."
CR[C5]="🔴 BLOQUÉ — OTP deliver non récupérable par API (DeliveryOtpService.java l.69)"

# ══════════════════════════════════════════════════════════════════════════════
# C6 — CONSULTER LE WALLET
# Assertion critique : withdraw_available doit être PRÉSENT dans le corps.
# Un champ absent → undefined côté mobile → le bouton Retirer est dans un état inconnu.
# C'est le défaut que le lot A avait omis de vérifier.
# ══════════════════════════════════════════════════════════════════════════════
step "C6 — GET /v1/freelancer/wallet"

C6_HTTP=$(curl -s \
  -o /tmp/tnt_c6_wallet.json \
  -w "%{http_code}" \
  --max-time 15 \
  -H "Authorization: Bearer ${TOKEN_FREELANCER}" \
  "${BFF_URL}/v1/freelancer/wallet" \
  2>/dev/null || echo "000")
C6_BODY=$(cat /tmp/tnt_c6_wallet.json 2>/dev/null || echo "{}")

assert_http "200" "$C6_HTTP" "C6 GET /v1/freelancer/wallet"

WA_PRESENT=$(echo "$C6_BODY" | jq 'has("withdraw_available")' 2>/dev/null || echo "false")
assert_eq "true" "$WA_PRESENT" "C6 withdraw_available PRÉSENT (champ défini, pas undefined)"

WA_VAL=$(echo "$C6_BODY" | jq -r '.withdraw_available' 2>/dev/null || echo "")
assert_eq "false" "$WA_VAL" "C6 withdraw_available == false (endpoint de retrait non implémenté côté core)"

C6_BALANCE=$(echo "$C6_BODY" | jq -r '.balance // empty' 2>/dev/null || echo "")
C6_CURRENCY=$(echo "$C6_BODY" | jq -r '.currency // empty' 2>/dev/null || echo "")
assert_nonempty "$C6_BALANCE"  "C6 wallet.balance présent"
assert_nonempty "$C6_CURRENCY" "C6 wallet.currency présent"

info "C6 wallet : balance=${C6_BALANCE} ${C6_CURRENCY}, withdraw_available=${WA_VAL}"

if [ "$C6_HTTP" = "200" ] && [ "$WA_PRESENT" = "true" ]; then
  CR[C6]="✅ PASS (withdraw_available présent, balance=${C6_BALANCE} ${C6_CURRENCY})"
else
  CR[C6]="❌ FAIL"
fi

# ══════════════════════════════════════════════════════════════════════════════
# C7 — TENTATIVE DE RETRAIT (attendu : 503 FEATURE_UNAVAILABLE)
# realCoreFreelancer.ts l.372-374 : withdraw retourne { ok:false, reason:'feature_unavailable' }
# sans aucun appel au core — le BFF renvoie 503 directement.
# ══════════════════════════════════════════════════════════════════════════════
step "C7 — POST /v1/freelancer/wallet/withdraw"

C7_HTTP=$(curl -s \
  -o /tmp/tnt_c7_withdraw.json \
  -w "%{http_code}" \
  --max-time 15 \
  -X POST \
  -H "Authorization: Bearer ${TOKEN_FREELANCER}" \
  -H "Content-Type: application/json" \
  -d "{\"amount\":1000,\"phone\":\"${E2E_PHONE}\",\"provider\":\"mtn\"}" \
  "${BFF_URL}/v1/freelancer/wallet/withdraw" \
  2>/dev/null || echo "000")
C7_BODY=$(cat /tmp/tnt_c7_withdraw.json 2>/dev/null || echo "{}")

assert_http "503" "$C7_HTTP" "C7 POST /v1/freelancer/wallet/withdraw → 503"

C7_CODE=$(echo "$C7_BODY" | jq -r '.error.code // empty' 2>/dev/null || echo "")
assert_eq "FEATURE_UNAVAILABLE" "$C7_CODE" "C7 error.code == FEATURE_UNAVAILABLE"

if [ "$C7_HTTP" = "503" ] && [ "$C7_CODE" = "FEATURE_UNAVAILABLE" ]; then
  CR[C7]="✅ PASS (503 FEATURE_UNAVAILABLE — retrait non implémenté)"
else
  CR[C7]="❌ FAIL (HTTP $C7_HTTP, code='$C7_CODE')"
fi

# ══════════════════════════════════════════════════════════════════════════════
# C8 — DOUBLE ACCEPT (garde anti-retry)
# Après C3 (accept réussi), l'annonce est ASSIGNED côté core.
# Un second POST /accept → BFF appelle à nouveau POST /api/announcements/{id}/assign
# → le core rejette (annonce déjà assignée) → BFF → 409 INVALID_STATE.
# ══════════════════════════════════════════════════════════════════════════════
step "C8 — double POST /v1/freelancer/jobs/:id/accept (garde anti-retry)"

C8_HTTP=$(curl -s \
  -o /tmp/tnt_c8_accept2.json \
  -w "%{http_code}" \
  --max-time 60 \
  -X POST \
  -H "Authorization: Bearer ${TOKEN_FREELANCER}" \
  "${BFF_URL}/v1/freelancer/jobs/${E2E_ANN_ID}/accept" \
  2>/dev/null || echo "000")
C8_BODY=$(cat /tmp/tnt_c8_accept2.json 2>/dev/null || echo "{}")

assert_http "409" "$C8_HTTP" "C8 2ème accept → 409 (transition interdite)"

C8_CODE=$(echo "$C8_BODY" | jq -r '.error.code // empty' 2>/dev/null || echo "")
assert_eq "INVALID_STATE" "$C8_CODE" "C8 error.code == INVALID_STATE"

if [ "$C8_HTTP" = "409" ] && [ "$C8_CODE" = "INVALID_STATE" ]; then
  CR[C8]="✅ PASS (409 INVALID_STATE — garde anti-retry active)"
else
  CR[C8]="❌ FAIL (HTTP $C8_HTTP, code='$C8_CODE')"
  warn "Corps C8 : $(echo "$C8_BODY" | jq -c '.' 2>/dev/null || echo "$C8_BODY")"
fi

# ══════════════════════════════════════════════════════════════════════════════
# RÉCAPITULATIF
# ══════════════════════════════════════════════════════════════════════════════
echo
echo "════════════════════════════════════════════════════════════════════════"
echo "  RÉCAPITULATIF — Lot B : boucle coursier freelancer"
echo "  Core       : $CORE_URL"
echo "  BFF        : $BFF_URL"
echo "  Commit SHA : $CORE_SHA"
echo "════════════════════════════════════════════════════════════════════════"
printf "  %-4s  %-46s  %s\n" "ÉT." "DESCRIPTION" "RÉSULTAT"
printf "  %-4s  %-46s  %s\n" "────" "──────────────────────────────────────────────" "────────────────────────────────────"
printf "  %-4s  %-46s  %s\n" "C0" "Empreinte binaire (git.commit.id)"               "${CR[C0]}"
printf "  %-4s  %-46s  %s\n" "C1" "Auth freelancer (OTP BFF)"                       "${CR[C1]}"
printf "  %-4s  %-46s  %s\n" "C2" "Missions disponibles (liste + mission semée)"    "${CR[C2]}"
printf "  %-4s  %-46s  %s\n" "C3" "Accepter mission (status=accepted)"              "${CR[C3]}"
printf "  %-4s  %-46s  %s\n" "C4" "Récupérer colis (pickup OTP)"                   "${CR[C4]}"
printf "  %-4s  %-46s  %s\n" "C5" "Livrer (deliver OTP)"                            "${CR[C5]}"
printf "  %-4s  %-46s  %s\n" "C6" "Wallet (withdraw_available présent)"             "${CR[C6]}"
printf "  %-4s  %-46s  %s\n" "C7" "Retrait → 503 FEATURE_UNAVAILABLE"              "${CR[C7]}"
printf "  %-4s  %-46s  %s\n" "C8" "Double accept → 409 INVALID_STATE"              "${CR[C8]}"
echo "════════════════════════════════════════════════════════════════════════"

if [ "$FAILED" -ne 0 ]; then
  echo -e "${RED}  BILAN : ${#STEP_FAILURES[@]} assertion(s) échouée(s)${RESET}"
  for f in "${STEP_FAILURES[@]}"; do echo -e "  ${RED}✗${RESET} $f"; done
  echo
  exit 1
else
  echo -e "${GREEN}  BILAN : C0-C3 et C6-C8 verts. C4/C5 bloqués par nature (OTP non récupérable).${RESET}"
  echo
fi
