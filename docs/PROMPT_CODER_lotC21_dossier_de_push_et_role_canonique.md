# Lot C-21 — le dossier de push, et le trou `freelancer:read`

## 0. Règle de conduite — autonomie totale

**Tu vas jusqu'au bout de ce prompt en une seule prise de parole.** Tu ne demandes
jamais de confirmation. Sont nommément interdits : « do you want me to proceed ? »,
« should I continue ? », « veux-tu que je … ? », et toute pause de validation
intermédiaire, y compris avant une commande longue, avant un commit, avant de lancer
les tests.

Tu tranches seul tous les arbitrages techniques, tu les consignes dans une section
**« Décisions prises sans arbitrage »** de ton rapport final, et tu ne rends la parole
qu'une seule fois, à la fin, avec ce rapport.

Seul un blocage matériel infranchissable justifie de t'arrêter avant la fin. Dans ce cas
tu écris **ce que tu as tenté et ce que tu as mesuré**, pas une question.

Une exception, et une seule, à l'autonomie : **§6 — tu ne pousses rien.** C'est détaillé
là-bas et ce n'est pas une question, c'est une borne de périmètre.

## 1. Dépôt cible

Tout ce lot se passe dans **`/home/jtk/projets/tiibntick-core`**.

Ce prompt est rangé dans `/home/jtk/projets/tiibntick-core/docs/PROMPT_CODER_lotC21_dossier_de_push_et_role_canonique.md`.
Commite-le avec le reste du lot.

Tu ne touches ni `tiibntick-bff` ni `tiibntick-mobile`. Si une correction y est nécessaire,
tu la décris dans ton rapport, tu ne la fais pas.

## 2. Où on en est — l'état vérifié, pas ton souvenir

