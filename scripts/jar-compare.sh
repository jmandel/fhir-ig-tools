#!/bin/bash
# Byte-for-byte regression check between shim builds: run each jar (`java -jar <jar> build --destination X`,
# as the wrappers do) on a golden's jekyll-in and compare every output file with the first jar's.
# For refactors that must not change output (e.g. the multi-module move: docs/MONOREPO.md).
# usage: scripts/jar-compare.sh <golden-name> <reference.jar> <candidate.jar> [more candidate jars...]
# The golden's jekyll-in (on the VM disk, scripts/lib/vmdata.sh) is mounted read-only; outputs go to
# $VMDATA/tmp/jar-compare/<name>/<k> and are deleted afterwards unless KEEP=1. Jars may be anywhere under
# a path the Docker host can mount (e.g. the checkout). Prints one TSV line:
#   name  files  then per candidate: identical|DIFF(n)  (n = files missing, extra or with different bytes)
# Exit status 1 if any candidate differs or a build fails. SHIM_XMX (default 6g) sets the heap.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
. "$ROOT/scripts/lib/vmdata.sh"
NAME="$1"; shift
[ $# -ge 2 ] || { echo "usage: $0 <golden-name> <reference.jar> <candidate.jar>..." >&2; exit 2; }
JARS=(); MOUNTS=()
for j in "$@"; do
  a="$(cd "$(dirname "$j")" && pwd)/$(basename "$j")"; [ -f "$a" ] || { echo "no jar $j" >&2; exit 2; }
  JARS+=("$a"); MOUNTS+=(-v "$a:$a:ro")
done
IN="$VMDATA/golden/$NAME/jekyll-in"; OUT="$VMDATA/tmp/jar-compare/$NAME"
docker run --rm -i -v "$VMDATA:$VMDATA" -v "$IN:/in:ro" "${MOUNTS[@]}" -w /in --entrypoint bash "$IMAGE" -s "$NAME" "$OUT" "${KEEP:-}" "${SHIM_XMX:-6g}" "${JARS[@]}" <<'EOF'
set -u
name="$1"; out="$2"; keep="$3"; xmx="$4"; shift 4
rm -rf "$out"; mkdir -p "$out"; k=0; bad=0
for jar in "$@"; do
  if ! java -Xmx"$xmx" -jar "$jar" build --destination "$out/$k" > "$out/$k.log" 2>&1; then
    echo "build failed: $jar (log: $out/$k.log)" >&2; tail -5 "$out/$k.log" >&2; bad=1
  fi
  k=$((k + 1))
done
line="$name	$(find "$out/0" -type f | wc -l)"
for ((i = 1; i < k; i++)); do
  n=$(diff -rq "$out/0" "$out/$i" | wc -l)
  if [ "$n" -eq 0 ]; then line="$line	identical"; else line="$line	DIFF($n)"; bad=1; diff -rq "$out/0" "$out/$i" | head -5 >&2; fi
done
echo "$line"
[ -n "$keep" ] || rm -rf "$out"
exit $bad
EOF
