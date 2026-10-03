# C-21 — Dossier de push vers la production

État : **prêt, non poussé.** La décision appartient au fondateur. Ce dossier ne lance rien.
Preuves brutes : `docs/e2e/c21/` (toutes versionnées). Scripts rejouables : `scripts/e2e/c21-*.sh`.

---

## 0. Ce qu'il faut retenir avant de lire le reste

1. **Le bloc `health.status` de C-20.2 était dangereux, mais pas dans le sens redouté.** Un `http-mapping`
   personnalisé *remplace* les correspondances par défaut de Spring Boot au lieu de les compléter : avec
   `DEGRADED: 200` seul, **DOWN et OUT_OF_SERVICE partaient en HTTP 200** sur toutes les sondes, liveness et
   readiness comprises. Mesuré : Redis arrêté, readiness `DOWN` → **200**
   (`health-prod-3-redis-arrete-AVANT-correctif.txt`). Pousser C-20 tel quel aurait rendu la prod incapable de
   signaler une panne. **Corrigé dans `7fc45b35`**, prouvé par test et par mesure : readiness `DOWN` → **503**
   (`health-prod-5-…`). Ne poussez jamais `fd5e4f15` seul.
2. **La production ne tourne pas avec le profil Spring `prod`** (`prod-profil-reel.txt`) : `/actuator/info` →
   `activeProfile: DEV`, `/actuator/prometheus` public (interdit en PROD), et le `docker-compose.prod.yml` du
   miroir dit l.80 « on n'active PAS le profil prod ». Le bloc `health.status` est dans le document principal :
   il s'applique bien en prod.
3. **L'orchestrateur de prod ne lit que `/actuator/health/liveness`** (healthcheck compose du miroir, `curl -sf`,
   `start_period 180s`, `retries 20`). L'agrégat `DEGRADED` ne peut ni redémarrer le conteneur ni le retirer du proxy.
4. **Aucun changement de schéma.**

---

## 1. Inventaire (mesuré le 2026-10-03, après `git fetch github`)

```
$ git fetch github && git rev-parse --short github/main
8ed4710a
$ git log --oneline github/main..HEAD | wc -l
23            # avant le commit de ce dossier — 24 après ; re-mesurer le jour du push
```

Les 19 commits des lots C-16 à C-20 (`09e10cd5` → `fd5e4f15`), puis C-21 : `5d40126d` (gofp SSE),
`e25403e8` (roles `freelancer:read`), `4754b6b1` (dossier partiel), `7fc45b35` (sondes de santé), et le commit de ce dossier.

`git diff --name-only github/main..HEAD | wc -l` → **117 fichiers** :

| Fichiers | Zone |
|---:|---|
| 29 | coreBackend/tnt-go-freelancer-point-back-core |
| 28 | docs/e2e (preuves) |
| 22 | foundation/tnt-roles-core |
| 22 | docs/PROMPT_* |
| 6 | scripts/e2e |
| 4 | tnt-bootstrap |
| 3 | identity/tnt-actor-core |
| 1 | logistics/tnt-delivery-core |
| 2 | docs/NOTE_DEPLOIEMENT_CORE.md, docs/deploy |

**Changements de schéma : zéro.**
```
$ git diff --name-only github/main..HEAD | grep -iE '\.sql$|liquibase|changelog|db/'
coreBackend/.../gofreelancer/changelog/GofpProjectionTablesSeedGuardTest.java   # test
identity/.../actor/changelog/TntActorCoreChangelogGuardTest.java                # test
```
Les deux occurrences sont des classes de test (gardes anti-seed). La migration `V5__seed_role_freelancer.sql`
ajoutée par C-16 a été supprimée par C-17 : bilan net nul.

**Seul fichier de configuration de production touché : `tnt-bootstrap/src/main/resources/application.yml`**, diff complet :
```diff
+      # Lot C-20.2 — DEGRADED (TntRoleSyncOutboxHealthIndicator: DEAD Kernel role-sync
+      # entries) ranks above UP so the aggregate shows it, but stays HTTP 200: a
+      # Kernel-side refusal is not something a pod restart can fix.
+      # Lot C-21 — a custom http-mapping REPLACES Spring's defaults instead of extending
+      # them: with DEGRADED alone, DOWN and OUT_OF_SERVICE were served as 200 on every
+      # health endpoint, liveness/readiness included (measured). Keep the two 503 lines.
+      status:
+        order: DOWN,OUT_OF_SERVICE,DEGRADED,UP,UNKNOWN
+        http-mapping:
+          DOWN: 503
+          OUT_OF_SERVICE: 503
+          DEGRADED: 200
```

