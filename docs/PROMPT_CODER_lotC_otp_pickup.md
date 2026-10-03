# Lot C — débloquer C4 (pickup) et, si possible, C5 (deliver)

## Dépôts cibles

- **Principal : `/home/jtk/projets/tiibntick-core`** — branche à créer :
  `lotC-otp-pickup` depuis `github/main` (fais d'abord `git fetch github`).
- **Secondaire, seulement si une modification BFF est nécessaire :**
  `/home/jtk/projets/tiibntick-bff` — branche `fix/lotC-<sujet>` depuis `github/main`.
  Commits séparés par dépôt, jamais de commit qui mélange les deux.

## Contexte : ce que le lot B a établi, et ce qu'il a affirmé de faux

Le lot B (`cd06a0e2`) a livré `scripts/e2e/e2e-freelancer-courier.sh`. Sept étapes
sur neuf passent contre le core réel. C4 et C5 sont marquées BLOQUÉ avec la
justification suivante, inscrite dans l'en-tête du script (l.12-19) et dans
`CR[C4]` / `CR[C5]` (l.431, l.439) :

> OTP pickup non récupérable par API (DeliveryOtpService.java l.67-76)

**Cette affirmation est fausse et doit être corrigée dans le script.** Vérification
faite dans le code du core :

`DeliveryOtpService.initOtpIfAbsentWithCodes` — son javadoc dit littéralement
*« exposes newly generated plain OTPs once (for assign response to the shipper FE) »*.
Et `AnnouncementApplicationService.buildAssignResponse` l.428 :

```java
if (otpResult != null && otpResult.newlyInitialized()) {
    dto.setConfirmationCode(otpResult.pickupOtp());
}
```

Donc : **le code de pickup en clair est renvoyé dans la réponse d'assignation**, une
seule fois, à la première assignation. Seul `pickupOtp` est exposé ; `deliveryOtp`
ne part que par notification push (`sendOtpNotifications`).

Le réflexe qui a produit l'erreur est le bon à corriger : tu t'es arrêté à la couche
de stockage (hash BCrypt) sans remonter le graphe d'appels jusqu'aux contrôleurs.
Quand tu conclus « indisponible », la preuve attendue n'est pas « c'est haché en
base » mais « aucun appelant ne l'expose », et cette seconde preuve se fait avec un
`grep` sur tous les usages du champ.

## Contrainte de sécurité — à lire avant de coder

Le code de pickup est destiné à **l'expéditeur**, pas au coursier. L'expéditeur le
dicte au livreur au moment où il lui remet physiquement le colis : c'est ce qui
prouve que la remise a eu lieu.

**Il est donc interdit de faire propager `confirmationCode` par le BFF jusqu'au
freelancer.** Ce serait une régression de sécurité : le coursier pourrait marquer un
colis « récupéré » sans que l'expéditeur le lui ait jamais donné.

Première tâche du lot : vérifier que le BFF ne le fait pas déjà. Si
`acceptJob` (`/home/jtk/projets/tiibntick-bff/src/core-adapter/real/realCoreFreelancer.ts`)
recopie `confirmationCode` dans la réponse rendue au mobile, c'est une faille —
tu la signales en tête de rapport et tu la corriges dans le dépôt BFF.

## Objectif

Obtenir le code de pickup **par une voie légitime**, c'est-à-dire une voie qui
n'affaiblit pas le modèle de sécurité et qui laisse le core exercer réellement
`otpService.verifyOtp` (`DeliveryStatusApplicationService` l.87-92 pour le pickup,
l.269-274 pour la livraison).

Trois voies, à évaluer **dans cet ordre**. Documente pour chacune ce que tu as
trouvé, même quand tu l'écartes.

**Voie 1 — l'expéditeur.** Le harnais possède déjà l'identité cliente qui sème
l'annonce. Cherche s'il existe un endpoint qu'un expéditeur authentifié peut appeler
pour relire le `confirmationCode` de sa propre livraison, ou une trace exploitable du
canal de notification. C'est la voie préférable : elle teste le vrai flux métier.

**Voie 2 — capture à l'assignation.** Le core renvoie le code à l'appelant de
l'assignation. Détermine précisément qui appelle quoi : C3 passe par
`POST {BFF}/v1/freelancer/jobs/:id/accept`, qui déclenche côté core
`POST /api/announcements/{id}/subscribe` puis `/assign`. Si la seule façon de capter
le code est que le harnais fasse l'assignation en direct contre le core, alors cette
étape doit être marquée **SETUP** et non assertion, et **C3 doit rester en plus**,
par le BFF, sur une seconde mission — sinon tu perds la couverture du chemin réel.

**Voie 3 — fixture maîtrisée.** Semer la livraison avec des hachages BCrypt calculés
par le harnais lui-même, donc des codes connus de lui. Acceptable **uniquement** si
c'est du seeding explicite, avant le début du scénario, et si le core exécute
réellement sa vérification derrière. Interdit de contourner, désactiver ou
court-circuiter `verifyOtp`.

Interdit dans tous les cas : inventer un code et espérer qu'il passe, ou marquer une
étape PASS sans réponse HTTP du core qui la justifie. Si aucune des trois voies
n'aboutit pour C5, tu laisses C5 en BLOQUÉ — mais avec la **vraie** raison écrite
(`deliveryOtp` n'est exposé nulle part, seul le canal push le transporte) et une
proposition chiffrée de ce qu'il faudrait demander à l'équipe core.

## Deux défauts du lot B à réparer au passage

**1. Le contrôle négatif est trop faible.** Le run en mode mock échoue sur 14
assertions, mais **toutes en `401 TOKEN_INVALID`** : le harnais meurt à
l'authentification et n'exerce aucun comportement métier. Il satisfait la lettre de
la consigne sans en satisfaire l'esprit. Fais en sorte que le run mock **franchisse
l'authentification** puis échoue sur les assertions métier — c'est cela qui prouve
que le harnais saurait détecter un `status` faux.

**2. La cible est ambiguë.** Les instructions de lancement mélangent
`TNT_CORE_URL=https://tiibntick-core.yowyob.com` et `TNT_POSTGRES=tnt-postgres`,
c'est-à-dire un core distant et une base locale. Soit c'est incohérent, soit c'est
volontaire et ce n'est écrit nulle part. Le script doit afficher en tête, sans
ambiguïté, contre quel core HTTP et contre quelle base il travaille, et **refuser de
démarrer** si la combinaison n'a pas de sens.

## Ce que je vérifierai moi-même

- `git log` des deux dépôts, un commit par dépôt, message expliquant le *pourquoi* ;
- l'en-tête du script et `CR[C4]` / `CR[C5]` ne contiennent plus l'affirmation fausse ;
- `grep -nE "\|\| true|set \+e|# skip|exit 0"` : aucune occurrence sur une assertion
  (sur un nettoyage, c'est légitime) ;
- `grep -rn "confirmationCode" /home/jtk/projets/tiibntick-bff/src` : le code de
  pickup ne remonte pas jusqu'au mobile ;
- C0 enregistre toujours le SHA du binaire interrogé, et le rapport le cite ;
- le run mock franchit l'authentification et échoue ensuite sur le métier ;
- si C4 passe : la sortie HTTP brute du core sur le `pickup`, et le `status` lu
  ensuite par une requête indépendante — pas la valeur renvoyée par l'appel lui-même.

## Format de rendu

Le tableau C0→C8 avec PASS / FAIL / BLOQUÉ, le SHA du binaire, les sorties brutes des
étapes nouvellement débloquées, la liste des bugs découverts, et une section « ce que
je n'ai pas pu prouver » — cette dernière m'intéresse autant que le reste.
