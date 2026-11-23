package org.hl7.fhir.sushi.importer.config;

import static org.hl7.fhir.sushi.importer.config.TestJs.*;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.hl7.fhir.common.js.Js;
import org.hl7.fhir.common.js.JsObject;
import org.hl7.fhir.sushi.utils.FshLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// port of sushi/test/import/loadConfigurationFromIgResource.test.ts (SUSHI 3.21.1)
/** Compares the TS configuration object (incl. undefined-valued keys, which toEqual ignores) via ConfigurationJs. */
class LoadConfigurationFromIgResourceTest {
  final Map<String, Object> vars = new HashMap<>();

  static String fixture(String... parts) {
    return ImportConfigurationTest.fixture(parts);
  }

  static JsObject load(String inputPath) {
    JsObject js = LoadConfigurationFromIgResource.loadConfigurationFromIgResourceJs(inputPath);
    assertNotNull(js);
    return js;
  }
  @Nested
  @DisplayName("loadConfigurationFromIgResource")
  class DLoadconfigurationfromigresource1 {
    @BeforeEach
    void beforeEach1() throws Exception {
      loggerSpy.reset();
      FshLogger.logger.resetForTest();
    }
    @Test
    @DisplayName("loadConfigurationFromIgResource should extract a configuration pointed to by an ig.ini file")
    void t2() throws Exception {
      // Note that the potentially valid IG at ig-JSON-with-ini/input/ImplementationGuide.json
      // is ignored in favor of the one pointed to by ig.ini
      String inputPath = fixture("fixtures", "ig-JSON-with-ini");
            vars.put("inputPath", inputPath);
      JsObject config = load(inputPath);
      assertToEqual(js("""
          {
          canonical: 'http://example.org',
          name: undefined,
          dependencies: undefined,
          packageId: undefined,
          url: 'http://example.org/ImplementationGuide',
          status: 'draft',
          version: undefined,
          FSHOnly: true,
          fhirVersion: [],
          parameters: []
          }""", vars), config);
      assertMatch(Pattern.compile("from " + Pattern.quote(NodePath.join(inputPath, "sneaky-input", "ImplementationGuide.json"))), loggerSpy.getFirstMessage("info"));
    }
    @Test
    @DisplayName("loadConfigurationFromIgResource should extract a configuration with only a url")
    void t3() throws Exception {
      String inputPath = fixture("fixtures", "ig-JSON-only-url");
            vars.put("inputPath", inputPath);
      JsObject config = load(inputPath);
      assertToEqual(js("""
          {
          canonical: 'http://example.org',
          url: 'http://example.org/ImplementationGuide',
          FSHOnly: true,
          status: 'draft',
          name: undefined,
          fhirVersion: [],
          parameters: [],
          dependencies: undefined,
          packageId: undefined,
          version: undefined
          }""", vars), config);
      assertMatch(Pattern.compile("from " + Pattern.quote(NodePath.join(inputPath, "input", "ImplementationGuide.json"))), loggerSpy.getFirstMessage("info"));
    }
    @Test
    @DisplayName("loadConfigurationFromIgResource should extract a configuration with a url and other data")
    void t4() throws Exception {
      String inputPath = fixture("fixtures", "ig-JSON");
            vars.put("inputPath", inputPath);
      JsObject config = load(inputPath);
      assertToEqual(js("""
          {
          canonical: 'http://example.org',
          url: 'http://example.org/ImplementationGuide',
          name: 'TestIG',
          packageId: 'fhir.test.ig',
          FSHOnly: true,
          status: 'draft',
          dependencies: [
          { packageId: 'foo.bar', version: '1.2.3' },
          { packageId: 'bar.foo', version: 'current' }
          ],
          fhirVersion: ['4.0.1'],
          version: '1.0.0',
          parameters: [
          {
          code: 'path-resource',
          value: 'input/resources'
          },
          {
          code: 'path-resource',
          value: 'input/second-resource'
          }
          ]
          }""", vars), config);
      assertMatch(Pattern.compile("from " + Pattern.quote(NodePath.join(inputPath, "input", "ImplementationGuide.json"))), loggerSpy.getFirstMessage("info"));
      assertEquals("Extracted configuration:", loggerSpy.getMessageAtIndex(1, "info"));
      assertEquals("  canonical: \"http://example.org\"", loggerSpy.getMessageAtIndex(2, "info"));
      assertEquals("  url: \"http://example.org/ImplementationGuide\"", loggerSpy.getMessageAtIndex(3, "info"));
      assertEquals("  name: \"TestIG\"", loggerSpy.getMessageAtIndex(4, "info"));
      assertEquals("  packageId: \"fhir.test.ig\"", loggerSpy.getMessageAtIndex(5, "info"));
      assertEquals("  version: \"1.0.0\"", loggerSpy.getMessageAtIndex(6, "info"));
      assertEquals("  status: \"draft\"", loggerSpy.getMessageAtIndex(7, "info"));
      assertEquals("  fhirVersion[0]: \"4.0.1\"", loggerSpy.getMessageAtIndex(8, "info"));
      assertEquals("  dependencies[0]: {\"packageId\":\"foo.bar\",\"version\":\"1.2.3\"}", loggerSpy.getMessageAtIndex(9, "info"));
      assertEquals("  dependencies[1]: {\"packageId\":\"bar.foo\",\"version\":\"current\"}", loggerSpy.getMessageAtIndex(10, "info"));
      assertEquals("  parameters[0]: {\"code\":\"path-resource\",\"value\":\"input/resources\"}", loggerSpy.getMessageAtIndex(11, "info"));
      assertEquals("  parameters[1]: {\"code\":\"path-resource\",\"value\":\"input/second-resource\"}", loggerSpy.getMessageAtIndex(12, "info"));
      assertEquals("  FSHOnly: true", loggerSpy.getMessageAtIndex(13, "info"));
    }
    @Test
    @DisplayName("loadConfigurationFromIgResource should not overwrite a configuration with an included status")
    void t5() throws Exception {
      String inputPath = fixture("fixtures", "ig-JSON-with-status");
            vars.put("inputPath", inputPath);
      JsObject config = load(inputPath);
      assertToEqual(js("""
          {
          canonical: 'http://example.org',
          url: 'http://example.org/ImplementationGuide',
          name: 'TestIG',
          packageId: 'fhir.test.ig',
          FSHOnly: true,
          status: 'active',
          dependencies: [
          { packageId: 'foo.bar', version: '1.2.3' },
          { packageId: 'bar.foo', version: 'current' }
          ],
          fhirVersion: ['4.0.1'],
          version: '1.0.0',
          parameters: [
          {
          code: 'path-resource',
          value: 'input/resources'
          },
          {
          code: 'path-resource',
          value: 'input/second-resource'
          }
          ]
          }""", vars), config);
      assertMatch(Pattern.compile("from " + Pattern.quote(NodePath.join(inputPath, "input", "ImplementationGuide.json"))), loggerSpy.getFirstMessage("info"));
      assertEquals("Extracted configuration:", loggerSpy.getMessageAtIndex(1, "info"));
      assertEquals("  canonical: \"http://example.org\"", loggerSpy.getMessageAtIndex(2, "info"));
      assertEquals("  url: \"http://example.org/ImplementationGuide\"", loggerSpy.getMessageAtIndex(3, "info"));
      assertEquals("  name: \"TestIG\"", loggerSpy.getMessageAtIndex(4, "info"));
      assertEquals("  packageId: \"fhir.test.ig\"", loggerSpy.getMessageAtIndex(5, "info"));
      assertEquals("  version: \"1.0.0\"", loggerSpy.getMessageAtIndex(6, "info"));
      assertEquals("  status: \"active\"", loggerSpy.getMessageAtIndex(7, "info"));
      assertEquals("  fhirVersion[0]: \"4.0.1\"", loggerSpy.getMessageAtIndex(8, "info"));
      assertEquals("  dependencies[0]: {\"packageId\":\"foo.bar\",\"version\":\"1.2.3\"}", loggerSpy.getMessageAtIndex(9, "info"));
      assertEquals("  dependencies[1]: {\"packageId\":\"bar.foo\",\"version\":\"current\"}", loggerSpy.getMessageAtIndex(10, "info"));
      assertEquals("  parameters[0]: {\"code\":\"path-resource\",\"value\":\"input/resources\"}", loggerSpy.getMessageAtIndex(11, "info"));
      assertEquals("  parameters[1]: {\"code\":\"path-resource\",\"value\":\"input/second-resource\"}", loggerSpy.getMessageAtIndex(12, "info"));
      assertEquals("  FSHOnly: true", loggerSpy.getMessageAtIndex(13, "info"));
    }
    @Test
    @DisplayName("loadConfigurationFromIgResource should convert uppercase package Ids to lowercase")
    void t6() throws Exception {
      String inputPath = fixture("fixtures", "ig-JSON-uppercase-deps");
            vars.put("inputPath", inputPath);
      JsObject config = load(inputPath);
      assertToEqual(js("""
          {
          canonical: 'http://example.org',
          url: 'http://example.org/ImplementationGuide',
          name: 'TestIG',
          packageId: 'fhir.test.ig',
          status: 'draft',
          FSHOnly: true,
          dependencies: [
          { packageId: 'foo.bar', version: '1.2.3' },
          { packageId: 'bar.foo', version: 'current' },
          { packageId: 'boo.far', version: '0.0.1' }
          ],
          fhirVersion: ['4.0.1'],
          version: '1.0.0',
          parameters: []
          }""", vars), config);
      assertMatch(Pattern.compile("from " + Pattern.quote(NodePath.join(inputPath, "input", "ImplementationGuide.json"))), loggerSpy.getFirstMessage("info"));
      assertEquals("Extracted configuration:", loggerSpy.getMessageAtIndex(1, "info"));
      assertEquals("  canonical: \"http://example.org\"", loggerSpy.getMessageAtIndex(2, "info"));
      assertEquals("  url: \"http://example.org/ImplementationGuide\"", loggerSpy.getMessageAtIndex(3, "info"));
      assertEquals("  name: \"TestIG\"", loggerSpy.getMessageAtIndex(4, "info"));
      assertEquals("  packageId: \"fhir.test.ig\"", loggerSpy.getMessageAtIndex(5, "info"));
      assertEquals("  version: \"1.0.0\"", loggerSpy.getMessageAtIndex(6, "info"));
      assertEquals("  status: \"draft\"", loggerSpy.getMessageAtIndex(7, "info"));
      assertEquals("  fhirVersion[0]: \"4.0.1\"", loggerSpy.getMessageAtIndex(8, "info"));
      assertEquals("  dependencies[0]: {\"packageId\":\"foo.bar\",\"version\":\"1.2.3\"}", loggerSpy.getMessageAtIndex(9, "info"));
      assertEquals("  dependencies[1]: {\"packageId\":\"bar.foo\",\"version\":\"current\"}", loggerSpy.getMessageAtIndex(10, "info"));
      assertEquals("  dependencies[2]: {\"packageId\":\"boo.far\",\"version\":\"0.0.1\"}", loggerSpy.getMessageAtIndex(11, "info"));
      assertEquals("  FSHOnly: true", loggerSpy.getMessageAtIndex(12, "info"));
    }
    @Test
    @Disabled("differs from TS SUSHI by design: the fixture is not valid FHIR XML (no FHIR namespace, elements out of order); TS reads it with fhir-tool's lenient xmlToObj, the port with core's XML parser, which rejects it (docs/core-divergences.md C6; covered by LoadConfigurationFromCoreXmlTest)")
    @DisplayName("loadConfigurationFromIgResource should extract an XML configuration with a url and dependencies")
    void t7() throws Exception {
      String inputPath = fixture("fixtures", "ig-XML");
            vars.put("inputPath", inputPath);
      JsObject config = load(inputPath);
      assertToEqual(js("""
          {
          canonical: 'http://example.org',
          url: 'http://example.org/ImplementationGuide',
          name: 'TestIG',
          packageId: 'fhir.test.ig',
          FSHOnly: true,
          status: 'draft',
          dependencies: [
          { packageId: 'foo.bar', version: '1.2.3' },
          { packageId: 'bar.foo', version: 'current' }
          ],
          fhirVersion: ['4.0.1'],
          parameters: []
          }""", vars), config);
      assertMatch(Pattern.compile("from " + Pattern.quote(NodePath.join(inputPath, "input", "ImplementationGuide.xml"))), loggerSpy.getFirstMessage("info"));
      assertEquals("Extracted configuration:", loggerSpy.getMessageAtIndex(1, "info"));
      assertEquals("  canonical: \"http://example.org\"", loggerSpy.getMessageAtIndex(2, "info"));
      assertEquals("  url: \"http://example.org/ImplementationGuide\"", loggerSpy.getMessageAtIndex(3, "info"));
      assertEquals("  name: \"TestIG\"", loggerSpy.getMessageAtIndex(4, "info"));
      assertEquals("  packageId: \"fhir.test.ig\"", loggerSpy.getMessageAtIndex(5, "info"));
      assertEquals("  version: undefined", loggerSpy.getMessageAtIndex(6, "info"));
      assertEquals("  status: \"draft\"", loggerSpy.getMessageAtIndex(7, "info"));
      assertEquals("  fhirVersion[0]: \"4.0.1\"", loggerSpy.getMessageAtIndex(8, "info"));
      assertEquals("  dependencies[0]: {\"packageId\":\"foo.bar\",\"version\":\"1.2.3\"}", loggerSpy.getMessageAtIndex(9, "info"));
      assertEquals("  dependencies[1]: {\"packageId\":\"bar.foo\",\"version\":\"current\"}", loggerSpy.getMessageAtIndex(10, "info"));
      assertEquals("  FSHOnly: true", loggerSpy.getMessageAtIndex(11, "info"));
    }
    @Test
    @Disabled("differs from TS SUSHI by design: the fixture is not valid FHIR XML (no FHIR namespace, elements out of order); TS reads it with fhir-tool's lenient xmlToObj, the port with core's XML parser, which rejects it (docs/core-divergences.md C6; covered by LoadConfigurationFromCoreXmlTest)")
    @DisplayName("loadConfigurationFromIgResource should convert uppercase package Ids to lowercase when extracting from XML configuration")
    void t8() throws Exception {
      String inputPath = fixture("fixtures", "ig-XML-uppercase-deps");
            vars.put("inputPath", inputPath);
      JsObject config = load(inputPath);
      assertToEqual(js("""
          {
          canonical: 'http://example.org',
          url: 'http://example.org/ImplementationGuide',
          name: 'TestIG',
          packageId: 'fhir.test.ig',
          status: 'draft',
          FSHOnly: true,
          dependencies: [
          { packageId: 'foo.bar', version: '1.2.3' },
          { packageId: 'bar.foo', version: 'current' },
          { packageId: 'boo.far', version: '0.0.1' }
          ],
          fhirVersion: ['4.0.1'],
          parameters: []
          }""", vars), config);
      assertMatch(Pattern.compile("from " + Pattern.quote(NodePath.join(inputPath, "input", "ImplementationGuide.xml"))), loggerSpy.getFirstMessage("info"));
      assertEquals("Extracted configuration:", loggerSpy.getMessageAtIndex(1, "info"));
      assertEquals("  canonical: \"http://example.org\"", loggerSpy.getMessageAtIndex(2, "info"));
      assertEquals("  url: \"http://example.org/ImplementationGuide\"", loggerSpy.getMessageAtIndex(3, "info"));
      assertEquals("  name: \"TestIG\"", loggerSpy.getMessageAtIndex(4, "info"));
      assertEquals("  packageId: \"fhir.test.ig\"", loggerSpy.getMessageAtIndex(5, "info"));
      assertEquals("  version: undefined", loggerSpy.getMessageAtIndex(6, "info"));
      assertEquals("  status: \"draft\"", loggerSpy.getMessageAtIndex(7, "info"));
      assertEquals("  fhirVersion[0]: \"4.0.1\"", loggerSpy.getMessageAtIndex(8, "info"));
      assertEquals("  dependencies[0]: {\"packageId\":\"foo.bar\",\"version\":\"1.2.3\"}", loggerSpy.getMessageAtIndex(9, "info"));
      assertEquals("  dependencies[1]: {\"packageId\":\"bar.foo\",\"version\":\"current\"}", loggerSpy.getMessageAtIndex(10, "info"));
      assertEquals("  dependencies[2]: {\"packageId\":\"boo.far\",\"version\":\"0.0.1\"}", loggerSpy.getMessageAtIndex(11, "info"));
      assertEquals("  FSHOnly: true", loggerSpy.getMessageAtIndex(12, "info"));
    }
    @Test
    @DisplayName("loadConfigurationFromIgResource should find the ImplementationGuide JSON file even when other files are present")
    void t9() throws Exception {
      String inputPath = fixture("fixtures", "ig-JSON-other-files");
            vars.put("inputPath", inputPath);
      JsObject config = load(inputPath);
      assertToEqual(js("""
          {
          canonical: 'http://example.org',
          url: 'http://example.org/ImplementationGuide',
          FSHOnly: true,
          status: 'draft',
          fhirVersion: [],
          parameters: []
          }""", vars), config);
      assertMatch(Pattern.compile("from " + Pattern.quote(NodePath.join(inputPath, "input", "ImplementationGuide.json"))), loggerSpy.getFirstMessage("info"));
    }
    @Test
    @Disabled("differs from TS SUSHI by design: the fixture is not valid FHIR XML (no FHIR namespace, elements out of order); TS reads it with fhir-tool's lenient xmlToObj, the port with core's XML parser, which rejects it (docs/core-divergences.md C6; covered by LoadConfigurationFromCoreXmlTest)")
    @DisplayName("loadConfigurationFromIgResource should return null when there are two potentially valid IG files")
    void t10() throws Exception {
      String inputPath = fixture("fixtures", "ig-XML-and-ig-JSON");
            vars.put("inputPath", inputPath);
      assertNull(LoadConfigurationFromIgResource.loadConfigurationFromIgResource(inputPath));
      assertMatch(re("Multiple possible ImplementationGuide resources"), loggerSpy.getLastMessage("error"));
    }
    @Test
    @DisplayName("loadConfigurationFromIgResource should return null when there is no ImplementationGuide JSON with a url")
    void t11() throws Exception {
      String inputPath = fixture("fixtures", "ig-JSON-no-url");
            vars.put("inputPath", inputPath);
      assertNull(LoadConfigurationFromIgResource.loadConfigurationFromIgResource(inputPath));
    }
    @Test
    @DisplayName("loadConfigurationFromIgResource should return null when there is no ImplementationGuide JSON")
    void t12() throws Exception {
      String inputPath = fixture("fixtures", "ig-JSON-no-ig-JSON");
            vars.put("inputPath", inputPath);
      assertNull(LoadConfigurationFromIgResource.loadConfigurationFromIgResource(inputPath));
    }
    @Test
    @DisplayName("loadConfigurationFromIgResource should return null when the input path does not exist")
    void t13() throws Exception {
      fixture("fixtures"); // skip without the TS checkout
      String inputPath = ImportConfigurationTest.tsTestDir().resolve("fixtures/fake-path/fsh").toString();
            vars.put("inputPath", inputPath);
      assertNull(LoadConfigurationFromIgResource.loadConfigurationFromIgResource(inputPath));
    }
  }
}
