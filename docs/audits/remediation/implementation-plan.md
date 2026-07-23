# Plan d'implémentation — vue d'ensemble

Ce document est le point d'entrée du **plan de remédiation complet** de TiiBnTick Core, construit à partir de l'intégralité des 7 audits techniques ([portail des audits](../index.html)) sans aucune omission : les **145 constats individuels** relevés (17 en architecture, 10 en connexions inter-modules, 22 sur yow-event-kernel, 24 sur yow-i18n-kernel, 20 sur Kafka, 27 sur la scalabilité, 25 sur system design & sécurité) sont chacun repris comme un item cochable dans l'une des trois phases ci-dessous, plus le chantier transverse de délégation paiement/facturation. Rien n'est laissé de côté silencieusement : un constat non retenu pour une action immédiate est explicitement classé, jamais simplement absent.

> **Mode d'emploi des cases à cocher.** La source de vérité est le Markdown versionné dans git — chaque case `- [ ]`/`- [x]` se coche dans l'IDE et se committe, comme n'importe quel changement de code. La page HTML (régénérée via `python3 docs/audits/build_reports.py`) affiche l'état du fichier au moment du build et une barre de progression calculée automatiquement. **Les cases sont en lecture seule dans le navigateur** — la page est un tableau de bord qui reflète l'avancement réel de l'équipe, pas un outil de saisie.

## Comment lire ce plan

```
implementation-plan.md          ← vous êtes ici : vue d'ensemble, séquencement, glossaire des ID
├── phase-0-critical.md         ← bloquant production (3-5 sem.)
│   └── workstream-payment-billing-kernel-delegation.md   ← détail du chantier B de la Phase 0
├── phase-1-hardening.md        ← durcissement (1-3 mois, après Phase 0)
└── phase-2-target-architecture.md  ← trajectoire long terme (3-18 mois, conditionnelle)
```

