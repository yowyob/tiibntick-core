# Demande à l'équipe Kernel — organisation dédiée aux notifications TiiBnTick

**À :** équipe Kernel (TSAFACK Savio) · **De :** équipe TiiBnTick Core · **Date :** 2026-11-11
**Environnement :** production, `https://kernel-core.yowyob.com/kernel-api`

> **Statut (2026-11-21) : résolu côté Kernel.** L'équipe Kernel a fourni le tenant `fbd90da6-629d-4d78-9a36-8735ff1762cb` et l'organisation « TiiBnTick Notif » (`a804a304-fb65-4970-bf97-b94390cdbdff`, `APPROVED`, abonnée à `NOTIFICATION`) ; le client `tibntick-backend` y est autorisé pour `NOTIFICATION`. Le contenu ci-dessous reste l'historique de la demande initiale (tenant `b8b17c6e-…`, abandonné).

## Objectif

Le Core TiiBnTick délègue ses notifications au Kernel (service `NOTIFICATION`). Nous voulons un tenant dédié, avec une organisation abonnée à `NOTIFICATION`, qui sera aussi le tenant système de TiiBnTick. Nous avons besoin de deux valeurs pour la configuration du Core : `TNT_NOTIFY_KERNEL_DEFAULT_TENANT_ID` et `TNT_NOTIFY_KERNEL_DEFAULT_ORGANIZATION_ID`.

## Ce que nous avons fait

| Étape | Résultat |
|---|---|
| Compte `yow_npk@yowyob.com` (user `248db015-4461-45c4-96f1-ff0813c25027`, tenant d'origine `11111111-1111-1111-1111-111111111111`, acteur `ed47489e-ecc2-4fc7-9a21-b20d7c615182`) | connecté avec MFA dans les deux tenants |
| `POST /api/auth/me/spaces` | tenant **`b8b17c6e-9bbd-4b4b-8d54-b162cabeea64`** créé, le compte y est `OWNER` (`GET /api/auth/me/memberships` le confirme) |
| `POST /api/actors/onboarding` dans le tenant `b8b17c6e-…` | 404 `BUSINESS_ACTOR_NOT_FOUND` — « User account 248db015-… is not linked to an actor » |
| `POST /api/actors/onboarding` dans le tenant d'origine `11111111-…` | **201**, acteur métier `fcbc53dc-a673-457b-873e-4e148d32cea3`, `governanceStatus: APPROVED` (« auto-approved by platform options »), 2026-11-11T17:29:05Z |
| `POST /api/actors` (`CreateActorRequest`) dans le tenant `b8b17c6e-…` | **201**, acteur `8b22fe9a-d5c2-4d2e-8306-90f245d1ece9` créé dans ce tenant (2026-11-11T17:48:04Z), mais `GET /api/actors/me` répond toujours 404 « not linked to an actor » et `users/me` garde `actorId = ed47489e-…` : l'acteur n'est **pas relié au compte** |
| Jeton du tenant d'origine (émis après l'onboarding) : `GET /api/actors/me`, `GET /api/users/me` | toujours 404 `BUSINESS_ACTOR_NOT_FOUND` et `businessActorId: null` (2026-11-11T17:54:50Z) |
| Jeton du tenant d'origine + en-tête `X-Tenant-Id: b8b17c6e-…` (parcours « autre `X-Tenant-Id` autorisé » de la doc §5bis) : `POST /api/organizations`, puis `GET /api/auth/me/memberships` | **401**, corps vide, `www-authenticate: Basic realm="Realm"` : le Kernel refuse un jeton dont le `tid` diffère de `X-Tenant-Id` |
| `POST /api/organizations` dans le tenant `b8b17c6e-…` avec `businessActorId=fcbc53dc-…` | 409 `BUSINESS_ACTOR_NOT_APPROVED` (2026-11-11T17:30:05Z), alors que l'acteur est `APPROVED` |

## Anomalies constatées

1. **Compte non relié à un acteur dans un tenant créé par `me/spaces`.** Dans `b8b17c6e-…`, `GET /api/actors/me` et `POST /api/actors/onboarding` répondent 404 `BUSINESS_ACTOR_NOT_FOUND` (« not linked to an actor »). `GET /api/users/me` y renvoie `tenantId = 11111111-…` et `actorId = ed47489e-…` : l'acteur du compte vit dans le tenant d'origine.
2. **Acteur métier créé mais introuvable.** Après le 201 ci-dessus, dans le tenant d'origine :
   - `GET /api/actors/me` répond 404 « Business actor profile was not found for actor ed47489e-… » (constaté à 17:28:47Z, 17:30:48Z et 17:31:37Z, soit plus de 2 minutes après la création) ;
   - `GET /api/users/me` renvoie `businessActorId: null` et `businessActorStatus: null`.
