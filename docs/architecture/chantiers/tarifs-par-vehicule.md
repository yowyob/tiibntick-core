# Grille tarifaire par défaut par type de véhicule (proposition)

> Date de la recherche : 26/11/2026. Monnaie : franc CFA BEAC (XAF / FCFA).
> Statut : **appliquée** le 26/11/2026 (décision D-058, voir §7). La grille sert de tarif proposé par défaut : chaque livreur peut la remplacer par ses propres prix (voir §5).

---

## 1. Résumé

| Type (VehicleType) | Base | /km | /kg | Minimum* | /m³ | Fragile | Périssable | Attente/min* |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| BICYCLE (BIKE) | 300 | 75 | 50 | 500 | 2 000 | 300 | 300 | 15 |
| MOTORCYCLE (MOTORBIKE) | 600 | 100 | 75 | 1 000 | 2 000 | 500 | 500 | 25 |
| SCOOTER | 500 | 90 | 75 | 900 | 2 000 | 500 | 500 | 25 |
| TRICYCLE | 1 000 | 150 | 15 | 1 500 | 1 000 | 500 | 500 | 25 |
| CAR | 1 000 | 200 | 25 | 2 000 | 1 500 | 750 | 750 | 50 |
| VAN | 2 500 | 300 | 10 | 5 000 | 1 000 | 1 000 | 1 000 | 75 |
| TRUCK | 5 000 | 400 | 5 | 10 000 | 500 | 2 000 | 2 000 | 100 |
| BUS | 1 000 | 10 | 100 | 1 500 | 2 000 | 500 | 500 | 0 |

\* Le **minimum** et l'**attente/min** sont câblés depuis le 26/11/2026 (§7). Avant, ils existaient dans le moteur mais pas dans la grille (§2.3, constat historique).

Règle d'arrondi appliquée : base, minimum et suppléments sont des multiples de 25 ou 50 FCFA. Pour les taux unitaires, un multiple de 25 est trop grossier sur les poids lourds et sur le bus (25 FCFA/km × 250 km = 6 250 FCFA, soit plus que le prix d'un colis en agence). Ces taux sont donc arrondis à 5 FCFA près.

Formule du moteur (une seule règle, sans franchise de km ni de kg) :
`prix = max(minimum, base + km × /km + kg × /kg [+ m³ × /m³] [+ min_attente × /min]) + fragile + périssable`

---

## 2. Le modèle existant dans le code (lecture seule)

### 2.1 Composantes de la grille livreur

Le DTO de la grille, `FreelancerPricingDTO` (`coreBackend/tnt-go-freelancer-point-back-core/src/main/java/com/yowyob/tiibntick/core/gofreelancer/adapter/in/web/request/FreelancerPricingDTO.java:16-39`), contient :
`baseFee`, `pricePerKm`, `pricePerKg`, `pricePerCbm`, `fragileSurcharge`, `perishableSurcharge`, `waitingFeePerMinute` et `currency`. La devise par défaut est « FCFA », normalisée en `XAF`. La seule validation est `@Min(0)`.

`FreelancerGridPolicyMapper.toRules` (`.../application/service/FreelancerGridPolicyMapper.java:109-123`) transforme cette grille en **une** `PricingRule` sans condition, plus deux `SurchargeRule` FIXED au maximum :
- `packageType == 'FRAGILE'` ;
- `packageType == 'PERISHABLE'` (lignes 35-36).

Il n'y a dans la grille ni minimum, ni majoration d'urgence, de nuit ou de week-end.

Côté moteur, `PricingRule` (`billing/tnt-billing-pricing/src/main/java/com/yowyob/tiibntick/core/billing/pricing/domain/model/PricingRule.java:19-28`) porte en plus `minimumPrice` et `maximumPrice`, appliqués aux lignes 46-51. Le mapper ne les renseigne jamais (lignes 111-118).

### 2.2 Valeurs actuelles par véhicule (lot C-54)

Sources :
- `GofpCourierPricingProperties.defaults()` (`.../gofreelancer/config/GofpCourierPricingProperties.java:71-79`) ;
- `tnt-bootstrap/src/main/resources/application.yml:451-457` (`tnt.gofp.courier.grids.*`, mêmes valeurs).

| Catégorie (clé yml) | VehicleType couverts | Base | /km | /kg | /m³ | Fragile | Périssable | Attente |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| `moto` | MOTORBIKE, SCOOTER, BIKE | 500 | 100 | 50 | 2 000 | 500 | 500 | 0 |
| `tricycle` | TRICYCLE | 800 | 150 | 40 | 2 000 | 500 | 500 | 0 |
| `taxi` | CAR | 1 000 | 200 | 30 | 2 000 | 500 | 500 | 0 |
| `camion` | TRUCK, VAN | 3 000 | 400 | 20 | 2 000 | 500 | 500 | 0 |
| `bus` | BUS | 2 000 | 300 | 30 | 2 000 | 500 | 500 | 0 |

