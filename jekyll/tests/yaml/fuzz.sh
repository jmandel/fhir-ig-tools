#!/bin/bash
# Differential fuzzing of the YAML port against Ruby SafeYAML.
# usage: jekyll/tests/yaml/fuzz.sh <first-seed> <last-seed> <cases-per-seed>   (needs: ./build.sh --fast yaml)
# Writes work/yfuzz/f<seed>-{cases,expected,report}.jsonl; prints pass/fail per seed.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$ROOT"
mkdir -p work/yfuzz
for s in $(seq "$1" "$2"); do
  python3 -I jekyll/tests/yaml/gen_fuzz_cases.py "$s" "$3" "work/yfuzz/f$s-cases.jsonl" --files work/yfuzz/files >/dev/null
  jekyll/oracle/oracle.sh "work/yfuzz/f$s-cases.jsonl" "work/yfuzz/f$s-expected.jsonl"
  r=$(BUILD=build-yaml jekyll/run.sh org.hl7.fhir.jekyll.test.yaml.YamlDiff "work/yfuzz/f$s-cases.jsonl" "work/yfuzz/f$s-expected.jsonl" --show 0 --report "work/yfuzz/f$s-report.jsonl" 2>&1 | tail -1 || true)
  echo "seed $s: $r"
done
