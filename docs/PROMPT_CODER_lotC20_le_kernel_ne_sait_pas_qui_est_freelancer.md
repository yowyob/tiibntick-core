# Lot C-20 — Le Kernel ne sait pas qui est freelancer (et ne l'a jamais su)

## 0. Règle de conduite — AUTONOMIE TOTALE (à lire avant tout)

Tu procèdes **de bout en bout sans jamais demander de confirmation**. Sont interdits nommément :
« do you want to proceed ? », « should I continue ? », « veux-tu que je continue ? », et toute
pause de validation intermédiaire. Tu tranches seul tous les arbitrages techniques, tu les
consignes dans une section « Décisions prises sans arbitrage », et **tu ne rends la parole qu'une
seule fois, à la fin, avec ton rapport**. Seul un blocage matériel infranchissable justifie de
t'arrêter avant la fin — et dans ce cas tu écris ce que tu as tenté, pas une question.

## 1. Dépôt cible

**`/home/jtk/projets/tiibntick-core`**, branche `feat/lotC16-role-freelancer`, qui part de
`24e298f7`. Ouvre VS Code sur ce dossier.

## 2. C-19 est accepté — et il a eu raison contre moi deux fois

Tout est vérifié : les 7 commits, le prédicat de chemin du filtre, la suppression de `/register`,
le wallet `34728a32…` créé le 2026-10-02 à 02:08:37 avec un solde de 2850, l'ancien wallet intact
à 22800, et l'invariant du grand livre (9 transactions, somme 25650). Les preuves de `docs/e2e/c19/`
sont bien toutes suivies par git.

Deux points où tu avais raison et moi tort, actés :

- **Mon Fait 3 était partiellement faux.** `setRemainingDeliveries` existe bien
  (`GofpFreelancerService:141`) ; mon `grep` sur `remainingDeliveries` en minuscule ne pouvait pas
  le voir. Ta correction est juste. La conclusion pratique tient (aucun appelant de
  `record-delivery-success`, quota jamais consommé), mais l'affirmation était mal fondée.
- **Mon Fait 2 est réfuté**, et tu l'as réfuté proprement : 4 redémarrages, 12 requêtes à froid,
  rien au-delà de 1,2 s, avant comme après. Tu as aussi eu la rigueur de ne pas vendre les écarts
  de p95 et de rafales concurrentes comme un effet. C'est la bonne manière de rendre un chiffre.

Et surtout : **ton run sur compte vierge a cassé une conclusion de ton propre rapport C-18.**
Tu écrivais « à la lecture du code, `getOrCreateWallet` devrait créer le wallet ». Le run a répondu
HTTP 500. Sans C-19.2, aucun freelancer réellement neuf n'aurait jamais pu être payé en production.
Garde ce réflexe : annoncer une lecture de code comme non prouvée, puis aller la prouver.

## 3. Ce que la vérification a trouvé et que ton grep n'a pas vu

Tu as écrit « aucune erreur avalée dans les logs ». Ton motif était
`best-effort|side-effect failed|Access denied`. Avec un motif plus large :

```
$ psql -c "SELECT operation, status, count(*) FROM tnt_role_sync_outbox GROUP BY 1,2"
 PROVISION_ROLE | DEAD        | 10
 ASSIGN_ROLE    | DEAD        |  4
 UPDATE_ROLE    | PROVISIONED |  2
 ASSIGN_ROLE    | RETRYING    |  1
```

La chaîne causale :

1. **2026-08-05** — les **dix** rôles TiiBnTick (`FREELANCER`, `CLIENT`, `TNT_ADMIN`,
   `PERMANENT_DELIVERER`, `ORG_ADMIN`, `AGENCY_MANAGER`, `BRANCH_MANAGER`, `SUPPORT_AGENT`,
   `RELAY_OPERATOR`, `AGENCY_HUB_OPERATOR`) échouent à être provisionnés dans le Kernel :
   `403 Forbidden from POST https://kernel-core.yowyob.com/kernel-api/api/roles`. Dix tentatives,
   puis `DEAD`.
