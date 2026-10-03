#!/usr/bin/env bash
# Lot C-21.2 — capture brute (octets) du flux SSE /api/notifications/stream/{id} pour un
# MatchingNotificationEvent publié sur Kafka, afin de comparer le contrat avant/après.
#   TNT_E2E_TOKEN=<jwt> bash scripts/e2e/c21-sse-golden.sh <fichier-sortie-brute>
set -uo pipefail
CORE="${CORE_URL:-http://localhost:8080}"
TOKEN="${TNT_E2E_TOKEN:?TNT_E2E_TOKEN requis}"
OUT="${1:?fichier de sortie requis}"
FID="c21c21c2-0000-0000-0000-00000000f001"
AID="c21c21c2-0000-0000-0000-00000000a001"
# null volontaire sur message : la sérialisation des null fait partie du contrat.
EVENT="{\"freelancerId\":\"$FID\",\"announcementId\":\"$AID\",\"title\":\"Annonce C-21 été\",\"message\":null}"
curl -sN --max-time 12 -H "Authorization: Bearer $TOKEN" -H 'Accept: text/event-stream' \
     -D "$OUT.headers" -o "$OUT" "$CORE/api/notifications/stream/$FID" &
CURL_PID=$!
sleep 4
echo "$EVENT" | docker exec -i tnt-kafka /opt/kafka/bin/kafka-console-producer.sh \
    --bootstrap-server localhost:9092 --topic gofp.matching.notifications >/dev/null 2>&1 \
  || echo "$EVENT" | docker exec -i tnt-kafka kafka-console-producer --bootstrap-server localhost:9092 --topic gofp.matching.notifications
wait $CURL_PID
echo "# binaire : $(curl -s "$CORE/actuator/info" | jq -c '{commit: (.git.commit.id.abbrev // .git.commit.id), dirty: .git.dirty}')"
echo "# en-têtes :"; grep -i '^HTTP\|content-type' "$OUT.headers"
echo "# corps brut ($(wc -c < "$OUT") octets), sha256 $(sha256sum "$OUT" | cut -c1-16) :"
od -c "$OUT" | head -20
