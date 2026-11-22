#!/bin/bash
# Differential run over the corpus: for each IG, stage a SUSHI-relevant copy on the VM (scripts/stage-src.py), run TS
# SUSHI 3.21.1 and the Java jar on it (scripts/diff-run.sh <IG> corpus), then drop the staged copy and the TS output
# when the result is identical. Results: work/sushi-port/diff/<IG>-corpus.json; one summary line per IG appended to
# work/sushi-port/diff/corpus-summary.txt.
# usage: scripts/corpus-diff.sh [-j N] [list-file]    (default list: work/sushi-spike/fsh-repos.txt, N=3)
# Already-reported IGs are skipped (delete the report to redo one). JAR=... and ROOT as for diff-run.sh.
set -u
HERE="$(cd "$(dirname "$0")/.." && pwd)"; ROOT="${ROOT:-$(cd "$HERE/.." && pwd)}"; . "$ROOT/scripts/lib/vmdata.sh"
J=3; [ "${1:-}" = -j ] && { J=$2; shift 2; }
LIST=${1:-$ROOT/work/sushi-spike/fsh-repos.txt}; REP="${SUSHI_DIFF_REPORTS:-$ROOT/work/sushi-port/diff}"; mkdir -p "$REP"
one() {
  IG=$1; SRC=$VMDATA/sushi-spike/src/$IG
  [ -f "$REP/$IG-corpus.json" ] && return 0
  staged=0
  if ! vm "[ -d '$SRC' ]"; then
    python3 "$HERE/scripts/stage-src.py" "$ROOT/repos/$IG" | docker run --rm -i -v "$VMDATA:$VMDATA" --entrypoint bash \
      "$IMAGE" -c "mkdir -p '$SRC' && tar -xf - -C '$SRC'"
    staged=1
  fi
  line=$("$ROOT/work/locks/waitfree.sh" timing "$HERE/scripts/diff-run.sh" "$IG" corpus 2>&1 | tail -2 | tr '\n' ' ')
  echo "$IG | $line" >> "$REP/corpus-summary.txt"
  if grep -q '"files": {}' "$REP/$IG-corpus.json" 2>/dev/null && grep -q '"identical": true' "$REP/$IG-corpus.json"; then
    vm "rm -f '$VMDATA/sushi-spike/runs/$IG-ts-corpus/out.tar'"
  fi
  [ $staged = 1 ] && vm "rm -rf '$SRC'"
  return 0
}
export -f one; export HERE ROOT REP VMDATA IMAGE; export -f vm
grep -v '^\s*$' "$LIST" | xargs -P "$J" -I{} bash -c 'one "$@"' _ {}