D'où viennent ces valeurs :
- Les valeurs m³, fragile et périssable viennent de `FreelancerGridPolicyMapper.PLATFORM_DEFAULT_GRID` (lignes 40-43).
- L'attente est forcée à `0.0` dans `gridFor` (`GofpCourierPricingProperties.java:94`).
- Les catégories sont définies dans `VehicleCategory` (`.../domain/model/enums/vehicle/VehicleCategory.java:21-26`). BIKE et SCOOTER sont tarifés comme `moto`, VAN comme `camion` (lignes 40-49). Un véhicule inconnu est tarifé comme `moto`.

Autres tables vues :
- `delivery_person_pricing` (`.../resources/db/changelog/20260707_create_pricing_policies.sql:2-15`) : ancien miroir de la grille, mêmes colonnes, devise `'FCFA'`.
- `pricing_catalog` (`create-pricing-catalog.sql:8-43`) : prix des **abonnements** (FREE 0, BASIC 7 500, STANDARD 10 000, PREMIUM 10 000, ADVANCE 15 000 XAF). Ce ne sont **pas** des tarifs de livraison.
- Les gabarits de facturation semés (`billing/tnt-billing-templates/.../config/TemplateCatalogSeeder.java`) donnent des repères déjà présents dans le Core :
  - TPL-BASE-STD : 500 + 50/km + 30/kg, MIN 500 (l.136-150) ;
  - TPL-EXPRESS : +50 % pour une garantie de 2 h, +500 FCFA la nuit (l.212-222) ;
  - TPL-INTER_CITY : 3 000 + 40/km, péage 500, retour à vide 30 % (l.250-261) ;
  - TPL-TIME_SLOTS : ×1,5 la nuit (l.311-317).

### 2.3 Ce qui manque pour charger la grille proposée

1. **Minimum** : ajouter un champ `minimumFee` à `Grid` et au DTO, puis le transmettre à `PricingRule.minimumPrice` dans `toRules`. Sans lui, une course moto de 3 km revient à 975 FCFA au lieu de 1 000, et un camion pour 3 km à 7 200 FCFA.
2. **Attente** : ne plus forcer `waitingFeePerMinute(0.0)` dans `gridFor` (ligne 94), et ajouter le champ à `Grid`.
3. **BICYCLE, SCOOTER et VAN séparés** : il faut de nouvelles constantes dans `VehicleCategory` (clés `velo`, `scooter`, `camionnette`, par exemple). Sinon, BIKE et SCOOTER restent sur la grille `moto`, et VAN sur la grille `camion`.
4. **Sans aucun changement de code**, seules les 5 clés existantes peuvent être mises à jour via `application.yml`, avec base, /km, /kg, /m³, fragile et périssable :

```yaml
# proposition, NON appliquée
tnt.gofp.courier.grids:
  moto:     { base-fee: 600,  price-per-km: 100, price-per-kg: 75, price-per-cbm: 2000, fragile-surcharge: 500,  perishable-surcharge: 500 }
  tricycle: { base-fee: 1000, price-per-km: 150, price-per-kg: 15, price-per-cbm: 1000, fragile-surcharge: 500,  perishable-surcharge: 500 }
  taxi:     { base-fee: 1000, price-per-km: 200, price-per-kg: 25, price-per-cbm: 1500, fragile-surcharge: 750,  perishable-surcharge: 750 }
  camion:   { base-fee: 5000, price-per-km: 400, price-per-kg: 5,  price-per-cbm: 500,  fragile-surcharge: 2000, perishable-surcharge: 2000 }
  bus:      { base-fee: 1000, price-per-km: 10,  price-per-kg: 100, price-per-cbm: 2000, fragile-surcharge: 500, perishable-surcharge: 500 }
```

---

## 3. Recherche : prix observés et coûts (sources)

