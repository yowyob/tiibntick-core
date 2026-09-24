# RUN_LOCAL — faire tourner le monolithe `tnt-bootstrap` en local

> Repo `main` @ `5f134619`. Dernière vérification : **2026-08-05**, boot complet réussi,
> endpoints GOFP + Market vérifiés sur la vraie base.
>
> Tout ce qui suit a été exécuté. Les rares points non vérifiés sont signalés comme tels.

---

## 0. Résumé

**Boot reproductible : oui.** Conditions en §11.

| Étape | État |
|---|---|
| Build complet 53 modules | ✅ 17 min 55 s à froid, **1 min 15 s** à chaud |
| Infra Docker (4 conteneurs) | ✅ postgres, redis, kafka, minio |
| Migrations Liquibase | ✅ passent |
| Démarrage appli | ✅ **64,4 s**, Netty sur 8080 |
| `/actuator/health` | ⚠️ `DOWN` — cause identifiée et bénigne, §7 |
| `POST /api/delivery-needs` | ✅ 201, persiste |
| `GET /api/announcements` (Market) | ✅ 200 `[]` |
| Empreinte mémoire | ✅ mesurée, ~2,35 Go au total |

---

## 1. Environnement de référence (mesuré)

```
Machine   jtk-Latitude-5580
OS        Ubuntu 24.04, noyau 7.0.0-28-generic, glibc 2.39
CPU       8 cœurs
RAM       15 Gio
Java      openjdk 21.0.11 (Ubuntu 21.0.11+10-1-24.04.2)
Maven     3.9.9 — installé sans root dans ~/.local/opt/apache-maven-3.9.9
Docker    présent (l'utilisateur doit être dans le groupe `docker`)
```

glibc 2.39 suffit largement pour OR-Tools. Le commentaire du `pom.xml` racine
(`:189-190`) « image Docker Debian OBLIGATOIRE » concerne le packaging conteneur,
**pas** l'exécution locale : les natifs OR-Tools 9.8.3296 se chargent sans
problème directement sur Ubuntu (`OR-Tools 9.8.3296 native libraries loaded
successfully` au premier log de démarrage).

---

## 2. Outillage — deux accrocs

### 2.1 `./mvnw` n'existe pas

`README.md` §« Démarrage rapide » propose `./mvnw`. **Il n'y a aucun wrapper Maven
dans le repo.** Sans root :

```bash
mkdir -p ~/.local/opt && cd ~/.local/opt
curl -LO https://dlcdn.apache.org/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz
tar xzf apache-maven-3.9.9-bin.tar.gz
export PATH="$HOME/.local/opt/apache-maven-3.9.9/bin:$PATH"
```

→ À corriger : committer le wrapper (`mvn wrapper:wrapper`) ou retirer `./mvnw` du README.

### 2.2 Le dépôt `confluent` bloque le premier build

Le `pom.xml` racine (`:282-314`) déclare `https://packages.confluent.io/maven/`.
Ce host répond en HEAD mais **stalle indéfiniment sur les GET d'artefacts** — le
premier build est resté >100 s figé sur `io/zipkin/brave/brave-bom/6.3.1/…`.

Contournement, dans `~/.m2/settings.xml` (hors repo, à conserver) :

```xml
<settings>
  <mirrors>
    <mirror>
      <id>confluent-to-central</id>
      <mirrorOf>confluent</mirrorOf>
      <url>https://repo.maven.apache.org/maven2</url>
    </mirror>
  </mirrors>
</settings>
```

Sans risque : **zéro dépendance `io.confluent` / `kafka-avro` / `schema-registry`**
dans tout le réacteur. Le dépôt confluent peut être retiré du `pom.xml` racine.

### 2.3 Bonne nouvelle : ni registre privé, ni JARs Kernel

Contrairement à ce que laisse entendre `CLAUDE.md` (« Kernel = dépendance externe
read-only »), **aucun module n'a de dépendance `com.yowyob.api` active** : les seules
occurrences (`logistics/tnt-dispute-core/pom.xml:64-71`) sont commentées, et le
GitLab Package Registry est commenté lui aussi.

