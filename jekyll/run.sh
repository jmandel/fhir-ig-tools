#!/bin/bash
# Run a Java main from the shim inside the reference image.
# usage: [BUILD=build-liquid] jekyll/run.sh <main-class> [args...]
#   BUILD: a ./build.sh --fast output dir under jekyll/ (classes + test-classes, common included);
#   unset = the Maven build (jekyll/target/shim.jar + jekyll/target/test-classes).
#   Paths under the repo root are visible in the container as /w/<relative path>;
#   the working directory is /w, so relative paths from the root work unchanged.
# Daemon: honors DOCKER_CONTEXT / DOCKER_HOST (docker CLI env), e.g. DOCKER_CONTEXT=igbuild for the Lima VM; unset = as before.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$HERE")"
CLS="$1"; shift
if [ -n "${BUILD:-}" ]; then CP="/w/jekyll/$BUILD/classes:/w/jekyll/$BUILD/test-classes"
else CP="/w/jekyll/target/shim.jar:/w/jekyll/target/test-classes"; fi
docker run --rm -i --entrypoint java -v "$ROOT":/w -w /w ig-publisher-localdev-ca \
  -Xss16m -Xmx4g -cp "$CP" "$CLS" "$@"
