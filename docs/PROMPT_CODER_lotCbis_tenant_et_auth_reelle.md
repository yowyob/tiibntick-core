# Prompt coder — Lot C-bis : rendre le harnais conscient du mode d'authentification

## Règle de conduite (lis-la d'abord)

**Tu procèdes de bout en bout sans jamais demander de confirmation.**
Pas de « do you want to proceed ? », pas de « should I continue ? », pas de pause pour
validation intermédiaire. Tu enchaînes toutes les étapes, tu prends les décisions
techniques toi-même, et tu ne rends la parole qu'à la fin, une seule fois, avec ton
rapport. Si une ambiguïté apparaît : tu choisis l'option la plus défendable, tu la
documentes dans le rapport sous « Décisions prises sans arbitrage », et tu continues.
La seule raison légitime de t'arrêter avant la fin est un blocage matériel que tu ne
peux pas contourner — et dans ce cas tu écris ce que tu as tenté, pas une question.

## Dépôt cible

**Unique dépôt à modifier : `/home/jtk/projets/tiibntick-core`**
Branche de travail : `lotCbis-auth-mode-detection`, créée depuis **`lotC-otp-pickup`**
— surtout PAS depuis `github/main`.

```bash
cd /home/jtk/projets/tiibntick-core
git fetch github
git checkout -b lotCbis-auth-mode-detection lotC-otp-pickup
```

**Pourquoi pas `github/main` :** le lot C n'est pas mergé. Sur `github/main` le script
fait 570 lignes (version lot B) ; sur `lotC-otp-pickup` il en fait 767 et contient le
bloc SETUP-PICKUP, la capture du `pickupOtp` et la preuve exhaustive du blocage de C5.
Partir de `github/main` détruirait tout ce travail sans que rien ne le signale.
Vérifie avant de commencer :

```bash
git show lotC-otp-pickup:scripts/e2e/e2e-freelancer-courier.sh | wc -l   # doit afficher 767
```

Aucune modification dans `/home/jtk/projets/tiibntick-bff` pour ce lot.
Aucune modification de classe Java. Aucune modification de configuration Docker.

## Contexte — ce qui a été mesuré, et que tu dois traiter comme acquis

Le run réel du lot C a produit 5 assertions en échec (C2, C3 x2, C8 x2) et un SETUP
bloqué. **Ces 5 échecs ont une cause unique, déjà isolée et prouvée. Ne les
« corrige » pas un par un.**

Le core local tourne avec `TNT_AUTH_ALLOW_ANONYMOUS=true`. Dans ce mode,
`TntSecurityConfig.devAuthFilter()` injecte un principal synthétique `ROLE_TNT_ADMIN`
sur **chaque** requête et le JWT n'est jamais lu. Preuves mesurées :

```
GET /api/announcements  avec "Bearer ceci.est.nimportequoi"  → 200 + liste
GET /api/announcements  sans en-tête Authorization            → 200 + même liste
GET /api/v1/freelancers/me → actorId 709f0069… (dev-actor-id), tenantId 43427172… (dev-tenant-id)
```

alors que le jeton obtenu via le BFF porte `sub=f69444e3…`, `actor=514b632b…`,
`tid=dbae6615-8f7e-4ef5-9e58-23a6179acf22`.

La requête du core est :

```sql
SELECT * FROM tnt_delivery_announcements
WHERE tenant_id = :tenantId AND status IN ('PUBLISHED','IN_NEGOTIATION')
```

avec `:tenantId` = `43427172` (dev), tandis que le seed écrit dans `dbae6615` (claim
`tid`). Zéro ligne → C2 échoue → C3 et C8 renvoient 404 « Course introuvable » **par
conséquence**. Expérience de contrôle déjà faite : trois annonces identiques semées
dans les trois tenants candidats, seule celle de `43427172` est retournée par le core
et par le BFF.

## Point crucial : le harnais n'a PAS tort

`TntSecurityConfig` ligne ~371, chaîne OAuth2 réelle :

```java
String tenantId = jwt.getClaimAsString("tid");
if (tenantId != null) authorities.add(new SimpleGrantedAuthority("TENANT_" + tenantId));
```

Dans la chaîne réelle, le tenant **est** le claim `tid`. Donc lire `tid` dans le seed
est correct, et le correctif `?tenantId=` du lot B est correct. C'est le bypass qui
impose un autre tenant.

**Interdiction explicite : ne change PAS le seed pour qu'il aille chercher le
dev-tenant-id via `/freelancers/me` afin de faire passer C2 au vert.** Ce serait
adapter le test à une configuration cassée. Le test doit rester aligné sur le
comportement de production et *signaler* l'écart.

## Travail demandé

### 1. Nouvelle étape `C0b — MODE D'AUTHENTIFICATION` (juste après C0)

Trois appels sur `${CORE_DIRECT_URL}` vers un endpoint protégé — utilise
`/api/v1/freelancers/me` :

| appel | en-tête | code attendu si chaîne JWT réelle | code si bypass dev |
|---|---|---|---|
| a | aucun `Authorization` | 401 | 200 |
| b | `Bearer ceci.est.nimportequoi` | 401 | 200 |
| c | jeton valide | 200 | 200 |

Déduis `AUTH_MODE` :

- a=401 **et** b=401 **et** c=200 → `AUTH_MODE="jwt-reel"`, assertion **PASS**
- a=200 **ou** b=200 → `AUTH_MODE="bypass-dev"`

En mode `bypass-dev` : **`warn` très visible, pas `fail`** — le harnais constate une
configuration d'environnement, il ne juge pas le produit. Le message doit dire
exactement :

