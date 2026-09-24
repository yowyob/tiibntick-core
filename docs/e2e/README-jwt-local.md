# Run JWT E2E en local — procédure pas-à-pas

Ce document décrit comment obtenir un vrai JWT Kernel pour le script
`scripts/e2e/e2e-presence-loop.sh` sans accès à un core de production déployé.

**Architecture du flux** (validée lot 16, 2026-09-20) :

```
BFF (port 3001)
  → core local (port 8080) — platform gateway /api/v1/auth/*
    → Kernel de production (kernel-core.yowyob.com/kernel-api)
      → SMS OTP → JWT signé par la clé de prod
  → script E2E envoie le JWT au core local
    → Spring Security valide contre le JWKS de prod  (JWT_JWK_SET_URI par défaut)
```

Le core local proxifie vers le Kernel de production via `kernelWebClient`
(`tnt.kernel.base-url`, configuré dans `KernelBridgeConfig`). Les tokens obtenus
sont donc des tokens Kernel de production — ils passent la validation Spring Security.

---

## Prérequis

- Core local en fonctionnement (`mvn package` + rebuild Docker récent).
- `tiibntick-bff/.env` avec `CORE_BASE_URL=http://localhost:8080` et les creds
  du client E2E (voir §2 ci-dessous).
- Téléphone `+237695479355` disponible pour recevoir l'OTP (ou `TNT_E2E_TOKEN`
  si un jeton valide a été obtenu récemment — voir §4).

---

## 1. Créer un platform client E2E (une seule fois par environnement)

Le client doit exister dans la table `tnt_platform_clients` du core local.
**La clé API n'est affichée qu'une seule fois** : notez-la immédiatement.

### 1.1 Démarrer le core en bypass le temps de la création

```bash
cd tnt-bootstrap
TNT_AUTH_ALLOW_ANONYMOUS=true docker compose --profile app up -d tiibntick-core
# attendre ~70 s
until curl -sf http://localhost:8080/actuator/health/liveness > /dev/null; do sleep 5; done
```

### 1.2 Créer le client et émettre une clé

```bash
# Créer le client
CLIENT=$(curl -sf http://localhost:8080/api/v1/admin/platform-clients \
  -H 'Content-Type: application/json' \
  -d '{"name":"E2E JWT Test Client","platformCode":"E2E","environment":"DEV",
       "description":"Run JWT E2E local","contactEmail":"kouamulrich19@gmail.com"}')

CLIENT_UUID=$(echo "$CLIENT" | jq -r '.data.id')
CLIENT_ID=$(echo "$CLIENT" | jq -r '.data.clientId')
echo "clientId : $CLIENT_ID   uuid : $CLIENT_UUID"

# Accorder le scope AUTH:* (obligatoire pour /api/v1/auth/**)
curl -sf -X PUT "http://localhost:8080/api/v1/admin/platform-clients/${CLIENT_UUID}/permissions" \
  -H 'Content-Type: application/json' \
  -d '{"scopes":["AUTH:*"]}' > /dev/null

# Émettre la clé — ⚠️ SHO-ONCE : copiez plaintextSecret immédiatement
curl -s -X POST "http://localhost:8080/api/v1/admin/platform-clients/${CLIENT_UUID}/api-keys" \
  -H 'Content-Type: application/json' -d '{}' | jq '{clientId: "'"$CLIENT_ID"'", plaintextSecret: .data.plaintextSecret}'
```

### 1.3 Configurer `tiibntick-bff/.env`

```
CORE_BASE_URL=http://localhost:8080
CORE_GO_BASE_URL=http://localhost:8080
CORE_CLIENT_ID=<clientId de l'étape 1.2>
CORE_API_KEY=<plaintextSecret de l'étape 1.2>
CORE_AUTH_TARGET=gateway
CORE_TENANT_ID=43427172-b6ee-4dbf-9148-96682702ffc9
```

**Ne commitez jamais `CORE_API_KEY`** — `.env` est gitignore.

---

## 2. Client existant (run 2026-09-20)

Le client `e2e-dev-74a5284f` (uuid `054eefc0-7d01-4263-874e-f4b44ad0d26b`) a été
créé le 2026-09-20. Sa clé est configurée dans `tiibntick-bff/.env`.

Si la clé est perdue ou le core a été recréé (schéma migré), répétez §1.2
à partir de l'uuid existant : émettez une nouvelle clé (l'ancienne est révoquée
automatiquement si vous le souhaitez via `DELETE /api-keys/{keyId}`).

---

## 3. Lancer le run JWT

```bash
# Terminal 1 : core sans bypass
cd tnt-bootstrap
docker compose --profile app up -d tiibntick-core
until curl -sf http://localhost:8080/actuator/health/liveness > /dev/null; do sleep 5; done
docker exec tnt-core env | grep ALLOW_ANONYMOUS   # doit afficher false

# Terminal 2 : BFF
cd /home/jtk/projets/tiibntick-bff
node --env-file=.env ./node_modules/.bin/tsx src/index.ts
# → "tiibntick-bff sur http://0.0.0.0:3001 (core: real)"

# Terminal 3 : script E2E
cd /home/jtk/projets/tiibntick-core
bash scripts/e2e/e2e-presence-loop.sh
# → entrer le code OTP reçu par SMS sur +237695479355
```

---

## 4. Éviter l'OTP lors d'un second run (jeton toujours valide)

Le jeton Kernel est valide ~15 min. Pour rejouer sans SMS :

```bash
# Après un premier run réussi, noter le token masqué et exporter la valeur complète
export TNT_E2E_TOKEN=<accessToken_complet>
bash scripts/e2e/e2e-presence-loop.sh   # saute le flux OTP
```

Le script affiche `Jeton fourni via TNT_E2E_TOKEN (xxxxxxxxxxxx…, longueur N)` et
utilise le jeton tel quel. Une fois expiré, relancer sans la variable.

---

## 5. Rate limiter BFF

Le BFF a un rate limiter in-memory (reset au redémarrage) :

| Limiteur | Fenêtre | Seuil |
|---|---|---|
| Per-phone (OTP request) | 30 s | 1 demande |
| Per-IP (OTP request) | 15 min | 20 demandes |

Ne jamais boucler sur `/v1/auth/otp/request` pendant la mise au point.
Si saturé : redémarrer le BFF (reset), ou attendre l'expiration de la fenêtre.