2. **Depuis C-16**, chaque `ASSIGN_ROLE` de `FREELANCER` meurt en cascade :
   `Role 'FREELANCER' is not provisioned in the Kernel's system tenant yet`. Une entrée morte par
   run, dont une du 2026-10-02 et une encore en `RETRYING`.

**Le Kernel ne sait pas, et n'a jamais su, qu'un seul de nos utilisateurs est freelancer.** Tout est
vert en local parce que `tnt_roles` fait autorité localement — et ton correctif du resolver en C-18
est précisément ce qui a rendu ce chemin local fonctionnel. Nous avons donc rendu vert le chemin
local par-dessus une propagation morte depuis deux mois.

C'est la famille du lot C-8 : run vert, erreur avalée, effet de bord qui n'a jamais eu lieu. À une
différence près : celle-ci est **antérieure à tout notre travail** et aucun harnais ne pouvait la
voir, puisque aucun harnais n'interroge le Kernel sur les rôles.

## 4. Périmètre

### C-20.1 — Trancher la question d'autorité (c'est le cœur du lot, et ça décide de l'APK)

**La question** : en production, quelle autorité est consultée pour savoir qu'un utilisateur est
freelancer — le `tnt_roles` du core TiiBnTick, ou les rôles du Kernel ?

Tu dois y répondre **par la mesure, pas par la lecture seule**. Pistes :

- quel `ReactivePermissionResolver` est câblé selon le profil (`DEV` vs `PROD`) — y a-t-il une
  implémentation Kernel qui remplace `LocalReactivePermissionResolver` en production ?
- le core **de production** (`https://tiibntick-core.yowyob.com`) accorde-t-il
  `announcement:respond` à un utilisateur dont le rôle n'existe que dans le Kernel, ou que
  localement ? Tu as le droit d'interroger le core de prod en **lecture** et avec un compte de test.
  **Tu n'écris rien en production.**
- le BFF ou le mobile lisent-ils des rôles depuis le Kernel plutôt que depuis le core ?

Trois issues possibles, et tu dis laquelle est la vraie, avec la preuve :

- **(a) L'autorité est locale** → le Kernel mort n'est qu'une dette de cohérence, pas un blocage
  APK. Tu le documentes comme telle, et C-20.2 devient le seul vrai travail.
- **(b) L'autorité est le Kernel** → le module Freelancer **ne peut pas fonctionner en production**,
  quoi qu'on pousse. C'est un blocage externe de la même nature que F-R4, et tu rédiges la demande
  précise à adresser à l'équipe Kernel : quel droit, sur quel endpoint, pour quel compte de service,
  et quels dix rôles à créer. **Tu n'inventes aucune clé, tu n'en cherches aucune dans un
  `docker-compose.override.yml` ni ailleurs** — c'est exactement l'erreur du lot C-4.
- **(c) Les deux selon le chemin** → tu cartographies quel chemin lit quoi. C'est le pire cas et
  c'est celui qu'il faut documenter le plus précisément.

### C-20.2 — Rendre une entrée morte impossible à ignorer

Quatorze entrées `DEAD` dormaient dans `tnt_role_sync_outbox` sans que rien ne le signale. Le worker
log un `ERROR` au moment du décès, puis plus jamais rien : au redémarrage suivant, le silence est
total et la table n'est consultée par personne.

Tu ajoutes, en choisissant seul la forme :

- une **métrique** du nombre d'entrées `DEAD` et `RETRYING`, exposée en continu (pas un compteur
  d'événements, une jauge lisible à tout instant) ;
- un **health indicator** qui dégrade l'état de l'application quand il existe au moins une entrée
  `DEAD` — parce qu'une entrée morte veut dire qu'un effet de bord annoncé n'a pas eu lieu ;
- un **test** qui prouve les deux à partir d'une entrée `DEAD` insérée dans un test, pas à la main
  en base.

Tu ne purges pas les 14 entrées existantes : elles sont la preuve du problème, et leur date est
l'information la plus utile du lot.

