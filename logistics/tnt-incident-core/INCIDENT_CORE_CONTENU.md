# tnt-incident-core — Contenu et Fonctionnement

**Auteur :** MANFOUO Braun  
**Couche :** L3 Logistics  
**Version :** 1.0.0  
**Package racine :** `com.yowyob.tiibntick.core.incident`

---

## 1. Rôle et Périmètre

`tnt-incident-core` est le moteur de gestion des **incidents de livraison** dans TiiBnTick. Un incident est tout événement non voulu interrompant, retardant ou compromettant le processus de livraison d'un colis. Ce module est **distinct de `tnt-dispute-core`** qui traite les litiges formels (perte, dommage déclaré, fraude avérée).

Le module couvre les 4 plateformes opérationnelles : **GO, FREELANCER, POINT, AGENCY**.

---

## 2. Structure du Module (124 fichiers Java, 4 XML)

```
tnt-incident-core/
├── pom.xml
└── src/main/java/com/yowyob/tiibntick/core/incident/
    ├── domain/
    │   ├── enums/          14 enums
    │   ├── model/          12 entités domaine
    │   ├── valueobject/    5 value objects
    │   ├── service/        3 services domaine (purs Java)
    │   └── event/          1 fichier — 13 événements domaine
    ├── application/
    │   ├── command/        12 commands
    │   ├── query/          2 objets query/KPI
    │   └── service/        9 services applicatifs
    ├── port/
    │   ├── inbound/        14 interfaces use case
    │   └── outbound/       16 interfaces infrastructure
    ├── adapter/
    │   ├── persistence/    1 mapper + 1 adaptateur + 7 entités R2DBC + 7 repos Spring Data
    │   ├── web/            4 controllers + 8 DTOs + 1 mapper web
    │   ├── kafka/          1 consumer + 1 publisher Kafka
    │   └── scheduler/      2 schedulers @Scheduled
    └── config/             2 classes @Configuration
src/main/resources/db/changelog/
    ├── db.changelog-master.xml
    ├── 001-incident-core-schema.xml   (6 tables)
    └── 002-incident-core-indexes.xml  (11 index de performance)
```

---

## 3. Domaine — Ce que le module modélise

### 3.1 Agrégat Racine : `Incident`

L'agrégat central. Chaque incident possède :
- `referenceCode` unique (ex: `TNT-INC-042381`)
- `IncidentType` parmi **140+ types** classifiés
- `IncidentSeverity` : NEGLIGIBLE → FATAL (6 niveaux)
- `IncidentStatus` : 19 états possibles avec machine à états validée
- `affectedParcelIds` : liste des colis impactés
- `ownBlockchainChainId` : chaîne blockchain dédiée si multi-colis

#### Machine à états

```
DETECTED → ACKNOWLEDGED → TRIAGED
                              ├─→ AUTO_RESOLVING
                              │       ├─→ REASSIGNING_DRIVER → AWAITING_HANDOVER
                              │       ├─→ REROUTING
                              │       ├─→ TRANSFERRING_TO_HUB
                              │       └─→ AUTO_RESOLUTION_FAILED
                              ├─→ PENDING_AGENCY_ASSIGNMENT → AGENCY_HANDLING
                              │       └─→ WAITING_INTERAGENCY → INTERAGENCY_IN_PROGRESS
                              └─→ ESCALATED
                                      ↓
                              RESOLVED → CLOSED
                              CANCELLED (depuis tout état non terminal)
```

### 3.2 Types d'incidents couverts (140+)

| Catégorie | Exemples |
|---|---|
| `DRIVER_DELIVERER` (22 types) | Retrait volontaire avant/après collecte, urgence médicale, arrestation, GPS spoofing, fraude multi-compte |
| `VEHICLE` (23 types) | Panne moteur, crevaison, défaillance chaîne du froid, collision, incendie, immersion |
| `CLIENT_RECIPIENT` (15 types) | Absent, injoignable, refus réception, adresse incorrecte, comportement agressif, fraude |
| `PARCEL_CARGO` (15 types) | Dommage physique, rupture chaîne du froid, perte partielle/totale, contenu illégal |
| `SYSTEM_INFRASTRUCTURE` (12 types) | Panne réseau, serveur down, Kafka failure, blockchain down, compromission compte |
| `GEOGRAPHIC` (11 types) | Route inondée, zone de conflit armé, couvre-feu, catastrophe naturelle |
| `RELAY_POINT` (7 types) | Fermeture inattendue, capacité pleine, vol interne |
| `SLA_TIME` (5 types) | Retard météo, embouteillage, douanes |
| `GO_*` (5 types) | Batterie e-biker faible, restaurant indisponible |
| `FREELANCER_*` (5 types) | Abandon en cours de livraison, fraude géolocalisation |
| `AGENCY_*` (6 types) | Flotte entière indisponible, grève interne |

### 3.3 Entités domaine

