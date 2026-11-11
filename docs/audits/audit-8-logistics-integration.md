# Audit 8 — Intégration de la couche logistics (9 modules) et du frontend Go-Freelancer

Date : 2026-11-11 · Branche : `feat/logistics-integration-audit` · Base : `main` @ `95c5ddb8`

> **Niveau de preuve.** Cet audit est une **analyse statique** du code (Core et frontend `TiiBnTick-Go-Freelance-Frontend`). Rien n'a été exécuté contre un Core ou un Kernel déployé, ni dans un navigateur. Chaque constat porte un niveau : **[Constaté]** (lu dans le code), **[À confirmer]** (hypothèse plausible qu'un test d'intégration doit trancher). Les lots du §6 commencent par écrire ces tests.

## 1. Décisions de cadrage (validées avec le mainteneur)

| Sujet | Décision |
|---|---|
| Démarche | Audit d'abord, puis implémentation par lots |
| API de livraison de référence | **gofp en façade** : le frontend ne parle qu'à `tnt-go-freelancer-point-back-core` ; gofp orchestre les modules logistics |
| Flux à rendre complets (Core + frontend) | Livraison et suivi live · Litiges et médiation · Incidents terrain · Preuves média et hors-ligne |
| Vérification | Tests d'intégration Core (Testcontainers) + tests frontend (vitest, faux Core HTTP), rapport honnête de ce qui n'est pas vérifié |
| Temps réel | SSE + ping GPS REST (pas de WebSocket) |
| Hors-ligne | File d'attente livreur (PWA) rejouée via `/api/v1/sync` |
| Arbitrage litiges/incidents (console admin) | **Non tranché** — voir §7 |

## 2. Inventaire des 9 modules

Classes de `src/main`, tests de `src/test` (unitaires / intégration), endpoints REST, consommateurs Kafka.

| Module | Classes | Tests U / IT | Endpoints | Base REST | Consommateurs Kafka | Utilisé par gofp | Utilisé par le frontend |
|---|---|---|---|---|---|---|---|
| geo | 63 | 6 / 1 | 18 | `/api/v1/geo` | 0 | Oui (9 fichiers : relais, géocodage, réseau routier) | **Non** (Nominatim public) |
| route | 49 | 5 / 1 | 1 | `/api/v1/routes/optimize` | 0 | Non | **Non** (OSRM public) |
| delivery | 111 | 12 / 1 | 28 | `/api/v1/tenants/{tenantId}/…` | 3 | **Objets-valeurs seulement** (voir F1) | **Non** (via gofp) |
| dispute | 95 | 8 / 3 | 20 | `/api/v1/disputes` | 1 | Non | Oui (`SupportHub`) |
| incident | 130 | 3 / 1 | 22 | `/api/v1/incidents` | 1 (3 listeners) | Non | Oui (`SupportHub`) |
| realtime | 92 | 12 / 3 | 4 | `/api/v1/realtime` | 1 | Oui (ping GPS, présence, ETA) | **Non** (aucun flux SSE de suivi) |
| sync | 63 | 6 / 2 | 4 | `/api/v1/sync` | 1 | Non | **Non** |
| notify | 61 | 6 / 1 | 17 | `/api/v1/notifications` | 2 | Oui (2 adaptateurs push/e-mail) | Oui (`NotificationBell`) |
| media | 37 | 4 / **0** | 5 | `/api/v1/media` | 0 | Non | Oui (`SupportHub`, preuves) |

Les 9 modules sont tous chargés par le scan global de `tnt-bootstrap` (`@ComponentScan("com.yowyob.tiibntick")`) ; seul le module trust en est exclu. **[Constaté]**

## 3. Constats

