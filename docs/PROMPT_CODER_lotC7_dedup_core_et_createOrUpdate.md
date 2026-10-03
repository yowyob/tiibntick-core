# Lot C-7 — Les deux défauts de production que le lot C-6 a révélés

## Règle d'autonomie (à lire avant tout)

Tu travailles en autonomie totale du début à la fin. Tu ne me poses **aucune**
question, tu ne demandes **aucune** confirmation, tu n'écris **jamais** « do you
want me to proceed », « souhaitez-vous que je continue », ni aucune variante.
Tu ne prends la parole qu'**une seule fois** : à la fin, pour rendre le rapport.
Si tu rencontres un choix technique, tu tranches toi-même, tu appliques, et tu
justifies ta décision dans le rapport. Si tu rencontres un blocage réel, tu
l'écris dans le rapport avec la preuve expérimentale — tu ne t'arrêtes pas pour
me le demander.

## Dépôts cibles

Ce lot touche **deux** dépôts :

- **`/home/jtk/projets/tiibntick-core`** (tâches 1, 2 et 4)
- **`/home/jtk/projets/tiibntick-bff`** (tâche 3)

Lis bien à chaque tâche lequel des deux est concerné.

## Branches

```bash
cd /home/jtk/projets/tiibntick-core
git checkout lotC6-bug2-afterschema
git checkout -b lotC7-dedup-et-createorupdate

cd /home/jtk/projets/tiibntick-bff
git checkout fix/created-at-numeric-sort
git checkout -b lotC7-subscribe-idempotent
```

---

## Contexte : ton lot C-6 est bon, et il a mis au jour deux bugs de production

J'ai vérifié. Le correctif est réel et il tourne : j'ai extrait
`TntEntityIsNewCallback.class` du conteneur et le bytecode contient bien
`AfterSaveCallback`, `onAfterSave` et `OutboundRow`. Donc `SETUP assign → 200` et
`C4 → 200` sont attribuables au correctif, pas à un résidu d'état. L'implémentation
est propre : tu as **ajouté** `AfterSaveCallback` sans retirer `AfterConvertCallback`,
ce qui était exactement la contrainte. Dix étapes vertes sur onze contre le vrai
kernel.

Une réserve de forme, la même qu'au lot C-5 : dans la section 1 tu présentes comme
preuve un `ERROR: duplicate key value violates unique constraint "deliveries_pkey"`
sur la clé `99999999-aaaa-bbbb-cccc-999999999999`. C'est ton propre `INSERT` de
synthèse : il prouve que la contrainte existe, pas que le 409 observé venait
d'elle. La vraie preuve, tu l'annonces en section 6 (« les logs `[DuplicateKey]`
ont confirmé »), mais tu ne la cites jamais. Cite la ligne de log, pas ton INSERT
de contrôle. Deuxième remarque : je n'ai trouvé **aucun** rapport surefire pour
`TntEntityIsNewCallbackTest` — la classe est compilée
(`tnt-bootstrap/target/test-classes/…`), mais je n'ai aucune trace indépendante
qu'elle soit passée au vert. Régénère-la.

### Le vrai sujet : deux défauts de production

Ta section 7 signale le premier, et tu as raison. Mais le second, tu l'as
présenté en section 6 comme un choix neutre de test, et il ne l'est pas.

---

## Tâche 1 — `createOrUpdate` : le bug latent (dépôt **core**)

Tu l'as trouvé par toi-même, en raisonnant sur la portée de ton propre correctif.
C'est la bonne façon de travailler et je l'inscris à ton crédit. J'ai relu le code,
ton diagnostic est exact :

`GofpFreelancerService.createOrUpdate` — branche `flatMap(existing -> …)` :
l'entité vient du `@RequestBody`, elle n'a **jamais** été lue en base, donc
`isNew` vaut `true`. On lui pose `setId(existing.getId())` puis on appelle
`save()` → `INSERT` sur une clé primaire existante → `DuplicateKeyException` →
409. `AfterSaveCallback` ne peut rien y faire, puisque l'entité n'a subi aucune
sauvegarde préalable.

Corrige les **quatre** services que tu as identifiés (`GofpFreelancerService`,
`GofpClientService`, `GofpUserService`, `GofpRelayPointService`) en appelant
`markNotNew()` sur l'entité avant le `save()` de la branche update.

Exigences :

- vérifie d'abord que les trois autres services ont **réellement** la même forme
  que `GofpFreelancerService` ; si l'un diffère, dis-le et traite-le pour ce
  qu'il est, ne le corrige pas par analogie ;
- cherche dans tout le dépôt les **autres** occurrences du même motif —
  `setId(...)` suivi d'un `save(...)` sur une entité venue de l'extérieur. Le
  motif est peut-être plus large que ces quatre services. Rapporte ce que tu
  trouves, même si tu ne le corriges pas ;
- un test par service corrigé, qui prouve qu'un second `createOrUpdate` sur le
  même `coreFreelancerId` (resp. équivalent) produit un `UPDATE` et non un 409.

