# Audit n°4 — Module `yow-i18n-kernel` (i18n & multidevise)

- **Projet** : TiiBnTick Core (`tiibntick-core`)
- **Module audité** : `foundation/yow-i18n-kernel` (L0 Foundation)
- **Date** : 2026-07-16
- **Auditeur** : Platform Engineering — Audit interne
- **Périmètre** : inventaire du module, intégration réelle dans le monorepo, couverture i18n effective du projet, gestion multidevise, verdict de préparation « mondial ».

---

## 1. Résumé exécutif

`yow-i18n-kernel` est un petit module hexagonal propre et testé (15 fichiers Java main, 3 classes de test, 14/14 tests verts) qui fournit deux capacités : traduction de clés de messages depuis des packs JSON (`/i18n/messages_{tag}.json`, 5 locales, 27 clés chacune) et formatage de prix localisé (6 devises, règle « XAF/XOF sans décimales » correcte).

**Mais son intégration réelle est quasi nulle et partiellement cassée.** Un seul fichier dans tout le dépôt importe le package (`NotifyCoreAutoConfiguration` de `tnt-notify-core`), et la chaîne de bout en bout est défaillante : tous les producteurs de notifications passent la langue `"fr"` (au lieu de `"fr_CM"`) et des clés de template (`notify.freelancer_org.*`, `incident.notification.*`) **absentes des packs JSON**. Résultat : chaque notification FreelancerOrg/incident envoyée par SMS/Email/Push contient littéralement le texte `⚠ Missing translation: <clé>`. Les catalogues FR/EN de secours (`FreelancerOrgNotificationTemplates`) sont du code mort jamais branché.

Le reste du projet ignore l'i18n : ~200 messages d'exception en dur (anglais) dont 94 dans les 4 modules `coreBackend/` (633 fichiers, **zéro** usage du kernel — voir Addendum §10), messages de validation Jakarta en dur, sujet d'email en dur, gabarits PDF de facture 100 % français, doc Swagger gofp en français en dur, aucun traitement de l'en-tête `Accept-Language`, préférence de langue utilisateur stockée mais jamais consultée à l'envoi. Les 8 notifications e-commerce de `tnt-market-back-core` sont cassées de la même façon (clés `ORDER_PAID`… absentes des packs, langue `"fr"` invalide).

Côté multidevise : **6 classes `Money` dupliquées** avec des règles d'arrondi divergentes, `'FCFA'` (non-ISO) utilisé comme code devise en base et dans le code, aucune conversion ni taux de change, et trois listes de devises/locales « supportées » incohérentes entre elles (enum du kernel, `SolutionContext`, colonnes SQL).

**Verdict : NON.** Le projet n'est prêt ni pour un support multilingue ni pour un support multidevise à l'échelle mondiale. Il est aujourd'hui monolingue de fait (français en dur + anglais technique) et monodevise de fait (XAF par défaut partout). Le kernel i18n est une bonne fondation embryonnaire, mais débranchée.

---

## 2. État actuel — Inventaire du module

### 2.1 Structure (18 fichiers : 15 main + 3 test)

Package racine : `com.yowyob.kernel.i18n` (confirmé — conforme à la convention CLAUDE.md pour les modules kernel L0).

```
adapter/currency/JavaCurrencyFormatterAdapter.java   — formatage DecimalFormat par locale Java
adapter/json/JsonLocalePackAdapter.java              — chargement des packs JSON en mémoire
application/port/in/TranslateMessageUseCase.java      — port entrant : traduction
application/port/in/PriceFormatterUseCase.java        — port entrant : formatage prix
application/port/out/MessageTranslationPort.java      — port sortant : source de messages
application/port/out/PriceFormatterPort.java          — port sortant : moteur de formatage
application/service/TranslationService.java           — impl. traduction + interpolation
application/service/PriceFormatterService.java        — impl. formatage
config/I18nKernelProperties.java                      — @ConfigurationProperties "yowyob.i18n"
config/YowI18nKernelAutoConfiguration.java            — auto-config Spring Boot (4 beans @ConditionalOnMissingBean)
domain/enums/SupportedLanguage.java                   — FR_CM, EN_CM, PIDGIN_CM, EN_NG, FR_FR, EN_US
domain/enums/SupportedCurrency.java                   — XAF, XOF, NGN, KES, USD, EUR
domain/vo/LocaleConfig.java                           — (langue, devise)
domain/vo/LocalizedMessage.java                       — message + interpolation {{var}}
domain/vo/LocalizedPrice.java                         — montant + devise + représentation
```

### 2.2 API offerte

