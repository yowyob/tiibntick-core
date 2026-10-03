# Lot C-17 — Annuler la migration V5, accorder `announcement:elect`, réconcilier les permissions

## Dépôt cible

**`/home/jtk/projets/tiibntick-core` — et uniquement celui-là.**
Tu continues sur la branche existante `feat/lotC16-role-freelancer` (HEAD = `09e10cd5`).
Tu ne touches ni `/home/jtk/projets/tiibntick-bff` ni `/home/jtk/projets/tiibntick-mobile`.

## Règle d'autonomie — lis-la avant tout

Tu travailles **seul, du début à la fin, sans jamais demander la permission de continuer.**
Aucune question du type « veux-tu que je procède ? », « dois-je continuer ? », « souhaites-tu que j'applique ? ».
Tu tranches toi-même chaque décision intermédiaire et tu la documentes dans le rapport final.
**Tu prends la parole une seule fois : à la fin, avec le rapport.**
Si un point est ambigu, tu choisis l'option la plus défensive, tu l'implémentes, et tu l'inscris dans
« Décisions tranchées seul ».

## Pourquoi ce lot existe : le lot C-16 contenait une régression silencieuse

Le lot C-16 a été demandé sur la base d'une instruction fausse — **le rôle `FREELANCER` existait déjà.**
Mesure :

- `foundation/.../roles/domain/model/TntRole.java` l.122 : entrée `FREELANCER("FREELANCER", "Freelancer Deliverer", RoleScopeType.TENANT, true, Set.of(...))` avec **~25 permissions**, dont `ANNOUNCEMENT_RESPOND`.
- `foundation/.../roles/domain/model/TntPermission.java` l.51-52 : `ANNOUNCEMENT_RESPOND = "announcement:respond"`, `ANNOUNCEMENT_ELECT = "announcement:elect"`.
- `TntRoleInitializationService.provisionSystemRoles()` est annoté `@EventListener(ApplicationReadyEvent.class)` et appelle `provisionForTenant(systemTenantId)` → `upsertIfAbsent(...)`.
- `upsertIfAbsent` fait `roleRepository.existsByCode(tenantId, code)` et **retourne `Mono.empty()` si le rôle existe** : il ne met jamais à jour les permissions.

Conséquence de la migration `V5__seed_role_freelancer.sql` : **Liquibase s'exécute avant `ApplicationReadyEvent`.**
Pour tout tenant déjà présent dans `tnt_roles` mais sans ligne `FREELANCER`, V5 insère un rôle à
**3 permissions**. Au démarrage suivant, `upsertIfAbsent` voit `exists == true` et **saute le seed canonique**.
Le rôle `FREELANCER` reste donc amputé de `mission:start`, `mission:complete`, `delivery:read`,
`delivery:track`, `delivery:confirm`, `delivery:proof`, `wallet:read`, `wallet:write`, `payment:process`,
`trust:read`, `trust:verify`, `media:read`, `media:upload`, `geo:read`, `route:read`, `dispute:*`, `incident:*`,
`actor:read`, `announcement:read`.

C'est une régression qui ne se voit dans aucun test unitaire, parce que les tests de C-16 utilisent des mocks.
**Elle ne doit pas partir en production.**

## Travail demandé

### 1. Supprimer la migration V5

- `git rm identity/tnt-actor-core/src/main/resources/db/changelog/migrations/V5__seed_role_freelancer.sql`
- Retirer son inclusion de `identity/tnt-actor-core/src/main/resources/db/changelog/tnt-actor-master.yaml`.

Aucune migration ne doit insérer de rôle : `TntRole` est la source de vérité unique, et
`TntRoleInitializationService` est le seul mécanisme de provisionnement. Écris cette phrase en Javadoc
sur `TntRoleInitializationService` pour que personne ne refasse l'erreur.

### 2. Accorder `announcement:elect` au rôle `FREELANCER`

Dans `TntRole.java`, ajoute `ANNOUNCEMENT_ELECT` à l'ensemble des permissions de `FREELANCER`.

Raison : le BFF accepte une course en deux appels — `POST /api/announcements/{id}/subscribe`
(→ `AnnouncementApplicationService.respondToAnnouncement`, `@RequirePermission(announcement:respond)`, l.165)
puis `POST /api/announcements/{id}/assign` (→ `assignResponse`/`assignFreelancer`,
`@RequirePermission(announcement:elect)`, l.238 et l.263). Sans `elect`, le second appel renvoie 403.