---

## 2. Le risque health, instrumenté

### 2.1 Comment le mapping se comporte (classe de Spring Boot 4.0.6, celle du binaire)
`SimpleHttpCodeStatusMapper`, sondé directement (jars extraits du conteneur) :

| Statut | défaut | `{DEGRADED:200}` (C-20.2) | C-21 |
|---|---|---|---|
| UP | 200 | 200 | 200 |
| DEGRADED | 200 | 200 | 200 |
| OUT_OF_SERVICE | **503** | **200** | 503 |
| DOWN | **503** | **200** | 503 |

`HealthStatusHttpMappingTest` (tnt-bootstrap) lit le vrai `application.yml` (document par défaut **et** profil
`prod`) et le passe à ce mapper : rouge sur la config C-20.2 (`health-mapping-test-AVANT.txt`, « DOWN expected
503 but was 200 »), vert après (`health-mapping-test-APRES.txt`).

### 2.2 Mesures sur conteneur fraîchement (re)démarré, profil `prod`
Environnement isolé : base `tiibntick_c21_prod` dédiée, Kafka et Redis dédiés, identifiants MinIO non par défaut
(le profil `prod` refuse les secrets du dev via `TntProdSecretsGuard`, et aurait porté les topics Kafka partagés à 6 partitions).

| Fichier | Binaire | État | `/health` | liveness | readiness |
|---|---|---|---|---|---|
| `health-prod-1-frais-sans-DEAD.txt` | e25403e | 0 DEAD | 200 UP | 200 | 200 |
| `health-prod-2-redemarre-avec-DEAD.txt` | e25403e | 10 DEAD, juste après démarrage | **200 OUT_OF_SERVICE** | 200 | 200 |
| `health-prod-3-redis-arrete-AVANT-correctif.txt` | e25403e | Redis arrêté | **200 DOWN** | 200 | **200 DOWN** |
| `health-prod-4-frais-avec-DEAD-7fc45b3.txt` | 7fc45b3 | 10 DEAD | 200 DEGRADED | 200 | 200 |
| `health-prod-5-redis-arrete-APRES-correctif.txt` | 7fc45b3 | Redis arrêté / rétabli | **503 DOWN** / 200 DEGRADED | 200 / 200 | **503 DOWN** / 200 UP |
| `health-prod-6-sequence-demarrage-7fc45b3.txt` | 7fc45b3 | démarrage seconde par seconde | 503 DOWN ~2 s → 200 DEGRADED | 503 1 s → 200 | 503 OUT_OF_SERVICE ~2 s → 200 |
| `health-prod-7-DEAD-purgees-7fc45b3.txt` | 7fc45b3 | DEAD purgées (base de test) + redémarrage | 200 UP | 200 | 200 |

Profil réellement utilisé par la prod (document par défaut), conteneur dev frais, 15 DEAD :
`final-dev-7fc45b3-me-et-sante.txt` → `/health` 200 DEGRADED, liveness 200, readiness 200.

Les 10 DEAD de la base de test sont **naturelles** : 403 du vrai Kernel (`POST /api/roles`), 10 tentatives.

---

## 3. Plan de retour

### 3.1 Comment revenir
Le miroir `yowyob/tiibntick-core` ne rejoue pas l'historique : `yowyob/main` contient **un seul commit**
(`689fabf2`, `git rev-list --count yowyob/main` → 1), un instantané. Son arbre = celui de `github/main` plus
6 fichiers qui lui sont propres (`.github/workflows/{deploy,docker-publish,mirror,notify-yowyob}.yml`,
`Dockerfile`, `docker-compose.prod.yml`) — `git diff --stat yowyob/main github/main` ne montre que ces 6 fichiers.
Revenir = pousser un `main` dont l'arbre égale `8ed4710a` :

