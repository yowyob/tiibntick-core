# Guide — Gestion des clients plateforme (Client-ID / API-Key)

Guide opérationnel pour les administrateurs `TNT_ADMIN` : créer, scoper, faire tourner et révoquer les identités des backends plateforme (Agency, Go, Link, Market, Point Relais, ...) qui consomment TiiBnTick Core.

Conception détaillée : [`platform-client-management-design.md`](./platform-client-management-design.md). Ce guide est la référence **opérationnelle** — "comment faire X" — pas la conception.

---

## 1. Concepts en 30 secondes

| Terme | C'est quoi |
|---|---|
| **Client-ID** | Identifiant public d'une plateforme (ex. `agency-prod-9f2a1c3d`), pas secret, généré automatiquement à la création. |
| **API Key** | Secret associé (ex. `tnt_9Iu01WuhBgm3pzlnM0oqd8nDBZscFVa3imab7z9M0YA=`), affiché **une seule fois** à l'émission/rotation, jamais stocké en clair (hash BCrypt côté serveur). |
| **Scope** | Chaîne `resource:action` (ex. `AUTH:*`, `SSO:*`, `*`) décrivant ce qu'un client a le droit d'appeler. Un client peut en avoir plusieurs. |
| **Environment** | `DEV` / `STAGING` / `PROD` — un client = une plateforme **pour un environnement donné**. Agency-PROD et Agency-DEV sont deux clients distincts. |

**Deux couches d'autorisation, à ne pas confondre** :
1. **Cette couche (plateforme)** — répond à "quel backend appelle Core, et a-t-il le droit de frapper à ce bloc/module ?"
2. **RBAC utilisateur** (`@RequirePermission`, JWT) — répond à "quel utilisateur final, à travers ce backend, a le droit de faire cette action précise ?"

Un scope plateforme ne remplace jamais le RBAC utilisateur — il s'applique **avant**, comme un portail.

---

## 2. Prérequis