| # | Source (URL) | Date | Ce qu'elle dit |
|---|---|---|---|
| S1 | https://kolonell.com/fr/blog/zones-et-tarifs-livraison-ecommerce-cameroun-2026 | 18/08/2026 | Douala/Yaoundé, tarifs conseillés : 0-4 km 500-1 000 ; 4-10 km 1 000-1 800 ; 10-20 km 1 800-3 000 ; au-delà de 20 km sur devis. Paliers de poids : 2-5 kg +500 ; 5-15 kg +1 000 à 1 500 ; plus de 15 kg ou encombrant +2 000. Livraison le jour même : environ +50 %. |
| S2 | https://kolonell.com/fr/blog/calcul-automatique-frais-livraison-multi-zones-douala-2026 | 10/08/2026 | Coût réel du livreur et tarif client : 0-5 km 900 / 1 500 ; 5-15 km 1 600 / 2 000 ; 15-30 km 2 800 / 3 000 ; plus de 30 km 4 500 de coût ; point relais 600 / 800. |
| S3 | https://kolonell.com/fr/blog/gestion-livraison-colis-ecommerce-douala-2026 | 23/08/2026 | Intra-Douala : livreur moto interne 1 000-1 800 ; agrégateur moto 1 500-2 500 ; **agrégateur voiture 2 500-4 000** ; point relais 800-1 500 ; **interurbain vers Yaoundé 3 000-6 000** (48-72 h). |
| S4 | https://kolonell.com/fr/blog/livraison-dernier-kilometre-ecommerce-douala-2026 | 08/08/2026 | Dernier kilomètre à Douala : 1 500 à 4 000 par colis. Coursier indépendant 1 500-2 500 ; plateformes (Yango, Glovo) 2 000-4 000. |
| S5 | https://kolonell.com/fr/blog/3pl-vs-flotte-interne-livraison-douala-2026 | 25/08/2026 | Flotte moto interne : moto d'environ 250 000 amortis, carburant et entretien d'environ 90 000/mois, salaire d'environ 120 000/mois, environ 25 courses/jour, soit environ 700 FCFA par course à pleine charge. Prestataire externe : 1 800-3 000 par course. |
| S6 | https://kolonell.com/fr/blog/cout-marges-e-commerce-livraison-douala-2026 | 13/08/2026 | Une course coûte 1 000 à 3 000. Une flotte moto interne ramène le coût d'environ 2 500 à environ 1 200 par course. |
| S7 | https://www.travellines.cm/transport-de-colis/ et https://www.travellines.cm/nos-tarifs/ | consultées le 26/11/2026 | Colis **d'agence à agence** Douala ↔ Yaoundé (253 km) : 0-5 kg 1 000 ; 5-10 kg 2 000 ; 10-20 kg 3 500 ; 20-30 kg 5 000 ; au-delà de 30 kg +300/kg. Billet passager 6 000 ; VIP 15 000-35 000. |
| S8 | https://www.kmerbus.com/ | consultée le 26/11/2026 | Colis « partout au Cameroun dès 1 000 XAF ». Billet Douala-Yaoundé dès 5 000 XAF. |
| S9 | https://papacameroun.com/2026/07/07/quels-sont-les-tarifs-de-touristique-express-en-2026/ | 07/07/2026 | Billet Douala-Yaoundé : classique dès 5 000, business environ 10 000. |
| S10 | https://infospratiques.cm/?p=70 | 04/2026 (mise à jour 05/2026) | Carburant : **super 840 FCFA/L, gasoil 828 FCFA/L**, pétrole 350 (valables en mars 2026, fixés par la CSPH). |
| S11 | https://www.combien-coute.net/essence/cameroun/ (données Numbeo) | mise à jour 23/11/2026 | Essence : 840 FCFA/L, ce qui confirme que le prix n'a pas bougé fin 2026. |
| S12 | https://fr.allafrica.com/stories/202412060684.html | 12/2024 | Le ministre des Finances confirme qu'il n'y aura pas de hausse du carburant en 2025. Prix fixés par la hausse du 03/02/2024 (super 840, gasoil 828). |
| S13 | https://www.combien-coute.net/taxi/cameroun/douala/ et https://www.combien-coute.net/taxi_attente/cameroun/douala/ (Numbeo) | 21-23/11/2026 | Taxi à Douala : environ 499 FCFA/km ; 1 h d'attente environ 3 503 FCFA (environ 58/min). Déclaratif. |
| S14 | https://www.cyriljarnias.com/expatriation/installer-cameroun-expatrie-guide-complet/transports-en-commun-cameroun-guide-pratique/ | non datée | Taxi partagé 250-500 par personne ; taxi « dépôt » en ville 1 000-3 000 (tarif réglementé cité : 3 000 le jour, 3 500 la nuit) ; aéroport environ 5 000. |
| S15 | https://www.digitalbusiness.africa/yango-se-lance-dans-le-service-de-livraison-a-douala/ et https://ecomatin.net/yango-se-lance-dans-le-service-de-livraison-a-douala/ | 28/04/2022 | Yango Livraison à Douala : moto ou voiture, colis jusqu'à 20 kg, prix « à partir de 120 FCFA » (prix d'appel). |
| S16 | Pages tarifaires Yango (`yango.yandex.com/abidjan/tariff/courier`, `/dakar/tariff/cargo`), extraits du moteur de recherche (la page elle-même est inaccessible) | non datées | Abidjan, livraison moto : minimum 288 FCFA avec 1,5 km inclus, au plus 195 FCFA/km en ville. Dakar, cargo : minimum 3 520 FCFA avec 1 km inclus. Ce sont des prix XOF, à parité avec le XAF. |
| S17 | https://news.abidjan.net/articles/684686/le-service-8239livraison8239-de-lapplication-yango-est-arrive-a-abidjan | ancienne (lancement) | Yango Livraison à Abidjan : minimum 650 FCFA avec 2 km inclus ; exemple d'une course urbaine à environ 1 100 FCFA. |
| S18 | https://www.237online.com/?p=10656 | 19/05/2016 (mise à jour 08/2025) | Moto-taxi à Douala : recette de 3 000 à 5 000 FCFA/jour ; versement hebdomadaire au propriétaire d'environ 18 000. |
| S19 | https://www.237online.com/cameroun-emplois-mototaximan-nouvelle-planche-de-salut-pour-la-jeunesse/ | non datée | Moto neuve 350 000-450 000. Carte grise 20 000, assurance 18-25 000 par an, impôt 10 000, vignette 2 000. |
| S20 | https://fasterwheeler.com/cm/bikes/honda-cg125-10748 | 2026 | Honda CG125 au Cameroun : environ 537 462 XAF (agrégateur). |
| S21 | https://www.spritmonitor.de/fr/detail/1154623.html | déclaratif | Piaggio Ape 200 (utilitaire à trois roues) : environ 3,9 L/100 km. |
| S22 | https://www.motoplanete.com/yamaha/9877/Tricity125-2023/contact.html (via moteur de recherche) | 2023 | Scooter 125 cm³ : 2,3 L/100 km (cycle WMTC). |
| S23 | https://www.investiraucameroun.com/economie/0202-15909-corridors-douala-bangui-et-douala-ndjamena-le-cameroun-arrete-a-3-3-millions-de-fcfa-le-prix-du-fret-par-camion | 02/02/2021 | Fret Douala-Bangui / Douala-N'Djamena : 3,3 M FCFA par camion lourd (prix consensuel), soit environ 1 800 FCFA/km pour un semi-remorque. |
| S24 | https://www.kayak.fr/location-voiture-Douala.12572.cars.ksp, https://www.liligo.fr/location-voiture/cameroun/douala | 2026 | Location de voiture à Douala, sans chauffeur ni carburant : 50-79 €/jour (environ 33 000-52 000 FCFA). |

