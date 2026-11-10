# Chantier — Migration complète vers le wallet Kernel

**Branche :** `feat/wallet-hardening-and-kernel-migration`
**Décidé le :** 2026-11-10 (utilisateur) — le Core cesse de tenir son propre grand livre ; le solde et les mouvements vivent dans le wallet du Kernel (`payment-controller`, `/api/payments/wallets/**`).
**Remplace / prolonge :** [workstream-payment-billing-kernel-delegation](workstream-payment-billing-kernel-delegation.md) (qui n'avait délégué que l'initiation des paiements Mobile Money/carte).

## Décisions prises avec l'utilisateur

| Sujet | Décision |
|---|---|
| Paiement d'une livraison | Le client paie depuis **son propre wallet** par un **transfert** vers le wallet du livreur (workflow Kernel : challenge → code de confirmation → transfert). Le livreur retire ensuite depuis son wallet (`withdrawals`). |
| Code de réception du livreur | Le livreur génère un `receive-code` à l'acceptation ; **le Core le stocke sur la livraison** et le fournit au flux de paiement du client. Expiré → le livreur en régénère un. |
| Commission plateforme (5 %) | **Demandée au Kernel** (transfert avec répartition livreur / wallet plateforme en une seule confirmation). Tant que non livrée : paiement sans prélèvement, commission tracée « due » dans le Core. |
| Flux sans utilisateur présent (remboursement après litige, indemnisation de litige, commission d'agence) | **Désactivés** avec le message « non supporté par le Kernel » et listés en demandes au Kernel (ci-dessous). |
| Gel de paiement lors d'une escalade incident → litige | **Table de blocages locale** `wallet_holds` (montant bloqué par wallet et par litige) ; le Core vérifie *solde Kernel − blocages* avant toute opération sortante. |

## Fait sur la branche

- `WalletController` (grand livre local) sécurisé : tenant issu du JWT, `/me/**` en self-service, tout le reste derrière la nouvelle permission `wallet:admin` (TNT_ADMIN). Voir commit `fix(wallet): secure WalletController…`.
- Client Kernel conforme à la spec : `wallets/mine`, `recharge` (`WalletRechargeRequest`), ordres de recharge, `receive-codes`, `transfers` (+ `/challenge`), `withdrawals` (+ `/challenge`), `withdrawals/availability`.
- API `/billing/kernel-wallet/**` (self-service, aucun id de wallet ou de propriétaire dans les routes ; le Kernel résout le wallet depuis le Bearer relayé).
- `kernelPaymentWebClient` : relais du Bearer de l'utilisateur, **aucun retry transport** (un POST d'argent expiré ne doit jamais être rejoué), disjoncteur qui compte désormais les réponses HTTP 5xx.
- Tests d'intégration : chaîne contrôleur → service → adaptateur contre un faux Kernel ; relais du Bearer et non-rejeu sur le vrai bean `kernelPaymentWebClient`.

## Reste à faire (ordre recommandé)

1. **Paiement d'une livraison par transfert** : stocker le `receive-code` du livreur sur la livraison (go-freelancer), exposer `POST /billing/kernel-wallet/deliveries/{id}/pay/challenge` et `/pay` (le client confirme avec son code), enregistrer le résultat sur la livraison.
2. **Basculer les appelants** de `IWalletUseCase` (market-back-core, go-freelancer-point-back-core, `WalletBillingEventConsumer`) vers le port Kernel ; supprimer `splitMissionRevenue`, `creditCommission`, `transferSubDelivererCommission`, `createFreelancerOrgWallet` du chemin nominal.
3. **Table `wallet_holds`** + adaptateur de gel incident (remplace `IncidentPaymentFreezeAdapter` et `Wallet.freeze`).
4. **Retirer le grand livre local** : `Wallet`, `WalletTransaction`, `PaymentIntent` locaux, repositories R2DBC, `ReconciliationService`, migration Liquibase de suppression — après **migration des soldes existants** (voir point ouvert ci-dessous).
5. **Retirer `/billing/wallet/**`** (ancienne API) une fois les frontends passés sur `/billing/kernel-wallet/**`.

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

- **Soldes existants** : les tables locales `billing.wallet_*` contiennent des soldes réels (et un écart connu de 2 850 XAF, voir lot C-11). Il faut décider : migrer (nécessite R1), ou solder/exporter avant suppression.
- **Livreurs sans wallet Kernel** : tant que R7 n'est pas livré, le wallet doit être créé par le livreur lui-même (`POST /billing/kernel-wallet/me`) avant d'accepter une course.
