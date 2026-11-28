"""Generates docs/kafka/guide-kafka-tiibntick.html from the merged code (lot L9). Run from the repository root."""
import html
import json
import os
import re
import sys
from collections import defaultdict
from datetime import date

ROOT = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else ".")
OUT = os.path.join(ROOT, "docs/kafka/guide-kafka-tiibntick.html")
TOPICS_JAVA = os.path.join(ROOT, "foundation/tnt-common-core/src/main/java/com/yowyob/tiibntick/common/kafka/TntTopics.java")
SCHEMAS = os.path.join(ROOT, "foundation/tnt-common-core/src/main/resources/kafka/schemas/v1")
CIBLE = os.path.join(ROOT, "docs/architecture/chantiers/cible-evenements-colis-livraison.md")
SKIP_DIRS = {".git", ".claude", "target", "node_modules", "test"}
NOT_PRODUCERS = {"TntTopics.java", "TntKafkaTopicsConfig.java", "TntModuleRegistry.java"}
AGGREGATE_PLUMBING = {"TntAggregateType.java", "TntEventSchemas.java", "TntAggregateEvent.java"}
AGGREGATE_PRODUCERS = {
    "tnt.parcel.events": [("Parcel → AggregateEvent (outbox)", "logistics/tnt-delivery-core")],
    "tnt.delivery.events": [("Delivery → AggregateEvent (outbox)", "logistics/tnt-delivery-core")],
    "tnt.announcement.events": [("DeliveryAnnouncement → AggregateEvent (outbox)", "logistics/tnt-delivery-core")],
    "tnt.incident.events": [("IncidentAggregateEventOutboxPublisher", "logistics/tnt-incident-core")],
    "tnt.dispute.events": [("DisputeAggregateEventOutboxPublisher", "logistics/tnt-dispute-core")],
}
AGGREGATES = ["tnt.parcel.events", "tnt.delivery.events", "tnt.announcement.events", "tnt.incident.events",
              "tnt.dispute.events"]
EXTERNAL = {
    "tnt.delivery.events": "Mobile BFF, Agency, Link, Market, Point Relais",
    "tnt.parcel.events": "Mobile BFF, Agency, Point Relais",
    "tnt.incident.events": "Mobile BFF, Agency, Link",
    "tnt.dispute.events": "Mobile BFF, Agency, Point Relais",
    "tnt.announcement.events": "Mobile BFF, Point Relais",
    "tnt.billing.wallet.payment.confirmed": "Agency, Market",
}

e = html.escape


def load_properties():
    """Flattened tnt-bootstrap application.yml (first document = default profile), for ${prop} topic names."""
    import yaml
    path = os.path.join(ROOT, "tnt-bootstrap/src/main/resources/application.yml")
    flat = {}

    def walk(node, prefix):
        if isinstance(node, dict):
            for k, v in node.items():
                walk(v, f"{prefix}.{k}" if prefix else str(k))
        else:
            flat[prefix] = node
    with open(path, encoding="utf-8") as fh:
        docs = [d for d in yaml.safe_load_all(fh) if isinstance(d, dict)]
    walk(docs[0] if docs else {}, "")
    return flat


PROPS = None


def prop_value(name, default):
    """Value of ${name:default}: application.yml first (itself possibly ${ENV:default}), then the default."""
    global PROPS
    if PROPS is None:
        PROPS = load_properties()
    v = PROPS.get(name)
    if isinstance(v, str):
        m = re.fullmatch(r"\$\{[^:}]+:([^}]*)\}", v)
        return m.group(1) if m else v
    return default or ""


# ── 1. TntTopics: constant, value, javadoc ───────────────────────────────────────────────
def read_topics():
    src = open(TOPICS_JAVA, encoding="utf-8").read()
    topics = []
    for m in re.finditer(r'(/\*\*(?:(?!\*/).)*\*/\s*)?public static final String (\w+)\s*=\s*"([^"]+)";', src, re.S):
        doc = m.group(1) or ""
        doc = re.sub(r"^\s*/?\*+/?", "", doc, flags=re.M)
        doc = re.sub(r"\{@code ([^}]*)\}", r"\1", doc)
        doc = re.sub(r"\{@link ([^}]*)\}", r"\1", doc)
        doc = " ".join(doc.replace("*/", "").split())
        topics.append({"const": m.group(2), "name": m.group(3), "doc": doc})
    return topics


