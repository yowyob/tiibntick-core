# Impacts pour le Mobile et le BFF

Référence : `cible-evenements-colis-livraison.md` §7.1. Une ligne par changement, avec son état avant/après.

## Lot L0 — socle commun (aucune rupture)

| Changement | Avant | Après | Nature | Lot |
|---|---|---|---|---|
| API REST | — | inchangée | aucun | L0 |
| Topics d'agrégat déclarés sur le cluster | `tnt.delivery.events` seul existait (ancien format, aucun lecteur interne) | `tnt.parcel.events`, `tnt.delivery.events`, `tnt.incident.events`, `tnt.dispute.events`, `tnt.announcement.events` créés au démarrage (3 partitions par défaut, `cleanup.policy=delete`, rétention 30 jours). Les quatre nouveaux restent vides jusqu'aux lots L2–L4 ; `tnt.delivery.events` garde l'ancien format jusqu'au lot L2 | ajout | L0 |
| Format des enregistrements d'agrégat (à partir de L2) | — | enveloppe JSON commune, schéma `foundation/tnt-common-core/src/main/resources/kafka/schemas/v1/envelope.schema.json` : `eventId`, `eventType`, `schemaVersion`, `occurredAt` (ISO-8601 UTC), `aggregateType`, `aggregateId`, `aggregateVersion`, `tenantId`, `correlationId`, `causationId`, `trigger`, `actor {type, id}`, `previousState`, `payload` (état complet masqué), `details`. Clé = id de l'agrégat ; ignorer un événement de `aggregateVersion` ≤ à la dernière appliquée ; dédoublonner par `eventId` ; ignorer les champs inconnus | ajout (format défini, publié à partir de L2) | L0 |
| En-tête `X-Yow-Event-Id` | absent | présent sur **tous** les enregistrements de l'outbox ; valeur = `eventId` du corps = `X-Yow-Envelope-Id` | ajout | L0 |
| Données personnelles dans les événements | — | jamais de téléphone, d'e-mail, de rue ni de repère ; nom abrégé « Prénom I. » ; adresse = quartier, ville, pays et coordonnées arrondies à **2 décimales** (≈ 1,1 km, décision QO-17) ; le détail s'obtient par l'API, avec les droits | règle | L0 |

## Lot L2 `evt-etats-colis-livraison` (états du colis, de la livraison et de l'annonce ; code de suivi)

Références : spécification §1.1, §1.2, §1.5, §2, §3.6 et tables livrées §1.7 ; décisions D-136, D-137, QO-3, QO-4, QO-9,
QO-30 du [registre](../../../decisions/registre-decisions.md).

