# Mise à Jour du Core TiiBnTick — Intégration de tnt-incident-core

**Auteur :** MANFOUO Braun  
**Version :** 1.0  
**Objectif :** Guide complet de réimplémentation de tous les modules Core pour intégrer `tnt-incident-core`

---

## Ordre d'intégration recommandé

```
1. tnt-delivery-core    (le plus impacté — pause/resume mission)
2. tnt-resource-core    (driver/vehicle availability)
3. tnt-trust            (blockchain audit)
4. tnt-actor-core       (réputation)
5. tnt-route-core       (rerouting + nearest hub)
6. tnt-realtime-core    (enrichissement events GPS)
7. tnt-notify-core      (notifications)
8. tnt-billing-wallet   (gel paiement)
9. tnt-media-core       (archivage)
10. tnt-dispute-core    (bascule litige)
11. tnt-bootstrap       (assemblage final)
```

---

## 1. tnt-delivery-core

### 1.1 — Ajouter `tnt-incident-core` comme dépendance Maven (`provided`/`api` scope)

> Non — ne pas ajouter `tnt-incident-core` comme dépendance. Le port `IMissionStatusPort` est **défini dans `tnt-incident-core`** et implémenté dans `tnt-delivery-core`. Le couplage se fait uniquement via `tnt-bootstrap`.

Le bon pattern : dans `tnt-bootstrap`, `tnt-delivery-core` dépend de `tnt-incident-core` à travers le contexte Spring. `tnt-delivery-core` implémente les ports définis dans `tnt-incident-core` mais **ne l'importe pas dans son pom.xml**.

> **Alternative propre** : Créer un module `tnt-incident-api` (interfaces seules) importable par les deux. Pour la v1, utiliser le no-op dans `IncidentCoreConfig` et fournir les implémentations réelles dans `tnt-bootstrap`.

### 1.2 — Modifier le domain `Mission`

**Fichier :** `src/main/java/.../delivery/domain/model/Mission.java`

```java
// Ajouter les champs
private UUID pausedByIncidentId;
private String statusBeforePause;

// Ajouter les méthodes de transition
public Mission pauseForIncident(UUID incidentId) {
    return toBuilder()
            .pausedByIncidentId(incidentId)
            .statusBeforePause(this.status.name())
            .status(MissionStatus.PAUSED_BY_INCIDENT)
            .build();
}

public Mission resumeAfterIncident(UUID newDriverId, UUID newVehicleId) {
    return toBuilder()
            .pausedByIncidentId(null)
            .status(MissionStatus.valueOf(this.statusBeforePause))
            .assignedDriverId(newDriverId)
            .vehicleId(newVehicleId)
            .build();
}
```

### 1.3 — Ajouter `PAUSED_BY_INCIDENT` à l'enum `MissionStatus`

```java
// Dans MissionStatus.java
PAUSED_BY_INCIDENT,  // bloqué par un incident en cours
```

### 1.4 — Créer `MissionIncidentAdapter` (implémente le port)

**Nouveau fichier :** `adapter/incident/MissionIncidentAdapter.java`

```java
@Component
@RequiredArgsConstructor
public class MissionIncidentAdapter implements IMissionStatusPort {

    private final IMissionRepository missionRepository;
    private final IDeliveryEventPublisher eventPublisher;

    @Override
    public Mono<Void> pauseMission(UUID missionId, UUID incidentId) {
        return missionRepository.findById(missionId)
                .map(mission -> mission.pauseForIncident(incidentId))
                .flatMap(missionRepository::save)
                .then();
    }

    @Override
    public Mono<Void> resumeMission(UUID missionId, UUID newDriverId, UUID newVehicleId) {
        return missionRepository.findById(missionId)
                .map(mission -> mission.resumeAfterIncident(newDriverId, newVehicleId))
                .flatMap(missionRepository::save)
                .then();
    }

    @Override
    public Mono<MissionSnapshot> getMissionSnapshot(UUID missionId) {
        return missionRepository.findById(missionId)
                .map(m -> new MissionSnapshot(
                        m.getId(), m.getAssignedDriverId(), m.getVehicleId(),
                        m.getAgencyId(), m.getStatus().name(), m.getSlaDeadline(),
                        m.getParcelIds()
                ));
    }
}
```

