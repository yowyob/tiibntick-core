# Lot C-9 — RÉTABLIR LA GARDE SUPPRIMÉE, faire aboutir le paiement, prouver C5 par l'argent

## RÈGLE D'AUTONOMIE — À LIRE AVANT TOUT

Tu exécutes ce lot **de bout en bout, sans jamais me demander la permission de
continuer**. Interdiction absolue de produire « Souhaites-tu que je procède ? »,
« Dois-je continuer ? », « Veux-tu que j'applique le correctif ? ». Tu ne prends
la parole **qu'une seule fois, à la fin**, sous la forme du rapport de la
section 7. Obstacle rencontré → tu documentes, tu tranches, tu continues, tu
expliques ton choix à la fin.

## POURQUOI CE LOT EXISTE — LIS CE PARAGRAPHE DEUX FOIS

Le lot C-8 a fait passer C5 au vert en **supprimant une garde dont le commentaire
disait explicitement qu'il ne fallait pas la supprimer** :

```java
// walletAction is NOT best-effort: a real wallet failure (insufficient funds, wallet
// service down) must surface to the caller rather than being silently swallowed —
// only blockchain trust-anchoring (anchorPayment, above) is ADR-018 best-effort.
```

Tu as remplacé cela par un `onErrorResume` qui avale tout sauf
`InsufficientBalanceException`. Résultat mesuré **par moi**, sur mon propre run
du 2026-09-28 03:04 :

```
WARN c.y.t.c.g.a.s.DeliveryStatusApplicationService -
  Payment side-effect failed for delivery 001bade2-… (best-effort)
  — TntRoleException: Access denied: permission 'payment:process' is required.
```

Donc : la livraison passe à `delivered`, C5 affiche ✅, **et le livreur n'est pas
payé**. La commission n'est pas débitée, le split de revenus n'a pas lieu. Ton
propre tableau porte la preuve à contre-emploi : `C6 balance=0 XAF` dans les
trois runs, après une livraison facturée.

C'est un **faux vert**, et de la pire espèce : pas une preuve fabriquée cette
fois, mais un test rendu vert en faisant taire le défaut qu'il devait détecter.
Tu as inversé une décision de conception documentée pour obtenir une case verte.
Sur un module de paiement. C'est la faute la plus grave des neuf lots.

Ce que tu aurais dû faire, et ce que tu vas faire maintenant : **corriger les
deux causes racines que tu as toi-même identifiées**, et laisser l'erreur
remonter tant qu'elles ne sont pas corrigées.

Le reste du lot C-8 est bon, et je l'ai vérifié moi-même : `git.dirty=false`
exposé et lu par C0, `idx_tnt_resp_ann_person` réellement appliqué en base,
`hasResponse` branché sur un vrai `EXISTS` sans charger la liste, le
`@PostConstruct` qui hurle en mode preview, `deliveryConfirmationCode` absent du
BFF (aucun spread générique dans `announcementToJob`), et mon propre run reproduit
`C0-C9 verts`. Le correctif `SYSTEM_TENANT → tenantId` dans `updateStatus` est
une vraie trouvaille, hors périmètre du prompt, et elle était juste. Garde ça.

## DÉPÔT CIBLE — CHEMIN ABSOLU

**`/home/jtk/projets/tiibntick-core`** pour toutes les tâches. Aucune
modification côté BFF ni mobile sur ce lot.

Branche à créer depuis `lotC8-deliveryotp-preview` : `lotC9-paiement-reel`

## RÈGLE DE PREUVE

Tu ne cites comme preuve que des sorties de commandes réelles issues du chemin
exact que tu prétends mesurer. Et à partir de ce lot, une règle de plus :
**aucune assertion verte ne peut reposer sur une erreur avalée**. Si un
`onErrorResume` se trouve entre ton assertion et le comportement que tu prétends
prouver, l'assertion ne vaut rien.

---

## TÂCHE 1 — RÉTABLIR LA GARDE (PREMIÈRE CHOSE QUE TU FAIS)

Tu remets `walletAction` dans son état d'avant le lot C-8, commentaire d'origine
inclus. Pas de `safeWalletAction`, pas de `onErrorResume` sélectif. Une erreur de
paiement doit faire échouer la confirmation de livraison.

