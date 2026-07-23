# RBAC S5 — tnt-roles-core : propriété locale + provisioning Kernel par outbox

**Audit n°6 · S5** — proposition d'architecture, à valider avant implémentation.
Statut : **proposition**, aucun code écrit à ce stade.

## 1. Diagnostic de l'existant

### 1.1 Ce qui fonctionne déjà (chemin d'écriture)

Le Kernel expose un `role-controller` complet (7 endpoints, `docs/kernel-api/endpoints.md:11949`) :
`GET/POST /api/roles`, `GET/DELETE /api/roles/{id}`, `GET/POST /api/roles/assignments`,
`DELETE /api/roles/assignments/{id}`. `tnt-roles-core` l'utilise déjà :

- `KernelRoleProvisioningAdapter` (`adapter/out/kernel/`) → `POST /api/roles`, idempotent
  (409 CONFLICT avalé silencieusement).
- `KernelRoleAssignmentAdapter` → `POST /api/roles/assignments` via `kernelTpWebClient`
  (bearer forwarding de l'admin appelant).
- `TntRoleInitializationService` provisionne les 9 rôles canoniques (`TntRole` enum) au
  démarrage (`ApplicationReadyEvent`), et `tnt-administration-core` les reprovisionne par
  tenant à l'onboarding.

Donc : **les rôles et affectations TiiBnTick sont déjà provisionnés dans le Kernel
aujourd'hui.** La prémisse initiale de S5 (« aucun endpoint Kernel de persistance de
rôles n'existe ») était fausse.

### 1.2 Le vrai trou : chemin de lecture

`RoleRepository` et `UserRoleAssignmentRepository` (ports utilisés par
`LocalReactivePermissionResolver` — mode `LOCAL`, le défaut — et par `TntRoleService`)
n'ont **aucune implémentation persistante** : seul le fallback
`InMemoryRoleRepository`/`InMemoryUserRoleAssignmentRepository` existe
(`TntRolesAutoConfiguration.java:73-86`).

Or **rien n'écrit jamais dedans** : `TntRoleAssignmentService.assignRole()`
(`application/service/TntRoleAssignmentService.java:53`) appelle uniquement
`assignmentPort.assignRole(...)` → HTTP vers le Kernel. Aucune écriture locale.

Conséquence en configuration par défaut (mode `LOCAL`) :
`LocalReactivePermissionResolver.resolvePermissions()` commence par
`assignmentRepository.findByTenantIdAndUserId(...)` — toujours vide → le Flux ne
produit jamais rien → **toute résolution de permission renvoie un ensemble vide**,
silencieusement, y compris pour les 9 rôles canoniques. Le "Niveau 2" (fallback sur
`TntRoleDefinitionRegistry`) documenté dans le Javadoc de la classe ne se déclenche
jamais en pratique puisqu'il dépend de trouver d'abord une assignation.

`RemoteReactivePermissionResolver` (mode `REMOTE`/`HYBRID`) est un stub qui renvoie
toujours `Set.of()`, avec un commentaire indiquant que le Kernel n'exposait pas encore
d'endpoint de résolution — obsolète depuis que `GET /api/roles/assignments?userId=` +
`GET /api/roles` existent.

Le garde-fou fail-fast prod (`rbacPersistentAdapterProdGuard`,
`TntRolesAutoConfiguration.java:99-125`) empêche bien de démarrer en prod avec le
fallback en mémoire actif — mais brancher naïvement un simple adaptateur R2DBC derrière
`RoleRepository`/`UserRoleAssignmentRepository` **ne suffirait pas** : rien ne le
peuplerait, puisque le chemin d'écriture actuel ne cible que le Kernel.

## 2. Principe directeur (validé avec l'utilisateur)

Séparation explicite des responsabilités entre deux systèmes :

| Système | Responsabilité |
|---|---|
| **KSM_KERNEL** | Identités globales, registre RBAC commun à tout l'écosystème Yowyob, API de provisioning et de consultation (`role-controller`). Source de vérité pour ce qui doit être visible par les autres backends de l'écosystème (Agency, Go, Link, Market, Point Relais...). |
| **tnt-roles-core** | Modèle RBAC métier TiiBnTick : création, modification, suppression **autonomes** des rôles et permissions métier, règles d'affectation. Source de vérité pour TiiBnTick lui-même — les `@RequirePermission` de tout le repo ne doivent **jamais** dépendre de la disponibilité du Kernel pour un chemin aussi chaud. Synchronise vers le Kernel de façon **asynchrone et fiable** ce qui doit être exposé à l'écosystème.

Contrainte explicite : **pas de double écriture synchrone**. `tnt-roles-core` ne doit
jamais bloquer une opération métier sur la disponibilité HTTP du Kernel. D'où le choix
outbox + worker plutôt qu'un simple "écrire aux deux endroits en séquence".

## 3. Modèle cible

### 3.1 tnt-roles-core devient un module à schéma propre (R2DBC + Liquibase)

Précédent direct dans le repo : `tnt-platform-gateway-core`, premier module L1 avec son
propre schéma (`docs/CLAUDE.md` §tnt-platform-gateway-core). Même structure de
changelog (`db/changelog/tnt-roles-master.yaml` + `db/changelog/changes/NNN_*.sql`,
format `--liquibase formatted sql`).

```sql
-- 001_create_roles_table.sql
CREATE TABLE IF NOT EXISTS tnt_roles (
    id              UUID            NOT NULL PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    code            VARCHAR(64)     NOT NULL,
    name            VARCHAR(120)    NOT NULL,
    scope_type      VARCHAR(16)     NOT NULL,
    permissions     TEXT[]          NOT NULL DEFAULT '{}',
    system_role     BOOLEAN         NOT NULL DEFAULT false,
    editable        BOOLEAN         NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, code)
);

-- 002_create_user_role_assignments_table.sql
CREATE TABLE IF NOT EXISTS tnt_user_role_assignments (
    id              UUID            NOT NULL PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    user_id         UUID            NOT NULL,
    role_id         UUID            NOT NULL REFERENCES tnt_roles(id),
    scope_type      VARCHAR(16)     NOT NULL,
    scope_id        UUID            NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, user_id, role_id, scope_type, scope_id)
);
CREATE INDEX idx_tura_tenant_user ON tnt_user_role_assignments(tenant_id, user_id);

-- 003_create_role_provisioning_outbox_table.sql
CREATE TABLE IF NOT EXISTS tnt_role_provisioning_outbox (
    id                  UUID            NOT NULL PRIMARY KEY,
    operation           VARCHAR(24)     NOT NULL,   -- PROVISION_ROLE | ASSIGN_ROLE | REVOKE_ASSIGNMENT
    aggregate_type      VARCHAR(16)     NOT NULL,   -- ROLE | ASSIGNMENT
    aggregate_id        UUID            NOT NULL,   -- tnt_roles.id or tnt_user_role_assignments.id
    tenant_id           UUID            NOT NULL,
    payload             JSONB           NOT NULL,   -- snapshot needed to replay the Kernel call
    status              VARCHAR(16)     NOT NULL DEFAULT 'PENDING',
    attempt_count       INT             NOT NULL DEFAULT 0,
    last_error          TEXT,
    kernel_ref_id       UUID,                       -- Kernel-side id once PROVISIONED
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    next_attempt_at     TIMESTAMPTZ     NOT NULL DEFAULT now(),
    processed_at        TIMESTAMPTZ
);
CREATE INDEX idx_outbox_pending ON tnt_role_provisioning_outbox(status, next_attempt_at)
    WHERE status IN ('PENDING', 'RETRYING');
```

`RoleRepository`/`UserRoleAssignmentRepository` gagnent enfin une implémentation
R2DBC réelle (`R2dbcRoleRepository`, `R2dbcUserRoleAssignmentRepository`,
`adapter/out/persistence/`), remplaçant les fallbacks en mémoire par défaut — le
garde-fou `rbacPersistentAdapterProdGuard` passe alors pour de vraies raisons.

`LocalReactivePermissionResolver` (mode `LOCAL`, qui reste le défaut) devient enfin
correct : il lit un état réellement peuplé, localement, sans appel réseau — c'est le
chemin chaud de tout `@RequirePermission`, il doit rester rapide et indépendant du
Kernel. `RemoteReactivePermissionResolver`/mode `HYBRID` restent disponibles pour les
cas où un autre backend Yowyob a besoin de la vue Kernel, mais ne sont plus le chemin
par défaut de TiiBnTick sur lui-même.

### 3.2 État outbox — machine à états

Repris du pattern déjà prouvé dans `yow-event-kernel`
(`OutboxEntry`/`OutboxStatus`/`OutboxPollerService`, Chantier C P1-P4, réhabilité et
vérifié), adapté : cible HTTP Kernel au lieu de Kafka.

```
PENDING → PROCESSING → PROVISIONED
                     → FAILED → RETRYING → PROVISIONED
                                         → DEAD  (max attempts épuisé, intervention manuelle)
```

- **PENDING** : ligne insérée dans la même transaction R2DBC que l'écriture du rôle/
  de l'affectation locale (pattern outbox transactionnel classique — écriture métier et
  intention de synchronisation atomiques, sans jamais appeler le Kernel en ligne).
