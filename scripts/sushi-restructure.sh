#!/bin/bash
# The mechanical part of moving the SUSHI port into this repository (docs/MONOREPO.md "The SUSHI module").
# Idempotent: scripts/sushi-sync.sh runs it after every import/re-sync, so files that arrive at their old
# locations are moved again. Hand-made changes (sushi/pom.xml, script paths, docs) are ordinary commits.
#
# 1. JS value model to common: sushi/.../sushi/js/*.java -> common/.../common/js/, package org.hl7.fhir.common.js;
#    its tests JsonTest and FrozenTest (and the vectors JsonTest reads) to common/src/test. LodashTest also checks
#    sushi's utils.ValidUrl, so it stays in sushi as utils.LodashOracleTest.
# 2. Every reference to org.hl7.fhir.sushi.js in sushi/ and common/ code (imports, qualified names, the ts2junit
#    generator) becomes org.hl7.fhir.common.js. Markdown is left alone: the docs name the old package on purpose.
# 3. YAML: delete sushi's vendored copy of the libyaml event layer (sushi/.../sushi/yaml/{ByteString,Event,
#    LibyamlError,Mark,Parser,Reader,Scanner,Token}.java); the yaml@1.10 composer (Yaml, YamlError) stays in sushi
#    and uses common's org.hl7.fhir.common.yaml, with the opt-in yaml110 mode (new Parser(bytes, enc, true)).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
S=sushi/src/main/java/org/hl7/fhir/sushi
ST=sushi/src/test/java/org/hl7/fhir/sushi
C=common/src/main/java/org/hl7/fhir/common
CT=common/src/test/java/org/hl7/fhir/common

mv_() { # git mv that creates the target dir and tolerates an already-moved file
  [ -e "$1" ] || return 0
  mkdir -p "$(dirname "$2")"
  if [ -e "$2" ]; then echo "both $1 and $2 exist: merge by hand" >&2; exit 1; fi
  git mv "$1" "$2"
}

# 1. js -> common
for f in "$S"/js/*.java; do if [ -e "$f" ]; then mv_ "$f" "$C/js/$(basename "$f")"; fi; done
mv_ "$ST/js/JsonTest.java" "$CT/js/JsonTest.java"
mv_ "$ST/js/FrozenTest.java" "$CT/js/FrozenTest.java"
mv_ sushi/src/test/resources/js/vectors.json common/src/test/resources/js/vectors.json
if [ -e "$ST/js/LodashTest.java" ]; then
  mv_ "$ST/js/LodashTest.java" "$ST/utils/LodashOracleTest.java"
  perl -0pi -e 's/^package org\.hl7\.fhir\.sushi\.js;\n/package org.hl7.fhir.sushi.utils;\n/m;
                s/^(import java\.util\.Map;\n)/$1import org.hl7.fhir.common.js.Js;\nimport org.hl7.fhir.common.js.Json;\n/m;
                s/\bLodashTest\b/LodashOracleTest/g' "$ST/utils/LodashOracleTest.java"
fi
left="$(find "$ST/js" -name '*.java' 2>/dev/null || true)"
[ -z "$left" ] || { echo "tests left in sushi's js package (move them by hand): $left" >&2; exit 1; }
rmdir "$S/js" "$ST/js" sushi/src/test/resources/js 2>/dev/null || true

# 2. package rename everywhere
JSRE='\borg\.hl7\.fhir\.sushi\.js(?!\w)'
git ls-files sushi common | grep -v '\.md$' | while IFS= read -r f; do
  [ -f "$f" ] && grep -qF 'org.hl7.fhir.sushi.js' "$f" && perl -pi -e "s/$JSRE/org.hl7.fhir.common.js/g" "$f" || true
done

# 3. YAML event layer: use common's
for f in ByteString Event LibyamlError Mark Parser Reader Scanner Token; do
  if [ -e "$S/yaml/$f.java" ]; then git rm -q "$S/yaml/$f.java"; fi
done
Y="$S/yaml/Yaml.java"
if [ -e "$Y" ]; then
  perl -0pi -e '
    s/^(package org\.hl7\.fhir\.sushi\.yaml;\n\n)(?!import org\.hl7\.fhir\.common\.yaml)/$1import org.hl7.fhir.common.yaml.Event;\nimport org.hl7.fhir.common.yaml.LibyamlError;\nimport org.hl7.fhir.common.yaml.Parser;\n/m;
    s/new Parser\(input, Reader\.ANY_ENCODING\);\n(\s*)parser\.yaml110 = true;/new Parser(input, Parser.ANY_ENCODING, true);/;
    s/\bToken\.((?:ANY|PLAIN|SINGLE_QUOTED|DOUBLE_QUOTED|LITERAL|FOLDED)_SCALAR_STYLE)\b/Event.$1/g;
  ' "$Y"
  # the composer must use only common's public API
  if grep -nE '\b(Reader|Scanner|Token|ByteString)\.|parser\.yaml110' "$Y"; then
    echo "$Y still uses the event layer's internals (above): adapt scripts/sushi-restructure.sh" >&2; exit 1
  fi
fi
for f in "$S"/yaml/*.java; do
  case "$(basename "$f")" in Yaml.java|YamlError.java|package-info.java) ;; *) echo "unexpected file $f" >&2; exit 1;; esac
done

# leftovers check
if git ls-files sushi common | grep -v '\.md$' | while IFS= read -r f; do [ -f "$f" ] && perl -ne "print \"\$ARGV:\$.: \$_\" if /$JSRE/" "$f" || true; done | grep .; then
  echo "references to org.hl7.fhir.sushi.js remain (above)" >&2; exit 1
fi
echo "sushi-restructure: done"
