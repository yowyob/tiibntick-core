# Lot C-12 — Fermer `cancel`, fermer `init-otp`, et arrêter d'écrire sur un GET

## Règle d'autonomie (à lire avant tout)

Tu travailles en autonomie totale du début à la fin. Tu ne demandes **jamais**
« veux-tu que je continue ? », « do you want me to proceed ? », « dois-je
appliquer ? ». Tu ne t'arrêtes pas pour faire valider une étape intermédiaire.
Tu prends les décisions techniques toi-même, tu les documentes, et tu ne
reprends la parole **qu'une seule fois, à la fin**, avec le rapport complet.
Si un choix est ambigu, tu tranches, tu appliques, et tu expliques ton
arbitrage dans le rapport.

**Dépôt cible : `/home/jtk/projets/tiibntick-core`** (branche
`lotC10-paiement-reel-2`, HEAD attendu `416d61b1`).

---

## Règles de preuve (les 6 règles, rappel + une nouvelle)

1. **Une affirmation sans mesure est une hypothèse.** Tu ne dis pas « probable »
   dans un rapport : tu mesures, ou tu écris la ligne dans « non mesuré ».
   Au lot C-11 tu as attribué la 500 du BFF à une « collision de cycle de vie
   Fastify 5 » avec un correctif `await sendError(...)`. La vraie cause, mesurée
   côté BFF, était le nom du champ (`balance` au lieu de `availableBalance`) et
   un tableau Java passé à `localeCompare`. Une hypothèse habillée d'un numéro
   de version reste une hypothèse.
2. **Un vert doit être exécuté, pas prédit.** Aucun tableau de run dont l'en-tête
   est « résultat attendu ».
3. **Le runtime doit porter le code annoncé.** `actuator/info` avant toute
   assertion de run.
4. **Un test qui ne s'exécute pas n'est pas un test.** Tes deux nouvelles
   assertions (C5 INVARIANT, C6 balance BFF==DB) n'ont tourné dans **aucun** de
   tes deux runs du lot C-11 : C5 était bloqué, C6 rouge. Elles ont été
   exécutées par un autre run, pas par le tien.
5. **Une assertion sur un delta n'est pas une assertion sur un état.**
6. **NOUVEAU — un total partiel est un faux total.** Tu as écrit « Bootstrap :
   2 ArchUnit pre-existing failures ». J'ai compté dans tes propres rapports
   surefire (02:41–02:43) : `LayeringArchitectureTest` 2 échecs,
   `PublicPathsSecurityTest` **10 erreurs**, `TiiBnTickApplicationTest` 1 erreur,
   `KernelBridgeConfigResilienceTest` 1 échec, `SpringdocProdExposureTest`
   1 échec — **15 rouges, pas 2**. Cause des 10+1 :
   `RedisConnectionException: Connection refused localhost:6379` depuis le
   conteneur maven. Ce n'est pas ta faute, mais ne pas le dire l'est. Désormais
   tu donnes le total **complet** par module, avec la cause de chaque rouge.

---

## Tâche 1 — `PATCH /api/v1/deliveries/{id}/cancel` : n'importe qui peut annuler

Tu l'as toi-même qualifié « défaut critique — correction à planifier » au
lot C-11. On ne planifie pas : on ferme. Aujourd'hui, tout porteur d'un JWT
valide, même sans aucun lien avec la livraison, annule la course de n'importe
qui. La perte n'est pas une permission, c'est la course du client.

1.1 — Ajoute `@CurrentUser(required = false) TntSecurityContext ctx` et refuse
explicitement le contexte nul (403), exactement comme tu l'as fait pour
`updateStatus` — ce traitement-là était juste, reproduis-le.

1.2 — Décide **qui** a le droit d'annuler et écris ta décision dans le javadoc :
le livreur assigné, l'expéditeur, ou les deux. Ce n'est pas la même règle que
`updateStatus` : l'expéditeur doit pouvoir annuler sa propre course alors qu'il
n'est pas le livreur. Si l'identité de l'expéditeur n'est pas résoluble depuis
la livraison, dis-le et restreins au livreur assigné en documentant le manque.

1.3 — Deux tests unitaires minimum : ayant droit → 200 ; tiers authentifié
→ 403.

## Tâche 2 — `POST /{id}/init-otp` : déni de livraison

Tout JWT authentifié peut ré-initialiser les OTP d'une livraison quelconque et
donc **invalider le code en cours** d'un livreur en pleine course. Même
traitement : contrôle de propriété, 403 pour un tiers, un test par branche.

## Tâche 3 — L'écriture d'ajustement du grand livre est chronologiquement fausse

