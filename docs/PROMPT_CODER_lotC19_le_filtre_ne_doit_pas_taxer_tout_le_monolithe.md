# Lot C-19 — Le filtre ne doit pas taxer tout le monolithe · compte vierge · `register`

## 0. Règle de conduite — AUTONOMIE TOTALE (à lire avant tout)

Tu procèdes **de bout en bout sans jamais demander de confirmation**. Sont interdits nommément :
« do you want to proceed ? », « should I continue ? », « veux-tu que je continue ? », et toute
pause de validation intermédiaire. Tu tranches seul tous les arbitrages techniques, tu les
consignes dans une section « Décisions prises sans arbitrage », et **tu ne rends la parole
qu'une seule fois, à la fin, avec ton rapport**. Seul un blocage matériel infranchissable
justifie de t'arrêter avant la fin — et dans ce cas tu écris ce que tu as tenté, pas une question.

## 1. Dépôt cible

**`/home/jtk/projets/tiibntick-core`**, branche `feat/lotC16-role-freelancer`, qui part de
`f23fabb9`. Ouvre VS Code sur ce dossier. Rien d'autre.

## 2. Contexte — ce que C-18 a réussi, et le prix qu'il fait payer

C-18 est accepté. La projection freelancer est créée par du code de production, les trois `id`
coïncident, le harnais E2E n'insère plus rien, et ton aveu sur le wallet non prouvé est exact
(vérifié : `billing.wallet_wallets.created_at = 2026-09-25 19:32:35`, antérieur au run).

Mais la vérification indépendante a sorti **trois faits que ton rapport n'a pas vus**. Les voici,
mesurés, pour que tu n'aies pas à les redécouvrir.

### Fait 1 — `GofpFreelancerProvisioningFilter` sérialise une lecture inter-schéma devant *toute* requête authentifiée

```java
return provision.then(chain.filter(exchange));   // @Order(1), donc avant le routage
```

`then` ne « ajoute pas une lecture », il **bloque** : aucun handler n'est atteint avant que la
lecture de `tnt_actor.freelancer_profiles` soit revenue. À `@Order(1)` c'est avant le routage, donc
cela s'applique à `/api/delivery-needs` (module **Go**, celui qui tourne déjà en production), à
`/actuator`, à tout. Tu as écrit au point 8 « pas seulement aux requêtes gofp » sans en tirer la
conséquence : on a mis une dépendance synchrone au schéma `tnt_actor` sur le chemin critique de
l'application entière, pour provisionner deux tables d'un seul module.

### Fait 2 — c'est très probablement la cause de ton point 7, que tu as laissé « non identifiée »

Les ~50 s de blocage au démarrage à froid, puis le HTTP 0 du BFF (timeout 10 s) : à froid le pool
R2DBC est vide, la première requête authentifiée doit ouvrir une connexion vers un schéma jamais
touché, et toutes les suivantes attendent derrière. Ta « requête de chauffe » avant le run final n'a
pas corrigé la cause, elle l'a masquée. En production il n'y a pas de chauffe : c'est un
utilisateur réel qui paie, sur n'importe quelle route. **Tu dois traiter ceci comme une hypothèse à
confirmer ou à réfuter par la mesure, pas comme un fait acquis.**

### Fait 3 — le quota n'est pas « non décrémenté », il est mort, et `register` crée des profils mort-nés

`grep -rn "remainingDeliveries" --include=*.java coreBackend/ | grep -v /test/` : **aucune écriture
nulle part**, sauf deux initialisations — `DEFAULT_INITIAL_QUOTA = 100` dans la projection, et
**`0` dans `FreelancerRegistrationService:181`**. Or `FreelancerQuotaService` refuse la course quand
le quota vaut 0. Donc tout freelancer créé par `POST /api/freelancers/register` ne pourra **jamais**
accepter de course, même après liaison d'un compte Kernel. Ton point 3 est plus grave que tu ne
l'écris : cet endpoint ne crée pas un profil incomplet, il crée un profil mort-né.

## 3. Périmètre — trois chantiers, dans cet ordre

### C-19.1 — Borner le coût du filtre (obligatoire, c'est le cœur du lot)

Objectif : **une requête authentifiée qui n'est pas celle d'un freelancer en attente de projection
ne doit coûter aucun accès base supplémentaire**, et aucune requête ne doit attendre derrière la
projection.

Tu choisis seul la combinaison, mais tu dois adresser les trois axes et justifier chacun :

1. **Restreindre le déclenchement.** Le filtre n'a aucune raison de tourner sur les routes hors
   gofp, ni sur `/actuator`. Décide d'un prédicat de chemin (ou d'un `@Order` / d'un point
   d'accroche différent) et dis pourquoi celui-là.
2. **Supprimer la lecture répétée.** Un freelancer déjà projeté paie aujourd'hui une lecture par
   requête, à vie. Un cache local borné (taille max + TTL, pas une `Map` qui fuit) suffit, mais
   attention : il doit être invalidé ou expirer, sinon un profil supprimé en base reste « projeté »
   en mémoire. Si tu écartes le cache, dis par quoi tu le remplaces.
