#!/bin/bash
# End-to-end check of the sushi wrapper as the IG Publisher calls it (`sushi <ig> -o <ig>`): runs
# sushi/tests/e2e/ig through the wrapper and through `java -jar sushi.jar`, at the same path, and requires the same
# fsh-generated/ (byte for byte), the same console output (the summary box's random pun aside) and the same exit
# status. Also checks that arguments and exit status pass through (`--version`, a missing project folder).
# The first run downloads the FHIR packages the project needs into the FHIR package cache (~/.fhir).
# usage: scripts/sushi-e2e.sh [dir-containing-the-sushi-wrapper] [sushi.jar]
#        (defaults: sushi/, i.e. the source-checkout wrapper and sushi/target/sushi.jar)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BIN="$(cd "${1:-$ROOT/sushi}" && pwd)"
JAR="${2:-$ROOT/sushi/target/sushi.jar}"
T="$(mktemp -d)"
win() { case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) cygpath -w "$1" ;; *) echo "$1" ;; esac; }
wrapper() {
  case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*) cmd //c "$(win "$BIN/sushi.cmd")" "$@" ;;
    *) "$BIN/sushi" "$@" ;;
  esac
}
jar() { java -jar "$(win "$JAR")" "$@"; }
# the summary box's pun is random (sushi/PORTING.md D7); CR from Windows consoles
norm() { sed -e 's/\r$//' -e 's/^\([|║]\) .*  *\([0-9][0-9]* Errors\{0,1\}\)  *\([0-9][0-9]* Warnings\{0,1\}\) \([|║]\)$/\1 <pun> \2 \3 \4/' "$1"; }
run() {  # run <wrapper|jar> <name>: one build of the project at $T/ig, keeping fsh-generated, output and status
  rm -rf "$T/ig" && cp -R "$ROOT/sushi/tests/e2e/ig" "$T/ig"
  set +e; "$1" "$(win "$T/ig")" -o "$(win "$T/ig")" > "$T/$2.out" 2> "$T/$2.err"; echo $? > "$T/$2.rc"; set -e
  rm -rf "${T:?}/$2.gen" && mv "$T/ig/fsh-generated" "$T/$2.gen" 2>/dev/null || mkdir -p "$T/$2.gen"
}
echo "== warm-up (fills the FHIR package cache)"; run jar warm; cat "$T/warm.rc"
run jar ref
run wrapper wrap
fail=0
diff -r "$T/ref.gen" "$T/wrap.gen" > "$T/gen.diff" || { echo "FAIL: fsh-generated differs"; head -20 "$T/gen.diff"; fail=1; }
diff <(norm "$T/ref.out") <(norm "$T/wrap.out") || { echo "FAIL: stdout differs"; fail=1; }
# stderr: the same, apart from the wrapper's own one-time "downloading sushi.jar" notice
diff <(norm "$T/ref.err") <(norm "$T/wrap.err" | grep -v '^sushi (fhir-ig-tools wrapper): downloading ') || { echo "FAIL: stderr differs"; fail=1; }
cmp -s "$T/ref.rc" "$T/wrap.rc" || { echo "FAIL: exit status $(cat "$T/ref.rc") vs $(cat "$T/wrap.rc")"; fail=1; }
[ "$(cat "$T/ref.rc")" = 0 ] || { echo "FAIL: the reference run failed"; cat "$T/ref.out"; fail=1; }
n=$(find "$T/ref.gen" -type f | wc -l | tr -d ' ')
[ "$n" -gt 0 ] || { echo "FAIL: nothing generated"; fail=1; }
# passthrough: --version, and a missing project folder (an error exit)
[ "$(wrapper --version 2>&1 | sed 's/\r$//')" = "$(jar --version 2>&1 | sed 's/\r$//')" ] || { echo "FAIL: --version differs"; fail=1; }
set +e; wrapper "$(win "$T/no-such-project")" > "$T/m1" 2>&1; r1=$?; jar "$(win "$T/no-such-project")" > "$T/m2" 2>&1; r2=$?; set -e
[ "$r1" = "$r2" ] && [ "$r1" != 0 ] || { echo "FAIL: missing-project exit status $r1 (wrapper) vs $r2 (jar)"; fail=1; }
diff <(norm "$T/m2") <(norm "$T/m1") > /dev/null || { echo "FAIL: missing-project output differs"; fail=1; }
[ $fail = 0 ] && echo "sushi e2e ok: $n files identical, output and exit status identical, arguments pass through"
exit $fail
