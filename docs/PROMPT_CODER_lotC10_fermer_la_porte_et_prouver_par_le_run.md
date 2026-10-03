# Lot C-10 — Fermer la porte ouverte sur le paiement, puis PROUVER par un run réel

**Dépôt cible : `/home/jtk/projets/tiibntick-core`** (branche de départ : `lotC9-paiement-reel`, HEAD `9462bd26`).

## Règle d'autonomie — à lire avant tout

Tu exécutes ce lot **de bout en bout, sans jamais me demander la permission de continuer**. Pas de
« souhaites-tu que je procède ? », pas de « veux-tu que j'applique ce correctif ? », pas de pause
intermédiaire. Tu prends **une seule fois la parole : à la fin**, avec le rapport complet. Si tu
rencontres un obstacle, tu le contournes ou tu le documentes dans le rapport final — tu ne
t'arrêtes pas pour demander.

## Règle de preuve — à lire deux fois

Ce lot est né de trois défauts de vérification consécutifs. Les règles suivantes sont non négociables.

1. **Aucune assertion verte ne peut reposer sur une erreur avalée.**
2. **Ne cite comme preuve que des sorties de commandes réelles issues du chemin exact que tu
   prétends mesurer.** Pas de SQL avec des placeholders `<FREELANCER_USER_ID>`. Pas de colonne
   « Résultat attendu ». Pas de tableau de run construit par raisonnement. Si tu n'as pas la
   sortie, tu écris **« non mesuré »** — c'est une réponse acceptable, un tableau prédit ne l'est pas.
3. **Un grep de frontière n'est pas une preuve d'accessibilité.** Au lot C-9 tu as écrit
   « Aucun autre appelant web trouvé (grep adapter/in/web → seul WalletController) ». C'était faux.
   Le chemin manquant était indirect : contrôleur → service applicatif → `walletUseCase`. Quand tu
   raisonnes sur l'autorisation, tu construis le **graphe d'accessibilité** depuis chaque
   `@PostMapping/@PutMapping/@PatchMapping`, pas la liste des appels directs.
4. **« Pas de run disponible dans cet environnement » est faux et ne sera plus accepté.** L'image
   `maven:3.9-eclipse-temurin-21` est présente localement (`docker images`). L'enforcer exige
   Maven ≥ 3.9 et le `mvn` du système est en 3.8.7 : tu construis donc **dans le conteneur**.
   Commande de référence :
   ```bash
   docker run --rm -v /home/jtk/projets/tiibntick-core:/ws -v "$HOME/.m2":/root/.m2 -w /ws \
     maven:3.9-eclipse-temurin-21 mvn -q -DskipTests package
   ```

---

## Tâche 1 — Fermer la porte ouverte (PRIORITÉ ABSOLUE, avant tout le reste)

Le commit `ee87e574` a retiré `@RequirePermission(payment:process)` de six méthodes de
`WalletService` et n'en a replacé que trois sur `WalletController`. Une quatrième voie web
existait, que ton grep n'a pas vue :

```
POST /market/orders/{id}/payment
  → MarketOrderController.processPayment            (ligne 76 — @PreAuthorize("isAuthenticated()") SEULEMENT)
  → MarketOrderApplicationService.processPayment
  → MarketOrderApplicationService.processWalletMovement   (ligne 257)
  → walletUseCase.creditCommission(creditCmd)             (ligne 274)
```

Avant le lot C-9, cet appel était gardé par `payment:process` porté par le service. Depuis
`ee87e574`, **tout utilisateur simplement authentifié peut déclencher un crédit de portefeuille.**
C'est une régression d'autorisation sur le module de paiement — pire que le faux vert du lot C-8
qu'elle était censée corriger.

À faire :

1. Ajouter `@RequirePermission(resource = "payment", action = "process")` sur
   `MarketOrderController.processPayment` (fichier
   `coreBackend/tnt-market-back-core/.../adapter/in/web/MarketOrderController.java`, ligne 76).
2. **Refaire l'inventaire correctement.** Pour chacune des six méthodes désannotées
   (`creditCommission`, `initiatePayment`, `handlePaymentCallback`, `refundPayment`,
   `splitMissionRevenue`, `transferSubDelivererCommission`), produire un tableau à quatre colonnes :
   méthode · chemin(s) web qui y mènent, direct **ou indirect** · garde actuelle sur ce chemin ·
   verdict (gardé / ouvert / aucun chemin web). Le tableau doit couvrir **tout le dépôt**, pas
   seulement `billing/`.
3. Documenter le cas de `handlePaymentCallback` et `refundPayment` : ils n'ont aujourd'hui **aucun**
   appelant dans `src/main`. Écrire dans le javadoc de chacun que l'autorisation devra être posée
   sur l'adaptateur entrant le jour où l'un d'eux sera exposé — et que pour un webhook de
   prestataire de paiement, la garde correcte est la **vérification de signature**, pas RBAC.
