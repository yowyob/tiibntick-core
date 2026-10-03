#!/usr/bin/env bash
# Lot C-21.1 §3.2 — triplet de sondes de santé avec code HTTP, empreinte du binaire en tête.
#   bash scripts/e2e/c21-health-triplet.sh [CORE_URL] [conteneur] [base]
CORE="${1:-http://localhost:8080}"; CTR="${2:-}"; DB="${3:-}"
echo "# $(date -u +%FT%TZ) — $CORE"
echo "# binaire : $(curl -s "$CORE/actuator/info" | jq -c '{commit: (.git.commit.id.abbrev // .git.commit.id), dirty: .git.dirty}')"
if [ -n "$CTR" ]; then
  echo "# conteneur $CTR : profil $(docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "$CTR" | grep '^SPRING_PROFILES_ACTIVE=' | cut -d= -f2), démarré $(docker inspect -f '{{.State.StartedAt}}' "$CTR" | cut -c1-19)Z, redémarrages $(docker inspect -f '{{.RestartCount}}' "$CTR")"
fi
if [ -n "$DB" ]; then
  echo "# tnt_role_sync_outbox ($DB) : $(docker exec tnt-postgres psql -U tiibntick -d "$DB" -Atc "select coalesce(string_agg(status||'='||n, ' ' order by status),'(vide)') from (select status, count(*) n from tnt_role_sync_outbox group by 1) s")"
fi
for p in /actuator/health /actuator/health/liveness /actuator/health/readiness; do
  body=$(curl -s -m 15 -o /tmp/c21-h.json -w '%{http_code}' "$CORE$p")
  printf '%-28s → HTTP %s  %s\n' "$p" "$body" "$(jq -c '{status} + (if .components then {components: (.components|keys)} else {} end)' /tmp/c21-h.json 2>/dev/null || cat /tmp/c21-h.json)"
done