Le lot C-20 est **accepté**. Ses chiffres ont été recoupés un par un : les 6 commits,
`dirty=false` sur `246d2ce`, `/actuator/health` en `DEGRADED` avec liveness et readiness
toujours à 200, la jauge DEAD à 15, le grand livre à 10 transactions / 28 500, le wallet B
à 5 700, une seule assignation pour le compte B (ton correctif d'idempotence tient),
`RemoteReactivePermissionResolver` bien bouchonné, `mode: ${TNT_ROLES_PERMISSION_MODE:LOCAL}`
identique au commit de prod. Ta sonde « même jeton, deux cores » est la bonne méthode :
une mesure, pas une lecture de code.

Deux corrections à ton recap :

1. **Tu annonces 13 commits non poussés. Il y en a 19.** Après `git fetch github`,
   `git log --oneline github/main..HEAD | wc -l` → `19`, de `09e10cd5` (C-16) à `fd5e4f15`
   (C-20) ; `github/main` est à `8ed4710a` (C-14). Un jour de push, ce chiffre ne se
   recopie pas de mémoire.
2. **Tu annonces « roles-core 160/160, gofp 128/128 » — c'est exact — mais tu as aussi
   ajouté un test dans `tnt-bootstrap`, dont tu ne donnes aucun chiffre.** Son rapport
   surefire du run de 03:38–03:39 dit : `Tests run=35, Failures=3`. J'ai vérifié que les
   trois échecs sont **antérieurs** au lot (présents au commit de prod), donc ce n'est pas
   une régression de C-20. Mais un module dont tu modifies la suite et dont tu ne donnes
   pas le compte, c'est exactement la forme que prenaient les faux verts C-4 à C-8. À
   partir de maintenant : **tout module que tu touches, tu en donnes le compte, vert ou
   rouge.**

Et le constat qui détermine ce lot : **la question d'autorité étant tranchée, le Kernel
n'est plus un blocage.** Ce qui bloque la production est désormais entièrement sous notre
contrôle : la prod tourne sur `689fabf` (lot C-14), où aucun freelancer ne peut même
souscrire, parce que C-16 (attribution de FREELANCER à la création) et C-18 (repli tenant
système du résolveur) n'y sont pas. **Le blocage, c'est le push.**

Ce lot prépare ce push jusqu'au dernier centimètre.

## 3. C-21.1 — Le dossier de push

Livrable : **`docs/deploy/C21_DOSSIER_DE_PUSH.md`**, versionné.

Il doit répondre, par la mesure et non par la lecture du journal des lots :

### 3.1 Inventaire exact

- Le nombre réel de commits `github/main..HEAD`, après `git fetch github`, avec la commande
  et sa sortie collées.
- Le nombre de fichiers modifiés et la répartition par module (`git diff --name-only github/main..HEAD`).
- **La liste des changements de schéma.** Ma mesure dit zéro : aucun `.sql` ni aucun
  changelog Liquibase dans le diff. Confirme-le ou contredis-le, avec la commande. Si c'est
  confirmé, dis-le explicitement — c'est le fait qui désamorce la moitié du risque de ce push.
- **Le diff complet de `tnt-bootstrap/src/main/resources/application.yml`**, qui est le seul
  fichier de configuration de production touché. Ma lecture : uniquement le bloc
  `management.endpoint.health.status` de C-20.2 (`order: DOWN,OUT_OF_SERVICE,DEGRADED,UP,UNKNOWN`
  et `http-mapping: DEGRADED: 200`).

### 3.2 Le seul risque sérieux que je vois, à instrumenter

Ce bloc `health.status` est la pièce la plus dangereuse du push, parce qu'il touche les
sondes que l'orchestrateur utilise pour décider si le conteneur est vivant. Si en prod
l'agrégat passe `DEGRADED` **et** que le mapping HTTP ne s'applique pas comme en local, la
readiness part en 503, le proxy ne basculera jamais, et **la prod tombe sur un changement
de configuration, pas sur un bug de code**.

Donc : prouve que ce n'est pas possible, sur un conteneur **fraîchement redémarré** avec le
**profil `prod`** autant que ton environnement le permet, et colle les sorties :

- `/actuator/health` (statut agrégé + code HTTP),
- `/actuator/health/liveness` et `/actuator/health/readiness` (code HTTP de chacun),
- le même triplet avec l'outbox **vidée de ses entrées DEAD** (sur une base de test, pas la
  base de dev), pour montrer les deux états.

Si tu ne peux pas monter le profil `prod` localement, dis-le et écris précisément ce qui
manque — ne substitue pas le profil `dev` en silence.

### 3.3 Le plan de retour

- La commande exacte pour revenir à l'état actuel de production, et ce qu'elle implique
  compte tenu du fait que le miroir `yowyob` **réécrit les SHA** (cf. `docs/NOTE_DEPLOIEMENT_CORE.md`
  §3 : `/actuator/info` ne renvoie jamais un SHA de notre dépôt).
- Les effets **non réversibles par un retour de code** : lignes écrites en base par le
  nouveau code et que l'ancien lirait mal, entrées d'outbox créées, wallets créés par
  `getOrCreateWallet`. Pour chacun : est-ce que l'ancien binaire s'en sort ? Réponds oui ou
  non, avec la raison.
- La fenêtre de bascule : ~20 min bout en bout d'après la note de déploiement, avec une
  courte fenêtre de 404 sur le proxy. Écris la séquence de vérification à exécuter *après*
  la bascule, dans l'ordre, avec les commandes prêtes à copier — y compris la résolution du
  SHA du miroir vers le nôtre via le remote `yowyob`.

### 3.4 Ce que la prod gagne, en langage d'utilisateur

Un tableau : pour chacun des 19 commits, une ligne — **ce qu'un utilisateur de l'APK peut
faire après qui ne marchait pas avant**, ou « interne, aucun effet utilisateur ». Pas de
résumé technique du diff : l'effet observable. C'est ce tableau qui sert à décider si on
pousse.

## 4. C-21.2 — Rendre la suite `tnt-bootstrap` lisible

