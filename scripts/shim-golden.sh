#!/bin/bash
# P1.3: run the shim (as the publisher would: `build --destination X` from jekyll-in) on a golden
# and compare its output with real Jekyll's using the normalized-DOM rule (scripts/domdiff.py).
# usage: scripts/shim-golden.sh <golden-name> [build-dir, default build-site]
# The trees are on the VM disk (scripts/lib/vmdata.sh); the shim and the comparison run there.
# Writes $VMDATA/golden/<name>/shim-out, and on the Mac work/golden/<name>/{shim.log,shim-diff.json};
# prints the domdiff summary.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
. "$ROOT/scripts/lib/vmdata.sh"
NAME="$1"; B="${2:-build-site}"
G="$ROOT/work/golden/$NAME"
vm "test -d '$VMDATA/golden/$NAME/jekyll-in' && mkdir -p '$VMDATA/golden/$NAME/shim-out'" || { echo "no golden $NAME"; exit 1; }
golden_mounts "$NAME"
# SHIM_XMX (default 6g) and SHIM_JAVA_OPTS (e.g. -Xlog:gc) tune the JVM; DOCKER_CONTEXT picks the daemon.
# DOMDIFF_OPTS is passed to domdiff.py.
docker run --rm --entrypoint bash -e SHIM_TIMING=1 -v "$ROOT:$ROOT" "${GM[@]}" -w "$G/jekyll-in" "$IMAGE" -c "
  find '$G/shim-out' -mindepth 1 -delete; rm -rf .jekyll-cache
  TIMEFORMAT='shim_elapsed=%R'
  time java -Xss16m -Xmx${SHIM_XMX:-6g} ${SHIM_JAVA_OPTS:-} -cp '$ROOT/jekyll/$B/classes' org.hl7.fhir.jekyll.site.Main build --destination '$G/shim-out'
  echo exit=\$?
  python3 -I '$ROOT/scripts/domdiff.py' '$G/jekyll-out' '$G/shim-out' --json '$G/shim-diff.json' --max-report 12 ${DOMDIFF_OPTS:-} > '$G/shim-diff.txt' 2>&1 || true
" > "$G/shim.log" 2>&1 || echo "shim exited non-zero (see $G/shim.log)"
grep -E 'done in|Liquid Exception|Error|shim_elapsed' "$G/shim.log" | head -5
cat "$G/shim-diff.txt"