- **`IncidentEventLog`** — Journal chronologique horodaté de chaque action sur l'incident, avec preuve blockchain optionnelle
- **`IncidentParticipant`** — Acteur impliqué (driver, manager, opérateur)
- **`IncidentAssignment`** — Affectation automatique ou manuelle pour traitement
- **`IncidentEscalation`** — Historique des escalades avec acteur source/destination
- **`IncidentDriverReplacement`** — Protocole de passation de colis entre livreur original et remplaçant avec double confirmation
- **`IncidentVehicleSubstitution`** — Remplacement de véhicule (inter-agence possible)
- **`IncidentAutomationDecision`** — Décision prise par le système automatisé
- **`IncidentInterAgencyCooperation`** — Coopération entre deux agences distinctes (7 types de coopération)
- **`IncidentEvidence`** — Preuve attachée (photo, vidéo, trace GPS, signature OTP, scan document)
- **`IncidentBlockchainRecord`** — Bloc de la chaîne immuable dédiée à l'incident
- **`ParcelIncidentLink`** — Lien entre la chaîne colis et la chaîne incident (multi-colis)

### 3.4 Value Objects

- **`IncidentGeoSnapshot`** — Coordonnées au moment de l'incident + hub le plus proche + indice de risque de zone
- **`IncidentSlaImpact`** — Délai estimé, deadline révisée, minutes de retard, pénalité applicable
- **`IncidentRiskScore`** — Score global 0-1, décomposé sur 8 facteurs pondérés (réputation driver, valeur colis, zone, météo, etc.)
- **`IncidentCompensationImpact`** — Estimation du préjudice financier et couverture applicable
- **`PricingAdjustment`** — Ajustement tarifaire lié à la substitution/réassignation

---

## 4. Services Domaine (purs Java, zéro Spring)

| Service | Responsabilité |
|---|---|
| `IncidentRiskScoringService` | Calcul du risk score global sur 8 facteurs pondérés — recommande ou non la résolution automatique |
| `IncidentTriageService` | Détermine la sévérité selon le type d'incident — dérive la catégorie depuis le type |
| `IncidentBlockchainHashService` | Calcul SHA-256 des blocs — vérification d'intégrité de la chaîne |

---

## 5. Use Cases (Ports Inbound — 14 interfaces)

| Interface | Implémentée par |
|---|---|
| `IReportIncidentUseCase` | `IncidentReportingService` |
| `IReportDriverWithdrawalUseCase` | `IncidentReportingService` |
| `ITriageIncidentUseCase` | `IncidentClassificationService` |
| `IStartAutoResolutionUseCase` | `IncidentAutoResolutionService` |
| `IAssignReplacementDriverUseCase` | `IncidentAutoResolutionService` |
| `IConfirmHandoverUseCase` | `IncidentHandoverService` |
| `IStartAgencyHandlingUseCase` | `IncidentAgencyManagementService` |
| `IInterAgencyCooperationUseCase` | `IncidentInterAgencyService` |
| `IEscalateIncidentUseCase` | `IncidentEscalationService` |
| `IResolveIncidentUseCase` | `IncidentAgencyManagementService` |
| `ICloseIncidentUseCase` | `IncidentEscalationService` |
| `ICancelIncidentUseCase` | `IncidentEscalationService` |
| `IAttachEvidenceUseCase` | `IncidentEvidenceService` |
| `IQueryIncidentUseCase` | `IncidentQueryService` |

---

## 6. Ports Outbound — Interfaces infrastructure (16)

| Port | Consommé depuis |
|---|---|
| `IIncidentRepository` | `IncidentPersistenceAdapter` (PostgreSQL R2DBC) |
| `IIncidentEventLogRepository` | `IncidentPersistenceAdapter` |
| `IIncidentEvidenceRepository` | `IncidentPersistenceAdapter` |
| `IIncidentBlockchainRepository` | `IncidentPersistenceAdapter` |
| `IIncidentDriverReplacementRepository` | `IncidentPersistenceAdapter` |
| `IIncidentCooperationRepository` | `IncidentPersistenceAdapter` |
| `IDriverAvailabilityPort` | Implémenté par `tnt-resource-core` |
| `IVehicleCompatibilityPort` | Implémenté par `tnt-resource-core` |
| `IRouteOptimizerPort` | Implémenté par `tnt-route-core` |
| `IBlockchainAuditPort` | Implémenté par `tnt-trust` (module transversal) |
| `INotificationPort` | Implémenté par `tnt-notify-core` |
| `IPaymentFreezePort` | Implémenté par `tnt-billing-wallet` |
| `IActorReputationPort` | Implémenté par `tnt-actor-core` |
| `IMissionStatusPort` | Implémenté par `tnt-delivery-core` |
| `IIncidentEventPublisher` | `IncidentKafkaEventPublisher` (Kafka) |
| `IMediaStoragePort` | Implémenté par `tnt-media-core` (MinIO) |

---

## 7. Topics Kafka

### Produits par ce module

