#!/usr/bin/env bash
# ════════════════════════════════════════════════════════════════════════════
# lib-auth.sh — utilitaires d'authentification partagés (E2E TNT)
#
# Source ce fichier depuis un script E2E :
#   source "$(dirname "$0")/lib-auth.sh"
#
# Fonctions exportées :
#   decode_jwt_payload <token>   — décode le payload JWT en JSON
#   auth_curl [curl-args...]     — ajoute le Bearer header si TOKEN est défini
#   tnt_otp_flow                 — flux OTP complet via BFF ; positionne TOKEN
#
# Variables attendues par le caller avant source :
#   BFF_URL, E2E_PHONE
#
# Variables positionnées pour le caller après tnt_otp_flow :
#   TOKEN
# ════════════════════════════════════════════════════════════════════════════

# ── Décodage JWT (URL-safe base64, tolérant au padding) ──────────────────────
# sed au lieu de tr '-_' '+/' : tr interprète -_ comme une plage ASCII 45–95
decode_jwt_payload() {
  local token="$1"
  local b64
  b64=$(echo "$token" | cut -d. -f2 | sed 's/-/+/g; s/_/\//g')
  local len=${#b64}
  local padlen=$(( (4 - len % 4) % 4 ))
  [ $padlen -gt 0 ] && b64="${b64}$(printf '=%.0s' $(seq 1 $padlen))"
  echo "$b64" | base64 -d 2>/dev/null || echo "{}"
}

# ── Inject Bearer header sur tous les appels HTTP au core ────────────────────
auth_curl() {
  if [ -n "${TOKEN:-}" ]; then
    curl -H "Authorization: Bearer $TOKEN" "$@"
  else
    curl "$@"
  fi
}

# ── Flux OTP complet via BFF ─────────────────────────────────────────────────
# Dépend de : BFF_URL, E2E_PHONE (lues depuis l'env du caller)
# Positionne : TOKEN (variable globale du caller)
# Retourne : 0 si succès, non-0 si erreur (avec message sur stderr)
tnt_otp_flow() {
  # 1. Demande OTP
  local otp_http otp_body challenge_id preview_code otp_code

  otp_http=$(curl -s \
      -o /tmp/tnt_otp_req.json \
      -w "%{http_code}" \
      --max-time 10 \
      -X POST "${BFF_URL}/v1/auth/otp/request" \
      -H "Content-Type: application/json" \
      -d "{\"phoneNumber\":\"${E2E_PHONE}\"}" 2>/dev/null || echo "000")
  otp_body=$(cat /tmp/tnt_otp_req.json 2>/dev/null || echo "{}")

  if [ "$otp_http" != "200" ]; then
    local err_code err_msg
    err_code=$(echo "$otp_body" | jq -r '.error.code // "inconnu"' 2>/dev/null || echo "inconnu")
    err_msg=$(echo "$otp_body" | jq -r '.error.message // ""' 2>/dev/null || echo "")
    echo "ERREUR : POST ${BFF_URL}/v1/auth/otp/request a échoué (HTTP $otp_http)" >&2
    echo "  errorCode : $err_code" >&2
    [ -n "$err_msg" ] && echo "  message   : $err_msg" >&2
    if [ "$otp_http" = "429" ]; then
      echo "" >&2
      echo "Limite de taux atteinte. Relancez dans quelques secondes ou exportez" >&2
      echo "un jeton existant : export TNT_E2E_TOKEN=<token>" >&2
    fi
    return 1
  fi

  challenge_id=$(echo "$otp_body" | jq -r '.challengeId // empty' 2>/dev/null || echo "")
  preview_code=$(echo "$otp_body" | jq -r '.previewCode // empty' 2>/dev/null || echo "")

  if [ -z "$challenge_id" ]; then
    echo "ERREUR : pas de challengeId dans la réponse OTP request :" >&2
    echo "  $otp_body" >&2
    return 1
  fi

  if [ -n "$preview_code" ]; then
    otp_code="$preview_code"
    echo "  Mode PREVIEW_ONLY — code OTP : $otp_code"
  else
    echo "  Un SMS a été envoyé au $E2E_PHONE"
    # || true : protège read contre set -e si stdin est fermé
    read -r -p "  Code OTP reçu : " otp_code || true
    if [ -z "$otp_code" ]; then
      echo "" >&2
      echo "ERREUR : code OTP vide (stdin non-interactif ou entrée vide)." >&2
      echo "Alternative : exportez un token existant : export TNT_E2E_TOKEN=<token>" >&2
      return 1
    fi
  fi

  # 2. Vérifie l'OTP et obtient le token
  local verify_http verify_body verify_status

  verify_http=$(curl -s \
      -o /tmp/tnt_verify_resp.json \
      -w "%{http_code}" \
      --max-time 15 \
      -X POST "${BFF_URL}/v1/auth/otp/verify" \
      -H "Content-Type: application/json" \
      -d "{\"challengeId\":\"${challenge_id}\",\"code\":\"${otp_code}\"}" \
      2>/dev/null || echo "000")
  verify_body=$(cat /tmp/tnt_verify_resp.json 2>/dev/null || echo "{}")
  verify_status=$(echo "$verify_body" | jq -r '.status // empty' 2>/dev/null || echo "")

  if [ "$verify_status" = "SIGNUP_REQUIRED" ]; then
    echo "" >&2
    echo "ERREUR : SIGNUP_REQUIRED — le compte $E2E_PHONE n'existe pas dans le Kernel." >&2
    echo "Créez le compte via le flux signup avant de relancer." >&2
    return 1
  fi

  if [ "$verify_http" != "200" ]; then
    local verify_err
    verify_err=$(echo "$verify_body" | jq -r '.error.code // empty' 2>/dev/null || echo "")
    echo "" >&2
    echo "ERREUR : OTP verify a échoué (HTTP $verify_http, errorCode : ${verify_err:-inconnu})" >&2
    echo "Corps : $verify_body" >&2
    return 1
  fi

  TOKEN=$(echo "$verify_body" | jq -r '.accessToken // empty' 2>/dev/null || echo "")
  if [ -z "$TOKEN" ]; then
    echo "ERREUR : accessToken absent de la réponse OTP verify." >&2
    echo "Corps : $verify_body" >&2
    return 1
  fi

  echo "  Jeton obtenu via BFF (${TOKEN:0:12}…, longueur ${#TOKEN})"
  return 0
}
