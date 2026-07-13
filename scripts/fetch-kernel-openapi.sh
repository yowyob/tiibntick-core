#!/usr/bin/env bash
# Refreshes docs/kernel-api/openapi.json from the live Kernel, then rebuilds
# docs/kernel-api/{endpoints,schemas}.md from it.
#
# Usage: scripts/fetch-kernel-openapi.sh
set -euo pipefail

KERNEL_URL="${KERNEL_URL:-https://kernel-core.yowyob.com/kernel-api}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DOCS_DIR="$ROOT_DIR/docs/kernel-api"

echo "Fetching OpenAPI spec from ${KERNEL_URL}/v3/api-docs ..."
curl -sS "${KERNEL_URL}/v3/api-docs" --max-time 180 -o "$DOCS_DIR/openapi.json"

echo "Regenerating endpoints.md / schemas.md ..."
KERNEL_OPENAPI_JSON="$DOCS_DIR/openapi.json" \
KERNEL_API_DOCS_DIR="$DOCS_DIR" \
python3 "$ROOT_DIR/scripts/gen_kernel_api_docs.py"

echo "Done. Review the diff and commit docs/kernel-api/ if it changed."
