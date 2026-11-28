# Registre des administrateurs de la plateforme (TNT_ADMIN)

Chantier ouvert le 2026-11-27 sur `feat/single-system-tenant`. Décisions D-159 à D-175 du
[registre des décisions](../../decisions/registre-decisions.md) (questions, options et réponses mot pour mot), directives
du mainteneur du 2026-11-27 (section 1 du registre). Ce document fait foi pour la conception ; toute décision qui le
change est une nouvelle entrée du registre, puis une mise à jour ici.

## 1. Constat (2026-11-27, vérifié dans le code)

| # | Constat | Preuve |
|---|---|---|
| C1 | Le premier TNT_ADMIN est le **propriétaire Kernel du tenant système** : jeton avec `tid` = `TNT_SYSTEM_TENANT_ID` et `ROLE_OWNER`/`tenant:admin` → le Core ajoute `ROLE_TNT_ADMIN` + 11 rôles métier + autorités fines. Connecté dans un autre tenant, il n'est plus rien. | `TntSecurityConfig.tntJwtAuthenticationConverter` |
| C2 | Un TNT_ADMIN attribué (`POST /api/v1/admin/roles/assignments`) est rangé sous le tenant système dans `tnt_user_role_assignments` ; il n'est relu (repli en base de l'évaluateur) **que si le jeton a `tid` = tenant système**. | `LocalReactivePermissionResolver.resolvePermissions(tid, user)` |
| C3 | Ce repli ne sert que `@RequirePermission`. Les **plus de 100 contrôles `hasRole`/`hasAnyRole`** (18 fichiers) qui citent TNT_ADMIN ne lisent que les autorités du jeton : un admin attribué y échoue. | inventaire `grep` du 2026-11-27 |
| C4 | **Faille** : le Core recopie tel quel le claim `permissions` en autorités, et l'évaluateur développe tout `ROLE_<code>#…` en permissions du rôle. `ROLE_TNT_ADMIN#TENANT:<un autre tenant>` donne donc `*` → `system:admin`. Un propriétaire de tenant peut créer un rôle `TNT_ADMIN` dans son propre tenant Kernel. Le claim `roles` est aussi recopié sans filtre. | sonde `ProbeForeignTntAdminTest` (verte, supprimée ensuite) ; `TntPermissionEvaluator.expandWithRolePermissions` |
| C5 | Le JWT du Kernel ne porte pas les rôles TiiBnTick utilisables : (a) il ne contient que les rôles du tenant choisi à la connexion ; (b) le provisionnement au démarrage passe par une outbox dont le worker n'a pas de jeton (403, contourné par le chemin « solution » quand un client dédié existe) ; (c) même présents, les codes de rôle d'un tenant sont fabriquables par son propriétaire, donc non fiables. | commit b53d611c, `KernelRoleSyncWorker.handleAssignRole` |

## 2. Décisions