| Capacité | Présente ? | Détail |
|---|---|---|
| Message source (traduction par clé) | ✅ | `TranslateMessageUseCase.translate(key, language[, params])` → `Optional<String>` |
| Interpolation de variables | ✅ | Syntaxe `{{variable}}` (`LocalizedMessage.interpolate`) |
| Formatage de prix par locale | ✅ | `PriceFormatterUseCase.formatForLocale(amount, currency, language)` |
| Arrondi par devise | ✅ (dans le module) | XAF/XOF → échelle 0, autres → 2 (`JavaCurrencyFormatterAdapter:52-67`) |
| **Résolution de locale (Accept-Language, préférence utilisateur)** | ❌ | Aucune classe. L'appelant doit fournir la langue en `String` brut |
| **Fallback de locale (fr_CM → fr_FR → défaut)** | ❌ | Commenté mais non implémenté (`JsonLocalePackAdapter.java:68-69`) |
| **Conversion de devises / taux de change** | ❌ | Aucune |
| **Formats de dates/nombres localisés (hors prix)** | ❌ | Aucun |
| **Pluralisation, genre, ICU MessageFormat** | ❌ | Simple `String.replace` |

### 2.3 Configuration

- `application.yaml` du module (embarqué dans le JAR de la **librairie** — anti-pattern, voir P-13) : locales `fr_CM, en_CM, pidgin_CM, en_NG, fr_FR`, défaut `FR_CM`.
- `I18nKernelProperties.java:21` : défaut programmatique = 3 locales camerounaises seulement.
- Auto-configuration déclarée dans `META-INF/spring/...AutoConfiguration.imports` — chargée automatiquement par `tnt-bootstrap` ; le bean `messageTranslationPort` exige un `ObjectMapper` qualifié `tntObjectMapper` (fourni par bootstrap).

### 2.4 Bundles de messages

- **Aucun `messages*.properties` dans tout le dépôt** (vérifié par `find` global) — pas de `MessageSource` Spring nulle part.
- 5 packs JSON dans `foundation/yow-i18n-kernel/src/main/resources/i18n/` : `fr_CM`, `en_CM`, `pidgin_CM`, `en_NG`, `fr_FR` — **27 clés chacun**, cohérents entre eux (familles `notification.*`, `error.*`, `billing.*`, `status.*`, `blockchain.*`, `account.*`).
- **`messages_en_US.json` absent** alors que `SupportedLanguage.EN_US` existe (`SupportedLanguage.java:516`) — locale déclarée mais non traduisible.

### 2.5 Tests

3 classes, 14 tests, tous verts (surefire) : `JsonLocalePackAdapterTest` (6), `PriceFormatterServiceTest` (4), `TranslationServiceTest` (4). Couvrent le happy path et les cas vides ; ne couvrent ni `JavaCurrencyFormatterAdapter` directement, ni `LocaleConfig`, ni l'auto-configuration.

---

## 3. Cartographie d'utilisation réelle

Grep exhaustif de `import com.yowyob.kernel.i18n` hors du module lui-même :

| Consommateur | Type d'usage | Constat |
|---|---|---|
| `logistics/tnt-notify-core` — `NotifyCoreAutoConfiguration.java:3,69-77` | **Seul import du dépôt.** Pont `TranslateMessageUseCase` → `ITranslationPort` (anti-corruption) | Fonctionnel, mais fallback dangereux : `defaultIfEmpty("⚠ Missing translation: " + key)` **envoyé tel quel à l'utilisateur final** |
| `logistics/tnt-delivery-core/pom.xml:55` | Dépendance Maven **sans aucun import** | Dépendance morte |
| `billing/tnt-billing-dsl/pom.xml:50` | Dépendance Maven **sans aucun import** | Dépendance morte |
| `coreBackend/tnt-go-freelancer-point-back-core/pom.xml:39` | Dépendance Maven **sans aucun import** | Dépendance morte |
| `tnt-bootstrap` | Dépendance transitive/agrégation, charge l'auto-config | Beans créés mais consommés uniquement via tnt-notify-core |
| Tous les autres modules (~30) | **Aucun usage** | `PriceFormatterUseCase` n'a **zéro** consommateur dans tout le dépôt, alors que sa Javadoc (`PriceFormatterUseCase.java:161`) affirme « Used by billing modules (tnt-billing-cost, tnt-billing-invoice) » — faux |

**Chaîne réelle de traduction (unique) :** producteurs (`FreelancerOrgKafkaEventConsumer`, `IncidentNotificationPortAdapter`, `NotificationController`, `MarketNotificationAdapter`) → `NotificationModel(templateKey, targetLanguage, params)` → `NotificationService.send` → `ITranslationPort` → `TranslationService` → `JsonLocalePackAdapter`. Cette chaîne est cassée en amont (langues et clés invalides, voir P-1/P-2).

---

## 4. Couverture i18n réelle du projet (par module, échantillonnée)

