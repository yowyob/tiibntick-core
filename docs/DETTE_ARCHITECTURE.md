# Dette d'architecture — règles ArchUnit

Deux règles `FreezingArchRule` dans `LayeringArchitectureTest`
(`tnt-bootstrap`) détectent des violations pré-existantes. Ces violations
préexistaient avant le Chantier C (commitées avant le 23 juillet 2026) et
ne sont pas introduites par les lots C-10 à C-13.

Le freeze (`archunit_store`) ne peut pas être stabilisé tant que le code
tiers (agency, realtime) évolue entre les runs : ArchUnit retire les
violations dont la signature a changé et en ajoute de nouvelles à chaque
build. Le store est donc reverted à son état du 23 juillet.

**État au 2026-09-29 : 2 tests rouges en CI, cause documentée ici.**

**Règle non-freeze :**
- `noModuleMayDependOnAStrictlyHigherLayer` → **✅ PASS** — aucune violation de
  couche L0–L6 (les bugs A1 et A2 restent corrigés).

---

## Règle 1 — `applicationMustNotDependOnAdapterInWeb`

**Principe** : les services applicatifs ne doivent pas connaître les DTOs web
(`adapter.in.web`). Les contrôleurs font la conversion.

**Violations gelées dans le store (2026-07-18) : 302 lignes ≈ 294 violations.**
Le store contient exactement ce qui était connu à la date du commit `b260aa8f`.

**Modules concernés** (par nombre de violations décroissant, mesuré dans le store) :

| Module | Catégorie principale | Nombre approx. |
|--------|---------------------|----------------|
| `tnt-agency-*` (14 sous-modules) | `AgencyOrgMapper`, `HubHandoffService`, `HubOperatorProvisioningService`, `TrackingService`, `WorkforceMapper`, `CommissionMapper`, `OnboardingService` construisent ou retournent des DTOs `adapter.in.web` ; inner-classes (`$ProvisionOperatorResult`, `$ApprovalContext`) avec champs de type DTO web | ~173 |
| `tnt-roles-core` et modules utilisateurs | Services annotés `@RequirePermission` — l'annotation réside dans `roles.adapter.in.web`, pas dans un package neutre | ~129 |
| `tnt-billing-wallet` | `WalletService` — 6 méthodes annotées `@RequirePermission` | ~33 |
| `tnt-organization-core` | Mappers + services retournant des types `adapter.in.web` | ~36 |
| `tnt-tp-core` | idem | ~18 |
| `tnt-administration-core` | Services annotés `@RequirePermission` | ~16 |
| `tnt-resource-core` | idem | ~12 |
| `tnt-delivery-core` | `DeliveryAnnouncementService.createDirect` annoté `@RequirePermission` | ~8 |
| `tnt-actor-core` | `ActorRatingService`, `DelivererService` annotés `@RequirePermission` | ~6 |
| `tnt-sync-core` | idem | ~2 |
| `tnt-go-freelancer-point-back-core` | Port interfaces (`AddressUseCase`, `AdminFreelancerUseCase`) typées DTO web ; `DeliveryNeedApplicationService.DEFAULT_POLICY` de type `FreelancerPricingDTO` | ~5 |

**Cause systémique — deux racines distinctes :**

1. **`@RequirePermission` dans `adapter.in.web`** : l'annotation RBAC est définie dans
   `tnt-roles-core.adapter.in.web.RequirePermission` au lieu d'un package neutre.
   Chaque service qui s'en sert viole la règle. *Correctif systémique :* déplacer
   `@RequirePermission` vers `tnt-roles-core.domain.annotation` ou `tnt-common-core`.

2. **Mappers applicatifs retournant des DTOs web** : principalement dans
   `tnt-agency-back-core`, les `application/mapper/*.java` appellent directement les
   constructeurs de DTOs `adapter.in.web.dto.*`. *Correctif :* introduire des types
   domaine et déléguer la construction du DTO au contrôleur.

---

## Règle 2 — `adapterInMustNotDependOnAdapterOut`

**Principe** : les adaptateurs entrants (contrôleurs web, consumers Kafka)
ne doivent pas appeler directement les adaptateurs sortants (repositories
R2DBC, ports externes). Ils doivent passer par les ports applicatifs.

**Violations gelées dans le store (2026-07-18) : 75 lignes ≈ 23 violations.**

| Classe (adapter.in) | Dépendance interdite (adapter.out) | Module |
|---------------------|-------------------------------------|--------|
| `InventoryHubConsumer` (messaging) | Injecte `AgencyRelayHubR2dbcRepository` + `HubParcelRecordR2dbcRepository` directement | `tnt-agency-org-core` (hubops) |
| `TntStompWebSocketHandler` (websocket) | Injecte `WebSocketSessionRegistry` (adapter.out.websocket) | `tnt-realtime-core` |
| `PermissionCacheInvalidationListener` (kafka) | Injecte `PermissionCache` (adapter.out.permission) | `tnt-roles-core` |

**Violation NON gelée (cause du build rouge actuel) :**

| Classe | Violation | Correctif |
|--------|-----------|-----------|
| `NotificationStreamController` (adapter.in.web) | Retourne `Flux<MatchingNotificationEvent>` dont le type paramètre dépend de `MatchingNotificationEvent` (adapter.out.kafka.event) | Introduire un type domaine `MatchingNotification` en `domain/model/` et mapper depuis le type Kafka dans `adapter.in.web` |

**Correctif global règle 2** : créer des ports applicatifs (interfaces) pour
`HubParcelRecordRepository`, `WebSocketSessionRegistry`, et `PermissionCache`,
puis injecter ces ports dans les consumers.

---

## Suivi

Ces violations sont tracées ici en attendant leur correction dans un Chantier
dédié. Tout nouveau lot introduisant une **nouvelle** violation (non listée
ci-dessus) doit la corriger dans le même commit.
