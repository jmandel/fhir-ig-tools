package org.hl7.fhir.sushi.utils;

import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.getTestFHIRDefinitions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fhirdefs.FHIRDefinitions;
import org.hl7.fhir.sushi.fshtypes.Configuration;
import org.hl7.fhir.sushi.js.JsObject;
import org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions;
import org.hl7.fhir.sushi.utils.Processing.AutomaticDependency;
import org.hl7.fhir.sushi.utils.Processing.AutomaticDependencyPriority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// port of the dependency-loading describes of sushi/test/utils/Processing.test.ts (SUSHI 3.21.1):
// '#loadExternalDependencies()' and '#loadAutomaticDependencies'. The other describes are WP-10's.
@DisplayName("Processing")
class ProcessingDependenciesTest {
  static final int NUM_R4_AUTO_DEPENDENCIES = 4;
  static final int NUM_R4_AUTO_DEPENDENCIES_LOW = 3;
  static final int NUM_R4_AUTO_DEPENDENCIES_HIGH = 1;
  static final int NUM_R5_AUTO_DEPENDENCIES = 3;

  /** test/utils/minimalConfig.ts */
  static Configuration minimalConfig() {
    Configuration c = new Configuration();
    c.filePath = "sushi-config.yaml";
    c.id = "fhir.us.minimal";
    c.canonical = "http://hl7.org/fhir/us/minimal";
    c.name = "MinimalIG";
    c.status = "draft";
    c.version = "1.0.0";
    c.fhirVersion = new ArrayList<>(List.of("4.0.1"));
    return c;
  }

  static JsObject dep(String packageId, String version) {
    JsObject o = new JsObject();
    o.put("packageId", packageId);
    if (version != null) o.put("version", version);
    return o;
  }

  static List<JsObject> deps(JsObject... d) {
    return new ArrayList<>(List.of(d));
  }

  static List<String> loadedPackages(FHIRDefinitions defs) {
    return defs.findPackageInfos("*").stream().map(p -> p.name + "#" + p.version).toList();
  }

  static void assertMatches(String regex, String actual) {
    assertTrue(actual != null && Pattern.compile(regex, Pattern.DOTALL).matcher(actual).find(),
        () -> "expected /" + regex + "/s to match: " + actual);
  }

  static void assertNotMatches(String regex, String actual) {
    assertFalse(actual != null && Pattern.compile(regex, Pattern.DOTALL).matcher(actual).find(),
        () -> "expected /" + regex + "/s not to match: " + actual);
  }

  /** test/testhelpers/asserts.ts assertAutomaticR4Dependencies */
  static void assertAutomaticR4Dependencies(List<String> packages) {
    assertTrue(packages.contains("sushi-r5forR4#1.0.0"), packages::toString);
    for (AutomaticDependency dep : Processing.AUTOMATIC_DEPENDENCIES) {
      if (dep.packageId().equals("hl7.terminology.r4") && dep.version().equals("latest")) {
        assertTrue(packages.contains("hl7.terminology.r4#9.9.9"), packages::toString);
      } else if (dep.packageId().equals("hl7.fhir.uv.extensions.r4") && dep.version().equals("latest")) {
        assertTrue(packages.contains("hl7.fhir.uv.extensions.r4#9.9.9"), packages::toString);
      } else if (dep.packageId().equals("hl7.fhir.uv.tools.r4") && dep.version().equals("latest")) {
        assertTrue(packages.contains("hl7.fhir.uv.tools.r4#9.9.9"), packages::toString);
      } else if (!dep.packageId().endsWith(".r5")) {
        assertTrue(packages.contains(dep.packageId() + "#" + dep.version()), packages::toString);
      }
    }
  }

  /** test/testhelpers/asserts.ts assertAutomaticR5Dependencies */
  static void assertAutomaticR5Dependencies(List<String> packages) {
    for (AutomaticDependency dep : Processing.AUTOMATIC_DEPENDENCIES) {
      if (dep.packageId().equals("hl7.terminology.r5") && dep.version().equals("latest")) {
        assertTrue(packages.contains("hl7.terminology.r5#9.9.9"), packages::toString);
      } else if (dep.packageId().equals("hl7.fhir.uv.extensions.r5") && dep.version().equals("latest")) {
        assertTrue(packages.contains("hl7.fhir.uv.extensions.r5#9.9.9"), packages::toString);
      } else if (dep.packageId().equals("hl7.fhir.uv.tools.r5") && dep.version().equals("latest")) {
        assertTrue(packages.contains("hl7.fhir.uv.tools.r5#9.9.9"), packages::toString);
      } else if (!dep.packageId().endsWith(".r4")) {
        assertTrue(packages.contains(dep.packageId() + "#" + dep.version()), packages::toString);
      }
    }
  }

