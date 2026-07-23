# Synthèse exécutive — Audit technique TiiBnTick Core

## Résumé exécutif

Sept audits spécialisés ont été menés sur l'intégralité du dépôt `tiibntick-core` : **52 modules Maven** (34 modules « historiques » L0–L7 **plus les 18 modules/sous-modules de `coreBackend/`** — `tnt-agency-back-core`, `tnt-go-freelancer-point-back-core`, `tnt-link-back-core`, `tnt-market-back-core`), soit ~2 800 fichiers Java, l'ensemble des configurations Spring/Docker/Liquibase, les scripts, les tests et la documentation.

**Verdict global : une fondation architecturale authentiquement solide, mais une exécution incomplète qui interdit toute mise en production ouverte en l'état.** Le cœur L0–L6 applique réellement l'architecture hexagonale et le DDD (agrégats riches, ports/adapters disciplinés, migration Kernel HTTP-only vérifiée). En revanche : des failles de sécurité critiques (paiements falsifiables, isolation tenant contournable), une architecture événementielle largement cassée (~20 topics consommés sans producteur), un scale-out multi-instances aujourd'hui **impossible** sans corruption de données, et une couche `coreBackend/` qui dérive hors de tous les standards du projet (zéro test, autorisation absente).

La cible « centaines de millions d'utilisateurs » n'est pas atteignable avec l'architecture actuelle, mais la trajectoire proposée (feuille de route P0→P2 ci-dessous, architecture cible détaillée dans l'[Audit n°7](audit-7-system-design.html)) est réaliste : durcir le monolithe modulaire d'abord, extraire ensuite les axes de charge (Link temps réel via BFF + fan-out geohash).

## Chiffres clés

<div class="stat-row">
<div class="stat crit"><div class="num">15+</div><div class="lbl">Constats critiques</div></div>
<div class="stat high"><div class="num">35+</div><div class="lbl">Constats élevés</div></div>
<div class="stat"><div class="num">52</div><div class="lbl">Modules audités</div></div>
<div class="stat"><div class="num">112/223</div><div class="lbl">Dépendances mortes</div></div>
<div class="stat"><div class="num">~20</div><div class="lbl">Topics Kafka orphelins</div></div>
<div class="stat"><div class="num">0</div><div class="lbl">Test sur coreBackend</div></div>
</div>

| Audit | Rapport | Problèmes relevés | Verdict en une phrase |
|---|---|---|---|
| n°1 | [Architecture globale](audit-1-architecture.html) | 17 | Cœur hexagonal/DDD authentique et discipliné ; risque = dérive de la couche `coreBackend/`. |
| n°2 | [Connexions inter-modules](audit-2-inter-modules.html) | 112 dép. mortes, 2 violations | Graphe acyclique et layering globalement tenu, mais 50 % des arêtes Maven sont mortes. |
| n°3 | [yow-event-kernel](audit-3-yow-event-kernel.html) | 22 | Module 100 % mort (0 import) ; toute la plateforme fait de l'événementiel ad-hoc à côté. |
| n°4 | [yow-i18n-kernel](audit-4-yow-i18n-kernel.html) | 24 | Quasi inutilisé ; projet mono-langue FR et mono-devise XAF de fait, jusque dans les API. |
| n°5 | [Kafka](audit-5-kafka.html) | 20 | Fondations saines (acks/idempotence) mais chaînes cassées : ~20 topics sans producteur. |
| n°6 | [Scalabilité](audit-6-scalabilite.html) | 27 (6 critiques) | Scale-out multi-instances impossible en l'état ; Link plafonne à ~10⁴ utilisateurs. |
| n°7 | [System Design & Sécurité](audit-7-system-design.html) | 25 | Conception saine, exécution sécurité incomplète ; P0 prérequis absolu avant prod. |

## Risques majeurs (transverses)

### 1. Sécurité des paiements et du multi-tenant — <span class="badge crit">Critique</span>

