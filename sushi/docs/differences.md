# Differences from TypeScript SUSHI 3.21.1

Behaviour where the Java port knowingly differs from TS SUSHI 3.21.1. Each entry: scope, evidence, decision.
Every WP adds its entries when it finishes (WP-1/2/7/10 pending). Output-affecting differences on real inputs are
bugs, not entries. Classes: **none** = no observable effect; **diag** = messages/diagnostics only; **malformed** =
only on invalid input; **open** = decision pending.

| id | WP | area | class | difference | decision |
|---|---|---|---|---|---|
| KD-1 | WP-3 | sushi-config.yaml parsing | malformed, open | 49 YAML error-wording differences and 8 accept/reject mismatches out of 1,035 mutations (0 on the 266 real configs); details below | open: accept, or map to yaml@1.10 wording later |
| KD-2 | WP-4 | ElementDefinition.clone() | none | re-fetches the cached StructureDefinition of the element (`_edStructureDefinition`) instead of deep-copying it | accepted (cache is read-only) |
| KD-3 | WP-4 | JS TypeError crash paths | diag | where TS crashes with a JS TypeError on unexpected input, the port reproduces it only at a few sites, with V8-style messages (`Cannot read properties of undefined (reading 'x')`); elsewhere Java fails differently | accepted; add sites when the differential finds them |
| KD-4 | WP-6 | metadata de-duplication (MasterFisher) | none | a missing key is treated like a key whose value is undefined; cannot change results | accepted |
| KD-5 | WP-6 | coloured "non-conformant resource" box | diag | coloured only when `FshLogger.colorEnabled()` (TTY/FORCE_COLOR), as chalk; uncoloured otherwise | matches TS when colour state matches |
| KD-6 | WP-6 | debug stack traces | diag | `--log-level debug` stack traces are Java's, not V8's | accepted |
| KD-7 | WP-6 | applyDeferredRules TypeError branch | diag | the TS TypeError branch surfaces as an uncaught `Common.JsTypeError` | accepted (crash in both; message text may differ) |
| KD-8 | WP-8 | package resolution | none | 0 differences: 5,497/5,497 lookups on the 6 reference IGs pick the same definition; FPL rules ported where core differed are in [package-resolution-differences.md](package-resolution-differences.md) | n/a |


## KD-1 Malformed sushi-config.yaml: YAML error wording and accept/reject (WP-3)

**Scope.** Only YAML that is *not* valid for yaml@1.10 (the npm `yaml` package SUSHI uses) or that libyaml and
yaml@1.10 judge differently. All 266 real corpus `sushi-config.yaml` files import identically (CorpusConfigTest),
and none of the 1,035 mutation inputs differs in the imported configuration.

**Evidence.** `MutationConfigTest` (scripts/oracle/config-mutations.py, oracle = TS SUSHI 3.21.1 on node 22):
1,035 inputs: 936 identical, 42 typed-lossy (same TS-shaped configuration; the typed `Configuration` can't hold a
raw value of an unexpected type, see `ConfigurationJs`), **49 yaml-wording** (both reject, different message),
**8 accept/reject mismatches** (2 only TS rejects, 6 only Java rejects), 0 import differences.

**Cause.** The YAML scanner/parser is the libyaml 0.2.5 port vendored from fhir-ig-jekyll (shared with the Jekyll
shim); yaml@1.10 has its own parser with its own error detection and messages. The composer on top already follows
yaml@1.10 (core schema, duplicate keys, etc.). Consequence for users: the `Error parsing configuration: <message>`
line differs, or (8 cases) a config with stray tabs/control characters/odd indentation is accepted by one side only.

**Decision (open, for the owner):**
- *Accept* (current state): documented here; malformed configs still fail (or pass) with an error at the same place
  in 49 of 57 cases; cost zero; risk: users comparing error text, and 8 edge inputs where the build outcome differs.
- *Fix later*: map libyaml's errors to yaml@1.10's wording and add yaml@1.10's extra checks (e.g. tab-started plain
  values, comment spacing) as a SUSHI-only layer over the shared event layer; the mutation set is the regression test.
  Recommended for the 8 accept/reject cases at least (they change the exit status); wording can follow.

