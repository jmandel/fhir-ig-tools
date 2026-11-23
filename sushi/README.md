# SUSHI port (`sushi/`)

A Java port of [SUSHI](https://github.com/FHIR/sushi) 3.21.1, the FHIR Shorthand (FSH) compiler: for the same
inputs and FHIR packages it writes byte-identical `fsh-generated/`, prints the same console messages and exits
with the same status as TypeScript SUSHI 3.21.1, with no Node.js or npm. Java 21; package `org.hl7.fhir.sushi`.

```sh
sushi/scripts/build.sh -B -q -DskipTests package    # from the repository root -> sushi/target/sushi.jar
java -jar sushi/target/sushi.jar path/to/ig         # = sushi path/to/ig (same options: build, init, -o, -p, -s, ...)
sushi/scripts/build.sh                              # compile, all tests (common + sushi)
```

- [`PORTING.md`](PORTING.md): design decisions (dependencies, the JS value model, the hand-written parser with
  ANTLR as test oracle, package resolution through org.hl7.fhir.core), conventions, the TS → Java module map, the
  tests and the differential harness.
- [`docs/differences.md`](docs/differences.md): every known behaviour difference from TypeScript SUSHI, with
  evidence and decision. [`docs/package-resolution-differences.md`](docs/package-resolution-differences.md): where
  core's package resolution differed from fhir-package-loader and what was ported.
- `tests/inventory.tsv`: SUSHI's Jest tests and which JUnit test ports each.
- `scripts/`: `build.sh` (the module build), `diff-run.sh` / `corpus-diff.sh` (differential runs against
  TypeScript SUSHI), `classify-diff.py`; `oracle/`, `xhtml/`, `fhirtool/`, `probe/`, `wp1/`, `ts2junit/` are the
  oracle and test-porting tools used during the port (they expect the corpus workspace and the reference image).

The module's place in the repository (shared code in `common`, module rules, building) is in
[`../docs/MONOREPO.md`](../docs/MONOREPO.md).