| Changement | Avant | Après | Nature | Lot |
|---|---|---|---|---|
| Valeurs de `DeliveryStatus` (réponses de livraison, GOFP, Kafka) | `CREATED, PICKED_UP, IN_TRANSIT, AT_RELAY_POINT, DELIVERED, FAILED, CANCELLED, PAUSED_BY_INCIDENT, TIMED_OUT, SLA_BREACHED` | `CREATED, ASSIGNED, PICKED_UP, IN_TRANSIT, AT_RELAY_HUB, OUT_FOR_DELIVERY, DELIVERY_ATTEMPT_FAILED, DELIVERED, FAILED, CANCELLED, INCIDENT_REPORTED, PAUSED_BY_INCIDENT, INCIDENT_HANDOVER`. `AT_RELAY_POINT` → `AT_RELAY_HUB` (lignes migrées ; l'ancien nom reste **accepté en entrée** et dans les opérations hors ligne déjà en file) ; `TIMED_OUT` et `SLA_BREACHED` supprimés (le retard devient une alerte, lot L4) | **rupture** (valeurs) | L2 |
| Livraison avec un livreur, pas encore enlevée | `CREATED` | `ASSIGNED` (D2) ; les lignes existantes sont migrées. L'enlèvement exige un livreur : `CREATED → PICKED_UP` est refusé (409) | **rupture** | L2 |
| Détail d'une livraison (`DeliveryDetailResponse`) | — | champs ajoutés **à la fin** : `kind` (`OUTBOUND`, `RETURN`, `REDELIVERY`), `parentDeliveryId`, `parcelStatus`, `failedAttempts`, `maxAttempts`, `failureReason`, `lastAttemptReason`, `relayHubId`, `relayStorageDays`, `relayStorageExpiresAt`, `late` | ajout | L2 |
| Compteur `deliveryAttemptNumber` | 1 à la création, +1 à chaque échec | nombre de **tentatives échouées** (0 à la création) ; lignes existantes : −1 | **rupture** (sens) | L2 |
| Échec de remise | `POST /api/v1/tenants/{t}/deliveries/{id}/fail?reason=<texte libre>` → `FAILED` | `POST .../{id}/attempts/failed?reason=<code>` (alias `/fail` conservé) ; `reason` = code `DeliveryFailureReason` (`CLIENT_ABSENT_AT_DELIVERY`, `CLIENT_PHONE_UNREACHABLE`, `CLIENT_REFUSED_RECEPTION`, `CLIENT_REFUSED_OTP_VALIDATION`, `CLIENT_WRONG_ADDRESS`, `CLIENT_INCOMPLETE_ADDRESS`, `CLIENT_BUILDING_ACCESS_BLOCKED`), sinon 400. Refus du destinataire ou dernière tentative → `FAILED` (D10) ; sinon `DELIVERY_ATTEMPT_FAILED` (D9) | **rupture** | L2 |
| Nouvelle tentative / dernier trajet | — | `POST .../{id}/out-for-delivery` → `OUT_FOR_DELIVERY` (D8), depuis le relais de destination ou après une tentative échouée (tant qu'il reste des tentatives) | ajout | L2 |
| Dépôt au relais | `POST .../{id}/relay/{relayPointId}/deposit` → `AT_RELAY_POINT` | même route → `AT_RELAY_HUB` ; paramètres facultatifs `hubPackageEntryId`, `requestedStorageDays` (durée demandée par le client) ; la garde effective = min(demande, garde du relais) (QO-30) est renvoyée et sert au rappel et à l'échéance | changé | L2 |
| Fin de garde au relais | aucune | rappel avant la fin (alerte `DeliveryAlertRaised`, `RELAY_STORAGE_EXPIRING`), puis `FAILED` motif `NOT_COLLECTED_AT_RELAY` (D12) et retour du colis | ajout | L2 |
| Erreurs de transition | 400/500 selon les cas | **409** `application/problem+json`, `type` = `urn:tiibntick:<delivery/parcel/announcement>:invalid-transition` (`from`, `to`) ; livraison gelée par un litige : 409 `urn:tiibntick:delivery:frozen-by-dispute` | changé | L2 |
| Suivi public par code | `GET .../deliveries/track/{code}` : détail complet de **la** livraison (adresses, destinataire, téléphone) ; code tiré avec `Random` | code **du colis** (D-136), tiré avec `SecureRandom`, même format `TNT-AAAAMMJJ-XXXXXXXX` ; réponse **masquée** (D-137) : `trackingCode`, `parcelStatus`, villes d'origine et de destination, dates, `history[]` des trajets (`kind`, `status`, villes, dates) ; **429** au-delà de 30 requêtes par minute et par IP (`tnt.delivery.tracking.public-rate-limit-per-minute`). Les codes imprimés avant restent valables | **rupture** | L2 |
| Code de suivi d'une nouvelle livraison | colonne `tnt_deliveries.tracking_code` | lu sur le colis (`trackingCode` des réponses inchangé) ; un retour et une nouvelle livraison gardent le même code | changé | L2 |
| Annonces de retour | — | un brouillon d'annonce `kind = RETURN` est ouvert automatiquement pour le payeur, **toujours l'expéditeur** (aussi pour un colis non retiré au relais, D-147), jamais publié seul ; champs `kind`, `creatorRole`, `outcome` ; un brouillon sans contact connu de l'expéditeur n'a pas de destinataire (à compléter avant publication) | ajout | L2 |
| GOFP `PATCH` statut de livraison | cible `FAILED` avec un texte libre ; `AT_RELAY_POINT` | `FAILED` / `DELIVERY_ATTEMPT_FAILED` exigent un code `DeliveryFailureReason` dans `reason` (sinon 400) ; cibles `OUT_FOR_DELIVERY`, `AT_RELAY_HUB` ; le dépôt transmet la durée demandée du besoin ; plus de durée de garde 3 jours codée en dur dans la notification au client | **rupture** | L2 |
| GOFP opérations hors ligne | `HUB_DEPOSIT` → `AT_RELAY_POINT` | `HUB_DEPOSIT` → `AT_RELAY_HUB` ; cibles autorisées : + `AT_RELAY_HUB`, `OUT_FOR_DELIVERY`, `DELIVERY_ATTEMPT_FAILED` | changé | L2 |
| GOFP profil du point relais | — | champ `storageDays` (durée de garde d'un relais indépendant) dans la mise à jour ; 422 `RELAY_STORAGE_DAYS_OUT_OF_BOUNDS` hors bornes (3 à 30 jours par défaut, D-130) | ajout | L2 |
| Garantie de retour (livraison vers un relais) | — | champs ajoutés à la fin du détail de livraison : `returnGuaranteeStatus` (`HELD`, `RELEASED`, `CAPTURED`, `NOT_HANDLED`, `FAILED`, ou absent) et `returnGuaranteeReason` (code stable, ex. `SENDER_NOT_PAYING_BY_WALLET`) ; le gel réel du wallet arrive avec le lot L5 : d'ici là, `NOT_HANDLED` / `WALLET_GUARANTEE_NOT_CONNECTED` | ajout | L2 (D-147, D-151) |
| Dépôt au relais d'un hub non agréé | dépôt accepté puis refusé par l'inventaire | refusé **avant** tout changement d'état : 409 (`RelayHubStateException`) sur `POST .../relay/{relayPointId}/deposit` et le `PATCH` GOFP ; la livraison reste `IN_TRANSIT` | **rupture** | L2 (D-140) |
| Retour annulé avant enlèvement | colis inchangé, rien | colis `RETURN_REQUESTED`, événement `ParcelReturnDeliveryCancelled`, nouveau brouillon de retour pour l'expéditeur | ajout | L2 (D-148) |
| GOFP dépôt au relais | ancrage « mission terminée » et paiement du livreur à tout dépôt | seulement si le relais est la destination de la livraison ; un relais étape ne termine rien | changé | L2 (D-149) |
| GOFP demande terminée | `COMPLETED` même si la livraison a échoué | `status = COMPLETED` + nouveau champ `outcome` (`DELIVERED` / `FAILED`) | ajout | L2 (QO-9) |
| Colis sans tenant | aucun événement `ParcelCreated` | rattaché au tenant système, `ParcelCreated` toujours publié | changé | L2 (D-150) |
| Kafka (lecture éventuelle) | `tnt.delivery.events` : ancien format | `tnt.parcel.events`, `tnt.delivery.events`, `tnt.announcement.events` au nouveau format (§3.3), un événement par transition (`<Agrégat><ÉtatCible>`) ; les anciens topics sont encore produits jusqu'au lot L8 | ajout | L2 |

## Garantie de retour et suites L2 (décisions D-147 à D-151, 2026-11-26)

| Sujet | Effet visible |
|---|---|
| Création d'une livraison vers un relais payée par wallet | un second montant (égal au prix de la livraison au relais) est gelé comme garantie du retour ; dégelé au retrait. **D-152 (2026-11-27)** : ce gel est un second séquestre confirmé par un second code à la sélection (voir lot L5) |
| Colis non retiré à la fin de la garde | garantie débitée, brouillon d'annonce retour au nom de l'expéditeur (plus jamais au nom du relais) |
| Livraison retour annulée avant enlèvement | événement de colis + nouveau brouillon retour pour le payeur |
| Dépôt au relais (Go-Freelancer) | mission terminée et livreur payé seulement si le relais est la destination |

## Grille tarifaire par véhicule (décision D-058, 2026-11-26)

| Changement | Avant | Après | Nature |
|---|---|---|---|
| Champ `minimumFee` de la grille livreur (`GET`/`PUT /api/freelancers/{id}/policies`) | absent | prix minimum d'une course, optionnel ; `0` supprime le minimum | ajout |
| Champ `waitingFeePerMinute` de la grille plateforme | toujours `0` | valeur par type (15 à 100 FCFA/min, 0 pour le bus) | changement de valeur |
| Premier `PUT` d'un livreur sans grille | champs non envoyés mis à **0** | champs non envoyés = valeur de la **grille plateforme de son véhicule** (moto si inconnu) | changement de comportement |
| Grille plateforme (devis `source = PLATFORM_DEFAULT`, prix indicatif « Postuler ») | 5 grilles (BIKE et SCOOTER au prix moto, VAN au prix camion), sans minimum | 8 grilles (`tarifs-par-vehicule.md` §1) avec minimum ; ex. moto 10 km : 1 500 → 1 600 FCFA ; bus 250 km, 1 kg : 77 030 → 3 600 FCFA | changement de valeur |

## Lot L3 `evt-incidents` (incidents déclarés par le livreur ; alertes de livraison)

Références : spécification §1.3, §3.5 ; décisions [C.3], [D.1], QO-2, QO-12 à QO-15, QO-18 ; seuils D-131 à D-133.

| Changement | Avant | Après | Nature | Lot |
|---|---|---|---|---|
| Statut d'un incident à sa création | `DETECTED` ou `REPORTED` selon l'origine | toujours `REPORTED`, puis `TRIAGED` dans la même requête (la réponse de `POST /api/v1/incidents` est déjà triée). `DETECTED` n'existe plus (lignes migrées en `REPORTED`) | **rupture** (valeurs) | L3 |
| Qui déclare | tout titulaire de `incident:create` ; le système ouvrait aussi des incidents (GPS, géofence, SLA) | **seulement le livreur affecté** à la livraison (`missionId`) : **403** sinon ; type non déclarable par un livreur (détections, auto-accusation) : **422**. Liste des types autorisés : `GET /api/v1/incidents/declarable-types` | **rupture** | L3 (QO-12, [D.1]) |
| Champ `blockingForced` de la déclaration | — | booléen facultatif « je ne peux pas continuer » : rend l'incident bloquant quels que soient le type et la gravité | ajout | L3 (QO-2) |
| Réponse d'un incident (`IncidentResponse`) | — | champs ajoutés **à la fin** : `blocking`, `deliveryOutcome` (`RESUMED`, `FAILED`, `DISPUTE`), `responsibleParty` (`SENDER`, `RECIPIENT`, `COURIER`, `PLATFORM`, `RELAY`, `AGENCY`, `NONE`), `resolutionNote`, `resolutionProposedAt` | ajout | L3 |
| Effet sur la livraison | tout incident mettait la livraison en pause | non bloquant : la livraison reprend aussitôt (`INCIDENT_REPORTED` → état d'avant) ; bloquant : `PAUSED_BY_INCIDENT` jusqu'à la résolution | **rupture** (comportement) | L3 (QO-2) |
| Clôture par le livreur | `POST .../{id}/resolve` réservé au gestionnaire | le livreur déclarant peut résoudre **son** incident **non bloquant** (même route, sans `resolutionMode`) ; un incident bloquant : 403/409 | ajout | L3 (I6) |
| Résolution par un gestionnaire | `resolutionMode` obligatoire | `resolutionMode` toujours exigé du gestionnaire (400 sinon) ; nouveaux champs `deliveryOutcome` (obligatoire pour un incident bloquant, 400 sinon) et `responsibleParty` | **rupture** (gestionnaire) | L3 |
| Proposition / confirmation | — | `POST .../{id}/propose-resolution` (gestionnaire : `deliveryOutcome`, `responsibleParty`, `note`) → `PENDING_CONFIRMATION` ; le déclarant `POST .../{id}/confirm-resolution` ou `POST .../{id}/contest-resolution?reason=` ; sans réponse sous 24 h, confirmation automatique | ajout | L3 (QO-14) |
| Remplaçant | proposé par notification, aucune route d'acceptation | `POST /api/v1/incidents/{id}/handover/accept` (le livreur proposé accepte) ; si l'ancien livreur a le colis, l'incident passe `AWAITING_HANDOVER` et la livraison `INCIDENT_HANDOVER` | ajout | L3 |
| Confirmation de passation | l'acteur lui-même ou un gestionnaire, sans vérifier qu'il est le livreur de ce côté | seul le livreur du côté qui confirme (ancien ou remplaçant), 403 pour un autre ; 409 hors `AWAITING_HANDOVER` ; sans les deux confirmations sous 2 h, la passation est abandonnée et un autre remplaçant est cherché | **rupture** | L3 |
| Annulation par le livreur | possible à tout moment | seulement avant toute prise en charge (`REPORTED`, `ACKNOWLEDGED`, `TRIAGED`), 409 ensuite ; un gestionnaire peut annuler tant que l'incident n'est pas résolu | **rupture** | L3 |
| Erreurs de transition | 400/500 selon les cas | **409** | changé | L3 |
| Code de référence d'un incident | `TNT-INC-NNNNNN` (6 chiffres, `Math.random`, collisions possibles) | `TNT-INC-AAMMJJ-XXXXXXXXXX` (10 hexadécimaux, `SecureRandom`) ; les anciens codes restent lisibles par `GET .../ref/{code}` | changé | L3 |
| Retards, perte GPS, écarts de trajet, zones | ouvraient un incident | **alertes** sur la livraison : champ `activeAlerts` du payload Kafka de la livraison, événements `DeliveryAlertRaised` / `DeliveryAlertCleared` ; notification « livraison en retard » au client et au livreur (Go-Freelancer). Aucun incident n'est ouvert | **rupture** | L3 ([C.3], QO-18) |
| Kafka (lecture éventuelle) | anciens topics d'incident | `tnt.incident.events` au nouveau format (§3.3), un événement par transition (`Incident<ÉtatCible>`) + `IncidentEscalatedToDispute`, `IncidentHandoverConfirmed`, `IncidentHandlerAssigned` ; description jamais publiée, coordonnées arrondies. Anciens topics écrits jusqu'au lot L8 | ajout | L3 |

## Lot L4 `evt-litiges` (litiges : machine d'états, effets sur le colis, indemnisation)

Références : spécification §1.4, §4.3 ; décisions [B.6], [B.7], [E], QO-7, QO-16, QO-23, QO-24, C10.

| Changement | Avant | Après | Nature | Lot |
|---|---|---|---|---|
| Qui ouvre un litige (`POST /api/v1/disputes`) | tout titulaire de `dispute:create` lié à la mission | le demandeur doit être **partie au colis** : expéditeur, livreur affecté (ou son organisation freelance), opérateur du relais détenteur ou de destination ; **403** sinon. `claimantType = SYSTEM` réservé au support. Un destinataire ne peut pas encore ouvrir lui-même (le support ouvre en son nom) | **rupture** | L4 ([E]) |
| Effet sur le colis | aucun (statut du colis inchangé) | le colis passe `UNDER_DISPUTE` dans la même requête ; la livraison est gelée sauf pour un litige de retard (`DELIVERY_DELAYED`, QO-7). Un seul litige ouvert par colis (**409** sinon) | ajout (comportement) | L4 |
| Délai de dépôt | aucun | sur un colis clos (livré, retourné…), ouverture refusée en **409** au-delà du délai réglé par l'administration (7 jours par défaut) | **rupture** | L4 |
| Réponse d'ouverture | `packageId` tel que saisi | `packageId` = id du colis résolu (aussi quand seule la livraison `missionId` est donnée) | changé | L4 |
| Jugement (`POST .../{id}/mediation/rule`) | `resolutionType` libre, indemnité facultative | nouvelles valeurs `TOTAL_LOSS_COMPENSATED` (colis `LOST`) et `DAMAGE_COMPENSATED` (colis `DAMAGED`) ; type indemnisant ⇒ montant > 0 obligatoire, type non indemnisant ⇒ aucune indemnité (**409** sinon) ; champ ajouté `responsibleParty` (`SENDER`, `RECIPIENT`, `COURIER`, `PLATFORM`, `RELAY`, `AGENCY`, `NONE`) | **rupture** (gestionnaire) | L4 |
| Indemnisation | statut `PENDING_COMPENSATION` jusqu'à la saisie manuelle d'une référence | payée aussitôt par le fonds de la plateforme → `COMPENSATED` dans la même réponse ; si le paiement échoue, le litige reste `PENDING_COMPENSATION` (la réponse du jugement est un succès) et le support relance par `POST /api/v1/disputes/{id}/compensation/pay` : **503** `COMPENSATION_UNAVAILABLE` (fonds pas encore branché, lot L5) ou **409** `COMPENSATION_FUND_INSUFFICIENT` | ajout | L4 |
| Issue sur le colis | aucune | à la clôture : colis remis dans son état d'avant (rejet, accord, retrait, expiration, clôture administrative, indemnisation simple), `LOST`, `DAMAGED`, nouvelle livraison ou retour à l'expéditeur ; une livraison encore active d'un colis perdu ou abîmé passe en échec | ajout (comportement) | L4 |
| Clôture (`POST .../{id}/close`) | tout `closureType` (dont `RESOLVED_WITH_COMPENSATION`, `EXPIRED`) | seulement `ADMINISTRATIVE_CLOSURE` avec motif ; refusée en `PENDING_COMPENSATION` (**409**) | **rupture** | L4 (C10) |
| Retrait (`DELETE .../{id}`) | sans vérifier l'auteur | seul le demandeur (**403** sinon) | **rupture** | L4 |
| Détail d'un litige (`DisputeDetailResponse`) | — | champs ajoutés **à la fin** : `responsibleParty`, `incidentId` (incident d'origine), `closedAt` | ajout | L4 |
| Mises à jour d'un litige | chaque sauvegarde créait une nouvelle ligne (pièces, commentaires et chronologie perdus) | le litige, ses pièces, ses commentaires et sa chronologie sont bien enregistrés | correctif | L4 |
| Kafka (lecture éventuelle) | `tnt.dispute.opened`, `.resolved`, `.ruled`… | `tnt.dispute.events` au nouveau format (§3.3), un événement par transition (`Dispute<ÉtatCible>`, `DisputeEvidenceAdded`) ; description et résumé jamais publiés. Anciens topics écrits jusqu'au lot L8 | ajout | L4 |

## Lot L5 `evt-paiement-sequestre`

| Changement | Avant | Après | Nature | Lot |
|---|---|---|---|---|
| Paiement d'une indemnité de litige | jamais versé (statut `PENDING_COMPENSATION` jusqu'à une saisie manuelle) | versé par le fonds d'indemnisation du Kernel sur le wallet du bénéficiaire, dans la requête du jugement ou de la relance (`POST /api/v1/disputes/{id}/compensation/pay`). Erreurs de la relance : **409** `COMPENSATION_FUND_INSUFFICIENT`, `BENEFICIARY_WALLET_MISSING` (le bénéficiaire n'a pas encore de wallet), `KERNEL_PAYMENT_REFUSED` ; **503** `KERNEL_PAYMENT_UNAVAILABLE` | ajout (comportement) | L5 |
| Kafka `tnt.billing.compensation.paid` | jamais publié | publié à chaque indemnité versée (`disputeId`, `compensationId`, `beneficiaryActorId`, `amount`, `currency`, `fundWalletId`) | ajout | L5 |
| Kafka `tnt.billing.compensation.initiated` | déclaré | **supprimé** | suppression | L5 |
| Sélection d'une candidature payée | `POST /api/announcements/{aid}/responses/{rid}/assign` puis paiement par receive-code | **nouveau** `POST /api/announcements/{aid}/responses/{rid}/selection/challenge` `{paymentMode: ESCROW\|CASH}` → `{intentId, deliveryId, paymentMode, amount, currency, platformShare, courierShare, challengeToken, expiresInSeconds, codePreview, expiresAt}` (pas de jeton en CASH) ; puis `POST …/selection/confirm` `{intentId, challengeToken, code}` → `{deliveryId, paymentMode, paymentStatus, announcement}`. Le code est envoyé par le Kernel au client | ajout | L5 |
| Paiement d'une livraison déjà attribuée | `POST /api/v1/deliveries/{id}/payment/receive-code` (livreur) puis `POST …/payment` (client) | `POST /api/v1/deliveries/{id}/payment/challenge` (client) puis `POST /api/v1/deliveries/{id}/payment` `{challengeToken, code}` ; l'argent est **bloqué** jusqu'à la remise | **rupture** | L5 |
| Endpoint receive-code | `POST /api/v1/deliveries/{id}/payment/receive-code` | **supprimé** | suppression | L5 |
| Statut de paiement (`PaymentStatusResponse`) | `status` du règlement | `status` : `HELD`, `FROZEN`, `PAID`, `REFUNDED`… ; champ ajouté **à la fin** : `escrowStatus` ; `method = ESCROW` | ajout | L5 |
| Espèces du livreur | — | `GET /api/v1/gofp/cash-collections/mine` (reversements en attente), `POST /api/v1/gofp/cash-collections/{deliveryId}/remit/challenge`, `POST …/{deliveryId}/remit` `{challengeToken, code}` | ajout | L5 |
| Erreurs de paiement | 422 `urn:tiibntick:billing:wallet-frozen` (wallet gelé) | ProblemDetail `type = urn:tiibntick:payment:<code>`, propriété `code` : **403** `PAYMENT_INTENT_FORBIDDEN`, `CASH_COLLECTION_FORBIDDEN`, `NOT_THE_CLIENT` ; **404** `PAYMENT_INTENT_NOT_FOUND`, `CASH_COLLECTION_NOT_FOUND` ; **409** `PAYMENT_INTENT_INVALID`, `PAYMENT_INTENT_EXPIRED`, `WALLET_MISSING`, `ALREADY_SELECTED`, `KERNEL_PAYMENT_REFUSED` ; **503** `PLATFORM_WALLET_NOT_CONFIGURED`, `KERNEL_PAYMENT_UNAVAILABLE` | **rupture** | L5 |
| Kafka `tnt.delivery.events` D7 | `details` sans destination | `details.atDestination` (vrai au relais de destination) | ajout | L5 |
| Sélection d'une livraison vers un relais (D-152) | un code | **deux codes** : le défi renvoie en plus, **à la fin**, `guaranteeAmount`, `guaranteeChallengeToken`, `guaranteeExpiresInSeconds`, `guaranteeCodePreview` (garantie de retour, même montant que l'aller) ; la confirmation exige en plus `guaranteeChallengeToken` et `guaranteeCode` (sinon **400** `RETURN_GUARANTEE_CODE_REQUIRED`) ; réponse : `guaranteeStatus` ajouté à la fin (`HELD`). Les autres sélections sont inchangées (champs `null`) | **rupture** (relais seulement) | L5 |
| Sélection d'une annonce retour payée par le fonds (QO-22, D-151) | séquestre du client | `paymentMode = FUND` imposé par le Core (payeur `PLATFORM` ou `COURIER`, ou garantie capturée) : pas de code, rien n'est pris ; `POST …/selection/confirm` `{intentId}` → `paymentStatus = FUNDED`. Demander `FUND` soi-même → **400** `PAYMENT_MODE_INVALID` | ajout | L5 |
| Dettes envers le fonds (QO-22, QO-26) | — | `GET /api/v1/billing/debts/mine` (dettes de l'appelant ou de son organisation : `id`, `reason` `DISPUTE_COMPENSATION`/`RETURN_COST`, `reference`, `deliveryId`, `amount`, `currency`, `status` `PENDING`/`OPEN`/`REPAID`/`CANCELLED`, `recoveryOpen`, `debtorType`, `debtorId`, `note`, `createdAt`, `updatedAt`), `POST /api/v1/billing/debts/{id}/repay/challenge`, `POST …/{id}/repay` `{challengeToken, code}`. Erreurs : **403** `DEBT_FORBIDDEN`, **404** `DEBT_NOT_FOUND`, **409** `DEBT_NOT_RECOVERABLE` | ajout | L5 |
| Kafka `tnt.billing.wallet.split_executed` | déclaré, jamais publié | **supprimé** (la répartition est faite par le Kernel) | suppression | L5 |

## Lot L6 `evt-comptabilite-kernel` (livres tenus dans le Kernel, 2026-11-27)

| Changement | Avant | Après | Nature | Lot |
|---|---|---|---|---|
| Écriture manuelle dans le journal local | `POST /api/accounting/journal-entries` → 201 | **410** `ACCOUNTING_JOURNAL_READ_ONLY` (QO-21 : les livres sont tenus dans le Kernel) ; `GET` inchangés | **rupture** | L6 |
| Écritures automatiques | journal local (jamais réellement alimenté) | écritures validées dans le service comptable du Kernel (livre plateforme ou de l'organisation émettrice) | comportement | L6 |
| Administration des écritures | — | `GET /api/v1/accounting/kernel-entries?status=PENDING\|POSTED\|VALIDATED\|FAILED\|SKIPPED&limit=` → `[{id, sourceKey, sourceType, sourceEventId, ownerType, ownerId, reference, amount, currency, kernelEntryId, status, reason, postedAt, createdAt, updatedAt}]` ; `POST /api/v1/accounting/kernel-entries/{id}/retry` (permission `accounting:admin`) | ajout | L6 |
| Kafka `tnt.billing.compensation.paid` | indemnités de litige seulement | aussi les retours payés par le fonds ; champs ajoutés `reason` (`DISPUTE_COMPENSATION` \| `RETURN_COST`) et `returnDeliveryId` ; `disputeId` vaut `null` pour un retour | ajout | L6 |
| Kafka `tnt.billing.wallet.commission.calculated` | publié à `DUE` pour les espèces, jamais ensuite | republié avec `commissionStatus = SETTLED` quand le livreur reverse les espèces | ajout | L6 |
| Kafka `tnt.billing.invoice.paid` / `InvoicePaid` | `invoiceId, paymentRef, amount, occurredAt, tenantId` | champs ajoutés à la fin : `issuerOrgType`, `issuerOrgId` | ajout | L6 |

## Lot L8 `evt-branchement-total` (bascule de tous les consommateurs, retrait des anciens topics, 2026-11-27)

| Changement | Avant | Après | Nature | Lot |
|---|---|---|---|---|
| Synchronisation hors ligne (delta pull) : `aggregateType` des enregistrements | `MISSION`, `PACKAGE` (+ types incident/litige d'avant) | `DELIVERY`, `PARCEL`, `ANNOUNCEMENT`, `INCIDENT`, `DISPUTE` ; la valeur stockée est l'enveloppe complète de l'événement (instantané masqué, §3.3) ; opération `CREATED` (pas d'état précédent), `STATUS_CHANGED` (l'état change), sinon `UPDATED` | **rupture** | L8 |
| SSE / WebSocket de suivi `/topic/tracking/{code}` | statuts de la **livraison** | statuts du **colis** (`ParcelStatus` : `AT_RELAY_HUB`, `RETURN_REQUESTED`, `DELIVERED`…) | **rupture** | L8 |
| WebSocket `/topic/delivery/{id}` | `tnt.delivery.mission.status.changed` | `tnt.delivery.events`, mêmes champs | aucun | L8 |
| Kafka (lecture éventuelle) | `tnt.delivery.mission.*`, `tnt.delivery.package.*`, `tnt.delivery.hub-deposit.*`, `tnt.delivery.announcement.*`, `tnt.delivery.freelancer_org.assigned`, anciens topics incident/litige, `tnt.inventory.hub.package.*` | **supprimés** : lire les 5 topics d'agrégat (`tnt.parcel|delivery|announcement|incident|dispute.events`) ; le type d'événement est `eventType`, la clé est l'id de l'agrégat | **rupture** | L8 |
| Kafka `tnt.sync.entity-changed.dlq` | file morte sync | **supprimée** (échecs dans `tnt.dlq`) | suppression | L8 |
| Notifications push (gabarits) | `notify.freelancer_org.mission_assigned`, `notify.sub_deliverer.mission_assigned`, `notify.gofp.delivery.at_relay_point` | `notify.freelancer_org.delivery_assigned`, `notify.sub_deliverer.delivery_assigned`, `notify.gofp.delivery.at_relay_hub` ; textes « mission » → « livraison ». Ne compte que pour un client qui filtre sur la clé du gabarit | rupture mineure | L8 |
| API REST | champs `missionId`, paramètres `missionId` | **inchangés** (le renommage §2.3 touche les ports internes, pas les contrats REST) | aucun | L8 |

## QO-11 / D-153 : livreur remplacé, payé par le fonds au prorata (2026-11-27)

| Changement | Avant | Après | Nature | Décision |
|---|---|---|---|---|
| `tnt.delivery.events`, événement qui reprend une livraison avec un autre livreur (D18 après remise, D14/D16 avant ramassage) | `details` vide | `details.replacedCourierId`, `replacementCourierId`, `replacedLegKm` (km à vol d'oiseau du livreur qui part), `remainingKm` (du point de remise à l'adresse de livraison) ; distances `null` si un point est inconnu, `replacedLegKm = 0` avant ramassage | ajout | D-153 |
| Dettes du livreur `GET /api/v1/billing/debts/mine` | raisons `DISPUTE_COMPENSATION`, `RETURN_COST` | + `COURIER_REPLACEMENT` (le fonds a payé le remplaçant ; référence `REPLACEMENT:<livraison>:<rang>`) | ajout (enum) | D-153 |
| `tnt.billing.compensation.paid` | `reason` `DISPUTE_COMPENSATION` ou `RETURN_COST` | + `COURIER_REPLACEMENT`, avec `deliveryId` | ajout (enum) | D-153 |
| Gains du remplaçant | rien n'était versé au remplaçant | versement par le fonds à la libération du séquestre (livraison ou dépôt au relais de destination) | comportement | D-153 |

À prévoir dans l'application du livreur : afficher la dette `COURIER_REPLACEMENT` (montant retenu au profit du
remplaçant) et, pour le remplaçant, le versement du fonds. La remise d'un colis transmet déjà le point de remise
(`handoverLatitude`, `handoverLongitude` de l'incident) : un point absent ou (0, 0) laisse le partage à
l'administrateur.

