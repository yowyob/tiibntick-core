# Lot C-8 — deliveryOtp PREVIEW_ONLY (débloquer C5), gate anti-arbre-sale, port hasResponse

## RÈGLE D'AUTONOMIE — À LIRE AVANT TOUT

Tu exécutes ce lot **de bout en bout, sans jamais me demander la permission de
continuer**. Interdiction absolue de produire une phrase du type « Souhaites-tu
que je procède ? », « Dois-je continuer ? », « Veux-tu que j'applique le
correctif ? ». Tu ne prends la parole **qu'une seule fois, à la fin**, sous la
forme du rapport décrit en section 6. Si tu rencontres un obstacle, tu ne
t'arrêtes pas pour demander : tu documentes l'obstacle, tu appliques la
meilleure décision technique disponible, tu continues, et tu expliques ton choix
dans le rapport final.

## RÈGLE DE PREUVE — TROISIÈME RAPPEL, DEVENU CRITÈRE DE NOTATION

Tu ne cites comme preuve **que des sorties de commandes réelles issues du chemin
exact que tu prétends mesurer**. Pas de SQL synthétique, pas d'UUID inventé, pas
de raisonnement « le diff le confirme par son silence ». Une preuve qui démontre
qu'une contrainte existe ne démontre pas qu'un 409 observé vient d'elle. Sur les
lots C-5 et C-6 tu as fourni deux fois une preuve fabriquée ; c'est corrigé sur
C-7, garde cette discipline.

## DÉPÔTS CIBLES — CHEMINS ABSOLUS

- Tâches 1, 3, 4, 5 : **`/home/jtk/projets/tiibntick-core`**
- Tâche 2 : **`/home/jtk/projets/tiibntick-core`** (script E2E, même dépôt)
- Aucune tâche côté BFF sur ce lot.

Branche à créer depuis `lotC7-dedup-et-createorupdate` :
`lotC8-deliveryotp-preview`

## CE QUI EST DÉJÀ ACQUIS (vérifié par moi, pas sur ta parole)

J'ai relu le lot C-7 ligne par ligne et **tout tient**. Pour que tu saches sur
quel socle tu construis :

- Les 5 `markNotNew()` sont présents dans le bytecode **du conteneur qui
  tourne** (`tnt-core`, jar `tnt-go-freelancer-point-back-core-0.0.1.jar`,
  `strings` → `markNotNew=1` sur chacun des 5 services).
- La garde applicative est présente dans ce même bytecode
  (`Freelancer {} already responded to announcement {}` + `idempotent return`).
- `uq_announcement_delivery_person UNIQUE (announcement_id, delivery_person_id)`
  est **réellement appliquée** en base (`pg_get_constraintdef` sur
  `announcement_subscriptions`).
- Les 6 rapports surefire existent et disent ce que tu affirmes (4×1, 7, 3 tests,
  0 échec).
- Le BFF qui écoute sur `:3001` (pid tsx, démarré 17:07:56) charge bien
  `realCoreFreelancer.ts` modifié à 17:04:25 — la béquille `subscribedJobs` est
  absente du processus qui sert réellement.
- **J'ai relancé le harnais moi-même** : `C0-C4, C6-C9 verts, C5 bloqué`.
  Ton tableau était exact.

Deux réserves, qui deviennent les tâches 2 et 3 :

1. `git.commit.id = e4dbaa8` alors que le jar contient du code C-7. Tu l'as
   signalé honnêtement, mais cela rend **C0 inopérant comme instrument** : une
   empreinte qui n'identifie pas le code qui tourne ne prouve rien. C'est
   exactement la classe de faux-vert que je traque depuis le lot C-4.
2. Ton point « non déployable » de la section 7 (over-fetch de `findById`) est
   une préoccupation de **performance**, pas un blocage de déploiement, et tu
   conclus « la décision appartient à l'équipe delivery-core ». Non. C'est notre
   code, le correctif est à notre portée, et déléguer la décision à une autre
   équipe est le schéma du faux blocage kernel du lot C-4. Tu le fais.

---

## TÂCHE 1 — DÉBLOQUER C5 : `deliveryOtp` en mode PREVIEW_ONLY (PRIORITÉ 1)