  // ---------------------------------------------------------------------------------------- jest mock equivalents
  // The only places that touch the fake package provider (TS: defs.<x>ClientMock.<method>.mockReset()
  // .calledWith(...).mockRejectedValue(...)).

  static FHIRDefinitions defsWith(TestFHIRDefinitions.FakePackageProvider provider) {
    FHIRDefinitions.Overrides o = new FHIRDefinitions.Overrides();
    o.packageProvider = provider;
    return FHIRDefinitions.createFHIRDefinitions(o);
  }

  /** TS: defs.registryClientMock.download.calledWith(name, version).mockRejectedValue(new Error(message)) */
  static FHIRDefinitions defsWithFailingDownload(String name, String version, String message) {
    return defsWith(new TestFHIRDefinitions.FakePackageProvider() {
      @java.lang.Override
      public CachedPackage loadPackage(String n, String v) {
        if (n.equals(name) && v.equals(version)) throw new RuntimeException(message);
        return super.loadPackage(n, v);
      }
    });
  }

  /** TS: defs.currentBuildClientMock.downloadCurrentBuild.calledWith(name).mockRejectedValue(new Error()) */
  static FHIRDefinitions defsWithFailingCurrentDownload(String name) {
    return defsWith(new TestFHIRDefinitions.FakePackageProvider() {
      @java.lang.Override
      public CachedPackage loadPackage(String n, String v) {
        if (n.equals(name) && v.startsWith("current")) throw new RuntimeException();
        return super.loadPackage(n, v);
      }
    });
  }

  /** TS: defs.registryClientMock.resolveVersion: reject for (name, 'latest'), otherwise latest -> 9.9.9 */
  static FHIRDefinitions defsWithFailingResolveLatest(String name) {
    return defsWith(new TestFHIRDefinitions.FakePackageProvider() {
      @java.lang.Override
      public String resolveLatest(String n) {
        if (n.equals(name)) throw new RuntimeException();
        return super.resolveLatest(n);
      }
    });
  }

  // ------------------------------------------------------------------------------------------------ tests

  @Nested
  @DisplayName("#loadExternalDependencies()")
  class LoadExternalDependencies {
    @BeforeEach
    void beforeEach() {
      loggerSpy.reset();
    }

    @Test
    @DisplayName("should load specified dependencies")
    void specified() {
      Configuration config = minimalConfig();
      config.dependencies = deps(dep("hl7.fhir.us.core", "3.1.0"));
      FHIRDefinitions defs = getTestFHIRDefinitions();
      Processing.loadExternalDependencies(defs, config);
      List<String> loaded = loadedPackages(defs);
      assertEquals(2 + NUM_R4_AUTO_DEPENDENCIES, loaded.size());
      assertTrue(loaded.contains("hl7.fhir.r4.core#4.0.1"));
      assertTrue(loaded.contains("hl7.fhir.us.core#3.1.0"));
      assertAutomaticR4Dependencies(loaded);
      assertEquals(0, loggerSpy.getAllLogs("warn").size());
    }

