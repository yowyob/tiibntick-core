# Lot C-18 — Projection du freelancer : rendre « accepter une course » possible sans SQL manuel

## Dépôt cible

**`/home/jtk/projets/tiibntick-core`** — c'est le seul dépôt à modifier dans ce lot.
Ne touche pas à `/home/jtk/projets/tiibntick-bff` ni à l'application mobile.
Branche de travail : pars de `feat/lotC16-role-freelancer` (qui contient déjà C-16 et C-17).

## Règle d'autonomie — à lire avant tout le reste

Tu travailles **seul et jusqu'au bout**, sans jamais me redonner la parole en cours de route.

- **Interdiction absolue** de poser « do you want me to proceed », « should I continue », « veux-tu que je… », ou toute variante. Aucune question intermédiaire.
- Tu prends **toutes** les décisions techniques toi-même. Si une décision est discutable, tu la prends quand même, tu la documentes dans une section « décisions tranchées seul » de ton rapport final, et tu continues.
- Tu ne me parles **qu'une seule fois : à la fin**, avec ton rapport complet.
- Si tu rencontres un blocage, tu ne t'arrêtes pas pour demander : tu contournes, ou tu livres ce qui marche et tu décris précisément le blocage dans le rapport final.
- Tu lances toi-même tous les builds et tous les tests, et tu **colles la sortie brute** (pas un résumé) dans ton rapport.

Maven : le Maven système (3.8.7) est **refusé par l'enforcer** qui exige `[3.9,)`.
Utilise impérativement : `/home/jtk/.local/opt/apache-maven-3.9.9/bin/mvn`

---

## 1. Le problème, établi par lecture du code (ne le réenquête pas, vérifie-le)

Aujourd'hui, en production, un freelancer ne peut **pas** accepter une course. Ce n'est pas un problème de permission (C-16 et C-17 ont réglé le rôle `FREELANCER` et la réconciliation des permissions). C'est un problème de **projections manquantes**.

