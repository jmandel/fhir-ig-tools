#!/bin/bash
# Jekyll vs shim on goldens, like for like: same input on the VM disk (scripts/lib/vmdata.sh), output to
# container-local disk, peak RSS and wall time from scripts/peakmem.sh. Each tool runs twice per IG,
# alternating (jekyll, shim, jekyll, shim); the second (warm page cache) run of each is reported, so
# neither side benefits from the other having read the input first. Run on an otherwise idle VM.
# usage: scripts/bench.sh <golden-name>... > work/bench.tsv   (columns: name tool run wall_s peak_rss_mb exit)
# SHIM_JAR (default jekyll/target/jekyll.jar) picks the shim; it runs as the wrapper does (MaxRAMPercentage=75)
# unless SHIM_HEAP is set, e.g. SHIM_HEAP=512m (to measure what it needs, not what the JVM grows to).
# TOOLS (default "jekyll shim") limits which tools run.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
. "$ROOT/scripts/lib/vmdata.sh"
JAR="${SHIM_JAR:-$ROOT/jekyll/target/jekyll.jar}"
printf 'name\ttool\trun\twall_s\tpeak_rss_mb\texit\n'
for n in "$@"; do
  golden_mounts "$n"
  G="$ROOT/work/golden/$n"
  for run in 1 2; do
    for tool in ${TOOLS:-jekyll shim}; do
      if [ "$tool" = jekyll ]; then cmd="jekyll build --destination /tmp/out"
      elif [ -n "${SHIM_HEAP:-}" ]; then cmd="java -Xmx$SHIM_HEAP -jar '$JAR' build --destination /tmp/out"
      else cmd="java -XX:MaxRAMPercentage=75 -jar '$JAR' build --destination /tmp/out"; fi
      r=$(docker run --rm --entrypoint bash -v "$ROOT:$ROOT" "${GM[@]}" -w "$G/jekyll-in" "$IMAGE" -c \
        "rm -rf .jekyll-cache; '$ROOT/scripts/peakmem.sh' $cmd >/dev/null 2>/tmp/err; rc=\$?; rm -rf .jekyll-cache; grep -o 'peak_rss_mb=.*' /tmp/err | tail -1; exit \$rc" || true)
      wall=$(echo "$r" | grep -o 'wall_s=[0-9.]*' | cut -d= -f2)
      rss=$(echo "$r" | grep -o 'peak_rss_mb=[0-9]*' | cut -d= -f2)
      ex=$(echo "$r" | grep -o 'exit=[0-9]*' | cut -d= -f2)
      printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$n" "$tool" "$run" "${wall:--}" "${rss:--}" "${ex:--}"
    done
  done
done