- Un JWT valide portant le rôle `TNT_ADMIN` (seul rôle autorisé — voir `TntPermission.PLATFORM_CLIENTS_MANAGE`).
- Toutes les routes ci-dessous sont sous `/api/v1/admin/platform-clients`, `/api/v1/admin/api-keys`, `/api/v1/admin/scope-registry` — authentification **JWT classique** (pas de X-Client-Id/X-Api-Key ici, c'est réservé aux plateformes elles-mêmes).

---

## 3. Démarrage rapide — accès complet (cas par défaut)

C'est le flux le plus simple : une plateforme qui doit tout consommer.

```bash
# 1. Créer le client — le scope "*" (accès complet) est accordé AUTOMATIQUEMENT
curl -X POST https://core.tiibntick.io/api/v1/admin/platform-clients \
  -H "Authorization: Bearer $ADMIN_JWT" -H "Content-Type: application/json" \
  -d '{
    "name": "TNT Agency",
    "platformCode": "AGENCY",
    "environment": "PROD",
    "description": "Backend agence — accès complet",
    "contactEmail": "ops@tnt-agency.io"
  }'
```
Réponse (`201`) :
```json
{
  "status": "SUCCESS",
  "data": {
    "id": "b7e2...",
    "clientId": "agency-prod-9f2a1c3d",
    "name": "TNT Agency",
    "platformCode": "AGENCY",
    "environment": "PROD",
    "status": "ACTIVE",
    "createdAt": "2026-07-09T10:00:00Z"
  }
}
```

```bash
# 2. Émettre une clé — le secret n'est renvoyé qu'ICI, une seule fois
curl -X POST https://core.tiibntick.io/api/v1/admin/platform-clients/b7e2.../api-keys \
  -H "Authorization: Bearer $ADMIN_JWT" -H "Content-Type: application/json" -d '{}'
```
Réponse (`201`) :
```json
{
  "status": "SUCCESS",
  "data": {
    "keyId": "c19a...",
    "prefix": "tnt_9Iu01WuhBg",
    "plaintextSecret": "tnt_9Iu01WuhBgm3pzlnM0oqd8nDBZscFVa3imab7z9M0YA=",
    "expiresAt": null
  }
}
```

**3. Transmettre `clientId` + `plaintextSecret` à l'équipe plateforme, hors bande** (coffre-fort partagé, jamais Slack/email en clair). C'est tout — la plateforme peut immédiatement appeler `/api/v1/auth/**`, `/api/v1/sso/**`, `/api/v1/onboarding/**` avec :
```
X-Client-Id: agency-prod-9f2a1c3d
X-Api-Key: tnt_9Iu01WuhBgm3pzlnM0oqd8nDBZscFVa3imab7z9M0YA=
```

**Aucune 3ᵉ étape requise.** Créer un client + émettre une clé = utilisable de bout en bout (décision du 2026-07-09 : sécurité par défaut = accès complet, pas "aucun accès").

---

## 4. Restreindre un client (accès complet inutile ou dangereux)

Si une plateforme ne doit **pas** avoir accès à tout (ex. Point Relais ne devrait pas pouvoir déclencher l'onboarding d'agence), retirez le scope `*` et attribuez uniquement ce qu'il faut :

```bash
curl -X PUT https://core.tiibntick.io/api/v1/admin/platform-clients/b7e2.../permissions \
  -H "Authorization: Bearer $ADMIN_JWT" -H "Content-Type: application/json" \
  -d '{ "scopes": ["AUTH:*", "SSO:*"] }'
```

`PUT` **remplace** l'intégralité du jeu de scopes (pas un ajout incrémental). Pour interroger le catalogue en direct :

```bash
curl https://core.tiibntick.io/api/v1/admin/scope-registry -H "Authorization: Bearer $ADMIN_JWT"
```
```json
{ "status": "SUCCESS", "data": [
  { "code": "AUTH", "description": "Kernel auth-controller/auth-oidc-controller proxy (Bloc A)", "availableActions": ["*"] },
  { "code": "SSO", "description": "YowYob SSO handshake proxy (Bloc B)", "availableActions": ["*"] },
  { "code": "ONBOARDING", "description": "Agency onboarding orchestration proxy (Bloc C)", "availableActions": ["*"] }
]}
```

### Catalogue complet des scopes — les seules valeurs acceptées aujourd'hui

`ScopeResourceDefinition` (`PlatformScopeRegistry`) recense **3 ressources**, chacune avec une seule action possible (`*`) — soit **4 valeurs de scope valides au total**, pas une de plus :

| Scope | Ressource couverte | Endpoints concrets couverts |
|---|---|---|
| `*` | **Toutes** (méta-scope global) | `/api/v1/auth/**`, `/api/v1/sso/**`, `/api/v1/onboarding/**`, et tout futur module métier proxifié, sans exception |
| `AUTH:*` | `AUTH` — Bloc A (proxy `auth-controller`/`auth-oidc-controller` du Kernel) | `POST /api/v1/auth/login`, `/register`, `/sign-up`, `/otp`, `/otp/verify`, `/mfa/*`, `/forgot-password`, `/reset-password`, `/discover-contexts`, `/select-context`, etc. (`PlatformAuthController`) + `/.well-known/**`, `/oauth2/**` (`PlatformAuthOidcController`, en réalité public, voir §8) |
| `SSO:*` | `SSO` — Bloc B (handshake YowYob SSO) | `POST /api/v1/sso/context/resolve`, `/token/exchange`, `/yowyob/launch` (`PlatformSsoController`) |
| `ONBOARDING:*` | `ONBOARDING` — Bloc C (orchestration onboarding agence) | `/api/v1/onboarding/agency/applications/**` (`PlatformAgencyOnboardingController`, module `tnt-administration-core`) |

**Aucun autre scope n'existe** — ni `DELIVERY:*`, ni `BILLING:*`, ni aucun scope par module métier : ce sont des exemples illustratifs du design (§2.6), pas des valeurs réellement acceptées tant qu'un proxy métier curé n'est pas construit (aucun ne l'est à ce jour). Envoyer un scope inconnu à `PUT .../permissions` (ex. `"DELIVERY:read"`) échoue avec `400` / code `PLATFORM_INVALID_SCOPE` — la requête entière est rejetée, aucun scope n'est appliqué partiellement.

Combinaisons possibles avec les 3 ressources actuelles (au lieu du méta-scope `*`) :
- `["AUTH:*"]` — uniquement authentification
- `["SSO:*"]` — uniquement SSO
- `["ONBOARDING:*"]` — uniquement onboarding
- `["AUTH:*", "SSO:*"]` — auth + SSO, pas onboarding
- `["AUTH:*", "ONBOARDING:*"]` — auth + onboarding, pas SSO
- `["SSO:*", "ONBOARDING:*"]` — SSO + onboarding, pas auth
- `["AUTH:*", "SSO:*", "ONBOARDING:*"]` — équivalent fonctionnel à `["*"]` tant qu'aucun module métier n'existe, mais **pas recommandé** : le jour où un proxy métier est ajouté au registre, `*` le couvre automatiquement alors que la liste explicite non.

