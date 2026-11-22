#!/bin/bash
# Check the self-downloading wrappers (jekyll/jekyll-shim, jekyll/jekyll-shim.cmd) as a release ships them:
# a local HTTP server stands in for GitHub Releases.
#   1. a stamped wrapper alone in a folder downloads shim.jar, verifies it, caches it, and renders the
#      end-to-end site (scripts/e2e.sh) identically to real Jekyll;
#   2. the next run uses the cache, with the server unreachable;
#   3. a wrapper stamped with the wrong SHA-256 refuses to run and caches nothing.
# usage: scripts/test-wrapper.sh [shim.jar, default jekyll/target/shim.jar]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="$(cd "$(dirname "${1:-$ROOT/jekyll/target/shim.jar}")" && pwd)/$(basename "${1:-shim.jar}")"
T="$(mktemp -d)"
PY=python3; command -v python3 >/dev/null 2>&1 || PY=python
win() { case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) cygpath -w "$1" ;; *) echo "$1" ;; esac; }

mkdir -p "$T/srv" && cp "$JAR" "$T/srv/shim.jar"
PORT=18765
"$PY" -m http.server "$PORT" --bind 127.0.0.1 --directory "$T/srv" > "$T/http.log" 2>&1 &
SRV=$!
trap 'kill $SRV 2>/dev/null || true' EXIT
for _ in $(seq 1 50); do curl -fsI "http://127.0.0.1:$PORT/shim.jar" >/dev/null 2>&1 && break; sleep 0.2; done

export FHIR_IG_JEKYLL_HOME; FHIR_IG_JEKYLL_HOME="$(win "$T/home")"
export FHIR_IG_JEKYLL_URL="http://127.0.0.1:$PORT/shim.jar"

echo "== 1. first run downloads, verifies, caches"
"$ROOT/scripts/stamp-wrappers.sh" 0.0.0-test "$JAR" "$T/w"
"$ROOT/scripts/e2e.sh" "$T/w"
[ -f "$T/home/0.0.0-test/shim.jar" ] || { echo "FAIL: jar not cached"; exit 1; }
cmp -s "$JAR" "$T/home/0.0.0-test/shim.jar" || { echo "FAIL: cached jar differs"; exit 1; }

echo "== 2. second run uses the cache (server stopped)"
kill $SRV; wait $SRV 2>/dev/null || true
"$ROOT/scripts/e2e.sh" "$T/w"

echo "== 3. wrong checksum is refused"
"$ROOT/scripts/stamp-wrappers.sh" 0.0.1-test "$JAR" "$T/bad" 0000000000000000000000000000000000000000000000000000000000000000
"$PY" -m http.server "$PORT" --bind 127.0.0.1 --directory "$T/srv" > "$T/http2.log" 2>&1 &
SRV=$!
for _ in $(seq 1 50); do curl -fsI "http://127.0.0.1:$PORT/shim.jar" >/dev/null 2>&1 && break; sleep 0.2; done
if "$ROOT/scripts/e2e.sh" "$T/bad" > "$T/bad.log" 2>&1; then echo "FAIL: bad checksum accepted"; cat "$T/bad.log"; exit 1; fi
grep -q 'checksum mismatch' "$T/bad.log" || { echo "FAIL: no checksum message"; cat "$T/bad.log"; exit 1; }
[ ! -e "$T/home/0.0.1-test/shim.jar" ] || { echo "FAIL: bad jar was cached"; exit 1; }
ls "$T/home/0.0.1-test" 2>/dev/null | grep -q part && { echo "FAIL: partial download left behind"; exit 1; }
echo "wrapper bootstrap ok"