### 1.5 — Enrichir `MissionStatusChangedEvent`

```java
// Dans MissionStatusChangedEvent
String platform;           // AJOUTER
List<UUID> parcelIds;      // AJOUTER
```

### 1.6 — Liquibase migration sur `tnt_missions`

```xml
<changeSet id="003-add-incident-fields-to-missions" author="MANFOUO Braun">
    <addColumn tableName="tnt_missions">
        <column name="paused_by_incident_id" type="UUID"/>
        <column name="status_before_pause" type="VARCHAR(40)"/>
    </addColumn>
</changeSet>
```

### 1.7 — Kafka Consumer `tnt.incident.resolved`

```java
@KafkaListener(topics = "tnt.incident.resolved", groupId = "tnt-delivery-core")
public void onIncidentResolved(ConsumerRecord<String, String> record) {
    JsonNode payload = objectMapper.readTree(record.value());
    UUID missionId = UUID.fromString(payload.path("missionId").asText());
    // Vérifier si la mission est toujours PAUSED_BY_INCIDENT
    // Si oui et si le driver/vehicle sont déjà assignés, reprendre automatiquement
}
```

---

## 2. tnt-resource-core

### 2.1 — Créer `DriverAvailabilityPortAdapter`

**Nouveau fichier :** `adapter/incident/DriverAvailabilityPortAdapter.java`

```java
@Component
@RequiredArgsConstructor
public class DriverAvailabilityPortAdapter implements IDriverAvailabilityPort {

    private final IDelivererProfileRepository delivererRepo;

    @Override
    public Flux<DriverCandidate> findEligibleReplacementDrivers(
            UUID tenantId, UUID agencyId, double lat, double lng,
            double requiredCapacityKg, String vehicleCategory) {
        // PostGIS query : trouver les livreurs dans un rayon de 10km
        // dont la capacité véhicule est suffisante et statut AVAILABLE
        return delivererRepo.findEligibleNearby(tenantId, lat, lng, 10000, requiredCapacityKg)
                .map(p -> new DriverCandidate(
                        p.getActorId(), p.getVehicleId(), p.getAgencyId(),
                        p.getDistanceKm(), p.getReputationScore(),
                        p.getCapacityKg(), p.getVehicleCategory()
                ));
    }

    @Override
    public Mono<Boolean> isDriverAvailable(UUID driverId) {
        return delivererRepo.findById(driverId)
                .map(p -> "AVAILABLE".equals(p.getStatus()))
                .defaultIfEmpty(false);
    }
}
```

### 2.2 — Créer `VehicleCompatibilityPortAdapter`

```java
@Component
@RequiredArgsConstructor
public class VehicleCompatibilityPortAdapter implements IVehicleCompatibilityPort {

    private final IVehicleRepository vehicleRepo;

    @Override
    public Mono<VehicleInfo> getVehicleInfo(UUID vehicleId) {
        return vehicleRepo.findById(vehicleId)
                .map(v -> new VehicleInfo(v.getId(), v.getAgencyId(),
                        v.getCategory(), v.getCapacityKg(), v.getVolumeM3(),
                        v.hasRefrigeration()));
    }

    @Override
    public Mono<Boolean> isVehicleAvailable(UUID vehicleId) {
        return vehicleRepo.findById(vehicleId)
                .map(v -> "AVAILABLE".equals(v.getStatus()))
                .defaultIfEmpty(false);
    }
}
```

### 2.3 — Ajouter `IN_INCIDENT_SUBSTITUTION` dans `VehicleStatus`

```java
IN_INCIDENT_SUBSTITUTION,  // prêt temporairement à une autre agence suite à incident
```

### 2.4 — Repository PostGIS `findEligibleNearby`