3. **Ne plus bloquer la requête.** Si la projection doit rester devant le handler pour la
   **première** requête (c'est le seul cas où `then` est justifié : le contrôleur lit juste après),
   alors dis-le explicitement et borne-la par un timeout court plutôt que de laisser la requête
   pendre 50 s. Pour tous les autres cas, elle ne doit pas être sur le chemin critique.

**Mesure exigée, avant / après, sur la même machine :** latence d'une requête authentifiée sur une
route **hors gofp** (`GET /api/delivery-needs` ou équivalent), sur un conteneur **fraîchement
redémarré**, puis à chaud. Donne les chiffres bruts des deux côtés. Un « c'est plus rapide » sans
nombre ne vaut rien. Et dis si la mesure confirme ou réfute l'hypothèse du Fait 2.

### C-19.2 — Le parcours complet sur un compte vierge (prouve le wallet, observe le quota)

Un numéro de téléphone jamais vu, inscription par le chemin mobile réel (BFF
`/v1/kyc/freelancer/submit`), puis souscrire → être élu → démarrer → livrer → être payé.

Ce que ce run doit établir, par la base et non par la lecture du code :

- `billing.wallet_wallets` : **aucune ligne avant**, une ligne après, créée par `getOrCreateWallet`
  au premier crédit. C'est le seul point de ton rapport que tu avais explicitement renvoyé au lot
  suivant. **N'efface aucun wallet existant** — tu l'avais refusé pour une bonne raison (détruire
  un historique de grand livre), cette raison tient toujours. Compte vierge, donc wallet neuf.
- `gofp_freelancers.remaining_deliveries` après une livraison complète : relève la valeur. Si elle
  n'a pas bougé, **ne corrige rien** — voir C-19.4, c'est une décision produit, pas la tienne.
- Les trois `id` identiques, comme en C-18.

Journalise le run dans un fichier **versionné du dépôt** (p. ex. `docs/e2e/`), pas dans un
`scratchpad/` volatil : `scratchpad/e2e-run4.log` que tu citais dans ton rapport C-18 est
introuvable dans le dépôt, il n'existe que dans un répertoire temporaire. Une preuve qu'on ne peut
pas relire n'est pas une preuve.

### C-19.3 — `POST /api/freelancers/register` : supprimer plutôt que rafistoler

Tu l'as « réparé » en C-18 : il persiste maintenant deux tables sur quatre, avec un `coreUserId`
provisoire bidon et un quota à 0. Résultat : il crée des freelancers qui ne peuvent rien faire.
Il n'a aucun test et aucun appelant connu.

**Commence par établir s'il a un appelant** (`grep` dans `tiibntick-bff` et `tiibntick-mobile`, et
dans le core lui-même). Puis :

- **aucun appelant** → supprime l'endpoint et le service, et dis-le dans le rapport. Le chemin
  mobile réel (`POST /api/v1/freelancers` via `FreelancerController`) est le seul qui respecte
  l'invariant des trois tables ; deux portes d'entrée dont une cassée, c'est pire qu'une seule.
- **un appelant existe** → ne supprime pas ; fais-le respecter l'invariant des trois tables, ou
  renvoie une erreur explicite nommant ce qui manque. Jamais un 201 sur un profil mort-né.

### C-19.4 — Ce que tu ne dois PAS décider seul

- **Le barème du quota.** `100` et `0` sont des valeurs sorties d'un seed historique. Qui consomme
  le quota, quand, et que vaut-il à l'inscription : c'est une décision du fondateur. Tu te contentes
  d'**instruire la question** : où le décompte devrait logiquement se brancher (quel service, quelle
  transition d'état), et ce que coûterait chaque option. Pas de code.
- **Le statut KYC `APPROVED` automatique.** Le `TODO` reste un `TODO`.
- **Le store `archunit_store`.** Ne regèle rien. Ton refus de geler 1025 violations pour obtenir du
  vert était la bonne décision ; elle reste valable.

### C-19.5 — Petit durcissement du correctif du resolver

Ton repli vers le tenant système s'applique à **tout** `roleId` introuvable dans le tenant de
l'assignation. C'est le bon correctif pour le symptôme, mais le resolver ne vérifie plus que le
tenant de l'assignation a le droit de porter ce rôle. Borne le repli aux rôles que
`TntRoleDefinitionRegistry` reconnaît comme canoniques, plutôt qu'à n'importe quel `roleId`. Garde
le refus par défaut, garde la priorité au rôle local au tenant, et ajoute le test du cas exclu.

## 4. Ce que je vérifierai moi-même, donc ne le survends pas

J'ai relancé ces contrôles sur C-18 et je les relancerai sur C-19 :

- `curl localhost:8080/actuator/info` → `git.commit.id` et `dirty`.
- `docker logs tnt-core --since Nh | grep -icE 'best-effort|side-effect failed|Access denied'`.
- `grep -rn "INSERT INTO gofp_freelancers|INSERT INTO tnt_delivery_persons" scripts/`.
- l'état SQL réel des trois tables, de `billing.wallet_wallets` et de `billing.wallet_transactions`
  (8 lignes, solde 22800 = 8 × 2850 au moment où j'écris).
- `grep` sur toute écriture de `remainingDeliveries` hors tests.
- l'existence réelle des fichiers de preuve que tu cites.

## 5. Rapport attendu

1. **Verdict** en une ligne par sous-lot (C-19.1 à C-19.5).
2. **Mesures de latence avant / après**, chiffres bruts, à froid et à chaud, sur une route hors
   gofp. Dis si le Fait 2 est confirmé ou réfuté.
3. **Run compte vierge** : état SQL avant (les comptes à zéro) et après, chemin du log versionné.
4. **Ce que ce lot ne prouve pas** — garde cette section, c'est la meilleure chose de ton rapport
   C-18.
5. **Décisions prises sans arbitrage.**
6. **La question du quota instruite**, sans code.
7. Liste des commits (non poussés), et la liste des fichiers modifiés avec les `+/-`.

## 6. Note de séquencement pour ulrich (pas pour toi)

Le core en ligne est `689fabf2`, miroir de `github/main` au 2026-09-30, c'est-à-dire **le lot
C-14**. L'écart local est de **6 commits** = C-15 → C-18, dont le correctif
`LocalReactivePermissionResolver` qui vit dans `foundation/` et touche **tous** les modules.
Le push de ces 6 commits devrait précéder C-19.
