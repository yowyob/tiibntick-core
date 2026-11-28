# Chantier — Un seul tenant système, plus aucune valeur codée en dur

**Branche :** `feat/single-system-tenant`
**Décidé le :** 2026-11-11 (utilisateur) — TiiBnTick (Go-Freelancer compris) n'a qu'**un seul** tenant système, fourni par la configuration ; aucune valeur de tenant n'est plus écrite dans le code.

## Avant

- `tnt.roles.system-tenant-id` avait un défaut codé en dur dans `application.yml`, dans `TntRolesProperties`, `TntRoleRegistrar` et `TntSecurityConfig`.
- Le module Go-Freelancer avait **son propre** `SYSTEM_TENANT` (`TenantContextHolder`), indépendant de cette propriété.
- `BillingEventAccountingConsumer` et les trois classes de `tnt-incident-core` avaient chacun une copie du même littéral.

## Après

| Élément | Comportement |
|---|---|
| Source unique | `com.yowyob.tiibntick.common.tenant.SystemTenant` (`tnt-common-core`) : `id()` et `systemActorId()` |
| Configuration | variable d'environnement **`TNT_SYSTEM_TENANT_ID`** (ou propriété JVM `tnt.roles.system-tenant-id`, prioritaire) |
| Défaut | **aucun**. Non définie ou non-UUID → le démarrage échoue avec un message explicite |
| Spring | `tnt.roles.system-tenant-id: ${TNT_SYSTEM_TENANT_ID}` (3 profils de `application.yml`) |
| Cohérence | `SystemTenantConsistencyCheck` (bootstrap) refuse de démarrer si la valeur Spring et la valeur lue par `SystemTenant` diffèrent |
| Go-Freelancer | `TenantContextHolder.SYSTEM_TENANT = SystemTenant.id()` : même tenant que le reste |
| Acteur système | `SystemTenant.systemActorId()` = UUID v3 déterministe dérivé du tenant (incidents) |
| Tests | `pom.xml` fournit `tnt.test.system-tenant-id` (UUID de test fixe) à surefire/failsafe |

## À faire AVANT de déployer