# ── 2. Main Java sources: listeners and producer references ─────────────────────────────
def java_files():
    for d, dirs, files in os.walk(ROOT):
        dirs[:] = [x for x in dirs if x not in SKIP_DIRS]
        if "/src/main/java" not in d.replace("\\", "/") + "/":
            continue
        for f in files:
            if f.endswith(".java"):
                yield os.path.join(d, f)


def module_of(path):
    rel = os.path.relpath(path, ROOT).replace("\\", "/")
    return rel.split("/src/main/java")[0]


def resolve(expr, consts):
    """Topic expressions of a @KafkaListener: constants, literals, ${prop:default}."""
    out = []
    for c in re.findall(r"TntTopics\.(\w+)", expr):
        if c in consts:
            out.append(consts[c])
    for lit in re.findall(r'"([^"]*)"', expr):
        p = re.fullmatch(r"\$\{([^:}]+)(?::([^}]*))?\}", lit)
        if p:
            out.extend(x.strip() for x in prop_value(p.group(1), p.group(2)).split(",") if x.strip())
        elif lit and not lit.startswith("#") and "." in lit:
            out.append(lit)
    return out


def group_of(block):
    m = re.search(r'groupId\s*=\s*"([^"]*)"', block)
    if not m:
        return "(groupe de la fabrique)"
    g = m.group(1)
    p = re.fullmatch(r"\$\{([^:}]+)(?::([^}]*))?\}", g)
    return (prop_value(p.group(1), p.group(2)) or p.group(1)) if p else g


def scan(topics):
    consts = {t["const"]: t["name"] for t in topics}
    by_value = {t["name"]: t["const"] for t in topics}
    consumers, producers = defaultdict(set), defaultdict(set)
    for path in java_files():
        src = open(path, encoding="utf-8").read()
        name = os.path.basename(path)
        cls = name[:-5]
        listener_spans = []
        for m in re.finditer(r"@KafkaListener\s*\(", src):
            depth, i = 1, m.end()
            while depth and i < len(src):
                depth += {"(": 1, ")": -1}.get(src[i], 0)
                i += 1
            block = src[m.start():i]
            listener_spans.append((m.start(), i))
            tm = re.search(r"topics\s*=\s*(\{[^}]*\}|[^,)]+)", block, re.S)
            for t in resolve(tm.group(1) if tm else "", consts):
                consumers[t].add((cls, group_of(block), module_of(path)))
        if name in NOT_PRODUCERS:
            continue
        outside = src
        for a, b in reversed(listener_spans):
            outside = outside[:a] + outside[b:]
        listened = {t for t, cs in consumers.items() for c in cs if c[0] == cls}
        for c in set(re.findall(r"TntTopics\.(\w+)", outside)):
            if c in consts and consts[c] not in listened and name not in AGGREGATE_PLUMBING:
                producers[consts[c]].add((cls, module_of(path)))
        for lit in set(re.findall(r'"(tnt\.[a-z0-9_.\-]+)"', outside)):
            if lit in by_value and lit not in listened:
                producers[lit].add((cls, module_of(path)))
    # The aggregate topics are written by the outbox: the topic comes from TntAggregateType, the event from the aggregate.
    for topic, publishers in AGGREGATE_PRODUCERS.items():
        producers[topic] = {(p, m) for p, m in publishers}
    return consumers, producers


# ── 3. Schemas ───────────────────────────────────────────────────────────────────────────
def schemas():
    files = []
    for d, _, fs in os.walk(SCHEMAS):
        for f in sorted(fs):
            if f.endswith(".json"):
                p = os.path.join(d, f)
                files.append((os.path.relpath(p, SCHEMAS), json.load(open(p, encoding="utf-8"))))
    return sorted(files)


# ── 4. Old → new table (spec §3.8) ───────────────────────────────────────────────────────
def old_new():
    lines = open(CIBLE, encoding="utf-8").read().split("\n")
    i = next(k for k, l in enumerate(lines) if l.startswith("### 3.8"))
    rows = []
    for l in lines[i + 1:]:
        if l.startswith("### "):
            break
        if l.startswith("|") and not l.startswith("|---") and "Ancien topic" not in l:
            cells = [c.strip() for c in l.strip().strip("|").split("|")]
            if len(cells) >= 4:
                rows.append(cells[:4])
    return rows


def md(text):
    text = e(text)
    text = re.sub(r"`([^`]+)`", r"<code>\1</code>", text)
    return re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", text)


