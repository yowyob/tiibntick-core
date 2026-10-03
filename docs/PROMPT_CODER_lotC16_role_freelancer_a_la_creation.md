# Lot C-16 — Le profil freelancer doit donner le droit de répondre à une annonce

## Dépôt cible

**`/home/jtk/projets/tiibntick-core` — et uniquement celui-là.**
Branche de départ : `main`. Travaille sur une branche dédiée `feat/lotC16-role-freelancer`.
Tu ne touches ni `/home/jtk/projets/tiibntick-bff` ni `/home/jtk/projets/tiibntick-mobile`.
À la fin, `git status --porcelain` dans ces deux dépôts doit être inchangé.

## Règle d'autonomie — lis-la avant tout

Tu travailles **seul, du début à la fin, sans jamais demander la permission de continuer.**
Aucune question du type « veux-tu que je procède ? », « dois-je continuer ? », « souhaites-tu que j'applique ? ».
Tu tranches toi-même chaque décision intermédiaire et tu documentes ton choix dans le rapport final.
**Tu prends la parole une seule fois : à la fin, avec le rapport.**
Si un point est ambigu, tu choisis l'option la plus défensive, tu l'implémentes, et tu l'inscris dans la section « Décisions tranchées seul ».

## Le fait mesuré qui motive ce lot

Le module Freelancer est fonctionnel en local mais **impossible à utiliser sur le core déployé**, parce que
les garde-fous de permission ne sont pas sur les contrôleurs — ils sont sur la **couche service**.

Mesure exacte (`grep -rn "@RequirePermission" coreBackend/tnt-go-freelancer-point-back-core/src/main`) :

| Fichier / ligne | Annotation | Route exposée | Étape du parcours |
|---|---|---|---|
| `AnnouncementApplicationService.java:75` | `announcement:create` | `POST /api/announcements` | (non utilisée par le BFF) |
| `AnnouncementApplicationService.java:158` | `announcement:create` | `POST /api/announcements/{id}/publish` | (non utilisée) |
| **`AnnouncementApplicationService.java:165`** | **`announcement:respond`** | **`POST /api/announcements/{id}/subscribe`** | **Accepter une course — étape 1** |
| `AnnouncementApplicationService.java:212` | `announcement:respond` | `initiateSubscription` (déprécié) | — |
| **`AnnouncementApplicationService.java:238`** | **`announcement:elect`** | **`POST /api/announcements/{id}/assign`** | **Accepter une course — étape 2** |
| `AnnouncementApplicationService.java:263` | `announcement:elect` | `assignFreelancer` (déprécié) | idem |

`AnnouncementController` lui-même ne porte **aucune** `@RequirePermission` — d'où l'erreur d'audit initiale.
`TntPermissionAspect` (foundation/tnt-roles-core) intercepte **n'importe quel bean Spring** annoté, donc
le service est bien protégé même si le contrôleur ne l'est pas.

Conséquence : un utilisateur authentifié sans rôle obtient **403 sur `subscribe`**, donc « Accepter la course »
est mort sur le core déployé. Aucun rôle du dépôt ne porte `announcement:respond` :
`grep -rn "announcement:respond" --include=*.sql --include=*.yml .` → **0 résultat**.

Et le module Go (Expéditeur) fonctionne en production **parce qu'il passe par `/api/delivery-needs`**
(`DeliveryNeedApplicationService` — aucune `@RequirePermission`), pas par `/api/announcements`.
C'est pour cela que le build #2 du 12 septembre a pu être testé par le professeur sans aucun rôle.

## Ce qu'il faut construire

Le principe métier : **créer son profil freelancer, c'est devenir livreur — donc obtenir les droits du livreur.**
Aujourd'hui `POST /api/v1/freelancers` crée le profil et n'accorde rien. C'est le trou à combler.

### 1. Un rôle `FREELANCER` existant, par tenant

Crée une migration Liquibase dans `identity/tnt-actor-core/src/main/resources/db/changelog/`
(nom : `20260930_seed_role_freelancer.sql`, déclarée dans le master changelog du module, à la suite des autres).

Elle insère dans `tnt_roles` un rôle de code `FREELANCER` **pour chaque tenant déjà présent**
(`INSERT ... SELECT DISTINCT tenant_id FROM tnt_roles` ou la table de tenants si elle existe — choisis et justifie),
avec :

- `code = 'FREELANCER'`
- `permissions = 'announcement:respond,announcement:elect,freelancer:read'`
- `system_role = true`, `editable = false`
- `scope_type` : la même valeur que celle utilisée par le rôle E2E dans `scripts/e2e/e2e-freelancer-flow.sh` (lis-la, ne la devine pas)
- `ON CONFLICT DO NOTHING` — la migration doit être rejouable

