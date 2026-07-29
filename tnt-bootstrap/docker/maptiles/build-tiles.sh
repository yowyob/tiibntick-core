#!/usr/bin/env bash
#
# Self-hosted map tiles — Central Africa vector tileset build pipeline.
#
# Produces central-africa.mbtiles from OpenStreetMap data, served in dev/prod by the
# `tileserver` service in ../../docker-compose.yml (maptiler/tileserver-gl), so
# TiiBnTick-Link-Frontend's LeafletMapContainer.tsx no longer hits tile.openstreetmap.org /
# basemaps.cartocdn.com directly (both prohibit the commercial/high-traffic usage this
# platform needs — see docs/audits or the Chantier G real-time chantier notes for the analysis).
#
# Why not a single "Central Africa" download: Geofabrik (the standard OSM extract source) only
# publishes per-country extracts, not a pre-bundled Central Africa region — this script downloads
# each country individually and merges them with osmium before handing the result to Planetiler.
#
# Prerequisites (install once on the host):
#   - osmium-tool   (Debian/Ubuntu/Kali: `apt install osmium-tool`)
#   - curl
#   - Docker        (Planetiler and the merge step's disk-heavy work stay containerized/host-run
#                     as appropriate; see below)
#
# Disk/network: combined country extracts for this region are on the order of a few hundred MB;
# Planetiler needs several GB of free scratch space to build the tileset (more than the input
# size — it builds an intermediate node-location index). Do not run this on a host with less than
# ~10GB free. The resulting central-africa.mbtiles is typically tens of MB.
#
# Usage:
#   ./build-tiles.sh                # full build
#   ./build-tiles.sh --skip-download  # reuse extracts already in ./work/
#
# Re-run periodically (e.g. monthly) for data freshness — not automated on a schedule here.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK_DIR="${SCRIPT_DIR}/work"
OUTPUT_DIR="${SCRIPT_DIR}/output"
MERGED_PBF="${WORK_DIR}/central-africa-merged.osm.pbf"
OUTPUT_MBTILES="${OUTPUT_DIR}/central-africa.mbtiles"

# Cameroon (the current market — see DEFAULT_CENTER in TiiBnTick-Link-Frontend) plus its
# immediate neighbors, per the Chantier G decision to cover Central Africa, not just one country.
GEOFABRIK_BASE="https://download.geofabrik.de/africa"
COUNTRIES=(
  cameroon
  gabon
  congo-brazzaville
  central-african-republic
  chad
  equatorial-guinea
)

skip_download=false
for arg in "$@"; do
  case "$arg" in
    --skip-download) skip_download=true ;;
    *) echo "Unknown argument: $arg" >&2; exit 1 ;;
  esac
done

mkdir -p "$WORK_DIR" "$OUTPUT_DIR"

if [[ "$skip_download" == false ]]; then
  echo "== Downloading ${#COUNTRIES[@]} country extracts from Geofabrik =="
  for country in "${COUNTRIES[@]}"; do
    dest="${WORK_DIR}/${country}-latest.osm.pbf"
    echo "-- ${country}"
    curl -fL --retry 3 -o "$dest" "${GEOFABRIK_BASE}/${country}-latest.osm.pbf"
  done
else
  echo "== Skipping download, reusing extracts in ${WORK_DIR} =="
fi

echo "== Merging extracts with osmium =="
if ! command -v osmium >/dev/null 2>&1; then
  echo "osmium-tool not found — install it first (Debian/Ubuntu/Kali: apt install osmium-tool)" >&2
  exit 1
fi
osmium merge "${WORK_DIR}"/*-latest.osm.pbf -o "$MERGED_PBF" --overwrite

echo "== Building vector tileset with Planetiler =="
docker run --rm \
  -v "${WORK_DIR}:/data" \
  -v "${OUTPUT_DIR}:/output" \
  ghcr.io/onthegomap/planetiler:latest \
  --input="/data/$(basename "$MERGED_PBF")" \
  --output="/output/$(basename "$OUTPUT_MBTILES")" \
  --force

echo "== Done: ${OUTPUT_MBTILES} =="
ls -lh "$OUTPUT_MBTILES"
echo
echo "Next: docker compose up -d tileserver (see docker-compose.yml) then verify:"
echo "  curl -sI http://localhost:8090/styles/basic/0/0/0.png"