```java
@Query("""
    SELECT *, ST_Distance(
        geom, ST_SetSRID(ST_Point(:lng, :lat), 4326)::geography
    ) / 1000 AS distance_km
    FROM tnt_deliverer_profiles
    WHERE tenant_id = :tenantId
      AND status = 'AVAILABLE'
      AND capacity_kg >= :minCapacity
      AND ST_DWithin(geom, ST_SetSRID(ST_Point(:lng, :lat), 4326)::geography, :radiusMeters)
    ORDER BY distance_km ASC
    LIMIT 5
""")
Flux<DelivererProfileEntity> findEligibleNearby(UUID tenantId, double lat,
        double lng, int radiusMeters, double minCapacity);
```

---

## 3. tnt-trust (TiiBnTick Trust Layer)

### 3.1 — Créer `IncidentBlockchainAuditAdapter`

**Nouveau fichier :** `adapter/incident/IncidentBlockchainAuditAdapter.java`

```java
@Component
@RequiredArgsConstructor
public class IncidentBlockchainAuditAdapter implements IBlockchainAuditPort {

    private final IBlockchainChainRepository chainRepository;
    private final IncidentBlockchainHashService hashService;

    @Override
    public Mono<String> writeIncidentEvent(UUID incidentId, String chainId,
                                            String eventType, String payload) {
        return chainRepository.findLatestByChainId(chainId)
                .defaultIfEmpty(genesisBlock())
                .flatMap(prev -> {
                    long nonce = System.currentTimeMillis();
                    String hash = hashService.computeHash(
                            prev.getBlockIndex() + 1, prev.getCurrentHash(),
                            eventType, payload, Instant.now().toString(), nonce);
                    BlockRecord record = BlockRecord.create(chainId, prev.getBlockIndex() + 1,
                            prev.getCurrentHash(), hash, eventType, payload, nonce);
                    return chainRepository.save(record).map(BlockRecord::getCurrentHash);
                });
    }

    @Override
    public Mono<Boolean> verifyChain(String chainId) {
        return chainRepository.findByChainIdOrderByBlockIndexAsc(chainId)
                .collectList()
                .map(blocks -> {
                    for (int i = 1; i < blocks.size(); i++) {
                        if (!blocks.get(i).getPreviousHash()
                                .equals(blocks.get(i - 1).getCurrentHash())) return false;
                    }
                    return true;
                });
    }

    @Override
    public Mono<String> getParcelChainTailHash(UUID parcelId) {
        return chainRepository.findLatestHashByParcelId(parcelId)
                .defaultIfEmpty("GENESIS");
    }
}
```

---

## 4. tnt-actor-core

### 4.1 — Créer `ActorReputationPortAdapter`

```java
@Component
@RequiredArgsConstructor
public class ActorReputationPortAdapter implements IActorReputationPort {

    private final IActorProfileRepository actorRepo;
    private final IRatingRepository ratingRepo;

    @Override
    public Mono<Void> decreaseReputation(UUID actorId, double points, String reason) {
        return actorRepo.findById(actorId)
                .flatMap(profile -> {
                    double current = profile.getReputationScore();
                    return actorRepo.save(profile.withReputationScore(Math.max(0, current - points)));
                }).then();
    }

    @Override
    public Mono<Void> flagForFraud(UUID actorId, UUID incidentId, String evidence) {
        return actorRepo.findById(actorId)
                .flatMap(p -> actorRepo.save(p.withKycStatus(KycStatus.FLAGGED_FOR_FRAUD)))
                .then();
    }

    @Override
    public Mono<Double> getReputationScore(UUID actorId) {
        return actorRepo.findById(actorId).map(p -> p.getReputationScore());
    }

    @Override
    public Mono<Integer> getIncidentHistoryCount(UUID actorId) {
        return ratingRepo.countIncidentsByActorId(actorId);
    }
}
```

### 4.2 — Ajouter `FLAGGED_FOR_FRAUD` dans `KycStatus`

```java
FLAGGED_FOR_FRAUD,  // flagué suite à un incident de fraude
```

---

## 5. tnt-route-core