1. Définir `TNT_SYSTEM_TENANT_ID` dans l'environnement de production (`.env.prod.example`, `docker-compose.yml` l'exige désormais : `${TNT_SYSTEM_TENANT_ID:?…}`).
2. Valeur retenue : le tenant `fbd90da6-629d-4d78-9a36-8735ff1762cb` (propriétaire `OWNER` : `yow_npk@yowyob.com`). C'est le même tenant que celui des notifications : il porte l'organisation « TiiBnTick Notif » (`a804a304-fb65-4970-bf97-b94390cdbdff`, code `TNT-NOTIF-001`, `APPROVED`, abonnée à `NOTIFICATION`).
3. Variables associées (clé d'API du client Kernel à fournir séparément, jamais dans le dépôt) :

```
TNT_SYSTEM_TENANT_ID=fbd90da6-629d-4d78-9a36-8735ff1762cb
TNT_NOTIFY_KERNEL_DEFAULT_TENANT_ID=fbd90da6-629d-4d78-9a36-8735ff1762cb
TNT_NOTIFY_KERNEL_DEFAULT_ORGANIZATION_ID=a804a304-fb65-4970-bf97-b94390cdbdff
TNT_KERNEL_CLIENT_ID=tibntick-backend
TNT_KERNEL_API_KEY=<fournie par l'équipe Kernel>
```

> Historique : le tenant `b8b17c6e-9bbd-4b4b-8d54-b162cabeea64` (créé par `me/spaces`) était d'abord prévu, mais le Kernel n'y reliait pas le compte à un acteur métier (voir `kernel-request-notification-organization.md`) ; il est abandonné au profit de `fbd90da6-…`. Le tenant `43427172-b6ee-4dbf-9148-96682702ffc9` annoncé par l'équipe Kernel n'a pas été retenu : le compte n'y a aucun rôle.

## Conséquences d'un changement de tenant système en production

- Les rôles canoniques (`TntRole`) sont re-provisionnés au démarrage **dans le nouveau tenant** ; les affectations de rôles existantes restent attachées à l'ancien.
- Les données déjà écrites sous l'ancien tenant système (comptabilité, incidents créés par le système) y restent ; rien n'est migré automatiquement.
- Les lignes d'incident créées par le système à partir de maintenant auront un autre `reportedByActorId` (dérivé du nouveau tenant).

## Migration corrective du littéral SQL

`20260216_insert_guest_client.sql` (changeset `033`, déjà appliqué) insérait la personne « Guest » sous une clé fixe. Il n'est **pas modifié** (checksum, règle Liquibase du projet) ; le changeset `075-relocate-guest-person` (`20261114_relocate_guest_person.sql`) la corrige :

- la personne est retrouvée par ses attributs (email, identifiant national) et l'ancien id par **motif**, donc le nouveau fichier ne contient pas le littéral ;
- elle reçoit un id aléatoire (`gen_random_uuid()`), et toutes les clés étrangères vers `persons(id)` ainsi que `clients.person_id` sont repointées (contraintes supprimées puis recréées) ;
- idempotent ; la ligne du client Guest (id utilisé par les frontends) n'est pas touchée ;
- vérifié par `LiquibaseMasterChangelogIntegrationTest#guestPersonIsRelocated` (PostGIS réel : état avant, migration, état après, clés étrangères recréées).

Le littéral n'existe donc plus dans les données ni dans le code ; il subsiste uniquement dans le fichier historique `033` (inaltérable sans casser les bases déjà migrées).

## Documentation

Le littéral a été remplacé par `<SYSTEM_TENANT_ID>` dans les documents sous `docs/` (y compris les captures `docs/e2e/**`) et dans `foundation/tnt-roles-core/TNT_BOOTSTRAP_UPDATE.md`.

## Vérification

- `SystemTenantTest` (5), `SystemTenantConsistencyCheckTest` (3), `LocalReactivePermissionResolverTest`, `TntJwtSelfServiceRolesTest`, `TntRoleSyncOutboxHealthIndicatorTest`, `TiiBnTickApplicationTest`, `PublicPathsSecurity…`, `WalletAutoProvisioning…` : verts avec le tenant de test.
- Sans la variable : le contexte refuse de démarrer (« The TiiBnTick system tenant is not configured… there is no default »).
- Variable seule (aucune propriété JVM) : `SystemTenant.id()` se résout bien depuis l'environnement ; la propriété JVM, si présente, est prioritaire.

- **Démarrage complet vérifié** (Postgres/PostGIS vierge, Redis, Kafka locaux, aucune propriété JVM, uniquement `TNT_SYSTEM_TENANT_ID` dans l'environnement, sans profil `test`/`r2dbc`) : `Started TiiBnTickApplication`, et les 11 rôles canoniques sont créés dans le tenant configuré. La porte `LiquibaseMasterChangelogIntegrationTest` passe aussi.

### Limites

- Le profil `r2dbc` (tests) force `spring.liquibase.url` vers la base locale `tiibntick_core` : lancer l'appli avec ce profil ignore `DB_NAME` et peut échouer sur une vieille base locale incohérente (changeset `025`, `packets.weight`). Ce n'est pas un défaut du changelog.
- L'erreur `tnt_roles_tenant_id_code_key` vue au premier démarrage sur base vide (deux lancements concurrents de `provisionSystemRoles`) est corrigée sur la même branche (`feat/single-system-tenant`).
- Un test via surefire avec une propriété vide (`-Dtnt.test.system-tenant-id=`) échoue : surefire injecte alors `tnt.roles.system-tenant-id=""` qui l'emporte sur l'environnement. Artefact de test, pas de production.
