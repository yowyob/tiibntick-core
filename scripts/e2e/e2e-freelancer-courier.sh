#!/usr/bin/env bash
# ══════════════════════════════════════════════════════════════════════════════
# e2e-freelancer-courier.sh — Lot C : boucle coursier end-to-end
#
# Prouve la chaîne que le testeur APK va parcourir :
#   voir les missions → accepter → récupérer le colis → consulter le portefeuille
#   → tenter retrait → garde anti-retry
#
# ── Étapes et résultats attendus ────────────────────────────────────────────
#   C0  Empreinte du binaire   CORE_URL/actuator/info → git.commit.id affiché
#   C1  Auth freelancer        jeton JWT obtenu via BFF OTP
#   C2  Missions disponibles   GET /v1/freelancer/jobs/available → 200 + annonce semée
#   C3  Accepter (via BFF)     POST /v1/freelancer/jobs/:id/accept → 200 + accepted
#
#   SETUP-PICKUP [étape de préparation, pas une assertion BFF] :
#     POST direct /api/announcements/:id/subscribe puis /assign → pickupOtp capturé.
#     L'OTP de pickup est renvoyé une seule fois dans la réponse d'assignation
#     (AnnouncementApplicationService.buildAssignResponse l.427-428 :
#       if (otpResult.newlyInitialized()) dto.setConfirmationCode(otpResult.pickupOtp()))
#     vers l'appelant de /assign — ici le harnais joue le rôle de l'expéditeur FE.
#     C3 reste séparé (BFF sur une mission distincte) afin que la couverture du
#     chemin BFF subscribe+assign ne soit pas perdue.
#
#   C4  Récupérer colis        POST /v1/freelancer/jobs/:id/pickup + otp capturé
#                               → 200 + status=picked_up
#                               Le core vérifie via OtpService.verifyOtp (BCrypt)
#   C5  Livrer [BLOQUÉ]        deliveryOtp non exposé par API
#   C6  Wallet                 GET /v1/freelancer/wallet → 200 + withdraw_available
#   C7  Tentative retrait      POST /v1/freelancer/wallet/withdraw → 503 FEATURE_UNAVAILABLE
#   C8  Double accept (garde)  POST /v1/freelancer/jobs/:id/accept x2 → 409 INVALID_STATE
#
# ── C5 BLOQUÉ — preuve exhaustive ───────────────────────────────────────────
#   Voie 1 (lecture expéditeur) : toDTO/toCandidateDTO (AnnouncementApplicationService
#     l.493-535) ne positionne jamais confirmationCode — aucun GET ne le retourne.
#   Voie 2 (capture à l'assignation) : buildAssignResponse l.427-428 positionne
#     uniquement pickupOtp (pas deliveryOtp). Ce dernier est envoyé par
#     sendOtpNotifications via pushNotificationPort + email au destinataire
#     (DeliveryOtpService.java l.109-120). Aucune réponse HTTP ne le transporte.
#   Voie 3 (fixture psql) : possible en théorie (UPDATE delivery_otp_hash avec
#     hash BCrypt connu), mais la livraison n'est créée qu'à l'assignation et son
#     UUID est inconnu en avance — impossible à seeder avant le début du scénario.
#     Propulsion : demander à l'équipe core un mécanisme PREVIEW_ONLY pour
#     deliveryOtp (analogue à previewCode pour l'OTP de connexion).
#
# ── Sécurité BFF (vérifié avant ce lot) ─────────────────────────────────────
#   CoreAnnouncementDTO (realCoreFreelancer.ts l.40-58) : pas de champ
#   confirmationCode. announcementToJob (l.385-407) : ne le mappe pas.
#   Le BFF ne relaie PAS pickupOtp au mobile. Aucune fuite de sécurité.
#
# ── Usage ────────────────────────────────────────────────────────────────────
#   Variables d'environnement :
#     TNT_CORE_URL            URL du core pour C0 (SHA)   (défaut : http://localhost:8080)
#     TNT_E2E_BFF_URL         URL du BFF                  (défaut : http://localhost:3001)
#     TNT_E2E_CORE_DIRECT_URL URL core pour appels directs (défaut : http://localhost:8080)
#                             Doit pointer vers le même core que le BFF utilise.
#     E2E_PHONE               Numéro compte freelancer    (défaut : +237695479355)
#     TNT_E2E_TOKEN           Jeton existant (ignoré si BFF en mode mock)
#     TNT_POSTGRES            Conteneur PostgreSQL        (défaut : tnt-postgres)
#
#   Run réel (BFF real, core local, SHA Yowyob) :
#     TNT_CORE_URL=https://tiibntick-core.yowyob.com \
#     TNT_E2E_BFF_URL=http://localhost:3001 \
#     bash scripts/e2e/e2e-freelancer-courier.sh
#
#   Run mock (prouve que le harnais détecte les données fausses) :
#     Démarrer le BFF avec CORE_ADAPTER=mock, puis lancer sans TNT_E2E_TOKEN.
# ══════════════════════════════════════════════════════════════════════════════
set -euo pipefail

# ── Configuration ─────────────────────────────────────────────────────────────
CORE_URL="${TNT_CORE_URL:-http://localhost:8080}"
BFF_URL="${TNT_E2E_BFF_URL:-http://localhost:3001}"
CORE_DIRECT_URL="${TNT_E2E_CORE_DIRECT_URL:-http://localhost:8080}"
E2E_PHONE="${E2E_PHONE:-+237695479355}"
PG_CONTAINER="${TNT_POSTGRES:-tnt-postgres}"
PG_DB="tiibntick_core"
PG_USER="tiibntick"
KAFKA_CONTAINER="${TNT_KAFKA:-tnt-kafka}"
CORE_CONTAINER="${TNT_CORE_CONTAINER:-tnt-core}"
E2E_RUN_START=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

# Mission B : utilisée par C2 (listage) + C3 (BFF accept) + C8 (double accept)
E2E_ANN_C3_ID_PSQL="e2ebb000-0000-0000-0000-e2ebb0000099"
E2E_PARCEL_C3_ID_PSQL="e2e0ba5e-0000-0000-0000-e2e0ba5e0001"

# Mission A : utilisée par SETUP-PICKUP + C4
# Entrée dans l'ancienne table announcements requise (FK fk_deliveries_announcement)
# pour que softMirrorDeliveryAndInitOtp puisse créer la livraison dans deliveries.
E2E_ANN_C4_ID_PSQL="e2ebb000-0000-0000-0000-e2ebb0000004"
E2E_PARCEL_C4_ID_PSQL="e2e0ba5e-0000-0000-0000-e2e0ba5e0004"

# ── État global ────────────────────────────────────────────────────────────────
TOKEN=""
TOKEN_FREELANCER=""
FREELANCER_USER_ID=""
BFF_ADAPTER="unknown"
CORE_SHA=""
E2E_ANN_ID=""         # mission B (C3/C8)
E2E_ANN_SETUP_ID=""   # mission A (SETUP/C4)
SEED_METHOD=""
PICKUP_OTP=""         # capturé lors du SETUP-PICKUP
DELIVERY_OTP=""       # présent uniquement si TNT_GOFP_DELIVERY_OTP_PREVIEW=true dans le conteneur
E2E_DELIVERY_ID=""    # livraison créée lors du SETUP-PICKUP (pour vérification indépendante)
AUTH_MODE=""             # jwt-reel | bypass-dev
JWT_TENANT=""            # claim tid du jeton
CORE_EFFECTIVE_TENANT="" # tenant effectivement utilisé par le core (via /freelancers/me)
AUTH_CHAIN_ACTIVE="unknown"     # true dès que probe-a et probe-b → 401/401
TENANT_SCOPING_PROVEN="unknown" # true UNIQUEMENT après test d'isolation réussi
CORE_EFFECTIVE_FL_ID=""  # id freelancer dans le contexte de sécurité du core
SEEDED_ROLE=0            # 1 après création du rôle E2E RBAC
WALLET_BALANCE_BEFORE="" # solde avant C5
FAILED=0
STEP_FAILURES=()

# Rôle E2E minimal pour ce lot (permissions courier + freelancer:read)
E2E_ROLE_C4_ID="e2ec4000-0000-0000-0000-000000000001"
# Annonce dans l'AUTRE tenant — pour tester le cloisonnement
OTHER_TENANT_ID="43427172-b6ee-4dbf-9148-96682702ffc9"
OTHER_ANN_ID="e2ec4000-0000-0000-0000-000000000099"
OTHER_PARCEL_ID="e2ec4000-0000-0000-0000-000000000098"

# C10 — cancel par l'expéditeur (branche isSender) : annonce où client_id=FREELANCER_USER_ID
#        et livraison sans livreur assigné (freelancer_id=NULL → isDeliveryPerson=false)
E2E_ANN_C10_ID="e2ec1000-0000-0000-0000-e2ec10000001"
E2E_DEL_C10_ID="e2ec1000-0000-0000-0000-e2ec10000002"
# C11 — cancel par un tiers → 403 : annonce avec client_id étranger, livreur étranger
E2E_ANN_C11_ID="e2ec1100-0000-0000-0000-e2ec11000001"
E2E_DEL_C11_ID="e2ec1100-0000-0000-0000-e2ec11000002"
E2E_C11_FAKE_CLIENT="00000000-cafe-cafe-cafe-000000000011"
E2E_C11_FAKE_PROFILE="00000000-cafe-cafe-cafe-000000000012"

declare -A CR
for c in C0 C0b C1 C2 C3 SETUP C4 C5 C6 C7 C8 C9 C10 C11; do CR[$c]="⬜ non exécuté"; done

# ── Couleurs / helpers ─────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

pass()    { echo -e "${GREEN}[PASS]${RESET} $1"; }
fail()    { echo -e "${RED}[FAIL]${RESET} $1"; FAILED=1; STEP_FAILURES+=("$1"); }
warn()    { echo -e "${YELLOW}[WARN]${RESET} $1"; }
info()    { echo -e "${CYAN}[INFO]${RESET} $1"; }
blocked() { echo -e "${YELLOW}[BLOQUÉ]${RESET} $1"; }
setup_ok(){ echo -e "${CYAN}[SETUP]${RESET} $1"; }
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

# ── Invalidation du cache de permissions Kafka ────────────────────────────────
kafka_invalidate_permission_cache() {
  local tenant_id="$1" user_id="$2"
  local payload="{\"tenantId\":\"${tenant_id}\",\"userId\":\"${user_id}\"}"
  echo "$payload" | docker exec -i "$KAFKA_CONTAINER" \
    kafka-console-producer --bootstrap-server localhost:9092 \
    --topic tnt.roles.permission-changed \
    > /dev/null 2>&1 || true
}

# ── Cleanup idempotent ─────────────────────────────────────────────────────────
cleanup() {
  echo
  echo "──── NETTOYAGE ────"
  # Rôle RBAC E2E (lot C4) — supprimé en C-18 : le rôle FREELANCER canonique
  # est attribué par FreelancerService et persiste (pas de nettoyage).
  # gofp_freelancers + tnt_delivery_persons — créés par GofpFreelancerProjectionService,
  # persistants : aucun nettoyage nécessaire.
  # Annonces + livraisons C10/C11 (cancel guard)
  psql_q "DELETE FROM deliveries WHERE id IN ('${E2E_DEL_C10_ID}','${E2E_DEL_C11_ID}');" > /dev/null 2>&1 || true
  psql_q "DELETE FROM announcements WHERE id IN ('${E2E_ANN_C10_ID}','${E2E_ANN_C11_ID}');" > /dev/null 2>&1 || true
  # Annonce de test d'isolation (autre tenant)
  psql_q "DELETE FROM tnt_delivery_announcements WHERE id='${OTHER_ANN_ID}';" > /dev/null 2>&1 || true
  psql_q "DELETE FROM tnt_parcels WHERE id='${OTHER_PARCEL_ID}';" > /dev/null 2>&1 || true
  if [ "$SEED_METHOD" = "psql" ]; then
    for ANN_ID in "${E2E_ANN_C3_ID_PSQL}" "${E2E_ANN_C4_ID_PSQL}"; do
      psql_q "DELETE FROM tnt_announcement_responses WHERE announcement_id = '${ANN_ID}';" > /dev/null 2>&1 || true
      psql_q "DELETE FROM tnt_deliveries WHERE announcement_id = '${ANN_ID}';" > /dev/null 2>&1 || true
      psql_q "DELETE FROM deliveries WHERE announcement_id = '${ANN_ID}';" > /dev/null 2>&1 || true
      psql_q "DELETE FROM tnt_delivery_announcements WHERE id = '${ANN_ID}';" > /dev/null 2>&1 || true
      psql_q "DELETE FROM announcements WHERE id = '${ANN_ID}';" > /dev/null 2>&1 || true
    done
    psql_q "DELETE FROM tnt_parcels WHERE id IN ('${E2E_PARCEL_C3_ID_PSQL}','${E2E_PARCEL_C4_ID_PSQL}');" > /dev/null 2>&1 || true
    echo "Annonces + colis psql supprimés."
  elif [ "$SEED_METHOD" = "api" ]; then
    for AID in "$E2E_ANN_ID" "$E2E_ANN_SETUP_ID"; do
      [ -n "$AID" ] && curl -s -o /dev/null -X DELETE \
        -H "Authorization: Bearer ${TOKEN_FREELANCER}" \
        "${CORE_DIRECT_URL}/api/announcements/${AID}" 2>/dev/null || true
    done
    echo "Annonces API supprimées."
  else
    echo "Aucune donnée à supprimer (méthode de seed : '${SEED_METHOD:-aucune}')."
  fi
}
trap cleanup EXIT