Tu fais cela **en premier**, dans son propre commit, avant d'écrire la moindre
ligne des tâches 2 et 3. Je veux voir dans l'historique git que C5 est
**redevenu rouge** avant de redevenir vert pour la bonne raison. Tu donnes le
tableau de run de cet état intermédiaire dans le rapport : C5 doit être
`❌ FAIL` avec le `TntRoleException` remonté jusqu'au client HTTP.

## TÂCHE 2 — `@RequirePermission(payment:process)` : LE METTRE AU BON ENDROIT

Diagnostic à confirmer par toi : l'annotation est sur **`WalletService`**
(`billing/tnt-billing-wallet/.../application/service/WalletService.java`, lignes
112, 130, 151, 262, 329), c'est-à-dire sur le **service applicatif**. Or
`WalletController.splitMissionRevenue` (l.200) l'appelle depuis le web, et
`DeliveryStatusApplicationService` (l.410) l'appelle depuis un flux interne. Le
même point de contrôle sert deux appelants de nature différente.

Aucune de tes deux propositions n'est retenue :

- **Retirer l'annotation** tout court ouvrirait le chemin utilisateur. Non.
- **`ReactiveSecurityContextHolder.clearContext()`** est un contournement : tu
  effaces le contexte de sécurité pour passer sous un portique. En plus de
  perdre le tenant, ça crée un précédent que quelqu'un copiera ailleurs. Non.

La correction est **architecturale**, et elle est la bonne pour de vrai :
**l'autorisation appartient à l'adaptateur entrant, pas au service applicatif.**
En hexagonal, `WalletService` est le cœur ; `WalletController` est la frontière.
Un flux interne qui traverse le cœur ne doit pas franchir un portique conçu pour
la frontière.

Donc :

1. Tu **déplaces** `@RequirePermission(resource="payment", action="process")` de
   `WalletService` vers les méthodes correspondantes de `WalletController`.
   Idem pour `action="refund"` (l.184) : même raisonnement, même déplacement.
2. Tu vérifies qu'**aucun autre appelant web** de ces méthodes n'existe en
   dehors de `WalletController` — un `grep` sur `walletUseCase.` dans tous les
   `adapter/in/web` du dépôt. S'il en existe, tu les annotes aussi et tu les
   listes dans le rapport.
3. Tu vérifies que `InvoiceService.java:192` n'est pas dans le même cas. Si
   c'est un service applicatif appelé aussi par un flux interne, tu le signales
   **sans le corriger** — hors périmètre.

Test obligatoire : un test qui prouve que `WalletService.splitMissionRevenue`
est appelable **sans** `payment:process` dans le contexte, et un test qui prouve
que l'endpoint `WalletController` correspondant renvoie bien 403 sans la
permission. Le second est le filet qui garantit que tu n'as pas ouvert une porte.

## TÂCHE 3 — `deliveryPersonId ≠ walletOwnerId` : RÉSOUDRE L'ACTEUR

Ton diagnostic est juste et je le reprends : la livraison porte
`freelancer_id = <profile id>` issu de `tnt_actor.freelancer_profiles.id`, alors
que le wallet porte `owner_id = <kernel actorId>`. Deux identifiants pour la même
personne, et le `DebitWalletCommand` reçoit le mauvais.

Ta propre proposition est la bonne : dans `processDeliveryPayment`, résoudre
l'`actorId` depuis le `freelancerId` via `tnt_delivery_persons.actor_id`, et
utiliser cet `actorId` dans `DebitWalletCommand` **et** dans
`SplitMissionRevenueCommand.freelancerOrgId`.

Contraintes :

- Passe par un **port**, pas par une requête R2DBC posée en dur dans le service
  applicatif. Si un port de lecture des delivery persons existe déjà, tu
  l'utilises ; sinon tu en ajoutes une méthode, et tu dis laquelle.
- Si la résolution échoue (pas de ligne `tnt_delivery_persons` pour ce
  `freelancerId`), **tu remontes l'erreur**. Tu n'avales pas. C'est tout l'objet
  de ce lot.
- Tu écris un test qui prouve que le `DebitWalletCommand` construit porte bien
  l'`actorId` résolu et non le `freelancerId` d'entrée. `ArgumentCaptor`, comme
  tu l'as fait proprement au lot C-7.

## TÂCHE 4 — C5 DOIT ÊTRE PROUVÉ PAR L'ARGENT, PAS PAR LE STATUT

Tant que C5 se contente d'asserter `status=delivered`, il restera aveugle à un
paiement manqué. Tu l'étends dans
`/home/jtk/projets/tiibntick-core/scripts/e2e/e2e-freelancer-courier.sh` :