```
MODE BYPASS DEV DÉTECTÉ (TNT_AUTH_ALLOW_ANONYMOUS=true)
  Le core accepte toute requête comme ROLE_TNT_ADMIN, tenant=<tenant observé>.
  → C1 ne prouve RIEN sur l'authentification du core.
  → Les étapes C2 a C8 ne traversent PAS la chaine d'authentification reelle.
  → Le tenant utilise par le core (<observé>) differe du claim tid du jeton (<tid>).
```

En mode `bypass-dev`, capture aussi le tenant réellement utilisé par le core
(`GET /api/v1/freelancers/me` → `.data.tenantId`) dans `CORE_EFFECTIVE_TENANT`, et le
claim `tid` dans `JWT_TENANT`. Les deux doivent apparaître dans l'encadré de tête du
rapport, côte à côte, même quand ils sont identiques.

### 2. Le seed sème dans le tenant que le core utilise, mais le DÉCLARE

Le seed continue de prendre `JWT_TENANT` comme valeur de référence. Si
`CORE_EFFECTIVE_TENANT` en diffère, il sème dans `CORE_EFFECTIVE_TENANT` **et** émet :

```
warn "SEED : semé dans <effective> (tenant effectif du core) au lieu de <tid> (claim du jeton)
      — écart dû au bypass dev. C2..C8 mesurent le comportement métier, pas le scoping par tenant."
```

et positionne `TENANT_SCOPING_PROVEN=false`. En mode `jwt-reel`, les deux coïncident
et `TENANT_SCOPING_PROVEN=true`.

Ajoute la relecture de contrôle que le lot B avait et que le lot C a perdue :

```bash
SEED_CHECK=$(psql_q "SELECT status FROM tnt_delivery_announcements WHERE id='<id>';")
[ "$SEED_CHECK" = "PUBLISHED" ] || { fail "SEED : relecture → status='$SEED_CHECK'"; exit 1; }
```

**Le seed ne doit jamais déclarer `pass` sans avoir relu la base.** Le lot C
annonçait « missions créées » sans aucune vérification ; c'est ce qui a masqué la
cause réelle pendant tout le run.

### 3. Neutraliser le faux vert de C6

Actuellement C6 passe avec `balance=0`, ce qui est indistinguable d'une requête sur un
tenant vide ou inexistant. Ajoute :

- une assertion que le `tenantId` retourné par la réponse wallet (ou, à défaut, le
  `tenantId` que le BFF a envoyé en paramètre) **égale** `CORE_EFFECTIVE_TENANT` ;
- si `TENANT_SCOPING_PROVEN=false`, C6 est rapporté `⚠️ PASS NON QUALIFIANT` et non
  `✅ PASS`, avec la mention « balance=0 ne distingue pas portefeuille vide de mauvais
  tenant tant que le scoping n'est pas prouvé ».

### 4. Corriger la 409 du SETUP-PICKUP

Une fois le tenant cohérent, relance et **mesure** ce que devient
`POST /api/announcements/:id/subscribe` sur la mission A. Si c'est encore 409,
remonte la cause dans le code (`respondToAnnouncement`) et écris le motif exact dans
le rapport — pas « probablement ». Si la 409 vient d'une réponse déjà existante,
ajoute le nettoyage manquant dans le bloc de nettoyage préventif.

### 5. Restaurer le bit exécutable

Le rebase du lot C a fait passer le script en `100644`.

```bash
chmod +x scripts/e2e/e2e-freelancer-courier.sh
git update-index --chmod=+x scripts/e2e/e2e-freelancer-courier.sh
```

### 6. Vérifier que `lib-auth.sh` n'a rien cassé chez les voisins

`lib-auth.sh` a gagné 44 lignes au lot C et est sourcé par `e2e-freelancer-flow.sh` et
`e2e-presence-loop.sh`. Lance `bash -n` sur les trois, puis **exécute réellement**
`e2e-presence-loop.sh` et rapporte son résultat. Un `bash -n` vert ne prouve rien sur
le comportement.

## Contraintes

- Aucun `|| true` sur une assertion. Uniquement sur les `DELETE` de nettoyage.
- Aucune ligne du tableau final ne doit contenir « attendu », « probable » ou « devrait ».
  Chaque cellule reflète une valeur observée dans ce run.
- Le rapport doit montrer, dans l'encadré de tête : `AUTH_MODE`, `JWT_TENANT`,
  `CORE_EFFECTIVE_TENANT`, `TENANT_SCOPING_PROVEN`, `git.commit.id`.

## Livrable attendu (une seule prise de parole, à la fin)

1. Le diff complet.
2. **La sortie brute intégrale d'un run réel que tu as lancé toi-même**, pas un
   résumé, pas un tableau reconstitué.
3. La sortie brute du run de `e2e-presence-loop.sh`.
4. Une section « Décisions prises sans arbitrage ».
5. Une section « Ce que ce run ne prouve pas » — au minimum le scoping par tenant et
   l'authentification du core tant que le bypass est actif.

Commande de lancement :

```bash
cd /home/jtk/projets/tiibntick-core
TNT_CORE_URL=http://localhost:8080 \
TNT_E2E_BFF_URL=http://localhost:3001 \
TNT_E2E_CORE_DIRECT_URL=http://localhost:8080 \
TNT_POSTGRES=tnt-postgres \
bash scripts/e2e/e2e-freelancer-courier.sh 2>&1 | tee /tmp/lotCbis-run.log
```

Le BFF doit tourner en `CORE_ADAPTER=real` sur le port 3001. Le core local `tnt-core`
doit répondre 200 sur `/actuator/health`. Tu ne modifies **pas**
`TNT_AUTH_ALLOW_ANONYMOUS` dans ce lot — c'est l'objet du lot C-ter.