| Module | Messages d'exception | Réponses API / DTO | Validation | Templates / sorties | Verdict i18n |
|---|---|---|---|---|---|
| `yow-i18n-kernel` | n/a | n/a | n/a | 5 packs JSON × 27 clés | ✅ seul îlot i18n |
| `tnt-notify-core` | Anglais en dur | Fallback `⚠ Missing translation` exposé | — | Sujet email en dur `"TiiBnTick — Notification"` (`EmailNotificationAdapter.java:59`) ; catalogues FR/EN morts (`FreelancerOrgNotificationTemplates.java:47-98`) | ❌ cassé |
| `tnt-delivery-core` | 15 `throw` anglais en dur (ex. `Parcel.java:33,48`, `DeliveryAnnouncement.java:217-229`) | Anglais en dur | — | — | ❌ |
| `tnt-dispute-core` | 8 `throw` anglais (ex. `OpenDisputeAgainstFreelancerOrgCommand.java:57`) | Anglais | — | — | ❌ |
| `tnt-billing-wallet` | 25 `throw` anglais (ex. `PaymentIntent.java:47,61,83`) | Anglais | — | — | ❌ |
| `tnt-billing-invoice` | 6 `throw` anglais | Anglais | — | PDF via tnt-media-core (voir ci-dessous) | ❌ |
| `tnt-billing-dsl` | Anglais | Anglais | `@NotBlank(message = "Rule name is required")` etc. en dur (`DslRuleRequest.java:16-35`) | — | ❌ |
| `tnt-billing-templates` | Anglais | Titres ProblemDetail anglais en dur (`BillingTemplatesExceptionHandler.java:38,51,64`) | — | Libellés de paramètres **bilingues FR/EN en dur dans le Java** (`TemplateCatalogSeeder.java:119-174`) — 3e système i18n parallèle | ❌ |
| `tnt-trust-core` | Anglais | Anglais | `@NotBlank(message = "orgId is required")` en dur (`TrustApiController.java:306-309`) | — | ❌ |
| `tnt-media-core` | Anglais | — | — | **8 gabarits JRXML monolingues français** (`invoice.jrxml` : « FACTURE », « FACTURÉ À », « TVA », « TOTAL TTC ») ; `PDFRenderRequest.java:24` locale par défaut `"fr_CM"` ; `REPORT_LOCALE` n'affecte que les formats Jasper, pas les libellés | ❌ FR-only |
| `tnt-organization-core` | 20 `throw` anglais | Anglais | — | — | ❌ |
| Handlers d'exceptions (10 trouvés : bootstrap, delivery, geo, auth, actor, dispute, resource, wallet, invoice, templates) | — | **Aucun** n'utilise le kernel i18n ni un `MessageSource` ; titres/détails anglais en dur | — | — | ❌ |
| `coreBackend/tnt-agency-back-core` (223 fichiers) | 38 `throw` anglais (ex. `AgencyMission.java:112-159`) | Anglais | — | `XAF` en dur dans les clients sortants (`DeliveryMissionClient.java:100,109`, `MissionService.java:693`) | ❌ |
| `coreBackend/tnt-market-back-core` (170 fichiers) | 31 `throw` anglais | Anglais | — | 8 clés de notif e-commerce absentes des packs + langue `"fr"` (`MarketNotificationAdapter.java:39-84`) — même bug que P-1/P-2 | ❌ cassé |
| `coreBackend/tnt-link-back-core` (120 fichiers) | 18 `throw` anglais | Anglais | — | — | ❌ |
| `coreBackend/tnt-go-freelancer-point-back-core` (120 fichiers) | 7 `throw` anglais | Anglais | — | Doc Swagger **en français en dur** (`DeliveryCoreController.java:24-74`, `RelayHubSubscriptionCoreController.java:65-77`) ; dépendance `yow-i18n-kernel` déclarée mais 0 import | ❌ |

**Locale du client :** grep global de `Accept-Language`, `LocaleResolver`, `LocaleContextResolver`, `LocaleContextHolder` → **0 résultat**. L'en-tête HTTP standard n'est traité nulle part. La préférence utilisateur existe (`NotificationPreference.preferredLanguage`, `ManagePreferencesService.java:23` défaut `fr_CM`) mais `NotificationService.send` (`NotificationService.java:43-59`) ne la consulte jamais : la langue vient du producteur, en dur.

---

## 5. Multidevise — état réel

### 5.1 Types monétaires : 6 classes `Money` dupliquées

| Classe | Arrondi | Devise |
|---|---|---|
| `foundation/tnt-common-core/.../common/vo/Money.java` (canonique) | échelle = fraction ISO de la devise (XAF→0), HALF_UP | `java.util.Currency` (ISO validé) |
| `billing/tnt-billing-dsl/.../domain/model/Money.java` | idem (XAF→0) | `java.util.Currency` |
| `billing/tnt-billing-invoice/.../domain/model/Money.java:25` | **échelle 2 forcée pour toutes devises, y compris XAF** | `String` libre (non validé ISO) |
| `billing/tnt-billing-wallet/.../domain/model/Money.java` | (variante locale) | — |
| `billing/tnt-billing-cost/.../domain/model/Money.java` | (variante locale) | — |
| `coreBackend/tnt-market-back-core/.../domain/model/Money.java` | (variante locale) | — |

Conséquence concrète : une facture XAF est représentée `15000.00` côté invoice mais `15000` côté DSL/pricing — divergence d'arrondi et de sérialisation entre modules qui échangent des montants par Kafka/REST.

