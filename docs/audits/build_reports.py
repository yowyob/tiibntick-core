#!/usr/bin/env python3
"""Génère les rapports HTML d'audit à partir des markdown bruts (raw/*.md).

Usage: python3 build_reports.py
Regénère tous les fichiers audit-*.html à partir de raw/audit-*.md.
index.html et executive-summary.html sont écrits séparément (fichiers .md dédiés dans raw/).
"""
import re
import html as html_mod
from pathlib import Path

import markdown

HERE = Path(__file__).parent
RAW = HERE / "raw"

REPORTS = [
    ("audit-1-architecture", "Audit n°1", "Architecture globale", "🏛️"),
    ("audit-2-inter-modules", "Audit n°2", "Connexions inter-modules", "🔗"),
    ("audit-3-yow-event-kernel", "Audit n°3", "yow-event-kernel", "📨"),
    ("audit-4-yow-i18n-kernel", "Audit n°4", "yow-i18n-kernel", "🌍"),
    ("audit-5-kafka", "Audit n°5", "Kafka", "🧵"),
    ("audit-6-scalabilite", "Audit n°6", "Scalabilité", "📈"),
    ("audit-7-system-design", "Audit n°7", "System Design & Sécurité", "🧭"),
]

NAV_EXTRA = [
    ("executive-summary", "Synthèse", "Résumé exécutif global", "📋"),
]

REMEDIATION = [
    ("implementation-plan", "Plan", "Plan d'implémentation — vue d'ensemble", "🗂️"),
    ("phase-0-critical", "P0", "Phase 0 — Bloquant production", "🚨"),
    ("phase-1-hardening", "P1", "Phase 1 — Durcissement", "🛡️"),
    ("phase-2-target-architecture", "P2", "Phase 2 — Architecture cible", "🚀"),
    ("workstream-payment-billing-kernel-delegation", "Chantier", "Délégation paiement/facturation au Kernel", "💳"),
]

# Phase files whose checkbox counts roll up into the aggregate progress shown
# on the implementation-plan overview page.
PROGRESS_TRACKED = ["phase-0-critical", "phase-1-hardening", "phase-2-target-architecture",
                     "workstream-payment-billing-kernel-delegation"]

