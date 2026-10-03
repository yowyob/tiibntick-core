# Lot B — prouver la boucle coursier Freelancer **contre le core réel**

**Dépôt cible — UN SEUL :** `/home/jtk/projets/tiibntick-core`
**Branche :** `git checkout -b lotB-e2e-coursier-reel github/main`
**Aucune ligne de code applicatif.** Ce lot ne produit qu'un harnais de preuve.

---

## Pourquoi ce lot existe

On prépare un APK Freelancer testable sur Android. Tout le code est écrit et vert en local.
Ce qui manque n'est pas du code : c'est **la preuve que la chaîne fonctionne en réel**.

Aujourd'hui, `scripts/e2e/e2e-freelancer-flow.sh` couvre le cycle de vie *administratif* du
freelancer (MF0 à MF8 : validation, suspension, révocation). La boucle que le testeur va
réellement parcourir n'est couverte **nulle part** :

```
voir les missions → accepter → récupérer le colis → livrer → consulter le portefeuille
```

`grep -c "jobs/available\|/accept\|/pickup\|/wallet" scripts/e2e/*.sh` renvoie **0**.

Un APK dont la boucle principale n'a jamais été exécutée contre le vrai backend n'est pas
un APK testable, c'est un pari.

## Le contexte a changé ce matin, lis-le avant de coder

Le core déployé expose désormais le commit dont il est construit :

```
$ curl -s https://tiibntick-core.yowyob.com/actuator/info
"git": {"branch":"main","commit":{"id":"70d7435","time":"2026-09-24T01:25:29Z"}}
```

Attention au piège : ce SHA **n'est pas un de nos commits**. Yowyob miroite notre dépôt dans
`github.com/yowyob/tiibntick-core` et construit depuis là ; chaque synchronisation crée un
commit `mirror: sync ...` avec un SHA différent du nôtre. Pour savoir *ce que* contient le
binaire en ligne, il faut résoudre ce SHA côté miroir, pas chez nous.

Ça a une conséquence directe sur ce lot : **le harnais doit enregistrer, à chaque exécution,
le SHA du binaire qu'il a interrogé.** Un rapport E2E sans cette information ne vaut rien —
on ne saurait pas six heures plus tard contre quel code il a tourné. C'est exactement l'erreur
du lot 24, à l'échelle du déploiement.

---

## B1 — Ce que le harnais doit prouver

Nouveau script : `scripts/e2e/e2e-freelancer-courier.sh`.

Cibles pilotées par l'environnement, **jamais codées en dur** — suis la convention déjà en
place dans `e2e-freelancer-flow.sh` (`CORE_URL="${TNT_CORE_URL:-...}"`,
`BFF_URL="${TNT_E2E_BFF_URL:-...}"`). Le script doit pouvoir viser indifféremment un core
local et le core de Yowyob, sans être modifié.

Étapes attendues, chacune avec une assertion sur **le statut ET le corps** :

| # | Étape | Attendu |
|---|---|---|
| C0 | Empreinte du binaire | `/actuator/info` → `git.commit.id` relevé et affiché en tête de rapport |
| C1 | Authentification freelancer | jeton obtenu via le BFF |
| C2 | `GET /v1/freelancer/jobs/available` | 200 + tableau ; la mission semée est présente |
| C3 | `POST /v1/freelancer/jobs/:id/accept` | 200 + statut passé à `accepted` |
| C4 | `POST /v1/freelancer/jobs/:id/pickup` | 200 + statut `picked_up` |
| C5 | `POST /v1/freelancer/jobs/:id/deliver` | 200 + statut `delivered` |
| C6 | `GET /v1/freelancer/wallet` | 200 + `withdraw_available` **présent** dans le JSON |
| C7 | `POST /v1/freelancer/wallet/withdraw` | 503 + code `FEATURE_UNAVAILABLE` |
| C8 | Double `accept` sur la même mission | refus — la garde anti-retry doit mordre |

C6 n'est pas décoratif : le lot A a livré un adaptateur réel qui omettait
`withdraw_available`, et le mobile a *paru* fonctionner parce qu'il retombait sur `undefined`.
Cette assertion est le filet qui aurait attrapé le défaut.