**Interdit :** `permissions = '*'`. Exactement les trois permissions ci-dessus, pas une de plus.
Vérifie d'abord le **séparateur réellement attendu** par `TntPermissionEvaluator` (virgule ? espace ? JSON ?)
en lisant le code de `foundation/tnt-roles-core`, et utilise celui-là. Si le format est un tableau JSON, utilise-le.

### 2. L'assignation à la création du profil

Dans `identity/tnt-actor-core/.../FreelancerService.java`, méthode de création (lignes ~42-67 :
le `existsByActorId(...).flatMap(exists -> ...)`), après la persistance du profil **et aussi dans la branche
`exists == true`** (idempotence : un profil déjà créé avant ce lot doit obtenir le rôle au prochain appel),
assigne le rôle.

`tnt-actor-core` dépend déjà de `tnt-roles-core` (voir `pom.xml` l.54-57). Utilise
`AssignTntRoleUseCase.assignRole(UUID tenantId, UUID targetUserId, String roleCode, UUID scopeId)`.
Le `targetUserId` est l'utilisateur, pas l'acteur : établis la correspondance en lisant le code, ne la suppose pas.

**Best-effort obligatoire :** si l'assignation échoue, on **logue en warn et on renvoie quand même le profil**.
La création de profil ne doit jamais devenir plus fragile qu'avant ce lot.

### 3. L'invalidation de cache

`TntPermissionEvaluator` lit les autorisations depuis le contexte Spring Security / un cache invalidé par Kafka
(topic `tnt.roles.permission-changed`, voir `PermissionCacheInvalidationListener` et
`kafka_invalidate_permission_cache()` dans le script E2E).

Détermine par lecture du code si `assignRole` publie déjà cet événement. Si oui, ne fais rien de plus et dis-le.
Si non, publie-le après l'assignation, **best-effort** lui aussi.

Documente dans le rapport la réponse à cette question : **après assignation, le JWT déjà émis porte-t-il
les nouvelles permissions, ou l'utilisateur doit-il rafraîchir son token ?** C'est décisif pour le mobile.

### 4. Les tests

Écris des tests qui portent sur le **comportement observable**, pas sur les appels de méthodes :

1. Création d'un profil freelancer pour un acteur sans rôle → une ligne apparaît dans `tnt_user_role_assignments` avec le role_id du rôle `FREELANCER`.
2. Second appel sur le même acteur (`exists == true`) → toujours exactement **une** assignation (pas de doublon), et le profil est renvoyé.
3. Un acteur qui avait déjà un profil **avant** ce lot (profil inséré directement en base, sans assignation) → l'appel suivant crée l'assignation.
4. `assignRole` qui lève → la création de profil **réussit** quand même et renvoie le profil.
5. La migration est rejouable : l'appliquer deux fois ne crée pas deux rôles `FREELANCER` pour un même tenant.

### 5. La preuve par le run

Tu ne me rapportes aucun vert que tu n'as pas exécuté. Dans le rapport, colle la **sortie brute** de :

```
mvn -q -pl identity/tnt-actor-core -am test
```

et le `git show --stat` de chaque commit.

Si un test échoue, tu le dis. Un rapport qui annonce vert sans la sortie correspondante sera rejeté.

## Ce que ce lot ne prouve pas — à écrire explicitement dans le rapport

Liste au minimum :

- Ce lot ne prouve rien contre le core **déployé** ; la validation viendra de l'APK.
- La clé étrangère `delivery_notifications.assigned_freelancer_id → gofp_freelancers(id)`
  (`coreBackend/.../20260805_fk_assigned_freelancer_to_gofp_freelancers.sql`) exige une ligne dans
  `gofp_freelancers`. `POST /api/v1/freelancers` crée le profil dans `tnt-actor-core`, pas dans `gofp_freelancers`.
  **Dis si l'assignation de rôle suffit, ou s'il faudra un lot C-17 pour projeter le profil acteur dans `gofp_freelancers`.**
  Si tu peux répondre par lecture du code (`FreelancerEventConsumer`, `FreelancerProviderAdapter#fromGofp`), réponds.
- Le format exact des permissions et la sémantique de `elect` (le client élit un freelancer ; ici le freelancer
  s'auto-assigne) sont une dette de conception que ce lot ne corrige pas.

## Rapport final attendu

1. Tableau des commits (SHA + sujet)
2. Diff résumé fichier par fichier
3. Sortie brute des tests
4. Décisions tranchées seul
5. Ce que ce lot ne prouve pas
6. Réponse à la question du rafraîchissement de token
7. Réponse à la question de la FK `gofp_freelancers`