### C-20.3 — Refermer le scénario C-8 que ton run 1 a laissé ouvert

Tu as écrit ne pas savoir si la livraison du run 1 était passée en `DELIVERED` **sans être payée**,
le nettoyage du harnais ayant effacé les lignes avant que tu les lises. C'est exactement le motif du
lot C-8 : statut avancé, paiement échoué, personne averti.

Reproduis-le volontairement, dans un test, pas en base : une livraison passe en `DELIVERED`, le
paiement échoue (wallet absent, permission refusée, peu importe le moyen). Puis réponds :

- le statut reste-t-il `DELIVERED` ? Si oui, on a une livraison livrée et impayée, silencieuse.
- quelque chose compense-t-il, ou alerte-t-il ?
- quel est le comportement **voulu** — rollback du statut, ou statut conservé plus une alerte et une
  reprise ? Tu proposes, tu argumentes, et tu implémentes ta proposition **seulement si elle ne
  change pas la sémantique métier de la livraison**. Si elle la change, tu t'arrêtes à la
  proposition écrite.

### C-20.4 — Le trou `wallet:read` que tu as toi-même signalé

`getOrCreateWallet` exige `wallet:read`, `splitMissionRevenue` ne l'exigeait pas. `FREELANCER` l'a
(vérifié en base). Mais si un autre acteur qu'un livreur fait passer une livraison en `DELIVERED`,
le paiement serait refusé. Établis quels acteurs peuvent déclencher cette transition, et couvre le
cas par un test. Si un acteur légitime n'a pas la permission, c'est un bug à corriger, pas une
permission à ajouter au petit bonheur.

### C-20.5 — Ce que tu ne décides pas

- **Le barème du quota.** Ton instruction du point 6 de C-19 est bonne et suffit : trois points de
  branchement, les deux copies, les barèmes concurrents. C'est au fondateur de trancher. Pas de code.
- **Les droits Kernel.** Tu rédiges la demande, tu ne contournes rien.
- **Le statut KYC `APPROVED` automatique**, et **`archunit_store`** : inchangés.
- **Les orphelins de `/register`** (`KycVerificationPort`, `publishFreelancerCreated`,
  `FreelancerCreatedEvent`) : laisse-les, c'est du nettoyage à faire quand la question KYC sera
  tranchée.

## 5. Ce que je vérifierai, donc ne le survends pas

- `SELECT operation, status, count(*) FROM tnt_role_sync_outbox GROUP BY 1,2` — l'état réel.
- `docker logs tnt-core | grep -iE "Kernel bridge error|exhausted|DEAD|best-effort|side-effect failed|Access denied"` —
  **note le motif élargi**. Un `grep` trop étroit est une manière de ne pas voir.
- `curl localhost:8080/actuator/info` et `/actuator/health` — commit, `dirty`, et le nouvel
  indicateur de C-20.2.
- l'état SQL des wallets et du grand livre (9 transactions, somme 25650 à l'instant où j'écris).
- l'existence réelle et le suivi git des fichiers de preuve que tu cites.
- un `grep -i` cette fois, pour ne pas refaire mon erreur de casse.

## 6. Rapport attendu

1. **Verdict** par sous-lot.
2. **La réponse à la question d'autorité**, avec la preuve : (a), (b) ou (c), et ce qui l'établit.
   C'est le livrable principal. Si c'est (b), la demande à l'équipe Kernel, rédigée.
3. **Ce que ce lot ne prouve pas** — garde cette section, c'est ce qui rend tes rapports utiles.
4. **Décisions prises sans arbitrage.**
5. Commits (non poussés), fichiers modifiés avec `+/-`, et le détail des tests.

## 7. Note de séquencement pour ulrich (pas pour toi)

7 commits non poussés (C-15 → C-19) contre `github/main`, qui est au lot C-14. Le push devrait
précéder C-20 — mais si C-20.1 répond **(b)**, pousser ne suffira pas à débloquer l'APK, et la
demande à l'équipe Kernel devient le chemin critique du projet.
