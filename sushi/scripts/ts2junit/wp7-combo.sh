#!/bin/bash
# WP-7 scratch compile/test check: wp7 worktree + other WPs' not-yet-merged work (read-only overlay), in
# work/wp7-combo-<name>. Nothing is written to any worktree.
# usage: combo.sh <name> <test-file-basename-to-keep|ALL> [maven args...]   (default maven args: -q -B test-compile)
#   e.g. combo.sh cs CodeSystemExporterTest.java -q -B test-compile
# Overlay order: wp7 checkout <- wp2, wp8, wp6, wp4, wp5 (branch commits vs merge-base, then uncommitted files) <- wp7 src
set -e
NAME="$1"; KEEP="$2"; shift 2
R="${ROOT:-$(cd "$(dirname "$0")/../../.." && pwd)}"; C=$R/work/wp7-combo-$NAME
rm -rf $C && mkdir -p $C
(cd $R/sushi-java-wp7 && tar --exclude .git -cf - .) | tar -xf - -C $C
for w in ${COMBO_WPS:-wp2 wp8 wp6 wp4 wp5}; do
  [ -d $R/sushi-java-$w ] || continue
  (cd $R/sushi-java-$w && git diff --name-only --diff-filter=AM $(git merge-base main HEAD) HEAD | grep -E '^src/main/|^src/test/java/org/hl7/fhir/sushi/testhelpers/' | xargs -r git archive HEAD ) | tar -xf - -C $C 2>/dev/null || true
  (cd $R/sushi-java-$w && git status --short | grep -v '^ D\|^D' | awk '{print $2}' | grep -E '^src/main/|^src/test/java/org/hl7/fhir/sushi/testhelpers/' | grep -v '/$' | tar -cf - -T - 2>/dev/null) | tar -xf - -C $C || true
done
# re-apply wp7's own changes (vs main) on top
(cd $R/sushi-java-wp7 && { git diff --name-only --diff-filter=AM $(git merge-base main HEAD); git ls-files --others --exclude-standard; } | grep '^src/' | sort -u | tar -cf - -T -) | tar -xf - -C $C
if [ "$KEEP" != ALL ]; then
  for f in $C/src/test/java/org/hl7/fhir/sushi/export/*Test.java; do
    b=$(basename $f); case " $KEEP " in *" $b "*) ;; *) case $b in CodeSystemExporter*|ValueSetExporter*|InstanceExporter*|MappingExporter*) rm -f $f;; esac;; esac
  done
fi
find $C/src -type f -exec touch {} +   # git archive keeps old mtimes: force recompilation
[ $# -gt 0 ] || set -- -q -B test-compile
cd $C && LOAD_BUDGET=10 $R/work/locks/waitfree.sh timing scripts/build.sh "$@" 2>&1