→ **Le build ne demande aucun credential.** Tout vient de Maven Central.

---

## 3. Build

```bash
export PATH="$HOME/.local/opt/apache-maven-3.9.9/bin:$PATH"
cd ~/projets/tiibntick-core
mvn -pl tnt-bootstrap -am package -DskipTests \
    -Dmaven.wagon.http.readTimeout=20000 \
    -Dmaven.wagon.http.connectionTimeout=10000
```

| | Durée | Artefact |
|---|---|---|
| `.m2` froid (1ᵉʳ build) | **17 min 55 s** | `tnt-bootstrap/target/tnt-bootstrap-0.0.1.jar`, 408 Mo |
| `.m2` chaud (rebuild) | **1 min 15 s** | idem |

À froid, les 18 minutes sont dominées par le réseau (~500 Mo tirés, débit du proxy
oscillant entre ~200 o/s et ~256 ko/s). Le build peut sembler figé plusieurs minutes
sur un module (observé sur `tnt-trust-core [34/53]`) : un `jstack` montre
`RunnableErrorForwarder.awaitTerminationOfAllRunnables`, c'est-à-dire une attente de
téléchargement lent. **Ne pas tuer le build.**

Le seul module runnable est `tnt-bootstrap` ; c'est lui qui produit l'uber-jar
repackagé. Un `mvn … compile` ne produit **aucun** jar — pour embarquer une
modification dans le binaire il faut `package` sur `tnt-bootstrap`.

Vérifier qu'une modif est bien dans l'uber-jar :

```bash
unzip -p tnt-bootstrap/target/tnt-bootstrap-0.0.1.jar \
      BOOT-INF/lib/tnt-go-freelancer-point-back-core-0.0.1.jar > /tmp/gofp.jar
unzip -p /tmp/gofp.jar com/yowyob/tiibntick/core/gofreelancer/application/service/DeliveryNeedApplicationService.class \
  | strings | grep persistPacketIfPresent
```

---

## 4. Infra locale

```bash
cd ~/projets/tiibntick-core/tnt-bootstrap
cp .env.example .env
docker compose up -d postgres redis kafka minio
```

**Quatre conteneurs suffisent.** Ne pas lancer `elasticsearch`, `prometheus`,
`grafana`, `zipkin`, `tileserver`.

État réel en fonctionnement :

```
NAME           STATUS                  PORTS
tnt-redis      Up (healthy)            0.0.0.0:6379->6379/tcp
tnt-postgres   Up (healthy)            0.0.0.0:5433->5432/tcp
tnt-minio      Up (healthy)            0.0.0.0:9000-9001->9000-9001/tcp
tnt-kafka      Up (healthy)            0.0.0.0:9092->9092/tcp, 0.0.0.0:9094->9094/tcp
```

### 4.1 Le README annonce 5432, le compose publie 5433

`README.md:108-162` donne une commande pointant `localhost:5432`. Le compose publie
**5433**. Utiliser 5433, ou forcer `DB_PORT=5432` dans `.env`.

### 4.2 Elasticsearch n'est pas requis

`.env.example` : `ELASTICSEARCH_ENABLED=false`, avec le commentaire « optional — Go
Freelancer search; disabled in bootstrap monolith ». Confirmé par `application.yml`
(`elasticsearch.enabled: ${ELASTICSEARCH_ENABLED:false}`). Plafonner `ES_JAVA_OPTS`
est donc sans objet : ne pas démarrer le service du tout, ~1 Go économisé.

### 4.3 PostGIS est obligatoire

`tnt-bootstrap/docker/postgres/init.sql` tourne en superuser et fait
`CREATE EXTENSION postgis, postgis_topology, "uuid-ossp", pg_trgm`, plus un bloc `DO`
qui crée le rôle/base `tiibntick_test`. Un Postgres installé à la main sans droits
superuser ne convient pas.

---

## 5. Démarrage de l'application