CSS = """
:root {
  --bg: #f6f7f9; --panel: #ffffff; --text: #1c2430; --muted: #5b6675;
  --border: #e3e7ec; --accent: #0f5fa8; --accent-soft: #e8f1fa;
  --code-bg: #f0f2f5; --sidebar-bg: #101823; --sidebar-text: #c7d0dc;
  --crit: #c62828; --high: #e65100; --med: #f9a825; --low: #2e7d32;
}
@media (prefers-color-scheme: dark) {
  :root {
    --bg: #0e1319; --panel: #161d27; --text: #dbe2ea; --muted: #8b97a5;
    --border: #263140; --accent: #5aa2e0; --accent-soft: #17273a;
    --code-bg: #1d2733; --sidebar-bg: #0a0f15; --sidebar-text: #a9b4c0;
  }
}
* { box-sizing: border-box; }
body { margin: 0; font-family: "Segoe UI", system-ui, -apple-system, sans-serif;
  background: var(--bg); color: var(--text); line-height: 1.65; }
.layout { display: flex; min-height: 100vh; }
nav.sidebar { width: 270px; flex-shrink: 0; background: var(--sidebar-bg);
  color: var(--sidebar-text); padding: 1.4rem 1rem; position: sticky; top: 0;
  height: 100vh; overflow-y: auto; }
nav.sidebar .brand { font-size: 1.05rem; font-weight: 700; color: #fff;
  margin-bottom: .2rem; }
nav.sidebar .brand small { display:block; font-weight: 400; color: var(--sidebar-text);
  font-size: .75rem; margin-top: .2rem; }
nav.sidebar ul { list-style: none; padding: 0; margin: 1.2rem 0; }
nav.sidebar li a { display: block; color: var(--sidebar-text); text-decoration: none;
  padding: .45rem .6rem; border-radius: 6px; font-size: .86rem; }
nav.sidebar li a:hover { background: rgba(255,255,255,.08); color: #fff; }
nav.sidebar li a.active { background: var(--accent); color: #fff; }
nav.sidebar .section-label { text-transform: uppercase; letter-spacing: .08em;
  font-size: .68rem; color: #6b7683; margin: 1.4rem 0 .4rem .4rem; }
main { flex: 1; min-width: 0; padding: 2.2rem 3rem 4rem; max-width: 1100px; }
header.page { border-bottom: 1px solid var(--border); padding-bottom: 1.2rem;
  margin-bottom: 1.6rem; }
header.page .kicker { color: var(--accent); font-weight: 600; font-size: .85rem;
  text-transform: uppercase; letter-spacing: .06em; }
header.page h1 { margin: .25rem 0 .4rem; font-size: 1.9rem; }
header.page .meta { color: var(--muted); font-size: .85rem; }
h2 { margin-top: 2.4rem; padding-bottom: .35rem; border-bottom: 1px solid var(--border);
  font-size: 1.35rem; }
h3 { margin-top: 1.8rem; font-size: 1.08rem; }
h4 { margin-top: 1.4rem; font-size: .98rem; }
a { color: var(--accent); }
code { background: var(--code-bg); padding: .12em .38em; border-radius: 4px;
  font-size: .84em; font-family: "JetBrains Mono", Consolas, monospace; }
pre { background: var(--code-bg); border: 1px solid var(--border); border-radius: 8px;
  padding: 1rem; overflow-x: auto; font-size: .82rem; line-height: 1.5; }
pre code { background: none; padding: 0; }
.table-wrap { overflow-x: auto; margin: 1rem 0; border: 1px solid var(--border);
  border-radius: 8px; }
table { border-collapse: collapse; width: 100%; font-size: .85rem; background: var(--panel); }
th, td { border-bottom: 1px solid var(--border); padding: .55rem .75rem; text-align: left;
  vertical-align: top; }
th { background: var(--accent-soft); color: var(--text); font-weight: 600;
  white-space: nowrap; }
tr:last-child td { border-bottom: none; }
blockquote { border-left: 4px solid var(--accent); background: var(--accent-soft);
  margin: 1rem 0; padding: .7rem 1rem; border-radius: 0 8px 8px 0; }
blockquote p { margin: .3rem 0; }
.badge { display: inline-block; padding: .1em .6em; border-radius: 999px;
  font-size: .74rem; font-weight: 700; color: #fff; white-space: nowrap; }
.badge.crit { background: var(--crit); } .badge.high { background: var(--high); }
.badge.med { background: var(--med); color: #333; } .badge.low { background: var(--low); }
.mermaid { background: var(--panel); border: 1px solid var(--border); border-radius: 8px;
  padding: 1rem; margin: 1rem 0; text-align: center; overflow-x: auto; }
.toc { background: var(--panel); border: 1px solid var(--border); border-radius: 8px;
  padding: 1rem 1.4rem; margin: 1.4rem 0; font-size: .88rem; }
.toc > ul { margin: .3rem 0; padding-left: 1.1rem; }
.toc .toctitle { font-weight: 700; }
.cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
  gap: 1rem; margin: 1.4rem 0; }
.card { background: var(--panel); border: 1px solid var(--border); border-radius: 10px;
  padding: 1.1rem 1.2rem; text-decoration: none; color: var(--text); display: block; }
.card:hover { border-color: var(--accent); }
.card .icon { font-size: 1.5rem; }
.card h3 { margin: .4rem 0 .2rem; font-size: 1rem; }
.card p { margin: 0; color: var(--muted); font-size: .82rem; }
.stat-row { display: flex; gap: 1rem; flex-wrap: wrap; margin: 1.2rem 0; }
.stat { background: var(--panel); border: 1px solid var(--border); border-radius: 10px;
  padding: .8rem 1.2rem; min-width: 130px; }
.stat .num { font-size: 1.6rem; font-weight: 800; }
.stat .lbl { font-size: .74rem; color: var(--muted); text-transform: uppercase;
  letter-spacing: .05em; }
.stat.crit .num { color: var(--crit); } .stat.high .num { color: var(--high); }
.stat.med .num { color: var(--med); } .stat.low .num { color: var(--low); }
footer { margin-top: 3rem; padding-top: 1rem; border-top: 1px solid var(--border);
  color: var(--muted); font-size: .78rem; }
.progress-block { background: var(--panel); border: 1px solid var(--border); border-radius: 10px;
  padding: 1rem 1.2rem; margin: 1rem 0 1.6rem; }
.progress-block .progress-label { display: flex; justify-content: space-between; align-items: baseline;
  font-size: .85rem; margin-bottom: .5rem; }
.progress-block .progress-label b { font-size: 1rem; }
.progress-bar-track { background: var(--code-bg); border-radius: 999px; height: .6rem; overflow: hidden; }
.progress-bar-fill { height: 100%; border-radius: 999px;
  background: linear-gradient(90deg, var(--low), var(--accent)); transition: width .3s ease; }
.progress-note { font-size: .72rem; color: var(--muted); margin-top: .5rem; }
li:has(> input.task-checkbox) { list-style: none; margin-left: -1.4rem; padding-left: .3rem;
  display: list-item; }
input.task-checkbox { width: 1rem; height: 1rem; margin-right: .5rem; vertical-align: middle;
  accent-color: var(--accent); }
li:has(> input.task-checkbox:checked) { color: var(--muted); }
li:has(> input.task-checkbox:checked) code { opacity: .75; }
@media (max-width: 900px) {
  .layout { flex-direction: column; }
  nav.sidebar { width: 100%; height: auto; position: static; }
  main { padding: 1.4rem 1.2rem 3rem; }
}
@media print { nav.sidebar { display: none; } main { padding: 0; } }
"""

