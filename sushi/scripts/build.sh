#!/bin/bash
# Build/test sushi-java with Maven inside the reference image on the Lima VM (the Mac has no JDK).
#
# usage: scripts/build.sh [maven args...]        default: -q -B verify (compile + ANTLR + all tests + jar)
#   scripts/build.sh -B test -Dtest='JsonWriterTest'     one test class
#   scripts/build.sh -B -DskipTests package              jar only
#
# Heavy outputs live on the VM disk (D11, scripts/lib/vmdata.sh): Maven repo $VMDATA/sushi-port/m2 (shared),
# target dir $VMDATA/sushi-port/build/<checkout-name> (one per checkout/worktree, so parallel agents don't clash).
# The checkout itself is mounted read-only at its own path; nothing is written into it.
# Start heavy runs with work/locks/waitfree.sh timing (see PORTING.md "Shared machine").
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
ROOT="${ROOT:-$ROOT}"
VMDATA="${VMDATA:-/srv/ig}"
IMAGE="${IMAGE:-ig-publisher-localdev-ca}"
NAME="$(basename "$HERE")"
SP="$VMDATA/sushi-port"
OUT="${SUSHI_BUILD_DIR:-$SP/build/$NAME}"
MVN="$SP/tools/apache-maven-3.9.9/bin/mvn"
[ $# -gt 0 ] || set -- -q -B verify
SRC="$HERE"
if [ -n "${SUSHI_EXCLUDE:-}" ]; then
  # SUSHI_EXCLUDE="src/main/java/org/hl7/fhir/sushi/foo ...": build a snapshot of the checkout without those paths
  # (e.g. someone else's half-written package), copied to the VM disk.
  SRC="$SP/snap/$NAME"; EX=(--exclude .git); for p in $SUSHI_EXCLUDE; do EX+=(--exclude "./$p"); done
  COPYFILE_DISABLE=1 tar -C "$HERE" --no-mac-metadata --no-xattrs "${EX[@]}" -cf - . | docker run --rm -i \
    -v "$VMDATA:$VMDATA" --entrypoint bash "$IMAGE" -c "rm -rf '$SRC' && mkdir -p '$SRC' && tar -xf - -C '$SRC'"
fi
# -T1C would be faster but output interleaving makes failures hard to read; keep sequential.
docker run --rm -i -v "$VMDATA:$VMDATA" -v "$HERE:$HERE:ro" -w "$SRC" --entrypoint bash "$IMAGE" -c '
  set -e; mkdir -p "$0"
  exec "$1" -Dmaven.repo.local="$2/m2" -Dsushi.buildDir="$0" "${@:3}"' "$OUT" "$MVN" "$SP" "$@"
echo "build dir (VM): $OUT"
