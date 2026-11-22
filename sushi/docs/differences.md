# Differences from TypeScript SUSHI 3.21.1

Behaviour where the Java port knowingly differs from TS SUSHI 3.21.1. Each entry: scope, evidence, decision.
Every WP adds its entries when it finishes (WP-1/2/7 pending). Output-affecting differences on real inputs are
bugs, not entries. Classes: **none** = no observable effect; **diag** = messages/diagnostics only; **malformed** =
only on invalid input; **open** = decision pending.

| id | WP | area | class | difference | decision |
|---|---|---|---|---|---|
| KD-1 | WP-3 | sushi-config.yaml parsing | malformed, diag | on 1,035 malformed/unusual configs: same accept/reject, same configuration and exit status everywhere; 44 YAML error messages worded differently (0 on the 266 real configs); details below | **accepted** (owner, 2026-11-22): exact wording needs a port of yaml@1.10's ~1,500-line CST parser as an error pass; not worth it for messages on broken configs; revisit if users report it |
| KD-2 | WP-4 | ElementDefinition.clone() | none | re-fetches the cached StructureDefinition of the element (`_edStructureDefinition`) instead of deep-copying it | accepted (cache is read-only) |
| KD-3 | WP-4 | JS TypeError crash paths | diag | where TS crashes with a JS TypeError on unexpected input, the port reproduces it only at a few sites, with V8-style messages (`Cannot read properties of undefined (reading 'x')`); elsewhere Java fails differently (corpus: WHO smart-trust, logical-model AddElement type lookup -- TS `Cannot read properties of undefined (reading 'sdType')`, now reproduced with V8's text; corpus smart-trust identical incl. log) | accepted; add sites when the differential finds them |
| KD-4 | WP-6 | metadata de-duplication (MasterFisher) | none | a missing key is treated like a key whose value is undefined; cannot change results | accepted |
| KD-5 | WP-6 | coloured "non-conformant resource" box | diag | coloured only when `FshLogger.colorEnabled()` (TTY/FORCE_COLOR), as chalk; uncoloured otherwise | matches TS when colour state matches |
| KD-6 | WP-6 | debug stack traces | diag | `--log-level debug` stack traces are Java's, not V8's | accepted |
| KD-7 | WP-6 | applyDeferredRules TypeError branch | diag | the TS TypeError branch surfaces as an uncaught `Common.JsTypeError` | accepted (crash in both; message text may differ) |
| KD-8 | WP-8 | package resolution | none | 0 differences: 5,497/5,497 lookups on the 6 reference IGs pick the same definition; FPL rules ported where core differed are in [package-resolution-differences.md](package-resolution-differences.md); log-only D7/D8 there. The FPL warning "Failed to download most recent X#current from current builds. Using most recent cached package instead." (build not listed on build.fhir.org/ig/qas.json) is reproduced (corpus case HL7-cz__ps) | n/a; D7 info line still open |
| KD-9 | WP-3 | typed Configuration | malformed, none | 42 mutation inputs with a raw YAML value of the wrong type (e.g. `title: 2023`): the TS-shaped configuration and all output/messages are identical, but the typed `Configuration` object cannot hold the raw value (see `ConfigurationJs`); none in the 266-config corpus | **accepted** (owner, 2026-11-22) |
| KD-10 | WP-10 | latest-SUSHI-version check | diag | off by default (PORTING D7): the summary box then shows no version block, as TS does when its `npm view` check finds the running release is the latest; enable with `-Dsushi.versionCheck=true` / `SUSHI_VERSION_CHECK=true` (`--require-latest` always checks). The Java port runs no `npm view`: its first source fails (`Unable to determine the latest version of sushi: npm is not used by the Java port of SUSHI`, info) and the registry fallback (https://registry.npmjs.org/fsh-sushi) is used, with TS's messages | accepted (D7) |
| KD-11 | WP-10 | `sushi update-dependencies` rewrite of sushi-config.yaml | none | TS re-serialises the whole file with the yaml library (its prompt warns this "may affect the formatting of your file"); the port edits only the version scalars of the top-level `dependencies` block in place and keeps every other byte | accepted (no information lost; same resulting configuration) |
| KD-12 | WP-10 | `sushi init` sushi-config.yaml | none | the template is shipped as the yaml library re-serialises it (TS: `YAML.parseDocument` + `set` + `toString`); answered values are substituted in place and quoted when a plain YAML scalar would not read back as the same string. The ported init tests compare with the TS fixtures | accepted |
| KD-13 | WP-10 | page ordering in auto-generated IG pages (`localeCompare`) | none | TS sorts unprefixed page file names with ICU collation (`String.prototype.localeCompare`); the port approximates ICU root collation (ASCII punctuation/digits/letters in CLDR order, case- and accent-insensitive first, then accents, then lowercase-before-uppercase); identical on all reference/corpus IGs measured | accepted; revisit if the differential finds a page-order difference |
| KD-14 | WP-10 | "history" error text | malformed, diag | the deprecated `history` property is reported with the package-list JSON in importConfiguration's key order (the typed Configuration keeps no raw object); TS prints the raw object, which only differs for hand-built configurations (a unit test) | accepted |
| KD-15 | WP-10 | ini parsing quirks (ig.ini) | none | `ini` 5.0.0 decode/encode ported (`ig.Ini`); a quoted value that JSON-parses to a non-string (e.g. `'5'`) becomes its string form, which only feeds "is it null" checks | accepted |


## KD-1 Malformed sushi-config.yaml: YAML error wording (WP-3)

**Scope.** Only YAML that yaml@1.10 (the npm `yaml` package SUSHI uses) rejects. All 266 real corpus
`sushi-config.yaml` files import identically (CorpusConfigTest); on the 1,035-input mutation set every input is
accepted/rejected the same way and imports to the same configuration and log messages, except the wording of 44
YAML errors.

**Evidence.** `MutationConfigTest` (scripts/oracle/config-mutations.py; oracle = TS SUSHI 3.21.1 on node 22,
scripts/oracle/config-oracle.js): 1,035 inputs: **949 identical**, 42 typed-lossy (identical TS-shaped configuration;
the typed `Configuration` can't hold a raw YAML value of an unexpected type such as `title: 2023`, see
`ConfigurationJs`), **44 yaml-wording** (both reject the YAML; the `Error parsing configuration: <message>.` line
differs), 0 accept/reject mismatches, 0 import differences.

**What was done (owner decision: fix accept/reject, wording where straightforward).** The YAML layer is the libyaml
0.2.5 event layer vendored from fhir-ig-jekyll plus a yaml@1.10 composer. Accept/reject now matches yaml@1.10 via an
opt-in `yaml110` switch in the vendored Reader/Scanner/Parser (off for the Jekyll shim): any character accepted, only
CR/LF are line breaks (NEL/LS/PS are content), tabs on blank lines (also inside plain scalars), `%` may start a plain
value after column 0, a value without a key in a block mapping has an empty key. The composer adds yaml@1.10's
semantic errors (duplicate keys, unknown aliases, multiple documents incl. content after `...`, comment glued to a
quoted value, excessive aliases, wrong-type tags). libyaml's syntax errors are mapped to yaml@1.10's message text
(`Yaml.syntaxMessage`): 185 differing messages before, 44 now.

**What is left and why.** For a malformed line yaml@1.10 reports the *first* error of its own CST/resolution pass,
which depends on how its parser re-groups the following lines (e.g. a tab-indented line yields "Plain value cannot
start with a tab character" or, depending on the shape and even the spaces of the next lines, "All collection items
must start at the same column" reported on the enclosing collection). libyaml stops at its first error with no
equivalent of that regrouping, so the remaining 44 cases (tab-indented lines 23, mis-indented continuation lines 12,
other 9) can't be mapped by a local rule. Exact parity would need a port of yaml@1.10's CST parser (parse-cst,
~1,500 JS lines) as the error-detection pass; not done. The build outcome is the same (both reject the file).

**Decision (owner, 2026-11-22): accepted.** Not worth a CST-parser port for messages on broken configs; revisit if
users report it.

| case | TS (yaml@1.10) | Java |
|---|---|---|
| `hand/tab-indent` | Plain value cannot start with a tab character | All collection items must start at the same column |
| `hand/directive-in-body` | Implicit map keys need to be followed by map values | Document contains trailing content not separated by a ... or --- line |
| `syn/CoBHA__CoBHRM-FHIR-IG/06-tabline` | All collection items must start at the same column | Plain value cannot start with a tab character |
| `syn/CoBHA__CoBHRM-FHIR-IG/10-dedent` | All collection items must start at the same column | Nested mappings are not allowed in compact mappings |
| `syn/HL7__DeviceAlerting/13-tabline` | All collection items must start at the same column | Plain value cannot start with a tab character |
| `syn/HL7-cz__cz-ems/05-tabline` | All collection items must start at the same column | Plain value cannot start with a tab character |
| `syn/HL7-cz__cz-ems/06-tabline` | All collection items must start at the same column | Plain value cannot start with a tab character |
| `syn/FHIR__MADFSH/00-lswap` | All collection items must start at the same column | Nested mappings are not allowed in compact mappings |
| `syn/FHIR__MADFSH/01-dedent` | All collection items must start at the same column | Nested mappings are not allowed in compact mappings |
| `syn/ehealthsuisse__ch-health-dossier/06-tabline` | All collection items must start at the same column | Plain value cannot start with a tab character |
| `syn/HL7__case-reporting/00-del` | All collection items must start at the same column | Nested mappings are not allowed in compact mappings |
| `syn/HL7__case-reporting/10-tabline` | All collection items must start at the same column | Plain value cannot start with a tab character |
| `syn/HL7__case-reporting/13-tabline` | All collection items must start at the same column | Plain value cannot start with a tab character |
| `syn/HL7__emedicinal-product-info/01-tabline` | All collection items must start at the same column | Plain value cannot start with a tab character |
| `syn/hl7-be__core-clinical/06-indent` | All collection items must start at the same column | Nested mappings are not allowed in compact mappings |
| `syn/vadi2__VadimTestIG/02-tabline` | Plain value cannot start with a tab character | All collection items must start at the same column |
| `syn/HL7__imaging-service-request-ig/04-dedent` | All collection items must start at the same column | Nested mappings are not allowed in compact mappings |
| `syn/HL7__imaging-service-request-ig/09-indent` | All collection items must start at the same column | Nested mappings are not allowed in compact mappings |
| `syn/HL7__cds-hooks-library/06-tabline` | All collection items must start at the same column | Plain value cannot start with a tab character |
| `syn/HL7__cds-hooks-library/12-lswap` | All collection items must start at the same column | Nested mappings are not allowed in compact mappings |
| `syn/HL7__davinci-pct/00-tabline` | All collection items must start at the same column | Plain value cannot start with a tab character |
| `syn/euridice-org__eu-health-data-api/00-lswap` | Implicit map keys need to be on a single line | Implicit map keys need to be followed by map values |
| `syn/euridice-org__eu-health-data-api/06-tabline` | All collection items must start at the same column | Plain value cannot start with a tab character |
| `syn/HL7__davinci-ra/01-dedent` | All collection items must start at the same column | Nested mappings are not allowed in compact mappings |
| `syn/WorldHealthOrganization__smart-base-clinical/00-quote` | All collection items must start at the same column | The ""# look ...e, the "" key is too long |
| `syn/HL7__smart-scheduling-links/08-lswap` | All collection items must start at the same column | Nested mappings are not allowed in compact mappings |
| `syn/HL7__smart-scheduling-links/12-quote` | All collection items must start at the same column | Implicit map keys need to be followed by map values |
| `syn/HL7__ebm/08-lswap` | All collection items must start at the same column | Nested mappings are not allowed in compact mappings |
| `syn/medcomdk__dk-medcom-xds-metadata/00-quote` | All collection items must start at the same column | Implicit map keys need to be followed by map values |
| `syn/medcomdk__dk-medcom-xds-metadata/07-tabline` | All collection items must start at the same column | Plain value cannot start with a tab character |
| `syn/medcomdk__dk-medcom-xds-metadata/12-tabline` | All collection items must start at the same column | Plain value cannot start with a tab character |
| `syn/Minsal-CL__tei/07-tabline` | Plain value cannot start with a tab character | All collection items must start at the same column |
| `syn/argonautproject__smart-imaging/10-ins` | All collection items must start at the same column | Aliased anchor not found: title |
| `syn/hl7ch__agentic-ig/10-tabline` | All collection items must start at the same column | Plain value cannot start with a tab character |
| `syn/medcomdk__dk-medcom-acknowledgement/01-tabline` | All collection items must start at the same column | Plain value cannot start with a tab character |
| `syn/medcomdk__dk-medcom-acknowledgement/04-lswap` | All collection items must start at the same column | Nested mappings are not allowed in compact mappings |
| `syn/medcomdk__dk-medcom-acknowledgement/10-tabline` | All collection items must start at the same column | Plain value cannot start with a tab character |
| `syn/hl7-be__mycarenet/00-tabline` | All collection items must start at the same column | Plain value cannot start with a tab character |
| `syn/hl7-be__mycarenet/04-tabline` | All collection items must start at the same column | Plain value cannot start with a tab character |
| `syn/hl7-be__mycarenet/06-tabline` | All collection items must start at the same column | Plain value cannot start with a tab character |
| `syn/HL7__US-Core/09-ins` | All collection items must start at the same column | Implicit map keys need to be followed by map values |
| `syn/fut-infrastructure__implementation-guide-test-data/07-tabline` | All collection items must start at the same column | Plain value cannot start with a tab character |
| `syn/fut-infrastructure__implementation-guide-test-data/10-quote` | All collection items must start at the same column | The ""      -... name: "" key is too long |
| `syn/fut-infrastructure__implementation-guide-test-data/12-del` | Unexpected PLAIN node in sequence | Implicit map keys need to be followed by map values |