def main():
    topics = read_topics()
    consumers, producers = scan(topics)
    sch = schemas()
    rows = old_new()
    groups = defaultdict(set)
    for t, cs in consumers.items():
        for cls, g, mod in cs:
            groups[g].add((cls, t))

    def who(items, with_group):
        if not items:
            return '<span class="muted">—</span>'
        out = []
        for it in sorted(items):
            if with_group:
                cls, g, mod = it
                out.append(f"<code>{e(cls)}</code> <span class=\"muted\">{e(mod.split('/')[-1])} · groupe "
                           f"<code>{e(g)}</code></span>")
            else:
                cls, mod = it
                out.append(f"<code>{e(cls)}</code> <span class=\"muted\">{e(mod.split('/')[-1])}</span>")
        return "<br>".join(out)

    topic_rows = []
    for t in sorted(topics, key=lambda x: (x["name"] not in AGGREGATES, x["name"])):
        n = t["name"]
        badge = ' <span class="tag agg">agrégat</span>' if n in AGGREGATES else ""
        if n == "tnt.dlq":
            badge = ' <span class="tag dlq">file morte</span>'
        ext = EXTERNAL.get(n, "")
        topic_rows.append(
            f'<tr id="t-{e(n)}"><td><code>{e(n)}</code>{badge}<div class="muted small"><code>TntTopics.{e(t["const"])}</code></div></td>'
            f'<td class="small">{md(t["doc"]) or "<span class=muted>—</span>"}</td>'
            f'<td class="small">{who(producers.get(n), False)}</td>'
            f'<td class="small">{who(consumers.get(n), True)}</td>'
            f'<td class="small">{e(ext) or "<span class=muted>—</span>"}</td></tr>')

    group_rows = "".join(
        f"<tr><td><code>{e(g)}</code></td><td class=\"small\">"
        + "<br>".join(f"<code>{e(c)}</code> ← <code>{e(t)}</code>" for c, t in sorted(v)) + "</td></tr>"
        for g, v in sorted(groups.items()))

    schema_blocks = "".join(
        f"<details><summary><code>{e(name)}</code> <span class=\"muted\">{e(s.get('title', ''))}</span></summary>"
        f"<pre>{e(json.dumps(s, indent=2, ensure_ascii=False))}</pre></details>"
        for name, s in sch)
    detail_types = sorted(n.split("/")[-1].replace(".schema.json", "") for n, _ in sch if n.startswith("details/"))

    old_rows = "".join("<tr>" + "".join(f"<td class=\"small\">{md(c)}</td>" for c in r) + "</tr>" for r in rows)
    unconsumed = [t["name"] for t in topics if not consumers.get(t["name"])]
    unproduced = [t["name"] for t in topics if not producers.get(t["name"]) and t["name"] != "tnt.dlq"]

    page = TEMPLATE.format(
        today=date.today().isoformat(),
        ntopics=len(topics),
        nconsumed=len(topics) - len(unconsumed),
        ngroups=len(groups),
        topic_rows="\n".join(topic_rows),
        group_rows=group_rows,
        schema_blocks=schema_blocks,
        detail_types=", ".join(f"<code>{e(d)}</code>" for d in detail_types),
        old_rows=old_rows,
        unconsumed=", ".join(f"<code>{e(u)}</code>" for u in unconsumed) or "aucun",
        unproduced=", ".join(f"<code>{e(u)}</code>" for u in unproduced) or "aucun",
    )
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    open(OUT, "w", encoding="utf-8").write(page)
    print(f"{OUT}: {len(topics)} topics, {sum(len(v) for v in consumers.values())} listener bindings, "
          f"{len(groups)} groups, {len(sch)} schemas, {len(rows)} old->new rows")


