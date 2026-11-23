#!/bin/bash
# P0.12: run real Jekyll with the timing plugin on one golden -> work/timing/<name>.jsonl
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; n="$1"
. "$ROOT/scripts/lib/vmdata.sh"
rm -f "$ROOT/work/timing/$n.jsonl"
golden_mounts "$n"
docker run --rm --entrypoint bash -v "$ROOT:$ROOT" "${GM[@]}" -e TIMING_OUT="$ROOT/work/timing/$n.jsonl" -w "$ROOT/work/golden/$n/jekyll-in" "$IMAGE" \
  -c "rm -rf .jekyll-cache; s=\$(date +%s.%N); jekyll build --plugins '$ROOT/jekyll/oracle/timing-plugin' --destination /tmp/out >/dev/null 2>&1; e=\$(date +%s.%N); rm -rf .jekyll-cache; echo \"total \$(echo \"\$e - \$s\" | bc)\""
