#!/bin/bash
# WP-9 differential: run the JS oracle and the Java port over an inputs file on the VM, compare.
# usage: scripts/xhtml/diff.sh <vm-inputs.ndjson> [max-shown]   (run scripts/build.sh -B -q -DskipTests test-compile first)
set -euo pipefail
HERE="$(cd "$(dirname "$0")/../.." && pwd)"
IN="$1"; MAX="${2:-10}"
NM=/srv/ig/sushi-spike/sushi/node_modules
OUT=${SUSHI_BUILD_DIR:-/srv/ig/sushi-port/build/$(basename "$HERE")}
docker run --rm -v /srv/ig:/srv/ig -v "$HERE/scripts:/scripts:ro" --entrypoint bash "${IMAGE:-ig-publisher-localdev-ca}" -c "
  set -e; cd \$(dirname '$IN'); b=\$(basename '$IN' .ndjson)
  node /scripts/xhtml/oracle.js $NM < '$IN' > \$b.js.out
  java -Xss64m -cp $OUT/classes:$OUT/test-classes org.hl7.fhir.sushi.xhtml.XhtmlOracleMain < '$IN' > \$b.java.out
  python3 /scripts/xhtml/compare.py '$IN' \$b.js.out \$b.java.out $MAX"
