# SUSHI 3.21.1 test fixtures (vendored)

Files from the test tree of [SUSHI](https://github.com/FHIR/sushi) 3.21.1 (commit 9c1bf5c, v3.21.1+2), copied
unchanged at their original paths under `test/`: the fixtures (IG folders, configs, FHIR definition packages under
`test/testhelpers/testdefs`) that the ported JUnit tests read. Only those files are here (the full TS test tree,
with the `.test.ts` sources, is about 29 MB); the selection is every file a full run of the JUnit suite read, plus
the other non-TypeScript files of each fixture folder it used.

SUSHI is Copyright 2019-2025 Health Level Seven International (per its README), licensed under the Apache License 2.0
([`LICENSE`](LICENSE), SUSHI's own license file; SUSHI ships no NOTICE file). See also the repository's
`THIRD_PARTY_NOTICES.md`.

The tests find this folder on the test class path (`TestFHIRDefinitions.tsDir()`); `-Dsushi.ts.dir=<a full SUSHI
checkout>` points them at another copy.
