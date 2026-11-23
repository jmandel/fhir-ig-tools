# WP-8 design: FHIR definitions on org.hl7.fhir.core

SUSHI (TS) gets its external definitions from fhir-package-loader 2.2.4 (FPL). The Java port uses
org.hl7.fhir.core's package layer (`org.hl7.fhir.utilities.npm`, 7.0.6) **as is** for everything core already does,
and keeps only what core has no equivalent for (PORTING.md D1a). Where core decides differently from FPL, the
difference is measured on real IGs and listed in [package-resolution-differences.md](package-resolution-differences.md).

## What comes from core, what we add

| class (`org.hl7.fhir.sushi.fhirdefs`) | what core provides | what we add | why it is ours |
|---|---|---|---|
| `CorePackageProvider` | everything about packages: `FilesystemPackageCacheManager` (~/.fhir/packages, same layout as FPL), registry access and download (`PackageClient`, packages2/packages.fhir.org), `latest` (packages.fhir.org's `dist-tags.latest`, rule R2, else `getLatestVersion(id, false)`), `x.y.x`, `current`/`current$branch` (build.fhir.org, staleness check), `dev` (falls back to a cached `current`), tarball extraction, the resource list of a package (`NpmPackage.listResources()` = `package/.index.json`) | nothing but the adapter (~90 lines) | — |
| `PackageProvider` | — | the 4-method seam (`resolveLatest`, `isCached`, `loadPackage`, `readResource`) | lets tests use a fake instead of the user's cache and the network (TS tests mock FPL's cache/registry the same way) |
| `BasePackageLoader` | — | `loadPackage` = call core, then register the package's resources in the index; `loadVirtualPackage`; the resource LRU cache; **FPL's log messages** (`Resolved X#latest to concrete version Y`, `Loaded X#Y with N resources`, `X#Y already loaded`, `Falling back to X#current ...`, `Failed to load X#Y: ...`, `Downloaded`/`Cached ... to`) | SUSHI prints these lines (they are part of its console output, which must stay identical) |
| `InMemoryPackageDB`, `PackageDB`, `ResourceInfo`, `PackageInfo`, `PackageStats`, `FindResourceInfoOptions` | — | the resource index and its query: key = `id` OR `name` OR `url` (optionally `\|version`), filtered by resourceType or **SD flavor** (Profile/Extension/Type/Resource/Logical), scoped to a package, ordered by type list then **load order** | this is what SUSHI's "fishing" is; core has no such index (see below). FPL keeps it in SQLite (sql.js); here plain lists + lookup-only hash maps (never iterated: results are always ordered by rowid/sort) |
| `VirtualPackage`, `InMemoryVirtualPackage`, `DiskBasedVirtualPackage` | — | packages that are not in the cache: SUSHI's R5-for-R4 definitions (`sushi-r5forR4#1.0.0`), predefined resources from `input/` (`sushi-local#LOCAL`, incl. instances of logical models registered as `Unknown`), test fixtures; FPL's file walk (recursive, sorted), spreadsheet/non-JSON counting and messages | SUSHI-specific; core has no notion of them |
| `FHIRDefinitions` | — | SUSHI's `FHIRDefinitions.ts`: `fishForFHIR/Metadata/Metadatas` with SUSHI's DEFAULT_SORT (FISHING_ORDER, newest first), predefined-resource lookups, cross-version (xver) extension URL handling and messages | SUSHI code, ported 1:1 |
| `R5DefsForR4`, `FplErrors` | — | 7 R5 definitions SUSHI injects for R4/R4B; FPL error texts SUSHI reports (`Latest version of package X could not be determined ...`) | SUSHI/FPL content |
| `utils.Processing` (dependency part), `utils.FHIRVersionUtils` | — | which packages SUSHI asks for and in which order (automatic dependencies low/high priority, configured dependencies, FHIR core, npm aliases, old xver package names) | SUSHI code, ported 1:1 |

Removed after the owner's review (they re-implemented core): FPL-style `PackageCache`, `RegistryClient`,
`CurrentBuildClient` and their core adapters; FPL's own `latest`/`x.y.x` resolution, current-build staleness check
and download code. FPL does not load transitive dependencies (SUSHI lists every package itself), and core's
`loadPackage` does not either, so there is no dependency-closure code on either side.

## Why core's worker context (CanonicalResourceManager) can't serve SUSHI's lookups

core's lookup layer for conformance resources is `IWorkerContext` / `CanonicalResourceManager` (org.hl7.fhir.r5):

1. **Model classes and conversion.** It stores parsed `org.hl7.fhir.r5.model` resources (R4/R4B packages are
   converted to R5 on load). SUSHI must see each definition as the exact JSON in the package (key order, number text
   as JS parses it, R4 shapes for R4 IGs) because it clones the parent snapshot and writes it back out; a converted R5
   object round-tripped to JSON is not that JSON. PORTING.md D1/D2 exclude core's model classes for this reason.
2. **Different keys.** CRM looks up by canonical URL (and `url|version`, partial versions) per resource type.
   SUSHI fishes by **id OR name OR url** in one query across several types (`Patient`, `us-core-patient` and
   `http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient` all find the same profile), including names,
   which CRM does not index.
3. **Different classification.** SUSHI asks for SD *flavors* (Profile vs Extension vs Type vs Resource vs Logical,
   derived from kind/derivation/type/baseDefinition) and for `Instance` as a wildcard; CRM only knows resource types.
4. **Different precedence.** When several packages define the same canonical, CRM picks by its own rules (version
   ordering, package priority / "core wins", dropping duplicates on load). SUSHI's rule is: first by the requested
   type order, then the **most recently loaded package wins**, and SUSHI relies on its load order to get the result
   it wants (R5-for-R4 definitions lowest, then terminology/tools, configured dependencies, FHIR core, and the
   extensions pack last so it overrides core). `fishForMetadatas` also needs *all* matches in that order.
5. **Things that are not packages.** SUSHI's virtual packages (in-memory R5 defs, predefined resources incl. logical
   instances with no FHIR resourceType) have no place in a worker context.
6. **Weight.** A worker context needs a loaded terminology/validation stack (`SimpleWorkerContext`, r5 model,
   FHIRPath, snapshot generation) that SUSHI does not use.

So the index is small (≈300 lines) and specific; the measured question is only whether core *loads the same
packages and files* as FPL — see the differences document.

## Notes

- core logs through slf4j; with no provider on the classpath slf4j prints a 3-line `SLF4J(W): No SLF4J providers`
  notice on **stderr** once (stdout, which the publisher parses, is unaffected).
- `commons-compress` is optional in core's pom but core's cache needs it at runtime; it is a direct dependency here.
- FPL's SafeMode.FREEZE (deep-frozen shared resource objects) is not emulated: callers get the cached object and must
  clone before modifying (TS code does), as FPL would throw on mutation.
