#!/bin/bash
# Build/test the sushi module (and common, which it needs) through the repository's ./build.sh, which runs Maven
# locally or in the reference image (docs/MONOREPO.md "Building").
#
# usage: sushi/scripts/build.sh [maven args...]      default: -B -q verify (compile, ANTLR oracle, all tests, jars)
#   sushi/scripts/build.sh -B test -Dtest='JsonWriterTest'       one test class
#   sushi/scripts/build.sh -B -q -DskipTests package             jars only: sushi/target/sushi.jar
#
# SUSHI_BUILD_DIR=<dir> puts sushi's target dir there instead of sushi/target (for example on the VM disk,
# $VMDATA/sushi-port/build/<name>, which the oracle scripts under scripts/{xhtml,probe,corexml} read; D11).
# Start heavy runs with work/locks/waitfree.sh timing on the shared build machine.
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
[ $# -gt 0 ] || set -- -B -q verify
args=(-pl sushi -am -Dsurefire.failIfNoSpecifiedTests=false)
[ -z "${SUSHI_BUILD_DIR:-}" ] || args+=("-Dsushi.buildDir=$SUSHI_BUILD_DIR")
exec "$ROOT/build.sh" "${args[@]}" "$@"
