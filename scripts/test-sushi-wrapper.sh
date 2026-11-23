#!/bin/bash
# Check the self-downloading SUSHI wrappers (sushi/sushi, sushi/sushi.cmd) as a release ships them; a local HTTP
# server stands in for GitHub Releases. The same checks as scripts/test-wrapper.sh does for jekyll-shim:
#   1. a stamped wrapper alone in a folder downloads sushi.jar, verifies it, caches it, and builds the end-to-end
#      FSH project (scripts/sushi-e2e.sh) exactly like `java -jar sushi.jar`, passing arguments and exit status;
#   2. the next run uses the cache, with the server unreachable;
#   3. a wrapper stamped with the wrong SHA-256 refuses to run and caches nothing;
#   4. with a Java older than 21 the wrapper stops with a clear message (a stand-in `java` that reports 17).
# usage: scripts/test-sushi-wrapper.sh [sushi.jar, default sushi/target/sushi.jar]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="$(cd "$(dirname "${1:-$ROOT/sushi/target/sushi.jar}")" && pwd)/$(basename "${1:-sushi.jar}")"
T="$(mktemp -d)"
PY=python3; command -v python3 >/dev/null 2>&1 || PY=python
win() { case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) cygpath -w "$1" ;; *) echo "$1" ;; esac; }

mkdir -p "$T/srv" && cp "$JAR" "$T/srv/sushi.jar"
PORT=18766
"$PY" -m http.server "$PORT" --bind 127.0.0.1 --directory "$T/srv" > "$T/http.log" 2>&1 &
SRV=$!
trap 'kill $SRV 2>/dev/null || true' EXIT
for _ in $(seq 1 50); do curl -fsI "http://127.0.0.1:$PORT/sushi.jar" >/dev/null 2>&1 && break; sleep 0.2; done

export FHIR_IG_SUSHI_HOME; FHIR_IG_SUSHI_HOME="$(win "$T/home")"
export FHIR_IG_SUSHI_URL="http://127.0.0.1:$PORT/sushi.jar"

echo "== 1. first run downloads, verifies, caches"
"$ROOT/scripts/stamp-wrappers.sh" --sushi 0.0.0-test "$JAR" "$T/w"
"$ROOT/scripts/sushi-e2e.sh" "$T/w" "$JAR"
[ -f "$T/home/0.0.0-test/sushi.jar" ] || { echo "FAIL: jar not cached"; exit 1; }
cmp -s "$JAR" "$T/home/0.0.0-test/sushi.jar" || { echo "FAIL: cached jar differs"; exit 1; }

echo "== 2. second run uses the cache (server stopped)"
kill $SRV; wait $SRV 2>/dev/null || true
"$ROOT/scripts/sushi-e2e.sh" "$T/w" "$JAR"

echo "== 3. wrong checksum is refused"
"$ROOT/scripts/stamp-wrappers.sh" --sushi 0.0.1-test "$JAR" "$T/bad" 0000000000000000000000000000000000000000000000000000000000000000
"$PY" -m http.server "$PORT" --bind 127.0.0.1 --directory "$T/srv" > "$T/http2.log" 2>&1 &
SRV=$!
for _ in $(seq 1 50); do curl -fsI "http://127.0.0.1:$PORT/sushi.jar" >/dev/null 2>&1 && break; sleep 0.2; done
if "$T/bad/sushi" --version > "$T/bad.log" 2>&1; then echo "FAIL: bad checksum accepted"; cat "$T/bad.log"; exit 1; fi
grep -q 'checksum mismatch' "$T/bad.log" || { echo "FAIL: no checksum message"; cat "$T/bad.log"; exit 1; }
[ ! -e "$T/home/0.0.1-test/sushi.jar" ] || { echo "FAIL: bad jar was cached"; exit 1; }
ls "$T/home/0.0.1-test" 2>/dev/null | grep -q part && { echo "FAIL: partial download left behind"; exit 1; }

echo "== 4. Java older than 21 is refused with a clear message"
mkdir -p "$T/jdk17/bin"
printf '#!/bin/sh\necho '\''openjdk version "17.0.9" 2023-10-17'\'' >&2\n' > "$T/jdk17/bin/java"; chmod +x "$T/jdk17/bin/java"
if JAVA_HOME="$T/jdk17" "$T/w/sushi" --version > "$T/old.log" 2>&1; then echo "FAIL: Java 17 accepted"; cat "$T/old.log"; exit 1; fi
grep -q 'needs Java 21 or newer, but .* is version 17.0.9' "$T/old.log" || { echo "FAIL: no Java version message"; cat "$T/old.log"; exit 1; }
echo "sushi wrapper bootstrap ok"