### F1 — gofp a son propre modèle de livraison ; les modules logistics ne voient pas ses livraisons **[Constaté]** — critique
- gofp définit son agrégat `Delivery`, `DeliveryRepository`, `DeliveryApplicationService` et `DeliveryStatusApplicationService` (670 lignes). Il n'importe de `tnt-delivery-core` que des **objets-valeurs et énumérations** (`TrackingCode`, `DeliveryAddress`, `DeliveryStatus`, `DeliveryPerson`, `AnnouncementResponse`…), jamais les cas d'usage de livraison.
- Aucune référence à `DELIVERY_MISSION_*`, `tnt.delivery.mission.*` ou `REALTIME_*` dans les sources de gofp : **gofp ne publie pas les événements de mission** que consomment les autres modules.
- Conséquences attendues pour une livraison gofp (celle que voit le frontend) : le consommateur d'incidents (`tnt.delivery.mission.status.changed`), le suivi temps réel par mission (`MissionStatusEventConsumer` de realtime), `tnt-sales-core` (`DELIVERY_MISSION_STARTED/COMPLETED/FAILED`), `tnt-tp-core` et `InvoiceBillingEventConsumer` (`MISSION_COMPLETED`) **ne reçoivent rien**. La facture a été rattachée à la confirmation du paiement (commit `fa273afe`) précisément pour contourner ce trou.
- **Décision à traduire en code** : « gofp en façade » exige que gofp **émette** (ou délègue à delivery-core) les événements de cycle de vie, sans dupliquer la logique métier.

### F2 — Notifications : aucun lien entre cycle de vie de livraison / litige / incident et notify **[Constaté]** — majeur
- `tnt-notify-core` n'écoute que `tnt.admin.freelancer_org.*`, `tnt.delivery.freelancer_org.assigned` et `tnt.billing.template.applied`. Aucun listener sur `DELIVERY_*`, `DISPUTE_*`, `INCIDENT_*`.
- Dans gofp, les notifications sortent par `PushNotificationPort` (adaptateurs `GofpNotifyPushAdapter`/`GofpNotifyEmailAdapter`) et ne couvrent que des cas ponctuels (dépôt en relais, matching). Le client n'est donc pas prévenu du ramassage, de la remise, d'un litige ou d'un incident.
- Côté frontend, la cloche lit `/api/v1/notifications/recipient/{id}` ; le seul flux temps réel est le SSE livreur `/api/notifications/stream/{profilId}` (chemin gofp historique, pas `/api/v1/…`).

### F3 — Permissions : trous du même type que `invoice:read` / `report:own` **[Constaté]**
Les permissions exigées par les cas d'usage sont comparées à `TntRole` (vérité de l'évaluateur) :

| Permission / rôle | Constat | Impact |
|---|---|---|
| `incident:create` | accordée à FREELANCER, PERMANENT_DELIVERER, RELAY_OPERATOR, AGENCY_HUB_OPERATOR — **pas à CLIENT** | Un client ne peut pas signaler un incident (il peut ouvrir un litige). À confirmer comme intentionnel. |
| `delivery:create-direct` | exigée par `DeliveryAnnouncementService` (ligne 139), **absente de `TntPermission`** | Aucun rôle de `TntRole` ne la porte : seul TNT_ADMIN (`ALL`) peut passer. |
| `FREELANCER_OWNER` | utilisé dans des `@PreAuthorize` de geo et delivery ; c'est un **type d'acteur** (`ActorType`), **pas un rôle de `TntRole`** | La garde `hasRole('FREELANCER_OWNER')` ne peut correspondre que si un tel rôle figure dans le JWT, ce que le catalogue ne prévoit pas : branche vraisemblablement morte **[À confirmer]**. |
| `dispute:resolve`, `incident:manage` | réservées à AGENCY_MANAGER / SUPPORT_AGENT / ORG_ADMIN (+ BRANCH_MANAGER pour incident) | Il n'existe pas de rôle « médiateur plateforme » hors TNT_ADMIN : lié à la question d'arbitrage (§7). |
| `mission:start`, `mission:complete` | FREELANCER et PERMANENT_DELIVERER seulement | Cohérent avec le livreur ; gofp utilise ses propres contrôles. |

- Dans `DisputeController`, `@RequirePermission` n'apparaît **que dans le Javadoc** ; la garde est portée par les services (`DisputeCommandService`, `DisputeQueryService`, `EvidenceApplicationService`) via l'AOP `TntPermissionAspect`. Elle ne s'applique que si `tnt.roles.aop-enabled=true` (valeur par défaut `true`, non surchargée dans `tnt-bootstrap`). **[À confirmer en IT]** que la garde déclenche bien avec un JWT `ROLE_CLIENT`.
- **Sans garde de méthode ni de classe** (authentification seule) : `POST /api/v1/routes/optimize` ; tous les endpoints `delivery-announcements`, `delivery-persons`, `deliveries/track/{code}` de delivery-core ; les 4 endpoints realtime ; les 4 endpoints sync. Pour realtime et sync, il faut vérifier la propriété de la ressource (un livreur ne doit lire que ses missions / ses deltas).

