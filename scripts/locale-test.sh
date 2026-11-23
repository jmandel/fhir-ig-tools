#!/bin/bash
# Run the differential suites under hostile JVM locales (native digits, Turkish casing).
set -u
cd "$(dirname "$0")/.."
for loc in "-Duser.language=ar -Duser.country=EG" "-Duser.language=tr -Duser.country=TR" "-Duser.language=th -Duser.country=TH -Duser.variant=TH"; do
  echo "== JVM locale: $loc"
  while read -r b c e; do
    r=$(docker run --rm --entrypoint java -v "$PWD":/w -w /w ig-publisher-localdev-ca $loc -Xss16m \
        -cp "/w/jekyll/$b/classes:/w/jekyll/$b/test-classes" org.hl7.fhir.jekyll.test.DiffRunner "$c" "$e" < /dev/null 2>&1 | tail -1)
    echo "  $(basename "$c"): $r"
  done <<'L'
build-liquid jekyll/tests/liquid/cases.jsonl jekyll/tests/liquid/expected.jsonl
build-liquid jekyll/tests/liquid/cases-crlf.jsonl jekyll/tests/liquid/expected-crlf.jsonl
build-site2 jekyll/tests/site/jekyll-cases.jsonl jekyll/tests/site/jekyll-expected.jsonl
build-yaml jekyll/tests/site/data-cases.jsonl jekyll/tests/site/data-expected.jsonl
L
done