Quand un utilisateur devient freelancer, la production ne crée **qu'une** ligne : `tnt_actor.freelancer_profiles`
(`identity/tnt-actor-core/.../application/service/FreelancerService.java`, vers l.66 — publication d'un `ActorStatusChangedEvent` avec `reason = "freelancer_profile_created"`).

Or le parcours « accepter une course » a besoin de **trois** lignes de plus :

| Table | Qui la lit | Ce qui casse si elle manque |
|---|---|---|
| `gofp_users` | tout le module gofp | rien — déjà provisionnée automatiquement, voir §2 |
| `gofp_freelancers` | `FreelancerQuotaService.hasRemainingQuota` (l.43-45 : `findById` puis `findByCoreFreelancerId`) | `IllegalStateException("Freelancer has no remaining delivery quota: …")` levée par `AnnouncementApplicationService.respondToAnnouncement` (l.165) **avant** que la FK soit atteinte |
| `tnt_delivery_persons` | `DeliveryAnnouncementService.respondToAnnouncement` → `deliveryPersonRepository.findById(tenantId, freelancerId)` | la souscription échoue, le livreur est introuvable |
| `gofp_freelancers` (encore) | FK `delivery_needs.assigned_freelancer_id → gofp_freelancers(id)` — voir `coreBackend/tnt-go-freelancer-point-back-core/src/main/resources/db/changelog/20260805_fk_assigned_freelancer_to_gofp_freelancers.sql` | l'`assign` viole la contrainte |

**La preuve que ça n'a jamais marché sans SQL manuel** : `scripts/e2e/e2e-freelancer-courier.sh`, vers les lignes 693-719, insère ces deux lignes **à la main** avant `subscribe` / `assign` :

```sql
INSERT INTO gofp_freelancers (id, core_freelancer_id, core_user_id, status, is_active, remaining_deliveries)
VALUES ('${GOFP_FL_ID}', '${GOFP_FL_ID}', '${FREELANCER_USER_ID}', 'APPROVED', true, 100)
ON CONFLICT (id) DO UPDATE SET remaining_deliveries=100, is_active=true, status='APPROVED';

INSERT INTO tnt_delivery_persons (id, tenant_id, actor_id, logistics_type, logistics_class, ...)
VALUES ('${GOFP_FL_ID}', '${E2E_TENANT_ID}', '${FREELANCER_USER_ID}', 'MOTORBIKE', 'STANDARD', ...);
```

avec `GOFP_FL_ID = SELECT id FROM tnt_actor.freelancer_profiles WHERE actor_id = '<user>'`.
Son propre commentaire l.685 le dit : *« soit un 409 quota (pas de gofp_freelancers) »*.

**Et deux causes racines que j'ai identifiées par lecture — vérifie-les et corrige-les :**

1. **`FreelancerEventConsumer` est un simple logger sur le mauvais topic.**
   `coreBackend/tnt-go-freelancer-point-back-core/.../adapter/out/kafka/consumer/FreelancerEventConsumer.java` écoute `@KafkaListener(topics = "delivery-person-created")` et ne fait qu'un `log.info`. Le producteur, lui, publie sur `tnt.actor.status.changed`. Les topics ne correspondent pas, et même s'ils correspondaient rien ne serait inséré.

2. **`POST /api/freelancers/register` ne persiste rien du tout.**
   `application/service/FreelancerRegistrationService.java`, méthode `persistAndNotify` (vers l.134-157) : elle fait `UUID freelancerId = UUID.randomUUID()`, publie un `FreelancerCreatedEvent`, log, et renvoie un `201 Created` avec cet id. **Aucun appel de repository.** L'endpoint répond « créé » sans rien créer. La méthode s'appelle `persistAndNotify` et ne persiste pas.

---

## 2. Le modèle à suivre : il existe déjà dans ce dépôt

Ne réinvente pas un mécanisme. Le module gofp provisionne **déjà** `gofp_users` paresseusement, et ce code marche :

- `coreBackend/tnt-go-freelancer-point-back-core/.../application/service/GofpUserProvisioningService.java` — `provisionIfAbsent(UUID coreUserId)`, idempotent via `findByCoreUserId(...).switchIfEmpty(Mono.defer(...))`, crée une ligne minimale avec un email système.
- `coreBackend/tnt-go-freelancer-point-back-core/.../adapter/in/web/GofpUserProvisioningFilter.java` — `WebFilter` `@Order(0)`, lit `authentication.getName()` (= claim `sub`), erreurs loguées et avalées, compteur Micrometer `gofp.provisioning.failures`.

C'est ce style-là que j'attends : **idempotent, best-effort, observable par un compteur, jamais bloquant pour la requête**.

**Et la direction des dépendances t'autorise à tout faire depuis le module gofp.** Vérifié dans `coreBackend/tnt-go-freelancer-point-back-core/pom.xml` : ce module dépend de `tnt-actor-core`, `tnt-delivery-core`, `tnt-roles-core`, `tnt-common-core`. Donc la projection doit vivre **dans le module gofp**, qui peut légalement voir le profil acteur et le `DeliveryPersonRepository`. Ne crée **aucune** dépendance de `tnt-actor-core` vers `coreBackend/*` : ce serait une inversion et `LayeringArchitectureTest` (dans `tnt-bootstrap`) doit rester vert.

Brique déjà disponible et à réutiliser : `GofpFreelancerService.createOrUpdate(GofpFreelancer)` (vers l.52-73) est déjà idempotent sur `coreFreelancerId` et **respecte un `id` fourni** (`if (freelancer.getId() == null) freelancer.setId(UUID.randomUUID())`). Elle ne porte aucun `@RequirePermission`.

---

## 3. L'invariant à respecter — c'est le cœur du lot

**Un seul identifiant de freelancer, partagé par trois tables :**

```
tnt_actor.freelancer_profiles.id
        == gofp_freelancers.id      (et aussi gofp_freelancers.core_freelancer_id)
        == tnt_delivery_persons.id
```

Pourquoi cet invariant et pas un autre :
- le matching GOFP produit `candidate.freelancerId` = `gofp_freelancers.id` (voir `FreelancerProviderAdapter#fromGofp`, cité par le commentaire de la migration `20260805_…`) ;
- la FK `delivery_needs.assigned_freelancer_id` pointe vers `gofp_freelancers(id)` ;
- `DeliveryAnnouncementService.respondToAnnouncement` cherche ce même id dans `tnt_delivery_persons.id` ;
- et le E2E, en choisissant `freelancer_profiles.id` comme valeur commune, a déjà tranché lequel des trois est la source.

Si tu estimes que cet invariant est mauvais, tu as le droit de proposer autre chose — mais alors tu le démontres par les appels de code, tu le documentes, et tu adaptes le E2E en conséquence.

**Attention à `gofp_freelancers.core_user_id`** : contrainte `fk_gofp_freelancers_user FOREIGN KEY (core_user_id) REFERENCES gofp_users(core_user_id) ON DELETE CASCADE` (migration `20260726_create_gofp_actor_tables.sql`, section 3). La ligne `gofp_users` doit donc exister **avant** l'insertion. Le filtre existant s'en charge pour toute requête authentifiée, mais ne t'appuie pas là-dessus par chance : appelle `GofpUserProvisioningService.provisionIfAbsent` explicitement dans ta projection.

**Attention à `DeliveryPerson.register(...)`** : `logistics/tnt-delivery-core/.../domain/…/DeliveryPerson.java` l.72-73 force `id(UUID.randomUUID())` et `status(PENDING)`, et rejette `tankCapacity <= 0`. Cette factory ne peut donc **pas** servir à projeter un id imposé. Ajoute une factory de projection explicite (par exemple `DeliveryPerson.projection(UUID id, UUID tenantId, UUID actorId, …)`) plutôt que de contourner par un builder nu depuis un autre module ; documente-la clairement comme réservée à la projection.

---

## 4. Travail à faire

### 4.1 — La projection (le cœur)

Crée un service de provisioning du freelancer dans le module gofp, à côté de `GofpUserProvisioningService`, qui pour un `coreUserId` donné :

1. résout la ligne `tnt_actor.freelancer_profiles` de cet acteur et en tire `profileId` ; si elle n'existe pas → ne rien faire (ce n'est pas un freelancer) ;
2. garantit la ligne `gofp_users` (`provisionIfAbsent`) ;
3. garantit la ligne `gofp_freelancers` avec `id = core_freelancer_id = profileId`, `core_user_id = coreUserId`, `is_active = true`, un `remaining_deliveries` initial issu d'une **constante nommée et documentée** (le E2E utilise 100 ; choisis, justifie, n'invente pas une valeur muette) ;
4. garantit la ligne `tnt_delivery_persons` avec `id = profileId`, `tenant_id`, `actor_id = coreUserId`.

