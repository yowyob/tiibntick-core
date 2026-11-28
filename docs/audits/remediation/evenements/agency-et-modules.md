# Impacts pour Agency, Link, Market, Point Relais et les modules du Core

Référence : `cible-evenements-colis-livraison.md` §7.2 à §7.5 et §3. Une ligne par changement, avec son état
avant/après.

## Lot L0 — socle commun

### Comportement changé pour tous les consommateurs Kafka du Core

| Changement | Avant | Après | Nature | Lot |
|---|---|---|---|---|
| Échec d'un `@KafkaListener` | gestionnaire par défaut de spring-kafka : 10 tentatives immédiates, puis l'enregistrement est **journalisé et sauté** (perdu) ; sync et link avaient leur propre file | sur **toutes** les fabriques de conteneurs sans gestionnaire propre (celle de Spring Boot et celles des modules, coreBackend compris) : `tnt.kafka.retry.max-attempts` tentatives (4 par défaut, attente 1 s puis ×2), puis publication sur **`tnt.dlq`** avec les en-têtes d'origine et `kafka_dlt-*` (topic, partition, offset, groupe, exception) ; log `ERROR KAFKA_RECORD_DEAD_LETTERED` et compteur `yow.event.consumer.dead_lettered.total{topic,group}`. Les fabriques de **tnt-sync-core** et **tnt-link-back-core** gardent leur gestionnaire et leur file (`tnt.sync.entity-changed.dlq`, …) jusqu'au lot L8 | changement de comportement | L0 |
| Listeners « lancés sans attendre » (`.subscribe()`) | — | **inchangés** en L0 (corrigés par L2–L8) : leur échec asynchrone n'atteint toujours pas le conteneur. Règle ArchUnit gelée (`TntArchitectureTest`) : aucun **nouveau** listener ne peut appeler `.subscribe()` ni avaler son erreur (`onErrorResume(e -> Mono.empty())`, `onErrorComplete()`) ; 67 violations existantes gelées (43 `subscribe`, 24 erreurs avalées) | garde-fou | L0 |
| Anti-doublon (`IdempotentRecordInterceptor`, `event_bus.processed_events`) | inchangé | inchangé ; un enregistrement en échec n'est jamais marqué traité, une nouvelle tentative repasse par le filtre | aucun | L0 |

### Socle réutilisable (couches basses)

| Élément | Où | Usage |
|---|---|---|
| `TntAggregateEvent<S>` + `TntAggregateType` + `TntEventActor` | `tnt-common-core`, `com.yowyob.tiibntick.common.kafka` | enveloppe commune ; `TntAggregateType.eventTypeFor("PICKED_UP")` → `DeliveryPickedUp` (règle mécanique §1.0) ; `topic()` |
| `TntEventJson` | idem | sérialisation (dates ISO-8601 UTC, champs nuls présents) |
| `TntEventMasking` (bean `tntEventMasking`) | idem ; bean déclaré par `tnt-bootstrap` | `address(...)` → `MaskedAddress` (quartier, ville, pays, coordonnées arrondies), `displayName("Awa Ndiaye")` → `Awa N.`, `amount(BigDecimal)` → chaîne décimale |
| `ReactiveListenerSupport` (bean `reactiveListenerSupport`) | idem | `listenerSupport.await(mono)` dans un `@KafkaListener` : attend la fin, délai `tnt.kafka.listener.timeout` (30 s), toute erreur remonte au conteneur |
| `TntEventSchemas`, `TntPiiSentinels` | idem | chemins des schémas JSON (`kafka/schemas/v1/`) ; valeurs sentinelles des tests de fuite de données personnelles |
| `DomainEventEnvelope.Builder.eventId(String)` | `yow-event-kernel` | fixe l'id d'événement = id d'enveloppe = en-tête `X-Yow-Event-Id` |
| `TntTopics.PARCEL_EVENTS`, `INCIDENT_EVENTS`, `DISPUTE_EVENTS`, `ANNOUNCEMENT_EVENTS` (+ `DELIVERY_EVENTS`, `DLQ`) | `tnt-common-core` | noms des topics |

