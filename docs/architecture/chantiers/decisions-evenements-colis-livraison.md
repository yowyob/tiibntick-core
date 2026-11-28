# Décisions du 2026-11-26 — cycle du colis, livraison, incidents, litiges, paiement, Kafka

Décisions prises par l'utilisateur (questions/réponses). Elles sont **non négociables** pour les agents ; tout point
non couvert ici est remonté à l'architecte, jamais deviné.

## A. Constat de départ (code de `feat/single-system-tenant`, hors coreBackend)

- `Parcel` (delivery-core) : aucun état, aucun événement propre.
- `DeliveryStatus` : CREATED, PICKED_UP, IN_TRANSIT, AT_RELAY_POINT, DELIVERED, FAILED, CANCELLED, PAUSED_BY_INCIDENT,
  TIMED_OUT, SLA_BREACHED ; transitions dans `DeliveryStateTransitionPolicy`.
- Incohérences : TIMED_OUT/SLA_BREACHED jamais posés ; FAILED « terminal » (`isTerminal`) mais `FAILED → IN_TRANSIT`
  autorisé ; javadoc ≠ table ; trust a son propre `ParcelLifecycleState` (CREATED, ASSIGNED, PICKED_UP, IN_TRANSIT,
  AT_RELAY_HUB, OUT_FOR_DELIVERY, DELIVERED, DELIVERY_FAILED, UNDER_DISPUTE, RETURNED) ; 7 topics « colis » déclarés
  sans producteur (`tnt.delivery.package.picked-up`, `hub-deposit.created/picked-up`, `package.disputed`,
  `package.dispute.released`, `tnt.inventory.hub.package.deposited/pickedup`).
- incident-core : `IncidentStatus` (19 états dont DETECTED), ~150 `IncidentType`, `HandoverStatus`.
- dispute-core : `DisputeStatus` (OPEN … CLOSED_EXPIRED), `ResolutionType` (COMPENSATION_GRANTED, PARTIAL_COMPENSATION,
  COMPLAINT_DISMISSED, MUTUAL_AGREEMENT, REDELIVERY_ORDERED).

## B. Cycle d'états du colis

