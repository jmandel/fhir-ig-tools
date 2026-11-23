#!/bin/bash
# usage: jekyll/oracle/oracle.sh <cases.jsonl> <expected.jsonl>   (paths relative to repo root or absolute under it)
# Daemon: honors DOCKER_CONTEXT / DOCKER_HOST (docker CLI env), e.g. DOCKER_CONTEXT=igbuild for the Lima VM; unset = as before.
# ORACLE_IMAGE: the image whose gems are the reference (default ig-publisher-localdev-ca, built from docker/Dockerfile).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
rel() { python3 -I -c 'import os,sys;print(os.path.relpath(os.path.abspath(sys.argv[1]),sys.argv[2]))' "$1" "$ROOT"; }
docker run --rm --entrypoint ruby -v "$ROOT":/w -w /w "${ORACLE_IMAGE:-ig-publisher-localdev-ca}" \
  /w/jekyll/oracle/oracle.rb "/w/$(rel "$1")" "/w/$(rel "$2")"
