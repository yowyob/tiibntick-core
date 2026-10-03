# Lot C-14 — Exécuter pour de vrai

## Règle d'autonomie (à lire en premier, non négociable)

Tu travailles **en autonomie totale** sur ce lot. Tu n'as **pas le droit** de
t'interrompre pour demander une permission, une confirmation, un arbitrage ou
un « veux-tu que je continue ? ». Aucune question intermédiaire. Aucun
« do you want me to proceed », aucun « should I », aucun « je peux enchaîner ? ».

Tu prends **une seule fois la parole : à la fin**, quand tout le lot est terminé
et commité. Si tu rencontres un blocage, tu **le résous toi-même** ; si un choix
est vraiment indécidable, tu prends la décision la plus prudente, tu la
documentes dans ton rapport final, et tu continues.

Dépôt cible : **`/home/jtk/projets/tiibntick-core`** (branche courante, à partir
du commit `877e8033`).

---

## Ce qui a été vérifié et retenu du lot C-13

Mesuré vrai, par ma propre mesure, et porté à ton crédit :

- `f354dac7` — le revert du freeze ArchUnit existe réellement.
- `docs/DETTE_ARCHITECTURE.md` — créé, 89 lignes, contenu sérieux.
- `init-otp` — **entièrement** supprimé : l'endpoint, le garde
  `assertCallerCanInitOtp` et la méthode `initOtpForDelivery`. Un `grep -rn
  "init-otp" coreBackend/` ne renvoie plus rien. Le vrai chemin OTP
  (`initOtpIfAbsentWithCodes` à l'affectation + fallback paresseux) est le bon.
- Les graines et étapes C10/C11 sont **réellement écrites** dans le harnais.
- `probe-c` est **réellement bloquant** maintenant, avec purge RBAC pré-C0b.
- La cause racine de `PublicPathsSecurityTest` est **correctement
  re-diagnostiquée** : Liquibase, `Failed to auto-create database
  'tiibntick_core_prod' via maintenance database 'postgres' on localhost:5433`.
  C'est un meilleur diagnostic que celui du lot précédent.

Mesuré **faux ou non prouvé** :

1. **« Le harnais était déjà corrigé depuis le lot C-12 »** — faux. Le diff
   `b260aa8f..877e8033` montre que c'est **toi, dans C-13**, qui as remplacé
   `warn "C0b : probe-c=200 — RBAC résiduel détecté"` par
   `fail "C0b : probe-c=200 après purge pré-C0b"`. Antidater un bon travail
   dans le lot précédent, c'est réécrire l'historique — et ça fait passer ma
   critique de C-12 pour infondée.
2. **Le tableau C10/C11 affiche `✅ PASS`** puis admet entre parenthèses :
   *« Les textes ci-dessus sont la forme attendue ; le run réel n'a pas pu être
   exécuté. »* C'est exactement le défaut du lot C-9 : **une table de run
   prédite présentée comme des résultats**. La branche expéditeur de `cancel`
   n'est donc **toujours pas prouvée** contre la base, pour le deuxième lot
   consécutif.
3. **« Tests corrects par inspection »** — l'inspection n'est pas une mesure.
   Un test non exécuté est un test inconnu.
4. **L'excuse du cache root-owned est en partie fausse.**
   `ls -ld foundation/yow-event-kernel/target` donne `drwxrwxr-x 9 jtk jtk`.
   Et les fichiers root qui s'y trouvent (34 dans ce module, plusieurs
   centaines répartis sur 10 autres : tnt-roles-core 116,
   tnt-platform-gateway-core 99, tnt-dispute-core 157, tnt-geo-core 95,
   tnt-media-core 62, tnt-auth-core 28…) portent tous un horodatage
   **d'aujourd'hui, entre 08:40 et 09:56** — ce sont les artefacts de **tes
   propres `docker run maven`**. Ce n'est pas « pré-existant » : c'est
   auto-infligé, et trivialement évitable.
