# Impact de tnt-incident-core sur les modules Core existants

**Auteur :** MANFOUO Braun  
**Version :** 1.0  
**Portée :** Tous les modules Core existants sauf `tnt-bootstrap`

---

## Principe général

`tnt-incident-core` suit le **pattern hexagonal strict** : il interagit avec les autres modules uniquement via des **ports outbound** (interfaces) dont les implémentations réelles sont fournies par les modules concernés lors de l'assemblage dans `tnt-bootstrap`. Les modules existants **ne dépendent pas** de `tnt-incident-core` en import Maven ; c'est `tnt-incident-core` qui dépend d'eux via des ports.

---

## 1. tnt-delivery-core

### Rôle actuel
Gère les missions, manifestes, packages, dépôts hub.

### Impact — AJOUTS NÉCESSAIRES

#### a) Publication d'un événement enrichi `MissionStatusChangedEvent`

Le consumer Kafka `IncidentEventConsumer` écoute `tnt.delivery.mission.status.changed`. Il faut que cet événement contienne les champs suivants si non déjà présents :

```java
// Dans MissionStatusChangedEvent ou un événement dédié
String newStatus;       // doit inclure "TIMED_OUT", "SLA_BREACHED"
UUID tenantId;
UUID agencyId;
UUID missionId;
String platform;        // GO | FREELANCER | POINT | AGENCY
List<UUID> parcelIds;
```

#### b) Implémentation de `IMissionStatusPort`

`tnt-delivery-core` doit fournir une implémentation du port `IMissionStatusPort` (défini dans `tnt-incident-core`) lors de l'assemblage :

```java
// À créer dans tnt-delivery-core — adapter/incident/
@Component
public class MissionStatusPortAdapter implements IMissionStatusPort {

    @Override
    public Mono<Void> pauseMission(UUID missionId, UUID incidentId) {
        // → changer le statut Mission à PAUSED_BY_INCIDENT
        // → persister l'incidentId dans Mission pour traçabilité
    }

    @Override
    public Mono<Void> resumeMission(UUID missionId, UUID newDriverId, UUID newVehicleId) {
        // → réassigner le driver et vehicle dans Mission
        // → changer le statut de PAUSED_BY_INCIDENT à IN_TRANSIT
    }

    @Override
    public Mono<MissionSnapshot> getMissionSnapshot(UUID missionId) {
        // → lire Mission et retourner les champs requis
    }
}
```

#### c) Nouveau champ `pausedByIncidentId` dans `Mission`

```java
// Champ à ajouter dans Mission domain model
UUID pausedByIncidentId;     // nullable — rempli quand incident bloque la mission
MissionStatus previousStatus; // pour restaurer après résolution
```

#### d) Liquibase — colonnes à ajouter sur `tnt_missions`

```xml
<column name="paused_by_incident_id" type="UUID"/>
<column name="previous_status_before_pause" type="VARCHAR(40)"/>
```

#### e) Kafka — Consommer les topics incident

`tnt-delivery-core` doit écouter `tnt.incident.resolved` et `tnt.incident.closed` pour réactiver automatiquement les missions bloquées si la logique de résumption n'est pas pilotée par l'incident.

---

## 2. tnt-realtime-core

### Rôle actuel
Tracking GPS, Kalman, geofencing, détection anomalies.

### Impact — AJOUTS NÉCESSAIRES

#### a) Enrichissement du payload `GpsPositionUpdatedEvent`

Le consumer incident écoute `tnt.realtime.gps.position.updated`. Ajouter :

```json
{
  "missionId": "...",
  "tenantId": "...",
  "agencyId": "...",
  "driverId": "...",
  "lat": 3.848,
  "lng": 11.502,
  "trajectoryAnomaly": true,
  "prolongedStop": false,
  "stopDurationMinutes": 0
}
```

Les champs `trajectoryAnomaly` et `prolongedStop` doivent être calculés dans `tnt-realtime-core` (via l'algorithme de Kalman + seuils configurables) avant publication.

#### b) Enrichissement du payload `GeofenceTriggeredEvent`

Ajouter le champ `zoneType` parmi : `DANGER_ZONE`, `RESTRICTED_ZONE`, `NORMAL`.

---

## 3. tnt-actor-core

### Rôle actuel
Profils acteurs, réputation, KYC, localisation.

### Impact — AJOUTS NÉCESSAIRES

#### a) Implémentation de `IActorReputationPort`

```java
@Component
public class ActorReputationPortAdapter implements IActorReputationPort {

    @Override
    public Mono<Void> decreaseReputation(UUID actorId, double points, String reason) {
        // Appeler le service de réputation existant
    }

    @Override
    public Mono<Void> flagForFraud(UUID actorId, UUID incidentId, String evidence) {
        // Passer le compte en KycStatus.FLAGGED ou similaire
        // Notifier l'équipe de modération
    }

    @Override
    public Mono<Double> getReputationScore(UUID actorId) { ... }

    @Override
    public Mono<Integer> getIncidentHistoryCount(UUID actorId) {
        // Requête sur les incidents où cet acteur était driver
    }
}
```

#### b) Nouveau champ `incidentHistoryCount` dans `DelivererProfile`

Compteur d'incidents impliquant ce livreur (mis à jour à chaque fermeture d'incident).