### 5.1 — Créer `IncidentRouteOptimizerAdapter`

```java
@Component
@RequiredArgsConstructor
public class IncidentRouteOptimizerAdapter implements IRouteOptimizerPort {

    private final IHubRepository hubRepository;
    private final IRouteGraphService routeGraphService;

    @Override
    public Mono<NearestHubResult> findNearestHub(double lat, double lng, UUID tenantId) {
        return hubRepository.findNearestToPoint(lat, lng, tenantId)
                .map(hub -> new NearestHubResult(hub.getId(), hub.getName(),
                        hub.getLatitude(), hub.getLongitude(), hub.getDistanceKm()));
    }

    @Override
    public Mono<RerouteResult> rerouteFromCurrentPosition(UUID missionId, double lat, double lng) {
        return routeGraphService.computeNewRoute(missionId, lat, lng)
                .map(route -> new RerouteResult(missionId, route.getPolyline(), route.getEtaMinutes()));
    }
}
```

---

## 6. tnt-realtime-core

### 6.1 — Enrichir `GpsPositionPublisher`

Dans le service qui publie `tnt.realtime.gps.position.updated`, ajouter la détection :

```java
// Dans GpsKalmanTrackingService ou similaire
boolean trajectoryAnomaly = kalmanFilter.detectDeviation(driverId, lat, lng);
boolean prolongedStop = stopDetector.isProlongedStop(driverId, lat, lng, lastPositionAt);

var event = GpsPositionUpdatedEvent.builder()
        // ... champs existants ...
        .trajectoryAnomaly(trajectoryAnomaly)
        .prolongedStop(prolongedStop)
        .stopDurationMinutes(prolongedStop ? stopDetector.getDurationMinutes(driverId) : 0)
        .build();
```

### 6.2 — Enrichir `GeofencePublisher`

```java
var event = GeofenceTriggeredEvent.builder()
        // ... champs existants ...
        .zoneType(zone.isDangerZone() ? "DANGER_ZONE"
                  : zone.isRestricted() ? "RESTRICTED_ZONE" : "NORMAL")
        .build();
```

---

## 7. tnt-notify-core

### 7.1 — Créer `IncidentNotificationAdapter`

```java
@Component
@RequiredArgsConstructor
public class IncidentNotificationAdapter implements INotificationPort {

    private final INotificationService notificationService;
    private final IActorContactRepository contactRepo;

    @Override
    public Mono<Void> notifyActor(UUID actorId, String title, String body,
                                   String type, UUID incidentId) {
        return notificationService.send(NotificationRequest.builder()
                .targetActorId(actorId).title(title).body(body)
                .type(type).referenceId(incidentId)
                .channel(NotificationChannel.PUSH)
                .build());
    }

    @Override
    public Mono<Void> notifyActors(List<UUID> actorIds, String title, String body,
                                    String type, UUID incidentId) {
        return Flux.fromIterable(actorIds)
                .flatMap(id -> notifyActor(id, title, body, type, incidentId))
                .then();
    }

    @Override
    public Mono<Void> notifyAgency(UUID agencyId, String title, String body,
                                    String type, UUID incidentId) {
        return contactRepo.findManagersByAgencyId(agencyId)
                .flatMap(manager -> notifyActor(manager.getId(), title, body, type, incidentId))
                .then();
    }
}
```

---

## 8. tnt-billing-wallet

### 8.1 — Créer `IncidentPaymentFreezeAdapter`

```java
@Component
@RequiredArgsConstructor
public class IncidentPaymentFreezeAdapter implements IPaymentFreezePort {

    private final IWalletTransactionRepository txRepository;
    private final IPaymentRepository paymentRepository;

    @Override
    public Mono<Void> freezePayment(UUID missionId, String reason) {
        return paymentRepository.findByMissionId(missionId)
                .flatMap(payment -> paymentRepository.save(payment.freeze(reason)))
                .then();
    }

    @Override
    public Mono<Void> unfreezePayment(UUID missionId, String reason) {
        return paymentRepository.findByMissionId(missionId)
                .flatMap(payment -> paymentRepository.save(payment.unfreeze(reason)))
                .then();
    }
}
```

