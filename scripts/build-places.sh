#!/usr/bin/env bash
# Builds src/main/resources/geo/places-pt.tsv from the GeoNames PT dump (CC BY 4.0).
# Usage: scripts/build-places.sh [path/to/PT.txt]   (no argument = download PT.zip)
# Output columns: name, district, lat, lon, feature, population. Sorted, so a rerun gives the same file.
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=src/main/resources/geo/places-pt.tsv
SRC=${1:-}
if [ -z "$SRC" ]; then
  TMP=$(mktemp -d)
  trap 'rm -rf "$TMP"' EXIT
  curl -sSfL -o "$TMP/PT.zip" https://download.geonames.org/export/dump/PT.zip
  unzip -q "$TMP/PT.zip" PT.txt -d "$TMP"
  SRC=$TMP/PT.txt
fi
{
  echo "# GeoNames (geonames.org) PT dump, CC BY 4.0, https://download.geonames.org/export/dump/PT.zip, $(date +%F), trimmed by scripts/build-places.sh"
  python3 -I - "$SRC" <<'PY' | LC_ALL=C sort -u
import re, sys
rows = [l.rstrip("\n").split("\t") for l in open(sys.argv[1], encoding="utf-8")]
# admin1 code -> district name ("Distrito de/da/do " stripped)
district = {r[10]: re.sub(r"^Distrito d[eao] ", "", r[1]) for r in rows if r[7] == "ADM1"}
district = {k: ("Açores" if v == "Azores" else v) for k, v in district.items()}
ALT = {"PPLC", "PPLA", "PPLA2", "ADM2"}
keep = [r for r in rows if r[6] == "P" or r[7] in ("ADM2", "ADM3")]
names = {r[1] for r in keep}
for r in keep:
    d = district.get(r[10], "")
    out = [(r[1], r)]
    if r[7] in ALT:
        # only capitalised Latin-script alternates (no codes, no transliterations); they never shadow a real place name, so they cannot create false ambiguity
        out += [(a, r) for a in r[3].split(",") if a not in names and re.fullmatch(r"[A-ZÀ-Þ][a-zà-ÿ]+([ -][A-Za-zÀ-ÿ]+)*", a)]
    for n, _ in out:
        print("\t".join([n, d, r[4], r[5], r[7], r[14] or "0"]))
PY
} > "$OUT.tmp"
mv "$OUT.tmp" "$OUT"
echo "$(wc -l < "$OUT") lines, $(du -h "$OUT" | cut -f1)"