### F4 — Isolation par tenant dans delivery-core **[À confirmer]** — potentiellement critique
Les contrôleurs de delivery-core prennent `{tenantId}` **dans l'URL** et aucune classe du module ne référence `TntSecurityContext` ni ne compare ce paramètre au tenant du jeton. Si aucun filtre global ne le fait, un utilisateur authentifié pourrait lire ou publier dans un autre tenant en changeant l'URL. Les modules gofp, billing et invoice déduisent au contraire le tenant du jeton. Un test d'intégration doit trancher.

### F5 — Frontend : les services Core logistics sont presque inutilisés **[Constaté]**
- `disputeService`, `incidentService`, `mediaEvidenceService` : un seul consommateur, `components/support/SupportHub.tsx`.
- Géocodage et itinéraires : **Nominatim et OSRM publics** (`services/geocoding.ts`, `services/routing.ts`), pas `geo` ni `route` du Core. Donc ni cohérence avec les relais/réseau routier du Core, ni optimisation de tournée.
- Aucun flux SSE de suivi (`/api/v1/realtime/sse/tracking/{code}`) côté client ; la page de suivi (`app/livraison/[id]/suivi`) charge l'état par `getAnnouncementTracking` (requête ponctuelle), sans abonnement temps réel.
- Aucun usage de `sync`, aucun mode hors-ligne.
- Détail de la cloche et des mappings d'identifiants : voir `TiiBnTick-Go-Freelance-Frontend/docs/RECENSEMENT-NON-CONNECTE.md` (§4 « Mappings à vérifier en direct »).

### F6 — Câblage Kafka : producteurs sans consommateur **[Constaté, heuristique]**
Recherche statique des constantes `TntTopics` et des littéraux. Les cas suivants n'ont **aucun consommateur** dans le dépôt : `DISPUTE_OPENED/STATUS_CHANGED/EVIDENCE_SUBMITTED/RULED/ESCALATED/COMPENSATION_PROCESSED/CLOSED`, `INCIDENT_CREATED/STATUS_CHANGED/TRIAGED/DRIVER_ASSIGNED/HANDOVER_COMPLETED/CANCELLED/ESCALATED/INTERAGENCY_*`, `DELIVERY_PACKAGE_DISPUTED/DISPUTE_RELEASED`, `DELIVERY_ANNOUNCEMENT_RESPONSE_SELECTED`. C'est cohérent avec F2 : ce sont exactement les événements qui devraient déclencher des notifications.
`REALTIME_GPS_POSITION_UPDATED` a deux consommateurs (incident, realtime) mais son producteur est défini dans `GpsPositionUpdatedEvent` (littéral de topic) ; **à confirmer** que le ping REST aboutit bien à cet événement. Les topics `SYNC_*`, `MEDIA_*`, `NOTIFY_*`, `REALTIME_ETA/PRESENCE` n'ont ni producteur ni consommateur repérés par l'heuristique.

### F7 — Couverture de tests faible là où le risque est le plus grand **[Constaté]**
`media` : 0 test d'intégration. `incident` : 130 classes pour 3 tests unitaires et 1 d'intégration. `delivery` : 111 classes, 1 test d'intégration. Aucun test d'intégration ne traverse deux modules (par exemple gofp → delivery → realtime → notify).

## 4. Matrice flux × modules × frontend

Légende : ✅ câblé et plausible · 🟡 partiel · ❌ absent · ❓ à confirmer par test.

### Flux A — Livraison et suivi live
| Étape | Modules concernés | Core | Frontend |
|---|---|---|---|
| Création d'annonce, tarification | gofp, billing-pricing | ✅ | ✅ |
| Affectation / sélection du livreur | gofp (matching) | ✅ | ✅ |
| Cycle de vie (ramassage → remise, OTP) | gofp ; **delivery-core non utilisé** | 🟡 (F1) | ✅ (`deliveryLifecycle`) |
| Position GPS du livreur | realtime (`IProcessGpsPingUseCase`) via gofp | ❓ | 🟡 (ping, pas de suivi client) |
| Suivi live côté client | realtime SSE par code de suivi | ❓ | ❌ |
| Itinéraire / ETA | route, realtime (Kalman) | ❌ branchement gofp | ❌ (OSRM public) |
| Notifications d'étape | notify | ❌ (F2) | 🟡 (cloche) |
| Événements mission (sales, invoice, tp, incident) | delivery-core | ❌ (F1) | n/a |