### 8.2 — Consumer Kafka `tnt.incident.escalated.to.dispute`

```java
@KafkaListener(topics = "tnt.incident.escalated.to.dispute", groupId = "tnt-billing-wallet")
public void onIncidentEscalatedToDispute(ConsumerRecord<String, String> record) {
    // Geler définitivement le paiement jusqu'à la résolution du litige par tnt-dispute-core
}
```

---

## 9. tnt-media-core

### 9.1 — Créer `IncidentMediaArchiveAdapter`

```java
@Component
@RequiredArgsConstructor
public class IncidentMediaArchiveAdapter implements IMediaStoragePort {

    private final MinioClient minioClient;

    private static final String EVIDENCE_BUCKET = "tnt-incident-evidences";

    @Override
    public Mono<Void> archiveIncidentEvidence(UUID incidentId) {
        return Mono.fromCallable(() -> {
            // Lister les objets dans incidents/temp/{incidentId}/
            // Les copier vers incidents/archive/{incidentId}/
            // Supprimer les originaux temporaires
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
}
```

---

## 10. tnt-dispute-core

### 10.1 — Consumer Kafka `tnt.incident.escalated.to.dispute`

```java
@Component
@RequiredArgsConstructor
public class IncidentDisputeConsumer {

    private final IOpenDisputeUseCase openDisputeUseCase;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "tnt.incident.escalated.to.dispute", groupId = "tnt-dispute-core")
    public void onIncidentEscalatedToDispute(ConsumerRecord<String, String> record) {
        try {
            JsonNode payload = objectMapper.readTree(record.value());
            UUID incidentId = UUID.fromString(payload.path("incidentId").asText());
            UUID missionId = UUID.fromString(payload.path("missionId").asText());
            String fraudReason = payload.path("fraudReason").asText("");

            openDisputeUseCase.execute(OpenDisputeCommand.builder()
                    .originatingIncidentId(incidentId)
                    .missionId(missionId)
                    .type(DisputeType.FRAUD)
                    .description("Auto-opened from incident: " + fraudReason)
                    .parcelIds(/* extraire la liste */ List.of())
                    .build()
            ).subscribe();
        } catch (Exception e) {
            log.error("Error creating dispute from incident escalation: {}", e.getMessage(), e);
        }
    }
}
```

### 10.2 — Ajouter `originatingIncidentId` dans `Dispute`

```java
// Dans le domaine de tnt-dispute-core
UUID originatingIncidentId;  // nullable — rempli si le litige vient d'un incident
```

---

## 11. tnt-bootstrap — Assemblage Final

C'est la mise à jour la plus structurelle. Elle lie tout.

### 11.1 — pom.xml : ajouter la dépendance

```xml
<dependency>
    <groupId>com.yowyob.tiibntick.core</groupId>
    <artifactId>tnt-incident-core</artifactId>
</dependency>
```

### 11.2 — TiiBnTickApplication.java : ajouter l'import

```java
@SpringBootApplication
@EnableWebFlux
@EnableR2dbcRepositories
@EnableReactiveSecurity
@Import({
    KernelBridgeConfig.class,
    TntCoreConfig.class,
    TntBillingConfig.class,
    TntDeliveryConfig.class,
    TntGeoConfig.class,
    TntDisputeCoreConfig.class,
    IncidentCoreConfig.class        // ← AJOUTER
})
public class TiiBnTickApplication {
    public static void main(String[] args) {
        Loader.loadNativeLibraries();
        SpringApplication.run(TiiBnTickApplication.class, args);
    }
}
```

### 11.3 — TntKafkaTopicsConfig.java : ajouter les topics incident