```bash
cd ~/projets/tiibntick-core
DB_HOST=localhost DB_PORT=5433 DB_NAME=tiibntick_core \
DB_USER=tiibntick DB_PASSWORD=tiibntick_pass \
REDIS_HOST=localhost REDIS_PORT=6379 \
KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
MINIO_ENDPOINT=http://localhost:9000 \
ELASTICSEARCH_ENABLED=false \
TNT_SENTIMENT_ENABLED=false \
JWT_AUTO_GENERATE_KEY_PAIR=true \
TNT_AUTH_ALLOW_ANONYMOUS=true \
TNT_ROLES_PROVISION_ON_STARTUP=false \
java -Xms512m -Xmx2g -jar tnt-bootstrap/target/tnt-bootstrap-0.0.1.jar
```

Sortie attendue :

```
OR-Tools 9.8.3296 native libraries loaded successfully
LiquibaseConfig - Database 'tiibntick_core' already exists — nothing to create
LiquibaseConfig - Running TiiBnTick Core Liquibase migrations
                  → jdbc:postgresql://localhost:5433/tiibntick_core?sslmode=disable
NettyWebServer  - Netty started on port 8080 (http)
TiiBnTickApplication - Started TiiBnTickApplication in 64.439 seconds
```

**Les migrations Liquibase passent** (c'était le dernier point d'incertitude, il est levé).

### 5.1 Ne pas utiliser `--spring.profiles.active=test`

Le README le recommande. **C'est un piège.** Le profil `test` de `application.yml`
(à partir de `:435`) code en dur, **sans aucun placeholder `${}`** :

```yaml
r2dbc:
  url: r2dbc:postgresql://localhost:5432/tiibntick_test
  username: tiibntick_test
  password: tiibntick_test_pass
liquibase:
  url: jdbc:postgresql://localhost:5432/tiibntick_test
```

Port 5432 (le compose publie 5433) et **impossible à surcharger par variable
d'environnement**. Utiliser le profil par défaut, lui correctement paramétré
(`r2dbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5433}/${DB_NAME:tiibntick_core}`),
avec les variables ci-dessus.

→ À corriger : re-paramétrer le profil `test` avec des `${}`, ou créer un profil `local`.

### 5.2 Le modèle BERT / sentiment est débrayable

`application.yml:367-370` :

```yaml
tnt.sentiment:
  enabled:   ${TNT_SENTIMENT_ENABLED:true}
  cache-dir: ${TNT_SENTIMENT_CACHE_DIR:/app/.djl.ai}
```

`TNT_SENTIMENT_ENABLED=false` évite le téléchargement DJL/PyTorch (~400 Mo). Noter
que le `cache-dir` par défaut `/app/.djl.ai` est un **chemin de conteneur** : hors
Docker avec le sentiment activé, passer aussi `TNT_SENTIMENT_CACHE_DIR=$HOME/.djl.ai`.

---

## 6. Vérification fonctionnelle — sorties réelles

```bash
curl -s localhost:8080/actuator/health
# {"status":"DOWN","groups":["liveness","readiness"]}
curl -s localhost:8080/actuator/health/liveness   # {"status":"UP"}
curl -s localhost:8080/actuator/health/readiness  # {"status":"UP"}
```

**GOFP — création d'un besoin de livraison avec colis :**

```bash
curl -s -X POST localhost:8080/api/delivery-needs -H 'Content-Type: application/json' -d '{
  "userId": "11111111-1111-1111-1111-111111111111",
  "title": "Colis test binome",
  "paymentMethod": "CASH", "transportMethod": "MOTO", "distance": 7.4, "duration": 25,
  "pickupAddress":   {"address":{"street":"Rue Akwa","quarter":"Akwa","city":"Douala","country":"CM","latitude":4.0511,"longitude":9.7679},"type":"HOME"},
  "deliveryAddress": {"address":{"street":"Rue Bonapriso","city":"Douala","country":"CM","latitude":4.0250,"longitude":9.7100},"type":"WORK"},
  "packet": {"weight":2.5,"width":30.0,"height":20.0,"length":40.0,"thickness":5.0,
             "fragile":true,"isPerishable":false,"description":"Vaisselle emballee",
             "designation":"Carton vaisselle","photoPacket":"https://example.com/colis.jpg"}
}'
```