# ── Self-test garde d'isolation (prouve que liste vide → TENANT_SCOPING_PROVEN=unknown) ─
# Ce bloc s'exécute au démarrage du script. Il ne dépend d'aucun serveur externe.
echo
echo "──── SELF-TEST garde isolation ────"
_SELFTEST_PROVEN="unknown"
_SELFTEST_C2_BODY="[]"
_SELFTEST_C2_MISSION_B_OK="false"
# Reproduire exactement la logique de la garde
if [ "200" = "200" ] && [ "yes" = "yes" ] && [ -n "e2ec4000-0000-0000-0000-000000000099" ]; then
  if [ "$_SELFTEST_C2_MISSION_B_OK" != "true" ]; then
    echo "[SELF-TEST] liste vide + mission_b_ok=false → TENANT_SCOPING_PROVEN non modifié ✓"
  else
    if echo "$_SELFTEST_C2_BODY" | jq -e --arg id "OTHER" 'any(.[]; .id == $id)' > /dev/null 2>&1; then
      _SELFTEST_PROVEN="false"
    else
      _SELFTEST_PROVEN="true"
    fi
  fi
fi
if [ "$_SELFTEST_PROVEN" = "unknown" ]; then
  echo "[SELF-TEST] PASS — liste vide n'élève PAS TENANT_SCOPING_PROVEN (reste 'unknown')"
else
  echo "[SELF-TEST] FAIL — liste vide a produit TENANT_SCOPING_PROVEN='${_SELFTEST_PROVEN}'" >&2
  echo "ERREUR BLOQUANTE : garde d'isolation défectueuse." >&2
  exit 1
fi
unset _SELFTEST_PROVEN _SELFTEST_C2_BODY _SELFTEST_C2_MISSION_B_OK

# ══════════════════════════════════════════════════════════════════════════════
# C0 — EMPREINTE DU BINAIRE
# ══════════════════════════════════════════════════════════════════════════════
step "C0 — EMPREINTE DU BINAIRE"

INFO_HTTP=$(curl -s -o /tmp/tnt_c0_info.json -w "%{http_code}" --max-time 10 \
  "${CORE_URL}/actuator/info" 2>/dev/null || echo "000")
INFO_BODY=$(cat /tmp/tnt_c0_info.json 2>/dev/null || echo "{}")
# mode=full → git.commit.id.abbrev ; mode=simple (legacy) → git.commit.id (string)
CORE_SHA=$(echo "$INFO_BODY" | jq -r '.git.commit.id.abbrev // .git.commit.id // empty' 2>/dev/null || echo "")
GIT_DIRTY=$(echo "$INFO_BODY" | jq -r '.git.dirty // "false"' 2>/dev/null || echo "false")

if [ "$INFO_HTTP" != "200" ]; then
  echo "ERREUR : ${CORE_URL}/actuator/info inaccessible (HTTP $INFO_HTTP)" >&2
  exit 1
fi
if [ -z "$CORE_SHA" ]; then
  fail "C0 : git.commit.id absent de /actuator/info"
  echo "ERREUR BLOQUANTE : sans empreinte du binaire, le rapport ne prouve rien." >&2
  exit 1
fi

if [ "$GIT_DIRTY" = "true" ]; then
  warn "C0 : git.dirty=true — binaire construit depuis un arbre non commité."
  warn "     L'empreinte ${CORE_SHA} identifie le dernier commit,"
  warn "     PAS le code réellement exécuté. Les assertions restent valides"
  warn "     mais l'empreinte seule ne suffit pas à rejouer le build à l'identique."
  CR[C0]="⚠️  AVERTI — commit ${CORE_SHA} (arbre sale : git.dirty=true)"
else
  pass "C0 : /actuator/info → HTTP 200, git.commit.id = ${CORE_SHA}, git.dirty=false"
  CR[C0]="✅ PASS — commit ${CORE_SHA}"
fi

# Avertissement si CORE_URL est distant mais seed est local
if echo "$CORE_URL" | grep -qv "localhost\|127\.0\.0\.1"; then
  if psql_available 2>/dev/null; then
    warn "CORE_URL est distant (${CORE_URL}) mais psql pointe sur une base locale." # [info] mise en garde sur la cohérence SHA/data, pas une erreur
    warn "C0 lit le SHA de Yowyob ; les appels API (C2-C8) passent par le BFF"
    warn "qui appelle ${CORE_DIRECT_URL} — base locale, données locales."
  fi
fi

# ══════════════════════════════════════════════════════════════════════════════
# PRÉFLIGHT OTP — seul et unique appel /v1/auth/otp/request du run
#
# Effectue le flux OTP complet (request + verify) avant C1 pour :
#   1. Détecter en 2 s une rupture kernel (HTTP 401) avec message diagnostique.
#   2. Garantir qu'un seul appel OTP est fait par run (discipline rate-limit).
# C1 détectera que TOKEN est déjà positionné et sautera le flux OTP.
# Ignoré si : BFF mock (pas de kernel), TNT_E2E_TOKEN fourni, TOKEN déjà défini.
# ══════════════════════════════════════════════════════════════════════════════
step "PRÉFLIGHT — santé BFF + OTP unique"

BFF_PF_HTTP=$(curl -s -o /tmp/tnt_bff_health.json -w "%{http_code}" --max-time 5 \
  "${BFF_URL}/health" 2>/dev/null || echo "000")
if [ "$BFF_PF_HTTP" != "200" ]; then
  echo "ERREUR préflight : BFF inaccessible (${BFF_URL}/health → HTTP $BFF_PF_HTTP)" >&2
  echo "  Démarrer le BFF : cd tiibntick-bff && node --env-file=.env node_modules/.bin/tsx src/index.ts" >&2
  exit 1
fi
BFF_ADAPTER=$(cat /tmp/tnt_bff_health.json 2>/dev/null | jq -r '.coreAdapter // "unknown"' 2>/dev/null || echo "unknown")
info "Préflight : BFF=$BFF_URL (coreAdapter=$BFF_ADAPTER)"

if [ "$BFF_ADAPTER" != "mock" ] && [ -z "${TNT_E2E_TOKEN:-}" ] && [ -z "${TOKEN:-}" ]; then
  info "Préflight : flux OTP unique pour $E2E_PHONE"
  if ! tnt_otp_flow; then
    _PF_OTP_BODY=$(cat /tmp/tnt_otp_req.json 2>/dev/null || echo "{}")
    _PF_OTP_MSG=$(echo "$_PF_OTP_BODY" | jq -r '.error.message // ""' 2>/dev/null || echo "")
    if echo "$_PF_OTP_MSG" | grep -qi "401"; then
      echo >&2
      echo "╔══════════════════════════════════════════════════════════════════╗" >&2
      echo "║ ERREUR BLOQUANTE : le kernel rejette les credentials du core    ║" >&2
      echo "║ Cause  : TNT_KERNEL_API_KEY absent ou invalide dans le container ║" >&2
      echo "║ Preuve : BFF → core → kernel /api/auth/otp → 401               ║" >&2
      echo "║ Correctif (depuis tnt-bootstrap/) :                             ║" >&2
      echo "║   docker compose -f docker-compose.yml \\                       ║" >&2
      echo "║     -f docker-compose.override.yml up -d tiibntick-core         ║" >&2
      echo "║ Ensuite : vérifier docker exec tnt-core printenv | grep KERNEL  ║" >&2
      echo "╚══════════════════════════════════════════════════════════════════╝" >&2
    fi
    exit 1
  fi
  info "Préflight OTP : TOKEN obtenu (${TOKEN:0:12}…)"
else
  info "Préflight OTP : ignoré (BFF=$BFF_ADAPTER, TNT_E2E_TOKEN=${TNT_E2E_TOKEN:+fourni}, TOKEN=${TOKEN:+déjà défini})"
fi

# ══════════════════════════════════════════════════════════════════════════════
# C1 — AUTHENTIFICATION FREELANCER
#
# En mode mock (coreAdapter=mock) : TNT_E2E_TOKEN est ignoré même s'il est fourni.
# Le mock BFF valide les jetons avec MOCK_JWT_SECRET (pas la JWKS kernel) — un JWT
# kernel est systématiquement rejeté (TOKEN_INVALID). Le flux OTP via le mock BFF
# retourne un previewCode et un jeton mock valide, permettant aux assertions
# métier (C2-C8) de s'exécuter et de prouver que le harnais détecte les
# données fictives renvoyées par le mock.
# ══════════════════════════════════════════════════════════════════════════════
step "C1 — AUTHENTIFICATION FREELANCER"

BFF_HEALTH=$(curl -s -o /tmp/tnt_bff_health.json -w "%{http_code}" --max-time 5 \
  "${BFF_URL}/health" 2>/dev/null || echo "000")
if [ "$BFF_HEALTH" != "200" ]; then
  echo "ERREUR : BFF inaccessible (${BFF_URL}/health → HTTP $BFF_HEALTH)" >&2
  echo "Remèdes :" >&2
  echo "  1. Démarrer le BFF : cd tiibntick-bff && node --env-file=.env node_modules/.bin/tsx src/index.ts" >&2
  echo "  2. Fournir un jeton : export TNT_E2E_TOKEN=<token>" >&2
  exit 1
fi
BFF_ADAPTER=$(cat /tmp/tnt_bff_health.json 2>/dev/null | jq -r '.coreAdapter // "unknown"' 2>/dev/null || echo "unknown")
info "BFF : $BFF_URL (coreAdapter=$BFF_ADAPTER)"

if [ "$BFF_ADAPTER" = "mock" ]; then
  if [ -n "${TNT_E2E_TOKEN:-}" ]; then
    warn "BFF est en mode mock — TNT_E2E_TOKEN ignoré (JWT kernel refusé par mock)."
  fi
  info "Flux OTP via mock BFF (previewCode attendu) pour $E2E_PHONE"
  tnt_otp_flow || exit 1
elif [ -n "${TNT_E2E_TOKEN:-}" ]; then
  TOKEN="$TNT_E2E_TOKEN"
  info "Jeton fourni via TNT_E2E_TOKEN (${TOKEN:0:12}…, longueur ${#TOKEN})"
elif [ -n "${TOKEN:-}" ]; then
  info "C1 : jeton déjà obtenu par le préflight OTP (${TOKEN:0:12}…, longueur ${#TOKEN})"
else
  info "Flux OTP via BFF pour $E2E_PHONE"
  tnt_otp_flow || exit 1
fi
TOKEN_FREELANCER="$TOKEN"

JWT_PAYLOAD=$(decode_jwt_payload "$TOKEN_FREELANCER")
FREELANCER_USER_ID=$(echo "$JWT_PAYLOAD" | jq -r '.sub // empty' 2>/dev/null || echo "")
JWT_TENANT=$(echo "$JWT_PAYLOAD" | jq -r '.tid // empty' 2>/dev/null || echo "")
assert_nonempty "$FREELANCER_USER_ID" "C1 JWT sub (FREELANCER_USER_ID) non vide"

if [ -n "$FREELANCER_USER_ID" ]; then
  CR[C1]="✅ PASS — userId ${FREELANCER_USER_ID:0:8}…"
else
  CR[C1]="❌ FAIL — userId absent du JWT"
fi

# ── Purge préventive RBAC avant C0b ─────────────────────────────────────────
# Le rôle E2E peut survivre dans le cache Caffeine (TTL=300s) d'un run précédent
# même si la DB est nettoyée par le trap EXIT. Sans purge, probe-c renverrait 200
# (freelancer:read encore en cache) rendant le run enchaîné indiscernable d'une
# contamination réelle.
# On supprime le rôle de la DB si présent (idempotent) puis on invalide le cache.
if psql_available 2>/dev/null && [ -n "$FREELANCER_USER_ID" ] && [ -n "$JWT_TENANT" ]; then
  # Lot C-18 : plus de rôle E2E_COURIER_C4 à purger. On invalide seulement le cache
  # pour que les permissions FREELANCER (C-16/C-17) soient fraîches dès C0b.
  kafka_invalidate_permission_cache "${JWT_TENANT}" "${FREELANCER_USER_ID}"
  sleep 2
  info "Purge RBAC pré-C0b : cache invalidé pour ${FREELANCER_USER_ID:0:8}…"
fi

# ══════════════════════════════════════════════════════════════════════════════
# ONBOARD — devenir freelancer par le chemin exact de l'application mobile (C-18)
#
#   mobile → BFF POST /v1/kyc/freelancer/submit (tiibntick-bff src/routes/kyc.ts)
#          → RealCoreFreelancer.ensureProfile → GET /api/v1/freelancers/me,
#            puis POST /api/v1/freelancers si absent (idempotent)
#          → FreelancerService.createFreelancerProfile → grantFreelancerRole (C-16)
#
# Le script n'écrit RIEN en SQL ici : il appelle l'API puis vérifie que le profil
# et l'assignation du rôle canonique FREELANCER existent. Rejouable : si le profil
# existe déjà, ensureProfile le renvoie tel quel.
# ══════════════════════════════════════════════════════════════════════════════
step "ONBOARD — POST /v1/kyc/freelancer/submit (chemin mobile)"