C'est le **seul rouge** du harnais et il bloque la boucle coursier complète sur
l'APK : un testeur ne pourra pas aller jusqu'à `delivered`.

État actuel, que tu as correctement documenté :
`DeliveryOtpService.java` envoie `deliveryOtp` uniquement au destinataire par
`emailPort.sendSimpleMessageReactive` (branche `emailRecipient`). Aucune réponse
HTTP ne le transporte. `buildAssignResponse` (l.427-428) ne positionne que
`pickupOtp`.

Le kernel fournit déjà le précédent exact : son OTP de connexion renvoie
`"deliveryMode":"PREVIEW_ONLY"` et le code en clair quand le canal réel n'est
pas disponible. **Tu reproduis ce mécanisme, pas une porte dérobée.**

Contrat à implémenter :

- Une propriété `tnt.gofp.delivery-otp.preview-mode` (défaut `false`), exposée
  dans `tnt-bootstrap/src/main/resources/application.yml` sous la forme
  `${TNT_GOFP_DELIVERY_OTP_PREVIEW:false}`.
- Quand elle est `true` **et seulement alors**, `buildAssignResponse` positionne
  un champ `deliveryConfirmationCode` (nom distinct de `confirmationCode`, pour
  qu'aucun mapping existant ne le relaie par accident) avec le `deliveryOtp` en
  clair, et le DTO porte `deliveryOtpDeliveryMode = "PREVIEW_ONLY"`.
- Quand elle est `false`, le champ est **absent du JSON** (`@JsonInclude(NON_NULL)`
  ou équivalent), et le comportement actuel est inchangé au bit près.
- Un `log.warn` au démarrage si la propriété est `true`, du genre
  `"deliveryOtp PREVIEW MODE actif — NE JAMAIS activer en production"`.

Garde-fous non négociables :

- Tu **ne modifies pas** le BFF. Vérifie et affirme dans le rapport que
  `CoreAnnouncementDTO` (`realCoreFreelancer.ts`) ne comporte pas
  `deliveryConfirmationCode` et que `announcementToJob` ne le mappe pas — donc
  le mobile ne le voit jamais. Si par malheur un `...spread` générique le
  relayait, tu le signales sans le corriger et tu me le dis en section 6.
- Tu vérifies que le hash BCrypt en base reste **la seule** source de vérité à
  la vérification : le mode preview expose le code, il ne court-circuite pas
  `OtpService.verifyOtp`.

Test unitaire obligatoire : `DeliveryOtpPreviewModeTest`, deux cas —
`preview=false` → champ absent du DTO ; `preview=true` → champ présent et égal
au code généré. Rapport surefire attendu.

## TÂCHE 2 — RENDRE C0 HONNÊTE : GATE ANTI-ARBRE-SALE

Le problème : le jar est construit depuis un arbre de travail non commité, donc
`git.properties` porte le SHA précédent, donc C0 affiche une empreinte qui ne
correspond pas au code exécuté.

Deux corrections, les deux :

**(a) Côté build.** Le plugin `git-commit-id` sait exposer `git.dirty`. Tu
l'actives et tu ajoutes `git.dirty` à la sortie de `/actuator/info`.

**(b) Côté harnais.** Dans
`/home/jtk/projets/tiibntick-core/scripts/e2e/e2e-freelancer-courier.sh`,
l'étape C0 lit `git.dirty`. Si elle vaut `true`, C0 passe en
`⚠️ AVERTI — binaire construit depuis un arbre sale, l'empreinte n'identifie pas
le code exécuté` et le bilan final le répète en clair. Tu **ne fais pas échouer**
le run pour autant : un arbre sale est le mode de travail normal en itération.
Ce qui est interdit, c'est qu'un run affiche une empreinte rassurante et
mensongère.

Dans le rapport, tu donnes la sortie réelle de
`curl -s http://localhost:8080/actuator/info | jq '.git'` après rebuild.

## TÂCHE 3 — PORT `hasResponse` : SUPPRIMER L'OVER-FETCH

Tu ajoutes à `IDeliveryAnnouncementPort` :

```java
Mono<Boolean> hasResponse(UUID tenantId, UUID announcementId, UUID freelancerId);
```

Implémentation dans `DeliveryAnnouncementPortAdapter` : une requête qui répond
par un booléen **sans charger la liste des réponses ni passer par
`enrichTracking`**. Puis `AnnouncementApplicationService.respondToAnnouncement`
appelle `hasResponse` au lieu de `findById` + `stream().anyMatch()`, et ne fait
le `findById` que dans la branche « déjà répondu », là où il faut effectivement
construire le DTO de retour.

Tu ajoutes l'index manquant : `tnt_announcement_responses` n'a aujourd'hui que
`tnt_announcement_responses_pkey` et `idx_tnt_resp_announcement` — j'ai vérifié
via `pg_indexes`. Il faut un index composite
`(announcement_id, delivery_person_id)`, dans un nouveau changeset Liquibase
numéroté à la suite des existants. Tu donnes la sortie `pg_indexes` après
migration appliquée.

`AnnouncementApplicationServiceTest` doit rester vert : ses 7 tests portent sur
le comportement, pas sur le mécanisme. Si un test casse parce qu'il mockait
`findById`, tu le réécris sur `hasResponse` — et tu dis lequel dans le rapport.

## TÂCHE 4 — RECENSEMENT (RAPPORT SEUL, AUCUNE CORRECTION)

Mon balayage élargi `\.setId\([a-z]+\.get(Id|ID)\(\)\)` a sorti une vingtaine
d'occurrences hors GOFP, dans des **mappers de persistance** :
`InvoicePersistenceMapper`, `TntTpPersistenceMapper`, `CommissionMapper`,
`FleetMapper`, `WorkforceMapper`, `MissionMapper`, `StaffMemberMapper`,
`BillingMapper`, `InboxMapper`, `IntakeMapper`, `OnboardingMapper`,
`HubParcelMapper`, `InvoiceReportProjectionRepositoryAdapter`.

La forme `entity.setId(domain.getId())` suivie d'un `save()` est **la même classe
de bug** que celle corrigée au lot C-7 : entité neuve en mémoire, PK existante,
`isNew=true`, INSERT au lieu d'UPDATE. Mais ces mappers peuvent être appelés sur
des chemins de création uniquement, où il n'y a aucun bug.

Tu produis un tableau : fichier, ligne, appelé sur un chemin de mise à jour
`oui/non/indéterminé`, et pour les `oui` si l'entité étend
`TntPersistableEntity`. **Tu ne corriges rien.** Hors périmètre Freelancer, la
décision de correction est la mienne.

## TÂCHE 5 — RUN COMPLET

Tu relances le harnais deux fois de suite et tu donnes les **deux** tableaux
récapitulatifs bruts. C5 doit passer au vert avec
`TNT_GOFP_DELIVERY_OTP_PREVIEW=true`. Tu donnes aussi un run avec la propriété à
`false` montrant que C5 redevient `🔴 BLOQUÉ` — c'est la preuve que le mode
preview est bien la seule voie d'exposition et qu'il est réellement désactivable.

Gate de non-régression : `wc -l scripts/e2e/e2e-freelancer-courier.sh` vaut
**1251** avant tes modifications. Tu donnes la valeur avant et après.

## SECTION 6 — FORMAT DU RAPPORT FINAL

Une seule prise de parole, à la fin, dans cet ordre :

1. **SHA du commit** sur `lotC8-deliveryotp-preview` (`git log --oneline -1`).
   Le lot C-7 ne donnait aucun SHA ; ne recommence pas.
2. Diffs réels, pas des descriptions de diffs.
3. Sortie `curl /actuator/info | jq '.git'` après rebuild.
4. Sortie `pg_indexes` sur `tnt_announcement_responses` après migration.
5. Rapports surefire (`Tests run:` intégral) pour `DeliveryOtpPreviewModeTest` et
   `AnnouncementApplicationServiceTest`.
6. Les trois tableaux de run (preview=true ×2, preview=false ×1).
7. Le tableau de recensement de la tâche 4.
8. Ce qui reste rouge, avec la preuve du blocage — et **une proposition que nous
   pouvons exécuter nous-mêmes**. Pas de renvoi vers une autre équipe.