MERMAID_SCRIPT = """
<script type="module">
  try {
    const m = await import("https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.esm.min.mjs");
    const dark = window.matchMedia("(prefers-color-scheme: dark)").matches;
    m.default.initialize({ startOnLoad: true, theme: dark ? "dark" : "neutral", securityLevel: "loose" });
  } catch (e) {
    document.querySelectorAll(".mermaid").forEach(el => {
      el.style.textAlign = "left";
      el.innerHTML = "<div style='color:var(--muted);font-size:.75rem;margin-bottom:.5rem'>⚠ Diagramme Mermaid (connexion internet requise pour le rendu) — source :</div><pre>" + el.textContent.replace(/&/g,"&amp;").replace(/</g,"&lt;") + "</pre>";
    });
  }
</script>
"""



def nav_html(active_slug: str, prefix: str = "") -> str:
    items = ['<div class="brand">TiiBnTick Core<small>Audit technique — juillet 2026</small></div>']
    items.append('<ul><li><a href="%sindex.html"%s>🏠 Accueil</a></li>' % (prefix, ' class="active"' if active_slug == "index" else ""))
    for slug, kicker, title, icon in NAV_EXTRA:
        cls = ' class="active"' if slug == active_slug else ""
        items.append(f'<li><a href="{prefix}{slug}.html"{cls}>{icon} {title}</a></li>')
    items.append('</ul><div class="section-label">Rapports d\'audit</div><ul>')
    for slug, kicker, title, icon in REPORTS:
        cls = ' class="active"' if slug == active_slug else ""
        items.append(f'<li><a href="{prefix}{slug}.html"{cls}>{icon} {kicker} — {title}</a></li>')
    items.append('</ul><div class="section-label">Plan de remédiation</div><ul>')
    rem_prefix = "" if prefix else "remediation/"
    for slug, kicker, title, icon in REMEDIATION:
        cls = ' class="active"' if slug == active_slug else ""
        items.append(f'<li><a href="{rem_prefix}{slug}.html"{cls}>{icon} {kicker} — {title}</a></li>')
    items.append("</ul>")
    return '<nav class="sidebar">' + "\n".join(items) + "</nav>"