### Flux B — Litiges et médiation
| Étape | Core | Frontend |
|---|---|---|
| Ouvrir un litige (client / livreur) | ✅ `dispute:create` accordée | ✅ `SupportHub` |
| Preuves, commentaires | ✅ | ✅ |
| Mise en médiation, décision, compensation | ✅ côté API, `dispute:resolve` réservée aux rôles back-office | ❌ aucune console |
| Effet sur le colis / le portefeuille | 🟡 `DELIVERY_PACKAGE_DISPUTED` publié, sans consommateur ; compensation côté wallet via `INCIDENT_ESCALATED_TO_DISPUTE` seulement | ❌ |
| Notifications aux parties | ❌ (F2, F6) | 🟡 |

### Flux C — Incidents terrain
| Étape | Core | Frontend |
|---|---|---|
| Signalement (livreur) | ✅ `incident:create` | ✅ `SupportHub` |
| Signalement (client) | ❌ permission absente (F3) | n/a |
| Tri, escalade, résolution, clôture | ✅ API, `incident:manage` back-office | ❌ aucune console |
| Transfert à un autre livreur (handover), coopération inter-agences | ✅ API | ❌ |
| Effet sur la livraison gofp | ❌ le consommateur d'incidents n'écoute que les missions delivery-core (F1) | ❌ |

### Flux D — Preuves média et hors-ligne
| Étape | Core | Frontend |
|---|---|---|
| Upload de preuve, URL signée | ✅ API ; **0 IT** ; `MEDIA_UPLOAD` : FREELANCER oui, CLIENT non | ✅ `mediaEvidenceService` (support uniquement) |
| Preuve de remise (photo, signature) rattachée à la livraison gofp | ❌ non branché | ❌ |
| Synchronisation hors-ligne (push/pull/bootstrap, schéma DuckDB) | ✅ API, 2 IT | ❌ aucune file d'attente |

## 5. Risques si on n'agit pas
1. Les modules logistics restent des îlots : les écrans de litige, d'incident et de suivi ne reflètent pas les livraisons réelles.
2. Si F4 se confirme, fuite inter-tenant sur delivery-core (à traiter avant tout autre lot).
3. Les trous de permission se manifestent comme des 403 en production, un par un, comme pour la facturation.

## 6. Plan de lots proposé

| Lot | Contenu | Vérification |
|---|---|---|
| **L0 — Sécurité** | Trancher F4 (IT multi-tenant sur delivery-core, correction si besoin). Corriger F3 : `delivery:create-direct`, `FREELANCER_OWNER`, décision sur `incident:create` pour CLIENT. Gardes sur route/realtime/sync. | IT + test `TntProfileRolePermissionsTest` étendu aux ressources logistics |
| **L1 — Cycle de vie → événements** | gofp émet les événements de mission (ou délègue à delivery-core) pour que sales, invoice, tp, incident et realtime voient les livraisons gofp. | IT gofp → Kafka (Testcontainers) → consommateurs |
| **L2 — Notifications** | Listeners notify sur `DELIVERY_*`, `DISPUTE_*`, `INCIDENT_*`, gabarits i18n, préférences ; cloche et SSE frontend alignés. | IT notify + test frontend |
| **L3 — Suivi live** | Ping GPS → realtime → SSE de suivi par code ; consommation côté client ; ETA. Géocodage/itinéraire via Core si pertinent. | IT realtime/gofp + test frontend |
| **L4 — Litiges et incidents** | Effets métier (colis en litige, compensation, handover sur gofp) ; écrans client/livreur de suivi de dossier ; console admin selon décision §7. | IT dispute/incident + tests frontend |
| **L5 — Preuves et hors-ligne** | Preuve de remise rattachée à la livraison ; file d'attente PWA livreur, idempotence et conflits via `/api/v1/sync`. | IT media/sync + tests frontend |

Chaque lot : branche dédiée du Core (`feat/logistics-L<n>-…`), commits en anglais sans co-auteur, push par le mainteneur.

## 7. Questions ouvertes
1. **Arbitrage** : qui traite litiges et incidents côté TiiBnTick (console admin dans ce frontend, back-office externe, ou à décider après lecture) ? Conditionne la création d'un rôle « médiateur » et le périmètre de L4.
2. `incident:create` pour CLIENT : voulu ou oubli ?
3. Pour F1, préférez-vous que gofp **délègue** à `tnt-delivery-core` (une seule source de vérité) ou qu'il **émette** les mêmes événements depuis son modèle (moins invasif, deux modèles à garder cohérents) ?