---

## 4. tnt-resource-core (tnt-fleet-core / vehicle + staff)

### Rôle actuel
Gestion flotte véhicules, personnel, contrats.

### Impact — AJOUTS NÉCESSAIRES

#### a) Implémentation de `IDriverAvailabilityPort`

```java
@Component
public class DriverAvailabilityPortAdapter implements IDriverAvailabilityPort {

    @Override
    public Flux<DriverCandidate> findEligibleReplacementDrivers(
            UUID tenantId, UUID agencyId, double lat, double lng,
            double requiredCapacityKg, String vehicleCategory) {
        // Requête : drivers AVAILABLE, agencyId optionnel
        //   → filtrer par capacité véhicule >= requiredCapacityKg
        //   → trier par distance depuis lat/lng (PostGIS ST_Distance)
        //   → retourner les N premiers candidats
    }

    @Override
    public Mono<Boolean> isDriverAvailable(UUID driverId) { ... }
}
```

#### b) Implémentation de `IVehicleCompatibilityPort`

```java
@Component
public class VehicleCompatibilityPortAdapter implements IVehicleCompatibilityPort {

    @Override
    public Mono<VehicleInfo> getVehicleInfo(UUID vehicleId) {
        // Retourner : catégorie, capacité, réfrigération, agencyId
    }

    @Override
    public Mono<Boolean> isVehicleAvailable(UUID vehicleId) { ... }
}
```

#### c) Nouveau statut véhicule : `IN_INCIDENT_SUBSTITUTION`

```java
// Dans l'enum VehicleStatus
IN_INCIDENT_SUBSTITUTION  // véhicule prêté temporairement à une autre agence
```

---

## 5. tnt-route-core

### Rôle actuel
Optimisation VRP/CVRP, calcul ETA, graphe routier.

### Impact — AJOUTS NÉCESSAIRES

#### a) Implémentation de `IRouteOptimizerPort`

```java
@Component
public class IncidentRouteOptimizerAdapter implements IRouteOptimizerPort {

    @Override
    public Mono<NearestHubResult> findNearestHub(double lat, double lng, UUID tenantId) {
        // PostGIS : SELECT ... ORDER BY ST_Distance(hub.geom, ST_Point(lng, lat)) LIMIT 1
    }

    @Override
    public Mono<RerouteResult> rerouteFromCurrentPosition(UUID missionId, double lat, double lng) {
        // Recalculer la route depuis position actuelle → destination finale
    }
}
```

---

## 6. tnt-notify-core

### Rôle actuel
Push notifications, SMS, WhatsApp, email.

### Impact — AJOUTS NÉCESSAIRES

#### a) Implémentation de `INotificationPort`

```java
@Component
public class IncidentNotificationPortAdapter implements INotificationPort {

    @Override
    public Mono<Void> notifyActor(UUID actorId, String title, String body,
                                   String type, UUID incidentId) {
        // Créer une NotificationRequest et déléguer au service existant
    }

    @Override
    public Mono<Void> notifyActors(List<UUID> actorIds, String title, String body,
                                    String type, UUID incidentId) { ... }

    @Override
    public Mono<Void> notifyAgency(UUID agencyId, String title, String body,
                                    String type, UUID incidentId) {
        // Notifier tous les managers actifs de l'agence
    }
}
```

#### b) Nouveaux types de notification

Ajouter dans l'enum `NotificationType` (si existant) :

```
INCIDENT_CREATED, INCIDENT_ESCALATED, INCIDENT_DRIVER_PROPOSAL,
HANDOVER_CONFIRMED, DRIVER_MISSION_ASSIGNED, INTERAGENCY_COOP_REQUESTED,
INTERAGENCY_COOP_UPDATED, AGENCY_HANDLING_STARTED, INCIDENT_AUTO_FAILED
```

---

## 7. tnt-media-core

### Rôle actuel
Upload/download MinIO, gestion des fichiers médias.

### Impact — AJOUTS NÉCESSAIRES

#### a) Implémentation de `IMediaStoragePort`

