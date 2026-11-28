# Registre des décisions de l'utilisateur

> **Règle permanente (décidée par l'utilisateur le 2026-11-26).** Ce fichier versionné consigne **toutes** les décisions
> prises par l'utilisateur (le mainteneur) sur le Core : pour chaque question posée, la question, les options telles
> qu'elles ont été présentées (libellé et détail) et la réponse choisie, **telle quelle, au moment de la sélection**.
> Toute nouvelle question posée à l'utilisateur, dans n'importe quel chantier, est ajoutée ici dans le même commit que
> le travail qui en découle (ou dans un commit dédié juste après), à la suite, sans jamais réécrire une entrée passée.
> Une décision qui en remplace une autre fait l'objet d'une nouvelle entrée qui cite l'ancienne.
>
> Tout ce qui demande l'intervention d'un administrateur est en outre reporté dans
> [la spécification de la plateforme d'administration](../plateforme-administration/specifications.md).

## Sommaire

1. [Directives données en message libre](#1-directives-données-en-message-libre)
2. [Décisions par questions-réponses (ordre chronologique)](#2-décisions-par-questions-réponses-ordre-chronologique)
3. [Documents de décisions détaillés par chantier](#3-documents-de-décisions-détaillés-par-chantier)

## 1. Directives données en message libre

Citations exactes des messages de l'utilisateur qui fixent une règle ou une décision (hors questions à options).

| Date | Directive (citation) |
|---|---|
| 2026-11-24 | « Afin de limiter tout impact sur les clients Mobile et le BFF, privilégiez la modification des mécanismes internes plutôt que des contrats d'interface. Conservez les endpoints existants en surface, mais déléguez le traitement en arrière-plan aux modules métier des couches inférieures déjà existants. » |
| 2026-11-24 | « Pour ce cas, on utilise les ids du coeur partout et on force tous les autres développeurs à faire pareil et c'est non négociable. » |
| 2026-11-24 | « Si nécessaire modifier les modules des couches inférieures par ajout pour qu'il soit le plus global possible et accessibles à tous les modules de corebackend. » |
| 2026-11-24 | « Assures toit aussi que tout ce qui concerne l'asynchrone et kafka dans le projet est bien fonctionnel et cabler. » |
| 2026-11-24 | « Et utilises plusieurs agents pour aller plus vite et être très éfficace avec un agent modérateur/censeur/architecte, pour les worktree donnes leur des noms congrès je ne veux pas des noms style : «worktree-agent-*» » |
| 2026-11-24 | « Pas de décisions d'après le code au piff par les agents d'implémentation, attendre l'architecte. » |
| 2026-11-25 | « Étant donné que un nouveau livreur doit être validé par un administrateur désormais, je veux que tu notes tout cela dans le rapport .md de ce travail afin qu'il soit pris en compte plus tard quand oon ferra la plateforme d'administration. Et fait le pour toutes étapes métiers nécessitants une validation d'un administrateur. » |
| 2026-11-26 | « Concernant les évènements de livraison arranges tout ce qui ne vas pas et connectes tout normalement. Résoudre toutes les incohérence trouvées le plus éfficement possile […] je veux que tu résolves cela en lui créant son propre cycle d'états (tous les états du colis déjà present dans le cycle de vie ancré sur blockchain sont reconduit sans débat et y intégrer avec homogénéité les litiges et retour) complet et fonctionnel de bout en bout et puis pour les évènements et topics kafka il faudra créer uniquement ce qui est nécessaire car sur cet aspect là beaucoup de chose se chevaucherons car un colis est rattaché à une livraison. De plus pour les états d'une livraison il faudra ajouté aussi les états complets liés à la gestion et suivis des incidents déclarés concernant une livraison(seul un livreur peu déclaré un incident) avec création des évènements associés et topics kafka. » |
| 2026-11-26 | « La création d'anonce dans Go&Freelancer et Point Relais est censé envoyer les différentes données dans un topic kafka pour que toutes les plateformes et éléments qui veulent lire et utiliser ces données consommeront. Et pour tous les évènements possible qui doivent être afficher(par d'autres consommateurs) directement après leur création/modification envoyer les modifications directement via Kafka pour que les autres puisse lire et actualiser de leur côté. Parmi les évènements nous avons tou ceux liées aux états d'un colis et d'une livraison, tout(états, traitement et autres) ce qui concerne les incidents et les litiges. De plus faire un fichier .html de guide pour l'utilisation de kafka(topics avec rôles et autres, producteurs et consommateurs déjà connus et branché dans le core) dans TiiBntick afin que les cosommateurs puissent utiliser éfficacement de leurs côtés. Concernant les topics kafka branche tous normalement(producteurs et consommateurs) sans exception et assures toi que tout soit fonctionnel de bout en bout en condition réelle. » |
| 2026-11-26 | « Et pour les états des colis et livraisons asssures toi de la cohérence(des transitions et autres) et de l'homogénéité des cycles d'états de ces entités. » |
| 2026-11-26 | Point relais (contexte métier, **ne pas implémenter sans demande**) : « le point relais c'est un peu comme une mini organisation quoi où le propriétaire peu disposer lui même de sa propre flotte de véhicule pour livraison qu'il utilise via des livreurs indépendants pour faire et il peu aussi gérer la livraison dès son début dans la mesure où un client viens au point relais avec son colis qu'il veut faire livrer, le point relai récupère le colis et puis par la suite le soutraites à un livreur indépendant pour livraison où via des livreurs utilisant sa propre flotte de véhicules. De plus un client peu directement venir retirer son colis à un point relai. On reste juste sur l'aspect de ce que fait Point relais, je ne te demande pas d'implémenter tout ceci. » |
| 2026-11-26 | « Ternir un fichier .md versionné git dans le dépôt qui mentionne toutes mes décisions prises(question posée + réponse prise avec tous les détails comme tel au moment de la sélection de l'option) fais ceci systématiquement dans tout le projet désormais et pour toutes les questions posés. De plus tout ce qui demande un intervention administrateur doit être automatiquement ajouté au document des spécifications de la future plateforme d'administration de TiiBntick. Appliques ceci systématiquement dans la suite du projet. […] Sans oublier toutes les décisions déjà enregistrer dans le fichier decisions-evenements-colis-livraison.md. Et je veux aussi que le fichier tarifs-par-vehicule.md soit bien conservé et versionné dans le core pour être retrouver dans toutes les circonstances. » |
| 2026-11-26 | « Je tiens aussi à ce que tu ajoutes dans la spécification de la plateformes d'administration que toutes les actions d'administrateurs doivent être journaliser et persister en immuable pour que l'on puisse retrouver qui a fit quoi et que rien de puisse être modifier une fois journaliser. Nous sommes ici hors blockchain. » |
| 2026-11-26 | « Toujours mettre à jour progressivement les documents concernés quand je répond aux questions conformément à ce qu'on c'est dit. » |
| 2026-11-26 | « Ne lances pas plus d'agents. » puis « Arrêtes celui que tu viens de lancer puis toi même fais le travail ici dans la conversation pendant que l'autre agent continue ce qu'il fait. » |
| 2026-11-26 | « Et je veux que absolument tout soit fait sans exception, ne t'arrêtes pas tant que ce n'est pas fini. » |
| 2026-11-26 | « Mais la blockchain de côté pour le moment » (lot L7 : `TrustCommittedEventConsumer` laissé tel quel) |
| 2026-11-27 | « Vérifie un peu si il y a de nouvelle nodification sur la branche main et si c'est le cas, récupères les intégres les et vérifie la compatibilité avec ma branche de travail actuelle et corriges ce qu'il faut si nécessaire pour que tout soit aligné sur ma branche actuelle. » |
| 2026-11-27 | « Ma branche doit rester la reférence et les modifications de main sont intégrerau au mieux tant que mon travail sur cette branche n'est pas casser. Si quelques choses de particulier poses problèmes pose moi cela. » |
| 2026-11-27 | « J'aimerais que l'on fasse des modifications, proposes moi tout ce qui te semble éfficace via des questions avec propositions et recommandations et agis comme un ingénieur très expérimenté et qualifié. De plus j'ai déjà une doliance j'aimerais que lorsque on assigne le role TNT_ADMIN à quelqu'un qu'il n'ait plus besoin obligatoirement de se connecter via le tenant système pour que son role TNT_ADMIN soit éffectif et aussi pas obliger que l'utilisateur soit dans le tenant système pour qu'on puisse lui assigner le role TNT_ADMIN. Règle aussi éfficacement le problème lié à : «il passe les contrôles par permission (@RequirePermission) mais pas ceux écrits @PreAuthorize("hasRole('TNT_ADMIN')")». Par contre je veux que l'option de delete du role TNT_ADMIN ne soit pas possible pour le propriétaire du tenant système. Le core doit aussi enregistrer qui à assigner le role à qui et pour le propriétaire du tenant système le marqué dans cette logique SYSTEM_OWNER. J'aimerais aussi que l'on mettes en place un mécanisme qui fera que quand le propriétaire du tenant système se connectes qu'il puisse lui même assigner le rôle TNT_ADMIN afin que même en se reconnectant plus tard avec un autre tenant(différent du tenant système) qu'il soit toujours reconnu comme TNT_ADMIN. On doit aussi enregistrer le tenant à partir duquel quelqu'un obtient le rôle TNT_ADMIN et la possibilité aussi de révoquer tout les roles TNT_ADMIN dont le tenant d'obtention est différent du tenant système. Structures bien tout ceci et n'oublie d'ajouter aussi tes proposition sachant que on veux une cohérence maximale. Aussi je veux savoir vu que les rôles sont provisionner dans le kernel au démarrage de l'application pourquoi est ce que celui-ci ne renvoie pas un jwt avec les rôles de TiiBnTick si c'est possible de résoudre cela fais le. » |
| 2026-11-27 | « Enregistres et versionnes toutes ces décisions dans un fichier dédié. » |
| 2026-11-27 | « L'attribution et la révocation doivent bien déclencher l'envoie d'un message automatique au OWNER du tenant système pour l'informer bien sans oublier le mail. et le message doit respecter les préférences linguistiques du OWNER en question. » |

Les règles permanentes de travail (commits en anglais sans co-auteur, jamais de push, jamais de publication en ligne,
ne jamais lire `tiibntick-platforms`, changesets Liquibase jamais modifiés, tests d'intégration exécutés avant de dire
« vérifié », diagrammes PlantUML fond blanc et lignes orthogonales) sont dans `CLAUDE.md`.

## 2. Décisions par questions-réponses (ordre chronologique)

Extraites des sessions de travail : la question et les options sont reproduites telles qu'elles ont été affichées ;
« (Recommended) »/« (Recommandé) » marque l'option recommandée à l'époque. Une réponse entre guillemets est un texte
libre saisi par l'utilisateur. Horodatage en UTC.

### 2026-11-10 06:46 — questions sans réponse enregistrée

- Que doit contenir la page d'accueil contextualisée de chaque espace (client et livreur freelance) à la connexion ?
- Comment positionner « Go » (prix fixé par le client) par rapport au mode « Freelancer » (devis des livreurs) ?
- Un même compte peut-il être à la fois client ET livreur freelance, avec un sélecteur de profil dans l'interface ?
- Que faire des écrans et pages « Go » existants (`/go`, doublon de `/user`, et les landings `/TiiBnTick-Go-Frontend` et `/TiiBnTick-Freelancer-landing`) ?

> Réponse de l'outil : The user doesn't want to proceed with this tool use. The tool use was rejected (eg. if it was a file edit, the new_string was NOT written to the file). To tell you how to proceed, the user said: The user wants to clarify these questions.     This means they may have additional information, context or questions for you.     Take their response into account and then reformulate the questions if appropriate.     Start by asking them what they would like to clarify.      Questions asked: - "Que doit contenir la page d'accueil contextualisée de chaque espace (client et livreur freelance) à la conne

### D-001 · Crédits Core · 2026-11-10 07:46 UTC

**Question :** Le Kernel n'a aucun endpoint pour créditer un wallet côté serveur : les transferts exigent un code de confirmation saisi par l'utilisateur, et `recharge` passe par un paiement Mobile Money/carte. Or le Core doit créditer sans interaction (split de revenu de mission, commissions, indemnisation de litige, remboursement). Comment migrer ces flux ?

Options proposées :

- **Demander un endpoint au Kernel (Recommandé)** — On définit avec l'équipe Kernel un endpoint de crédit/transfert serveur à serveur, sans code de confirmation (clé API). Je code le Core sur un port `IKernelWalletLedgerPort`, avec le contrat proposé écrit dans un document à lui envoyer. Tant que l'endpoint n'existe pas, le flux reste derrière un feature flag désactivé.
- **Wallet plateforme + transferts** — Le Core utilise un wallet « plateforme » Kernel et fait des `transfers` vers chaque bénéficiaire. Impossible sans contourner le code de confirmation, donc à valider avec le Kernel.
- **Garder un grand livre de gains local** — Les gains internes (split, commissions) restent dans le Core. Le Kernel ne gère que recharge, retrait, solde visible et paiements. Ce n'est plus une migration complète.

**Réponse de l'utilisateur :** Je ne comprends pas ta question, poses là mieux !!

### D-002 · Gel/litige · 2026-11-10 07:46 UTC

**Question :** Le Kernel n'expose ni gel de wallet ni réservation de solde. Le Core s'en sert pour geler un paiement lors d'une escalade incident → litige. Que faire de ces gels ?

Options proposées :

- **Table de blocages locale (Recommandé)** — Le Core garde une petite table `wallet_holds` (montant bloqué par wallet et par litige), et vérifie le solde Kernel moins les blocages avant toute opération sortante. Le solde reste dans le Kernel.
- **Demander gel/réservation au Kernel** — On ajoute ces opérations à la liste des demandes envoyées à l'équipe Kernel, et le gel reste désactivé en attendant.

**Réponse de l'utilisateur :** Table de blocages locale (Recommandé)

### D-003 · Crédit auto · 2026-11-10 07:48 UTC

**Question :** Exemple : un client paie 5 000 XAF pour une course. Le Core doit ajouter automatiquement ~4 750 XAF au wallet du livreur, sans que personne ne clique. Le Kernel ne le permet pas aujourd'hui : on ne peut alimenter un wallet que par recharge Mobile Money, ou par un transfert qu'un humain confirme avec un code reçu par SMS ou mail. Que fait-on pour ce « crédit automatique » ?

Options proposées :

- **Demander cette fonction au Kernel (Recommandé)** — Je rédige la demande précise (route, champs) pour l'équipe Kernel et je prépare le Core pour l'appeler. Tant que le Kernel ne l'a pas livrée, ce crédit automatique reste désactivé, donc les livreurs ne sont pas crédités en production.
- **Garder les gains dans le Core** — Les gains de course (split, commissions) restent dans la base du Core, qui reste le livre de comptes pour cela. Le Kernel gère seulement recharge, retrait et paiements. Ça marche tout de suite, mais ce n'est plus une migration complète : deux soldes à afficher.

**Réponse de l'utilisateur :** Pour payer une livraison, le client recherche son propre wallet puis dans l'application il émet son paiement de la livraison avec tout le workflow puis la plateforme reduit la balance(solde) du wallet du client du montant payé et augmente la balance(solde) du wallet du livreur en question. Tout est logique par besoin d'une transaction réelle d'argent; après le livreur peu retirer son argent directement depuis son compte. Donc revoie encore bien les endpoints du kernel-core concernant le wallet afin de mettre ceci en place si possible et pose toutes tes questions si tu en as encore avec propositions.

### D-004 · Identité · 2026-11-10 07:48 UTC

**Question :** Identité (nom, prénom, téléphone) : comment le Core doit-il l'obtenir et la persister ? Le Kernel n'a pas GET /api/actors/{id} (d'où identity=null aujourd'hui), mais il a GET /api/actors/me et PUT /api/actors/me/identity.

Options proposées :

- **Relayer le bearer de l'utilisateur (Recommandé)** — Le Core appelle GET /api/actors/me avec le JWT de l'appelant pour lire l'identité, et expose PUT /api/v1/actors/me/identity qui relaie vers le Kernel. Aucune donnée dupliquée en base Core.
- **Compte de service** — Le Core lit/écrit via X-Api-Key sans le jeton utilisateur. Plus simple, mais le Kernel ne peut pas vérifier que l'acteur est bien l'appelant.
- **Copie locale dans le Core** — Colonnes nom/téléphone dans les tables client/freelancer (migration Liquibase). Indépendant du Kernel, mais deux sources de vérité.

**Réponse de l'utilisateur :** Relayer le bearer de l'utilisateur (Recommandé)

### D-005 · Wallet · 2026-11-10 07:48 UTC

**Question :** Wallet (C-01) : sur quoi le frontend doit-il se brancher quand on le réactivera ?

Options proposées :

- **Reporter, hors de cette branche (Recommandé)** — Tu as dit de vérifier d'abord le wallet Core contre celui du Kernel. Je documente l'écart dans la branche, sans coder de relais.
- **Relais Kernel /api/payments/wallets** — Endpoint Core /api/v1/wallets/me qui relaie vers le wallet du Kernel via kernelPaymentWebClient.
- **Wallet billing du Core** — Exposer le module tnt-billing-wallet tel quel au frontend.

**Réponse de l'utilisateur :** Reporter, hors de cette branche (Recommandé)

### D-006 · Périmètre · 2026-11-10 07:48 UTC

**Question :** Périmètre des autres corrections Core (C-07 404 sur client inconnu, C-03 activation admin freelancer, C-02 liste freelancers, C-13 masquer ctx/securityContext dans OpenAPI, C-12 DTO d'auth typés, C-14 liste de zones). Lesquelles inclure maintenant ?

Options proposées (choix multiple) :

- **C-07 + C-13 (Recommandé)** — Correctifs sans risque : 404 propre et nettoyage OpenAPI. Aucun changement de contrat pour les clients existants.
- **C-03 + C-02** — Nouveaux endpoints admin/liste freelancers. Utiles à l'espace admin, à valider côté sécurité (TNT_ADMIN).
- **C-12 + C-14** — DTO d'auth typés et liste de zones. Plus gros, touche la documentation de contrat.

**Réponse de l'utilisateur :** C-07 + C-13 (Recommandé), Il faut retirer la nécessité de créer validation par un admin pour la création d'un profil livreur freelancer.

### D-007 · Test local · 2026-11-10 07:48 UTC

**Question :** Test en conditions réelles : veux-tu que je lance le Core en local (docker compose + mvn spring-boot:run) pour vérifier la persistance des profils ?

Options proposées :

- **Oui, lancer le Core local (Recommandé)** — Docker compose (Postgres, Redis, Kafka…) puis app sur 8080. Je ne touche à rien d'existant sur ta machine sans te prévenir.
- **Non, tests unitaires seulement** — Pas de démarrage d'infrastructure. La persistance réelle reste à vérifier avec ton MFA.

**Réponse de l'utilisateur :** Oui, lancer le Core local (Recommandé)

### D-008 · Code livreur · 2026-11-10 08:04 UTC

**Question :** Ton flux correspond bien au `transfers` du Kernel : le client demande un code de confirmation (`/transfers/challenge`), le reçoit, puis valide son transfert (`/transfers`) vers le livreur. Mais le Kernel exige que le transfert vise un « code de réception » temporaire que le livreur génère lui-même depuis son wallet (`/receive-codes`). Comment le client obtient-il ce code ?

Options proposées :

- **Le Core le stocke (Recommandé)** — Quand le livreur accepte la livraison, son appli génère un code de réception et le Core l'attache à la livraison. Au paiement, le Core le fournit au flux du client, qui ne saisit rien de plus que son code de confirmation. Si le code a expiré, le livreur en régénère un.
- **Le client saisit le code** — Le livreur montre ou envoie son code au client, qui le tape. Simple côté Core, mais plus lourd pour l'utilisateur et sujet aux erreurs.

**Réponse de l'utilisateur :** Le Core le stocke (Recommandé)

### D-009 · Commission · 2026-11-10 08:04 UTC

**Question :** La plateforme prélève aujourd'hui 5 % de commission sur chaque course. Avec un seul transfert client vers livreur, le Core ne peut pas prélever cette part tout seul. Comment gérer la commission ?

Options proposées :

- **Demander un paiement avec commission (Recommandé)** — Je note dans la demande au Kernel un transfert qui répartit le montant entre livreur et wallet plateforme en une seule confirmation. En attendant, le paiement fonctionne sans commission, tracée comme « due » dans le Core.
- **Deux transferts** — Le client confirme deux transferts (livreur puis plateforme). Fonctionne avec le Kernel actuel, mais deux codes à saisir par course.
- **Commission prélevée au retrait** — Le livreur reçoit 100 %, et la commission est récupérée plus tard sur ses retraits. Nécessite aussi une fonction Kernel.

**Réponse de l'utilisateur :** Demander un paiement avec commission (Recommandé)

### D-010 · Flux sans humain · 2026-11-10 08:04 UTC

**Question :** Trois flux n'ont pas d'utilisateur présent pour confirmer : remboursement après litige, indemnisation de litige, paiement de commission d'agence. Le Kernel actuel ne peut pas les faire. Que fait-on ?

Options proposées :

- **Les lister comme demandes Kernel (Recommandé)** — Ces trois flux sont désactivés dans le Core avec un message clair (« non supporté par le Kernel »), et je prépare un document de demande à envoyer à l'équipe Kernel.
- **Les traiter à la main** — Le Core crée un enregistrement à traiter par un admin qui effectue le transfert depuis un wallet plateforme.

**Réponse de l'utilisateur :** Les lister comme demandes Kernel (Recommandé)

### 2026-11-10 10:41 — questions sans réponse enregistrée

- Audit notifications : le Core envoie bien l'email/SMS/WhatsApp/push au Kernel (POST /api/notifications/deliveries), mais garde aussi son propre mécanisme (table locale tnt_notifications, historique, préférences locales de repli, adaptateurs SMTP/Orange-MTN/WhatsApp/FCM directs désactivés par défaut, rendu des messages par i18n). Jusqu'où faut-il aller ?
- Les messages : aujourd'hui le Core les rend lui-même (clés i18n, FreelancerOrgNotificationTemplates, NotificationType) puis envoie un texte brut. Le Kernel sait gérer des modèles (templateCode + variables, par langue et canal). Que choisis-tu ?
- Problème constaté : le canal in-app (utilisé par Market, par Go-Freelancer pour les push, et par les notifications de statut) passe par un SimpMessagingTemplate STOMP qui n'existe pas dans cette appli WebFlux, donc ces envois échouent aujourd'hui avec « No provider configured ». Le Kernel offre les canaux WEBSOCKET et IN_APP avec une boîte de réception (/inbox). Que fait-on ?
- Contexte tenant/organisation : le Kernel refuse tout appel sans X-Tenant-Id ET X-Organization-Id, mais plusieurs appelants (Market, Go-Freelancer, provisioning d'agence) passent l'organisation à null, donc leurs envois sont rejetés. D'où doit venir l'organisation ?

> Réponse de l'outil : <tool_use_error>InputValidationError: This call included a question with fewer than 2 options, so it was rejected and the person never saw it. A question with a single option has no decision in it. Do not retry this call and do not invent a filler second option. Instead, state the one path you were going to offer as the approach you are taking, then continue with the task. If this call also contained questions with 2 to 4 options (each with distinct labels), you may re-ask those questions alone in a new call. Ask a question only when the person has at least two genuinely distinct choices.  [  

### D-011 · Modèles · 2026-11-10 10:41 UTC

**Question :** Pour les textes des notifications : le Core les rend aujourd'hui lui-même (clés i18n + FreelancerOrgNotificationTemplates) puis envoie un texte brut au Kernel. Le Kernel sait gérer des modèles (templateCode + variables, par langue/canal), mais ils sont propres à chaque tenant/organisation. Que choisis-tu ?

Options proposées :

- **Modèles dans le Kernel (Recommandé)** — Le Core envoie templateCode + variables et enregistre (idempotent, au premier envoi d'un tenant/organisation) ses modèles TiiBnTick via POST /templates. Plus aucun texte métier de notification côté Core, mais un peu de logique d'enregistrement par tenant.
- **Garder le rendu i18n du Core** — Le Core continue de fabriquer le texte (yow-i18n-kernel) et n'envoie que le corps au Kernel : plus simple, mais les textes métier restent dans le Core.

**Réponse de l'utilisateur :** Modèles dans le Kernel (Recommandé)

### D-012 · Rôles JWT · 2026-11-10 13:26 UTC

**Question :** Les rôles TNT (CLIENT, FREELANCER…) sont déjà définis et synchronisés vers le Kernel au démarrage, mais personne ne les attribue à l'utilisateur à la création de son profil. Quand et avec quels droits le Core doit-il les attribuer au Kernel ?

Options proposées :

- **À la création du profil, bearer de l'utilisateur (Recommandé)** — Créer le profil livreur ou client déclenche l'attribution du rôle FREELANCER ou CLIENT via les endpoints rôles du Kernel, avec le jeton de l'utilisateur. Le rôle apparaît dans le JWT après refresh. Si le Kernel refuse (droits insuffisants), je le signale au lieu de le masquer.
- **Avec un compte de service du Core** — Le Core attribue le rôle avec ses propres identifiants techniques. Cela marche même si l'utilisateur n'a pas le droit d'assigner des rôles, mais il faut stocker un secret et l'étendue de ce compte est sensible.
- **À l'inscription (avant tout profil)** — Tout nouveau compte reçoit CLIENT d'emblée, et FREELANCER à la création du profil livreur. Le Core ne voit pas l'inscription tant que yowauth la gère (I-09).

**Réponse de l'utilisateur :** À la création du profil, bearer de l'utilisateur (Recommandé)

### D-013 · Tenant rôles · 2026-11-10 13:26 UTC

**Question :** Les rôles existent par tenant : le Core ne provisionne que le tenant système et les agences onboardées. Un utilisateur dans « Mon espace » ou « Espace partagé » n'a pas ces rôles chez lui. Que fait le Core avant d'attribuer le rôle ?

Options proposées :

- **Provisionner le tenant à la demande (Recommandé)** — Appeler provisionForTenant(tid) (idempotent) juste avant l'attribution si les rôles manquent dans le tenant du jeton. Marche pour tout tenant, au prix d'un coût au premier profil.
- **Utiliser seulement le tenant système** — Tous les clients et livreurs vivent dans le tenant système. Plus simple, mais cela impose de changer la façon dont le frontend choisit le tenant à la connexion.

**Réponse de l'utilisateur :** Provisionner le tenant à la demande (Recommandé)

### D-014 · Politiques · 2026-11-10 13:26 UTC

**Question :** Le module livreur a sa propre grille (`delivery_person_pricing`, formule codée en dur) et ignore billing-pricing, DSL et templates. Comment le refaire autour de la couche billing ?

Options proposées :

- **Politique billing par livreur + façade (Recommandé)** — Chaque livreur possède une BillingPolicy (propriétaire freelancer) créée depuis un template, avec ses règles DSL. Le calcul passe par le moteur billing. Les routes /api/freelancers/{id}/policies et /api/pricing/calculate/freelancer restent comme façade, et une migration convertit les grilles existantes.
- **Remplacement net** — Je supprime l'ancienne grille et ses routes, le frontend utilise seulement /api/v1/billing/*. C'est plus propre, mais cela casse tous les clients existants de ces routes.

**Réponse de l'utilisateur :** Politique billing par livreur + façade (Recommandé)

### D-015 · Disponibilités · 2026-11-10 13:26 UTC

**Question :** Le DSL ne sait exprimer que des actions de prix (SET_BASE, ADD_FIXED, ADD_PCT, remises, SET_PER_KM/KG), pas un refus. Où stocker disponibilités, colis acceptés et plage de poids, qui sont des critères d'éligibilité et non des prix ?

Options proposées :

- **Nouvelle table d'éligibilité du livreur (Recommandé)** — Une table `freelancer_eligibility` (créneaux, types de colis, poids min/max) liée au livreur, lue par le matching. Le frais d'attente reste dans la politique billing (paramètre du template). Aucun changement du langage DSL.
- **Étendre le DSL avec une action de refus** — Ajouter REJECT au DSL pour que tout (éligibilité + prix) vive dans les règles. Plus uniforme, mais cela modifie le langage commun à toute la couche billing.

**Réponse de l'utilisateur :** Nouvelle table d'éligibilité du livreur (Recommandé)

### D-016 · Démarche · 2026-11-10 23:08 UTC

**Question :** Comment voulez-vous procéder sur ces 9 modules ?

Options proposées :

- **Audit d'abord, puis lots (Recommandé)** — Je produis d'abord une matrice module × flux métier × frontend (ce qui est câblé, mort ou cassé, vérifié par tests réels), vous la validez, puis j'implémente par lots.
- **Implémenter directement** — Je pars sur les flux prioritaires sans rapport préalable et je corrige au fil de l'eau.
- **Audit seulement** — Rapport d'écarts et plan chiffré, sans modifier le code pour l'instant.

**Réponse de l'utilisateur :** Audit d'abord, puis lots (Recommandé)

### D-017 · API livraison · 2026-11-10 23:08 UTC

**Question :** Deux API de livraison coexistent : `/api/v1/deliveries` (gofp) et `/api/v1/tenants/{tenantId}/deliveries` (tnt-delivery-core). Laquelle doit être la référence pour le frontend Go-Freelancer ?

Options proposées :

- **gofp en façade (Recommandé)** — Le frontend ne parle qu'à gofp, qui orchestre delivery-core, geo, realtime, notify, dispute, incident, media. Une seule porte d'entrée, le tenant est déduit du jeton.
- **delivery-core direct** — Le frontend appelle directement les modules logistics ; gofp ne garde que le matching et le paiement.
- **Je ne sais pas, à toi de trancher** — Je compare les deux après l'audit et je vous propose un choix argumenté.

**Réponse de l'utilisateur :** gofp en façade (Recommandé)

### D-018 · Flux métiers · 2026-11-10 23:08 UTC

**Question :** Quels flux métiers doivent être complets de bout en bout (Core + frontend) ? Cochez tous ceux qui comptent.

Options proposées (choix multiple) :

- **Livraison et suivi live** — Création → affectation → ramassage → transit → remise, avec position GPS livreur (realtime), itinéraire (route/geo) et notifications.
- **Litiges et médiation** — Client ou livreur ouvre un litige, preuves, commentaires, médiation (module dispute).
- **Incidents terrain** — Colis endommagé, retard, accident, transfert à un autre livreur (module incident, handover, coopération).
- **Preuves média et hors-ligne** — Photos, signature, preuve de remise (media) et synchronisation quand le livreur n'a pas de réseau (sync).

**Réponse de l'utilisateur :** Livraison et suivi live, Litiges et médiation, Incidents terrain, Preuves média et hors-ligne

### D-019 · Qualité exigée · 2026-11-10 23:08 UTC

**Question :** Quel niveau de vérification attendez-vous avant de dire qu'un flux est « fonctionnel » ?

Options proposées :

- **IT Core + tests frontend (Recommandé)** — Tests d'intégration Testcontainers (Postgres, Kafka si nécessaire) côté Core et tests vitest contre un faux Core côté frontend, plus un rapport honnête de ce qui n'a pas pu être vérifié.
- **Parcours E2E complet** — En plus : un parcours bout en bout démarrant le vrai Core local (docker compose) et appelé comme le frontend le ferait. Plus long, plus fiable.
- **Tests unitaires seulement** — Plus rapide, mais ne prouve pas le câblage entre modules.

**Réponse de l'utilisateur :** IT Core + tests frontend (Recommandé)

### D-020 · Temps réel · 2026-11-10 23:11 UTC

**Question :** Pour la position GPS du livreur et le suivi live côté client, le Core expose du SSE (`/api/v1/realtime/sse`) et un ping GPS REST, et le frontend a déjà un client SSE authentifié. Quel transport retient-on ?

Options proposées :

- **SSE + ping REST (Recommandé)** — On garde l'existant : le livreur envoie sa position par REST, le client reçoit les mises à jour par SSE. Pas de nouveau protocole à maintenir.
- **WebSocket** — Canal bidirectionnel unique, mais plus de code Core et frontend, et à valider avec votre infra (proxy, load balancer).

**Réponse de l'utilisateur :** SSE + ping REST (Recommandé)

### D-021 · Hors-ligne · 2026-11-10 23:11 UTC

**Question :** Le module sync permet au livreur de travailler sans réseau puis de resynchroniser. Le frontend Go-Freelancer est une app web Next.js sans mode hors-ligne. Que faut-il livrer sur ce point ?

Options proposées :

- **File d'attente livreur (PWA)** — Le frontend met en file d'attente les actions du livreur (statuts, preuves) quand le réseau tombe et les rejoue via `/api/v1/sync` au retour. Le plus complet, le plus lourd.
- **Core prêt, frontend plus tard** — Je vérifie et teste l'API sync côté Core (idempotence, conflits) mais je ne touche pas au frontend dans ce chantier.
- **Hors périmètre** — On ne traite pas sync pour l'instant.

**Réponse de l'utilisateur :** File d'attente livreur (PWA)

### D-022 · Arbitrage · 2026-11-10 23:11 UTC

**Question :** Pour les litiges et les incidents, qui traite le dossier côté TiiBnTick ?

Options proposées :

- **Console admin dans ce frontend** — Il faut connecter l'espace `/admin` (aujourd'hui entièrement non branché) : file des litiges, médiation, décision. Chantier plus large.
- **Client et livreur seulement** — Ils ouvrent, suivent, ajoutent des preuves et commentent dans l'app ; l'arbitrage reste dans un back-office hors de ce dépôt (non traité ici).
- **Je ne sais pas encore** — L'audit inclut l'écart côté admin et vous tranchez à la lecture du rapport.

**Réponse de l'utilisateur :** Je ne sais pas encore

### D-023 · Livrable audit · 2026-11-10 23:11 UTC

**Question :** Où et sous quelle forme voulez-vous recevoir l'audit ? (La règle globale m'interdit de publier un artifact en ligne ; ce sera un fichier local.)

Options proposées :

- **Markdown dans docs/audits du Core (Recommandé)** — Un fichier versionné avec la matrice module × flux × frontend, committé sur une branche dédiée du Core.
- **Page HTML locale** — Même contenu en HTML autonome comme les audits existants (`docs/audits/*.html`), plus agréable à lire.
- **Dans le chat seulement** — Synthèse ici, sans fichier.

**Réponse de l'utilisateur :** Markdown dans docs/audits du Core (Recommandé)

### D-024 · Source gabarits · 2026-11-11 04:54 UTC

**Question :** Où doivent vivre les messages et gabarits de notification (source de vérité) ?

Options proposées :

- **Base TiiBnTick + cache (Recommandé)** — yow-i18n-kernel gagne ses propres tables (Liquibase, R2DBC) et un cache mémoire/Redis invalidé à chaque modification. Les JSON actuels deviennent un jeu de départ (seed) idempotent. notify rend le texte avec les paramètres du destinataire, puis l'envoie au Kernel Yowyob déjà rendu.
- **Base TiiBnTick + synchro vers le Kernel Yowyob** — Même base TiiBnTick, mais on continue d'enregistrer chaque gabarit dans le moteur de notification du Kernel Yowyob, qui fait le rendu à l'envoi. Plus de pièces mobiles, mais le Kernel garde la main sur le rendu.
- **Garder les JSON dans le jar** — Pas de base : on complète seulement les fichiers fr/en. Contredit votre demande d'avoir tout en base.

**Réponse de l'utilisateur :** Base TiiBnTick + cache (Recommandé)

### D-025 · Langue profil · 2026-11-11 04:54 UTC

**Question :** Où stocker la langue choisie par l'utilisateur ? (un compte a un profil client et un profil livreur)

Options proposées :

- **Une préférence par compte (Recommandé)** — Une seule langue pour le compte, quel que soit le profil actif. Nouvelle table de préférences du compte côté Core (clé : identifiant Kernel de l'utilisateur), lue par notify et par toutes les plateformes. On migre l'existant de TntClientProfile.preferredLocale.
- **Une langue par profil** — On réutilise TntClientProfile.preferredLocale et on ajoute le même champ au profil livreur. Un même compte pourrait avoir deux langues différentes.
- **Dans le Kernel Yowyob** — On stocke la langue côté Kernel (profil d'identité) et le Core la lit. Dépend de l'API Kernel, que l'on ne modifie pas.

**Réponse de l'utilisateur :** Une préférence par compte (Recommandé)

### D-026 · Langues · 2026-11-11 04:54 UTC

**Question :** Quel périmètre de langues et quel format de code ?

Options proposées :

- **fr et en, avec repli régional (Recommandé)** — Les codes publics sont 'fr' et 'en'. Les variantes régionales actuelles (fr_CM, en_CM, fr_FR, en_US...) servent de chaîne de repli, et pidgin/en_NG restent en sommeil, prêts à activer sans changement d'architecture. Devise et format de prix suivent la langue.
- **Garder les 6 locales actuelles** — Le frontend propose fr_CM/en_CM etc. Plus fin, mais le sélecteur et les messages à maintenir sont plus nombreux.
- **Seulement fr_CM et en_CM** — Deux locales Cameroun, rien d'autre.

**Réponse de l'utilisateur :** fr et en, avec repli régional (Recommandé)

### D-027 · Textes UI · 2026-11-11 04:54 UTC

**Question :** Comment le frontend Go-Freelancer reçoit-il ses textes d'interface ?

Options proposées :

- **Depuis le Core, partagés par les plateformes (Recommandé)** — Le Core expose des paquets de messages par langue et par espace de noms (go, link, agency, point, market, common), avec ETag et cache. Le frontend les charge au démarrage et change de langue sans rechargement. Toutes les plateformes réutilisent le même mécanisme et les textes se modifient en base.
- **Fichiers JSON dans chaque frontend** — next-intl ou équivalent avec des fichiers fr.json/en.json dans le dépôt Go-Freelancer. Plus simple et hors ligne, mais chaque plateforme duplique ses textes et le kernel n'est pas utilisé côté UI.
- **Les deux** — Paquets du Core par défaut, avec un jeu embarqué dans le frontend comme secours si le Core est injoignable (utile pour la file hors ligne).

**Réponse de l'utilisateur :** Depuis le Core, partagés par les plateformes (Recommandé)

### D-028 · Couverture UI · 2026-11-11 04:59 UTC

**Question :** Quelle part du frontend Go-Freelancer faut-il traduire dans cette livraison ? (tous les écrans ont aujourd'hui des textes français en dur)

Options proposées :

- **Tous les écrans (Recommandé)** — Connexion, inscription, dashboards client et livreur, expédition, livraison et suivi, messagerie, litiges, médiateur, abonnements, portefeuille, notifications, erreurs et validations. Un test garde-fou échoue si une clé manque en fr ou en en. C'est le plus long, mais rien n'est laissé en français.
- **Parcours essentiels puis le reste** — Navigation, auth, dashboards, notifications, messagerie, livraison et suivi d'abord ; les écrans secondaires restent en français avec une liste des clés restantes.

**Réponse de l'utilisateur :** Tous les écrans (Recommandé)

### D-029 · Format gabarit · 2026-11-11 04:59 UTC

**Question :** Quel format de gabarit pour les messages avec paramètres, et quels canaux ?

Options proposées :

- **{{param}} + pluriel et formats (Recommandé)** — On garde la syntaxe {{param}} déjà utilisée, et on ajoute le pluriel (1 colis / 3 colis) et des formats typés selon la langue : date, heure, montant avec devise, distance. Un gabarit peut avoir une variante par canal : notification dans l'app, push, SMS (court), e-mail (objet + corps).
- **ICU MessageFormat complet** — Syntaxe standard {count, plural, ...} utilisable aussi côté frontend. Plus expressif mais il faut migrer tous les gabarits existants et ajouter une bibliothèque Java et JS.
- **{{param}} simple, un texte par canal** — Pas de pluriel ni de formats : le minimum, sans changement de syntaxe.

**Réponse de l'utilisateur :** {{param}} + pluriel et formats (Recommandé)

### D-030 · Administration · 2026-11-11 04:59 UTC

**Question :** Comment gérer les gabarits au quotidien (création, correction, nouvelle langue) ?

Options proposées :

- **API admin + seed versionné (Recommandé)** — Endpoints réservés à TNT_ADMIN pour lister, créer, modifier, publier et voir les clés manquantes par langue, avec historique des versions et invalidation du cache. Un seed Liquibase fournit les textes fr/en initiaux. Pas d'écran dans le frontend (vous avez exclu /admin).
- **Seed uniquement** — Les textes sont en base mais ne se modifient que par migration Liquibase ou SQL manuel.
- **API admin + écran d'édition** — En plus de l'API, un écran dans le frontend pour éditer les textes. Contredit l'exclusion de /admin.

**Réponse de l'utilisateur :** API admin + seed versionné (Recommandé)

### D-031 · Langue défaut · 2026-11-11 04:59 UTC

**Question :** Quelle langue pour un utilisateur qui n'a encore rien choisi ?

Options proposées :

- **Langue du navigateur, puis fr (Recommandé)** — Avant connexion : cookie, puis Accept-Language, puis fr. À la première connexion, la langue détectée est enregistrée dans la préférence du compte. Les notifications suivent toujours la langue du destinataire, jamais celle de la personne qui a déclenché l'action.
- **Toujours fr par défaut** — Tout compte sans préférence reçoit le français, même si son navigateur est en anglais.
- **Défaut par tenant** — Chaque tenant définit sa langue par défaut, appliquée aux comptes sans préférence.

**Réponse de l'utilisateur :** Langue du navigateur, puis fr (Recommandé)

### D-032 · Périmètre Core · 2026-11-11 05:02 UTC

**Question :** Au-delà des notifications, quels autres contenus produits par le Core doivent suivre la langue de l'utilisateur ? (plusieurs choix possibles)

Options proposées (choix multiple) :

- **Messages d'erreur de l'API** — Erreurs métier et validations renvoyées par le Core avec un code stable plus un message traduit selon Accept-Language ou la préférence du compte. Le frontend affiche le message tel quel au lieu de maintenir sa propre traduction.
- **Documents PDF** — Factures, reçus, bordereaux de livraison, preuves générés par media-core : libellés, dates et montants dans la langue du destinataire.
- **Messages système (chat, litiges, incidents)** — Lignes automatiques des fils de discussion, de la chronologie d'un litige ou d'un incident, stockées avec une clé et rendues à la lecture dans la langue du lecteur.
- **Libellés de référence** — Statuts de livraison, types de colis, motifs d'échec et autres énumérations exposées avec leur libellé traduit, pour que chaque plateforme n'ait pas à les retraduire.

**Réponse de l'utilisateur :** Messages d'erreur de l'API, Documents PDF, "Messages système (chat, litiges, incidents)", Libellés de référence

### D-033 · Branche · 2026-11-11 05:02 UTC

**Question :** Sur quelle branche faut-il travailler ?

Options proposées :

- **Nouvelle branche dédiée (Recommandé)** — feat/i18n-multilingual créée depuis la branche actuelle, dans le Core et dans le frontend. Votre fusion unique des six lots reste propre, et ce chantier se relit et se fusionne séparément.
- **Rester sur feat/logistics-integration-audit** — Tout dans la même branche, pour une seule fusion finale vers main.

**Réponse de l'utilisateur :** Nouvelle branche dédier basé sur main. La branche feat/logistics-integration-audit vient d'être merger sur la main.

### D-034 · Tests rouges · 2026-11-11 05:02 UTC

**Question :** Deux tests de tnt-bootstrap restent rouges depuis tout à l'heure. Que fait-on avant de passer à l'i18n ?

Options proposées :

- **Swagger @Disabled + regel ArchUnit (Recommandé)** — Je désactive SpringdocProdExposureTest avec la raison et la décision du 2026-07-23, et je regle la base ArchUnit pour cette seule règle, dans un commit séparé. La configuration de prod n'est pas modifiée.
- **Laisser rouges pour l'instant** — Je ne touche à rien, vous décidez plus tard ; le chantier i18n est validé sur ses propres tests.
- **Couper Swagger en prod** — Je modifie application.yml pour désactiver Swagger UI et /v3/api-docs en prod, et je regle ArchUnit. Décision de sécurité qui contredit celle du 2026-07-23.

**Réponse de l'utilisateur :** Laisser rouges pour l'instant

### D-035 · Base frontend · 2026-11-11 05:08 UTC

**Question :** Le Core est bien fusionné dans main (PR #18), mais pas le frontend : feat/logistics-integration-audit n'est ni dans origin/main ni dans votre main locale (qui a 3 commits non poussés : refresh de session, portefeuille, paiement). Sur quoi baser la branche i18n du frontend ?

Options proposées :

- **Depuis feat/logistics-integration-audit (Recommandé)** — La branche i18n contient messagerie, médiateur, suivi live, file hors ligne et preuves, et je traduis ces écrans aussi. Elle reste empilée sur la branche des lots : à fusionner après elle. Core : branche i18n créée depuis main à jour.
- **Depuis main (local)** — Les écrans de messagerie, médiateur, suivi live et hors ligne n'existent pas sur cette base : ils ne seraient pas traduits et il faudrait les traduire après la fusion des lots, avec des conflits probables.
- **Fusionner d'abord les lots dans main (local)** — Je fusionne feat/logistics-integration-audit dans votre main locale (sans push, c'est vous qui poussez), puis je pars de main. Opération locale uniquement.

**Réponse de l'utilisateur :** Fusionner d'abord les lots dans main (local)

### D-036 · Déclencheur · 2026-11-11 07:08 UTC

**Question :** À quel moment le Core doit-il s'assurer que le wallet Kernel de l'utilisateur existe ? Il faut un moment où le jeton de l'utilisateur est disponible, car le Kernel refuse de créer le wallet d'un tiers.

Options proposées (choix multiple) :

- **À la connexion (Recommandé)** — Après un login réussi via la passerelle /api/v1/auth/* (login, MFA, select-context, refresh), le Core vérifie le wallet en tâche de fond avec le jeton reçu. Un échec n'empêche jamais la connexion. Ne couvre pas un login fait directement auprès du Kernel.
- **Filet de sécurité à l'usage (Recommandé)** — Les opérations wallet (recharge, code de réception, transfert, retrait) créent le wallet à la volée s'il manque, au lieu d'échouer. Couvre tous les chemins de connexion.
- **À la création du profil** — Dès la création d'un profil client ou freelancer dans le Core, avec le jeton de la requête. Ne couvre pas les comptes dont le profil existe déjà.

**Réponse de l'utilisateur :** À la connexion (Recommandé), À la création du profil

### D-037 · Périmètre · 2026-11-11 07:08 UTC

**Question :** Pour quels comptes créer un wallet ?

Options proposées :

- **Tout compte connecté (Recommandé)** — Simple et sans surprise : dès qu'un utilisateur se connecte, il a un wallet personnel, quel que soit son profil.
- **Seulement avec un profil TiiBnTick** — Le Core vérifie d'abord l'existence d'un profil client ou freelancer (GET /clients/me, /freelancers/me) avant de créer le wallet. Plus restrictif, mais un appel de plus à chaque connexion.

**Réponse de l'utilisateur :** Tout compte connecté (Recommandé)

### D-038 · Test réel · 2026-11-11 07:08 UTC

**Question :** Voulez-vous que je vérifie le résultat contre le vrai Kernel de production, sur votre compte ?

Options proposées :

- **Oui, sur mon compte** — Je me connecte comme avant (code MFA à me donner) et j'appelle GET puis POST wallets/mine. Cela crée peut-être un vrai wallet, vide, rattaché à braunnk@yowyob.com, dans la production.
- **Non, tests locaux uniquement** — Tests avec un faux Kernel (WireMock) et un contexte Spring réel, sans rien écrire en production. La forme réelle de la réponse du Kernel restera non vérifiée.

**Réponse de l'utilisateur :** Oui, sur mon compte

### D-039 · OTP à retirer · 2026-11-24 05:11 UTC

**Question :** Les codes de connexion/MFA/OTP de compte passent déjà par le Kernel (le Core ne fait que relayer `/api/auth/otp`). Le seul OTP fabriqué par le Core est le code de remise de colis (pickup/livraison) du module Go-Freelancer : généré, haché BCrypt, vérifié à la prise en charge, à la livraison et au dépôt relais, et envoyé par push/e-mail. Que dois-je retirer ?

Options proposées :

- **Seulement l'envoi des codes (Recommended)** — Je garde la génération, la vérification et la lecture du code dans l'application (par le propriétaire du besoin), mais le Core n'envoie plus le code par push/e-mail. Je supprime aussi la clé inutilisée `account.otp.sent`. Les parcours pickup/livraison/relais continuent de fonctionner.
- **Tout le mécanisme de remise** — Je supprime génération, hachage, vérification, endpoints, synchronisation hors-ligne et tests associés. Pickup, livraison et dépôt relais ne seront plus protégés par un code : travail important, à valider côté mobile/BFF (changement d'API).
- **Seulement la clé morte** — Je supprime uniquement `account.otp.sent` et le code mort ; le Core continue d'envoyer les codes de remise comme aujourd'hui.

**Réponse de l'utilisateur :** Seulement l'envoi des codes (Recommended)

### D-040 · Ampleur · 2026-11-24 05:20 UTC

**Question :** Quelle ampleur pour le dédoublonnage du module Go-Freelancer avec les couches inférieures ?

Options proposées :

- **Enums + statut de livraison (Recommended)** — Remplacer les copies (DeliveryStatus, ResponseStatus, VehicleClass, LoyaltyStatus...) par celles des modules inférieurs, et supprimer la colonne deliveries.status recopiée : le statut se lit toujours depuis delivery-core. Peu de migrations, risque maîtrisé, livrable en une passe.
- **Enums + statut + entités** — En plus : fusionner announcements, véhicules, évaluations, paiements, notifications, adresses, profils livreur/client et relais avec ceux des couches inférieures, avec migrations de données. Gros chantier, plusieurs lots, risque élevé pour le mobile/BFF.
- **Audit complet d'abord** — Je ne change rien dans le code : je produis un document tableau par tableau (doublon, propriétaire du concept, action proposée, risque) et vous choisissez ensuite les lots.

**Réponse de l'utilisateur :** Enums + statut + entités

### D-041 · Colis · 2026-11-24 05:20 UTC

**Question :** Le colis : le cœur a l'agrégat Parcel (tnt_parcels, cycle de vie propre) alors que Go-Freelancer ne manipule qu'un `packetId` et des preuves (PacketProof). Que voulez-vous ?

Options proposées :

- **Utiliser Parcel du cœur** — Chaque besoin/annonce Go crée un Parcel dans delivery-core ; Go-Freelancer ne garde que ses spécificités (codes de remise, relais choisi). Les états du colis viennent du cœur.
- **Garder packetId** — Go-Freelancer continue de référencer un identifiant de colis sans Parcel du cœur ; seuls les statuts de livraison sont dédupliqués.
- **Je ne sais pas** — Dites-moi qui définit le colis dans le produit (Go-Freelancer, Agency ou le cœur) et je propose un schéma.

**Réponse de l'utilisateur :** Utiliser Parcel du cœur

### D-042 · Compatibilité · 2026-11-24 05:20 UTC

**Question :** Compatibilité : le mobile et le BFF consomment aujourd'hui les DTO et les valeurs de statut de Go-Freelancer. Puis-je les modifier ?

Options proposées :

- **API stable (Recommended)** — Les routes, champs et valeurs JSON restent identiques ; je ne change que l'interne (types, tables). Aucune coordination mobile/BFF nécessaire.
- **Changements autorisés** — Je peux renommer/supprimer champs et valeurs de statut, et je documente chaque rupture pour le mobile/BFF.
- **Pas de données à conserver** — Comme pour le wallet : aucune donnée réelle à préserver, je peux supprimer ou recréer des tables par migration sans reprise.

**Réponse de l'utilisateur :** API stable (Recommended)

### D-043 · Conflits · 2026-11-24 05:20 UTC

**Question :** Coordination : kouam-kamdem a poussé 19 commits dans Go-Freelancer en deux semaines (codes de remise, véhicules, relais, invitations). Comment gérer le risque de conflits ?

Options proposées :

- **Sur cette branche, maintenant** — Je fais le travail sur feat/single-system-tenant, en récupérant main régulièrement. Risque de conflits si l'autre développeur continue à modifier les mêmes fichiers.
- **Lots courts, fusion rapide** — Lots petits que vous poussez et faites fusionner un par un, pour que l'autre développeur se rebase vite.
- **Attendre son retour** — Je termine d'abord les notifications et je prépare seulement le document d'audit ; le code attend que son travail soit fusionné.

**Réponse de l'utilisateur :** Sur cette branche, maintenant

### D-044 · Contrat gelé · 2026-11-24 05:35 UTC

**Question :** « Conservez les endpoints existants en surface » : qu'est-ce qui fait partie du contrat à ne jamais changer pour le mobile et le BFF ?

Options proposées (choix multiple) :

- **URL + méthode HTTP** — Chemins, verbes, paramètres de chemin et de requête.
- **Corps JSON** — Noms, types et valeurs des champs de requête et de réponse (dont les valeurs de statut comme PICKED_UP).
- **Codes et messages d'erreur** — Codes HTTP (403, 404, 409...) et format du corps d'erreur.
- **Droits et flux temps réel** — Permissions exigées par endpoint, flux SSE/WebSocket, contrats de synchronisation hors-ligne et événements Kafka consommés par d'autres services.

**Réponse de l'utilisateur :** "En fait gardent ce qui est le plus pertinent et modifies ce qui le plus logique et nécessaires afin d'avoir une éfficacité maximale à la fin tu me feras un rapport complet de ces modifications en fichier markdown pour les autres développeurs(Notement celui de Mobile+BFF, agency et autres) afin qu'ils se conforment."

### D-045 · Arrière-plan · 2026-11-24 05:35 UTC

**Question :** « Déléguez le traitement en arrière-plan aux modules métier des couches inférieures » : que signifie « en arrière-plan » ?

Options proposées :

- **Derrière le contrôleur (Recommended)** — Le contrôleur Go-Freelancer garde sa route et son DTO, puis appelle directement, dans la même requête, les cas d'usage des modules inférieurs (delivery-core, actor-core...). C'est de la délégation synchrone, pas de tâche asynchrone.
- **Asynchrone par événements** — Le contrôleur répond puis un événement Kafka déclenche le traitement dans les modules inférieurs. Cela change le timing des réponses et l'ordre de lecture après écriture.
- **Les deux selon le cas** — Synchrone pour ce que le client attend dans la réponse, asynchrone pour les effets secondaires. Vous me dites lesquels.

**Réponse de l'utilisateur :** Derrière le contrôleur (Recommended)

### D-046 · Identifiants · 2026-11-24 05:35 UTC

**Question :** Identifiants : l'API expose aujourd'hui des identifiants Go-Freelancer (id de livraison, id d'annonce, id de livreur...). Si l'entité sous-jacente devient celle du cœur, que doit voir le client ?

Options proposées :

- **Mêmes ids qu'aujourd'hui (Recommended)** — Les ids déjà connus du mobile/BFF ne changent jamais ; si le cœur a un id différent, je garde une table de correspondance interne.
- **Ids du cœur partout** — L'API expose les ids du cœur dès qu'ils existent ; les anciens ids restent acceptés en entrée pendant une période de transition.
- **Je ne sais pas** — Dites-moi si le mobile ou le BFF mémorise ces ids (cache local, liens, notifications déjà envoyées) et je tranche.

**Réponse de l'utilisateur :** Pour ce cas, on utilise les ids du coeur partout et on force tous les autres développeurs à faire pareil et c'est non négociable.

### D-047 · Manque en bas · 2026-11-24 05:35 UTC

**Question :** Quand un module inférieur ne couvre pas un besoin de Go-Freelancer (ex. urgence NORMAL alors que le cœur a SAME_DAY/SCHEDULED, ou les codes de remise, les invitations, la grille de prix par véhicule), que dois-je faire ?

Options proposées :

- **Garder dans Go-Freelancer (Recommended)** — Go-Freelancer conserve seulement ce que les couches inférieures ne font pas, comme vous l'avez demandé. Je ne touche pas aux modules inférieurs.
- **Ajouter au module inférieur** — J'étends delivery-core / actor-core / resource-core pour que ce soit réutilisable par Agency et Link ; plus propre mais je modifie du code d'autres équipes.
- **Au cas par cas** — Je vous liste chaque manque avec les deux options et vous choisissez avant que j'écrive du code.

**Réponse de l'utilisateur :** Si nécessaire modifier les modules des couches inférieures par ajout pour qu'il soit le plus global possible et accessibles à tous les modules de corebackend.

### D-048 · Seuil rupture · 2026-11-24 05:46 UTC

**Question :** Rupture de contrat : « modifie ce qui est le plus logique et nécessaire ». Qui décide, au cas par cas, qu'une rupture est justifiée ?

Options proposées :

- **Je décide, je documente (Recommandé)** — Je casse un contrat seulement si garder l'ancien oblige à garder un doublon ou une logique parallèle. Chaque rupture est listée (avant/après, exemple JSON, impact mobile/BFF) dans le rapport. Pas de validation avant.
- **Vous validez chaque rupture** — Je vous soumets la liste des ruptures de chaque lot avant de les appliquer. Plus lent, aucune surprise.
- **Ruptures par lot** — Je vous demande une validation une fois par lot (enums, livraison, annonces...), pas par endpoint.

**Réponse de l'utilisateur :** Je décide, je documente (Recommandé)

### D-049 · Données · 2026-11-24 05:46 UTC

**Question :** Données existantes : passer aux identifiants du cœur change les clés des lignes déjà présentes dans les tables Go-Freelancer. Que fait-on des données actuelles (livraisons, annonces, profils, paiements) ?

Options proposées :

- **Les migrer vers les ids du cœur** — Migrations Liquibase qui recopient/relient les lignes existantes aux entités du cœur et réécrivent les références. Plus sûr, plus long.
- **Aucune donnée réelle à garder** — Comme pour le wallet : les tables dupliquées sont supprimées ou recréées, sans reprise. Vous confirmez qu'il n'y a pas de données de production Go-Freelancer à préserver.
- **Je ne sais pas** — Dites-moi si la production tourne déjà avec de vraies livraisons Go-Freelancer ; sinon je pars sur une migration prudente par défaut.

**Réponse de l'utilisateur :** Les migrer vers les ids du cœur

### D-050 · Rapport · 2026-11-24 05:46 UTC

**Question :** Le rapport pour les autres développeurs : quel format voulez-vous ?

Options proposées :

- **Un fichier par public (Recommandé)** — Un markdown pour Mobile+BFF (routes, JSON, ids, erreurs), un pour Agency et autres modules coreBackend (ajouts dans les modules inférieurs à réutiliser), plus un journal technique global. Dans docs/.
- **Un seul fichier** — Un seul markdown avec une section par public et un tableau avant/après.
- **Mis à jour à chaque lot** — Je tiens le rapport à jour dans chaque commit pour que chacun puisse suivre sans attendre la fin.

**Réponse de l'utilisateur :** Un fichier par public (Recommandé)

### D-051 · Propre à Go · 2026-11-24 05:46 UTC

**Question :** Ces concepts n'ont pas d'équivalent dans les couches inférieures et me semblent propres à Go-Freelancer : messages de négociation, invitations à un besoin, codes de remise, favoris (bookmarks), profils visuels et photos de relais, horaires d'ouverture. Qu'en faisons-nous ?

Options proposées :

- **Les laisser dans Go-Freelancer (Recommandé)** — Ils restent dans le module, mais rattachés aux ids du cœur (livraison, colis, annonce, profil).
- **Les remonter dans le cœur** — Je les rends génériques dans delivery-core / actor-core / geo-core pour qu'Agency et Link les réutilisent.
- **Me proposer d'abord** — Pour chacun je vous dis qui d'autre en aurait besoin et vous tranchez.

**Réponse de l'utilisateur :** Les laisser dans Go-Freelancer (Recommandé)

### D-052 · Fidélité · 2026-11-24 06:12 UTC

**Question :** Fidélité (H3) : le cœur (tp-core) fait gagner 10 points par livraison et passe SILVER à 500 points. Go-Freelancer utilise 10/25/50 commandes. Lesquels garder ?

Options proposées :

- **Seuils du cœur** — Le cœur fait foi, comme vous l'avez demandé. Des clients existants peuvent rétrograder ; leurs points sont recalculés à 10 par commande lors de la migration.
- **Garder 10/25/50** — J'ajoute au cœur une politique de seuils paramétrable, générique, et Go la règle sur 10/25/50 commandes. Aucun client ne change de niveau.

**Réponse de l'utilisateur :** Seuils du cœur

### D-053 · Identité · 2026-11-24 06:12 UTC

**Question :** Identité du client (H1) : le client Go sera identifié côté cœur par son `actorId` (celui du JWT), avec son profil dans actor-core. Est-ce bien ce que vous voulez ?

Options proposées :

- **Oui, actorId partout** — Profil de référence : `client_profiles` d'actor-core. La fidélité et les notes tp-core utilisent le même actorId.
- **Non, autre identifiant** — Vous précisez quel identifiant du Kernel doit servir de clé client ; je mets L3 en pause pour cette partie.

**Réponse de l'utilisateur :** Oui, actorId partout

### D-054 · Mot de passe · 2026-11-24 06:12 UTC

**Question :** Authentification locale (H8) : Go-Freelancer garde encore des restes de mot de passe local (`password_hash`, `/api/auth/setup-password`, `/request-password-reset`) alors que l'auth passe par le Kernel. Que faire ?

Options proposées :

- **Supprimer en clôture** — Routes et colonnes retirées dans le changeset de clôture, après vérification qu'aucun client ne les appelle. Rupture d'API documentée pour Mobile/BFF.
- **Les garder** — On ne touche pas à ces routes ; elles restent dans Go jusqu'à nouvel ordre.

**Réponse de l'utilisateur :** Supprimer en clôture

### D-055 · Disque plein · 2026-11-25 20:56 UTC

**Question :** Le disque est plein à 100 % (400 Mo libres) et les agents L1, L2 et L3 en ont besoin pour compiler et lancer leurs tests. Docker occupe de la place récupérable : 18 Go de cache de build, 3,7 Go de volumes inutilisés, 6,5 Go d'images inutilisées. Que puis-je supprimer ?

Options proposées :

- **Cache de build seulement** — `docker builder prune -f` : libère environ 18 Go. Aucune donnée, image ni volume n'est touché ; les prochains `docker build` seront juste plus lents la première fois.
- **Cache + volumes orphelins** — En plus, `docker volume prune -f` : environ 3,7 Go. Supprime les volumes qu'aucun conteneur n'utilise (bases de tests jetables, mais peut-être aussi d'anciennes données locales que vous voudriez garder).
- **Je m'en occupe** — Je ne supprime rien ; vous libérez de la place vous-même.

**Réponse de l'utilisateur :** Cache de build seulement

### D-056 · Tarifs · 2026-11-25 20:56 UTC

**Question :** Tarification (lot L4, critère non atteint) : certaines anciennes grilles de prix des livreurs n'ont pas encore de politique dans billing-pricing, donc Go-Freelancer lit encore sa table `delivery_person_pricing` en repli. Comment créer ces politiques ?

Options proposées :

- **Reprise au démarrage (Recommended)** — Une reprise Java exécutée une seule fois au démarrage, dans le tenant système, après un comptage des grilles concernées. Ensuite le repli est retiré et la table supprimée en clôture.
- **Changeset Liquibase** — Un paramètre Liquibase fournit le tenant système et un changeset SQL crée les politiques. Il faut que chaque environnement fournisse ce paramètre.
- **Au premier devis** — La politique est créée la première fois qu'un devis est demandé pour ce livreur. Pas de migration, mais la table ancienne reste lue tant que tous les livreurs n'ont pas été sollicités.

**Réponse de l'utilisateur :** Reprise au démarrage (Recommended)

### D-057 · Véhicules · 2026-11-25 23:57 UTC

**Question :** Véhicules des livreurs : resource-core exige pour un véhicule une organisation propriétaire et une capacité maximale (kg), que Go-Freelancer ne connaît pas pour un livreur indépendant, et il n'a pas de cas d'usage de mise à jour. Que fait-on ?

Options proposées :

- **Étendre resource-core (Recommended)** — Ajout générique : organisation propriétaire facultative (véhicule personnel), capacité par défaut selon le type, cas d'usage de mise à jour. Puis Go délègue ses véhicules et la table locale part en clôture.
- **Garder local pour l'instant** — `gofreelancer_vehicles` reste dans Go-Freelancer ; on y revient plus tard.

**Réponse de l'utilisateur :** Étendre resource-core pour traiter aussi ce cas et garder la capacité maximale(kg) obligatoire même pour un livreur indépendant qui déclare un véhicule. Mettre l'organisation propriétaire facultative(a revisé même car risque d'être retirer avec tout le concept de organisation de trois(3) freelancer indépendant avec un chef).

### D-058 · Tarifs types · 2026-11-25 23:57 UTC

**Question :** Tarif des nouveaux types de véhicules (SCOOTER, TRICYCLE, BUS) : aujourd'hui ils prennent par défaut le tarif moto. Que voulez-vous ?

Options proposées :

- **Tarif moto par défaut** — On garde le repli moto jusqu'à ce qu'un administrateur définisse une grille propre à ces types dans billing-pricing.
- **Je fournirai les tarifs** — Vous me donnerez les montants (base, par km, par kg) pour chaque type ; je les mettrai dans billing-pricing.

**Réponse de l'utilisateur :** Définir toi même des tarifs par type dans billing-pricing en faisant des recherches sur internet sur les prix de livraisons et coûts des trajets. Bien noter que le prix fixer automatiquement par Tiibntick n'est pas dínitif car un livreur peu redéfinir les prix lui-même à sa convenance donc prendre ceci en compte(et mentionner également tout ceci dans le rapport .md enregistré conformément à ta stratégie mise en place).

### D-059 · Agrément · 2026-11-25 23:57 UTC

**Question :** Auto-agrément : quand un livreur est projeté dans le cœur, il est aujourd'hui marqué APPROVED automatiquement. Maintenant que le cœur fait autorité sur l'agrément, faut-il garder cela ?

Options proposées :

- **Agrément par un admin** — Un nouveau livreur reste PENDING jusqu'à validation par un administrateur (comme les points relais). Les livreurs déjà actifs gardent leur statut.
- **Garder l'auto-agrément** — Tout livreur projeté est APPROVED d'office, comme aujourd'hui.

**Réponse de l'utilisateur :** Agrément par un admin

### D-060 · Wallet · 2026-11-26 00:18 UTC

**Question :** Wallet : depuis le passage au Kernel, rien dans le Core ne publie plus les événements commission calculée, paiement confirmé et répartition exécutée. Leurs consommateurs (comptabilité, facturation, market, versements) ne reçoivent donc plus rien. Que fait-on ?

Options proposées :

- **Le Core publie (Recommandé)** — Après chaque appel réussi au wallet du Kernel (règlement, paiement, répartition), le service de règlement du Core publie l'événement via l'outbox, au format attendu par les consommateurs. La comptabilité et la facturation refonctionnent, sans dépendre du Kernel.
- **Le Kernel publie** — On demande à l'équipe Kernel (Savio) d'émettre ces événements, par webhook ou Kafka, et le Core s'y abonne. Plus propre à terme, mais cela dépend d'une autre équipe et d'un délai inconnu.
- **Retirer les consommateurs** — On supprime les consommateurs devenus muets. La comptabilité et la facturation interrogent alors le wallet du Kernel à la demande. Moins de code, mais l'écriture comptable automatique des commissions disparaît.

**Réponse de l'utilisateur :** Le Core publie (Recommandé)

### D-061 · Outbox · 2026-11-26 00:18 UTC

**Question :** Outbox : si Kafka est en panne au moment de publier, l'événement passe en échec et n'est jamais renvoyé automatiquement (le relanceur est désactivé depuis septembre). Quelle garantie voulez-vous ?

Options proposées :

- **Relance automatique (Recommandé)** — On réactive un relanceur planifié (verrou ShedLock, une seule instance) qui renvoie les événements en échec ou en attente, avec un délai croissant et un nombre d'essais maximal. Au-delà, l'événement passe en « mort », avec une alerte et un rejeu manuel. Garantie « au moins une fois » : les consommateurs doivent ignorer les doublons (par l'id d'événement).
- **Outbox transactionnelle** — L'événement est écrit dans la même transaction que la donnée métier, puis seul le relanceur publie. C'est la garantie la plus forte, mais elle demande de revoir chaque producteur (transactions R2DBC) : c'est beaucoup plus de travail.
- **Garder le rejeu manuel** — Rien ne change ; un opérateur rejoue les événements en échec. Un événement peut rester perdu tant que personne ne regarde.

**Réponse de l'utilisateur :** Relance automatique (Recommandé)

### D-062 · Bascule · 2026-11-26 00:18 UTC

**Question :** Déploiement : chaque module a maintenant son propre groupe de consommateurs. Au premier démarrage, ces nouveaux groupes reliraient tout l'historique des topics : notifications envoyées une seconde fois, incidents rouverts, etc. Comment faire la bascule ?

Options proposées :

- **Copier les positions (Recommandé)** — Avant la bascule, un script recopie, partition par partition, la position de l'ancien groupe commun (`tiibntick-core`) vers chaque nouveau groupe. Les nouveaux groupes reprennent exactement là où l'ancien s'était arrêté : rien n'est perdu ni rejoué. Je fournis le script et la procédure (runbook).
- **Repartir de la fin** — Avant la bascule, on place les nouveaux groupes à la fin de chaque topic (`--reset-offsets --to-latest`). C'est simple, mais les événements publiés pendant l'arrêt sont perdus pour ces groupes.
- **Paramètre « latest »** — Les nouveaux groupes démarrent à la fin du topic (`auto-offset-reset=latest`), sans script. C'est le plus simple, mais c'est la même perte que l'option précédente, et le comportement change aussi pour tout futur groupe.

**Réponse de l'utilisateur :** Il faut que tu proposes encore autre chose ici car je ne veux pas une méthode qui demandera d'exécuter soi-même(le développeur responsable ou le devops) un script pour appliquer éfficacement les modifications. Donc ŕeanalises et proposes à nouveau.

### D-063 · Doublons · 2026-11-26 00:18 UTC

**Question :** Doublons : avec la relance automatique, un même événement peut être reçu deux fois. Faut-il rendre tous les consommateurs idempotents, c'est-à-dire qu'un événement déjà traité soit ignoré grâce à son id ?

Options proposées :

- **Oui, partout (Recommandé)** — Une table commune des événements traités, par groupe, et un filtre partagé appliqué à tous les consommateurs. Aucun double effet : notification, crédit de fidélité, écriture comptable, incident.
- **Seulement les sensibles** — Uniquement là où un doublon coûte : argent (wallet, comptabilité, fidélité) et notifications. Moins de travail ; les autres consommateurs restent « au moins une fois ».

**Réponse de l'utilisateur :** Oui, partout (Recommandé)

### D-064 · Bascule auto · 2026-11-26 00:24 UTC

**Question :** Bascule des groupes Kafka sans aucun script manuel : quelle méthode automatique voulez-vous ?

Options proposées :

- **L'appli recopie (Recommandé)** — Au démarrage, avant que les consommateurs écoutent, l'application elle-même (Kafka AdminClient) recopie la position de l'ancien groupe commun vers chaque nouveau groupe qui n'en a pas encore. Elle le fait une seule fois, sous verrou entre instances, puis lance les consommateurs. Rien n'est perdu ni rejoué, et personne n'exécute quoi que ce soit. La table anti-doublons sert de filet de sécurité.
- **Fin du topic + anti-doublons** — Les nouveaux groupes démarrent à la fin du topic ; zéro code de bascule. Inconvénient : les événements publiés pendant l'arrêt du déploiement sont perdus pour ces groupes.
- **Début + anti-doublons pré-rempli** — Les nouveaux groupes relisent tout l'historique, mais au démarrage l'application marque comme « déjà traités » tous les événements antérieurs à la position de l'ancien groupe. Aucune perte, mais relecture complète des topics (lent si l'historique est gros) et plus de code.

**Réponse de l'utilisateur :** Déjà par du principe que pour le moment il n'y a aucune données, notifications en production à conserver et adaptes encore mieux tes propositions.

### D-065 · Départ groupe · 2026-11-26 00:26 UTC

**Question :** Sans données de production à conserver, il n'y a rien à « basculer » : la question devient seulement où un groupe de consommateurs commence quand il démarre pour la première fois. Que choisissez-vous ?

Options proposées :

- **Début + anti-doublons (Recommandé)** — Aucun code de bascule, aucune action manuelle. Un groupe neuf lit depuis le début du topic (`earliest`), donc il ne perd jamais un événement publié avant son premier démarrage. La table anti-doublons (que vous avez validée) empêche tout double effet. Les environnements de dev/staging qui ont déjà un vieil historique sont simplement remis à zéro par leur propre déploiement (topics recréés), ce qui est documenté dans RUN_LOCAL.
- **Fin du topic** — Un groupe neuf commence à la fin (`latest`) : jamais de relecture d'historique, même en dev/staging. Mais un événement publié avant le tout premier démarrage d'un consommateur (par exemple pendant un déploiement) est perdu pour lui.

**Réponse de l'utilisateur :** Début + anti-doublons (Recommandé)

### D-066 · États colis · 2026-11-26 00:49 UTC

**Question :** Cycle du colis : les 10 états de trust sont repris tels quels (CREATED, ASSIGNED, PICKED_UP, IN_TRANSIT, AT_RELAY_HUB, OUT_FOR_DELIVERY, DELIVERED, DELIVERY_FAILED, UNDER_DISPUTE, RETURNED). Pour intégrer litiges et retours de façon homogène, quels états ajouter ?

Options proposées :

- **Retour complet + pertes (Recommandé)** — Ajout de RETURN_REQUESTED, RETURN_IN_TRANSIT (retour en cours vers l'expéditeur ou un relais), puis RETURNED (déjà présent, final), et de deux états finaux issus d'un litige : LOST (colis perdu) et DAMAGED (colis endommagé, indemnisé). UNDER_DISPUTE mémorise l'état d'avant et y revient si le litige est rejeté.
- **Retour minimal** — Ajout du seul RETURN_IN_TRANSIT. UNDER_DISPUTE revient à l'état précédent à la clôture du litige. Pas d'état perdu/endommagé : l'issue du litige reste dans le module litige.
- **Retour + attente relais** — Comme « complet », avec en plus AWAITING_PICKUP (colis au relais, en attente du destinataire) distinct de AT_RELAY_HUB (colis au relais en transit vers ailleurs).

**Réponse de l'utilisateur :** Retour complet + pertes (Recommandé)

### D-067 · Retour · 2026-11-26 00:49 UTC

**Question :** Un retour à l'expéditeur : est-ce la même livraison qui continue, ou une nouvelle livraison (mission inverse) pour le même colis ?

Options proposées :

- **Nouvelle livraison (Recommandé)** — Le retour crée une livraison « retour » liée au même colis (avec son livreur, son prix, ses codes de remise). Le colis garde tout l'historique à travers ses livraisons. Les états de la livraison restent simples.
- **Même livraison** — La livraison passe en états de retour (RETURNING, RETURNED). Plus simple à suivre pour un seul livreur, mais mélange aller et retour (prix, preuves, commission).

**Réponse de l'utilisateur :** Nouvelle livraison (Recommandé)

### D-068 · Déclencheurs · 2026-11-26 00:49 UTC

**Question :** Qu'est-ce qui déclenche un retour du colis ? (plusieurs choix possibles)

Options proposées (choix multiple) :

- **Échecs répétés** — Après un nombre maximal de tentatives de livraison échouées (configurable, par exemple 3).
- **Refus du destinataire** — Le destinataire refuse le colis à la remise.
- **Non retiré au relais** — Le colis reste au point relais au-delà de la durée de garde gratuite (storageDays) sans être retiré.
- **Décision de litige** — Un litige se conclut par un retour à l'expéditeur (ou l'expéditeur annule après enlèvement).

**Réponse de l'utilisateur :** Échecs répétés, Refus du destinataire, Non retiré au relais, Décision de litige

### D-069 · Propriétaire · 2026-11-26 00:49 UTC

**Question :** Qui porte et fait évoluer l'état du colis ?

Options proposées :

- **delivery-core (Recommandé)** — Le Parcel de delivery-core reçoit son cycle d'états. Il est mis à jour dans la même opération que la livraison (pas d'écart possible), et par les cas d'usage litige/retour. trust se contente d'ancrer ces états en blockchain et réutilise le même enum (fin du doublon).
- **Module colis dédié** — Nouveau module (ex. tnt-parcel-core) qui écoute les événements de livraison et de litige et fait évoluer le colis. Découplage plus fort, mais état éventuellement en retard sur la livraison (asynchrone).

**Réponse de l'utilisateur :** delivery-core (Recommandé)

### D-070 · États incident · 2026-11-26 00:52 UTC

**Question :** Incidents dans les états de la livraison (seul le livreur déclare) : quels états ajouter à la livraison ? Aujourd'hui il n'existe que PAUSED_BY_INCIDENT.

Options proposées :

- **4 états (Recommandé)** — INCIDENT_REPORTED (le livreur a déclaré ; la livraison continue si l'incident n'est pas bloquant), PAUSED_BY_INCIDENT (bloquant, en attente de traitement), INCIDENT_HANDOVER (le colis est remis à un livreur remplaçant), puis retour à l'état d'avant à la résolution ; ou FAILED si l'incident est jugé sans issue. Le détail du traitement (en examen, escaladé, résolu, clos) reste dans le cycle propre de l'incident, avec ses événements.
- **Tout dans la livraison** — La livraison porte aussi INCIDENT_UNDER_REVIEW, INCIDENT_ESCALATED, INCIDENT_RESOLVED. Tout est lisible sur la livraison, mais l'état de la livraison double celui de l'incident (risque d'écart).
- **État + champ incident** — La livraison garde PAUSED_BY_INCIDENT seulement, et porte un champ séparé incidentStatus (copie de l'état de l'incident en cours).

**Réponse de l'utilisateur :** 4 états (Recommandé)

### D-071 · Auto-incidents · 2026-11-26 00:52 UTC

**Question :** « Seul un livreur peut déclarer un incident » : aujourd'hui le système ouvre aussi des incidents automatiquement (rupture de SLA, anomalie GPS, zone dangereuse). Que fait-on de ces détections ?

Options proposées :

- **Alertes, pas incidents (Recommandé)** — Les détections automatiques deviennent des alertes (événements Kafka et notifications au livreur, à l'agence, au support), qui invitent le livreur à déclarer. Seul le livreur crée un incident.
- **Garder l'automatique** — Le livreur déclare manuellement, mais le système peut aussi ouvrir un incident tout seul en cas de détection grave.
- **Livreur ou support** — Le livreur déclare ; le support (administrateur) peut aussi en ouvrir un pour lui. Les détections deviennent des alertes.

**Réponse de l'utilisateur :** Alertes, pas incidents (Recommandé)

### D-072 · Échec · 2026-11-26 00:52 UTC

**Question :** Échec de livraison : aujourd'hui FAILED est à la fois « final » et « peut repartir » (contradiction). Que choisissez-vous ?

Options proposées :

- **Tentative puis échec (Recommandé)** — Nouvel état DELIVERY_ATTEMPT_FAILED (non final : nouvel essai possible, compteur de tentatives). Au-delà du maximum configurable, FAILED devient vraiment final et déclenche le retour du colis (nouvelle livraison retour).
- **FAILED final** — FAILED est final, sans nouvel essai : toute nouvelle tentative est une nouvelle livraison.
- **FAILED non final** — FAILED reste non final (nouvel essai possible) ; la fin est seulement l'annulation ou le retour.

**Réponse de l'utilisateur :** Tentative puis échec (Recommandé)

### D-073 · SLA · 2026-11-26 00:52 UTC

**Question :** TIMED_OUT et SLA_BREACHED existent mais rien ne les pose jamais. Que fait-on ?

Options proposées :

- **Événements, pas états (Recommandé)** — On les retire des états (la livraison est toujours en cours quand un délai est dépassé). Un surveillant planifié détecte les retards et publie des alertes (SLA dépassé, délai expiré) avec un indicateur « en retard » sur la livraison.
- **Vrais états** — On garde les deux états et un surveillant planifié y fait passer les livraisons en retard.

**Réponse de l'utilisateur :** Événements, pas états (Recommandé)

### D-074 · Topics · 2026-11-26 00:55 UTC

**Question :** Organisation des topics Kafka pour colis, livraison, incident, litige et annonce (« créer uniquement le nécessaire ») : quel découpage ?

Options proposées :

- **1 topic par agrégat (Recommandé)** — tnt.parcel.events, tnt.delivery.events, tnt.incident.events, tnt.dispute.events, tnt.announcement.events. Le type d'événement est dans l'enveloppe et en en-tête, la clé est l'id de l'agrégat (ordre garanti par colis ou livraison). Les consommateurs filtrent par type. Les ~25 topics actuels qui se chevauchent sont retirés.
- **1 topic par événement** — On garde le style actuel (mission.created, mission.completed, package.updated…) et on complète. Facile à filtrer pour un consommateur, mais beaucoup de topics et l'ordre entre événements d'un même colis n'est plus garanti.
- **Mixte** — Un topic par agrégat pour tous les changements d'état, plus quelques topics métier dédiés pour les événements très consommés (annonce publiée, livraison terminée).

**Réponse de l'utilisateur :** 1 topic par agrégat (Recommandé)

### D-075 · Contenu · 2026-11-26 00:55 UTC

**Question :** Contenu des messages pour les autres plateformes (« afficher et actualiser de leur côté ») : que contient chaque événement ?

Options proposées :

- **État complet (Recommandé)** — Chaque événement porte l'instantané complet de l'objet après le changement (ex. l'annonce entière, le colis entier), plus le type de changement et l'état précédent. Le consommateur actualise son affichage sans rappeler le Core.
- **Id + changement** — L'événement porte l'id, le type et les champs modifiés. Le consommateur rappelle l'API du Core pour le reste. Messages légers, mais plus d'appels.

**Réponse de l'utilisateur :** État complet (Recommandé)

### D-076 · Données perso · 2026-11-26 00:55 UTC

**Question :** Données personnelles dans Kafka (nom, téléphone, adresse du destinataire et de l'expéditeur) : que fait-on ?

Options proposées :

- **Masquées (Recommandé)** — Les événements publics ne portent pas de téléphone ni d'adresse exacte : ville/quartier, coordonnées arrondies, nom abrégé. Les détails restent accessibles par l'API avec les droits.
- **Complètes** — Les événements portent toutes les données. Simple pour les consommateurs, mais toute plateforme abonnée voit les coordonnées personnelles.

**Réponse de l'utilisateur :** Masquées (Recommandé)

### D-077 · Accès Kafka · 2026-11-26 00:55 UTC

**Question :** Les autres plateformes (BFF Mobile, Agency, Link, Market, Point Relais) se connectent-elles directement au Kafka du Core ?

Options proposées :

- **Oui, cluster partagé (Recommandé)** — Elles lisent les topics du Core sur le même cluster, chacune avec son groupe de consommateurs et des droits en lecture seule (ACL). Le guide HTML explique la connexion, les topics, les formats et les bonnes pratiques.
- **Non, passerelle** — Le Core expose les événements autrement (webhooks ou flux SSE/WebSocket) et Kafka reste interne. Plus de code côté Core.
- **Je ne sais pas encore** — Le guide documente les deux possibilités ; je pars sur le cluster partagé.

**Réponse de l'utilisateur :** Oui, cluster partagé (Recommandé)

### D-078 · Cycle incident · 2026-11-26 00:58 UTC

**Question :** Cycle de l'incident (incident-core) : il a déjà 19 états, dont DETECTED (ouvert par le système) et REPORTED (déclaré). Avec « seul le livreur déclare » et « détections = alertes », que fait-on ?

Options proposées :

- **Retirer DETECTED (Recommandé)** — Un incident naît toujours REPORTED (déclaré par le livreur). DETECTED disparaît, car les détections deviennent des alertes séparées. Les 18 autres états (prise en compte, tri, réaffectation, remise du colis, escalade, résolu, clos…) restent et publient chacun un événement.
- **Garder tous les états** — On garde les 19 états ; DETECTED reste mais n'est plus utilisé tant qu'aucune décision ne le réactive.

**Réponse de l'utilisateur :** Retirer DETECTED (Recommandé)

### D-079 · Types · 2026-11-26 00:58 UTC

**Question :** Types d'incident : il en existe environ 150, dont des types système (panne serveur, Kafka, blockchain…) qu'un livreur ne peut pas constater. Que peut déclarer le livreur ?

Options proposées :

- **Sous-ensemble livreur (Recommandé)** — Le livreur choisit parmi les types qu'il peut constater : lui-même, son véhicule, le client, le colis, la route ou la zone, le point relais, le réglementaire et l'humain. Les types SYSTEM_* et AUTOMATION_* ne servent qu'aux alertes et au support.
- **Tous les types** — Le livreur peut choisir n'importe quel type.

**Réponse de l'utilisateur :** Sous-ensemble livreur (Recommandé)

### D-080 · Litige · 2026-11-26 00:58 UTC

**Question :** Qui peut ouvrir un litige sur un colis ? (plusieurs choix possibles)

Options proposées (choix multiple) :

- **Expéditeur (client)** — Le client qui a demandé la livraison.
- **Destinataire** — La personne qui reçoit le colis (colis endommagé, manquant…).
- **Livreur** — Le livreur (paiement contesté, accusation injustifiée…).
- **Point relais / support** — L'opérateur du point relais, ou un administrateur ou agent de support pour le compte d'un client.

**Réponse de l'utilisateur :** Expéditeur (client), Destinataire, Livreur, Point relais / support

### D-081 · Issue litige · 2026-11-26 00:58 UTC

**Question :** Issue d'un litige et état du colis : je propose ce passage automatique. Le validez-vous ?

Options proposées :

- **Valider (Recommandé)** — Plainte rejetée ou accord amiable : le colis revient à son état d'avant le litige. Perte totale indemnisée : LOST. Dommage indemnisé : DAMAGED. Nouvelle livraison ordonnée : nouvelle livraison pour le même colis. Retour ordonné : RETURN_REQUESTED (j'ajoute RETURN_TO_SENDER aux issues possibles).
- **Toujours manuel** — L'issue du litige ne change jamais l'état du colis automatiquement : un administrateur le fait à la main.

**Réponse de l'utilisateur :** Valider (Recommandé)

### D-082 · Point Relais · 2026-11-26 01:01 UTC

**Question :** Annonces : vous citez Go&Freelancer et Point Relais. Dans le code, seul Go-Freelancer crée des annonces (via delivery-core) ; un point relais en est seulement la destination. Qu'entendez-vous par annonce « Point Relais » ?

Options proposées :

- **Relais crée aussi** — Un opérateur de point relais peut publier lui-même une annonce (par exemple pour faire partir un colis qu'il détient vers sa destination). Je l'ajoute, avec les mêmes événements que Go-Freelancer.
- **Annonces vers un relais** — Il s'agit des annonces Go-Freelancer dont la destination ou l'origine est un point relais : elles passent dans le même topic, avec l'information du relais dans le message.
- **Les deux** — Le relais peut créer des annonces, et toute annonce mentionne le relais concerné.

**Réponse de l'utilisateur :** Relais crée aussi

### D-083 · Évts annonce · 2026-11-26 01:01 UTC

**Question :** Quels événements d'annonce publier dans le topic des annonces ?

Options proposées :

- **Tout le cycle (Recommandé)** — Créée, publiée, modifiée, candidature reçue, candidature retirée ou rejetée, livreur sélectionné (attribuée), annulée, terminée. Chaque message porte l'état complet de l'annonce, avec les données personnelles masquées.
- **Changements d'état seulement** — Publiée, attribuée, annulée, terminée ; les candidatures restent internes.

**Réponse de l'utilisateur :** Tout le cycle (Recommandé)

### D-084 · Anciens topics · 2026-11-26 01:01 UTC

**Question :** Anciens topics (mission.created, mission.completed, package.updated…, environ 25) : aucune donnée de production à garder. On les retire ?

Options proposées :

- **Retirer d'un coup (Recommandé)** — Producteurs et consommateurs du Core basculent sur les 5 topics d'agrégat dans le même lot ; les anciens topics, leurs constantes et leurs NewTopic sont supprimés. Le guide indique aux équipes externes les nouveaux noms (correspondance ancien → nouveau).
- **Double publication temporaire** — Pendant une période, le Core publie sur les anciens et les nouveaux topics, le temps que les équipes externes migrent ; puis on retire.

**Réponse de l'utilisateur :** Retirer d'un coup (Recommandé)

### D-085 · Format · 2026-11-26 01:01 UTC

**Question :** Format et versions des messages : comment garantir que les consommateurs externes ne cassent pas quand un message évolue ?

Options proposées :

- **JSON + version (Recommandé)** — Enveloppe JSON commune (eventId, eventType, schemaVersion, occurredAt, aggregateId, tenantId, payload) ; on ajoute des champs sans jamais en retirer ni en renommer ; tout changement cassant crée un nouveau schemaVersion. Les schémas JSON de chaque événement sont dans le guide et vérifiés par des tests.
- **Schema Registry** — Avro ou JSON Schema avec un Schema Registry (Confluent ou Apicurio). Plus robuste, mais nouvelle infrastructure à déployer et à intégrer à chaque plateforme.

**Réponse de l'utilisateur :** JSON + version (Recommandé)

### D-086 · Coût retour · 2026-11-26 01:06 UTC

**Question :** Qui paie la livraison retour du colis vers l'expéditeur ?

Options proposées :

- **Selon la faute (Recommandé)** — Par défaut l'expéditeur paie (refus, absence, non-retrait au relais). Si l'incident ou le litige établit la faute du livreur ou de la plateforme, le retour est gratuit pour lui et imputé au responsable. Le prix suit la grille par type de véhicule.
- **Toujours l'expéditeur** — L'expéditeur paie toujours le retour, quelle que soit la cause.
- **Toujours gratuit** — La plateforme prend le retour à sa charge.

**Réponse de l'utilisateur :** Selon la faute (Recommandé)

### D-087 · Seuils · 2026-11-26 01:06 UTC

**Question :** Seuils de retour : je propose 3 tentatives de livraison au maximum, et un retour quand le colis dépasse la durée de garde du point relais (storageDays du relais, 7 jours par défaut), avec un rappel au destinataire la veille. Tout configurable. Validez-vous ?

Options proposées :

- **Valider (Recommandé)** — 3 tentatives, durée de garde du relais (7 jours par défaut), rappel J-1, configurables par tenant.
- **Autres valeurs** — Précisez vos valeurs dans le champ libre (« Other »).

**Réponse de l'utilisateur :** Je choisi l'option 1. Valider (Recommandé) 3 tentatives, durée de garde du relais (7 jours par défaut), rappel J-1 sauf que je ne suis pas d'accord avec le configurable par tenant mais plus tôt par l'administrateur(à ajouter dans le notes pour la plateforme d'dministration de TiiBntick) de TiiBnTick par contre pour les aspects agences (de TiiBntick agency) où on retrouve en réalité des organisations(avec ids unique) on doit plus tôt permettre un confirabilité par organisation et qui peu et à le droit sera pleinement mis en place par le responsable de agency en fonction du fonctionnement métier de sa plateforme.

### D-088 · Sans exception · 2026-11-26 01:06 UTC

**Question :** « Tout brancher sans exception, de bout en bout en condition réelle » : il y a aussi 33 topics codés en dur sans création, des consommateurs qui attendent des champs que personne n'envoie, et des topics sans producteur ou sans consommateur. Que fait-on de ces derniers ?

Options proposées :

- **Brancher ou supprimer (Recommandé)** — Chaque topic restant a au moins un producteur réel et est déclaré (NewTopic). Un topic sans producteur métier possible est supprimé avec ses consommateurs morts ; un topic sans consommateur interne est gardé seulement s'il sert aux plateformes externes (documenté dans le guide). Les champs manquants sont ajoutés aux producteurs. Preuve : un test sur un vrai broker par topic (production → consommation → effet observé) et une application démarrée en entier sur Kafka/PostGIS/Redis réels.
- **Tout brancher** — On ne supprime rien : chaque topic reçoit un producteur, même si l'événement est peu utile.

**Réponse de l'utilisateur :** Brancher ou supprimer (Recommandé)

### D-089 · Véhicules · 2026-11-26 01:06 UTC

**Question :** Retour de l'agent L3 (véhicules) : aucune table Go-Freelancer ne contient la capacité du véhicule, qui est désormais obligatoire. Les véhicules existants n'ont donc pas été recréés dans le cœur. Que fait-on ?

Options proposées :

- **Le livreur la saisit (Recommandé)** — Comme fait par l'agent : le véhicule est recréé dans le cœur au prochain enregistrement du livreur avec sa capacité ; l'application l'y invite (capacité vide en attendant). Pas de donnée inventée.
- **Capacité par défaut** — Migration avec une capacité par défaut selon le type (ex. moto 30 kg, voiture 300 kg, camion 3 000 kg), à corriger ensuite par le livreur.
- **Poids max accepté** — Reprendre freelancer_eligibility.max_weight_kg (poids maximal que le livreur accepte, pas la capacité du véhicule).

**Réponse de l'utilisateur :** Le livreur la saisit (Recommandé)

### D-090 · Annonce relais · 2026-11-26 01:14 UTC

**Question :** Annonce créée par un point relais : dans quel cas et au nom de qui ?

Options proposées :

- **Colis qu'il détient (Recommandé)** — Le relais publie une annonce seulement pour un colis déposé chez lui (suite de trajet, retour, colis non retiré). L'expéditeur reste le client d'origine ; le relais apparaît comme « publié par » et comme point de départ. Le prix suit la grille et la règle de paiement (faute ou expéditeur).
- **Ses propres envois** — Le relais agit comme un client ordinaire : il expédie ses propres colis, il est l'expéditeur et il paie.
- **Les deux** — Colis détenus (au nom du client d'origine) et envois propres (le relais est expéditeur).

**Réponse de l'utilisateur :** envois propres (le relais est expéditeur) et pour les colis qu'il détient uniquement pour les cas particulier(colis non retiré)

### D-091 · Comptabilité · 2026-11-26 01:14 UTC

**Question :** Relève de l'agent Kafka : la comptabilité enregistre la commission de la plateforme comme une charge de commission livreur, et impute tout paiement non-Orange Money au compte MTN MoMo. On corrige ?

Options proposées :

- **Corriger les deux (Recommandé)** — La commission plateforme (PLATFORM_FEE) va vers un compte de produit (706 prestations), et chaque moyen de paiement (Orange Money, MTN MoMo, espèces, carte, wallet) a son propre compte via une table de correspondance.
- **Plus tard** — Noter le défaut dans le rapport sans le corriger dans ce chantier.

**Réponse de l'utilisateur :** Cette question et ces propositions sont très ambigues et ne prennent pas encore l'infrastructure du kernel au sujet du paiement. Je veux que tu réanalyses cela afin de mieux poser la questions avec de meilleures propositions et recommandation.

### D-092 · Consommateurs · 2026-11-26 01:14 UTC

**Question :** Relève de l'agent Kafka : plusieurs consommateurs lancent leur traitement sans en attendre la fin. Si le traitement échoue après coup, le message est quand même considéré comme traité (perdu). On corrige dans ce chantier ?

Options proposées :

- **Oui, tous (Recommandé)** — Chaque consommateur attend la fin de son traitement (avec un délai maximal) ; un échec déclenche les nouvelles tentatives puis la file des messages morts. Indispensable pour « fonctionnel de bout en bout ». L'agent L3 a déjà trouvé un cas réel : 51 messages donnaient 1 seul crédit de fidélité.
- **Plus tard** — Laisser tel quel et le noter.

**Réponse de l'utilisateur :** Oui, tous (Recommandé)

### D-093 · Remplaçant · 2026-11-26 01:14 UTC

**Question :** Incident bloquant avec remise du colis à un remplaçant (INCIDENT_HANDOVER) : qui choisit le livreur remplaçant ?

Options proposées :

- **Matching auto + validation (Recommandé)** — Le système propose les meilleurs livreurs proches (même classement que le matching), le premier qui accepte est retenu ; le client est notifié. La remise se fait par double confirmation (codes), comme le prévoit déjà incident-core. Pour une livraison d'agence, c'est l'agence qui choisit parmi ses livreurs.
- **Re-publier l'annonce** — L'annonce repasse en publication pour recueillir de nouvelles candidatures ; le client choisit.
- **Support choisit** — Un agent de support ou administrateur désigne le remplaçant.

**Réponse de l'utilisateur :** Matching auto + validation (Recommandé)

### D-094 · Séquestre · 2026-11-26 01:33 UTC

**Question :** Paiement numérique d'une livraison : le Kernel offre maintenant un séquestre réparti (livreur, plateforme, relais/agence), gelable et remboursable. On l'adopte à la place du transfert direct client → livreur ?

Options proposées :

- **Séquestre Kernel (Recommandé)** — À la sélection du livreur, le client confirme une fois (code) et l'argent est mis en séquestre avec les parts : livreur, plateforme (5 %) et, s'il y en a un, relais ou agence. À la remise du colis (code OTP), le Core libère le séquestre. En cas d'incident escaladé ou de litige, il le gèle. Selon l'issue du litige, il rembourse ou tranche. La commission est enfin prélevée. La table locale de blocages et le transfert direct disparaissent.
- **Garder le transfert** — Le client paie le livreur directement, et la commission reste « due » sans être prélevée. On n'utilise pas le séquestre.
- **Les deux** — Séquestre pour les livraisons au-dessus d'un montant ou pour un nouveau client, transfert direct sinon.

**Réponse de l'utilisateur :** Séquestre Kernel (Recommandé)

### D-095 · Espèces · 2026-11-26 01:33 UTC

**Question :** Paiement en espèces : le Kernel suit l'encaissement par le livreur et le reversement des parts (plateforme, relais/agence) depuis son wallet. On l'utilise ?

Options proposées :

- **Encaissement Kernel (Recommandé)** — À la sélection, un encaissement attendu est créé avec ses parts. À la remise, le livreur déclare le montant encaissé. Il reverse ensuite les autres parts depuis son wallet, avec un code. Les montants non reversés sont visibles, et un livreur qui doit trop pourrait être bloqué (à décider par l'administrateur).
- **Suivi local** — On garde le suivi actuel : espèces enregistrées et commission « due » dans le Core, sans reversement contrôlé.

**Réponse de l'utilisateur :** Encaissement Kernel (Recommandé)

### D-096 · Comptabilité · 2026-11-26 01:33 UTC

**Question :** Que doit enregistrer la comptabilité ? L'argent circule uniquement dans les wallets du Kernel, et la plateforme ne touche que sa part.

Options proposées :

- **Livres par propriétaire (Recommandé)** — Chaque organisation tient ses propres livres : la plateforme, chaque agence et chaque relais. La plateforme n'enregistre que sa part : un produit de commission (706) au moment où le séquestre est libéré ou la part reversée, plus les indemnisations sorties du fonds. La part d'un relais ou d'une agence va dans ses propres livres, de même que ce qu'elle doit à la plateforme. Le compte de trésorerie est « wallet Kernel », un par organisation, et non MTN ou Orange : l'opérateur ne compte qu'à la recharge ou au retrait, que gère le Kernel. Le paiement d'un client à un livreur indépendant n'entre dans aucun livre TiiBnTick.
- **Tout dans la plateforme** — La plateforme enregistre la totalité de chaque paiement de livraison (encaissement et reversement au livreur), comme si l'argent passait par elle.

**Réponse de l'utilisateur :** Livres par propriétaire (Recommandé)

### D-097 · Où écrire · 2026-11-26 01:33 UTC

**Question :** Où sont écrites les écritures comptables ? Le Kernel a son propre module comptable (comptes, écritures, journaux, périodes, clôtures).

Options proposées :

- **Kernel (Recommandé)** — Le Core envoie les écritures à la comptabilité du Kernel, comme il le fait déjà pour le wallet et les notifications. Le Core ne tient plus de journal local et garde seulement les correspondances entre ses objets métier et les écritures. La vérification réelle demandera un code MFA de votre part.
- **Core local** — On garde le journal local du Core (plan comptable OHADA déjà présent), en le corrigeant.
- **Local puis Kernel** — Le journal local fait foi maintenant, puis il est synchronisé vers le Kernel par ses travaux de synchronisation.

**Réponse de l'utilisateur :** Kernel (Recommandé)

### D-098 · QO-1 · 2026-11-26 05:14 UTC

**Question :** Livraison — que signifie l'état INCIDENT_REPORTED (incident déclaré par le livreur) ?

Options proposées :

- **État de passage (Recommandé)** — La livraison est INCIDENT_REPORTED seulement le temps du tri de l'incident. Ensuite elle passe en PAUSED_BY_INCIDENT si l'incident bloque, ou revient à son état d'avant s'il ne bloque pas. Un incident non bloquant ne masque donc pas l'état réel (en route, au relais…).
- **État tant qu'ouvert** — La livraison reste INCIDENT_REPORTED tant que l'incident est ouvert, même s'il ne bloque pas. On ne voit alors plus si elle est en route ou au relais.

**Réponse de l'utilisateur :** État de passage (Recommandé)

### D-099 · QO-2 · 2026-11-26 05:14 UTC

**Question :** Qu'est-ce qu'un incident bloquant (qui met la livraison en pause) ?

Options proposées :

- **Règle + forçage livreur (Recommandé)** — Une règle selon le type et la gravité (par exemple accident, panne moteur ou colis volé : bloquant ; route ralentie : non bloquant). Le livreur peut aussi cocher « je ne peux pas continuer », ce qui rend l'incident bloquant.
- **Règle seule** — Seule la règle type/gravité décide.
- **Choix du livreur** — Le livreur seul indique si l'incident le bloque.
- **Tout bloque** — Tout incident met la livraison en pause (comportement actuel).

**Réponse de l'utilisateur :** Règle + forçage livreur (Recommandé)

### D-100 · QO-3 · 2026-11-26 05:14 UTC

**Question :** Une tentative de remise échouée (client absent, injoignable, refus…) est-elle un incident ?

Options proposées :

- **Non, tentative (Recommandé)** — C'est une opération de la livraison : le livreur enregistre la tentative échouée avec un motif (absent, injoignable, refus, mauvaise adresse). Elle est comptée pour le seuil de 3 tentatives, sans ouvrir de dossier d'incident. Un refus du destinataire déclenche directement le retour.
- **Oui, incident** — Chaque tentative échouée est déclarée comme un incident de type CLIENT_* et suit le cycle de traitement d'un incident.

**Réponse de l'utilisateur :** Non, tentative (Recommandé)

### D-101 · QO-12 · 2026-11-26 05:14 UTC

**Question :** Parmi les types « livreur », faut-il retirer de la déclaration ceux qui relèvent d'une détection ou par lesquels le livreur s'accuserait lui-même ? Sont concernés : usurpation GPS, fraude multi-comptes, collusion, livraison fantôme, tentative de vol du colis, déviation de trajet, arrêt prolongé suspect, changement de véhicule ou substitution non déclarés, non-réponses répétées, décès du livreur.

Options proposées :

- **Les exclure (Recommandé)** — Ces types ne sont pas déclarables par le livreur. Ils servent aux alertes automatiques et au support. Un livreur peut déclarer par exemple sa propre urgence médicale, son accident ou une perte de réseau.
- **Tout déclarable** — Tous les types commençant par DRIVER_ restent déclarables par le livreur.

**Réponse de l'utilisateur :** Les exclure (Recommandé)

### D-102 · QO-13 · 2026-11-26 05:19 UTC

**Question :** Incident — que faire de l'état TRANSFERRING_TO_HUB (colis transféré vers un relais pendant un incident) ?

Options proposées :

- **Garder (Recommandé)** — Il est déclenché quand aucun livreur remplaçant n'est trouvé et qu'un point relais est à portée : le livreur dépose le colis au relais, qui le met à l'abri en attendant un remplaçant.
- **Supprimer** — L'état est retiré : sans remplaçant, l'incident reste en attente ou part en escalade.

**Réponse de l'utilisateur :** Garder (Recommandé)

### D-103 · QO-14 · 2026-11-26 05:19 UTC

**Question :** Incident — que faire de l'état PENDING_CONFIRMATION (le gestionnaire propose une résolution, le livreur déclarant confirme ou conteste) ?

Options proposées :

- **Garder (Recommandé)** — Le livreur qui a déclaré confirme que le problème est réglé ou le conteste, avec un délai de confirmation automatique. C'est utile, car c'est lui qui est sur le terrain.
- **Supprimer** — Le gestionnaire résout directement, sans confirmation du livreur.

**Réponse de l'utilisateur :** Garder (Recommandé)

### D-104 · QO-15 · 2026-11-26 05:19 UTC

**Question :** Un incident escaladé en litige : que devient l'incident ?

Options proposées :

- **RESOLVED auto (Recommandé)** — À l'ouverture du litige, l'incident passe RESOLVED avec le motif « transféré en litige » et un lien vers le litige. Le litige prend la main ; on évite deux dossiers ouverts sur le même fait.
- **Reste ESCALATED** — L'incident reste ESCALATED jusqu'à ce qu'un gestionnaire le résolve à la main.
- **CLOSED** — L'incident est clos dès l'ouverture du litige.

**Réponse de l'utilisateur :** RESOLVED auto (Recommandé)

### D-105 · QO-25 · 2026-11-26 05:19 UTC

**Question :** Le support (administrateur) peut-il ouvrir un incident des types SYSTEM_* ou AUTOMATION_* (panne serveur, mauvais matching…) sur une livraison ?

Options proposées :

- **Non, alertes seulement (Recommandé)** — Ces types ne servent qu'aux alertes et aux tickets du support, jamais à un incident de livraison. C'est cohérent avec « seul le livreur déclare ».
- **Oui, le support peut** — Le support peut ouvrir un incident de ces types sur une livraison, par exception à la règle.

**Réponse de l'utilisateur :** Non, alertes seulement (Recommandé)

### D-106 · QO-4 · 2026-11-26 05:21 UTC

**Question :** Retour du colis — qui crée et attribue la livraison retour ?

Options proposées :

- **Annonce retour auto (Recommandé)** — Une annonce « retour » est créée automatiquement en brouillon pour celui qui paie : l'expéditeur, ou le point relais pour un colis non retiré. Elle est publiée et attribuée par le matching habituel, avec le séquestre habituel.
- **Livreur de l'aller** — Le retour est attribué d'office au livreur qui a fait l'aller.
- **Agence ou relais** — L'agence ou le point relais concerné attribue le retour à un livreur.

**Réponse de l'utilisateur :** Annonce retour auto (Recommandé)

### D-107 · QO-5 · 2026-11-26 05:21 UTC

**Question :** Une livraison échoue définitivement à cause d'un incident sans issue. Que devient le colis ?

Options proposées :

- **Automatique + choix (Recommandé)** — Par défaut : si le colis avait été enlevé, il passe RETURN_REQUESTED (retour à l'expéditeur) ; sinon il revient à CREATED, prêt pour une nouvelle annonce. Le gestionnaire qui résout l'incident peut choisir autrement : nouvelle livraison ou retour.
- **Toujours nouvelle livraison** — Le colis revient toujours à CREATED et une nouvelle livraison est créée.
- **Toujours le gestionnaire** — Pas de défaut : le gestionnaire choisit à chaque fois.

**Réponse de l'utilisateur :** Automatique + choix (Recommandé)

### D-108 · QO-6 · 2026-11-26 05:21 UTC

**Question :** Depuis quels états une livraison peut-elle être annulée ?

Options proposées :

- **Jusqu'à l'enlèvement + pause (Recommandé)** — CREATED, ASSIGNED, PICKED_UP et PAUSED_BY_INCIDENT. Une annulation après enlèvement déclenche le retour du colis. Une fois le colis en route ou au relais, on ne peut plus annuler : il faut passer par un échec ou un litige.
- **Avant enlèvement** — Seulement CREATED et ASSIGNED.
- **Tout état non final** — À tout moment avant la fin.

**Réponse de l'utilisateur :** Jusqu'à l'enlèvement + pause (Recommandé)

### D-109 · QO-7 · 2026-11-26 05:21 UTC

**Question :** Un litige est ouvert alors que la livraison est encore en cours. Que se passe-t-il ?

Options proposées :

- **Selon l'objet (Recommandé)** — Si le litige porte sur un retard, la livraison continue et seul l'argent est gelé. Pour tout autre objet (colis endommagé, fraude…), la livraison est gelée : plus aucune transition, sauf un échec. Le colis passe UNDER_DISPUTE dans les deux cas.
- **Toujours gelée** — La livraison est toujours gelée pendant un litige.
- **Jamais gelée** — La livraison continue toujours ; seul l'argent est gelé.

**Réponse de l'utilisateur :** Selon l'objet (Recommandé)

### D-110 · QO-8 · 2026-11-26 05:24 UTC

**Question :** La livraison retour elle-même échoue ou est annulée. Que devient le colis ?

Options proposées :

- **Nouveau retour + seuil (Recommandé)** — Le colis repasse RETURN_REQUESTED et un nouveau retour est organisé. Après 3 retours échoués (réglable par l'administrateur), il est stocké au relais ou par la plateforme et signalé au support, qui décide (destruction, don, déclaration de perte).
- **Toujours réessayer** — Le colis repasse RETURN_REQUESTED, sans limite.
- **Perdu après N** — Après N échecs de retour, le colis passe LOST.

**Réponse de l'utilisateur :** Nouveau retour + seuil (Recommandé)

### D-111 · QO-9 · 2026-11-26 05:24 UTC

**Question :** Que devient l'annonce quand sa livraison échoue définitivement ?

Options proposées :

- **COMPLETED + issue (Recommandé)** — L'annonce passe COMPLETED avec une issue FAILED : elle a bien été servie, un livreur a été choisi. La suite (retour ou nouvelle livraison) passe par une nouvelle annonce.
- **CANCELLED** — L'annonce passe CANCELLED.

**Réponse de l'utilisateur :** COMPLETED + issue (Recommandé)

### D-112 · QO-10 · 2026-11-26 05:24 UTC

**Question :** Destination = point relais : quand le séquestre est-il libéré (le livreur payé) ?

Options proposées :

- **Au dépôt au relais (Recommandé)** — Le livreur est payé quand il dépose le colis au relais, avec la confirmation du relais : son travail est fini. Si le destinataire ne vient pas retirer le colis, cela ne pénalise pas le livreur.
- **Au retrait** — Le livreur n'est payé que quand le destinataire retire le colis au relais.

**Réponse de l'utilisateur :** Au dépôt au relais (Recommandé)

### D-113 · QO-11 · 2026-11-26 05:24 UTC

**Question :** Un livreur est remplacé en cours de route après un incident. Qui reçoit la part livreur du séquestre ? (Le séquestre Kernel ne semble pas permettre de modifier les parts après coup ; un partage demanderait de vérifier ou de demander cette possibilité au Kernel.)

Options proposées :

- **Prorata distance (Recommandé)** — La part est partagée selon la distance parcourue par chacun. Si le Kernel ne permet pas de modifier les parts, on retient « tout au remplaçant » en attendant, avec une compensation au livreur d'origine depuis le fonds, et on fait une demande au Kernel.
- **Tout à l'origine** — Le livreur d'origine garde tout. Simple, et compatible avec le Kernel actuel, mais le remplaçant n'est pas payé par le séquestre.
- **Tout au remplaçant** — Celui qui termine la livraison reçoit tout.

**Réponse de l'utilisateur :** Prorata distance (Recommandé)

### D-114 · QO-19 · 2026-11-26 05:38 UTC

**Question :** Séquestre — part MERCHANT (point relais ou agence) : que contient-elle ?

Options proposées (choix multiple) :

- **Frais de garde du relais** — Quand le colis passe par un relais, ses frais de garde ou de dépôt sont une part MERCHANT du séquestre, payée en même temps que le livreur.
- **Part de l'agence** — Quand le livreur appartient à une agence, sa part va (en totalité ou en partie) à l'agence, qui paie ensuite son livreur.
- **Annonce d'un relais** — Quand le relais est expéditeur ou organisateur (ses envois propres, colis non retiré), sa marge éventuelle est une part MERCHANT.
- **Aucune pour l'instant** — Pas de part MERCHANT : seulement livreur et plateforme.

**Réponse de l'utilisateur :** Frais de garde du relais, Part de l'agence

### D-115 · QO-22 · 2026-11-26 05:38 UTC

**Question :** Le retour est à la charge du livreur (sa faute est établie). Comment le fait-on payer ?

Options proposées :

- **Dette du livreur (Recommandé)** — Le retour est payé tout de suite par le fonds d'indemnisation. Le montant devient une dette du livreur, recouvrée comme un impéyé d'espèces (reversement depuis son wallet, visible et bloquant au-delà d'un seuil fixé par l'administrateur).
- **Retenue sur sa part** — Le montant est retenu sur sa part du séquestre de cette livraison (seulement si elle n'est pas encore libérée, et si le Kernel permet de modifier les parts).
- **Fonds sans recouvrement** — Le fonds paie et l'on ne recouvre rien pour l'instant.

**Réponse de l'utilisateur :** Dette du livreur (Recommandé)

### D-116 · QO-23 · 2026-11-26 05:38 UTC

**Question :** Un litige ordonne un retour ou une nouvelle livraison. Que devient l'argent de l'aller, et qui paie la nouvelle course ?

Options proposées :

- **Rembourser + responsable (Recommandé)** — Le séquestre de l'aller est remboursé au client, sauf si l'aller a vraiment été effectué et que le livreur n'est pas en faute. La nouvelle course est payée par la partie responsable désignée par le litige.
- **L'aller paie** — L'argent de l'aller sert à payer la nouvelle course.

**Réponse de l'utilisateur :** Rembourser + responsable (Recommandé)

### D-117 · QO-24 · 2026-11-26 05:38 UTC

**Question :** La livraison échoue ou est annulée après l'enlèvement. Le livreur est-il payé ?

Options proposées :

- **Selon le responsable (Recommandé)** — Si la faute revient au client ou à l'expéditeur (absent, refus, annulation), le séquestre est libéré et le livreur est payé. Si elle revient au livreur ou à la plateforme, le client est remboursé. Le paiement partiel n'est pas possible avec le Kernel actuel.
- **Toujours remboursé** — Le client est toujours remboursé et le livreur n'est pas payé.
- **Paiement partiel** — Le livreur reçoit une partie (par exemple au prorata). Cela demande une évolution du Kernel, et en attendant on applique « selon le responsable ».

**Réponse de l'utilisateur :** Selon le responsable (Recommandé)

### D-118 · QO-21 · 2026-11-26 05:46 UTC

**Question :** Faut-il retirer le journal local de tnt-accounting-core, maintenant que les écritures vont dans la comptabilité du Kernel ?

Options proposées :

- **Lecture puis suppression (Recommandé)** — On arrête d'y écrire tout de suite. Les tables restent en lecture seule pendant une période de contrôle, puis un changeset Liquibase les supprime, ce qui demandera votre accord explicite. Il n'y a pas de donnée de production à garder.
- **Supprimer tout de suite** — Un changeset supprime les tables du journal local dans ce chantier.
- **Garder en lecture** — On les garde indéfiniment en lecture, pour l'historique.

**Réponse de l'utilisateur :** Lecture puis suppression (Recommandé)

### D-119 · QO-20 · 2026-11-26 05:46 UTC

**Question :** Numéros de comptes des livres (plan OHADA) : l'architecte propose 4671 (séquestre client), 4672 (dette ou créance livreur), 706 (commissions de la plateforme), 6788 (indemnisations) et 411 (clients). Que fait-on ?

Options proposées :

- **Valider ce plan (Recommandé)** — On part sur ces comptes, regroupés en constantes dans une seule table de correspondance, faciles à changer. Votre comptable pourra les ajuster plus tard sans toucher au code métier.
- **Je fournis le plan** — Vous donnez les numéros dans le champ libre (« Other »).

**Réponse de l'utilisateur :** Valider ce plan (Recommandé)

### D-120 · QO-26 · 2026-11-26 05:46 UTC

**Question :** Recouvrement d'une indemnité auprès du responsable (par exemple le fonds paie un client, alors que le livreur ou le relais était en faute) : quand le traiter ?

Options proposées :

- **Dette comme le retour (Recommandé)** — Par cohérence avec la règle du retour à la charge du livreur, l'indemnité devient une dette du responsable (livreur, relais ou agence). Elle est suivie et récupérée par reversement depuis son wallet, et la suspension est décidée par l'administrateur. Cela se fait dans ce chantier.
- **Plus tard** — Pas de recouvrement pour l'instant : le fonds absorbe, et c'est noté dans la spec d'administration.

**Réponse de l'utilisateur :** Dette comme le retour (Recommandé)

### D-121 · QO-28 · 2026-11-26 05:46 UTC

**Question :** Séquestre pour les livraisons d'agence créées directement, sans annonce (l'agence affecte elle-même ses livreurs) ?

Options proposées :

- **Pas de séquestre (Recommandé)** — La facturation de l'agence reste inchangée : l'agence encaisse et paie ses livreurs selon son propre fonctionnement. Le séquestre ne s'applique qu'aux livraisons issues d'une annonce.
- **Séquestre aussi** — Toute livraison, même créée par une agence, passe par le séquestre Kernel.
- **Au choix de l'agence** — Chaque organisation choisit dans ses réglages (réglable par le responsable de l'agence).

**Réponse de l'utilisateur :** Pas de séquestre (Recommandé)

### D-122 · QO-18 · 2026-11-26 05:52 UTC

**Question :** Alertes de retard : seuils et lieu de réglage. Proposé : enlèvement en retard = fin de la fenêtre + 15 min ; livraison en retard = fin de la fenêtre, à défaut un délai selon l'urgence ; ETA dépassée de 30 min ; signal GPS perdu depuis 10 min ; arrêt prolongé de 20 min.

Options proposées :

- **Ces valeurs, réglage admin (Recommandé)** — On retient ces valeurs par défaut, réglables dès maintenant par l'administrateur de TiiBnTick (comme les autres seuils), avec une surcharge possible par organisation Agency. Ajouté à la spec d'administration.
- **Ces valeurs, config** — Ces valeurs dans la configuration de l'application ; le réglage admin viendra plus tard.
- **Autres valeurs** — Précisez-les dans le champ libre.

**Réponse de l'utilisateur :** Ces valeurs, réglage admin (Recommandé)

### D-123 · QO-29 · 2026-11-26 05:52 UTC

**Question :** Un point relais indépendant (hors TiiBnTick Agency) peut-il changer sa propre durée de garde ?

Options proposées :

- **Oui, dans une limite (Recommandé)** — Le relais est une mini-organisation : il peut fixer sa durée de garde (son storageDays) entre un minimum et un maximum fixés par l'administrateur de TiiBnTick. Les autres seuils (tentatives, rappel) restent ceux de la plateforme.
- **Non** — Seule la valeur de la plateforme s'applique aux relais indépendants.
- **Oui, librement** — Le relais fixe librement sa durée de garde.

**Réponse de l'utilisateur :** Oui, dans une limite (Recommandé)

### D-124 · QO-30 · 2026-11-26 05:52 UTC

**Question :** Le client demande une durée de garde (requestedStorageDays) en créant l'annonce. Quel effet ?

Options proposées :

- **Minimum des deux (Recommandé)** — L'échéance est la plus courte entre la demande du client et la durée du relais : le client peut raccourcir, pas allonger gratuitement.
- **Garde payante plus longue** — Le client peut demander plus que la durée du relais en payant des frais de garde supplémentaires (part du relais dans le séquestre).
- **Affichée seulement** — Elle est affichée à titre indicatif ; seule la durée du relais compte.

**Réponse de l'utilisateur :** Minimum des deux (Recommandé)

### D-125 · QO-17 · 2026-11-26 05:52 UTC

**Question :** Coordonnées GPS publiées dans Kafka (données masquées) : quelle précision ?

Options proposées :

- **2 décimales ~1,1 km (Recommandé)** — Assez pour situer un quartier sur une carte, sans révéler l'adresse exacte. Le détail exact reste accessible par l'API, avec les droits.
- **3 décimales ~110 m** — Plus précis : rue ou pâté de maisons.
- **1 décimale ~11 km** — Seulement la zone de la ville.

**Réponse de l'utilisateur :** 2 décimales ~1,1 km (Recommandé)

### D-126 · QO-16 · 2026-11-26 05:58 UTC

**Question :** Nom de l'issue de litige « retour à l'expéditeur » : vos décisions disaient RETURN_TO_SENDER, mais l'architecte a trouvé que RETURN_TO_SENDER_ORDERED existe déjà dans le code, à côté de REDELIVERY_ORDERED. Lequel garder ?

Options proposées :

- **RETURN_TO_SENDER_ORDERED (Recommandé)** — On garde le nom existant, homogène avec REDELIVERY_ORDERED (« nouvelle livraison ordonnée ») : un seul nom pour la notion, rien à renommer.
- **RETURN_TO_SENDER** — On renomme en RETURN_TO_SENDER, comme dans la décision initiale.

**Réponse de l'utilisateur :** RETURN_TO_SENDER_ORDERED (Recommandé)

### D-127 · QO-27 · 2026-11-26 05:58 UTC

**Question :** Topics Kafka hors du périmètre colis/livraison qu'aucun consommateur du Core ne lit (une centaine, listés en annexe de la spec). Je ne peux pas savoir lesquels vos plateformes externes lisent, car je ne consulte pas tiibntick-platforms. Que fait-on ?

Options proposées :

- **Garder et documenter (Recommandé)** — On les garde tous, avec un producteur réel et une déclaration, et le guide HTML les présente comme « destinés aux plateformes externes ». Vous ou les équipes externes pourrez ensuite indiquer ceux qui ne servent à personne, pour les supprimer dans un lot ultérieur.
- **Je fournis la liste** — Vous indiquez dans le champ libre quelles plateformes lisent quels topics ; les autres sont supprimés.
- **Supprimer les non lus** — On supprime dès maintenant tout topic sans consommateur dans le Core ; les plateformes externes demanderont ce dont elles ont besoin.

**Réponse de l'utilisateur :** Garder et documenter (Recommandé)

### D-128 · Adresses · 2026-11-26 05:58 UTC

**Question :** Relève de l'intégration des lots : les adresses et dépôts relais migrés ont encore pour propriétaire l'ancien id de personne Go-Freelancer, et non l'acteur du cœur. Leurs propriétaires risquent de ne plus les voir. On corrige ?

Options proposées :

- **Convertir vers l'acteur (Recommandé)** — Un nouveau changeset Liquibase convertit l'id de personne Go en id d'acteur du cœur (correspondance déjà établie par le lot acteurs), sur les liens d'adresse et les entrées de dépôt relais, avec un test sur une vraie base. C'est conforme à « ids du cœur partout ».
- **Laisser** — Ne rien faire. Les adresses migrées restent rattachées à l'ancien id.

**Réponse de l'utilisateur :** Convertir vers l'acteur (Recommandé)

### D-129 · Relais révoqué · 2026-11-26 05:58 UTC

**Question :** Relève de l'intégration des lots : un livreur peut être révoqué (REVOKED, définitif), mais un point relais ne le peut pas encore dans le cœur. On aligne ?

Options proposées :

- **Ajouter REVOKED (Recommandé)** — Un point relais peut être révoqué définitivement par un administrateur, avec un motif et une notification. Ses colis en garde déclenchent un retour ou un transfert. C'est homogène avec les livreurs et ajouté à la spec d'administration.
- **Suspension seulement** — Un relais peut seulement être suspendu, jamais révoqué.

**Réponse de l'utilisateur :** Ajouter REVOKED (Recommandé)

### D-130 · Bornes garde · 2026-11-26 06:47 UTC

**Question :** Un point relais indépendant peut fixer sa propre durée de garde entre des bornes fixées par l'administrateur (QO-29). Quelles bornes par défaut au démarrage ?

Options proposées :

- **3 à 30 jours (Recommandé)** — Minimum 3 jours, pour laisser au destinataire le temps de venir. Maximum 30 jours, pour éviter que les relais stockent indéfiniment. Le défaut plateforme reste 7 jours, et l'administrateur peut changer ces bornes.
- **1 à 60 jours** — Les garde-fous techniques choisis par l'agent, très larges.
- **Pas de bornes** — Bornes vides tant que l'administrateur ne les a pas fixées : le relais garde alors la durée de la plateforme (7 jours).

**Réponse de l'utilisateur :** 3 à 30 jours (Recommandé)

### D-131 · Plages · 2026-11-26 06:47 UTC

**Question :** Plages de valeurs acceptées par l'API pour chaque seuil (garde-fous contre une erreur de saisie). L'agent a fixé : tentatives 1–10, retours échoués 1–10, garde 1–60 jours, alertes 0 ou 1–1 440 minutes, délais 1–720 heures. On les garde ?

Options proposées :

- **Garder (Recommandé)** — Ces plages évitent les valeurs absurdes (0 tentative, garde de 2 ans) tout en laissant de la marge. Elles sont documentées dans la spec d'administration.
- **Autres plages** — Précisez-les dans le champ libre.

**Réponse de l'utilisateur :** Garder (Recommandé)

### D-132 · Délais · 2026-11-26 06:47 UTC

**Question :** Délai de livraison quand l'annonce n'a pas de fenêtre horaire (sert à l'alerte « livraison en retard »). Repris de la configuration existante : 48 h en standard, 24 h en express, 8 h pour le jour même ; les livraisons programmées ont toujours une fenêtre. Tous réglables par l'administrateur.

Options proposées :

- **Garder (Recommandé)** — 48 h standard, 24 h express, 8 h jour même, réglables par l'administrateur et surchargeables par une agence.
- **Autres valeurs** — Précisez-les dans le champ libre.

**Réponse de l'utilisateur :** Garder (Recommandé)

### D-133 · Surcharge · 2026-11-26 06:47 UTC

**Question :** Surcharge des seuils par une agence : l'agent permet à toute organisation Agency de surcharger tous les seuils (alertes comprises). C'est le responsable de l'agence qui délègue la permission, et l'administrateur peut supprimer une surcharge. Faut-il en plus que l'administrateur autorise chaque agence ?

Options proposées :

- **Libre, sans autorisation (Recommandé)** — Toute agence peut surcharger selon la permission donnée par son responsable, conformément à votre décision. L'administrateur voit toutes les surcharges et peut en supprimer une.
- **Autorisation admin** — On ajoute un interrupteur par agence, que l'administrateur active, avant toute surcharge possible.
- **Seuils limités** — Une agence ne peut surcharger que certains seuils (par exemple la garde et les alertes, pas le nombre de tentatives).

**Réponse de l'utilisateur :** Libre, sans autorisation (Recommandé)

### D-134 · Orphelins · 2026-11-26 07:05 UTC

**Question :** Migration des propriétaires vers l'acteur du cœur (D-128) : certaines adresses migrées n'ont aucune correspondance sûre vers un acteur. Par exemple, les 8 adresses des données de test appartiennent à des personnes sans compte utilisateur. Que fait-on de ces lignes ?

Options proposées :

- **Outil admin de rattachement (Recommandé)** — Les lignes restent telles quelles et sont listées dans la plateforme d'administration. Un administrateur les rattache à la main au bon acteur, ou les supprime. Rien n'est deviné. C'est déjà noté dans la spec d'administration.
- **Rapprocher par e-mail** — Un changeset rapproche les personnes et les comptes par adresse e-mail identique. C'est automatique, mais il y a un risque d'erreur si deux personnes partagent un e-mail ou si l'e-mail a changé.
- **Supprimer** — Les liens sans correspondance sont supprimés (aucune donnée de production à garder).

**Réponse de l'utilisateur :** Supprimer

### D-135 · Révocation · 2026-11-26 07:05 UTC

**Question :** Révocation d'un point relais : depuis quels états peut-on révoquer ? Côté Go-Freelancer, l'agent a choisi APPROVED ou SUSPENDED, comme pour les livreurs. Côté cœur (hub), il l'a permis depuis n'importe quel état. Les deux doivent être homogènes.

Options proposées :

- **Approuvé ou suspendu (Recommandé)** — Partout (cœur et Go-Freelancer), seul un relais APPROVED ou SUSPENDED peut être révoqué, comme pour les livreurs. Un relais encore PENDING est refusé (REJECTED), pas révoqué.
- **Depuis tout état** — Partout, un relais peut être révoqué depuis n'importe quel état non final, PENDING compris. Il faudrait alors aligner les livreurs aussi.

**Réponse de l'utilisateur :** Approuvé ou suspendu (Recommandé)

### D-136 · Code suivi · 2026-11-26 07:11 UTC

**Question :** Aujourd'hui le code de suivi (TNT-AAAAMMJJ-XXXXXXXX) est attribué à la livraison et non au colis. Avec vos décisions, un retour ou une nouvelle livraison ordonnée par un litige crée une nouvelle livraison : le destinataire recevrait alors un autre code. Que fait-on ?

Options proposées :

- **Code au colis (Recommandé)** — Le code de suivi est attribué au colis à sa création et ne change jamais. Il suit le colis à travers toutes ses livraisons (aller, retour, nouvelle livraison). C'est lui que Link, l'application et les QR codes utilisent, et le suivi montre l'historique complet. Chaque livraison garde un identifiant interne. Les routes de suivi existantes sont gardées et cherchent désormais par le code du colis.
- **Code par livraison** — On garde un code par livraison. Pour suivre un retour, il faut le nouveau code, transmis au client par notification.
- **Les deux codes** — Le colis a un code permanent, et chaque livraison son propre code. Les deux sont acceptés pour le suivi.

**Réponse de l'utilisateur :** Code au colis (Recommandé)

### D-137 · Sécurité · 2026-11-26 07:11 UTC

**Question :** Le code de suivi est généré avec un tirage aléatoire non cryptographique : on pourrait deviner des codes et suivre le colis de quelqu'un d'autre. Le suivi par code est public (sans compte). Que fait-on ?

Options proposées :

- **Aléa sûr + données masquées (Recommandé)** — Tirage cryptographique (SecureRandom), même format (32^8 ≈ 1 100 milliards de combinaisons par jour), limitation du nombre de requêtes par adresse IP sur le suivi public. Le suivi public ne montre que l'état, la ville et les horaires, avec les données personnelles masquées comme dans Kafka. Le détail reste réservé aux personnes concernées.
- **Code plus long** — En plus, on allonge la partie aléatoire (12 caractères). Le format change, et les codes déjà émis restent valides.
- **Ne rien changer** — On garde le tirage actuel.

**Réponse de l'utilisateur :** Aléa sûr + données masquées (Recommandé)

### D-138 · États hub · 2026-11-26 11:49 UTC

**Question :** Révocation d'un point relais dans le cœur (D-135 : seulement depuis approuvé ou suspendu). Le hub du cœur n'a pas ces états ; il a ACTIVE, FULL (plein), TEMPORARILY_CLOSED (fermé temporairement) et PERMANENTLY_CLOSED (fermé définitivement). Comment appliquer la règle ?

Options proposées :

- **Statut d'agrément séparé (Recommandé)** — Le hub du cœur reçoit un statut d'agrément distinct (PENDING, APPROVED, SUSPENDED, REJECTED, REVOKED), qui fait foi pour Go-Freelancer et Agency. L'état d'exploitation (ouvert, plein, fermé temporairement) reste à part. La révocation se décide sur l'agrément, exactement comme pour les livreurs, et le relais Go recopie l'agrément du cœur au lieu de le porter lui-même.
- **Correspondance d'états** — On garde les états actuels et on les fait correspondre : ACTIVE ou FULL = approuvé, TEMPORARILY_CLOSED = suspendu, PERMANENTLY_CLOSED = révocation refusée. Cela mélange l'ouverture du relais (horaires, capacité) et sa validation par l'administrateur.
- **Go seulement** — La règle ne s'applique qu'au relais Go-Freelancer ; le hub du cœur accepte la révocation depuis tout état non révoqué.

**Réponse de l'utilisateur :** Statut d'agrément séparé (Recommandé)

### D-139 · Suspension · 2026-11-26 11:49 UTC

**Question :** Suspendre ou réactiver un point relais : l'agent a imposé la même règle que pour les livreurs. On suspend seulement un relais approuvé, on réactive seulement un relais suspendu, et tout autre cas répond 409 (avant, la route répondait toujours 200 sans rien faire). Redemander l'état actuel ne fait rien. Validez-vous ?

Options proposées :

- **Valider (Recommandé)** — Même règle que les livreurs : APPROVED ↔ SUSPENDED, 409 sinon ; un relais en attente ne peut pas être activé sans passer par la validation (KYC). La rupture d'API (409 possible) est documentée pour le Mobile, le BFF et Agency.
- **Plus souple** — On accepte aussi d'activer directement un relais en attente (sans validation KYC séparée).

**Réponse de l'utilisateur :** Valider (Recommandé)

### D-140 · Effet hub · 2026-11-26 11:49 UTC

**Question :** Quand un administrateur suspend un point relais, le hub du cœur doit-il cesser de recevoir des colis ?

Options proposées :

- **Oui, plus de dépôts (Recommandé)** — Un relais suspendu ne peut plus être choisi comme destination et refuse tout nouveau dépôt. Les colis déjà en garde restent retirables par les destinataires jusqu'à la fin de leur garde, puis repartent en retour selon les règles habituelles.
- **Oui, et retour immédiat** — Comme ci-dessus, et en plus les colis en garde sont retournés ou transférés tout de suite.
- **Non** — La suspension ne touche que le compte (connexion, notifications) ; le hub continue de recevoir des colis.

**Réponse de l'utilisateur :** Oui, plus de dépôts (Recommandé)

### D-141 · Routes cœur · 2026-11-26 12:23 UTC

**Question :** Routes d'agrément d'un point relais : aujourd'hui seules les routes d'administration de Go-Freelancer pilotent l'agrément (approuver, refuser, suspendre, réactiver), et le cœur n'a qu'une route de révocation. La future plateforme d'administration couvrira tous les produits. Que fait-on ?

Options proposées :

- **Routes cœur + permission (Recommandé)** — Le cœur expose les routes d'agrément de tout hub relais (Go, Agency, indépendant) sous une permission relay:approve réservée à l'administrateur de TiiBnTick. Les routes Go restent et délèguent au cœur. La plateforme d'administration s'appuie sur les routes du cœur.
- **Seulement via Go** — Pas de route d'agrément dans le cœur tant qu'Agency n'en a pas besoin.

**Réponse de l'utilisateur :** Routes cœur + permission (Recommandé)

### D-142 · Permission · 2026-11-26 12:23 UTC

**Question :** La route PATCH /api/v1/gofp/relay-point-profiles/{id}/status (changer le statut d'un relais) est ouverte à tout le « personnel », alors que suspendre et activer exigent la permission d'administration Go (gofp-admin:manage). On aligne ?

Options proposées :

- **Aligner sur l'admin (Recommandé)** — Changer le statut d'agrément d'un relais exige la même permission d'administrateur que suspendre ou activer. C'est une action d'administrateur, donc journalisée (règle 4 bis). La rupture est documentée pour le Mobile et le BFF.
- **Laisser au personnel** — Le personnel garde ce droit.

**Réponse de l'utilisateur :** Aligner sur l'admin (Recommandé)

### D-143 · Hubs Agency · 2026-11-26 12:23 UTC

**Question :** Agency enregistre ses points relais dans un autre registre (organization-core, tnt_hub_relais) que les hubs du cœur utilisés par Go et l'inventaire (geo-core, relay_hubs). La règle « pas de dépôt dans un relais non agréé » ne s'applique donc pas aux relais d'agence. Que fait-on ?

Options proposées :

- **Registre unique cœur (Recommandé)** — Un seul registre de hubs relais, celui du cœur (geo-core), pour tous les produits, avec l'agrément, l'état d'exploitation et l'organisation propriétaire. Le registre d'organization-core devient un rattachement (quelle agence possède quel hub). Migration des données par Liquibase, dans un lot dédié après le chantier des événements.
- **Appliquer la règle aux deux** — On garde les deux registres et on ajoute le contrôle d'agrément au registre d'Agency aussi.
- **Laisser Agency** — Agency garde son fonctionnement ; la règle ne vaut que pour les hubs du cœur.

**Réponse de l'utilisateur :** Point ce point laisser la gestion à la plateforme Agency sachant que une agence est en réalité une organisation(commerciale ou entreprise) qui possède plusieurs agences comme les antennes autres que le sièges dans d'autres villes quartiers(un peu comme General express voyage sur qui tu peux faire des recherches sur internet pour mieux comprendre le concept que je veux que tu comprennes) mais l'agence(ici par abus de langage confondu avec l'organisation à but lucratif qui possède ses agences è plusieurs endroits communément appelle antenne ou Agence de Yaoundé, Douala, Biyem-assi, Mvan) ne dispose pas de point relais.

### D-144 · Destination · 2026-11-26 12:23 UTC

**Question :** Un besoin Go-Freelancer peut indiquer une destination relais inconnue de Go : elle n'est pas vérifiée (comportement actuel). On la vérifie ?

Options proposées :

- **Refuser si non agréée (Recommandé)** — Toute destination relais est vérifiée dans le registre du cœur : inconnue ou non agréée, le besoin est refusé (409 ou 404). C'est cohérent avec la règle D-140.
- **Laisser** — On garde le comportement actuel.

**Réponse de l'utilisateur :** Refuser si non agréée (Recommandé)

### D-145 · Relais en pause · 2026-11-26 12:56 UTC

**Question :** Un point relais approuvé mais mis en pause par son propriétaire (fermé pour un temps : congés, travaux…) : peut-il être choisi comme destination d'un nouveau besoin ?

Options proposées :

- **Non, refusé (Recommandé)** — Tant qu'il est en pause, le relais n'est pas proposé et un besoin qui le vise est refusé (409), comme un relais non agréé. Les colis déjà en route vers lui restent gérés normalement.
- **Oui, accepté** — La pause n'empêche pas de le choisir : le colis sera déposé à sa réouverture.

**Réponse de l'utilisateur :** Non, refusé (Recommandé)

### D-146 · Relais plein · 2026-11-26 12:56 UTC

**Question :** Un point relais agréé mais plein (capacité atteinte) : peut-il être choisi comme destination d'un nouveau besoin ?

Options proposées :

- **Oui, contrôle au dépôt (Recommandé)** — On l'accepte, car le colis n'arrive que plus tard et la capacité aura pu se libérer. Le contrôle se fait au moment du dépôt : si le relais est toujours plein, le dépôt est refusé et le livreur est orienté vers le relais agréé le plus proche, ou un incident « relais plein » est déclaré.
- **Non, refusé** — Un relais plein n'est pas proposé et un besoin qui le vise est refusé.

**Réponse de l'utilisateur :** Non, refusé

### D-147 · Payeur relais · 2026-11-26 15:11 UTC

**Question :** Quand un colis n'est pas retiré au point relais, c'est le relais qui paie la livraison retour (QO-4). Dans le système, ce payeur est aujourd'hui représenté par l'identifiant du hub, et pas par une personne ou un portefeuille. Qui doit être enregistré comme payeur ?

Options proposées :

- **Acteur exploitant (Recommandé)** — Le payeur est l'acteur du cœur qui exploite le relais (son opérateur). C'est son portefeuille qui alimente le séquestre Kernel et c'est lui qui reçoit le brouillon d'annonce retour à compléter. L'id du hub reste en référence.
- **Id du hub** — On garde l'id du hub comme payeur ; la conversion vers un portefeuille se fera au moment du séquestre.

**Réponse de l'utilisateur :** Le propriétaire ou le responsable d'un point relais n'a pas a payé la livraison pour un colis non retiré quand le client initie la livraison le montant de la livraison est gelé de son wallet pour assurer que la livraison soit payé si le colis arrive au point relais et que le destinataire ne vient pas retirer le colis pour les livraisons qui demandaient une livraison au point relais le montant est définitivement retirer du compte de l'expéditeur pour payer la livraison la durée de l'attente avant que ce processus soit enclanché est le même que celui au bout duquel un colis est déclaré abandonné par le destinataire. Ceci est applicable uniquement pour les cas où c'est possible(l'expéditeur paye la livraison via son wallet) pour tous les autres scénarios marqués d'abord comme non traités pour le moment.

### D-148 · Retour annulé · 2026-11-26 15:11 UTC

**Question :** Une livraison retour annulée avant que le livreur ait récupéré le colis : le colis reste aujourd'hui « retour demandé », sans événement ni nouveau brouillon. Que doit-il se passer ?

Options proposées :

- **Nouveau brouillon (Recommandé)** — Le colis reste « retour demandé », un événement de colis est publié, et un nouveau brouillon d'annonce retour est recréé pour le payeur, qui doit relancer le retour. Rien n'est perdu et le colis n'est jamais bloqué sans suite.
- **Retour abandonné** — L'annulation abandonne le retour : le colis revient à son état d'avant la demande de retour (par exemple en garde au relais).
- **Laisser tel quel** — Le colis reste « retour demandé » sans événement ; un administrateur ou le payeur relance à la main.

**Réponse de l'utilisateur :** Nouveau brouillon (Recommandé)

### D-149 · Dépôt relais · 2026-11-26 15:11 UTC

**Question :** Dans Go-Freelancer, quand un livreur dépose le colis au point relais (et non chez le destinataire), la mission est aujourd'hui considérée comme terminée : ancrage blockchain « mission terminée » et paiement du livreur. Est-ce correct ?

Options proposées :

- **Oui si relais = destination (Recommandé)** — Si le relais était la destination choisie de la livraison, le dépôt termine bien la mission du livreur : ancrage et libération de sa part du séquestre. Si le relais n'est qu'une étape (transfert, colis non livrable), la mission continue et rien n'est payé avant la vraie fin.
- **Paiement au retrait** — Le livreur n'est payé qu'au retrait du colis par le destinataire au relais.
- **Toujours terminé** — On garde le comportement actuel : tout dépôt au relais termine la mission.

**Réponse de l'utilisateur :** Oui si relais = destination (Recommandé)

### D-150 · Colis sans tenant · 2026-11-26 15:11 UTC

**Question :** Un colis enregistré sans organisation ni tenant ne publie aucun événement Kafka « colis créé », car le message exige un tenant. Avec le modèle à tenant système unique, que fait-on ?

Options proposées :

- **Tenant système (Recommandé)** — Tout colis sans tenant explicite est rattaché au tenant système unique de TiiBnTick, et tous ses événements sont publiés. Un test garantit qu'aucun chemin d'enregistrement ne crée un colis sans tenant.
- **Refuser la création** — Créer un colis sans tenant devient une erreur (400).

**Réponse de l'utilisateur :** Tenant système (Recommandé)

### D-151 · Montant gelé · 2026-11-26 15:39 UTC

**Question :** Je veux appliquer exactement votre réponse sur le colis non retiré au relais (le relais ne paie jamais ; le wallet de l'expéditeur est gelé à la création, puis débité définitivement à la fin de la garde). Mais vous venez aussi de valider que le livreur est payé dès le dépôt au relais quand le relais est la destination. Que couvre le montant gelé à la création ?

Options proposées :

- **Garantie du retour (Recommandé)** — À la création d'une livraison vers un relais, le prix de l'aller est mis en séquestre comme d'habitude, et le livreur est payé au dépôt. En plus, le prix estimé du retour est gelé sur le wallet de l'expéditeur. Si le destinataire retire le colis, ce montant est dégelé. S'il ne le retire pas avant la fin de la garde (le délai d'abandon), il est débité définitivement et paie la livraison retour.
- **Aller gardé jusqu'au retrait** — Le séquestre de l'aller n'est libéré qu'au retrait par le destinataire, ou à la fin de la garde (débit définitif de l'expéditeur). Le livreur n'est donc pas payé dès le dépôt : cela remplace la réponse précédente « payé au dépôt ». Le retour serait ensuite une nouvelle livraison payée par l'expéditeur.
- **Débit à l'abandon** — Rien de plus n'est gelé à la création. À la fin de la garde, le prix du retour est prélevé automatiquement sur le wallet de l'expéditeur. Si le solde est insuffisant, le cas est marqué « non traité pour le moment ».

**Réponse de l'utilisateur :** Partiellement d'accord avec 1. Garantie du retour (Recommandé)
     À la création d'une livraison vers un relais, le prix de l'aller est mis en séquestre comme d'habitude, et le livreur est payé au dépôt. En plus, le prix estimé du retour est gelé sur le wallet de l'expéditeur. Si le destinataire
     retire le colis, ce montant est dégelé. Mais par contre je veux préciser que le montant gelé est exactement celui de la somme qui servira a payé le livreur à la livraison au point relais avec tout le mécasnisme des parts(prélèvement plateforme, part du point relais quand concerné et applicable, part livreur, ....) déjà connu.


### D-152 · Garantie de retour · 2026-11-27 04:34 UTC

**Question :** Garantie de retour d'une livraison vers un point relais (D-147, D-151) : le Kernel ne sait geler de l'argent que par un séquestre, et chaque séquestre exige un code envoyé au payeur. Comment l'expéditeur confirme-t-il ce gel ?

Options proposées :

- **Deux codes à la sélection (Recommandé)** — À la sélection d'une livraison vers un relais, le client confirme deux codes : celui du prix de l'aller et celui de la garantie (un second séquestre du même montant, au profit du fonds). Retrait au relais : la garantie est remboursée. Fin de la garde : elle est versée au fonds, qui paie le livreur du retour. Le gel est exact et séparé.
- **Un seul code, double montant** — Un seul séquestre de deux fois le prix : la part garantie va au fonds dès le dépôt au relais. Au retrait, le fonds rembourse l'expéditeur par une indemnité. Un seul code, mais l'argent quitte le wallet de l'expéditeur au dépôt et revient ensuite.
- **Confirmation séparée plus tard** — La sélection reste à un code. Après l'élection, l'expéditeur est invité à confirmer la garantie par une étape distincte (défi puis code). Tant qu'il ne l'a pas fait, la garantie est « non traitée » et visible par l'administrateur.

**Réponse de l'utilisateur :** Deux codes à la sélection (Recommandé)

### D-153 · QO-11 · 2026-11-27 08:30 UTC

**Question :** QO-11 (livreur remplacé en cours de route) : vous aviez choisi « prorata distance ». Le séquestre Kernel ne permet pas de modifier les parts après la mise en séquestre : son API n'a que hold/release/refund/freeze/decision, sans aucun champ de parts. Aujourd'hui, toute la part livreur va au livreur d'origine (provisoire). Que fait-on ?

Options proposées :

- **Fonds paie le remplaçant (Recommandé)** — Le séquestre paie toujours la part au livreur d'origine (parts figées). Le fonds d'indemnisation verse au remplaçant sa part au prorata de la distance et ouvre une dette du même montant sur le livreur d'origine, recouvrée comme les autres dettes. Une demande de modification des parts est aussi faite au Kernel.
- **Tout au livreur d'origine** — On garde le comportement actuel : le remplaçant n'est pas payé par la plateforme. On documente la limite et on fait une demande au Kernel.
- **Attendre le Kernel** — Rien ne change dans le Core pour l'instant. Une demande de route de modification des parts est faite au Kernel ; le prorata sera fait quand elle existera.

**Réponse de l'utilisateur :** Fonds paie le remplaçant (Recommandé)

### D-154 · gdd-final · 2026-11-27 08:30 UTC

**Question :** Clôture GOFP (changeset gdd-final, spec gofp-dedup §4.6) : il supprime les tables et colonnes Go dupliquées par le Core (announcements, payments, notifications*, relay_deposits, gofreelancer_vehicles, evaluations, addresses, packets, colonnes de deliveries…) et les routes de mot de passe local (D-054). C'est irréversible sur une base existante. Je le fais maintenant ?

Options proposées :

- **Oui, avec garde-fous (Recommandé)** — Je retire d'abord le code Go qui lit encore ces tables. Chaque suppression est précédée d'une précondition : on compte les lignes sans contrepartie dans le Core, et la migration s'arrête (HALT) s'il y en a. Le tout est testé sur une base migrée de la version précédente.
- **Plus tard, par vous** — Je ne touche pas à la clôture ; vous la passerez après vérification des données de production. Je documente ce qui reste à supprimer.

**Réponse de l'utilisateur :** Oui, avec garde-fous (Recommandé)

### D-155 · Kernel/e2e · 2026-11-27 08:30 UTC

**Question :** Vérification en direct contre le Kernel et scripts e2e (scripts/e2e) : il faut lancer l'application complète sur le vrai Kernel, et vous devez me donner un code MFA frais au moment des écritures. On le fait quand ?

Options proposées :

- **Maintenant** — Je démarre l'application sur le Kernel, je vous demande le code MFA au moment voulu et je lance les scripts e2e (coursier freelance, flux freelance, présence).
- **Plus tard** — Je m'arrête là pour la partie en direct ; les tests d'intégration (vrais Kafka, PostGIS, Redis ; Kernel simulé par MockWebServer) restent la preuve actuelle.

**Réponse de l'utilisateur :** Maintenant

### D-156 · Conteneurs · 2026-11-27 08:30 UTC

**Question :** Les conteneurs de test tnt-pg-l7, tnt-kafka-l7 et tnt-redis-l7 (créés pour ce chantier) tournent encore. Je les supprime ?

Options proposées :

- **Les garder** — Ils servent encore aux tests d'intégration (KafkaAsyncWiringTest, TntApplicationFullStackIT) : on les supprimera à la fin de tout.
- **Les supprimer** — docker rm -f des trois conteneurs ; il faudra les recréer pour relancer les tests d'intégration.

**Réponse de l'utilisateur :** Les garder

### D-157 · Inbox C-36 · 2026-11-27 19:40 UTC

**Question :** Fusion de main : le lot C-36 de main ajoute une boîte de notifications in-app propre au module Go (table locale « notifications », endpoints /notifications lus par l'app). Votre branche avait posé que le module Go ne garde aucune table de notifications : tout passe par tnt-notify-core et le Kernel (modèles Kernel D-011 ; le Kernel offre un canal IN_APP avec /inbox). Que fait-on de la boîte C-36 ?

Options proposées :

- **Garder C-36 tel quel (Recommandé)** — La table notifications reste la boîte in-app Go (gdd-final-907 ne la supprime plus) ; le garde-fou ne vise plus que notification_annonces/livraisons. L'app de main fonctionne tout de suite ; un écart assumé avec « tout par le Kernel », à reprendre plus tard.
- **Garder l'API, stocker via Kernel** — Mêmes endpoints et mêmes événements, mais chaque notification part au Kernel (canal IN_APP) via tnt-notify-core et la boîte est lue sur /inbox du Kernel. Conforme à votre branche, mais plus long et non prouvable sans test live Kernel (MFA).
- **Retirer C-36** — Votre branche fait foi : boîte in-app locale, endpoints et événements C-36 retirés. L'app de main perd sa boîte de notifications.

**Réponse de l'utilisateur :** Garder l'API, stocker via Kernel

### D-158 · Auto-approb. · 2026-11-27 19:40 UTC

**Question :** Fusion de main : le lot C-57 de main approuve automatiquement un point relais dès que son propriétaire l'enregistre (tnt.gofp.relay.auto-approve, vrai par défaut, plus un rattrapage au démarrage des relais PENDING), « en attendant la plateforme d'administration ». Je l'ai fait passer par votre chemin D-138 (le hub Core est agréé d'abord). Quelle valeur par défaut ?

Options proposées :

- **Vrai par défaut, comme main (Recommandé)** — Les relais sont utilisables tout de suite tant qu'il n'y a pas d'admin ; l'admin peut toujours suspendre ou révoquer. Ajouté à la spec admin (réactiver l'approbation manuelle).
- **Faux par défaut** — Votre branche fait foi : approbation manuelle par un admin (D-138) ; l'auto-approbation reste disponible via TNT_GOFP_RELAY_AUTO_APPROVE=true.

**Réponse de l'utilisateur :** Vrai par défaut, comme main (Recommandé)

### D-159 · Source rôle · 2026-11-27 21:27 UTC

**Question :** Série 1/4 — Où le Core doit-il garder la vérité sur « qui est TNT_ADMIN », pour que le rôle marche quel que soit le tenant de connexion et sans que la personne soit membre du tenant système ?

Options proposées :

- **Registre Core dédié (Recommandé)** — Nouvelle table des administrateurs de la plateforme, une ligne par compte Kernel (id = sub du jeton), lue quel que soit le tid. Elle porte qui a attribué, le type (SYSTEM_OWNER ou ASSIGNED), le tenant d'obtention, les dates et l'auteur de la révocation. Le Kernel n'est plus qu'un miroir facultatif.
- **Étendre tnt_user_role_assignments** — Ajouter des colonnes d'audit à la table actuelle et ignorer le tid pour les rôles de portée SYSTEM. Moins de code, mais on mélange les rôles par tenant et le rôle plateforme, et une révocation supprime la ligne, donc l'historique.
- **Rôles « solution » du Kernel** — Confier le rôle au Kernel (/api/roles/solution) et lire le jeton. Il n'est pas prouvé que le jeton les porte en dehors du tenant système ni qu'un propriétaire de tenant ne puisse pas les fabriquer. Cela dépend aussi d'une autre équipe.

**Réponse de l'utilisateur :** Registre Core dédié (Recommandé)

### D-160 · hasRole · 2026-11-27 21:27 UTC

**Question :** Série 1/4 — Comment corriger le fait qu'un TNT_ADMIN attribué passe @RequirePermission mais échoue sur les plus de 100 contrôles hasRole/hasAnyRole (18 fichiers) qui ne lisent que le jeton ?

Options proposées :

- **Enrichir à l'authent. + garde (Recommandé)** — Un seul point : à la lecture du jeton, le Core ajoute ROLE_TNT_ADMIN (et ce qui va avec) si le registre dit que le compte est admin. Cela couvre d'un coup hasRole, hasAnyRole, @RequirePermission et les gardes manuelles. Un test d'architecture interdit ensuite tout nouveau contrôle qui contournerait ce point.
- **Tout migrer vers @RequirePermission** — Remplacer les plus de 100 annotations par des permissions explicites. Plus propre à terme, mais très gros chantier, risque de régression élevé et effet lent.
- **Les deux, migration par étapes** — Enrichissement tout de suite, puis migration progressive des hasRole vers des permissions, module par module, dans des lots séparés.

**Réponse de l'utilisateur :** Enrichir à l'authent. + garde (Recommandé)

### D-161 · Propriétaire · 2026-11-27 21:27 UTC

**Question :** Série 1/4 — Comment le propriétaire du tenant système obtient-il son entrée SYSTEM_OWNER, qui le garde TNT_ADMIN même quand il se reconnecte plus tard dans un autre tenant ?

Options proposées :

- **Auto à la connexion + bouton (Recommandé)** — Dès qu'un jeton du tenant système avec ROLE_OWNER arrive, le Core crée l'entrée SYSTEM_OWNER une seule fois (attribuée par lui-même, tenant d'obtention = tenant système). Un endpoint « réclamer » permet aussi de le faire explicitement. Personne n'a à y penser.
- **Action explicite seulement** — Le propriétaire, connecté dans le tenant système, appelle lui-même un endpoint pour s'attribuer le rôle. Tant qu'il ne l'a pas fait, il reste admin seulement dans le tenant système.
- **Variable de configuration** — L'identifiant du propriétaire est donné par TNT_SYSTEM_OWNER_USER_ID au déploiement. Pas de dépendance au jeton, mais une valeur de plus à maintenir à chaque environnement.

**Réponse de l'utilisateur :** Auto à la connexion + bouton (Recommandé)

### D-162 · Faille · 2026-11-27 21:27 UTC

**Question :** Série 1/4 — Faille prouvée : un jeton portant ROLE_TNT_ADMIN#TENANT:<autre tenant> (rôle qu'un propriétaire de tenant peut créer chez lui dans le Kernel) obtient system:admin via @RequirePermission. Que fait-on ?

Options proposées :

- **Fermer tout de suite (Recommandé)** — Le Core ne fait plus confiance à aucun rôle privilégié venu du jeton (claims roles et permissions). Seuls CLIENT et FREELANCER restent acceptés. TNT_ADMIN ne vient plus que du registre Core ou de la règle du propriétaire. Un test de non-régression reprend la sonde.
- **Fermer avec la refonte** — Corriger dans le même lot que le registre, sans commit de sécurité séparé.
- **Ne pas toucher** — Garder le comportement actuel (non recommandé : élévation de privilèges).

**Réponse de l'utilisateur :** Fermer tout de suite (Recommandé)

### D-163 · Protection · 2026-11-27 21:27 UTC

**Question :** Série 2/4 — Le TNT_ADMIN du propriétaire du tenant système (entrée SYSTEM_OWNER) ne doit pas pouvoir être supprimé. Jusqu'où va cette protection ?

Options proposées :

- **Liée au tenant système actuel (Recommandé)** — Toute révocation de l'entrée SYSTEM_OWNER est refusée (409) tant que son tenant d'obtention est le tenant système en vigueur, y compris par lui-même. Si le tenant système change, elle redevient une entrée « tenant d'obtention différent », révocable par la révocation de masse.
- **Absolue** — Jamais révocable par l'API, même après un changement de tenant système. Seule une intervention en base peut la retirer.

**Réponse de l'utilisateur :** Liée au tenant système actuel (Recommandé)

### D-164 · Tenant obt. · 2026-11-27 21:27 UTC

**Question :** Série 2/4 — Qu'appelle-t-on le « tenant d'obtention » d'un TNT_ADMIN, enregistré pour chaque attribution et utilisé par la révocation de masse ?

Options proposées :

- **Tenant du jeton de l'attributeur (Recommandé)** — Le tid de celui qui attribue, au moment de l'attribution. Pour SYSTEM_OWNER, c'est le tenant système. On enregistre aussi, pour l'audit, le tenant système en vigueur à ce moment-là.
- **Tenant système en vigueur** — Le tenant système configuré au moment de l'attribution, quel que soit l'espace de l'attributeur. La révocation de masse ne servirait alors qu'après un changement de tenant système.

**Réponse de l'utilisateur :** Je propose Tenant du jeton de l'attributeur sachant que même si l'attributeur est SYSTEM_OWNER il peu se connecter avec un autre tenant et toujours être reconnu TNT_ADMIN puis procéder à l'attribution et à ce moment le tenant d'obtention n'est plus le tenant système.

### D-165 · Révoc. masse · 2026-11-27 21:27 UTC

**Question :** Série 2/4 — Comment se passe la révocation de tous les TNT_ADMIN dont le tenant d'obtention est différent du tenant système ?

Options proposées :

- **Aperçu puis confirmation (Recommandé)** — Un appel en aperçu renvoie la liste exacte et un jeton de confirmation de courte durée. Un second appel avec ce jeton révoque exactement cette liste, en enregistrant l'auteur, le motif et la date. L'entrée protégée du propriétaire n'est jamais touchée.
- **Un seul appel** — Révoque immédiatement avec un motif obligatoire. Plus simple, mais pas de garde-fou contre une erreur.
- **Automatique au changement** — Au démarrage, si le tenant système a changé, le Core révoque tout seul. Pas d'oubli possible, mais un déploiement mal configuré retirerait tous les admins.

**Réponse de l'utilisateur :** Aperçu puis confirmation (Recommandé)

### D-166 · Droits · 2026-11-27 21:27 UTC

**Question :** Série 2/4 — Qui peut faire quoi parmi les TNT_ADMIN ?

Options proposées :

- **Tous attribuent, SYSTEM_OWNER révoque en masse (Recommandé)** — Tout TNT_ADMIN actif peut attribuer le rôle et révoquer un admin ASSIGNED. La révocation de masse est réservée au propriétaire du tenant système. Personne ne révoque l'entrée protégée.
- **Tout réservé au SYSTEM_OWNER** — Seul le propriétaire attribue et révoque. Très sûr, mais il devient un point de passage obligatoire.
- **Tous égaux** — Tout TNT_ADMIN peut attribuer, révoquer et lancer la révocation de masse (comportement actuel étendu).

**Réponse de l'utilisateur :** Tous attribuent, SYSTEM_OWNER révoque en masse (Recommandé)

### D-167 · Contenu rôle · 2026-11-27 21:27 UTC

**Question :** Série 3/4 — Aujourd'hui le propriétaire du tenant système reçoit ROLE_TNT_ADMIN plus 11 autres rôles (AGENCY_MANAGER, FREELANCER, CLIENT…) et des autorités fines. Que reçoit un TNT_ADMIN du registre ?

Options proposées :

- **Même ensemble que le propriétaire (Recommandé)** — Une seule constante partagée : un admin attribué et le propriétaire ont exactement les mêmes autorités. Cohérence maximale, aucun écran admin ne se comporte différemment selon l'origine du rôle.
- **TNT_ADMIN seul pour tous** — Pour tous, propriétaire compris : ROLE_TNT_ADMIN et les autorités admin fines, sans les rôles métier (CLIENT, FREELANCER…). Moindre privilège, mais les endpoints métier qui n'acceptent pas TNT_ADMIN deviennent fermés au propriétaire.

**Réponse de l'utilisateur :** TNT_ADMIN seul pour tous

### D-168 · Cache · 2026-11-27 21:27 UTC

**Question :** Série 3/4 — Le Core consultera le registre à chaque requête authentifiée. Comment le servir sans coût ni délai de révocation ?

Options proposées :

- **Cache court + invalidation (Recommandé)** — Cache mémoire de 60 s par compte, vidé sur toutes les instances dès qu'un rôle est attribué ou révoqué (événement déjà utilisé pour les permissions). Une révocation prend effet en quelques secondes, avec 60 s maximum si l'événement se perd.
- **Pas de cache** — Une requête SQL indexée à chaque appel authentifié (environ 1 ms). La révocation est immédiate, mais la base est sollicitée à chaque appel.

**Réponse de l'utilisateur :** Cache court + invalidation (Recommandé)

### D-169 · Reprise · 2026-11-27 21:27 UTC

**Question :** Série 3/4 — Que fait-on des TNT_ADMIN déjà attribués dans l'ancienne table tnt_user_role_assignments ?

Options proposées :

- **Reprise en LEGACY (Recommandé)** — Un changeset les recopie dans le registre avec le type LEGACY, sans auteur connu, et le tenant système actuel comme tenant d'obtention. Ils restent admins et peuvent être révoqués un par un. Les anciennes lignes TNT_ADMIN cessent d'être lues.
- **Pas de reprise** — Les anciennes attributions sont ignorées : chaque admin doit être réattribué à la main par le propriétaire.

**Réponse de l'utilisateur :** Pas de reprise

### D-170 · Miroir Kernel · 2026-11-27 21:27 UTC

**Question :** Série 3/4 — Faut-il encore recopier le TNT_ADMIN dans le Kernel, sachant que le Core ne fera plus confiance au jeton pour ce rôle ?

Options proposées :

- **Non, Core seul (Recommandé)** — Plus d'écriture Kernel pour TNT_ADMIN : il n'y a plus de 403 possible et plus besoin que la personne soit membre du tenant système. Les rôles par tenant (CLIENT, FREELANCER…) continuent d'aller au Kernel comme avant.
- **Miroir facultatif** — Envoi au Kernel (chemin « solution ») quand c'est possible, sans jamais bloquer. Visible dans les outils du Kernel, mais deux sources à garder alignées.

**Réponse de l'utilisateur :** Non, Core seul (Recommandé)

### D-171 · Ancien chemin · 2026-11-27 21:27 UTC

**Question :** Série 4/4 — L'endpoint actuel POST /api/v1/admin/roles/assignments accepte aussi roleCode=TNT_ADMIN, et l'ancienne table lui donne encore la permission « * ». Que deviennent-ils ?

Options proposées :

- **Rediriger vers le registre (Recommandé)** — Nouveaux endpoints dédiés /api/v1/admin/platform-admins (attribuer, révoquer, lister, historique, réclamer, révocation de masse). L'ancien endpoint garde son contrat mais envoie TNT_ADMIN au registre. Les anciennes lignes TNT_ADMIN ne donnent plus aucune permission (conforme « pas de reprise »).
- **Refuser TNT_ADMIN sur l'ancien** — L'ancien endpoint répond 400 pour TNT_ADMIN en indiquant le nouveau. Plus net, mais les clients qui l'utilisent doivent changer.

**Réponse de l'utilisateur :** Rediriger vers le registre (Recommandé)

### D-172 · Alertes · 2026-11-27 21:27 UTC

**Question :** Série 4/4 — Qui est prévenu quand un TNT_ADMIN est attribué ou révoqué ?

Options proposées :

- **Événement + courriel (Recommandé)** — Événement d'audit Kafka à chaque attribution ou révocation. Courriel (Kernel) au bénéficiaire et au propriétaire du tenant système, pour qu'une attribution suspecte se voie tout de suite.
- **Événement seul** — Trace d'audit uniquement, sans message aux personnes.

**Réponse de l'utilisateur :** Événement + courriel (Recommandé)

### D-173 · JWT Kernel · 2026-11-27 21:27 UTC

**Question :** Série 4/4 — Pour que, à terme, le JWT du Kernel porte des rôles TiiBnTick de confiance, que fait-on ?

Options proposées :

- **Contrôle K19 + demande Kernel (Recommandé)** — J'ajoute aux contrôles du contrat Kernel (K19) : des rôles « solution » TiiBnTick, valables dans tous les tenants et impossibles à fabriquer par un propriétaire de tenant, portés par le jeton. Puis je les teste en réel quand vous fournirez une connexion MFA. Le registre Core reste la vérité tant que ce n'est pas prouvé.
- **Ne rien demander** — Le registre Core suffit, le JWT n'est jamais utilisé pour TNT_ADMIN.

**Réponse de l'utilisateur :** Expliques bien ceci car je ne comprends pas puis repose la question. Surtout au niveau de «demande kernel» cela veut il dire que nous allons demander un ajoute de fonctionnalités au développeurs du kernel ?

### D-174 · Étendue · 2026-11-27 21:27 UTC

**Question :** Série 4/4 — Le registre doit-il servir aussi à d'autres rôles de la plateforme (SUPPORT_AGENT, MEDIATOR), qui ont aujourd'hui le même défaut de dépendre du tenant de connexion ?

Options proposées :

- **TNT_ADMIN seul, table extensible (Recommandé)** — Seul TNT_ADMIN maintenant, mais la table porte un code de rôle (tnt_platform_role_grants) : ajouter SUPPORT_AGENT ou MEDIATOR plus tard sera une décision, sans migration de schéma.
- **Aussi SUPPORT_AGENT et MEDIATOR** — Les trois rôles plateforme passent au registre dès ce lot, avec les mêmes règles d'audit. Plus long et plus de tests.
- **TNT_ADMIN seul, table dédiée** — Table tnt_platform_admins sans code de rôle : plus simple, mais une extension future demandera une migration.

**Réponse de l'utilisateur :** TNT_ADMIN seul, table extensible (Recommandé)

### D-175 · JWT Kernel (reprise) · 2026-11-27 21:27 UTC

**Question :** Série 4/4 (reprise) — Le Kernel a déjà des rôles « solution » (propres à TiiBnTick, pas à un tenant). Il reste à prouver que le jeton les porte dans tous les tenants et qu'un propriétaire de tenant ne peut pas les imiter. Le registre Core reste la vérité dans tous les cas. Que fait-on ?

Options proposées :

- **Vérifier d'abord, demander si manque (Recommandé)** — J'ajoute le contrôle K19 au contrat Kernel et je le teste en réel quand vous fournirez une connexion MFA. Si l'essai montre que la fonctionnalité manque, je rédige une fiche de demande pour l'équipe Kernel, que vous transmettez vous-même. Aucun contact de ma part.
- **Préparer la demande tout de suite** — Je rédige dès maintenant la fiche pour l'équipe Kernel, sans attendre l'essai réel. Vous la transmettez si vous le souhaitez.
- **Ne rien faire côté Kernel** — Le registre Core suffit. Le jeton n'est jamais utilisé pour TNT_ADMIN et aucun contrôle K19 n'est ajouté.

**Réponse de l'utilisateur :** Vérifier d'abord, demander si manque (Recommandé)

### D-176 · Rôles d'agence venus du jeton (P-1) · 2026-11-27 22:09 UTC

**Question :** P-1 : la faille fermée pour TNT_ADMIN existe aussi pour les rôles d'agence/organisation. Un propriétaire de tenant peut se fabriquer « ROLE_AGENCY_MANAGER#… » dans son propre tenant Kernel, et le Core développe alors les permissions de l'agence sans contrôle de portée. Que fait-on ?

Options proposées :

- **Vérifier la portée (Recommandé)** — Le Core ne développe un rôle d'agence/organisation venu du jeton que si l'appartenance est confirmée par ses propres tables (membre de l'agence/organisation). Le reste est ignoré, avec un test de non-régression.
- **Ne plus développer depuis le jeton** — Les rôles d'agence/organisation viennent uniquement des tables du Core (comme TNT_ADMIN). C'est plus strict mais plus gros, et touche le flux Agency.
- **Plus tard** — Laisser en l'état et le noter comme dette de sécurité.

**Réponse de l'utilisateur :** Vérifier la portée (Recommandé)

### D-177 · Transfert de propriété du tenant système (P-2) · 2026-11-27 22:09 UTC

**Question :** P-2 : si le propriétaire du tenant système change dans le Kernel (transfert de propriété, même tenant système), l'ancienne entrée SYSTEM_OWNER reste protégée et active. Que fait-on ?

Options proposées :

- **Bascule auto à la connexion (Recommandé)** — Quand un nouveau propriétaire se connecte dans le tenant système, son entrée SYSTEM_OWNER est créée et l'ancienne passe en ASSIGNED (révocable), motif tracé, alerte aux deux.
- **Action manuelle du nouveau** — Le nouveau propriétaire réclame la propriété ; l'ancien reste protégé jusqu'à ce qu'il le demande explicitement.
- **Ne rien faire** — Un seul SYSTEM_OWNER protégé pour toujours, sauf changement de tenant système.

**Réponse de l'utilisateur :** Bascule auto à la connexion (Recommandé)

### D-178 · Filtre des chemins de tenant (P-4) · 2026-11-27 22:09 UTC

**Question :** P-4 : le filtre des chemins « /tenants/{id}/… » ne laisse passer qu'une permission « * », que les TNT_ADMIN n'ont jamais eue. Un TNT_ADMIN est donc bloqué sur les données d'un autre tenant par ces routes. Faut-il l'ouvrir aux TNT_ADMIN ?

Options proposées :

- **Ouvrir en lecture seule (Recommandé)** — Un TNT_ADMIN passe le filtre pour GET sur tout tenant (support, audit) ; écritures toujours limitées à son tenant. Chaque accès inter-tenant est journalisé.
- **Ouvrir complètement** — Lecture et écriture sur tout tenant pour un TNT_ADMIN, journalisées.
- **Garder fermé** — Un TNT_ADMIN doit se connecter dans le tenant concerné.

**Réponse de l'utilisateur :** Ouvrir en lecture seule (Recommandé)

### D-179 · Contrôles hasRole (P-3) · 2026-11-27 22:09 UTC

**Question :** P-3 : plus de 100 contrôles écrits « hasRole('TNT_ADMIN') » (18 fichiers) fonctionnent désormais, mais restent grossiers. Faut-il les migrer progressivement vers @RequirePermission (permissions fines par action) ?

Options proposées :

- **Migration progressive (Recommandé)** — Chaque fois qu'un contrôleur admin est touché, on remplace hasRole par la permission précise ; un test liste ceux qui restent. Aucun lot dédié.
- **Lot dédié maintenant** — Tout migrer d'un coup dans un lot à part, avec tests.
- **Garder hasRole** — TNT_ADMIN reste un rôle tout-ou-rien sur ces écrans.

**Réponse de l'utilisateur :** Laisser pour le moment et noter comme dette envisageant une migration complète pour plus tard.

## 3. Documents de décisions détaillés par chantier

| Chantier | Document versionné |
|---|---|
| Dédoublonnage Go-Freelancer (architecture cible, hypothèses H1–H12, lots) | [architecture/chantiers/gofp-dedup-architecture-cible.md](../architecture/chantiers/gofp-dedup-architecture-cible.md), [audits/remediation/gofp-dedup/README.md](../audits/remediation/gofp-dedup/README.md) |
| Tarifs par type de véhicule (étude, grille proposée, sources) | [architecture/chantiers/tarifs-par-vehicule.md](../architecture/chantiers/tarifs-par-vehicule.md) |
| Cycle du colis, livraison, incidents, litiges, séquestre, Kafka par agrégat — décisions | [architecture/chantiers/decisions-evenements-colis-livraison.md](../architecture/chantiers/decisions-evenements-colis-livraison.md) |
| Même chantier — spécification cible de l'architecte (questions QO-1…QO-31) | [architecture/chantiers/cible-evenements-colis-livraison.md](../architecture/chantiers/cible-evenements-colis-livraison.md) |
| Fiabilité Kafka (wallet, outbox, anti-doublons, départ des groupes) | [audits/remediation/kafka-async-wiring.md](../audits/remediation/kafka-async-wiring.md) §8 |
| Migration vers le wallet Kernel | [audits/remediation/kernel-wallet-migration.md](../audits/remediation/kernel-wallet-migration.md) |
| Administrateurs de la plateforme (registre TNT_ADMIN, SYSTEM_OWNER, faille du jeton, K19, K20, alertes localisées) — D-159 à D-179 et directive du 2026-11-27 sur le message au propriétaire | [architecture/chantiers/registre-administrateurs-plateforme.md](../architecture/chantiers/registre-administrateurs-plateforme.md) |
| Validations et réglages administrateur | [plateforme-administration/specifications.md](../plateforme-administration/specifications.md) |
