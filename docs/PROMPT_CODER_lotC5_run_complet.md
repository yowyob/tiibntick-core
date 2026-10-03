# Lot C-5 — Run complet du parcours coursier, sans blocage kernel

## Règle d'autonomie (à lire avant tout)

Tu travailles en autonomie totale du début à la fin. Tu ne me poses **aucune**
question, tu ne demandes **aucune** confirmation, tu n'écris **jamais** « do you
want me to proceed », « souhaitez-vous que je continue », ni aucune variante.
Tu ne prends la parole qu'**une seule fois** : à la fin, pour rendre le rapport.
Si tu rencontres un choix technique, tu tranches toi-même, tu appliques, et tu
justifies ta décision dans le rapport. Si tu rencontres un blocage réel, tu
l'écris dans le rapport avec la preuve expérimentale — tu ne t'arrêtes pas pour
me le demander.

## Dépôt cible

Tout se passe dans **`/home/jtk/projets/tiibntick-core`**.

## Branche

```bash
cd /home/jtk/projets/tiibntick-core
git checkout lotC4-rbac-role-freelancer
git checkout -b lotC5-run-complet
wc -l scripts/e2e/e2e-freelancer-courier.sh   # doit afficher 1052
```

Si `wc -l` n'affiche pas 1052, **arrête-toi** et dis-le dans le rapport : tu n'es
pas parti de la bonne base.

---

## Contexte : le blocage kernel du lot C-4 n'existait pas

Ton rapport de lot C-4 concluait qu'il fallait obtenir `TNT_KERNEL_API_KEY`
auprès de TSAFACK Savio. C'était un faux diagnostic. Les faits mesurés :

1. La clé existe déjà dans le dépôt, à
   **`tnt-bootstrap/docker-compose.override.yml:5`**. Ce fichier est
   **gitignoré** (`.gitignore:284`) — c'est pour ça qu'il est invisible quand on
   raisonne depuis `git`. Ne le cherche pas avec `git log`, ouvre-le.
2. La valeur envoyée n'était **pas** `changeme-kernel-api-key`. Le défaut du
   `@Value` de `KernelBridgeConfig:84` est écrasé par
   `application.yml:644` → `api-key: ${TNT_KERNEL_API_KEY:}`, donc la valeur
   réelle était la **chaîne vide**. D'où le `401 Not Authenticated`.
3. Le conteneur `tnt-core` avait été démarré avec `-f docker-compose.yml`
   **seul**, ce qui supprime le chargement automatique de l'override.
   `docker inspect tnt-core` ne montrait aucune variable `TNT_KERNEL_*`.

Le core a été redémarré avec l'override. État vérifié **avant** que tu commences :

```
docker exec tnt-core printenv | grep TNT_KERNEL
  TNT_KERNEL_CLIENT_ID=tibntick-backend
  TNT_KERNEL_API_KEY=v2KC…

POST http://localhost:3001/v1/auth/otp/request  {"phoneNumber":"+237677889901"}
  → HTTP 200, challengeId émis par kernel-core
```

La chaîne BFF local → core local → kernel fonctionne. Tu n'attends personne.

### Conséquence : ne casse pas ça

Si tu dois redémarrer le core, la seule commande autorisée est, depuis
`tnt-bootstrap` :

```bash
docker compose -f docker-compose.yml -f docker-compose.override.yml up -d tiibntick-core
```

`docker compose up -d tiibntick-core` tout court est **interdit** dans ce lot :
selon le contexte il peut réappliquer le compose seul et te faire perdre la clé.
Tu attends ensuite que `/actuator/health` réponde `"status":"UP"` avant de
lancer quoi que ce soit — compte environ 60 secondes.

---

## Tâche 1 — Fermer le faux vert résiduel de `TENANT_SCOPING_PROVEN`

`scripts/e2e/e2e-freelancer-courier.sh`, bloc « Test d'isolation tenant »
(≈ lignes 696-705). Le code actuel :

```bash
if [ "$C2_HTTP" = "200" ] && [ "$IS_ARRAY" = "yes" ] && [ -n "$OTHER_ANN_ID" ]; then
  if echo "$C2_BODY" | jq -e --arg id "$OTHER_ANN_ID" 'any(.[]; .id == $id)' ...
  else
    pass "C2 isolation : … cloisonnement prouvé"
    TENANT_SCOPING_PROVEN="true"
```

Le défaut : une liste **vide** (`[]`) satisfait `IS_ARRAY=yes`, l'annonce de
l'autre tenant est donc « absente », et le cloisonnement est déclaré prouvé
alors qu'on n'a rien reçu du tout. Une absence de données n'est pas une preuve
d'exclusion.

Corrige en exigeant que la liste contienne **au moins** l'annonce du bon tenant
avant de conclure — c'est-à-dire ajoute la condition `C2_MISSION_B_OK = true` à
la garde. Si la liste est vide, `TENANT_SCOPING_PROVEN` doit rester `unknown` et
le script doit émettre un `warn` explicite disant pourquoi l'isolation n'a pas
pu être testée.

Vérifie ta correction **sans dépendre du run** : écris un cas de contrôle (une
fonction de test dans le script, ou un run avec un `C2_BODY` forcé à `[]`) qui
prouve que `TENANT_SCOPING_PROVEN` vaut bien `unknown` sur liste vide. Le
rapport doit montrer la sortie de ce contrôle.

## Tâche 2 — Substantier ou retirer la décision #3 du lot C-4

Ton rapport de lot C-4 annonçait avoir corrigé un UUID invalide (« 11 caractères
dans le dernier groupe au lieu de 12 »), décrit comme un « bug injecté dans la
session précédente ». J'ai cherché :

