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

/** The core-backed provider on a local cache folder (no network): core's package semantics as is. */
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
  @DisplayName("dev with only #current cached: core's answer (the cached current build, registered as #dev)")
  void devUsesCachedCurrentAsCoreDoes() throws Exception {
    // FPL instead logs "Falling back to my.pkg#current since my.pkg#dev is not locally cached. ..." and registers
    // the package as #current; log-only on the corpus (hl7-eu__imaging-api), so the port follows core
    pkg("my.pkg#current", "my.pkg", "current", "A");
    assertTrue(new CorePackageProvider(cache.toString()).isCached("my.pkg", "dev"));
    FHIRDefinitions defs = defs();
    assertEquals(LoadStatus.LOADED, defs.loadPackage("my.pkg", "dev"));
    assertEquals(List.of("info Loaded my.pkg#dev with 1 resources"), logs);
    assertEquals("http://x/A", defs.fishForMetadata("A").url);
  }

  /** A missing cache folder (first use on a machine) is created, as FPL does, instead of failing every download. */
  @Test void missingCacheFolderIsCreated() {
    Path missing = cache.resolve("no-such").resolve("packages");
    CorePackageProvider p = new CorePackageProvider(missing.toString());
    assertFalse(p.isCached("hl7.fhir.r4.core", "4.0.1"));
    assertTrue(Files.isDirectory(missing));
  }
}