Les webhooks de paiement sont falsifiables (signature Stripe non vérifiée, MTN facultative, Orange absente) : n'importe qui peut marquer une facture comme payée. **Décision actée le 17/07/2026 :** plutôt que de patcher ces vérifications, TiiBnTick Core délègue entièrement les paiements et la facturation au Kernel (`payment-controller`, `payment-gateway-controller`, `billing-legacy-documents-controller`) — voir le [chantier dédié](remediation/workstream-payment-billing-kernel-delegation.html). L'identité du tenant est par ailleurs acceptée depuis l'en-tête client `X-Tenant-Id` sur 68 endpoints et ~139 requêtes `findById` ne filtrent pas par tenant (IDOR cross-tenant). Environ 40 contrôleurs de mutation n'ont aucun contrôle d'autorisation — le pire sur les backends produits (`market` 0/9, `gofp` 0/10, `link` 2/9) placés derrière un `permitAll()`. **Impact métier : fraude financière directe, fuite de données inter-clients.** → Détails et correctifs : [Audit n°7](audit-7-system-design.html), [Phase 0](remediation/phase-0-critical.html).

### 2. Chaînes événementielles cassées — <span class="badge crit">Critique</span>

Des flux métier entiers ne fonctionnent pas de bout en bout parce que producteurs et consommateurs n'utilisent pas les mêmes noms de topics (`mission.status-changed` vs `mission.status.changed`, `wallet.payment-confirmed` vs `wallet.payment.confirmed`, etc.) et que des événements attendus ne sont jamais émis (`mission.completed`, `invoice.paid`, `compensation.paid`). Facturation, comptabilité, boucle litige→remboursement, temps réel mission et ERP Agency sont concernés. `auto.create.topics.enable=true` masque le problème en créant silencieusement les topics orphelins. Par ailleurs `yow-event-kernel` — qui contient précisément l'outbox, la DLQ et l'idempotence qui manquent partout — est mort (0 import). → [Audit n°3](audit-3-yow-event-kernel.html), [Audit n°5](audit-5-kafka.html).

### 3. Scale-out impossible en l'état — <span class="badge crit">Critique</span>

Lancer une deuxième instance aujourd'hui **corromprait des données** : séquence de références litiges en `AtomicInteger` par JVM, ~10 `@Scheduled` sans verrou distribué, réconciliation wallet et caches RBAC en `ConcurrentHashMap` locaux. Le pool R2DBC primaire est limité à 10 connexions pour ~30 schémas sur un PostgreSQL unique ; aucun read replica, aucune pagination sur les listings à forte volumétrie, SPOF sur chaque brique d'infra (Kafka mono-broker RF=1, Redis, MinIO, Postgres). Les appels au Kernel Yowyob n'ont ni timeout ni circuit breaker (hors trust). → [Audit n°6](audit-6-scalabilite.html).

### 4. Link : le temps réel cartographique n'existe pas encore — <span class="badge high">Élevé</span>

