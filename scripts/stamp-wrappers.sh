#!/bin/bash
# Write release copies of the wrappers that pin a shim version and the SHA-256 of its shim.jar, so
# each works on its own (downloads and caches that jar on first use; see jekyll/jekyll-shim).
# usage: scripts/stamp-wrappers.sh <version, e.g. 0.1.0> <shim.jar> <out-dir> [sha256 override, for tests]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="$1"; JAR="$2"; OUT="$3"
if [ -n "${4:-}" ]; then SHA="$4"
elif command -v sha256sum >/dev/null 2>&1; then SHA=$(sha256sum "$JAR" | cut -c1-64)
else SHA=$(shasum -a 256 "$JAR" | cut -c1-64); fi
mkdir -p "$OUT"
for f in jekyll-shim jekyll-shim.cmd; do
  sed -e "s/@SHIM_VERSION@/$VERSION/" -e "s/@SHIM_SHA256@/$SHA/" "$ROOT/jekyll/$f" > "$OUT/$f"
done
chmod +x "$OUT/jekyll-shim"
echo "stamped $OUT/{jekyll-shim,jekyll-shim.cmd}: version $VERSION sha256 $SHA"
