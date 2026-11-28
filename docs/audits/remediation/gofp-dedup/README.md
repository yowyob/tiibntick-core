# Dédoublonnage du module Go-Freelancer avec les couches inférieures

**Branche :** `feat/single-system-tenant` · **Décidé le :** 2026-11-24 (utilisateur).

## Règle

Le module `tnt-go-freelancer-point-back-core` (couche `coreBackend`) **utilise ce que fournissent les couches
inférieures** (delivery-core, actor-core, resource-core, geo-core, tp-core, notify-core, billing, trust…) et ne
fait lui-même **que ce qu'elles ne font pas**.

## Décisions prises avec l'utilisateur

| Sujet | Décision |
|---|---|
| Périmètre | Énumérations, statut de livraison, **et entités** (annonces, véhicules, évaluations, paiements, notifications locales, adresses, profils, relais). |
| Colis | Utiliser l'agrégat `Parcel` du cœur. |
| Contrats d'interface | Préférer modifier les mécanismes internes ; une rupture est permise quand elle est logique et nécessaire, décidée et **documentée** par moi, jamais en silence. |
| Délégation | Synchrone, derrière le contrôleur : la route et le DTO Go-Freelancer appellent les cas d'usage des modules inférieurs dans la même requête. |
| Identifiants | **Ids du cœur partout**, sans période de transition. Non négociable pour les autres développeurs. |
| Données existantes | Migrées vers les ids du cœur (Liquibase, jamais de changeset publié modifié). |
| Manques dans les couches inférieures | Complétés **par ajout**, de façon générique, pour tout `coreBackend`. |
| Propre à Go-Freelancer (reste dans le module) | messages de négociation, invitations à un besoin, codes de remise, favoris, profils visuels et photos de relais, horaires d'ouverture — rattachés aux ids du cœur. |

## Documents

- [mobile-bff.md](mobile-bff.md) — ce que le Mobile et le BFF doivent changer.
- [agency-et-modules.md](agency-et-modules.md) — ce qui a été ajouté dans les modules inférieurs et que les autres modules `coreBackend` (Agency, Link, Market) peuvent réutiliser.
- [journal-technique.md](journal-technique.md) — journal lot par lot (quoi, pourquoi, preuve).
- [plateforme-administration/specifications.md](../../../plateforme-administration/specifications.md) (anciennement `validations-administrateur.md`) — toutes les étapes métier qui demandent une
  validation par un administrateur (dont l'agrément des nouveaux livreurs), pour la future plateforme d'administration.

## Reports acceptés (lot L4, revue du 2026-11-26)

Écarts connus, acceptés à la revue de L4 et laissés à un lot ultérieur :

- **`delivery_person_pricing`** (soldé par la clôture `gdd-final`, 2026-11-27) : plus lue que par la reprise unique au démarrage (`LegacyPricingBackfillService`, supprimée à la clôture), plus
  écrite. L'entité de lecture `FreelancerPricingPolicy` et la table restent jusqu'au changeset de clôture, à passer une fois
  le comptage « grilles sans politique = 0 » constaté dans les journaux de démarrage.
- **Alignement `gofp_relay_points.id` → id du `RelayHub`** (3.15 e) : non fait ; le code résout les deux ids. Demande un
  changeset de repointage sur des tables d'autres lots.
- **Dimensions / quota de dépôts du hub geo** (3.15 c) : non ajoutés (choix « dimensions + unité » ou « capacité volumique »
  ouvert ; le quota relève de l'abonnement relais, côté Go).
- **Statut `REVOKED`** (H2) : à ajouter une seule fois, dans L3 (livreurs et relais).