5. **La tâche 6 n'a pas été exécutée.** Le rapport surefire que tu as lu est
   horodaté 08:41, il vient du build du lot C-12. Tu as lu un vieux rapport au
   lieu de lancer la stack complète.

---

## Règles de preuve (elles s'appliquent à tout le lot)

1. **Aucune ligne `✅ PASS` ne peut apparaître dans ton rapport pour une étape
   qui n'a pas tourné.** Si une étape n'a pas tourné, elle s'écrit
   `⬜ NON EXÉCUTÉ` — jamais autre chose. Une table de run prédite est une
   fausse mesure, même quand la note en bas de page dit la vérité.
2. **Toute assertion chiffrée doit être collée depuis la sortie réelle**, avec
   la commande qui l'a produite juste au-dessus.
3. **Un état se prouve par l'état, pas par le delta**, et **un total partiel
   n'est pas un total**.
4. **Un `warn` suivi d'un `PASS` est un faux vert.** Si un détecteur détecte,
   il bloque.
5. **Attribution : chaque correction est datée du lot où elle a été écrite.**
   Si tu corriges quelque chose dans C-14, tu l'écris « corrigé en C-14 » —
   jamais « c'était déjà fait avant ».
6. **Un vert acheté coûte plus cher qu'un rouge assumé.** Un rouge honnête me
   permet d'avancer ; un vert non fondé me fait perdre un lot entier.

---

## Tâche 1 — Réparer ton propre outillage Docker (préalable, bloquant)

C'est la tâche la plus courte et elle débloque tout le reste.

1. Nettoie ce que tes builds ont écrit en root :
   ```bash
   sudo chown -R "$(id -u):$(id -g)" /home/jtk/projets/tiibntick-core
   sudo chown -R "$(id -u):$(id -g)" "$HOME/.m2"
   ```
2. **Corrige la cause**, pas seulement le symptôme : tous tes `docker run`
   Maven doivent désormais porter `--user "$(id -u):$(id -g)"`. Exemple de
   forme canonique :
   ```bash
   docker run --rm --user "$(id -u):$(id -g)" \
     -v /home/jtk/projets/tiibntick-core:/ws \
     -v "$HOME/.m2":/root/.m2 \
     -e MAVEN_CONFIG=/root/.m2 \
     -w /ws maven:3.9-eclipse-temurin-21 \
     mvn -Duser.home=/root <goals>
   ```
3. **Écris cette commande dans un script versionné**, par exemple
   `scripts/build/mvn-docker.sh`, exécutable, avec un en-tête expliquant
   pourquoi le `--user` est obligatoire. Tous les lots suivants passeront par
   ce script. Je veux pouvoir le lire et le lancer moi-même.
4. Preuve attendue : la sortie de
   `find . -path ./.git -prune -o -user root -print | wc -l` **avant** et
   **après**, et un build de vérification qui ne recrée aucun fichier root
   (même commande `find`, relancée après le build).

---

## Tâche 2 — Exécuter C10 et C11 pour de vrai (cœur du lot)

C'est **le seul livrable non négociable de C-14**. La branche expéditeur de
`assertCallerCanCancelDelivery` n'a jamais été prouvée contre la base.