    @Test
    @DisplayName("should load specified dependencies when NPM aliases are used")
    void npmAliases() {
      // See: https://chat.fhir.org/#narrow/channel/179239-tooling/topic/NPM.20Aliases/near/517985527
      Configuration config = minimalConfig();
      config.dependencies = deps(
          dep("hl7.fhir.us.mcode", "4.0.0"),
          dep("hl7.fhir.us.core", "7.0.0"),
          dep("v610@npm:hl7.fhir.us.core", "6.1.0"),
          dep("hl7.fhir.us.qicore", "6.0.0"),
          dep("v311@npm:hl7.fhir.us.core", "3.1.1"));
      FHIRDefinitions defs = getTestFHIRDefinitions();
      Processing.loadExternalDependencies(defs, config);
      List<String> loaded = loadedPackages(defs);
      assertEquals(6 + NUM_R4_AUTO_DEPENDENCIES, loaded.size());
      // Check that specified packages are loaded in correct order after autodependencies.
      // Correct order includes sorting multi-version packages so most recent is last.
      assertEquals(List.of(
          "hl7.fhir.us.mcode#4.0.0",
          "hl7.fhir.us.core#3.1.1",
          "hl7.fhir.us.core#6.1.0",
          "hl7.fhir.us.core#7.0.0",
          "hl7.fhir.us.qicore#6.0.0",
          "hl7.fhir.r4.core#4.0.1"),
          loaded.subList(NUM_R4_AUTO_DEPENDENCIES_LOW, loaded.size() - NUM_R4_AUTO_DEPENDENCIES_HIGH));
      assertAutomaticR4Dependencies(loaded);
      assertEquals(0, loggerSpy.getAllLogs("warn").size());
      assertEquals(0, loggerSpy.getAllLogs("error").size());
    }

    @Test
    @DisplayName("should load automatic dependencies last so they have highest priority")
    void autoLast() {
      Configuration config = minimalConfig();
      config.dependencies = deps(dep("hl7.fhir.us.core", "3.1.0"));
      FHIRDefinitions defs = getTestFHIRDefinitions();
      Processing.loadExternalDependencies(defs, config);
      List<String> loaded = loadedPackages(defs);
      assertEquals(2 + NUM_R4_AUTO_DEPENDENCIES, loaded.size());
      assertEquals(List.of(
          "sushi-r5forR4#1.0.0",
          "hl7.fhir.uv.tools.r4#9.9.9",
          "hl7.terminology.r4#9.9.9",
          "hl7.fhir.us.core#3.1.0",
          "hl7.fhir.r4.core#4.0.1",
          "hl7.fhir.uv.extensions.r4#9.9.9"), loaded);
      assertEquals(0, loggerSpy.getAllLogs("warn").size());
    }

    @Test
    @DisplayName("should wait to load user-specified autodepency overrides until the end (with the other automatic dependencies)")
    void overridesAtEnd() {
      Configuration config = minimalConfig();
      config.dependencies = deps(
          dep("hl7.fhir.us.core", "3.1.0"),
          dep("hl7.fhir.uv.extensions.r4", "7.7.7"),
          dep("hl7.fhir.uv.tools.r4", "9.8.7"));
      FHIRDefinitions defs = getTestFHIRDefinitions();
      Processing.loadExternalDependencies(defs, config);
      List<String> loaded = loadedPackages(defs);
      assertEquals(2 + NUM_R4_AUTO_DEPENDENCIES, loaded.size());
      assertEquals(List.of(
          "sushi-r5forR4#1.0.0",
          "hl7.fhir.uv.tools.r4#9.8.7",
          "hl7.terminology.r4#9.9.9",
          "hl7.fhir.us.core#3.1.0",
          "hl7.fhir.r4.core#4.0.1",
          "hl7.fhir.uv.extensions.r4#7.7.7"), loaded);
      assertEquals(0, loggerSpy.getAllLogs("warn").size());
    }

    void fhirVersionCase(String fhirVersion, int expectedSize, List<String> contains, boolean preReleaseWarn) {
      Configuration config = minimalConfig();
      config.fhirVersion = new ArrayList<>(List.of(fhirVersion));
      FHIRDefinitions defs = getTestFHIRDefinitions();
      Processing.loadExternalDependencies(defs, config);
      List<String> loaded = loadedPackages(defs);
      assertEquals(expectedSize, loaded.size(), loaded::toString);
      for (String c : contains) assertTrue(loaded.contains(c), () -> c + " not in " + loaded);
      if (preReleaseWarn) {
        assertMatches("support for pre-release versions of FHIR is experimental", loggerSpy.getLastMessage("warn"));
      } else {
        assertEquals(0, loggerSpy.getAllLogs("warn").size());
      }
    }

