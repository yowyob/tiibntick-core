# Lot C-4 — Débloquer SETUP, C6, C3, C8 et rendre le harnais honnête

## 0. RÈGLE DE CONDUITE — AUTONOMIE TOTALE

Tu procèdes de bout en bout, **sans jamais demander de confirmation**.
Interdits : « do you want to proceed ? », « should I continue ? », toute pause de
validation. Tu tranches seul, tu consignes dans **« Décisions prises sans arbitrage »**,
et tu ne rends la parole **qu'une seule fois**, à la fin, avec ton rapport.
Un blocage matériel infranchissable : tu écris ce que tu as tenté, pas une question.

## 1. DÉPÔTS ET BRANCHES

- **BFF** : `/home/jtk/projets/tiibntick-bff` — branche `fix/created-at-numeric-sort`
  existe déjà (commit `bd1ca37`). Tu la reprends, tu ne la recrées pas.
- **Core** : `/home/jtk/projets/tiibntick-core` — branche `lotC4-rbac-role-freelancer`,
  créée depuis **`lotCter-coupure-bypass`**.

```bash
cd /home/jtk/projets/tiibntick-core
git checkout -b lotC4-rbac-role-freelancer lotCter-coupure-bypass
wc -l scripts/e2e/e2e-freelancer-courier.sh   # DOIT afficher 902
```

902 = lot C-ter. 880 = C-bis. 767 = C. Autre chiffre → mauvaise base, arrête-toi.

## 2. ÉTAT ÉTABLI (ne le re-démontre pas)

- Bypass coupé : `docker inspect tnt-core` → `TNT_AUTH_ALLOW_ANONYMOUS=false`,
  sondes sans jeton et avec jeton bidon → **401/401**.
- Profil du vrai utilisateur créé par l'API : `aa25f7f2-f984-4ec4-92ad-9022668ecf60`,
  `actor_id=f69444e3…` (= le `sub` du JWT), `tenant_id=dbae6615…`.
- `e2e-presence-loop.sh` 21/21 vert, `e2e-freelancer-flow.sh` 24/24 vert.
- **Deux systèmes d'autorisation coexistent** :
  - `@PreAuthorize("hasRole(...)")` → autorités **issues des seuls claims JWT**.
    Le jeton kernel ne porte ni `roles` ni `permissions` → 403 systématique.
  - `@RequirePermission(resource, action)` → 81 fichiers, résolution **en base** par
    `LocalReactivePermissionResolver` (l.61-72 : `assignmentRepository
    .findByTenantIdAndUserId` puis `roleRepository.findById`). Donc **une assignation
    RBAC en base suffit** — aucun changement kernel nécessaire.
- Bug #1 corrigé côté BFF (`toIsoString()`), 5 tests verts, **mais le BFF n'a jamais
  été redémarré avec ce correctif** : le `FAIL` de C2 du lot C-ter décrit du code qui
  n'a pas tourné.

## 3. TÂCHE 1 — Redéployer le BFF et mesurer C2 (à faire EN PREMIER)

Rien d'autre n'a de sens avant ça.

1. Arrête le BFF, relance-le en `CORE_ADAPTER=real` sur la branche
   `fix/created-at-numeric-sort`.
2. Vérifie que le processus tourne bien sur le code corrigé (présence de `toIsoString`
   dans le fichier chargé, ou empreinte git du répertoire de travail).
3. Relance **uniquement** `e2e-freelancer-courier.sh` et note le résultat de C2 :
   HTTP + corps. C'est la première mesure réelle du correctif.

Si C2 est encore rouge, tu diagnostiques avant de passer à la suite, et tu donnes la
cause prouvée (corps de réponse + ligne de log), pas une hypothèse.

## 4. TÂCHE 2 — Seed RBAC pour `announcement:respond`, `announcement:elect`, `wallet:read`

`e2e-freelancer-flow.sh` fait déjà exactement ça pour `gofp-admin:manage` : il crée un
rôle, l'assigne à l'utilisateur, et `LocalReactivePermissionResolver` le lit. **Copie ce
mécanisme**, ne l'invente pas — va lire comment ce script s'y prend et reproduis le même
schéma dans le SEED de `e2e-freelancer-courier.sh`.

Permissions à accorder à `f69444e3…` dans le tenant `dbae6615…` :
`announcement:respond`, `announcement:elect`, `wallet:read`.

Contraintes :
- Le seed doit être **idempotent** : deux exécutions de suite ne doivent pas échouer.
- Le teardown doit retirer ce qu'il a créé, comme le fait le flow script.
- **Read-back obligatoire** : après création, relis les assignations en base et
  échoue avec `exit 1` si elles ne sont pas là. Ne déclare jamais un seed réussi sans
  relecture — c'est l'omission qui nous a coûté le lot C entier.