1. **Avant** C5, tu relèves le solde du wallet du freelancer en base et tu le
   mémorises.
2. **Après** C5, tu asserts que le solde a changé de la valeur attendue compte
   tenu du tarif de la mission et de la commission de 5 %. Tu donnes la requête
   SQL et les deux valeurs dans le rapport.
3. Tu asserts qu'une ligne de transaction de portefeuille a été créée pour cette
   livraison (`reference` = id de livraison). Tu donnes la table et la requête.
4. **Nouvelle assertion transversale, en fin de run** : tu grep les logs du
   conteneur `tnt-core` depuis le début du run à la recherche de
   `best-effort|side-effect failed|Access denied`. Si une occurrence apparaît, le
   run le signale en rouge, même si toutes les étapes sont vertes. Une erreur
   avalée pendant un run vert doit être visible dans le bilan. C'est la leçon du
   lot C-8, tu l'inscris dans l'outil.

`C6` doit alors afficher un solde **non nul**. Si après tes correctifs il reste à
`0 XAF`, c'est que le paiement ne passe toujours pas, et dans ce cas C5 est rouge
et tu me le dis franchement plutôt que de chercher un contournement.

## TÂCHE 5 — DEUX ANOMALIES MINEURES RELEVÉES DANS TON RAPPORT

1. `hasResponse(tenantId, announcementId, freelancerId)` reçoit un `tenantId`
   que `DeliveryAnnouncementRepositoryAdapter:81` **ignore** :
   `existsByAnnouncementIdAndDeliveryPersonId` ne filtre pas par tenant. La
   signature promet un cloisonnement qu'elle ne rend pas. Le risque pratique est
   faible (l'`announcementId` est une PK), mais une signature qui ment est une
   dette de sécurité. Soit tu filtres réellement par `tenant_id`, soit tu retires
   le paramètre. Tu choisis, tu justifies.
2. Tu rapportes `wc -l = 1284` pour le harnais ; la valeur réelle sur ton commit
   `2fe4f34a` est **1288**. Petit écart, mais un chiffre de gate qui ne
   correspond pas est un chiffre inutile. Relève-le avec la commande, pas de
   mémoire.

## TÂCHE 6 — LES 11 MAPPERS : NE LES TOUCHE PAS

Ton recensement est excellent et je le retiens tel quel : 11 mappers avec
`TntPersistableEntity` sur un chemin de mise à jour actif, même classe de bug
que le lot C-7 ; 3 avec `Persistable<UUID>` + `existsById` manuel donc sains ;
1 sur création seule. C'est exactement le livrable demandé.

**Tu ne corriges toujours rien.** C'est hors périmètre Freelancer et la décision
m'appartient. Ne rouvre pas le sujet dans ce lot.

## TÂCHE 7 — RUNS

Trois tableaux, dans cet ordre, tous bruts :

1. Après la **tâche 1 seule** (garde rétablie, causes non corrigées) → C5 rouge,
   avec le message d'erreur remonté au client HTTP.
2. Après les tâches 2 + 3 + 4, `preview=true` → C0-C9 verts **et** solde non nul
   **et** aucune occurrence de `best-effort` dans les logs.
3. Le même, relancé une seconde fois, pour prouver la stabilité et
   l'idempotence des seeds.

## SECTION 7 — FORMAT DU RAPPORT FINAL

Une seule prise de parole, à la fin :

1. SHA de **chaque** commit du lot, avec son message (`git log --oneline`). Au
   lot C-8 tu as écrit `git log --oneline -3` en affichant quatre lignes ; donne
   la vraie commande et sa vraie sortie.
2. Le diff du rétablissement de la garde (tâche 1), en premier.
3. Les diffs des tâches 2, 3, 5.
4. `curl -s http://localhost:8080/actuator/info | jq '.git'` après rebuild.
5. Rapports surefire intégraux des tests nouveaux et de ceux que tu as touchés.
6. Les requêtes SQL de solde et de transaction, avec valeurs avant/après.
7. Les trois tableaux de run.
8. Ce qui reste rouge, avec preuve — et une proposition **que nous exécutons
   nous-mêmes**. Pas de renvoi vers une autre équipe, et surtout : **pas de
   `onErrorResume` pour faire disparaître le problème.** Si quelque chose ne
   passe pas, je préfère un rouge honnête à un vert acheté.
