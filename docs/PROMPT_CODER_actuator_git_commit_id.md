# Lot infra-1 — rendre le binaire déployé identifiable (`git.commit.id` dans `/actuator/info`)

**Dépôt cible — UN SEUL :** `/home/jtk/projets/tiibntick-core`
**Branche :** `git checkout -b chore/actuator-git-commit-id github/main`
**Aucune ligne de logique métier.** Ce lot ne touche qu'à la construction et à l'observabilité.

---

## Pourquoi ce lot existe

Le core tourne sur `https://tiibntick-core.yowyob.com`, une infrastructure que nous ne
contrôlons pas. Nous poussons sur `main`, quelqu'un d'autre reconstruit et redéploie.

Aujourd'hui, la seule information que l'instance nous donne sur elle-même est :

```
$ curl -s https://tiibntick-core.yowyob.com/actuator/info
{"build":{"artifact":"tnt-bootstrap","time":"2026-09-24T00:15:39.686Z","version":"0.0.1", ...}}
```

`build.time` nous dit **quand** le binaire a été construit. Il ne nous dit pas **depuis quel
commit**. Or c'est exactement la question qui compte : nos lots 21 à 25 corrigent des défauts
d'autorisation dans `tnt-go-freelancer-point-back-core`, et le lot suivant consiste à valider
la chaîne mobile → BFF → core **contre le core réel**. Valider contre un binaire dont on
ignore le contenu ne prouve rien.

`spring-boot-maven-plugin` est déjà configuré dans `tnt-bootstrap/pom.xml` (l.490-497) avec
les goals `repackage` et `build-info` — c'est lui qui produit le bloc `build` ci-dessus.
Il manque son pendant côté git.

---

## Ce qu'il faut obtenir

`/actuator/info` doit publier, en plus du bloc `build` existant, de quoi identifier le commit.

Le mécanisme attendu : le plugin `io.github.git-commit-id:git-commit-id-maven-plugin` génère
un `git.properties` dans les classes compilées, et Spring Boot le découvre tout seul via
`GitInfoContributor` — **aucune classe Java à écrire, aucun `@Bean`, aucun contributeur
maison.** Si tu te retrouves à écrire du Java pour ce lot, tu as pris le mauvais chemin.

Contexte technique du dépôt, pour t'éviter des essais : Java 21, Spring Boot `4.0.6`,
module exécutable unique `tnt-bootstrap`, build multi-modules Maven. Le `pom.xml` racine
centralise les versions de plugins dans `<properties>` (voir l.264 et suivantes) —
**suis cette convention, ne code pas la version en dur dans le module.**

---

## Trois décisions que je te laisse, et que je jugerai

**1. Le niveau de détail exposé.** Spring Boot offre `management.info.git.mode`. Le mode
par défaut et le mode `full` ne publient pas la même chose. Regarde ce que `full` ajoute
réellement, puis choisis — et **justifie ton choix dans le message de commit**. Indice pour
ne pas te tromper : `/actuator/info` est joignable sans authentification sur l'instance de
production (vérifie-le toi-même avec un `curl`), et la question à te poser est donc
« qu'est-ce que je suis en train de publier sur Internet ? ».

**2. L'emplacement de la déclaration.** Le plugin peut être déclaré dans le `pom.xml` du
module `tnt-bootstrap`, ou dans le `<pluginManagement>` du `pom.xml` racine. Les deux
marchent. L'un prépare le jour où un second module deviendra exécutable, l'autre garde la
configuration près de son unique usage. Tranche, et dis pourquoi.

**3. Le comportement quand le dossier `.git` est absent.** C'est le piège classique de ce
plugin : dans une image Docker construite en plusieurs étapes, ou depuis une archive
source, `.git` n'existe pas et le plugin échoue — et il fait échouer tout le build. Le
`Dockerfile` est dans `tnt-bootstrap/`, **lis-le** et détermine si le cas se présente chez
nous. Le plugin a une option pour ça ; choisis entre « échouer bruyamment » et « continuer
sans l'information », et justifie. Une information de traçabilité qui casse la construction
de tout le monde est un mauvais compromis ; une information silencieusement absente aussi.
Tu dois savoir laquelle des deux tu préfères et pourquoi.

---

## Ce que je vérifierai moi-même, avant de dire oui

Je ne juge pas sur ton compte rendu. Je rejoue les commandes. Concrètement :

- Le build passe : `./mvnw -q -pl tnt-bootstrap -am -DskipTests package` (ou l'équivalent
  exact de ce dépôt — dis-moi lequel tu as utilisé).
- `git.properties` existe dans les classes compilées après build, et **n'est pas versionné**
  (il est produit, pas écrit à la main — vérifie qu'il tombe sous un chemin déjà ignoré).
- L'application démarrée en local répond avec un bloc `git` :
  `curl -s localhost:8080/actuator/info | grep -o '"commit":{[^}]*}'` — colle la sortie
  réelle dans le commit, pas une reconstitution.
- Le SHA renvoyé **correspond** à `git rev-parse HEAD` au moment du build. Un champ présent
  mais faux serait pire que pas de champ du tout.
- La version du plugin est **figée** dans les properties, pas flottante. Un build qui n'est
  pas reproductible n'a rien à faire dans un lot dont le but est la traçabilité.
- Aucune classe Java ajoutée.

## Format de rendu

Un seul commit, message en français, corps expliquant **les trois décisions** et contenant
**les sorties réelles** (build + `curl`). Pas de commit vide : le lot 25.4 a été livré en
`--allow-empty` et la preuve n'existait que dans ton terminal.

Et avant d'annoncer « livré » : le lot A t'a coûté un aller-retour parce que `vitest` ne
vérifie pas les types. Ici l'équivalent est que **`mvn` compile sans démarrer l'application**.
Un build vert ne prouve pas que l'endpoint répond. Démarre-la.

Ne pousse pas sur `main`. Pousse ta branche, je relis, on fusionne en `--ff-only`.
