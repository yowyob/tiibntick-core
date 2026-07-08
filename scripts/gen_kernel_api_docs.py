#!/usr/bin/env python3
"""Regenerate docs/kernel-api/{endpoints,schemas}.md from docs/kernel-api/openapi.json.

Usage: python3 gen_kernel_api_docs.py
Run scripts/fetch-kernel-openapi.sh first to refresh openapi.json.
"""
import json
import os
import re

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)))
REPO_DOCS = None  # set by caller via env var KERNEL_API_DOCS_DIR


def load(path):
    with open(path) as f:
        return json.load(f)


def schema_ref_name(schema):
    if schema is None:
        return None
    if "$ref" in schema:
        return schema["$ref"].split("/")[-1]
    if schema.get("type") == "array" and "items" in schema:
        inner = schema_ref_name(schema["items"])
        return f"{inner}[]" if inner else "array"
    t = schema.get("type")
    if t:
        fmt = schema.get("format")
        return f"{t}({fmt})" if fmt else t
    return None


def param_line(p):
    name = p.get("name")
    loc = p.get("in")
    required = p.get("required", False)
    schema = p.get("schema", {})
    typ = schema_ref_name(schema) or "?"
    desc = p.get("description", "")
    req = "required" if required else "optional"
    line = f"- `{name}` ({loc}, {typ}, {req})"
    if desc:
        line += f" — {desc}"
    return line


def body_info(request_body):
    if not request_body:
        return None
    content = request_body.get("content", {})
    out = []
    for ctype, media in content.items():
        ref = schema_ref_name(media.get("schema", {}))
        out.append(f"`{ctype}` → `{ref}`")
    required = request_body.get("required", False)
    return ", ".join(out) + (" (required)" if required else " (optional)")


def responses_info(responses):
    out = []
    for code, resp in sorted(responses.items()):
        content = resp.get("content", {})
        refs = []
        for ctype, media in content.items():
            ref = schema_ref_name(media.get("schema", {}))
            if ref:
                refs.append(ref)
        if refs:
            out.append(f"{code} → `{', '.join(refs)}`")
        else:
            out.append(f"{code}")
    return "; ".join(out)


def slugify(tag):
    return re.sub(r"[^a-z0-9]+", "-", tag.lower()).strip("-")


def main():
    spec_path = os.environ["KERNEL_OPENAPI_JSON"]
    out_dir = os.environ["KERNEL_API_DOCS_DIR"]
    spec = load(spec_path)

    info = spec.get("info", {})
    servers = spec.get("servers", [])
    paths = spec.get("paths", {})
    schemas = spec.get("components", {}).get("schemas", {})

    # Group operations by tag
    by_tag = {}
    total_ops = 0
    for path, methods in paths.items():
        for method, op in methods.items():
            if method not in ("get", "post", "put", "delete", "patch"):
                continue
            total_ops += 1
            tags = op.get("tags") or ["untagged"]
            for tag in tags:
                by_tag.setdefault(tag, []).append((path, method, op))

    # ---- endpoints.md ----
    lines = []
    lines.append(f"# Kernel API — Endpoints Reference\n")
    lines.append(f"Auto-generated from `{info.get('title')}` v{info.get('version')} "
                  f"OpenAPI spec (`openapi.json` in this directory). "
                  f"Server: {servers[0]['url'] if servers else '?'}.\n")
    lines.append(f"**Do not edit by hand** — regenerate with "
                  f"`scripts/fetch-kernel-openapi.sh` (fetches spec) then "
                  f"`python3 scripts/gen_kernel_api_docs.py` (rebuilds this file + `schemas.md`).\n")
    lines.append(f"{total_ops} operations across {len(paths)} paths, {len(by_tag)} tags, "
                  f"{len(schemas)} schemas.\n")

    lines.append("## Tags (controllers)\n")
    for tag in sorted(by_tag):
        anchor = slugify(tag)
        lines.append(f"- [{tag}](#{anchor}) ({len(by_tag[tag])} ops)")
    lines.append("")

    for tag in sorted(by_tag):
        lines.append(f"## {tag}\n")
        ops = sorted(by_tag[tag], key=lambda x: (x[0], x[1]))
        for path, method, op in ops:
            summary = op.get("summary", "")
            lines.append(f"### {method.upper()} `{path}`")
            if summary:
                lines.append(f"{summary}\n")
            params = op.get("parameters", [])
            if params:
                lines.append("**Parameters:**")
                for p in params:
                    lines.append(param_line(p))
                lines.append("")
            rb = body_info(op.get("requestBody"))
            if rb:
                lines.append(f"**Request body:** {rb}\n")
            resp = responses_info(op.get("responses", {}))
            if resp:
                lines.append(f"**Responses:** {resp}\n")
            security = op.get("security")
            if security is not None and len(security) == 0:
                lines.append("**Auth:** none (public)\n")
            lines.append("")
        lines.append("")

    with open(os.path.join(out_dir, "endpoints.md"), "w") as f:
        f.write("\n".join(lines))

    # ---- schemas.md ----
    slines = []
    slines.append("# Kernel API — Schemas Reference\n")
    slines.append(f"Auto-generated from `openapi.json`. {len(schemas)} component schemas. "
                   "Referenced by name from `endpoints.md` (request/response bodies).\n")
    slines.append("**Do not edit by hand** — see regeneration instructions in `endpoints.md`.\n")

    for name in sorted(schemas):
        s = schemas[name]
        slines.append(f"## {name}\n")
        desc = s.get("description")
        if desc:
            slines.append(f"{desc}\n")
        if "enum" in s:
            slines.append(f"Enum: {', '.join(repr(v) for v in s['enum'])}\n")
            continue
        props = s.get("properties", {})
        required = set(s.get("required", []))
        if not props:
            t = s.get("type", "object")
            slines.append(f"Type: `{t}`\n")
            continue
        slines.append("| Field | Type | Required |")
        slines.append("|---|---|---|")
        for pname, pschema in props.items():
            typ = schema_ref_name(pschema) or "?"
            req = "yes" if pname in required else "no"
            slines.append(f"| `{pname}` | `{typ}` | {req} |")
        slines.append("")

    with open(os.path.join(out_dir, "schemas.md"), "w") as f:
        f.write("\n".join(slines))

    print(f"Wrote endpoints.md ({total_ops} ops, {len(by_tag)} tags) "
          f"and schemas.md ({len(schemas)} schemas) to {out_dir}")


if __name__ == "__main__":
    main()