Ce qui n'a pas pu être trouvé : aucun tarif public pour le tricycle de charge, la camionnette, le camion léger urbain ou le vélo-coursier au Cameroun. La page de grille de Yango Douala n'était pas accessible, et l'article de Cameroon Tribune sur la livraison à moto-taxi (« 1 500 à 2 500F ») est réservé aux abonnés et non daté.

### 3.1 Hypothèses de coût par km

Carburant : super à 840 et gasoil à 828 FCFA/L (S10-S12). Les consommations ci-dessous sont des ordres de grandeur en ville et en charge. Seuls le scooter (S22) et le tricycle (S21, par analogie) sont sourcés ; les autres valeurs sont des **hypothèses**. Le temps du conducteur est valorisé à environ 17 FCFA/min, ce qui correspond à un revenu net visé de 7 000 à 8 000 FCFA/jour, cohérent avec S18 réindexé et le salaire de S5.

| Type | Conso. (L/100) | Carburant/km | Entretien + pneus/km | Amortissement/km | Assurances et taxes/km | Temps conducteur/km | **Coût/km** | Proposé/km |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| BICYCLE | 0 | 0 | 5 | 2 (vélo à 80 000 sur 40 000 km) | 0 | 75 (environ 12 km/h) | **≈ 80** | 75 |
| MOTORCYCLE | 2,5-3 (super) | 21-25 | 10 | 8 (500 000 sur 60 000 km, S19/S20) | 1-2 (S19) | 55 (environ 18 km/h) | **≈ 98** | 100 |
| SCOOTER | 2,3 (S22) | 19 | 10 | 8 | 2 | 55 | **≈ 94** | 90 |
| TRICYCLE | 4-5 (S21, en charge) | 34-42 | 15 | 25 (≈ 1,5 M sur 60 000 km, hypothèse) | 3 | 70 (environ 15 km/h) | **≈ 150** | 150 |
| CAR | 8 (super) | 67 | 35 | 30 (≈ 4,5 M sur 150 000 km) | 8 | 60 | **≈ 200** | 200 |
| VAN | 11 (gasoil) | 91 | 50 | 50 (≈ 10 M sur 200 000 km) | 15 | 75 (conducteur + chargement) | **≈ 280** | 300 |
| TRUCK léger 3,5-7 t | 17 (gasoil) | 141 | 80 | 65 (≈ 20 M sur 300 000 km) | 20 | 100 (conducteur + aide) | **≈ 405** | 400 |
| BUS (colis en soute) | 13 (gasoil) | 108 pour tout le car | — | — | — | — | marginal ≈ 10 par colis | 10 |