| Topic | Déclencheur |
|---|---|
| `tnt.incident.created` | Signalement d'un incident |
| `tnt.incident.status.changed` | Tout changement d'état |
| `tnt.incident.triaged` | Fin du triage + scoring |
| `tnt.incident.driver.assigned` | Livreur remplaçant assigné |
| `tnt.incident.handover.completed` | Double confirmation de passation |
| `tnt.incident.resolved` | Résolution |
| `tnt.incident.closed` | Clôture définitive |
| `tnt.incident.cancelled` | Annulation |
| `tnt.incident.escalated` | Escalade hiérarchique |
| `tnt.incident.escalated.to.dispute` | Bascule vers litige (`tnt-dispute-core`) |
| `tnt.incident.interagency.requested` | Demande de coopération inter-agences |
| `tnt.incident.interagency.completed` | Coopération terminée |

### Consommés par ce module

| Topic | Source | Réaction |
|---|---|---|
| `tnt.delivery.mission.status.changed` | `tnt-delivery-core` | Crée auto un incident SLA si `TIMED_OUT` |
| `tnt.realtime.gps.position.updated` | `tnt-realtime-core` | Crée incident si anomalie trajectoire ou arrêt prolongé |
| `tnt.realtime.geofence.triggered` | `tnt-realtime-core` | Crée incident si zone danger/interdite |

---

## 8. REST API — Endpoints (groupe `/api/v1/incidents/**`)

| Méthode | Endpoint | Use Case |
|---|---|---|
| `POST` | `/api/v1/incidents` | Signaler un incident |
| `POST` | `/api/v1/incidents/driver-withdrawal` | Retrait livreur |
| `GET` | `/api/v1/incidents/{id}` | Obtenir par ID |
| `GET` | `/api/v1/incidents/ref/{code}` | Obtenir par code de référence |
| `GET` | `/api/v1/incidents` | Liste filtrée par agence |
| `POST` | `/api/v1/incidents/{id}/triage` | Lancer le triage |
| `POST` | `/api/v1/incidents/{id}/auto-resolve` | Déclencher résolution automatique |
| `POST` | `/api/v1/incidents/{id}/escalate` | Escalader |
| `POST` | `/api/v1/incidents/{id}/resolve` | Résoudre manuellement |
| `POST` | `/api/v1/incidents/{id}/close` | Clôturer |
| `POST` | `/api/v1/incidents/{id}/cancel` | Annuler |
| `POST` | `/api/v1/incidents/{id}/evidence` | Joindre une preuve |
| `GET` | `/api/v1/incidents/{id}/timeline` | Journal chronologique |
| `GET` | `/api/v1/incidents/{id}/blockchain` | Chaîne blockchain |
| `GET` | `/api/v1/incidents/kpi` | KPI agence |
| `POST` | `/api/v1/incidents/{id}/handover/assign-driver` | Assigner livreur remplaçant |
| `POST` | `/api/v1/incidents/{id}/handover/confirm` | Confirmer passation |
| `POST` | `/api/v1/incidents/{id}/agency/handle` | Prise en charge agence |
| `POST` | `/api/v1/incidents/{id}/cooperation/request` | Demander coopération inter-agences |
| `POST` | `/api/v1/incidents/{id}/cooperation/{cid}/accept` | Accepter coopération |
| `POST` | `/api/v1/incidents/{id}/cooperation/{cid}/reject` | Rejeter coopération |
| `POST` | `/api/v1/incidents/{id}/cooperation/{cid}/complete` | Finaliser coopération |

---

## 9. Schedulers automatiques

| Scheduler | Fréquence | Rôle |
|---|---|---|
| `IncidentSlaMonitorScheduler` | Toutes les 5 min | Escalade auto les incidents dont le SLA est dépassé depuis > 0 min |
| `IncidentAutoEscalationScheduler` | Toutes les 10 min | Escalade les incidents bloqués depuis 30+ min sans résolution |

---

## 10. Mécanisme Blockchain Multi-Colis

Quand `affectedParcelIds.size() > 1` :

1. Une chaîne blockchain dédiée à l'incident est créée (`INC-{CHAIN_ID}`)
2. Les queues des chaînes colis individuelles se rattachent à la tête de la chaîne incident via `ParcelIncidentLink`
3. Chaque événement majeur (handover, coopération, clôture) produit un bloc SHA-256 dans la chaîne incident
4. À la résolution, la fin de la chaîne incident se rattache à la reprise de chaque chaîne colis individuelle
5. `ParcelIncidentLink.resume()` confirme le raccordement en inscrivant le `incidentChainTailHash`

---

## 11. Schéma de Base de Données (6 tables + 11 index)

| Table | Rôle |
|---|---|
| `tnt_incidents` | Agrégat principal |
| `tnt_incident_event_logs` | Journal ordonné des événements |
| `tnt_incident_evidences` | Preuves attachées |
| `tnt_incident_driver_replacements` | Protocole de passation livreur |
| `tnt_incident_inter_agency_cooperations` | Coopérations inter-agences |
| `tnt_incident_blockchain_records` | Chaîne blockchain locale |
| `tnt_parcel_incident_links` | Jointure colis ↔ incident (multi-colis) |

---

*MANFOUO Braun — tnt-incident-core v1.0 — TiiBnTick Core*