TEMPLATE = """<!doctype html>
<html lang="fr">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Guide Kafka TiiBnTick</title>
<style>
:root {{ --bg:#ffffff; --fg:#1c2330; --muted:#5d6878; --line:#d9dee6; --soft:#f3f5f8; --accent:#1f5fbf; --agg:#0f7b4a; --dlq:#b4361e; }}
@media (prefers-color-scheme: dark) {{ :root:not([data-theme="light"]) {{ --bg:#12161d; --fg:#e4e8ef; --muted:#9aa5b5; --line:#2b3340; --soft:#1a202a; --accent:#7fb0ff; --agg:#5fd39a; --dlq:#ff8a70; }} }}
:root[data-theme="dark"] {{ --bg:#12161d; --fg:#e4e8ef; --muted:#9aa5b5; --line:#2b3340; --soft:#1a202a; --accent:#7fb0ff; --agg:#5fd39a; --dlq:#ff8a70; }}
* {{ box-sizing:border-box; }}
body {{ margin:0; background:var(--bg); color:var(--fg); font:15px/1.55 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif; }}
main {{ max-width:1180px; margin:0 auto; padding:24px 16px 64px; }}
h1 {{ font-size:1.7rem; margin:.2em 0; }} h2 {{ margin-top:2.2em; border-bottom:1px solid var(--line); padding-bottom:.25em; }}
h3 {{ margin-top:1.6em; }}
a {{ color:var(--accent); }}
code, pre {{ font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace; font-size:.88em; }}
code {{ background:var(--soft); padding:.05em .3em; border-radius:4px; overflow-wrap:anywhere; }}
pre {{ background:var(--soft); padding:12px; border-radius:6px; overflow-x:auto; }}
pre code {{ background:none; padding:0; }}
.table-wrap {{ overflow-x:auto; }}
table {{ border-collapse:collapse; width:100%; margin:.6em 0; }}
th, td {{ border:1px solid var(--line); padding:6px 8px; vertical-align:top; text-align:left; }}
th {{ background:var(--soft); position:sticky; top:0; }}
.small {{ font-size:.86em; }} .muted {{ color:var(--muted); }}
.tag {{ font-size:.72em; font-weight:600; padding:.05em .45em; border-radius:9px; border:1px solid currentColor; margin-left:.3em; }}
.agg {{ color:var(--agg); }} .dlq {{ color:var(--dlq); }}
.note {{ border-left:4px solid var(--accent); background:var(--soft); padding:8px 12px; border-radius:4px; }}
nav ol {{ columns:2; }} @media (max-width:640px) {{ nav ol {{ columns:1; }} }}
details {{ margin:.3em 0; }} summary {{ cursor:pointer; }}
input#q {{ width:100%; max-width:420px; padding:6px 8px; border:1px solid var(--line); border-radius:6px; background:var(--bg); color:var(--fg); }}
</style>
</head>
<body>
<main>
<h1>Guide Kafka TiiBnTick</h1>
<p class="muted">Lot L9 <code>evt-guide-kafka-html</code> · fichier local du dépôt, à ne pas publier en ligne · généré le {today}
depuis le code fusionné par <code>docs/kafka/gen_kafka_guide.py</code> (relancer après chaque changement de topic).</p>
<p><strong>{ntopics}</strong> topics déclarés (<code>TntTopics</code>), dont <strong>{nconsumed}</strong> lus par au moins
un consommateur du Core ; <strong>{ngroups}</strong> groupes de consommateurs internes.</p>

<nav><ol>
<li><a href="#connexion">Se connecter au cluster</a></li>
<li><a href="#agregats">Les cinq topics d'agrégat</a></li>
<li><a href="#distinguer">Distinguer les événements d'un même topic</a></li>
<li><a href="#enveloppe">Enveloppe, en-têtes et schémas</a></li>
<li><a href="#catalogue">Catalogue des topics</a></li>
<li><a href="#groupes">Groupes de consommateurs du Core</a></li>
<li><a href="#externes">Plateformes externes : groupes et ACL</a></li>
<li><a href="#pratiques">Bonnes pratiques</a></li>
<li><a href="#ancien-nouveau">Ancien → nouveau</a></li>
<li><a href="#dlq">Relancer un message de <code>tnt.dlq</code></a></li>
<li><a href="#exploitation">Exploitation : anciens topics</a></li>
</ol></nav>

<h2 id="connexion">1. Se connecter au cluster</h2>
<ul>
<li>Adresse : propriété <code>spring.kafka.bootstrap-servers</code> = <code>${{KAFKA_BOOTSTRAP:localhost:9092}}</code>.</li>
<li>Clé : chaîne UTF-8 (<code>StringSerializer</code>) ; valeur : JSON UTF-8 en octets (<code>ByteArraySerializer</code>).</li>
<li>Producteur du Core : <code>acks=all</code>, idempotence activée, une requête en vol par connexion (ordre par clé).
Les événements d'agrégat passent par l'outbox de <code>yow-event-kernel</code> : publication « au moins une fois ».</li>
<li>Topics créés au démarrage (<code>TntKafkaTopicsConfig</code>) : <code>KAFKA_PARTITIONS</code> partitions (3),
réplication <code>KAFKA_REPLICATION</code> (1 en local) ; topics d'agrégat en <code>cleanup.policy=delete</code>,
rétention <code>TNT_KAFKA_AGGREGATE_RETENTION_MS</code> (30 jours) pour qu'une plateforme externe puisse rattraper son retard.</li>
<li>Démarrage d'un nouveau lecteur : <code>auto.offset.reset=earliest</code> lit les 30 derniers jours ; un projecteur
doit donc supporter un historique complet (voir <a href="#pratiques">bonnes pratiques</a>).</li>
</ul>

<h2 id="agregats">2. Les cinq topics d'agrégat</h2>
<p>Un topic par agrégat ; chaque enregistrement est un <strong>événement complet</strong> : instantané masqué après le
changement (<code>payload</code>), état précédent (<code>previousState</code>), cause (<code>trigger</code>, <code>actor</code>)
et données propres au type (<code>details</code>). Clé = identifiant de l'agrégat.</p>
<div class="table-wrap"><table>
<tr><th>Topic</th><th>Clé</th><th>Types d'événements</th></tr>
<tr><td><code>tnt.delivery.events</code></td><td><code>deliveryId</code></td><td class="small">13 transitions :
<code>DeliveryCreated</code>, <code>DeliveryAssigned</code>, <code>DeliveryPickedUp</code>, <code>DeliveryInTransit</code>,
<code>DeliveryAtRelayHub</code>, <code>DeliveryOutForDelivery</code>, <code>DeliveryAttemptFailed</code>,
<code>DeliveryDelivered</code>, <code>DeliveryFailed</code>, <code>DeliveryCancelled</code>, <code>DeliveryIncidentReported</code>,
<code>DeliveryPausedByIncident</code>, <code>DeliveryIncidentHandover</code> ; sans transition : <code>DeliveryEtaUpdated</code>,
<code>DeliveryVehicleChanged</code>, <code>DeliveryFreelancerOrgAssigned</code>, <code>DeliveryPaymentUpdated</code>,
<code>DeliveryAlertRaised</code> (<code>PICKUP_LATE</code>, <code>DELIVERY_LATE</code>, <code>ETA_EXCEEDED</code>,
<code>GPS_SIGNAL_LOST</code>, <code>TRAJECTORY_DEVIATION</code>, <code>PROLONGED_STOP</code>, <code>GEOFENCE_EXIT</code>,
<code>RELAY_STORAGE_EXPIRING</code>), <code>DeliveryAlertCleared</code></td></tr>
<tr><td><code>tnt.parcel.events</code></td><td><code>parcelId</code></td><td class="small">14 transitions :
<code>ParcelCreated</code>, <code>ParcelAssigned</code>, <code>ParcelPickedUp</code>, <code>ParcelInTransit</code>,
<code>ParcelAtRelayHub</code>, <code>ParcelOutForDelivery</code>, <code>ParcelDeliveryFailed</code>, <code>ParcelDelivered</code>,
<code>ParcelUnderDispute</code>, <code>ParcelReturnRequested</code>, <code>ParcelReturnInTransit</code>, <code>ParcelReturned</code>,
<code>ParcelLost</code>, <code>ParcelDamaged</code> ; sans transition : <code>ParcelCustodyChanged</code>, <code>ParcelDetailsUpdated</code></td></tr>
<tr><td><code>tnt.incident.events</code></td><td><code>incidentId</code></td><td class="small">18 transitions
(<code>IncidentReported</code> … <code>IncidentCancelled</code>, une par état) ; sans transition :
<code>IncidentEscalatedToDispute</code>, <code>IncidentHandoverConfirmed</code>, <code>IncidentHandlerAssigned</code></td></tr>
<tr><td><code>tnt.dispute.events</code></td><td><code>disputeId</code></td><td class="small">10 transitions
(<code>DisputeOpen</code> … <code>DisputeClosedExpired</code>) ; sans transition : <code>DisputeEvidenceAdded</code></td></tr>
<tr><td><code>tnt.announcement.events</code></td><td><code>announcementId</code> (candidatures comprises)</td><td class="small">
6 transitions : <code>AnnouncementDraft</code>, <code>AnnouncementPublished</code>, <code>AnnouncementInNegotiation</code>,
<code>AnnouncementAssigned</code>, <code>AnnouncementCompleted</code>, <code>AnnouncementCancelled</code> ; sans transition :
<code>AnnouncementUpdated</code>, <code>AnnouncementResponseReceived</code>, <code>AnnouncementResponseWithdrawn</code></td></tr>
</table></div>

<h2 id="distinguer">3. Distinguer les événements d'un même topic</h2>
<p>Tous les types d'événements continuent d'être produits et traités ; regrouper les topics n'en supprime aucun. Un
consommateur choisit ce qui le concerne ainsi :</p>
<ol>
<li><strong><code>eventType</code></strong> (corps) ou l'en-tête <code>X-Yow-Event-Type</code> : le nom exact
(<code>DeliveryDelivered</code>, <code>ParcelReturnRequested</code>…). Filtrer sur l'en-tête évite de désérialiser le corps.</li>
<li><strong><code>aggregateType</code></strong> (<code>PARCEL</code>, <code>DELIVERY</code>, <code>ANNOUNCEMENT</code>,
<code>INCIDENT</code>, <code>DISPUTE</code>, en-tête <code>X-Yow-Aggregate-Type</code>) : un seul par topic.</li>
<li><strong>La transition</strong> <code>previousState.status</code> → <code>payload.status</code> : un même type peut suffire
(ex. <code>ParcelReturnRequested</code>), mais une réaction précise lit la transition et les champs (ex. l'agence
expire un colis sur <code>AT_RELAY_HUB → RETURN_REQUESTED</code> avec <code>returnCause = NOT_COLLECTED_AT_RELAY</code>).
<code>previousState = null</code> = création.</li>
<li><strong><code>details</code></strong> : les données propres au type (motif d'échec, alerte, indemnisation…), décrites
par un schéma par type (<a href="#enveloppe">§4</a>).</li>
<li>Un enregistrement sans <code>schemaVersion</code> (ancien format) est ignoré : <code>TntAggregateEvent.isAggregateEvent</code>.</li>
</ol>
<p>Ordre et doublons : la clé est l'id de l'agrégat (même partition, donc ordre de publication), mais l'outbox peut rejouer
un ancien enregistrement. <code>aggregateVersion</code> croît d'une unité par sauvegarde : un projecteur ignore une
version ≤ la dernière appliquée. Un effet unique (paiement, ouverture de litige) se protège par <code>eventId</code>
(table <code>event_bus.processed_events</code> dans le Core). Règle du Core : une transition par sauvegarde.</p>
<pre><code>// Exemple (Java, Spring Kafka) — ne réagir qu'à la remise au destinataire
@KafkaListener(topics = TntTopics.DELIVERY_EVENTS, groupId = "ma-plateforme-delivery")
void on(ConsumerRecord&lt;String, String&gt; r) throws Exception {{
    JsonNode ev = mapper.readTree(r.value());
    if (!TntAggregateEvent.isAggregateEvent(ev) || !"DeliveryDelivered".equals(ev.path("eventType").asText())) return;
    if (ev.path("aggregateVersion").asLong() &lt;= lastApplied(ev.path("aggregateId").asText())) return; // rejeu
    apply(ev.path("payload"));
}}</code></pre>

<h2 id="enveloppe">4. Enveloppe, en-têtes et schémas</h2>
<p>Champs obligatoires : <code>eventId</code>, <code>eventType</code>, <code>schemaVersion</code>, <code>occurredAt</code>,
<code>aggregateType</code>, <code>aggregateId</code>, <code>aggregateVersion</code>, <code>tenantId</code>, <code>trigger</code>,
<code>actor</code> (<code>type</code> parmi <code>COURIER</code>, <code>SENDER</code>, <code>RECIPIENT</code>, <code>RELAY_OP</code>,
<code>AGENCY</code>, <code>SUPPORT</code>, <code>SYSTEM</code>), <code>previousState</code>, <code>payload</code>, <code>details</code>.
Facultatifs : <code>correlationId</code>, <code>causationId</code>. Des champs peuvent être ajoutés sans préavis ; un
changement cassant incrémente <code>schemaVersion</code>.</p>
<p>En-têtes : <code>X-Yow-Event-Id</code>, <code>X-Yow-Event-Type</code>, <code>X-Yow-Aggregate-Type</code>,
<code>X-Yow-Aggregate-Id</code>, <code>X-Yow-Schema-Version</code>, <code>X-Yow-Tenant-Id</code>, <code>X-Yow-Correlation-Id</code>,
<code>X-Yow-Envelope-Id</code>, <code>X-Yow-Solution-Code</code> ; <code>X-Yow-Replay: true</code> seulement sur une relance
manuelle (<a href="#dlq">§10</a>). Les en-têtes reprennent le corps à l'identique.</p>
<p>Données personnelles masquées : pas de téléphone ni d'adresse exacte (quartier, ville, pays, coordonnées arrondies à
2 décimales, environ 1,1 km), nom affiché « Prénom N. ». Le détail s'obtient par l'API, avec les droits.</p>
<p>Schémas JSON (draft 2020-12, <code>foundation/tnt-common-core/src/main/resources/kafka/schemas/v1</code>) ; types
ayant un schéma de <code>details</code> : {detail_types}.</p>
{schema_blocks}

<h2 id="catalogue">5. Catalogue des topics</h2>
<p class="muted small">Producteurs = classes du Core qui référencent le topic hors d'un <code>@KafkaListener</code> ;
consommateurs = <code>@KafkaListener</code> du Core avec leur groupe. Topics sans consommateur interne (sortants, lus
par les plateformes ou en attente d'usage) : {unconsumed}. Topics sans producteur relevé (alimentés par une propriété
ou de l'extérieur) : {unproduced}.</p>
<input id="q" type="search" placeholder="Filtrer (nom, classe, groupe…)" aria-label="Filtrer les topics">
<div class="table-wrap"><table id="cat">
<tr><th>Topic</th><th>Rôle</th><th>Producteurs</th><th>Consommateurs (groupe)</th><th>Lecture externe</th></tr>
{topic_rows}
</table></div>

<h2 id="groupes">6. Groupes de consommateurs du Core</h2>
<p class="muted small">Un groupe reçoit chaque enregistrement une fois ; deux groupes reçoivent tous les deux. Ne jamais
réutiliser un de ces noms hors du Core.</p>
<div class="table-wrap"><table><tr><th>Groupe</th><th>Listeners (classe ← topic)</th></tr>{group_rows}</table></div>

<h2 id="externes">7. Plateformes externes : un groupe chacune, ACL lecture seule</h2>
<p>Décision G.4 : BFF Mobile, Agency, Link, Market et Point Relais lisent directement le cluster partagé, chacune avec
<strong>son propre groupe</strong> (préfixe conseillé <code>ext-&lt;plateforme&gt;-</code>) et un compte en <strong>lecture
seule</strong> : lire les topics utiles et décrire/valider son groupe, jamais écrire. Exemple pour Agency :</p>
<pre><code>kafka-acls.sh --bootstrap-server $KAFKA --add --allow-principal User:agency \\
  --operation Read --operation Describe --topic tnt.delivery.events --topic tnt.parcel.events \\
  --topic tnt.incident.events --topic tnt.dispute.events --topic tnt.billing.wallet.payment.confirmed
kafka-acls.sh --bootstrap-server $KAFKA --add --allow-principal User:agency \\
  --operation Read --operation Describe --group ext-agency- --resource-pattern-type prefixed</code></pre>
<p>La gestion de ces comptes et ACL est un besoin de la plateforme d'administration (spec §5.5).</p>

<h2 id="pratiques">8. Bonnes pratiques</h2>
<ul>
<li><strong>Idempotence par <code>eventId</code></strong> : tout effet unique (paiement, notification, création) doit
survivre à un rejeu.</li>
<li><strong>Ordre par <code>aggregateVersion</code></strong> : ignorer une version déjà appliquée ou plus ancienne.</li>
<li><strong>Ignorer les champs inconnus</strong> (<code>FAIL_ON_UNKNOWN_PROPERTIES=false</code>) et les types
d'événements inconnus : de nouveaux s'ajoutent sans changer <code>schemaVersion</code>.</li>
<li>Filtrer tôt sur l'en-tête <code>X-Yow-Event-Type</code> ; acquitter les événements qui ne concernent pas le lecteur.</li>
<li>Dans le Core, attendre la fin du travail réactif dans le listener (<code>ReactiveListenerSupport.await</code>, 30 s
au plus) : un échec est retenté 4 fois (1 s, ×2) puis envoyé dans <code>tnt.dlq</code> ; ne jamais avaler une erreur
(<code>onErrorResume(e -&gt; Mono.empty())</code>).</li>
<li>Ne pas passer de clé explicite à l'outbox : la clé est l'id de l'agrégat.</li>
<li>Ne jamais publier de données personnelles en clair ; passer par <code>TntEventMasking</code>.</li>
<li>Un topic nouveau = une constante <code>TntTopics</code> + un <code>NewTopic</code> (test <code>KafkaAsyncWiringTest.everyTopicConstantIsDeclared</code>) + une ligne de ce guide.</li>
</ul>

<h2 id="ancien-nouveau">9. Ancien → nouveau (spec §3.8)</h2>
<p class="muted small">Les anciens topics ne sont plus produits ni lus depuis le lot L8 ; ils sont retirés de
<code>TntTopics</code>.</p>
<div class="table-wrap"><table><tr><th>Ancien topic</th><th>Nouveau</th><th>Type(s) d'événement</th><th>Lot</th></tr>{old_rows}</table></div>

<h2 id="dlq">10. Relancer un message de <code>tnt.dlq</code></h2>
<p>Un listener du Core qui échoue encore après 4 tentatives publie l'enregistrement dans <code>tnt.dlq</code> avec sa
clé, sa valeur, ses en-têtes et <code>kafka_dlt-original-topic</code>, <code>-partition</code>, <code>-offset</code>,
<code>-original-consumer-group</code>, <code>-exception-fqcn</code>, <code>-exception-message</code>. Journal
<code>KAFKA_RECORD_DEAD_LETTERED</code>, compteur <code>yow_event_consumer_dead_lettered_total{{topic,group}}</code>.</p>
<ol>
<li>Lire la file avec ses en-têtes :
<pre><code>kafka-console-consumer.sh --bootstrap-server $KAFKA --topic tnt.dlq --from-beginning \\
  --property print.key=true --property print.headers=true --property print.timestamp=true</code></pre></li>
<li>Comprendre la cause (<code>kafka_dlt-exception-message</code>), corriger (code, donnée, configuration).</li>
<li>Republier l'enregistrement <strong>à l'identique</strong> (même clé, même valeur) sur
<code>kafka_dlt-original-topic</code>, avec l'en-tête <code>X-Yow-Replay: true</code> : c'est le seul cas où l'anti-doublon
<code>event_bus.processed_events</code> laisse repasser un <code>eventId</code> déjà traité.
<pre><code>printf '%s\\t%s\\t%s\\n' 'X-Yow-Replay:true' '&lt;clé&gt;' '&lt;valeur JSON sur une ligne&gt;' | \\
kafka-console-producer.sh --bootstrap-server $KAFKA --topic &lt;topic d'origine&gt; \\
  --property parse.headers=true --property parse.key=true --property key.separator=$'\\t'</code></pre></li>
<li>Noter la relance (qui, quoi, pourquoi) : elle relève du journal d'administration. Une route d'API de relance est à
construire (spec d'administration §5.5) ; ce mode opératoire la remplace en attendant.</li>
</ol>
<p class="note">Attention : relancer un enregistrement dont l'effet a déjà été appliqué en partie peut le réappliquer
(une notification peut repartir). Vérifier l'état avant.</p>

<h2 id="exploitation">11. Exploitation : anciens topics d'un cluster existant</h2>
<p>Spring Kafka crée les topics manquants mais n'en supprime aucun. Sur un cluster existant, tout topic <code>tnt.*</code>
absent du <a href="#catalogue">catalogue</a> (hors <code>tnt.trust.*</code>) est un ancien topic : vider les lecteurs
(offsets en fin), vérifier qu'aucune plateforme externe ne le lit, puis <code>kafka-topics.sh --delete --topic &lt;nom&gt;</code>.
La liste complète (42 migrés, 22 morts) est dans le journal technique du chantier, lot L8. La rétention de
<code>tnt.delivery.events</code> existant avant le lot L0 doit être réglée une fois
(<code>kafka-configs.sh --alter --entity-type topics --entity-name tnt.delivery.events --add-config retention.ms=2592000000</code>).</p>
</main>
<script>
(function () {{
  var q = document.getElementById('q'), rows = document.querySelectorAll('#cat tr[id]');
  q.addEventListener('input', function () {{
    var v = q.value.toLowerCase();
    rows.forEach(function (r) {{ r.style.display = r.textContent.toLowerCase().indexOf(v) >= 0 ? '' : 'none'; }});
  }});
}})();
</script>
</body>
</html>
"""

if __name__ == "__main__":
    main()