Trois échecs, préexistants au lot, qu'il ne faut **ni geler ni ignorer** :

1. **`SpringdocProdExposureTest`** : `springdoc.swagger-ui.enabled` et `api-docs.enabled`
   sont à `true` **dans le profil `prod`**, réactivés le 23/07/2026 « temporairement, pour
   une revue d'audit externe » — le commentaire est en clair dans `application.yml:625`.
   Deux mois et demi plus tard c'est toujours actif, donc Swagger UI et `/v3/api-docs` sont
   ouverts en production : la cartographie complète de la surface d'attaque.
   **Tu ne touches pas à cette valeur** (voir §7). Ce que tu fais : établir par la mesure si
   ces deux routes répondent réellement sur `https://tiibntick-core.yowyob.com` aujourd'hui,
   sans authentification. `curl -s -o /dev/null -w '%{http_code}'` sur `/swagger-ui.html`,
   `/v3/api-docs`, et la route configurée en §166 de l'`application.yml`. Colle les codes.
   C'est un fait, et il décide de l'arbitrage.
2. **`LayeringArchitectureTest.applicationMustNotDependOnAdapterInWeb`** : ~993 violations
   hors du freeze du 18/07. **Tu ne regèles rien** (voir §7). Ce que tu fais : un comptage
   par module des violations non gelées, pour qu'on sache si c'est de la dette d'un coin du
   dépôt ou un motif généralisé. Une ligne par module, un nombre.
3. **`LayeringArchitectureTest.adapterInMustNotDependOnAdapterOut`** : **une seule**
   violation, et elle est dans **notre module**, `gofp` —
   `NotificationStreamController.getNotificationStream` expose un
   `adapter.out.kafka.event.MatchingNotificationEvent` dans sa signature de retour SSE.
   Celle-là, **corrige-la** : un DTO de réponse web dans `adapter.in.web`, un mapping, et la
   signature du contrôleur qui n'expose plus l'événement Kafka. Vérifie que le contrat JSON
   du flux SSE est **identique octet pour octet** avant/après — le mobile le consomme.
   Prouve-le par un test, pas par relecture.

## 5. C-21.3 — Le trou `freelancer:read`

Ta propre sonde l'a trouvé : `GET /api/v1/freelancers/me` répond **403** pour un utilisateur
qui **est** FREELANCER, parce que le rôle canonique ne porte pas `freelancer:read`. Le BFF
contourne en traitant le 403 comme « pas de profil » et en repartant sur
`POST /api/v1/freelancers` — le repli que tu viens de rendre non destructeur, mais qui reste
une création déguisée en lecture.

Côté core :

- Ajoute `freelancer:read` aux permissions du rôle canonique FREELANCER dans
  `TntRoleDefinitionRegistry`, et assure-toi que la réconciliation des permissions
  l'applique aux porteurs **existants** du rôle — pas seulement aux nouveaux. C'est
  exactement le motif de C-17, ne le refais pas à moitié.
- **Prouve-le par le run**, pas par le test unitaire : le même compte B qui recevait 403
  doit recevoir **200** sur `GET /api/v1/freelancers/me`. Colle les deux sorties, avant et
  après, avec le commit en vol dans chacune.
- Vérifie qu'aucune autre route ne devient accessible par effet de bord : liste les points
  d'appel annotés `@RequirePermission(resource="freelancer", action="read")` et dis, pour
  chacun, si un freelancer doit pouvoir y accéder. S'il y en a un où non, dis-le et ne
  l'ouvre pas.
- Ne touche **pas** au repli du BFF. Décris dans ton rapport ce qu'il faudra y changer une
  fois le 200 acquis, pour que je l'instruise dans un lot BFF.

## 6. C-21.4 — La bascule : tu prépares, tu ne pousses pas

**Tu n'exécutes aucun `git push`, vers aucun remote.** Ce n'est pas une question de
confirmation, c'est le périmètre : la décision de basculer la production appartient au
fondateur, et elle se prend sur le dossier du §3, pas en cours de lot.