Pour le bus, le car circule de toute façon. Le colis ne paie qu'une part marginale du trajet (environ 108 FCFA/km répartis sur plus de 10 colis ou passagers). Le prix du bus est donc porté par le **poids**, calibré sur S7.

---

## 4. Grille proposée par type : justification et exemples

Les devis ci-dessous sont calculés avec la formule du §1, sans fragile ni volume, et le minimum est appliqué. Entre parenthèses figure le devis que donne **aujourd'hui** la grille plateforme (sans minimum) pour le même cas.

### 4.1 BICYCLE : base 300, 75/km, 50/kg, minimum 500
- **Pertinence** : faible à Douala et Yaoundé (relief de Yaoundé, chaleur, trafic). Le vélo n'a d'intérêt qu'en hyper-centre, pour moins de 5 km et moins de 5 kg. Aucun tarif observé (non sourcé).
- **Justification** : pas de carburant, mais une vitesse lente, donc le temps domine (environ 80 FCFA/km). L'offre doit rester sous la moto (environ 30 % moins chère) pour avoir un sens commercial.
- **Fourchette observée** : aucune. Bas de marché moto 500-1 000 pour 0-4 km (S1).
- **Exemples (1 kg)** : 3 km **575** (850) · 10 km **1 100** (1 550) · 30 km 2 600, hors domaine réaliste.

### 4.2 MOTORCYCLE (bendskin) : base 600, 100/km, 75/kg, minimum 1 000
- **Justification** : coût d'environ 98 FCFA/km (§3.1). Le prix au km reste à 100. La base passe de 500 à 600 et un minimum de 1 000 est ajouté, pour couvrir la prise en charge (approche et attente au départ, environ 700 FCFA de coût fixe par course selon S5). Le prix au kg passe de 50 à 75 pour coller aux paliers de poids du marché : +1 000 à 1 500 pour 5-15 kg et +2 000 au-delà de 15 kg (S1). Capacité pratique : 20 kg (S15).
- **Fourchette observée** : 500-1 000 (0-4 km) ; 1 000-1 800 (4-10 km) ; 1 800-3 000 (10-20 km) (S1) ; coursier 1 500-2 500 (S3/S4) ; plateformes 2 000-4 000 (S4) ; Yango Abidjan au plus 195/km (S16).
- **Exemples** :
  - 1 kg : 3 km **1 000** (850) · 10 km **1 675** (1 550) · 30 km **3 675** (3 550) ;
  - 20 kg : 3 km **2 400** (1 800) · 10 km **3 100** (2 500) · 30 km **5 100** (4 500).

### 4.3 SCOOTER : base 500, 90/km, 75/kg, minimum 900
- **Justification** : consommation sourcée de 2,3 L/100 (S22), soit environ 19 FCFA/km. Le coffre est plus petit que le porte-bagages d'une moto (charge utile pratique de 10 à 15 kg). Le scooter est positionné 5 à 10 % sous la moto. Aujourd'hui, il est tarifé comme une moto (`VehicleCategory.of`, l.43).
- **Fourchette observée** : celle de la moto (S1-S4) ; aucun tarif propre au scooter.
- **Exemples** :
  - 1 kg : 3 km **900** (850) · 10 km **1 475** (1 550) · 30 km **3 275** (3 550) ;
  - 20 kg : 3 km 2 270 · 10 km 2 900 · 30 km 4 700. 20 kg dépasse la charge conseillée pour un scooter.

### 4.4 TRICYCLE (tricycle de charge) : base 1 000, 150/km, 15/kg, minimum 1 500
- **Justification** : coût d'environ 150 FCFA/km (§3.1), et 150/km est conservé. Le prix au kg passe de **40 à 15** : avec 40/kg, 200 kg coûtent 8 000 FCFA de poids seul, plus qu'un taxi dépôt (1 000-3 000, S14) ou qu'une voiture de livraison (2 500-4 000, S3). Or le tricycle sert justement aux charges lourdes, de 300 à 500 kg. La base passe à 1 000 pour couvrir le chargement.
- **Fourchette observée** : aucun tarif publié (S14 et la recherche « tricycle Douala » ne donnent que des prix de taxi). Repère indirect : taxi dépôt 1 000-3 000.
- **Exemples** :
  - 20 kg : 3 km **1 750** (2 050) · 10 km **2 800** (3 100) · 30 km **5 800** (6 100) ;
  - 200 kg : 3 km **4 450** (9 250) · 10 km **5 500** (10 300) · 30 km **8 500** (13 300).