### 5.2 Colonnes devise en base

- ~20 tables ont une colonne devise, quasi toutes `NOT NULL DEFAULT 'XAF'` (ex. `billing/tnt-billing-invoice/.../001_create_billing_invoice_tables.sql:32-67`, `business/tnt-sales-core/.../002-create-orders-tables.sql:14`). Le multidevise est donc *stockable*, mais le défaut systémique est XAF.
- **Incohérence de type** : `CHAR(3)`, `VARCHAR(3)`, `VARCHAR(5)`, `VARCHAR(10)` selon les modules.
- **`DEFAULT 'FCFA'`** (symbole, pas un code ISO 4217) dans `coreBackend/tnt-go-freelancer-point-back-core/.../011_create_delivery_person_pricing.sql:14` et `012_create_logistics_pricing.sql:16`, repris dans le code (`CommissionPolicy.java:21`, `PricingCoreService.java:41,67,119,129`). Tout passage de `'FCFA'` à `Currency.getInstance()` (Money canonique) lèverait `IllegalArgumentException`.

### 5.3 Conversion, taux, formatage

- **Aucun taux de change, aucune conversion** : grep `exchangeRate|ExchangeRate|conversion` → 0 résultat monétaire. `Money.add/subtract` jettent en cas de devises différentes — correct mais signifie qu'aucune agrégation multi-devise (reporting multi-pays) n'est possible.
- Formatage localisé : seul `JavaCurrencyFormatterAdapter` le fait — et il n'a **aucun consommateur** (§3). Les PDF concatènent `montant + " " + code` sans format locale (`invoice.jrxml`).
- Listes « supportées » incohérentes : enum kernel = {XAF, XOF, NGN, KES, USD, EUR} ; `ApplicationProfileConfig.java:66` = idem **+ GHS** ; locales `ApplicationProfileConfig.java:65` = `pcm_NG, fr_SN, en_KE` qui n'existent pas dans `SupportedLanguage` (et `pcm_NG` ≠ `pidgin_CM`).

---

## 6. Points positifs

1. **Architecture hexagonale exemplaire** du module : ports in/out, services purs sans annotation Spring, adaptateurs substituables, auto-configuration `@ConditionalOnMissingBean` — conforme aux conventions du repo.
2. **Règle ISO 4217 XAF/XOF sans décimales correctement implémentée** dans le module (`JavaCurrencyFormatterAdapter.java:52-67`) et dans les `Money` de common/dsl.
3. **Pack pidgin camerounais** : rare et pertinent pour le marché cible ; les 5 packs sont complets et synchronisés (27 clés partout).
4. **Tests unitaires réels et verts** (14/14), y compris chargement effectif des JSON du classpath.
5. **Pont anti-corruption dans tnt-notify-core** (`ITranslationPort`) : le pattern d'intégration est le bon, seul le contenu qui y transite est cassé.
6. Le modèle `NotificationPreference.preferredLanguage` et le `SolutionContext` montrent une **intention** multilingue/multidevise dès la conception.

---

## 7. Problèmes détectés — tableau de synthèse

