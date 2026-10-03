# Lot C-13 — Un run propre, la branche expéditeur prouvée, et les orphelins tranchés

## Règle d'autonomie (à lire avant tout)

Tu travailles en autonomie totale du début à la fin. Tu ne demandes **jamais**
« veux-tu que je continue ? », « do you want me to proceed ? ». Tu ne t'arrêtes
pas pour faire valider une étape intermédiaire. Tu tranches, tu appliques, tu
documentes, et tu ne reprends la parole **qu'une seule fois, à la fin**, avec le
rapport complet.

**Dépôt cible : `/home/jtk/projets/tiibntick-core`** (branche
`lotC10-paiement-reel-2`, HEAD attendu `b260aa8f`).

---

## D'abord : le lot C-12 est le meilleur que tu aies livré

J'ai tout remesuré. Le grand livre est monotone et juste :
`2850 → 5700 → 8550 → 11400 → 14250 → 17100`, solde `17100.00` = somme des
écritures, l'ajustement rétrodaté au 28/09 22:00 avant le premier crédit réel.
`findWallet` est câblé, en lecture pure, `switchIfEmpty → 404`. Le portefeuille
fantôme est supprimé — il ne reste qu'un portefeuille en base. Les deux gardes
de propriété sont réelles, avec le refus explicite du contexte nul. Et tu as
fermé `cancel` en deux branches (livreur **ou** expéditeur via
`announcement.clientId`), ce qui est la bonne décision métier et pas la plus
simple.

Un mot sur le lot BFF : modifier un test pour qu'il passe est l'acte le plus
suspect qui soit. J'ai donc vérifié la justification en base, pas sur parole :
`uq_announcement_delivery_person` existe bien comme index unique sur
`announcement_subscriptions (announcement_id, delivery_person_id)`.
L'arbitrage tient. C'était la bonne décision, correctement argumentée.

---

## Tâche 1 — Ton run 2 n'est pas un run propre, et le harnais te ment

Tu as écrit : « Run 2 — C0-C9 tous verts (probe-c=200 run contaminé — attendu
pour run enchaîné) ». Voici ce que dit ton propre harnais, lignes 450 à 463 :

```
# En run propre (cleanup précédent complet), probe-c=403 EST la valeur attendue
warn "C0b : probe-c=200 — RBAC résiduel détecté (état contaminé par run précédent)"
warn "  Pour un run propre, relancer après cleanup complet. probe-c DOIT être 403."
```

« probe-c DOIT être 403 » et « attendu pour run enchaîné » ne sont pas la même
phrase. Tu as requalifié en normal ce que le harnais appelle une contamination.

Mais le vrai coupable est le harnais, pas toi : ligne 471, il écrit
`CR[C0b]="✅ PASS"` **quoi qu'il arrive**. Un détecteur qui avertit puis passe
au vert n'est pas un détecteur.

1.1 — `probe-c=200` doit produire `fail`, pas `warn`+`PASS`. Si un run enchaîné
ne peut structurellement pas repartir propre, alors le harnais doit **remettre
l'état à zéro lui-même** avant C0b (dé-seed du RBAC résiduel), de sorte qu'un
run enchaîné soit propre par construction. Choisis l'une des deux voies et
applique-la ; ne laisse pas un troisième état « contaminé mais vert ».

1.2 — Cherche dans le harnais les **autres** endroits où un `warn` est suivi
d'un `PASS`. Liste-les. Pour chacun : soit c'est une information et le mot
`warn` est juste, soit c'est un défaut et il doit devenir `fail`. Un seul
tableau, une décision par ligne.

## Tâche 2 — La branche expéditeur de `cancel` n'a jamais touché la base

Tu l'as dit toi-même en §8, et c'est la bonne franchise. Mais c'est la branche
qui compte commercialement : un client qui annule sa propre commande. Un mock
sur `assertCallerCanCancelDelivery` ne prouve rien du chemin
`announcementRepository.findById → getClientId() → comparaison avec l'actorId
appelant`.

2.1 — Ajoute au harnais un cas C10 : un **second** acteur, l'expéditeur de
l'annonce, obtient un jeton réel et annule la livraison. Attendu : 200.

2.2 — Et le cas négatif, qui est le seul qui prouve la garde : un **troisième**
acteur, ni livreur ni expéditeur, appelle `cancel`. Attendu : 403. Si le
harnais ne sait pas fabriquer un troisième acteur, dis-le et explique ce qu'il
manque — mais essaie d'abord.

