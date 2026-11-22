#!/bin/bash
# Run scripts/wp1/js-dump.js (SUSHI's generated JS parser, antlr4 JS runtime) over every *.fsh (or *.mini) file
# under an input dir on the VM disk, writing <out>/<relative path>.dump.
# usage: scripts/wp1/js-oracle.sh <fsh|mini> <input dir on VM> <output dir on VM>
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
MODE="$1"; IN="$2"; OUT="$3"
SUSHI="${SUSHI_TS_DIR:-/srv/ig/sushi-spike/sushi}"
EXT=fsh; [ "$MODE" = mini ] && EXT=mini
docker run --rm -i -v /srv/ig:/srv/ig -v "$HERE:$HERE:ro" --entrypoint bash ig-publisher-localdev-ca -c "
  set -e; mkdir -p '$OUT'; cd '$IN'
  find . -name '*.$EXT' -type f | sort | sed 's|^\./||' | awk -v o='$OUT' -v i='$IN' '{print i\"/\"\$0\"\t\"o\"/\"\$0\".dump\"}' > '$OUT.list'
  wc -l < '$OUT.list'
  # chunks of 2000 files per node process
  split -l 2000 '$OUT.list' '$OUT.list.part.'
  for p in '$OUT'.list.part.*; do node '$HERE/js-dump.js' '$SUSHI' '$MODE' \"\$p\" & done; wait
  rm -f '$OUT'.list.part.*
  find '$OUT' -name '*.dump' | wc -l
"