Le total est juste, je l'ai vérifié : `balance 11400.00 == somme des écritures
11400.00`, dérive `0.00`, la requête de dérive ne renvoie plus rien. C'est
réparé économiquement. Mais voici les 4 lignes, dans l'ordre de `created_at` :

```
CREDIT 2850.00  balance_after 5700.00   MISSION-26ee0341…   2026-09-28 23:57:49
CREDIT 2850.00  balance_after 8550.00   MISSION-0a2ccefe…   2026-09-29 00:08:09
CREDIT 2850.00  balance_after 2850.00   LEDGER-ADJUSTMENT…  2026-09-29 01:02:20
CREDIT 2850.00  balance_after 11400.00  MISSION-e7832009…   2026-09-29 05:50:31
```

Ton écriture d'ajustement porte `balance_after = 2850.00` alors qu'elle est
datée **après** deux lignes qui affichent déjà 5700 et 8550. `balance_after`
n'est plus monotone : un auditeur qui lit la dernière ligne par date entre
01:02 et 05:50 lit 2850 comme solde courant, alors que le portefeuille en
portait 8550. Le montant ne ment pas ; la chronologie, si.

3.1 — Tranche entre les deux seules issues acceptables, et applique :
  (a) redater `created_at` de l'écriture d'ajustement **avant** le premier
      crédit réel (elle représente précisément ce crédit manquant), ce qui rend
      la colonne monotone ; ou
  (b) garder la date réelle de l'ajustement et documenter, dans
      `scripts/sql/wallet-ledger-reconciliation.sql` **et** dans un commentaire
      de colonne SQL (`COMMENT ON COLUMN`), que `balance_after` n'est pas
      autoritaire sur les lignes de type ajustement.
Je préfère (a). Si tu choisis (b), justifie.

3.2 — Ajoute à `wallet-ledger-reconciliation.sql` une **seconde** requête :
la détection de non-monotonie de `balance_after` (une ligne dont le
`balance_after` ne vaut pas le `balance_after` précédent ± le montant signé).
Aujourd'hui rien ne la détecte.

## Tâche 4 — `findWallet` est du code mort et le GET écrit toujours

État mesuré : `findWallet` existe dans `IWalletUseCase` et `WalletService`, et
n'est référencé **par rien** sauf un javadoc. Le commit `6ec7434a` a reverté
son câblage au motif que « le BFF ne gère pas 404 ». Côté BFF, `getWallet`
ne traite que le 401 puis part sur `httpError` : ton motif est exact
**aujourd'hui**. Sauf que chacun des deux côtés a renvoyé à l'autre un
correctif d'une ligne, et le résultat est qu'un verbe `GET` continue d'écrire
en base. Le portefeuille fantôme `ad2627fb-50e7-4aae-acf6-f876596e6913`
(solde 0, 0 transaction, créé le 25/09 par un GET) est toujours là.

4.1 — Le BFF traitera le 404 dans son propre lot, en parallèle. Toi, tu câbles
`findWallet` dans `WalletController.getBalance` et tu renvoies **404** quand le
portefeuille n'existe pas. Tu ne reverts pas cette fois.

4.2 — Un test qui prouve les deux branches : portefeuille existant → 200 avec
le solde ; portefeuille inexistant → 404 **et** aucune ligne créée dans
`billing.wallet_wallets` (compte avant/après dans le test).

4.3 — Décide du sort de `ad2627fb-…` : suppression ou conservation documentée.
Un portefeuille fantôme à zéro n'est pas neutre — il masque la différence entre
« pas de portefeuille » et « portefeuille vide ».

## Tâche 5 — Redis dans le conteneur de build

Les 10 erreurs de `PublicPathsSecurityTest` et l'erreur de
`TiiBnTickApplicationTest` viennent de `Connection refused localhost:6379` :
ton conteneur maven ne voit pas Redis. Conséquence : **toute classe de test qui
charge le contexte Spring n'est pas testée**, elle est en erreur. Ce n'est pas
un vert, ce n'est pas un rouge légitime, c'est un angle mort.

5.1 — Fais tourner le build sur le réseau docker qui porte Redis
(`docker run --network <réseau de tnt-redis> …`) ou pointe les tests sur le
service Redis existant. Rapporte le total surefire de `tnt-bootstrap`
**après**, classe par classe.

5.2 — Ce qui reste rouge une fois Redis joignable : une ligne par échec, avec
la cause et le verdict (pré-existant prouvé par un run sur le commit antérieur,
ou introduit par ce lot).

5.3 — La phrase « freeze ArchUnit mis à jour » de ton rapport C-11 est sans
fondement : `tnt-bootstrap/archunit_store` n'a pas été modifié depuis le
23 juillet et l'arbre de travail est propre. Soit tu mets réellement le freeze
à jour et tu le committes, soit tu ne le dis pas. Et si tu le mets à jour, tu
listes explicitement les violations gelées : un freeze est une dette
consentie, pas un silence.

## Tâche 6 — Un run réel qui exécute tes propres assertions

6.1 — `TNT_GOFP_DELIVERY_OTP_PREVIEW=true`, rebuild, redémarrage,
`actuator/info` collé dans le rapport.

6.2 — **Deux** runs complets enchaînés. Chaque run doit afficher, ligne à
ligne, `C5 INVARIANT grand livre : …` et `C6 balance BFF(…) == DB(…)`. Si l'une
des deux n'apparaît pas dans la sortie, le lot n'est pas livré — c'est la
règle 4.

6.3 — Après les runs, recolle la requête de dérive **et** la nouvelle requête
de non-monotonie. Les deux doivent être vides.

---

## Rapport attendu

1. Commits (SHA, sujet, fichiers).
2. Décision d'autorisation sur `cancel` (qui, pourquoi) et sur `init-otp`.
3. Tableau d'accessibilité complet de `GofpDeliveryController` — **tous** les
   endpoints, une ligne chacun, mécanisme réel et verdict. Pas de mécanisme
   surqualifié : « OTP » n'est pas « propriété », « JWT requis » n'est pas
   « sécurisé ».
4. Ajustement du grand livre : choix (a) ou (b), les 4 lignes après correction.
5. `actuator/info` + les deux runs, verbatim des lignes C5 INVARIANT et C6.
6. Totaux surefire **complets** par module, Redis joignable, cause de chaque
   rouge restant.
7. Sort du portefeuille fantôme.
8. **Non mesuré** — tout ce que tu n'as pas pu prouver. Cette section a été
   honnête au lot C-11 (tu y as écrit que C5 INVARIANT n'avait pas tourné).
   C'est la partie la plus utile de ton rapport. Garde-la.
