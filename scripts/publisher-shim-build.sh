#!/bin/bash
# P3.7: full IG Publisher run with the shim as its Jekyll (`-jekyll jekyll/jekyll-shim`), then compare
# the publisher's final output/ against a baseline publisher output built with real Jekyll.
# usage: scripts/publisher-shim-build.sh <repos-name> [baseline-output-dir on the VM, default $VMDATA/igs/<name>/<ig>/output] [-Xmx, default 6g]
# Build copy on the VM disk ($VMDATA/igs-shim/<name>); log and diff on the Mac under work/igs-shim/.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
. "$ROOT/scripts/lib/vmdata.sh"
NAME="$1"; XMX="${3:-6g}"
SRC="$ROOT/repos/$NAME"; WORK="$VMDATA/igs-shim/$NAME"
mkdir -p "$ROOT/work/igs-shim"; LOG="$ROOT/work/igs-shim/$NAME.publisher.log"
igini=$(cd "$SRC" && find . -maxdepth 3 -name ig.ini -not -path '*/node_modules/*' | head -1); igsub=$(dirname "$igini")
BASE="${2:-$VMDATA/igs/$NAME/$igsub/output}"
to_vm "$SRC" "$WORK" --exclude .git
start=$(date +%s)
docker run --rm -v "$WORK":/home/publisher/ig -v "${FHIR_CACHE:-$VMDATA/fhir-cache}":/home/publisher/.fhir \
  -v "$ROOT/work/bin":/opt/pub:ro -v "$ROOT/jekyll":/opt/shim:ro -w "/home/publisher/ig/$igsub" "$IMAGE" \
  bash -c "java -Xmx$XMX -jar /opt/pub/publisher.jar -ig . -no-network-check -jekyll /opt/shim/jekyll-shim 2>&1 | tail -n 4000" > "$LOG" 2>&1 || true
end=$(date +%s)
echo "full_build_seconds=$((end-start))"
grep -E 'Run jekyll:|Jekyll: .*done in|Jekyll has failed|Publishing Content Failed|Finished @' "$LOG" | cut -c1-160 | head -6
grep -oE 'jekyll: [0-9:.]+' "$LOG" | tail -1 || true
echo "== final output/ vs baseline (publisher+real Jekyll):"
docker run --rm --entrypoint python3 -v "$VMDATA:$VMDATA" -v "$ROOT:$ROOT" "$IMAGE" -I "$ROOT/scripts/domdiff.py" \
  "$BASE" "$WORK/$igsub/output" --max-report 8 --json "$ROOT/work/igs-shim/$NAME.diff.json" || true