- **PROCESSING** : verrouillée par le worker (`SELECT ... FOR UPDATE SKIP LOCKED`,
  identique à `OutboxEntryRepository.fetchPendingBatch` dans yow-event-kernel — sûr en
  multi-instances sans lock applicatif).
- **PROVISIONED** : `kernel_ref_id` renseigné, `processed_at` horodaté.
- **FAILED → RETRYING** : backoff exponentiel (`next_attempt_at`), plafonné.
- **DEAD** : `attempt_count` a dépassé le maximum configuré — sort de la boucle de
  poll, remonté en métrique/log pour investigation manuelle (pas de DLQ Kafka ici,
  juste un statut terminal visible en base).

### 3.3 Worker de synchronisation

`KernelRoleSyncWorker` (nouveau, `application/service/`), `@Scheduled` +
`@SchedulerLock` (ShedLock déjà en dépendance racine du repo — voir
`MediaFileCleanupScheduler` pour le pattern `lockAtMostFor`/`lockAtLeastFor`) :
**contrairement au poller de `yow-event-kernel` qui n'est pas encore câblé dans
tnt-bootstrap et n'a donc pas besoin de lock aujourd'hui, celui-ci tourne réellement
en multi-instances dès le jour 1 — le lock n'est pas optionnel.**

- Poll batch (`FOR UPDATE SKIP LOCKED`, taille configurable
  `tnt.roles.outbox.batch-size`).