### 4.5 CAR (voiture) : base 1 000, 200/km, 25/kg, minimum 2 000
- **Justification** : coût d'environ 200 FCFA/km (§3.1), et la grille actuelle (200/km) est cohérente avec le marché. On ajoute un minimum de 2 000 : sous ce prix, une voiture ne se déplace pas (taxi dépôt réglementé à 3 000, S14). Le prix au kg passe de 30 à 25.
- **En interurbain** : 200/km × 250 km = 50 000. C'est un **véhicule dédié** pour un aller simple avec un retour partiellement chargé. L'aller seul coûte environ 39 000 (carburant 16 800 + usure, amortissement et temps). Un retour entièrement à vide ajouterait environ 25 000. Repère : location sans chauffeur 33 000-52 000 par jour hors carburant (S24).
- **Fourchette observée** : intra-Douala 2 500-4 000 (S3) ; taxi 1 000-3 000 (S14) ; environ 499/km (S13, déclaratif).
- **Exemples** :
  - 1 kg : 3 km **2 000** (1 630) · 10 km **3 025** (3 030) · 30 km **7 025** (7 030) · 250 km **51 025** (51 030) ;
  - 20 kg : 3 km 2 100 · 10 km 3 500 · 30 km 7 500 · 250 km 51 500 ;
  - 200 kg : 3 km 6 600 · 10 km 8 000 · 30 km 12 000 · 250 km 56 000.

### 4.6 VAN (camionnette/fourgonnette 1-1,5 t) : base 2 500, 300/km, 10/kg, minimum 5 000
- **Justification** : coût d'environ 280 FCFA/km (§3.1), soit 300/km avec une marge d'environ 7 %. Aujourd'hui, la camionnette est tarifée comme un camion (400/km), ce qui la surfacture d'environ 25-30 % sur longue distance. Le prix au kg est bas (10), car la capacité est grande. Un minimum de 5 000 couvre la mobilisation du véhicule.
- **Fourchette observée** : aucune source chiffrée locale (non sourcé). Repère supérieur : camion lourd en corridor, environ 1 800 FCFA/km (S23).
- **Exemples** :
  - 20 kg : 3 km **5 000** (4 600) · 10 km **5 700** (7 400) · 30 km **11 700** (15 400) · 250 km **77 700** (103 400) ;
  - 200 kg : 3 km 5 400 · 10 km 7 500 · 30 km 13 500 · 250 km **79 500** (107 000).

### 4.7 TRUCK (camion léger 3,5-7 t) : base 5 000, 400/km, 5/kg, minimum 10 000
- **Justification** : coût d'environ 405 FCFA/km (§3.1), et 400/km est conservé. La base passe de 3 000 à 5 000 et un minimum de 10 000 est ajouté : un camion avec son aide ne se mobilise pas pour 4 200 FCFA (3 km dans la grille actuelle). Le prix au kg tombe à 5, car le camion est payé pour sa capacité et non pour le kg.
- **Fourchette observée** : non sourcé pour l'urbain. Corridor poids lourd environ 1 800/km (S23). Le camion léger représente environ 22 % de ce prix, ce qui est plausible.
- **Exemples (200 kg)** : 3 km **10 000** (8 200) · 10 km **10 000** (11 000) · 30 km **18 000** (19 000) · 250 km **106 000** (107 000). Pour 2 000 kg sur 250 km : 115 000.

### 4.8 BUS (minibus ou car, colis interurbain) : base 1 000, 10/km, 100/kg, minimum 1 500
- **Justification** : c'est l'écart le plus important. La grille actuelle (2 000 + 300/km) donne **77 030 FCFA pour 1 kg entre Douala et Yaoundé**, contre 1 000 FCFA en agence (S7) et 3 000-6 000 chez un transporteur interurbain (S3), soit 13 à 77 fois le marché. Un colis en soute ne paie pas le trajet du car. La proposition déplace donc le prix vers le poids : 100/kg, entre la pente de 0 à 30 kg de S7 (environ 130-170/kg) et le prix d'appel. Le prix au km devient symbolique (10), pour garder une modulation entre lignes courtes et longues. L'attente est à 0, car le car suit un horaire.
- **Fourchette observée** : Douala ↔ Yaoundé d'agence à agence, 1 000 (0-5 kg), 3 500 (10-20 kg), 5 000 (20-30 kg), +300/kg au-delà (S7) ; « dès 1 000 » (S8) ; interurbain e-commerce 3 000-6 000 (S3).
- **Exemples** :
  - 1 kg : 250 km **3 600** (77 030) · 3, 10 ou 30 km **1 500**, cas peu réaliste en ville ;
  - 20 kg : 250 km **5 500** (77 600) ;
  - 200 kg : 250 km **23 500** (83 000), contre environ 56 000 chez Travel Lines (5 000 + 170 kg × 300), qui facture les gros envois plus cher.

