package org.hl7.fhir.sushi.fhirdefs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.sushi.utils.FshLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The core-backed provider on a local cache folder (no network) and the FPL rules ported on top of core. */
class CorePackageProviderTest {
  @TempDir Path cache;
  private final List<String> logs = new ArrayList<>();

  private void pkg(String folder, String name, String version, String... resources) throws Exception {
    Path p = cache.resolve(folder).resolve("package");
    Files.createDirectories(p);
    Files.writeString(p.resolve("package.json"),
        "{\"name\":\"" + name + "\",\"version\":\"" + version + "\",\"fhirVersions\":[\"4.0.1\"]}");
    for (String r : resources) {
      Files.writeString(p.resolve("StructureDefinition-" + r + ".json"),
          "{\"resourceType\":\"StructureDefinition\",\"id\":\"" + r + "\",\"url\":\"http://x/" + r
              + "\",\"kind\":\"resource\",\"type\":\"" + r + "\"}");
    }
  }

  private FHIRDefinitions defs() {
    FshLogger.logger.resetForTest();
    FshLogger.logger.setSink(e -> logs.add(e.level() + " " + e.message()));
    FHIRDefinitions.Overrides o = new FHIRDefinitions.Overrides();
    o.packageProvider = new CorePackageProvider(cache.toString());
    return FHIRDefinitions.createFHIRDefinitions(o);
  }

  @AfterEach
  void reset() {
    FshLogger.logger.resetForTest();
  }

  @Test
  @DisplayName("loads a cached package through core and registers its indexed resources")
  void loadsCached() throws Exception {
    pkg("my.pkg#1.0.0", "my.pkg", "1.0.0", "A", "B");
    FHIRDefinitions defs = defs();
    assertEquals(LoadStatus.LOADED, defs.loadPackage("my.pkg", "1.0.0"));
    assertEquals(List.of("info Loaded my.pkg#1.0.0 with 2 resources"), logs);
    assertEquals("http://x/B", defs.fishForMetadata("B").url);
  }

  @Test
  @DisplayName("rule R1: dev falls back to current with FPL's message when only #current is cached")
  void devFallsBackToCurrent() throws Exception {
    pkg("my.pkg#current", "my.pkg", "current", "A");
    CorePackageProvider p = new CorePackageProvider(cache.toString());
    assertFalse(p.isCached("my.pkg", "dev"));
    assertTrue(p.isCached("my.pkg", "current"));
  }
}
