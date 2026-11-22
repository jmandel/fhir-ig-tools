package org.hl7.fhir.sushi.export;

import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.hl7.fhir.sushi.testhelpers.MinimalConfig.minimalConfig;
import static org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.testDefsPath;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.sushi.importer.FSHDocument;
import org.hl7.fhir.sushi.importer.FSHTank;
import org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions;
import org.hl7.fhir.sushi.testhelpers.TestFisher;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;

/**
 * Shared setup of the StructureDefinitionExporter test files (TS beforeAll/beforeEach of
 * test/export/StructureDefinitionExporter.test.ts "StructureDefinitionExporter R4"): defs loaded once per JVM and
 * argument list (TS beforeAll per file), then a fresh doc/tank/pkg/fisher/exporter per test.
 */
abstract class SdeTestBase {
  private static final Map<List<Object>, TestFHIRDefinitions> DEFS_CACHE = new LinkedHashMap<>();

  /** getTestFHIRDefinitions(includeR5forR4, paths...), cached; skips the test when the TS checkout is absent. */
  static synchronized TestFHIRDefinitions defs(boolean includeR5forR4, String... paths) {
    Assumptions.assumeTrue(Files.isDirectory(Path.of(testDefsPath())), "TS SUSHI checkout not found (sushi.ts.dir)");
    List<Object> key = new java.util.ArrayList<>();
    key.add(includeR5forR4);
    key.addAll(List.of(paths));
    return DEFS_CACHE.computeIfAbsent(key, k -> TestFHIRDefinitions.getTestFHIRDefinitions(includeR5forR4, paths));
  }

  /** The R4 defs of StructureDefinitionExporter.test.ts "StructureDefinitionExporter R4". */
  static TestFHIRDefinitions r4Defs() {
    return defs(true, testDefsPath("r4-definitions"), testDefsPath("CodeSystem-type-characteristics-code.json"),
        testDefsPath("StructureDefinition-FuturePlanet.json"), testDefsPath("StructureDefinition-PastPlanet.json"),
        testDefsPath("StructureDefinition-elementdefinition-type-must-support.json"));
  }

  /** TS defs.loadVirtualPackage(new InMemoryVirtualPackage({name, version}, map, {log, allowNonResources: true})). */
  static void loadInMemoryVirtualPackage(TestFHIRDefinitions defs, String name, String version,
      Map<String, Object> resourcesById) {
    defs.loadVirtualPackage(new org.hl7.fhir.sushi.fhirdefs.InMemoryVirtualPackage(
        org.hl7.fhir.sushi.fhirdefs.InMemoryVirtualPackage.packageJSON(name, version), resourcesById,
        TestFHIRDefinitions.LOG_MESSAGE, true));
  }

  TestFHIRDefinitions defs;
  TestFisher fisher;
  FSHDocument doc;
  FSHTank tank;
  Package pkg;
  StructureDefinitionExporter exporter;

  /** Override to load different defs (e.g. R5). */
  TestFHIRDefinitions loadDefs() {
    return r4Defs();
  }

  @BeforeEach
  void sdeSetUp() {
    TestFisher.assumeTankImplemented();
    defs = loadDefs();
    loggerSpy.reset();
    doc = new FSHDocument("fileName");
    tank = new FSHTank(new java.util.ArrayList<>(List.of(doc)), minimalConfig());
    pkg = new Package(tank.config);
    fisher = new TestFisher(tank, defs, pkg);
    exporter = new StructureDefinitionExporter(tank, pkg, fisher);
  }
}
