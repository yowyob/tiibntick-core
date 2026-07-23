# Plan de remédiation — TiiBnTick Core

Ce dossier contient le **plan d'implémentation** issu de l'[audit technique](../index.html) (`docs/audits/`). Différence avec `docs/audits/raw/` :

- `docs/audits/raw/` = **constat** (diagnostic figé au 17 juillet 2026, preuve par preuve). Ne pas modifier rétroactivement même si le plan change — c'est la trace de l'état observé.
- `docs/audits/remediation/` = **plan d'action** (document vivant, mis à jour à chaque décision d'implémentation). C'est ici que vivent les arbitrages ultérieurs à l'audit (ex. délégation paiement/facturation au Kernel, décidée le 17/07/2026).

## Fichiers

**Commencer par [implementation-plan.md](implementation-plan.md)** — vue d'ensemble de bout en bout, séquencement, et table de correspondance garantissant qu'aucun des 145 constats des 7 audits n'a été omis.

| Fichier | Contenu | Statut |
|---|---|---|
| [implementation-plan.md](implementation-plan.md) | Vue d'ensemble, séquencement des 3 phases, table de correspondance audit→phase | — (index) |
| [phase-0-critical.md](phase-0-critical.md) | Phase 0 (bloquant production, 3–5 sem.) — sécurité, paiement, événementiel, scale-out, i18n cassé | 🟡 Planifié |
| [phase-1-hardening.md](phase-1-hardening.md) | Phase 1 (durcissement, 1–3 mois) — BFF Link temps réel, résilience, Kafka fiable, scalabilité | 🟡 Planifié |
| [phase-2-target-architecture.md](phase-2-target-architecture.md) | Phase 2 (trajectoire long terme, 3–18 mois) — infrastructure distribuée, extraction conditionnelle | 🟡 Planifié |
| [workstream-payment-billing-kernel-delegation.md](workstream-payment-billing-kernel-delegation.md) | Détail du chantier B de la Phase 0 : délégation complète des paiements et de la facturation au Kernel | 🟡 Planifié |

Chaque phase contient des cases à cocher (`- [ ]`) pour chaque constat d'audit repris — cochées par commit au fur et à mesure de l'implémentation. La page HTML calcule et affiche automatiquement la progression.

## Convention de statut

- 🟡 Planifié — plan écrit et validé, implémentation non commencée
- 🔵 En cours — implémentation démarrée
- 🟢 Terminé — livré et vérifié (tests + `mvn verify`)
- 🔴 Bloqué — en attente d'une décision ou d'une dépendance externe (ex. évolution Kernel)

## Directive de fond actée le 17/07/2026

L'utilisateur a tranché un point d'architecture qui recadre une partie du P0 sécurité : **TiiBnTick Core ne gère plus aucun mécanisme de paiement en propre**. Tout ce qui touche aux modes de paiement (Mobile Money, Stripe, cartes) et à leurs webhooks est **délégué entièrement au Kernel** via `payment-controller` / `payment-gateway-controller`. Pour la facturation, les endpoints du `billing-legacy-documents-controller` du Kernel doivent être exploités au mieux plutôt que ré-implémentés localement. Détail complet : [workstream-payment-billing-kernel-delegation.md](workstream-payment-billing-kernel-delegation.md).

Conséquence directe sur le P0 initial de la synthèse exécutive : le point *« vérifier la signature des webhooks Stripe/MTN/Orange »* est **remplacé** par *« supprimer les adaptateurs de paiement locaux et déléguer au Kernel »* — ce n'est plus un correctif, c'est une suppression de surface d'attaque.
