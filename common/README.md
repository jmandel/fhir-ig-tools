# common: code shared by jekyll.jar and sushi.jar

The `common` module of [fhir-ig-tools](../README.md): zero-dependency (JDK only) Java 17 code that both tools use.
It is shaded into `jekyll.jar` (the YAML event layer only) and `sushi.jar`; artifact `org.hl7.fhir:fhir-ig-common`.

![common: the libyaml 0.2.5 event layer (Reader, Scanner, Parser, Event) under the shim's Psych/SafeYAML construction (plain libyaml behaviour) and SUSHI's yaml@1.10 composer (opt-in yaml110 mode); the JS value model (JsObject, JsNumber, Json, Js, Lodash, Frozen) used by the SUSHI port](../docs/img/common.svg)

- **`org.hl7.fhir.common.yaml`**: a port of libyaml 0.2.5's reader, scanner and parser. `new Parser(bytes,
  encoding)` gives libyaml's events exactly (what the shim's Psych/SafeYAML layer in `org.hl7.fhir.jekyll.yaml`
  needs); `new Parser(bytes, encoding, true)` adds the opt-in yaml110 mode, the few inputs yaml@1.10 accepts and
  libyaml rejects (what SUSHI's composer in `org.hl7.fhir.sushi.yaml` needs). Errors are `LibyamlError` with
  libyaml's own problem, context and marks; each tool words its messages itself.
- **`org.hl7.fhir.common.js`**: JavaScript values in Java, as SUSHI's output depends on them: `JsObject`
  (insertion-ordered objects), numbers as `Double` with ECMAScript formatting (`JsNumber`), `Json`
  (`JSON.stringify`/`JSON.parse`, the bytes SUSHI writes), `Js`/`Lodash` (the lodash functions SUSHI uses) and
  `Frozen` (deeply frozen views). Used by the SUSHI port.

Rules (no dependencies, `--release 17`, nothing tool-specific by default, public API changed together with its
callers) and the API details are in [`../docs/MONOREPO.md`](../docs/MONOREPO.md). Tests: `src/test/java` (JUnit;
the JS vectors were generated with node, `sushi/scripts/oracle/js-vectors.js`).
