# Lot C-20 — qui sait qu'un utilisateur est freelancer ?

Preuves brutes de ce dossier (2026-10-02, machine de dev, Kernel de production) :

| Fichier | Contenu |
|---|---|
| `sondes-autorite.txt` | claims du jeton Kernel ; même jeton contre le core local et le core de prod (GET) ; gardes `hasRole` en local |
| `etat-outbox-health-grand-livre.txt` | `tnt_role_sync_outbox`, `/actuator/health`, jauges Prometheus, grand livre |
| `run-c20-compte-B-3e2e9f4-assignRole-500.txt` | run E2E sur un freelancer qui revient : C3/C4/C5/C8 en 500 (`assignRole` non idempotent) |
| `run-c20-compte-B-246d2ce-sortie2.txt` | même run après correctif : C0–C9 verts, verdict Kernel « BLOQUÉ EXTERNE », sortie 2 |

## 1. Réponse : (c) — deux autorités selon le chemin

| Mécanisme | Source de vérité | Où | État mesuré |
|---|---|---|---|
| `@RequirePermission` → `TntPermissionEvaluator` | 1) claims `roles`/`permissions` du JWT, puis 2) `ReactivePermissionResolver` = **`tnt_roles` local** (mode `LOCAL`) | tout le parcours APK : subscribe/respond/elect, livraisons, wallet, paiement, `/me` | **fonctionne** dès que le rôle existe dans le `tnt_roles` du core |
| `@PreAuthorize("hasRole('FREELANCER')")` | **uniquement** le claim `roles` du JWT, émis par le Kernel | `PATCH /api/v1/freelancers/me/location`, `POST/DELETE /me/agencies/{id}`, `POST/DELETE /me/org`, `PATCH /api/v1/deliverers/me/location` (actor-core) | **mort pour tout utilisateur réel**, en local comme en prod |
| Synchro Kernel (`KernelRoleSyncWorker`, `KernelRoleReconciliationJob`) | écrit vers le Kernel, **n'est lue par personne** | outbox | 10 `PROVISION_ROLE` DEAD (403) depuis le 2026-08-05, 5 `ASSIGN_ROLE` DEAD |

Ce qui l'établit :

1. **Le jeton Kernel ne porte aucun rôle.** Payload d'un compte réel : `sub, tid, actor, aud, iss, mfa, adm, exp, iat`
   — ni `roles` ni `permissions`. Aucune autorité `ROLE_*` ne vient donc jamais du Kernel, quel que soit
   l'état des rôles côté Kernel. Conséquence directe : toutes les gardes `hasRole(...)` refusent
   (mesuré : 403 sur `/me/location` et `/me/agencies/…` pour un FREELANCER local).