| ID | Problème | Localisation (preuve) | Criticité |
|---|---|---|---|
| P-1 | Clés de notification inexistantes dans les packs → l'utilisateur reçoit `⚠ Missing translation: notify.freelancer_org.verified` par SMS/Email/Push | `FreelancerOrgKafkaEventConsumer.java:98-240` (clés `notify.*`), `IncidentNotificationPortAdapter.java:224` (clés `incident.notification.*`), fallback `NotifyCoreAutoConfiguration.java:74` ; packs JSON = familles `notification.*`/`error.*` uniquement | **Critique** |
| P-2 | Langue `"fr"` passée partout alors que les packs sont indexés `fr_CM`/`fr_FR` → lookup toujours vide même pour les clés existantes | `FreelancerOrgKafkaEventConsumer.java:99` et 5 autres occurrences, `IncidentNotificationPortAdapter.java:225`, `NotificationController.java:86` (défaut `"fr"`), `MarketNotificationAdapter.java:84` | **Critique** |
| P-3 | `FreelancerOrgNotificationTemplates.DEFAULT_FR_TEMPLATES`/`DEFAULT_EN_TEMPLATES` : catalogues complets jamais référencés (grep : 0 usage) + syntaxe `{var}` incompatible avec l'interpolateur `{{var}}` du kernel | `FreelancerOrgNotificationTemplates.java:47-98` | **Élevé** |
| P-4 | Aucune résolution de locale : `Accept-Language` jamais lu, `preferredLanguage` stocké mais jamais consulté à l'envoi | grep global = 0 ; `NotificationService.java:43-59` ; `ManagePreferencesService.java:23` | **Élevé** |
| P-5 | Fallback de locale non implémenté (fr_CM→fr_FR) — commentaire TODO déguisé | `JsonLocalePackAdapter.java:68-69` | Moyen |
| P-6 | 6 classes `Money` dupliquées, arrondis divergents (invoice force échelle 2 même en XAF) | `tnt-billing-invoice/.../Money.java:25` vs `tnt-common-core/.../Money.java:37-40` vs `tnt-billing-dsl/.../Money.java:33` (+ wallet, cost, market-back) | **Élevé** |
| P-7 | `'FCFA'` utilisé comme code devise (non ISO 4217) en base et en dur dans le code — incompatible avec `Currency.getInstance` | `011_create_delivery_person_pricing.sql:14`, `012_create_logistics_pricing.sql:16`, `CommissionPolicy.java:21`, `PricingCoreService.java:41,67,119,129` | **Élevé** |
| P-8 | Aucun mécanisme de conversion/taux de change ; agrégations reporting mono-devise par construction | grep global `ExchangeRate` = 0 ; `ReportingService.java:266-343` (une seule `cur` par requête) | Moyen (bloquant pour « mondial ») |
| P-9 | ~100+ messages d'exception et titres ProblemDetail en dur (anglais), 10 exception handlers sans aucune traduction ; messages de validation Jakarta en dur | échantillons : `Parcel.java:33`, `PaymentIntent.java:47`, `DslRuleRequest.java:16-35`, `TrustApiController.java:306-309`, `BillingTemplatesExceptionHandler.java:38,51,64` | **Élevé** |
| P-10 | Gabarits PDF (facture, reçus, manifeste…) monolingues français ; « FACTURE », « TVA », « TOTAL TTC » en dur ; montants non formatés par locale | `logistics/tnt-media-core/src/main/resources/templates/invoice.jrxml` (+7 autres JRXML), `PDFRenderRequest.java:24` | **Élevé** |
| P-11 | Sujet d'email unique en dur, non localisé | `EmailNotificationAdapter.java:59` | Moyen |
| P-12 | `LocaleConfig.toLocaleTag()` bogué : `replace("_","_")` est un no-op et `PIDGIN_CM.name().toLowerCase()` = `pidgin_cm` ≠ tag de fichier `pidgin_CM` → lookup cassé si utilisé ; devrait retourner `language.getTag()` | `LocaleConfig.java:46-48` | Moyen (latent — classe inutilisée) |
| P-13 | `application.yaml` embarqué dans le JAR d'une librairie : risque de masquer/entrer en collision avec la config de l'application hôte | `foundation/yow-i18n-kernel/src/main/resources/application.yaml` | Moyen |
| P-14 | `SupportedLanguage.EN_US` déclaré sans pack `messages_en_US.json` ; `messages_en_NG`/`fr_FR` non chargés par le défaut programmatique (3 locales) | `SupportedLanguage.java:516`, `I18nKernelProperties.java:21` | Faible |
| P-15 | Référentiels incohérents : `SolutionContext` annonce `pcm_NG, fr_SN, en_KE` (inexistants dans l'enum) et `GHS` (absent de `SupportedCurrency`) | `ApplicationProfileConfig.java:65-66` | Moyen |
| P-16 | `LocalizedPrice` interdit les montants négatifs (remboursements/avoirs impossibles) et `LocalizedPrice.of()` force l'échelle 2 même pour XAF, contredisant l'adaptateur | `LocalizedPrice.java:25-26,39` | Moyen |
| P-17 | Dépendances Maven mortes vers `yow-i18n-kernel` (aucun import) | `tnt-delivery-core/pom.xml:55`, `tnt-billing-dsl/pom.xml:50`, `tnt-go-freelancer-point-back-core/pom.xml:39` | Faible |
| P-18 | Javadoc mensongère : `PriceFormatterUseCase` « used by tnt-billing-cost, tnt-billing-invoice » — 0 consommateur réel | `PriceFormatterUseCase.java:160-165` | Faible |
| P-19 | Colonnes devise hétérogènes (`CHAR(3)` à `VARCHAR(10)`), défaut `XAF` codé en dur dans ~20 migrations | ex. `001_create_billing_invoice_tables.sql:32-67`, `002-create-orders-tables.sql:14`, `v0002_create_tnt_agency.sql:30` | Faible |
| P-20 | `TranslationService.translationPort` non `final` (mutabilité inutile d'un service « pur ») | `TranslationService.java:340` (champ ligne 18 du fichier) | Faible |
| P-21 | Notifications e-commerce market-back : 8 clés (`CLIENT_QUOTE_RECEIVED`, `ORDER_PAID`, `LISTING_APPROVED`…) absentes des packs JSON + langue `"fr"` invalide → tout client/marchand reçoit `⚠ Missing translation: …` | `MarketNotificationAdapter.java:39-84` | **Critique** |
| P-22 | Variante market-back de `Money` (une des 6 de P-6) : `long` « unités mineures » avec commentaire erroné (« centimes for XAF » — XAF n'a pas de centimes, ISO 4217) → ambiguïté francs/centimes à la frontière `BigDecimal` (`MarketServiceOfferPersistenceAdapter.java:67`) ; `formatted()` utilise la locale JVM par défaut | `marketback/domain/model/Money.java:7,11,58-60` | **Élevé** |
| P-23 | Devise figée dans les contrats d'API e-commerce : 117 identifiants suffixés `Xaf` (`amountXaf`, `minPriceXaf`, `paidAmountXaf`…) dans les ports/commandes/DTO de market-back ; `"XAF"` en dur dans les clients agency | `IMarketNotificationPort.java:15`, `MarketOrderApplicationService.java:232`, `DeliveryMissionClient.java:100,109`, `MissionService.java:693` | **Élevé** |
| P-24 | coreBackend : 94 messages d'exception en dur (agency 38, market 31, link 18, gofp 7) et documentation OpenAPI/Swagger en français en dur (gofp) — troisième langue de code mélangée (code EN, Swagger FR, notifs FR) | `AgencyMission.java:112-159`, `DeliveryCoreController.java:24-74` | Moyen |

---

## 8. Détails et preuves des problèmes majeurs

### P-1 / P-2 — La seule chaîne i18n branchée produit des messages cassés (Critique)

Le pont notify (`NotifyCoreAutoConfiguration.java:69-77`) :

```java
return model -> Mono.justOrEmpty(
        translateMessageUseCase.translate(model.templateKey(), model.targetLanguage(), model.parameters()))
        .defaultIfEmpty("⚠ Missing translation: " + model.templateKey());
```

Or **tous** les producteurs construisent `new NotificationModel(templateKey, "fr", …)` (6 occurrences dans `FreelancerOrgKafkaEventConsumer.java`, `IncidentNotificationPortAdapter.java:225`, défaut du contrôleur `NotificationController.java:86`) :
1. `"fr"` n'est pas une clé de pack (packs indexés par `fr_CM`, `fr_FR`…) → `dictionary == null` → `Optional.empty()`.
2. Même avec la bonne locale, `notify.freelancer_org.kyc_rejected` ou `incident.notification.*` n'existent dans **aucun** JSON (les packs ne contiennent que `notification.*`, `error.*`, `billing.*`, `status.*`, `blockchain.*`, `account.*`).

**Impact** : chaque notification KYC/suspension/mission/incident part vers l'utilisateur final (SMS payant inclus) avec le texte technique `⚠ Missing translation: …`. C'est un défaut de production, pas une dette esthétique.

**Recommandation** : (a) normaliser les tags de langue via `SupportedLanguage` au lieu de `String` libre ; (b) ajouter les clés manquantes aux 5 packs ; (c) remplacer le `defaultIfEmpty` par un fallback vers la locale par défaut + alerte métrique, jamais le texte brut de la clé ; (d) test d'intégration qui échoue si un `templateKey` référencé dans le code est absent d'un pack.

### P-3 — Trois systèmes de templates parallèles

1. Packs JSON du kernel (`{{var}}`), 2. `FreelancerOrgNotificationTemplates.DEFAULT_FR/EN_TEMPLATES` (`{var}`, jamais branchés — grep : aucune référence hors de la classe), 3. libellés bilingues en dur du `TemplateCatalogSeeder` (billing-templates) et templates Kernel RT-comops (`KernelNotificationTemplateAdapter`). Aucune source de vérité. **Recommandation** : fusionner 2 dans 1 (mêmes clés, syntaxe `{{}}`), supprimer le code mort.

### P-6 / P-7 — Multidevise incohérent

`tnt-billing-invoice/.../Money.java:25` : `amount.setScale(2, RoundingMode.HALF_UP)` inconditionnel — un total XAF `15000` devient `15000.00`, alors que le même montant sorti du DSL (`tnt-billing-dsl/.../Money.java:33` : `scale = max(fractionDigits,0)` → 0 pour XAF) vaut `15000`. Les comparaisons/égalités inter-modules (facture vs politique de prix) peuvent diverger d'un centime et les payloads JSON ne sont pas canoniques. Par ailleurs `'FCFA'` (go-freelancer) n'est pas un code ISO : toute future unification sur le `Money` canonique (qui fait `Currency.getInstance(code)`) plantera à l'exécution sur les lignes existantes en base. **Recommandation** : un seul `Money` dans `tnt-common-core`, migration Liquibase `'FCFA'→'XAF'`, contrainte `CHECK` ISO 4217 sur les colonnes devise.

### P-4 — Aucune propagation de la locale client

Aucun `LocaleResolver`/`Accept-Language` dans le dépôt ; `TntSecurityContext` ne porte pas de locale ; `NotificationService.send` ignore `NotificationPreference.preferredLanguage` pourtant persisté (défaut `fr_CM`, `ManagePreferencesService.java:23`). **Recommandation** : filtre WebFlux qui résout `Accept-Language` → `SupportedLanguage` (avec fallback), propagation via le contexte Reactor, et résolution de la langue du destinataire depuis ses préférences dans `NotificationService` (pas chez les producteurs Kafka).

### P-10 — Documents financiers non localisables

`invoice.jrxml` : `FACTURE`, `FACTURÉ À:`, `TVA:`, `TOTAL TTC:` en dur ; montant rendu par concaténation `$P{amountHT} + " " + $P{currency}`. `InvoicePdfAdapter.generateAndStore` (`InvoicePdfAdapter.java:36-47`) ne transmet même pas de locale au service média. Une facture pour un client nigérian sortira en français avec la mention « TVA » (fiscalement inadaptée). **Recommandation** : externaliser les libellés JRXML en resource bundles Jasper (`$R{}`), passer la locale du client dans la requête PDF, formater les montants via `PriceFormatterUseCase` (enfin lui donner un consommateur).

---

## 9. Recommandations priorisées

| Priorité | Action | Problèmes couverts | Effort estimé |
|---|---|---|---|
| **P0 (immédiat)** | Corriger les tags de langue (`"fr"`→`fr_CM` via enum) et ajouter les clés `notify.*`/`incident.notification.*`/market (`ORDER_PAID`…) aux 5 packs ; remplacer le fallback `⚠ Missing translation` par un repli sur la locale par défaut + log/métrique | P-1, P-2, P-21 | 1–2 j |
| **P0** | Test de non-régression : scanner les `templateKey` du code et vérifier leur présence dans chaque pack | P-1, P-3 | 0,5 j |
| **P1** | Unifier `Money` sur `tnt-common-core` (supprimer les 5 doublons, y compris market-back et sa sémantique long ambiguë), migrer `'FCFA'→'XAF'` en base, contrainte ISO 4217 | P-6, P-7, P-19, P-22 | 4–6 j (touche billing + market — à traiter en ADR, cf. process feedback) |
| **P1** | Implémenter la résolution de locale : filtre `Accept-Language` → contexte Reactor ; consultation de `preferredLanguage` dans `NotificationService` | P-4 | 2–3 j |
| **P1** | Implémenter le fallback de locale dans `JsonLocalePackAdapter` (fr_CM→fr_FR→défaut) et corriger `LocaleConfig.toLocaleTag()` (retourner `getTag()`) | P-5, P-12 | 0,5 j |
| **P2** | Localiser les handlers d'exceptions (clés `error.*` déjà présentes dans les packs !) et les messages de validation via un adaptateur `MessageSource` branché sur le kernel | P-9 | 3–5 j |
| **P2** | Internationaliser les JRXML (`$R{}` + bundles), transmettre la locale dans `InvoicePdfAdapter`, brancher `PriceFormatterUseCase` sur invoice/media | P-10, P-18 | 3 j |
| **P2** | Supprimer le code mort (`FreelancerOrgNotificationTemplates` maps) et les dépendances Maven mortes ; localiser le sujet email | P-3, P-11, P-17 | 0,5 j |
| **P3** | Réconcilier les référentiels (`SupportedLanguage`/`SupportedCurrency` ↔ `SolutionContext` ↔ colonnes SQL) ; ajouter `messages_en_US.json` ou retirer `EN_US` ; renommer `application.yaml` de la librairie en défauts programmatiques | P-13, P-14, P-15 | 1 j |
| **P3** | Décider d'une stratégie taux de change (service dédié ou intégration Kernel) avant toute expansion multi-pays ; autoriser les montants négatifs dans `LocalizedPrice` ou documenter l'usage avoirs | P-8, P-16 | ADR |

---

## 10. Addendum — modules coreBackend (extension de périmètre)

Périmètre ajouté : `coreBackend/` — 4 modules Maven, 633 fichiers Java main (`tnt-agency-back-core` 223, `tnt-market-back-core` 170, `tnt-link-back-core` 120, `tnt-go-freelancer-point-back-core` 120).

### 10.1 Utilisent-ils yow-i18n-kernel ?

**Non — zéro import.** Grep de `com.yowyob.kernel.i18n` sur tout `coreBackend/` : 0 résultat. Un seul module déclare la dépendance Maven (`tnt-go-freelancer-point-back-core/pom.xml:39`) sans jamais l'utiliser (dépendance morte, déjà P-17). `tnt-market-back-core` passe par le pont notify (`ISendNotificationUseCase` de tnt-notify-core), donc *indirectement* par le kernel — et hérite de sa chaîne cassée (voir 10.3).

### 10.2 Chaînes en dur

- **94 messages d'exception en dur** (anglais) : agency 38 (ex. `AgencyMission.java:112-159` — machine à états entière), market 31, link 18, gofp 7 (P-24).
- **Documentation OpenAPI en français en dur** dans gofp : `@Tag`/`@Operation` (« Créer une livraison depuis une annonce assignée », `DeliveryCoreController.java:24-74` ; `RelayHubSubscriptionCoreController.java:65-77`). Le Swagger exposé aux intégrateurs est monolingue FR alors que les exceptions sont EN — trois couches de langues incohérentes dans les mêmes modules.

### 10.3 Notifications e-commerce cassées (P-21, Critique)

`MarketNotificationAdapter.java:39-84` envoie 8 types de notifications au cœur du parcours marchand (`CLIENT_QUOTE_RECEIVED`, `PROVIDER_NEW_QUOTE_REQUEST`, `ORDER_CONFIRMED`, `ORDER_PAID`, `ORDER_COMPLETED`, `REVIEW_PUBLISHED`, `LISTING_APPROVED`, `LISTING_REJECTED`) avec `NotificationModel.of(templateKey, "fr", …)` : clés en MAJUSCULES absentes des 5 packs JSON **et** tag de langue invalide. Chaque acheteur/vendeur du marketplace reçoit donc `⚠ Missing translation: ORDER_PAID` — le même défaut de production que P-1/P-2, étendu au e-commerce.

### 10.4 Gestion des devises dans coreBackend

- **market-back (e-commerce)** : sa variante de `Money` (`marketback/domain/model/Money.java`, une des 6 recensées en §5.1) — `record Money(long amount, String currency)` en « unités mineures, ex. centimes pour XAF » (Javadoc ligne 7) alors que **XAF n'a pas d'unité mineure** (ISO 4217, fraction 0). La frontière persistence convertit `BigDecimal.valueOf(minPriceXaf)` sans division (`MarketServiceOfferPersistenceAdapter.java:67`) : le `long` est donc traité tantôt comme francs, tantôt documenté comme centimes — bombe à retardement si une devise à 2 décimales (NGN, USD) est un jour activée (P-22). `formatted()` (`Money.java:58-60`) formate avec la locale JVM par défaut du serveur, pas celle du client.
- **Devise figée dans les contrats** : 117 identifiants suffixés `Xaf` dans market-back (`amountXaf`, `paidAmountXaf`, `minimumPriceXaf`… ex. `IMarketNotificationPort.java:15`, `MarketOrderApplicationService.java:232`) — la devise fait partie des signatures de ports et des commandes ; passer au multi-devise imposerait de casser l'API interne (P-23).
- **Colonnes DB** : 4 tables market (`orders`, `service-offers`, `merchant-contracts`, `campaigns`) ont `currency VARCHAR(10) DEFAULT 'XAF'` (`004-create-market-orders.xml:40` etc.) — stockage possible, défaut XAF ; les mappers retombent sur `"XAF"` si null (`MarketOrderMapper.java:122`, `ServiceOfferMapper.java:92`).
- **agency-back** : `"XAF"` en dur dans les payloads sortants (`DeliveryMissionClient.java:100,109`, `MissionService.java:693`) ; seul point positif : l'onboarding propage un `defaultCurrency` paramétrable avec repli XAF (`OnboardingKernelClient.java:118`, `OnboardingService.java:180`).
- **gofp** : `'FCFA'` non-ISO en base et en dur (déjà P-7).
- **link-back** : pas de type monétaire propre ; champs devise limités aux réponses de board (`BoardEntryResponse.java`) — surface faible.

**Conclusion de l'addendum** : les 4 modules coreBackend confirment et aggravent le verdict — aucun n'utilise le kernel i18n, le e-commerce (market-back) a ses notifications utilisateur cassées et sa devise câblée XAF jusque dans les signatures de méthodes. Recommandations : intégrer P-21 au lot P0 (mêmes correctifs que P-1/P-2 : clés + tags de langue) ; inclure le `Money` de market-back dans l'unification P1 (attention à la sémantique long/BigDecimal) ; dé-suffixer progressivement les identifiants `Xaf` (P2).

---

## 11. Conclusion — verdict

**Le projet n'est pas prêt pour un support multilingue et multidevise à l'échelle mondiale.**

- **Multilingue** : l'infrastructure existe (kernel propre, 5 packs, pidgin inclus) mais elle est branchée sur un seul module, avec des langues et des clés erronées qui font fuiter des messages techniques vers les utilisateurs. 95 %+ des chaînes visibles (exceptions, validation, PDF, emails, ProblemDetails) sont en dur, et la locale du client n'est jamais résolue ni propagée. Couverture réelle estimée : **~5 % des surfaces utilisateur**, et la seule surface couverte est défectueuse.
- **Multidevise** : le stockage par ligne le permet nominalement, mais 6 types `Money` divergents, `'FCFA'` non-ISO en base, l'absence totale de conversion/taux et le défaut XAF systémique en font une plateforme **mono-devise XAF de fait**. Le e-commerce (market-back) va plus loin : la devise est câblée dans les signatures mêmes de l'API interne (117 identifiants `*Xaf`) et la sémantique unités mineures/majeures de son `Money` est ambiguë. Une expansion Nigeria/Kenya déclencherait des incohérences d'arrondi et des erreurs d'exécution avant même la question des taux.
- Le chemin est néanmoins clair et borné : les correctifs P0 (quelques jours) réparent la production ; P1–P2 (2–3 semaines) donnent une base saine ; le « mondial » exige en plus une stratégie de taux de change et une gouvernance des référentiels — décisions d'architecture à acter en ADR, pas seulement du code.