Ce que tu livres à la place, à la fin de `C21_DOSSIER_DE_PUSH.md` :

- La **commande exacte** à exécuter, avec son remote et sa branche, telle qu'elle pourra
  être copiée sans réflexion.
- Les vérifications **à faire juste avant** de la lancer (arbre propre, `fetch` à jour,
  suites de tests des modules touchés, compte de commits re-mesuré).
- La séquence de vérification **d'après-bascule** du §3.3, dans l'ordre.
- Ce qu'il faudra regarder **dans les 24 h** qui suivent : quelles jauges, quels motifs de
  log, quelles requêtes SQL, et à partir de quelle valeur il faut revenir en arrière.

## 7. Ce que tu ne décides pas seul

- **Swagger et `/v3/api-docs` en prod.** Tu mesures, tu ne modifies pas. C'est une décision
  du fondateur : la revue d'audit qui justifiait la réactivation est peut-être finie, peut-être
  pas, et ce n'est pas à toi de le supposer.
- **Le freeze ArchUnit.** Tu ne regèles rien, tu ne mets pas le store à jour, tu comptes.
- **Le barème du quota freelancer** (`remaining_deliveries`, `SubscriptionType`, quelle copie
  fait foi, qui décrémente et quand). Décision produit, toujours ouverte.
- **L'idempotence de `splitMissionRevenue` par `reference_id = MISSION-<deliveryId>` et la
  reprise automatique des `DELIVERED` impayées**, que tu as proposées en C-20.3. Ta proposition
  est bonne et ton analyse du double crédit est juste. Elle touche le module billing (propriétaire
  L5) : elle s'arbitre, elle ne se code pas dans ce lot.
- **Les gardes `@PreAuthorize("hasRole('FREELANCER'))`** mortes faute de claim `roles` dans le
  jeton Kernel. Tu les as correctement identifiées. On ne les change pas avant d'avoir la réponse
  du Kernel sur la question 4 de ta demande (`§2` de `docs/e2e/c20/README.md`) : si des claims
  `roles` arrivent un jour, la bonne correction n'est pas la même.
- **Le push.** Voir §6.

## 8. Ce que je vérifierai — donc ne le survends pas

- `git fetch github && git log --oneline github/main..HEAD | wc -l` — le compte, de mon côté.
- `git diff --name-only github/main..HEAD` et la recherche de changements de schéma, de mon côté.
- `curl /actuator/info`, `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`
  avec leurs codes HTTP, sur le conteneur en vol.
- Les codes HTTP réels de `/swagger-ui.html` et `/v3/api-docs` sur l'instance de production.
- `GET /api/v1/freelancers/me` avec le jeton du compte B : je veux voir le 200 moi-même.
- `SELECT` sur les permissions du rôle FREELANCER et sur les assignations, pour vérifier que la
  réconciliation a bien touché les porteurs existants.
- Les comptes surefire de **tous** les modules que tu touches, y compris `tnt-bootstrap`.
- `git ls-files` sur chaque fichier de preuve que tu cites — un fichier dans `/tmp` ou dans
  `scratchpad/` ne compte pas comme preuve.
- Et un `grep -i` partout, parce que j'ai déjà perdu une assertion sur une casse.

## 9. Rapport final

Une seule prise de parole, à la fin. Elle contient :

1. **Ce que le lot prouve** — assertion par assertion, avec la commande et sa sortie.
2. **Ce que le lot ne prouve pas** — et cette section doit être longue. C'est la partie de tes
   rapports C-19 et C-20 qui a le plus de valeur ; ne la raccourcis pas parce que le lot est
   surtout documentaire.
3. **Décisions prises sans arbitrage.**
4. **Les comptes de tests de tous les modules touchés**, vert ou rouge, sans exception.
5. **Le hash du commit final**, et la mention explicite « non poussé ».
