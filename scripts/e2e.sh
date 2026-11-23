#!/bin/bash
# End-to-end check of a wrapper as the IG Publisher calls it: `<wrapper> build --destination <dir>` in
# the source folder. Renders jekyll/tests/e2e/site and compares with the real-Jekyll output recorded in
# jekyll/tests/e2e/expected (scripts/domdiff.py, exact).
# usage: scripts/e2e.sh [dir-containing-jekyll-shim]   (default: jekyll/, i.e. jekyll/target/jekyll.jar)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BIN="$(cd "${1:-$ROOT/jekyll}" && pwd)"
OUT="$(mktemp -d)/site-out"
cd "$ROOT/jekyll/tests/e2e/site"
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) cmd //c "$(cygpath -w "$BIN/jekyll-shim.cmd")" build --destination "$(cygpath -w "$OUT")" ;;
  *) "$BIN/jekyll-shim" build --destination "$OUT" ;;
esac
PY=python3; command -v python3 >/dev/null 2>&1 || PY=python
"$PY" -I "$ROOT/scripts/domdiff.py" "$ROOT/jekyll/tests/e2e/expected" "$OUT"
