# Lot C-ter — Couper le bypass dev et mesurer la chaîne JWT réelle

## 0. RÈGLE DE CONDUITE — AUTONOMIE TOTALE (à lire avant tout)

Tu procèdes de bout en bout, **sans jamais demander de confirmation**.

Sont explicitement interdits : « do you want to proceed ? », « should I continue ? »,
« veux-tu que je corrige aussi… ? », et toute pause de validation intermédiaire.
Tu tranches seul les arbitrages techniques, tu les consignes dans une section
**« Décisions prises sans arbitrage »**, et tu ne rends la parole **qu'une seule fois**,
à la fin, avec ton rapport complet.

Seul un blocage matériel infranchissable justifie de t'arrêter avant la fin — et dans
ce cas tu écris **ce que tu as tenté**, pas une question.

## 1. DÉPÔTS CIBLES

Ce lot touche **deux dépôts**. Les phases sont ordonnées : ne mélange pas les commits.

- **BFF** : `/home/jtk/projets/tiibntick-bff` — phase 1 uniquement
- **Core** : `/home/jtk/projets/tiibntick-core` — phases 2 à 6

### Branches

**BFF** — branche `fix/created-at-numeric-sort`, depuis `main`.

**Core** — branche `lotCter-coupure-bypass`, créée depuis **`lotCbis-auth-mode-detection`**,
surtout PAS depuis `github/main` ni depuis `lotC-otp-pickup`.

```bash
cd /home/jtk/projets/tiibntick-core
git checkout -b lotCter-coupure-bypass lotCbis-auth-mode-detection
wc -l scripts/e2e/e2e-freelancer-courier.sh     # DOIT afficher 880
```

Si ce `wc -l` n'affiche pas 880, tu t'es trompé de base : arrête, ne commite rien,
et écris-le dans ton rapport. 880 = version lot C-bis. 767 = lot C. 570 = lot B.

## 2. CE QUI EST DÉJÀ PROUVÉ (ne le re-démontre pas)

1. Le core tourne avec `TNT_AUTH_ALLOW_ANONYMOUS=true` + profil `dev`.
   `TntSecurityConfig.devAuthFilter()` injecte sur **chaque** requête un principal
   synthétique `ROLE_TNT_ADMIN` + 24 autres autorités. Le JWT n'est jamais lu.
2. Le bypass n'est écrit dans **aucun fichier du dépôt** : `.env` dit `false`, le défaut
   compose dit `false`, `docker-compose.override.yml` est muet. Il vient d'un `export`
   dans le shell qui a lancé compose — l'environnement du shell écrase `.env` à
   l'interpolation.
3. Sous bypass : tenant effectif `43427172-b6ee-4dbf-9148-96682702ffc9`
   (`tnt.auth.dev-tenant-id`), acteur `709f0069-c5ed-4d50-8ad0-b6c67a9eb630`
   (`tnt.auth.dev-actor-id`).
4. Le vrai jeton porte `sub=f69444e3-ec21-4fee-bd84-20c435c567ae`,
   `actor=514b632b-9af2-4dd7-ae39-da771201f8a8`,
   `tid=dbae6615-8f7e-4ef5-9e58-23a6179acf22`.
5. Cinq symptômes ont **la même** cause unique (le bypass), et non cinq bugs :
   C2 (0 mission), C6 (faux vert), C1 (ne prouve rien sur le core),
   le 403 de `PATCH /api/freelancers/{id}/location`, et le 409 du SETUP.
   Le 403 est prouvé par le log du conteneur :
   `Position spoofing attempt: caller 709f0069… tried to write position for f69444e3…`
   — c'est le garde-fou `!id.equals(securityContext.userId())` de
   `FreelancerLocationController` l.41. Ce n'est **pas** un problème préexistant.
6. Le core sérialise `createdAt` en **nombre** (`1790387231.830532000`), pas en chaîne ISO.

## 3. LE MUR QUI T'ATTEND — lis-le avant de couper quoi que ce soit

Couper le bypass ne suffira pas à rendre les scripts verts, et ce lot n'a pas pour but
de les rendre verts. Voici pourquoi, vérifié dans le code :

`TntSecurityConfig.tntJwtAuthenticationConverter()` construit les autorités
**uniquement** à partir des claims `roles` et `permissions` du JWT. Il n'existe
**aucun** enrichissement depuis la base : pas de `WebFilter` qui charge les rôles d'un
utilisateur, `TntPermissionEvaluator` ne lit que les autorités déjà présentes.

Or le jeton kernel réel porte exactement : `actor, sub, aud, iss, mfa, adm, exp, iat,
jti, tid`. **Ni `roles`, ni `permissions`.**

Conséquence mécanique : dès que le bypass tombe, tout endpoint annoté
`@PreAuthorize("hasRole(...)")` répondra **403**. Il y en a 12 sur
`FreelancerController` et 15 sur `DeliveryController`, dont
`@PreAuthorize("hasRole('FREELANCER')")` sur `GET /api/v1/freelancers/me` —
c'est-à-dire la sonde C0b elle-même.

