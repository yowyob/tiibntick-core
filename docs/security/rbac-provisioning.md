# RBAC Provisioning — Comment les rôles d'un tenant réel sont-ils créés ?

## Situation actuelle (2026-09-23)

### Ce qui est implémenté

Au démarrage de l'application (`TNT_ROLES_PROVISION_ON_STARTUP=true`), `TntRoleInitializationService`
provisionne les 9 rôles canoniques TiiBnTick dans `tnt_roles` **uniquement pour le tenant système**
(`00000000-0000-0000-0000-000000000001`). Ces rôles couvrent FREELANCER, CLIENT, ORG_ADMIN, etc.

Les assignations (`tnt_user_role_assignments`) ne sont **pas** créées automatiquement. Pour qu'un
utilisateur accède à une route protégée par `@RequirePermission`, une ligne doit exister dans
`tnt_user_role_assignments` avec :

```
tenant_id = <tenant du JWT (claim tid)>
user_id   = <UUID du JWT (claim sub)>
role_id   = <id d'un rôle dans tnt_roles pour ce tenant>
scope_type = TENANT (ou SYSTEM / AGENCY / ORGANIZATION)
scope_id   = <tenant_id pour scope TENANT>
```

### Ce qui n'est PAS implémenté

Aucun mécanisme automatique ne crée les rôles ni les assignations pour les **tenants réels**
(i.e., les tenants issus du Kernel, comme `dbae6615-8f7e-4ef5-9e58-23a6179acf22`).

- Le Kernel émet des JWTs avec des claims `sub` et `tid`, mais sans claims de rôles TiiBnTick.
- `TntRoleInitializationService` ignore tous les tenants sauf `00000000-...0001`.
- Il n'existe pas de route `/api/v1/admin/roles/provision-tenant` ni de migration d'onboarding.
- Il n'existe pas d'écoute d'événement Kernel "tenant created" qui déclencherait la provisioning.

En conséquence, **un administrateur réel sur un tenant de production n'a aucune permission RBAC
TiiBnTick tant qu'une ligne n'est pas insérée manuellement en base.**

C'est pour contourner cette absence que le lot 20 a inséré directement en base un rôle
`e2e19000-…` avec `permissions = '*'` — une élévation de privilège non déclarée sur le tenant
réel, corrigée au lot 21.

## Ce qu'il faudrait implémenter

Trois options, par ordre de priorité recommandée :

### Option A — Endpoint admin de provisioning de tenant (recommandé)

Ajouter une route TNT_ADMIN-only :

```
POST /api/v1/admin/tenants/{tenantId}/roles/provision
```

qui crée les 9 rôles canoniques dans `tnt_roles` pour ce tenant et retourne les IDs créés.
L'assignation des rôles aux utilisateurs resterait une opération séparée (déjà partiellement
prévue par `AssignTntRoleUseCase`).

### Option B — Provisioning automatique au premier login d'un tenant

Enrichir `TntRoleInitializationService` (ou un filtre dédié) pour provisionner les rôles
la première fois qu'un JWT portant un nouveau `tid` est vu. Ce comportement serait contrôlé
par `tnt.roles.provision-on-demand` (désactivé par défaut pour les ennvironements de production).

### Option C — Migration Liquibase par tenant

Pour les déploiements avec liste de tenants connue à l'avance, un changeset Liquibase peut
insérer les rôles et les assignations initiales. Fragile en SaaS multi-tenant.

## Impact opérationnel immédiat

Jusqu'à ce que l'Option A soit implémentée :

- Tout nouveau tenant réel est **bloqué** sur toutes les routes `@RequirePermission`.
- La seule solution de contournement est un INSERT direct en base (opération manuelle,
  à documenter dans le runbook de déploiement, et à ne jamais faire avec `permissions = '*'`).
- Le rôle minimal à créer pour un admin GOFP est `permissions = 'gofp-admin:manage'` ;
  l'assignation requiert `scope_type = 'TENANT'` et `scope_id = <tenant_id>`.

## Conséquence sur le harnais E2E

Le harnais `e2e-freelancer-flow.sh` (lot 21) gère lui-même la provisioning minimale :
il crée le rôle `E2E_GOFP_ADMIN` avec `permissions = 'gofp-admin:manage'` au début du run
et le supprime au cleanup (`SEEDED_ROLE=1`). Un `[WARN]` bloquant détecte tout rôle `*`
préexistant pour éviter que les assertions RBAC ne soient vidées de leur sens.

## Trou de conception à remonter

Ce document est un constat d'une lacune de conception : le cycle de vie d'un tenant
(création → provisioning des rôles → assignation aux premiers admins) n'est pas
implémenté. C'est un lot à part entière, distinct des corrections urgentes.