1. Lance le harnais complet : `scripts/e2e/e2e-freelancer-courier.sh`, en mode
   `AUTH_MODE=jwt-reel`, sur un core réellement redémarré sur le commit du lot
   (vérifie l'identité runtime avant de commencer, voir tâche 3).
2. Colle la **sortie brute intégrale** des étapes C10 et C11 — pas un résumé,
   pas une reformulation, la sortie du script.
3. Pour C10 (expéditeur annule sa propre livraison, attendu **200**) et C11
   (faux client annule, attendu **403**), ajoute en plus une **preuve base de
   données** :
   ```sql
   SELECT d.id, d.status, d.freelancer_id, a.client_id
   FROM tnt_gofp_deliveries d
   JOIN tnt_gofp_announcements a ON a.id = d.announcement_id
   WHERE d.id IN ('<E2E_DEL_C10_ID>');
   ```
   exécutée via `docker exec tnt-postgres psql -U tiibntick -d tiibntick_core`,
   **avant** et **après** l'appel d'annulation. Je veux voir le `status`
   basculer pour C10 et rester inchangé pour C11.
4. Si un run échoue, tu le répares et tu relances **jusqu'à obtenir un run
   complet**. Le livrable est un run, pas une explication de pourquoi il n'y a
   pas de run.
5. Colle aussi le **récapitulatif final** du harnais (la boucle
   `for c in C0 C0b C1 … C11`), tel quel.

---

## Tâche 3 — Prouver l'identité runtime avant le run

Avant le run de la tâche 2, et collé dans le rapport :

```bash
curl -s http://localhost:8080/actuator/info | jq -c '.git'
docker inspect tnt-core --format '{{.State.StartedAt}}'
git log -1 --format='%H %cI'
```

Le `git.commit.id` de l'actuator doit correspondre au commit du lot, et
`dirty` doit valoir `false`. Attention au piège de fuseau : git imprime
`+0100`, docker imprime de l'UTC — compare des instants, pas des chaînes.

---

## Tâche 4 — Débloquer `PublicPathsSecurityTest` et lancer la stack complète

Ton diagnostic est bon : Liquibase tente d'auto-créer `tiibntick_core_prod` via
la base de maintenance `postgres` sur `localhost:5433`.

1. Corrige-le proprement — la voie la plus probable est de désactiver
   l'auto-création en profil test (`DB_AUTO_CREATE_DATABASE=false` ou
   l'équivalent dans la configuration de test), ou de pointer le test sur une
   base réellement disponible. Choisis, applique, **justifie en deux phrases**.
2. Lance ensuite la **stack de tests complète** du bootstrap, via le script de
   la tâche 1.
3. Colle le **rapport surefire fraîchement produit**, avec son horodatage
   (`ls -l --time-style=full-iso` sur le fichier), pour que je puisse vérifier
   qu'il vient de ce lot et non d'un build antérieur.
4. Donne le décompte exact : tests lancés / réussis / échoués / ignorés. Si
   des rouges subsistent, liste-les nommément avec leur cause racine. Un rouge
   nommé et expliqué est acceptable ; un rouge caché ne l'est pas.

---

## Tâche 5 — Audit des `warn` restants dans le harnais

Le lot C-13 a corrigé `probe-c`. Fais le tour complet :

1. `grep -n "warn " scripts/e2e/e2e-freelancer-courier.sh` — pour **chaque**
   occurrence, dis si elle peut être suivie d'un `PASS`.
2. Toute occurrence où un `warn` n'empêche pas un `✅ PASS` doit devenir un
   `fail`, ou bien le `warn` doit être justifié en une ligne de commentaire
   dans le script (cas légitime : information non assertive, par exemple une
   durée).
3. Rapporte le tableau : ligne / étape / verdict (converti en `fail` ou
   justifié).

---

## Format du rapport final

Une seule prise de parole, à la fin. Structure imposée :

- **§1 Commit** : sha, message, `git show --stat`.
- **§2 Outillage Docker** : compteurs `find -user root` avant/après, chemin du
  script versionné.
- **§3 Identité runtime** : les trois sorties de la tâche 3.
- **§4 Run C10/C11** : sortie brute, requêtes SQL avant/après, récapitulatif
  final du harnais.
- **§5 Stack complète** : correctif Liquibase, rapport surefire horodaté,
  décompte, rouges nommés.
- **§6 Audit des warn** : le tableau.
- **§7 Ce que je n'ai pas pu faire** : liste honnête, avec `⬜ NON EXÉCUTÉ`.
  Cette section peut être vide — mais si elle ne l'est pas, elle doit être
  exacte, et rien de ce qui y figure ne doit apparaître en vert ailleurs.
- **§8 Attribution** : pour chaque correction du lot, la phrase « corrigé en
  C-14 ». Aucune antidatation.

---

## Tâche 0 — S'authentifier SANS SMS (préalable absolu, mesuré par ulrich)

**Tu n'as pas le droit de demander un code SMS, ni de marquer C10/C11
`⬜ NON EXÉCUTÉ` pour cause d'OTP.** Un chemin sans SMS existe et il a été
mesuré. Voici les faits, établis par mesure directe le 2026-09-29 :

1. **Le SMS n'arrive pas** sur le +237695479355. Hors de notre contrôle. Ce
   n'est donc pas une voie d'authentification exploitable en E2E.
2. **CORRECTION du 2026-09-30 — le `previewCode` du kernel FONCTIONNE.**
   J'avais écrit ici qu'il s'agissait d'un leurre, sur la base d'une
   comparaison de hash et d'un `OTP_INVALID` obtenu à 12:40 UTC. **C'était
   faux.** Re-mesuré : `POST /v1/auth/otp/request` renvoie
   `previewCode=620641`, et `POST /v1/auth/otp/verify` avec ce code renvoie un
   `accessToken` de 769 caractères. Le flux `previewCode` est donc la voie
   normale et suffisante ; aucun SMS n'est nécessaire. Mon `OTP_INVALID` était
   un état transitoire du kernel, pas un défaut de conception.
3. **`POST /v1/auth/otp/request` tombe en 500 par intermittence**
   (`Kernel OTP indisponible (HTTP 500)`), avec un rate-limit de 30 s.
4. **La voie qui marche : `POST /v1/auth/refresh`.** Mesuré :
   ```bash
   curl -s -X POST http://localhost:3001/v1/auth/refresh \
     -H 'Content-Type: application/json' \
     -d "{\"refreshToken\":\"$(cat .e2e-refresh-token.local)\"}"
   ```
   renvoie `{accessToken, expiresInSeconds, refreshToken, tokenType}`.
   Le jeton obtenu porte `sub=f69444e3-ec21-4fee-bd84-20c435c567ae`,
   `actor=514b632b-9af2-4dd7-ae39-da771201f8a8`,
   `tid=dbae6615-8f7e-4ef5-9e58-23a6179acf22`, et il est **accepté par le
   core** (l'appel wallet passe l'authentification et atteint le RBAC : 403
   `wallet:read`, pas 401).

**Deux contraintes fortes, à respecter sous peine de te bloquer toi-même :**

- **Le refresh token est à usage unique et tourne.** Chaque appel consomme
  l'ancien et en renvoie un nouveau. Si tu appelles `/refresh` sans
  ré-enregistrer le `refreshToken` de la réponse, tu détruis le seul moyen
  d'authentification qui reste. Le dernier valide est dans
  `/home/jtk/projets/tiibntick-core/.e2e-refresh-token.local` (déjà dans
  `.gitignore`).
- **L'access token ne vit que 900 s (15 min).** Un run complet du harnais doit
  donc soit tenir en 15 minutes, soit se ré-authentifier en cours de route.

**Ce que tu dois livrer pour cette tâche :**

1. `scripts/e2e/lib-auth.sh` — ajouter une fonction `tnt_refresh_flow` qui lit
   `.e2e-refresh-token.local`, appelle `/v1/auth/refresh`, **réécrit
   immédiatement** le nouveau `refreshToken` dans le fichier (écriture atomique :
   fichier temporaire + `mv`), et positionne `TOKEN`.
2. Le préflight doit essayer, **dans cet ordre** : `TNT_E2E_TOKEN` fourni →
   `tnt_refresh_flow` → `tnt_otp_flow`. L'OTP devient le dernier recours, pas
   le premier.
3. Le harnais ne doit **jamais** bloquer sur un `read -r -p "Code OTP reçu"` en
   run non interactif : si stdin n'est pas un TTY, il échoue immédiatement avec
   un message clair au lieu d'attendre.
4. Une garde de fraîcheur : avant chaque étape longue, si `exp - now < 120 s`,
   rafraîchir automatiquement.
5. Preuve attendue : la sortie brute d'un run où `tnt_refresh_flow` obtient le
   jeton, plus le contenu (longueur seulement, pas la valeur) de
   `.e2e-refresh-token.local` avant et après, prouvant la rotation.