> Quand un module métier (ex. `DELIVERY`) obtiendra un proxy curé (`/api/v1/platform/delivery/**`), `PlatformScopeRegistry` gagnera une nouvelle entrée avec ses vraies actions (`read`, `write`, ...) — mise à jour de ce guide requise à ce moment-là, pas avant.

---

## 5. Scénarios courants

| Scénario | Action |
|---|---|
| **Nouvelle plateforme, accès total** | Créer + émettre la clé (§3). Rien d'autre. |
| **Plateforme qui ne fait QUE de l'authentification** (ex. un service interne qui valide juste des logins) | `PUT .../permissions` avec `["AUTH:*"]`. |
| **Plateforme qui ne fait QUE du SSO** | `PUT .../permissions` avec `["SSO:*"]`. |
| **Plateforme qui gère l'onboarding d'agences uniquement** | `PUT .../permissions` avec `["ONBOARDING:*"]`. |
| **Outillage interne / superadmin technique** | Laisser `*` (par défaut). À réserver à des cas exceptionnels, jamais à une vraie plateforme externe. |
| **Même plateforme, plusieurs environnements** | Créer un client **par environnement** (`agency-dev-...`, `agency-staging-...`, `agency-prod-...`) — jamais réutiliser une clé DEV en PROD. |
| **Clé compromise (incident)** | `POST /api/v1/admin/api-keys/{keyId}/revoke` — immédiat, pas de fenêtre de grâce (§6). |
| **Rotation planifiée sans coupure** | `POST /api/v1/admin/api-keys/{keyId}/rotate` — voir §6. |
| **Désactiver temporairement sans tout casser** | `PATCH /api/v1/admin/platform-clients/{id}` avec `{"status": "SUSPENDED"}` — la clé reste en base mais toute authentification échoue tant que le client n'est pas `ACTIVE`. |
| **Fin de partenariat** | `DELETE /api/v1/admin/platform-clients/{id}` — décommissionnement doux : statut `DECOMMISSIONED`, toutes les clés actives révoquées. Jamais de suppression physique (audit). |
| **Vérifier ce qu'un client a le droit de faire** | `GET /api/v1/admin/platform-clients/{id}/permissions`. |
| **Enquêter sur un comportement suspect** | `GET /api/v1/admin/platform-clients/{id}/audit-logs?outcome=FORBIDDEN_SCOPE` (ou `INVALID_KEY`, `UNKNOWN_CLIENT`). |

---

## 6. Cycle de vie d'une clé API

### Émission (`POST .../api-keys`)
```json
{ "expiresAt": "2027-01-01T00:00:00Z" }   // ou {} / body vide → sans expiration
```

### Rotation sans coupure (`POST /api/v1/admin/api-keys/{keyId}/rotate`)
```bash
curl -X POST https://core.tiibntick.io/api/v1/admin/api-keys/c19a.../rotate \
  -H "Authorization: Bearer $ADMIN_JWT" -H "Content-Type: application/json" \
  -d '{ "graceHours": 24, "reason": "rotation trimestrielle" }'
```
- Nouvelle clé `ACTIVE` créée (secret retourné une fois) ; ancienne clé passe en `ROTATING` avec une expiration = maintenant + `graceHours` (défaut 24h si omis).
- **Les deux clés fonctionnent** pendant la fenêtre de grâce → la plateforme peut basculer sans interruption.
- Après expiration de la fenêtre, l'ancienne clé cesse automatiquement de fonctionner (vérifiée à chaque authentification).

### Révocation immédiate — incident (`POST /api/v1/admin/api-keys/{keyId}/revoke`)
```bash
curl -X POST https://core.tiibntick.io/api/v1/admin/api-keys/c19a.../revoke \
  -H "Authorization: Bearer $ADMIN_JWT" -H "Content-Type: application/json" \
  -d '{ "reason": "clé compromise — fuite détectée le 2026-07-09" }'
```
Pas de fenêtre de grâce. Effet quasi-immédiat (cache local ≤ 45s, `tnt.platform-gateway.client-cache-ttl`) — acceptable pour ce modèle de menace B2B (voir design doc §7).

---

## 7. Référence complète des endpoints

Tous sous JWT + `TNT_ADMIN` uniquement.