| Décision | Choix |
|---|---|
| D-159 Source de vérité | **Registre Core dédié**, une ligne par attribution, lu par le `sub` du jeton **quel que soit le `tid`** ; la personne n'a pas besoin d'être membre du tenant système. |
| D-160 Contrôles `hasRole` | **Enrichissement à l'authentification** (un seul point) + test d'architecture qui empêche de le contourner. |
| D-161 Propriétaire | Entrée **SYSTEM_OWNER créée automatiquement** à la première connexion du propriétaire dans le tenant système, plus un endpoint « réclamer ». |
| D-162 Faille C4 | **Fermée tout de suite** : aucun TNT_ADMIN n'est plus accepté depuis le jeton (claims `roles` et `permissions`) ; test de non-régression. |
| D-163 Protection | L'entrée SYSTEM_OWNER est **irrévocable** (409) tant que son tenant d'obtention est le tenant système en vigueur, y compris par lui-même ; après un changement de tenant système, elle devient révocable par la révocation de masse. |
| D-164 Tenant d'obtention | **`tid` du jeton de l'attributeur** au moment de l'attribution. Précision du mainteneur : un SYSTEM_OWNER connecté dans un autre tenant attribue avec ce tenant-là comme tenant d'obtention. Le tenant système en vigueur est aussi enregistré (audit). |
| D-165 Révocation de masse | **Aperçu puis confirmation** (jeton de confirmation court, liste figée) ; auteur, motif, date et lot enregistrés. |
| D-166 Droits | Tout TNT_ADMIN actif **attribue** et **révoque un ASSIGNED** ; la **révocation de masse** est réservée au SYSTEM_OWNER ; personne ne révoque l'entrée protégée. |
| D-167 Contenu du rôle | **TNT_ADMIN seul pour tous**, propriétaire compris : `ROLE_TNT_ADMIN` + autorités admin fines ; les 11 rôles métier ajoutés au propriétaire disparaissent. |
| D-168 Cache | Cache mémoire **60 s** par compte + **invalidation** sur toutes les instances à chaque attribution/révocation. |
| D-169 Reprise | **Aucune reprise** : les anciennes lignes TNT_ADMIN de `tnt_user_role_assignments` ne donnent plus rien ; les admins sont réattribués à la main. |
| D-170 Miroir Kernel | **Core seul** : plus d'écriture Kernel pour TNT_ADMIN. Les rôles par tenant (CLIENT, FREELANCER…) vont au Kernel comme avant. |
| D-171 Ancien endpoint | `POST /api/v1/admin/roles/assignments` garde son contrat et **envoie TNT_ADMIN au registre** ; nouveaux endpoints `/api/v1/admin/platform-admins`. |
| D-172 Alertes | **Événement d'audit Kafka** + **courriel** (Kernel) au bénéficiaire et au propriétaire du tenant système. |
| Directive du 2026-11-27 | En plus du courriel, **message automatique in-app** au propriétaire du tenant système à chaque attribution et révocation ; courriel et message **dans la langue du propriétaire** (préférence de son compte). |
| D-173 / D-175 JWT Kernel | **Vérifier d'abord, demander si manque** : contrôle K19 ; essai réel avec une connexion MFA du mainteneur ; fiche de demande pour l'équipe Kernel seulement si la fonctionnalité manque, transmise par le mainteneur. |
| D-174 Étendue | **TNT_ADMIN seul**, mais table extensible par code de rôle (SUPPORT_AGENT, MEDIATOR plus tard, sur décision). |
| D-176 Rôles d'agence du jeton (P-1) | **Vérifier la portée** : un rôle TiiBnTick de portée agence ou organisation venu du jeton n'est gardé que si les attributions du Core le confirment (§3.5). |
| D-177 Transfert de propriété (P-2) | **Bascule auto à la connexion** : le nouveau propriétaire prend l'entrée protégée ; l'ancien reste TNT_ADMIN en ASSIGNED, révocable (§3.6). |
| D-178 Chemins `/tenants/{id}` (P-4) | **Lecture seule** pour TNT_ADMIN sur tout tenant, journalisée ; écritures limitées à son tenant (§3.7). |
| D-179 `hasRole` (P-3) | **Laissé pour le moment**, noté comme dette ; migration complète envisagée plus tard. |

## 3. Conception cible

### 3.1 Table `tnt_platform_role_grants` (tnt-roles-core, changeset `005`)

