#!/usr/bin/env bash
# ══════════════════════════════════════════════════════════════════════════════
# measure-cold-latency.sh — Lot C-19.1 : coût d'une requête authentifiée hors
# parcours freelancer, sur un conteneur fraîchement redémarré puis à chaud.
#
# Le jeton doit être obtenu AVANT le redémarrage (sinon le flux OTP, qui
# traverse le core, le réchauffe et fausse la mesure « à froid »).
#
# Usage :
#   TNT_E2E_TOKEN=<jwt> bash scripts/e2e/measure-cold-latency.sh [label]
# Variables :
#   CORE_URL        (défaut http://localhost:8080)
#   CORE_CONTAINER  (défaut tnt-core)
#   ROUTE           (défaut /api/delivery-needs/user/<sub du jeton>)
#   WARM_N          (défaut 30)   requêtes séquentielles à chaud
#   CONC_N          (défaut 5)    requêtes concurrentes dans la 2e mesure à froid
# Sortie : texte brut sur stdout (à rediriger vers docs/e2e/…).
# ══════════════════════════════════════════════════════════════════════════════
set -uo pipefail
export LC_ALL=C   # durées curl et awk avec un point décimal

CORE_URL="${CORE_URL:-http://localhost:8080}"
CORE_CONTAINER="${CORE_CONTAINER:-tnt-core}"
WARM_N="${WARM_N:-30}"
CONC_N="${CONC_N:-5}"
LABEL="${1:-sans-label}"
TOKEN="${TNT_E2E_TOKEN:?TNT_E2E_TOKEN requis}"

source "$(dirname "$0")/lib-auth.sh"
SUB=$(decode_jwt_payload "$TOKEN" | jq -r '.sub')
EXP=$(decode_jwt_payload "$TOKEN" | jq -r '.exp')
ROUTE="${ROUTE:-/api/delivery-needs/user/${SUB}}"

req() {  # → "<http_code> <time_total_s>"
  curl -s -o /dev/null -w "%{http_code} %{time_total}" --max-time 120 \
    -H "Authorization: Bearer ${TOKEN}" "${CORE_URL}${ROUTE}" 2>/dev/null || echo "000 120"
}

restart_core() {
  docker restart "$CORE_CONTAINER" > /dev/null
  local s; s=$(date +%s.%N)
  until curl -sf -m2 "${CORE_URL}/actuator/health/liveness" > /dev/null; do sleep 0.5; done
  echo "  conteneur prêt (liveness UP) après $(echo "$(date +%s.%N) - $s" | bc | xargs printf '%.1f') s"
}

stats() {  # lit des durées (s) sur stdin → min/médiane/p95/max en ms
  sort -n | awk '{a[NR]=$1*1000} END {
    p95=int(NR*0.95); if (p95<1) p95=1;
    printf "  n=%d  min=%.1f ms  médiane=%.1f ms  p95=%.1f ms  max=%.1f ms\n",
      NR, a[1], a[int((NR+1)/2)], a[p95], a[NR] }'
}

echo "═══ measure-cold-latency — ${LABEL} ═══"
echo "date (UTC)   : $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "core         : $(curl -s -m5 "${CORE_URL}/actuator/info" | jq -c '{commit: .git.commit.id.abbrev, dirty: .git.dirty}')"
echo "route        : GET ${ROUTE}"
echo "sub          : ${SUB}"
echo "jeton expire : $(date -u -d "@${EXP}" +%H:%M:%SZ)"
echo

echo "── Mesure 1 : première requête authentifiée après redémarrage, puis ${WARM_N} à chaud ──"
restart_core
echo "  froid #1   : $(req)"
echo "  froid #2   : $(req)"
for _ in $(seq 1 "$WARM_N"); do req; echo; done > /tmp/tnt_warm.txt
echo "  codes HTTP à chaud : $(awk '{print $1}' /tmp/tnt_warm.txt | sort | uniq -c | xargs)"
awk '{print $2}' /tmp/tnt_warm.txt | stats
echo

echo "── Mesure 2 : ${CONC_N} requêtes concurrentes, première rafale après redémarrage ──"
restart_core
for i in $(seq 1 "$CONC_N"); do (echo "  froid concurrent #$i : $(req)") & done
wait
echo

echo "── Journal core pendant la mesure 2 (provisioning / projection / erreurs) ──"
docker logs "$CORE_CONTAINER" --since 2m 2>&1 \
  | grep -iE "projection|provision|best-effort|Access denied|timeout|Projecting" | tail -20 | cut -c1-240
echo "═══ fin ═══"
