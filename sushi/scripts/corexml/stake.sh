#!/bin/bash
# Core-XML evaluation, step 2: what changes in SUSHI's output if predefined XML resources are converted with
# org.hl7.fhir.core instead of fhir-tool's xmlToObj. Runs the jar twice on the same staged copy of one corpus IG, at
# the same path, with the same package cache (the corpus overlay over the frozen snapshot, as scripts/diff-run.sh):
#   port: java -jar sushi.jar --out . .                                  (today's default)
#   core: java CoreXml.java sushi <baseline> <r6 core> --out . .         (DiskBasedVirtualPackage.setXmlConverter)
# and classifies the difference with scripts/classify-diff.py ("ts" side = port, "java" side = core).
# usage: sushi/scripts/corexml/stake.sh <IG> ...   (wrap in work/locks/waitfree.sh timing; run.sh first for cp.txt)
# Reports: $ROOT/work/sushi-core-review/stake/<IG>.json (COREXML_REPORTS) and a one-line summary per IG on stdout.
set -eu
HERE="$(cd "$(dirname "$0")" && pwd)"; SUSHI="$(cd "$HERE/../.." && pwd)"; ROOT="${ROOT:-$(cd "$SUSHI/.." && pwd)}"
. "$ROOT/scripts/lib/vmdata.sh"
REP=${COREXML_REPORTS:-$ROOT/work/sushi-core-review/stake}; mkdir -p "$REP"
W=${COREXML_WORK:-$VMDATA/sushi-core-review}; JAR=${JAR:-$VMDATA/sushi-port/build/sushi-core-review/sushi.jar}
SNAP=$VMDATA/determinism/snap/baseline-20261120; R6="$SNAP/packages/hl7.fhir.r6.core#6.0.0-snapshot1"
REPOS=${REPOS:-$ROOT/repos}   # ROOT = the workspace holding repos/ (the corpus) and work/sushi-spike/corpus.tsv
vm "mkdir -p $W/stake"; vm "cat > $W/CoreXml.java" < "$HERE/CoreXml.java"
for IG in "$@"; do
  B=$(awk -F'\t' -v i="$IG" '$1==i{print $2}' "$HERE/baselines.tsv")
  SUB=$(awk -F'\t' -v n="$IG" '$1==n{print $8}' "$ROOT/work/sushi-spike/corpus.tsv"); [ "$SUB" = . ] && SUB=""
  S=$W/stake/$IG; PKG=$VMDATA/sushi-spike/pkg/$IG
  vm "rm -rf '$S' && mkdir -p '$S/src' '$PKG/upper/packages'"
  python3 "$SUSHI/scripts/stage-src.py" "$REPOS/$IG" | docker run --rm -i -v "$VMDATA:$VMDATA" --entrypoint bash "$IMAGE" -c "tar -xf - -C '$S/src'"
  for MODE in port core; do
    vol="corexml-$IG-$MODE"; vol="${vol//[^A-Za-z0-9_.-]/_}"; docker volume rm -f "$vol" >/dev/null 2>&1 || true
    vm "mkdir -p '$S/$MODE/pkgup' '$S/$MODE/pkgwork'"
    docker volume create --driver local --opt type=overlay --opt device=overlay \
      --opt "o=lowerdir=$PKG/upper:$SNAP,upperdir=$S/$MODE/pkgup,workdir=$S/$MODE/pkgwork" "$vol" >/dev/null
    if [ $MODE = port ]; then CMD="java -jar '$JAR'"; else CMD="java -cp '$JAR:'\$(cat '$W/cp.txt') '$W/CoreXml.java' sushi $B '$R6'"; fi
    docker run --rm -v "$VMDATA:$VMDATA" -v "$vol:/root/.fhir" -e LC_ALL=C.UTF-8 --entrypoint bash "$IMAGE" -c "
      set -u; rm -rf '$S/ig' && cp -a '$S/src' '$S/ig' && cd '$S/ig/$SUB'
      $CMD --out . . > '$S/$MODE.log' 2>&1; echo rc=\$? >> '$S/$MODE.log'
      mkdir -p fsh-generated '$S/$MODE/out' && cp -a fsh-generated '$S/$MODE/out/' && rm -rf '$S/ig' '$S/$MODE/pkgup' '$S/$MODE/pkgwork'"
    docker volume rm -f "$vol" >/dev/null 2>&1 || true
  done
  # the pun in the summary box is random (D7)
  vm "for m in port core; do sed -E 's/^\\| .* ([0-9]+ Errors? +[0-9]+ Warnings?) \\|\$/| PUN \\1 |/' '$S/'\$m.log > '$S/'\$m.nopun.log; done"
  docker run --rm -i -v "$VMDATA:$VMDATA" --entrypoint python3 "$IMAGE" - "$S/port/out/fsh-generated" "$S/core/out/fsh-generated" \
    "$S/port.log" "$S/core.log" /dev/null /dev/null < "$SUSHI/scripts/classify-diff.py" > "$REP/$IG.json" 2>/dev/null || true
  echo "$IG ($B): files $(vm "diff -rq '$S/port/out' '$S/core/out' | wc -l") differ; log lines $(vm "diff '$S/port.nopun.log' '$S/core.nopun.log' | grep -c '^[<>]'") differ; $(vm "tail -1 '$S/port.log'") / $(vm "tail -1 '$S/core.log'")"
done