BADGE_MAP = {
    "critique": "crit",
    "élevé": "high", "eleve": "high", "élevée": "high", "haute": "high",
    "moyen": "med", "moyenne": "med", "modéré": "med",
    "faible": "low", "bas": "low", "basse": "low", "info": "low",
}


def badgeify(html: str) -> str:
    def repl(m):
        word = m.group(1)
        cls = BADGE_MAP.get(word.strip().lower())
        if not cls:
            return m.group(0)
        return f'<td><span class="badge {cls}">{word.strip()}</span></td>'
    return re.sub(r"<td>\s*(?:<strong>)?\s*(Critique|Élevé|Élevée|Eleve|Moyen|Moyenne|Modéré|Faible|Info)\s*(?:</strong>)?\s*</td>",
                  repl, html, flags=re.IGNORECASE)


TASK_RE = re.compile(r"^(\s*)[-*] \[([ xX])\] ", re.MULTILINE)


def count_tasks(md_text: str) -> tuple[int, int]:
    """Returns (done, total) by scanning raw markdown task-list items."""
    matches = TASK_RE.findall(md_text)
    total = len(matches)
    done = sum(1 for _, mark in matches if mark.lower() == "x")
    return done, total


def progress_html(done: int, total: int, label: str = "Progression") -> str:
    if total == 0:
        return ""
    pct = round(100 * done / total)
    return f"""<div class="progress-block">
  <div class="progress-label"><span>{label}</span><b>{done} / {total} ({pct}%)</b></div>
  <div class="progress-bar-track"><div class="progress-bar-fill" style="width:{pct}%"></div></div>
  <div class="progress-note">Lecture seule : ces cases reflètent l'état du fichier Markdown versionné dans git au moment de la génération. On les coche en éditant le <code>.md</code> (IDE ou <code>- [x]</code> par commit), jamais depuis cette page — régénérer avec <code>python3 docs/audits/build_reports.py</code> après un commit pour mettre à jour l'affichage.</div>
</div>"""


def render_checkboxes(body: str) -> str:
    body = re.sub(r"<li>\[ \] ", '<li><input type="checkbox" class="task-checkbox" disabled> ', body)
    body = re.sub(r"<li>\[[xX]\] ", '<li><input type="checkbox" class="task-checkbox" checked disabled> ', body)
    return body


def convert_md(md_text: str) -> str:
    md = markdown.Markdown(extensions=["tables", "fenced_code", "toc", "attr_list", "md_in_html"],
                           extension_configs={"toc": {"toc_depth": "2-3", "title": "Sommaire"}})
    body = md.convert(md_text)
    toc = md.toc if getattr(md, "toc_tokens", None) else ""
    # mermaid fences -> <pre class="mermaid">
    def mermaid_repl(m):
        code = m.group(1)
        return '<pre class="mermaid">' + code + "</pre>"
    body = re.sub(
        r'<pre><code class="language-mermaid">(.*?)</code></pre>',
        lambda m: '<pre class="mermaid">' + html_mod.unescape(m.group(1)) + "</pre>",
        body, flags=re.DOTALL)
    body = badgeify(body)
    body = render_checkboxes(body)
    body = re.sub(r"<table>", '<div class="table-wrap"><table>', body)
    body = re.sub(r"</table>", "</table></div>", body)
    return toc, body


