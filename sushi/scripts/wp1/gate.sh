#!/bin/bash
# WP-1 gate: the heavy differential suites (corpus 17,869 files, fuzz 120k, MiniFSH fuzz 20k) against the stored JS
# runtime dumps on the VM, on 2 threads, started through the shared-machine lock.
# usage: scripts/wp1/gate.sh [extra maven args]
set -euo pipefail
HERE="$(cd "$(dirname "$0")/../.." && pwd)"
exec "$HERE/../work/locks/waitfree.sh" timing "$HERE/scripts/build.sh" -q -B test \
  -Dtest='HandParserDifferentialTest,LexerDifferentialTest,OracleCrossCheckTest,AtnEquivalenceTest' \
  -Dsurefire.failIfNoSpecifiedTests=false -Dwp1.heavy=true -Dwp1.threads="${WP1_THREADS:-2}" "$@"