4. **Test de non-régression architectural.** Écrire un test qui échoue si une méthode
   d'`IWalletUseCase` touchant l'argent est atteignable depuis un `@*Mapping` sans qu'une
   `@RequirePermission` figure sur la méthode de contrôleur. Si un test de réflexion complet est
   hors de portée, un test ciblé sur `MarketOrderController.processPayment`
   (`@RequirePermission` présente, resource=payment, action=process) est le minimum acceptable.

## Tâche 2 — Durcir la résolution de l'actorId

Dans `DeliveryQueryService.resolveActorIdForDeliveryPerson`, si `tnt_delivery_persons.actor_id`
est `NULL`, `.map(dp -> dp.getActorId())` lève un `NullPointerException` Reactor opaque
(« The mapper returned a null value ») au lieu d'une erreur métier lisible. Remplacer par un
`flatMap` qui distingue explicitement les deux cas : profil introuvable, et profil trouvé sans
`actor_id`. Le message d'erreur doit nommer le `deliveryPersonId` et la colonne manquante.

Ajouter un test unitaire pour chacun des deux cas.

## Tâche 3 — Reconstruire et redémarrer (aucune ligne de code neuve après ce point)

1. Construire le jar dans le conteneur Maven 3.9 (commande ci-dessus).
2. Redémarrer `tnt-core`.
3. **Prouver que le conteneur exécute bien le code de ce lot** :
   ```bash
   curl -s http://localhost:8080/actuator/info | jq -c '.git'
   ```
   Le rapport doit contenir cette sortie brute. Elle doit montrer `dirty: false`, le SHA du dernier
   commit du lot C-10, et la branche du lot. **Si `abbrev` ne correspond pas au commit du lot, le
   lot est rouge** — tu ne rapportes rien d'autre que ça et la raison.

   Rappel factuel : au moment où j'écris, `actuator/info` renvoie
   `{"dirty":"false","commit":{"id":{"abbrev":"2fe4f34"}},"branch":"lotC8-deliveryotp-preview"}`.
   Le conteneur tourne encore du code de lot **C-8**. Aucune ligne des lots C-9 n'a jamais été
   exécutée. C'est le trou que ce lot doit fermer.

## Tâche 4 — Les trois tableaux de runs RÉELS

Trois exécutions du harnais `scripts/e2e/e2e-freelancer-courier.sh`, chacune avec son tableau de
résultats observés (C0 → C9 + l'assertion transversale logs) :

- **Run 1** — `TNT_GOFP_DELIVERY_OTP_PREVIEW=true`, parcours nominal complet.
- **Run 2** — même configuration, relancé immédiatement après le run 1, pour prouver que les
  assertions d'idempotence (C8, C9) tiennent sur un état non vierge.
- **Run 3** — sans `TNT_GOFP_DELIVERY_OTP_PREVIEW`, pour vérifier que C5 est proprement « bloqué »
  et non « faussement vert ».

Pour chaque run, le rapport donne **la sortie réelle** du bilan final, y compris le bloc
« ASSERTION TRANSVERSALE : logs core ». Si un run est rouge, tu le rapportes rouge avec la sortie
complète. **Un rouge honnête vaut mieux qu'un vert acheté.**

## Tâche 5 — La preuve par l'argent, avec de vraies valeurs

Pour le run 1, exécuter et reporter les sorties **réelles** (pas de placeholder, pas de
`-- Attendu`) :

```bash
docker exec tnt-postgres psql -U tiibntick -d tiibntick_core -c \
  "SELECT owner_id, tenant_id, balance FROM billing.wallet_wallets WHERE owner_id = '<actorId réel du run>';"

docker exec tnt-postgres psql -U tiibntick -d tiibntick_core -c \
  "SELECT id, type, amount, reference_id, created_at FROM billing.wallet_transactions
   WHERE reference_id LIKE 'MISSION-%' ORDER BY created_at DESC LIMIT 5;"
```

Le rapport doit montrer le solde **avant** et **après** la livraison, le delta, et la ligne
`CREDIT` correspondante avec son `reference_id` complet. Et aussi : la ligne de
`tnt_delivery_persons` du livreur du run, avec `id` **et** `actor_id`, pour établir noir sur blanc
que les deux UUID diffèrent et que c'est bien `actor_id` qui porte le portefeuille.

## Tâche 6 — Balayage des logs, indépendamment du harnais

Après le run 1 :

```bash
docker logs tnt-core --since <heure de début du run, format ISO UTC> 2>&1 | \
  grep -inE "best-effort|side-effect failed|Access denied|TntRoleException|WalletNotFound|swallow" | head -40
```

Reporter la sortie brute, même vide. Si elle n'est pas vide, le lot est rouge.

## Format du rapport final

1. Liste des commits (SHA court, sujet, fichiers touchés).
2. Le tableau d'accessibilité de la tâche 1 — c'est la section la plus importante du rapport.
3. Sortie brute de `actuator/info` après redémarrage.
4. Les trois tableaux de runs, avec bilans réels.
5. Les sorties SQL réelles de la tâche 5.
6. La sortie brute du balayage de logs de la tâche 6.
7. Totaux surefire par module touché (tests / échecs / erreurs), avec le chemin des rapports.
8. **Une section « non mesuré »** listant tout ce que tu n'as pas pu observer, sans l'habiller.