### 4.9 Majorations (hors grille actuelle, à porter par des SurchargeRule si souhaité)
- **Urgence ou jour même** : +50 %. Sources : S1, et TPL-EXPRESS `express_2h_surcharge_pct` = 50 (`TemplateCatalogSeeder.java:220`).
- **Nuit (22 h-6 h)** : +500 FCFA pour les deux-roues, +1 000 FCFA pour CAR, VAN et TRUCK. Sources : TPL-EXPRESS `night_surcharge_fixed` = 500 (l.222) ; taxi dépôt 3 000 → 3 500 la nuit, soit +17 % (S14).
- **Interurbain** : péages et retour à vide sont inclus dans le prix au km proposé pour CAR, VAN et TRUCK. Une alternative plus fine serait une seconde `PricingRule` conditionnée par `distance >= 50`, sur le modèle de TPL-INTER_CITY (l.250-261). Le mapper de grille ne sait produire qu'une règle sans condition (`FreelancerGridPolicyMapper.java:111-118`).

---

## 5. Le prix est une proposition

**Oui, le code actuel permet déjà au livreur de redéfinir ses prix.** La grille plateforme n'est qu'un repli.

- **Ordre de résolution du devis** (`FreelancerPriceQuoteService.java:78-106`) :
  1. la `BillingPolicy` ACTIVE et valide la plus récente du livreur, qu'il s'agisse de sa propre grille (`templateCode = FREELANCER-GRID`) ou d'un gabarit de facturation appliqué ;
  2. sinon, son ancienne grille `delivery_person_pricing`, si elle n'a pas encore été migrée ;
  3. sinon, la **grille plateforme du véhicule** (`platformGrid(vehicleType)`, l.74-76), c'est-à-dire la grille proposée ici.

  L'estimation « prix plateforme » d'une annonce utilise directement l'étape 3 (`platformQuote`, l.69-71, appelée depuis `DeliveryNeedApplicationService.java:171`).
- **Entité et endpoint pour redéfinir** : `PUT /api/freelancers/{id}/policies` (`FreelancerPricingPolicyController.java:40-48`). La propriété est vérifiée par `FreelancerOwnershipGuard` (admin `gofp-admin:manage` sinon), via `FreelancerPricingPolicyApplicationService.upsertPolicyAsCaller` (l.59-64). La grille est enregistrée comme une `BillingPolicy` (propriétaire `FREELANCER_ORG`, activée automatiquement, l.97-120), avec un miroir dans `delivery_person_pricing` (l.82-95). La lecture se fait par `GET /api/freelancers/{id}/policies` (contrôleur l.32-37).
- **Négociation par course** : il existe un fil de messages client ↔ livreur par annonce (`gofp_negotiation_messages`, `20261112_create_negotiation_messages.sql`), mais en **texte libre**. Aucun champ de prix structuré n'a été trouvé dans ce fil.

**Points d'articulation à corriger** (constats, non modifiés) :
1. **La fusion ne part pas de la grille plateforme.** Au premier enregistrement, `currentGrid` part d'un DTO vide (`FreelancerPricingPolicyApplicationService.java:75-80`), puis `merge` remplace les champs absents par 0 (`FreelancerGridPolicyMapper.java:179-181`). Un livreur qui n'envoie que `pricePerKm` se retrouve donc avec une base, un prix au kg, des suppléments fragile et périssable à 0. Il faudrait partir de `courierPricing.gridFor(catégorie du véhicule)` pour que « proposition puis ajustement » fonctionne champ par champ.
2. **Aucun garde-fou** sur les valeurs du livreur hormis `@Min(0)` (`FreelancerPricingDTO.java:16-36`). Si l'on veut des bornes, par exemple de 50 % à 300 % de la grille plateforme, ou un plancher, rien ne les impose aujourd'hui.
3. **Sans tenant dans le contexte**, la grille reste dans l'ancienne table jusqu'à une mise à jour faite avec un tenant (l.106-110). Elle reste tout de même utilisée par le devis (étape 2).
4. Le front doit afficher la source du devis (`POLICY`, `LEGACY_GRID` ou `PLATFORM_DEFAULT`, champ `source` de `Quote`) pour que le client sache s'il voit le prix du livreur ou la proposition de la plateforme.

