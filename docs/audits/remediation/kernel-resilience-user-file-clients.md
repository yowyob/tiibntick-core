# Chantier — 409/502 des notifications, photo de profil, adresses, catalogue de modèles

**Branche :** `feat/single-system-tenant` · **Date :** 2026-11-12

## Causes trouvées

| Symptôme (frontend) | Cause | Correctif |
|---|---|---|
| `GET /notifications/recipient/{id}` → **409** (lent) | `TNT_NOTIFY_KERNEL_DEFAULT_ORGANIZATION_ID` vide : `KernelNotificationClient.requireContext` levait une `IllegalStateException`, que le gestionnaire global transforme en 409 « State Conflict », hors du `onErrorResume` fail-open. L'organisation dédiée n'existe pas encore côté Kernel (voir `kernel-request-notification-organization.md`). | Les **lectures** (livraisons, préférences, fournisseurs, rappels, modèles) répondent une liste vide (avertissement au plus 1×/min). Les **écritures** échouent en **503** (`KernelContextMissingException` + `NotifyExceptionHandler`), plus en 409. |
| Même appel : 3 à 16 s | Le Kernel n'offre aucun filtre par destinataire : le Core relisait **tout le journal d'envoi du tenant** à chaque appel. | Journal partagé 10 s par tenant/organisation (appels simultanés = 1 appel Kernel). |
| `PUT /actors/me/photo` → 502 après ~7 s ; `GET /actors/me/photo/content` → 404 | L'instance Resilience4j **`kernelUserWebClient` n'était déclarée nulle part** dans `application.yml` : valeurs par défaut de la bibliothèque (limite de temps **1 s**, 3 tentatives). `GET /api/users/me` dépassait 1 s → lecture fail-open → « pas de photo » (404) ; l'envoi (le Kernel analyse le fichier) échouait, et le corps multipart streamé ne peut pas être rejoué. | Instance déclarée (5 s comme `kernelWebClient`) + nouveau client **`kernelFileWebClient`** (30 s, 0 retry) pour `POST /api/files`. `KernelResilienceConfigDeclaredTest` échoue si une instance nommée n'est pas déclarée. |
| 500 opaques sur erreur Kernel | Aucun mapping des erreurs amont. | `TntGlobalExceptionHandler` : erreur HTTP Kernel → **502** (corps Kernel tronqué, `upstreamStatus`), injoignable → 502, délai → **504**, circuit ouvert → **503**. Un 401/403 Kernel n'est jamais relayé tel quel (le front y verrait un jeton expiré). |
| Autocomplétion d'adresse : `suggestions.map is not a function` | Le front supposait un tableau plat ; le Core renvoie des `AddressDTO` imbriqués (`{id, address:{…}, type}`) et ignorait le paramètre `city`. | Front : normalisation tolérante (`lib/addressSearch.ts`). Core : `city` pris en compte, géocodeur en panne = liste vide. |
| Catalogue de modèles tarifaires vide alors que la base en contient | **Non reproduit** sur une base neuve (le test réel `TemplateCatalogSeederIntegrationTest` passe). Piste durcie : une seule ligne illisible (propriétaire/catégorie inconnus) faisait échouer **toute** la liste. | Lignes illisibles ignorées et journalisées par code (`Template 'X' cannot be read…`), `FREELANCER` lu comme `FREELANCER_ORG`, seeder qui continue après un échec. |

## À faire côté exploitation

1. Définir `TNT_NOTIFY_KERNEL_DEFAULT_TENANT_ID` et `TNT_NOTIFY_KERNEL_DEFAULT_ORGANIZATION_ID` (en attente de l'équipe Kernel). Sans eux : historique vide et envois en 503, sans plus de 409.
2. `TNT_KERNEL_FILE_RESPONSE_TIMEOUT_MS` (défaut 30000) ajuste le délai des envois de fichiers.
3. Chercher dans les logs du Core au démarrage et à chaque lecture du catalogue : `Template '…' cannot be read` (ligne de `billing_policy_templates` à corriger).

## Limites connues

- `/actuator/health` du Core déployé répondait `DOWN` (liveness et readiness `UP`) le 2026-11-11 : composant non identifié, détails non exposés.
- Le Core déployé (`6eece15`) n'est pas une révision de ce dépôt local.
