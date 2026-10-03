# Lot C-11 — Réconcilier le grand livre, fermer le contrôle de propriété

**Dépôt cible : `/home/jtk/projets/tiibntick-core`** (branche de départ : `lotC10-paiement-reel-2`, HEAD `03435817`).

## Règle d'autonomie

Tu exécutes ce lot **de bout en bout, sans jamais demander la permission de continuer**. Pas de
« souhaites-tu que je procède ? », pas de pause intermédiaire. **Une seule prise de parole : à la
fin**, avec le rapport complet. Obstacle rencontré → tu le contournes ou tu le documentes dans le
rapport final ; tu ne t'arrêtes pas.

## Règles de preuve (inchangées, plus une)

1. Aucune assertion verte ne peut reposer sur une erreur avalée.
2. Ne cite comme preuve que des sorties réelles du chemin exact que tu prétends mesurer. « Non
   mesuré » est une réponse acceptable ; un tableau prédit ne l'est pas.
3. Un grep de frontière n'est pas une preuve d'accessibilité — construis le graphe depuis chaque
   `@*Mapping`.
4. `actuator/info` doit montrer le SHA du lot avant tout run. Build dans le conteneur :
   `docker run --rm -v /home/jtk/projets/tiibntick-core:/ws -v "$HOME/.m2":/root/.m2 -w /ws maven:3.9-eclipse-temurin-21 mvn -q -DskipTests package`
5. **NOUVEAU — une assertion sur un delta n'est pas une assertion sur un état.** Au lot C-10 tu as
   prouvé que le solde avait augmenté de 2850 XAF et tu en as conclu que le paiement était correct.
   Il l'était. Mais pendant ce temps le solde absolu était faux, et tu ne l'as pas vu parce que tu
   ne mesurais qu'une différence. Chaque fois que tu contrôles de l'argent, contrôle aussi
   l'**invariant** : `solde == somme des lignes du grand livre`.

---

## Tâche 1 — Le grand livre est faux de 2850 XAF (priorité absolue)

Mesure que j'ai faite moi-même, sur la base vivante :

```sql
SELECT ww.id, ww.owner_id, ww.balance,
       COALESCE(SUM(CASE WHEN wt.type='CREDIT' THEN wt.amount ELSE -wt.amount END),0) AS somme_tx,
       ww.balance - COALESCE(SUM(CASE WHEN wt.type='CREDIT' THEN wt.amount ELSE -wt.amount END),0) AS derive
FROM billing.wallet_wallets ww
LEFT JOIN billing.wallet_transactions wt ON wt.wallet_id = ww.id
GROUP BY ww.id, ww.owner_id, ww.balance
HAVING ww.balance <> COALESCE(SUM(CASE WHEN wt.type='CREDIT' THEN wt.amount ELSE -wt.amount END),0);
```

Résultat :

```
 db432caa-eaaf-44dc-8ad2-851d86481a64 | f69444e3-ec21-4fee-bd84-20c435c567ae | 8550.00 | 5700.00 | 2850.00
```

Un portefeuille dont le solde dépasse de 2850 XAF la somme de ses écritures. Deux lignes `CREDIT`
existent, il en faudrait trois. C'est la trace laissée par le bug que **tu viens de corriger** en
`03435817` : avant ce commit, `splitMissionRevenue` déplaçait le solde sans écrire dans
`wallet_transactions`. Ton correctif arrête l'hémorragie, il ne recolle pas ce qui a déjà coulé.

Conséquence sur ton rapport, à corriger explicitement : ton « Delta run 1 : 0 → 2850 » est faux. Le
solde valait déjà 2850 avant le run 1 (crédit non tracé d'un run antérieur au correctif). Le run 1
a fait 2850 → 5700. Ton propre §5 l'énonce sans le voir, puisqu'il donne run 2 = 5700 → 8550 : deux
affirmations arithmétiquement incompatibles dans le même rapport.

À faire :

1. **Une requête de réconciliation réutilisable**, versionnée dans
   `scripts/sql/wallet-ledger-reconciliation.sql`, qui liste tout portefeuille dont le solde diffère
   de la somme de ses écritures, avec la dérive signée.
2. **Décider et documenter le traitement des 2850 XAF orphelins.** Deux options, tu choisis et tu
   justifies : écrire une écriture d'ajustement traçable (`type=CREDIT`,
   `reference_id='LEDGER-ADJUSTMENT-C11-<date>'`, description nommant la cause), ou corriger le
   solde vers la somme des écritures. Ce qui est **interdit** : laisser la dérive sans décision.
   C'est une donnée de test, donc le risque est nul et le geste est gratuit — c'est exactement pour
   ça qu'il faut l'apprendre maintenant plutôt qu'en production.
3. **Une assertion d'invariant dans le harnais E2E**, après C5 : le harnais devient **rouge** si
   `balance <> somme(transactions)` pour le portefeuille du livreur du run. Elle doit être
   indépendante de l'assertion de delta existante, qui reste.
4. **Un test unitaire par méthode de `WalletService` qui bouge de l'argent**, vérifiant qu'un
   `saveTransaction` accompagne chaque `save(wallet)`. `creditWallet`, `debitWallet`,
   `creditCommission`, `splitMissionRevenue`, `transferSubDelivererCommission`, `refundPayment` :
   pour chacune, un mock du repository et une vérification que le nombre d'écritures persistées
   égale le nombre de mouvements de solde. Si l'une d'elles est déjà défaillante comme l'était
   `splitMissionRevenue`, tu la corriges dans le même lot.