```bash
git fetch github
git switch -c revert/c21 github/main          # github/main contient alors les commits poussés
git revert --no-edit 8ed4710a..HEAD           # un commit de revert par commit, du plus récent au plus ancien
git diff --stat 8ed4710a HEAD                 # DOIT être vide
git push github revert/c21:main               # décision du fondateur, comme le push
```
Le miroir produira un nouveau SHA (jamais `689fabf`). Vérification : `/actuator/info` → SHA miroir `M`, puis
`git fetch yowyob && git diff --stat yowyob/main 8ed4710a` → uniquement les 6 fichiers du miroir.

### 3.2 Effets non réversibles par un retour de code (l'ancien binaire s'en sort-il ?)
| Effet laissé en base par le nouveau code | Ancien binaire OK ? | Raison |
|---|---|---|
| `tnt_roles` FREELANCER élargi (elect, freelancer:read, …) par la réconciliation | **oui** | l'ancien code (`upsertIfAbsent`) ne touche pas un rôle existant ; permissions en plus, inoffensives sans le repli C-18 |
| lignes `tnt_role_sync_outbox` `UPDATE_ROLE` | **oui, dégradé** | l'enum de l'ancien binaire n'a pas `UPDATE_ROLE` ; son `KernelRoleReconciliationJob` mappe toutes les lignes PROVISIONED avant de filtrer → échec à chaque passage horaire (rattrapé par `onErrorResume`, journalisé). Le worker n'est bloqué que si une `UPDATE_ROLE` est encore PENDING/RETRYING : mesuré PROVISIONED en quelques secondes. Le job Kernel est de toute façon inutile (403). Remède si besoin : `DELETE FROM tnt_role_sync_outbox WHERE operation='UPDATE_ROLE'` (lignes d'audit local uniquement) |
| assignations FREELANCER (`tnt_user_role_assignments`, C-16) | **oui** | sans le repli tenant système de C-18, elles ne donnent aucune permission — retour à l'état actuel |
| entrées `ASSIGN_ROLE` de l'outbox | **oui** | opération connue de l'ancien binaire ; elles iront DEAD (Kernel 403) comme aujourd'hui |
| wallets créés par `getOrCreateWallet` | **oui** | même table, même clé (`owner_id = actorId`) ; l'ancien `splitMissionRevenue` les trouvera — les paiements de ces livreurs continueront de marcher |
| projections `gofp_freelancers` / `tnt_delivery_persons` / `gofp_users` (C-18) | **oui** (non rejoué) | même schéma, aucune migration ; non vérifié en run sur `689fabf` |
| copies de rôles canoniques réconciliées dans d'autres tenants (C-21) | **oui** | même raison que la première ligne |

### 3.3 Fenêtre de bascule et vérification d'après-bascule (dans l'ordre)
Bout en bout ≈ 20 min (note de déploiement §3) ; le job `deploy.yml` fait `docker compose up -d` (arrêt de
l'ancien, démarrage du nouveau) : 404 sur le proxy jusqu'au premier healthcheck `liveness` vert. Le démarrage
mesuré en profil prod local a pris de 35 s à 2 min 50 ; `start_period` est de 180 s.

```bash
P=https://tiibntick-core.yowyob.com
# 1. Le binaire a changé, et c'est bien le nôtre
curl -s $P/actuator/info | jq -c '{commit: .git.commit.id.abbrev, profile: .tiibntick.solution.activeProfile}'
git fetch yowyob && git diff --stat yowyob/main <SHA_POUSSE>        # uniquement les 6 fichiers du miroir
# 2. Sondes (attendu : DEGRADED 200 — la prod a des DEAD Kernel ; liveness 200 ; readiness 200)
for p in /actuator/health /actuator/health/liveness /actuator/health/readiness; do
  printf '%s → ' $p; curl -s -o /tmp/h.json -w '%{http_code} ' $P$p; jq -c '{status}' /tmp/h.json; done
# 3. Le parcours débloqué, avec un compte freelancer réel (jeton BFF)
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" $P/api/v1/freelancers/me   # 200 (404 si pas de profil)
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" \
     "$P/billing/wallet/$SUB/balance?tenantId=$TID"                                                     # 200 (403 aujourd'hui)
# 4. Jauges, publiques en prod
curl -s $P/actuator/prometheus | grep -E '^(tnt_roles_sync_outbox_entries|gofp_delivery_payment_failures_total|gofp_freelancer_provisioning_failures_total)'
```

---

## 4. Ce que la prod gagne, en langage d'utilisateur de l'APK

| Commit | Après le push, l'utilisateur peut… |
|---|---|
| 09e10cd5 C-16 | — (attribue FREELANCER à la création du profil ; sans effet seul, voir f23fabb9) |
| 6f329a11 C-17 | être **élu** sur une annonce à laquelle il a répondu |
| f26db205 C-18 | exister côté Go/Point dès sa première requête : il peut **souscrire / répondre** sans intervention manuelle |
| 25a02dd5 | interne, aucun effet utilisateur |
| b4c86b68 | interne (tests), aucun effet |
| f23fabb9 C-18 | **ne plus recevoir 403** sur répondre/souscrire, wallet, livraisons : le rôle FREELANCER donne enfin ses permissions |
| 6965df1f C-19.5 | interne (sécurité du repli), aucun effet |
| 6948cab2 C-19.3 | aucun (route publique sans appelant supprimée) |
| 23e62425 C-19.1 | obtenir des réponses plus rapides sur les écrans non-freelancer |
| 3b415051 | documentation, aucun effet |
| ae65383b | test E2E, aucun effet |
| ba99add5 C-19.2 | **être payé dès sa première livraison** (avant : 500, wallet absent) |
| 24e298f7 | documentation, aucun effet |
| 96cb607e C-20.2 | interne (supervision) — à pousser **avec** 7fc45b35, jamais sans |
| d22580c0 C-20.3 | interne (alerte livrée-impayée), aucun effet visible |
| 3488e892 | test E2E, aucun effet |
| 3e2e9f4b | documentation, aucun effet |
| 246d2ceb C-20 | **rouvrir l'app en étant déjà freelancer sans erreur 500** |
| fd5e4f15 | documentation, aucun effet |
| 5d40126d C-21.2 | aucun (contrat SSE identique octet pour octet) |
| e25403e8 C-21.3 | lire son profil (`/me` 200 au lieu de 403) — effet visible une fois le BFF adapté |
| 4754b6b1 | documentation, aucun effet |
| 7fc45b35 C-21 | interne : une panne réelle redevient visible (503) pour l'orchestrateur |

---

## 5. La bascule — à exécuter par le fondateur, pas par le lot

### 5.1 Juste avant
```bash
cd tiibntick-core
git status --short                                  # vide
git fetch github && git log --oneline github/main..HEAD | wc -l   # recompter, comparer à §1
git log -1 --format=%h                              # le SHA qu'on pousse
M=/home/jtk/.local/opt/apache-maven-3.9.9/bin/mvn
$M -o -pl foundation/tnt-roles-core,coreBackend/tnt-go-freelancer-point-back-core,identity/tnt-actor-core,logistics/tnt-delivery-core,tnt-bootstrap test
#   attendu : roles 163/0, gofp 130/0, actor 61/0, delivery 65/0, bootstrap 37/3 (les 3 préexistants : §7)
#   ATTENTION : la suite bootstrap pose des verrous ShedLock dans la base de dev (voir §7) — libérer après.
```

### 5.2 La commande
```bash
git push github feat/lotC16-role-freelancer:main
```

### 5.3 Après : §3.3, dans l'ordre.

### 5.4 Les 24 h qui suivent
Jauges (publiques : `curl -s $P/actuator/prometheus`) :

| Signal | Normal | Revenir en arrière si |
|---|---|---|
| liveness / readiness | 200 | 503 persistant > 5 min hors redémarrage |
| `/actuator/health` | 200 DEGRADED (DEAD Kernel existants) | 503 persistant |
| `tnt_roles_sync_outbox_entries{status="DEAD"}` | valeur de départ, puis +1 par nouveau freelancer (ASSIGN_ROLE refusé par le Kernel) | hausse sans nouveaux freelancers |
| `…{status="PENDING"}` + `{status="RETRYING"}` | ≈ 0 en régime établi | > 0 et croissant pendant 30 min (worker bloqué) |
| `gofp_delivery_payment_failures_total` | 0 | ≥ 1 : livraison livrée-impayée, traiter à la main ; ≥ 3 en 24 h → revenir |
| `gofp_freelancer_provisioning_failures_total` | 0 | croissant |

Motifs de journal (`docker logs tiibntick-core`, insensible à la casse) :
`UNPAID_DELIVERED` · `Role sync poll cycle failed` · `reconciliation error` · `DuplicateKeyException` ·
`ROLE_FORBIDDEN` sur `/api/v1/freelancers` ou `/billing/wallet` pour un freelancer · `ROLLBACK` · HTTP 500 sur `/api/v1/freelancers`.

SQL (accès base de prod) :
```sql
-- FREELANCER porte bien freelancer:read, partout où il existe
SELECT tenant_id, position('freelancer:read' in permissions) > 0 AS ok
FROM tnt_roles WHERE code = 'FREELANCER';
-- état de l'outbox Kernel
SELECT operation, status, count(*) FROM tnt_role_sync_outbox GROUP BY 1,2 ORDER BY 1,2;
-- freelancers ayant un profil mais aucune assignation FREELANCER (doit diminuer)
SELECT count(*) FROM tnt_actor.freelancer_profiles fp
WHERE NOT EXISTS (SELECT 1 FROM tnt_user_role_assignments a JOIN tnt_roles r ON r.id = a.role_id
                  WHERE r.code = 'FREELANCER' AND a.user_id = fp.actor_id);
-- livraisons livrées sans crédit MISSION (non exécutée sur données : table vide en local)
SELECT d.id FROM public.deliveries d
WHERE d.status = 'DELIVERED'
  AND NOT EXISTS (SELECT 1 FROM billing.wallet_transactions t
                  WHERE t.reference_id = 'MISSION-' || d.id AND t.type = 'CREDIT');
```
Les quatre requêtes ont été exécutées sur la base de dev le 2026-10-03 : FREELANCER `ok = t` ; 3ᵉ → **1** profil
sans assignation (profil antérieur à C-16, non investigué) ; 4ᵉ → 0, mais sur une table `public.deliveries` vide
(purgée par les nettoyages E2E) — sa correction sur des données réelles n'est pas prouvée. La 3ᵉ suppose
`freelancer_profiles.actor_id` = `user_id` de l'assignation (le run C-21 montre `actorId = sub`).

---

## 6. Ce que le push ne règle pas
- Swagger UI et `/v3/api-docs` sont ouverts sans authentification en prod (`swagger-prod-exposition.txt`) ;
  la valeur du profil `prod` n'y change rien puisque la prod n'utilise pas ce profil. Décision du fondateur.
- `/actuator/prometheus` est public en prod pour la même raison.
- Le flux SSE ne délivre aucune notification (consommateur Kafka sans convertisseur JSON) : `sse-live-avant-apres.txt`.
- Les gardes `hasRole('FREELANCER')` restent mortes (aucun claim `roles` dans le jeton Kernel).

## 7. Défauts préexistants relevés en route (non corrigés)
- `KernelBridgeConfigResilienceTest.circuitBreakerOpensAndShortCircuits` échoue sur l'arbre d'origine (`fd5e4f15`) :
  le filtre de résilience voit un 500 comme une réponse réussie (la conversion en erreur se fait après le filtre).
- `TntRoleInitializationService.provisionSystemRoles` est lancé deux fois en parallèle (registrar + `ApplicationReadyEvent`) :
  `UPDATE_ROLE` en double à chaque réconciliation, `DuplicateKeyException` sur base vierge.
- `TiiBnTickApplicationTest` démarre le contexte contre la base de dev (port 5433) et y pose des verrous ShedLock
  à l'heure locale (+01:00) dans un `timestamp without time zone` : les tâches planifiées du conteneur de dev
  sont gelées ~1 h après chaque `mvn test` de `tnt-bootstrap`.
- Démarrage du profil prod local : 35 s à 2 min 50 selon les runs, à rapprocher du `start_period` de 180 s.
