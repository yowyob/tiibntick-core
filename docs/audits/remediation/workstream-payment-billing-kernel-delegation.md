# Chantier — Délégation complète Paiement & Facturation au Kernel

**Statut : 🟡 Planifié — non commencé**
**Décidé le :** 17/07/2026, par directive explicite de l'utilisateur.
**Remplace :** le point P0 « vérifier les signatures webhooks Stripe/MTN/Orange » de l'[executive-summary](../executive-summary.html) et de l'[Audit n°7](../audit-7-system-design.html).
**Fait partie de :** [Phase 0, chantier B](phase-0-critical.html#chantier-b-paiement-facturation-delegation-complete-au-kernel).

> Cases à cocher : source de vérité = ce fichier Markdown, coché par commit. Voir le mode d'emploi complet en tête de la [Phase 0](phase-0-critical.html).

## Décision

TiiBnTick Core **cesse de gérer lui-même les modes de paiement et leurs mécanismes**. Toute intégration Mobile Money / carte bancaire, tout webhook de confirmation, toute vérification de signature est **entièrement déléguée au Kernel** :

- **Paiements / wallet** → Kernel `payment-controller` (`/api/payments/wallets/**`) + `payment-gateway-controller` (`/api/payments/orders/**`).
- **Facturation / documents commerciaux** → Kernel `billing-legacy-documents-controller` (`/api/bon-commande`, `/api/bons-achat`, `/api/bons-livraison`, `/api/facture-fournisseurs`, `/api/factures-proforma`, `/api/v1/facturation/bon-receptions`, `/api/v1/facturation/note-credits` — 64 opérations) + `billing-legacy-payments-controller` (`/api/paiement`).

Ce chantier **supprime une vulnérabilité critique plutôt que de la corriger** : voir preuve ci-dessous. C'est aussi une simplification nette — environ 1 200 lignes de logique paiement/webhook maison disparaissent du périmètre TiiBnTick.

## Pourquoi c'est aussi un correctif de sécurité (pas seulement un refactor)

Preuve relevée dans `PaymentWebhookController.java` (`billing/tnt-billing-wallet/src/main/java/.../adapter/in/web/`) :

| Provider | Ligne | État réel de la vérification |
|---|---|---|
| Stripe | `PaymentWebhookController.java:101-116` | **Aucune vérification de signature.** Le commentaire dit littéralement « Stripe signature verification would use stripe-java SDK in production » — le SDK n'est pas utilisé, le corps JSON brut est parsé directement (`parseStripeEvent`). N'importe qui peut POST `{"type":"payment_intent.succeeded", ...}` et faire confirmer un paiement. |
| MTN MoMo | `PaymentWebhookController.java:120-137` (`verifyMtnSignature`) | HMAC-SHA256 vérifié **seulement si l'en-tête `X-MTN-Signature` est présent** ; s'il est absent, la requête est acceptée sans vérification (`// MTN sandbox may not send signatures — allow in non-production`, ligne 123). |
| Orange Money | `PaymentWebhookController.java:74-95` | **Zéro vérification.** Le contrôleur fait confiance au corps `OrangeCallbackRequest` tel quel. |

Le Kernel expose déjà `POST /api/payments/orders/callbacks/{provider}` avec un en-tête `Stripe-Signature` documenté dans son propre swagger — la vérification de signature devient la responsabilité d'une équipe qui possède réellement l'intégration provider. **Supprimer le contrôleur maison élimine la faille au lieu de la patcher.**

## Inventaire du code actuel concerné

### `billing/tnt-billing-wallet` — à restructurer en profondeur

| Fichier | Lignes | Sort |
|---|---|---|
| `adapter/in/web/PaymentWebhookController.java` | 177 | **Supprimé.** Remplacé par la relation directe Kernel↔Kernel (le Kernel reçoit et traite lui-même les callbacks provider). |
| `adapter/out/momo/StripeAdapter.java` | 72 | **Supprimé.** |
| `adapter/out/momo/MtnMoMoAdapter.java` | 111 | **Supprimé.** |
| `adapter/out/momo/OrangeMoneyAdapter.java` | 47 | **Supprimé.** |
| `application/port/out/IMoMoPaymentPort.java` | — | **Supprimé** (port devenu sans objet — plus d'implémentation locale de dispatch provider). |
| `config/StripeProperties.java`, `MtnMoMoProperties.java`, `OrangeMoneyProperties.java` | — | **Supprimés** avec leurs entrées `application.yml` (clés API/secrets provider ne doivent plus résider dans TiiBnTick Core). |
| `adapter/in/web/dto/request/{Orange,Mtn}CallbackRequest.java` | — | **Supprimés.** |
| `application/service/WalletService.java` (481 lignes) | — | **Conservé mais réduit.** Le ledger local (`Wallet`, `WalletTransaction`, `PaymentIntent`, `getOrCreateWallet`, `creditWallet`, `debitWallet`, `initiatePayment`, `handlePaymentCallback`, `refundPayment`, `freezeWallet`) est remplacé par des appels au Kernel `payment-controller`. La logique **métier TiiBnTick** sans équivalent Kernel est conservée : `splitMissionRevenue` (répartition plateforme/organisation/sous-livreur), `creditCommission`, `transferSubDelivererCommission`, l'ancrage blockchain (`anchorPaymentCommit` → `tnt-trust-core`), les notifications de confirmation/échec. |
| `adapter/out/redis/RedisIdempotencyStore.java` | — | **Conservé** — l'idempotence côté TiiBnTick (déduplication de commandes entrantes) reste utile même si le Kernel gère sa propre idempotence de paiement. |

### `billing/tnt-billing-invoice` — périmètre à clarifier (voir « Question ouverte » ci-dessous)

`InvoiceController.java` (110 lignes) gère la facturation **mission/livraison** propre à TiiBnTick (numérotation `TNT-FACT-{agence}-{année}-{séquence}`, PDF, note de crédit). Ce n'est pas strictement le même objet que les « documents commerciaux legacy » du Kernel (bons de commande/livraison/achat, factures fournisseurs/proforma, notes de crédit, bons de réception) qui semblent relever d'un module ERP/comptabilité fournisseur générique côté Kernel. Voir section Question ouverte.

**Correctif de sécurité indépendant à faire dans ce module quel que soit l'arbitrage** : `InvoiceController.java:78,88,105` — `cancel`, `markPaid`, `issueCreditNote` acceptent le tenant depuis l'en-tête client `X-Tenant-Id` au lieu du JWT (`TntSecurityContext`). C'est l'instance concrète du finding IDOR critique de l'[Audit n°7](../audit-7-system-design.html) sur ce module précis — à corriger dans ce chantier, indépendamment de la délégation Kernel.

### Modules dépendants (impact à vérifier, ne pas casser)

| Module | Fichier | Dépendance actuelle |
|---|---|---|
| `trust/tnt-trust-core` | `adapter/out/wallet/PaymentAnchorAdapter.java` | Implémente `IPaymentAnchorPort` (owned par `tnt-billing-wallet`, pattern d'inversion standard du repo). Consomme les événements `PaymentConfirmed` pour ancrer sur la blockchain. **Doit continuer de fonctionner** — si la confirmation de paiement vient désormais du Kernel plutôt que de `WalletService.handleSuccessfulPayment`, le point d'entrée qui publie `PaymentConfirmed` doit être identifié dans le nouveau flux (webhook Kernel→TiiBnTick, ou polling `GET /api/payments/orders/{id}`). |
| `business/tnt-accounting-core` | `adapter/in/messaging/BillingEventAccountingConsumer.java` | Consomme les événements de facturation/paiement Kafka. Vérifier que les topics ne changent pas de forme après la migration. |
| `coreBackend/tnt-market-back-core` | `application/service/MarketOrderApplicationService.java` | Utilise le wallet pour le paiement des commandes e-commerce — à rebrancher sur les nouveaux appels Kernel. |
| `tnt-bootstrap` | `config/TntKafkaTopicsConfig.java`, `config/trustnoop/NoOpPaymentAnchorPort.java`, `config/TntModuleRegistry.java` | Wiring à mettre à jour si des beans/qualifiers disparaissent (ex. `IMoMoPaymentPort` n'a plus d'implémentation à câbler). |

## Architecture cible

Suivre **exactement** le pattern déjà établi dans le repo pour les proxies Kernel (voir `tnt-bootstrap/.../bridge/KernelBridgeConfig.java` et `foundation/tnt-platform-gateway-core/.../adapter/out/kernel/KernelAuthGatewayAdapter.java`) :

1. **Un bean `WebClient` nommé de plus dans `KernelBridgeConfig`** (`tnt-bootstrap`), ex. `kernelPaymentWebClient` — même base URL/credentials que les autres, pas de nouvelle configuration réseau.
2. **Un ou plusieurs adaptateurs `adapter/out/kernel/KernelPaymentGatewayAdapter`** dans `tnt-billing-wallet`, implémentant les ports existants (`IWalletUseCase` pour les opérations conservées) ou un nouveau port `IKernelPaymentGatewayPort` pour les opérations pures de proxy (create wallet, pay, recharge, transactions).
3. **Réponses désenveloppées via `KernelResponses`** (`tnt-common-core`), comme tous les autres adaptateurs Kernel du repo (cf. mémoire *Kernel Facade Migration 2026-07-08* — ne pas reproduire le bug historique où l'enveloppe de réponse était sautée).
4. **Pour `billing-legacy-documents-controller` (64 opérations, 7 types de documents)** : réutiliser la **technique de codegen en masse déjà validée sur `tnt-hrm-core`** (voir mémoire *Bulk Kernel Proxy Codegen Technique*) — extraction des endpoints via `awk` depuis `docs/kernel-api/endpoints.md:4224-4702`, génération d'un contrôleur passthrough par type de document (`JsonNode` brut, comme HRM), validation par un smoke test de chargement de contexte. Ne pas mapper manuellement 64 endpoints à la main.
5. **Aucun secret provider (clé Stripe, secret webhook MTN, credentials Orange) ne doit plus exister dans `tnt-billing-wallet`** ni dans `application.yml` de `tnt-bootstrap` — ces credentials appartiennent désormais exclusivement à la configuration Kernel.

## Mapping des endpoints Kernel utilisés

| Besoin TiiBnTick | Endpoint Kernel | Remplace |
|---|---|---|
| Créer un wallet | `POST /api/payments/wallets` | `WalletService.createAndSaveWallet` |
| Lire un wallet / solde | `GET /api/payments/wallets/{walletId}`, `GET /api/payments/wallets/owner/{ownerId}` | `WalletService.getOrCreateWallet`, `getBalance` |
| Vérifier la capacité d'opérer | `GET /api/payments/wallets/{walletId}/can-operate` | Logique de solde disponible dans `Wallet.availableBalance()` |
| Débiter/payer | `POST /api/payments/wallets/{walletId}/pay` (`TransactionRequest`) | `WalletService.debitWallet`, `initiatePayment` (partie débit direct) |
| Recharger | `POST /api/payments/wallets/{walletId}/recharge` (`WalletRechargeRequest`) | `WalletService.creditWallet` (recharge externe) |
| Historique transactions | `GET /api/payments/wallets/{walletId}/transactions` | `WalletService.getTransactionHistory` |
| Initier un paiement provider (Mobile Money/Stripe) | `POST /api/payments/orders` (`InitiatePaymentRequest`) | `WalletService.initiatePayment` → `dispatchToProvider` |
| Callback provider (signature vérifiée côté Kernel) | `POST /api/payments/orders/callbacks/{provider}` | `PaymentWebhookController` (supprimé) |
| Suivre un paiement en cours | `POST /api/payments/orders/{id}/refresh`, `GET /api/payments/orders/{id}` | `WalletService.handlePaymentCallback` (partie polling/refresh) |
| Documents commerciaux (bons, factures fournisseurs, proforma, notes de crédit) | `billing-legacy-documents-controller` (7 types × CRUD + `payments/bank` + `payments/cashier` + `sync/accounting-invoice` + `sync/cashier-bill`) | Toute réimplémentation locale équivalente envisagée |
| Paiements legacy liés aux documents | `billing-legacy-payments-controller` (`/api/paiement`) | — |

## Question ouverte à trancher avant l'implémentation

**Le mapping entre `tnt-billing-invoice` (facturation mission TiiBnTick, numérotation `TNT-FACT-*`) et `billing-legacy-documents-controller` (documents commerciaux génériques du Kernel) n'est pas 1:1 évident** depuis la seule lecture du swagger. Deux options, à trancher en ADR avant de toucher au code :

- **Option A — Substitution** : les factures mission TiiBnTick deviennent des `factures-proforma` ou un type dédié piloté par le Kernel ; `tnt-billing-invoice` devient un thin wrapper autour du proxy Kernel + génération PDF locale.
- **Option B — Coexistence** : `tnt-billing-invoice` reste le système de facturation mission (domaine métier propre, numérotation propre), et le proxy `billing-legacy-documents-controller` est utilisé uniquement là où TiiBnTick a un besoin réel de documents commerciaux génériques (ex. achats internes, bons de livraison fournisseur pour `tnt-inventory-core`/`tnt-accounting-core`) sans toucher à `tnt-billing-invoice`.

Recommandation par défaut : **Option B**, car rien dans l'audit n'indique que la facturation mission (spécifique logistique) doit disparaître au profit d'un module ERP générique — mais c'est à confirmer par l'utilisateur avant d'entamer le sous-chantier facturation (le sous-chantier paiement/wallet, lui, est sans ambiguïté et peut démarrer immédiatement).

**Décision (2026-07-18, par l'utilisateur) : Option B — Coexistence.** `tnt-billing-invoice` reste intégralement le système de facturation mission TiiBnTick (numérotation `TNT-FACT-*`, PDF, note de crédit) — non substitué par le Kernel. Le proxy `billing-legacy-documents-controller` (étape 8) sera généré et utilisé uniquement pour les besoins réels de documents commerciaux génériques (achats internes, bons de livraison fournisseur pour `tnt-inventory-core`/`tnt-accounting-core`), sans jamais toucher au code ou au domaine de `tnt-billing-invoice`.

> **Correction (2026-07-18)** : l'agent ayant traité le sous-chantier wallet avait conclu à tort que `payment-gateway-controller`/`/api/payments/orders/**` n'existaient pas dans le Kernel — vérification directe par l'utilisateur dans `docs/kernel-api/endpoints.md` et `openapi.json` : **le contrôleur existe bel et bien**, avec ses 5 opérations (`GET /api/payments/orders` liste/historique, `POST /api/payments/orders` initiation, `POST /api/payments/orders/callbacks/{provider}` callback signé Stripe/MyCoolPay, `GET /api/payments/orders/{id}` lecture, `POST /api/payments/orders/{id}/refresh` refresh). Le mapping d'endpoints initial de ce chantier était donc correct. Les étapes 4 et 5 ci-dessous, précédemment bloquées sur cette fausse prémisse, sont débloquées.

## Étapes d'implémentation (ordre recommandé)

- [x] **1. ADR courte** : trancher la question ouverte ci-dessus (Option A/B) et documenter la décision dans ce fichier. Tranché : Option B (coexistence), 2026-07-18.
- [x] **2. Ajouter `kernelPaymentWebClient`** dans `KernelBridgeConfig` (tnt-bootstrap). Bean ajouté, même base URL/credentials que les autres. Vérifié : `tnt-bootstrap` compile.
- [x] **3. Construire `KernelPaymentGatewayAdapter`** implémentant les opérations wallet/pay/recharge/refresh, en réutilisant `KernelResponses`. Couvre exactement les 7 opérations confirmées dans le swagger Kernel (`payment-controller`) : create/get/get-by-owner/can-operate/pay/recharge/transactions. Les erreurs sur pay/recharge/can-operate **propagent** (pas de fail-open — ce sont des mouvements d'argent / une porte de sécurité avant débit). Voir la javadoc de `KernelWalletTransactionDto` pour une collision de nom de schéma côté Kernel découverte pendant l'implémentation (`TransactionRequest`/`TransactionResponse` réutilisés tels quels entre `payment-controller` et `blockchain-controller`/`bank-transaction-controller` — formes incompatibles). Vérifié : `KernelPaymentGatewayAdapterTest` (9 cas, MockWebServer).
- [x] **4. Rebrancher `WalletService`** : `dispatchToProvider` appelle désormais réellement `IKernelPaymentGatewayPort.initiateOrder` (`POST /api/payments/orders`, `payment-gateway-controller`) pour les canaux adossés à un vrai provider externe (`MTN_MOMO`/`ORANGE_MONEY` → `provider=MYCOOLPAY`, `method=MOBILE_MONEY` ; `STRIPE` → `provider=STRIPE`, `method=CARD` — mapping confirmé contre les enums du schéma `InitiatePaymentRequest` dans `openapi.json`, pas deviné). L'id d'ordre Kernel retourné est attaché au `PaymentIntent` (`PaymentIntent#attachProviderReference`, réutilise le champ `externalRef`) pour permettre la réconciliation de l'étape 5. Les erreurs d'initiation **propagent** (pas de fail-open — jamais laisser croire qu'un dispatch provider a eu lieu s'il a échoué). `CASH_ON_DELIVERY`/`WALLET` ne déclenchent aucun appel Kernel (pas de provider externe). Ce qui reste conservé tel quel, faute d'équivalent Kernel confirmé après re-vérification : freeze/unfreeze, réservation de solde, remboursement (`refundPayment`) — `payment-controller`/`payment-gateway-controller` n'exposent toujours rien pour ces trois opérations. `splitMissionRevenue`/`creditCommission`/`transferSubDelivererCommission`/ancrage trust/notifications inchangés. Vérifié : `KernelPaymentGatewayAdapterTest` (+4 cas `initiateOrder`/`refreshOrder`, 13/13) et `WalletServiceTest` (+2 cas dispatch Kernel, 9/9).
- [x] **5. Point d'entrée de confirmation de paiement — polling actif, pas de webhook.** Re-vérification précise demandée par l'utilisateur : `docs/kernel-api/endpoints.md` (section `payment-gateway-controller`, ligne 9958) et `openapi.json` confirment bel et bien les 5 opérations `/api/payments/orders/**`, dont `POST /api/payments/orders/{id}/refresh`. Aucun webhook sortant Kernel→TiiBnTick n'est documenté nulle part dans le spec (recherché explicitement) — le mécanisme retenu est donc le polling actif via `refresh`, comme anticipé par le chantier. Implémenté : `WalletService.pollPendingProviderOrders()` (`@Scheduled(fixedDelayString = "${tnt.billing.wallet.kernel.order-poll-interval-ms:20000}")`, 20s par défaut — backoff raisonnable pour un flux où l'utilisateur attend un push USSD/redirect carte — gardé par ShedLock `@SchedulerLock` comme `ReconciliationService`, un seul poll actif par déploiement multi-instance). Chaque tick récupère les `PaymentIntent` `PENDING` porteurs d'un id d'ordre Kernel (`IPaymentIntentRepository#findAllPendingWithProviderReference`, nouvelle requête dérivée R2DBC), appelle `refreshOrder` (fail-open : une erreur de poll ne casse pas la boucle, retenté au tick suivant), et route un statut terminal vers le **même** chemin interne que l'ancien webhook (`applyProviderStatus`, extrait de `handlePaymentCallback`) — donc `tnt-trust-core`/`PaymentAnchorAdapter` continue de recevoir `PaymentConfirmed` exactement comme avant. La logique de reconciliation est isolée dans `reconcilePendingProviderOrders()` (sans l'assertion ShedLock) pour rester testable unitairement. Vérifié : `WalletServiceTest` (+2 cas, ordre confirmé → ancrage trust déclenché ; ordre encore en cours → aucune écriture).
- [x] **6. Supprimer** `PaymentWebhookController`, `StripeAdapter`, `MtnMoMoAdapter`, `OrangeMoneyAdapter`, `IMoMoPaymentPort`, les `*Properties` provider et leurs entrées `application.yml`. Fait, y compris `MoMoPayload` (devenu orphelin) et les DTOs `OrangeCallbackRequest`/`MtnCallbackRequest`. `RedisIdempotencyStore` conservé. Vérifié : suite de tests `tnt-billing-wallet` verte (38/38), aucune référence résiduelle aux types supprimés ailleurs dans le repo (grep repo-wide).
- [x] **7. Corriger l'IDOR tenant** de `InvoiceController.java` (`X-Tenant-Id` client → JWT) dans la foulée, indépendamment de l'arbitrage A/B. Les 5 occurrences du fichier (`generate`, `listByClient`, `cancel`, `markPaid`, `issueCreditNote` — lignes 43/70/78/88/105 avant correctif) résolvent désormais le tenant via `@CurrentUser TntUserIdentity` (JWT), plus jamais via l'en-tête client. Vérifié : nouveau `InvoiceControllerTest` — un en-tête `X-Tenant-Id` usurpé pointant vers un tenant tiers est ignoré, la commande dispatchée au service porte toujours le tenant du JWT (3/3 tests, reproduisent l'accès cross-tenant avant/après correctif) ; suite complète `tnt-billing-invoice` verte (20/20).
- [x] **8. Générer le proxy `billing-legacy-documents-controller`** via la technique de codegen HRM. Nouveau module **`business/tnt-legacy-documents-core`** (L4, package `com.yowyob.tiibntick.core.legacydocuments`), enregistré dans le `pom.xml` racine (`<modules>` + `<dependencyManagement>`). Même architecture que `tnt-hrm-core` : `AbstractLegacyDocumentProxyController` partagé, `IKernelLegacyDocumentGatewayPort`/`KernelLegacyDocumentGatewayAdapter` (proxy HTTP brut vers le `kernelWebClient` partagé, `JsonNode` opaque, enveloppe `KernelApiEnvelope` → `ApiResponse`), un contrôleur passthrough par type de document — 7 contrôleurs générés via script Python jetable (`gen_legacy_docs_controllers.py`, endpoints extraits manuellement de `docs/kernel-api/endpoints.md:4224-4702` et vérifiés un par un) : `BonCommandeController` (8 ops), `BonAchatController` (9), `BonLivraisonController` (11, avec `GET client/{idClient}` et `POST {id}/effectuer`), `FactureFournisseurController` (8), `FactureProformaController` (10), `BonReceptionController` (9), `NoteCreditController` (9) — **64 opérations au total**, correspondant exactement au compte annoncé par le chantier. Respecte strictement l'ADR Option B : ce module ne déclare aucune dépendance vers/depuis `tnt-billing-invoice` (vérifié par grep) et ne touche à aucun de ses fichiers. Vérifié : le module compile, et un test de chargement de contexte (`TntLegacyDocumentsAutoConfigurationTest`, `ReactiveWebApplicationContextRunner` avec un bean `kernelWebClient` de test) confirme que les 7 contrôleurs + l'adaptateur/service Kernel s'enregistrent correctement (1/1).
- [x] **9. Mettre à jour les modules dépendants** (`tnt-market-back-core`, `tnt-accounting-core`, wiring `tnt-bootstrap`) et vérifier `mvn -pl billing/tnt-billing-wallet,billing/tnt-billing-invoice,trust/tnt-trust-core -am verify`. **Statut (2026-07-19) :** `tnt-market-back-core` n'appelle que le port `IWalletUseCase` (`debitWallet`/`creditCommission`, canal `WALLET` uniquement — jamais les adaptateurs provider supprimés), donc aucun changement de code n'était nécessaire, isolé par la frontière hexagonale ; vérifié via `mvn -pl coreBackend/tnt-market-back-core -am test` (vert). `tnt-accounting-core`/`tnt-trust-core` non cassés (topics/ports inchangés).
- [x] **10. Vérifier qu'aucun secret provider** ne subsiste dans les fichiers de config versionnés (`application*.yml`, `docker-compose.yml`). Grep repo-wide sur les noms de variables d'env provider (MTN_*, ORANGE_*, STRIPE_*) : zéro occurrence restante.

## Note de vérification (étapes 2, 3, 6, 7 — 2026-07-18)

`mvn -pl billing/tnt-billing-wallet,billing/tnt-billing-invoice,trust/tnt-trust-core -am verify` → **BUILD SUCCESS**.

- `tnt-billing-wallet` : 38/38 tests (dont les 9 nouveaux `KernelPaymentGatewayAdapterTest`).
- `tnt-billing-invoice` : 20/20 tests (dont les 3 nouveaux `InvoiceControllerTest` reproduisant l'IDOR tenant).
- `tnt-trust-core` : 61/61 tests, y compris `PaymentAnchorAdapterTest` — la chaîne `IPaymentAnchorPort` reste intacte (non modifiée par ce chantier).

L'agent ayant réalisé ce sous-chantier travaillait dans un worktree isolé dont la base (`pom.xml` sans version pour `tnt-go-freelancer-point-back-core`, `trust/tnt-trust-core` absent des `<modules>`) était en retard sur `master` — ces deux manques étaient déjà résolus sur `master` au moment de la fusion et n'ont donc pas eu besoin d'être reportés ici.

Un défaut pré-existant, sans lien avec ce chantier, a été identifié mais **non corrigé** (hors périmètre) : `coreBackend/tnt-go-freelancer-point-back-core/.../CommissionCoreService.java:96` ne compile pas (« local variables referenced from a lambda expression must be final or effectively final »), ce qui fait échouer un build `-am` incluant `tnt-bootstrap` en entier. Sans impact sur `tnt-billing-wallet`/`tnt-billing-invoice`/`tnt-trust-core`, qui compilent et passent leurs tests indépendamment de ce module. À vérifier séparément.

## Note de vérification (étapes 4, 5 — 2026-07-18)

Travail réalisé dans un worktree isolé branché directement sur `master` (le worktree assigné à l'origine ne contenait pas l'historique de ce chantier ni les modules `tnt-go-freelancer-point-back-core`/`tnt-trust-core` à jour — écart déjà noté ci-dessus pour les étapes 2/3/6/7).

`mvn -pl billing/tnt-billing-wallet,billing/tnt-billing-invoice,trust/tnt-trust-core -am verify` → **BUILD SUCCESS**.

- `tnt-billing-wallet` : **48/48** tests (dont les 4 nouveaux cas `KernelPaymentGatewayAdapterTest` sur `initiateOrder`/`refreshOrder` et les 4 nouveaux cas `WalletServiceTest` sur le dispatch Kernel et la réconciliation par polling).
- `tnt-billing-invoice` : 27/27 tests (inchangé par ce sous-chantier).
- `tnt-trust-core` : 71/71 tests (inchangé par ce sous-chantier — `IPaymentAnchorPort`/`PaymentAnchorAdapter` non touchés, `PaymentConfirmed` toujours publié par le même chemin interne).

Vérifié également : aucune référence résiduelle à `payment-gateway-controller`/`/api/payments/orders` en dehors de `IKernelPaymentGatewayPort`/`KernelPaymentGatewayAdapter`/`WalletService` (grep repo-wide).

Ambiguïtés rencontrées et tranchées avec prudence (documentées dans le code) :
- `InitiatePaymentRequest.clientId`/`serviceCode` sont volontairement **omis** du corps envoyé à `POST /api/payments/orders` : leur sémantique exacte (vs. l'en-tête `X-Client-Id` déjà envoyé par `kernelPaymentWebClient`) n'est pas confirmée par le schéma Kernel, et les deux champs sont optionnels — aucune conséquence fonctionnelle à les omettre plutôt que deviner.
- Le statut `PaymentOrderResponse.status` n'a pas d'enum documenté (comme `KernelWalletTransactionDto.status` à l'étape 3) — même heuristique de classification best-effort reprise (`isSuccess`/`isFailure`, penchant vers « encore en attente » pour toute valeur non reconnue).
- Le champ à utiliser comme `financialTransactionId` lors de la confirmation via polling n'est pas nommé explicitement dans le swagger comme tel : `providerReference` est utilisé (avec repli sur l'`id` de l'ordre Kernel si absent), car c'est le champ le plus proche sémantiquement d'une référence de transaction fournisseur.

## Note de vérification (étape 8 — 2026-07-18)

`mvn -pl business/tnt-legacy-documents-core -am verify` → **BUILD SUCCESS**. `tnt-legacy-documents-core` (nouveau module) : 1/1 test — `TntLegacyDocumentsAutoConfigurationTest`, chargement de contexte confirmant les 7 contrôleurs + l'adaptateur/service Kernel s'enregistrent correctement.

Vérifié également : `tnt-legacy-documents-core` ne déclare aucune dépendance Maven vers/depuis `tnt-billing-invoice` (grep sur les deux `pom.xml`), et l'ensemble `billing/tnt-billing-wallet,billing/tnt-billing-invoice,trust/tnt-trust-core,business/tnt-legacy-documents-core` continue de passer `mvn ... -am verify` (48/27/71/1 tests, 0 échec).

## Ce que ce chantier ne couvre pas

- La correction des ~40 contrôleurs de mutation sans autorisation ni les ~139 `findById` sans filtre tenant ailleurs dans le repo (`coreBackend/` notamment) — c'est un chantier distinct de l'[Audit n°7](../audit-7-system-design.html), à traiter en parallèle.
- L'alignement des noms de topics Kafka ([Audit n°5](../audit-5-kafka.html)) — orthogonal, mais à vérifier si les événements de paiement Kernel introduisent de nouveaux topics à intégrer au référentiel unique recommandé.
