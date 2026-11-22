#!/bin/bash
# t2j.sh <TestFile.test.ts basename in sushi/test/export> <JavaClass> [from to]
R=$ROOT; . $R/scripts/lib/vmdata.sh
docker run --rm -i -v "$VMDATA:$VMDATA" -v $R/sushi-java-wp7/scripts:/s:ro -e NODE_PATH=/srv/ig/sushi-spike/sushi/node_modules --entrypoint node "$IMAGE" /s/ts2junit/ts2junit.js /srv/ig/sushi-spike/sushi/test/export/$1 $2 org.hl7.fhir.sushi.export ${3:-} ${4:-}