    @Test
    @DisplayName("should support prerelease FHIR R4B dependencies")
    void r4bPre() {
      fhirVersionCase("4.1.0", 1 + NUM_R4_AUTO_DEPENDENCIES, List.of("hl7.fhir.r4b.core#4.1.0", "sushi-r5forR4#1.0.0",
          "hl7.fhir.uv.tools.r4#9.9.9", "hl7.terminology.r4#9.9.9"), true);
    }

    @Test
    @DisplayName("should support prerelease FHIR R4B snapshot dependencies")
    void r4bSnapshot() {
      fhirVersionCase("4.3.0-snapshot1", 1 + NUM_R4_AUTO_DEPENDENCIES, List.of("hl7.fhir.r4b.core#4.3.0-snapshot1",
          "sushi-r5forR4#1.0.0", "hl7.fhir.uv.tools.r4#9.9.9", "hl7.terminology.r4#9.9.9"), true);
    }

    @Test
    @DisplayName("should support official FHIR R4B dependency")
    void r4b() {
      fhirVersionCase("4.3.0", 1 + NUM_R4_AUTO_DEPENDENCIES, List.of("hl7.fhir.r4b.core#4.3.0", "sushi-r5forR4#1.0.0",
          "hl7.fhir.uv.tools.r4#9.9.9", "hl7.terminology.r4#9.9.9"), false);
    }

    @Test
    @DisplayName("should support prerelease FHIR R5 dependencies")
    void r5Pre() {
      fhirVersionCase("4.5.0", 1 + NUM_R5_AUTO_DEPENDENCIES, List.of("hl7.fhir.r5.core#4.5.0",
          "hl7.fhir.uv.tools.r5#9.9.9", "hl7.terminology.r5#9.9.9"), true);
    }

    @Test
    @DisplayName("should support prerelease FHIR R5 snapshot dependencies")
    void r5Snapshot() {
      fhirVersionCase("5.0.0-snapshot1", 1 + NUM_R5_AUTO_DEPENDENCIES, List.of("hl7.fhir.r5.core#5.0.0-snapshot1",
          "hl7.fhir.uv.tools.r5#9.9.9", "hl7.terminology.r5#9.9.9"), true);
    }

    @Test
    @DisplayName("should support official FHIR R5 dependency")
    void r5() {
      fhirVersionCase("5.0.0", 1 + NUM_R5_AUTO_DEPENDENCIES, List.of("hl7.fhir.r5.core#5.0.0",
          "hl7.fhir.uv.tools.r5#9.9.9", "hl7.terminology.r5#9.9.9", "hl7.fhir.uv.extensions.r5#9.9.9"), false);
    }

    @Test
    @DisplayName("should support FHIR current dependencies")
    void current() {
      fhirVersionCase("current", 1 + NUM_R5_AUTO_DEPENDENCIES, List.of("hl7.fhir.r5.core#current",
          "hl7.fhir.uv.tools.r5#9.9.9", "hl7.terminology.r5#9.9.9", "hl7.fhir.uv.extensions.r5#9.9.9"), true);
    }

    @Test
    @DisplayName("should switch old-style cross-version FHIR extensions package to new xver package")
    void oldXver() {
      Configuration config = minimalConfig();
      config.fhirVersion = new ArrayList<>(List.of("5.0.0"));
      config.dependencies = deps(
          dep("hl7.fhir.extensions.r2", "5.0.0"),
          dep("hl7.fhir.extensions.r3", "5.0.0"),
          dep("hl7.fhir.extensions.r4", "5.0.0"),
          dep("hl7.fhir.extensions.r4b", "5.0.0"));
      FHIRDefinitions defs = getTestFHIRDefinitions();
      Processing.loadExternalDependencies(defs, config);
      List<String> loaded = loadedPackages(defs);
      assertEquals(5 + NUM_R5_AUTO_DEPENDENCIES, loaded.size(), loaded::toString);
      assertTrue(loaded.contains("hl7.fhir.uv.xver-r2.r5#9.9.9"));
      assertTrue(loaded.contains("hl7.fhir.uv.xver-r3.r5#9.9.9"));
      assertTrue(loaded.contains("hl7.fhir.uv.xver-r4.r5#9.9.9"));
      assertTrue(loaded.contains("hl7.fhir.uv.xver-r4b.r5#9.9.9"));
      assertAutomaticR5Dependencies(loaded);
      assertMatches("Found old-style .+ hl7\\.fhir\\.extensions\\.r2\\..+official .+ hl7\\.fhir\\.uv\\.xver-r2\\.r5\\.",
          loggerSpy.getMessageAtIndex(-4, "warn"));
      assertMatches("Found old-style .+ hl7\\.fhir\\.extensions\\.r3\\..+official .+ hl7\\.fhir\\.uv\\.xver-r3\\.r5\\.",
          loggerSpy.getMessageAtIndex(-3, "warn"));
      assertMatches("Found old-style .+ hl7\\.fhir\\.extensions\\.r4\\..+official .+ hl7\\.fhir\\.uv\\.xver-r4\\.r5\\.",
          loggerSpy.getMessageAtIndex(-2, "warn"));
      assertMatches("Found old-style .+ hl7\\.fhir\\.extensions\\.r4b\\..+official .+ hl7\\.fhir\\.uv\\.xver-r4b\\.r5\\.",
          loggerSpy.getMessageAtIndex(-1, "warn"));
      assertEquals(0, loggerSpy.getAllLogs("error").size());
    }