## Tâche 2 — C6 est vert alors que le livreur lit 0 XAF

Tu l'as documenté en §8, puis classé « pré-existant, hors scope, côté BFF ». Ce classement est
refusé. Sur l'APK, le livreur termine sa course, se fait créditer 2850 XAF en base, et lit **0 XAF**
sur son écran. Le résultat vécu est identique à celui du bug du lot C-8 ; seul le côté de la
tuyauterie change. C'est le dernier mètre du geste pour lequel ce module existe.

Et C6 est vert parce que l'assertion ne teste que la **présence** du champ `withdraw_available`,
jamais sa valeur.

Dans **ce** dépôt :

1. Rendre l'assertion C6 du harnais **rouge** quand le solde renvoyé par le BFF diffère du solde en
   base pour le même livreur. Le message d'échec doit afficher les deux valeurs et les deux
   identifiants utilisés (celui que le BFF a transmis, celui qui porte le portefeuille).
2. Vérifier et rapporter ce que `GET /billing/wallet/{userId}/balance` fait lorsqu'aucun
   portefeuille n'existe pour l'identifiant reçu. Si `getOrCreateWallet` en **crée** un à zéro sur
   un simple GET de lecture, c'est un défaut en soi : un verbe de lecture ne doit pas écrire. Le
   rapporter et proposer la correction (lecture pure renvoyant 404 ou un solde absent explicite).
3. Chercher dans `billing.wallet_wallets` les portefeuilles fantômes à zéro créés par ce
   mécanisme, et les compter dans le rapport.

La correction côté BFF fait l'objet d'un prompt séparé pour `/home/jtk/projets/tiibntick-bff`.

## Tâche 3 — Requalifier la ligne « DOMAIN-SECURED » de ton tableau

Tu as classé `GofpDeliveryController.PATCH /api/v1/deliveries/{id}/status` en « DOMAIN-SECURED (pas
RBAC, mais OTP non contournable) ». J'ai lu le contrôleur : la méthode prend un `@PathVariable UUID
id` et un `@RequestBody`, et **rien d'autre**. Pas de `@RequirePermission`, pas de `@PreAuthorize`,
pas de `@CurrentUser`, aucun contrôle de propriété.

L'OTP BCrypt est une vraie barrière, et je ne le nie pas. Mais il prouve que l'appelant **connaît le
code**, pas qu'il est **le livreur assigné à cette course**. Le destinataire, qui reçoit le code par
SMS, peut clore la livraison à la place du livreur. Dans un audit d'accessibilité, écrire
« DOMAIN-SECURED » pour cette ligne est une surévaluation.

À faire :

1. Requalifier la ligne en « OTP seul — aucun contrôle de propriété » dans le tableau corrigé.
2. Ajouter dans `updateStatus` la vérification que l'acteur du JWT est bien le livreur assigné à la
   course (via le `@CurrentUser` déjà importé dans ce contrôleur et utilisé ailleurs, lignes 127 et
   152), et renvoyer 403 sinon. Attention : cette vérification doit comparer le bon identifiant —
   l'acteur du JWT est un `actorId`, le champ de la course est un profil. Réutilise
   `resolveActorIdForDeliveryPerson`.
3. Un test par cas : livreur assigné → 200 ; autre acteur authentifié avec un OTP valide → 403.
4. Faire le même examen pour `POST /{id}/init-otp` et `PATCH /{id}/cancel`, et rapporter leur
   verdict dans le tableau.

## Tâche 4 — Le 500 TOKEN_INVALID, mesuré et non plus « non investigué »

C6 est tombé en HTTP 500 `TOKEN_INVALID` aux runs 2 et 3. Deux runs sur trois sur la même
assertion : « cause non investiguée » n'est pas une conclusion acceptable, et un 500 sur un jeton
invalide est de toute façon un défaut de contrat (ce serait un 401).

Rapporter : la ligne de log côté BFF **et** côté core au moment exact de l'échec, la valeur
`exp`/`iat` du jeton utilisé, l'écart avec l'heure du conteneur, et le verdict — jeton réellement
expiré, ou rejet erroné. Si le défaut est côté BFF, il part dans l'autre prompt, mais le diagnostic
se fait ici avec les logs des deux côtés.

## Tâche 5 — Nettoyage

`TX_COUNT=$(... | tr -d '\n')` pour supprimer l'avertissement `[: 0\n0 : nombre entier attendu`.
Un avertissement toléré dans un harnais est un avertissement qu'on n'entend plus le jour où il
compte.

## Format du rapport final

1. Commits (SHA, sujet, fichiers).
2. **Sortie brute de la requête de réconciliation, avant et après** ton traitement des 2850 XAF.
3. `actuator/info` après rebuild et redémarrage.
4. Deux runs réels du harnais avec la nouvelle assertion d'invariant et le nouveau C6 : un run
   nominal, un run enchaîné. Bilans réels, y compris rouges.
5. Le tableau d'accessibilité **corrigé** (ligne `PATCH /status` requalifiée, `init-otp` et
   `cancel` ajoutés).
6. Diagnostic du 500 `TOKEN_INVALID` avec les logs des deux côtés.
7. Comptage des portefeuilles fantômes à zéro.
8. Totaux surefire par module.
9. Section **« non mesuré »**, sans habillage.
