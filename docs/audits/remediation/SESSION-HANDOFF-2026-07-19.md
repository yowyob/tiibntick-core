# Handoff — Phase 0 & délégation paiement/facturation (2026-07-19)

## État au 2026-07-19 (fin de session) : voir mise à jour ci-dessous — rien n'est encore committé.

## Décisions ADR — tranchées cette session

- **HRM×16 — Audit n°7 #6** : **Option A retenue** — déléguer au Kernel tel quel,
  aucun changement de code. `docs/audits/remediation/phase-0-critical.md` mis à jour,
  item #6 entièrement coché.
- **RBAC S5 — persistance des rôles** : la prémisse initiale de la question posée en
  début de session ("aucun endpoint Kernel de persistance de rôles") était **fausse** —
  vérifié : le Kernel a un `role-controller` complet, déjà utilisé en écriture par
  `KernelRoleProvisioningAdapter`/`KernelRoleAssignmentAdapter`. Le vrai trou est le
  chemin de **lecture** (`RoleRepository`/`UserRoleAssignmentRepository` jamais
  peuplés → résolution de permission en mode `LOCAL` toujours vide, silencieusement).
  Après reformulation, l'utilisateur a demandé une refonte plus ambitieuse : TiiBnTick
  propriétaire de son modèle RBAC métier (CRUD autonome des rôles), synchronisé vers
  le Kernel de façon **asynchrone et fiable par outbox** (PENDING/PROVISIONED/FAILED,
  retry, idempotence, réconciliation) — pas de double écriture synchrone. Proposition
  d'architecture écrite dans `docs/audits/remediation/rbac-s5-outbox-architecture.md`,
  **en attente de revue/validation avant tout code**.

## Reste à faire — Phase 0

1. **Chantier D · [Audit n°6 · S5]** — revoir/valider
   `rbac-s5-outbox-architecture.md`, puis implémenter (schéma R2DBC + table outbox +
   worker `KernelRoleSyncWorker` + job de réconciliation). Question ouverte à trancher
   dans le doc §6 : le CRUD de rôles métier autonomes fait-il partie du périmètre S5
   ou est-il tracké séparément ?
2. **Chantier C · [Audit n°3 · P5]** — migrer les 25 publishers Kafka qui contournent
   encore l'event-kernel vers l'outbox (`yow-event-kernel` P1-P4 réhabilité et
   vérifié). **Non démarré, non codé cette session** (inventaire écrit seulement, à la
   demande explicite de l'utilisateur) — voir
   `docs/audits/remediation/chantier-c-p5-inventory.md` pour la liste des 25 fichiers,
   le séquencement recommandé (module pilote `tnt-delivery-core` d'abord), et un
   **correctif isolé à faire avant tout le reste** : `OutboxPollerService`
   (`yow-event-kernel`) tourne réellement en prod depuis la réactivation P1
   (2026-07-18) mais sans `@SchedulerLock` — son Javadoc affirmant le contraire est
   maintenant obsolète (régression silencieuse de Chantier D · S2, sans impact tant
   qu'aucun module n'est migré, mais à corriger avant le premier).
3. Une fois 1-2 clos : cocher les lignes "Definition of Done" globales de chantiers
   C/D dans `docs/audits/remediation/phase-0-critical.md`. Chantier A est maintenant
   entièrement clos (HRM×16 tranché) — vérifier s'il reste d'autres items non cochés
   avant de cocher sa Definition of Done globale (`#5` était encore ouvert au
   2026-07-18, à reconfirmer).

## Reste à faire — Délégation paiement/facturation

**Rien.** Workstream B est à 10/10 (`docs/audits/remediation/workstream-payment-billing-kernel-delegation.md`).

## Consigne toujours valable pour la prochaine session

Pour toute nouvelle décision d'architecture rencontrée, poser la question à
l'utilisateur avec toutes les options identifiées et une recommandation par défaut
clairement marquée, pour un choix en un clic — ne jamais trancher seul une ambiguïté
d'architecture. **Corollaire ajouté cette session : vérifier la prémisse factuelle
avant de formuler les options** (l'hypothèse "aucun endpoint Kernel" ci-dessus était
fausse et aurait faussé toutes les options proposées si elle n'avait pas été vérifiée
en premier).
