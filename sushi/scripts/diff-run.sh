#!/bin/bash
# Differential run: TS SUSHI 3.21.1 vs the Java port on one corpus IG, same inputs and FHIR package cache.
# usage: scripts/diff-run.sh <IG> [label]          e.g. scripts/diff-run.sh HL7__genomics-reporting
# TS side: reuses work/sushi-spike/tools/run-sushi.sh (node in the reference image, per-IG package-cache overlay
#   over the frozen snapshot) -> $VMDATA/sushi-spike/runs/<IG>-<label>/{out.tar,sushi.log}; skipped if present.
# Java side: same source copy, same overlay, `java -jar sushi-java.jar build --out . .`
#   -> $VMDATA/sushi-port/runs/<IG>-<label>/{out.tar,sushi.log,exit}
# Report: work/sushi-port/diff/<IG>-<label>.json (classified, scripts/classify-diff.py); summary on stderr.
# Wrap in work/locks/waitfree.sh timing (untimed). Build the jar first: scripts/build.sh -B -q -DskipTests package
set -eu
HERE="$(cd "$(dirname "$0")/.." && pwd)"; ROOT="${ROOT:-$ROOT}"; . "$ROOT/scripts/lib/vmdata.sh"
IG=$1; LABEL=${2:-diff}; SP=$VMDATA/sushi-port; JAR=${JAR:-$SP/build/$(basename "$HERE")/sushi-java-0.1.0-SNAPSHOT.jar}
TSR=$VMDATA/sushi-spike/runs/$IG-ts-$LABEL; JR=$SP/runs/$IG-$LABEL; REP="$ROOT/work/sushi-port/diff"; mkdir -p "$REP"
vm "[ -f '$TSR/out.tar' ]" || "$ROOT/work/sushi-spike/tools/run-sushi.sh" "$IG" "ts-$LABEL"
SUB=$(awk -F'\t' -v n="$IG" '$1==n{print $8}' "$ROOT/work/sushi-spike/corpus.tsv"); [ "$SUB" = . ] && SUB=""
SNAP=$VMDATA/determinism/snap/baseline-20261120; PKG=$VMDATA/sushi-spike/pkg/$IG
vol="sushij-$IG-$LABEL"; vol="${vol//[^A-Za-z0-9_.-]/_}"; docker volume rm -f "$vol" >/dev/null 2>&1 || true
vm "rm -rf '$JR' && mkdir -p '$JR' '$PKG/upper/packages' '$PKG/work' && cp -a '$VMDATA/sushi-spike/src/$IG' '$JR/ig'"
docker volume create --driver local --opt type=overlay --opt device=overlay \
  --opt "o=lowerdir=$SNAP,upperdir=$PKG/upper,workdir=$PKG/work" "$vol" >/dev/null
docker run --rm -v "$VMDATA:$VMDATA" -v "$vol:/root/.fhir" --entrypoint bash "$IMAGE" -c "
  cd '$JR/ig/$SUB'; set +e; java -jar '$JAR' build --out . . > '$JR/sushi.log' 2>&1; echo \$? > '$JR/exit'; set -e
  mkdir -p fsh-generated; tar -C . -cf '$JR/out.tar' fsh-generated
  mkdir -p '$JR/cmp/ts' '$JR/cmp/java'; tar -C '$JR/cmp/ts' -xf '$TSR/out.tar'; tar -C '$JR/cmp/java' -xf '$JR/out.tar'
  rm -rf '$JR/ig'"
docker volume rm -f "$vol" >/dev/null 2>&1 || true
docker run --rm -i -v "$VMDATA:$VMDATA" --entrypoint python3 "$IMAGE" - "$JR/cmp/ts/fsh-generated" "$JR/cmp/java/fsh-generated" \
  "$TSR/sushi.log" "$JR/sushi.log" < "$HERE/scripts/classify-diff.py" > "$REP/$IG-$LABEL.json"
echo "exit: ts=$(vm "cat '$TSR/time.txt' 2>/dev/null | tr '\n' ' '") java=$(vm "cat '$JR/exit'")  report: $REP/$IG-$LABEL.json"
