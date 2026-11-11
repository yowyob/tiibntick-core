# Chantier — Wallet Kernel créé automatiquement

**Branche :** `feat/wallet-auto-provisioning`
**Demandé le :** 2026-11-11 (utilisateur) — un utilisateur qui a créé son profil TiiBnTick et qui est connecté doit pouvoir utiliser son wallet sans rien faire ; le Core vérifie si le wallet existe et le crée sinon.
**Prolonge :** [kernel-wallet-migration](kernel-wallet-migration.md) (point ouvert « Livreurs sans wallet Kernel », demande R7).

## Décisions prises avec l'utilisateur
| Sujet | Décision |
|---|---|
| Déclencheurs | À la **connexion** (login / MFA / sélection de contexte / refresh / sign-up relayés par `/api/v1/auth/**`) **et** à la **création du profil** client ou freelancer. Pas de création « à l'usage » dans les opérations wallet. |
| Périmètre | **Tout compte connecté** reçoit un wallet personnel, quel que soit son profil. |
| Vérification réelle | Oui, sur le compte de l'utilisateur, contre le Kernel de production (voir « Vérifié »). |

## Pourquoi c'est faisable
Le Kernel ne crée un wallet que pour son propriétaire authentifié (`POST /api/payments/wallets/mine`). Le Core ne peut donc agir qu'aux moments où il tient le jeton de l'utilisateur :
- **Login** : la passerelle plateforme relaie la réponse du Kernel, qui contient l'`accessToken`. Le Core l'utilise tout de suite, en tâche de fond.
- **Création de profil** : la requête porte déjà le JWT de l'utilisateur ; le relais Bearer habituel du client Kernel suffit.

## Ce qui a été fait
- `tnt-common-core` : port `AuthenticatedSessionListener` (`onTokenIssued`, `onProfileCreated`) et `AuthenticatedSessionPublisher` (diffusion *fire-and-forget*, un écouteur en échec n'arrête ni les autres ni l'appelant).
- `tnt-auth-core` : bean du diffuseur (les écouteurs sont résolus à chaque événement, pas à la construction).
- `tnt-platform-gateway-core` : `KernelAuthGatewayService` annonce tout `accessToken` d'une réponse Kernel 2xx. Les réponses d'erreur et les défis MFA (202, sans jeton) n'annoncent rien. La réponse au client n'est jamais modifiée.
- `tnt-actor-core` : `ClientController` et `FreelancerController` annoncent la création du profil (injection par setter optionnel, comme `ProfileLanguageSupport`, donc les contrôleurs construits à la main fonctionnent toujours).
- `tnt-billing-wallet` : `WalletAutoProvisioningListener` → `KernelWalletService.getOrCreateMyWallet` (GET `wallets/mine`, puis POST si 404). Cache par utilisateur (`sub` du JWT) : succès retenu 1 h, échec 5 min, pour que les refresh de jeton n'appellent pas le Kernel à chaque fois.
- `tnt-bootstrap` : `KernelBridgeConfig.propagateBearerToken` accepte un jeton explicite via le contexte Reactor (`tnt.session.bearer-override`), car la requête de login ne porte pas de JWT.
- Configuration : `tnt.wallet.auto-provision.{enabled,success-ttl,failure-ttl}` (variables `TNT_WALLET_AUTO_PROVISION_*`).

**Aucune rupture d'API.** Rien n'est ajouté au contrat REST ; `POST /billing/kernel-wallet/me` reste utilisable.

## Vérifié
- 12 tests unitaires (diffuseur, passerelle d'auth, écouteur wallet) + 3 tests du contrôleur de profil.
- 5 tests de bout en bout (`WalletAutoProvisioningEndToEndTest`) : passerelle → diffuseur → écouteur → `KernelWalletService` → vrai adaptateur HTTP → vrai bean `kernelPaymentWebClient` → faux Kernel (WireMock). Ils prouvent que le GET/POST `wallets/mine` part avec **le jeton que l'utilisateur vient d'obtenir** (login) ou **son JWT de requête** (profil), que le Kernel en panne ne casse pas le login, et qu'un défi MFA ne touche aucun wallet.
- 1 test de câblage dans le contexte Spring complet (`WalletAutoProvisioningWiringTest`, avec Postgres/Redis/Kafka réels), plus `TiiBnTickApplicationTest` et `PublicPathsSecurityTest` verts.

### Vérification réelle contre le Kernel de production (2026-11-11, compte de l'utilisateur, espace partagé)
Appel direct du Kernel avec le jeton de l'utilisateur et l'identité applicative du Core (le Core de production ne porte pas encore cette branche, donc le code du Core n'a pas été exercé en réel) :
- `GET /api/payments/wallets/mine` → **200** (~1,3 s) : `{"success":true,"data":{"id","ownerId","ownerType":"USER","ownerName":"me:<actorId>","balance":0.00,"createdAt","updatedAt"},"message":"Personal wallet."}`. La forme correspond au `KernelWalletDto` du Core (enveloppe `success`/`data`).
- Le wallet **existait déjà** (créé à 05:36 UTC, environ 1 h 50 avant le test, pas par ce test) : le `POST wallets/mine` n'a donc **pas** été exercé en réel, ni le comportement du Kernel face à un second `POST` / à deux créations simultanées.
- `ownerId` du wallet = **actorId** de l'utilisateur (et non son `userId`) ; à garder en tête pour toute recherche par propriétaire.
- **Latence** : le tout premier appel de la session a dépassé 30 s sans réponse, les suivants répondent en ~1,3 s. Le client Kernel du Core coupe à 5 s (`tnt.kernel.response-timeout-ms`) : un premier appel froid peut donc échouer côté Core ; l'échec est avalé et le wallet est revérifié 5 min plus tard (`failure-ttl`), à la connexion ou au refresh suivant.

## Limites connues
- **Les connexions faites directement auprès du Kernel** (sans passer par `/api/v1/auth/**` du Core) ne déclenchent pas la création au login ; elles sont rattrapées à la création du profil, ou par `POST /billing/kernel-wallet/me`.
- **Un profil créé avant ce déploiement** n'a pas de déclencheur « création » ; son wallet est créé à la prochaine connexion qui passe par le Core.
- **Tâche de fond sans garantie** : si le Core redémarre pendant la création, elle est perdue ; elle sera retentée au prochain login (cache d'échec de 5 min). Aucun journal persistant.
- **Wallet personnel uniquement** (pas de `organizationId`) ; le Core n'envoie que `label` (absent ici).
- **Course possible** : deux logins simultanés d'un même utilisateur sans wallet peuvent chacun créer. Le comportement du Kernel (refus ou doublon) n'est pas documenté ; le cache réduit mais n'élimine pas ce risque.
- **Le statut `201` de `POST /billing/kernel-wallet/me`** est renvoyé même quand le wallet existait déjà (inchangé).
- Les profils créés par les contrôleurs propres à Go-Freelancer (`/api/clients`, `/api/freelancers` du module `tnt-go-freelancer-point-back-core`) ne sont pas branchés ; seuls les profils de `tnt-actor-core` (`/api/v1/clients`, `/api/v1/freelancers`) le sont. La connexion reste le déclencheur principal.