2.3 — Le cas `announcementId` nul (livraison issue d'un `DeliveryNeed` direct) :
tu as décidé de restreindre au livreur seul. Un test unitaire au minimum.

## Tâche 3 — `POST /{id}/init-otp` n'a aucun appelant

J'ai cherché dans le core, dans les scripts et dans le BFF : **rien** n'appelle
cet endpoint. Tu viens de le restreindre au livreur assigné, ce qui ne coûte
rien puisque personne ne l'appelle — mais cela veut dire que soit les OTP sont
initialisés ailleurs (à la création), soit ce chemin est mort.

3.1 — Trouve **où** les OTP sont réellement initialisés dans le parcours qui
fonctionne aujourd'hui (le harnais capture bien un `pickupOtp`, donc quelque
chose les crée). Écris le chemin exact, fichier et ligne.

3.2 — Tranche : si l'endpoint est redondant, supprime-le et dis-le ; s'il a un
appelant prévu (mobile, expéditeur), alors ta garde « livreur uniquement »
verrouille peut-être le seul appelant légitime — corrige la règle en
conséquence. Un endpoint sans appelant et avec une garde est une fausse
sécurité : il rassure sans rien protéger.

## Tâche 4 — `onErrorReturn(false)` avale la mise au point du lot C-10

Dans `assertCallerCanCancelDelivery`, les deux vérifications se terminent par
`.onErrorReturn(false)`. C'est fail-closed, donc pas un trou de sécurité — et
c'est le bon choix par défaut. Mais `resolveActorIdForDeliveryPerson` a été
durci au lot C-10 précisément pour émettre deux messages distincts (livreur
introuvable / `actor_id` NULL). Ces deux messages sont maintenant avalés : le
livreur légitime dont l'`actor_id` est NULL reçoit « vous n'êtes ni le livreur
ni l'expéditeur », ce qui est faux et indébogable sur le terrain.

4.1 — Journalise l'erreur en `warn` **avant** de retourner `false`, dans les
deux branches, avec l'id de la livraison et l'id de l'appelant. Garde le
comportement fail-closed.

4.2 — Vérifie si le même motif existe dans `assertCallerIsAssignedDeliveryPerson`
et dans la garde de tracking (`requireTrackingOwnership`,
`checkTrackingOwnership`). Même traitement si oui.

## Tâche 5 — Le freeze ArchUnit ne sert à rien dans l'état

Tu as commité 317 violations gelées (294 + 23) et les deux règles
`LayeringArchitectureTest` sont **toujours rouges**, parce que les violations
fluctuent entre les runs. Nous avons donc le pire des deux mondes : une dette
cachée dans un fichier binaire **et** un build rouge.

5.1 — Tranche. Soit le freeze fonctionne — et alors les deux règles passent au
vert et tu le prouves par un run ; soit il ne peut pas fonctionner tant que le
code bouge — et alors tu **reverts** `b260aa8f`, tu laisses les deux rouges
visibles, et tu inscris la dette là où un humain la lit.

5.2 — Dans les deux cas : écris les 317 violations sous forme lisible dans
`docs/DETTE_ARCHITECTURE.md` — par règle, par module, avec le nombre. Une dette
consentie qui n'est lisible que par ArchUnit n'est pas consentie, elle est
oubliée.

## Tâche 6 — `PublicPathsSecurityTest` ne tourne toujours pas

Redis est réglé, bravo. Restent MinIO, Kafka, Elasticsearch : les 10 erreurs et
l'erreur de `TiiBnTickApplicationTest` viennent de là. Conséquence inchangée :
**la classe qui vérifie quels chemins sont publics n'est jamais exécutée**.
C'est la classe de test la plus importante du dépôt pour la sécurité, et elle
n'a pas tourné une seule fois depuis le début de ce chantier.

6.1 — Fais tourner le build avec la stack complète (le `docker-compose` de
`tnt-bootstrap` les fournit déjà : branche le conteneur maven sur le même
réseau et pointe chaque service sur son nom de conteneur, pas sur `localhost`).

6.2 — Rapporte le résultat réel de `PublicPathsSecurityTest`, test par test.
S'il est rouge pour une vraie raison, c'est la découverte la plus utile de ce
lot.

---

## Rapport attendu

1. Commits (SHA, sujet, fichiers).
2. Tâche 1 : la voie choisie pour `probe-c`, et le tableau des `warn` suivis
   d'un `PASS` avec une décision par ligne.
3. Tâche 2 : les runs, avec les lignes C10 verbatim (cas expéditeur 200, cas
   tiers 403).
4. Tâche 3 : le chemin réel d'initialisation des OTP (fichier, ligne) et le
   sort de l'endpoint.
5. Tâche 5 : freeze vert prouvé **ou** revert, plus `docs/DETTE_ARCHITECTURE.md`.
6. Tâche 6 : `PublicPathsSecurityTest` test par test, stack complète.
7. Totaux surefire complets par module, cause de chaque rouge restant.
8. **Non mesuré.** Tes trois dernières sections « non mesuré » ont été exactes
   et c'est ce qui rend tes rapports utilisables. Continue.
