#!/bin/bash
# Runs golden-nod7.sh over every golden in goldens.txt; summary -> jekyll/tests/rouge/reports/goldens-nod7.tsv
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"; cd "$ROOT"
OUT=jekyll/tests/rouge/reports/goldens-nod7.tsv
: > $OUT
while read -r g; do
  r=$(jekyll/tests/rouge/golden-nod7.sh "$g" 2>&1 | grep -E '^same=' | head -1)
  echo -e "$g\t$r" >> $OUT
  rm -rf "work/golden/$g/shim-out-rouge"
done < "${1:-jekyll/tests/rouge/goldens.txt}"
echo done >> $OUT
