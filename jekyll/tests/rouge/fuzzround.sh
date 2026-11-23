#!/bin/bash
# One differential fuzz round: jekyll/tests/rouge/fuzzround.sh <seed> <per-lexer> [build-dir]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"; cd "$ROOT"
S=$1; N=$2; B=${3:-build-rouge}; D=jekyll/tests/rouge/fuzz
mkdir -p $D
python3 jekyll/tests/rouge/fuzz.py $S $N > $D/cases-$S.jsonl
jekyll/oracle/oracle.sh $D/cases-$S.jsonl $D/expected-$S.jsonl
BUILD=$B jekyll/run.sh org.hl7.fhir.jekyll.test.DiffRunner $D/cases-$S.jsonl $D/expected-$S.jsonl --no-d7 --show 0 --report $D/report-$S.jsonl | tail -1 || true