2. **Même jeton, même Kernel, deux cores** : `GET /billing/wallet/{B}/balance` → **200** sur le core local
   (B a FREELANCER dans `tnt_roles`), **403 `wallet:read` required** sur le core de prod (B n'y a aucun rôle).
   L'état Kernel étant identique dans les deux cas, ce qui décide est la base du core.
3. **Le chemin Kernel du résolveur n'existe pas** : `RemoteReactivePermissionResolver` renvoie un ensemble
   vide (« The Kernel does not expose a permission-resolution REST endpoint yet ») ; le mode par défaut
   est `LOCAL` (`TNT_ROLES_PERMISSION_MODE:LOCAL`, identique au commit de prod `689fabf`). Aucun fichier du
   dépôt ne le surcharge.
4. **Personne ne lit les rôles Kernel** : seuls le worker de synchro et le job de réconciliation appellent
   `/api/roles` ; ni le BFF ni le mobile ne lisent de rôles (les « rôles » du mobile sont des puces d'UI).

Conséquences pour l'APK :

- La propagation Kernel morte n'est **pas** un blocage du parcours APK : c'est une dette de cohérence (cas a
  pour ce chemin).
- Les gardes `hasRole('FREELANCER')` sont mortes **indépendamment** du Kernel. Aucune n'est sur le parcours
  du BFF (il passe par `PATCH /api/freelancers/{id}/location`, module gofp). À corriger côté core
  (passer en `@RequirePermission`) le jour où ces routes servent — pas une demande au Kernel.
- Trouvé en route, **local** : `GET /api/v1/freelancers/me` est 403 pour un FREELANCER (le rôle canonique
  n'a pas `freelancer:read`). Le BFF le contourne en se repliant sur `POST /api/v1/freelancers` — ce
  repli renvoyait 500 pour tout freelancer qui revient, corrigé dans ce lot (`assignRole` idempotent).
- En prod (`689fabf`, lot C-14), aucun freelancer ne peut de toute façon souscrire : C-16 (attribution de
  FREELANCER à la création) et C-18 (repli tenant système du résolveur) n'y sont pas. Le blocage prod
  actuel est **le push**, pas le Kernel.

Ce qui n'est pas observable en lecture seule : la valeur réelle de `TNT_ROLES_PERMISSION_MODE` en prod
(aucun actuator ne l'expose ; ses valeurs `REMOTE`/`HYBRID` renverraient de toute façon un ensemble vide
côté Kernel, ce qui casserait tout `@RequirePermission` pour tous les utilisateurs).

## 2. Demande à l'équipe Kernel (non bloquante pour l'APK, nécessaire pour la cohérence)

> **Objet : TiiBnTick Core — droits du client `tibntick-backend` sur `/api/roles` et `/api/actors`**
>
> Le backend TiiBnTick Core appelle `https://kernel-core.yowyob.com/kernel-api` avec les en-têtes
> `X-Client-Id: tibntick-backend`, `X-Solution-Code: TNT` et sa clé d'API (déjà configurée, non jointe ici).
>
> 1. **Rôles canoniques.** `GET /api/roles` et `POST /api/roles` avec `X-Tenant-Id: 00000000-0000-0000-0000-000000000001`
>    répondent **403 Forbidden** depuis le 2026-08-05 (10 tentatives par rôle, dernière erreur conservée).
>    - Ce tenant `00000000-…-0001` est-il le bon tenant Kernel pour les rôles canoniques d'une solution,
>      ou faut-il les créer dans un autre (lequel) ?
>    - Merci d'accorder à `tibntick-backend` la lecture et la création de rôles dans ce tenant, ou de créer
>      vous-mêmes ces **10 rôles** : `FREELANCER`, `CLIENT`, `TNT_ADMIN`, `PERMANENT_DELIVERER`, `ORG_ADMIN`,
>      `AGENCY_MANAGER`, `BRANCH_MANAGER`, `SUPPORT_AGENT`, `RELAY_OPERATOR`, `AGENCY_HUB_OPERATOR`
>      (définitions code/nom/scope/permissions disponibles dans les payloads `tnt_role_sync_outbox`).
> 2. **Assignations.** Droit de `POST /api/roles/assignments` pour des utilisateurs d'autres tenants
>    (ex. `dbae6615-8f7e-4ef5-9e58-23a6179acf22`) vers ces rôles.
> 3. **Acteurs.** `GET /api/actors/{id}` répond **405 Method Not Allowed**. Quelle est la route de lecture
>    d'un acteur par id ? Et quel identifiant faut-il y passer : le `sub` du jeton
>    (`61aab2d1-…`) ou son claim `actor` (`8b4db5c5-…`) ? Ils diffèrent, et le core utilise le `sub`.
> 4. **Question de conception.** Les jetons d'accès Kernel porteront-ils un jour un claim `roles` (ou
>    `permissions`) ? Une partie des routes du core le suppose (`hasRole`). Si la réponse est non, nous
>    retirons cette dépendance de notre côté.

## 3. C-20.3 — livraison livrée-impayée : constat et proposition

Constat (test `DeliveredButUnpaidTest`, pas en base) : en livraison directe, `DELIVERED` est sauvé **avant**
le paiement, sans transaction commune. Si le paiement échoue (wallet absent, `wallet:read` refusé…), le
statut reste `DELIVERED`, rien ne compense, l'erreur remonte en 5xx. Avant ce lot, aucune alerte au-delà
d'un `ERROR` noyé dans le flux.

Comportement voulu (proposition) : **garder `DELIVERED`, alerter, reprendre** — pas de rollback.
L'OTP du destinataire prouve la remise du colis : annuler le statut mentirait sur le monde physique et
redemanderait une livraison déjà faite.

- **Implémenté (ne change pas la sémantique de la livraison)** : compteur
  `gofp.delivery.payment.failures` + log `UNPAID_DELIVERED delivery=… freelancerProfile=… tarif=…`, et le
  harnais E2E échoue s'il voit ce marqueur.
- **Proposé, non implémenté** :
  1. rendre `splitMissionRevenue` idempotent par `reference_id = MISSION-<deliveryId>` — aujourd'hui un
     re-PATCH `DELIVERED` (que le BFF n'empêche pas) relance le paiement : sûr si le premier n'a rien écrit
     (cas mesuré), **double crédit** si le premier a écrit puis échoué plus loin ;
  2. une reprise automatique : détecter les livraisons `DELIVERED` sans `CREDIT MISSION-<id>` (requête
     dérivable, sans nouvelle table) et rejouer le paiement idempotent, avec la jauge correspondante.
  Les deux touchent le module billing (propriétaire L5) : à arbitrer avant de coder.
