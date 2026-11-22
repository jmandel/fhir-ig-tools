#!/bin/bash
# fhir-tool xmlToObj differential: JS (oracle.js) vs Java (FhirXmlOracleMain) on the corpus XML + mutations.
# usage: scripts/fhirtool/diff.sh [mutations-per-file] [seed]   (corpus files under /srv/ig/sushi-port/fhirtool/corpus)
set -eu
ROOT=$ROOT; . "$ROOT/scripts/lib/vmdata.sh"
HERE="$(cd "$(dirname "$0")/../.." && pwd)"
BUILD=$VMDATA/sushi-port/build/$(basename "$HERE"); D=$VMDATA/sushi-port/fhirtool; FT=$VMDATA/sushi-spike/sushi/node_modules/fhir-tool
vm "cat > $D/oracle.js" < "$HERE/scripts/fhirtool/oracle.js"
vm "cat > $D/cmp.py" <<'PY'
import json,sys
a={};b={}
for l in open(sys.argv[1],encoding='utf-8'):
    if l.strip(): o=json.loads(l); a[o['id']]=o['out']
for l in open(sys.argv[2],encoding='utf-8'):
    if l.strip(): o=json.loads(l); b[o['id']]=o['out']
diff=[k for k in a if a[k]!=b.get(k)]
corpus=[k for k in a if '#m' not in k]
print(f"inputs {len(a)} (corpus {len(corpus)}, errors in JS {sum(1 for k in a if a[k].startswith('ERR:'))}); diffs {len(diff)} (corpus {sum(1 for k in diff if '#m' not in k)})")
for k in diff[:int(sys.argv[3]) if len(sys.argv)>3 else 10]:
    x,y=a[k],b.get(k,'MISSING')
    i=next((j for j in range(min(len(x),len(y))) if x[j]!=y[j]),min(len(x),len(y)))
    print('==',k); print('  JS:  ',x[max(0,i-150):i+200]); print('  Java:',y[max(0,i-150):i+200])
PY
vm "cd $D && node oracle.js $FT inputs corpus inputs.ndjson ${1:-6} ${2:-7} && node oracle.js $FT run inputs.ndjson js.ndjson && \
  java -cp '$BUILD/classes:$BUILD/test-classes:'\$(cat '$BUILD/cp.txt') org.hl7.fhir.sushi.fhirdefs.xml.FhirXmlOracleMain inputs.ndjson java.ndjson && \
  python3 cmp.py js.ndjson java.ndjson ${3:-12}"
