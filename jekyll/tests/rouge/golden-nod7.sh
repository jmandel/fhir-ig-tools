#!/bin/bash
# Like scripts/shim-golden.sh, but writes to work/golden/<name>/shim-out-rouge (so it does not clobber
# the main agent's shim-out) and compares with domdiff --no-d7 (Rouge token spans compared exactly).
# usage: jekyll/tests/rouge/golden-nod7.sh <golden-name> [build-dir, default build-rouge]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
NAME="$1"; B="${2:-build-rouge}"
G="$ROOT/work/golden/$NAME"
[ -d "$G/jekyll-in" ] || { echo "no golden $NAME"; exit 1; }
rm -rf "$G/shim-out-rouge"
docker run --rm --entrypoint bash -v "$ROOT":/w -w "/w/work/golden/$NAME/jekyll-in" ig-publisher-localdev-ca -c \
  "TIMEFORMAT='shim_elapsed=%R'; time java -Xss16m -Xmx${SHIM_XMX:-6g} -cp /w/jekyll/$B/classes org.hl7.fhir.jekyll.site.Main build --destination /w/work/golden/$NAME/shim-out-rouge; echo exit=\$?" \
  > "$G/shim-rouge.log" 2>&1 || echo "shim exited non-zero (see $G/shim-rouge.log)"
grep -E 'done in|Liquid Exception|Error|shim_elapsed' "$G/shim-rouge.log" | head -5
python3 -I "$ROOT/scripts/domdiff.py" "$G/jekyll-out" "$G/shim-out-rouge" --json "$G/shim-diff-rouge.json" --max-report 12 --no-d7 || true