- Pour chaque entrée : réutilise **tel quel** `KernelRoleProvisioningAdapter`/
  `KernelRoleAssignmentAdapter` — ils implémentent déjà l'appel HTTP idempotent
  (409 CONFLICT avalé) ; le worker les appelle au lieu que
  `TntRoleAssignmentService`/`TntRoleInitializationService` les appellent en ligne.
- Succès → `PROVISIONED` + `kernel_ref_id`. Échec → `FAILED`/`RETRYING` avec
  `next_attempt_at = now() + backoff(attempt_count)` et `last_error`.
- Idempotence garantie à deux niveaux : le Kernel lui-même (409 sur re-POST d'un rôle
  existant, déjà géré par `roleExists()`/`doProvision()`), et l'outbox (une ligne =
  une intention, jamais rejouée deux fois en parallèle grâce à `SKIP LOCKED`).

### 3.4 Réconciliation

Job périodique séparé (ex. toutes les heures, `tnt.roles.reconciliation.cron`) :
compare les lignes locales `PROVISIONED` sans `kernel_ref_id` cohérent, ou les rôles/
affectations locaux sans entrée outbox `PROVISIONED` du tout (ex. après une purge
manuelle de la table outbox), contre `GET /api/roles` + `GET /api/roles/assignments?
userId=` côté Kernel. Toute divergence détectée ré-enfile une entrée `PENDING`. Ce
job est le filet de sécurité pour les scénarios que le worker seul ne couvre pas
(perte de la ligne outbox, désynchronisation après incident Kernel prolongé).