    @Test
    @DisplayName("should log a warning when an NPM alias uses a character not valid for FHIR ids")
    void npmAliasInvalid() {
      // See: https://chat.fhir.org/#narrow/channel/179239-tooling/topic/NPM.20Aliases/near/518051795
      Configuration config = minimalConfig();
      config.dependencies = deps(
          dep("hl7.fhir.us.mcode", "4.0.0"),
          dep("hl7.fhir.us.core", "7.0.0"),
          dep("v610!@npm:hl7.fhir.us.core", "6.1.0"),
          dep("hl7.fhir.us.qicore", "6.0.0"),
          dep("v311@npm:hl7.fhir.us.core", "3.1.1"));
      FHIRDefinitions defs = getTestFHIRDefinitions();
      Processing.loadExternalDependencies(defs, config);
      List<String> loaded = loadedPackages(defs);
      assertEquals(6 + NUM_R4_AUTO_DEPENDENCIES, loaded.size());
      // Check that the package w/ the invalid alias was still loaded anyway
      assertTrue(loaded.contains("hl7.fhir.us.core#6.1.0"));
      assertMatches("NPM aliases should .+\\. Found 'v610!'\\.", loggerSpy.getLastMessage("warn"));
      assertEquals(0, loggerSpy.getAllLogs("error").size());
    }