Chaque item porte l'étiquette **`[Audit n°X · ID]`** — l'ID (A1, P1, P-01, S1, #1…) renvoie au tableau « Problèmes détectés » du rapport d'audit source, où se trouve la preuve complète (extrait de code, ligne exacte, raisonnement). Ce plan ne répète pas les preuves déjà écrites dans les audits ; il les organise en séquence d'exécution.

## Pourquoi trois phases et pas une liste plate

Une liste unique de 145 items serait exhaustive mais inexploitable : elle mélangerait une faille de paiement critique avec un champ `final` manquant. Le séquencement en phases répond à une seule question à chaque étape : **qu'est-ce qui doit être vrai avant que la suite ait du sens ?**

- **Phase 0** répond à *« qu'est-ce qui empêche d'ouvrir la production sans risque inacceptable »* — fraude financière, fuite de données inter-tenant, flux métier cassés, corruption de données au premier scale-out. Tout le reste peut attendre que ceci soit vrai.
- **Phase 1** répond à *« qu'est-ce qui doit être vrai avant une montée en charge significative »* — résilience, observabilité, isolation tenant en profondeur, et le premier chantier produit structurant (Link temps réel).
- **Phase 2** répond à *« qu'est-ce qui prépare l'échelle sans être payé avant d'en avoir besoin »* — infrastructure distribuée, partitionnement, extraction de services, sous condition de traction réelle.

## Séquencement recommandé

1. **Semaines 1–5 : Phase 0**, chantiers A à G en parallèle par des personnes différentes (ils sont indépendants entre eux, sauf le chantier B qui est le plus gros et peut démarrer immédiatement sur son sous-chantier wallet pendant que l'ADR facturation se discute).
2. **Mois 2–4 : Phase 1**, en commençant par le chantier G (Link BFF + temps réel) car c'est le chantier produit le plus visible et le plus long ; les chantiers A, C, D, E, F peuvent progresser en parallèle par équipe.
3. **Mois 3–18 : Phase 2**, déclenchée par la charge réelle observée, pas par le calendrier — ne pas anticiper l'infrastructure distribuée (Chantier H/I/J) avant que Phase 0+1 soient stabilisées en production et que des métriques réelles justifient l'investissement.

## Table de correspondance complète — audit → phase

Cette table garantit qu'aucun des 145 constats n'a été oublié. Les compteurs sont calculés automatiquement depuis les fichiers de phase (voir la barre de progression globale ci-dessus dans la version HTML).

| Audit source | Nombre de constats | Répartition |
|---|---|---|
| [Audit n°1 — Architecture globale](../audit-1-architecture.html) | 17 (A1–A17) | 5 en Phase 0 (A1,A2,A3,A5*,A6 — corrections rapides), 5 en Phase 1 (A4,A5**,A7,A13,A16,A17 — *cf. note), 7 en Phase 2 (A8,A9,A10,A11,A12,A14,A15) |
| [Audit n°2 — Connexions inter-modules](../audit-2-inter-modules.html) | 10 (P1–P10) | 4 en Phase 0 (P1,P3,P4,P5 — dédupliqués avec A2/EK/A7), 3 en Phase 1 (P2,P6,P7 + reco 8), 3 en Phase 2 (P8,P9,P10 + reco 7) |
| [Audit n°3 — yow-event-kernel](../audit-3-yow-event-kernel.html) | 22 (P1–P22) | 6 en Phase 0 (P1–P6 + PR1 décision), 5 en Phase 1 (P7–P11 + PR4/PR5/PR6), 11 en Phase 2 (P12–P22, chantier L conditionnel) |
| [Audit n°4 — yow-i18n-kernel](../audit-4-yow-i18n-kernel.html) | 24 (P-1–P-24) | 3 en Phase 0 (P-1,P-2,P-21 — messages cassés en prod), 5 en Phase 1 (P-4,P-5,P-6,P-7,P-12,P-19,P-22,P-23 — Money + locale), 13 en Phase 2 (le reste — hygiène, référentiels, ADR taux de change) |
| [Audit n°5 — Kafka](../audit-5-kafka.html) | 20 (P-01–P-20) | 3 en Phase 0 (P-01,P-02,P-03), 8 en Phase 1 (P-04–P-08,P-17,P-20b,P-20d), 5 en Phase 2 (P-09,P-10,P-11,P-13,P-16) |
| [Audit n°6 — Scalabilité](../audit-6-scalabilite.html) | 27 (S1–S27) | 6 en Phase 0 (S1–S5,S14 + mitigation S25), 12 en Phase 1 (S6,S7,S9,S10,S11,S12,S15,S17,S18,S20,S24,S25,S26,S27 — dont le chantier Link complet), 9 en Phase 2 (S8,S13,S16,S19,S21,S22 + reco 13/16) |
| [Audit n°7 — System Design & Sécurité](../audit-7-system-design.html) | 25 (#1–#25) | 10 en Phase 0 (#1–#10,#15,#16,#19,#23 — sécurité + paiement), 9 en Phase 1 (#11–#14,#17,#18,#22,#24,#25), 4 en Phase 2 (#12*,#13,#14*,#20,#21 — *dédupliqués avec Phase 1) |

*Notes de dédoublonnage : plusieurs audits observent le même fait sous un angle différent (ex. `tnt-actor-core→tnt-incident-core` est à la fois A2 dans l'audit architecture et P1 dans l'audit inter-modules ; le mismatch `mission.status` est P6 dans l'audit event-kernel et fait partie de P-01 dans l'audit Kafka). Chaque fait n'a **qu'un seul checkbox exécutable** dans le plan, mais référence systématiquement tous les audits qui l'ont observé — voir les fichiers de phase pour le détail exact des références croisées.*

## Décisions d'architecture actées pendant ce plan

| Date | Décision | Détail |
|---|---|---|
| 17/07/2026 | TiiBnTick Core délègue entièrement les paiements et la facturation au Kernel | [Chantier dédié](workstream-payment-billing-kernel-delegation.html) |
| 18/07/2026 | ADR tranchée : `yow-event-kernel` réhabilité comme voie unique (Option A) | [Phase 0, chantier C](phase-0-critical.html#chantier-c-evenementiel-kafka-flux-casses) |
| 18/07/2026 | ADR tranchée : `tnt-billing-invoice` coexiste avec `billing-legacy-documents-controller` (Option B) | [Chantier paiement, question ouverte](workstream-payment-billing-kernel-delegation.html#question-ouverte-a-trancher-avant-limplementation) |
| — | Décision actée : BFF Link + temps réel par tuiles geohash, réutilisant `tnt-realtime-core` | [Phase 1, chantier G](phase-1-hardening.html#chantier-g-link-bff-temps-reel-par-tuiles-geohash-priorite-n1-de-la-phase-1) |
| — | Principe directeur : rester monolithe modulaire, extraire seulement sous condition de traction mesurée | [Phase 2](phase-2-target-architecture.html) |

## Liens directs

- [Phase 0 — Bloquant production](phase-0-critical.html)
- [Phase 1 — Durcissement](phase-1-hardening.html)
- [Phase 2 — Architecture cible](phase-2-target-architecture.html)
- [Chantier — Délégation paiement/facturation au Kernel](workstream-payment-billing-kernel-delegation.html)
- [README du dossier remediation](index.html)