`tnt-link-back-core` — destiné à des millions d'utilisateurs avec des mises à jour temps réel individualisées par carte — fonctionne aujourd'hui à 100 % en **polling REST** de bounding-box, sans WebSocket/SSE, sans Kafka/Redis, sans requête PostGIS ni index spatial. Plafond estimé : ~10⁴ utilisateurs. La recommandation consolidée (justifiée dans l'[Audit n°7](audit-7-system-design.html)) : **oui au BFF Link** consommant tiibntick-core et servant frontend + mobile, avec abonnements par tuiles geohash et fan-out borné réutilisant le moteur `tnt-realtime-core` (Redis pub/sub multi-instances) déjà présent dans le dépôt — plutôt que construire un second moteur temps réel.

### 5. Dérive de la couche coreBackend — <span class="badge high">Élevé</span>

Les 4 backends produits (~630 classes, un tiers du réacteur) sont hors du modèle de couches documenté, sans aucun test, avec des standards dégradés (contrôleurs court-circuitant les ports, DTOs web retournés par les services, RBAC déclaré mais jamais appliqué). Chaque correctif a pourtant déjà son modèle exemplaire ailleurs dans le dépôt (trust, sync, realtime). → [Audit n°1](audit-1-architecture.html), [Audit n°2](audit-2-inter-modules.html).

### 6. Gouvernance et i18n — <span class="badge med">Moyen</span>

50 % des dépendances Maven inter-modules sont mortes ; l'assemblage réel (`@ComponentScan` global) ne correspond pas à la doc (`@Import` par module) ; aucune règle d'architecture n'est exécutable en CI. Côté i18n : utilisateurs recevant « ⚠ Missing translation » par SMS/Email/Push, 6 classes `Money` dupliquées aux arrondis divergents, devise XAF câblée jusque dans les signatures d'API. → [Audit n°2](audit-2-inter-modules.html), [Audit n°4](audit-4-yow-i18n-kernel.html).

## Points forts confirmés

- **Hexagonal/DDD réels** sur L0–L6 : agrégats riches, ports = interfaces, domaine sans Spring ni entités R2DBC, aucun adapter→adapter inter-modules.
- **Migration Kernel HTTP-only vérifiée** : zéro dépendance Maven RT-comops résiduelle.
- **Modules exemplaires réutilisables comme modèles internes** : `tnt-trust-core` (ports inversés, circuit breakers, retry `SKIP LOCKED`), `tnt-sync-core` (seule vraie DLQ), `tnt-realtime-core` (fan-out Redis multi-instances), outbox `SKIP LOCKED`.
- **Kafka bien configuré à la base** : acks=all + idempotence producteur quasi partout, auto-commit désactivé, provisioning central des topics (RF 3 en prod).
- **Sécurité plateforme** : API keys BCrypt + rotation + audit, double chaîne d'authentification propre.
- **Hygiène de code** : dette marquée quasi nulle (14 TODO), 388 index en base, Dockerfile production-grade, graphe de dépendances acyclique.

## Feuille de route priorisée

| Priorité | Horizon | Actions | Impact attendu |
|---|---|---|---|
| **P0 — bloquant prod** | 3–5 sem. | **Mise à jour du 17/07/2026 :** délégation complète des paiements et de la facturation au Kernel (`payment-controller`/`payment-gateway-controller`/`billing-legacy-documents-controller`) — supprime la vulnérabilité webhook au lieu de la patcher ; tenant depuis le JWT uniquement + filtre tenant sur les requêtes sensibles ; autorisation sur les ~40 contrôleurs non protégés ; référentiel unique des topics dans `tnt-common-core` + alignement des noms + `auto.create.topics=false` ; correction des désérialiseurs par défaut ; les 6 correctifs multi-instances (séquence litiges en DB, ShedLock, caches distribués) ; messages i18n cassés en production. Détail exhaustif et cases à cocher : [Phase 0 du plan d'implémentation](remediation/phase-0-critical.html). | Élimine la fraude paiement à la racine, l'IDOR cross-tenant, répare les flux métier cassés, débloque le scale-out. |
| **P1 — durcissement** | 1–3 mois | BFF Link + temps réel par tuiles geohash ; RLS multi-tenant ; résilience Kernel généralisée ; DLQ/outbox/idempotence Kafka ; pagination et read replica ; unification `Money` et résolution de locale. Détail : [Phase 1](remediation/phase-1-hardening.html). | Couvre le besoin produit Link à l'échelle ; élimine les IDOR structurellement ; rend le monolithe observable. |
| **P2 — architecture cible** | 3–18 mois, sous condition de traction | Kubernetes + HPA, cluster Kafka, réplicas et partitionnement, extraction sélective (wallet, temps réel) si la charge le justifie. Détail : [Phase 2](remediation/phase-2-target-architecture.html). | Trajectoire réaliste vers des centaines de millions d'utilisateurs sans sur-investissement prématuré. |

**Séquencement recommandé :** rester en monolithe modulaire durci (P0+P1) avant toute extraction microservices ; n'extraire en P2 que ce que la charge mesurée justifie (Link temps réel en premier). Chaque recommandation est justifiée, localisée et chiffrée dans le rapport d'audit correspondant.

## Conclusion

Le projet a une colonne vertébrale rare pour un projet de cette taille : une architecture hexagonale réellement appliquée, un graphe de modules acyclique et des modèles internes exemplaires. Ce qui manque n'est pas de la conception mais de l'**achèvement** : brancher ce qui est débranché (événements, i18n, event-kernel), protéger ce qui est exposé (paiements, tenant, coreBackend), et lever les verrous connus du scale-out. Le P0 est un prérequis absolu avant toute production ouverte ; avec P0+P1 réalisés, la plateforme est saine pour croître, et P2 trace une route réaliste vers l'échelle visée.