## 8. Mise en œuvre et preuves (branche `feat/logistics-integration-audit`)

### 8.1 Décisions appliquées
- §7.1 : rôle **MÉDIATEUR** complet côté Core, pas de console `/admin` ; les clients ouvrent des **litiges** uniquement (pas d'incidents).
- §7.3 : **convergence** — gofp délègue à `tnt-delivery-core`, qui reste la seule source de vérité du cycle de vie.

### 8.2 Lots livrés
| Lot | Résultat |
|---|---|
| L0 Sécurité | Gardes de permission logistics ; évaluateur de rôles toujours instancié (voir 8.3). |
| L1 Convergence | Les livraisons gofp passent par `tnt-delivery-core` et émettent ses événements. |
| L2 Notifications | Listeners notify sur `DELIVERY_*`, `DISPUTE_*`, `INCIDENT_*`. |
| L3 Suivi live | Position et ETA poussés en SSE jusqu'à la page de suivi par code. |
| L4 Litiges / incidents / médiateur | Effets métier branchés ; messagerie de négociation côté Core (REST + SSE) et interface frontend réactive. |
| L5 Preuves et hors-ligne | `DeliveryProof` rattaché à la livraison ; applier gofp pour `/api/v1/sync/push` (idempotent, ordre par `sequenceNumber`) ; file IndexedDB par utilisateur côté frontend. |

### 8.3 Défauts du Core découverts et corrigés en route
1. `TntPermissionEvaluator` n'était créé que si `tnt.roles.aop-enabled=true` : le contexte ne démarrait pas avec l'AOP désactivé. Le bean est maintenant inconditionnel (seul l'aspect reste conditionnel).
2. `KafkaEventPublisher` envoyait en direct de façon eager : un broker injoignable bloquait l'appelant. L'envoi est différé sur `boundedElastic`.
3. `EventPublisherService.publishAll` publiait les enveloppes en mémoire et non celles persistées ; le verrou optimiste faisait échouer `updateStatus` sans bruit et toute enveloppe publiée par lot restait `PENDING` alors que Kafka avait le message. Corrigé, avec tests unitaires.
4. `tnt-trust-core` figeait `testcontainers` en 1.19.8 (docker-java API 1.32), refusé par les démons Docker actuels ; aligné sur la version gérée par le BOM.
5. Les 21 `*OutboxIntegrationTest` testaient l'outbox décommissionné le 2026-09-18 (`OutboxPollerPort`, statut `PENDING`) et ne démarraient plus. Réécrits pour la publication directe (enveloppe `PUBLISHED`, enregistrement lu sur Kafka embarqué).

### 8.4 Preuves
- Infra réelle : PostGIS, Redis et Kafka locaux ; le contexte applicatif complet démarre et la porte Liquibase `LiquibaseMasterChangelogIntegrationTest` passe.
- `mvn -o verify -Pintegration-tests` sur les 54 modules : tous les modules sont verts **sauf deux tests de `tnt-bootstrap`** (8.5).
- Frontend : suites vitest vertes, serveurs Core factices HTTP réels pour les services.

### 8.5 Points ouverts (non modifiés volontairement)
1. `SpringdocProdExposureTest.swaggerUiAndApiDocsResolveToDisabledInProd` échoue : le profil `prod` active Swagger UI et `/v3/api-docs` **volontairement** (décision du 2026-07-23, audit de conformité externe, commentée dans `application.yml`). La config n'est pas touchée ; le test attend l'inverse. À trancher : réactiver le test dès que Swagger est coupé, ou le désactiver explicitement en attendant.
2. `LayeringArchitectureTest.applicationMustNotDependOnAdapterInWeb` échoue : 1098 violations application → `adapter.in.web` contre une base gelée de ~290. Aucune n'est introduite par cette branche (les 42 fichiers gofp concernés sont identiques avant et après) ; la base est obsolète depuis `main`. Un regel de la base avait déjà été annulé par le mainteneur (Lot C-12) : la décision de regeler ou de corriger la dette lui appartient.
3. Stale mais hors périmètre : `docs/kernel-api/*` modifiés par le mainteneur, non stagés.