    @Test
    @DisplayName("should log an error when it fails to load a dependency")
    void failsToLoad() {
      Configuration config = minimalConfig();
      config.dependencies = deps(dep("hl7.does.not.exist", "current"));
      FHIRDefinitions defs = defsWithFailingCurrentDownload("hl7.does.not.exist");
      Processing.loadExternalDependencies(defs, config);
      List<String> loaded = loadedPackages(defs);
      assertEquals(1 + NUM_R4_AUTO_DEPENDENCIES, loaded.size());
      assertTrue(loaded.contains("hl7.fhir.r4.core#4.0.1"));
      assertAutomaticR4Dependencies(loaded);
      assertMatches("Failed to load hl7\\.does\\.not\\.exist#current", loggerSpy.getLastMessage("error"));
      // But don't log the error w/ details about proxies
      assertNotMatches("SSL", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("should log a more detailed error when it fails to load a dependency due to certificate issue")
    void certificate() {
      Configuration config = minimalConfig();
      config.dependencies = deps(dep("self-signed.package", "1.0.0"));
      FHIRDefinitions defs = defsWithFailingDownload("self-signed.package", "1.0.0",
          "self signed certificate in certificate chain");
      Processing.loadExternalDependencies(defs, config);
      List<String> loaded = loadedPackages(defs);
      assertEquals(1 + NUM_R4_AUTO_DEPENDENCIES, loaded.size());
      assertTrue(loaded.contains("hl7.fhir.r4.core#4.0.1"));
      assertAutomaticR4Dependencies(loaded);
      assertMatches("Failed to load self-signed\\.package#1\\.0\\.0", loggerSpy.getLastMessage("error"));
      // AND it should log the detailed message about SSL
      assertMatches("Sometimes this error occurs in corporate or educational environments that use proxies and/or "
          + "SSL inspection", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("should log an error when a dependency has no specified version")
    void noVersion() {
      Configuration config = minimalConfig();
      config.dependencies = deps(dep("hl7.fhir.r4.core", null));
      FHIRDefinitions defs = getTestFHIRDefinitions();
      Processing.loadExternalDependencies(defs, config);
      List<String> loaded = loadedPackages(defs);
      assertEquals(1 + NUM_R4_AUTO_DEPENDENCIES, loaded.size());
      assertTrue(loaded.contains("hl7.fhir.r4.core#4.0.1"));
      assertAutomaticR4Dependencies(loaded);
      assertMatches("Failed to load hl7\\.fhir\\.r4\\.core: No version specified\\.", loggerSpy.getLastMessage("error"));
      assertNotMatches("SSL", loggerSpy.getLastMessage("error"));
    }
  }

  @Nested
  @DisplayName("#loadAutomaticDependencies")
  class LoadAutomaticDependencies {
    @BeforeEach
    void beforeEach() {
      loggerSpy.reset();
    }

    void eachAuto(String fhirVersion, String rx, boolean r5forR4) {
      Configuration config = minimalConfig();
      config.dependencies = deps(dep("hl7.fhir.us.core", "3.1.0"));
      FHIRDefinitions defs = getTestFHIRDefinitions();
      Processing.loadAutomaticDependencies(fhirVersion, config.dependencies, defs, AutomaticDependencyPriority.Low);
      List<String> loaded = loadedPackages(defs);
      assertEquals(r5forR4 ? 3 : 2, loaded.size(), loaded::toString);
      if (r5forR4) assertTrue(loaded.contains("sushi-r5forR4#1.0.0"));
      assertTrue(loaded.contains("hl7.fhir.uv.tools." + rx + "#9.9.9"));
      assertTrue(loaded.contains("hl7.terminology." + rx + "#9.9.9"));
      assertFalse(loaded.contains("hl7.fhir.uv.extensions." + rx + "#9.9.9"));
      Processing.loadAutomaticDependencies(fhirVersion, config.dependencies, defs, AutomaticDependencyPriority.High);
      loaded = loadedPackages(defs);
      assertEquals(r5forR4 ? 4 : 3, loaded.size(), loaded.toString());
      assertTrue(loaded.contains("hl7.fhir.uv.extensions." + rx + "#9.9.9"));
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
    }

    @Test
    @DisplayName("should load each automatic dependency for FHIR R4")
    void r4() {
      eachAuto("4.0.1", "r4", true);
    }

    @Test
    @DisplayName("should load each automatic dependency for FHIR R4B")
    void r4b() {
      eachAuto("4.3.0", "r4", true);
    }

    @Test
    @DisplayName("should load each automatic dependency for FHIR R5 Final Draft")
    void r5FinalDraft() {
      eachAuto("5.0.0-draft-final", "r5", false);
    }

    @Test
    @DisplayName("should load each automatic dependency for FHIR R5")
    void r5() {
      eachAuto("5.0.0", "r5", false);
    }

    @Test
    @DisplayName("should load each automatic dependency for FHIR R6 prerelease")
    void r6Pre() {
      eachAuto("6.0.0-ballot2", "r5", false);
    }

    List<String> lowWith(List<JsObject> deps) {
      Configuration config = minimalConfig();
      config.dependencies = deps;
      FHIRDefinitions defs = getTestFHIRDefinitions();
      Processing.loadAutomaticDependencies(config.fhirVersion.get(0), config.dependencies, defs,
          AutomaticDependencyPriority.Low);
      return loadedPackages(defs);
    }

    @Test
    @DisplayName("should load configured versions for dependencies that are present in the config")
    void configured() {
      List<String> loaded = lowWith(deps(dep("hl7.fhir.uv.tools.r4", "2.2.0-test")));
      assertEquals(NUM_R4_AUTO_DEPENDENCIES_LOW, loaded.size());
      assertTrue(loaded.contains("hl7.fhir.uv.tools.r4#2.2.0-test"));
      assertFalse(loaded.contains("hl7.fhir.uv.tools.r4#9.9.9"));
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
    }

    @Test
    @DisplayName("should load multiple configured versions for dependencies that are present in the config")
    void multipleConfigured() {
      List<String> loaded = lowWith(deps(dep("hl7.fhir.uv.tools.r4", "2.0.0"), dep("hl7.fhir.uv.tools.r4", "2.2.0-test")));
      assertEquals(NUM_R4_AUTO_DEPENDENCIES_LOW + 1, loaded.size());
      assertTrue(loaded.contains("hl7.fhir.uv.tools.r4#2.0.0"));
      assertTrue(loaded.contains("hl7.fhir.uv.tools.r4#2.2.0-test"));
      assertFalse(loaded.contains("hl7.fhir.uv.tools.r4#9.9.9"));
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
    }

    @Test
    @DisplayName("should load configured versions for dependencies that are present in the config even if they do not have an r{x} suffix and the auto dependency does")
    void noSuffix() {
      List<String> loaded = lowWith(deps(dep("hl7.terminology", "4.0.0-test")));
      assertEquals(NUM_R4_AUTO_DEPENDENCIES_LOW, loaded.size());
      assertTrue(loaded.contains("hl7.terminology#4.0.0-test"));
      assertFalse(loaded.contains("hl7.terminology.r4#1.2.3-test"));
      assertFalse(loaded.contains("hl7.terminology.r4#latest"));
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
    }

    @Test
    @DisplayName("should load configured versions for dependencies that are present in the config even if they do have an r{x} suffix that does not match the auto dependency r{x} suffix")
    void otherSuffix() {
      List<String> loaded = lowWith(deps(dep("hl7.terminology.r5", "4.0.0-test")));
      assertEquals(NUM_R4_AUTO_DEPENDENCIES_LOW, loaded.size());
      assertTrue(loaded.contains("hl7.terminology.r5#4.0.0-test"));
      assertFalse(loaded.contains("hl7.terminology.r4#1.2.3-test"));
      assertFalse(loaded.contains("hl7.terminology.r4#latest"));
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
    }

    @Test
    @DisplayName("should log a warning when it fails to load an automatic dependency")
    void failsToLoad() {
      Configuration config = minimalConfig();
      config.dependencies = deps(dep("hl7.fhir.us.core", "3.1.0"));
      FHIRDefinitions defs = defsWithFailingDownload("hl7.fhir.uv.tools.r4", "9.9.9", null);
      Processing.loadAutomaticDependencies(config.fhirVersion.get(0), config.dependencies, defs,
          AutomaticDependencyPriority.Low);
      List<String> loaded = loadedPackages(defs);
      assertEquals(NUM_R4_AUTO_DEPENDENCIES_LOW - 1, loaded.size(), loaded::toString);
      assertFalse(loaded.contains("hl7.fhir.uv.tools.r4#9.9.9"));
      assertEquals(1, loggerSpy.getAllMessages("warn").size());
      assertMatches("Failed to load automatically-provided hl7\\.fhir\\.uv\\.tools\\.r4#latest",
          loggerSpy.getLastMessage("warn"));
      // But don't log the warning w/ details about proxies
      assertNotMatches("SSL", loggerSpy.getLastMessage("warn"));
      // And don't log an error
      assertEquals(0, loggerSpy.getAllLogs("error").size());
    }

    @Test
    @DisplayName("should log a warning when it fails to find the latest version of an automatic dependency due to an error")
    void failsToResolve() {
      Configuration config = minimalConfig();
      config.dependencies = deps(dep("hl7.fhir.us.core", "3.1.0"));
      FHIRDefinitions defs = defsWithFailingResolveLatest("hl7.terminology.r4");
      Processing.loadAutomaticDependencies(config.fhirVersion.get(0), config.dependencies, defs,
          AutomaticDependencyPriority.Low);
      List<String> loaded = loadedPackages(defs);
      assertEquals(NUM_R4_AUTO_DEPENDENCIES_LOW - 1, loaded.size(), loaded::toString);
      assertFalse(loaded.contains("hl7.terminology.r4#4.0.0"));
      assertEquals(1, loggerSpy.getAllMessages("warn").size());
      assertMatches("Failed to load automatically-provided hl7\\.terminology\\.r4#latest",
          loggerSpy.getLastMessage("warn"));
      assertNotMatches("SSL", loggerSpy.getLastMessage("warn"));
      assertEquals(0, loggerSpy.getAllLogs("error").size());
    }
  }
}
