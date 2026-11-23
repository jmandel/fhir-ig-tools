#!/bin/bash
# Core-XML evaluation, step 1: convert every predefined FHIR XML file of the corpus twice -- with the port's
# fhir-tool xmlToObj (what sushi.jar uses) and with org.hl7.fhir.core's parsers for the IG's FHIR version -- and
# compare the two as FHIR content (compare.py). Results: docs/core-divergences.md "XML conversion".
# usage: sushi/scripts/corexml/run.sh            (wrap in work/locks/waitfree.sh timing)
# Inputs: $VMDATA/sushi-port/fhirtool/corpus/<IG>/... (the 798 predefined XML files of 27 corpus IGs, as staged for
# scripts/fhirtool/diff.sh) and baselines.tsv (IG -> r4 | r5 | r6 from its sushi-config.yaml fhirVersion).
# Needs the jar: SUSHI_BUILD_DIR=$VMDATA/sushi-port/build/sushi-core-review sushi/scripts/build.sh -B -q -DskipTests package
set -eu
HERE="$(cd "$(dirname "$0")" && pwd)"; ROOT="${ROOT:-$(cd "$HERE/../../.." && pwd)}"; . "$ROOT/scripts/lib/vmdata.sh"
W=${COREXML_WORK:-$VMDATA/sushi-core-review}; JAR=${JAR:-$VMDATA/sushi-port/build/sushi-core-review/sushi.jar}
C=$VMDATA/sushi-port/fhirtool/corpus; R6=$VMDATA/determinism/snap/baseline-20261120/packages/hl7.fhir.r6.core#6.0.0-snapshot1
vm "mkdir -p $W"
# class path of the r4/r5 model jars (harness only)
[ -n "$(vm "cat $W/cp.txt 2>/dev/null")" ] || "$ROOT/build.sh" -B -q -f "$HERE/pom.xml" dependency:build-classpath -Dmdep.outputFile="$W/cp.txt"
vm "cat > $W/baselines.tsv" < "$HERE/baselines.tsv"; vm "cat > $W/CoreXml.java" < "$HERE/CoreXml.java"
vm "cd $C && find . -name '*.xml' | sed 's|^\./||' | sort | while read f; do ig=\${f%%/*}; b=\$(awk -F'\t' -v i=\$ig '\$1==i{print \$2}' $W/baselines.tsv); printf '%s\t%s\t%s\n' \"\$f\" \"$C/\$f\" \"\$b\"; done > $W/inputs.tsv; wc -l < $W/inputs.tsv"
vm "cd $W && java -Xmx3g -cp '$JAR:'\$(cat cp.txt) CoreXml.java convert inputs.tsv convert.ndjson '$R6'"
docker run --rm -i -v "$VMDATA:$VMDATA" --entrypoint python3 "$IMAGE" - "$W/convert.ndjson" "$W/compare.json" < "$HERE/compare.py"