| Colonne | Rôle |
|---|---|
| `id` uuid PK | identifiant de l'attribution |
| `role_code` | `TNT_ADMIN` (contrainte : seule valeur admise aujourd'hui, D-174) |
| `user_id` | `sub` Kernel du bénéficiaire |
| `grant_kind` | `SYSTEM_OWNER` ou `ASSIGNED` |
| `granted_by` | `sub` de l'attributeur (lui-même pour SYSTEM_OWNER) |
| `source_tenant_id` | tenant d'obtention = `tid` de l'attributeur (D-164) |
| `system_tenant_at_grant` | tenant système en vigueur à l'attribution |
| `granted_at` | date |
| `revoked_at`, `revoked_by`, `revoke_reason`, `revoke_batch_id` | révocation (jamais de suppression : l'historique reste) |

Index unique partiel `(role_code, user_id) WHERE revoked_at IS NULL` : une seule attribution active par compte ; une
nouvelle attribution après révocation crée une nouvelle ligne.

### 3.2 Résolution à l'authentification (D-160, D-162, D-167, D-168)

Dans `tntJwtAuthenticationConverter` :

1. Les claims `roles` et `permissions` ne peuvent plus apporter TNT_ADMIN : toute entrée `TNT_ADMIN`/`ROLE_TNT_ADMIN…`
   est écartée (C4 fermée).
2. Propriétaire du tenant système (règle C1) → `ROLE_TNT_ADMIN` + autorités admin fines, et création idempotente de
   son entrée SYSTEM_OWNER (D-161).
3. Sinon, entrée active dans le registre pour le `sub` → mêmes autorités, quel que soit le `tid`.
4. Un seul ensemble d'autorités admin, partagé par 2 et 3 (D-167).

Le registre est lu à travers un cache de 60 s par compte, vidé à chaque attribution/révocation (D-168). Le repli en base
de l'évaluateur n'accorde plus jamais TNT_ADMIN depuis `tnt_user_role_assignments` (D-169).

### 3.3 API `/api/v1/admin/platform-admins` (D-161, D-163, D-165, D-166, D-171)

| Route | Qui | Effet |
|---|---|---|
| `GET /` (`?includeRevoked=true`) | TNT_ADMIN | attributions actives (et historique) |
| `POST /` `{targetUserId}` | TNT_ADMIN | attribution ASSIGNED ; idempotente si déjà active |
| `DELETE /{grantId}` `?reason=` | TNT_ADMIN | révocation d'un ASSIGNED ; 409 sur l'entrée protégée |
| `POST /claim-ownership` | propriétaire connecté dans le tenant système | crée/rend son entrée SYSTEM_OWNER |
| `POST /revocations/foreign-tenant/preview` | SYSTEM_OWNER | liste des actives dont le tenant d'obtention ≠ tenant système + jeton de confirmation (5 min) |
| `POST /revocations/foreign-tenant` `{confirmationToken, reason}` | SYSTEM_OWNER | révoque exactement la liste de l'aperçu ; 409 si elle a changé |

`POST/DELETE /api/v1/admin/roles/assignments` avec `TNT_ADMIN` passent par le même service (D-171).

### 3.4 Alertes (D-172, directive du 2026-11-27)

| Destinataire | Canal | Langue |
|---|---|---|
| bus | événement d'audit `tnt.roles.platform-admin-changed` (`GRANTED`/`REVOKED`, clé = compte) | — |
| bénéficiaire | courriel (objet + corps) | la sienne |
| propriétaire du tenant système (entrée SYSTEM_OWNER protégée) | courriel (objet + corps) **et** message in-app (boîte Kernel, D-157, application `tiibntick-admin`) | la sienne |

- **Langue** : jamais choisie par l'appelant. notify lit la préférence du compte (`i18n.user_language_preference`,
  même règle que toutes les notifications) ; la langue par défaut (`fr`) ne sert que si le compte n'en a pas. Un même
  événement peut donc être lu en anglais par le propriétaire et en français par le bénéficiaire.
- **Textes** : catalogue `notify.roles.platform_admin.granted|revoked`, variantes `EMAIL_SUBJECT`, `EMAIL_BODY`,
  `IN_APP` et `DEFAULT`, fr/en (seed `i18n-007`, modifiables par l'API i18n admin). Le message in-app prend l'objet
  du courriel comme titre et la variante `IN_APP` comme corps (`IInAppInboxUseCase.sendLocalized`).
- **Propriétaire bénéficiaire** (sa propre entrée SYSTEM_OWNER) : un seul courriel et un seul message.
- **Aucun propriétaire enregistré** (il ne s'est jamais connecté dans le tenant système) : courriel au bénéficiaire
  seulement, avertissement dans les journaux.
- **Au mieux** : un échec du Kernel est journalisé et n'annule jamais l'attribution ; le message in-app est tenté même
  si le courriel a échoué.
- Pont : `tnt-bootstrap` `bridge/PlatformAdminAlerts` (relie tnt-roles-core L1 et tnt-notify-core L3).

### 3.5 Rôles d'agence et d'organisation venus du jeton (D-176)

Un propriétaire de tenant peut créer et attribuer n'importe quel code de rôle dans son tenant Kernel : le jeton ne
prouve donc rien pour `AGENCY_MANAGER`, `BRANCH_MANAGER`, `PERMANENT_DELIVERER`, `RELAY_OPERATOR`,
`AGENCY_HUB_OPERATOR` (portée agence) et `ORG_ADMIN` (portée organisation).

- Dans le convertisseur JWT (claims `roles` et `permissions`), ces entrées sont mises de côté puis gardées seulement si
  `tnt_user_role_assignments` (attributions faites par le Core) les confirme **pour ce compte dans le tenant du jeton** :
  `AGENCY_MANAGER` sans portée = une attribution de ce rôle dans le tenant ; `ROLE_AGENCY_MANAGER#AGENCY:<id>` = une
  attribution sur cette agence précise (idem `#ORGANIZATION:`/`#ORG:`).
- Pas concernés : rôles de portée tenant (CLIENT, FREELANCER…), rôles système, codes propres au Kernel (le
  propriétaire d'agence reçoit `AGENCY_ADMIN`, code Kernel jamais développé en permissions), permissions simples.
- Le personnel d'agence provisionné (`AgencyCredentialsProvisioningService`) passe par l'attribution du Core : il est
  confirmé.
- Cache de 60 s par (tenant, compte), vidé par `tnt.roles.permission-changed`. **Fermé en cas d'erreur** : rien n'est
  gardé si les attributions ne peuvent pas être lues ; aucune lecture quand le jeton ne porte aucun de ces rôles.
- Limite : une fois confirmé, le rôle se développe comme avant (permissions sans suffixe d'agence) ; le contrôle de la
  ressource visée reste celui de chaque endpoint.

### 3.6 Transfert de propriété du tenant système (D-177)

À la connexion d'un propriétaire Kernel du tenant système qui n'a pas encore l'entrée protégée : toute autre entrée
SYSTEM_OWNER protégée est close (motif `system-tenant ownership transferred…`, auteur = nouveau propriétaire) et
remplacée par une entrée **ASSIGNED** du même compte (attributeur = nouveau propriétaire, tenant d'obtention = tenant
système) ; puis l'entrée SYSTEM_OWNER du nouveau est créée, dans la même transaction. Alertes : révocation et
attribution de l'ancien, attribution du nouveau. L'ancien reste TNT_ADMIN, révocable par tout TNT_ADMIN, hors
révocation de masse (son tenant d'obtention est le tenant système). Limite : si le Kernel laissait deux propriétaires
du tenant système, l'entrée protégée changerait de main à chaque connexion de l'un ou de l'autre (alertes à chaque fois).

### 3.7 Lecture inter-tenant (D-178)

`TenantPathGuardWebFilter` (tnt-auth-core) laisse un porteur de `ROLE_TNT_ADMIN` (donné seulement par le
convertisseur, §3.2) lire `/api/v1/tenants/{autre}/…` en `GET`, `HEAD`, `OPTIONS` ; chaque lecture est écrite dans le
journal `tnt.audit.cross-tenant` (compte, tenant du jeton, tenant lu, méthode, chemin). Toute écriture vers un autre
tenant reste refusée (403). Des permissions admin sans le rôle n'ouvrent rien.

## 4. Contrôle Kernel K19 (D-175)

Rôles « solution » TiiBnTick (`/api/roles/solution`, `/api/roles/solution/assignments`, `/api/permissions/solution`) :
à prouver en réel (connexion MFA du mainteneur) que (1) le jeton les porte quel que soit le tenant choisi, (2) un
propriétaire de tenant ne peut pas produire la même entrée de jeton, (3) une révocation se voit au rafraîchissement
du jeton. Tant que ce n'est pas prouvé, le registre Core reste la seule vérité. Si une condition manque, une fiche de
demande est rédigée ici, et c'est le mainteneur qui la transmet à l'équipe Kernel.

## 5. Points ouverts (propositions à soumettre)

P-1, P-2 et P-4 sont tranchés (D-176, D-177, D-178) et implémentés ; P-3 est une dette notée (D-179).

- **P-1** Même faille de développement des rôles pour les rôles d'agence/organisation venus du jeton
  (`ROLE_AGENCY_MANAGER#…` fabriqué dans son propre tenant → permissions de l'agence sans contrôle de portée).
- **P-2** Transfert de propriété du tenant système dans le Kernel : l'ancienne entrée SYSTEM_OWNER reste protégée.
- **P-3** Migration des `hasRole` vers `@RequirePermission` : **dette** (D-179), migration complète plus tard.
- **P-4** `TenantPathGuardWebFilter` ne laisse passer qu'une permission `*` ; un TNT_ADMIN (qui n'en a jamais eu)
  reste donc bloqué sur les chemins `/tenants/{autre}/…`. Faut-il l'ouvrir aux TNT_ADMIN ?
- **K20** (contrat Kernel) : notify envoie les courriels par **identifiant de compte** seulement (le Core ne détient
  aucune adresse) ; que le Kernel résolve l'adresse d'un `recipientUserId` sur le canal EMAIL n'est pas prouvé en réel.

Permissions réservées aux administrateurs (calculées : présentes dans `TntPermission` et dans aucun rôle non système),
jamais acceptées depuis le jeton : `media:delete`, `mission:dispute`, `payment:refund`, `platform:clients`,
`relay:approve`, `relay:revoke`, `system:admin`, `tenant:admin`, `tnt:platform:admin`, `wallet:admin`
(`PlatformAdminAuthorities.adminOnly()`).

## 6. Suivi

| Étape | État |
|---|---|
| Décisions D-159 à D-175 consignées | fait (2026-11-27) |
| Implémentation (registre, résolution, API, alertes, faille) | fait (2026-11-27) : changeset roles `005`, seed i18n `007`, convertisseur JWT, garde d'architecture, API, alertes localisées |
| Tests | unitaires : `PlatformAdminServiceTest` (19), `PlatformAdminAuthoritiesTest`, `ForgedTntAdminAuthorityTest`, `TntAdminThroughRolesApiTest`, `TntJwtPlatformAdminTest` (6), `PlatformAdminSingleSourceTest`, `PlatformAdminAlertsTest` (4), `InAppInboxLocalizedTest` (3) ; intégration pleine pile sur PostgreSQL/Kafka/Redis réels : `PlatformAdminFullStackIT` (cycle complet, `hasRole` + `@RequirePermission` depuis un autre tenant, jetons forgés refusés, 409, ancien endpoint, révocation de masse, Kafka, courriel et message in-app en anglais pour un propriétaire anglophone) ; porte Liquibase verte |
| K20 en réel | en attente d'une connexion MFA du mainteneur |
| D-176 à D-179 consignées et D-176 à D-178 implémentées | fait (2026-11-27) : `ScopedTokenRoles`, `ScopedRoleConfirmationService`, bascule dans `PlatformAdminService`, `TenantPathGuardWebFilter` ; tests `ScopedTokenRolesTest`, `ScopedRoleConfirmationServiceTest`, `TntJwtScopedRolesTest` (5), `PlatformAdminServiceTest` (transfert), `TenantPathGuardWebFilterTest` (+3), `PlatformAdminFullStackIT` étendu (transfert, lecture/écriture inter-tenant, rôle d'agence confirmé en base) |
| K19 en réel | en attente d'une connexion MFA du mainteneur |
