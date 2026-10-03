# Note à l'équipe qui gère le kernel / le déploiement du core

**De :** équipe TiiBnTick mobile (Ulrich)
**Objet :** chaîne de redéploiement du core — constat, et une demande pour la suite
**Date :** 2026-09-24

---

## 1. Où on en est

Nous poussons régulièrement sur `main` de `tiibntick-org/TiiBnTick-core`. Les derniers
commits (lots 21 à 25) corrigent des défauts d'autorisation dans le module
`tnt-go-freelancer-point-back-core` : fermeture d'un *fail-open* où un appelant dont
l'identifiant n'était pas résolu était tout de même considéré comme authentifié,
suppression d'un IDOR sur `getAllDeliveryNeeds`, unification du 404 entre REST et SSE,
et correction de l'ordre de commit de la réponse SSE — le contrôle d'accès s'exécutait
après l'envoi des en-têtes, donc un refus partait en `200` sur une vraie socket.

Dernier commit poussé sur notre `main` : **`56e3124c`**.

## 2. Ce qu'on a ajouté pour pouvoir vérifier

`build.time` nous disait *quand* le binaire avait été construit, pas **depuis quel
commit**. Nous ne pouvions donc pas vérifier que l'instance en ligne contenait nos
correctifs.

Nous avons réglé ça nous-mêmes (commit `56e3124c`, 34 lignes, aucune classe Java touchée) :
`tnt-bootstrap/pom.xml` génère désormais un `git.properties` via
`git-commit-id-maven-plugin`, que Spring Boot expose automatiquement dans
`/actuator/info` sous `git.commit.id` et `git.branch`. Nous sommes restés sur le mode
`simple`, volontairement : le mode `full` publierait l'e-mail de l'auteur, le nom de la
machine de build et l'URL du dépôt, ce qui n'a rien à faire sur un endpoint accessible.

Rien à faire de votre côté. Nous le signalons simplement pour que le champ ne vous
surprenne pas.

## 3. Ce que nous avons observé — la chaîne fonctionne

Nous avons mesuré le cycle complet le 24/09 et il est entièrement automatique :

```
push sur tiibntick-org/TiiBnTick-core@main
   |
commit miroir dans yowyob/tiibntick-core    01:25:29Z
   |
build du binaire                            01:27:24Z   (~2 min)
   |
bascule (courte fenetre de 404 sur le proxy)
   |
instance en ligne qui repond 200            ~01:48Z     (~20 min bout en bout)
```

Confirmé par `/actuator/info`, qui renvoie maintenant
`{"branch":"main","commit":{"id":"70d7435","time":"2026-09-24T01:25:29Z"}}`. Nos
correctifs sont donc bien en production.

**Une subtilité qu'il vaut la peine d'écrire noir sur blanc :** `70d7435` n'existe pas
dans notre dépôt. Le miroir rejoue nos commits sous de nouveaux SHA
(`mirror: sync ...@main`, auteur `yowyob-mirror`). Nous avons vérifié que son diff est
exactement nos 3 fichiers / 34 insertions, donc le contenu est identique — mais le SHA
affiché par `/actuator/info` n'est jamais comparable directement au nôtre. Nous avons
ajouté `yowyob/tiibntick-core` en remote de lecture chez nous pour faire la résolution.
Si vous pouviez propager le SHA d'origine (`git.commit.id` du dépôt amont plutôt que
celui du miroir), la vérification deviendrait immédiate pour tout le monde — mais ce
n'est pas bloquant.

## 4. La demande qui reste

**Un environnement de recette que nous pourrions redéployer nous-mêmes.**
Même base de code, même profil, mais destiné à nos validations bout en bout, avant que
l'instance de référence ne soit touchée. Deux raisons :

- les prochains lots toucheront encore le core, et nous préférons ne pas consommer une
  bascule de l'instance de référence à chaque itération ;
- la fenêtre de 404 pendant la bascule tombe en plein milieu de nos campagnes de test et
  produit des échecs qui ne sont pas les nôtres. Si la bascule peut devenir progressive
  (démarrer le nouveau conteneur, attendre `/actuator/health` en `UP`, puis basculer le
  proxy), le problème disparaît aussi côté production.

Ce n'est pas urgent. La chaîne actuelle nous débloque pour cette semaine.

## 5. Pourquoi ça compte maintenant

Nous préparons un APK de test du module Freelancer. Le dernier verrou est une validation
bout en bout mobile → BFF → core **contre le core réel**. Valider contre un binaire dont
nous ignorons le contenu ne prouve rien, et c'est précisément le genre d'angle mort qui
nous a déjà coûté plusieurs reprises.

Merci d'avance,
Ulrich — équipe mobile TiiBnTick
