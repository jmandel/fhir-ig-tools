#!/bin/bash
# Write release copies of a tool's wrappers that pin a version and the SHA-256 of its jar, so each works on its
# own (downloads and caches that jar on first use; see jekyll/jekyll-shim and sushi/sushi).
# usage: scripts/stamp-wrappers.sh [--sushi] <version, e.g. 0.2.0> <jar> <out-dir> [sha256 override, for tests]
#   default: jekyll/jekyll-shim, jekyll/jekyll-shim.cmd for jekyll.jar
#   --sushi: sushi/sushi, sushi/sushi.cmd for sushi.jar
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOOL=jekyll; WRAPPERS=(jekyll-shim jekyll-shim.cmd); P=SHIM
if [ "${1:-}" = --sushi ]; then TOOL=sushi; WRAPPERS=(sushi sushi.cmd); P=SUSHI; shift; fi
VERSION="$1"; JAR="$2"; OUT="$3"
if [ -n "${4:-}" ]; then SHA="$4"
elif command -v sha256sum >/dev/null 2>&1; then SHA=$(sha256sum "$JAR" | cut -c1-64)
else SHA=$(shasum -a 256 "$JAR" | cut -c1-64); fi
mkdir -p "$OUT"
for f in "${WRAPPERS[@]}"; do
  sed -e "s/@${P}_VERSION@/$VERSION/" -e "s/@${P}_SHA256@/$SHA/" "$ROOT/$TOOL/$f" > "$OUT/$f"
done
chmod +x "$OUT/${WRAPPERS[0]}"
echo "stamped $OUT/{${WRAPPERS[0]},${WRAPPERS[1]}}: version $VERSION sha256 $SHA"
