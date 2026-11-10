# Chantier — Migration complète vers le wallet Kernel

**Branche :** `feat/wallet-hardening-and-kernel-migration`
**Décidé le :** 2026-11-10 (utilisateur) — le Core cesse de tenir son propre grand livre ; le solde et les mouvements vivent dans le wallet du Kernel (`payment-controller`, `/api/payments/wallets/**`).
**Remplace / prolonge :** [workstream-payment-billing-kernel-delegation](workstream-payment-billing-kernel-delegation.md) (qui n'avait délégué que l'initiation des paiements Mobile Money/carte).

## Décisions prises avec l'utilisateur

| Sujet | Décision |
|---|---|
| Paiement d'une livraison | Le client paie depuis **son propre wallet** par un **transfert** vers le wallet du livreur (workflow Kernel : challenge → code de confirmation → transfert). Le livreur retire ensuite depuis son wallet (`withdrawals`). |
| Code de réception du livreur | Le livreur déclenche la génération du `receive-code` (avec son jeton) ; **le Core le stocke sur la livraison** et le fournit au flux de paiement du client. Expiré → le livreur en régénère un. |
| Commission plateforme (5 %) | **Demandée au Kernel** (transfert avec répartition livreur / wallet plateforme en une seule confirmation). Tant que non livrée : paiement sans prélèvement, commission tracée « due » dans le Core. |
| Flux sans utilisateur présent (remboursement après litige, indemnisation de litige, commission d'agence) | **Désactivés** avec le message « non supporté par le Kernel » et listés en demandes au Kernel (ci-dessous). |
| Gel de paiement lors d'une escalade incident → litige | **Table de blocages locale** `wallet_holds` (montant bloqué par wallet et par litige) ; le Core vérifie *solde Kernel − blocages* avant toute opération sortante. |

## Fait sur la branche

**Sécurité et client Kernel**
- Ancien `WalletController` sécurisé (tenant du JWT, `/me/**`, `wallet:admin`) puis **supprimé** avec le grand livre local.
- Client Kernel conforme à la spec : `wallets/mine`, `recharge`, ordres de recharge, `receive-codes`, `transfers` (+ `/challenge`), `withdrawals` (+ `/challenge`), `withdrawals/availability`.
- API `/billing/kernel-wallet/**` (self-service, aucun id de wallet/propriétaire dans les routes).
- `kernelPaymentWebClient` : relais du Bearer de l'utilisateur, **aucun retry transport** sur les POST d'argent, disjoncteur qui compte les 5xx.

**Paiement d'une livraison par transfert** (`/api/v1/deliveries/{id}/payment`)
1. Livreur : `POST /receive-code` — le code est généré **avec le jeton du livreur** (le Core ne stocke jamais un code fourni par le client HTTP, ce qui évite de rediriger un paiement vers un autre wallet) et stocké sur la livraison (`driver_user_id`, `receive_code`, `receive_code_expires_at`). Expiré → le livreur le régénère.
2. Client : `POST /challenge` puis `POST /` `{challengeToken, code}` → transfert Kernel, règlement enregistré (`PAID`), commission 5 % tracée `DUE`.
3. `GET /` : statut du règlement (client ou livreur).
4. À la fin de la livraison (OTP) le Core **ne bouge plus d'argent** : il réconcilie (`PAID` → ancrage blockchain ; `CASH` → espèces + commission `DUE` ; numérique non payé → `UNPAID`, compteur `gofp.delivery.payment.failures` + log `UNPAID_DELIVERED`, la requête réussit).

**Grand livre local retiré** (module `tnt-billing-wallet`) : `Wallet`, `WalletTransaction`, `PaymentIntent`, split, réconciliation, `WalletService`, `/billing/wallet/**`, éditeur Kafka d'événements wallet, store d'idempotence Redis. Remplacés (tables supprimées par le changelog 005) par `billing.wallet_settlements` (quel objet métier a été réglé, par quel transfert ou en espèces, commission due) et `billing.wallet_holds` (montants que le Core refuse de laisser sortir pendant un incident/litige).

**Appelants migrés** : annonces (plus d'escrow à la publication/sélection), frais de stockage relais (règlement tracé ; le transfert numérique des frais n'est pas fait par le Core), commandes Market (règlement tracé, plus de mouvement), gel d'incident → blocage local, `WalletBillingEventConsumer` (gel OK ; crédit de commission et indemnisation de litige **désactivés bruyamment**), commission d'agence (**501** explicite).

**Vérifié** : 18 tests unitaires + 7 IT (faux Kernel) du module wallet ; 138 tests unitaires + 8 IT de bout en bout du paiement (contrôleur → services → adaptateur HTTP → faux Kernel, avec relais du Bearer par utilisateur) côté go-freelancer ; 3 IT sur un vrai Postgres (Testcontainers) pour le changelog `004` et les adaptateurs R2DBC ; démarrage complet du contexte Spring contre Postgres/Redis/Kafka réels.

## Limites connues (à lire avant le déploiement)

- **Aucun appel contre le vrai Kernel** n'a pu être fait (pas de jeton). Les corps de `transfers`/`challenge` suivent la spec OpenAPI ; la forme de la réponse d'un transfert est lue au mieux (`transactionId`/`id`/`reference`) faute de schéma fiable (R3).
- **Commission 5 %** : tracée `DUE` mais **non prélevée** tant que le Kernel n'offre pas de transfert avec répartition (R2). Liste via `ISettlementUseCase#listCommissionsDue`.
- **Frais de stockage relais en numérique** : non transférés par le Core (le point relais n'a pas de code de réception stocké). Enregistrés `UNPAID` + log `RELAY_FEE_NOT_TRANSFERRED`. Espèces : commission `DUE`.
- **Blocages** : le Core ne contrôle que les opérations passant par `/billing/kernel-wallet`. Un utilisateur qui appellerait le Kernel directement n'est pas bloqué (R5).
- **Soldes historiques** : le mainteneur a confirmé qu'il n'y avait aucune donnée réelle à conserver ; le changelog `005-drop-legacy-ledger-tables` **supprime** `wallet_wallets`, `wallet_transactions`, `wallet_payment_intents`, `wallet_payment_splits` et `wallet_reconciliations` (irréversible, aucune migration de soldes). Seules `wallet_settlements` et `wallet_holds` restent. Le script `scripts/sql/wallet-ledger-reconciliation.sql` est supprimé avec elles.
- **Rupture d'API** : `/billing/wallet/**` n'existe plus (remplacé par `/billing/kernel-wallet/**`) ; le paiement d'agence nécessite R1.

## Demandes à envoyer à l'équipe Kernel (TSAFACK Savio)

| # | Demande | Pourquoi |
|---|---|---|
| R1 | **Crédit/transfert serveur à serveur** authentifié par clé API (`X-Api-Key`), sans challenge ni code de confirmation, idempotent (`idempotencyKey`) | Remboursement après litige, indemnisation de litige, commission d'agence, crédit de solde de gains : aucun utilisateur présent pour confirmer. |
| R2 | **Transfert avec répartition** : un montant → N bénéficiaires (livreur + wallet plateforme) en une seule confirmation | Commission plateforme de 5 % sur chaque course sans demander deux codes au client. |
| R3 | **Schémas réels de `TransactionRequest` / `TransactionResponse`** pour `pay`, `transfers`, `withdrawals`, `transactions` | Le swagger réutilise le nom de schéma de la blockchain (`chainCode`, `payloadHash`, `signature`…, aucun champ montant/devise). Le Core relaie aujourd'hui le JSON brut faute de contrat fiable. |
| R4 | **Enum documenté de `PaymentOrderResponse.status`** (et des statuts de transaction) | Le Core classe aujourd'hui le statut par heuristique sur le texte (`SUCCESS`, `FAIL`…). |
| R5 | **Gel / réservation de solde** (ou confirmation que la table de blocages locale est la voie voulue) | Escalade incident → litige. |
| R6 | **Remboursement d'un transfert** | Litige tranché en faveur du client. |
| R7 | **Création du wallet d'un tiers** (livreur) par le Core, ou création automatique à l'inscription | `POST /wallets/mine` exige le JWT du propriétaire ; le Core ne peut pas créer le wallet d'un livreur à sa place. |

## Points ouverts

- **Livreurs sans wallet Kernel** : tant que R7 n'est pas livré, le livreur doit créer son wallet lui-même (`POST /billing/kernel-wallet/me`) avant de pouvoir générer un code de réception.
- **Événements `tnt.billing.wallet.payment.confirmed`** : plus émis par le Core. Les consommateurs (facturation, comptabilité, market, commission d'agence) restent à l'écoute mais ne reçoivent plus rien ; à rebrancher sur le règlement `PAID` si ces flux doivent rester actifs.
