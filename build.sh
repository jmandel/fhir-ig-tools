#!/bin/bash
# Build the repo (docs/MONOREPO.md). Two ways:
#
#   ./build.sh [maven args...]     Maven, all modules (common, jekyll, sushi). Default args: -B -q package
#                                  -> common/target/classes, jekyll/target/{classes,test-classes},
#                                     jekyll/target/jekyll.jar (the shim's self-contained release jar),
#                                     sushi/target/sushi.jar (the SUSHI port's self-contained jar)
#                                  Needs JDK 17+; every module is compiled for Java 17.
#       e.g. ./build.sh -B -pl jekyll -am package     the shim only
#            ./build.sh -B -pl sushi -am package      SUSHI only (sushi/scripts/build.sh wraps this)
#            ./build.sh -B -o verify                  offline
#
#   ./build.sh --fast [module]     the shim only: plain javac, no Maven (quick, and lets parallel workers build
#                                  one module without tripping over each other's half-finished code); sushi
#                                  has no --fast build (it needs Maven for the ANTLR oracle and core):
#       no module : common + all of jekyll  -> jekyll/build/{classes,test-classes,jekyll.jar}
#       module    : common + jekyll util + that module (+ what it needs) -> jekyll/build-<module>/{classes,test-classes}
#                   module = liquid | kramdown | site | yaml | rouge
#       BUILD_OUT=<dir> puts the output in jekyll/<dir> instead. Each output dir holds common's classes too,
#       so `-cp <dir>/classes:<dir>/test-classes` is a complete classpath.
#
# Where it runs: a local JDK (and `mvn` for Maven builds) when they work; otherwise the reference image
# (Docker; DOCKER_CONTEXT / DOCKER_HOST pick the daemon, e.g. DOCKER_CONTEXT=igbuild for the Lima VM). In the
# container, Maven and its local repository live on the Docker host's disk ($VMDATA/fhir-ig, D11), not in the
# checkout; Maven is fetched from Maven Central on first use and checked against a pinned SHA-512.
# Force the container with BUILD_DOCKER=1 (SHIM_BUILD_DOCKER=1 still works).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
IMAGE="${IMAGE:-ig-publisher-localdev-ca}"
VMDATA="${VMDATA:-/srv/ig}"
MAVEN_VERSION=3.9.9
MAVEN_SHA512=a555254d6b53d267965a3404ecb14e53c3827c09c3b94b5678835887ab404556bfaf78dcfe03ba76fa2508649dca8531c74bca4d5846513522404d48e8c4ac8b
DOCKER="${BUILD_DOCKER:-${SHIM_BUILD_DOCKER:-}}"

# A working JDK (macOS ships /usr/bin/javac stubs that fail without one, so check that it runs).
have_jdk() { javac -version >/dev/null 2>&1 && jar --version >/dev/null 2>&1; }

if [ "${1:-}" = "--fast" ]; then
  MOD="${2:-}"
  SCRIPT='
set -e
P=org/hl7/fhir/jekyll
J=jekyll/src
COMMON="common/src/main/java"
if [ -z "$MOD" ]; then OUT=jekyll/${BUILD_OUT:-build}; MAIN="$COMMON $J/main/java"; TEST="$J/test/java";
else OUT=jekyll/${BUILD_OUT:-build-$MOD}; MAIN="$COMMON $J/main/java/$P/util $J/main/java/$P/$MOD"; TEST="$J/test/java/$P/test/DiffRunner.java $J/test/java/$P/test/$MOD";
  # site (the Jekyll glue, and the in-process API Shim/IncludeProvider/ShimException beside it) depends on both ports
  if [ "$MOD" = site ]; then MAIN="$MAIN $J/main/java/$P/*.java $J/main/java/$P/liquid $J/main/java/$P/kramdown $J/main/java/$P/yaml"; fi
  # yaml (the SafeYAML/Psych port, on common'"'"'s libyaml event layer) yields org.hl7.fhir.jekyll.liquid.RubyTime values
  if [ "$MOD" = yaml ]; then MAIN="$MAIN $J/main/java/$P/liquid"; fi
  # rouge (the Rouge 4.7.0 port) is used by kramdown (fenced code) and site ({% highlight %})
  if [ "$MOD" = kramdown ] || [ "$MOD" = site ]; then MAIN="$MAIN $J/main/java/$P/rouge"; TEST="$TEST $J/test/java/$P/test/rouge"; fi; fi
rm -rf $OUT && mkdir -p $OUT/classes $OUT/test-classes
find $MAIN -name "*.java" 2>/dev/null > $OUT/main-sources.txt
javac -encoding UTF-8 -Xlint:-options --release 17 -d $OUT/classes @$OUT/main-sources.txt
find $TEST -name "*.java" 2>/dev/null > $OUT/test-sources.txt || true
if [ -s $OUT/test-sources.txt ]; then javac -encoding UTF-8 -Xlint:-options --release 17 -cp $OUT/classes -d $OUT/test-classes @$OUT/test-sources.txt; fi
if [ -z "$MOD" ]; then jar --create --file $OUT/jekyll.jar --main-class org.hl7.fhir.jekyll.site.Main -C $OUT/classes .; fi
echo "built $OUT"
'
  if [ -z "$DOCKER" ] && have_jdk; then
    (cd "$ROOT" && MOD="$MOD" BUILD_OUT="${BUILD_OUT:-}" bash -c "$SCRIPT")
  else
    docker run --rm --entrypoint bash -v "$ROOT":/repo -w /repo -e MOD="$MOD" -e BUILD_OUT="${BUILD_OUT:-}" "$IMAGE" -c "$SCRIPT"
  fi
  exit 0
fi

[ $# -gt 0 ] || set -- -B -q package
if [ -z "$DOCKER" ] && have_jdk && command -v mvn >/dev/null 2>&1; then
  cd "$ROOT" && exec mvn "$@"
fi
# In the reference image: the checkout is mounted at its own path (so paths in messages are the host's).
docker run --rm -i -v "$VMDATA:$VMDATA" -v "$ROOT:$ROOT" -w "$ROOT" --entrypoint bash "$IMAGE" -c '
  set -e
  T="$1/fhir-ig/tools"; M="$T/apache-maven-$2"
  if [ ! -x "$M/bin/mvn" ]; then
    mkdir -p "$T"; tgz="$T/apache-maven-$2-bin.tar.gz.$$"
    curl -fsSL -o "$tgz" "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$2/apache-maven-$2-bin.tar.gz"
    echo "$3  $tgz" | sha512sum -c --quiet - || { rm -f "$tgz"; echo "Maven download: checksum mismatch" >&2; exit 1; }
    tar -xzf "$tgz" -C "$T" && rm -f "$tgz"
  fi
  exec "$M/bin/mvn" -Dmaven.repo.local="$1/fhir-ig/m2" "${@:4}"' _ "$VMDATA" "$MAVEN_VERSION" "$MAVEN_SHA512" "$@"