**Écris explicitement dans le rapport** que c'est une dette de conception assumée : `elect` signifie
« le client élit un livreur », et on autorise ici le livreur à s'auto-élire. La correction propre serait un
écran « choisir un candidat » côté expéditeur ; elle est hors de ce lot.

### 3. Réconcilier les permissions des rôles déjà en base — le vrai correctif de fond

`upsertIfAbsent` ne met jamais à jour un rôle existant. Donc **modifier `TntRole` n'a aucun effet sur une base
déjà provisionnée** — y compris le core déployé, dont le rôle `FREELANCER` existe déjà sans `elect`.

Transforme `upsertIfAbsent` en réconciliation :

- rôle absent → comportement actuel (`provisionAndEnqueue`), inchangé ;
- rôle présent **et** `permissions` identiques à `definition.defaultPermissions()` → no-op, log `debug` ;
- rôle présent **et** `permissions` différentes → **mise à jour** vers l'ensemble canonique, log `info` nommant
  les permissions ajoutées et retirées, et une entrée d'outbox `RoleSyncOutboxEntry` de la même façon que
  `provisionAndEnqueue` (détermine l'opération correcte en lisant `RoleSyncOperation` ; si aucune n'existe pour
  la mise à jour, ajoute-la et dis-le).

Contrainte : la réconciliation ne touche que les rôles `system_role = true`. Un rôle créé par un tenant
(`editable = true`) ne doit jamais être écrasé — vérifie-le par un test.

Renomme la méthode pour que son nom dise ce qu'elle fait.

### 4. Tests

Tous sur le comportement observable, pas sur les appels de méthodes :

1. Rôle absent → créé avec l'ensemble canonique complet + une entrée d'outbox.
2. Rôle présent avec permissions identiques → aucune écriture (ni `save`, ni outbox).
3. Rôle présent avec permissions amputées (exactement le cas V5 : les 3 permissions) → mis à jour vers
   l'ensemble canonique complet, outbox enfilé, et l'assertion porte sur **le contenu final des permissions**.
4. Rôle `editable = true` avec des permissions différentes → **non modifié**.
5. `TntRole.FREELANCER.defaultPermissions()` contient `announcement:respond` **et** `announcement:elect`.
6. Un test qui échouerait si une migration Liquibase contenait `INSERT INTO tnt_roles` — par exemple un test
   qui scanne les fichiers de changelog du dépôt. Si tu juges ce test trop fragile, propose-en un autre qui
   protège la même invariante, et justifie.

### 5. La preuve par le run

Colle la **sortie brute** de :

```
/home/jtk/.local/opt/apache-maven-3.9.9/bin/mvn -pl foundation/tnt-roles-core -am test
/home/jtk/.local/opt/apache-maven-3.9.9/bin/mvn -pl identity/tnt-actor-core -am test
```

(Maven 3.8.7 du système est refusé par l'enforcer : utilise bien le chemin ci-dessus.)

Tu ne rapportes aucun vert que tu n'as pas exécuté. Un rapport annonçant vert sans la sortie
correspondante sera rejeté.

### 6. Répondre à la question restante, par lecture du code

`FreelancerEventConsumer` (dans `coreBackend/tnt-go-freelancer-point-back-core`) est censé projeter le profil
acteur dans `gofp_freelancers` en réaction à l'événement Kafka publié par
`IActorEventPublisher.publishActorStatusChanged("freelancer_profile_created")`.

Réponds précisément à ces trois questions, citations de code à l'appui :

1. Sur quel **topic** l'événement est-il publié, et est-ce bien celui que `FreelancerEventConsumer` écoute ?
2. Le consommateur **insère-t-il** une ligne dans `gofp_freelancers`, et avec quelle valeur dans `id` —
   un UUID nouveau, ou l'identifiant du profil acteur ?
3. La valeur que le BFF envoie dans `assigned_freelancer_id` (l'`id` de `FreelancerProfileResponse`)
   satisfait-elle la clé étrangère
   `delivery_notifications.assigned_freelancer_id → gofp_freelancers(id)`
   (`coreBackend/.../20260805_fk_assigned_freelancer_to_gofp_freelancers.sql`) ?

Si la réponse à 3 est non, **n'implémente rien** : décris en cinq lignes ce que devrait faire un lot C-18.

## Rapport final attendu

1. Tableau des commits (SHA + sujet)
2. Diff résumé fichier par fichier
3. Sortie brute des deux commandes Maven
4. Décisions tranchées seul
5. Ce que ce lot ne prouve pas
6. Réponses aux trois questions de la section 6