Publication d'un événement d'agrégat par l'outbox (modèle à suivre par L2–L4, voir `TntApplicationFullStackIT.envelopeOf`) :
`DomainEventEnvelope.wrap().eventId(e.eventId().toString()).correlationId(<UUID de corrélation, sinon e.eventId()>).causationId(e.causationId())
.eventType(e.eventType()).aggregateId(e.aggregateId()).aggregateType(e.aggregateType().name()).tenantId(e.tenantId())
.solutionCode("TNT").payload(TntEventJson.write(e)).schemaVersion(e.schemaVersion()).kafkaTopic(e.topic())` — ne
jamais passer de clé explicite (clé = `aggregateId`). L'id de corrélation est obligatoire côté outbox
(`event_bus.domain_event_envelopes.correlation_id` : `NOT NULL`, `VARCHAR(36)`) : un UUID, jamais une chaîne libre.

### Agency

Aucun changement de topic consommé en L0 (`application.yml` `tnt.kafka.topics.consumed.*` inchangé, bascule en L8).
Les consommateurs Agency (`DeliveryStatusConsumer`, `WalletPayoutConsumer`, `InventoryHubConsumer`,
`ActorKycConsumer`) reçoivent le gestionnaire commun : une exception **synchrone** du listener mène désormais à
`tnt.dlq` après 4 tentatives.

### Link, Market, Point Relais

Link : fabrique `linkKafkaListenerContainerFactory` inchangée (gestionnaire propre). Market : listeners concernés par
le gestionnaire commun comme ci-dessus. Point Relais : rien en L0 ; les topics `tnt.parcel.events` et
`tnt.announcement.events` existent mais restent vides jusqu'au lot L2.

## Lot L1 `evt-seuils-plateforme` (seuils de livraison et de garde)