1. **Propriétaire : delivery-core.** `Parcel` reçoit son cycle d'états, mis à jour dans la même opération que la
   livraison (pas d'écart), et par les cas d'usage litige/retour. **trust réutilise le même enum** (fin du doublon) et ne
   fait qu'ancrer.
2. **États** : les 10 états de trust **reconduits sans débat** — CREATED, ASSIGNED, PICKED_UP, IN_TRANSIT, AT_RELAY_HUB,
   OUT_FOR_DELIVERY, DELIVERED, DELIVERY_FAILED, UNDER_DISPUTE, RETURNED — **plus** RETURN_REQUESTED,
   RETURN_IN_TRANSIT, LOST (final), DAMAGED (final, indemnisé). UNDER_DISPUTE mémorise l'état d'avant et y revient si
   le litige est rejeté.
3. **Retour = nouvelle livraison** (livraison « retour » liée au même colis, avec son livreur, son prix, ses codes de
   remise). Le colis garde l'historique à travers ses livraisons.
4. **Déclencheurs du retour** (tous) : échecs répétés (max de tentatives atteint) ; refus du destinataire ; non retiré
   au point relais après la durée de garde ; décision de litige (ou annulation par l'expéditeur après enlèvement).
5. **Seuils** : 3 tentatives max ; durée de garde = `storageDays` du relais (7 jours par défaut) ; rappel au
   destinataire J-1. **Configurables par l'administrateur de TiiBnTick** (réglage plateforme, PAS par tenant) — à
   ajouter aux notes de la plateforme d'administration ; **et configurables par organisation** pour TiiBnTick Agency
   (organisations à id unique) : le responsable de l'agence décide qui a le droit de les régler, selon le
   fonctionnement de sa plateforme.
6. **Coût du retour : selon la faute.** Par défaut l'expéditeur paie (refus, absence, non-retrait) ; si l'incident ou
   le litige établit la faute du livreur ou de la plateforme, le retour est gratuit pour l'expéditeur et imputé au
   responsable. Prix : grille par type de véhicule.
7. **Issue d'un litige → état du colis (automatique)** : plainte rejetée ou accord amiable → état d'avant le litige ;
   perte totale indemnisée → LOST ; dommage indemnisé → DAMAGED ; nouvelle livraison ordonnée → nouvelle livraison du
   même colis ; retour ordonné → RETURN_REQUESTED (ajouter **RETURN_TO_SENDER** aux issues de litige).

## C. États de la livraison

1. **Incidents : 4 états** — INCIDENT_REPORTED (déclaré ; la livraison continue si non bloquant), PAUSED_BY_INCIDENT
   (bloquant, en attente de traitement), INCIDENT_HANDOVER (colis remis à un livreur remplaçant), puis retour à l'état
   d'avant à la résolution, ou FAILED si l'incident est sans issue. Le détail du traitement (examen, escalade, résolu,
   clos) reste dans le cycle propre de l'incident, avec ses événements.
2. **Échec** : nouvel état DELIVERY_ATTEMPT_FAILED (non final, nouvel essai possible, compteur de tentatives) ;
   au-delà du maximum, FAILED devient vraiment final et déclenche le retour du colis (nouvelle livraison retour).
3. **TIMED_OUT et SLA_BREACHED : retirés des états.** Un surveillant planifié détecte les retards et publie des alertes
   (SLA dépassé, délai expiré) + indicateur « en retard » sur la livraison.

## C bis. Exigence de cohérence et d'homogénéité (utilisateur, 2026-11-26)

Les cycles d'états du colis et de la livraison doivent être **cohérents et homogènes** :
- même vocabulaire pour les mêmes faits (un seul nom par notion entre colis, livraison, trust, notify et les topics) ;
- chaque état de livraison a son pendant défini pour le colis (table de correspondance livraison → colis, sans trou ni
  ambiguïté), et inversement chaque état du colis indique quelle livraison/quel événement l'y amène ;
- une seule table de transitions par entité, qui fait foi (javadoc, `isTerminal`, `isActive`, doc et tests en sont
  dérivés ou vérifiés contre elle) ; états finaux réellement sans sortie ; aucun état inatteignable ni sans issue ;
- chaque transition émet exactement un événement nommé de façon homogène, et les tests parcourent toutes les
  transitions autorisées et vérifient le refus de toutes les autres.

## D. Incidents

1. **Seul le livreur déclare un incident.** Les détections automatiques (SLA, anomalie GPS, zone dangereuse…)
   deviennent des **alertes** (événements Kafka + notifications au livreur, à l'agence, au support) qui invitent le
   livreur à déclarer.
2. `IncidentStatus` : **DETECTED retiré** ; un incident naît REPORTED. Les 18 autres états restent et publient chacun un
   événement.
3. **Types déclarables par le livreur** : sous-ensemble (lui-même, véhicule, client, colis, route/zone, point relais,
   réglementaire, humain). SYSTEM_* et AUTOMATION_* réservés aux alertes et au support.
4. **Remplaçant (INCIDENT_HANDOVER)** : matching automatique (même classement que le matching), le premier qui accepte
   est retenu, client notifié ; remise par double confirmation (codes) comme dans incident-core ; pour une livraison
   d'agence, l'agence choisit parmi ses livreurs.

## E. Litiges

- Peuvent ouvrir un litige : l'expéditeur (client), le destinataire, le livreur, le point relais / le support.
- Issues → état du colis : voir B.7.

## F. Annonces

- Tout le cycle publié : créée, publiée, modifiée, candidature reçue, retirée/rejetée, livreur sélectionné (attribuée),
  annulée, terminée — état complet de l'annonce, données personnelles masquées.
- **Point relais** : peut créer des annonces pour **ses envois propres** (il est l'expéditeur et paie) et, pour les
  colis qu'il détient, **uniquement dans les cas particuliers (colis non retiré)**.
- Contexte métier (NE PAS implémenter sans demande) : le point relais est une mini-organisation qui peut avoir sa
  flotte (livreurs indépendants), prendre un colis au guichet et sous-traiter sa livraison, et remettre un colis à un
  client qui vient le retirer.

## G. Kafka

1. **Un topic par agrégat** : `tnt.parcel.events`, `tnt.delivery.events`, `tnt.incident.events`, `tnt.dispute.events`,
   `tnt.announcement.events` ; type d'événement dans l'enveloppe et en en-tête ; clé = id de l'agrégat (ordre garanti).
   Ne créer que le nécessaire (colis et livraison se chevauchent).
2. **Contenu : état complet** (instantané après changement + type de changement + état précédent).
3. **Données personnelles masquées** (pas de téléphone ni d'adresse exacte : ville/quartier, coordonnées arrondies, nom
   abrégé ; détails par l'API avec droits).
4. **Plateformes externes** (BFF Mobile, Agency, Link, Market, Point Relais) : lecture directe du cluster partagé, un
   groupe chacune, ACL lecture seule.
5. **Anciens topics retirés d'un coup** (mission.created, package.updated… ~25), producteurs et consommateurs du Core
   basculés dans le même lot ; table de correspondance ancien → nouveau dans le guide.
6. **Format** : enveloppe JSON commune (eventId, eventType, schemaVersion, occurredAt, aggregateId, tenantId, payload) ;
   ajout de champs seulement ; changement cassant → nouveau schemaVersion ; schémas JSON dans le guide, vérifiés par
   tests.
7. **Sans exception** : chaque topic restant a un producteur réel et est déclaré (NewTopic) ; topic sans producteur
   possible → supprimé avec ses consommateurs morts ; topic sans consommateur interne gardé seulement s'il sert aux
   plateformes externes (documenté) ; champs manquants ajoutés aux producteurs. Preuve : test sur vrai broker par
   topic (production → consommation → effet) + application complète démarrée sur Kafka/PostGIS/Redis réels.
8. **Consommateurs « lancer sans attendre »** (`.subscribe()` dans un listener void) : **tous corrigés** — chaque
   consommateur attend la fin de son traitement (délai max), un échec déclenche les nouvelles tentatives puis la file
   des messages morts.
9. Événements à publier dès création/modification pour affichage par d'autres : états de colis et de livraison, tout ce
   qui concerne incidents (états, traitement…) et litiges, annonces (Go-Freelancer et Point Relais).
10. **Guide HTML local** (jamais publié en ligne) : topics avec rôles, schémas, producteurs et consommateurs branchés
    dans le Core, connexion, groupes, bonnes pratiques, correspondance ancien → nouveau.

## H. Paiement et comptabilité (Kernel)

1. **Séquestre Kernel** (`/api/payments/escrows`, parts COURIER / PLATFORM / MERCHANT) remplace le transfert direct
   client → livreur : le client confirme une fois (code) à la sélection ; libération à la remise (OTP) ; gel sur
   incident escaladé ou litige (`freeze`/`unfreeze`) ; remboursement ou décision selon l'issue (`refund`, `decision`).
   Commission 5 % enfin prélevée. La table locale `wallet_holds` et le transfert direct disparaissent.
2. **Espèces** : encaissement Kernel (`/api/payments/cash-collections`, parts) : créé à la sélection, montant déclaré à
   la remise, reversement des parts par le livreur depuis son wallet (challenge + code) ; impayés visibles ; blocage
   d'un livreur trop débiteur = décision de l'administrateur (note plateforme d'administration).
3. **Comptabilité : livres par propriétaire.** La plateforme n'enregistre que sa part (produit de commission 706 à la
   libération du séquestre ou au reversement ; indemnisations sorties du fonds `/api/payments/compensations`) ; la part
   d'un relais ou d'une agence va dans ses propres livres (et ce qu'elle doit à la plateforme) ; trésorerie = compte
   « wallet Kernel » par organisation, pas MTN/Orange ; le paiement client → livreur indépendant n'entre dans aucun
   livre TiiBnTick.
4. **Écritures dans la comptabilité du Kernel** (`/api/accounting-service/**`) ; le Core ne tient plus de journal
   local, il garde les correspondances objet métier ↔ écriture. Vérification réelle : code MFA à demander à
   l'utilisateur.

## I. Autres décisions du jour

- Véhicules existants sans capacité (H10) : le livreur la saisit à son prochain enregistrement (rien d'inventé).
- Fiabilité Kafka (fusionnée a31fec25) : le Core publie les événements wallet ; relance auto de l'outbox ; anti-doublons
  partout ; groupes neufs à `earliest`.

## J. Réponses aux questions ouvertes de l'architecte (QO-1 à QO-31, 2026-11-26)

Détail mot pour mot (questions, options, réponses) dans `docs/decisions/registre-decisions.md` (D-090 et suivantes).
Ces réponses **priment** sur les valeurs provisoires de `cible-evenements-colis-livraison.md` §8.

| QO | Décision |
|---|---|
| QO-1 | `INCIDENT_REPORTED` = état de passage jusqu'au tri ; ensuite `PAUSED_BY_INCIDENT` si bloquant, sinon retour à l'état d'avant. |
| QO-2 | Bloquant = règle type/gravité **+** forçage « je ne peux pas continuer » par le livreur. |
| QO-3 | Une tentative échouée n'est pas un incident : opération de livraison avec motif, comptée pour le seuil ; refus du destinataire → retour direct. |
| QO-4 | Livraison retour = annonce « retour » créée automatiquement en brouillon pour le payeur (expéditeur ; relais pour un non-retiré), matching et séquestre habituels. **Précisé par D-147/D-151 : le relais ne paie jamais ; pour un non-retiré, le payeur est l'expéditeur (garantie gelée à la création).** |
| QO-5 | Échec définitif après incident : par défaut retour si enlevé, sinon `CREATED` (nouvelle annonce) ; le gestionnaire peut choisir autrement. |
| QO-6 | Annulation possible depuis CREATED, ASSIGNED, PICKED_UP, PAUSED_BY_INCIDENT ; après enlèvement → retour. |
| QO-7 | Litige pendant une livraison : retard → la livraison continue, seul l'argent est gelé ; autre objet → livraison gelée (sauf échec). Colis `UNDER_DISPUTE` dans les deux cas. |
| QO-8 | Retour échoué → nouveau `RETURN_REQUESTED` ; après 3 retours échoués (réglable par l'admin) : stockage relais/plateforme + signalement au support qui décide. |
| QO-9 | Annonce d'une livraison en échec définitif → `COMPLETED` avec issue `FAILED`. |
| QO-10 | Destination relais : séquestre libéré au dépôt au relais (confirmé par le relais). |
| QO-11 | Remplacement : part livreur au prorata de la distance ; si le Kernel ne permet pas de modifier les parts : tout au remplaçant + compensation du livreur d'origine par le fonds, et demande au Kernel. **Révisé par D-153 (2026-11-27)** : le séquestre Kernel ne modifie pas ses parts et paie toute la part livreur au livreur d'origine ; le fonds paie chaque remplaçant au prorata de la distance et le livreur d'origine lui doit la même somme (dette `COURIER_REPLACEMENT`) ; demande K15 au Kernel. Livré le 2026-11-27. |
| QO-12 | Types de détection / auto-accusation exclus de la déclaration livreur (liste de l'architecte). |
| QO-13 | `TRANSFERRING_TO_HUB` gardé (aucun remplaçant + relais à portée). |
| QO-14 | `PENDING_CONFIRMATION` gardé (le livreur déclarant confirme ou conteste, avec confirmation automatique au délai). |
| QO-15 | Incident escaladé en litige → `RESOLVED` automatiquement (motif « transféré en litige », lien vers le litige). |
| QO-16 | Nom gardé : `RETURN_TO_SENDER_ORDERED` (homogène avec `REDELIVERY_ORDERED`). Remplace « RETURN_TO_SENDER » de B.7. |
| QO-17 | Coordonnées publiées à 2 décimales (~1,1 km). |
| QO-18 | Alertes : enlèvement en retard = fin de fenêtre + 15 min ; livraison en retard = fin de fenêtre (à défaut délai selon l'urgence) ; ETA + 30 min ; GPS perdu 10 min ; arrêt prolongé 20 min — **réglables par l'admin TiiBnTick dès maintenant**, surcharge par organisation Agency. |
| QO-19 | Part `MERCHANT` = frais de garde du relais **et** part de l'agence du livreur. |
| QO-20 | Plan de comptes validé : 4671, 4672, 706, 6788, 411 (constantes dans une table de correspondance). |
| QO-21 | Journal local d'accounting-core : arrêt des écritures, lecture seule, puis suppression par changeset après accord explicite du mainteneur. |
| QO-22 | Retour à la charge du livreur : payé par le fonds, puis dette du livreur suivie comme un impayé d'espèces (seuil et suspension décidés par l'admin). |
| QO-23 | Litige ordonnant retour/nouvelle livraison : séquestre de l'aller remboursé (sauf aller effectué sans faute du livreur) ; nouvelle course payée par le responsable. |
| QO-24 | Échec/annulation après enlèvement : faute client/expéditeur → libération ; faute livreur/plateforme → remboursement. |
| QO-25 | Types `SYSTEM_*`/`AUTOMATION_*` : alertes et tickets support uniquement, jamais d'incident. |
| QO-26 | Indemnité payée par le fonds alors qu'un tiers est responsable → dette du responsable (livreur, relais, agence), recouvrée par reversement ; **dans ce chantier**. |
| QO-27 | Topics sans consommateur interne : gardés (producteur réel, déclarés) et documentés « pour plateformes externes » ; suppression éventuelle dans un lot ultérieur. |
| QO-28 | Pas de séquestre pour les livraisons d'agence créées sans annonce. |
| QO-29 | Relais indépendant : peut fixer sa durée de garde entre un minimum et un maximum fixés par l'admin ; autres seuils = plateforme. |
| QO-30 | Échéance de garde = minimum(demande du client, durée du relais). |
| QO-31 | Tranché par les faits : `tnt.billing.template.applied` a un producteur (`ApplyTemplateUseCase`, tnt-billing-templates) → consommateur gardé. |

Décisions liées issues de l'intégration des lots Go-Freelancer : propriétaires des adresses et dépôts relais migrés
convertis de l'id de personne Go vers l'acteur du cœur (nouveau changeset + IT) ; `REVOKED` ajouté aux points relais
(définitif, motif, notification, colis en garde retournés ou transférés).

## K. Code de suivi (2026-11-26, registre D-136 et D-137)

Constat : le code de suivi `TNT-AAAAMMJJ-XXXXXXXX` (`TrackingCode`, delivery-core) est porté par la **livraison**
(`Delivery.trackingCode`, unique en base) ; `Parcel` n'a que son UUID. Suivi par code : delivery-core
`GET /api/v1/tenants/{tenantId}/deliveries/track/{code}`, Link `GET /api/v1/platform/link/parcels/track/{code}`
(client de plateforme), flux SSE `GET /api/v1/realtime/sse/tracking/{code}`.

1. **Code au colis (D-136)** : attribué au colis à sa création, **immuable**, il le suit à travers toutes ses
   livraisons (aller, retour, nouvelle livraison ordonnée) ; c'est lui qu'utilisent Link, les applications et les QR
   codes, et le suivi montre l'historique complet du colis. Chaque livraison garde un identifiant interne. Les routes de
   suivi existantes restent et cherchent par le code du colis.
2. **Sécurité (D-137)** : tirage cryptographique (`SecureRandom`), même format ; limitation du nombre de requêtes par
   adresse IP sur le suivi public ; le suivi public ne montre que l'état, la ville et les horaires, données
   personnelles masquées comme dans Kafka ; le détail est réservé aux personnes concernées.

## L. Seuils (lot L1) et suites Go-Freelancer (2026-11-26, registre D-130 à D-135)

- D-130 : bornes de la durée de garde d'un relais indépendant = **3 à 30 jours** par défaut, réglables par l'admin.
- D-131 : plages de saisie gardées (tentatives 1–10, retours échoués 1–10, garde 1–60 j, alertes 0 ou 1–1 440 min,
  délais 1–720 h).
- D-132 : délais sans fenêtre gardés : 48 h standard, 24 h express, 8 h jour même ; réglables par l'admin, surchargeables
  par une agence.
- D-133 : surcharge par une agence **libre**, sans autorisation préalable de l'admin (tous les seuils, permission
  déléguée par le responsable de l'agence ; l'admin voit et peut supprimer).
- D-134 : liens d'adresse migrés sans correspondance sûre vers un acteur : **supprimés** (les dépôts relais orphelins ne
  sont pas concernés : comptés et signalés).
- D-135 : révocation (livreurs, points relais, cœur et Go-Freelancer) **uniquement depuis approuvé ou suspendu** ; un
  relais en attente est refusé, pas révoqué.

## M. Agrément des points relais (2026-11-26, registre D-138 à D-146)

- D-138 : statut d'agrément du hub du cœur séparé de l'état d'exploitation, recopié par le relais Go ; D-139 :
  APPROVED↔SUSPENDED, 409 sinon ; D-140 : relais suspendu = plus de dépôts ni de sélection comme destination, garde
  existante retirable jusqu'à son terme.
- D-141 routes d'agrément dans le cœur (`relay:approve`) ; D-142 `PATCH …/status` réservé à l'administrateur ;
  D-143 une agence (organisation à antennes) n'a pas de point relais, ses hubs restent gérés par Agency ;
  D-144 destination vérifiée dans le cœur ; D-145 relais en pause refusé ; D-146 relais plein refusé.

## N. Suites du lot L2 (2026-11-26, registre D-147 à D-151)

1. **Colis non retiré au relais : le relais ne paie jamais (D-147, D-151).** Applicable seulement quand l'expéditeur
   paie par son wallet :
   - à la création d'une livraison vers un relais, le prix de l'aller est mis en séquestre Kernel comme d'habitude
     (le livreur est payé au dépôt, D-149) ;
   - **en plus**, un montant est gelé sur le wallet de l'expéditeur comme garantie du retour ; ce montant est
     **exactement la somme qui a payé la livraison au relais**, avec le même mécanisme de parts (prélèvement
     plateforme, part du point relais quand applicable, part du livreur…) ;
   - retrait par le destinataire → la garantie est dégelée ;
   - non-retrait à la fin de la garde (même délai que celui au bout duquel le colis est déclaré abandonné) → la
     garantie est débitée définitivement et paie la livraison retour, l'expéditeur étant le payeur ;
   - tous les autres cas (expéditeur ne payant pas par wallet : espèces, organisation…) sont **marqués « non traités
     pour le moment »** et signalés (spec d'administration §5.2.1).
   - Interprétation retenue de la réponse (à corriger par l'utilisateur au besoin) : la garantie est un gel
     supplémentaire égal au montant de l'aller, et non le séquestre de l'aller lui-même.
   - **Confirmation du gel (D-152, 2026-11-27)** : le Kernel ne gèle que par séquestre, chacun confirmé par un code
     du payeur. À la sélection d'une livraison vers un relais, le client confirme **deux codes** : le prix de l'aller
     et la garantie (second séquestre du même montant, objet `RETURN_GUARANTEE`, au profit du fonds d'indemnisation).
     Retrait → garantie remboursée ; fin de la garde → garantie versée au fonds, qui paie le livreur du retour
     (retour en mode `FUND`).
2. **Livraison retour annulée avant enlèvement (D-148)** : le colis reste `RETURN_REQUESTED`, un événement de colis est
   publié et un nouveau brouillon d'annonce retour est recréé pour le payeur.
3. **Dépôt au relais dans Go-Freelancer (D-149)** : il termine la mission (ancrage « mission terminée » et libération de
   la part du livreur) seulement si le relais est la destination de la livraison ; un relais étape (transfert, colis
   non livrable) ne termine rien.
4. **Colis sans tenant (D-150)** : rattaché au tenant système unique ; tous ses événements sont publiés ; un test
   garantit qu'aucun chemin ne crée de colis sans tenant.
5. Rappels techniques (sans nouvelle décision) : affichage GOFP d'une demande terminée en échec = `COMPLETED` avec issue
   `FAILED` (QO-9, à exposer) ; `DELIVERY_LATE` et l'ouverture d'incident sur dépassement SLA sont rebranchés par les
   lots incidents/L8 sur les événements SLA décidés.