def page(slug: str, kicker: str, title: str, icon: str, toc: str, body: str, prefix: str = "",
         meta: str = "Projet TiiBnTick Core · dépôt <code>tiibntick-core</code> (34 modules Maven, y compris <code>trust/tnt-trust-core</code>) · Audit réalisé le 16 juillet 2026",
         progress: str = "") -> str:
    return f"""<!DOCTYPE html>
<html lang="fr">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{kicker} — {title} · Audit TiiBnTick</title>
<style>{CSS}</style>
</head>
<body>
<div class="layout">
{nav_html(slug, prefix)}
<main>
<header class="page">
  <div class="kicker">{kicker}</div>
  <h1>{icon} {title}</h1>
  <div class="meta">{meta}</div>
</header>
{progress}
{('<div class="toc">' + toc + '</div>') if toc else ''}
{body}
<footer>Audit technique TiiBnTick — généré à partir de l'analyse du code source réel. Chaque constat référence les fichiers et lignes concernés.</footer>
</main>
</div>
{MERMAID_SCRIPT}
</body>
</html>
"""


def build_all():
    all_pages = REPORTS + NAV_EXTRA + [("index", "", "Portail des audits", "🏠")]
    for slug, kicker, title, icon in all_pages:
        src = RAW / f"{slug}.md"
        if not src.exists():
            print(f"SKIP {slug} (pas de {src.name})")
            continue
        toc, body = convert_md(src.read_text(encoding="utf-8"))
        out = HERE / f"{slug}.html"
        show_toc = toc if slug not in ("index",) else ""
        out.write_text(page(slug, kicker or "TiiBnTick", title, icon, show_toc, body),
                       encoding="utf-8")
        print(f"OK   {out.name} ({out.stat().st_size // 1024} Ko)")

    REMEDIATION_DIR = HERE / "remediation"
    remediation_meta = ('Plan d\'implémentation — document vivant, mis à jour à chaque décision. '
                         'Voir <code>docs/audits/raw/</code> pour le constat figé.')

    aggregate_done, aggregate_total = 0, 0
    phase_texts = {}
    for slug in PROGRESS_TRACKED:
        src = REMEDIATION_DIR / f"{slug}.md"
        if src.exists():
            text = src.read_text(encoding="utf-8")
            phase_texts[slug] = text
            d, t = count_tasks(text)
            aggregate_done += d
            aggregate_total += t

    for slug, kicker, title, icon in REMEDIATION:
        src = REMEDIATION_DIR / f"{slug}.md"
        if not src.exists():
            print(f"SKIP remediation/{slug} (pas de {src.name})")
            continue
        md_text = src.read_text(encoding="utf-8")
        toc, body = convert_md(md_text)
        out = REMEDIATION_DIR / f"{slug}.html"
        if slug == "implementation-plan":
            bar = progress_html(aggregate_done, aggregate_total, "Progression globale du plan (P0 + P1 + P2 + chantier paiement)")
        elif slug in PROGRESS_TRACKED:
            d, t = count_tasks(md_text)
            bar = progress_html(d, t, f"Progression — {title}")
        else:
            bar = ""
        out.write_text(page(slug, kicker, title, icon, toc, body, prefix="../", meta=remediation_meta,
                             progress=bar),
                       encoding="utf-8")
        print(f"OK   remediation/{out.name} ({out.stat().st_size // 1024} Ko)")

    readme_src = REMEDIATION_DIR / "README.md"
    if readme_src.exists():
        _, body = convert_md(readme_src.read_text(encoding="utf-8"))
        out = REMEDIATION_DIR / "index.html"
        bar = progress_html(aggregate_done, aggregate_total, "Progression globale du plan")
        out.write_text(page("remediation-index", "Plan", "Plan de remédiation", "🗺️", "", body,
                             prefix="../", meta=remediation_meta, progress=bar),
                       encoding="utf-8")
        print(f"OK   remediation/{out.name} ({out.stat().st_size // 1024} Ko)")


if __name__ == "__main__":
    build_all()