- `git diff 7dd26f5e..7748c59e` ne supprime **aucun** UUID ;
- aucun UUID malformé ne subsiste dans `e2e-freelancer-courier.sh` ;
- `git status` est propre, donc rien n'a été corrigé hors commit.

Soit tu produis la preuve (fichier, ligne, `git show` qui la montre), soit tu
écris dans le rapport que la décision #3 était erronée et tu la retires. Les
deux réponses sont acceptables ; une claim non vérifiable ne l'est pas.

## Tâche 3 — Préflight OTP en tête de script

Ajoute en tête de `e2e-freelancer-courier.sh`, **avant** toute autre étape, un
préflight qui appelle une fois `POST ${BFF_URL}/v1/auth/otp/request` et qui, en
cas de `401`, affiche un message d'erreur nommant la cause exacte et la commande
de correction (l'override compose ci-dessus), puis `exit 1`.

But : que le prochain 401 kernel soit diagnostiqué par le script en deux
secondes au lieu de coûter un lot entier.

## Tâche 4 — Discipline de rate-limit sur l'OTP

Le kernel applique un rate-limit par destinataire : des appels répétés sur le
même numéro renvoient `429`, remonté par le BFF en
`500 {"code":"INTERNAL","message":"Kernel OTP indisponible (HTTP 429)"}`.

Règles pour ce lot :

- **Un seul** appel `/v1/auth/otp/request` par run. Jamais de boucle de retry
  sur cet endpoint.
- Le jeton vit **900 secondes**. Tu l'exportes dans `TNT_E2E_TOKEN` et tu le
  réutilises pour tous les appels du run.
- Si le run dépasse 900 s, tu réauthentifies **une** fois, et tu notes dans le
  rapport combien de fois tu as dû le faire.
- Si tu prends un `429`, tu attends et tu le dis dans le rapport — tu ne
  contournes pas en changeant de numéro au milieu d'un run (ça casserait
  l'identité de l'acteur testé).

## Tâche 5 — Le run complet

Lance `scripts/e2e/e2e-freelancer-courier.sh` en entier et va au bout.
Pour **chaque** étape (C0b probe-a/b/c, C1, SETUP subscribe, SETUP assign, C2,
C3, C4, C5, C6, C7, C8) le rapport doit donner :

- le **code HTTP** obtenu ;
- le **corps de réponse** (tronqué à 300 caractères si nécessaire) ;
- en cas d'échec, la **cause prouvée** — pas une hypothèse. Une cause prouvée
  est adossée à un log conteneur, une requête SQL, ou une sonde HTTP que tu as
  faite et dont tu montres la sortie.

Rappels sur ce que tu sais déjà et qui ne doit pas te surprendre :

- Le rôle `FREELANCER` du **tenant système**
  (`00000000-0000-0000-0000-000000000001`) n'a **pas** `freelancer:read` en base
  — je l'ai vérifié. Ton ajout dans `TntRoleDefinition` ne vaut que pour les
  **nouveaux** déploiements, parce que `TntRoleInitializationService.upsertIfAbsent()`
  ne met jamais à jour un rôle existant. Ce n'est **pas** un problème pour ce
  run : le rôle que le script sème est `E2E_COURIER_C4` sur le tenant
  `dbae6615-8f7e-4ef5-9e58-23a6179acf22`, et il porte bien `freelancer:read`
  (vérifié en base, avec son assignation à l'acteur `f69444e3…`). Ne touche pas
  au rôle du tenant système dans ce lot.
- `Bug #2` (`TntEntityIsNewCallback` est un `AfterConvertCallback`, donc `isNew`
  reste `true` après `save()`) bloque C4. Si C4 échoue là-dessus, dis-le et
  passe à la suite ; sa correction est un lot à part.
- `C5` attend un OTP en mode `PREVIEW_ONLY` — le kernel confirme bien
  `"deliveryMode":"PREVIEW_ONLY"` sur les challenges émis.

## Interdictions

- Ne réactive **jamais** le bypass anonyme
  (`TNT_AUTH_ALLOW_ANONYMOUS` doit rester `false`).
- Ne fabrique **jamais** un jeton à la main avec un claim `roles`.
- Ne touche à **aucun** `@PreAuthorize` dans ce lot — les trois
  `hasRole('FREELANCER')` restants de `FreelancerController` (lignes 168, 180,
  194) ne sont pas sur le chemin du BFF : j'ai vérifié, la boucle de présence
  passe par `PATCH /api/freelancers/{id}/location`
  (`realCoreGo.ts:140`, `FreelancerLocationController`), pas par
  `/api/v1/freelancers/me/location`. Ils seront traités plus tard.
- Aucun `INSERT` ni `UPDATE` manuel en base pour fabriquer un profil freelancer
  ou une permission qui manquerait. Si une donnée manque, le script doit la
  semer lui-même, de façon idempotente, avec relecture obligatoire.
- Ne déclare **aucune** étape verte sans le code HTTP et le corps à l'appui.

## Rapport final

Une seule prise de parole, à la fin. Structure attendue :

1. Tableau des 12 étapes : étape / HTTP / verdict / cause prouvée si échec.
2. Valeurs finales de `AUTH_MODE`, `AUTH_CHAIN_ACTIVE`, `TENANT_SCOPING_PROVEN`,
   et pourquoi chacune vaut ce qu'elle vaut.
3. Sortie du cas de contrôle de la tâche 1 (liste vide → `unknown`).
4. Réponse à la tâche 2 (preuve de l'UUID, ou retrait de la claim).
5. Nombre de réauthentifications OTP, et tout `429` rencontré.
6. Décisions techniques que tu as tranchées seul, avec justification.
7. Réponse honnête à : **ce parcours coursier est-il déployable en production
   tel quel ?** Si non, la liste exacte de ce qui manque, avec pour chaque
   élément la preuve qu'il manque.