```json
HTTP 201
{"id":"998b6132-d62b-48c3-b520-7a215703119e",
 "userId":"11111111-1111-1111-1111-111111111111",
 "packetId":"7c60c2a5-898c-4cd0-8705-79e96ac19151",
 "pickupAddressId":"aa8bfc90-701c-49c7-8a1e-59583415d751",
 "deliveryAddressId":"d6dfe3aa-eb90-4b82-a7cc-f23a9923f737",
 "title":"Colis test binome","status":"PENDING","duration":25,
 "paymentMethod":"CASH","transportMethod":"MOTO","distance":7.4,
 "deliveryId":null,"createdAt":1785933068.907682591}
```

**Market :**

```bash
curl -s localhost:8080/api/announcements     # HTTP 200 — []
curl -s localhost:8080/api/delivery-needs    # HTTP 200 — 2100 o
```

### 6.1 Contraintes de validation à connaître

`AddressJsonDeserializer` accepte `latitude`/`longitude` à plat **ou** sous
`coordinates`. En revanche l'adresse exige **au moins `street` ou `landmark`** —
sinon 400 :

```json
{"type":"urn:tiibntick:validation-error","title":"Invalid Request","status":400,
 "detail":"At least one of 'street' or 'landmark' is required",
 "instance":"/api/delivery-needs","properties":{"timestamp":1785933077.7}}
```

⚠️ **Cette enveloppe d'erreur est un RFC 7807 `ProblemDetail`, produit par
`TntGlobalExceptionHandler` (niveau bootstrap)** — pas le `ErrorResponse
{timestamp,status,error,message,path}` du `GlobalExceptionHandler` de GOFP.
Le handler du bootstrap prend le dessus. `docs/CORE_GO_REEL.md` (repo BFF, §5)
décrit l'autre format et doit être corrigé : c'est celui-ci que le BFF verra.

Valeurs acceptées pour `type` d'adresse : `PRIMARY`, `SECONDARY`, `HOME`, `WORK`.

---

## 7. Pourquoi `/actuator/health` est `DOWN`

`liveness` et `readiness` sont tous deux `UP` ; c'est un indicateur hors de ces deux
groupes qui fait basculer l'agrégat. Cause identifiée dans les logs :

```
o.s.b.d.e.h.DataElasticsearchReactiveHealthIndicator - Elasticsearch health check failed
```

**Spring enregistre l'indicateur de santé Elasticsearch même quand
`ELASTICSEARCH_ENABLED=false`**, parce que le flag est une propriété maison
(`tnt.*`) qui ne désactive pas l'auto-configuration `spring-boot-actuator`. L'appli
est parfaitement fonctionnelle malgré le `DOWN`.

Correctifs possibles (aucun appliqué, ce sont des changements de config) :

```
management.health.elasticsearch.enabled=false
# ou
spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.elasticsearch.ReactiveElasticsearchRepositoriesAutoConfiguration
```

Autres avertissements au démarrage, **tous non bloquants** :

```
YowyobKernelBridge - Kernel ping failed: Did not observe any item … within 5000ms
YowyobKernelBridge - YowAuth0 unreachable: …
YowyobKernelBridge - Kernel event bus unreachable: …
KernelPublicKeyProvider - Failed to fetch JWKS from Kernel:
    connection timed out … kernel-core.yowyob.com/173.212.216.20:443
```

→ Le démarrage tape par défaut sur `kernel-core.yowyob.com`
(`jwk-set-uri: ${JWT_JWK_SET_URI:https://kernel-core.yowyob.com/…}`). Hors ligne,
cela coûte une quinzaine de secondes de timeouts. `JWT_AUTO_GENERATE_KEY_PAIR=true`
ne supprime pas ces appels. C'est une dépendance réseau externe invisible dans la doc.

Deux `EndpointId … contains invalid characters` (`tnt-kernel`, `tnt-modules`) :
cosmétique, mais à renommer avant une future montée de version.

---

## 8. Dépendances dures vs souples au démarrage

Établi en démarrant volontairement l'appli sans infra :