Référence : [spécification d'administration §5.1.1](../../../plateforme-administration/specifications.md),
décisions QO-8, QO-18, QO-29, QO-30 du [registre](../../../decisions/registre-decisions.md).

### Pour le backend Agency (BFF `backend/tnt-agency`)

1. **Nouvelles routes de réglage par organisation** (id d'organisation Kernel de l'agence) :
   `GET|PUT|DELETE /api/v1/organizations/{organizationId}/settings/delivery-thresholds` et
   `GET .../delivery-thresholds/effective`. Le `PUT` remplace toute la surcharge : un champ absent revient à la
   valeur de la plateforme. Création sans `version`, remplacement avec la `version` lue (sinon 409).
2. **Nouvelles permissions** `organization:delivery-settings:read` et `organization:delivery-settings:write`
   (portée organisation, assignables). Par défaut : `write` pour `AGENCY_MANAGER` et `ORG_ADMIN`, `read` pour
   `BRANCH_MANAGER`, `PERMANENT_DELIVERER`, `RELAY_OPERATOR`, `AGENCY_HUB_OPERATOR`. Le responsable de l'agence
   donne `write` à qui il veut via ses rôles d'organisation : l'écran de gestion des rôles de l'agence doit proposer
   ces deux permissions.
3. Seules les organisations qui ont au moins une agence au registre (`organization.agencies` du Core) peuvent
   surcharger (422 `ORGANIZATION_NOT_AGENCY` sinon).
4. Les valeurs effectives sont renvoyées avec leur source (`PLATFORM` ou `ORGANIZATION`) : à afficher pour que le
   responsable voie ce qu'il a changé.

### Pour les modules du Core

- **delivery-core (lot L2)** : lire les seuils via `ResolveDeliveryThresholdsUseCase` (administration-core, couche
  inférieure) et invalider son cache à la réception de `DeliveryThresholdsChanged` sur `tnt.administration.events`.
  Attention : `Delivery.agencyId` porte l'id du registre `agency_org.agencies`, pas l'id d'organisation Kernel qui
  indexe la surcharge ; il faut le convertir via `kernel_organization_id` avant la résolution.
- **Échéance de garde (QO-30)** : `relayStorageDeadline(relayOwnerOrgId, independentDays, depositedAt,
  requestedStorageDays)` = dépôt + min(demande du client, durée du relais), rappel avant la fin. L'ordonnanceur est
  livré par L2. Le défaut `requestedStorageDays = 3` codé en dur dans GOFP (`DeliveryStatusApplicationService`) reste à
  traiter par L2 / GOFP.
- **Relais indépendants (QO-29)** : le module qui enregistre la durée de garde d'un relais indépendant doit appeler
  `ManageDeliveryThresholdsUseCase.checkIndependentRelayStorageDays(days)` (422 `RELAY_STORAGE_DAYS_OUT_OF_BOUNDS`) ;
  bornes 3 à 30 jours par défaut, réglables par l'administrateur (D-130).
  Une valeur déjà enregistrée hors bornes est ramenée dans les bornes à la résolution (source `RELAY`).
- **`relayPointMaxStorageHours`** retiré du DTO des options plateforme : voir le journal technique.

## Lot L7 — consommateurs bloquants (2026-11-26)

- **Agency** : `WalletPayoutConsumer` et `ActorKycConsumer` n'acquittent plus un message en échec ; après 4 tentatives,
  il part dans `tnt.dlq` (groupes `tnt-agency-erp-billing-consumer`, `tnt-agency-erp-actor-consumer`). Un paiement
  qui n'est pas une commission d'agence reste ignoré. `HubParcelExpiryJob` traite ses colis expirés dans la fenêtre
  de son verrou (plus de double traitement entre deux instances).
- **Market** : les transitions de commande pilotées par Kafka sont rejouables sans erreur (transition déjà atteinte =
  sans effet) ; une transition illégale part en file morte au lieu d'être logguée.
- **Go-Freelancer, acteurs, livraison, notifications** : mêmes garanties (voir journal technique).
- Aucun changement de contrat de message ni de route HTTP.

## Lot L2 `evt-etats-colis-livraison`

Les changements visibles par le Mobile et le BFF (valeurs de statut, routes, suivi public) sont dans
[mobile-bff.md](mobile-bff.md) ; ils valent aussi pour les BFF Agency, Link et Point Relais.

### Agency

| Changement | Avant | Après | Lot |
|---|---|---|---|
| `DeliveryStatusSyncMapper` (assignment-core) | `CREATED`, `AT_RELAY_POINT`, `TIMED_OUT`, `SLA_BREACHED` | accepte les deux noms (`AT_RELAY_POINT` lu dans les enregistrements déjà en file, `AT_RELAY_HUB`) ; `ASSIGNED` → `PENDING` comme `CREATED` ; `OUT_FOR_DELIVERY`, `DELIVERY_ATTEMPT_FAILED`, `INCIDENT_REPORTED`, `INCIDENT_HANDOVER` → `IN_TRANSIT` | L2 |
| Seuils de livraison d'une livraison d'agence | — | `AgencyDeliveryThresholdsAdapter` (agency-org-core) convertit `Delivery.agencyId` (id du registre `agency_org.agencies`) en `kernel_organization_id` pour lire la surcharge de l'agence ; un relais d'agence (`agency_relay_hubs.core_hub_id`) prend les seuils de l'organisation de son agence (sa colonne `retention_delay_hours` n'est pas lue par la garde) | L2 |
| Nouvelle dépendance Maven | — | `tnt-agency-org-core` → `tnt-delivery-core` (couche inférieure, implémente ses ports) | L2 |

### Link