## Tâche 2 — La déduplication manquante côté core (dépôt **core**)

`AnnouncementApplicationService.respondToAnnouncement` **n'a aucune clause de
déduplication** : deux appels créent deux candidatures et deux fils de
négociation, sans brûler de quota puisque `hasRemainingQuota` est une lecture
pure sans décrément. C'est écrit noir sur blanc dans le commentaire de
`realCoreFreelancer.ts` (lignes 255-264) — donc c'est connu, documenté, et jamais
corrigé.

C'est la **cause racine**. Rends `respondToAnnouncement` idempotent : un second
appel du même freelancer sur la même annonce ne doit créer ni seconde
candidature ni second fil. Retourne la candidature existante.

Tranche toi-même entre une garde applicative et une contrainte d'unicité en base
`(announcement_id, freelancer_id)`, et justifie. Indice pour ta décision : une
garde applicative seule ne protège pas de deux requêtes concurrentes ; une
contrainte en base protège mais transforme le doublon en exception qu'il faut
rattraper proprement. Rien ne t'interdit les deux.

Tests attendus : un test qui appelle deux fois et vérifie qu'il n'existe qu'une
candidature et qu'un fil ; et un test qui prouve qu'on récupère bien la
candidature existante et non une erreur.

## Tâche 3 — Retirer la béquille en mémoire du BFF (dépôt **bff**)

`src/core-adapter/real/realCoreFreelancer.ts:127` :

```ts
private readonly subscribedJobs = new Set<string>();
```

Cet ensemble est en **mémoire du processus**. Il compense côté BFF le défaut
core de la tâche 2. Trois raisons pour lesquelles ce n'est pas tenable en
production, et pourquoi ce n'était pas un « choix neutre de test » :

1. il disparaît à chaque redémarrage ou redéploiement ;
2. il n'est pas partagé entre instances — deux répliques ne voient pas les mêmes
   souscriptions ;
3. ligne 277, `if (this.subscribedJobs.size >= SUBSCRIBE_SET_MAX)
   this.subscribedJobs.clear()` vide **tout** l'ensemble quand il est plein.
   Des utilisateurs sans aucun rapport avec celui qui a fait déborder le seuil
   perdent leur trace de souscription.

Une fois la tâche 2 livrée, cette béquille n'a plus de raison d'être : retire-la
et laisse `acceptJob` appeler `subscribe` puis `assign` sans garde locale, en
s'appuyant sur l'idempotence du core. Mets à jour le commentaire des lignes
255-264 pour qu'il décrive le nouveau contrat au lieu de documenter l'ancien
défaut.

Si tu conclus qu'on ne peut pas encore la retirer, tu as le droit de le dire —
mais alors tu l'écris comme une **dette assumée avec sa raison**, pas comme une
protection.

## Tâche 4 — Preuves et run (dépôt **core**)

- Régénère le rapport surefire de `TntEntityIsNewCallbackTest` et cite sa sortie.
- Relance le parcours complet. `C0`→`C4`, `C6`→`C8` doivent rester verts : ce lot
  ne doit **rien** régresser. `C5` reste hors périmètre.
- Ajoute au script une étape qui prouve la tâche 2 côté E2E : deux `subscribe`
  consécutifs sur la même annonce, puis un `SELECT COUNT(*)` qui montre **une**
  seule candidature.

## Interdictions

- Ne réactive jamais le bypass anonyme (`TNT_AUTH_ALLOW_ANONYMOUS=false`).
- Ne fabrique jamais un jeton à la main avec un claim `roles`.
- Aucun `INSERT`/`UPDATE`/`DELETE` manuel hors du script.
- Ne touche à aucun `@PreAuthorize` dans ce lot.
- Discipline OTP inchangée : un seul appel `/v1/auth/otp/request` par run, jeton
  réutilisé pendant ses 900 s, rate-limit kernel de 600 s par destinataire.
- **Ne cite comme preuve que des sorties de commandes réelles issues du chemin
  que tu prétends mesurer.** Un `INSERT` de contrôle que tu as fabriqué n'est pas
  une preuve du comportement observé. C'est la troisième fois que je te le dis ;
  c'est le seul point sur lequel ce lot sera jugé sévèrement.

## Rapport final

Une seule prise de parole, à la fin :

1. Les quatre services `createOrUpdate` : forme réelle de chacun, correctif,
   test. Plus la liste des autres occurrences du motif trouvées dans le dépôt.
2. Tâche 2 : garde applicative, contrainte d'unicité, ou les deux — et pourquoi.
   Sortie des tests.
3. Tâche 3 : béquille retirée, ou dette assumée avec sa raison.
4. Sortie surefire de `TntEntityIsNewCallbackTest`.
5. Tableau des étapes du run, avec la nouvelle étape de double-subscribe.
6. Décisions tranchées seul, avec justification.
7. Ce parcours est-il déployable en production tel quel ? Si non, la liste de ce
   qui manque, avec pour chaque élément la preuve qu'il manque.
