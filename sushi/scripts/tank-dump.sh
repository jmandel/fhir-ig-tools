#!/bin/bash
# WP-2 gate: TS SUSHI 3.21.1 importer vs the Java importer on the FSH corpus.
# usage: scripts/tank-dump.sh [ts|java|compare|all] [IG,IG,...]
#   corpus: $VMDATA/sushi-wp2/corpus/<IG>/input/fsh (copied from repos/ once; see progress/sushi-wp2.log)
#   ts   -> $VMDATA/sushi-wp2/ts/<IG>/{tank.json,log.txt,_preprocessed/}   (scripts/oracle/tank-dump.js, node 22)
#   java -> $VMDATA/sushi-wp2/java/<IG>/...                               (TankDumpCorpusTest)
#   compare -> summary on stdout + $VMDATA/sushi-wp2/compare.tsv (IG, tank, log, preprocessed: same|DIFF|missing)
# Start under ../work/locks/waitfree.sh timing.
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
VMDATA="${VMDATA:-/srv/ig}"; IMAGE="${IMAGE:-ig-publisher-localdev-ca}"
W=$VMDATA/sushi-wp2; DIST=$VMDATA/sushi-spike/sushi/dist
what="${1:-all}"; only="${2:-}"
ts() {
  docker run --rm -i -v "$VMDATA:$VMDATA" -v "$HERE:$HERE:ro" --entrypoint bash "$IMAGE" -c '
    set -u; W=$1; DIST=$2; JS=$3; ONLY=$4
    for d in "$W"/corpus/*/; do ig=$(basename "$d"); [ -d "$d/input/fsh" ] || continue
      if [ -n "$ONLY" ] && [[ ",$ONLY," != *",$ig,"* ]]; then continue; fi
      rm -rf "$W/ts/$ig"; mkdir -p "$W/ts/$ig"
      ( cd /tmp && node --max-old-space-size=8000 "$JS" "$DIST" "$d"input/fsh "$W/ts/$ig" ) 2> "$W/ts/$ig/stderr.txt" || echo "$ig: node exit $?" >&2
    done' _ "$W" "$DIST" "$HERE/scripts/oracle/tank-dump.js" "$only"
}
java() {
  "$HERE/scripts/build.sh" -q -B test -Dtest=TankDumpCorpusTest -Dsurefire.failIfNoSpecifiedTests=false \
    -Dwp2.corpus="$W/corpus" -Dwp2.out="$W/java" ${only:+-Dwp2.only=$only}
}
compare() {
  docker run --rm -i -v "$VMDATA:$VMDATA" --entrypoint bash "$IMAGE" -c '
    W=$1; out=$W/compare.tsv; : > "$out"
    for t in "$W"/ts/*/; do ig=$(basename "$t"); j=$W/java/$ig
      if [ ! -f "$j/tank.json" ]; then echo -e "$ig\tmissing\tmissing\tmissing" >> "$out"; continue; fi
      a=same; cmp -s "$t/tank.json" "$j/tank.json" || a=DIFF
      b=same; diff -q <(grep -v "^info  Wrote preprocessed FSH to" "$t/log.txt") <(grep -v "^info  Wrote preprocessed FSH to" "$j/log.txt") >/dev/null || b=DIFF
      c=same; diff -rq "$t/_preprocessed" "$j/_preprocessed" >/dev/null 2>&1 || c=DIFF
      echo -e "$ig\t$a\t$b\t$c" >> "$out"
    done
    echo "IGs: $(wc -l < "$out")  tank same: $(awk -F"\t" "\$2==\"same\"" "$out" | wc -l)  log same: $(awk -F"\t" "\$3==\"same\"" "$out" | wc -l)  preprocessed same: $(awk -F"\t" "\$4==\"same\"" "$out" | wc -l)"
    awk -F"\t" "\$2!=\"same\"||\$3!=\"same\"||\$4!=\"same\"" "$out"' _ "$W"
}
case "$what" in
  ts) ts;; java) java;; compare) compare;;
  all) ts; java; compare;;
esac
