# Purpose
Kafka topic/producer/consumer map across all modules — what publishes what, what listens to what.

# Summary
Apache Kafka in KRaft mode (no ZooKeeper, see `infrastructure/docker.md`). Most modules define their own module-scoped `*KafkaTemplate`/`*KafkaListenerContainerFactory` beans rather than sharing one global factory — `tnt-bootstrap`'s `TntKafkaConfig` provides `tntKafkaTemplate` plus `@ConditionalOnMissingBean` fallbacks so the app still starts if a module's own bean is missing.

# Details

## Module → topics

| Module | Role | Topic(s) | Class |
|---|---|---|---|
| tnt-delivery-core | Producer | `tnt.delivery.events`, `tnt.delivery.mission.status-changed`, `tnt.delivery.freelancer_org.assigned` | `KafkaDeliveryEventPublisher` |
| tnt-delivery-core | Consumer | `tnt.incident.resolved`, `tnt.incident.closed`, `tnt.vehicle.assigned_to_mission`, `tnt.vehicle.released_from_mission` | `IncidentEventConsumer`, `FreelancerVehicleEventConsumer` |
| tnt-incident-core | Producer | (via domain event publisher, no fixed topic constant found) | `IncidentKafkaEventPublisher` |
| tnt-incident-core | Consumer | `tnt.delivery.mission.status.changed`, `tnt.realtime.gps.position.updated`, `tnt.realtime.geofence.triggered` | `IncidentEventConsumer` |
| tnt-route-core | Producer | `tnt.route.tour.events`, `tnt.route.eta.events`, `tnt.route.reroute.events`, `tnt.route.vrp.events` | `KafkaRouteEventPublisher` |
| tnt-geo-core | Producer | `tnt.geo.traffic.events`, `tnt.geo.node.events`, `tnt.geo.zone.events` | `KafkaGeoEventPublisher` |
| tnt-realtime-core | Producer | Dynamic, via `RealtimeDomainEvent#kafkaTopic()` | `KafkaRealtimeEventPublisher` |
| tnt-realtime-core | Consumer | `tnt.route.eta.updated`, `tnt.delivery.mission.status.changed` (both configurable) | `ETAUpdateEventConsumer`, `MissionStatusEventConsumer` |
| tnt-sync-core | Producer | Dynamic via event routing | `KafkaSyncEventPublisher` |
| tnt-sync-core | Consumer | `tnt.delivery.mission.status.changed`, `tnt.delivery.package.updated`, `tnt.actor.profile.updated`, `tnt.organization.hub.updated`, `tnt.geo.alert.created`, `tnt.realtime.geofence.triggered` | `EntityChangedEventConsumer` (manual ACK) |
| tnt-dispute-core | Producer | (via domain event publisher) | `DisputeKafkaEventPublisher` |
| tnt-dispute-core | Consumer | `tnt.incident.resolved`, `tnt.incident.closed` | `DisputeEventConsumer` |
| tnt-notify-core | Consumer | FreelancerOrg domain event stream | `FreelancerOrgKafkaEventConsumer` |
| tnt-roles-core | Consumer | `tnt.roles.permission-changed` | `PermissionCacheInvalidationListener` — **scaffold, no producer exists yet** (see `security/permissions.md`) |
| tnt-billing-wallet | Producer/Consumer | Transaction/payment events | `WalletEventPublisher` |
| tnt-billing-templates | Producer | Policy template events | `KafkaTemplateEventPublisher` |
| tnt-billing-report | Consumer | `tnt.billing.invoice.created` (and similar) | `InvoiceEventReportConsumer` |

## Conventions
- Idempotence enabled on most producers (`enable.idempotence=true`, `max.in.flight.requests.per.connection=1`, `acks=all`) — matches the global default in `application.yml`.
- `tnt-sync-core`'s consumer uses `ErrorHandlingDeserializer` + manual ACK — the one module that needs strict at-least-once with poison-pill protection (offline sync can't tolerate silent message loss).
- Default serialization: `StringSerializer` (key) + `ByteArraySerializer` (value) globally; `tnt-roles-core`'s dedicated `rolesConsumerFactory` uses String/String instead (JSON text payloads) — see `knowledge/known-issues.md`-style rationale comment in `TntRolesKafkaConfig`.

## Global config (`application.yml`)
```yaml
spring.kafka:
  bootstrap-servers: ${KAFKA_BOOTSTRAP:localhost:9092}
  producer: { acks: all, retries: 3, enable.idempotence: true }
  consumer: { group-id: tiibntick-core, auto-offset-reset: earliest, enable-auto-commit: false, isolation.level: read_committed }
```

# Links
- `infrastructure/docker.md` — Kafka container config (KRaft mode, ports)
- `domain/events.md` — domain events vs. Kafka topics relationship
- `security/permissions.md` — the one Kafka-driven cache invalidation flow

---
> **Comment maintenir ce document** : un nouveau `@KafkaListener` ou `KafkaTemplate` bean = une nouvelle ligne dans le tableau. Si `tnt.roles.permission-changed` obtient enfin un producteur, mettre à jour cette ligne ET `security/permissions.md`.
