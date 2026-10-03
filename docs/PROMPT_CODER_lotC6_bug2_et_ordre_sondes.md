# Lot C-6 — Tuer Bug #2 avec la preuve du nom de contrainte

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
git checkout lotC5-run-complet
git checkout -b lotC6-bug2-afterschema
wc -l scripts/e2e/e2e-freelancer-courier.sh   # doit afficher 1167
```

Si `wc -l` n'affiche pas 1167, arrête-toi et dis-le dans le rapport.

---

## Contexte : ce que ton lot C-5 a prouvé, et ce qu'il n'a pas prouvé

J'ai vérifié ton rapport moi-même. Ce qui est solide : le harnais est corrigé,
le self-test de la garde d'isolation est réel et en place (lignes 221-241), et
tu as **respecté** l'interdiction de SQL manuel — tout ce que tu avais bricolé à
la main est encodé dans le script, idempotent (`ON CONFLICT DO UPDATE`) avec
relecture `assert_eq`, y compris `gross_floor` et `total_seat_number`. Tes
quatre découvertes d'énumérations (`FreelancerStatus.APPROVED` et non `ACTIVE`,
`LogisticsType.MOTORBIKE` et non `MOTORCYCLE`, les primitifs qui explosent en
NPE, `id = actor_profile_id`) sont du vrai travail de mesure. Rien à redire.

Deux choses ne tiennent pas.

### 1. Ton attribution de Bug #2 repose sur la mauvaise preuve

Tu as cité un `WARN` de violation de clé étrangère
(`fk_deliveries_announcement`) pour justifier un `DuplicateKeyException`. Ce
sont **deux défauts différents sur deux tables différentes** : la FK concerne le
miroir GOFP `deliveries → announcements`, le 409 vient d'ailleurs. Tu as
collapsé deux problèmes en un.

La bonne preuve, tu ne l'as pas utilisée. Elle est dans
`coreBackend/tnt-go-freelancer-point-back-core/…/GlobalExceptionHandler.java`
lignes 68-85 : le message `"A resource with this information already exists"`
est émis **uniquement** par le handler de `DuplicateKeyException` (ligne 77).
Le handler de `DuplicateResourceException` (ligne 68-71) renvoie
`ex.getMessage()`, donc un message métier spécifique. Ton 409 portait le message
générique ⇒ c'est bien une violation de contrainte d'unicité en base, pas une
règle métier. Ça, c'est établi.

Ce qui n'est **pas** établi : **quelle contrainte**. Et tu ne pouvais pas le
savoir, parce que ce handler n'a jamais journalisé `ex.getMessage()`. Tant que
le nom de la contrainte est inconnu, « c'est Bug #2 » reste l'hypothèse la plus
probable, pas une preuve. On ne corrige pas un défaut de persistance sur une
hypothèse.

### 2. `probe-c = 200` n'est pas un résultat de run propre

`C0b` s'exécute autour de la ligne 402, le seed RBAC à la ligne 501. La sonde
part donc **avant** que la permission existe. Ton `200` du run 6 vient de l'état
laissé par les runs précédents, pas du script. Tu l'as écrit en note de bas de
page, et le script gère explicitement le `403` (lignes 441-448) — donc tu es
honnête. Mais le tableau affiche `✅` pour une mesure que le script ne
reproduit pas.

---

## Tâche 1 — Rendre `DuplicateKeyException` diagnosticable

Dans `GlobalExceptionHandler.handleDuplicateKeyException`, ajoute une
journalisation au niveau `WARN` (ou `ERROR`) qui imprime `ex.getMessage()` en
entier, avant de construire la réponse. Le corps HTTP renvoyé au client ne
change **pas** — on ne fuite pas un nom de contrainte vers l'extérieur — mais le
log doit le contenir.

Fais la même chose pour le `onErrorResume` de
`AnnouncementApplicationService.softMirrorDeliveryAndInitOtp` s'il avale encore
l'exception sans la nommer : une exception avalée silencieusement est la raison
pour laquelle ce lot existe.

## Tâche 2 — Reproduire le 409 et nommer la contrainte

Repars d'un état propre (le cleanup du script, pas un `DELETE` improvisé),
relance jusqu'à `SETUP assign`, et récupère dans les logs du conteneur le
message complet du `DuplicateKeyException`. Le rapport doit contenir :

- la ligne de log brute, avec le **nom exact de la contrainte** et la **table** ;
- la définition de cette contrainte
  (`SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint WHERE …`) ;
- la ligne de code exacte qui déclenche le second `INSERT`.

Note utile : il existe **deux** tables candidates, `deliveries` (miroir GOFP) et
`tnt_deliveries` (`tnt-delivery-core`). Ne suppose pas laquelle, mesure-le.

## Tâche 3 — Corriger Bug #2, seulement si la tâche 2 le confirme

`TntEntityIsNewCallback` (`tnt-bootstrap/…/config/TntEntityIsNewCallback.java`)
implémente `AfterConvertCallback`, qui ne se déclenche qu'à la **lecture**.
`isNew` reste donc `true` après un `save()`. J'ai vérifié la classe, ce point est
factuel.

**Si et seulement si** la tâche 2 confirme que la contrainte violée est la clé
primaire de l'entité sauvegardée deux fois : corrige le mécanisme pour que
`markNotNew()` soit appelé après chaque sauvegarde. Attention, c'est un callback
**global** — toute entité `TntPersistableEntity` du projet passe par là. Donc :

- tu n'enlèves pas `AfterConvertCallback`, tu **ajoutes** le comportement après
  sauvegarde ;
- tu écris un test qui prouve le comportement des **deux** côtés : après une
  lecture, `isNew` est `false` ; après un `save()`, `isNew` est `false` aussi ;
- tu cherches et tu signales tout endroit du code qui **dépendait** de l'ancien
  comportement (un `save()` appelé deux fois volontairement pour forcer un
  INSERT, par exemple). S'il en existe, tu le dis dans le rapport plutôt que de
  le casser en silence.

Si la tâche 2 **infirme** l'hypothèse Bug #2, tu ne touches pas au callback, tu
corriges la vraie cause, et tu expliques dans le rapport pourquoi Bug #2 était
une fausse piste. Cette issue est parfaitement acceptable.

## Tâche 4 — Remettre la sonde `probe-c` dans le bon ordre

Deux options, tu tranches et tu justifies :

- déplacer la sonde `probe-c` **après** le seed RBAC, pour qu'elle mesure ce
  qu'elle prétend mesurer ;
- ou la garder où elle est et faire du `403` la valeur **attendue** en run
  propre, le `200` devenant l'anomalie à signaler.

Dans les deux cas, le tableau final doit afficher ce qu'un run propre produit,
pas ce qu'un run contaminé produit. Prouve-le en lançant le script **deux fois
de suite** depuis un état nettoyé et en montrant que `probe-c` donne la même
valeur aux deux passages.

## Tâche 5 — Run complet

Relance le parcours en entier. L'objectif de ce lot est que **`SETUP assign` et
`C4` passent au vert**. Même exigence que précédemment pour chaque étape : code
HTTP, corps de réponse, et en cas d'échec une cause **prouvée** — adossée à un
log, une requête SQL ou une sonde dont tu montres la sortie.

`C5` reste hors périmètre (`deliveryOtp` push-only, lot séparé).

## Interdictions

- Ne réactive jamais le bypass anonyme (`TNT_AUTH_ALLOW_ANONYMOUS=false`).
- Ne fabrique jamais un jeton à la main avec un claim `roles`.
- Aucun `INSERT`/`UPDATE`/`DELETE` manuel hors du script. Tu as bien fait au lot
  C-5 en encodant tes correctifs dans le seed — continue exactement comme ça.
- Ne touche à aucun `@PreAuthorize` dans ce lot.
- Discipline OTP inchangée : un seul appel `/v1/auth/otp/request` par run, jeton
  réutilisé pendant ses 900 s, rate-limit kernel de 600 s par destinataire.
- Ne déclare **aucune** cause « prouvée » sans la sortie de commande qui la
  prouve. C'est le seul reproche de fond du lot C-5 ; c'est le point sur lequel
  ce lot sera jugé.

## Rapport final

Une seule prise de parole, à la fin :

1. Nom exact de la contrainte violée, table, définition, ligne de code du second
   `INSERT`.
2. Verdict sur Bug #2 : confirmé ou infirmé, et pourquoi.
3. Si corrigé : le test des deux côtés (lecture et sauvegarde), et la liste des
   endroits qui dépendaient de l'ancien comportement.
4. Valeur de `probe-c` sur deux runs propres consécutifs.
5. Tableau des 12 étapes : HTTP / corps / verdict / cause prouvée si échec.
6. Décisions tranchées seul, avec justification.
7. Ce parcours est-il déployable en production tel quel ? Si non, la liste de ce
   qui manque, avec pour chaque élément la preuve qu'il manque.
