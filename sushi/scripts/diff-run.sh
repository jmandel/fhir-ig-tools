#!/bin/bash
# Differential run: TS SUSHI 3.21.1 vs the Java port on one corpus IG, same inputs and FHIR package cache.
# usage: scripts/diff-run.sh <IG> [label]       e.g. scripts/diff-run.sh HL7__genomics-reporting
#                                                     scripts/diff-run.sh IHE__ITI.MHD ref-a   (reuse a reference run)
# TS side: $VMDATA/sushi-spike/runs/<IG>-<label> if it has an out.tar (the 6 reference IGs: label ref-a / ref-b),
#   else <IG>-ts-<label>, produced by work/sushi-spike/tools/run-sushi.sh (node in the reference image, per-IG
#   package-cache overlay over the frozen snapshot) when missing.
# Java side: the same source copy at the SAME path the TS run used (<TS run>/ig, so absolute paths in messages match),
#   `java -jar sushi-java.jar --out . .` (the TS command line) under the same timeit.py wrapper (stdout+stderr, TZ=UTC),
#   with a package cache = TS's per-IG overlay upper over the snapshot as read-only lowers + a private upper.
#   -> $VMDATA/sushi-port/runs/<IG>-<label>/{out.tar,sushi.log,time.txt}
# Report: work/sushi-port/diff/<IG>-<label>.json (classified, scripts/classify-diff.py, incl. exit codes); summary on
# stderr. Wrap in work/locks/waitfree.sh timing (untimed). Build the jar first: scripts/build.sh -B -q -DskipTests
# package (lib/ with the runtime dependencies sits next to it). JAR=... overrides the jar; the
# extracted trees ($JR/cmp) are kept only when files differ (KEEP=1: always).
set -eu
HERE="$(cd "$(dirname "$0")/.." && pwd)"; ROOT="${ROOT:-$ROOT}"; . "$ROOT/scripts/lib/vmdata.sh"
IG=$1; LABEL=${2:-diff}; SP=$VMDATA/sushi-port; JAR=${JAR:-$SP/build/$(basename "$HERE")/sushi-java-0.1.0-SNAPSHOT.jar}
TSR=$VMDATA/sushi-spike/runs/$IG-$LABEL
vm "[ -f '$TSR/out.tar' ]" || TSR=$VMDATA/sushi-spike/runs/$IG-ts-$LABEL
JR=$SP/runs/$IG-$LABEL; REP="$ROOT/work/sushi-port/diff"; mkdir -p "$REP"
vm "[ -f '$TSR/out.tar' ]" || "$ROOT/work/sushi-spike/tools/run-sushi.sh" "$IG" "ts-$LABEL" >&2
SUB=$(awk -F'\t' -v n="$IG" '$1==n{print $8}' "$ROOT/work/sushi-spike/corpus.tsv"); [ "$SUB" = . ] && SUB=""
SNAP=$VMDATA/determinism/snap/baseline-20261120; PKG=$VMDATA/sushi-spike/pkg/$IG
vol="sushij-$IG-$LABEL"; vol="${vol//[^A-Za-z0-9_.-]/_}"; docker volume rm -f "$vol" >/dev/null 2>&1 || true
vm "rm -rf '$JR' && mkdir -p '$JR/pkgup' '$JR/pkgwork' '$PKG/upper/packages' && rm -rf '$TSR/ig' && cp -a '$VMDATA/sushi-spike/src/$IG' '$TSR/ig'"
docker volume create --driver local --opt type=overlay --opt device=overlay \
  --opt "o=lowerdir=$PKG/upper:$SNAP,upperdir=$JR/pkgup,workdir=$JR/pkgwork" "$vol" >/dev/null
docker run --rm -v "$VMDATA:$VMDATA" -v "$vol:/root/.fhir" -e LC_ALL=C.UTF-8 --entrypoint bash "$IMAGE" -c "
  set -u; cd '$TSR/ig/$SUB'
  python3 /srv/ig/sushi-spike/tools/timeit.py '$JR/sushi.log' java -jar '$JAR' --out . . > '$JR/time.txt'
  mkdir -p fsh-generated; tar -C . -cf '$JR/out.tar' fsh-generated
  mkdir -p '$JR/cmp/ts' '$JR/cmp/java'; tar -C '$JR/cmp/ts' -xf '$TSR/out.tar'; tar -C '$JR/cmp/java' -xf '$JR/out.tar'
  rm -rf '$TSR/ig' '$JR/pkgup' '$JR/pkgwork'"
docker volume rm -f "$vol" >/dev/null 2>&1 || true
docker run --rm -i -v "$VMDATA:$VMDATA" --entrypoint python3 "$IMAGE" - "$JR/cmp/ts/fsh-generated" "$JR/cmp/java/fsh-generated" \
  "$TSR/sushi.log" "$JR/sushi.log" "$TSR/time.txt" "$JR/time.txt" < "$HERE/scripts/classify-diff.py" > "$REP/$IG-$LABEL.json"
# keep the extracted trees only when something differs (or KEEP=1)
if [ "${KEEP:-0}" != 1 ] && grep -q '"files": {}' "$REP/$IG-$LABEL.json"; then vm "rm -rf '$JR/cmp' '$JR/out.tar'"; fi
echo "$IG $LABEL: $(vm "grep -o 'rc=[0-9]*' '$TSR/time.txt'" | tr '\n' ' ')/ java $(vm "grep -o 'wall=[0-9.]* .*rc=[0-9]*' '$JR/time.txt'")  report: $REP/$IG-$LABEL.json"