Seuls les `@PreAuthorize("isAuthenticated()")` passeront (9 occurrences), dont
`POST /api/v1/freelancers`.

**Donc : ce lot est un lot de MESURE, pas un lot de correction.** Ton livrable n'est pas
« les scripts sont verts », c'est **« voici exactement ce qui casse, avec quel code HTTP,
à cause de quelle annotation, et voici les options pour le résoudre »**. Un rapport
honnête avec 6 échecs expliqués vaut infiniment mieux qu'un vert obtenu en desserrant
une garde.

## 4. PHASE 1 — BFF : Bug #1, `localeCompare` sur un nombre

Fichier : `/home/jtk/projets/tiibntick-bff/src/core-adapter/real/realCoreFreelancer.ts`

Deux sites, **l.164** et **l.201** :

```ts
.sort((a, b) => b.created_at.localeCompare(a.created_at));
```

`created_at` provient de `announcementToJob()` l.419 :
`created_at: ann.createdAt ?? new Date().toISOString()`. Quand le core renvoie un
nombre, `created_at` est un nombre, et `.localeCompare` n'existe pas dessus.

Démonstration déjà faite : 1 annonce visible → **200** (`Array.prototype.sort`
n'appelle pas le comparateur sur un tableau d'un seul élément) ; 2 annonces visibles →
**500** `b.created_at.localeCompare is not a function`.

Ce bug **bloque le lot C-ter** : le seed publie deux missions dans le même tenant, donc
dès que le scoping sera correct, C2 recevra deux annonces et plantera.

À faire :
1. Normaliser `createdAt` à la frontière, dans `announcementToJob()`, en une chaîne ISO
   quelle que soit la forme reçue du core (nombre de secondes epoch avec fraction,
   nombre de millisecondes, chaîne ISO, `null`). C'est un adaptateur : c'est **là** que
   la normalisation appartient, pas dans le comparateur.
2. Rendre le tri robuste malgré tout (compare des valeurs comparables, ne suppose pas
   le type).
3. **Test unitaire obligatoire avec au moins DEUX éléments** — un test à un seul élément
   ne prouve rien, c'est précisément ce qui a masqué le bug. Couvre : deux nombres, deux
   chaînes ISO, un mélange des deux, et `null`.
4. `npx tsc --noEmit` et la suite `npx vitest run` doivent passer.
5. Commit sur `fix/created-at-numeric-sort`. Message conventionnel.

## 5. PHASE 2 — Core : inventaire du fossé de rôles (mesure, aucune modification)

Aucune ligne de code modifiée dans cette phase. Tu produis un **tableau**.

