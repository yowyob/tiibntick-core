#!/usr/bin/env bash
# Lot C-21.3 — GET /api/v1/freelancers/me avec un jeton réel, empreinte du binaire en tête.
#   TNT_E2E_TOKEN=<jwt> bash scripts/e2e/c21-probe-freelancer-me.sh [CORE_URL]
set -uo pipefail
CORE="${1:-http://localhost:8080}"
TOKEN="${TNT_E2E_TOKEN:?TNT_E2E_TOKEN requis}"
echo "# $(date -u +%Y-%m-%dT%H:%M:%SZ) — $CORE"
echo "# binaire en vol : $(curl -s "$CORE/actuator/info" | jq -c '{commit: (.git.commit.id.abbrev // .git.commit.id), dirty: .git.dirty}')"
echo "# sub du jeton  : $(echo "$TOKEN" | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null | jq -r .sub)"
code=$(curl -s -o /tmp/c21-me.json -w '%{http_code}' -H "Authorization: Bearer $TOKEN" "$CORE/api/v1/freelancers/me")
echo "GET /api/v1/freelancers/me → $code"
jq -c '{status, error: (.error | if . then {code, message} else null end), data: (.data | if . then {id, actorId, status} else null end)}' /tmp/c21-me.json 2>/dev/null || cat /tmp/c21-me.json