| Service | Comportement si absent |
|---|---|
| **Redis** | **fatal** — `realtimeRedisListenerContainer` → `RedisBackedWebSocketBroadcaster` → `notificationBroadcastApplicationService`, le contexte meurt avant même Liquibase |
| **PostgreSQL/PostGIS** | fatal (schéma + R2DBC) |
| Kafka | à lancer, non testé en absence |
| MinIO | non fatal — `IncidentMediaStorageAdapter` logge l'échec de création du bucket et continue |
| Elasticsearch | non fatal — rend seulement `/actuator/health` `DOWN` (§7) |
| Kernel distant | non fatal — timeouts au boot |

**Redis en premier**, donc.

---

## 9. Empreinte mémoire — mesurée en régime établi

```
NAME           CPU %     MEM USAGE
tnt-redis      1.31%     12.84 MiB
tnt-postgres   0.25%    108.5  MiB
tnt-minio      0.00%    130.9  MiB
tnt-kafka     10.69%    904.3  MiB
```

JVM applicative (`-Xms512m -Xmx2g`) : **RSS 1 201 Mo**.

**Total ≈ 2,35 Go**, soit très confortable sur 15 Gio. Kafka est de loin le plus gros
conteneur ; le réduire (`KAFKA_HEAP_OPTS`) est la première piste si la RAM devient
contrainte.

Pic RSS de la JVM Maven pendant le build : ~1,2 Go.

---

## 10. Récapitulatif des accrocs

| # | Accroc | Où | Gravité |
|---|---|---|---|
| 1 | `./mvnw` annoncé mais absent | `README.md` | bas |
| 2 | Dépôt `confluent` qui stalle, pour zéro dépendance réelle | `pom.xml:282-314` | **moyen** — bloque le 1ᵉʳ build |
| 3 | Profil `test` codé en dur, port 5432, non surchargeable | `application.yml:435+` | **haut** — recommandé par le README |
| 4 | README pointe 5432, compose publie 5433 | README vs compose | moyen |
| 5 | Health indicator Elasticsearch actif malgré `ELASTICSEARCH_ENABLED=false` | actuator | moyen — `/health` `DOWN` trompeur |
| 6 | JWKS + bridge Kernel appelés au boot vers `kernel-core.yowyob.com` | `application.yml` | moyen — dépendance réseau invisible |
| 7 | Redis = dépendance dure fail-fast, non documentée | `RedisRealtimeConfig` | bas — mais surprenant |
| 8 | `TNT_SENTIMENT_CACHE_DIR` par défaut = chemin conteneur | `application.yml:367-370` | bas |
| 9 | Enveloppe d'erreur = ProblemDetail du bootstrap, pas l'`ErrorResponse` GOFP | `TntGlobalExceptionHandler` | **moyen** — impacte le contrat BFF |
| 10 | `EndpointId` invalides (`tnt-kernel`, `tnt-modules`) | bootstrap actuator | bas |

---

## 11. Conclusion

**Boot reproductible : oui**, aux conditions suivantes :

1. **Docker installé** et utilisateur dans le groupe `docker`.
2. **Maven 3.9.x installé à la main** (pas de wrapper dans le repo), avec le mirror
   `confluent` → Central dans `~/.m2/settings.xml`.
3. **Quatre conteneurs seulement** : postgres, redis, kafka, minio. Redis en premier.
4. **Profil par défaut, jamais `test`**, avec les variables d'environnement de §5.
5. Accepter que `/actuator/health` affiche `DOWN` tant que l'indicateur Elasticsearch
   n'est pas exclu (§7) — vérifier `liveness`/`readiness` à la place.

Chiffres de référence : build 18 min à froid / **1 min 15 s à chaud**, démarrage
**64 s**, empreinte **~2,35 Go**.

Le code compile intégralement depuis zéro sans credential ni artefact Yowyob, les
migrations Liquibase passent, et les endpoints GOFP et Market répondent sur la vraie
base. Il n'y a plus d'inconnue technique sur la mise en route.

---

*Fichiers créés hors repo : `~/.m2/settings.xml` (mirror confluent) et
`~/.local/opt/apache-maven-3.9.9/`.*
