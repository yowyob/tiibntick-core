# Chantier — Notifications : tout passe par le moteur du Kernel

**Branche :** `feat/notifications-kernel-delegation`
**Principe (utilisateur) :** le Core n'a aucun mécanisme de notification de base ; il utilise au mieux le moteur de notifications du Kernel (`/api/notifications/*`, tag `notification-controller`).

## Constat de l'audit (avant)

Le Core envoyait bien email / SMS / WhatsApp / push au Kernel, mais :

| # | Constat | Gravité |
|---|---|---|
| 1 | Le canal **in-app** (`IN_APP_WEBSOCKET`, utilisé par Market, par les push Go-Freelancer et d'autres) passait par un `SimpMessagingTemplate` STOMP qui **n'existe pas** dans l'application WebFlux : aucun fournisseur → « No provider configured », envoi en échec silencieux. Le Kernel sait faire (canaux `WEBSOCKET`/`IN_APP`). | Bug |
| 2 | Le Kernel exige `X-Tenant-Id` **et** `X-Organization-Id` ; Market, Go-Freelancer et le provisioning d'agence passaient l'organisation à `null` → rejet `ORGANIZATION_CONTEXT_REQUIRED`. | Bug |
| 3 | Pour push et in-app, le destinataire (UUID utilisateur) partait comme `recipientAddress` au lieu de `recipientUserId` : le Kernel ne pouvait ni retrouver les appareils/sessions de l'utilisateur ni appliquer ses préférences. | Bug |
| 4 | Mécanismes parallèles : adaptateurs directs SMTP / Orange-MTN / Meta WhatsApp / FCM (désactivés par défaut mais présents, avec leurs jetons), table locale `tnt_notifications` + historique local, préférences locales de repli, interrupteur `tnt.notify.kernel.enabled`. | Dette |
| 5 | Les textes étaient rendus par le Core puis envoyés en texte brut ; `templateCode` / `variables` du Kernel n'étaient jamais utilisés. | Écart |

## Fait sur la branche

- **Un seul port sortant** `INotificationDeliveryPort`, implémenté par `KernelDeliveryAdapter` : tous les canaux (email, SMS, WhatsApp, push, in-app → `WEBSOCKET`) partent chez le Kernel.
- **Adressage correct** : un UUID destinataire est envoyé en `recipientUserId` (push / in-app : pas d'adresse) ; l'adresse explicite est conservée pour mail / SMS / WhatsApp, avec le `recipientUserId` quand il est connu (préférences appliquées côté Kernel). La priorité métier part en `metadata.priority`.
- **Contexte Kernel** : `KernelNotificationClient` retombe sur `tnt.notify.kernel.default-tenant-id` / `default-organization-id` quand l'appelant n'en a pas ; si rien n'est configuré, l'appel échoue immédiatement avec un message explicite (au lieu d'un rejet opaque du Kernel).
- **Modèles dans le Kernel** : `KernelTemplateRegistrar` enregistre à la demande, par tenant/organisation, chaque `(code, canal, langue)` utilisé (liste unique mise en cache puis `POST /templates` pour les manquants) à partir des packs i18n (`notify.*`). Le Core envoie `templateCode` + `variables`. L'enregistrement est « au mieux » : un échec est journalisé et l'envoi continue avec le corps déjà prêt. Interrupteur : `TNT_NOTIFY_KERNEL_TEMPLATES_ENABLED` (défaut `true`).
- **Historique et préférences = Kernel** : `GET /api/v1/notifications/**` lit le journal de livraisons du Kernel (`KernelNotificationSearchAdapter`, filtré par tenant/organisation) ; les préférences passent toujours par le Kernel. Les routes de recherche et l'API Link (`/api/v1/platform/link/notifications/me`) sont désormais **scopées par tenant**.
- **Supprimés** : adaptateurs SMTP / Orange-MTN / WhatsApp / FCM / STOMP, persistance locale (`NotificationEntity`, dépôts R2DBC, préférences locales), `INotificationRepositoryPort`, `IMessageProviderPort`, propriétés de jetons fournisseurs, dépendances `spring-boot-starter-mail`, `r2dbc` et `tnt-realtime-core` du module, interrupteur `tnt.notify.kernel.enabled`.
- Un envoi refusé ou en échec ne lève jamais d'exception chez l'appelant : il revient `FAILED` (et part sur le topic d'échec). Un envoi simplement mis en file par le Kernel reste `PENDING` (le Kernel fait foi pour le statut final) ; le topic `tnt.notifications.sent` n'est publié que pour un statut `SENT`.

## Vérifié

- Tests unitaires du service (4) et du pont HTTP contre un faux Kernel WireMock (12) : en-têtes tenant/organisation, repli sur les valeurs par défaut, échec explicite sans appel réseau, canaux → `SMS`/`PUSH`/`WEBSOCKET`/`WHATSAPP`/`EMAIL`, `recipientUserId` vs adresse, enregistrement unique des modèles, envoi malgré un échec d'enregistrement, mode sans modèles, lecture/filtrage de l'historique.
- Tests i18n (pattern brut `{{variable}}` conservé, repli sur la langue par défaut).

## Limites connues (à lire avant le déploiement)

- **Aucun appel contre le vrai Kernel** (pas de jeton). Le comportement réel du moteur de modèles du Kernel n'est pas vérifié : syntaxe des variables (les packs utilisent `{{variable}}`), résolution `code + canal + langue`, et priorité entre `templateCode` et `body` (le schéma impose `body`). Si le rendu côté Kernel se révélait différent, passer `TNT_NOTIFY_KERNEL_TEMPLATES_ENABLED=false` renvoie au texte déjà rendu par le Core, sans autre changement.
- **Le Core produit encore le corps de repli** (traduction via `yow-i18n-kernel`) car le Kernel exige `body` ; il disparaîtra quand le Kernel acceptera un envoi par modèle seul.
- Le client Kernel utilise l'identité applicative (`X-Api-Key` / `X-Client-Id`), pas le jeton de l'utilisateur : la boîte de réception propre à l'utilisateur (`GET /api/notifications/inbox`, `POST /inbox/{id}/read`) n'est pas exposée. L'historique du Core est une vue filtrée du journal du tenant (le Kernel n'offre pas de recherche par destinataire ni par id).
- **Organisation par défaut obligatoire** : sans `TNT_NOTIFY_KERNEL_DEFAULT_ORGANIZATION_ID` (et `…TENANT_ID`), tout envoi dont l'appelant ne fournit pas l'organisation échoue explicitement. Elle doit être abonnée au service NOTIFICATION du Kernel.
- **Tables locales conservées** : `tnt_notifications` et `tnt_preference_notifications` ne sont plus lues ni écrites mais **pas supprimées** (suppression irréversible, décision du mainteneur — voir points ouverts).
- **Non traité (hors module notify)** : Go-Freelancer garde sa propre entité `Notification` persistée (+ flux SSE `NotificationStreamController`, Kafka `MatchingNotification`) pour la notification de correspondance annonce/livreur ; elle appelle bien le Kernel pour l'email/push mais duplique une boîte de réception locale. Idem `NotificationType`/`NotificationLivraison`/`NotificationAnnonce` côté gofp et `NotificationType` de `tnt-delivery-core`.

## Demandes à envoyer à l'équipe Kernel

| # | Demande | Pourquoi |
|---|---|---|
| N1 | Documenter le moteur de modèles : syntaxe des variables, résolution `code + canal + locale`, et rendre `body` facultatif quand `templateCode` est fourni | Supprimer le texte de repli rendu par le Core |
| N2 | Recherche des livraisons par destinataire / par id / par statut (ou pagination) | Aujourd'hui le Core liste tout le journal du tenant et filtre en mémoire |
| N3 | Autoriser l'identité applicative (`X-Api-Key`) à écrire dans la boîte de réception d'un utilisateur (`IN_APP`) et confirmer que `WEBSOCKET` atteint l'application mobile/web | Valider le canal in-app de bout en bout |
| N4 | Confirmer la résolution des appareils push à partir de `recipientUserId` (enregistrement des jetons FCM) | Le Core n'envoie plus de jeton d'appareil |

## Points ouverts (décisions du mainteneur)

- Supprimer ou non `tnt_notifications` et `tnt_preference_notifications` (changeset Liquibase irréversible, comme pour le wallet).
- Migrer ou non la boîte de réception propre à Go-Freelancer (entité `Notification`, flux SSE) vers l'inbox du Kernel.