| class | case | TS (yaml@1.10) | Java (libyaml port) |
|---|---|---|---|
| yaml-ts-only-rejects | `hand/comment-no-space` | Comments must be separated from other tokens by white space characters | (parses) |
| yaml-wording | `hand/tab-indent` | Plain value cannot start with a tab character | All collection items must start at the same column |
| yaml-java-only-rejects | `hand/tab-blank-line` | (parses) | Plain value cannot start with a tab character |
| yaml-java-only-rejects | `hand/control-char` | (parses) | control characters are not allowed |
| yaml-java-only-rejects | `hand/del-char` | (parses) | control characters are not allowed |
| yaml-java-only-rejects | `hand/nel-char` | (parses) | Implicit map keys need to be followed by map values |
| yaml-ts-only-rejects | `hand/circular` | Maximum call stack size exceeded | (parses) |
| yaml-wording | `hand/stray-dash` | A collection cannot be both a mapping and a sequence | All collection items must start at the same column |
| yaml-java-only-rejects | `hand/stray-colon` | (parses) | All collection items must start at the same column |
| yaml-java-only-rejects | `hand/percent-plain` | (parses) | All collection items must start at the same column |
| yaml-wording | `hand/directive-in-body` | Implicit map keys need to be followed by map values | Document contains trailing content not separated by a ... or --- line |
| yaml-wording | `hand/anchor-twice` | A node can have at most one anchor | All collection items must start at the same column |
| yaml-wording | `hand/alias-with-anchor` | An alias node must not specify any properties | All collection items must start at the same column |
| yaml-wording | `syn/CoBHA__CoBHRM-FHIR-IG/06-tabline` | All collection items must start at the same column | Plain value cannot start with a tab character |
| yaml-wording | `syn/CoBHA__CoBHRM-FHIR-IG/10-dedent` | All collection items must start at the same column | Nested mappings are not allowed in compact mappings |
| yaml-wording | `syn/HL7__DeviceAlerting/13-tabline` | All collection items must start at the same column | Plain value cannot start with a tab character |
| yaml-wording | `syn/HL7-cz__cz-ems/05-tabline` | All collection items must start at the same column | Plain value cannot start with a tab character |
| yaml-wording | `syn/HL7-cz__cz-ems/06-tabline` | All collection items must start at the same column | Plain value cannot start with a tab character |
| yaml-wording | `syn/HL7NZ__nzbase/00-lswap` | A collection cannot be both a mapping and a sequence | All collection items must start at the same column |
| yaml-wording | `syn/FHIR__MADFSH/00-lswap` | All collection items must start at the same column | Nested mappings are not allowed in compact mappings |
| yaml-wording | `syn/FHIR__MADFSH/01-dedent` | All collection items must start at the same column | Nested mappings are not allowed in compact mappings |
| yaml-wording | `syn/ehealthsuisse__ch-health-dossier/06-tabline` | All collection items must start at the same column | Plain value cannot start with a tab character |
| yaml-wording | `syn/HL7__case-reporting/00-del` | All collection items must start at the same column | Nested mappings are not allowed in compact mappings |
| yaml-wording | `syn/HL7__case-reporting/10-tabline` | All collection items must start at the same column | Plain value cannot start with a tab character |
| yaml-wording | `syn/HL7__case-reporting/13-tabline` | All collection items must start at the same column | Plain value cannot start with a tab character |
| yaml-wording | `syn/HL7__emedicinal-product-info/01-tabline` | All collection items must start at the same column | Plain value cannot start with a tab character |
| yaml-wording | `syn/hl7-be__core-clinical/06-indent` | All collection items must start at the same column | Nested mappings are not allowed in compact mappings |
| yaml-wording | `syn/vadi2__VadimTestIG/02-tabline` | Plain value cannot start with a tab character | All collection items must start at the same column |
| yaml-wording | `syn/HL7__imaging-service-request-ig/04-dedent` | All collection items must start at the same column | Nested mappings are not allowed in compact mappings |
| yaml-wording | `syn/HL7__imaging-service-request-ig/09-indent` | All collection items must start at the same column | Nested mappings are not allowed in compact mappings |
| yaml-wording | `syn/HL7__cds-hooks-library/06-tabline` | All collection items must start at the same column | Plain value cannot start with a tab character |
| yaml-wording | `syn/HL7__cds-hooks-library/12-lswap` | All collection items must start at the same column | Nested mappings are not allowed in compact mappings |
| yaml-wording | `syn/HL7__davinci-pct/00-tabline` | All collection items must start at the same column | Plain value cannot start with a tab character |
| yaml-wording | `syn/euridice-org__eu-health-data-api/00-lswap` | Implicit map keys need to be on a single line | Implicit map keys need to be followed by map values |
| yaml-wording | `syn/euridice-org__eu-health-data-api/06-tabline` | All collection items must start at the same column | Plain value cannot start with a tab character |
| yaml-wording | `syn/HL7__davinci-ra/01-dedent` | All collection items must start at the same column | Nested mappings are not allowed in compact mappings |
| yaml-wording | `syn/WorldHealthOrganization__smart-base-clinical/00-quote` | All collection items must start at the same column | The ""# look ...e, the "" key is too long |
| yaml-wording | `syn/HL7__smart-scheduling-links/01-indent` | A collection cannot be both a mapping and a sequence | All collection items must start at the same column |
| yaml-wording | `syn/HL7__smart-scheduling-links/08-lswap` | All collection items must start at the same column | Nested mappings are not allowed in compact mappings |
| yaml-wording | `syn/HL7__smart-scheduling-links/12-quote` | All collection items must start at the same column | Implicit map keys need to be followed by map values |
| yaml-wording | `syn/HL7__ebm/08-lswap` | All collection items must start at the same column | Nested mappings are not allowed in compact mappings |
| yaml-wording | `syn/medcomdk__dk-medcom-xds-metadata/00-quote` | All collection items must start at the same column | Implicit map keys need to be followed by map values |
| yaml-wording | `syn/medcomdk__dk-medcom-xds-metadata/07-tabline` | All collection items must start at the same column | Plain value cannot start with a tab character |
| yaml-wording | `syn/medcomdk__dk-medcom-xds-metadata/12-tabline` | All collection items must start at the same column | Plain value cannot start with a tab character |
| yaml-wording | `syn/Minsal-CL__tei/07-tabline` | Plain value cannot start with a tab character | All collection items must start at the same column |
| yaml-wording | `syn/argonautproject__smart-imaging/10-ins` | All collection items must start at the same column | Aliased anchor not found: title |
| yaml-wording | `syn/hl7ch__agentic-ig/10-tabline` | All collection items must start at the same column | Plain value cannot start with a tab character |
| yaml-wording | `syn/medcomdk__dk-medcom-acknowledgement/01-tabline` | All collection items must start at the same column | Plain value cannot start with a tab character |
| yaml-wording | `syn/medcomdk__dk-medcom-acknowledgement/04-lswap` | All collection items must start at the same column | Nested mappings are not allowed in compact mappings |
| yaml-wording | `syn/medcomdk__dk-medcom-acknowledgement/10-tabline` | All collection items must start at the same column | Plain value cannot start with a tab character |
| yaml-wording | `syn/hl7-be__mycarenet/00-tabline` | All collection items must start at the same column | Plain value cannot start with a tab character |
| yaml-wording | `syn/hl7-be__mycarenet/04-tabline` | All collection items must start at the same column | Plain value cannot start with a tab character |
| yaml-wording | `syn/hl7-be__mycarenet/06-tabline` | All collection items must start at the same column | Plain value cannot start with a tab character |
| yaml-wording | `syn/HL7__US-Core/09-ins` | All collection items must start at the same column | Implicit map keys need to be followed by map values |
| yaml-wording | `syn/fut-infrastructure__implementation-guide-test-data/07-tabline` | All collection items must start at the same column | Plain value cannot start with a tab character |
| yaml-wording | `syn/fut-infrastructure__implementation-guide-test-data/10-quote` | All collection items must start at the same column | The ""      -... name: "" key is too long |
| yaml-wording | `syn/fut-infrastructure__implementation-guide-test-data/12-del` | Unexpected PLAIN node in sequence | Implicit map keys need to be followed by map values |