**Décision de statut — à trancher et à documenter.** Le E2E met `APPROVED` des deux côtés. En vraie vie il devrait probablement y avoir une validation KYC avant approbation. Pour ce lot, mets le statut qui rend le parcours testable de bout en bout, mais **laisse un `TODO` explicite** nommant la décision métier reportée, et dis-le dans ton rapport final. Ne masque pas ce compromis.

**Idempotence obligatoire** : appeler la projection dix fois de suite doit produire exactement une ligne par table, sans erreur, sans écraser un quota déjà consommé. Un `remaining_deliveries` descendu à 3 par l'usage ne doit **pas** être remis à 100 par un second passage.

### 4.2 — Le point de déclenchement

Choisis **un seul** point de déclenchement, et justifie-le :
- soit à la création du profil freelancer (le chemin que l'application mobile emprunte réellement — identifie-le avant de choisir) ;
- soit paresseusement, au premier usage freelancer, sur le modèle du filtre existant ;
- soit en réparant le consumer Kafka (topic `tnt.actor.status.changed`, filtré sur `reason == "freelancer_profile_created"`).

Contrainte forte si tu retiens la voie Kafka : `ActorStatusChangedEvent` (`identity/tnt-actor-core/.../domain/event/ActorStatusChangedEvent.java`) ne transporte que `actorId`, `tenantId`, `oldStatus`, `newStatus`, `reason`, `occurredAt` — **pas le `profileId`**. Il te faudra donc soit le résoudre par requête côté consumer, soit enrichir l'événement. Si tu enrichis un événement partagé, tu vérifies tous ses autres consommateurs.

Dans tous les cas, **ne laisse pas en place un consumer mort** : `FreelancerEventConsumer` doit soit devenir fonctionnel, soit être supprimé. Un `@KafkaListener` sur un topic que personne n'alimente est un piège pour le prochain lecteur.

### 4.3 — Réparer `POST /api/freelancers/register`

`FreelancerRegistrationService.persistAndNotify` doit persister ce qu'elle annonce, ou l'endpoint doit disparaître. Un `201 Created` qui ne crée rien est un mensonge d'API et c'est exactement le genre de chose qui n'explose qu'au moment de l'APK. Tranche, et justifie.

### 4.4 — Le correctif d'outbox de C-17

`foundation/tnt-roles-core/.../KernelRoleSyncWorker.java` l.144 : `case UPDATE_ROLE -> Mono.empty();`.
`processEntry` (l.128-136) sauvegarde `entry.asProcessing()` puis appelle `dispatch`. Tous les autres `case` terminent par `outboxRepository.save(entry.asProvisioned(...))`, seul chemin vers l'état terminal. `UPDATE_ROLE` ne sauvegarde rien, donc l'entrée **reste en `PROCESSING` à vie** : `FETCH_PENDING_SQL` filtre `WHERE status IN ('PENDING','RETRYING')` (`adapter/out/persistence/RoleSyncOutboxRepositoryAdapter.java` l.59-67), donc pas de boucle de retry, mais accumulation de lignes zombies et `processed_at` qui reste `NULL`.

Correctif attendu, une ligne, plus un test qui l'atteste :

```java
case UPDATE_ROLE -> outboxRepository.save(entry.asProvisioned(entry.kernelRefId())).then();
```

(`asProvisioned` accepte un `kernelRefId` nul et estampille `processedAt` — vérifié dans `RoleSyncOutboxEntry.java` l.120-126.)

### 4.5 — Amputer le E2E de ses béquilles

`scripts/e2e/e2e-freelancer-courier.sh` contient **treize `INSERT INTO`**. Plusieurs sont des fixtures légitimes (préparer l'annonce de l'expéditeur, par exemple). Trois groupes ne le sont pas, et masquent du code de production absent ou non exercé :

| Lignes | Ce qui est semé | Pourquoi c'est une béquille à retirer |
|---|---|---|
| ~546 et ~565 | rôle ad hoc `E2E_COURIER_C4` avec `announcement:respond,announcement:elect,wallet:read,freelancer:read`, puis `tnt_user_role_assignments` | **Le E2E contourne entièrement le rôle `FREELANCER`.** Il fabrique son propre rôle et se l'assigne en SQL. Donc l'attribution automatique du rôle livrée en C-16 (`FreelancerService.grantFreelancerRole`) et la réconciliation des permissions livrée en C-17 **n'ont jamais été exercées par ce harnais**. Retire ce rôle ad hoc : le parcours doit réussir avec le rôle `FREELANCER` canonique, attribué automatiquement. |
| ~696 et ~711 | `gofp_freelancers` et `tnt_delivery_persons` | les deux projections manquantes, objet principal de ce lot |
| ~1260 | `billing.wallet_wallets` | quatrième projection potentiellement manquante — voir la question 4 du §8 |

Retire aussi les `DELETE` de nettoyage correspondants (vers l.200-204 et l.541-542) et le seed l.926-935 s'il repose sur la même béquille.

Remplace chacun par une **assertion** : après le parcours normal, le script doit vérifier que la ligne existe, avec le bon id, le bon statut et les bonnes permissions, **sans l'avoir écrite**. C'est exactement l'inverse de ce qu'il fait aujourd'hui.

Si un de ces retraits révèle un manque hors du périmètre de ce lot (le wallet, typiquement), tu ne le bricoles pas en douce : tu le laisses rouge, tu le nommes précisément dans « ce que ce lot ne prouve pas », et tu proposes le lot suivant.

Un harnais E2E qui sème ce que la production doit produire transforme un bug de prod en test vert. C'est ce qui nous a coûté deux semaines. Ça ne doit plus pouvoir se reproduire.

---

## 5. Interdictions

- **Aucun `INSERT INTO gofp_freelancers` ni `INSERT INTO tnt_delivery_persons` dans une migration Liquibase**, et aucun dans un script de test. Ajoute un test-garde qui échoue si un tel `INSERT` réapparaît dans `db/changelog/` (tu as déjà écrit `TntActorCoreChangelogGuardTest` en C-17 — même principe).
- Aucune dépendance nouvelle de `tnt-actor-core`, `tnt-delivery-core` ou `foundation/*` vers `coreBackend/*`.
- Aucun `onErrorResume` supprimé, aucun garde dont le commentaire interdit la suppression. Si un test rouge t'y pousse, c'est le test ou ton code qui est faux, pas le garde.
- Aucun `@RequirePermission` retiré ni affaibli pour faire passer un test.
- Ne pousse rien. Tu commits sur la branche, tu ne fais pas `git push`.

---

## 6. Tests exigés

Écris au minimum, et fais-les passer :

1. Projection depuis zéro : les trois lignes créées, avec `id` égal des trois côtés.
2. Idempotence : deux appels consécutifs → une seule ligne par table, aucune erreur.
3. Idempotence non destructive : `remaining_deliveries` préalablement à 3 n'est pas remis à la valeur initiale par un second passage.
4. Acteur sans profil freelancer → aucune ligne créée, aucune erreur.
5. `gofp_users` absent → provisionné avant l'insertion, pas de violation de FK.
6. Best-effort : si la projection échoue, la requête appelante n'échoue pas et le compteur d'échecs est incrémenté.
7. `UPDATE_ROLE` : l'entrée d'outbox termine en `PROVISIONED` avec `processed_at` non nul.
8. Test-garde : aucun `INSERT INTO gofp_freelancers` / `tnt_delivery_persons` dans les changelogs.

---

## 7. Preuve exigée — c'est le seul critère d'acceptation

Un rapport vert de tests unitaires **ne suffira pas** pour ce lot. Ce que j'attends, c'est :

1. La sortie brute de `mvn -pl <modules touchés> test` avec le Maven 3.9.9, ligne `Tests run:` visible.
2. La sortie brute de `mvn -pl tnt-bootstrap test` (ou de la commande équivalente) montrant `LayeringArchitectureTest` vert.
3. **La sortie brute du run complet de `scripts/e2e/e2e-freelancer-courier.sh`, avec les `INSERT` manuels retirés**, montrant `subscribe` puis `assign` réussis. Sans cette sortie, le lot est considéré non livré.
4. Les requêtes SQL de contrôle que tu as lancées toi-même après le run, avec leur résultat, prouvant que les trois lignes ont été créées **par le code de production** :

```sql
SELECT id, core_freelancer_id, core_user_id, status, is_active, remaining_deliveries
  FROM gofp_freelancers WHERE core_user_id = '<user>';
SELECT id, tenant_id, actor_id, status FROM tnt_delivery_persons WHERE actor_id = '<user>';
SELECT id FROM tnt_actor.freelancer_profiles WHERE actor_id = '<user>';
```

Et une phrase explicite dans le rapport : les trois `id` sont-ils identiques, oui ou non.

5. La requête prouvant que le parcours a fonctionné avec le **rôle canonique `FREELANCER`**, attribué automatiquement, et non avec un rôle fabriqué par le script :

```sql
SELECT r.code, r.permissions
  FROM tnt_user_role_assignments a
  JOIN tnt_roles r ON r.id = a.role_id
 WHERE a.user_id = '<user>';
```

Le résultat doit contenir `FREELANCER` et **ne doit plus contenir** `E2E_COURIER_C4`. C'est la seule preuve que C-16 et C-17 servent réellement à quelque chose.

---

## 8. Questions auxquelles ton rapport doit répondre

1. Quel est **exactement** le chemin que l'application mobile emprunte pour devenir freelancer (endpoint, service, méthode, fichier:ligne) ? C'est ce chemin, et pas un autre, qui doit déclencher la projection.
2. `POST /api/freelancers/register` : réparé ou supprimé, et pourquoi ?
3. Le statut initial : `APPROVED` ou `PENDING` + approbation, et quelle décision métier as-tu reportée dans le `TODO` ?
4. Y a-t-il d'autres projections manquantes que ces trois-là sur le parcours complet (souscrire → être élu → démarrer → livrer → être payé) ? Le wallet est indexé sur `tnt_delivery_persons.actor_id` (voir le E2E vers l.1256) — est-ce satisfait par ta projection ?
5. Le `FreelancerEventConsumer` : réparé, ou supprimé ?

---

## 9. Format du rapport final

- Ce que tu as changé, fichier par fichier, avec le nombre de lignes.
- Sorties brutes complètes des §7.1, §7.2, §7.3, §7.4.
- **Décisions tranchées seul** — la liste, avec la justification de chacune.
- **Ce que ce lot ne prouve pas** — sois aussi précis ici que dans le reste. Cette section est celle que je lis en premier.
- Les réponses aux cinq questions du §8.
- Le hash du commit.