## 4. Ce qui change vs ce qui ne change pas

**Ne change pas :**
- `KernelRoleProvisioningAdapter`, `KernelRoleAssignmentAdapter` — réutilisés tels
  quels, juste appelés par le worker au lieu des services applicatifs.
- Le contrat HTTP avec le Kernel (`/api/roles`, `/api/roles/assignments`).
- Les 9 `TntRole` canoniques comme catalogue **système** (seed de démarrage,
  non éditables — `systemRole=true`/`editable=false`, déjà porté par
  `TntRoleDefinition`).
- Le garde-fou fail-fast prod (`rbacPersistentAdapterProdGuard`) — son fondement
  devient enfin réel plutôt que théorique.

**Change :**
- `TntRoleAssignmentService.assignRole()` : écrit dans `UserRoleAssignmentRepository`
  (local) + insère l'entrée outbox, dans la même transaction. N'appelle plus
  `ITntRoleAssignmentPort` directement.
- `TntRoleInitializationService` : au démarrage, upsert les définitions dans
  `RoleRepository` local (source de vérité pour l'édition autonome par tenant) +
  entrées outbox `PENDING`, plutôt que d'appeler `provisioningPort.provisionAll(...)`
  en ligne.
- Nouveau : use-cases de gestion autonome des rôles métier (create/update/delete un
  rôle **non-système**, par tenant) — jusqu'ici absents : seul l'enum `TntRole` fixe
  existait, il n'y avait pas de notion de rôle tenant-défini.
- `TntRolesAutoConfiguration` : nouveaux beans R2DBC + worker + job de
  réconciliation, retrait des fallbacks `InMemory*` comme chemin "normal" (ils
  restent en `@ConditionalOnMissingBean` pour dev/test légers).

## 5. Rollout proposé (si validé)

1. Schéma Liquibase (`tnt_roles`, `tnt_user_role_assignments`,
   `tnt_role_provisioning_outbox`) + adaptateurs R2DBC pour
   `RoleRepository`/`UserRoleAssignmentRepository`.
2. `KernelRoleSyncWorker` + branchement outbox dans `TntRoleAssignmentService`/
   `TntRoleInitializationService`.
3. Job de réconciliation.
4. Use-cases CRUD rôles métier autonomes (create/update/delete tenant role) —
   seulement si souhaité dans ce chantier ; sinon tracké comme item séparé, S5 au
   sens strict de l'audit (persistance des affectations) est déjà couvert par 1-3.
5. Retrait du commentaire obsolète sur `RemoteReactivePermissionResolver`, vraie
   implémentation Kernel-backed pour les cas `REMOTE`/`HYBRID` (utile pour d'autres
   backends Yowyob qui interrogeraient TiiBnTick, pas pour TiiBnTick sur lui-même).
6. Mise à jour du garde-fou S5 et de la Definition of Done Chantier D dans
   `phase-0-critical.md`.

## 6. Questions ouvertes avant implémentation

- **Portée du point 4** (CRUD rôles métier autonomes) : inclus dans ce chantier S5,
  ou tracké séparément ? Change sensiblement l'effort (S5 strict ≈ schéma + outbox +
  worker ; avec CRUD complet, il faut aussi des endpoints d'admin, validation des
  permissions custom, etc.).
- **Backoff/max attempts** : valeurs par défaut à fixer (proposé : backoff
  exponentiel 1s→2s→4s...→plafond 5 min, `max-attempts` avant `DEAD` = 10).
- **Fréquence de réconciliation** : horaire proposé ci-dessus — à confirmer selon le
  volume attendu d'affectations/jour.