---

## 6. Limites et incertitudes

- **Pas de grille officielle** pour la livraison urbaine au Cameroun. Les fourchettes urbaines viennent surtout d'un seul éditeur de blog (Kolonell, S1-S6, août 2026), cohérent mais non indépendant. L'article de Cameroon Tribune (1 500 à 2 500F par moto-taxi) n'a pas pu être lu en entier (réservé aux abonnés, non daté).
- **Non sourcés** : tarifs du tricycle de charge, de la camionnette et du camion léger en ville ; prix d'achat du tricycle, de la camionnette et du camion ; consommations moto 125, voiture, camionnette, camion et car (hypothèses usuelles) ; péages Douala-Yaoundé ; coûts d'entretien.
- **Yango** : le prix d'appel « dès 120 FCFA » (S15, 2022) n'est pas un tarif réel. La grille de Douala n'a pas pu être consultée (domaine inaccessible) ; seuls Abidjan et Dakar sont connus par extraits (S16, non datés).
- **Les sources sur le moto-taxi sont anciennes** (S18 : 2016 ; S19 : non datée). Le revenu visé du conducteur (7 000 à 8 000 par jour) est une réindexation et une hypothèse.
- **Carburant** : 840/828 FCFA/L, stable depuis 02/2024 et confirmé à 840 en 11/2026 (S11). Le gasoil subit une pression à l'importation et la subvention a été fortement réduite en 2025 (S12 et articles liés). Une hausse rendrait les prix au km des véhicules diesel (VAN, TRUCK) sous-évalués d'environ 10 % pour chaque hausse de 100 FCFA/L.
- **Le modèle est linéaire, sans franchise.** Le moteur facture le kg dès le premier kg et le km dès le premier km. Les paliers du marché (S1, S7) ne sont donc qu'approchés. Les colis de 200 kg ou plus en bus sont sous le prix de Travel Lines.
- **Les devis « 3/10/30 km » en BUS et « 30 km » en BICYCLE ne correspondent pas à des usages réels.** Ils ne sont donnés que pour comparaison.
- **Le poids et le volume s'additionnent** dans le moteur (pas de « poids volumétrique max(kg, m³) »). Les prix au m³ sont gardés modestes pour limiter la double facturation.
- **Les écarts entre villes ne sont pas modélisés** (Douala, Yaoundé, villes secondaires, où les prix sont probablement 10 à 20 % plus bas). Ils ne sont pas sourcés.

---

## 7. Application dans le code (26/11/2026)

Les trois manques du §2.3 et le point d'articulation n° 1 du §5 sont corrigés :

1. **Minimum** : champ `minimumFee` dans `FreelancerPricingDTO` et `GofpCourierPricingProperties.Grid`, transmis à `PricingRule.minimumPrice` par `FreelancerGridPolicyMapper.toRules` et relu par `toGrid`. Un `minimumFee` à 0 supprime le minimum.
2. **Attente** : `waitingFeePerMinute` vient de la grille, au lieu d'être forcé à 0.
3. **Vélo, scooter et camionnette séparés** : clés `velo` (BIKE), `scooter` (SCOOTER) et `camionnette` (VAN), résolues par `GofpCourierPricingProperties.gridForVehicle(type)`. `VehicleCategory` est inchangé (matching et vocabulaire de l'application) ; un type précis sans grille propre retombe sur la grille de sa catégorie.
4. **Première grille d'un livreur** : `FreelancerPricingPolicyApplicationService` part de la grille plateforme de son véhicule (`FreelancerVehicleRepository`), ou de la grille moto si le véhicule est inconnu. Un livreur qui n'envoie que `pricePerKm` garde donc les autres valeurs proposées.
5. Les 8 grilles du §1 sont à la fois les valeurs par défaut du code et celles de `tnt.gofp.courier.grids` dans `application.yml`. Une entrée yml remplace entièrement l'entrée du code ; chaque colonne y est donc écrite.

Restent hors code : les majorations du §4.9 (urgence, nuit), les bornes sur la grille d'un livreur (§5, point 2 ; voir la spec d'administration §3) et la grille modifiable par un administrateur (même section).

Tests :
- `CourierCardRulesTest` (grilles par catégorie et par type précis) ;
- `FreelancerBillingPricingTest` (minimum, première grille depuis le véhicule) ;
- `CourierVehicleJourneyTest` ;
- `CourierPlatformGridsBindingTest` (bootstrap : liaison du vrai `application.yml`, 8 clés et toutes les colonnes) ;
- `FreelancerPricingPersistenceIntegrationTest` (PostGIS).
