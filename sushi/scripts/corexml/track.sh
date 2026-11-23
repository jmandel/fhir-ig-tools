#!/bin/bash
# XML-usage census: which corpus IGs actually use content of their predefined XML resources, and for what.
# Runs sushi.jar once per IG (default fhir-tool conversion, so output is unchanged) with two harness-only class
# copies ahead of the jar on the class path (track/patch.py): Frozen records every property read on a resource that
# came from an XML file, FHIRDefinitions every metadata lookup that resolves to one; the caller (first SUSHI frames
# outside the fishing layer) says what for. track/report.py classifies each IG.
# usage: ROOT=<workspace> sushi/scripts/corexml/track.sh <IG> ...   (wrap in work/locks/waitfree.sh timing)
# Output: $W/track/<IG>/track.tsv (kind, resource, property path, caller), $W/track/<IG>/sushi.log
set -eu
HERE="$(cd "$(dirname "$0")" && pwd)"; SUSHI="$(cd "$HERE/../.." && pwd)"; REPO="$(cd "$SUSHI/.." && pwd)"
ROOT="${ROOT:-$REPO}"; . "$ROOT/scripts/lib/vmdata.sh"
W=${COREXML_WORK:-$VMDATA/sushi-core-review}; JAR=${JAR:-$VMDATA/sushi-port/build/sushi-core-review/sushi.jar}
SNAP=$VMDATA/determinism/snap/baseline-20261120; REPOS=${REPOS:-$ROOT/repos}
T=$W/track; vm "rm -rf $T/src-java $T/classes && mkdir -p $T/src-java $T/classes"
TMP=$(mktemp -d); python3 "$HERE/track/patch.py" "$REPO" "$TMP"; cp "$HERE/track/XmlTrack.java" "$TMP/org/hl7/fhir/common/js/"
cp "$HERE/track/TrackMain.java" "$TMP/"
COPYFILE_DISABLE=1 tar -C "$TMP" -cf - . | docker run --rm -i -v "$VMDATA:$VMDATA" --entrypoint bash "$IMAGE" -c "tar -xf - -C $T/src-java"
rm -rf "$TMP"
vm "cd $T/src-java && javac -nowarn -cp '$JAR' -d $T/classes \$(find . -name '*.java') 2>&1 | grep -v '^Note:' || true"
for IG in "$@"; do
  SUB=$(awk -F'\t' -v n="$IG" '$1==n{print $8}' "$ROOT/work/sushi-spike/corpus.tsv"); [ "$SUB" = . ] && SUB=""
  S=$T/$IG; PKG=$VMDATA/sushi-spike/pkg/$IG
  vm "rm -rf '$S' && mkdir -p '$S/src' '$S/pkgup' '$S/pkgwork' '$PKG/upper/packages'"
  python3 "$SUSHI/scripts/stage-src.py" "$REPOS/$IG" | docker run --rm -i -v "$VMDATA:$VMDATA" --entrypoint bash "$IMAGE" -c "tar -xf - -C '$S/src'"
  vol="xmltrack-$IG"; vol="${vol//[^A-Za-z0-9_.-]/_}"; docker volume rm -f "$vol" >/dev/null 2>&1 || true
  docker volume create --driver local --opt type=overlay --opt device=overlay \
    --opt "o=lowerdir=$PKG/upper:$SNAP,upperdir=$S/pkgup,workdir=$S/pkgwork" "$vol" >/dev/null
  docker run --rm -v "$VMDATA:$VMDATA" -v "$vol:/root/.fhir" -e LC_ALL=C.UTF-8 --entrypoint bash "$IMAGE" -c "
    set -u; cd '$S/src/$SUB'; : > '$S/track.tsv'
    java -Dxmltrack.out='$S/track.tsv' -cp '$T/classes:$JAR' TrackMain --out . . > '$S/sushi.log' 2>&1; echo rc=\$? >> '$S/sushi.log'
    rm -rf '$S/src' '$S/pkgup' '$S/pkgwork'"
  docker volume rm -f "$vol" >/dev/null 2>&1 || true
  echo "$IG: $(vm "wc -l < '$S/track.tsv'") usage records; $(vm "tail -1 '$S/sushi.log'")"
done
