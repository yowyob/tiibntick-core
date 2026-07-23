# Audit n°1 — Architecture globale — TiiBnTick Core

**Date :** 2026-07-16
**Auditeur :** Principal Software Architect (audit indépendant, basé sur l'analyse directe du code)
**Périmètre :** l'ensemble du reactor Maven `tiibntick-core-parent` (foundation, identity, logistics, business, billing, trust, coreBackend, tnt-bootstrap)
**Méthode :** lecture du `pom.xml` racine et des poms de chaque module, cartographie complète des dépendances inter-modules, échantillonnage approfondi de 10+ modules (structure hexagonale, pureté du domaine, ports, adapters), greps systématiques (`.block(`, `Thread.sleep`, `TODO/FIXME/HACK`, `@Deprecated`, `@Table` dans le domaine, imports croisés d'adapters), lecture des classes d'assemblage de `tnt-bootstrap`.

---

## 1. Résumé exécutif

L'architecture de TiiBnTick Core est **globalement saine et remarquablement disciplinée pour un projet de cette taille** : l'architecture hexagonale est réellement appliquée (pas seulement cosmétique) dans les modules « cœur » (L0–L6), le DDD y est authentique (agrégats riches avec transitions d'état et événements de domaine collectés), la migration « Kernel HTTP-only » est effective (zéro dépendance Maven `RT-comops` résiduelle), et la dette marquée (TODO/FIXME) est quasi nulle.

Cependant, l'audit révèle **un décalage croissant entre l'architecture déclarée (CLAUDE.md, pom racine) et la réalité du code**, concentré sur trois fronts :

1. **La règle de couches L0–L7, pourtant qualifiée de « stricte », est violée deux fois** dans le code livré : `yow-i18n-kernel` (L0) dépend de `tnt-common-core` (L1), et `tnt-actor-core` (L2) dépend de `tnt-incident-core` (L3).
2. **Une couche entière (`coreBackend/`, 18 modules, ~350 classes) n'existe pas dans la documentation d'architecture** et applique des standards nettement inférieurs au reste du code : contrôleurs accédant directement à la persistance, services applicatifs retournant des DTOs web, **zéro test** sur l'ensemble de la couche.
3. **Six appels `.block()` subsistent dans du code de production réactif**, dont un dans un aspect AOP transversal (`TenantValidationAspect`) potentiellement exécuté sur le chemin chaud des requêtes.

Aucun problème n'est jugé « Critique » au sens d'un risque immédiat de corruption de données ou de faille de sécurité, mais deux problèmes « Élevé » (violations de couches, `.block()` dans l'aspect tenant) méritent une correction à court terme, et la dérive de la couche `coreBackend` doit être stoppée avant qu'elle ne devienne la norme de fait.

---

## 2. État actuel

### 2.1 Topologie réelle du reactor

Le pom racine (`/pom.xml`) déclare **52 modules** (et non ~34 comme documenté) :

- L0–L6 : 34 modules conformes à la documentation (dont `tnt-hrm-core`, L4, et `tnt-platform-gateway-core`, L1, ajoutés récemment).
- **Couche non documentée** `coreBackend/` (déclarée entre L6 et L7, `pom.xml:132-135`) :
  - `tnt-go-freelancer-point-back-core`, `tnt-link-back-core`, `tnt-market-back-core` (backends de plateformes)
  - `tnt-agency-back-core` : agrégateur Maven de **14 sous-modules** `tnt-agency-*-core` (org, staff, workforce, assignment, commission, billing, onboarding, intake, inbox, fleet-local, compliance, analytics, sync, eventing).
- L7 : `tnt-bootstrap`, qui dépend désormais des 51 autres modules, y compris tous les backends de plateformes — l'application assemblée est donc un **monolithe modulaire complet** incluant les BFF Agency/Go/Link/Market.

### 2.2 Frontière Kernel (RT-comops)

**Vérifié : la migration HTTP-only est réelle.** `grep "RT-comops|yowyob.comops"` sur tous les `pom.xml` ne retourne que des commentaires descriptifs — aucune balise `<dependency>` vers un artefact `RT-comops-*`. Aucun `import com.yowyob.kernel.*` en dehors des deux modules foundation maison (`yow-event-kernel`, `yow-i18n-kernel`) qui possèdent légitimement ce package. L'accès au Kernel passe par des WebClients (`kernelWebClient`, `kernelOrganizationWebClient`, `kernelTpWebClient`) définis dans `tnt-bootstrap/src/main/java/com/yowyob/tiibntick/bootstrap/bridge/KernelBridgeConfig.java:57-93`.

### 2.3 Structure hexagonale — échantillon audité (10 modules)

`tnt-delivery-core`, `tnt-geo-core`, `tnt-realtime-core`, `tnt-billing-dsl`, `tnt-billing-wallet`, `tnt-actor-core`, `tnt-organization-core`, `tnt-resource-core`, `tnt-platform-gateway-core`, `tnt-trust-core` : les 10 présentent la structure `adapter/{in,out}` + `application/port/{in,out}` + `application/service` + `domain/...` + `config` (variante `domain/port` + `infrastructure` pour `tnt-billing-dsl` et `tnt-organization-core`, conforme à la doc). Constats transverses :

- **Aucune entité R2DBC (`@Table`) dans un package `domain/`** : les entités de persistance vivent dans `adapter/out/persistence/entity` avec mappers dédiés (ex. `logistics/tnt-incident-core/.../adapter/persistence/IncidentMapper.java`, 341 lignes).
- **Aucun import croisé d'adapter à adapter entre modules** détecté par grep sur tout le dépôt (`import com.yowyob.tiibntick.core.<autre>.adapter.*` : zéro occurrence hors module propriétaire).
- Les ports sont bien des interfaces ; les classes non-interfaces trouvées sous `application/port/in` sont des **commandes/records** (`CreateVehicleCommand`, `PublishEventCommand`…), ce qui est idiomatique.
- Fuites mineures de Spring dans `domain/` (détail A14) : essentiellement `@ResponseStatus` sur des exceptions de domaine et deux `@Component` sur des services de domaine.

### 2.4 DDD — réel ou cosmétique ?

**Réel dans le cœur.** Preuve type : l'agrégat `Delivery` (`logistics/tnt-delivery-core/.../domain/model/aggregate/Delivery.java`, 564 lignes) expose une machine à états métier complète — `assignDeliveryPerson` (l.238), `confirmPickup` (l.259), `startTransit` (l.273), `depositAtRelayPoint` (l.287), `pauseForIncident` (l.444), `resumeFromIncident` (l.475) — et collecte ses événements de domaine (`getDomainEvents()` l.507, `clearDomainEvents()` l.515), publiés ensuite via les ports `out`. Même richesse observée sur `Dispute` (596 lignes), `Wallet` (470 lignes), `Invoice` (458 lignes). Les packages `domain/event` existent et sont peuplés dans tous les modules échantillonnés.

**Nettement plus faible dans `coreBackend/`** : services applicatifs transaction-script retournant des DTOs web (détail A6), pas de couche domaine riche équivalente.

### 2.5 Zoom sur la couche `coreBackend/` (633 fichiers Java)

Audit détaillé des 4 modules déclarés dans le pom racine (`pom.xml:132-135`) :

| Module | Classes | Tests | Hexagonal | Observations |
|---|---|---|---|---|
| `tnt-go-freelancer-point-back-core` | 120 | 0 | **Violé** | 9 contrôleurs accèdent directement à la persistance (A5) |
| `tnt-market-back-core` | 170 | 0 | Partiel | Services transaction-script 588 lignes, 9 des 14 TODO du dépôt |
| `tnt-link-back-core` | 120 | 0 | **Respecté** | Structure ports/adapters exemplaire, mais scalabilité non traitée (A16) |
| `tnt-agency-back-core` (14 sous-modules) | ~220 | 0 | Partiel | Services retournant des DTOs web (A6), `MissionService` 768 lignes (A8) |

**Position dans les couches :** le pom racine les déclare entre L6 (trust) et L7 (bootstrap) sans leur attribuer de couche ; leurs poms montrent des dépendances descendantes massives vers L1–L5 (ex. `tnt-market-back-core` → 19 modules cœur, `tnt-go-freelancer-point-back-core` → 19 modules dont billing). Aucune dépendance montante détectée (aucun module cœur ne dépend d'eux, hormis `tnt-bootstrap`) : leur position de facto est cohérente (« L6-bis »), mais **non écrite** — ni dans CLAUDE.md ni dans les commentaires du pom racine, qui documentent L0–L7 sans eux.

**Cas particulier `tnt-link-back-core` (cartographie temps réel, cible « millions d'utilisateurs ») :** c'est paradoxalement le module le plus propre de la couche (ports `in/command`, `in/result`, `out`, adapters de persistance dédiés, mappers, aucune classe > 254 lignes, aucun état en mémoire, aucun `.block()`). Mais l'architecture *fonctionnelle* n'est pas dimensionnée pour sa cible — détail en A16.

### 2.6 Assemblage `tnt-bootstrap`

Trois mécanismes d'enregistrement de beans **coexistent** :
1. `@ComponentScan(basePackages = "com.yowyob.tiibntick")` global (`TiiBnTickApplication.java:46-58`), avec exclusion regex du package trust ;
2. Auto-configurations `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` dans la plupart des modules (dont `tnt-trust-core`) ;
3. `@Import` explicite dans `TntCoreConfig` (`TntCoreConfig.java:31-44`) — qui, contrairement à la documentation, n'importe **pas** « chaque module » mais uniquement les configs bootstrap + `GoFreelancerPointCoreConfig`.

Les beans de secours `@ConditionalOnMissingBean` (`TntKafkaConfig.java:67-68, 110-111` : `deliveryKafkaProducer`, `deliveryObjectMapper`) et les WebClients Kernel partagés complètent l'assemblage. Analyse de solidité en A10.

---

## 3. Points positifs (constatés dans le code, pas seulement déclarés)

| # | Point fort | Preuve |
|---|-----------|--------|
| P1 | **Migration Kernel HTTP-only effective** — zéro dépendance Maven RT-comops, zéro classe Kernel importée hors modules foundation dédiés | grep exhaustif poms + sources |
| P2 | **Hexagonal appliqué uniformément sur L0–L6** : mêmes packages, ports = interfaces, entités R2DBC isolées avec mappers, aucun adapter→adapter inter-modules | §2.3 |
| P3 | **DDD authentique** : agrégats riches à transitions d'état + événements de domaine collectés/purgés (`Delivery.java:201-523`) | §2.4 |
| P4 | **Pattern trust exemplaire** : chaque module appelant possède son port outbound, `tnt-trust-core` (L6) implémente les adapters et dépend *vers le bas* (`trust/.../adapter/out/{actor,billing,delivery,dispute,incident,organization,realtime,wallet}/`) — l'inversion de dépendance inter-modules est réellement câblée | arborescence trust + pom trust |
| P5 | **Hygiène de dette remarquable** : 14 TODO/FIXME/HACK sur tout le dépôt (dont 9 dans `tnt-market-back-core`), **zéro** `@Deprecated`, **zéro** `Thread.sleep` en production | greps §Méthode |
| P6 | **Gouvernance build sérieuse** : `maven-enforcer-plugin` (ban commons-logging/log4j/postgresql compile-scope), `dependencyManagement` centralisé épinglant chaque module, profils CI/release propres | `/pom.xml` |
| P7 | **Utilitaires transverses non dupliqués** : une seule implémentation de `PermissionMatcher` et de `KernelResponses`, toutes deux dans `tnt-common-core` | grep classes |
| P8 | **Toggle trust soigné** : exclusion du scan documentée ligne à ligne (`TiiBnTickApplication.java:48-58`) + fallbacks no-op `@ConditionalOnMissingBean` pour que l'app démarre trust désactivé | lecture bootstrap |
| P9 | **Réactif discipliné dans l'ensemble** : seulement 6 `.block()` en production sur ~2000 classes, et un commentaire de garde explicite dans `TntStartupRunner.java:82` (« Never .block() here ») | grep `.block(` |
| P10 | **Séparation write/read des schémas** : Liquibase JDBC (runtime-only) vs R2DBC runtime, 40 configs `@EnableR2dbcRepositories` scoping chaque module sur ses propres repositories | grep + enforcer |

---

## 4. Problèmes détectés — tableau récapitulatif

| ID | Problème | Localisation principale | Criticité |
|----|----------|------------------------|-----------|
| A1 | Violation de la règle de couches : `yow-i18n-kernel` (L0) → `tnt-common-core` (L1) | `foundation/yow-i18n-kernel/pom.xml:24-27` | **Élevé** |
| A2 | Violation de la règle de couches : `tnt-actor-core` (L2) → `tnt-incident-core` (L3) | `identity/tnt-actor-core/pom.xml:62` | **Élevé** |
| A3 | `.block()` dans du code de production réactif (6 occurrences, dont un aspect AOP transversal) | `TenantValidationAspect.java:74` et 5 autres | **Élevé** |
| A4 | Couche `coreBackend/` (18 modules) absente de la doc d'architecture, standards dégradés | `pom.xml:132-135`, `coreBackend/**` | **Élevé** |
| A5 | Contournement hexagonal : 9 contrôleurs accèdent directement à la persistance | `coreBackend/tnt-go-freelancer-point-back-core/.../adapter/in/web/*` | **Élevé** |
| A6 | Fuite de DTOs web dans la couche application (services retournant des `*Response` web) | `AgencyRegistryService.java:6-7`, `MissionService.java:9` | Moyen |
| A7 | Couverture de test très faible ; **zéro test** sur tout `coreBackend/`, `tnt-platform-gateway-core` (82 classes), `tnt-hrm-core` ; gate JaCoCo désactivée | comptage A7 | **Élevé** |
| A8 | God classes / classes surdimensionnées (772, 768, 596, 588 lignes) | `LogisticTrustEvent.java`, `MissionService.java`… | Moyen |
| A9 | `@RequirePermission` défini dans un package `adapter/in/web` mais consommé par la couche application de tous les modules | `roles/adapter/in/web/RequirePermission` | Moyen |
| A10 | Triple mécanisme d'enregistrement de beans redondant + doc/`TntCoreConfig` désynchronisés (import mort, commentaire erroné) | `TntCoreConfig.java:7,43` | Moyen |
| A11 | Configuration dupliquée : 10+ beans `ObjectMapper` et 16 classes de config Kafka quasi identiques par module | A11 | Moyen |
| A12 | Contrat de bean caché : 10+ modules référencent le qualifier `kernelWebClient` défini uniquement dans bootstrap | `KernelBridgeConfig.java:57` | Moyen |
| A13 | Responsabilités potentiellement dupliquées entre couche billing/sync core et sous-modules agency (`tnt-agency-sync-core` vs `tnt-sync-core`, `tnt-agency-billing-core` vs `billing/*`) | `coreBackend/tnt-agency-back-core/*` | Moyen |
| A14 | Fuites Spring dans `domain/` : `@ResponseStatus` sur ~12 exceptions, `@Component` sur 2 services de domaine, `MediaType` dans un modèle | A14 | Faible |
| A15 | Hygiène du dépôt : répertoire `BOOT-INF/` (jar décompressé) à la racine, code commenté dans bootstrap, doc CLAUDE.md périmée (« ~34 modules ») | racine du dépôt | Faible |
| A16 | `tnt-link-back-core` non dimensionné pour sa cible (« cartes temps réel, millions d'utilisateurs ») : requête bbox non spatiale et **sans LIMIT**, N+1 sur le batch, aucun cache, aucun push temps réel (dépendance `tnt-realtime-core` déclarée mais jamais utilisée) | `NetworkNodeR2dbcRepository.java:17-20`, `NetworkNodeController.java:109-128` | **Élevé** |
| A17 | Dépendances Maven déclarées jamais importées : 5 des 12 dépendances internes de `tnt-link-back-core` (`tnt-realtime-core`, `tnt-incident-core`, `tnt-sync-core`, `tnt-route-core`, `tnt-organization-core` — 0 import chacune) | `coreBackend/tnt-link-back-core/pom.xml` | Faible |

---

## 5. Détail des problèmes avec preuves

### A1 — `yow-i18n-kernel` (L0) dépend de `tnt-common-core` (L1) — Élevé

**Preuve :** `foundation/yow-i18n-kernel/pom.xml:24-27` :
```xml
<dependency>
    <groupId>com.yowyob.tiibntick.core</groupId>
    <artifactId>tnt-common-core</artifactId>
</dependency>
```
**Impact :** CLAUDE.md qualifie la règle de couches de « stricte » (« a module must never declare a Maven dependency on another module in a strictly higher-numbered layer »). Or L0 est décrit comme candidat à migration vers le Kernel Yowyob : cette dépendance vers un module L1 spécifique TiiBnTick **rend la migration promise impossible sans refactoring** et inverse la fondation (le socle dépend de ce qui est construit dessus). Le build fonctionne (Maven réordonne le reactor), ce qui masque la dérive.
**Recommandation :** extraire les types de `tnt-common-core` réellement utilisés par `yow-i18n-kernel` vers `yow-i18n-kernel` lui-même (ou un micro-module L0 partagé), ou requalifier officiellement `yow-i18n-kernel` en L1 dans le pom racine et CLAUDE.md. Ajouter une règle enforcer (`bannedDependencies` par groupe de modules ou ArchUnit au niveau reactor) pour rendre la règle de couches exécutable et non purement documentaire.

### A2 — `tnt-actor-core` (L2) dépend de `tnt-incident-core` (L3) — Élevé

**Preuve :** `identity/tnt-actor-core/pom.xml:62` (`<artifactId>tnt-incident-core</artifactId>`), consommé par `identity/tnt-actor-core/src/main/java/com/yowyob/tiibntick/core/actor/adapter/out/incident/ActorReputationPortAdapter.java`.
**Impact :** violation directe de la règle du projet, et du pattern que le projet applique pourtant ailleurs correctement (cf. `tnt-trust-core`). Ironique : `tnt-delivery-core`, `tnt-route-core`, `tnt-resource-core`, `tnt-billing-wallet` (couches ≥ L3) dépendent légitimement de `tnt-incident-core` vers le bas — mais `tnt-actor-core` (L2) le fait vers le haut. Risque de cycle à terme (incident → actor est un besoin plausible).
**Recommandation :** appliquer le pattern trust : `tnt-actor-core` conserve un port `out` (ex. `IActorReputationPort` avec ses propres types), et **`tnt-incident-core` fournit l'adapter** et prend la dépendance vers `tnt-actor-core` (L3 → L2, légal). Alternativement, si la réputation basée sur les incidents est un concept incident, déplacer l'adapter entier dans `tnt-incident-core`.

### A3 — `.block()` en production réactive (6 occurrences) — Élevé

**Preuves :**
| Fichier | Ligne | Contexte |
|---|---|---|
| `foundation/tnt-common-core/src/main/java/com/yowyob/tiibntick/common/aop/TenantValidationAspect.java` | 74 | `.block()` **dans un aspect AOP transversal de validation tenant** |
| `foundation/tnt-auth-core/.../application/service/KernelPublicKeyProvider.java` | 57 | `.block(Duration.ofSeconds(15))` |
| `identity/tnt-administration-core/.../service/TntAdministrationApplicationService.java` | 305 | `mono.block()` |
| `logistics/tnt-sync-core/.../adapter/in/kafka/EntityChangedEventConsumer.java` | 125 | `.block(Duration.ofSeconds(30))` |
| `logistics/tnt-dispute-core/.../adapter/in/messaging/DisputeEventConsumer.java` | 101 | `.block()` **sans timeout** |
| `logistics/tnt-dispute-core/.../adapter/out/persistence/DisputeRepositoryAdapter.java` | 69 | `.block(Duration.ofSeconds(6))` |

**Impact :** dans une application WebFlux, un `.block()` sur un thread event-loop lève `IllegalStateException` (`block()/blockFirst()/blockLast() are blocking, which is not supported in thread reactor-http-nio-*`) ou, s'il s'exécute sur un thread Kafka/boundedElastic, **gèle le thread consommateur** (30 s pour sync-core) et détruit le débit. Le cas le plus grave est `TenantValidationAspect.java:74` : un aspect de `tnt-common-core` potentiellement tissé sur des méthodes appelées dans des chaînes réactives — bombe à retardement dépendant du thread d'exécution. `DisputeEventConsumer.java:101` bloque **sans timeout** : un incident Kernel/DB peut suspendre le consumer indéfiniment. À noter : le projet connaît le problème (`TntStartupRunner.java:82` commente explicitement « Never .block() here »), la règle n'est simplement pas appliquée partout.
**Recommandation :** (1) refactorer l'aspect en interceptor réactif (retourner le `Mono` décoré plutôt que bloquer) — priorité 1 ; (2) dans les consumers Kafka, chaîner le traitement réactif à l'acknowledgment (reactor-kafka `receive().flatMap(...)`) au lieu de bloquer ; (3) `KernelPublicKeyProvider` : précharger/cacher la clé au démarrage (startup runner asynchrone) plutôt que bloquer à la demande ; (4) ajouter BlockHound en profil test pour empêcher toute régression.

### A4 — Couche `coreBackend/` non documentée, non gouvernée — Élevé

**Preuves :** `pom.xml:132-135` déclare 4 modules `coreBackend/*` absents de la cartographie L0–L7 de CLAUDE.md (qui annonce « ~34 modules » ; le reactor en compte 52). `coreBackend/tnt-agency-back-core/pom.xml:30-43` agrège 14 sous-modules supplémentaires. `tnt-bootstrap/pom.xml` dépend de l'intégralité (y compris `tnt-agency-eventing-core`… `tnt-agency-sync-core`).
**Impact :** (1) une couche représentant ~35 % des modules échappe à la règle de layering documentée — quelle est sa position ? Les poms montrent qu'elle dépend de L1→L5, donc de facto « L6-bis », mais rien ne l'écrit ni ne l'outille ; (2) le périmètre du monolithe change silencieusement : les BFF Agency/Go/Link/Market sont maintenant embarqués dans la même application que le cœur (surface de déploiement, temps de démarrage, couplage de release) ; (3) c'est précisément dans cette couche que se concentrent les violations hexagonales (A5, A6) et l'absence totale de tests (A7) — l'absence de gouvernance produit une architecture à deux vitesses.
**Recommandation :** documenter officiellement la couche (position, règles de dépendance, droit ou non de définir des schémas propres), trancher si ces backends doivent être déployables séparément (auquel cas ils ne devraient pas être des dépendances obligatoires de `tnt-bootstrap` mais activables par profil), et imposer les mêmes standards (tests, hexagonal) avant toute nouvelle extension.

### A5 — Contrôleurs accédant directement à la persistance (bypass des ports) — Élevé

**Preuve :** 9 contrôleurs de `coreBackend/tnt-go-freelancer-point-back-core/src/main/java/com/yowyob/tiibntick/core/gofp/adapter/in/web/` importent `...adapter.out.persistence.*` ou `infrastructure.persistence.*` : `AnnouncementCoreController`, `DeliveryNeedCoreController`, `SubscriptionCoreController`, `PricingCoreController`, `RelayDepositCoreController`, `EvaluationCoreController`, `CommissionCoreController`, `RelayHubSubscriptionCoreController`, `DeliveryCoreController` ; plus `coreBackend/tnt-agency-back-core/tnt-agency-org-core/.../hubops/adapter/in/messaging/InventoryHubConsumer.java`.
**Impact :** adapter `in` → adapter `out` sans passer par un port ni un service applicatif : logique métier dans les contrôleurs, aucune couture de test, invariants du domaine contournables, et rupture du contrat architectural que les 34 modules cœur respectent. Aucune occurrence de ce pattern n'existe hors `coreBackend/` — la dérive est localisée mais récente et donc réversible à faible coût.
**Recommandation :** introduire pour chaque contrôleur un use-case `application/port/in` + service, comme dans les modules cœur. Verrouiller par un test ArchUnit global (`noClasses().that().resideInAPackage("..adapter.in..").should().dependOnClassesThat().resideInAPackage("..adapter.out..")`).

### A6 — DTOs web fuités dans la couche application — Moyen

**Preuves :** `coreBackend/tnt-agency-back-core/tnt-agency-org-core/.../application/service/AgencyRegistryService.java:6-7` importe `adapter.in.web.dto.AgencyRegistryResponse` / `AgencySettingsResponse` ; `coreBackend/tnt-agency-back-core/tnt-agency-assignment-core/.../application/service/MissionService.java:9` importe `adapter.in.web.dto.MissionResponse`. Le pattern se répète dans ~15 services de `coreBackend/` (intake, billing, commission, fleet, inbox, onboarding, sync, hubops…).
**Impact :** la couche application dépend du format de l'API web : impossible de réutiliser ces use-cases depuis Kafka/scheduler sans traîner les DTOs REST, et tout changement de contrat web se propage dans la logique applicative. Les modules cœur (delivery, wallet, invoice…) sont **sains** sur ce point (leurs seuls imports `adapter.in.web` sont l'annotation `@RequirePermission` — cf. A9).
**Recommandation :** faire retourner aux services des modèles applicatifs/domaine (ou des records de résultat définis dans `application/port/in`), et mapper vers les `*Response` dans les contrôleurs — c'est déjà la convention du reste du dépôt.

### A7 — Couverture de test très faible, nulle sur des pans entiers — Élevé

**Preuves (comptage fichiers `src/test` vs `src/main`) :** `coreBackend/tnt-market-back-core` : **0 test / 170 classes** ; `tnt-platform-gateway-core` : **0 / 82** (module qui gère les API keys et le périmètre de sécurité plateforme !) ; `business/tnt-hrm-core` : 0 / 25 ; les 14 sous-modules agency : 0 test chacun. Meilleurs élèves : `tnt-trust-core` 14/91, `tnt-billing-dsl` 8/54. La gate de couverture JaCoCo est présente mais commentée dans le pom racine (confirmé par CLAUDE.md).
**Impact :** le risque est maximal exactement là où l'architecture est la plus faible (coreBackend) et là où l'enjeu sécurité est le plus fort (platform-gateway : authentification des backends, rotation d'API keys, scopes — 0 test). Les refactorings recommandés par cet audit (A3, A5, A6) seront difficiles à sécuriser sans filet.
**Recommandation :** prioriser des tests sur `tnt-platform-gateway-core` (matching de scopes, chaîne de sécurité `@Order(10)`, rotation de clés), réactiver la gate JaCoCo à un seuil bas mais non nul (ex. 30 %) avec ratchet progressif, exiger des tests pour tout nouveau module coreBackend.

### A8 — God classes / classes surdimensionnées — Moyen

**Preuves :**
- `trust/tnt-trust-core/.../domain/model/valueobject/LogisticTrustEvent.java` : **772 lignes**, ~25 fabriques statiques (`forDeliveryProof` l.104 … `forParcelChainResumed` l.654). Point positif : la classe est autonome (imports limités à ses propres enums + `java.*`), donc pas de couplage inter-modules — mais chaque nouveau type d'événement ancré la fait encore grossir.
- `coreBackend/tnt-agency-back-core/tnt-agency-assignment-core/.../application/service/MissionService.java` : **768 lignes** — service applicatif fourre-tout (création, cycle de vie, requêtes, mapping DTO).
- `logistics/tnt-dispute-core/.../domain/model/Dispute.java` : 596 lignes ; `Delivery.java` : 564 ; `MarketOrderApplicationService.java` : 588 ; `FreelancerOrganization.java` : 554.
**Impact :** pour les agrégats (Dispute, Delivery), la taille reflète une vraie richesse métier — acceptable mais à surveiller (extraire des policies/état). Pour `MissionService` et `MarketOrderApplicationService`, c'est le symptôme du transaction-script de coreBackend. Pour `LogisticTrustEvent`, chaque module consommateur du trust ajoute sa fabrique dans la même classe : goulet de contention Git et violation OCP.
**Recommandation :** scinder `LogisticTrustEvent` en fabriques par domaine (ex. `TrustEventFactories.delivery()`, `.incident()`, …) ou passer à un builder + catalogues de types ; découper `MissionService` par use-case (pattern déjà en place dans `tnt-delivery-core` : `DeliveryLifecycleService` vs `DeliveryAnnouncementService`).

### A9 — `@RequirePermission` placé dans `adapter/in/web` de tnt-roles-core — Moyen

**Preuves :** `logistics/tnt-delivery-core/.../application/service/DeliveryLifecycleService.java:14`, `billing/tnt-billing-wallet/.../application/service/WalletService.java:10`, `billing/tnt-billing-invoice/.../application/service/InvoiceService.java:8` importent tous `com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission`.
**Impact :** l'annotation de sécurité transverse — vocabulaire déclaré du projet — vit dans un package *adapter web* d'un autre module, si bien que **toutes les couches application du dépôt dépendent formellement d'un package adapter**. C'est le principal « faux positif hexagonal » du projet : le grep des fuites web pointe massivement sur cette seule classe.
**Recommandation :** déplacer l'annotation (et son aspect) vers `com.yowyob.tiibntick.core.roles.api` ou `application.annotation` dans `tnt-roles-core`, en conservant un alias déprécié le temps de la migration mécanique (sed sur les imports).

### A10 — Assemblage bootstrap : triple mécanisme et doc désynchronisée — Moyen

**Preuves :** (1) `TiiBnTickApplication.java:46-47` : scan global `com.yowyob.tiibntick` qui découvre déjà toutes les `@Configuration` des modules — rendant les `@Import` de `TntCoreConfig` largement redondants pour les modules cœur ; (2) mais les modules `com.yowyob.kernel.*` (yow-event/i18n-kernel) sont **hors du scan** et dépendent exclusivement de leurs `AutoConfiguration.imports` ; (3) `TntCoreConfig.java:7` importe `MarketBackCoreConfig` **sans l'utiliser** (absent du tableau `@Import` l.31-44) ; (4) le commentaire `TntCoreConfig.java:43` étiquette `GoFreelancerPointCoreConfig` « L6 Core Backend Market » (mauvais module, mauvaise couche) ; (5) la javadoc (`TntCoreConfig.java:17`, repris dans CLAUDE.md) affirme que la classe « imports and wires all module configs » — faux ; (6) code mort commenté : `TntCoreConfig.java:67-70`, `TiiBnTickApplication.java:63-66` (`@EnableR2dbcRepositories` désactivé).
**Impact :** trois chemins d'activation différents selon le module = diagnostic difficile (« pourquoi ce bean existe-t-il ? »), risque de double enregistrement ou d'oubli (ex. `MarketBackCoreConfig` est-il activé par scan ? par imports ? l'import mort suggère une intention non aboutie). L'exclusion regex du package trust (`TiiBnTickApplication.java:56-58`) est correcte mais fragile : un renommage de package la casse silencieusement et ré-active trust inconditionnellement.
**Recommandation :** choisir **un** mécanisme nominal — l'auto-configuration `AutoConfiguration.imports` par module (déjà majoritaire) — et restreindre le `@ComponentScan` au package bootstrap ; supprimer l'import mort et corriger les commentaires ; ajouter un test de contexte qui vérifie que trust désactivé ⇒ aucun bean `com.yowyob.tiibntick.core.trust.*`.

### A11 — Configuration dupliquée par module (ObjectMapper, Kafka) — Moyen

**Preuves :** 10+ beans `ObjectMapper` qualifiés quasi identiques (`MediaCoreConfig.java:55`, `IncidentCoreConfig.java:57`, `GeoCoreConfig.java:38`, `TntDisputeCoreConfig.java:38`, `DeliveryModuleConfig.java:51`, `RouteCoreConfig.java:51`, `TntBillingReportAutoConfiguration.java:37`, `BillingTemplatesConfig.java:37`, `DslBillingAutoConfiguration.java:55`, `WalletModuleConfig.java:63`) ; 16 classes de configuration Kafka définissant chacune leurs producteurs (`IncidentKafkaConfig`, `SyncKafkaConfig`, `DisputeKafkaConfig`, `KafkaRealtimeConfig`, `TntTpKafkaConfig`, `KafkaProducerConfig` (trust), etc.), en plus des fallbacks de `TntKafkaConfig` (bootstrap).
**Impact :** dérive de sérialisation probable (un module oublie `JavaTimeModule` ou un réglage de visibilité ⇒ bugs d'interop Kafka subtils entre modules) ; le contrat implicite « le fallback bootstrap doit rester synchronisé avec la config module » (documenté dans CLAUDE.md) est un couplage caché déjà matérialisé par `deliveryObjectMapper`/`deliveryKafkaProducer` (`TntKafkaConfig.java:67-68,110-111`).
**Recommandation :** fournir dans `tnt-common-core` une fabrique statique unique (`TntJson.mapper()` / `TntKafkaSenders.create(props)`) que chaque config module appelle — les qualifiers restent par module, la *configuration* devient unique. Réduire progressivement les fallbacks bootstrap à mesure que les qualifiers convergent.

### A12 — Contrat de bean caché `kernelWebClient` — Moyen

**Preuve :** le bean est défini uniquement dans `tnt-bootstrap/.../bridge/KernelBridgeConfig.java:57` mais référencé par qualifier dans 10+ modules (tnt-auth-core, tnt-common-core, tnt-platform-gateway-core, tous les modules business, tnt-billing-cost…).
**Impact :** aucun module n'est démarrable/testable seul sans recréer ce bean à la main ; le nom du qualifier est un contrat non typé, invisible à la compilation. Les modules coreBackend destinés (peut-être) à devenir des applications séparées hériteraient du même trou.
**Recommandation :** déplacer la définition du WebClient Kernel (avec ses properties `iwm.kernel.*`) dans une auto-configuration de `tnt-common-core` (`@ConditionalOnMissingBean`), bootstrap ne conservant que d'éventuels overrides.

### A13 — Responsabilités dupliquées cœur vs agency — Moyen

**Preuves :** `coreBackend/tnt-agency-back-core/tnt-agency-sync-core` (dépend de `tnt-sync-core`) recrée une couche sync spécifique agence ; `tnt-agency-billing-core` coexiste avec les 7 modules `billing/*` ; `tnt-agency-org-core` avec `tnt-organization-core` ; `tnt-agency-eventing-core` avec `yow-event-kernel`. Deux implémentations d'application de commissions (`tnt-agency-commission-core` vs logique pricing/cost billing).
**Impact :** frontière conceptuelle floue : qu'est-ce qui relève du *cœur* (multi-plateformes) vs du *BFF Agency* ? Sans règle écrite, la logique métier va se dupliquer (le signe avant-coureur est `MissionService` 768 lignes côté agency alors que `tnt-delivery-core` possède déjà le cycle de vie mission/livraison).
**Recommandation :** écrire la charte coreBackend (A4) avec la règle « un BFF orchestre les use-cases du cœur, il n'en réimplémente pas » ; auditer `tnt-agency-billing-core` et `tnt-agency-commission-core` pour rapatrier toute règle de calcul dans `billing/*`.

### A14 — Fuites Spring dans le domaine — Faible

**Preuves :** `@ResponseStatus`/`HttpStatus` sur des exceptions de domaine : `billing/tnt-billing-wallet/.../domain/exception/WalletNotFoundException.java:3-4` (idem `WalletFrozenException`, `InsufficientBalanceException`, `PaymentIntentNotFoundException`, `InvoiceNotFoundException`, `GeoNotFoundException`, `DelivererNotFoundException`, `FreelancerNotFoundException`, `TntThirdPartyNotFoundException`, 4 exceptions resource — ~12 fichiers) ; `@Component` sur des services de domaine : `identity/tnt-administration-core/.../domain/service/TntRoleTemplateRegistry.java:3`, `business/tnt-accounting-core/.../domain/service/OhadaChartOfAccountsInitializer.java:5` ; `MediaType` Spring dans `foundation/tnt-platform-gateway-core/.../domain/model/KernelRawResponse.java:3`.
**Impact :** limité (annotations passives), mais le domaine cesse d'être compilable sans spring-web, et le mapping exception→HTTP est décidé par le domaine au lieu de l'adapter. C'est la seule entorse à une pureté domaine par ailleurs très bonne (zéro `@Table`, zéro repository Spring Data dans `domain/`).
**Recommandation :** supprimer `@ResponseStatus` au profit des `@RestControllerAdvice`/handlers d'erreurs des adapters web (il en existe déjà) ; instancier les deux services de domaine via `@Bean` dans la config du module plutôt que `@Component`.

### A15 — Hygiène du dépôt — Faible

**Preuves :** répertoire `BOOT-INF/classes` à la racine du dépôt (jar Spring Boot décompressé ; ignoré par git mais pollue le workspace et les greps) ; `ENDPOINT_TEST_REPORT.md` à la racine ; blocs commentés dans `TntCoreConfig.java:67-70` et `TiiBnTickApplication.java:63-66` ; CLAUDE.md annonce « ~34 modules » et « tnt-bootstrap @Imports each module's @Configuration » alors que le reactor en compte 52 et que l'assemblage passe majoritairement par scan/auto-configuration (cf. A10) ; la couche coreBackend est absente du schéma L0–L7.
**Impact :** faible techniquement, mais la doc d'architecture étant le contrat d'équipe (et l'entrée des outils IA), sa péremption accélère les dérives constatées en A1/A2/A4.
**Recommandation :** supprimer `BOOT-INF/`, purger le code commenté, mettre à jour CLAUDE.md (couche coreBackend, mécanisme d'assemblage réel, compte de modules).

### A16 — `tnt-link-back-core` : architecture propre mais non dimensionnée pour « des millions d'utilisateurs » — Élevé

Le module est destiné à la cartographie/cartes temps réel à très grande échelle. Quatre constats bloquants pour cette cible :

1. **Requête de viewport non spatiale et sans limite.** `coreBackend/tnt-link-back-core/.../adapter/out/persistence/repository/NetworkNodeR2dbcRepository.java:17-20` :
```sql
SELECT * FROM tnt_link.network_nodes WHERE tenant_id = :tenantId
AND latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLng AND :maxLng
```
Colonnes `latitude`/`longitude` scalaires (pas de `geometry` PostGIS ni d'index GIST, alors que PostGIS est dans la stack et que `tnt-geo-core` l'utilise), **aucun `LIMIT`**, aucun clustering par niveau de zoom. Un viewport large (ou un client malveillant passant `-90/90/-180/180`) rapatrie *tous* les nœuds du tenant. L'endpoint `GET /nearby` (`NetworkNodeController.java:119-128`) n'impose ni taille maximale de bbox ni pagination.
2. **N+1 sur le batch.** `NetworkNodeController.java:109-116` : `getByRefIds` exécute `Flux.fromIterable(refIds).flatMap(refId -> queryUseCase.findByRefId(...))` — une requête SQL par refId au lieu d'une clause `IN`.
3. **Aucun cache, aucun push.** Zéro usage de Redis/Caffeine dans le module ; le « temps réel » repose sur du polling REST (`PUT /{nodeId}/location` écrit en base, les clients relisent), alors que la plateforme possède déjà `tnt-realtime-core` (SSE/WebSocket/STOMP + Redis) — déclaré dans le pom du module mais **jamais importé** (0 occurrence).
4. **Zéro test** (0 fichier dans `src/test`) pour un module à fort trafic attendu.

**Impact :** sous charge réelle, chaque déplacement de carte de chaque utilisateur déclenche un full-range-scan Postgres non borné ; le plan « millions d'utilisateurs » est irréaliste sans refonte de la couche requête. Le point positif : l'hexagonal étant respecté, la correction est localisée dans l'adapter de persistance et le contrôleur, sans toucher au domaine.
**Recommandation :** (1) colonne `geometry(Point,4326)` + index GIST et requête `ST_Within`/`ST_DWithin` (aligné sur `tnt-geo-core`) ; (2) `LIMIT` obligatoire + validation de la taille de bbox + clustering côté serveur au-delà d'un seuil ; (3) batch par `WHERE ref_id IN (:refIds)` ; (4) brancher la diffusion des positions sur `tnt-realtime-core` (la dépendance existe déjà) avec throttling par nœud ; (5) cache Redis court (1–5 s) sur les viewports chauds.

### A17 — Dépendances Maven déclarées jamais utilisées (link-back) — Faible

**Preuve :** `coreBackend/tnt-link-back-core/pom.xml` déclare 12 dépendances internes ; 5 n'ont **aucun** import correspondant dans le code (`tnt-realtime-core`, `tnt-incident-core`, `tnt-sync-core`, `tnt-route-core`, `tnt-organization-core` — vérifié par grep des packages).
**Impact :** temps de build et graphe de dépendances artificiellement gonflés, faux signal architectural (le pom suggère une intégration realtime qui n'existe pas — cf. A16.3).
**Recommandation :** purger via `mvn dependency:analyze` (à généraliser aux autres modules coreBackend), ou implémenter réellement l'intégration realtime prévue.

---

## 6. Recommandations priorisées

| Priorité | Action | Problèmes couverts | Effort estimé |
|---|---|---|---|
| **P1 — immédiat** | Éliminer le `.block()` de `TenantValidationAspect` (aspect → décorateur réactif) et ajouter un timeout à `DisputeEventConsumer.java:101` | A3 | 1–2 j |
| **P1 — immédiat** | Rendre la règle de couches exécutable : test ArchUnit reactor-wide (couches Maven + interdiction adapter.in→adapter.out et application→adapter.in.web) ; corriger A1 (i18n) et A2 (actor→incident, inversion façon trust) | A1, A2, A5, A6 | 3–5 j |
| **P2 — court terme** | Charte `coreBackend/` : position dans les couches, règle « BFF orchestre, ne réimplémente pas », activation par profil dans bootstrap ; refactorer les 9 contrôleurs gofp vers des use-cases | A4, A5, A13 | 1–2 sem |
| **P2 — court terme** | Tests sur `tnt-platform-gateway-core` (sécurité) puis réactivation de la gate JaCoCo avec ratchet | A7 | 1–2 sem |
| **P2 — court terme** | Durcir `tnt-link-back-core` avant montée en charge : requête bbox PostGIS + LIMIT + validation viewport, batch `IN`, diffusion via `tnt-realtime-core`, cache Redis court | A16, A17 | 1 sem |
| **P3 — moyen terme** | Consolider l'assemblage : un seul mécanisme (auto-configuration), scan restreint au bootstrap, suppression import mort/commentaires faux ; fabriques communes `ObjectMapper`/Kafka dans tnt-common-core ; auto-configuration du `kernelWebClient` dans tnt-common-core | A10, A11, A12 | 1 sem |
| **P3 — moyen terme** | Déplacer `@RequirePermission` hors de `adapter/in/web` ; purger `@ResponseStatus` du domaine ; scinder `LogisticTrustEvent` et `MissionService` | A8, A9, A14 | 3–5 j |
| **P4 — continu** | Mettre à jour CLAUDE.md/README (52 modules, coreBackend, assemblage réel), nettoyer BOOT-INF et le code commenté | A15 | 0,5 j |

---

## 7. Conclusion

TiiBnTick Core est un projet dont **le cœur (L0–L6) tient réellement ses promesses architecturales** : hexagonal appliqué et vérifiable, DDD riche et non cosmétique, frontière Kernel HTTP-only prouvée, dette marquée quasi nulle, et un pattern d'inversion de dépendance inter-modules (trust) qui est un modèle du genre. Les fondations de gouvernance (enforcer, dependencyManagement, conventions) existent et fonctionnent.

Le risque principal n'est pas l'existant mais la **dérive** : la règle de couches « stricte » n'étant qu'un texte, elle a déjà cédé deux fois (A1, A2) ; la couche coreBackend, née hors de la documentation, applique des standards inférieurs (bypass hexagonal, DTOs web en couche application, zéro test) qui, non corrigés, deviendront le modèle copié par les prochains modules. Les six `.block()` — dont un dans un aspect transversal — sont le seul danger d'exploitation immédiat.

La bonne nouvelle : chaque problème identifié a déjà sa solution *dans le dépôt lui-même* (le pattern trust pour A2, les use-cases delivery pour A5/A8, les handlers d'erreurs web pour A14). L'effort demandé est donc moins de l'invention que de l'**uniformisation outillée** : un test ArchUnit reactor-wide et la réactivation de la gate JaCoCo transformeraient les règles documentaires en règles exécutables, et verrouilleraient durablement la qualité déjà atteinte.

---
*Audit réalisé sur l'état du dépôt au 2026-07-16 (branche `master`, HEAD `c1c1732`). Toutes les localisations fichier:ligne ont été vérifiées dans le code source.*