C8 non plus : une boucle qui ne passe qu'une fois ne prouve pas qu'elle refuse la seconde.

## B2 — Les trois choses que tu vas devoir élucider, sans les inventer

**1. Le semis de la mission.** C2 suppose qu'une mission existe et est visible du freelancer.
Ça demande une identité *expéditeur* : créer un `delivery-need`, puis assigner le freelancer.
Deux identités distinctes dans un même script. Regarde comment `e2e-presence-loop.sh` et
`lib-auth.sh` s'y prennent et **réutilise** — `tnt_otp_flow` positionne `TOKEN`, donc deux
appels successifs s'écrasent : c'est le premier problème concret à résoudre proprement.

**2. L'OTP de récupération et de livraison.** C4 et C5 exigent un OTP. Le core expose
`POST /api/v1/deliveries/{id}/init-otp` (voir `GofpDeliveryController` l.93). **Je ne sais pas**
si le code est récupérable par l'appelant en dehors du SMS, ni si le mode `PREVIEW` du kernel
le surface comme il l'a fait pour l'OTP de connexion. C'est à toi de le déterminer, en lisant
le code du core, pas en devinant.

Et voici l'interdit absolu de ce lot : **si l'OTP n'est pas récupérable, tu ne le fabriques
pas.** Tu ne mets pas `000000`, tu ne contournes pas la vérification, tu ne fais pas passer
C4 par un chemin de test. Tu marques C4 et C5 **BLOQUÉ**, tu écris précisément pourquoi avec
les lignes de code à l'appui, et tu livres C0-C3 et C6-C8 verts. Un rapport honnête à 6/9 vaut
infiniment mieux qu'un 9/9 dont trois lignes sont mises en scène — c'est littéralement l'erreur
du lot 24, où le résolveur de test fabriquait lui-même la condition qu'il prétendait vérifier.

**3. Ce que le harnais ne peut plus faire contre un core distant.** `e2e-freelancer-flow.sh`
s'appuie sur `psql` et sur les logs du conteneur Docker. Contre `tiibntick-core.yowyob.com`,
tu n'as **ni base ni logs** : uniquement HTTP. Certaines assertions du script existant ne sont
donc pas transposables. Identifie-les, et pour chacune, soit tu trouves un équivalent
observable par l'API, soit tu l'écartes explicitement en disant ce qu'on perd. Ne remplace pas
une assertion en base par une assertion vide.

---

## Ce que je vérifierai moi-même, avant de dire oui

Je ne juge pas sur ton compte rendu. Je relance le script et je relis le diff.

- Le script tourne depuis un poste propre, avec pour seule configuration des variables
  d'environnement. Donne-moi la ligne de commande exacte.
- **Il échoue si on le pointe sur le BFF en mode `mock`.** Un harnais E2E qui passe contre un
  mock ne teste rien. Prouve-le : deux exécutions, une réelle verte, une mock rouge.
- Le SHA du core interrogé apparaît dans la sortie.
- `grep -nE "\|\| true|set \+e|# skip|exit 0" scripts/e2e/e2e-freelancer-courier.sh` → rien
  qui masque un échec.
- Aucune valeur attendue n'est dérivée de la réponse elle-même. Si le script lit `status` dans
  la réponse puis assied `assert_eq "$status" "$status"`, il ne teste rien.
- Les étapes bloquées sont marquées BLOQUÉ, pas PASS, pas silencieusement absentes.

## Format de rendu

Un commit, message en français, corps contenant **la sortie réelle complète** du run contre
le core de Yowyob (avec le SHA), **la sortie du run contre le mock qui échoue**, et pour
chaque étape bloquée la raison avec référence au fichier et à la ligne du core.

Pas de commit vide, pas de preuve qui n'existe que dans ton terminal : le lot 25.4 a été livré
en `--allow-empty` et il a fallu tout refaire.

Ne pousse pas sur `main`. Pousse ta branche, je relis, on fusionne en `--ff-only`.