3. **409 `BUSINESS_ACTOR_NOT_APPROVED` sur un acteur approuvé.** Même `businessActorId` explicite, tenant actif `b8b17c6e-…`.
4. **Doublon créé par notre erreur.** Un second appel à `POST /api/actors/onboarding` (2026-11-11T17:31:39Z) a créé un deuxième acteur, `a00c593e-8070-44bf-b636-d2e3c99fb880`, avec le même code `TIIBNTICK_NOTIFICATIONS`. Nous avons arrêté d'écrire sur le Kernel après cela.
5. **Aucune voie self-service pour relier un acteur au compte.** Le compte n'a qu'un `actorId` (celui du tenant d'origine) ; ni `actors/onboarding` ni `POST /api/actors` ne le relient à un acteur du tenant créé par `me/spaces`.
6. **Droits de gouvernance.** `yow_npk` n'est pas admin plateforme : `GET /api/administration/governance/business-actors` et `GET /api/organizations/my` (tenant d'origine) répondent 403 `ACCESS_DENIED`. Nous ne pouvons ni fusionner les doublons, ni approuver.

## Ce que nous vous demandons

1. **Acteur métier** : relier le compte `yow_npk@yowyob.com` à un acteur métier approuvé dans le tenant `b8b17c6e-…` (acteur `8b22fe9a-d5c2-4d2e-8306-90f245d1ece9` déjà créé dans ce tenant, ou l'un des deux acteurs métier du tenant d'origine), ou nous indiquer le parcours attendu pour un tenant créé par `me/spaces`.
2. **Doublons** : supprimer ou fusionner l'acteur `8b22fe9a-…` s'il ne sert pas, et fusionner `a00c593e-8070-44bf-b636-d2e3c99fb880` dans `fcbc53dc-a673-457b-873e-4e148d32cea3` (`POST /api/administration/governance/business-actors/{primary}/merge/{duplicate}`) — ou supprimer l'un des deux.
3. **Organisation** : créer dans `b8b17c6e-…`, pour cet acteur, une organisation (proposition : code `TNT_NOTIFICATIONS`, nom « TiiBnTick System Notifications », service `NOTIFICATIONS`), l'approuver, puis l'abonner : `POST /api/organizations/{id}/services {"serviceCode":"NOTIFICATION"}`.
4. **Application cliente** : ajouter `NOTIFICATION` aux `allowedServices` de l'application `prod-platform-backend` (`d6fe3909-c257-4418-958d-de48b7d88f37`). Aujourd'hui l'envoi d'une notification par le Core répond 403 `CLIENT_APPLICATION_SERVICE_NOT_ALLOWED`.
5. **Nous renvoyer** l'identifiant de l'organisation créée.

## Parcours testés sans succès (documentation `kernel-core-workflows`)

- §5/§14 : espace → `actors/onboarding` → organisation : échoue au lien compte-acteur (voir ci-dessus).
- §5bis : organisation dans « un autre tenant autorisé » via `X-Tenant-Id` : refusé en 401 (jeton et en-tête de tenants différents).
- §6 : nouveau jeton après approbation du business actor : sans effet, le profil reste introuvable.

## Questions

- Cette application cliente est liée au tenant `af1f5fb6-…` ; accepte-t-elle des appels pour le tenant `b8b17c6e-…`, ou faut-il l'y rattacher ?
- Le comportement du point 2 (acteur créé, 201, mais non retrouvé) est-il un défaut connu (cache, lien acteur/compte manquant) ?
- Le §5bis de la doc annonce qu'une organisation peut être créée « avec un autre `X-Tenant-Id` autorisé » : quel jeton faut-il alors fournir, puisque `tid` ≠ en-tête donne un 401 ?
- Faut-il pour nous une permission particulière pour consulter `GET /api/organizations/my` et `GET /api/actors/me` dans le tenant d'origine ?

## Après votre intervention

Nous configurerons le Core avec :

```
TNT_SYSTEM_TENANT_ID=b8b17c6e-9bbd-4b4b-8d54-b162cabeea64
TNT_NOTIFY_KERNEL_DEFAULT_TENANT_ID=b8b17c6e-9bbd-4b4b-8d54-b162cabeea64
TNT_NOTIFY_KERNEL_DEFAULT_ORGANIZATION_ID=<identifiant de l'organisation>
```

Nous pourrons alors refaire un envoi de notification de bout en bout sur la production.