1. Décode le jeton `TNT_E2E_TOKEN` et liste **tous** ses claims (sans réécrire le jeton
   entier dans le rapport : les 12 premiers caractères suffisent pour l'identifier).
2. Pour chacun des trois scripts (`e2e-freelancer-courier.sh`,
   `e2e-presence-loop.sh`, `e2e-freelancer-flow.sh`), liste les endpoints appelés.
3. Pour chaque endpoint, va lire l'annotation `@PreAuthorize` de sa méthode de
   contrôleur (ou note « aucune »).
4. Rends un tableau : `étape | méthode + chemin | @PreAuthorize | rôle exigé présent
   dans le jeton ? | verdict prévu`.

C'est cette table qui déterminera le lot suivant. Elle doit être exacte : chaque ligne
est une lecture de fichier, pas une supposition.

## 6. PHASE 3 — Couper le bypass

1. Trouve d'où vient le `true`. Inspecte l'environnement du conteneur
   (`docker inspect tnt-core`), puis cherche l'`export` responsable : historique de
   shell, scripts de lancement du dépôt, fichiers `.envrc`, alias. Consigne ce que tu
   trouves — et si tu ne trouves pas la source, écris-le franchement.
2. Relance le core depuis un shell **propre**, en garantissant
   `TNT_AUTH_ALLOW_ANONYMOUS=false`. Le profil Spring peut rester `dev` : c'est bien la
   combinaison `dev` + `allow-anonymous=false` qu'on veut mesurer.
3. Vérifie **dans le conteneur** que la variable vaut bien `false` après redémarrage.
   Pas dans ton shell : dans le conteneur.
4. Attends que `/actuator/health` soit `UP` avant de continuer.
5. Sonde de contrôle, avant tout le reste :
   ```bash
   curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/v1/freelancers/me
   curl -s -o /dev/null -w "%{http_code}\n" -H "Authorization: Bearer ceci.est.nimportequoi" \
     http://localhost:8080/api/v1/freelancers/me
   ```
   Les deux **doivent** renvoyer 401. S'ils renvoient 200, le bypass est toujours actif :
   n'avance pas, remonte-le.

## 7. PHASE 4 — Ré-onboarder le freelancer par l'API

Il n'existe **qu'un seul** profil freelancer dans la base locale
(`e2e00000-…` / tenant `43427172` / acteur `709f0069`) et il appartient à l'identité dev.
Le vrai jeton ne le verra jamais.

- Crée le profil du vrai utilisateur via **`POST /api/v1/freelancers`** avec le vrai
  jeton. Cet endpoint est `@PreAuthorize("isAuthenticated()")`, donc il passera, et il
  dérive tenant + acteur de `@CurrentUser` : le profil naîtra automatiquement dans le
  bon tenant. Ne passe **aucun** identifiant de tenant dans le corps.
- **Interdiction absolue d'un `INSERT` psql pour créer ce profil.** Un profil posé à la
  main contourne exactement la chaîne qu'on cherche à valider, et nous replacerait dans
  la situation du lot C.
- Relis en base ce que l'API a créé : `actor_id` et `tenant_id` réellement écrits.
  Compare-les au `sub` et au `tid` du jeton et **dis lequel des deux** (`sub` ou `actor`)
  a servi d'`actor_id`. Cette réponse compte : c'est elle qui dira si le 403 de
  `PATCH /api/freelancers/{id}/location` disparaît ou non.
- Si la création échoue, rapporte le code HTTP et le corps de la réponse. N'essaie pas
  de contourner.

## 8. PHASE 5 — Relancer les trois scripts

Dans cet ordre, BFF en `CORE_ADAPTER=real` avec le correctif de la phase 1 en place :

1. `scripts/e2e/e2e-freelancer-courier.sh`
2. `scripts/e2e/e2e-presence-loop.sh`
3. `scripts/e2e/e2e-freelancer-flow.sh`

Pour **chaque** étape en échec, tu dois fournir trois choses, sans exception :
le **code HTTP**, le **corps de la réponse**, et la **cause racine constatée** —
annotation `@PreAuthorize`, garde applicative, ligne de log du conteneur. Va chercher la
ligne dans `docker logs tnt-core`. Une cause sans preuve n'est pas une cause.

C0b doit maintenant afficher `AUTH_MODE=jwt-reel` et `TENANT_SCOPING_PROVEN=true` —
sauf si `/api/v1/freelancers/me` renvoie 403 faute de `ROLE_FREELANCER`, auquel cas la
sonde c) ne renvoie plus 200 et C0b conclura à tort au bypass. Si c'est le cas, corrige
la logique de C0b pour distinguer les trois situations : **401** = non authentifié,
**403** = authentifié mais rôle manquant, **200** = autorisé. Un 403 sur la sonde c)
prouve que la chaîne JWT réelle est active — c'est un résultat, pas une panne.

## 9. INTERDICTIONS

- Ne **remets pas** `TNT_AUTH_ALLOW_ANONYMOUS=true` pour faire passer des étapes.
- N'ajoute **aucun** rôle au `devAuthFilter()`, et ne l'élargis pas.
- Ne **touche à aucune** annotation `@PreAuthorize` : ni suppression, ni élargissement,
  ni `permitAll`. Le fossé de rôles se mesure dans ce lot, il se comble dans le suivant.
- Ne **fabrique pas** un jeton maison avec un claim `roles` ajouté à la main pour
  contourner le problème. Si tu penses que c'est la bonne solution, écris-le comme
  **recommandation** dans le rapport — ne l'implémente pas.
- Aucun `INSERT`/`UPDATE` psql sur `freelancer_profiles`, `iwm_users`, ou les tables de
  rôles.
- Aucun `|| true` sur une assertion. Uniquement sur les `DELETE` de nettoyage.
- Ne traite **pas** le Bug #2 (`markNotNew()` après `deliveryRepository.save(local)`
  dans `AnnouncementApplicationService`) : il fait l'objet d'un lot core séparé.
- Le tableau final ne doit contenir ni « attendu », ni « probable », ni « devrait ».
  Chaque case est une observation.

## 10. RAPPORT FINAL — une seule prise de parole

1. Tableau de la phase 2 (fossé de rôles), complet.
2. Origine du `true` : trouvée où, ou « non trouvée ».
3. Sonde de contrôle après coupure : les deux codes HTTP.
4. Résultat du ré-onboarding : `actor_id` et `tenant_id` écrits, et lequel du `sub`/
   `actor` a servi.
5. Tableau des trois scripts, étape par étape, avec pour chaque échec : HTTP + corps +
   cause prouvée.
6. **Décisions prises sans arbitrage.**
7. Ce qui reste bloqué, et ce que tu recommandes pour le lot suivant — en distinguant
   ce qui exige un changement **kernel** (émettre `roles` dans le jeton) de ce qui
   exige un changement **core** (enrichir les autorités depuis la base).
8. SHA des commits, par dépôt.