| Méthode | Path | Effet |
|---|---|---|
| `POST` | `/api/v1/admin/platform-clients` | Crée un client, scope `*` accordé automatiquement |
| `GET` | `/api/v1/admin/platform-clients?platformCode=&environment=&status=&page=&size=` | Liste paginée |
| `GET` | `/api/v1/admin/platform-clients/{id}` | Détail |
| `PATCH` | `/api/v1/admin/platform-clients/{id}` | Modifie nom/description/contact/statut (champs `null` = inchangés) |
| `DELETE` | `/api/v1/admin/platform-clients/{id}` | Décommissionnement doux + révoque toutes les clés actives |
| `POST` | `/api/v1/admin/platform-clients/{id}/api-keys` | Émet une clé (`{"expiresAt": "..."}` optionnel) |
| `GET` | `/api/v1/admin/platform-clients/{id}/api-keys` | Liste les clés (préfixe/statut/expiration — jamais le secret) |
| `PUT` | `/api/v1/admin/platform-clients/{id}/permissions` | Remplace tous les scopes (`{"scopes": [...]}`) |
| `GET` | `/api/v1/admin/platform-clients/{id}/permissions` | Scopes actuels |
| `GET` | `/api/v1/admin/platform-clients/{id}/audit-logs?outcome=&from=&to=&page=&size=` | Journal d'audit paginé |
| `POST` | `/api/v1/admin/api-keys/{keyId}/rotate` | Rotation avec fenêtre de grâce (`graceHours`, défaut 24) |
| `POST` | `/api/v1/admin/api-keys/{keyId}/revoke` | Révocation immédiate |
| `GET` | `/api/v1/admin/scope-registry` | Liste des scopes valides |

Valeurs `status` (`ClientStatus`) : `ACTIVE`, `SUSPENDED`, `DECOMMISSIONED`.
Valeurs `environment` (`Environment`) : `DEV`, `STAGING`, `PROD`.
Valeurs `outcome` d'audit (`AuditOutcome`) : `SUCCESS`, `INVALID_KEY`, `UNKNOWN_CLIENT`, `SUSPENDED`, `EXPIRED`, `FORBIDDEN_SCOPE`.

---

## 8. Dépannage

| Symptôme | Cause probable |
|---|---|
| `401 PLATFORM_UNAUTHORIZED` | Client-ID inconnu, clé invalide/expirée/révoquée, ou client `SUSPENDED`/`DECOMMISSIONED`. Le message ne précise jamais lequel (par sécurité) — vérifier `audit-logs` (`UNKNOWN_CLIENT` vs `INVALID_KEY` vs `EXPIRED`/`SUSPENDED`). |
| `403` sur un appel précis alors que l'auth passe | Scope manquant pour ce bloc — vérifier `GET .../permissions` vs le bloc appelé (`AUTH`/`SSO`/`ONBOARDING`). Voir `audit-logs?outcome=FORBIDDEN_SCOPE`. |
| Le secret retourné ne fonctionne plus après coup | Normal — jamais stocké en clair côté serveur. Si perdu : `rotate`, pas de récupération possible. |
| Swagger UI — cadenas ne demande que Client-ID/Api-Key | Normal pour `PlatformAuthController`/`PlatformSsoController` (schémas `ClientIdAuth`/`ApiKeyAuth`). Les endpoints admin (ce guide) utilisent le cadenas Bearer JWT classique. |
| `PLATFORM_INVALID_SCOPE` sur `PUT .../permissions` | Un des scopes envoyés n'est pas dans le catalogue (§4) — seuls `*`, `AUTH:*`, `SSO:*`, `ONBOARDING:*` sont acceptés aujourd'hui. Vérifier via `GET /api/v1/admin/scope-registry`. |

---

## 9. Bonnes pratiques

- **Un client par (plateforme, environnement)** — jamais de clé DEV réutilisée en PROD.
- **Transmission du secret toujours hors-bande** (jamais dans un ticket, un email, un message Slack en clair).
- **`*` par défaut n'est pas un totem** — c'est la sécurité "immédiatement utilisable", pas "toujours laisser tel quel". Restreindre dès que le besoin réel d'une plateforme est connu et plus étroit que "tout".
- **Rotation planifiée régulière** recommandée même sans incident (ex. tous les 6-12 mois) — `graceHours` suffisamment large pour laisser la plateforme redéployer.
- **Auditer périodiquement** `GET .../audit-logs?outcome=FORBIDDEN_SCOPE` — un volume élevé indique soit une plateforme mal configurée, soit une tentative d'accès non autorisé.
