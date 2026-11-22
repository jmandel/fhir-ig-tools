#!/bin/bash
# Run every YAML-port suite against its oracle expectations (build first: ./build.sh --fast yaml).
# usage: jekyll/tests/yaml/run-all.sh [--regen]   (--regen: regenerate cases and re-run the Ruby oracle)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$ROOT"
if [ "${1:-}" = --regen ]; then
  python3 -I jekyll/tests/yaml/gen_suite_cases.py
  python3 -I jekyll/tests/yaml/gen_typing_cases.py
  jekyll/oracle/oracle.sh jekyll/tests/yaml/suite-cases.jsonl jekyll/tests/yaml/suite-expected.jsonl
  jekyll/oracle/oracle.sh jekyll/tests/yaml/typing-cases.jsonl jekyll/tests/yaml/typing-expected.jsonl
fi
run() { printf '%-28s ' "$1"; BUILD=build-yaml jekyll/run.sh org.hl7.fhir.jekyll.test.yaml.YamlDiff "$2" "$3" --show 0 2>&1 | tail -1 || true; }
run "real _config/_data yaml" jekyll/tests/site/yaml-cases.jsonl jekyll/tests/site/yaml-expected.jsonl
run "real _data files" jekyll/tests/site/data-cases.jsonl jekyll/tests/site/data-expected.jsonl
run "yaml-test-suite" jekyll/tests/yaml/suite-cases.jsonl jekyll/tests/yaml/suite-expected.jsonl
run "typing suite" jekyll/tests/yaml/typing-cases.jsonl jekyll/tests/yaml/typing-expected.jsonl
run "real front matter" jekyll/tests/yaml/frontmatter-cases.jsonl jekyll/tests/yaml/frontmatter-expected.jsonl
