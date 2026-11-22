#!/bin/bash
# Java half of the WP-8 lookup probe: replay the TS probe of <IG> with the Java FHIRDefinitions (core package layer)
# in the reference image, with the same package snapshot (+ per-IG overlay) and the IG source at the same path as the
# TS run. Output: /srv/ig/sushi-port/probe/<IG>.java.ndjson (+ .java.log).
# usage: scripts/probe/run-java-probe.sh <IG>       (TS half first: run-sushi.sh -n "-r .../probe.js" <IG> probe)
# Prereq: scripts/build.sh -B -q -DskipTests test-compile dependency:build-classpath -Dmdep.outputFile=$BUILD/cp.txt
set -eu
ROOT="${ROOT:-$(cd "$(dirname "$0")/../../.." && pwd)}"; . "$ROOT/scripts/lib/vmdata.sh"
HERE="$(cd "$(dirname "$0")/../.." && pwd)"
NAME=$1; LABEL=${2:-probe}
SP=$VMDATA/sushi-spike; BUILD=${SUSHI_BUILD_DIR:-$VMDATA/sushi-port/build/$(basename "$HERE")}; P=$VMDATA/sushi-port/probe
SNAP=$VMDATA/determinism/snap/baseline-20261120
SUB=$(awk -F'\t' -v n="$NAME" '$1==n{print $8}' "$ROOT/work/sushi-spike/corpus.tsv"); [ "$SUB" = . ] && SUB=""
vm "[ -d '$SP/src/$NAME' ]" || to_vm "$ROOT/repos/$NAME" "$SP/src/$NAME" --exclude .git --exclude output --exclude temp --exclude input-cache/txcache
R=$SP/runs/$NAME-$LABEL; PKG=$P/pkg/$NAME
vm "rm -rf '$R/ig' && mkdir -p '$R' '$PKG/upper/packages' '$PKG/work' && cp -a '$SP/src/$NAME' '$R/ig'"
vol="jprobe-$NAME"; vol="${vol//[^A-Za-z0-9_.-]/_}"
docker volume rm -f "$vol" >/dev/null 2>&1 || true
docker volume create --driver local --opt type=overlay --opt device=overlay \
  --opt "o=lowerdir=$SNAP,upperdir=$PKG/upper,workdir=$PKG/work" "$vol" >/dev/null
docker run --rm -v "$VMDATA:$VMDATA" -v "$vol:/root/.fhir" --entrypoint bash "$IMAGE" -c "
  set -u; cd '$R/ig/$SUB'
  java -cp '$BUILD/classes:$BUILD/test-classes:'\$(cat '$BUILD/cp.txt') org.hl7.fhir.sushi.fhirdefs.probe.ProbeReplay \
    '$P/$NAME.ts.ndjson' '$P/$NAME.java.ndjson' > '$P/$NAME.java.log' 2>&1 || echo \"replay rc=\$?\"
  rm -rf '$R/ig'
"
docker volume rm -f "$vol" >/dev/null 2>&1 || true
vm "rm -rf '$PKG/work'; du -sh '$PKG/upper' | cut -f1; tail -3 '$P/$NAME.java.log'"