| Changement | Avant | Après | Lot |
|---|---|---|---|
| `GET /api/v1/platform/link/parcels/track/{code}` | `deliveryId`, `senderId`, `deliveryPersonId`, adresses complètes, coordonnées exactes, nom et téléphone du destinataire, position GPS exacte | masqué (D-137) : `trackingCode`, `status`, `parcelStatus`, quartier et ville de départ et d'arrivée, coordonnées et position arrondies (2 décimales, QO-17), dates ; **429** au-delà de 30 requêtes par minute et par IP (le BFF doit transmettre `X-Forwarded-For`) | **rupture** |
| Code de suivi | celui de la livraison | celui du colis (D-136) ; les anciens codes restent valables | changé |

### Point Relais

- La durée de garde effective d'un dépôt = min(durée demandée par le client, garde du relais) (QO-30) ; elle est
  renvoyée par le cœur au dépôt et utilisée par le rappel et l'échéance (D12). Un relais indépendant règle sa garde
  (`storageDays`) dans les bornes de l'administrateur (D-130).
- Un relais révoqué : les livraisons en garde sont listées par `DeliveryRepository.findAtRelayHub(hubId)` (point
  d'accroche du lot de révocation ; le retour ou le transfert est décidé par ce lot).

### Modules du Core

| Module | Changement | Lot |
|---|---|---|
| GOFP (matching) | `MatchingUseCase` écoute **`tnt.announcement.events`** (`AnnouncementPublished`, clé = id de l'annonce) ; il écoutait `tnt.delivery.announcement.published` **et** `gofp.announcement.published`, que delivery-core alimentait tous les deux : chaque annonce était appariée deux fois. `gofp.announcement.published` n'a plus de producteur (topic encore déclaré, retiré en L8) ; `EventPublisher.publishAnnouncementPublished` supprimé | L2 |
| incident-core | `IMissionStatusPort` : nouvelles méthodes (signalement, blocage, passation, échec sans issue) ; delivery-core n'écoute plus les topics d'incident (`IncidentEventConsumer` supprimé), l'incident pilote la livraison en direct | L2 |
| trust-core | `ParcelLifecycleState` supprimé : le jumeau numérique lit `ParcelStatus` et le code du colis dans delivery-core (repli sur la chaîne de garde pour un colis inconnu) | L2 |
| realtime-core | le topic de suivi par code (`/topic/tracking/{code}`) ne reçoit plus que le code, les statuts et l'instant (plus d'id de tenant, d'agence ni de livreur) ; le code est celui du colis | L2 |
| media, dispute, incident | reçoivent le code de suivi tel que la livraison le donne : c'est désormais celui du colis ; aucune modification nécessaire | L2 |
| sync-core, sales, billing (anciens topics) | inchangés jusqu'en L8 : `tnt.delivery.mission.*` et `tnt.delivery.package.*` continuent d'être écrits (double écriture marquée `TODO(L8)`). Les événements hérités sans topic dédié (affectation, annulation, pause/reprise d'incident) ne sont **plus** écrits dans `tnt.delivery.events`, qui ne porte que le nouveau format (§3.9) | L2 |

## Lot L3 `evt-incidents`

Les changements visibles par le Mobile et le BFF (déclaration, champs, routes, alertes) sont dans
[mobile-bff.md](mobile-bff.md#lot-l3-evt-incidents-incidents-déclarés-par-le-livreur--alertes-de-livraison) ; ils valent
aussi pour les BFF Agency, Link et Point Relais.

### Agency

| Changement | Avant | Après | Lot |
|---|---|---|---|
| Incident bloquant sur une livraison d'agence | moteur automatique, puis l'agence | directement `PENDING_AGENCY_ASSIGNMENT` (I5) : l'agence prend en charge (`POST /api/v1/incidents/{id}/agency/handle`, inchangé), propose une résolution (`propose-resolution`, nouveau) que le livreur confirme ou conteste ; le moteur ne choisit pas les livreurs d'une agence | L3 |
| Résolution par l'agence | `resolutionMode` | + `deliveryOutcome` (obligatoire si bloquant : `RESUMED`, `FAILED`, `DISPUTE`) et `responsibleParty` ; `FAILED` met la livraison en échec (D20) | L3 |
| Coopération inter-agences | — | refus → retour à `AGENCY_HANDLING` (I21) ; fin → `RESOLVED` (`INTERAGENCY_COOPERATION`, livraison reprise) | L3 |
| `IncidentCoreClient` (compliance-core, signalement d'anomalie d'une mission) | rôle `DELIVERER` (inexistant : chaque signalement échouait en 400, erreur avalée) ; types `DRIVER_PARCEL_THEFT_ATTEMPT`, `SLA_BREACH_TRAFFIC_DELAY`, `AGENCY_SYSTEM_DOWN` ; l'agence déclarait à la place du livreur absent | rôle `AGENCY_DRIVER` ; vol → `PARCEL_TOTAL_LOSS` (le livreur ne s'accuse pas, QO-12) ; retard et anomalie inconnue → **aucun incident** (le retard est une alerte du cœur) ; sans livreur → aucun incident. Test `IncidentCoreClientTest` (MockWebServer) | L3 |
| Lecture des statuts d'incident | `DETECTED` possible | n'existe plus ; nouveaux statuts lus tels quels | L3 |

### Link, Market, Point Relais

- Aucun appel direct aux incidents. Les retards, pertes de GPS et zones sont des **alertes** de la livraison
  (`DeliveryAlertRaised` / `DeliveryAlertCleared` sur `tnt.delivery.events`, champ `activeAlerts`), plus jamais des
  incidents.
- Un incident `TRANSFERRING_TO_HUB` signifie que le livreur apporte le colis au relais le plus proche (5 km au plus) ;
  le relais reçoit un dépôt ordinaire.

### Modules du Core

| Module | Changement | Lot |
|---|---|---|
| incident-core | n'écoute plus `tnt.realtime.gps.position.updated`, `tnt.realtime.geofence.triggered` ni les statuts de mission (`IncidentEventConsumer` supprimé) ; publie `tnt.incident.events` (anciens topics écrits jusqu'en L8) | L3 |
| delivery-core | `DeliveryWatchService`, `DeliveryWatchConsumer` (GPS, géofence), `DeliveryLateScheduler` ; seuils d'alerte lus dans l'administration (`DeliveryThresholdsPort.Thresholds.alerts`) ; `MissionStatusPortAdapter.isAssignedCourier`, `courierHoldsParcel` | L3 |
| GOFP | `CoreDeliveryAlertConsumer` (groupe `tnt-gofp-delivery-alerts`, `tnt.delivery.events`) : `DeliveryAlertRaised(DELIVERY_LATE)` → notification au client et au livreur | L3 |
| billing-wallet | gel / dégel du paiement appelé par l'incident : gel quand l'incident est bloquant, dégel à la résolution (sauf `DISPUTE`) ou à l'annulation | L3 |
| dispute-core | inchangé : `tnt.incident.escalated.to.dispute` toujours écrit ; l'incident passe `RESOLVED` (`deliveryOutcome = DISPUTE`) à l'escalade | L3 |

## Lot L4 `evt-litiges`

Les changements visibles par le Mobile et le BFF (ouverture, jugement, indemnisation, clôture) sont dans
[mobile-bff.md](mobile-bff.md#lot-l4-evt-litiges-litiges--machine-détats-effets-sur-le-colis-indemnisation) ; ils
valent aussi pour les BFF Agency, Link et Point Relais.

### Agency

| Changement | Avant | Après | Lot |
|---|---|---|---|
| Ouverture par `DisputeCoreClient` (compliance-core) | garde par mission | inchangée pour un `AGENCY_MANAGER` (vérification par mission) ; un autre type de demandeur doit être partie au colis du Core | L4 |
| Colis d'une mission d'agence en litige | statut inchangé | `UNDER_DISPUTE` quand la mission correspond à une livraison du Core ; livraison gelée sauf retard | L4 |
| Lecture du détail (`DisputeDetail`) | — | trois champs ajoutés en fin de réponse, ignorés tant que le client ne les lit pas | L4 |

### Link, Market, Point Relais

- Point Relais : l'opérateur du relais qui détient le colis (ou du relais de destination) peut ouvrir un litige
  (`HUB_OPERATOR`) ; le colis reste au relais, statut `UNDER_DISPUTE`, jusqu'à l'issue.
- Market : `MarketOrderApplicationService` ouvre un `MARKET_CLAIM` avec l'id de commande comme mission ; sans colis du
  Core, aucun effet sur un colis.
- Link : aucun appel direct.

### Modules du Core

| Module | Changement | Lot |
|---|---|---|
| dispute-core | `DisputeStateMachine` (LT1–LT12) ; `IDisputeParcelPort`, `IDisputeCompensationPort`, `IDisputeSystemUseCase` ; `IDeliveryStatusPort` et `IBillingCompensationPort` supprimés ; `tnt.dispute.events` par l'outbox ; persistance corrigée (mises à jour, enfants, escalades) ; changeset `evt-l4` | L4 |
| delivery-core | `ParcelDisputeUseCase.resolveParcelId`, `disputeParties`, ouverture sans indicateur de délai (délai vérifié par `DisputeFilingWindowPort`) | L4 |
| administration-core | `ResolveDisputeFilingWindowUseCase` (option `disputeFilingWindowDays` du tenant, sinon du tenant système, sinon 7 jours) | L4 |
| billing-wallet | aucun changement ; le fonds d'indemnisation Kernel (adapter de `IDisputeCompensationPort`) arrive au lot L5 | L5 |
| incident-core | inchangé ; l'ouverture automatique d'un litige depuis `tnt.incident.escalated.to.dispute` fonctionne désormais (elle échouait sur `@RequirePermission`) et n'ouvre qu'un litige par incident | L4 |

## Lot L5 `evt-paiement-sequestre`

### Modules du Core

| Module | Changement | Lot |
|---|---|---|
| billing-wallet | `IKernelSettlementPort` / `KernelSettlementAdapter` (séquestres, encaissements, fonds) ; `CompensationService` et miroir `wallet_compensations` ; implémente `IDisputeCompensationPort` (dépend de dispute-core) ; publie `tnt.billing.compensation.paid` ; listener `compensation.initiated` supprimé | L5 |
| dispute-core | port d'indemnisation résolu par `ObjectProvider` (repli 503) ; `compensation.paid` sur un litige déjà indemnisé acquitté sans changement | L5 |
| bootstrap | client Kernel `kernelSettlementWebClient` (compte de service, sans rejeu) et ses instances Resilience4j | L5 |
| billing-wallet (partie 2) | `IEscrowUseCase`/`EscrowService`, `ICashCollectionUseCase`/`CashCollectionService`, `PaymentLifecycleService`, consommateurs `tnt.delivery.events` et `tnt.dispute.events` ; miroirs `wallet_payment_intents`, `wallet_escrows`, `wallet_cash_collections` ; `wallet_holds` et `WalletHold*` supprimés ; `SettlementMethod.ESCROW` ; gel d'incident sur le séquestre | L5 |
| delivery-core | `Delivery.create` avec id imposé ; `SelectAnnouncementResponseCommand.deliveryId` ; D7 `details.atDestination` | L5 |
| GOFP | sélection payée, paiement par séquestre, reversement espèces ; receive-code supprimé | L5 |
| bootstrap | propriété `tnt.payments.platform-wallet-id` | L5 |
| billing-wallet (partie 3) | dettes envers le fonds (`wallet_debts`, `DebtService`, `/api/v1/billing/debts`), retours payés par le fonds (`PaymentIntent.Mode.FUND`, `payReturnCourier`), garantie de retour (`RETURN_GUARANTEE`, `KernelReturnGuaranteeAdapter` implémente `ReturnGuaranteePort` de delivery-core : nouvelle dépendance Maven billing-wallet → delivery-core) | L5 |
| delivery-core | `ReturnGuaranteePort` désormais implémenté (plus de `WALLET_GUARANTEE_NOT_CONNECTED` quand le wallet est présent) ; nouvelle raison `GUARANTEE_NOT_CONFIRMED` | L5 |
| GOFP (partie 3) | second code de garantie pour une livraison vers un relais ; mode `FUND` imposé pour un retour à la charge de la plateforme ou du livreur | L5 |
| accounting-core | écoute de `tnt.billing.wallet.split_executed` supprimée (topic supprimé) | L5 |
| bootstrap | déclaration du topic `split_executed` supprimée | L5 |
| Agency | aucun changement d'appel ; une agence fautive dans un litige indemnisé reçoit une dette (`AGENCY`) que l'administrateur lui attribue, à rembourser par `/api/v1/billing/debts/{id}/repay` au nom de l'organisation | L5 |

### Agency

Les livraisons d'agence sans annonce n'ont pas de séquestre (QO-28) : aucun changement pour le BFF Agency. Le
consommateur `tnt.delivery.events` du wallet ignore une livraison sans intention de paiement.

## Lot L6 `evt-comptabilite-kernel`

| Module | Effet | Lot |
|---|---|---|
| accounting-core | écritures dans le Kernel (`KernelBookkeepingPort`, `KernelBookkeepingService`, tables `kernel_*_links`, consommateurs bloquants `commission.calculated`, `compensation.paid`, `invoice.paid`) ; écoutes `payment.confirmed` et `tnt.delivery.freelancer_org.assigned` supprimées ; journal local en lecture seule (`POST` → 410) ; administration `/api/v1/accounting/kernel-entries` | L6 |
| billing-wallet | `compensation.paid` aussi pour un retour payé par le fonds (`reason`, `returnDeliveryId`) ; commission reversée en espèces republiée `SETTLED` | L6 |
| dispute-core | ignore `compensation.paid` de motif `RETURN_COST` | L6 |
| billing-invoice | `InvoicePaid` porte `issuerOrgType`/`issuerOrgId` | L6 |
| bootstrap | propriété `tnt.accounting.kernel.platform-organization-id` (`TNT_ACCOUNTING_PLATFORM_ORGANIZATION_ID`) | L6 |
| Agency | une facture payée émise par une agence (`issuerOrgId`) est écrite dans le livre Kernel de cette agence ; aucun appel à changer. Une écriture manuelle dans le journal local (`POST /api/accounting/journal-entries`) répond désormais 410 | L6 |

## Lot L8 `evt-branchement-total` (2026-11-27)

### Agency

| Sujet | Avant | Après | Lot |
|---|---|---|---|
| `DeliveryStatusConsumer` (assignment) | `tnt.delivery.mission.status.changed` / `.created` | transitions de `tnt.delivery.events` ; indices masqués seulement (quartier, ville, nom affiché « Prénom N. », relais) | L8 |
| `InventoryHubConsumer` (org) | `tnt.inventory.hub.package.deposited` / `.pickedup` | `tnt.parcel.events` : entrée en `AT_RELAY_HUB` = dépôt au relais de la nouvelle garde, sortie = retrait du relais de la garde précédente | L8 |
| Expiration des colis en garde | `HubParcelExpiryJob` (planificateur horaire, échéance propre à l'agence) | **supprimé** : `AT_RELAY_HUB → RETURN_REQUESTED` avec `returnCause = NOT_COLLECTED_AT_RELAY` (échéance commune, réglée par l'administration) marque le colis `EXPIRED` (`HubParcelExpiryService.expireDeposited`) ; rejeu sans effet. Endpoint manuel `POST /api/v1/tenants/{tenantId}/agency-registry/hubs/expired/process` conservé | L8 |
| Propriétés | `tnt.kafka.topics.consumed.core-mission-created|core-mission-status|core-package-delivered|core-inventory-deposited|core-inventory-pickedup`, `tnt.agency.hub.expiry-job.*` (`TNT_AGENCY_HUB_EXPIRY_JOB_ENABLED|CRON|TENANT_ID`) | **supprimées** (à retirer des fichiers de déploiement) | L8 |
| Dépôt fait directement dans l'inventaire | dépôt REST / `HubParcelService` sans transition D7 de la livraison publiait `tnt.inventory.hub.package.deposited` | **aucun événement** : l'état du colis du Core fait foi ; passer par la transition D7 pour que les autres modules le voient | limite |
| Compliance (`DisputeCorePort`, `IncidentCorePort`, `ClaimEventPublisherPort`) | paramètres `missionId` | `deliveryId` dans les ports ; corps HTTP vers le Core et charge Kafka `missionId` **inchangés** | L8 |

### Link, Market, Point Relais

| Sujet | Effet | Lot |
|---|---|---|
| Market | commandes avancées sur les transitions de `tnt.delivery.events` ; publie chaque événement sur sa constante `tnt.market.*` (plus de nom concaténé ; même nom qu'avant) ; schéma corrigé (changesets `008-align-*` : 82 colonnes manquantes, dont `market_orders.mission_id`) | L8 |
| Point Relais / GOFP | notification « colis au relais » sur `AT_RELAY_HUB` seulement (`AT_RELAY_POINT` n'est plus accepté), gabarit `notify.gofp.delivery.at_relay_hub` | L8 |
| Lecteurs externes des anciens topics | supprimés (liste : journal technique L8) ; lire les topics d'agrégat | L8 |

### Modules du Core

| Module | Effet | Lot |
|---|---|---|
| sync, realtime, billing-invoice, sales, actor, tp, gofp, notify, dispute | consommateurs basculés sur les topics d'agrégat (table : journal technique L8) | L8 |
| tp | fidélité : un client sans profil client tp-core (Go-Freelancer) ne part plus en `tnt.dlq` | L8 |
| tous | `TntTopics` : 101 noms (22 morts retirés, 16 constantes créées) ; ports renommés `mission` → `delivery` (`IDeliveryStatusPort`, `IDeliveryAssignmentPort`, `deliveryId`) | L8 |

## QO-11 / D-153 : livreur remplacé, payé par le fonds au prorata (2026-11-27)

| Public | Effet |
|---|---|
| Agency, Link, Market, Point Relais (lecteurs de `tnt.delivery.events`) | `details` des reprises avec changement de livreur : `replacedCourierId`, `replacementCourierId`, `replacedLegKm`, `remainingKm` (ajout, champs facultatifs) |
| Agency (lecteur de `compensation.paid` via sa comptabilité) | nouvelle raison `COURIER_REPLACEMENT` (avec `deliveryId`) |
| delivery-core | colonnes `leg_start_latitude` / `leg_start_longitude` (`evt-qo11-001-delivery-leg-start`) ; `Delivery.resumeAfterIncident(…, handoverPoint)` |
| incident-core | `IDeliveryStatusPort.resumeAfterHandover(…, lat, lng)` (méthode par défaut) appelé après une remise confirmée |
| billing-wallet | table `billing.wallet_courier_legs` (`evt-qo11-001-wallet-courier-legs`), `ReplacementProrataPolicy`, `ICompensationUseCase.payReplacementCourier`, dette `COURIER_REPLACEMENT` |
| accounting-core | `compensation.paid` `COURIER_REPLACEMENT` → écriture `REP-<livraison>` (« Livreur remplaçant ») |
| dispute-core | ignore `compensation.paid` `COURIER_REPLACEMENT` (pas une indemnité de litige) |

