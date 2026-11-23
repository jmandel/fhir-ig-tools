#!/bin/bash
# usage: fuzzround.sh <seed> <count>  (differential fuzz; prints non-D7 failures)
set -e
cd "$(dirname "$0")/../../.."
S=$1; N=$2; D=jekyll/tests/kramdown/fuzz; mkdir -p $D
python3 jekyll/tests/kramdown/fuzz.py $S $N $D/f$S.jsonl
jekyll/oracle/oracle.sh $D/f$S.jsonl $D/f$S-exp.jsonl
BUILD=build-kramdown jekyll/run.sh org.hl7.fhir.jekyll.test.DiffRunner $D/f$S.jsonl $D/f$S-exp.jsonl --report /w/$D/f$S-report.jsonl --show 0 | tail -1 || true
python3 - $D/f$S-report.jsonl <<'PY'
import json,re,sys
s=lambda x: re.sub(r'<span class="[^"]*">|</span>','',x)
n=0
with open(sys.argv[1].replace('report','real'),'w') as o:
  for l in open(sys.argv[1]):
    d=json.loads(l)
    if s(d['want'])!=s(d['got']): n+=1; o.write(l)
print('non-D7 failures:',n)
PY
