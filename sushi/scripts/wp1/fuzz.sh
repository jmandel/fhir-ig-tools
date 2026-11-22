#!/bin/bash
# Generate a fuzz set on the VM and dump it with the JS runtime (ground truth).
# usage: scripts/wp1/fuzz.sh <fsh|mini> <set name> <count> <seed>     -> /srv/ig/sushi-wp1/<set>, jsdump/<set>
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
MODE="$1"; SET="$2"; COUNT="$3"; SEED="$4"
D=/srv/ig/sushi-wp1
docker run --rm -i -v /srv/ig:/srv/ig -v "$HERE:$HERE:ro" --entrypoint bash ig-publisher-localdev-ca -c "
  set -e; rm -rf '$D/$SET' '$D/jsdump/$SET'
  find '$D/corpus' -name '*.fsh' -type f | sort > '$D/seeds-corpus.txt'
  find '$D/fixtures' -name '*.fsh' -type f | sort > '$D/seeds-fixtures.txt'
  python3 -I '$HERE/fuzz-gen.py' '$MODE' '$D/$SET' '$COUNT' '$SEED' '$D/seeds-corpus.txt' '$D/seeds-fixtures.txt'
"
"$HERE/js-oracle.sh" "$MODE" "$D/$SET" "$D/jsdump/$SET"
