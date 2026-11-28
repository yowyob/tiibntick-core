# Spécifications de la future plateforme d'administration de TiiBnTick

> **Règle permanente (utilisateur, 2026-11-26).** Tout ce qui, dans le Core, demande l'intervention d'un administrateur
> (validation, réglage, arbitrage, surveillance, relance manuelle) est ajouté ici **automatiquement**, dans le même
> commit que le travail qui le crée. Les décisions correspondantes sont tracées dans
> [le registre des décisions](../decisions/registre-decisions.md).


> **Pour l'équipe qui construira la plateforme d'administration.** Ce document recense, dans le Core, chaque
> étape métier où une personne habilitée doit approuver, refuser, suspendre ou réactiver quelque chose. Il est
> tenu à jour par tous les chantiers (d'abord le dédoublonnage Go-Freelancer).
>
> Relevé dans le code de la branche `feat/single-system-tenant` le 2026-11-26. Les routes sont celles exposées
> par `tnt-bootstrap`.

## 1. Décision du 2026-11-26 : un nouveau livreur doit être agréé par un administrateur

**Avant :** un livreur projeté dans le cœur (`tnt_delivery_persons`) était marqué `APPROVED` automatiquement
(`DeliveryPerson`, `GofpFreelancerProjectionService`). Il pouvait donc recevoir des courses dès son inscription.

**Désormais :**
- un nouveau livreur reste `PENDING` tant qu'un administrateur ne l'a pas validé ;
- le cœur (`tnt-delivery-core`, `DeliveryPersonStatus`) fait foi pour l'agrément ;
- les livreurs déjà actifs gardent leur statut : la migration ne rétrograde personne ;
- tant qu'il est `PENDING`, le livreur ne reçoit pas de course (matching) ;
- cycle d'états : `PENDING → APPROVED`, `PENDING → REJECTED`, `APPROVED → SUSPENDED → APPROVED`, et
  `REVOKED`, qui est définitif.

**Ce que la plateforme d'administration doit offrir :**
1. une file des livreurs `PENDING`, avec les éléments à contrôler (identité/KYC, véhicule déclaré et sa capacité en kg,
   pièces, photos) ;
2. les actions approuver, refuser (avec un motif), suspendre, réactiver et révoquer ;
3. une notification au livreur à chaque décision, dans la langue de son compte (catalogue i18n `notify.*`) ;
4. un historique : qui a décidé, quand et pourquoi.

**Ce que le Mobile et le BFF doivent afficher :** un écran « compte en cours de validation » après l'inscription d'un livreur, au lieu d'un accès
direct aux courses.

**Routes actuelles :**
- côté Go-Freelancer : `PUT /api/v1/admin/tnt-go-freelancer/freelancers/{id}/validate`, `.../suspend`, `.../revoke` ;
- côté cœur : `POST /api/v1/tenants/{tenantId}/delivery-persons/{deliveryPersonId}/approve` et `.../suspend`.

Lot L3 (fait) : le refus (`reject`), la levée de suspension et la révocation existent dans le cœur (`DeliveryPerson`,
`DeliveryPersonStatus.canTransitionTo`). Les routes Go ci-dessus appliquent toutes la transition sur le livreur du cœur
(`GofpFreelancerService.updateStatus` → `DeliveryPerson.transitionTo`) : `validate` avec `approved=false` refuse,
`.../activate` lève une suspension. La projection et la création Go créent le livreur `PENDING` (plus d'agrément d'office).

## 2. Inventaire des validations par un administrateur

Garde = contrôle d'accès vu dans le code. « ⚠ » signale une garde trop faible, à corriger avant d'exposer la route à
une plateforme d'administration.

### 2.1 Livreurs, clients et points relais (Go-Freelancer, cœur)

| Étape | Route | Garde |
|---|---|---|
| Agréer, suspendre ou révoquer un livreur Go-Freelancer | `PUT /api/v1/admin/tnt-go-freelancer/freelancers/{id}/validate`, `/suspend`, `/revoke` | `@RequirePermission(gofp-admin, manage)` |
| Fixer le prix d'un livreur | `PUT /api/v1/admin/tnt-go-freelancer/freelancers/{id}/price` | idem |
| Agréer ou suspendre un livreur (cœur) | `POST /api/v1/tenants/{tenantId}/delivery-persons/{id}/approve`, `/suspend` | à vérifier |
| Suspendre (`APPROVED` → `SUSPENDED`) ou réactiver (`SUSPENDED` → `APPROVED`) un point relais, comme un livreur (D-139) | `PUT /api/v1/admin/tnt-go-freelancer/relay-points/{id}/suspend`, `/activate` (200, aussi quand le relais a déjà ce statut ; 404 ; 409 pour tout autre statut ou si le hub du cœur refuse ; un relais `PENDING` se valide, il ne s'active pas). Le changement est fait d'abord sur l'agrément du hub du cœur, puis recopié sur le relais (D-138) | `gofp-admin:manage` |
| Révoquer définitivement un point relais Go-Freelancer, motif obligatoire (D-129, § 6.1) | `PUT /api/v1/admin/tnt-go-freelancer/relay-points/{id}/revoke`, corps `{"reason": "..."}` | `relay:revoke` (administrateur de la plateforme seulement) |
| Révoquer définitivement un hub relais du cœur, motif obligatoire (D-129, § 6.1) | `POST /api/v1/geo/tenants/{tenantId}/hubs/{hubId}/revoke`, corps `{"reason": "..."}` | `relay:revoke` (gardée pour la révocation, D-141 ; administrateur seulement) |
| Approuver, refuser (motif obligatoire), suspendre (motif facultatif), réactiver un hub relais du cœur (D-141) | `POST /api/v1/geo/tenants/{tenantId}/hubs/{hubId}/approve`, `/reject` (corps `{"reason": "..."}`), `/suspend` (corps `{"reason": "..."}` facultatif), `/reactivate` ; 200 avec `approvalStatus` et `operatingStatus` (aussi quand le hub a déjà ce statut), 400 motif de refus absent, 403 sans la permission, 404 hub inconnu, 409 transition refusée. Le relais Go-Freelancer adossé au hub recopie le changement et son opérateur est notifié | `relay:approve` (donnée à aucun rôle : administrateur de TiiBnTick seulement) |
| Approuver ou rejeter un point relais (`PENDING` → `APPROVED` / `REJECTED` seulement, D-138) | `PATCH /api/v1/gofp/relay-point-profiles/{id}/status?status=APPROVED\|REJECTED\|SUSPENDED` : passe par la même machine et par le hub du cœur (`APPROVED` sur un relais suspendu = réactivation ; `PENDING` → 409 ; `REVOKED` → 400, voir la route de révocation). 200 avec le relais, 404, 409. L'approbation crée le hub du cœur s'il manque (relais avec coordonnées GPS) puis l'approuve. Notification `notify.gofp.relay_status.*` | `gofp-admin:manage` (D-142) |
| Activer, suspendre ou révoquer un client | `PUT /api/v1/admin/tnt-go-freelancer/clients/{id}/activate`, `/suspend`, `/revoke` | `gofp-admin:manage` |
| Suspendre ou reprendre un hub relais | `PATCH /api/v1/tenants/{tenantId}/hubs/{hubId}/suspend`, `/resume` | rôles `AGENCY_MANAGER`, `TNT_ADMIN` |

### 2.2 Identité et KYC

| Étape | Route | Garde |
|---|---|---|
| Valider le KYC d'un acteur | `PUT /api/v1/kyc/{actorId}/validate` | rôles `SUPPORT_AGENT`, `TNT_ADMIN` |
| Approuver ou rejeter un dossier KYC de tiers | `POST /api/v1/tnt-tp/kyc/{kycRecordId}/approve`, `/reject` | `actor:approve` |
| Approuver le KYC de base d'une organisation de freelancers | `POST /api/v1/admin/freelancer-orgs/{orgId}/kyc/basic/approve` | administration |
| KYC de base ou complet, vérification, suspension et levée de suspension d'une organisation de freelancers | `POST /api/v1/freelancer-orgs/{orgId}/kyc/basic`, `/kyc/full`, `/verify`, `/suspend`, `/unsuspend`, `/activate` | rôles `TNT_ADMIN`, `SUPPORT_AGENT` (KYC) |

Le concept d'organisation de freelancers (« 3 indépendants + un chef ») pourrait être retiré : décision attendue,
voir `agency-et-modules.md`.

### 2.3 Agences (onboarding et registre)

| Étape | Route | Garde |
|---|---|---|
| Approuver une candidature d'agence (plateforme) | `POST /api/v1/onboarding/agency/applications/{agencyRef}/approve` (+ `/kernel-identity`, `/provision`, `/assign-owner-role`) | chaîne client de plateforme (`X-Client-Id`/`X-Api-Key`) |
| Approuver ou rejeter une demande d'onboarding d'agence | `PATCH /api/v1/tenants/{tenantId}/agency-registry/admin/onboarding/requests/{agencyId}/approve`, `/reject` | ⚠ `isAuthenticated()` seulement |
| Activer ou suspendre une agence | `PATCH /api/v1/tenants/{tenantId}/agency-registry/agencies/{agencyId}/activate`, `/suspend` | ⚠ `isAuthenticated()` seulement |
| Approuver ou rejeter une demande d'intégration (intake) | `POST /api/v1/tenants/{tenantId}/agency-registry/intake-requests/{intakeId}/approve`, `/reject` | ⚠ `isAuthenticated()` seulement |
| Suspendre ou réactiver un livreur d'agence | `PATCH /api/v1/tenants/{tenantId}/agency-registry/deliverers/{delivererId}/suspend`, `/reactivate` | ⚠ `isAuthenticated()` seulement |
| Suspendre ou réactiver un membre du personnel | `PATCH /api/v1/tenants/{tenantId}/agency-registry/staff/{memberId}/suspend`, `/reactivate` | ⚠ `isAuthenticated()` seulement |

⚠ Avec `isAuthenticated()` seul, tout utilisateur connecté peut approuver ou suspendre. Il est possible qu'un contrôle
soit fait plus bas, dans le service : ce n'est pas vérifié. À corriger avant la plateforme d'administration, par une
permission explicite (`@RequirePermission`) testée.

### 2.4 Litiges, incidents, marketplace, plateforme

| Étape | Route | Garde |
|---|---|---|
| Médiation d'un litige : démarrer, trancher, escalader en arbitrage | `POST /api/v1/disputes/{disputeId}/mediation/start`, `/rule`, `/escalate` | `dispute:resolve` |
| Vérifier une preuve de litige | `PUT .../evidence/{evidenceId}/verify` | à vérifier |
| Résoudre ou escalader un incident | `POST .../incidents/{id}/resolve`, `/escalate` | à vérifier |
| Approuver, rejeter ou suspendre une annonce de la marketplace | `POST /api/v1/platform/market/listings/{id}/approve`, `/reject`, `/suspend` | client de plateforme |
| Modérer un avis sur un prestataire | `POST /api/v1/platform/market/reviews/{id}/approve`, `/reject` | client de plateforme |
| Activer ou désactiver une politique de facturation ou un modèle de politique | `PATCH .../billing-policies/{policyId}/activate`, `/deactivate` ; `POST .../templates/admin/{code}/activate` | à vérifier |
| Vérifier un point d'intérêt géographique | `POST /api/v1/geo/tenants/{tenantId}/pois/{poiId}/verify` | à vérifier |
| Faire tourner ou révoquer la clé API d'un client de plateforme | `POST /api/v1/admin/api-keys/{keyId}/rotate`, `/revoke` | `platform:clients` (`TNT_ADMIN`) |

Les validations RH (congés, notes de frais, avances, feuilles de temps, formations : `tnt-hrm-core`) relèvent du
manager de l'organisation et non de la plateforme. Elles ne sont donc pas listées ici.

## 3. Validations attendues et pas encore dans le code

| Étape | Origine | État |
|---|---|---|
| Véhicule déclaré par un livreur (type, plaque, capacité en kg obligatoire, photos) | décision du 2026-11-26 (lot L3, délégation à `tnt-resource-core`) | à prévoir : le véhicule est contrôlé avec l'agrément du livreur. Les véhicules migrés sans capacité restent en attente jusqu'à ce que le livreur la saisisse (décision H10) : la plateforme doit lister ces livreurs. |
| Prix d'un livreur différent de la grille par défaut | grille plateforme par type de véhicule appliquée (D-058, `tarifs-par-vehicule.md` §1, 8 grilles), que chaque livreur peut redéfinir champ par champ | **pas de validation dans le code** : un livreur modifie sa grille sans contrôle (seul `@Min(0)`). Décision à prendre : faut-il une validation, ou des bornes (par exemple 50 % à 300 % de la grille plateforme) ? La plateforme doit au minimum **lister les grilles qui s'écartent fortement** de la grille plateforme. |
| Grille plateforme par type de véhicule | D-058 | **paramètre plateforme** (aujourd'hui `tnt.gofp.courier.grids` dans `application.yml`, 8 clés : `velo`, `moto`, `scooter`, `tricycle`, `taxi`, `camionnette`, `camion`, `bus` ; colonnes base, /km, /kg, minimum, /m³, fragile, périssable, attente/min). À rendre modifiable par un administrateur, avec historique (§4 bis) ; un changement ne touche pas les grilles déjà enregistrées par les livreurs, seulement les nouveaux devis « prix plateforme » et la première grille d'un livreur. |
| Rétrogradation de fidélité après la migration vers les seuils du cœur | lot L3 (H3) | à suivre : aucune validation, mais un support doit pouvoir expliquer le nouveau palier |

## 4. Règles communes recommandées pour la plateforme d'administration

1. Chaque décision passe par une **permission explicite** (`@RequirePermission`), jamais par `isAuthenticated()` seul.
2. Chaque décision **notifie** la personne concernée, dans la langue de son compte.
3. Chaque refus, suspension ou révocation **porte un motif**, conservé et visible par la personne.
4. Chaque décision est **journalisée de façon immuable** : voir la règle obligatoire 4 bis ci-dessous.
5. Les états terminaux (`REVOKED`) ne sont pas réversibles depuis l'interface.

### 4 bis. Journal immuable de toutes les actions d'administrateur (OBLIGATOIRE, décision du 2026-11-26)

> Décision de l'utilisateur : « toutes les actions d'administrateurs doivent être journaliser et persister en immuable
> pour que l'on puisse retrouver qui a fait quoi et que rien ne puisse être modifié une fois journalisé. Nous sommes ici
> hors blockchain. »

**Portée.** Toute action d'administrateur, sans exception : validations, refus, suspensions, réactivations,
révocations, réglages (plateforme et organisation), arbitrages et décisions d'argent (séquestre, remboursement,
indemnisation, dette), relances manuelles (outbox, file des messages morts), gestion des accès (clés API, rôles,
permissions), suppressions et corrections de données, y compris une action **refusée** (tentative sans droit) et une
action **échouée**.

**Contenu de chaque entrée.** Qui (acteur du cœur, rôle et organisation au moment de l'action, client de plateforme
s'il y en a un), quand (horodatage serveur UTC), quoi (type d'action, ressource visée et son id, route/cas d'usage),
pourquoi (motif saisi), avant/après (valeurs modifiées), résultat (réussi, refusé, échoué et sa cause), contexte
technique (id de corrélation, adresse IP, agent utilisateur), tenant.

**Immuabilité, hors blockchain.** La preuve repose uniquement sur la base de données et le code, sans
`tnt-trust-core` ni ancrage blockchain :
- table en **ajout seul** : aucune route, aucun cas d'usage ni aucune requête ne modifie ou ne supprime une entrée ;
- protection **en base** : un déclencheur refuse tout `UPDATE`, `DELETE` et `TRUNCATE` sur la table, et le rôle
  applicatif n'a que les droits `INSERT` et `SELECT` (révocation des autres droits) ;
- **chaînage par empreinte** : chaque entrée porte l'empreinte (SHA-256) de son contenu et de l'entrée précédente ;
  une vérification planifiée recalcule la chaîne et lève une alerte si une entrée a été altérée ou retirée ;
- écriture dans la **même transaction** que l'action quand c'est possible ; sinon, l'action n'est pas considérée
  comme faite tant que l'entrée n'est pas écrite (pas d'action d'administrateur sans trace) ;
- une correction se fait par une **nouvelle entrée** qui cite l'entrée corrigée, jamais par modification ;
- aucune purge automatique ; toute politique de conservation ou d'archivage sera une décision explicite de
  l'utilisateur.

**Consultation.** La plateforme d'administration offre la recherche (par acteur, ressource, type d'action,
période, résultat) en **lecture seule**, sous une permission dédiée, et l'export ; la consultation du journal est
elle-même journalisée.

**À faire dans le Core.** Un module de fondation commun (journal d'audit administrateur), branché sur toutes les
routes d'administration existantes (inventaire des sections 2, 5 et 6) et futures. Les journaux d'audit déjà
présents (par exemple `client_audit_logs` de `tnt-platform-gateway-core`) devront être alignés sur cette règle ou
reliés à ce journal.

## 5. Chantier « cycle du colis, livraison, incidents, litiges, séquestre » (décisions du 2026-11-26)

Source : [décisions](../architecture/chantiers/decisions-evenements-colis-livraison.md),
[spécification cible](../architecture/chantiers/cible-evenements-colis-livraison.md) §5, et le
[registre](../decisions/registre-decisions.md) (QO-1 à QO-31). Les routes exactes seront fixées par les lots
d'implémentation (`evt-*`) ; cette section sera complétée à chaque fusion.

### 5.1 Seuils de livraison et de garde

| Réglage | Défaut | Qui règle |
|---|---|---|
| Nombre maximal de tentatives de remise avant retour du colis | 3 | **Administrateur de TiiBnTick** (réglage plateforme, pas par tenant) |
| Durée de garde au point relais (au-delà : retour du colis) | `storageDays` du relais, 7 jours par défaut | idem |
| Rappel au destinataire avant la fin de garde | J-1 | idem |
| Nombre de retours échoués avant stockage et signalement au support | 3 | idem |
| Alertes de retard (QO-18) : enlèvement en retard (fin de fenêtre + 15 min), livraison en retard (fin de fenêtre, à défaut délai selon l'urgence), ETA dépassée (+ 30 min), GPS perdu (10 min), arrêt prolongé (20 min) | valeurs indiquées | idem, surcharge possible par organisation Agency |
| Bornes de la durée de garde qu'un point relais indépendant peut fixer lui-même (QO-29) | 3 à 30 jours (D-130) | administrateur de TiiBnTick (minimum et maximum) |
| Délai de livraison quand l'annonce n'a pas de fenêtre horaire (alerte « livraison en retard ») | 48 h standard, 24 h express, 8 h jour même (D-132) ; une livraison programmée a toujours une fenêtre | idem, surcharge possible par organisation Agency |
| Surcharge des seuils pour une organisation TiiBnTick Agency (id d'organisation unique) | — | le **responsable de l'agence** décide qui, dans son organisation, a le droit de les régler, selon le fonctionnement de sa plateforme |

Plages de saisie acceptées (garde-fous, D-131) : tentatives 1–10, retours échoués 1–10, garde 1–60 jours, alertes 0 ou
1–1 440 minutes, délais 1–720 heures. Surcharge par une agence **libre, sans autorisation préalable de
l'administrateur** (D-133) : tous les seuils, selon la permission que délègue le responsable de l'agence ; l'administrateur
voit toutes les surcharges et peut en supprimer une.

La plateforme d'administration doit permettre : modifier ces valeurs ; voir, accorder ou retirer la surcharge d'une
agence ; afficher les valeurs effectives d'une organisation et leur source (plateforme ou organisation).

**Lot L3 (2026-11-26) — alertes branchées sur ces seuils.** La surveillance des livraisons (`DeliveryWatchService`,
toutes les 5 minutes) lit les seuils d'alerte **effectifs de l'organisation de la livraison** (plateforme, ou surcharge
de l'agence) : enlèvement en retard, livraison en retard (fin de fenêtre + grâce, à défaut création + délai de
l'urgence), ETA dépassée, GPS perdu. Une modification prend effet sous 60 s (cache invalidé par
`DeliveryThresholdsChanged`). Si l'administration ne répond pas, les valeurs par défaut de la plateforme s'appliquent
(journalisé). Seule exception : l'**arrêt prolongé** est détecté par le flux GPS temps réel avec son propre seuil ; la
valeur réglée ici n'est que reportée dans l'alerte (à brancher sur le flux temps réel).

À afficher au support : les **alertes actives** d'une livraison (`activeAlerts`) et leur historique
(`DeliveryAlertRaised` / `DeliveryAlertCleared`), filtrables par type ; elles n'ouvrent jamais d'incident (QO-25).
Réglages techniques du job (fichier de configuration, pas d'écran) : `tnt.delivery.alerts.enabled`, `.cron`,
`.batch-size`.

#### 5.1.1 Implémentation (lot `evt-l1`, module `tnt-administration-core`)

Stockage : `administration.tnt_delivery_thresholds` (une seule ligne `PLATFORM`, créée avec les valeurs ci-dessous) et
`administration.tnt_organization_delivery_thresholds` (une ligne par organisation Agency, colonne vide = valeur de la
plateforme). Chaque écriture contrôle la version (verrou optimiste) et publie `DeliveryThresholdsChanged` dans la même
transaction (outbox).

| Champ JSON | Défaut | Plage acceptée | Sens |
|---|---|---|---|
| `maxDeliveryAttempts` | 3 | 1–10 | tentatives de remise avant retour |
| `relayStorageDays` | 7 | 1–60 | garde au relais (relais d'agence et défaut d'un relais indépendant) |
| `relayStorageReminderDaysBefore` | 1 | 0–59, < garde | rappel avant fin de garde (0 = pas de rappel) |
| `maxFailedReturns` | 3 | 1–10 | retours échoués avant stockage et signalement au support (QO-8) |
| `pickupLateGraceMinutes` | 15 | 0–1440 | enlèvement en retard = fin de fenêtre + cette marge |
| `deliveryLateGraceMinutes` | 0 | 0–1440 | livraison en retard = fin de fenêtre + cette marge |
| `deliveryLateFallbackHoursStandard` / `Express` / `SameDay` | 48 / 24 / 8 | 1–720 | délai de livraison selon l'urgence quand il n'y a pas de fenêtre (`SCHEDULED` a toujours une fenêtre) |
| `etaExceededGraceMinutes` | 30 | 0–1440 | ETA dépassée |
| `gpsSignalLostMinutes` | 10 | 1–1440 | GPS perdu |
| `prolongedStopMinutes` | 20 | 1–1440 | arrêt prolongé |
| `independentRelayStorageMinDays` / `MaxDays` | 3 / 30 (D-130) | 1–60, min ≤ max, toujours renseignées | bornes QO-29 (plateforme seulement) |

Décisions du mainteneur (registre) :
- **D-130** : un relais indépendant fixe sa durée de garde entre **3 et 30 jours** par défaut ; l'administrateur
  règle ces bornes. Elles sont toujours renseignées (changeset `evt-l1-003`) ; la plage 1–60 n'est plus qu'une plage
  de saisie, jamais une valeur de bornes par défaut.
- **D-131** : les plages de saisie ci-dessus sont retenues (tentatives 1–10, retours échoués 1–10, garde 1–60 jours,
  minutes 0 ou 1 à 1440, heures 1–720).
- **D-132** : délais de livraison sans fenêtre retenus (48 h standard, 24 h express, 8 h jour même), réglables par
  l'administrateur et surchargeables par une agence.

| Route | Permission | Effet |
|---|---|---|
| `GET /api/v1/admin/settings/delivery-thresholds` | `tnt:platform:admin` (seul `TNT_ADMIN`, via `*`) | valeurs plateforme + `version` |
| `PUT /api/v1/admin/settings/delivery-thresholds` | `tnt:platform:admin` | remplace toutes les valeurs (tous les champs requis + `version` lue) ; 409 si la version a changé, 422 si une valeur sort de sa plage |
| `GET /api/v1/admin/settings/delivery-thresholds/organizations` | `tnt:platform:admin` | liste des surcharges d'agence |
| `GET /api/v1/organizations/{organizationId}/settings/delivery-thresholds` | `organization:delivery-settings:write` | surcharge de l'organisation (vide si aucune) |
| `PUT /api/v1/organizations/{organizationId}/settings/delivery-thresholds` | `organization:delivery-settings:write` | crée (sans `version`) ou remplace (avec la `version` lue) la surcharge ; champ absent = valeur plateforme ; 422 `ORGANIZATION_NOT_AGENCY` si l'organisation n'est pas une Agency |
| `DELETE /api/v1/organizations/{organizationId}/settings/delivery-thresholds` | `organization:delivery-settings:write` | retire la surcharge (204, 404 si aucune) |
| `GET /api/v1/organizations/{organizationId}/settings/delivery-thresholds/effective` | `organization:delivery-settings:read` | valeurs effectives, chacune avec sa source `PLATFORM` / `ORGANIZATION` |

Routes d'organisation : l'appelant doit appartenir à l'organisation du chemin, sauf l'administrateur de TiiBnTick
(`tnt:platform:admin`) qui agit sur toutes. Par défaut `AGENCY_MANAGER` et `ORG_ADMIN` ont `write` ; `BRANCH_MANAGER`,
`PERMANENT_DELIVERER`, `RELAY_OPERATOR` et `AGENCY_HUB_OPERATOR` ont `read`. Les deux permissions sont assignables :
le responsable de l'agence les donne à qui il veut via les rôles de son organisation.

Codes d'erreur : `DELIVERY_THRESHOLDS_INVALID` (422, liste `violations`), `DELIVERY_THRESHOLDS_EMPTY_OVERRIDE` (422),
`ORGANIZATION_NOT_AGENCY` (422), `DELIVERY_THRESHOLDS_VERSION_CONFLICT` (409), `RELAY_STORAGE_DAYS_OUT_OF_BOUNDS`
(422, contrôle offert au module relais).

Événement `DeliveryThresholdsChanged` sur `tnt.administration.events` (module `administration`, clé = tenant de
l'auteur) : `scope` (`PLATFORM`/`ORGANIZATION`), `change` (`UPDATED`/`DELETED`), `organizationId`, `version`,
`updatedBy`, `updatedAt`, `thresholds`, `previousThresholds` et, pour la plateforme, les bornes relais.

Échéance de garde (QO-30) : `ResolveDeliveryThresholdsUseCase.relayStorageDeadline` = dépôt + min(jours demandés par
le client, durée du relais), rappel J-`relayStorageReminderDaysBefore`. L'ordonnanceur de garde est livré par le lot L2.

Surcharge par une agence (**D-133**) : libre, sans autorisation de l'administrateur. Toute organisation Agency peut
surcharger tous les seuils, selon la permission que son responsable délègue ; l'administrateur voit les surcharges
(`GET .../organizations`, valeurs effectives) et peut en supprimer une. Pas d'interrupteur « cette agence a le droit
de surcharger ».

### 5.2 Argent (séquestre et espèces Kernel)

1. **Livreur trop débiteur en espèces** : consulter les reversements en attente d'un livreur (encaissements Kernel
   non reversés) et **décider** de le suspendre. Aucun blocage automatique : c'est une décision de l'administrateur,
   avec un seuil d'alerte qu'il règle.
2. **Dette d'un livreur responsable d'un retour** (QO-22) : le retour est payé par le fonds d'indemnisation, puis la
   dette du livreur est suivie comme un impayé d'espèces ; même seuil et même décision de suspension.
3. **Fonds d'indemnisation** : suivre son solde, les litiges bloqués faute de fonds (`PENDING_COMPENSATION`) et le
   réapprovisionner.
4. **Recouvrement d'une indemnité auprès du responsable** (QO-26) : l'indemnité payée par le fonds devient une dette
   du responsable (livreur, relais ou agence), suivie et recouvrée par reversement depuis son wallet ; l'administrateur
   voit ces dettes et décide d'une suspension.

5. **Séquestres sans issue automatique** (lot L5) : journal `ESCROW_OUTCOME_UNDECIDED` quand la faute d'un relais ou
   d'une agence (non tranchée) ou une partie absente empêche de choisir entre libération et remboursement ; le
   support décide (libérer ou rembourser) depuis la liste des séquestres `HELD`/`FROZEN` d'une livraison close.
6. **Sélection payée non annulée** : journal `SELECTION_PAYMENT_NOT_UNDONE` — l'élection a échoué mais le
   remboursement du séquestre (ou l'annulation de l'encaissement) aussi ; à rembourser à la main.
7. **Mouvements non enregistrés** : `PAYMENT_UNRECORDED` (séquestre libéré, règlement non écrit),
   `CASH_SETTLEMENT_UNRECORDED`, `CASH_COMMISSION_UNRECORDED` ; rapprochement manuel avec le Kernel.
8. **Reversements espèces en attente par livreur** (miroir `billing.wallet_cash_collections`, statut `COLLECTED`) :
   écran de suivi, base de la décision de suspension (point 1).
9. **Wallet plateforme** : réglage `tnt.payments.platform-wallet-id` (wallet Kernel qui reçoit la part PLATFORM) ;
   non réglé → tout paiement de livraison répond 503 `PLATFORM_WALLET_NOT_CONFIGURED`. À exposer comme réglage
   plateforme de l'administrateur.
10. **Avoir sur remboursement** : la facture est émise au blocage du séquestre ; un séquestre remboursé demande un
    avoir, émis à la main pour le moment.
11. **Dettes envers le fonds** (lot L5 partie 3, QO-22, QO-26 ; registre `billing.wallet_debts`) — API livrée
    (permission `wallet:admin`), écrans à construire :
    - liste `GET /api/v1/billing/debts?status=&limit=` (statuts `PENDING`, `OPEN`, `REPAID`, `CANCELLED`) ;
    - **désigner le débiteur** d'une dette de relais ou d'agence (créée sans débiteur) :
      `POST /api/v1/billing/debts/{id}/assign {debtorId}` (organisation débitrice), qui ouvre le recouvrement ;
    - **relancer le recouvrement** quand il n'a pas pu s'ouvrir (journal `DEBT_RECOVERY_NOT_OPENED`, débiteur sans
      wallet, Kernel indisponible) : `POST /api/v1/billing/debts/{id}/recovery` ;
    - **abandonner** une dette (insolvabilité, geste commercial) : `POST /api/v1/billing/debts/{id}/write-off {note}`,
      qui annule l'encaissement Kernel ; la note est gardée (`WRITTEN_OFF: …`) ;
    - décider d'une **suspension** du débiteur (points 1, 2 et 4) : aucune suspension ni retenue automatique.
12. **Retour payé par le fonds non versé** : journal `RETURN_FUND_PAYMENT_FAILED` (fonds insuffisant, livreur du
    retour sans wallet) — le livreur du retour n'est pas payé et la dette du livreur fautif reste `PENDING` ; à payer
    à la main. Journal `RETURN_DEBT_UNRECORDED` : le fonds paiera mais la dette du livreur fautif n'a pas été écrite ;
    à créer à la main.
13. **Retour élu sans sélection payée** (attribution historique) d'un retour à la charge de la plateforme ou du
    livreur : aucune intention `FUND`, donc aucun paiement automatique ; à régler par l'administrateur.
14. **Livreur remplacé en cours de route** (QO-11 / D-153, 2026-11-27) : à la libération du séquestre, le fonds paie
    chaque remplaçant au prorata de la distance et le livreur d'origine doit la même somme (dette `COURIER_REPLACEMENT`,
    référence `REPLACEMENT:<livraison>:<rang>`, visible dans la liste des dettes du point 11). À traiter à la main :
    - journal `REPLACEMENT_PRORATA_UNDECIDED` (raison `DISTANCE_UNKNOWN` : point de remise ou adresse sans
      coordonnées ; `LEGS_NOT_CHAINED`, `PAYEE_NOT_FIRST_COURIER`, `NO_DISTANCE`) : aucun paiement automatique,
      l'administrateur fixe la part de chaque livreur (étapes dans `billing.wallet_courier_legs`) et la paie ;
    - journal `REPLACEMENT_FUND_PAYMENT_FAILED` (fonds insuffisant, remplaçant sans wallet, refus du Kernel) : le
      remplaçant n'est pas payé et aucune dette n'est ouverte ; à payer à la main ;
    - une livraison payée en espèces (intention `CASH`) avec remplacement n'a aucun partage automatique : le livreur
      qui encaisse reverse ; partage à régler par l'administrateur.

#### 5.2.1 Garantie de retour d'un colis livré au relais (décisions D-147, D-151, 2026-11-26)

- À surveiller : garanties de retour gelées (montant = somme de la livraison au relais, avec ses parts), dégelées au
  retrait, débitées à la fin de la garde ; échecs de gel ou de débit (solde insuffisant).
- **Cas non traités pour le moment** (expéditeur ne payant pas par wallet : espèces, organisation…) : liste à afficher
  à l'administrateur, qui décide au cas par cas ; chaque décision est journalisée (règle 4 bis).
- Le relais n'est jamais payeur d'un retour.
- **Livré (lot L5 partie 3, D-152, 2026-11-27)** : la garantie est un second séquestre Kernel (`RETURN_GUARANTEE`,
  au profit du fonds) confirmé par un second code à la sélection. Une livraison élue sans ce code (élection sans le
  client, paiement après coup) est `NOT_HANDLED` avec la raison `GUARANTEE_NOT_CONFIRMED`, listée ici. Une garantie
  capturée finance le retour par le fonds. Remboursement manuel d'une garantie restée `HELD` : journal
  `SELECTION_PAYMENT_NOT_UNDONE` quand l'annulation d'une sélection n'a pas pu la rembourser.

### 5.3 Incidents (traitement par un gestionnaire)

1. Seul le livreur déclare un incident. Les détections automatiques (retard, perte GPS, arrêt prolongé, types
   `SYSTEM_*`/`AUTOMATION_*`) arrivent au support sous forme d'**alertes**, jamais d'incident (QO-25).
2. Le gestionnaire trie l'incident, organise le remplacement (matching automatique, ou choix de l'agence pour ses
   livreurs), propose une résolution que le livreur confirme ou conteste (`PENDING_CONFIRMATION`, QO-14), escalade.
3. Livraison en échec définitif après un incident sans issue (QO-5) : le gestionnaire peut remplacer le choix par
   défaut (retour si le colis a été enlevé, sinon nouvelle annonce) par une nouvelle livraison ou un retour.
4. Colis dont 3 retours ont échoué (QO-8) : stocké au relais ou par la plateforme, signalé au support, qui décide
   (destruction, don, déclaration de perte).

#### 5.3.1 Lot L3 `evt-incidents` — ce qui demande un administrateur (2026-11-26)

| Besoin | Détail | État |
|---|---|---|
| **File du support TiiBnTick** | un incident bloquant d'une livraison **sans agence** dont l'auto-résolution échoue (aucun remplaçant, aucun relais à portée, reroutage impossible) passe `PENDING_AGENCY_ASSIGNMENT` sans destinataire : la plateforme d'administration doit lister ces incidents (filtre `status = PENDING_AGENCY_ASSIGNMENT`, `agencyId` vide), permettre la prise en charge par un agent support (`agency/handle`, enregistre `IncidentHandlerAssigned`), la désignation d'un remplaçant (`handover/assign-driver`), la proposition puis la résolution | API existante ; écran à faire |
| Résolution d'un incident bloquant | le gestionnaire choisit l'issue de la livraison (`RESUMED`, `FAILED`, `DISPUTE`) et la partie responsable ; `FAILED` met la livraison en échec et le colis en retour ; le paiement est dégelé sauf `DISPUTE` | API existante |
| Incident `TRANSFERRING_TO_HUB` | aucun signal « colis déposé au relais » ne termine l'incident : le support le résout à la main après le dépôt | à brancher (réception du dépôt par l'inventaire) |
| Contestations | un livreur qui conteste la résolution proposée renvoie l'incident en `AGENCY_HANDLING` : à afficher en priorité au gestionnaire | API existante |
| Délai de confirmation automatique d'une résolution (QO-14) | 24 h (`tnt.incident.lifecycle.confirmation-delay`) | propriété de l'application ; à rendre réglable par l'administrateur |
| Délai de passation entre livreurs | 2 h (`tnt.incident.lifecycle.handover-timeout`) : au-delà, la passation est abandonnée et un autre remplaçant est cherché | idem |
| Distance maximale d'un relais pour le transfert (QO-13) | 5 km (`tnt.incident.lifecycle.hub-transfer-max-km`) | idem |
| Surcharge de ces trois délais par une agence | non prévue | à décider |
| Types d'incident déclarables par un livreur (QO-12) | liste servie par `GET /api/v1/incidents/declarable-types` ; types bloquants (QO-2) fixés dans `IncidentBlockingPolicy` | dans le code ; écran de consultation à prévoir, modification = décision |
| Surveillance | incidents bloqués dans un état d'attente (`PENDING_AGENCY_ASSIGNMENT`, `AWAITING_HANDOVER`, `PENDING_CONFIRMATION`) au-delà des délais ; enregistrements de `tnt.incident.events` en DLQ | à faire |

### 5.4 Litiges

- Ouverts par l'expéditeur, le destinataire, le livreur, le point relais ou le support (au nom d'un client).
- Médiation, arbitrage et issue décidés par un gestionnaire habilité ; l'issue fait évoluer automatiquement le colis
  (état d'avant, `LOST`, `DAMAGED`, nouvelle livraison, retour) et l'argent (remboursement ou libération du séquestre,
  indemnisation depuis le fonds) selon la partie responsable (QO-23, QO-24).

#### 5.4.1 Lot L4 `evt-litiges` — ce qui demande un administrateur (2026-11-26)

| Besoin | Détail | État |
|---|---|---|
| **Indemnités en attente** | un litige jugé avec indemnisation (LT7) dont le paiement par le fonds a échoué reste `PENDING_COMPENSATION` (colis toujours `UNDER_DISPUTE`) : liste à afficher, avec le motif (503 `COMPENSATION_UNAVAILABLE`, 409 `COMPENSATION_FUND_INSUFFICIENT`) et un bouton « relancer le paiement » (`POST /api/v1/disputes/{id}/compensation/pay`, `dispute:resolve`) | API existante ; le fonds Kernel est branché au lot L5 (d'ici là, toujours 503) |
| Paiement constaté hors plateforme | enregistrer une référence de paiement versé autrement (`POST /api/v1/disputes/{id}/compensation`, `paymentReference`) : LT9 et issue du colis | API existante ; à réserver à l'administrateur et à tracer |
| Clôture administrative (LT12) | doublon, litige abusif, demande hors champ : type `ADMINISTRATIVE_CLOSURE` et **motif obligatoire** ; refusée en `PENDING_COMPENSATION` ; le colis revient à son état d'avant | API existante |
| Partie responsable | le juge choisit `responsibleParty` (`SENDER`, `RECIPIENT`, `COURIER`, `PLATFORM`, `RELAY`, `AGENCY`, `NONE`) avec l'issue ; elle dira qui paie le retour ou la nouvelle livraison (QO-23, lot L5) | API existante |
| Délai de dépôt d'un litige | option `disputeFilingWindowDays` (7 jours par défaut) : après la clôture du colis, au-delà, l'ouverture est refusée (409) ; réglage du tenant, à défaut du tenant système | option existante ; écran à prévoir |
| Ouverture au nom d'un destinataire | le destinataire n'est pas encore identifié comme acteur sur le colis : le support ouvre le litige pour lui (`SYSTEM` ou gestionnaire) | contournement documenté |
| Fonds d'indemnisation (lot L5) | solde du fonds Kernel (`GET /api/payments/compensation-fund`) à afficher, avec l'historique des indemnités versées (miroir `billing.wallet_compensations` : litige, bénéficiaire, montant, référence Kernel) ; alerte quand un paiement est refusé faute de solde | à faire (lecture des données existantes) |
| Indemnité non enregistrée | marqueur `COMPENSATION_UNRECORDED` : le Kernel a payé mais le miroir n'a pas pu être écrit ; à rapprocher à la main | journal applicatif ; écran à prévoir |
| Bénéficiaire sans wallet | 409 `BENEFICIARY_WALLET_MISSING` : le bénéficiaire doit ouvrir son wallet (connexion à l'application) avant la relance | message au support |
| Surveillance | litiges `PENDING_COMPENSATION` anciens ; litiges expirés automatiquement (LT11) ; enregistrements de `tnt.dispute.events` en DLQ ; consommateur de litiges en échec (les erreurs ne sont plus avalées : relances puis DLQ) | à faire |

### 5.5 Exploitation Kafka

- Consulter la file des messages morts (outbox `DEAD` et file commune des consommateurs) et relancer un message après
  correction.
- Gérer les accès en lecture seule (ACL) des plateformes externes au cluster Kafka partagé.

**Lot L0 `evt-socle-evenements` (socle des événements, 2026-11-26) — ce qui existe dans le Core :**

| Besoin d'administration | Où, dans le Core | État |
|---|---|---|
| **Surveiller** les messages des consommateurs en échec définitif | topic Kafka **`tnt.dlq`** : un enregistrement dont le listener échoue encore après `tnt.kafka.retry.max-attempts` tentatives (4 par défaut, attente 1 s puis ×2) y est publié avec sa clé, sa valeur, ses en-têtes d'origine et les en-têtes `kafka_dlt-original-topic`, `-partition`, `-offset`, `-original-consumer-group`, `-exception-fqcn`, `-exception-message`, `-exception-stacktrace` ; log `ERROR KAFKA_RECORD_DEAD_LETTERED` ; compteur Micrometer `yow.event.consumer.dead_lettered.total{topic,group}` (Prometheus `yow_event_consumer_dead_lettered_total`) | en place (L0) |
| **Relancer** un message de `tnt.dlq` après correction | aucune route d'API à ce jour ; à construire. Effet attendu : republier l'enregistrement sur son topic d'origine (`kafka_dlt-original-topic`) avec l'en-tête `X-Yow-Replay: true` (seul cas où le filtre anti-doublon `event_bus.processed_events` laisse repasser un événement déjà traité). Procédure manuelle décrite par le guide Kafka (lot L9) ; permission à définir (réservée à `TNT_ADMIN`/support technique) | **à construire** |
| Surveiller / relancer les enveloppes **outbox** `DEAD` (côté producteur) | table `event_bus.dead_letter_entries`, log `OUTBOX_ENVELOPE_DEAD`, compteur `yow_event_outbox_dead_total` ; cas d'usage `ProcessDeadLetterUseCase` / `ReplayEventUseCase` (yow-event-kernel) sans route exposée (`kafka-async-wiring.md` §8.2) | cas d'usage en place, **route à construire** |
| Réglages techniques (non métier) | propriétés de déploiement, pas des réglages d'administration : `tnt.kafka.retry.max-attempts`, `tnt.kafka.listener.timeout` (30 s), `tnt.kafka.aggregate-retention-ms` (30 jours), `tnt.kafka.masking.coordinate-decimals` (2, décision QO-17 ; refusé hors 1–3) | en place (L0) |

**Lot L7 `evt-consommateurs-bloquants` (2026-11-26) — nouveaux messages pouvant arriver dans `tnt.dlq` :** ces
consommateurs perdaient silencieusement leurs échecs ; ils les envoient désormais en file morte après 4 tentatives. À
surveiller et relancer depuis la plateforme d'administration (relance = action journalisée, règle 4 bis) :

| Groupe (`kafka_dlt-original-consumer-group`) | Topic d'origine | Effet non appliqué |
|---|---|---|
| `tnt-agency-erp-billing-consumer` | `tnt.billing.wallet.payment.confirmed` | commission d'agence non marquée payée |
| `tnt-agency-erp-actor-consumer` | `tnt.actor.kyc.validated` | livreur d'agence non réactivé après KYC |
| `tnt-market-group` | `tnt.billing.wallet.payment.confirmed`, `tnt.delivery.mission.status_changed` | commande Market non confirmée / non avancée |
| `tnt-actor-freelancer-org-consumer` | `tnt.freelancer_org.*` | rattachement livreur ↔ organisation freelance non appliqué |
| `tnt-delivery-vehicle-consumer` | `tnt.vehicle.assigned_to_mission` | véhicule non enregistré sur la livraison |
| `tnt-notify-core` | `tnt.admin.freelancer_org.*`, `tnt.delivery.freelancer_org.assigned`, `tnt.billing.template.applied` | notification non envoyée (une relance peut renvoyer un canal déjà parti) |
| `tnt-go-freelancer-core-subscription-group` | `gofp.subscription.attempts` | inscription d'un livreur à une annonce non enregistrée |

~~Le planificateur Agency d'expiration des colis en garde (`HubParcelExpiryJob`) attend désormais la fin de son
traitement dans sa fenêtre de verrou.~~ Lot L8 : ce planificateur est **supprimé**. Un colis en garde d'agence passe
`EXPIRED` à la réception de l'événement de colis « non retiré à l'échéance commune » (`tnt.parcel.events`,
`AT_RELAY_HUB → RETURN_REQUESTED`, `returnCause = NOT_COLLECTED_AT_RELAY`, groupe
`tnt-agency-erp-inventory-consumer`) ; un échec arrive dans `tnt.dlq` (à relancer comme les autres). L'échéance se règle
au §5.1 (durée de garde commune), plus dans les propriétés Agency. L'endpoint manuel
`POST /api/v1/tenants/{tenantId}/agency-registry/hubs/expired/process` reste disponible pour un rattrapage.

**Lot L8 `evt-branchement-total` (2026-11-27) — ce qui demande un administrateur :**

| Besoin | Où | État |
|---|---|---|
| **Supprimer les anciens topics** d'un cluster existant (Spring Kafka ne supprime rien au démarrage) : les 42 topics migrés vers les topics d'agrégat (`tnt.delivery.mission.*`, `tnt.delivery.package.*`, `tnt.delivery.hub-deposit.*`, `tnt.delivery.announcement.*`, `gofp.announcement.published`, `tnt.delivery.freelancer_org.assigned`, `tnt.incident.*` sauf `tnt.incident.events`, `tnt.dispute.*` sauf `tnt.dispute.events`, `tnt.inventory.hub.package.*`, `tnt.sync.entity-changed.dlq`) et les 22 topics sans producteur ni consommateur (liste : journal technique du chantier, lot L8). Vérifier d'abord qu'aucune plateforme externe ne les lit encore (§3.7 « lecture externe ») et que leurs consommateurs internes sont à l'offset de fin | `kafka-topics --delete --topic <nom>` par l'exploitation ; liste de référence = `TntTopics` (101 noms) : tout topic `tnt.*` absent de cette liste et des `tnt.trust.*` est un ancien topic | procédure manuelle ; écran « topics orphelins » à construire |
| **Ancienne file morte sync** `tnt.sync.entity-changed.dlq` : la vider (relancer ce qui doit l'être) avant de la supprimer ; les nouveaux échecs sync arrivent dans `tnt.dlq` | cluster Kafka | procédure manuelle |
| **Schéma Market** : les changesets `008-align-*` (label `market-schema-008`) ajoutent 82 colonnes et 5 colonnes de version absentes ; une base existante reprend `delivery_mission_id` → `mission_id` et `transaction_id` → `transaction_ref`. Les anciennes colonnes restent : leur suppression demande une décision (changeset destructif) | Liquibase, `tnt-market-back-core` | migration automatique ; suppression des anciennes colonnes **à décider** |
| **Gabarits de notification renommés** (changeset `i18n-006`) : `notify.freelancer_org.delivery_assigned`, `notify.sub_deliverer.delivery_assigned`, `notify.gofp.delivery.at_relay_hub` remplacent les clés `…mission_assigned` / `…at_relay_point` ; une modification d'administrateur de l'ancienne clé est reportée automatiquement. L'écran d'édition des gabarits doit montrer les nouvelles clés | catalogue `i18n.message` | migration automatique |

Exploitation : sur un cluster où `tnt.delivery.events` existait déjà, sa rétention n'est **pas** modifiée par le
démarrage (Spring Kafka ne change pas la configuration d'un topic existant) ; l'appliquer une fois
(`kafka-configs --alter --entity-type topics --entity-name tnt.delivery.events --add-config retention.ms=2592000000`).

#### 5.5.1 Lot L2 `evt-etats-colis-livraison` — ce qui demande un administrateur (2026-11-26)

| Besoin | Où, dans le Core | État |
|---|---|---|
| **Mise à jour d'une base** qui contient encore des livraisons `TIMED_OUT` ou `SLA_BREACHED` | le changeset `evt-l2-005` arrête la migration (précondition HALT) : un exploitant doit d'abord décider de l'état de ces lignes (aucune règle automatique, ces états n'existent plus) | procédure manuelle |
| **Colis qui avaient plusieurs codes de suivi** (anciennes livraisons) | journal `WARN` « D-136: N parcel(s) carried several distinct delivery codes » au démarrage ; ces colis reçoivent un code neuf, les anciens restent valables | surveillance (journal) ; écran à construire |
| **Retours bloqués après la limite de retours échoués** (QO-8) | journal `WARN` « reached N failed returns … support to be notified » ; le colis reste `RETURN_REQUESTED` en stockage, aucun brouillon n'est ouvert | **file du support à construire** (décision : destruction, don, déclaration de perte, §5.3 point 4) |
| **Garantie de retour non traitée ou en échec** (D-147, D-151, §5.2.1) : livraisons vers un relais dont `return_guarantee_status` vaut `NOT_HANDLED` ou `FAILED`, avec la raison (`SENDER_NOT_PAYING_BY_WALLET`, `PAYMENT_METHOD_UNKNOWN`, `NO_GUARANTEE_HELD`, `WALLET_GUARANTEE_NOT_CONNECTED`, `WALLET_OPERATION_FAILED`) ; l'administrateur décide au cas par cas | `tnt_deliveries.return_guarantee_status` / `return_guarantee_reason` (index partiel), log `RETURN_GUARANTEE_NOT_HANDLED` | liste et décision **à construire** ; appel wallet réel livré au lot L5 partie 3 (raison supplémentaire `GUARANTEE_NOT_CONFIRMED`) |
| **Brouillons de retour** (annonces `kind = RETURN`, statut `DRAFT`, toujours au nom de l'expéditeur, D-147) jamais publiés par leur payeur ; brouillons sans destinataire (livraison sans annonce, contact de l'expéditeur inconnu) | `tnt_delivery_announcements` (`kind`, `creator_role`) | liste et relance du payeur **à construire** |
| **Garde au relais** : rappels envoyés, livraisons échouées pour colis non retiré (D12, `NOT_COLLECTED_AT_RELAY`) | ordonnanceur `RelayStorageScheduler` (toutes les heures, `tnt.delivery.relay-storage.cron`, verrou ShedLock `delivery-relay-storage`) ; événements `DeliveryAlertRaised` (`RELAY_STORAGE_EXPIRING`) et `DeliveryFailed` | automatique ; tableau de suivi à construire |
| **Colis d'un relais révoqué** (D-129) | liste des livraisons en garde : `DeliveryRepository.findAtRelayHub(hubId)` ; la décision retour ou transfert appartient au lot de révocation | point d'accroche en place |
| Durée de garde d'un **relais indépendant** (GOFP `storageDays`) | refusée hors bornes (422 `RELAY_STORAGE_DAYS_OUT_OF_BOUNDS`, D-130) ; les bornes sont réglées au §5.1 | en place |
| Réglage technique | `tnt.delivery.tracking.public-rate-limit-per-minute` (30, suivi public par IP, D-137), `tnt.delivery.tracking-code-backfill.enabled` | propriétés de déploiement |

**À journaliser** (journal immuable §4 bis, le module n'existe pas encore) : la décision prise sur les lignes
`TIMED_OUT`/`SLA_BREACHED` avant migration ; l'issue d'un litige appliquée au colis (`ParcelDisputeUseCase.applyDisputeOutcome` :
retour, nouvelle livraison, perte, avarie, état d'avant — acteur, motif, montant) ; la décision du support sur un colis
bloqué après la limite de retours ; la publication ou l'abandon d'un brouillon de retour par le support au nom du
payeur ; toute modification de la durée de garde d'un relais par un administrateur ; toute décision de l'administrateur
sur une garantie de retour non traitée ou en échec (montant, payeur, motif).

### 5.6 Comptabilité tenue dans le Kernel (lot L6, 2026-11-27)

1. **Réglage du livre plateforme** : `tnt.accounting.kernel.platform-organization-id`
   (`TNT_ACCOUNTING_PLATFORM_ORGANIZATION_ID`), l'organisation Kernel qui porte les comptes de la plateforme. Non
   réglé → commissions, indemnités et retours payés par le fonds restent `FAILED`
   (`PLATFORM_ORGANIZATION_NOT_CONFIGURED`). À exposer comme réglage plateforme.
2. **Faits non écrits** : `GET /api/v1/accounting/kernel-entries?status=FAILED` (permission `accounting:admin`) —
   raisons `PLATFORM_ORGANIZATION_NOT_CONFIGURED`, `KERNEL_REFUSED: <code>`, `KERNEL_UNAVAILABLE: …` ; journal
   `ACCOUNTING_NOT_BOOKED`. **Rejouer** après correction : `POST /api/v1/accounting/kernel-entries/{id}/retry` (une
   écriture déjà créée dans le Kernel est retrouvée par sa référence, jamais dupliquée). Écran à construire.
3. **Faits ignorés** (`SKIPPED`) : commission encore `DUE`, facture sans organisation émettrice
   (`NO_ISSUER_ORGANIZATION`), montant nul ; consultables par le même endpoint, pour contrôle.
4. **Plan de comptes** (QO-20) : 4671, 4672, 706, 6788, 411 créés à la demande dans chaque livre ; correspondances
   dans `accounting.kernel_account_links` / `kernel_journal_links`.
5. **Journal local** (QO-21) : en lecture seule ; sa **suppression** demande l'accord explicite du mainteneur
   (changeset destructif), à proposer depuis la plateforme d'administration une fois la reprise validée.

## 6. Points relevés lors de l'intégration des lots Go-Freelancer (2026-11-26)

- Points relais : ajout du statut `REVOKED` (décision D-129) — l'administrateur peut révoquer définitivement un relais,
  avec un motif et une notification ; les colis en garde sont retournés ou transférés. Action non réversible depuis
  l'interface. Implémenté, voir § 6.1.
- Adresses migrées : conversion vers l'acteur du cœur décidée (D-128), faite par le changeset
  `gdd-l4-417-legacy-owners-to-actor-ids`. **Adresses orphelines : supprimées (décision D-134)** par le changeset
  `gdd-l4-420-delete-orphan-address-links` (liens sans correspondance sûre vers un acteur, ambigus ou qui auraient
  doublé un lien de l'acteur ; nombre supprimé en `NOTICE`). Aucun outil de rattachement n'est à prévoir pour les
  adresses.
- Dépôts relais hérités orphelins (`tnt_hub_package_entries` venues de `relay_deposits` dont le propriétaire ou le
  retireur est resté un ancien id) : **gardés**, comptés en `NOTICE` par `gdd-l4-420`. L'administrateur doit pouvoir
  **les lister et les rattacher à la main à un acteur** (outil à prévoir, aucune route aujourd'hui).
- `PUT /api/v1/admin/tnt-go-freelancer/relay-points/{id}/suspend` et `/activate` sont branchés sur le service
  (statut local, profil d'opérateur relais du cœur, notification) ; ils répondaient 200 sans effet jusqu'ici.
- `scripts/e2e/e2e-freelancer-courier.sh` inclut désormais l'étape d'agrément par un administrateur
  (`PATCH /api/v1/gofp/freelancer-profiles/{id}/status?status=APPROVED`, `gofp-admin:manage`).

### 6.1 Révocation d'un point relais (D-129)

Mise en œuvre de l'agrément (D-138 à D-140) dans le cœur : `HubApprovalStatus`, `IManageHubApprovalUseCase`
(approuver, rejeter, suspendre, réactiver ; redemander l'état courant ne fait rien), `ICheckHubAcceptsDepositsUseCase`
(garde de dépôt de l'inventaire). Routes du cœur (D-141) : voir § 2.1, permission `relay:approve` ; la révocation
garde `relay:revoke`. Les routes Go-Freelancer délèguent au cœur ; un changement fait par une route du cœur est
recopié sur le relais Go par `GofpRelayApprovalApplier` (écouteur `IRelayHubApprovalListener`, une seule
notification). Un hub neuf naît `PENDING`. Agrément initial des hubs existants (`gdd-l4-421`, `gdd-l4-422`) : statut du
relais Go qui s'appuie sur le hub ; sinon `APPROVED` (hubs non liés à un relais Go, qui fonctionnaient sans agrément) ;
`REVOKED` pour les hubs déjà révoqués (`gdd-l4-418`, état d'exploitation passé à `PERMANENTLY_CLOSED`) ; cas
ambigus (relais aux statuts différents ou inconnus) comptés dans les journaux de migration et laissés `APPROVED`.
Effet D-140 : un hub non `APPROVED` n'apparaît plus dans les recherches de hubs proches et refuse tout nouveau dépôt
dans l'inventaire (409). Destination d'un besoin Go (D-144), vérifiée dans le registre des hubs du cœur à la création
du besoin : inconnue (ou relais Go sans hub dans le cœur) → 404 ; hub non agréé ou fermé, relais Go non agréé ou mis
en pause (D-145) → 409 ; un hub plein (état `FULL` ou plus aucune place libre) → 409 aussi (D-146), le dépôt
restant vérifié à l'arrivée du colis. Les listes de relais suggérés (`/active-approved`, `/available`) ne gardent
que les relais dont le hub du cœur est agréé, `ACTIVE` et a au moins une place libre. Hubs d'Agency : hors de ce registre, non concernés (D-143) ;
les retraits restent possibles. Le retour des colis en fin de garde n'est pas branché ici (lot L2 des événements :
point d'accroche `FindOverduePackagesUseCase.findOverduePackages`).


| Élément | Contenu |
|---|---|
| Où vit le statut | Cœur, module géo : statut d'agrément `relay_hubs.approval_status` (`PENDING`, `APPROVED`, `SUSPENDED`, `REJECTED`, `REVOKED`, D-138, changeset `gdd-l4-421`), séparé de l'état d'exploitation `relay_hubs.status` (`ACTIVE`, `FULL`, `TEMPORARILY_CLOSED`, `PERMANENTLY_CLOSED`) ; la révocation porte `revocation_reason`, `revoked_at`, `revoked_by_actor_id` (`gdd-l4-418`). La base refuse un agrément `REVOKED` sans date ; le motif est imposé par le domaine pour toute nouvelle révocation (il peut manquer sur un relais révoqué avant D-129). Le relais Go-Freelancer recopie l'agrément du hub. |
| Route du cœur | `POST /api/v1/geo/tenants/{tenantId}/hubs/{hubId}/revoke`, corps `{"reason": "..."}` (1 à 1 000 caractères). |
| Route Go-Freelancer | `PUT /api/v1/admin/tnt-go-freelancer/relay-points/{id}/revoke`, corps `{"reason": "..."}`, `?loginUrl=` facultatif (lien du courriel). Délègue au cœur quand le relais a un hub ; sinon révocation locale avec les mêmes effets. |
| Permission | `relay:revoke`, donnée à aucun rôle : seul `TNT_ADMIN` (`*`) la porte. |
| Transitions | Machine d'agrément du hub, la même que celle des livreurs (D-135, D-138) : `PENDING` → `APPROVED` / `REJECTED` ; `APPROVED` ↔ `SUSPENDED` ; `APPROVED` ou `SUSPENDED` → `REVOKED` ; `REJECTED` et `REVOKED` sont finals ; tout le reste répond 409 (`HubApprovalStatus`, testé sur les 25 couples). Le relais Go-Freelancer suit la même machine et le hub du cœur fait foi : si le cœur refuse, la route Go répond 409 sans rien écrire. Sur un hub `REVOKED`, rouvrir, fermer ou déposer un colis est refusé (409). |
| Effets | Hub fermé aux dépôts, comme tout hub non `APPROVED` (D-140 : plus proposé, plus de nouveau dépôt ; l'occupation ne rouvre jamais un hub fermé) ; relais Go-Freelancer `REVOKED` et inactif ; profil d'opérateur relais désactivé ; notification in-app à l'opérateur dans la langue de son compte (`notify.gofp.relay_status.revoked_reason`, paramètre `reason`) et courriel « compte révoqué ». |
| Réponses | 200 avec la révocation : statut (route du cœur : `approvalStatus` et `operatingStatus` ; route Go : `status`), motif, date, administrateur, `custodyKnown`, `parcelsInCustody` (entrée, colis, code de suivi, propriétaire, date de dépôt). 400 motif absent, 401 non authentifié, 403 sans `relay:revoke`, 404 relais inconnu, 409 transition refusée. |
| Colis en garde | **Non décidé ici** : la révocation ne déplace ni ne retourne aucun colis. Elle publie la liste dans la réponse et la trace dans les journaux (`WARN`). Les colis peuvent encore sortir du hub révoqué (retrait, transfert). |

**À brancher avec le cycle de vie du colis (lot L2 du chantier événements)** : déclencher pour chaque colis de
`parcelsInCustody` un retour à l'expéditeur ou un transfert vers un autre relais (D-129 : « retour ou transfert ») ;
publier un événement Kafka de révocation (aucun topic ajouté ici : `TntTopics` appartient au lot L0) ; prévenir les
propriétaires des colis. L'administrateur devra pouvoir **suivre ces colis jusqu'à leur sortie** et choisir le relais
de transfert si la règle ne le fixe pas.

### 6.2 Agrément et suspension des points relais (décisions D-138 à D-140, 2026-11-26)

- **Statut d'agrément du hub dans le cœur (D-138)** : le hub du cœur porte un statut d'agrément distinct
  (`PENDING`, `APPROVED`, `SUSPENDED`, `REJECTED`, `REVOKED`), qui fait foi pour les hubs relais du cœur (Go-Freelancer, relais indépendants ; pas pour Agency, D-143) ; l'état
  d'exploitation (ouvert, plein, fermé temporairement) reste séparé. Le relais Go recopie l'agrément du cœur. La
  révocation (D-135) se décide sur ce statut : seulement depuis `APPROVED` ou `SUSPENDED`.
- **Suspension / réactivation (D-139)** : suspendre seulement un relais `APPROVED`, réactiver seulement un relais
  `SUSPENDED` ; tout autre cas répond 409 ; redemander l'état courant ne fait rien ; un relais en attente ne s'active
  qu'en passant par la validation (KYC).
- **Effet d'une suspension (D-140)** : le relais ne peut plus être choisi comme destination et refuse tout nouveau
  dépôt ; les colis déjà en garde restent retirables jusqu'à la fin de leur garde, puis repartent en retour selon les
  règles habituelles.
- À journaliser (règle 4 bis) : agrément, refus, suspension, réactivation, révocation, avec motif.

### 6.3 Routes d'agrément des points relais dans le cœur (décisions D-141, D-142, D-144, 2026-11-26)

- **D-141** : le cœur expose les routes d'agrément de tout hub relais (approuver, refuser, suspendre, réactiver,
  révoquer) sous une permission `relay:approve` réservée à l'administrateur de TiiBnTick ; les routes d'administration
  Go-Freelancer restent et délèguent au cœur. La plateforme d'administration s'appuie sur les routes du cœur.
- **D-142** : `PATCH /api/v1/gofp/relay-point-profiles/{id}/status` exige la même permission d'administrateur que
  suspendre/activer (plus seulement « personnel ») ; action journalisée (règle 4 bis).
- **D-144** : toute destination relais d'un besoin Go-Freelancer est vérifiée dans le registre du cœur : inconnue ou
  non agréée → besoin refusé.
- **D-143 (Agency)** : les relais/hubs enregistrés côté Agency restent gérés par la plateforme Agency ; une « agence »
  est une organisation commerciale à antennes (siège, agences de ville ou de quartier) et **ne dispose pas de point
  relais**. Aucune règle d'agrément des relais ne leur est appliquée par le Core.
- **D-145** : un relais agréé mais mis en pause par son propriétaire n'est pas proposé et un besoin qui le vise est
  refusé (409) ; les colis déjà en route vers lui restent gérés normalement.
- **D-146** : un relais agréé mais **plein** (capacité atteinte) n'est pas proposé et un besoin qui le vise est refusé.

### 6.4 Clôture `gdd-final` : lignes héritées sans contrepartie (décision D-154, 2026-11-27)

La migration de clôture du chantier de dédoublonnage Go-Freelancer s'arrête (HALT) avant toute suppression si une
ligne héritée n'a pas de contrepartie dans le cœur. Le démarrage de l'application échoue alors, volontairement.
La plateforme d'administration doit offrir :

- **Rapport de clôture** : lecture du recensement `SELECT * FROM public.gdd_final_uncovered() WHERE uncovered > 0`
  (une ligne par élément : annonces, réponses, colis, évaluations, paiements payés, dépôts relais, adresses, liens
  d'adresse, grilles tarifaires, colonnes héritées des livraisons et des besoins, personnes, clients, livreurs,
  véhicules, utilisateurs, références vivantes vers ces tables), avec le détail des lignes concernées.
- **Décision manuelle par ligne** (journalisée, section 4 bis) : rattacher la ligne à son acteur ou à son objet du
  cœur, la recopier dans le cœur, ou la supprimer avec un rapport signé. Une fois le recensement à zéro, la clôture
  passe au démarrage suivant.
- Aucune contrepartie n'est exigée, par décision, pour les mots de passe locaux et jetons de réinitialisation
  (authentification Kernel, D-054) ni pour l'ancien fil de notifications local (sans lecteur, lot L4-2) ; les
  données de démonstration du changelog 040, le client invité et l'ancien administrateur local sont ignorés.
- La table `notifications` remplie par la boîte in-app du lot C-36 de `main` est elle aussi supprimée par la
  clôture (gdd-final-907) : la boîte in-app vit désormais dans le Kernel (D-157, §6.6).

### 6.5 Approbation automatique des points relais (lot C-57 de `main`, décision D-158, 2026-11-27)

- Tant que la plateforme d'administration n'existe pas, un point relais enregistré par son propriétaire est approuvé
  aussitôt (`tnt.gofp.relay.auto-approve`, `TNT_GOFP_RELAY_AUTO_APPROVE`, **vrai par défaut**), et les relais encore
  `PENDING` sont rattrapés au démarrage. L'approbation suit le chemin de l'administrateur (D-138) : hub du cœur
  provisionné puis agréé d'abord, le relais recopie ; KYC `VERIFIED` ; aucun e-mail ni push. Les relais `REJECTED`,
  `SUSPENDED` ou `REVOKED` ne sont jamais touchés.
- La plateforme d'administration doit offrir : **le réglage** (réactiver l'approbation manuelle en passant
  l'interrupteur à faux), **la liste des relais approuvés automatiquement** (motif KYC « auto-approve ») pour une
  revue a posteriori, et les actions habituelles sur chacun (suspendre, révoquer, §6.2) ; action journalisée
  (section 4 bis).

### 6.6 Boîte de notifications in-app (lot C-36 de `main`, décision D-157, 2026-11-27)

- La boîte des trois rôles (expéditeur, livreur, propriétaire de relais) est tenue par le Kernel (canal `IN_APP`,
  application `tiibntick-go`) ; le Core ne garde aucune copie. La rétention et la suppression relèvent du Kernel
  (K17, K18 : aucune route de suppression aujourd'hui).
- La plateforme d'administration doit pouvoir **consulter** les messages in-app d'un utilisateur (support) et la
  **trace d'idempotence** (`event_bus.processed_events`, groupe `gofp-in-app`), et **renvoyer** un message perdu
  (action journalisée, section 4 bis).

### 6.7 Administrateurs de la plateforme — TNT_ADMIN (décisions D-159 à D-175, 2026-11-27)

Conception : [registre-administrateurs-plateforme.md](../architecture/chantiers/registre-administrateurs-plateforme.md).

- **Écran « Administrateurs »** : liste des TNT_ADMIN actifs et de l'historique (`GET /api/v1/admin/platform-admins`) :
  bénéficiaire, type (SYSTEM_OWNER / ASSIGNED), attributeur, **tenant d'obtention**, tenant système à l'attribution,
  dates, auteur et motif de révocation.
- **Attribuer** TNT_ADMIN à un compte Kernel, sans qu'il soit membre du tenant système (tout TNT_ADMIN).
- **Révoquer** un ASSIGNED (tout TNT_ADMIN, motif). L'entrée SYSTEM_OWNER du tenant système en vigueur n'a **pas**
  de bouton de révocation (409 côté Core).
- **Révocation de masse** des TNT_ADMIN dont le tenant d'obtention diffère du tenant système : aperçu, puis
  confirmation avec le jeton de l'aperçu (SYSTEM_OWNER seulement).
- **Réclamer la propriété** : bouton pour le propriétaire connecté dans le tenant système (l'entrée est aussi créée
  automatiquement à sa connexion).
- **Alertes** : chaque attribution ou révocation envoie un courriel au bénéficiaire et au propriétaire, plus un
  message in-app au propriétaire ; chacun le reçoit dans la langue de son compte. Textes modifiables dans le
  catalogue i18n (`notify.roles.platform_admin.granted|revoked`, variantes objet, corps, in-app).
- **Transfert de propriété** (D-177) : quand un nouveau propriétaire Kernel du tenant système se connecte, l'écran
  montre l'ancien propriétaire passé en ASSIGNED (révocable) avec le motif du transfert.
- **Journal des lectures inter-tenant** (D-178) : vue du journal `tnt.audit.cross-tenant` (quel TNT_ADMIN a lu quel
  tenant, quand, quelle route) pour l'audit.
- **Rôles d'agence/organisation** (D-176) : un rôle d'agence attribué ailleurs que par le Core n'est plus reconnu ;
  l'écran des attributions par tenant doit permettre de (ré)attribuer ces rôles via le Core.
- Changement de `TNT_SYSTEM_TENANT_ID` : l'écran doit signaler les entrées dont le tenant d'obtention n'est plus le
  tenant système et proposer la révocation de masse.

