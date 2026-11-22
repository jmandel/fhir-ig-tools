package org.hl7.fhir.sushi.importer.config;

import static org.hl7.fhir.sushi.importer.config.TestJs.*;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.hl7.fhir.common.js.Js;
import org.hl7.fhir.common.js.JsObject;
import org.hl7.fhir.sushi.utils.FshLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// port of sushi/test/import/ensureConfigurationFile.test.ts (SUSHI 3.21.1)
@org.junit.jupiter.api.TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS)
class EnsureConfigurationFileTest {
  final Map<String, Object> vars = new HashMap<>();

  static String fixture(String... parts) {
    return ImportConfigurationTest.fixture(parts);
  }
  static final List<Path> TEMPS = new ArrayList<>();

  static String mkdtemp(String prefix) throws Exception {
    Path p = Files.createTempDirectory(prefix);
    TEMPS.add(p);
    return p.toString();
  }

  static void deleteTree(Path p) throws Exception {
    try (var s = Files.walk(p)) {
      for (Path q : s.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(q);
    }
  }
  @Nested
  @DisplayName("ensureConfigurationFile")
  class DEnsureconfigurationfile1 {
    @AfterAll
    static void afterAll1() throws Exception {
      // temp dirs: deleted by deleteOnExit-free cleanup below
      for (Path p : TEMPS) deleteTree(p);
    }
    @BeforeEach
    void beforeEach1() throws Exception {
      loggerSpy.reset();
      FshLogger.logger.resetForTest();
    }
    @Test
    @DisplayName("ensureConfigurationFile should return the path to a pre-existing sushi-config.yaml")
    void t2() throws Exception {
      String tank = fixture("fixtures", "existing-sushi-config-yaml");
            vars.put("tank", tank);
      String configPath = EnsureConfigurationFile.ensureConfiguration(tank);
      assertEquals(NodePath.join(tank, "sushi-config.yaml"), configPath);
      assertEquals(0, loggerSpy.getAllLogs("error").size());
      assertEquals(1, loggerSpy.getAllLogs("info").size());
      assertMatch("Using configuration file:", loggerSpy.getLastMessage("info"));
    }
    @Test
    @DisplayName("ensureConfigurationFile should return the path to a pre-existing sushi-config.yml")
    void t3() throws Exception {
      String tank = fixture("fixtures", "existing-sushi-config-yml");
            vars.put("tank", tank);
      String configPath = EnsureConfigurationFile.ensureConfiguration(tank);
      assertEquals(NodePath.join(tank, "sushi-config.yml"), configPath);
      assertEquals(0, loggerSpy.getAllLogs("error").size());
      assertEquals(1, loggerSpy.getAllLogs("info").size());
      assertMatch("Using configuration file:", loggerSpy.getLastMessage("info"));
    }
    @Test
    @DisplayName("ensureConfigurationFile should log an error and not return a path if there is a pre-existing config.yaml (unsupported)")
    void t4() throws Exception {
      String tank = fixture("fixtures", "existing-config-yaml");
            vars.put("tank", tank);
      String configPath = EnsureConfigurationFile.ensureConfiguration(tank);
      assertNull(configPath);
      assertEquals(1, loggerSpy.getAllLogs("error").size());
      assertMatch(re("Use of config\\.yaml is no longer supported"), loggerSpy.getLastMessage("error"));
    }
    @Test
    @DisplayName("ensureConfigurationFile should log an error and not return a path if there is a pre-existing config.yml (unsupported)")
    void t5() throws Exception {
      String tank = fixture("fixtures", "existing-config-yml");
            vars.put("tank", tank);
      String configPath = EnsureConfigurationFile.ensureConfiguration(tank);
      assertNull(configPath);
      assertEquals(1, loggerSpy.getAllLogs("error").size());
      assertMatch(re("Use of config\\.yml is no longer supported"), loggerSpy.getLastMessage("error"));
    }
    @Test
    @DisplayName("ensureConfigurationFile should return undefined on an empty folder (no config file)")
    void t6() throws Exception {
      String tank = mkdtemp("sushi-test");
      String configPath = EnsureConfigurationFile.ensureConfiguration(tank);
      assertNull(configPath);
      assertEquals(0, loggerSpy.getAllLogs("error").size());
      assertEquals(0, loggerSpy.getAllLogs("info").size());
    }
  }
}
