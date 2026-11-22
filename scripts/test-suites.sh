#!/bin/bash
# Run the committed differential suites (expected outputs recorded from the real Ruby gems) with a
# plain JDK; no Docker or Ruby needed. Builds first (./build.sh, Maven) unless SHIM_NO_BUILD=1. Documented expected
# differences live in <cases>.known (see DiffRunner --known); anything else fails the run.
# usage: scripts/test-suites.sh            (from anywhere; Linux, macOS, Windows Git Bash)
# The YAML file-case suites hold /w/... paths (the oracle container's mount point); they run only
# where /w is the repo root (the reference container, or CI with a /w symlink): set SHIM_W_SUITES=1.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
[ -n "${SHIM_NO_BUILD:-}" ] || ./build.sh >/dev/null || exit 1
SEP=":"; case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=";";; esac
# Default: the Maven build, run against the release jar itself (jekyll/target/shim.jar: jekyll + common).
# SHIM_BUILD=<dir>: a plain-javac build dir instead (./build.sh --fast [module] -> jekyll/build[-<module>]),
# whose classes/ holds common's classes too.
if [ -n "${SHIM_BUILD:-}" ]; then CP="$SHIM_BUILD/classes${SEP}$SHIM_BUILD/test-classes"
else CP="jekyll/target/shim.jar${SEP}jekyll/target/test-classes"; fi
suites=(
  "jekyll/tests/smoke-cases.jsonl jekyll/tests/smoke-expected.jsonl"
  "jekyll/tests/liquid/cases.jsonl jekyll/tests/liquid/expected.jsonl"
  "jekyll/tests/liquid/cases-crlf.jsonl jekyll/tests/liquid/expected-crlf.jsonl"
  "jekyll/tests/kramdown/cases.jsonl jekyll/tests/kramdown/expected.jsonl"
  "jekyll/tests/kramdown/cases-emskip.jsonl jekyll/tests/kramdown/expected-emskip.jsonl"
  "jekyll/tests/kramdown/cases-d7.jsonl jekyll/tests/kramdown/expected-d7.jsonl"
  "jekyll/tests/rouge/cases.jsonl jekyll/tests/rouge/expected.jsonl"
  "jekyll/tests/rouge/targeted-cases.jsonl jekyll/tests/rouge/targeted-expected.jsonl"
  "jekyll/tests/site/jekyll-cases.jsonl jekyll/tests/site/jekyll-expected.jsonl"
  "jekyll/tests/site/liquidcfg-cases.jsonl jekyll/tests/site/liquidcfg-expected.jsonl"
)
if [ -n "${SHIM_W_SUITES:-}" ]; then
  suites+=("jekyll/tests/yaml/suite-cases.jsonl jekyll/tests/yaml/suite-expected.jsonl"
           "jekyll/tests/yaml/typing-cases.jsonl jekyll/tests/yaml/typing-expected.jsonl")
fi
fail=0
for s in "${suites[@]}"; do
  set -- $s
  known=(); [ -f "$1.known" ] && known=(--known "$1.known")   # documented expected differences
  out=$(java -Xss16m -cp "$CP" org.hl7.fhir.jekyll.test.DiffRunner "$1" "$2" --show 3 ${known[@]+"${known[@]}"} 2>&1); rc=$?   # bash 3.2 + set -u: empty array
  printf '%-48s %s\n' "$1" "$(echo "$out" | tail -1)"
  if [ $rc -ne 0 ]; then fail=1; echo "$out" | head -20; fi
done
exit $fail