```java
@Component
public class IncidentMediaStorageAdapter implements IMediaStoragePort {

    @Override
    public Mono<Void> archiveIncidentEvidence(UUID incidentId) {
        // Déplacer les fichiers du bucket temporaire vers le bucket d'archives
        // avec préfixe : incidents/{incidentId}/evidences/
    }
}
```

#### b) Nouveau bucket MinIO : `tnt-incident-evidences`

Configurer dans MinIO avec politique de rétention adaptée (WORM — Write Once Read Many).

---

## 8. tnt-billing-wallet

### Rôle actuel
Gestion wallets, paiements, compensation.

### Impact — AJOUTS NÉCESSAIRES

#### a) Implémentation de `IPaymentFreezePort`

```java
@Component
public class IncidentPaymentFreezeAdapter implements IPaymentFreezePort {

    @Override
    public Mono<Void> freezePayment(UUID missionId, String reason) {
        // Geler le paiement associé à missionId (statut FROZEN)
        // Logger la raison dans l'audit trail du wallet
    }

    @Override
    public Mono<Void> unfreezePayment(UUID missionId, String reason) {
        // Libérer le gel — statut revient à PENDING ou CONFIRMED
    }
}
```

#### b) Écouter `tnt.incident.escalated.to.dispute`

Quand un incident bascule en litige, déclencher automatiquement le gel définitif du paiement jusqu'à résolution du litige par `tnt-dispute-core`.

---

## 9. tnt-trust (TiiBnTick Trust Layer — Blockchain)

### Rôle actuel
Preuve blockchain des opérations de livraison.

### Impact — AJOUTS NÉCESSAIRES

#### a) Implémentation de `IBlockchainAuditPort`

```java
@Component
public class TrustBlockchainAuditAdapter implements IBlockchainAuditPort {

    @Override
    public Mono<String> writeIncidentEvent(UUID incidentId, String chainId,
                                            String eventType, String payload) {
        // Créer un bloc dans la chaîne spécifiée
        // Retourner le hash SHA-256 du bloc créé
    }

    @Override
    public Mono<Boolean> verifyChain(String chainId) {
        // Vérifier l'intégrité séquentielle de tous les blocs
    }

    @Override
    public Mono<String> getParcelChainTailHash(UUID parcelId) {
        // Retourner le hash du dernier bloc de la chaîne colis
    }
}
```

#### b) Nouveaux types d'événements blockchain

```
INCIDENT_CREATED, INCIDENT_CHAIN_INITIALIZED, EVIDENCE_ATTACHED,
PARCEL_HANDOVER_COMPLETED, INTER_AGENCY_COOPERATION_COMPLETED,
INCIDENT_CLOSED, PARCEL_CHAIN_RESUMED
```

---

## 10. tnt-dispute-core

### Rôle actuel
Litiges formels (perte, dommage, fraude avérée).

### Impact — ÉCOUTER `tnt.incident.escalated.to.dispute`

```java
// Dans le consumer Kafka de tnt-dispute-core
@KafkaListener(topics = "tnt.incident.escalated.to.dispute")
public void onIncidentEscalatedToDispute(ConsumerRecord<String, String> record) {
    // Extraire : incidentId, missionId, parcelIds, fraudReason
    // Créer automatiquement un Dispute avec DisputeType.FRAUD
    // Lier l'incident au litige via incidentId
}
```

---

## Résumé des impacts par module

| Module | Type d'impact | Fichiers à créer/modifier |
|---|---|---|
| `tnt-delivery-core` | **Fort** — pause/resume mission + event enrichi | 1 adaptateur + 2 champs domaine + 1 migration Liquibase |
| `tnt-realtime-core` | **Moyen** — enrichissement des events GPS/geofence | 2 modifications de publishers Kafka |
| `tnt-actor-core` | **Moyen** — réputation + flag fraude | 1 adaptateur |
| `tnt-resource-core` | **Fort** — disponibilité drivers/véhicules | 2 adaptateurs + 1 enum status |
| `tnt-route-core` | **Moyen** — hub finder + rerouting | 1 adaptateur |
| `tnt-notify-core` | **Moyen** — types notifications incidents | 1 adaptateur + enum |
| `tnt-media-core` | **Faible** — archivage evidences | 1 adaptateur |
| `tnt-billing-wallet` | **Moyen** — gel paiement | 1 adaptateur |
| `tnt-trust` | **Fort** — chaîne blockchain incident | 1 adaptateur + nouveaux types |
| `tnt-dispute-core` | **Faible** — consumer Kafka bascule incident→litige | 1 consumer Kafka |

---

*MANFOUO Braun — Impact tnt-incident-core v1.0 — TiiBnTick Core*