```java
// Dans TntKafkaTopicsConfig

// ── Incident Core ( — NOUVEAU) ──────────────────────────────
public static final String TOPIC_INCIDENT_CREATED          = "tnt.incident.created";
public static final String TOPIC_INCIDENT_STATUS_CHANGED   = "tnt.incident.status.changed";
public static final String TOPIC_INCIDENT_TRIAGED          = "tnt.incident.triaged";
public static final String TOPIC_INCIDENT_DRIVER_ASSIGNED  = "tnt.incident.driver.assigned";
public static final String TOPIC_INCIDENT_HANDOVER_DONE    = "tnt.incident.handover.completed";
public static final String TOPIC_INCIDENT_RESOLVED         = "tnt.incident.resolved";
public static final String TOPIC_INCIDENT_CLOSED           = "tnt.incident.closed";
public static final String TOPIC_INCIDENT_CANCELLED        = "tnt.incident.cancelled";
public static final String TOPIC_INCIDENT_ESCALATED        = "tnt.incident.escalated";
public static final String TOPIC_INCIDENT_TO_DISPUTE       = "tnt.incident.escalated.to.dispute";
public static final String TOPIC_INTERAGENCY_REQUESTED     = "tnt.incident.interagency.requested";
public static final String TOPIC_INTERAGENCY_COMPLETED     = "tnt.incident.interagency.completed";

// Ajouter ces @Bean dans TntKafkaTopicsConfig
@Bean public NewTopic incidentCreatedTopic() {
    return TopicBuilder.name(TOPIC_INCIDENT_CREATED).partitions(4).replicas(1).build();
}
// ... (répéter pour chaque topic) ...
```

### 11.4 — TntModuleRegistry.java : ajouter le descriptor

```java
public static final ModuleDescriptor TNT_INCIDENT_CORE = ModuleDescriptor.builder()
        .moduleId("tnt-incident-core")
        .moduleName("TiiBnTick Incident Core")
        .version("1.0.0")
        .layer(ModuleLayer.LOGISTICS_L3)
        .origin(ModuleOrigin.TNT_EXCLUSIVE)
        .kafkaTopics(List.of(
                "tnt.incident.created", "tnt.incident.status.changed", "tnt.incident.triaged",
                "tnt.incident.driver.assigned", "tnt.incident.handover.completed",
                "tnt.incident.resolved", "tnt.incident.closed", "tnt.incident.cancelled",
                "tnt.incident.escalated", "tnt.incident.escalated.to.dispute",
                "tnt.incident.interagency.requested", "tnt.incident.interagency.completed"
        ))
        .databaseSchema("tnt_core")
        .build();
```

### 11.5 — TntOpenApiConfig.java : ajouter le groupe Swagger

```java
@Bean
public GroupedOpenApi incidentCoreOpenApi() {
    return GroupedOpenApi.builder()
            .group("incident-core")
            .pathsToMatch("/api/v1/incidents/**")
            .build();
}
```

### 11.6 — Fournir les implémentations réelles des ports via @Bean

Dans `tnt-bootstrap` (ou dans un `TntIncidentCoreConfig.java` dédié dans bootstrap) :

```java
@Configuration
@RequiredArgsConstructor
public class TntIncidentPortsConfig {

    // Remplacer les no-op de IncidentCoreConfig par les vraies implémentations
    @Bean
    @Primary
    public IMissionStatusPort realMissionStatusPort(MissionIncidentAdapter adapter) {
        return adapter;
    }

    @Bean
    @Primary
    public IDriverAvailabilityPort realDriverAvailabilityPort(DriverAvailabilityPortAdapter adapter) {
        return adapter;
    }

    @Bean
    @Primary
    public IVehicleCompatibilityPort realVehicleCompatibilityPort(VehicleCompatibilityPortAdapter adapter) {
        return adapter;
    }

    @Bean
    @Primary
    public IBlockchainAuditPort realBlockchainAuditPort(IncidentBlockchainAuditAdapter adapter) {
        return adapter;
    }

    @Bean
    @Primary
    public INotificationPort realNotificationPort(IncidentNotificationAdapter adapter) {
        return adapter;
    }

    @Bean
    @Primary
    public IPaymentFreezePort realPaymentFreezePort(IncidentPaymentFreezeAdapter adapter) {
        return adapter;
    }

    @Bean
    @Primary
    public IActorReputationPort realActorReputationPort(ActorReputationPortAdapter adapter) {
        return adapter;
    }

    @Bean
    @Primary
    public IRouteOptimizerPort realRouteOptimizerPort(IncidentRouteOptimizerAdapter adapter) {
        return adapter;
    }

    @Bean
    @Primary
    public IMediaStoragePort realMediaStoragePort(IncidentMediaArchiveAdapter adapter) {
        return adapter;
    }
}
```

