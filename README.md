# fhir-ig-tools

**Java replacements for the two non-Java stages of the [FHIR IG Publisher](https://github.com/HL7/fhir-ig-publisher):
SUSHI (Node.js) and Jekyll (Ruby). Same output; no Node.js, npm or Ruby.**

The IG Publisher is a Java program, but building an IG also runs two other toolchains: SUSHI compiles the IG's FSH
into FHIR JSON before the publisher loads it, and Jekyll renders the pages the publisher prepares into the website.
This repository has a faithful Java port of each, checked against the original tool file by file and message by
message until the output is identical, so an IG builds with nothing but Java.

![The IG Publisher pipeline: input/fsh goes through sushi.jar (which replaces SUSHI on Node.js) to fsh-generated; the IG Publisher (Java) loads the IG and writes temp/pages; jekyll.jar (which replaces Jekyll on Ruby) renders it to output; both jars share the common module](docs/img/pipeline.svg)

## sushi.jar: SUSHI in Java

A port of SUSHI 3.21.1, the FSH compiler, with a hand-written FSH parser (SUSHI's ANTLR grammar serves only as a test
oracle), FHIR packages through org.hl7.fhir.core's package cache and predefined FHIR XML through core's XML parsers.
**Byte-identical `fsh-generated/` to TypeScript SUSHI 3.21.1 on 259/267 FSH IGs of the corpus and 4/6 reference
IGs, the same exit status on all**; the other 8 IGs read predefined XML resources, which the port converts with
core instead of fhir-tool, and differ only in their ImplementationGuide resource listing
([`sushi/docs/core-divergences.md`](sushi/docs/core-divergences.md) C6). Console messages are the same except
package-loading lines in 6 IGs and XML warnings in 11; 3,730 JUnit tests, including 3,581 of SUSHI's 3,604 Jest
tests ported 1:1. Java 17+.

```sh
curl -fLO https://github.com/jmandel/fhir-ig-tools/releases/latest/download/sushi && chmod +x sushi
mv sushi ~/bin/                       # any folder on the PATH, ahead of an npm-installed sushi
java -jar publisher.jar -ig .         # the publisher runs `sushi`: first use fetches and verifies sushi.jar
```

Details, the Windows wrapper and caveats: [`sushi/README.md`](sushi/README.md).

## jekyll.jar: Jekyll in Java

A port of the slice of Jekyll 4.4.1 the publisher uses, with Liquid 4.0.4, kramdown 2.6.1, Rouge 4.7.0 and Jekyll's
YAML stack. **On 268 real IGs, 262 render identically to the autobuilder image's Jekyll (1,021,376 files)**; the other 6 cannot by construction. 3–7×
faster than Jekyll on the Jekyll stage. Java 17+.

```sh
curl -fLO https://github.com/jmandel/fhir-ig-tools/releases/latest/download/jekyll-shim && chmod +x jekyll-shim
java -jar publisher.jar -ig . -jekyll "$PWD/jekyll-shim"    # first use fetches and verifies jekyll.jar
```

Details (results, speed, memory, what is supported), the Windows wrapper and the in-process API:
[`jekyll/README.md`](jekyll/README.md).

## common

Both jars build on the zero-dependency [`common`](common/README.md) module: the libyaml event layer, which each tool's
own YAML construction sits on, and the JavaScript value model the SUSHI port uses.

## Repository layout

| Path | What |
|---|---|
| `pom.xml`, `build.sh` | the multi-module build ([`docs/MONOREPO.md`](docs/MONOREPO.md)): Maven aggregator and its wrapper script |
| [`common/`](common/README.md) | zero-dependency code shared by the modules, Java 17: the libyaml event layer (`org.hl7.fhir.common.yaml`) and the JavaScript value model (`org.hl7.fhir.common.js`) |
| [`jekyll/`](jekyll/README.md) | the shim: Java sources (`org.hl7.fhir.jekyll.{liquid,kramdown,rouge,yaml,site,util}`), tests, the differential oracle, porting notes (`NOTES-*.md`), integration notes, the `jekyll-shim` wrappers; builds `jekyll.jar` |
| [`sushi/`](sushi/README.md) | the SUSHI port, Java 17 (`org.hl7.fhir.sushi.*`): sources, JUnit tests, the `sushi` wrappers, the ANTLR test oracle (SUSHI's grammar), the differential scripts, [`PORTING.md`](sushi/PORTING.md) (design decisions and conventions) and [`docs/`](sushi/docs) (known differences); builds `sushi.jar` |
| `scripts/` | test drivers (`test-suites.sh`, `e2e.sh`, `test-wrapper.sh`, `sushi-e2e.sh`, `test-sushi-wrapper.sh`), corpus sampling, golden builds, the `domdiff.py` comparator, timing / memory / locale tools, `sushi-sync.sh` |
| `docker/` | the reference image: the autobuilder's localdev image pinned by digest, plus optional extra CAs |
| `lima/` | Lima VM definition for large corpus runs |
| `docs/` | [`MONOREPO.md`](docs/MONOREPO.md) (modules, building, testing, releases), the diagrams (`img/`), [`TRACKER.md`](docs/TRACKER.md) (the live project tracker: plan, decisions, evidence, open items) |
| `tracker-app/` | a small dashboard for the tracker |
| `third_party/` | the upstream Ruby sources as optional shallow submodules ([details](third_party/README.md)) |

## Building and testing

Building everything needs JDK 17+ and Maven 3.6.3+, or Docker (`./build.sh` then runs the same Maven build in the
reference image). Every module is compiled for Java 17, so `jekyll.jar` and `sushi.jar` run on Java 17.

```sh
./build.sh                    # all modules and all JUnit tests -> jekyll/target/jekyll.jar, sushi/target/sushi.jar
scripts/test-suites.sh        # the shim's differential suites vs recorded Ruby output
scripts/sushi-e2e.sh          # a small FSH project through the sushi wrapper vs java -jar sushi.jar
```

Everything else (one module at a time, the `--fast` build, every test entry point, CI, releases, module rules):
[`docs/MONOREPO.md`](docs/MONOREPO.md).

## License

BSD 3-Clause, see [`LICENSE`](LICENSE). The ported projects' notices are in
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md): for the shim MIT, BSD-2-Clause (Ruby, date, the
Pygments-derived lexers) and Apache-2.0 (one Addressable function); for the SUSHI port Apache-2.0 (SUSHI,
fhir-package-loader), BSD-3-Clause (the ANTLR runtime behaviour its parser reproduces) and MIT, ISC and Blue Oak
(the npm libraries it replaces).