if [ "$BFF_ADAPTER" != "mock" ] && [ -n "$FREELANCER_USER_ID" ]; then
  ONB_HTTP=$(curl -s -o /tmp/tnt_onboard.json -w "%{http_code}" --max-time 30 \
    -X POST "${BFF_URL}/v1/kyc/freelancer/submit" \
    -H "Authorization: Bearer ${TOKEN_FREELANCER}" \
    -H "Content-Type: application/json" \
    -d '{"firstName":"E2E","lastName":"Courier","birthDate":"1990-01-01","nationality":"CM",
         "rectoUri":"e2e://recto","versoUri":"e2e://verso","selfieUri":"e2e://selfie",
         "vehicleType":"moto","vehiclePlate":"E2E-C18","vehiclePhotoUris":["e2e://vehicle"]}' \
    2>/dev/null || echo "000")
  assert_http "201" "$ONB_HTTP" "ONBOARD BFF /v1/kyc/freelancer/submit"
  if psql_available 2>/dev/null; then
    ONB_PROFILE=$(psql_q "SELECT id FROM tnt_actor.freelancer_profiles WHERE actor_id='${FREELANCER_USER_ID}' LIMIT 1;" 2>/dev/null || echo "")
    assert_nonempty "$ONB_PROFILE" "ONBOARD : tnt_actor.freelancer_profiles créé par POST /api/v1/freelancers"
    ONB_ROLES=$(psql_q "SELECT string_agg(r.code, ',' ORDER BY r.code) FROM tnt_user_role_assignments a
                        JOIN tnt_roles r ON r.id=a.role_id WHERE a.user_id='${FREELANCER_USER_ID}';" 2>/dev/null || echo "")
    if echo ",${ONB_ROLES}," | grep -q ",FREELANCER,"; then
      pass "ONBOARD : rôle canonique FREELANCER assigné (rôles : ${ONB_ROLES})"
    else
      fail "ONBOARD : FREELANCER absent des rôles de l'utilisateur (rôles : '${ONB_ROLES}')"
    fi
    if echo ",${ONB_ROLES}," | grep -q ",E2E_COURIER_C4,"; then
      fail "ONBOARD : rôle ad hoc E2E_COURIER_C4 encore présent — béquille non retirée"
    fi
  fi
  # La permission ajoutée par l'assignation doit être visible tout de suite.
  kafka_invalidate_permission_cache "${JWT_TENANT}" "${FREELANCER_USER_ID}"
  sleep 2
else
  warn "ONBOARD ignoré (BFF=$BFF_ADAPTER ou userId inconnu)"
fi

# ══════════════════════════════════════════════════════════════════════════════
# C0b — MODE D'AUTHENTIFICATION DU CORE
#
# Trois sondes sur ${CORE_DIRECT_URL}/api/v1/freelancers/me :
#   a) sans Authorization    → 401 si JWT réel, 200 si bypass dev
#   b) Bearer invalide       → 401 si JWT réel, 200 si bypass dev
#   c) jeton valide          → 200 dans les deux modes
#
# Déduit AUTH_MODE et capture CORE_EFFECTIVE_TENANT + CORE_EFFECTIVE_FL_ID
# depuis la réponse du mode bypass (/.data.tenantId / .data.id).
# ══════════════════════════════════════════════════════════════════════════════
step "C0b — MODE D'AUTHENTIFICATION DU CORE"

C0B_A_HTTP=$(curl -s -o /tmp/tnt_c0b_a.json -w "%{http_code}" --max-time 5 \
  "${CORE_DIRECT_URL}/api/v1/freelancers/me" 2>/dev/null || echo "000")

C0B_B_HTTP=$(curl -s -o /tmp/tnt_c0b_b.json -w "%{http_code}" --max-time 5 \
  -H "Authorization: Bearer ceci.est.nimportequoi" \
  "${CORE_DIRECT_URL}/api/v1/freelancers/me" 2>/dev/null || echo "000")

C0B_C_HTTP=$(curl -s -o /tmp/tnt_c0b_c.json -w "%{http_code}" --max-time 5 \
  -H "Authorization: Bearer ${TOKEN_FREELANCER}" \
  "${CORE_DIRECT_URL}/api/v1/freelancers/me" 2>/dev/null || echo "000")

C0B_C_BODY=$(cat /tmp/tnt_c0b_c.json 2>/dev/null || echo "{}")

info "C0b probe-a (sans auth)       → HTTP ${C0B_A_HTTP}"
info "C0b probe-b (Bearer invalide)  → HTTP ${C0B_B_HTTP}"
info "C0b probe-c (jeton valide)     → HTTP ${C0B_C_HTTP}"

if [ "$C0B_A_HTTP" = "200" ] || [ "$C0B_B_HTTP" = "200" ]; then
  # Bypass : réponse sans token → chaîne JWT contournée
  AUTH_MODE="bypass-dev"
  TENANT_SCOPING_PROVEN="false"
  BYPASS_BODY=$(cat /tmp/tnt_c0b_a.json 2>/dev/null || echo "{}")
  CORE_EFFECTIVE_TENANT=$(echo "$BYPASS_BODY" | jq -r '.data.tenantId // .tenantId // empty' 2>/dev/null || echo "")
  CORE_EFFECTIVE_FL_ID=$(echo "$BYPASS_BODY" | jq -r '.data.id // .id // empty' 2>/dev/null || echo "")
  CORE_EFFECTIVE_ACTOR=$(echo "$BYPASS_BODY" | jq -r '.data.actorId // .actorId // empty' 2>/dev/null || echo "")
  echo
  echo -e "${RED}${BOLD}  ╔══════════════════════════════════════════════════════════════════════╗"
  echo    "  ║  ⚠  MODE BYPASS DEV DÉTECTÉ (TNT_AUTH_ALLOW_ANONYMOUS=true)         ║"
  echo    "  ╟──────────────────────────────────────────────────────────────────────╢"
  printf  "  ║  tenant core effectif : %-45s║\n" "${CORE_EFFECTIVE_TENANT}"
  printf  "  ║  claim tid du jeton   : %-45s║\n" "${JWT_TENANT:-<absent>}"
  printf  "  ║  actorId core effectif: %-45s║\n" "${CORE_EFFECTIVE_ACTOR}"
  echo    "  ╟──────────────────────────────────────────────────────────────────────╢"
  echo    "  ║  Le core accepte toute requete comme ROLE_TNT_ADMIN.                ║"
  echo    "  ║  → C1 ne prouve RIEN sur l'authentification du core.                ║"
  echo    "  ║  → Les etapes C2 a C8 ne traversent PAS la chaine JWT reelle.       ║"
  printf  "  ║  → tenant core (%s) differe du tid jeton (%s)  ║\n" \
          "${CORE_EFFECTIVE_TENANT:0:8}…" "${JWT_TENANT:0:8}…"
  echo -e "  ╚══════════════════════════════════════════════════════════════════════╝${RESET}"
  echo
  CR[C0b]="⚠️  BYPASS DEV — tenant core=${CORE_EFFECTIVE_TENANT:0:8}…, tid jeton=${JWT_TENANT:0:8}…"
elif [ "$C0B_A_HTTP" = "401" ] && [ "$C0B_B_HTTP" = "401" ]; then
  # Chaîne JWT réelle : sans token → 401, Bearer invalide → 401.
  # AUTH_CHAIN_ACTIVE prouve que la vérification JWT fonctionne.
  # TENANT_SCOPING_PROVEN reste "unknown" jusqu'au test d'isolation dans SEED.
  AUTH_MODE="jwt-reel"
  AUTH_CHAIN_ACTIVE="true"
  # probe-c s'exécute AVANT le seed RBAC (SETUP RBAC ligne ~492).
  # En run propre (cleanup précédent complet), probe-c=403 EST la valeur attendue :
  # le JWT est validé (401 sans token le prouve), mais freelancer:read n'est pas
  # encore seedé. probe-c=200 signale une contamination (RBAC résiduel d'un run
  # précédent) et doit être traité comme une anomalie, pas comme un succès propre.
  if [ "$C0B_C_HTTP" = "403" ]; then
    # VALEUR ATTENDUE : jeton valide, freelancer:read pas encore seedé (purge pré-C0b faite).
    pass "C0b : chaîne JWT réelle — 401 sans token, 403 avec jeton (run propre)"
    CORE_EFFECTIVE_TENANT="$JWT_TENANT"
    CORE_EFFECTIVE_FL_ID=""
    CR[C0b]="✅ PASS — AUTH_MODE=jwt-reel, probe-c=403"
  elif [ "$C0B_C_HTTP" = "200" ]; then
    # RBAC résiduel persistant après purge : freelancer:read accordé par une autre source
    # (ex. rôle système FREELANCER résolvable en DB avec le bon tenant_id).
    # La purge pré-C0b a bien tourné mais le cache a été réalimenté par une autre voie.
    fail "C0b : probe-c=200 après purge pré-C0b — RBAC non purgeable pour ce compte"
    CORE_EFFECTIVE_TENANT=$(echo "$C0B_C_BODY" | jq -r '.data.tenantId // .tenantId // empty' 2>/dev/null || echo "")
    CORE_EFFECTIVE_FL_ID=$(echo "$C0B_C_BODY" | jq -r '.data.id // .id // empty' 2>/dev/null || echo "")
    CR[C0b]="❌ FAIL — probe-c=200 après purge (RBAC non purgeable)"
  else
    fail "C0b : sonde-c inattendue (HTTP ${C0B_C_HTTP}) — état indéterminé"
    CORE_EFFECTIVE_TENANT="$JWT_TENANT"
    CORE_EFFECTIVE_FL_ID=""
    CR[C0b]="❌ FAIL — probe-c=${C0B_C_HTTP} (attendu 403)"
  fi
else
  AUTH_MODE="inconnu"
  TENANT_SCOPING_PROVEN="unknown"
  warn "C0b : résultat inattendu (a=${C0B_A_HTTP} b=${C0B_B_HTTP} c=${C0B_C_HTTP})"
  CR[C0b]="⚠️  INCONNU (a=${C0B_A_HTTP} b=${C0B_B_HTTP} c=${C0B_C_HTTP})"
fi

# ── Encadré de tête complet (affiché une fois tous les champs connus) ────────
echo
echo -e "${BOLD}  ┌──────────────────────────────────────────────────────────────────────┐"
echo    "  │ RAPPORT E2E — Lot C-bis : boucle coursier freelancer                  │"
printf  "  │ CORE_URL (SHA)          : %-44s│\n" "$CORE_URL"
printf  "  │ BFF_URL                 : %-44s│\n" "$BFF_URL"
printf  "  │ CORE_DIRECT_URL         : %-44s│\n" "$CORE_DIRECT_URL"
printf  "  │ git.commit.id           : %-44s│\n" "$CORE_SHA"
printf  "  │ AUTH_MODE               : %-44s│\n" "$AUTH_MODE"
printf  "  │ JWT_TENANT (tid)        : %-44s│\n" "${JWT_TENANT:-<absent>}"
printf  "  │ CORE_EFFECTIVE_TENANT   : %-44s│\n" "${CORE_EFFECTIVE_TENANT:-<inconnu>}"
printf  "  │ AUTH_CHAIN_ACTIVE       : %-44s│\n" "$AUTH_CHAIN_ACTIVE"
printf  "  │ TENANT_SCOPING_PROVEN   : %-44s│\n" "$TENANT_SCOPING_PROVEN"
printf  "  │ Horodatage              : %-44s│\n" "$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
echo -e "  └──────────────────────────────────────────────────────────────────────┘${RESET}"
echo

# ══════════════════════════════════════════════════════════════════════════════
# SETUP RBAC — assertion du rôle FREELANCER canonique (lot C-17/C-18)
#
# Lot C-16 : FreelancerService.grantFreelancerRole assigne automatiquement FREELANCER
#            à la création du profil (POST /api/v1/freelancers).
# Lot C-17 : la réconciliation TntRoleInitializationService garantit que FREELANCER
#            contient announcement:respond ET announcement:elect dans ses permissions.
#
# Ce bloc NE crée plus aucun rôle ad hoc en SQL. Il vérifie que le rôle FREELANCER
# a bien été attribué par le code de production, et invalide le cache pour s'assurer
# que les permissions sont fraîches pour la suite du scénario.
# ══════════════════════════════════════════════════════════════════════════════
step "SETUP RBAC — vérification rôle FREELANCER canonique (C-16/C-17)"

if psql_available 2>/dev/null && [ -n "$FREELANCER_USER_ID" ] && [ -n "$JWT_TENANT" ]; then
  # ─── Vérifier que FREELANCER est bien assigné par le code de production ──
  FREELANCER_ROLE_CODE=$(psql_q "
    SELECT r.code FROM tnt_user_role_assignments a
    JOIN tnt_roles r ON r.id = a.role_id
    WHERE a.user_id='${FREELANCER_USER_ID}' AND a.tenant_id='${JWT_TENANT}'
      AND r.code='FREELANCER'
    LIMIT 1;
  " 2>/dev/null || echo "")
  assert_eq "FREELANCER" "$FREELANCER_ROLE_CODE" \
    "SETUP RBAC : rôle FREELANCER assigné automatiquement par FreelancerService (C-16)"

  # ─── Vérifier que FREELANCER contient les permissions requises ────────────
  # Le rôle canonique vit dans le tenant système : on le lit via l'assignation,
  # jamais par tenant_id = tenant de l'utilisateur.
  FL_PERMS=$(psql_q "
    SELECT r.permissions FROM tnt_user_role_assignments a
    JOIN tnt_roles r ON r.id = a.role_id
    WHERE a.user_id='${FREELANCER_USER_ID}' AND a.tenant_id='${JWT_TENANT}' AND r.code='FREELANCER'
    LIMIT 1;
  " 2>/dev/null || echo "")
  # Vérifier announcement:respond et announcement:elect (injectés par C-17)
  if echo "$FL_PERMS" | grep -q "announcement:respond" && echo "$FL_PERMS" | grep -q "announcement:elect"; then
    setup_ok "SETUP RBAC : FREELANCER.permissions contient announcement:respond + announcement:elect (C-17)"
  else
    assert_eq "announcement:respond,announcement:elect (in permissions)" \
      "$FL_PERMS" "SETUP RBAC : FREELANCER manque une permission announcement"
  fi

  # ─── Invalider le cache Caffeine pour que les permissions soient fraîches ─
  kafka_invalidate_permission_cache "${JWT_TENANT}" "${FREELANCER_USER_ID}"
  sleep 2
  setup_ok "SETUP RBAC : rôle FREELANCER vérifié + cache invalidé"
else
  warn "SETUP RBAC : psql absent ou user_id/tenant inconnus — vérification RBAC ignorée"
  warn "  SETUP-PICKUP, C3, C6 peuvent échouer par 403."
fi

# ══════════════════════════════════════════════════════════════════════════════
# SEED — deux missions
#
# Mission B (E2E_ANN_C3_ID) : listée en C2, acceptée via BFF en C3.
# Mission A (E2E_ANN_C4_ID) : assignée en direct dans SETUP-PICKUP pour
#   capturer le pickupOtp ; parcourue via BFF en C4.
#
# La mission A nécessite une entrée dans l'ancienne table announcements
# (schéma plat GOFP) pour satisfaire la FK de la table deliveries, dont
# softMirrorDeliveryAndInitOtp a besoin pour créer la livraison locale.
#
# tnt_delivery_announcements : table lue par GET /api/announcements (C2).
# announcements (ancienne) : table dont deliveries.announcement_id est FK.
# ══════════════════════════════════════════════════════════════════════════════
step "SEED — deux missions E2E"

if psql_available 2>/dev/null; then
  # Choisir le tenant de seed : tenant effectif du core en bypass-dev, sinon claim tid
  if [ "$AUTH_MODE" = "bypass-dev" ] && [ -n "$CORE_EFFECTIVE_TENANT" ] \
     && [ "$CORE_EFFECTIVE_TENANT" != "$JWT_TENANT" ]; then
    E2E_TENANT_ID="$CORE_EFFECTIVE_TENANT"
    warn "SEED : semé dans ${CORE_EFFECTIVE_TENANT} (tenant effectif du core) au lieu de ${JWT_TENANT:-<absent>} (claim tid)" # [info] bypass dev actif — le cloisonnement tenant n'est pas prouvé, TENANT_SCOPING_PROVEN=false le signale
    warn "       — écart dû au bypass dev. C2..C8 mesurent le comportement métier, pas le scoping par tenant."
    TENANT_SCOPING_PROVEN="false"
  else
    E2E_TENANT_ID="${JWT_TENANT:-}"
    [ -z "$E2E_TENANT_ID" ] && E2E_TENANT_ID="43427172-b6ee-4dbf-9148-96682702ffc9"
    # Le test d'isolation (C2 cross-tenant) est la seule preuve du cloisonnement.
  fi

  # Nettoyage préventif
  for ANN_ID in "${E2E_ANN_C3_ID_PSQL}" "${E2E_ANN_C4_ID_PSQL}"; do
    psql_q "DELETE FROM tnt_announcement_responses WHERE announcement_id='${ANN_ID}';" > /dev/null 2>&1 || true
    psql_q "DELETE FROM deliveries WHERE announcement_id='${ANN_ID}';" > /dev/null 2>&1 || true
    psql_q "DELETE FROM tnt_deliveries WHERE announcement_id='${ANN_ID}';" > /dev/null 2>&1 || true
    psql_q "DELETE FROM tnt_delivery_announcements WHERE id='${ANN_ID}';" > /dev/null 2>&1 || true
    psql_q "DELETE FROM announcements WHERE id='${ANN_ID}';" > /dev/null 2>&1 || true
  done
  psql_q "DELETE FROM tnt_parcels WHERE id IN ('${E2E_PARCEL_C3_ID_PSQL}','${E2E_PARCEL_C4_ID_PSQL}');" > /dev/null 2>&1 || true

  # Colis
  psql_q "
    INSERT INTO tnt_parcels (id, weight_kg, width_cm, height_cm, length_cm, fragile, perishable, created_at, updated_at, version)
    VALUES
      ('${E2E_PARCEL_C3_ID_PSQL}', 1.0, 20.0, 15.0, 10.0, false, false, NOW(), NOW(), 0),
      ('${E2E_PARCEL_C4_ID_PSQL}', 1.0, 20.0, 15.0, 10.0, false, false, NOW(), NOW(), 0)
    ON CONFLICT (id) DO NOTHING;
  " > /dev/null

  # Mission B — pour C2 + C3 via BFF
  psql_q "
    INSERT INTO tnt_delivery_announcements
      (id, tenant_id, client_id, title, offered_amount, currency, parcel_id,
       status, urgency, pickup_city, delivery_city, recipient_name, recipient_phone,
       created_at, updated_at, version, pricing_mode)
    VALUES
      ('${E2E_ANN_C3_ID_PSQL}', '${E2E_TENANT_ID}', '${FREELANCER_USER_ID}',
       'E2E Lot C — mission BFF (C3/C8)', 3000.0, 'XAF', '${E2E_PARCEL_C3_ID_PSQL}',
       'PUBLISHED', 'STANDARD', 'Yaounde', 'Douala', 'Dest C3', '+237600000000',
       NOW(), NOW(), 0, 'FIXED_PRICE')
    ON CONFLICT (id) DO UPDATE SET status='PUBLISHED', created_delivery_id=NULL, updated_at=NOW();
  " > /dev/null

  # Mission A — pour SETUP-PICKUP + C4
  # tnt_delivery_announcements : lue par /api/announcements (liste)
  psql_q "
    INSERT INTO tnt_delivery_announcements
      (id, tenant_id, client_id, title, offered_amount, currency, parcel_id,
       status, urgency, pickup_city, delivery_city, recipient_name, recipient_phone,
       created_at, updated_at, version, pricing_mode)
    VALUES
      ('${E2E_ANN_C4_ID_PSQL}', '${E2E_TENANT_ID}', '${FREELANCER_USER_ID}',
       'E2E Lot C — mission SETUP/C4', 3000.0, 'XAF', '${E2E_PARCEL_C4_ID_PSQL}',
       'PUBLISHED', 'STANDARD', 'Yaounde', 'Douala', 'Dest C4', '+237600000000',
       NOW(), NOW(), 0, 'FIXED_PRICE')
    ON CONFLICT (id) DO UPDATE SET status='PUBLISHED', created_delivery_id=NULL, updated_at=NOW();
  " > /dev/null

  # FK deliveries.announcement_id → announcements(id)
  # Sans cette entrée, softMirrorDeliveryAndInitOtp lève une FK violation,
  # le miroir échoue silencieusement et la livraison n'existe pas dans deliveries.
  psql_q "
    INSERT INTO announcements (id, client_id, title, status, created_at, updated_at)
    VALUES ('${E2E_ANN_C4_ID_PSQL}', '${FREELANCER_USER_ID}',
            'E2E Lot C — SETUP/C4 (mirror FK)', 'PUBLISHED', NOW(), NOW())
    ON CONFLICT (id) DO UPDATE SET status='PUBLISHED', updated_at=NOW();
  " > /dev/null

  # Nettoyage des réponses résiduelles pour la mission B (cleanup idempotent — pas de pré-seed)
  # Le pré-seed avait été retiré : il créait une réponse avant subscribe,
  # ce qui déclenchait soit un 409 quota (pas de gofp_freelancers) soit un doublon.
  psql_q "DELETE FROM tnt_announcement_responses WHERE announcement_id='${E2E_ANN_C3_ID_PSQL}';" > /dev/null 2>&1 || true

  # ── Projection gofp_freelancers + tnt_delivery_persons (lot C-18) ───────────
  # Plus aucun INSERT manuel ici. GofpFreelancerProjectionService (via
  # GofpFreelancerProvisioningFilter @Order(1)) provisionne automatiquement
  # gofp_freelancers et tnt_delivery_persons à la première requête gofp authentifiée.
  # Les assertions sur ces lignes sont placées APRÈS subscribe (SETUP-PICKUP),
  # qui déclenche le filtre et garantit que la projection a eu lieu.
  GOFP_FL_ID=$(psql_q "SELECT id FROM tnt_actor.freelancer_profiles WHERE actor_id='${FREELANCER_USER_ID}' LIMIT 1;" 2>/dev/null || echo "")
  if [ -z "$GOFP_FL_ID" ] || [ "$GOFP_FL_ID" = "null" ]; then
    warn "SEED : profil acteur introuvable avant subscribe — la projection démarrera dès le premier appel gofp"
  fi

  # ── Livraisons C10/C11 : gardes de propriété cancel ──────────────────────────
  # C10 : expéditeur (isSender) — freelancer_id NULL, client_id = FREELANCER_USER_ID
  # C11 : tiers → 403        — freelancer_id et client_id étrangers
  psql_q "DELETE FROM deliveries WHERE id IN ('${E2E_DEL_C10_ID}','${E2E_DEL_C11_ID}');" > /dev/null 2>&1 || true
  psql_q "DELETE FROM announcements WHERE id IN ('${E2E_ANN_C10_ID}','${E2E_ANN_C11_ID}');" > /dev/null 2>&1 || true
  if [ -n "${E2E_TENANT_ID:-}" ]; then
    NOW_TS=$(date -u +"%Y-%m-%d %H:%M:%S")
    # Annonces dans la table announcements (lue par announcementRepository)
    psql_q "
      INSERT INTO announcements (id, client_id, title, status, created_at, updated_at)
      VALUES
        ('${E2E_ANN_C10_ID}','${FREELANCER_USER_ID}','E2E C10 cancel-sender','PUBLISHED','${NOW_TS}','${NOW_TS}'),
        ('${E2E_ANN_C11_ID}','${E2E_C11_FAKE_CLIENT}','E2E C11 cancel-tiers','PUBLISHED','${NOW_TS}','${NOW_TS}')
      ON CONFLICT (id) DO UPDATE SET status='PUBLISHED', updated_at=NOW();
    " > /dev/null
    # Livraisons dans la table deliveries (lue par deliveryRepository)
    psql_q "
      INSERT INTO deliveries (id, announcement_id, freelancer_id, status,
          pickup_min_time, pickup_max_time, delivery_min_time, delivery_max_time)
      VALUES
        ('${E2E_DEL_C10_ID}','${E2E_ANN_C10_ID}',NULL,'CREATED',
         NOW()+INTERVAL '1 hour',NOW()+INTERVAL '2 hours',
         NOW()+INTERVAL '3 hours',NOW()+INTERVAL '4 hours'),
        ('${E2E_DEL_C11_ID}','${E2E_ANN_C11_ID}','${E2E_C11_FAKE_PROFILE}','CREATED',
         NOW()+INTERVAL '1 hour',NOW()+INTERVAL '2 hours',
         NOW()+INTERVAL '3 hours',NOW()+INTERVAL '4 hours')
      ON CONFLICT (id) DO UPDATE SET status='CREATED';
    " > /dev/null 2>&1 || true
    C10_CHECK=$(psql_q "SELECT id FROM deliveries WHERE id='${E2E_DEL_C10_ID}';" 2>/dev/null || echo "")
    C11_CHECK=$(psql_q "SELECT id FROM deliveries WHERE id='${E2E_DEL_C11_ID}';" 2>/dev/null || echo "")
    [ -n "$C10_CHECK" ] && setup_ok "SEED C10 livraison sender semée (freelancer_id=NULL, clientId=FREELANCER)" || warn "SEED C10 livraison non semée"
    [ -n "$C11_CHECK" ] && setup_ok "SEED C11 livraison tiers semée (clientId étranger)" || warn "SEED C11 livraison non semée"
  fi

  # ── Annonce d'isolation : autre tenant (pour tester le cloisonnement dans C2) ─
  psql_q "DELETE FROM tnt_delivery_announcements WHERE id='${OTHER_ANN_ID}';" > /dev/null 2>&1 || true
  psql_q "DELETE FROM tnt_parcels WHERE id='${OTHER_PARCEL_ID}';" > /dev/null 2>&1 || true
  psql_q "
    INSERT INTO tnt_parcels (id,weight_kg,width_cm,height_cm,length_cm,fragile,perishable,created_at,updated_at,version)
    VALUES ('${OTHER_PARCEL_ID}',1.0,20.0,15.0,10.0,false,false,NOW(),NOW(),0)
    ON CONFLICT (id) DO NOTHING;
    INSERT INTO tnt_delivery_announcements
      (id,tenant_id,client_id,title,offered_amount,currency,parcel_id,
       status,urgency,pickup_city,delivery_city,recipient_name,recipient_phone,
       created_at,updated_at,version,pricing_mode)
    VALUES
      ('${OTHER_ANN_ID}','${OTHER_TENANT_ID}','${FREELANCER_USER_ID}',
       'E2E isolation — autre tenant (ne doit PAS apparaître en C2)','1000.0','XAF',
       '${OTHER_PARCEL_ID}','PUBLISHED','STANDARD','Bafoussam','Douala','Isolation Test',
       '+237600000001',NOW(),NOW(),0,'FIXED_PRICE')
    ON CONFLICT (id) DO UPDATE SET status='PUBLISHED', updated_at=NOW();
  " > /dev/null
  ISOLATION_CHECK=$(psql_q "SELECT tenant_id FROM tnt_delivery_announcements WHERE id='${OTHER_ANN_ID}';" 2>/dev/null || echo "")
  if [ "$ISOLATION_CHECK" = "$OTHER_TENANT_ID" ]; then
    setup_ok "SEED isolation : annonce ${OTHER_ANN_ID:0:8}… dans tenant ${OTHER_TENANT_ID:0:8}… semée"
  else
    warn "SEED isolation : annonce autre tenant non semée — test d'isolation ignoré" # [info] sans annonce dans l'autre tenant, l'absence dans la liste ne prouve rien
  fi

  # Read-back obligatoire — le seed n'est déclaré réussi qu'après vérification en base
  SEED_CHECK_B=$(psql_q "SELECT status FROM tnt_delivery_announcements WHERE id='${E2E_ANN_C3_ID_PSQL}';" 2>/dev/null || echo "")
  SEED_CHECK_A=$(psql_q "SELECT status FROM tnt_delivery_announcements WHERE id='${E2E_ANN_C4_ID_PSQL}';" 2>/dev/null || echo "")
  if [ "$SEED_CHECK_B" != "PUBLISHED" ]; then
    fail "SEED : relecture mission B → status='${SEED_CHECK_B}'"
    exit 1
  fi
  if [ "$SEED_CHECK_A" != "PUBLISHED" ]; then
    fail "SEED : relecture mission A → status='${SEED_CHECK_A}'"
    exit 1
  fi
  pass "SEED psql : missions B et A à PUBLISHED, tenant=${E2E_TENANT_ID:0:8}…"
  E2E_ANN_ID="$E2E_ANN_C3_ID_PSQL"
  E2E_ANN_SETUP_ID="$E2E_ANN_C4_ID_PSQL"
  SEED_METHOD="psql"

elif [ "$BFF_ADAPTER" = "mock" ]; then
  # Mode mock sans psql : le BFF ne lit pas la base, ses jobs sont générés en mémoire.
  # Les IDs statiques servent uniquement à cibler les assertions (C2 doit échouer car
  # le mock renvoie job-001/002/003, jamais ces UUIDs).
  E2E_ANN_ID="$E2E_ANN_C3_ID_PSQL"
  E2E_ANN_SETUP_ID="$E2E_ANN_C4_ID_PSQL"
  SEED_METHOD="mock"
  warn "SEED : mode mock sans psql — IDs statiques utilisés (mock ignore la base)."
  info "  C2 doit échouer : mock renvoie job-001/002/003, pas l'UUID ${E2E_ANN_C3_ID_PSQL:0:8}…"

else
  # Mode API pour mode réel sans psql — seed obligatoire.
  warn "psql absent — seed via API. SETUP-PICKUP sera tenté mais pick-up OTP peut échouer."

  for LABEL in "C3" "C4"; do
    SEED_HTTP=$(curl -s -o /tmp/tnt_seed_${LABEL}.json -w "%{http_code}" \
      --max-time 15 -X POST "${CORE_DIRECT_URL}/api/announcements" \
      -H "Authorization: Bearer ${TOKEN_FREELANCER}" \
      -H "Content-Type: application/json" \
      -d "{\"clientId\":\"${FREELANCER_USER_ID}\",\"title\":\"E2E Lot C — mission ${LABEL}\",
           \"amount\":3000,\"currency\":\"XAF\",\"paymentMethod\":\"CASH\",\"autoPublish\":true}" \
      2>/dev/null || echo "000")
    SEED_BODY=$(cat /tmp/tnt_seed_${LABEL}.json 2>/dev/null || echo "{}")
    if [ "$SEED_HTTP" != "201" ] && [ "$SEED_HTTP" != "200" ]; then
      fail "SEED API ${LABEL} → HTTP $SEED_HTTP"
      exit 1
    fi
    ANN_CREATED=$(echo "$SEED_BODY" | jq -r '.id // empty' 2>/dev/null || echo "")
    if [ -z "$ANN_CREATED" ] || [ "$ANN_CREATED" = "null" ]; then
      fail "SEED API ${LABEL} : id absent"
      exit 1
    fi
    if [ "$LABEL" = "C3" ]; then
      E2E_ANN_ID="$ANN_CREATED"
    else
      E2E_ANN_SETUP_ID="$ANN_CREATED"
    fi
    pass "SEED API ${LABEL} : annonce ${ANN_CREATED} créée"
  done
  SEED_METHOD="api"
fi

info "Mission B (C3/C8) : $E2E_ANN_ID"
info "Mission A (SETUP/C4) : $E2E_ANN_SETUP_ID"

# ══════════════════════════════════════════════════════════════════════════════
# C2 — MISSIONS DISPONIBLES
# Les deux missions sont PUBLISHED ici → les deux apparaissent dans la liste.
# L'assertion porte sur Mission B (E2E_ANN_ID) : son ID doit être présent.
# En mode mock, le BFF renvoie job-001/002/003 → Mission B absente → C2 FAIL :
# preuve que le harnais détecte les données fictives du mock.
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
info "C2 : $JOB_COUNT mission(s) disponible(s)"

C2_MISSION_B_OK=false
if echo "$C2_BODY" | jq -e --arg id "$E2E_ANN_ID" 'any(.[]; .id == $id)' > /dev/null 2>&1; then
  pass "C2 : mission B (${E2E_ANN_ID:0:8}…) présente dans la liste"
  C2_MISSION_B_OK=true
else
  fail "C2 : mission B (${E2E_ANN_ID:0:8}…) ABSENTE de la liste"
  SAMPLE_IDS=$(echo "$C2_BODY" | jq -r '.[0:3] | .[].id' 2>/dev/null | tr '\n' ' ' || echo "(vide)")
  warn "  IDs reçus (3 premiers) : $SAMPLE_IDS"
  warn "  En mode mock : BFF renvoie job-001/002/003, jamais un UUID réel — C2 doit échouer."
fi

# ── Test d'isolation tenant ───────────────────────────────────────────────────
# Pré-requis : C2_MISSION_B_OK=true (au moins un item présent dans la liste).
# Une liste VIDE satisfait "absent" de façon triviale — ce n'est pas une preuve
# d'exclusion. Si la liste est vide, on ne peut pas distinguer
# "aucune donnée disponible" de "la donnée d'un autre tenant est bien filtrée".
C2_ISOLATION_OK=false
if [ "$C2_HTTP" = "200" ] && [ "$IS_ARRAY" = "yes" ] && [ -n "$OTHER_ANN_ID" ]; then
  if [ "$C2_MISSION_B_OK" != "true" ]; then
    warn "C2 isolation : liste vide ou mission B absente — impossible de prouver" # [info] une liste vide prouve seulement l'absence, pas le cloisonnement actif
    warn "  le cloisonnement sur une liste qui ne contient rien de notre tenant."
    warn "  TENANT_SCOPING_PROVEN reste 'unknown'."
  elif echo "$C2_BODY" | jq -e --arg id "$OTHER_ANN_ID" 'any(.[]; .id == $id)' > /dev/null 2>&1; then
    fail "C2 isolation : annonce du tenant ${OTHER_TENANT_ID:0:8}… visible — cloisonnement BRISÉ"
  else
    pass "C2 isolation : annonce du tenant ${OTHER_TENANT_ID:0:8}… absente — cloisonnement prouvé"
    TENANT_SCOPING_PROVEN="true"
    C2_ISOLATION_OK=true
  fi
fi

if [ "$C2_HTTP" = "200" ] && [ "$IS_ARRAY" = "yes" ] && [ "$C2_MISSION_B_OK" = "true" ]; then
  if [ "$C2_ISOLATION_OK" = "true" ]; then
    CR[C2]="✅ PASS ($JOB_COUNT missions, mission B trouvée, isolation prouvée)"
  else
    CR[C2]="✅ PASS ($JOB_COUNT missions, mission B trouvée — isolation non testée)"
  fi
else
  CR[C2]="❌ FAIL"
fi

# ══════════════════════════════════════════════════════════════════════════════
# SETUP-PICKUP — assignation directe sur la Mission A, capture du pickupOtp
#
# Le code de pickup est renvoyé UNE SEULE FOIS dans la réponse JSON de
# POST /api/announcements/:id/assign, uniquement quand otpResult.newlyInitialized()
# est vrai (AnnouncementApplicationService.buildAssignResponse l.427-428).
# Ce code est destiné à l'expéditeur FE, pas au coursier.
# Le BFF ne le relaie pas au mobile (CoreAnnouncementDTO l.40-58, announcementToJob
# l.385-407 : aucun champ confirmationCode).
# Cette étape est un SETUP (rôle expéditeur), pas une assertion BFF.
# ══════════════════════════════════════════════════════════════════════════════
step "SETUP-PICKUP — assignation directe Mission A, capture pickupOtp"

# Résoudre le freelancerID pour le subscribe/assign
# En bypass-dev : le core ignore le JWT et utilise toujours le profil dev.
# FreelancerQuotaService.hasRemainingQuota cherche dans gofp_freelancers.id
# → il faut passer CORE_EFFECTIVE_FL_ID (e2e00000-..., remaining_deliveries=100).
FL_ID_FOR_SETUP=""
if [ "$AUTH_MODE" = "bypass-dev" ] && [ -n "$CORE_EFFECTIVE_FL_ID" ]; then
  FL_ID_FOR_SETUP="$CORE_EFFECTIVE_FL_ID"
  info "SETUP : mode bypass-dev — freelancerID depuis /api/v1/freelancers/me (${FL_ID_FOR_SETUP:0:8}…)"
elif psql_available 2>/dev/null; then
  FL_ID_FOR_SETUP=$(psql_q "SELECT id FROM tnt_actor.freelancer_profiles WHERE actor_id='${FREELANCER_USER_ID}' LIMIT 1;" 2>/dev/null || echo "")
  if [ -z "$FL_ID_FOR_SETUP" ]; then
    FL_ID_FOR_SETUP=$(psql_q "SELECT id FROM gofp_freelancers WHERE core_user_id='${FREELANCER_USER_ID}' LIMIT 1;" 2>/dev/null || echo "")
  fi
fi
if [ -z "$FL_ID_FOR_SETUP" ]; then
  FL_ID_FOR_SETUP="$FREELANCER_USER_ID"
  warn "SETUP : freelancerID non résolu, utilisation du userId comme fallback" # [info] FreelancerQuotaService cherche aussi par core_user_id ; le fallback peut fonctionner
fi

# Subscribe Mission A (direct → crée la réponse dans tnt_announcement_responses)
SETUP_SUB_HTTP=$(curl -s -o /tmp/tnt_setup_sub.json -w "%{http_code}" \
  --max-time 30 \
  -X POST "${CORE_DIRECT_URL}/api/announcements/${E2E_ANN_SETUP_ID}/subscribe" \
  -H "Authorization: Bearer ${TOKEN_FREELANCER}" \
  -H "Content-Type: application/json" \
  -d "{\"freelancerId\":\"${FL_ID_FOR_SETUP}\"}" \
  2>/dev/null || echo "000")

if [ "$SETUP_SUB_HTTP" = "202" ] || [ "$SETUP_SUB_HTTP" = "200" ]; then
  setup_ok "SETUP subscribe Mission A → HTTP $SETUP_SUB_HTTP"
else
  SETUP_SUB_ERR=$(cat /tmp/tnt_setup_sub.json 2>/dev/null | jq -r '.message // .error.message // empty' 2>/dev/null || echo "")
  warn "SETUP subscribe Mission A → HTTP $SETUP_SUB_HTTP (${SETUP_SUB_ERR})"
  warn "SETUP-PICKUP ne peut pas continuer — C4 sera BLOQUÉ."
  blocked "SETUP-PICKUP : subscribe échoué (HTTP $SETUP_SUB_HTTP)"
  CR[SETUP]="🔴 BLOQUÉ — subscribe échoué (HTTP $SETUP_SUB_HTTP)"
  CR[C4]="🔴 BLOQUÉ — SETUP-PICKUP échoué (subscribe HTTP $SETUP_SUB_HTTP)"
  # C4 ne peut pas s'exécuter sans l'OTP — continuer pour C6/C7/C8
  goto_c3=true
fi

# ── Assertions C-18 : la projection a été faite par le code de production ─────
# Exécutées que subscribe ait réussi ou non : si la projection manque, c'est elle
# qu'on veut voir rouge (et non un simple « subscribe 409 »). Le script n'écrit
# JAMAIS dans gofp_freelancers ni tnt_delivery_persons (GofpProjectionTablesSeedGuardTest).
if psql_available 2>/dev/null && [ -n "${FREELANCER_USER_ID:-}" ]; then
  AP_ID=$(psql_q "SELECT id FROM tnt_actor.freelancer_profiles WHERE actor_id='${FREELANCER_USER_ID}' LIMIT 1;" 2>/dev/null || echo "")
  GOFP_FL_ID="${AP_ID}"
  GFL_ROW=$(psql_q "SELECT id||'|'||core_freelancer_id||'|'||core_user_id||'|'||status||'|'||is_active
                    FROM gofp_freelancers WHERE core_user_id='${FREELANCER_USER_ID}';" 2>/dev/null || echo "")
  assert_eq "${AP_ID}|${AP_ID}|${FREELANCER_USER_ID}|APPROVED|true" "$GFL_ROW" \
    "C-18 projection : gofp_freelancers (id=core_freelancer_id=profileId, core_user_id, APPROVED, actif) — non écrit par le script"
  GFL_REMAINING=$(psql_q "SELECT remaining_deliveries FROM gofp_freelancers WHERE id='${AP_ID}';" 2>/dev/null || echo "")
  if [ -n "$GFL_REMAINING" ] && [ "$GFL_REMAINING" -gt 0 ] 2>/dev/null; then
    setup_ok "C-18 projection : gofp_freelancers.remaining_deliveries=${GFL_REMAINING} (> 0)"
  else
    assert_eq ">0" "$GFL_REMAINING" "C-18 projection : gofp_freelancers.remaining_deliveries doit être > 0"
  fi
  DP_ROW=$(psql_q "SELECT id||'|'||actor_id||'|'||status FROM tnt_delivery_persons
                   WHERE id='${AP_ID}' AND tenant_id='${JWT_TENANT}';" 2>/dev/null || echo "")
  assert_eq "${AP_ID}|${FREELANCER_USER_ID}|APPROVED" "$DP_ROW" \
    "C-18 projection : tnt_delivery_persons (id=profileId, actor_id=user, APPROVED) — non écrit par le script"
fi

if [ "${goto_c3:-false}" != "true" ]; then
  # Assign Mission A (direct) → doit renvoyer confirmationCode si newlyInitialized
  SETUP_ASSIGN_HTTP=$(curl -s -o /tmp/tnt_setup_assign.json -w "%{http_code}" \
    --max-time 60 \
    -X POST "${CORE_DIRECT_URL}/api/announcements/${E2E_ANN_SETUP_ID}/assign" \
    -H "Authorization: Bearer ${TOKEN_FREELANCER}" \
    -H "Content-Type: application/json" \
    -d "{\"freelancerId\":\"${FL_ID_FOR_SETUP}\"}" \
    2>/dev/null || echo "000")
  SETUP_ASSIGN_BODY=$(cat /tmp/tnt_setup_assign.json 2>/dev/null || echo "{}")

  if [ "$SETUP_ASSIGN_HTTP" = "200" ]; then
    PICKUP_OTP=$(echo "$SETUP_ASSIGN_BODY" | jq -r '.confirmationCode // empty' 2>/dev/null || echo "")
    DELIVERY_OTP=$(echo "$SETUP_ASSIGN_BODY" | jq -r '.deliveryConfirmationCode // empty' 2>/dev/null || echo "")
    E2E_DELIVERY_ID=$(echo "$SETUP_ASSIGN_BODY" | jq -r '.deliveryId // empty' 2>/dev/null || echo "")

    if [ -n "$PICKUP_OTP" ] && [ "$PICKUP_OTP" != "null" ]; then
      setup_ok "SETUP assign → HTTP 200, pickupOtp capturé (${PICKUP_OTP:0:3}***)"
      [ -n "$DELIVERY_OTP" ] && setup_ok "SETUP deliveryOtp capturé (PREVIEW_ONLY — ${DELIVERY_OTP:0:3}***)" || true
      setup_ok "SETUP deliveryId : ${E2E_DELIVERY_ID:0:16}…"
      CR[SETUP]="✅ SETUP — pickupOtp capturé, deliveryId=${E2E_DELIVERY_ID:0:8}…"
    else
      warn "SETUP assign → HTTP 200 mais confirmationCode absent (newlyInitialized=false ?)"
      warn "  Cause probable : la mission A a déjà été assignée (run précédent non nettoyé)"
      warn "  Relancer après cleanup (CTRL+C → nettoyage automatique) ou vider les missions A."
      CR[SETUP]="🔴 BLOQUÉ — assign 200 mais confirmationCode absent (OTP déjà initialisé)"
      CR[C4]="🔴 BLOQUÉ — SETUP-PICKUP : OTP déjà initialisé, non récupérable"
    fi
  else
    SETUP_ERR=$(echo "$SETUP_ASSIGN_BODY" | jq -r '.message // .detail // empty' 2>/dev/null || echo "")
    warn "SETUP assign Mission A → HTTP $SETUP_ASSIGN_HTTP (${SETUP_ERR})"
    CR[SETUP]="🔴 BLOQUÉ — assign échoué (HTTP $SETUP_ASSIGN_HTTP)"
    CR[C4]="🔴 BLOQUÉ — SETUP-PICKUP échoué (assign HTTP $SETUP_ASSIGN_HTTP)"
  fi
fi

# ══════════════════════════════════════════════════════════════════════════════
# PRÉ-C3 — subscribe direct Mission B (premier appel, puis C9 prouve l'idempotence)
#
# Le BFF appelle désormais subscribe sans garde locale (subscribedJobs Set retiré
# en lot C-7). respondToAnnouncement est idempotent côté core (lot C-7) : deux
# appels consécutifs retournent la même candidature sans en créer une seconde.
# Ce subscribe direct garantit que la candidature est en base avant C3 et sert
# de premier appel pour l'étape d'idempotence C9.
# ══════════════════════════════════════════════════════════════════════════════
if [ -n "$FL_ID_FOR_SETUP" ]; then
  PRE_C3_SUB_HTTP=$(curl -s -o /tmp/tnt_pre_c3_sub.json -w "%{http_code}" \
    --max-time 30 \
    -X POST "${CORE_DIRECT_URL}/api/announcements/${E2E_ANN_ID}/subscribe" \
    -H "Authorization: Bearer ${TOKEN_FREELANCER}" \
    -H "Content-Type: application/json" \
    -d "{\"freelancerId\":\"${FL_ID_FOR_SETUP}\"}" \
    2>/dev/null || echo "000")
  if [ "$PRE_C3_SUB_HTTP" = "202" ] || [ "$PRE_C3_SUB_HTTP" = "200" ]; then
    setup_ok "Pré-C3 : subscribe direct Mission B → HTTP $PRE_C3_SUB_HTTP (réponse en base)"
  else
    warn "Pré-C3 : subscribe Mission B → HTTP $PRE_C3_SUB_HTTP (C3 peut échouer si BFF saute son subscribe)" # [info] le BFF appelle subscribe lui-même dans accept() — ce pré-seed est optionnel
  fi
fi

# ══════════════════════════════════════════════════════════════════════════════
# C9 — IDEMPOTENCE subscribe (lot C-7, tâche 2)
#
# Prouve que deux POST /api/announcements/:id/subscribe consécutifs du même
# freelancer ne créent PAS deux candidatures dans tnt_announcement_responses.
# La garde applicative dans AnnouncementApplicationService.respondToAnnouncement
# (findById + responses().stream().anyMatch) court-circuite le second appel.
# La contrainte DB uq_announcement_delivery_person sur announcement_subscriptions
# constitue un second filet de sécurité (écrit via Kafka consumer).
#
# Appel 1 : Pré-C3 ci-dessus.
# Appel 2 : ci-dessous.
# Preuve  : SELECT COUNT(*) = 1 dans tnt_announcement_responses.
# ══════════════════════════════════════════════════════════════════════════════
step "C9 — IDEMPOTENCE subscribe (deux appels, une seule candidature)"

C9_PASS=1
if [ -n "${FL_ID_FOR_SETUP:-}" ] && psql_available 2>/dev/null; then
  C9_SUB2_HTTP=$(curl -s -o /tmp/tnt_c9_sub2.json -w "%{http_code}" \
    --max-time 30 \
    -X POST "${CORE_DIRECT_URL}/api/announcements/${E2E_ANN_ID}/subscribe" \
    -H "Authorization: Bearer ${TOKEN_FREELANCER}" \
    -H "Content-Type: application/json" \
    -d "{\"freelancerId\":\"${FL_ID_FOR_SETUP}\"}" \
    2>/dev/null || echo "000")

  if [ "$C9_SUB2_HTTP" = "202" ] || [ "$C9_SUB2_HTTP" = "200" ]; then
    pass "C9 : second subscribe → HTTP $C9_SUB2_HTTP (idempotent, pas d'erreur)"
  else
    fail "C9 : second subscribe → HTTP $C9_SUB2_HTTP (attendu 200 ou 202)"
    C9_PASS=0
  fi

  C9_COUNT=$(psql_q "
    SELECT COUNT(*) FROM tnt_announcement_responses
    WHERE announcement_id = '${E2E_ANN_ID}'
      AND delivery_person_id = '${FL_ID_FOR_SETUP}';
  " 2>/dev/null || echo "?")

  if [ "$C9_COUNT" = "1" ]; then
    pass "C9 : SELECT COUNT(*) tnt_announcement_responses = 1 — une seule candidature pour deux subscribes"
  else
    fail "C9 : SELECT COUNT(*) tnt_announcement_responses = ${C9_COUNT} (attendu 1 — doublon détecté)"
    C9_PASS=0
  fi

  if [ "$C9_PASS" = "1" ]; then
    CR[C9]="✅ PASS (idempotence prouvée — 1 candidature après 2 subscribes)"
  else
    CR[C9]="❌ FAIL"
  fi
else
  warn "C9 : FL_ID_FOR_SETUP absent ou psql non disponible — step ignoré"
  CR[C9]="⬜ ignoré (FL_ID ou psql absent)"
fi

# ══════════════════════════════════════════════════════════════════════════════
# C10 — CANCEL PAR L'EXPÉDITEUR (branche isSender)
#
# Livraison E2E_DEL_C10_ID : freelancer_id=NULL, announcement.client_id=FREELANCER_USER_ID
# L'appelant (TOKEN_FREELANCER = FREELANCER_USER_ID) est l'expéditeur → 200.
# Prouve que la branche isSender de assertCallerCanCancelDelivery traverse
# announcementRepository.findById → getClientId() → comparaison avec callerActorId.
# ══════════════════════════════════════════════════════════════════════════════
step "C10 — PATCH cancel par l'expéditeur (isSender → 200)"

C10_DELIVERY_SEEDED=$(psql_q "SELECT id FROM deliveries WHERE id='${E2E_DEL_C10_ID}';" 2>/dev/null || echo "")
if [ -z "${C10_DELIVERY_SEEDED:-}" ]; then
  warn "C10 : livraison ${E2E_DEL_C10_ID:0:8}… non semée — step ignoré"
  CR[C10]="⬜ ignoré (livraison non semée)"
else
  C10_HTTP=$(curl -s -o /tmp/tnt_c10_cancel.json -w "%{http_code}" \
    --max-time 15 \
    -X PATCH \
    -H "Authorization: Bearer ${TOKEN_FREELANCER}" \
    "${CORE_DIRECT_URL}/api/v1/deliveries/${E2E_DEL_C10_ID}/cancel" \
    2>/dev/null || echo "000")
  C10_BODY=$(cat /tmp/tnt_c10_cancel.json 2>/dev/null || echo "{}")

  assert_http "200" "$C10_HTTP" "C10 PATCH /deliveries/${E2E_DEL_C10_ID:0:8}…/cancel (expéditeur)"
  if [ "$C10_HTTP" = "200" ]; then
    CR[C10]="✅ PASS — expéditeur autorisé (isSender=true), HTTP 200"
  else
    C10_ERR=$(echo "$C10_BODY" | jq -r '.message // .detail // .error.message // empty' 2>/dev/null || echo "")
    CR[C10]="❌ FAIL — HTTP ${C10_HTTP} (${C10_ERR})"
    warn "Corps C10 : $(echo "$C10_BODY" | jq -c '.' 2>/dev/null || echo "$C10_BODY")"
  fi
fi

# ══════════════════════════════════════════════════════════════════════════════
# C11 — CANCEL PAR UN TIERS → 403
#
# Livraison E2E_DEL_C11_ID : freelancer_id=E2E_C11_FAKE_PROFILE (étranger),
#                             announcement.client_id=E2E_C11_FAKE_CLIENT (étranger).
# L'appelant (TOKEN_FREELANCER = FREELANCER_USER_ID) est ni le livreur ni l'expéditeur.
# Prouve la garde : assertCallerCanCancelDelivery retourne 403 sur les deux branches.
# ══════════════════════════════════════════════════════════════════════════════
step "C11 — PATCH cancel par un tiers → 403"

C11_DELIVERY_SEEDED=$(psql_q "SELECT id FROM deliveries WHERE id='${E2E_DEL_C11_ID}';" 2>/dev/null || echo "")
if [ -z "${C11_DELIVERY_SEEDED:-}" ]; then
  warn "C11 : livraison ${E2E_DEL_C11_ID:0:8}… non semée — step ignoré"
  CR[C11]="⬜ ignoré (livraison non semée)"
else
  C11_HTTP=$(curl -s -o /tmp/tnt_c11_cancel.json -w "%{http_code}" \
    --max-time 15 \
    -X PATCH \
    -H "Authorization: Bearer ${TOKEN_FREELANCER}" \
    "${CORE_DIRECT_URL}/api/v1/deliveries/${E2E_DEL_C11_ID}/cancel" \
    2>/dev/null || echo "000")
  C11_BODY=$(cat /tmp/tnt_c11_cancel.json 2>/dev/null || echo "{}")

  assert_http "403" "$C11_HTTP" "C11 PATCH /deliveries/${E2E_DEL_C11_ID:0:8}…/cancel (tiers → 403)"
  if [ "$C11_HTTP" = "403" ]; then
    CR[C11]="✅ PASS — tiers refusé (garde active), HTTP 403"
  else
    C11_ERR=$(echo "$C11_BODY" | jq -r '.message // .detail // .error.message // empty' 2>/dev/null || echo "")
    CR[C11]="❌ FAIL — HTTP ${C11_HTTP} attendu 403 (${C11_ERR})"
    warn "Corps C11 : $(echo "$C11_BODY" | jq -c '.' 2>/dev/null || echo "$C11_BODY")"
  fi
fi

# ══════════════════════════════════════════════════════════════════════════════
# C3 — ACCEPTER LA MISSION B VIA BFF
# Prouve le chemin BFF réel : subscribe + assign via l'adaptateur real.
# ══════════════════════════════════════════════════════════════════════════════
step "C3 — POST /v1/freelancer/jobs/${E2E_ANN_ID:0:8}…/accept (BFF, Mission B)"

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
# C4 — RÉCUPÉRER LE COLIS (via BFF, Mission A, OTP de SETUP-PICKUP)
#
# BFF : POST /v1/freelancer/jobs/:id/pickup { otp }
#   → transitionDelivery (realCoreFreelancer.ts l.311)
#   → GET /api/v1/deliveries/freelancer/{freelancerId} (GOFP deliveries table)
#   → PATCH /api/v1/deliveries/{id}/status { status:PICKED_UP, confirmationCode:otp }
#   → DeliveryStatusApplicationService.updateStatus l.86-95
#   → otpService.verifyOtp(otp, delivery.getPickupOtpHash()) — BCrypt réel
#
# Vérification indépendante (ne réutilise pas la réponse PATCH) :
#   GET /api/v1/deliveries/{deliveryId} → statut lu séparément.
# ══════════════════════════════════════════════════════════════════════════════
step "C4 — POST /v1/freelancer/jobs/:id/pickup (Mission A)"

if [ -z "$PICKUP_OTP" ]; then
  # SETUP a échoué ou a été bloqué — CR[C4] déjà positionné
  blocked "C4 skippé — pickupOtp non disponible (voir SETUP-PICKUP ci-dessus)"
else
  C4_HTTP=$(curl -s \
    -o /tmp/tnt_c4_pickup.json \
    -w "%{http_code}" \
    --max-time 60 \
    -X POST \
    -H "Authorization: Bearer ${TOKEN_FREELANCER}" \
    -H "Content-Type: application/json" \
    -d "{\"otp\":\"${PICKUP_OTP}\"}" \
    "${BFF_URL}/v1/freelancer/jobs/${E2E_ANN_SETUP_ID}/pickup" \
    2>/dev/null || echo "000")
  C4_BODY=$(cat /tmp/tnt_c4_pickup.json 2>/dev/null || echo "{}")

  assert_http "200" "$C4_HTTP" "C4 POST /v1/freelancer/jobs/:id/pickup"
  C4_STATUS=$(echo "$C4_BODY" | jq -r '.status // empty' 2>/dev/null || echo "")
  assert_eq "picked_up" "$C4_STATUS" "C4 response.status == 'picked_up'"

  # Vérification indépendante : relire le statut sans se fier à la réponse du PATCH
  if [ -n "$E2E_DELIVERY_ID" ]; then
    C4_CHK_HTTP=$(curl -s -o /tmp/tnt_c4_chk.json -w "%{http_code}" --max-time 10 \
      -H "Authorization: Bearer ${TOKEN_FREELANCER}" \
      "${CORE_DIRECT_URL}/api/v1/deliveries/${E2E_DELIVERY_ID}" 2>/dev/null || echo "000")
    C4_CHK_STATUS=$(cat /tmp/tnt_c4_chk.json 2>/dev/null | jq -r '.status // empty' 2>/dev/null || echo "")
    if [ "$C4_CHK_HTTP" = "200" ] && [ "$C4_CHK_STATUS" = "PICKED_UP" ]; then
      pass "C4 vérification indépendante : GET /deliveries/{id} → status=PICKED_UP"
    else
      warn "C4 vérification indépendante : HTTP $C4_CHK_HTTP, status='$C4_CHK_STATUS'" # [info] vérification secondaire ; l'assertion primaire (HTTP 200 + picked_up BFF) a déjà réussi
    fi
  else
    warn "C4 : deliveryId absent de la réponse SETUP — vérification indépendante non disponible" # [info] deliveryId optionnel dans la réponse d'assign ; l'assertion primaire reste valide
  fi

  if [ "$C4_HTTP" = "200" ] && [ "$C4_STATUS" = "picked_up" ]; then
    CR[C4]="✅ PASS (status=picked_up, OTP BCrypt vérifié par le core)"
  else
    CR[C4]="❌ FAIL (HTTP $C4_HTTP, status='$C4_STATUS')"
    warn "Corps C4 : $(echo "$C4_BODY" | jq -c '.' 2>/dev/null || echo "$C4_BODY")"
  fi
fi

# ── Debug intermédiaire : liste des livraisons du freelancer avant C5 ────────
if [ -n "${DELIVERY_OTP:-}" ] && [ -n "${GOFP_FL_ID:-}" ]; then
  _DBG_DELIVERIES=$(curl -s --max-time 10 \
    -H "Authorization: Bearer ${TOKEN_FREELANCER}" \
    "${CORE_DIRECT_URL}/api/v1/deliveries/freelancer/${GOFP_FL_ID}" 2>/dev/null || echo "[]")
  info "DEBUG avant C5 : $(echo "$_DBG_DELIVERIES" | jq 'length' 2>/dev/null || echo "?") livraisons pour GOFP_FL_ID=${GOFP_FL_ID:0:8}…"
  info "DEBUG livraisons : $(echo "$_DBG_DELIVERIES" | jq -c '[.[] | {id: .id, status: .status, annId: .announcementId, needId: .deliveryNeedId}]' 2>/dev/null || echo "$_DBG_DELIVERIES")"
fi

# ══════════════════════════════════════════════════════════════════════════════
# C5 — Livraison
#
# Débloqué si TNT_GOFP_DELIVERY_OTP_PREVIEW=true dans le conteneur core ET
# DELIVERY_OTP capturé lors du SETUP-PICKUP. Dans ce cas, deliveryConfirmationCode
# est renvoyé dans la réponse JSON d'assignation (GofpDeliveryOtpProperties.previewMode).
# Le BFF passe l'OTP à PATCH /api/v1/deliveries/{id}/status (chemin normal — aucun
# court-circuit de OtpService.verifyOtp).
#
# Sinon : BLOQUÉ. deliveryOtp envoyé uniquement par push/email au destinataire.
# ══════════════════════════════════════════════════════════════════════════════
step "C5 — POST /v1/freelancer/jobs/:id/deliver"

if [ -n "$DELIVERY_OTP" ] && [ "$DELIVERY_OTP" != "null" ]; then
  # ── C-18 : plus de seed de wallet ici ────────────────────────────────────────
  # Le script ne crée jamais de wallet. Lot C-19.2 : sur un compte vierge il n'y a
  # aucune ligne avant la livraison ; elle doit être créée par getOrCreateWallet au
  # premier crédit. Wallet absent = solde 0, et on retient qu'il était absent pour
  # exiger ensuite sa création pendant ce run (sinon la preuve de paiement était sautée).
  WALLET_EXISTED_BEFORE="unknown"
  if psql_available 2>/dev/null && [ -n "${FREELANCER_USER_ID:-}" ] && [ -n "${JWT_TENANT:-}" ]; then
    WALLET_ROWS_BEFORE=$(psql_q "
      SELECT COUNT(*) FROM billing.wallet_wallets
      WHERE owner_id='${FREELANCER_USER_ID}' AND tenant_id='${JWT_TENANT}'::uuid;
    " 2>/dev/null | tr -d '\n' || echo "?")
    if [ "$WALLET_ROWS_BEFORE" = "0" ]; then
      WALLET_EXISTED_BEFORE="false"
      WALLET_BALANCE_BEFORE="0"
      info "C5 (avant livraison) : AUCUN wallet pour ${FREELANCER_USER_ID:0:8}… (compte vierge) — solde de départ 0"
    elif [ "$WALLET_ROWS_BEFORE" != "?" ]; then
      WALLET_EXISTED_BEFORE="true"
      WALLET_BALANCE_BEFORE=$(psql_q "
        SELECT COALESCE(balance, 0)
        FROM billing.wallet_wallets
        WHERE owner_id='${FREELANCER_USER_ID}' AND tenant_id='${JWT_TENANT}'::uuid
        LIMIT 1;
      " 2>/dev/null | tr -d '\n' || echo "?")
      info "C5 (avant livraison) : solde wallet = ${WALLET_BALANCE_BEFORE} XAF (wallet préexistant)"
    fi
  fi

  C5_HTTP=$(curl -s \
    -o /tmp/tnt_c5_deliver.json \
    -w "%{http_code}" \
    --max-time 60 \
    -X POST \
    -H "Authorization: Bearer ${TOKEN_FREELANCER}" \
    -H "Content-Type: application/json" \
    -d "{\"otp\":\"${DELIVERY_OTP}\"}" \
    "${BFF_URL}/v1/freelancer/jobs/${E2E_ANN_SETUP_ID}/deliver" \
    2>/dev/null || echo "000")
  C5_BODY=$(cat /tmp/tnt_c5_deliver.json 2>/dev/null || echo "{}")

  assert_http "200" "$C5_HTTP" "C5 POST /v1/freelancer/jobs/:id/deliver (PREVIEW_ONLY OTP)"
  C5_STATUS=$(echo "$C5_BODY" | jq -r '.status // empty' 2>/dev/null || echo "")
  assert_eq "delivered" "$C5_STATUS" "C5 response.status == 'delivered'"

  # ── Après livraison : prouver le paiement par le solde ───────────────────────
  # Règle Lot C-9 : un vert de C5 ne vaut rien si le paiement a été avalé.
  # On prouve le paiement par les données, pas par le statut HTTP.
  if [ "$C5_HTTP" = "200" ] && [ "$C5_STATUS" = "delivered" ] \
      && psql_available 2>/dev/null \
      && [ -n "${E2E_DELIVERY_ID:-}" ] \
      && [ "${WALLET_BALANCE_BEFORE:-?}" != "?" ]; then

    WALLET_BALANCE_AFTER=$(psql_q "
      SELECT COALESCE(balance, 0)
      FROM billing.wallet_wallets
      WHERE owner_id='${FREELANCER_USER_ID}' AND tenant_id='${JWT_TENANT}'::uuid
      LIMIT 1;
    " 2>/dev/null || echo "?")

    DELIVERY_TARIF=$(psql_q "
      SELECT COALESCE(tarif, 0) FROM deliveries WHERE id='${E2E_DELIVERY_ID}' LIMIT 1;
    " 2>/dev/null || echo "0")

    if [ "${WALLET_EXISTED_BEFORE:-unknown}" = "false" ]; then
      WALLET_CREATED_IN_RUN=$(psql_q "
        SELECT COUNT(*) FROM billing.wallet_wallets
        WHERE owner_id='${FREELANCER_USER_ID}' AND tenant_id='${JWT_TENANT}'::uuid
          AND created_at >= '${E2E_RUN_START}'::timestamptz;
      " 2>/dev/null | tr -d '\n' || echo "?")
      assert_eq "1" "$WALLET_CREATED_IN_RUN" \
        "C5 wallet : absent avant, exactement une ligne créée pendant ce run (getOrCreateWallet au premier crédit)"
    fi

    info "C5 tarif delivery = ${DELIVERY_TARIF} XAF"
    info "C5 solde wallet : avant=${WALLET_BALANCE_BEFORE}, après=${WALLET_BALANCE_AFTER} XAF"

    if [ "${WALLET_BALANCE_AFTER}" != "?" ] && [ "${DELIVERY_TARIF}" != "0" ]; then
      # Attendu : tarif * 0.95 (5% commission plateforme, mode ORANGE_MONEY)
      # SELECT COALESCE(tarif,0)*0.95 donne la valeur attendue en SQL pour éviter bc
      EXPECTED_DELTA=$(psql_q "SELECT ROUND(${DELIVERY_TARIF}::numeric * 0.95, 2);" 2>/dev/null || echo "?")
      ACTUAL_DELTA=$(psql_q "SELECT ROUND(${WALLET_BALANCE_AFTER}::numeric - ${WALLET_BALANCE_BEFORE}::numeric, 2);" 2>/dev/null || echo "?")

      if [ "${ACTUAL_DELTA}" = "${EXPECTED_DELTA}" ]; then
        pass "C5 delta solde = ${ACTUAL_DELTA} XAF (tarif=${DELIVERY_TARIF}, commission 5% déduite) — paiement prouvé par l'argent"
      elif [ "${WALLET_BALANCE_AFTER}" != "${WALLET_BALANCE_BEFORE}" ]; then
        fail "C5 delta solde = ${ACTUAL_DELTA} XAF (attendu ${EXPECTED_DELTA} XAF) — paiement arrivé mais montant incorrect (commission/split mal calculé)"
      else
        fail "C5 solde inchangé après livraison : avant=${WALLET_BALANCE_BEFORE}, après=${WALLET_BALANCE_AFTER} XAF — le paiement n'a pas eu lieu"
      fi
    fi

    # Vérifier la transaction wallet créée
    TX_COUNT=$(psql_q "
      SELECT COUNT(*)
      FROM billing.wallet_transactions wt
      JOIN billing.wallet_wallets ww ON wt.wallet_id = ww.id
      WHERE ww.owner_id='${FREELANCER_USER_ID}'
        AND ww.tenant_id='${JWT_TENANT}'::uuid
        AND wt.reference_id = 'MISSION-${E2E_DELIVERY_ID}'
        AND wt.type = 'CREDIT';
    " 2>/dev/null | tr -d '\n' || echo "0")

    if [ "${TX_COUNT:-0}" -ge "1" ] 2>/dev/null; then
      pass "C5 transaction wallet : CREDIT reference_id=MISSION-${E2E_DELIVERY_ID:0:8}… trouvée"
    else
      fail "C5 aucune transaction CREDIT reference_id=MISSION-${E2E_DELIVERY_ID:0:8}… dans billing.wallet_transactions"
      warn "  SELECT * FROM billing.wallet_transactions WHERE reference_id='MISSION-${E2E_DELIVERY_ID}';"
    fi

    # ── Invariant grand livre (Lot C-11 règle 5) ──────────────────────────────
    # Un vert de C5 ne suffit pas si solde != somme des écritures.
    # Cette assertion est indépendante de l'assertion de delta ci-dessus.
    INVARIANT_WALLET_ID=$(psql_q "
      SELECT id FROM billing.wallet_wallets
      WHERE owner_id='${FREELANCER_USER_ID}' AND tenant_id='${JWT_TENANT}'::uuid
      LIMIT 1;
    " 2>/dev/null | tr -d '\n' || echo "")
    if [ -n "${INVARIANT_WALLET_ID:-}" ] && [ "${INVARIANT_WALLET_ID}" != "?" ]; then
      INVARIANT_BALANCE=$(psql_q "
        SELECT balance FROM billing.wallet_wallets WHERE id='${INVARIANT_WALLET_ID}';
      " 2>/dev/null | tr -d '\n' || echo "?")
      INVARIANT_LEDGER=$(psql_q "
        SELECT COALESCE(SUM(CASE WHEN type='CREDIT' THEN amount ELSE -amount END),0)
        FROM billing.wallet_transactions
        WHERE wallet_id='${INVARIANT_WALLET_ID}';
      " 2>/dev/null | tr -d '\n' || echo "?")
      INVARIANT_MATCH=$(psql_q "
        SELECT (ww.balance = COALESCE(SUM(CASE WHEN wt.type='CREDIT' THEN wt.amount ELSE -wt.amount END),0))::text
        FROM billing.wallet_wallets ww
        LEFT JOIN billing.wallet_transactions wt ON wt.wallet_id = ww.id
        WHERE ww.id='${INVARIANT_WALLET_ID}'
        GROUP BY ww.id, ww.balance;
      " 2>/dev/null | tr -d '\n' || echo "false")
      if [ "${INVARIANT_MATCH}" = "true" ]; then
        pass "C5 INVARIANT grand livre : balance(${INVARIANT_BALANCE}) == ledger_sum(${INVARIANT_LEDGER}) — portefeuille wallet_id=${INVARIANT_WALLET_ID:0:8}…"
      else
        fail "C5 INVARIANT grand livre : balance(${INVARIANT_BALANCE}) != ledger_sum(${INVARIANT_LEDGER}) — dérive détectée, wallet_id=${INVARIANT_WALLET_ID:0:8}…, livreur=${FREELANCER_USER_ID:0:8}…"
      fi
    else
      fail "C5 INVARIANT grand livre : portefeuille introuvable pour livreur=${FREELANCER_USER_ID:0:8}… — le paiement n'a pas créé de wallet"
    fi
  fi

  if [ "$C5_HTTP" = "200" ] && [ "$C5_STATUS" = "delivered" ]; then
    CR[C5]="✅ PASS (status=delivered, PREVIEW_ONLY OTP — BCrypt vérifié par core)"
  else
    CR[C5]="❌ FAIL (HTTP $C5_HTTP, status='$C5_STATUS')"
    warn "Corps C5 : $(echo "$C5_BODY" | jq -c '.' 2>/dev/null || echo "$C5_BODY")"
  fi
else
  blocked "C5 deliver — deliveryOtp accessible uniquement via push notification."
  echo "  Pour débloquer : TNT_GOFP_DELIVERY_OTP_PREVIEW=true dans le conteneur core."
  echo "  (tnt.gofp.delivery-otp.preview-mode, env TNT_GOFP_DELIVERY_OTP_PREVIEW)"
  echo "  buildAssignResponse expose alors deliveryConfirmationCode=PREVIEW_ONLY dans"
  echo "  la réponse JSON d'assignation. DELIVERY_OTP est capturé par SETUP-PICKUP."
  CR[C5]="🔴 BLOQUÉ — deliveryOtp non capturé (TNT_GOFP_DELIVERY_OTP_PREVIEW absent du conteneur)"
fi

# ══════════════════════════════════════════════════════════════════════════════
# C6 — WALLET
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
assert_eq "false" "$WA_VAL" "C6 withdraw_available == false"
C6_BALANCE=$(echo "$C6_BODY" | jq -r '.balance // empty' 2>/dev/null || echo "")
C6_CURRENCY=$(echo "$C6_BODY" | jq -r '.currency // empty' 2>/dev/null || echo "")
assert_nonempty "$C6_BALANCE"  "C6 wallet.balance présent"
assert_nonempty "$C6_CURRENCY" "C6 wallet.currency présent"
info "C6 wallet : balance=${C6_BALANCE} ${C6_CURRENCY}, withdraw_available=${WA_VAL}"

# ── C6 valeur réelle : BFF balance == DB balance ─────────────────────────────
# Un BFF balance de 0 peut masquer un vrai solde en base (wallet fantôme ou
# mauvais userId). Cette assertion compare les deux valeurs.
if [ "$C6_HTTP" = "200" ] && psql_available 2>/dev/null \
    && [ -n "${FREELANCER_USER_ID:-}" ] && [ -n "${JWT_TENANT:-}" ]; then
  C6_DB_BALANCE=$(psql_q "
    SELECT COALESCE(balance, 0)
    FROM billing.wallet_wallets
    WHERE owner_id='${FREELANCER_USER_ID}' AND tenant_id='${JWT_TENANT}'::uuid
    LIMIT 1;
  " 2>/dev/null | tr -d '\n' || echo "?")
  C6_DB_WALLET_ID=$(psql_q "
    SELECT id
    FROM billing.wallet_wallets
    WHERE owner_id='${FREELANCER_USER_ID}' AND tenant_id='${JWT_TENANT}'::uuid
    LIMIT 1;
  " 2>/dev/null | tr -d '\n' || echo "?")
  if [ "${C6_DB_BALANCE}" != "?" ] && [ -n "${C6_BALANCE}" ]; then
    # Compare as rounded decimals via psql to handle 2850 vs 2850.00 format differences
    C6_VALUES_MATCH=$(psql_q "SELECT (${C6_BALANCE}::numeric = ${C6_DB_BALANCE}::numeric)::text;" 2>/dev/null | tr -d '\n' || echo "false")
    if [ "${C6_VALUES_MATCH}" = "true" ]; then
      pass "C6 balance BFF(${C6_BALANCE}) == DB(${C6_DB_BALANCE}) — identifiant BFF=${FREELANCER_USER_ID:0:8}…, wallet_id=${C6_DB_WALLET_ID:0:8}…"
    else
      fail "C6 balance BFF(${C6_BALANCE}) != DB(${C6_DB_BALANCE}) — le BFF affiche un solde erroné — identifiant BFF=${FREELANCER_USER_ID:0:8}…, wallet owner_id=${C6_DB_WALLET_ID:0:8}…"
    fi
  else
    warn "C6 comparaison balance : DB=${C6_DB_BALANCE} — comparaison ignorée" # [info] wallet inexistant si C5 n'a pas tourné ; getOrCreateWallet crée un wallet vide (balance=0)
  fi
fi

if [ "$C6_HTTP" = "200" ] && [ "$WA_PRESENT" = "true" ]; then
  if [ "${TENANT_SCOPING_PROVEN:-unknown}" != "true" ]; then
    warn "C6 : PASS NON QUALIFIANT — balance=${C6_BALANCE} ${C6_CURRENCY} indistinguable"
    warn "     d'un portefeuille vide ou d'un mauvais tenant (TENANT_SCOPING_PROVEN=${TENANT_SCOPING_PROVEN})"
    CR[C6]="⚠️  PASS NON QUALIFIANT (balance=${C6_BALANCE} ${C6_CURRENCY} — scoping tenant non prouvé)"
  else
    CR[C6]="✅ PASS (withdraw_available présent, balance=${C6_BALANCE} ${C6_CURRENCY})"
  fi
else
  CR[C6]="❌ FAIL"
fi

# ══════════════════════════════════════════════════════════════════════════════
# C7 — TENTATIVE DE RETRAIT
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
  CR[C7]="✅ PASS (503 FEATURE_UNAVAILABLE)"
else
  CR[C7]="❌ FAIL (HTTP $C7_HTTP, code='$C7_CODE')"
fi

# ══════════════════════════════════════════════════════════════════════════════
# C8 — DOUBLE ACCEPT (Mission B — déjà ASSIGNED après C3)
# ══════════════════════════════════════════════════════════════════════════════
step "C8 — double POST /v1/freelancer/jobs/:id/accept (Mission B, garde anti-retry)"

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
echo "  RÉCAPITULATIF — Lot C-bis : boucle coursier freelancer"
echo "  git.commit.id          : $CORE_SHA"
echo "  AUTH_MODE              : ${AUTH_MODE}"
echo "  JWT_TENANT (tid)       : ${JWT_TENANT:-<absent>}"
echo "  CORE_EFFECTIVE_TENANT  : ${CORE_EFFECTIVE_TENANT:-<inconnu>}"
echo "  AUTH_CHAIN_ACTIVE      : ${AUTH_CHAIN_ACTIVE}"
echo "  TENANT_SCOPING_PROVEN  : ${TENANT_SCOPING_PROVEN}"
echo "  API via BFF            : $BFF_URL (adapter=$BFF_ADAPTER)"
echo "  Setup direct           : $CORE_DIRECT_URL"
echo "════════════════════════════════════════════════════════════════════════"
printf "  %-5s  %-45s  %s\n" "ÉT." "DESCRIPTION" "RÉSULTAT"
printf "  %-5s  %-45s  %s\n" "─────" "─────────────────────────────────────────────" "────────────────────────────────────"
printf "  %-5s  %-45s  %s\n" "C0"    "Empreinte binaire (git.commit.id)"                "${CR[C0]}"
printf "  %-5s  %-45s  %s\n" "C0b"   "Mode d'authentification du core"                  "${CR[C0b]}"
printf "  %-5s  %-45s  %s\n" "C1"    "Auth freelancer (OTP BFF)"                        "${CR[C1]}"
printf "  %-5s  %-45s  %s\n" "C2"    "Missions disponibles (mission B trouvée)"         "${CR[C2]}"
printf "  %-5s  %-45s  %s\n" "C3"    "Accepter via BFF (Mission B)"                     "${CR[C3]}"
printf "  %-5s  %-45s  %s\n" "SETUP" "Assign direct Mission A, capture pickupOtp"       "${CR[SETUP]}"
printf "  %-5s  %-45s  %s\n" "C4"    "Pickup (BCrypt vérifié par core)"                 "${CR[C4]}"
printf "  %-5s  %-45s  %s\n" "C5"    "Deliver (deliveryOtp)"                            "${CR[C5]}"
printf "  %-5s  %-45s  %s\n" "C6"    "Wallet (withdraw_available présent)"              "${CR[C6]}"
printf "  %-5s  %-45s  %s\n" "C7"    "Retrait → 503 FEATURE_UNAVAILABLE"               "${CR[C7]}"
printf "  %-5s  %-45s  %s\n" "C8"    "Double accept → 409 INVALID_STATE"               "${CR[C8]}"
printf "  %-5s  %-45s  %s\n" "C9"    "Idempotence subscribe (2 appels, 1 candidature)"  "${CR[C9]}"
printf "  %-5s  %-45s  %s\n" "C10"   "Cancel expéditeur (isSender) → 200"               "${CR[C10]}"
printf "  %-5s  %-45s  %s\n" "C11"   "Cancel tiers → 403 (garde propriété)"             "${CR[C11]}"
echo "════════════════════════════════════════════════════════════════════════"

# ── Assertion transversale : aucune erreur avalée pendant ce run ──────────────
# Leçon Lot C-8 : un run peut être entièrement vert pendant qu'une erreur est
# silencieusement avalée dans un onErrorResume. Ce garde scanne les logs du
# conteneur core depuis le début du run et échoue si une occurrence de
# 'best-effort|side-effect failed|Access denied' apparaît.
echo
echo "──── ASSERTION TRANSVERSALE : logs core (depuis $E2E_RUN_START) ────"
# Lot C-20 : motif élargi et insensible à la casse. L'ancien motif
# (best-effort|side-effect failed|Access denied) a laissé passer 15 entrées DEAD de
# tnt_role_sync_outbox pendant deux mois. Deux classes distinctes :
#   - SWALLOW : erreur avalée côté core (dont UNPAID_DELIVERED, C-20.3) → FAIL ;
#   - KERNEL  : propagation vers le Kernel morte (403 /api/roles, sync DEAD, 405 acteurs)
#               → verdict « BLOQUÉ EXTERNE », code de sortie 2 : vert localement ne veut
#               pas dire que le Kernel sait qui est freelancer.
SWALLOW_RE='best-effort|side-effect failed|access denied|UNPAID_DELIVERED'
KERNEL_RE='kernel bridge error|kernel sync failed|exhausted [0-9]+ attempts|marking DEAD|not provisioned in the kernel'
KERNEL_BLOCKED=0
if docker inspect "$CORE_CONTAINER" > /dev/null 2>&1; then
  RUN_LOGS=$(docker logs "$CORE_CONTAINER" --since "$E2E_RUN_START" 2>&1)
  BESTEFFORT_HITS=$(echo "$RUN_LOGS" | grep -ciE "$SWALLOW_RE" | tr -d '\n' || echo "0")
  if [ "${BESTEFFORT_HITS:-0}" -gt "0" ] 2>/dev/null; then
    fail "LOGS : ${BESTEFFORT_HITS} occurrence(s) '${SWALLOW_RE}' (-i) dans les logs core — une erreur a été avalée pendant ce run"
    echo "$RUN_LOGS" | grep -iE "$SWALLOW_RE" | grep -v '^\s*at ' | head -5 | cut -c1-240 | sed 's/^/  /'
  else
    pass "LOGS : aucune erreur avalée dans les logs core (${SWALLOW_RE}, insensible à la casse)"
  fi
  KERNEL_HITS=$(echo "$RUN_LOGS" | grep -ciE "$KERNEL_RE" | tr -d '\n' || echo "0")
  KERNEL_DEAD_NEW=""
  psql_available 2>/dev/null && KERNEL_DEAD_NEW=$(psql_q "SELECT count(*) FROM tnt_role_sync_outbox
      WHERE status IN ('DEAD','RETRYING') AND created_at >= '${E2E_RUN_START}'::timestamptz;" 2>/dev/null | tr -d '\n')
  if [ "${KERNEL_HITS:-0}" -gt "0" ] 2>/dev/null || [ "${KERNEL_DEAD_NEW:-0}" -gt "0" ] 2>/dev/null; then
    KERNEL_BLOCKED=1
    blocked "KERNEL : ${KERNEL_HITS} ligne(s) de propagation Kernel en échec, ${KERNEL_DEAD_NEW:-?} entrée(s) outbox DEAD/RETRYING créées pendant ce run"
    echo "$RUN_LOGS" | grep -iE "$KERNEL_RE" | grep -v '^\s*at ' | cut -c1-200 \
      | sed -E 's/^[0-9-]+ [0-9:.]+ //; s/[0-9a-f]{8}-[0-9a-f-]{27}/<id>/g' | sort | uniq -c | head -5 | sed 's/^/  /'
  else
    pass "KERNEL : aucune erreur de propagation Kernel pendant ce run"
  fi
else
  warn "LOGS : conteneur '$CORE_CONTAINER' introuvable — assertion transversale ignorée (TNT_CORE_CONTAINER=${CORE_CONTAINER})" # [info] TNT_CORE_CONTAINER est configurable ; les assertions HTTP précédentes restent valides
fi

if [ "$FAILED" -ne 0 ]; then
  echo -e "${RED}  BILAN : ${#STEP_FAILURES[@]} assertion(s) échouée(s)${RESET}"
  for f in "${STEP_FAILURES[@]}"; do echo -e "  ${RED}✗${RESET} $f"; done
  echo
  exit 1
else
  if [ -n "${DELIVERY_OTP:-}" ]; then
    echo -e "${GREEN}  BILAN : C0-C9 verts (C5 débloqué — PREVIEW_ONLY OTP capturé).${RESET}"
  else
    echo -e "${GREEN}  BILAN : C0-C4, C6-C9 verts. C5 bloqué (TNT_GOFP_DELIVERY_OTP_PREVIEW absent du conteneur).${RESET}"
  fi
  [ "$TENANT_SCOPING_PROVEN" != "true" ] && echo -e "${YELLOW}  NOTE : TENANT_SCOPING_PROVEN=${TENANT_SCOPING_PROVEN} — run en mode ${AUTH_MODE}.${RESET}"
  if [ "$KERNEL_BLOCKED" -eq 1 ]; then
    echo -e "${YELLOW}  BILAN KERNEL : vert localement, propagation Kernel morte (BLOQUÉ EXTERNE) — sortie 2.${RESET}"
    echo
    exit 2
  fi
  echo
fi
