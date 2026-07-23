# Portail des audits techniques TiiBnTick

Audit technique exhaustif du dépôt `tiibntick-core`, réalisé les 16–17 juillet 2026 par une équipe d'agents spécialisés (architecture, systèmes distribués, plateforme, sécurité, performance). Périmètre : **52 modules Maven** — les couches L0–L7 historiques **et** les 4 backends produits de `coreBackend/` (`tnt-agency-back-core`, `tnt-go-freelancer-point-back-core`, `tnt-link-back-core`, `tnt-market-back-core`) — soit ~2 800 fichiers Java, configurations Spring/Docker/Liquibase, scripts, tests et documentation.

Chaque constat est justifié par des preuves issues du code (références `fichier:ligne`), avec criticité (Critique / Élevé / Moyen / Faible), impact et recommandation.

## Par où commencer

Commencez par la **[Synthèse exécutive](executive-summary.html)** : principaux constats transverses, risques majeurs et feuille de route priorisée P0 → P2 (dont la décision BFF Link et l'architecture temps réel cible).

<div class="cards">
<a class="card" href="executive-summary.html"><div class="icon">📋</div><h3>Synthèse exécutive</h3><p>Constats majeurs, risques transverses, feuille de route priorisée P0/P1/P2.</p></a>
<a class="card" href="remediation/implementation-plan.html"><div class="icon">🗂️</div><h3>Plan d'implémentation</h3><p>Document vivant, cases à cocher : 3 phases de bout en bout couvrant les 145 constats des 7 audits, sans omission.</p></a>
<a class="card" href="audit-1-architecture.html"><div class="icon">🏛️</div><h3>Audit n°1 — Architecture globale</h3><p>Hexagonal, DDD, couches L0–L7, anti-patterns, dette — 17 problèmes.</p></a>
<a class="card" href="audit-2-inter-modules.html"><div class="icon">🔗</div><h3>Audit n°2 — Connexions inter-modules</h3><p>Cartographie des 223 dépendances, 112 mortes, violations de couches.</p></a>
<a class="card" href="audit-3-yow-event-kernel.html"><div class="icon">📨</div><h3>Audit n°3 — yow-event-kernel</h3><p>Module événementiel mort (0 import), événementiel ad-hoc généralisé.</p></a>
<a class="card" href="audit-4-yow-i18n-kernel.html"><div class="icon">🌍</div><h3>Audit n°4 — yow-i18n-kernel</h3><p>Couverture i18n réelle, multidevise — 24 problèmes, verdict non prêt.</p></a>
<a class="card" href="audit-5-kafka.html"><div class="icon">🧵</div><h3>Audit n°5 — Kafka</h3><p>Cartographie des flux, ~20 topics orphelins, fiabilité, sécurité.</p></a>
<a class="card" href="audit-6-scalabilite.html"><div class="icon">📈</div><h3>Audit n°6 — Scalabilité</h3><p>10 goulots classés, SPOF, verrous du scale-out, Link temps réel.</p></a>
<a class="card" href="audit-7-system-design.html"><div class="icon">🧭</div><h3>Audit n°7 — System Design & Sécurité</h3><p>Failles critiques, architecture cible, décision BFF Link, feuille de route.</p></a>
</div>

## Méthodologie

Sept agents spécialisés ont travaillé en parallèle sur le dépôt complet, chacun sur un domaine précis. Aucune affirmation sans preuve : chaque problème est localisé (`fichier:ligne`), qualifié (impact, criticité) et accompagné d'une recommandation. Les rapports sources (Markdown) sont conservés dans `docs/audits/raw/` ; les pages HTML sont régénérables via `python3 docs/audits/build_reports.py`.

## Plan d'implémentation

L'audit a produit un **diagnostic** ; le passage à l'implémentation vit désormais dans un document séparé et vivant, avec des cases à cocher au fur et à mesure de l'avancement : **[Plan d'implémentation](remediation/implementation-plan.html)** — 3 phases de bout en bout ([Phase 0](remediation/phase-0-critical.html), [Phase 1](remediation/phase-1-hardening.html), [Phase 2](remediation/phase-2-target-architecture.html)) couvrant l'intégralité des 145 constats des 7 audits, plus le chantier [Délégation paiement/facturation au Kernel](remediation/workstream-payment-billing-kernel-delegation.html) (directive du 17/07/2026 : plus aucun mécanisme de paiement géré en propre par TiiBnTick Core).

## Rappel des criticités

| Niveau | Signification |
|---|---|
| <span class="badge crit">Critique</span> | Perte/corruption de données, faille de sécurité exploitable, flux métier cassé — bloquant pour la production. |
| <span class="badge high">Élevé</span> | Casse à moyen terme ou sous charge ; à corriger avant la montée en charge. |
| <span class="badge med">Moyen</span> | Dette ou incohérence gênant la maintenabilité et l'évolution. |
| <span class="badge low">Faible</span> | Amélioration souhaitable, faible risque immédiat. |
