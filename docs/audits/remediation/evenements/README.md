# Événements par agrégat — impacts des contrats

**Chantier :** cycle du colis, livraison, incidents, litiges, paiement séquestre, Kafka par agrégat (décisions du
2026-11-26). **Sources :** [décisions](../../../architecture/chantiers/decisions-evenements-colis-livraison.md),
[spécification cible](../../../architecture/chantiers/cible-evenements-colis-livraison.md) (§3 catalogue Kafka,
§6 lots, §7 impacts), [registre des décisions](../../../decisions/registre-decisions.md). Le guide Kafka HTML local
(lot L9, jamais publié en ligne) reprend ces informations pour les équipes externes :
[`docs/kafka/guide-kafka-tiibntick.html`](../../../kafka/guide-kafka-tiibntick.html), généré depuis le code par
`docs/kafka/gen_kafka_guide.py` (le relancer après tout changement de topic, de listener ou de schéma).

Dossier créé par le lot **L0** (`evt-socle-evenements`). Chaque lot complète les lignes qui le concernent (colonne
« Lot »), avant et après, dans le même commit que le changement.

## Documents (un par public)

| Fichier | Public | Contenu |
|---|---|---|
| [mobile-bff.md](mobile-bff.md) | Mobile et BFF | §7.1 de la spécification : statuts, nouveaux champs et routes, lecture Kafka éventuelle |
| [agency-et-modules.md](agency-et-modules.md) | Agency, Link, Market, Point Relais, modules du Core | §7.2 à §7.5 : topics à consommer, comportements changés, socle réutilisable |
| [journal-technique.md](journal-technique.md) | Mainteneurs du Core | lot par lot : quoi, pourquoi, preuve, points non tranchés |
| [plateforme-administration/specifications.md](../../../plateforme-administration/specifications.md) | Plateforme d'administration | §7.6 : réglages, surveillance, relance (tenu dans le document commun, §5) |

La spécification (§7) nomme des fichiers `impacts-<plateforme>.md` ; les consignes communes des agents du chantier
fixent un fichier par public (`mobile-bff.md`, `agency-et-modules.md`, `journal-technique.md`, comme
`../gofp-dedup/`). Correspondance : `impacts-mobile-bff.md` → `mobile-bff.md` ; `impacts-agency.md`,
`impacts-link.md`, `impacts-market.md`, `impacts-point-relais.md` → `agency-et-modules.md` (une section par
plateforme) ; `impacts-administration.md` → `docs/plateforme-administration/specifications.md` §5.

Les impacts déjà notés dans [`../gofp-dedup/`](../gofp-dedup/README.md) ne sont pas recopiés.

## Avancement par lot

| Lot | Mobile + BFF | Agency et modules | Journal technique |
|---|---|---|---|
| L0 `evt-socle-evenements` | [L0](mobile-bff.md#lot-l0--socle-commun-aucune-rupture) | [L0](agency-et-modules.md#lot-l0--socle-commun) | [L0](journal-technique.md) |
| L1 `evt-seuils-plateforme` | — | [L1](agency-et-modules.md#lot-l1-evt-seuils-plateforme-seuils-de-livraison-et-de-garde) | [L1](journal-technique.md#lot-l1-evt-seuils-plateforme) |
| L2 `evt-etats-colis-livraison` | ruptures : valeurs de statut, échec de remise par code, suivi public masqué | Agency (mapper, seuils), Link (suivi masqué), matching unique, trust, realtime | tables, changesets, liste des doubles écritures `TODO(L8)` |
| L3 `evt-incidents` | ruptures : déclaration par le livreur affecté, `DETECTED` retiré, incident non bloquant sans pause, alertes à la place des incidents automatiques ; ajouts : confirmation de résolution, acceptation du remplaçant | Agency (traitement des incidents d'agence, proposition de résolution), GOFP (notification de retard), modules du Core (plus d'incident automatique) | machine §1.3, alertes et seuils de l'administration, limites |
| L4 `evt-litiges` | ruptures : ouverture réservée aux parties du colis, délai de dépôt, jugement typé (`responsibleParty`, montant), clôture administrative seule, retrait par le demandeur ; ajouts : colis `UNDER_DISPUTE` puis issue, paiement de l'indemnité et relance | Agency (garde inchangée pour le gestionnaire), Point Relais (ouverture par l'opérateur), Market (litige sans colis), modules du Core | machine §1.4, effets colis, indemnisation 503 jusqu'à L5, persistance corrigée, limites |
| L5 `evt-paiement-sequestre` | partie 1 : indemnités versées par le fonds Kernel (409/503 détaillés), `compensation.paid` publié, `compensation.initiated` supprimé ; partie 2 : sélection payée par séquestre ou espèces, paiement par séquestre, receive-code supprimé, reversement espèces, statut `escrowStatus`, erreurs ProblemDetail ; partie 3 : dettes envers le fonds (`/api/v1/billing/debts`), retours payés par le fonds (`FUND`/`FUNDED`), second code de garantie pour un relais (D-152), `split_executed` supprimé | billing-wallet (fonds, séquestre, encaissements, consommateurs), GOFP, delivery-core, bootstrap | fonds §4.3, séquestre §4.1-§4.2, deux identités Kernel, limites, questions au Kernel |
| L6 `evt-comptabilite-kernel` | rupture : `POST /api/accounting/journal-entries` → 410 ; ajouts : administration `/api/v1/accounting/kernel-entries`, `compensation.paid` des retours (`reason`), `commission.calculated` `SETTLED` au reversement espèces, `InvoicePaid.issuerOrgId` | accounting-core, billing-wallet, dispute-core, billing-invoice, bootstrap, Agency | écritures QO-20, idempotence et reprise, K13/K14, limites |
| L8 `evt-branchement-total` | ruptures : types de la synchronisation (`DELIVERY`, `PARCEL`…), SSE de suivi aux états du colis, anciens topics supprimés, clés de gabarits renommées ; REST inchangé | Agency (inventaire et expiration sur `tnt.parcel.events`, job supprimé, propriétés retirées), Market (schéma, constantes), GOFP, tous les modules (101 topics, ports renommés) | annexe 9.1 appliquée et corrigée, preuves par consommateur, scénario complet, ArchUnit, vocabulaire, limites |
| L9 `evt-guide-kafka-html` | guide [`guide-kafka-tiibntick.html`](../../../kafka/guide-kafka-tiibntick.html) : connexion, distinguer les événements d'un topic, schémas, groupe et ACL lecture seule par plateforme | même guide (catalogue des 101 topics avec producteurs et consommateurs relevés sur le code, ancien → nouveau, relance depuis `tnt.dlq`) | générateur `docs/kafka/gen_kafka_guide.py` |