### 11.7 — application.yml : ajouter les configurations incident

```yaml
tnt:
  incident:
    sla-monitor:
      delay-ms: 300000       # 5 minutes
    auto-escalation:
      delay-ms: 600000       # 10 minutes
    blockchain:
      chain-prefix: "INC"
    auto-resolution:
      max-attempts: 3
      driver-search-radius-km: 10
```

### 11.8 — Liquibase master : inclure les migrations incident

Dans `src/main/resources/db/tnt-core/changelog/db.changelog-master.xml` (ou le master central) :

```xml
<include file="classpath:db/changelog/db.changelog-master.xml"
         relativeToChangelogFile="false"
         context="incident-core"/>
```

### 11.9 — TntStartupRunner.java : ajouter l'étape de démarrage incident

```java
// Dans TntStartupRunner.run(), ajouter une étape
steps.add(StartupStep.builder()
        .order(6)
        .name("INCIDENT_CORE_INIT")
        .description("Validating incident-core tables and Kafka topics")
        .build());
```

---

## Checklist globale d'intégration

```
[ ] 1. Ajouter tnt-incident-core au pom.xml de tnt-bootstrap
[ ] 2. MissionStatus.PAUSED_BY_INCIDENT ajouté dans tnt-delivery-core
[ ] 3. Mission.pauseForIncident() et resumeAfterIncident() créés
[ ] 4. MissionIncidentAdapter créé dans tnt-delivery-core
[ ] 5. Liquibase migration sur tnt_missions (2 colonnes)
[ ] 6. MissionStatusChangedEvent enrichi (platform, parcelIds)
[ ] 7. DriverAvailabilityPortAdapter créé dans tnt-resource-core
[ ] 8. VehicleCompatibilityPortAdapter créé dans tnt-resource-core
[ ] 9. PostGIS query findEligibleNearby créée
[ ] 10. IncidentBlockchainAuditAdapter créé dans tnt-trust
[ ] 11. ActorReputationPortAdapter créé dans tnt-actor-core
[ ] 12. KycStatus.FLAGGED_FOR_FRAUD ajouté
[ ] 13. IncidentRouteOptimizerAdapter créé dans tnt-route-core
[ ] 14. GPS events enrichis (trajectoryAnomaly, prolongedStop) dans tnt-realtime-core
[ ] 15. Geofence events enrichis (zoneType) dans tnt-realtime-core
[ ] 16. IncidentNotificationAdapter créé dans tnt-notify-core
[ ] 17. IncidentPaymentFreezeAdapter créé dans tnt-billing-wallet
[ ] 18. IncidentMediaArchiveAdapter créé dans tnt-media-core
[ ] 19. IncidentDisputeConsumer créé dans tnt-dispute-core
[ ] 20. originatingIncidentId ajouté dans Dispute (tnt-dispute-core)
[ ] 21. @Import(IncidentCoreConfig.class) dans TiiBnTickApplication
[ ] 22. 12 topics incident ajoutés dans TntKafkaTopicsConfig
[ ] 23. TNT_INCIDENT_CORE ajouté dans TntModuleRegistry
[ ] 24. Groupe Swagger incidentCoreOpenApi() créé
[ ] 25. TntIncidentPortsConfig créé dans tnt-bootstrap
[ ] 26. application.yml enrichi avec section tnt.incident.*
[ ] 27. Migrations Liquibase incluses dans le master central
[ ] 28. TntStartupRunner étape incident ajoutée
```

---

*MANFOUO Braun — Mise à jour Core TiiBnTick  — Intégration tnt-incident-core*
