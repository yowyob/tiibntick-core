# Lot C-19 — preuves versionnées

Tous les fichiers de ce dossier sont des sorties brutes (codes couleur retirés), produites
le 2026-10-02 sur la machine de dev (core Docker `tnt-core`, Postgres `tnt-postgres`,
BFF local `:3001`, Kernel de production pour l'auth).

| Fichier | Contenu |
|---|---|
| `latence-avant-freelancer-projete.txt` | binaire `f23fabb` (C-18), compte freelancer déjà projeté |
| `latence-avant-non-freelancer.txt` | binaire `f23fabb`, compte vierge A, pas encore freelancer |
| `latence-apres-freelancer-projete.txt` | binaire `3b41505` (C-19.1), même compte freelancer |
| `latence-apres-non-freelancer.txt` | binaire `3b41505`, même compte non freelancer |
| `run1-compte-vierge-A-3b41505-C5-echec.txt` | run E2E compte vierge A — C5 en HTTP 500 (`WalletNotFoundException`) |
| `sql-compte-A-avant-apres-run1.txt` | état SQL du compte A avant / après le run 1 |
| `run2-compte-vierge-B-ba99add.txt` | run E2E compte vierge B — tout vert, wallet créé pendant le run |
| `sql-compte-B-avant-apres-run2.txt` | état SQL du compte B avant / après le run 2 |

Rejouer : `scripts/e2e/measure-cold-latency.sh` (jeton obtenu **avant** le redémarrage),
`scripts/e2e/sql-state-freelancer.sh <sub>`, `scripts/e2e/e2e-freelancer-courier.sh`.

## 1. Latence d'une route hors parcours freelancer

Route : `GET /api/delivery-needs/user/<sub>` (module Go). « Froid » = première requête
authentifiée après `docker restart tnt-core` + liveness UP, sans aucune requête de chauffe
(le jeton est obtenu avant le redémarrage). 30 requêtes séquentielles à chaud.

| Compte | Mesure | AVANT `f23fabb` | APRÈS `3b41505` |
|---|---|---|---|
| freelancer projeté | froid #1 | 1144 ms (1er essai : 815 ms) | 1342 ms |
| | froid #2 | 52 ms | 43 ms |
| | chaud médiane / p95 / max | **43.0** / 80.9 / 105.3 ms | **18.7** / 28.5 / 61.9 ms |
| | 5 concurrents à froid | 919 – 1066 ms | 1290 – 1333 ms |
| non freelancer | froid #1 | 1173 ms | 1104 ms |
| | chaud médiane / p95 / max | **25.3** / 33.5 / 58.5 ms | **17.6** / 46.0 / 83.5 ms |
| | 5 concurrents à froid | 975 – 1151 ms | 1667 – 1716 ms |

Lecture :
- **À chaud**, le gain est net sur la médiane : un freelancer déjà projeté payait ~18 ms de
  lectures de projection par requête (43 → 19 ms) ; un non-freelancer payait ~8 ms
  (lecture `gofp_users` + `findByActorId`, 25 → 18 ms). Le p95 du non-freelancer est plus
  haut après (46 vs 34 ms) : n = 30, une seule série — c'est du bruit, pas un effet.
- **À froid**, rien ne change : ~0,8 – 1,7 s avant comme après. Le filtre ne tourne plus du
  tout sur cette route après C-19.1, donc ce coût à froid n'est pas le sien (JIT, premières
  connexions R2DBC, JWKS…, non instrumenté). L'écart des rafales concurrentes (≈1,0 s avant,
  ≈1,3 – 1,7 s après) repose sur un seul échantillon par configuration et va dans le sens
  inverse de ce qu'un effet du filtre produirait : non significatif.

### Hypothèse du Fait 2 (stall ~50 s causé par le filtre) — **réfutée par la mesure**

- Sur le binaire C-18 (`f23fabb`, filtre actif devant toutes les routes), 4 redémarrages à
  froid, 12 requêtes froides au total : **jamais plus de 1,2 s**, jamais ~50 s.
- Le scénario exact du stall C-18 (ONBOARD juste après un redémarrage, sans chauffe) a été
  rejoué deux fois **après** C-19.1 : `POST /v1/kyc/freelancer/submit` → 201 en 2 – 3 s
  (run 1 : 02:00:19 → 02:00:21 ; run 2 : 02:08:08 → 02:08:11).
- Limite : le scénario ONBOARD à froid n'a pas été rejoué sur `f23fabb`. Le stall C-18 reste
  non reproduit et non expliqué. Observation non démontrée comme cause : au début de cette
  session, après 5 h d'uptime, le circuit breaker `kernelWebClient` du core était **OPEN**
  (`CallNotPermittedException`, OTP en échec jusqu'au redémarrage) — un état transitoire du
  pont Kernel est un meilleur suspect que le filtre.

## 2. Parcours complet sur compte vierge

Deux comptes Kernel créés pour l'occasion (jamais vus : `SIGNUP_REQUIRED` à l'OTP),
numéros dans la plage +237 600 (non attribuée), e-mail vérifié par le lien Kernel.

- **Compte A** `ab57d86b-…` — run 1 sur `3b41505` : tout vert jusqu'à C4, **C5 en HTTP 500**.
  Cause dans les logs core : `WalletNotFoundException: ab57d86b-…` levée par
  `WalletService.splitMissionRevenue`, qui ne fait qu'un `findByOwnerId` — `getOrCreateWallet`
  n'était **pas** sur ce chemin (seule la branche CASH y passe). Le rapport C-18 avait tort
  d'écrire que le wallet serait créé au premier crédit. Corrigé dans `ba99add`.
- **Compte B** `61aab2d1-…` — run 2 sur `ba99add`, conteneur redémarré sans chauffe : tout vert.

État SQL du compte B (extrait de `sql-compte-B-avant-apres-run2.txt`) :

| | avant | après |
|---|---|---|
| `tnt_actor.freelancer_profiles` | 0 | 1 (`47fa0b91-…`) |
| `tnt_user_role_assignments` | 0 | 1 (FREELANCER, rôle du tenant système) |
| `gofp_users` / `gofp_freelancers` / `tnt_delivery_persons` | 0 / 0 / 0 | 1 / 1 / 1 |
| trois `id` | — | `47fa0b91-91a2-4f0d-b365-fd6eb9009a95` ×4 (profil, gofp id, core_freelancer_id, delivery person) |
| `billing.wallet_wallets` (compte) | **0** | **1** — `34728a32-…`, créé 02:08:37, solde 2850.00 |
| `billing.wallet_wallets` (table entière) | 1 | 2 |
| `billing.wallet_transactions` (compte) | 0 | 1 CREDIT 2850.00 (= 3000 × 0,95) |
| `gofp_freelancers.remaining_deliveries` | — | **100** après une livraison complète (non décrémenté) |
| `tnt_delivery_persons.remaining_deliveries` | — | 100 |

Le wallet préexistant `db432caa-…` (compte historique) n'a pas été touché : 22800.00,
8 transactions.