- Ne touche à **aucune** annotation `@RequirePermission`.

Puis relance le script et note SETUP subscribe, SETUP assign et C6 : HTTP + corps.

## 5. TÂCHE 3 — `/me` : migrer `hasRole('FREELANCER')` vers `@RequirePermission`

`GET /api/v1/freelancers/me` porte `@PreAuthorize("hasRole('FREELANCER')")`
(`FreelancerController` l.135). C'est le seul point réellement bloqué par le fossé de
claims, et il fait tomber C3 et C8 en cascade côté BFF
(`resolveFreelancerId` → 403 → `null` → `not_found` → 404).

On ne peut pas attendre que le kernel émette un claim `roles` : ce n'est pas notre dépôt.
Donc on aligne cet endpoint sur les 81 autres.

1. Remplace l'annotation par un `@RequirePermission` cohérent avec la nomenclature déjà
   employée ailleurs dans le dépôt. **Va lire les valeurs `resource`/`action` existantes
   avant de choisir** — n'invente pas un couple qui n'existe nulle part.
2. Accorde la permission correspondante dans le seed de la tâche 2.
3. Vérifie que la garde métier reste intacte : `/me` doit continuer à ne renvoyer que le
   profil de l'appelant, jamais celui d'un autre. Si la migration ouvre un accès plus
   large, tu ajoutes le contrôle applicatif et tu l'écris dans le rapport.
4. **Test négatif obligatoire** : un appelant **sans** la permission doit recevoir 403.
   Écris-le, fais-le tourner, montre le résultat.
5. Compile et fais passer les tests du module concerné.

Si tu conclus que cette migration est une mauvaise idée, **tu ne l'implémentes pas** :
tu l'écris en recommandation argumentée dans le rapport, avec ce que tu proposes à la
place. C'est un arbitrage d'architecture, il a le droit d'être refusé — mais il doit
être tranché, pas contourné.

## 6. TÂCHE 4 — Rendre le harnais honnête

Deux corrections, petites et non négociables.

**(a)** `TENANT_SCOPING_PROVEN="true"` est posé l.322 uniquement parce que les sondes a
et b valent 401. Ça ne prouve **pas** le cloisonnement, seulement que la chaîne JWT est
active. Deux variables distinctes :
- `AUTH_CHAIN_ACTIVE` — vrai dès que 401/401. C'est ce qui est mesuré aujourd'hui.
- `TENANT_SCOPING_PROVEN` — vrai **seulement** après un test négatif réussi.

**(b)** Le test négatif, dans le SEED : sème une annonce `PUBLISHED` dans le tenant
`43427172-b6ee-4dbf-9148-96682702ffc9` et assure-toi que C2 **ne la renvoie pas**. Si
elle apparaît, c'est un `fail`. Nettoie-la au teardown.

C'est cette assertion, et elle seule, qui autorise `TENANT_SCOPING_PROVEN=true` et donc
le `✅ PASS` franc de C6.

## 7. INTERDICTIONS

- Ne remets **jamais** `TNT_AUTH_ALLOW_ANONYMOUS=true`, même temporairement.
- N'ajoute rien au `devAuthFilter()`.
- Ne fabrique pas un jeton maison avec un claim `roles` ajouté à la main.
- Aucun `INSERT` psql pour créer un **profil freelancer** (le RBAC, lui, passe par le
  même mécanisme que le flow script : c'est autorisé et c'est le sujet de la tâche 2).
- Aucun `|| true` sur une assertion. Uniquement sur les `DELETE` de nettoyage.
- Ne traite pas le Bug #2 (`markNotNew()`), ni C5 (`PREVIEW_ONLY`) : lots séparés.
- Le tableau final ne contient ni « attendu », ni « probable », ni « devrait ». Pour un
  test négatif réussi, écris « refus correct ».

## 8. RAPPORT FINAL — une seule prise de parole

1. C2 après redéploiement du BFF : HTTP + corps.
2. Seed RBAC : ce qui a été créé, la relecture en base, idempotence vérifiée oui/non.
3. SETUP subscribe / SETUP assign / C6 : HTTP + corps pour chacun.
4. `/me` : annotation retenue et **pourquoi** ce couple `resource`/`action`, résultat du
   test négatif, C3 et C8 après migration. Ou le refus argumenté.
5. `AUTH_CHAIN_ACTIVE` / `TENANT_SCOPING_PROVEN` : valeurs finales, et résultat de
   l'assertion de cloisonnement.
6. Tableau complet des 3 scripts, étape par étape.
7. **Décisions prises sans arbitrage.**
8. Ce qui reste bloqué, et si les changements de ce lot sont **déployables en
   production** tels quels — c'est la question qui décide de la suite.
9. SHA des commits, par dépôt.
