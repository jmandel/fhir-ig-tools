package org.hl7.fhir.sushi.importer.config;

import static org.hl7.fhir.sushi.importer.config.TestJs.*;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.common.js.Js;
import org.hl7.fhir.common.js.JsObject;
import org.hl7.fhir.sushi.utils.FshLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

// port of sushi/test/import/importConfiguration.test.ts (SUSHI 3.21.1)
/**
 * The TS tests compare the returned Configuration with toEqual; here the typed {@code Configuration} is turned back
 * into the TS object ({@link ConfigurationJs#toJs}) and compared with Jest's toEqual semantics ({@link TestJs}).
 * Fixtures are read from the TS checkout ({@code -Dsushi.ts.dir}, default the VM path).
 */
class ImportConfigurationTest {
  JsObject minYAML;
  final Map<String, Object> vars = new HashMap<>();

  static Path tsTestDir() {
    return Path.of(System.getProperty("sushi.ts.dir", "/srv/ig/sushi-spike/sushi")).resolve("test/import");
  }

  /** path.join(__dirname, ...) */
  static String fixture(String... parts) {
    Path p = tsTestDir();
    for (String s : parts) p = p.resolve(s);
    assumeTrue(Files.exists(p), "TS fixtures not available: " + p);
    return p.toString();
  }

  static String read(String path) throws Exception {
    return Files.readString(Path.of(path));
  }

  /** importConfiguration(yaml, file), as the TS configuration object. */
  @SuppressWarnings("unchecked")
  static JsObject imp(Object yaml, String file) {
    return ConfigurationJs.toJs(yaml instanceof String s ? ImportConfiguration.importConfiguration(s, file)
        : ImportConfiguration.importConfiguration((Map<String, Object>) yaml, file));
  }

  /** obj.key = value */
  @SuppressWarnings("unchecked")
  static void put(Object obj, String key, Object value) {
    ((Map<String, Object>) obj).put(key, value);
  }

  /** expect(fn).toThrow(msg): the message contains msg. */
  static void assertThrowsMsg(String msg, Executable fn) {
    Throwable t = assertThrows(Throwable.class, fn);
    assertTrue(String.valueOf(t.getMessage()).contains(msg), "expected message containing <" + msg + "> but was <"
        + t.getMessage() + ">");
  }

  /** config.parameters?.find(p => p.code === code) */
  static Object findParameter(JsObject config, String code) {
    if (!(config.get("parameters") instanceof List<?> params)) return Js.UNDEFINED;
    for (Object p : params) if (code.equals(((Map<?, ?>) p).get("code"))) return p;
    return Js.UNDEFINED;
  }
  @Nested
  @DisplayName("importConfiguration")
  class DImportconfiguration1 {
    @BeforeEach
    void beforeEach1() throws Exception {
      minYAML = obj("""
          {
            id: 'fhir.us.minimal',
            canonical: 'http://hl7.org/fhir/us/minimal',
            name: 'MinimalIG',
            status: 'draft',
            version: '1.0.0',
            fhirVersion: ['4.0.1'],
            copyrightYear: '2020+',
            releaseLabel: 'Build CI',
            template: 'hl7.fhir.template#0.0.5'
          }""");
      loggerSpy.reset();
      FshLogger.logger.resetForTest();
    }
    @Test
    @DisplayName("importConfiguration should import minimal config")
    void t2() throws Exception {
      String yamlPath = fixture("fixtures", "minimal-config.yaml");
            vars.put("yamlPath", yamlPath);
      String yaml = read(yamlPath);
      JsObject actual = imp(yaml, yamlPath);
      Object expected = js("""
          {
          filePath: yamlPath,
          id: 'fhir.us.minimal',
          canonical: 'http://hl7.org/fhir/us/minimal',
          url: 'http://hl7.org/fhir/us/minimal/ImplementationGuide/fhir.us.minimal',
          name: 'MinimalIG',
          status: 'draft',
          version: '1.0.0',
          fhirVersion: ['4.0.1'],
          parameters: [
          { code: 'copyrightyear', value: '2020+' },
          { code: 'releaselabel', value: 'Build CI' }
          ],
          packageId: 'fhir.us.minimal',
          FSHOnly: false,
          applyExtensionMetadataToRoot: true,
          instanceOptions: { setMetaProfile: 'always', setId: 'always', manualSliceOrdering: false }
          }""", vars);
      assertToEqual(expected, actual);
      assertEquals(0, loggerSpy.getAllLogs("error").size());
      assertEquals(0, loggerSpy.getAllLogs("warn").size());
    }
    @Test
    @DisplayName("importConfiguration should import example config")
    void t3() throws Exception {
      String yamlPath = fixture("fixtures", "example-config.yaml");
            vars.put("yamlPath", yamlPath);
      String yaml = read(yamlPath);
      JsObject actual = imp(yaml, yamlPath);
      Object expected = js("""
          {
          filePath: yamlPath,
          id: 'fhir.us.example',
          canonical: 'http://hl7.org/fhir/us/example',
          url: 'http://hl7.org/fhir/us/example/ImplementationGuide/fhir.us.example',
          name: 'ExampleIG',
          title: 'HL7 FHIR Implementation Guide: Example IG Release 1 - US Realm | STU1',
          description: 'Example IG exercises many of the fields in a SUSHI configuration.',
          status: 'active',
          packageId: 'fhir.us.example',
          license: 'CC0-1.0',
          date: '2020-02-26',
          version: '1.0.0',
          fhirVersion: ['4.0.1'],
          template: 'hl7.fhir.template#0.0.5',
          publisher: 'HL7 FHIR Management Group',
          contact: [
          {
          name: 'HL7 FHIR Management Group',
          telecom: [
          { system: 'url', value: 'http://www.hl7.org/Special/committees/fhirmg' },
          { system: 'email', value: 'fmg@lists.HL7.org' }
          ]
          },
          {
          name: 'Bob Smith',
          telecom: [{ system: 'email', value: 'bobsmith@example.org', use: 'work' }]
          }
          ],
          jurisdiction: [
          {
          coding: [
          { code: 'US', system: 'urn:iso:std:iso:3166', display: 'United States of America' }
          ]
          }
          ],
          dependencies: [
          { packageId: 'hl7.fhir.us.core', version: '3.1.0' },
          {
          id: 'mcode',
          packageId: 'hl7.fhir.us.mcode',
          uri: 'http://hl7.org/fhir/us/mcode/ImplementationGuide/hl7.fhir.us.mcode',
          version: '1.0.0'
          },
          {
          id: 'foo',
          packageId: 'hl7.fhir.us.foo',
          version: '1.0.0',
          extension: [
          { url: 'http://example.org/fake/StructureDefinition/bar', valueString: 'bar' }
          ]
          }
          ],
          global: [
          {
          type: 'Patient',
          profile: 'http://example.org/fhir/StructureDefinition/my-patient-profile'
          },
          {
          type: 'Encounter',
          profile: 'http://example.org/fhir/StructureDefinition/my-encounter-profile'
          }
          ],
          definition: {
          extension: [
          {
          url: 'http://example.org/example/ig-definition-ext',
          valueBoolean: true
          }
          ]
          },
          resources: [
          {
          reference: { reference: 'Patient/my-example-patient' },
          name: 'My Example Patient',
          description: 'An example Patient',
          exampleBoolean: true
          },
          { reference: { reference: 'Patient/bad-example' }, omit: true }
          ],
          groups: [
          {
          id: 'GroupA',
          name: 'Group A',
          description: 'The Alpha Group',
          resources: ['StructureDefinition/animal-patient', 'StructureDefinition/arm-procedure']
          },
          {
          id: 'GroupB',
          name: 'Group B',
          description: 'The Beta Group',
          resources: ['StructureDefinition/bark-control', 'StructureDefinition/bee-sting']
          }
          ],
          pages: [
          { nameUrl: 'index.md', title: 'Example Home' },
          { nameUrl: 'implementation.xml' },
          {
          nameUrl: 'examples.xml',
          title: 'Examples Overview',
          extension: [
          {
          url: 'http://example.org/my-extension',
          valueCode: 'example-code'
          }
          ],
          modifierExtension: [
          {
          url: 'http://example.org/my-modifier-extension',
          valueBoolean: true
          }
          ],
          page: [{ nameUrl: 'simpleExamples.xml' }, { nameUrl: 'complexExamples.xml' }]
          }
          ],
          menu: [
          { name: 'Home', url: 'index.html' },
          {
          name: 'Artifacts',
          subMenu: [
          { name: 'Profiles', url: 'artifacts.html#2' },
          { name: 'Extensions', url: 'artifacts.html#3' },
          { name: 'Value Sets', url: 'artifacts.html#4' }
          ]
          },
          { name: 'Downloads', url: 'downloads.html' },
          { name: 'History', url: 'http://hl7.org/fhir/us/example/history.html' },
          {
          name: 'FHIR Spec',
          url: 'http://hl7.org/fhir/R4/index.html',
          openInNewTab: true
          }
          ],
          parameters: [
          { code: 'copyrightyear', value: '2019+' },
          { code: 'releaselabel', value: 'STU1' },
          { code: 'excludettl', value: 'true' },
          { code: 'validation', value: 'allow-any-extensions' },
          { code: 'validation', value: 'no-broken-links' }
          ],
          history: {
          'package-id': 'fhir.us.example',
          canonical: 'http://hl7.org/fhir/us/example',
          title: 'HL7 FHIR Implementation Guide: Example IG Release 1 - US Realm | STU1',
          introduction: 'Example IG exercises many of the fields in a SUSHI configuration.',
          list: [
          {
          version: 'current',
          desc: 'Continuous Integration Build (latest in version control)',
          path: 'https://build.fhir.org/ig/HL7/example-ig/',
          status: 'ci-build',
          current: true
          },
          {
          version: '1.0.0',
          fhirversion: '4.0.1',
          date: '2020-03-06',
          desc: 'STU 1 Release',
          path: 'https://hl7.org/fhir/us/example/STU1/',
          status: 'trial-use',
          sequence: 'STU 1',
          current: true
          },
          {
          version: '0.9.1',
          fhirversion: '4.0.0',
          date: '2019-06-10',
          desc: 'Initial STU ballot (Sep 2019 Ballot)',
          path: 'https://hl7.org/fhir/us/example/2019Sep/',
          status: 'ballot',
          sequence: 'STU 1'
          }
          ]
          },
          indexPageContent: 'Example Index Page Content',
          FSHOnly: false,
          applyExtensionMetadataToRoot: true,
          instanceOptions: { setMetaProfile: 'always', setId: 'always', manualSliceOrdering: true }
          }""", vars);
      assertToEqual(expected, actual);
      assertEquals(0, loggerSpy.getAllLogs("error").size());
      assertEquals(0, loggerSpy.getAllLogs("warn").size());
    }
    @Test
    @DisplayName("importConfiguration should import a config with nearly all properties set")
    void t4() throws Exception {
      // this is "nearly all" because some properties are mutually exclusive when a choice of types is available
      String yamlPath = fixture("fixtures", "maximal-config.yaml");
            vars.put("yamlPath", yamlPath);
      String yaml = read(yamlPath);
      JsObject actual = imp(yaml, yamlPath);
      Object expected = js("""
          {
          filePath: yamlPath,
          id: 'fhir.us.example',
          canonical: 'http://hl7.org/fhir/us/example',
          url: 'http://hl7.org/fhir/us/example/ImplementationGuide/fhir.us.example',
          name: 'ExampleIG',
          title: 'HL7 FHIR Implementation Guide: Example IG Release 1 - US Realm | STU1',
          description: 'Example IG exercises many of the fields in a SUSHI configuration.',
          status: 'active',
          packageId: 'maximal-config-for-test',
          license: 'CC0-1.0',
          date: '2020-02-26',
          version: '1.0.0',
          fhirVersion: ['4.0.1'],
          template: 'hl7.fhir.template#0.0.5',
          templates: [
          {
          code: 'full',
          source: 'hl7.fhir.template'
          }
          ],
          publisher: 'HL7 FHIR Management Group',
          contact: [
          {
          name: 'HL7 FHIR Management Group',
          telecom: [
          { system: 'url', value: 'http://www.hl7.org/Special/committees/fhirmg' },
          { system: 'email', value: 'fmg@lists.HL7.org' }
          ]
          },
          {
          name: 'Bob Smith',
          telecom: [{ system: 'email', value: 'bobsmith@example.org', use: 'work' }]
          }
          ],
          jurisdiction: [
          {
          coding: [
          { code: 'US', system: 'urn:iso:std:iso:3166', display: 'United States of America' }
          ]
          }
          ],
          dependencies: [
          { packageId: 'hl7.fhir.us.core', version: '3.1.0' },
          {
          id: 'mcode',
          packageId: 'hl7.fhir.us.mcode',
          uri: 'http://hl7.org/fhir/us/mcode/ImplementationGuide/hl7.fhir.us.mcode',
          version: '1.0.0'
          }
          ],
          global: [
          {
          type: 'Patient',
          profile: 'http://example.org/fhir/StructureDefinition/my-patient-profile'
          },
          {
          type: 'Encounter',
          profile: 'http://example.org/fhir/StructureDefinition/my-encounter-profile'
          }
          ],
          definition: {
          extension: [
          {
          url: 'http://example.org/example/ig-definition-ext',
          valueBoolean: true
          }
          ]
          },
          resources: [
          {
          reference: { reference: 'Patient/my-example-patient' },
          name: 'My Example Patient',
          description: 'An example Patient',
          exampleBoolean: true
          },
          { reference: { reference: 'Patient/bad-example' }, omit: true }
          ],
          groups: [
          {
          id: 'GroupA',
          name: 'Group A',
          description: 'The Alpha Group',
          resources: ['StructureDefinition/animal-patient', 'StructureDefinition/arm-procedure']
          },
          {
          id: 'GroupB',
          name: 'Group B',
          description: 'The Beta Group',
          resources: ['StructureDefinition/bark-control', 'StructureDefinition/bee-sting']
          }
          ],
          pages: [
          { nameUrl: 'index.md', title: 'Example Home' },
          { nameUrl: 'implementation.xml' },
          {
          nameUrl: 'examples.xml',
          title: 'Examples Overview',
          extension: [
          {
          url: 'http://example.org/my-extension',
          valueCode: 'example-code'
          }
          ],
          modifierExtension: [
          {
          url: 'http://example.org/my-modifier-extension',
          valueBoolean: true
          }
          ],
          page: [{ nameUrl: 'simpleExamples.xml' }, { nameUrl: 'complexExamples.xml' }]
          }
          ],
          meta: {
          versionId: '3.1.4',
          lastUpdated: '2022-08-01',
          source: 'http://hl7.org/fhir/us/example',
          profile: ['http://hl7.org/fhir/us/example/StructureDefinition-IG'],
          security: [{ code: 'unrestricted' }],
          tag: [{ code: 'actionable' }]
          },
          implicitRules: 'http://hl7.org/fhir/us/example/some-rules',
          language: 'EN',
          text: {
          status: 'additional',
          div: '<div>this is additional text</div>'
          },
          contained: [
          {
          text: {
          status: 'generated',
          div: '<div>this text was generated for a contained resource</div>'
          }
          }
          ],
          extension: [
          {
          url: 'http://example.org/another-extension',
          valueCode: 'example-code'
          }
          ],
          modifierExtension: [
          {
          url: 'http://example.org/another-modifier-extension',
          valueBoolean: true
          }
          ],
          experimental: true,
          useContext: [
          {
          code: { code: 'gender' },
          valueQuantity: { value: 3.5, code: 'm', system: 'http://unitsofmeasure.org' }
          }
          ],
          copyright: 'Free for non-commercial use.',
          copyrightLabel: 'Test Example Group, 2023',
          versionAlgorithmCoding: { code: 'semver' },
          menu: [
          { name: 'Home', url: 'index.html' },
          {
          name: 'Artifacts',
          subMenu: [
          { name: 'Profiles', url: 'artifacts.html#2' },
          { name: 'Extensions', url: 'artifacts.html#3' },
          { name: 'Value Sets', url: 'artifacts.html#4' }
          ]
          },
          { name: 'Downloads', url: 'downloads.html' },
          { name: 'History', url: 'http://hl7.org/fhir/us/example/history.html' },
          {
          name: 'FHIR Spec',
          url: 'http://hl7.org/fhir/R4/index.html',
          openInNewTab: true
          }
          ],
          parameters: [
          { code: 'copyrightyear', value: '2019+' },
          { code: 'releaselabel', value: 'STU1' },
          { code: 'excludettl', value: 'true' },
          { code: 'validation', value: 'allow-any-extensions' },
          { code: 'validation', value: 'no-broken-links' }
          ],
          history: {
          'package-id': 'maximal-config-for-test',
          canonical: 'http://hl7.org/fhir/us/example',
          title: 'HL7 FHIR Implementation Guide: Example IG Release 1 - US Realm | STU1',
          introduction: 'Example IG exercises many of the fields in a SUSHI configuration.',
          list: [
          {
          version: 'current',
          desc: 'Continuous Integration Build (latest in version control)',
          path: 'https://build.fhir.org/ig/HL7/example-ig/',
          status: 'ci-build',
          current: true
          },
          {
          version: '1.0.0',
          fhirversion: '4.0.1',
          date: '2020-03-06',
          desc: 'STU 1 Release',
          path: 'https://hl7.org/fhir/us/example/STU1/',
          status: 'trial-use',
          sequence: 'STU 1',
          current: true
          },
          {
          version: '0.9.1',
          fhirversion: '4.0.0',
          date: '2019-06-10',
          desc: 'Initial STU ballot (Sep 2019 Ballot)',
          path: 'https://hl7.org/fhir/us/example/2019Sep/',
          status: 'ballot',
          sequence: 'STU 1'
          }
          ]
          },
          indexPageContent: 'Example Index Page Content',
          FSHOnly: false,
          applyExtensionMetadataToRoot: true,
          instanceOptions: { setMetaProfile: 'always', setId: 'always', manualSliceOrdering: true }
          }""", vars);
      assertToEqual(expected, actual);
      assertEquals(0, loggerSpy.getAllLogs("error").size());
      assertEquals(0, loggerSpy.getAllLogs("warn").size());
    }
    @Test
    @DisplayName("importConfiguration should report a warning when the config contains an unrecognized property")
    void t5() throws Exception {
      // @ts-ignore
      put(minYAML, "cookie", "delicious");
      JsObject actual = imp(minYAML, "test-config.yaml");
      Object expected = js("""
          {
          filePath: 'test-config.yaml',
          id: 'fhir.us.minimal',
          canonical: 'http://hl7.org/fhir/us/minimal',
          url: 'http://hl7.org/fhir/us/minimal/ImplementationGuide/fhir.us.minimal',
          name: 'MinimalIG',
          status: 'draft',
          template: 'hl7.fhir.template#0.0.5',
          version: '1.0.0',
          fhirVersion: ['4.0.1'],
          parameters: [
          { code: 'copyrightyear', value: '2020+' },
          { code: 'releaselabel', value: 'Build CI' }
          ],
          packageId: 'fhir.us.minimal',
          FSHOnly: false,
          applyExtensionMetadataToRoot: true,
          instanceOptions: { setMetaProfile: 'always', setId: 'always', manualSliceOrdering: false }
          }""", vars);
      assertToEqual(expected, actual);
      assertEquals(0, loggerSpy.getAllLogs("error").size());
      assertMatch("Configuration contains unexpected property: cookie. Check that this property is spelled, capitalized, and indented correctly.", loggerSpy.getLastMessage("warn"));
    }
    @Test
    @DisplayName("importConfiguration should report a warning when the config contains multiple unrecognized properties")
    void t6() throws Exception {
      // @ts-ignore
      put(minYAML, "cookie", "delicious");
      // @ts-ignore
      put(minYAML, "index.md", js("""
          {
          title: 'IG Home'
          }""", vars));
      put(minYAML, "definition", js("""
          {
          // @ts-ignore
          resource: [{ reference: { reference: 'Patient/my-example-patient' } }]
          }""", vars));
      JsObject actual = imp(minYAML, "test-config.yaml");
      Object expected = js("""
          {
          filePath: 'test-config.yaml',
          id: 'fhir.us.minimal',
          canonical: 'http://hl7.org/fhir/us/minimal',
          url: 'http://hl7.org/fhir/us/minimal/ImplementationGuide/fhir.us.minimal',
          name: 'MinimalIG',
          status: 'draft',
          template: 'hl7.fhir.template#0.0.5',
          version: '1.0.0',
          fhirVersion: ['4.0.1'],
          parameters: [
          { code: 'copyrightyear', value: '2020+' },
          { code: 'releaselabel', value: 'Build CI' }
          ],
          packageId: 'fhir.us.minimal',
          FSHOnly: false,
          applyExtensionMetadataToRoot: true,
          instanceOptions: { setMetaProfile: 'always', setId: 'always', manualSliceOrdering: false }
          }""", vars);
      assertToEqual(expected, actual);
      assertEquals(0, loggerSpy.getAllLogs("error").size());
      assertEquals(2, loggerSpy.getAllLogs("warn").size());
      assertMatch("Configuration contains unexpected properties: cookie, index.md. Check that these properties are spelled, capitalized, and indented correctly.", loggerSpy.getMessageAtIndex(0, "warn"));
      assertMatch("Configuration property definition contains unexpected property: resource. Only the extension property is allowed under definition. All other definition properties are represented at the top-level of the configuration.", loggerSpy.getMessageAtIndex(1, "warn"));
    }
    @Test
    @DisplayName("importConfiguration should report an error and throw on an invalid YAML config")
    void t7() throws Exception {
      assertThrowsMsg("Invalid configuration YAML", () -> imp("foo", "foo-config.yaml"));
      assertMatch(re("Configuration is not a valid YAML object\\.\\s*File: foo-config\\.yaml"), loggerSpy.getLastMessage("error"));
    }
    @Test
    @DisplayName("importConfiguration should report an error and throw on a YAML file with invalid entries")
    void t8() throws Exception {
      String yamlPath = fixture("fixtures", "invalid-config.yaml");
            vars.put("yamlPath", yamlPath);
      String invalidYaml = read(yamlPath);
      assertThrowsMsg("Invalid configuration YAML", () -> imp(invalidYaml, "invalid-config.yaml"));
      assertMatch(re("Error parsing configuration: Map keys must be unique; \"releaseLabel\" is repeated\\.\\s*File: invalid-config\\.yaml"), loggerSpy.getLastMessage("error"));
    }
    @Nested
    @DisplayName("#id")
    class DId9 {
      @Test
      @DisplayName("importConfiguration #id should import id as-is")
      void t10() throws Exception {
        put(minYAML, "id", "my-id");
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual("my-id", prop(config, "id"));
      }
      @Test
      @DisplayName("importConfiguration #id should report an error and throw if id is missing when FSHOnly is false")
      void t11() throws Exception {
        minYAML.remove("id");
        assertThrowsMsg("Minimal config not met", () -> imp(minYAML, "test-config.yaml"));
        assertMatch(re("SUSHI minimally requires the following configuration properties to generate an IG: canonical, fhirVersion, id, name, status\\.\\s*File: test-config\\.yaml"), loggerSpy.getLastMessage("error"));
      }
      @Test
      @DisplayName("importConfiguration #id should not report an error and throw if id is missing when FSHOnly is true")
      void t12() throws Exception {
        minYAML.remove("id");
        put(minYAML, "FSHOnly", true);
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(Js.UNDEFINED, prop(config, "id"));
        assertEquals(0, loggerSpy.getAllMessages("error").size());
      }
    }
    @Nested
    @DisplayName("#meta")
    class DMeta13 {
      @Test
      @DisplayName("importConfiguration #meta should support fhir syntax for meta coded properties")
      void t14() throws Exception {
        put(minYAML, "meta", js("""
            {
            security: [{ system: 'http://foo.org', code: 'bar', display: 'FooBar' }],
            tag: [{ system: 'http://foo.org', code: 'baz', display: 'FooBaz' }]
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            {
            security: [{ system: 'http://foo.org', code: 'bar', display: 'FooBar' }],
            tag: [{ system: 'http://foo.org', code: 'baz', display: 'FooBaz' }]
            }""", vars), prop(config, "meta"));
      }
      @Test
      @DisplayName("importConfiguration #meta should support FSH syntax for meta coded properties")
      void t15() throws Exception {
        put(minYAML, "meta", js("""
            {
            security: ['http://foo.org#bar "FooBar"'],
            tag: ['http://foo.org#baz "FooBaz"']
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            {
            security: [{ system: 'http://foo.org', code: 'bar', display: 'FooBar' }],
            tag: [{ system: 'http://foo.org', code: 'baz', display: 'FooBaz' }]
            }""", vars), prop(config, "meta"));
      }
      @Test
      @DisplayName("importConfiguration #meta should report invalid FSH syntax for meta coded properties")
      void t16() throws Exception {
        put(minYAML, "meta", js("""
            {
            security: ['foobar'],
            tag: ['foobaz']
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertMatch(re("Invalid code format for meta\\.security: foobar\\s*File: test-config\\.yaml"), loggerSpy.getMessageAtIndex(-2, "error"));
        assertMatch(re("Invalid code format for meta\\.tag: foobaz\\s*File: test-config\\.yaml"), loggerSpy.getLastMessage("error"));
        assertToEqual(js("""
            {}""", vars), prop(config, "meta"));
      }
    }
    @Nested
    @DisplayName("#implicitRules")
    class DImplicitrules17 {
      @Test
      @DisplayName("importConfiguration #implicitRules should import implicitRules as-is")
      void t18() throws Exception {
        put(minYAML, "implicitRules", "http://foo.org/bar");
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual("http://foo.org/bar", prop(config, "implicitRules"));
      }
    }
    @Nested
    @DisplayName("#language")
    class DLanguage19 {
      @Test
      @DisplayName("importConfiguration #language should support string for language")
      void t20() throws Exception {
        put(minYAML, "language", "en");
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual("en", prop(config, "language"));
      }
      @Test
      @DisplayName("importConfiguration #language should support code syntax for language")
      void t21() throws Exception {
        put(minYAML, "language", "#en");
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual("en", prop(config, "language"));
      }
    }
    @Nested
    @DisplayName("#text")
    class DText22 {
      @Test
      @DisplayName("importConfiguration #text should support fhir syntax for text.status")
      void t23() throws Exception {
        put(minYAML, "text", js("""
            {
            status: 'empty',
            div: '<div></div>'
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            {
            status: 'empty',
            div: '<div></div>'
            }""", vars), prop(config, "text"));
      }
      @Test
      @DisplayName("importConfiguration #text should support FSH syntax for text.status")
      void t24() throws Exception {
        put(minYAML, "text", js("""
            {
            status: '#empty',
            div: '<div></div>'
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            {
            status: 'empty',
            div: '<div></div>'
            }""", vars), prop(config, "text"));
      }
      @Test
      @DisplayName("importConfiguration #text should report invalid text.status code")
      void t25() throws Exception {
        put(minYAML, "text", js("""
            {
            // @ts-ignore Type '"whoknows"' is not assignable to type '"empty" | "generated" ...'
            status: 'whoknows',
            div: '<div></div>'
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertMatch(re("Invalid text\\.status value: 'whoknows'\\. Must be one of: 'generated','extensions','additional','empty'\\.\\s*File: test-config\\.yaml"), loggerSpy.getLastMessage("error"));
        assertToEqual(Js.UNDEFINED, prop(config, "text", "status"));
      }
    }
    @Nested
    @DisplayName("#contained")
    class DContained26 {
      @Test
      @DisplayName("importConfiguration #contained should import contained as-is")
      void t27() throws Exception {
        put(minYAML, "contained", js("""
            [
            {
            resourceType: 'Patient',
            id: 'bob',
            active: true
            }
            ]""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            {
            resourceType: 'Patient',
            id: 'bob',
            active: true
            }
            ]""", vars), prop(config, "contained"));
      }
    }
    @Nested
    @DisplayName("#extension")
    class DExtension28 {
      @Test
      @DisplayName("importConfiguration #extension should import extension as-is")
      void t29() throws Exception {
        put(minYAML, "extension", js("""
            [
            {
            url: 'http://extension.org/my-extension',
            // @ts-ignore Type '{ url: string; valueBoolean: boolean; }' is not assignable ...
            valueBoolean: true
            }
            ]""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            {
            url: 'http://extension.org/my-extension',
            valueBoolean: true
            }
            ]""", vars), prop(config, "extension"));
      }
    }
    @Nested
    @DisplayName("#modifierExtension")
    class DModifierextension30 {
      @Test
      @DisplayName("importConfiguration #modifierExtension should import modifierExtension as-is")
      void t31() throws Exception {
        put(minYAML, "modifierExtension", js("""
            [
            {
            url: 'http://extension.org/my-modifier-extension',
            // @ts-ignore Type '{ url: string; valueBoolean: boolean; }' is not assignable ...
            valueBoolean: true
            }
            ]""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            {
            url: 'http://extension.org/my-modifier-extension',
            valueBoolean: true
            }
            ]""", vars), prop(config, "modifierExtension"));
      }
    }
    @Nested
    @DisplayName("#canonical")
    class DCanonical32 {
      @Test
      @DisplayName("importConfiguration #canonical should import canonical as-is")
      void t33() throws Exception {
        put(minYAML, "canonical", "http://foo.org/some-canonical-url");
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual("http://foo.org/some-canonical-url", prop(config, "canonical"));
      }
      @Test
      @DisplayName("importConfiguration #canonical should report an error and throw if canonical is missing and FSHOnly is true")
      void t34() throws Exception {
        minYAML.remove("canonical");
        put(minYAML, "FSHOnly", true);
        assertThrowsMsg("Minimal config not met", () -> imp(minYAML, "test-config.yaml"));
        assertMatch(re("SUSHI minimally requires the following configuration properties to start processing FSH: canonical, fhirVersion\\.\\s*File: test-config\\.yaml"), loggerSpy.getLastMessage("error"));
      }
      @Test
      @DisplayName("importConfiguration #canonical should report an error and throw if canonical is missing and FSHOnly is false")
      void t35() throws Exception {
        minYAML.remove("canonical");
        assertThrowsMsg("Minimal config not met", () -> imp(minYAML, "test-config.yaml"));
        assertMatch(re("SUSHI minimally requires the following configuration properties to generate an IG: canonical, fhirVersion, id, name, status\\.\\s*File: test-config\\.yaml"), loggerSpy.getLastMessage("error"));
      }
    }
    @Nested
    @DisplayName("#url")
    class DUrl36 {
      @Test
      @DisplayName("importConfiguration #url should import url as-is if provided")
      void t37() throws Exception {
        put(minYAML, "url", "http://foo.org/some-url/ImplementationGuide/my.guide");
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual("http://foo.org/some-url/ImplementationGuide/my.guide", prop(config, "url"));
      }
      @Test
      @DisplayName("importConfiguration #url should default url based on canonical if url is not provided")
      void t38() throws Exception {
        minYAML.remove("url");
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(prop(config, "canonical") + "/ImplementationGuide/" + prop(config, "id"), prop(config, "url"));
        assertEquals(0, loggerSpy.getAllMessages("error").size());
      }
    }
    @Nested
    @DisplayName("#version")
    class DVersion39 {
      @Test
      @DisplayName("importConfiguration #version should support a single-component version when YAML parses it as a number")
      void t40() throws Exception {
        // @ts-ignore Type '1' is not assignable to type 'string'
        put(minYAML, "version", 1.0);
        // YAML parse will interpret 1 as a number, not a string
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual("1", prop(config, "version"));
      }
      @Test
      @DisplayName("importConfiguration #version should support a two-component version when YAML parses it as a number")
      void t41() throws Exception {
        put(minYAML, "version", 1.2);
        // YAML parse will interpret 1.2 as a number, not a string
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual("1.2", prop(config, "version"));
      }
    }
    @Nested
    @DisplayName("#name")
    class DName42 {
      @Test
      @DisplayName("importConfiguration #name should import name as-is")
      void t43() throws Exception {
        put(minYAML, "name", "MyIG");
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual("MyIG", prop(config, "name"));
      }
      @Test
      @DisplayName("importConfiguration #name should report an error and throw if name is missing and FSHOnly is false")
      void t44() throws Exception {
        minYAML.remove("name");
        assertThrowsMsg("Minimal config not met", () -> imp(minYAML, "test-config.yaml"));
        assertMatch(re("SUSHI minimally requires the following configuration properties to generate an IG: canonical, fhirVersion, id, name, status\\.\\s*File: test-config\\.yaml"), loggerSpy.getLastMessage("error"));
      }
      @Test
      @DisplayName("importConfiguration #name should not report an error and throw if name is missing and FSHOnly is true")
      void t45() throws Exception {
        minYAML.remove("name");
        put(minYAML, "FSHOnly", true);
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(Js.UNDEFINED, prop(config, "name"));
        assertEquals(0, loggerSpy.getAllMessages("error").size());
      }
    }
    @Nested
    @DisplayName("#title")
    class DTitle46 {
      @Test
      @DisplayName("importConfiguration #title should import title as-is")
      void t47() throws Exception {
        put(minYAML, "title", "My IG");
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual("My IG", prop(config, "title"));
      }
    }
    @Nested
    @DisplayName("#status")
    class DStatus48 {
      @Test
      @DisplayName("importConfiguration #status should support string for status")
      void t49() throws Exception {
        put(minYAML, "status", "draft");
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual("draft", prop(config, "status"));
      }
      @Test
      @DisplayName("importConfiguration #status should support code syntax for status")
      void t50() throws Exception {
        put(minYAML, "status", "#draft");
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual("draft", prop(config, "status"));
      }
      @Test
      @DisplayName("importConfiguration #status should report invalid status code")
      void t51() throws Exception {
        // @ts-ignore Type '"whoknows"' is not assignable to type 'YAMLConfigurationStatus'.
        put(minYAML, "status", "married");
        JsObject config = imp(minYAML, "test-config.yaml");
        assertMatch(re("Invalid status value: 'married'\\. Must be one of: 'draft','active','retired','unknown'\\.\\s*File: test-config\\.yaml"), loggerSpy.getLastMessage("error"));
        assertToEqual(Js.UNDEFINED, prop(config, "status"));
      }
      @Test
      @DisplayName("importConfiguration #status should report an error if status is missing and FSHOnly is false")
      void t52() throws Exception {
        minYAML.remove("status");
        assertThrowsMsg("Minimal config not met", () -> imp(minYAML, "test-config.yaml"));
        assertMatch(re("SUSHI minimally requires the following configuration properties to generate an IG: canonical, fhirVersion, id, name, status\\.\\s*File: test-config\\.yaml"), loggerSpy.getLastMessage("error"));
      }
      @Test
      @DisplayName("importConfiguration #status should export config with status set to default if status is missing and FSHOnly is true")
      void t53() throws Exception {
        minYAML.remove("status");
        put(minYAML, "FSHOnly", true);
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual("draft", prop(config, "status"));
        assertEquals(0, loggerSpy.getAllMessages("error").size());
      }
    }
    @Nested
    @DisplayName("#experimental")
    class DExperimental54 {
      @Test
      @DisplayName("importConfiguration #experimental should import experimental as-is")
      void t55() throws Exception {
        put(minYAML, "experimental", true);
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(true, prop(config, "experimental"));
      }
    }
    @Nested
    @DisplayName("#date")
    class DDate56 {
      @Test
      @DisplayName("importConfiguration #date should support a year-only date when YAML parses it as a number")
      void t57() throws Exception {
        // @ts-ignore Type '2000' is not assignable to type 'string'
        put(minYAML, "date", 2000.0);
        // YAML parse will interpret 2000 as a number, not a string
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual("2000", prop(config, "date"));
      }
    }
    @Nested
    @DisplayName("#publisher")
    class DPublisher58 {
      @Test
      @DisplayName("importConfiguration #publisher should convert single publisher with name only to publisher only")
      void t59() throws Exception {
        put(minYAML, "publisher", js("""
            { name: 'Bob' }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual("Bob", prop(config, "publisher"));
        assertToEqual(Js.UNDEFINED, prop(config, "contact"));
      }
      @Test
      @DisplayName("importConfiguration #publisher should convert single publisher with name and contact info to publisher and contact")
      void t60() throws Exception {
        put(minYAML, "publisher", js("""
            { name: 'Bob', email: 'bob@example.org', url: 'http://bob.example.org' }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual("Bob", prop(config, "publisher"));
        assertToEqual(js("""
            [
            {
            name: 'Bob',
            telecom: [
            { system: 'url', value: 'http://bob.example.org' },
            { system: 'email', value: 'bob@example.org' }
            ]
            }
            ]""", vars), prop(config, "contact"));
      }
      @Test
      @DisplayName("importConfiguration #publisher should convert multiple publishers to publisher and contacts")
      void t61() throws Exception {
        put(minYAML, "publisher", js("""
            [
            { name: 'Bob', email: 'bob@example.org', url: 'http://bob.example.org' },
            { name: 'Sue', email: 'sue@example.org', url: 'http://sue.example.org' }
            ]""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual("Bob", prop(config, "publisher"));
        assertToEqual(js("""
            [
            {
            name: 'Bob',
            telecom: [
            { system: 'url', value: 'http://bob.example.org' },
            { system: 'email', value: 'bob@example.org' }
            ]
            },
            {
            name: 'Sue',
            telecom: [
            { system: 'url', value: 'http://sue.example.org' },
            { system: 'email', value: 'sue@example.org' }
            ]
            }
            ]""", vars), prop(config, "contact"));
      }
    }
    @Nested
    @DisplayName("#contact")
    class DContact62 {
      @Test
      @DisplayName("importConfiguration #contact should convert single-item contact to an array")
      void t63() throws Exception {
        put(minYAML, "contact", js("""
            { name: 'Bob', telecom: [{ system: 'email', value: 'bob@example.com' }] }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            { name: 'Bob', telecom: [{ system: 'email', value: 'bob@example.com' }] }
            ]""", vars), prop(config, "contact"));
      }
      @Test
      @DisplayName("importConfiguration #contact should support contact as an array")
      void t64() throws Exception {
        put(minYAML, "contact", js("""
            [{ name: 'Bob', telecom: [{ system: 'email', value: 'bob@example.com' }] }]""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            { name: 'Bob', telecom: [{ system: 'email', value: 'bob@example.com' }] }
            ]""", vars), prop(config, "contact"));
      }
      @Test
      @DisplayName("importConfiguration #contact should translate codes for contact.telecom.use/system")
      void t65() throws Exception {
        put(minYAML, "contact", js("""
            {
            name: 'Bob',
            telecom: [{ system: '#email', value: 'bob@example.com', use: '#work' }]
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            { name: 'Bob', telecom: [{ system: 'email', value: 'bob@example.com', use: 'work' }] }
            ]""", vars), prop(config, "contact"));
      }
      @Test
      @DisplayName("importConfiguration #contact should report invalid telecom codes")
      void t66() throws Exception {
        put(minYAML, "contact", js("""
            {
            name: 'Bob',
            // @ts-ignore Type ... is not assignable to type ...
            telecom: [{ system: '#carrier-pidgeon', value: 'bob@example.com', use: '#whateva' }]
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertMatch(re("Invalid contact\\.telecom\\.system value: 'carrier-pidgeon'\\. Must be one of: 'phone','fax','email','pager','url','sms','other'\\.\\s*File: test-config\\.yaml"), loggerSpy.getMessageAtIndex(-2, "error"));
        assertMatch(re("Invalid contact\\.telecom\\.use value: 'whateva'\\. Must be one of: 'home','work','temp','old','mobile'\\.\\s*File: test-config\\.yaml"), loggerSpy.getLastMessage("error"));
        assertToEqual(Js.UNDEFINED, prop(config, "contact", 0, "telecom", 0, "system"));
        assertToEqual(Js.UNDEFINED, prop(config, "contact", 0, "telecom", 0, "use"));
      }
      @Test
      @DisplayName("importConfiguration #contact should put contacts after publisher contact details")
      void t67() throws Exception {
        put(minYAML, "publisher", js("""
            { name: 'Bob', email: 'bob@example.org', url: 'http://bob.example.org' }""", vars));
        put(minYAML, "contact", js("""
            {
            name: 'Frank',
            telecom: [{ system: 'email', value: 'frank@example.com' }]
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual("Bob", prop(config, "publisher"));
        assertToEqual(js("""
            [
            {
            name: 'Bob',
            telecom: [
            { system: 'url', value: 'http://bob.example.org' },
            { system: 'email', value: 'bob@example.org' }
            ]
            },
            { name: 'Frank', telecom: [{ system: 'email', value: 'frank@example.com' }] }
            ]""", vars), prop(config, "contact"));
      }
    }
    @Nested
    @DisplayName("#description")
    class DDescription68 {
      @Test
      @DisplayName("importConfiguration #description should copy description as-is")
      void t69() throws Exception {
        put(minYAML, "description", "This is a great IG. Really great. The best.");
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual("This is a great IG. Really great. The best.", prop(config, "description"));
      }
    }
    @Nested
    @DisplayName("#useContext")
    class DUsecontext70 {
      @Test
      @DisplayName("importConfiguration #useContext should convert single-item useContext to an array")
      void t71() throws Exception {
        put(minYAML, "useContext", js("""
            {
            code: {
            system: 'http://terminology.hl7.org/CodeSystem/usage-context-type',
            code: 'gender',
            display: 'Gender'
            },
            valueCodeableConcept: {
            coding: [
            {
            system: 'http://hl7.org/fhir/administrative-gender',
            code: 'female',
            display: 'Female'
            }
            ]
            }
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            {
            code: {
            system: 'http://terminology.hl7.org/CodeSystem/usage-context-type',
            code: 'gender',
            display: 'Gender'
            },
            valueCodeableConcept: {
            coding: [
            {
            system: 'http://hl7.org/fhir/administrative-gender',
            code: 'female',
            display: 'Female'
            }
            ]
            }
            }
            ]""", vars), prop(config, "useContext"));
      }
      @Test
      @DisplayName("importConfiguration #useContext should support useContext as an array")
      void t72() throws Exception {
        put(minYAML, "useContext", js("""
            [
            {
            code: {
            system: 'http://terminology.hl7.org/CodeSystem/usage-context-type',
            code: 'gender',
            display: 'Gender'
            },
            valueCodeableConcept: {
            coding: [
            {
            system: 'http://hl7.org/fhir/administrative-gender',
            code: 'female',
            display: 'Female'
            }
            ]
            }
            }
            ]""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            {
            code: {
            system: 'http://terminology.hl7.org/CodeSystem/usage-context-type',
            code: 'gender',
            display: 'Gender'
            },
            valueCodeableConcept: {
            coding: [
            {
            system: 'http://hl7.org/fhir/administrative-gender',
            code: 'female',
            display: 'Female'
            }
            ]
            }
            }
            ]""", vars), prop(config, "useContext"));
      }
      @Test
      @DisplayName("importConfiguration #useContext should translate codes for code and valueCodeableConcept")
      void t73() throws Exception {
        put(minYAML, "useContext", js("""
            {
            code: 'http://terminology.hl7.org/CodeSystem/usage-context-type#gender "Gender"',
            valueCodeableConcept: 'http://hl7.org/fhir/administrative-gender#female "Female"'
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            {
            code: {
            system: 'http://terminology.hl7.org/CodeSystem/usage-context-type',
            code: 'gender',
            display: 'Gender'
            },
            valueCodeableConcept: {
            coding: [
            {
            system: 'http://hl7.org/fhir/administrative-gender',
            code: 'female',
            display: 'Female'
            }
            ]
            }
            }
            ]""", vars), prop(config, "useContext"));
      }
      @Test
      @DisplayName("importConfiguration #useContext should report invalid FSH syntax for code and valueCodeableConcept")
      void t74() throws Exception {
        put(minYAML, "useContext", js("""
            {
            code: 'gender',
            valueCodeableConcept: 'female'
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertMatch(re("Invalid code format for useContext\\.code: gender\\s*File: test-config\\.yaml"), loggerSpy.getMessageAtIndex(-2, "error"));
        assertMatch(re("Invalid code format for useContext\\.valueCodeableConcept: female\\s*File: test-config\\.yaml"), loggerSpy.getLastMessage("error"));
        assertToEqual(js("""
            [{}]""", vars), prop(config, "useContext"));
      }
      @Test
      @DisplayName("importConfiguration #useContext should translate codes for valueQuantity")
      void t75() throws Exception {
        put(minYAML, "useContext", js("""
            {
            code: 'http://terminology.hl7.org/CodeSystem/usage-context-type#age "Age Range"',
            // technically "age" uses valueRange, but no other contexts used valueQuantity either, so...
            valueQuantity: {
            code: '#a',
            system: 'http://unitsofmeasure.org',
            value: 50,
            comparator: '#>='
            }
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            {
            code: {
            system: 'http://terminology.hl7.org/CodeSystem/usage-context-type',
            code: 'age',
            display: 'Age Range'
            },
            valueQuantity: {
            code: 'a',
            system: 'http://unitsofmeasure.org',
            value: 50,
            comparator: '>='
            }
            }
            ]""", vars), prop(config, "useContext"));
      }
      @Test
      @DisplayName("importConfiguration #useContext should translate quantity for valueQuantity")
      void t76() throws Exception {
        put(minYAML, "useContext", js("""
            {
            code: 'http://terminology.hl7.org/CodeSystem/usage-context-type#age "Age Range"',
            // technically "age" uses valueRange, but no other contexts used valueQuantity either, so...
            valueQuantity: "50 'a'"
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            {
            code: {
            system: 'http://terminology.hl7.org/CodeSystem/usage-context-type',
            code: 'age',
            display: 'Age Range'
            },
            valueQuantity: {
            code: 'a',
            system: 'http://unitsofmeasure.org',
            value: 50
            }
            }
            ]""", vars), prop(config, "useContext"));
      }
      @Test
      @DisplayName("importConfiguration #useContext should report invalid FSH syntax for quantity")
      void t77() throws Exception {
        put(minYAML, "useContext", js("""
            {
            code: 'http://terminology.hl7.org/CodeSystem/usage-context-type#age "Age Range"',
            // technically "age" uses valueRange, but no other contexts used valueQuantity either, so...
            valueQuantity: '50 a' // NOTE: missing '' around unit
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertMatch(re("Invalid useContext\\.valueQuantity value: 50 a\\s*File: test-config\\.yaml"), loggerSpy.getLastMessage("error"));
        assertToEqual(js("""
            [
            {
            code: {
            system: 'http://terminology.hl7.org/CodeSystem/usage-context-type',
            code: 'age',
            display: 'Age Range'
            }
            }
            ]""", vars), prop(config, "useContext"));
      }
      @Test
      @DisplayName("importConfiguration #useContext should report invalid quantity.comparator code")
      void t78() throws Exception {
        put(minYAML, "useContext", js("""
            {
            code: 'http://terminology.hl7.org/CodeSystem/usage-context-type#age "Age Range"',
            // technically "age" uses valueRange, but no other contexts used valueQuantity either, so...
            valueQuantity: {
            code: '#a',
            system: 'http://unitsofmeasure.org',
            value: 50,
            // @ts-ignore Type '"!="' is not assignable to type '"<" | "<=" | ">=" | ">" | "#<" | ...'.
            comparator: '!='
            }
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertMatch(re("Invalid useContext\\.valueQuantity\\.comparator value: '!='\\. Must be one of: '<','<=','>=','>'\\.\\s*File: test-config\\.yaml"), loggerSpy.getLastMessage("error"));
        assertToEqual(js("""
            [
            {
            code: {
            system: 'http://terminology.hl7.org/CodeSystem/usage-context-type',
            code: 'age',
            display: 'Age Range'
            },
            valueQuantity: {
            code: 'a',
            system: 'http://unitsofmeasure.org',
            value: 50
            }
            }
            ]""", vars), prop(config, "useContext"));
      }
      @Test
      @DisplayName("importConfiguration #useContext should translate codes for valueRange")
      void t79() throws Exception {
        put(minYAML, "useContext", js("""
            {
            code: 'http://terminology.hl7.org/CodeSystem/usage-context-type#age "Age Range"',
            valueRange: {
            low: {
            code: '#a',
            system: 'http://unitsofmeasure.org',
            value: 50
            },
            high: {
            code: '#a',
            system: 'http://unitsofmeasure.org',
            value: 65
            }
            }
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            {
            code: {
            system: 'http://terminology.hl7.org/CodeSystem/usage-context-type',
            code: 'age',
            display: 'Age Range'
            },
            valueRange: {
            low: {
            code: 'a',
            system: 'http://unitsofmeasure.org',
            value: 50
            },
            high: {
            code: 'a',
            system: 'http://unitsofmeasure.org',
            value: 65
            }
            }
            }
            ]""", vars), prop(config, "useContext"));
      }
      @Test
      @DisplayName("importConfiguration #useContext should translate quantity for valueQuantity")
      void t80() throws Exception {
        put(minYAML, "useContext", js("""
            {
            code: 'http://terminology.hl7.org/CodeSystem/usage-context-type#age "Age Range"',
            valueRange: { low: "50 'a'", high: "65 'a'" }
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            {
            code: {
            system: 'http://terminology.hl7.org/CodeSystem/usage-context-type',
            code: 'age',
            display: 'Age Range'
            },
            valueRange: {
            low: {
            code: 'a',
            system: 'http://unitsofmeasure.org',
            value: 50
            },
            high: {
            code: 'a',
            system: 'http://unitsofmeasure.org',
            value: 65
            }
            }
            }
            ]""", vars), prop(config, "useContext"));
      }
      @Test
      @DisplayName("importConfiguration #useContext should report invalid FSH syntax for range low / high quantity")
      void t81() throws Exception {
        put(minYAML, "useContext", js("""
            {
            code: 'http://terminology.hl7.org/CodeSystem/usage-context-type#age "Age Range"',
            valueRange: { low: '50 a', high: '65 a' }
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertMatch(re("Invalid useContext\\.valueRange\\.low value: 50 a\\s*File: test-config\\.yaml"), loggerSpy.getMessageAtIndex(-2, "error"));
        assertMatch(re("Invalid useContext\\.valueRange\\.high value: 65 a\\s*File: test-config\\.yaml"), loggerSpy.getLastMessage("error"));
        assertToEqual(js("""
            [
            {
            code: {
            system: 'http://terminology.hl7.org/CodeSystem/usage-context-type',
            code: 'age',
            display: 'Age Range'
            },
            valueRange: {}
            }
            ]""", vars), prop(config, "useContext"));
      }
      @Test
      @DisplayName("importConfiguration #useContext should translate codes for valueReference")
      void t82() throws Exception {
        put(minYAML, "useContext", js("""
            {
            code: 'http://terminology.hl7.org/CodeSystem/usage-context-type#venue "Clinical Venue"',
            valueReference: {
            identifier: {
            use: '#official',
            type: 'http://terminology.hl7.org/CodeSystem/v2-0203#PRN "Provider number"',
            value: '123',
            assigner: {
            identifier: {
            use: '#temp',
            type: 'http://terminology.hl7.org/CodeSystem/v2-0203#TAX "Tax ID number"',
            value: 'abc'
            }
            }
            }
            }
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            {
            code: {
            system: 'http://terminology.hl7.org/CodeSystem/usage-context-type',
            code: 'venue',
            display: 'Clinical Venue'
            },
            valueReference: {
            identifier: {
            use: 'official',
            type: {
            coding: [
            {
            system: 'http://terminology.hl7.org/CodeSystem/v2-0203',
            code: 'PRN',
            display: 'Provider number'
            }
            ]
            },
            value: '123',
            assigner: {
            identifier: {
            use: 'temp',
            type: {
            coding: [
            {
            system: 'http://terminology.hl7.org/CodeSystem/v2-0203',
            code: 'TAX',
            display: 'Tax ID number'
            }
            ]
            },
            value: 'abc'
            }
            }
            }
            }
            }
            ]""", vars), prop(config, "useContext"));
      }
      @Test
      @DisplayName("importConfiguration #useContext should report invalid FSH syntax for identifier.type")
      void t83() throws Exception {
        put(minYAML, "useContext", js("""
            {
            code: 'http://terminology.hl7.org/CodeSystem/usage-context-type#venue "Clinical Venue"',
            valueReference: {
            identifier: {
            use: '#official',
            type: 'PRN',
            value: '123',
            assigner: {
            identifier: {
            use: '#temp',
            type: 'TAX',
            value: 'abc'
            }
            }
            }
            }
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertMatch(re("Invalid code format for useContext\\.valueReference\\.identifier\\.type: PRN\\s*File: test-config\\.yaml"), loggerSpy.getMessageAtIndex(-2, "error"));
        assertMatch(re("Invalid code format for useContext\\.valueReference\\.identifier\\.assigner\\.identifier\\.type: TAX\\s*File: test-config\\.yaml"), loggerSpy.getLastMessage("error"));
        assertToEqual(js("""
            [
            {
            code: {
            system: 'http://terminology.hl7.org/CodeSystem/usage-context-type',
            code: 'venue',
            display: 'Clinical Venue'
            },
            valueReference: {
            identifier: {
            use: 'official',
            value: '123',
            assigner: {
            identifier: {
            use: 'temp',
            value: 'abc'
            }
            }
            }
            }
            }
            ]""", vars), prop(config, "useContext"));
      }
      @Test
      @DisplayName("importConfiguration #useContext should report invalid FSH identifier.use codes")
      void t84() throws Exception {
        put(minYAML, "useContext", js("""
            {
            code: 'http://terminology.hl7.org/CodeSystem/usage-context-type#venue "Clinical Venue"',
            valueReference: {
            identifier: {
            // @ts-ignore Type '"#myob"' is not assignable to type '"temp" | "old" | "#temp" | ...'.
            use: '#myob',
            value: '123',
            assigner: {
            identifier: {
            // @ts-ignore Type '"#the-usual"' is not assignable to type '"temp" | "old" | ...'.
            use: '#the-usual',
            value: 'abc'
            }
            }
            }
            }
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertMatch(re("Invalid useContext\\.valueReference\\.identifier\\.use value: 'myob'\\. Must be one of: 'usual','official','temp','secondary','old'\\.\\s*File: test-config\\.yaml"), loggerSpy.getMessageAtIndex(-2, "error"));
        assertMatch(re("Invalid useContext\\.valueReference\\.identifier\\.assigner\\.identifier\\.use value: 'the-usual'\\. Must be one of: 'usual','official','temp','secondary','old'\\.\\s*File: test-config\\.yaml"), loggerSpy.getLastMessage("error"));
        assertToEqual(js("""
            [
            {
            code: {
            system: 'http://terminology.hl7.org/CodeSystem/usage-context-type',
            code: 'venue',
            display: 'Clinical Venue'
            },
            valueReference: {
            identifier: {
            value: '123',
            assigner: {
            identifier: {
            value: 'abc'
            }
            }
            }
            }
            }
            ]""", vars), prop(config, "useContext"));
      }
      @Test
      @DisplayName("importConfiguration #useContext should report an error if useContext.code is missing")
      void t85() throws Exception {
        // @ts-ignore Type '...' is not assignable to type ...
        put(minYAML, "useContext", js("""
            {
            valueCodeableConcept: {
            coding: [
            {
            system: 'http://hl7.org/fhir/administrative-gender',
            code: 'female',
            display: 'Female'
            }
            ]
            }
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertMatch(re("Configuration missing required property: useContext\\.code\\s*File: test-config\\.yaml"), loggerSpy.getLastMessage("error"));
        assertToEqual(js("""
            [
            {
            valueCodeableConcept: {
            coding: [
            {
            system: 'http://hl7.org/fhir/administrative-gender',
            code: 'female',
            display: 'Female'
            }
            ]
            }
            }
            ]""", vars), prop(config, "useContext"));
      }
      @Test
      @DisplayName("importConfiguration #useContext should report an error if useContext.value[x] is missing")
      void t86() throws Exception {
        // @ts-ignore Type '...' is not assignable to type ...
        put(minYAML, "useContext", js("""
            {
            code: {
            system: 'http://terminology.hl7.org/CodeSystem/usage-context-type',
            code: 'gender',
            display: 'Gender'
            }
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertMatch(re("Configuration missing required property: useContext\\.value\\[x\\]\\s*File: test-config\\.yaml"), loggerSpy.getLastMessage("error"));
        assertToEqual(js("""
            [
            {
            code: {
            system: 'http://terminology.hl7.org/CodeSystem/usage-context-type',
            code: 'gender',
            display: 'Gender'
            }
            }
            ]""", vars), prop(config, "useContext"));
      }
      @Test
      @DisplayName("importConfiguration #useContext should report an error if there is more than one useContext.value[x]")
      void t87() throws Exception {
        // @ts-ignore Type '...' is not assignable to type ...
        put(minYAML, "useContext", js("""
            {
            code: {
            system: 'http://terminology.hl7.org/CodeSystem/usage-context-type',
            code: 'gender',
            display: 'Gender'
            },
            valueCodeableConcept: 'http://hl7.org/fhir/administrative-gender#female "Female"',
            valueQuantity: "50 'a'"
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertMatch(re("Only one useContext.value\\[x\\] is allowed but found multiple: valueCodeableConcept, valueQuantity\\s*File: test-config\\.yaml"), loggerSpy.getLastMessage("error"));
        assertToEqual(js("""
            [
            {
            code: {
            system: 'http://terminology.hl7.org/CodeSystem/usage-context-type',
            code: 'gender',
            display: 'Gender'
            },
            valueCodeableConcept: {
            coding: [
            {
            system: 'http://hl7.org/fhir/administrative-gender',
            code: 'female',
            display: 'Female'
            }
            ]
            },
            valueQuantity: {
            code: 'a',
            system: 'http://unitsofmeasure.org',
            value: 50
            }
            }
            ]""", vars), prop(config, "useContext"));
      }
    }
    @Nested
    @DisplayName("#jurisdiction")
    class DJurisdiction88 {
      @Test
      @DisplayName("importConfiguration #jurisdiction should convert single-item jurisdiction to an array")
      void t89() throws Exception {
        put(minYAML, "jurisdiction", js("""
            {
            coding: [
            {
            code: 'US',
            system: 'urn:iso:std:iso:3166',
            display: 'United States of America'
            }
            ]
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            {
            coding: [
            { code: 'US', system: 'urn:iso:std:iso:3166', display: 'United States of America' }
            ]
            }
            ]""", vars), prop(config, "jurisdiction"));
      }
      @Test
      @DisplayName("importConfiguration #jurisdiction should support jurisdiction as an array")
      void t90() throws Exception {
        put(minYAML, "jurisdiction", js("""
            [
            {
            coding: [
            { code: 'US', system: 'urn:iso:std:iso:3166', display: 'United States of America' }
            ]
            }
            ]""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            {
            coding: [
            { code: 'US', system: 'urn:iso:std:iso:3166', display: 'United States of America' }
            ]
            }
            ]""", vars), prop(config, "jurisdiction"));
      }
      @Test
      @DisplayName("importConfiguration #jurisdiction should translate jurisdiction codes")
      void t91() throws Exception {
        put(minYAML, "jurisdiction", js("""
            ['urn:iso:std:iso:3166#US "United States of America"']""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            {
            coding: [
            { code: 'US', system: 'urn:iso:std:iso:3166', display: 'United States of America' }
            ]
            }
            ]""", vars), prop(config, "jurisdiction"));
      }
      @Test
      @DisplayName("importConfiguration #jurisdiction should report invalid FSH syntax for jurisdiction")
      void t92() throws Exception {
        put(minYAML, "jurisdiction", js("""
            ['merica']""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertMatch(re("Invalid code format for jurisdiction: merica\\s*File: test-config\\.yaml"), loggerSpy.getLastMessage("error"));
        assertToEqual(Js.UNDEFINED, prop(config, "jurisdiction"));
      }
    }
    @Nested
    @DisplayName("#copyright")
    class DCopyright93 {
      @Test
      @DisplayName("importConfiguration #copyright should copy copyright as-is")
      void t94() throws Exception {
        put(minYAML, "copyright", "Copyright Scaly Productions 2020");
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual("Copyright Scaly Productions 2020", prop(config, "copyright"));
      }
    }
    @Nested
    @DisplayName("#copyrightLabel")
    class DCopyrightlabel95 {
      @Test
      @DisplayName("importConfiguration #copyrightLabel should copy copyrightLabel as-is")
      void t96() throws Exception {
        put(minYAML, "copyrightLabel", "Copyright Scaly Productions 2022");
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual("Copyright Scaly Productions 2022", prop(config, "copyrightLabel"));
      }
    }
    @Nested
    @DisplayName("#versionAlgorithm")
    class DVersionalgorithm97 {
      @Test
      @DisplayName("importConfiguration #versionAlgorithm should copy versionAlgorithmString as-is")
      void t98() throws Exception {
        put(minYAML, "versionAlgorithmString", "date");
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual("date", prop(config, "versionAlgorithmString"));
      }
      @Test
      @DisplayName("importConfiguration #versionAlgorithm should support versionAlgorithmCoding as a FSH code")
      void t99() throws Exception {
        put(minYAML, "versionAlgorithmCoding", "http://example.org#semver");
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            {
            code: 'semver',
            system: 'http://example.org'
            }""", vars), prop(config, "versionAlgorithmCoding"));
      }
      @Test
      @DisplayName("importConfiguration #versionAlgorithm should support versionAlgorithmCoding as a coding object")
      void t100() throws Exception {
        put(minYAML, "versionAlgorithmCoding", js("""
            {
            system: 'http://example.org',
            code: 'semver',
            version: '1.0.0'
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            {
            code: 'semver',
            system: 'http://example.org',
            version: '1.0.0'
            }""", vars), prop(config, "versionAlgorithmCoding"));
      }
    }
    @Nested
    @DisplayName("#packageId")
    class DPackageid101 {
      @Test
      @DisplayName("importConfiguration #packageId should use the id as packageId when packageId is not provided")
      void t102() throws Exception {
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual("fhir.us.minimal", prop(config, "packageId"));
      }
      @Test
      @DisplayName("importConfiguration #packageId should use the packageId when it is provided")
      void t103() throws Exception {
        put(minYAML, "packageId", "diff.package.id");
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual("fhir.us.minimal", prop(config, "id"));
        assertToEqual("diff.package.id", prop(config, "packageId"));
      }
    }
    @Nested
    @DisplayName("#license")
    class DLicense104 {
      @Test
      @DisplayName("importConfiguration #license should support string for license")
      void t105() throws Exception {
        put(minYAML, "license", "CC0-1.0");
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual("CC0-1.0", prop(config, "license"));
      }
      @Test
      @DisplayName("importConfiguration #license should support code syntax for license")
      void t106() throws Exception {
        put(minYAML, "license", "#CC0-1.0");
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual("CC0-1.0", prop(config, "license"));
      }
    }
    @Nested
    @DisplayName("#fhirVersion")
    class DFhirversion107 {
      @Test
      @DisplayName("importConfiguration #fhirVersion should support fhirVersion as an array")
      void t108() throws Exception {
        put(minYAML, "fhirVersion", js("""
            ['4.0.1', '3.0.2']""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            ['4.0.1', '3.0.2']""", vars), prop(config, "fhirVersion"));
      }
      @Test
      @DisplayName("importConfiguration #fhirVersion should convert single-item fhirVersion to an array")
      void t109() throws Exception {
        put(minYAML, "fhirVersion", "4.0.1");
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            ['4.0.1']""", vars), prop(config, "fhirVersion"));
      }
      @Test
      @DisplayName("importConfiguration #fhirVersion should support FSH syntax for fhirVersion")
      void t110() throws Exception {
        // because... fhirVersion is actually a code!
        put(minYAML, "fhirVersion", js("""
            ['#4.0.1']""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            ['4.0.1']""", vars), prop(config, "fhirVersion"));
      }
      @Test
      @DisplayName("importConfiguration #fhirVersion should report an error and throw if fhirVersion is missing and FSHOnly is true")
      void t111() throws Exception {
        minYAML.remove("fhirVersion");
        put(minYAML, "FSHOnly", true);
        assertThrowsMsg("Minimal config not met", () -> imp(minYAML, "test-config.yaml"));
        assertMatch(re("SUSHI minimally requires the following configuration properties to start processing FSH: canonical, fhirVersion\\.\\s*File: test-config\\.yaml"), loggerSpy.getLastMessage("error"));
      }
      @Test
      @DisplayName("importConfiguration #fhirVersion should report an error and throw if fhirVersion is an empty array and FSHOnly is true")
      void t112() throws Exception {
        put(minYAML, "fhirVersion", js("""
            []""", vars));
        put(minYAML, "FSHOnly", true);
        assertThrowsMsg("Minimal config not met", () -> imp(minYAML, "test-config.yaml"));
        assertMatch(re("SUSHI minimally requires the following configuration properties to start processing FSH: canonical, fhirVersion\\.\\s*File: test-config\\.yaml"), loggerSpy.getLastMessage("error"));
      }
      @Test
      @DisplayName("importConfiguration #fhirVersion should report an error and throw if fhirVersion is missing and FSHOnly is false")
      void t113() throws Exception {
        minYAML.remove("fhirVersion");
        assertThrowsMsg("Minimal config not met", () -> imp(minYAML, "test-config.yaml"));
        assertMatch(re("SUSHI minimally requires the following configuration properties to generate an IG: canonical, fhirVersion, id, name, status\\.\\s*File: test-config\\.yaml"), loggerSpy.getLastMessage("error"));
      }
      @Test
      @DisplayName("importConfiguration #fhirVersion should report an error and throw if fhirVersion is an empty array and FSHOnly is false")
      void t114() throws Exception {
        put(minYAML, "fhirVersion", js("""
            []""", vars));
        assertThrowsMsg("Minimal config not met", () -> imp(minYAML, "test-config.yaml"));
        assertMatch(re("SUSHI minimally requires the following configuration properties to generate an IG: canonical, fhirVersion, id, name, status\\.\\s*File: test-config\\.yaml"), loggerSpy.getLastMessage("error"));
      }
    }
    @Nested
    @DisplayName("#dependencies")
    class DDependencies115 {
      @Test
      @DisplayName("importConfiguration #dependencies should convert the dependencies map to a list")
      void t116() throws Exception {
        put(minYAML, "dependencies", js("""
            {
            foo: '1.2.3',
            bar: '4.5.6'
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            { packageId: 'foo', version: '1.2.3' },
            { packageId: 'bar', version: '4.5.6' }
            ]""", vars), prop(config, "dependencies"));
      }
      @Test
      @DisplayName("importConfiguration #dependencies should convert the dependencies map to a list when YAML imports version as a number")
      void t117() throws Exception {
        put(minYAML, "dependencies", js("""
            {
            // @ts-ignore Type '1' is not assignable to type 'string'
            foo: 1, // YAML parse will interpret 1 as a number, not a string
            // @ts-ignore Type '2.3' is not assignable to type 'string'
            bar: 2.3 // YAML parse will interpret 2.3 as a number, not a string
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            { packageId: 'foo', version: '1' },
            { packageId: 'bar', version: '2.3' }
            ]""", vars), prop(config, "dependencies"));
      }
      @Test
      @DisplayName("importConfiguration #dependencies should convert uppercase package Ids to lowercase")
      void t118() throws Exception {
        put(minYAML, "dependencies", js("""
            {
            'hl7.ex.PAcKage.iD1': '1.2.3',
            'hl7.ex.package.id2': '4.5.6'
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            { packageId: 'hl7.ex.package.id1', version: '1.2.3' },
            { packageId: 'hl7.ex.package.id2', version: '4.5.6' }
            ]""", vars), prop(config, "dependencies"));
        assertTrue(loggerSpy.getAllMessages("warn").stream().anyMatch(message -> re(
            "hl7.ex.PAcKage.iD1 contains uppercase characters, which is discouraged. SUSHI will use hl7.ex.package.id1 as the package name.")
            .matcher(message).find()));
      }
      @Test
      @DisplayName("importConfiguration #dependencies should convert dependencies to a list with reason and uri and extension when provided")
      void t119() throws Exception {
        put(minYAML, "dependencies", js("""
            {
            foo: {
            id: 'foo',
            uri: 'http://example.org',
            version: '1.0.0',
            reason: 'Foo is always necessary',
            extension: [{ url: 'http://example.org/fake/extension', valueString: 'foo' }]
            }
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            {
            packageId: 'foo',
            id: 'foo',
            version: '1.0.0',
            uri: 'http://example.org',
            reason: 'Foo is always necessary',
            extension: [{ url: 'http://example.org/fake/extension', valueString: 'foo' }]
            }
            ]""", vars), prop(config, "dependencies"));
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
      }
    }
    @Nested
    @DisplayName("#global")
    class DGlobal120 {
      @Test
      @DisplayName("importConfiguration #global should convert the global map to a list")
      void t121() throws Exception {
        put(minYAML, "global", js("""
            {
            Patient: 'http://example.org/fhir/StructureDefinition/my-patient-profile',
            Encounter: 'http://example.org/fhir/StructureDefinition/my-encounter-profile'
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            {
            type: 'Patient',
            profile: 'http://example.org/fhir/StructureDefinition/my-patient-profile'
            },
            {
            type: 'Encounter',
            profile: 'http://example.org/fhir/StructureDefinition/my-encounter-profile'
            }
            ]""", vars), prop(config, "global"));
      }
      @Test
      @DisplayName("importConfiguration #global should convert the global array values to a list items")
      void t122() throws Exception {
        put(minYAML, "global", js("""
            {
            Patient: [
            'http://example.org/fhir/StructureDefinition/my-patient-profile',
            'http://example.org/fhir/StructureDefinition/my-other-patient-profile'
            ],
            Encounter: [
            'http://example.org/fhir/StructureDefinition/my-encounter-profile',
            'http://example.org/fhir/StructureDefinition/my-other-encounter-profile'
            ]
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            {
            type: 'Patient',
            profile: 'http://example.org/fhir/StructureDefinition/my-patient-profile'
            },
            {
            type: 'Patient',
            profile: 'http://example.org/fhir/StructureDefinition/my-other-patient-profile'
            },
            {
            type: 'Encounter',
            profile: 'http://example.org/fhir/StructureDefinition/my-encounter-profile'
            },
            {
            type: 'Encounter',
            profile: 'http://example.org/fhir/StructureDefinition/my-other-encounter-profile'
            }
            ]""", vars), prop(config, "global"));
      }
    }
    @Nested
    @DisplayName("#definition")
    class DDefinition123 {
      @Test
      @DisplayName("importConfiguration #definition should convert the definition extensions to a list")
      void t124() throws Exception {
        put(minYAML, "definition", js("""
            {
            extension: [{ url: 'http://example.org/example/ig-definition-ext', valueBoolean: true }]
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            {
            extension: [
            {
            url: 'http://example.org/example/ig-definition-ext',
            valueBoolean: true
            }
            ]
            }""", vars), prop(config, "definition"));
      }
      @Test
      @DisplayName("importConfiguration #definition should not convert any extra definition properties")
      void t125() throws Exception {
        put(minYAML, "definition", js("""
            {
            extension: [{ url: 'http://example.org/example/ig-definition-ext', valueBoolean: true }],
            // @ts-ignore
            reference: { reference: 'Patient/my-example-patient' }
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            {
            extension: [
            {
            url: 'http://example.org/example/ig-definition-ext',
            valueBoolean: true
            }
            ]
            }""", vars), prop(config, "definition"));
      }
      @Test
      @DisplayName("importConfiguration #definition should not include a definition property if no extensions are provided")
      void t126() throws Exception {
        put(minYAML, "definition", js("""
            {
            // @ts-ignore
            reference: { reference: 'Patient/my-example-patient' }
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(Js.UNDEFINED, prop(config, "definition"));
      }
    }
    @Nested
    @DisplayName("#groups")
    class DGroups127 {
      @Test
      @DisplayName("importConfiguration #groups should convert the groups map to a list")
      void t128() throws Exception {
        put(minYAML, "groups", js("""
            {
            GroupA: {
            name: 'Group A',
            description: 'The Alpha Group',
            resources: ['StructureDefinition/animal-patient', 'StructureDefinition/arm-procedure']
            },
            GroupB: {
            description: 'The Beta Group',
            resources: ['StructureDefinition/bark-control', 'StructureDefinition/bee-sting']
            }
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            {
            id: 'GroupA',
            name: 'Group A',
            description: 'The Alpha Group',
            resources: ['StructureDefinition/animal-patient', 'StructureDefinition/arm-procedure']
            },
            {
            id: 'GroupB',
            name: 'GroupB',
            description: 'The Beta Group',
            resources: ['StructureDefinition/bark-control', 'StructureDefinition/bee-sting']
            }
            ]""", vars), prop(config, "groups"));
      }
    }
    @Nested
    @DisplayName("#resources")
    class DResources129 {
      @Test
      @DisplayName("importConfiguration #resources should convert the resources map to a list")
      void t130() throws Exception {
        put(minYAML, "resources", js("""
            {
            'Patient/my-example-patient': {
            name: 'My Example Patient',
            description: 'An example Patient',
            exampleBoolean: true
            },
            'Patient/my-other-example-patient': {
            name: 'My Other Example Patient',
            description: 'Another example Patient',
            exampleBoolean: true
            }
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            {
            reference: { reference: 'Patient/my-example-patient' },
            name: 'My Example Patient',
            description: 'An example Patient',
            exampleBoolean: true
            },
            {
            reference: { reference: 'Patient/my-other-example-patient' },
            name: 'My Other Example Patient',
            description: 'Another example Patient',
            exampleBoolean: true
            }
            ]""", vars), prop(config, "resources"));
      }
      @Test
      @DisplayName("importConfiguration #resources should support resources.[name].fhirVersion as an array")
      void t131() throws Exception {
        put(minYAML, "resources", js("""
            {
            'Patient/my-example-patient': {
            fhirVersion: ['4.0.1']
            }
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            {
            reference: { reference: 'Patient/my-example-patient' },
            fhirVersion: ['4.0.1']
            }
            ]""", vars), prop(config, "resources"));
      }
      @Test
      @DisplayName("importConfiguration #resources should convert single-item resources.[name].fhirVersion to an array")
      void t132() throws Exception {
        put(minYAML, "resources", js("""
            {
            'Patient/my-example-patient': {
            fhirVersion: '4.0.1'
            }
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            {
            reference: { reference: 'Patient/my-example-patient' },
            fhirVersion: ['4.0.1']
            }
            ]""", vars), prop(config, "resources"));
      }
      @Test
      @DisplayName("importConfiguration #resources should support FSH syntax for resource.fhirVersions")
      void t133() throws Exception {
        put(minYAML, "resources", js("""
            {
            'Patient/my-example-patient': {
            fhirVersion: ['#4.0.1']
            }
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            {
            reference: { reference: 'Patient/my-example-patient' },
            fhirVersion: ['4.0.1']
            }
            ]""", vars), prop(config, "resources"));
      }
      @Test
      @DisplayName("importConfiguration #resources should support resources.[name].profile as an array")
      void t134() throws Exception {
        put(minYAML, "resources", js("""
            {
            'Patient/my-example-patient': {
            profile: ['http://example.org/patient-profile']
            }
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            {
            reference: { reference: 'Patient/my-example-patient' },
            profile: ['http://example.org/patient-profile']
            }
            ]""", vars), prop(config, "resources"));
      }
      @Test
      @DisplayName("importConfiguration #resources should convert single-item resources.[name].profile to an array")
      void t135() throws Exception {
        put(minYAML, "resources", js("""
            {
            'Patient/my-example-patient': {
            profile: 'http://example.org/patient-profile'
            }
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            {
            reference: { reference: 'Patient/my-example-patient' },
            profile: ['http://example.org/patient-profile']
            }
            ]""", vars), prop(config, "resources"));
      }
      @Test
      @DisplayName("importConfiguration #resources should convert omitted resources correctly")
      void t136() throws Exception {
        put(minYAML, "resources", js("""
            {
            'Patient/my-bad-example-patient': 'omit',
            'Patient/my-other-bad-example-patient': '#omit'
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            {
            reference: { reference: 'Patient/my-bad-example-patient' },
            omit: true
            },
            {
            reference: { reference: 'Patient/my-other-bad-example-patient' },
            omit: true
            }
            ]""", vars), prop(config, "resources"));
      }
    }
    @Nested
    @DisplayName("#pages")
    class DPages137 {
      @Test
      @DisplayName("importConfiguration #pages should convert the pages map to a list")
      void t138() throws Exception {
        put(minYAML, "pages", js("""
            {
            'index.md': {
            title: 'Example Home'
            },
            'implementation.xml': null,
            'examples.xml': {
            title: 'Examples Overview',
            'simpleExamples.xml': null,
            'complexExamples.xml': null
            }
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            { nameUrl: 'index.md', title: 'Example Home' },
            { nameUrl: 'implementation.xml' },
            {
            nameUrl: 'examples.xml',
            title: 'Examples Overview',
            page: [{ nameUrl: 'simpleExamples.xml' }, { nameUrl: 'complexExamples.xml' }]
            }
            ]""", vars), prop(config, "pages"));
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
      }
      @Test
      @DisplayName("importConfiguration #pages should support pages with extensions")
      void t139() throws Exception {
        put(minYAML, "pages", js("""
            {
            'index.md': {
            title: 'Example Home'
            },
            'implementation.xml': null,
            'examples.xml': {
            title: 'Examples Overview',
            'simpleExamples.xml': null,
            'complexExamples.xml': null,
            extension: [
            {
            url: 'http://extension.org/my-extension',
            valueBoolean: true
            }
            ],
            modifierExtension: [
            {
            url: 'http://extension.org/my-modifier-extension',
            valueBoolean: true
            }
            ]
            }
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            { nameUrl: 'index.md', title: 'Example Home' },
            { nameUrl: 'implementation.xml' },
            {
            nameUrl: 'examples.xml',
            title: 'Examples Overview',
            extension: [
            {
            url: 'http://extension.org/my-extension',
            valueBoolean: true
            }
            ],
            modifierExtension: [
            {
            url: 'http://extension.org/my-modifier-extension',
            valueBoolean: true
            }
            ],
            page: [{ nameUrl: 'simpleExamples.xml' }, { nameUrl: 'complexExamples.xml' }]
            }
            ]""", vars), prop(config, "pages"));
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
      }
      @Test
      @DisplayName("importConfiguration #pages should support FSH syntax for pages.[name].generation")
      void t140() throws Exception {
        put(minYAML, "pages", js("""
            {
            'index.md': {
            title: 'Example Home'
            },
            'examples.xml': {
            title: 'Examples',
            generation: '#html'
            }
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            { nameUrl: 'index.md', title: 'Example Home' },
            {
            nameUrl: 'examples.xml',
            title: 'Examples',
            generation: 'html'
            }
            ]""", vars), prop(config, "pages"));
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
      }
      @Test
      @DisplayName("importConfiguration #pages should support pages with source[x]")
      void t141() throws Exception {
        put(minYAML, "pages", js("""
            {
            'index.md': {
            title: 'Example Home',
            sourceMarkdown: 'source markdown for index'
            },
            'examples.xml': {
            title: 'Examples',
            sourceUrl: 'http://example.org',
            'simpleExamples.xml': {
            sourceString: 'source of simple examples'
            }
            }
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            { nameUrl: 'index.md', title: 'Example Home', sourceMarkdown: 'source markdown for index' },
            {
            nameUrl: 'examples.xml',
            title: 'Examples',
            sourceUrl: 'http://example.org',
            page: [{ nameUrl: 'simpleExamples.xml', sourceString: 'source of simple examples' }]
            }
            ]""", vars), prop(config, "pages"));
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
      }
      @Test
      @DisplayName("importConfiguration #pages should report invalid generation codes")
      void t142() throws Exception {
        put(minYAML, "pages", js("""
            {
            'index.md': {
            title: 'Example Home',
            // @ts-ignore Type '"electric"' is not assignable to type '"generated" | "#generated" ...'.
            generation: 'electric'
            },
            'examples.xml': {
            title: 'Examples Overview',
            // @ts-ignore Type '{ generation: "gas" }' is not assignable to type string | Extension[] | YAMLConfigurationPage. Types of property generation are incompatible
            'simpleExamples.xml': {
            generation: 'gas'
            }
            }
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertMatch(re("Invalid pages\\[index\\.md\\]\\.generation value: 'electric'\\. Must be one of: 'html','markdown','xml','generated'\\.\\s*File: test-config\\.yaml"), loggerSpy.getMessageAtIndex(-2, "error"));
        assertMatch(re("Invalid pages\\[examples\\.xml\\]\\[simpleExamples\\.xml\\]\\.generation value: 'gas'\\. Must be one of: 'html','markdown','xml','generated'\\.\\s*File: test-config\\.yaml"), loggerSpy.getLastMessage("error"));
        assertToEqual(js("""
            [
            { nameUrl: 'index.md', title: 'Example Home' },
            {
            nameUrl: 'examples.xml',
            title: 'Examples Overview',
            page: [{ nameUrl: 'simpleExamples.xml' }]
            }
            ]""", vars), prop(config, "pages"));
      }
      @Test
      @DisplayName("importConfiguration #pages should generate a warning when a page does not end in .md, .xml, or .html")
      void t143() throws Exception {
        put(minYAML, "pages", js("""
            {
            'index.md': {
            title: 'Example Home'
            },
            'implementation.xml': null,
            menu: {
            Home: 'index.html'
            },
            'examples.xml': {
            title: 'Examples Overview',
            'simpleExamples.xml': null,
            'complexExamples.html': null
            }
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            { nameUrl: 'index.md', title: 'Example Home' },
            { nameUrl: 'implementation.xml' },
            { nameUrl: 'menu', page: [] },
            {
            nameUrl: 'examples.xml',
            title: 'Examples Overview',
            page: [{ nameUrl: 'simpleExamples.xml' }, { nameUrl: 'complexExamples.html' }]
            }
            ]""", vars), prop(config, "pages"));
        assertMatch("Configuration property pages contains unexpected property: menu. Check that this property is spelled, capitalized, and indented correctly. If this is a page, it should end with .md, .xml, or .html.", loggerSpy.getLastMessage("warn"));
      }
      @Test
      @DisplayName("importConfiguration #pages should generate a warning when multiple pages do not end in .md or .xml")
      void t144() throws Exception {
        put(minYAML, "pages", js("""
            {
            'index.md': {
            title: 'Example Home'
            },
            'implementation.xml': null,
            menu: {
            Home: 'index.html'
            },
            'examples.xml': {
            title: 'Examples Overview',
            'simpleExamples.xml': null,
            'complexExamples.xml': null
            },
            // @ts-ignore if this property was indented incorrectly, it could end up here
            license: 'CC0-1.0'
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            { nameUrl: 'index.md', title: 'Example Home' },
            { nameUrl: 'implementation.xml' },
            { nameUrl: 'menu', page: [] },
            {
            nameUrl: 'examples.xml',
            title: 'Examples Overview',
            page: [{ nameUrl: 'simpleExamples.xml' }, { nameUrl: 'complexExamples.xml' }]
            },
            { nameUrl: 'license', page: [] }
            ]""", vars), prop(config, "pages"));
        assertMatch("Configuration property pages contains unexpected properties: menu, license. Check that these properties are spelled, capitalized, and indented correctly. If these are pages, they should end with .md, .xml, or .html.", loggerSpy.getLastMessage("warn"));
      }
      @Test
      @DisplayName("importConfiguration #pages should generate a warning when a property has an unexpected type")
      void t145() throws Exception {
        //@ts-ignore publisher should be an object, but might be set as a string
        put(minYAML, "publisher", "Our Medical Research Team");
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(Js.UNDEFINED, prop(config, "publisher"));
        assertMatch("Configuration property publisher has a value with an unexpected type", loggerSpy.getLastMessage("warn"));
      }
    }
    @Nested
    @DisplayName("#parameters")
    class DParameters146 {
      @Test
      @DisplayName("importConfiguration #parameters should convert the parameters map to a list")
      void t147() throws Exception {
        put(minYAML, "parameters", js("""
            {
            // @ts-ignore Type 'true' is not assignable to type 'string | string[]'.
            excludettl: true,
            validation: 'allow-any-extensions'
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            { code: 'copyrightyear', value: '2020+' },
            { code: 'releaselabel', value: 'Build CI' },
            { code: 'excludettl', value: 'true' },
            { code: 'validation', value: 'allow-any-extensions' }
            ]""", vars), prop(config, "parameters"));
      }
      @Test
      @DisplayName("importConfiguration #parameters should convert parameter array values to list items")
      void t148() throws Exception {
        put(minYAML, "parameters", js("""
            {
            validation: ['allow-any-extensions', 'no-broken-links']
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            { code: 'copyrightyear', value: '2020+' },
            { code: 'releaselabel', value: 'Build CI' },
            { code: 'validation', value: 'allow-any-extensions' },
            { code: 'validation', value: 'no-broken-links' }
            ]""", vars), prop(config, "parameters"));
      }
    }
    @Nested
    @DisplayName("#templates")
    class DTemplates149 {
      @Test
      @DisplayName("importConfiguration #templates should support template as an array")
      void t150() throws Exception {
        // NOTE: I don't know what values would actually be used; I made these up.
        put(minYAML, "templates", js("""
            [
            {
            code: 'page',
            source: 'page.xml',
            scope: 'global'
            }
            ]""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            {
            code: 'page',
            source: 'page.xml',
            scope: 'global'
            }
            ]""", vars), prop(config, "templates"));
      }
      @Test
      @DisplayName("importConfiguration #templates should convert single-item templates to an array")
      void t151() throws Exception {
        // NOTE: I don't know what values would actually be used; I made these up.
        put(minYAML, "templates", js("""
            {
            code: 'page',
            source: 'page.xml',
            scope: 'global'
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            {
            code: 'page',
            source: 'page.xml',
            scope: 'global'
            }
            ]""", vars), prop(config, "templates"));
      }
      @Test
      @DisplayName("importConfiguration #templates should translate template.code if applicable")
      void t152() throws Exception {
        // NOTE: I don't know what values would actually be used; I made these up.
        put(minYAML, "templates", js("""
            [
            {
            code: '#page',
            source: 'page.xml',
            scope: 'global'
            }
            ]""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            {
            code: 'page',
            source: 'page.xml',
            scope: 'global'
            }
            ]""", vars), prop(config, "templates"));
      }
      @Test
      @DisplayName("importConfiguration #templates should report an error if templates.code is missing")
      void t153() throws Exception {
        put(minYAML, "templates", js("""
            [
            // @ts-ignore Property 'code' is missing in type...
            {
            source: 'page.xml',
            scope: 'global'
            }
            ]""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertMatch(re("Configuration missing required property: templates\\.code\\s*File: test-config\\.yaml"), loggerSpy.getLastMessage("error"));
        assertToEqual(js("""
            [
            {
            source: 'page.xml',
            scope: 'global'
            }
            ]""", vars), prop(config, "templates"));
      }
      @Test
      @DisplayName("importConfiguration #templates should report an error if templates.source is missing")
      void t154() throws Exception {
        put(minYAML, "templates", js("""
            [
            // @ts-ignore Property 'source' is missing in type...
            {
            code: 'page',
            scope: 'global'
            }
            ]""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertMatch(re("Configuration missing required property: templates\\.source\\s*File: test-config\\.yaml"), loggerSpy.getLastMessage("error"));
        assertToEqual(js("""
            [
            {
            code: 'page',
            scope: 'global'
            }
            ]""", vars), prop(config, "templates"));
      }
    }
    @Nested
    @DisplayName("#template")
    class DTemplate155 {
      @Test
      @DisplayName("importConfiguration #template should copy template as-is")
      void t156() throws Exception {
        put(minYAML, "template", "hl7.fhir.template#0.0.5");
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual("hl7.fhir.template#0.0.5", prop(config, "template"));
      }
    }
    @Nested
    @DisplayName("#copyrightYear")
    class DCopyrightyear157 {
      @Test
      @DisplayName("importConfiguration #copyrightYear should convert copyrightYear to a parameter")
      void t158() throws Exception {
        put(minYAML, "copyrightYear", "2019+");
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            { code: 'copyrightyear', value: '2019+' }""", vars), prop(config, "parameters", 0));
      }
      @Test
      @DisplayName("importConfiguration #copyrightYear should convert copyrightyear to a parameter")
      void t159() throws Exception {
        minYAML.remove("copyrightYear");
        put(minYAML, "copyrightyear", "2020+");
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            { code: 'copyrightyear', value: '2020+' }""", vars), prop(config, "parameters", 0));
      }
      @Test
      @DisplayName("importConfiguration #copyrightYear should support copyrightYear when YAML imports it as a number")
      void t160() throws Exception {
        // @ts-ignore Type '2020' is not assignable to type 'string'
        put(minYAML, "copyrightYear", 2020.0);
        // YAML parse will interpret 2020 as a number, not a string
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            { code: 'copyrightyear', value: '2020' }""", vars), prop(config, "parameters", 0));
        // @ts-ignore Type '2020' is not assignable to type 'string'
        minYAML.remove("copyrightYear");
        put(minYAML, "copyrightyear", 2020.0);
        // YAML parse will interpret 2020 as a number, not a string
        config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            { code: 'copyrightyear', value: '2020' }""", vars), prop(config, "parameters", 0));
      }
      @Test
      @DisplayName("importConfiguration #copyrightYear should report a warning (not an error) if copyrightYear/copyrightyear is missing and FSHOnly is false")
      void t161() throws Exception {
        minYAML.remove("copyrightYear");
        put(minYAML, "FSHOnly", false);
        JsObject config = imp(minYAML, "test-config.yaml");
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertTrue(loggerSpy.getAllMessages("warn").stream().anyMatch(m -> re("copyrightYear").matcher(m).find()));
        assertToEqual(Js.UNDEFINED, findParameter(config, "copyrightyear"));
      }
      @Test
      @DisplayName("importConfiguration #copyrightYear should not report a copyrightYear warning if copyrightYear/copyrightyear is missing and FSHOnly is true")
      void t162() throws Exception {
        minYAML.remove("copyrightYear");
        put(minYAML, "FSHOnly", true);
        imp(minYAML, "test-config.yaml");
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertFalse(loggerSpy.getAllMessages("warn").stream().anyMatch(m -> re("copyrightYear").matcher(m).find()));
      }
    }
    @Nested
    @DisplayName("#releaseLabel")
    class DReleaselabel163 {
      @Test
      @DisplayName("importConfiguration #releaseLabel should convert releaseLabel to a parameter")
      void t164() throws Exception {
        put(minYAML, "releaseLabel", "STU1");
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            { code: 'releaselabel', value: 'STU1' }""", vars), prop(config, "parameters", 1));
      }
      @Test
      @DisplayName("importConfiguration #releaseLabel should convert releaselabel to a parameter")
      void t165() throws Exception {
        minYAML.remove("releaseLabel");
        put(minYAML, "releaselabel", "STU2");
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            { code: 'releaselabel', value: 'STU2' }""", vars), prop(config, "parameters", 1));
      }
      @Test
      @DisplayName("importConfiguration #releaseLabel should report a warning (not an error) if releaseLabel/releaselabel is missing and FSHOnly is false")
      void t166() throws Exception {
        minYAML.remove("releaseLabel");
        put(minYAML, "FSHOnly", false);
        JsObject config = imp(minYAML, "test-config.yaml");
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertMatch(re("The releaseLabel configuration property is not set\\."), loggerSpy.getLastMessage("warn"));
        assertToEqual(Js.UNDEFINED, findParameter(config, "releaselabel"));
      }
      @Test
      @DisplayName("importConfiguration #releaseLabel should not report a releaseLabel warning if releaseLabel/releaselabel is missing and FSHOnly is true")
      void t167() throws Exception {
        minYAML.remove("releaseLabel");
        put(minYAML, "FSHOnly", true);
        imp(minYAML, "test-config.yaml");
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertFalse(loggerSpy.getAllMessages("warn").stream().anyMatch(m -> re("releaseLabel").matcher(m).find()));
      }
    }
    @Nested
    @DisplayName("#menu")
    class DMenu168 {
      @Test
      @DisplayName("importConfiguration #menu should convert the menu map to a list")
      void t169() throws Exception {
        put(minYAML, "menu", js("""
            {
            Home: 'index.html',
            Artifacts: {
            Profiles: 'artifacts.html#2',
            Extensions: 'artifacts.html#3',
            'Value Sets': 'artifacts.html#4'
            },
            Downloads: 'downloads.html',
            History: 'http://hl7.org/fhir/us/example/history.html',
            'FHIR Spec': 'new-tab external http://hl7.org/fhir/R4/index.html'
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            [
            { name: 'Home', url: 'index.html' },
            {
            name: 'Artifacts',
            subMenu: [
            { name: 'Profiles', url: 'artifacts.html#2' },
            { name: 'Extensions', url: 'artifacts.html#3' },
            { name: 'Value Sets', url: 'artifacts.html#4' }
            ]
            },
            { name: 'Downloads', url: 'downloads.html' },
            { name: 'History', url: 'http://hl7.org/fhir/us/example/history.html' },
            {
            name: 'FHIR Spec',
            url: 'http://hl7.org/fhir/R4/index.html',
            openInNewTab: true
            }
            ]""", vars), prop(config, "menu"));
        assertEquals(1, loggerSpy.getAllMessages("warn").size());
        assertMatch(re("The \"external\" keyword in menu configuration has been deprecated"), loggerSpy.getLastMessage("warn"));
      }
    }
    @Nested
    @DisplayName("#history")
    class DHistory170 {
      @Test
      @DisplayName("importConfiguration #history should use default values for history where applicable")
      void t171() throws Exception {
        put(minYAML, "title", "HL7 FHIR Implementation Guide: Minimal IG Release 1 - US Realm | STU1");
        put(minYAML, "description", "Minimal IG exercises only required fields in a SUSHI configuration.");
        put(minYAML, "history", js("""
            {
            current: 'https://build.fhir.org/ig/HL7/minimal-ig/'
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            {
            'package-id': 'fhir.us.minimal',
            canonical: 'http://hl7.org/fhir/us/minimal',
            title: 'HL7 FHIR Implementation Guide: Minimal IG Release 1 - US Realm | STU1',
            introduction: 'Minimal IG exercises only required fields in a SUSHI configuration.',
            list: [
            {
            version: 'current',
            desc: 'Continuous Integration Build (latest in version control)',
            path: 'https://build.fhir.org/ig/HL7/minimal-ig/',
            status: 'ci-build',
            current: true
            }
            ]
            }""", vars), prop(config, "history"));
      }
      @Test
      @DisplayName("importConfiguration #history should allow mix of default and provided values for history.current")
      void t172() throws Exception {
        put(minYAML, "title", "HL7 FHIR Implementation Guide: Minimal IG Release 1 - US Realm | STU1");
        put(minYAML, "description", "Minimal IG exercises only required fields in a SUSHI configuration.");
        put(minYAML, "history", js("""
            {
            current: {
            fhirversion: '4.0.1',
            date: '2020-04-01',
            path: 'https://build.fhir.org/ig/HL7/example-ig/',
            sequence: 'STU 2'
            }
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            {
            'package-id': 'fhir.us.minimal',
            canonical: 'http://hl7.org/fhir/us/minimal',
            title: 'HL7 FHIR Implementation Guide: Minimal IG Release 1 - US Realm | STU1',
            introduction: 'Minimal IG exercises only required fields in a SUSHI configuration.',
            list: [
            {
            version: 'current',
            fhirversion: '4.0.1',
            date: '2020-04-01',
            desc: 'Continuous Integration Build (latest in version control)',
            path: 'https://build.fhir.org/ig/HL7/example-ig/',
            status: 'ci-build',
            sequence: 'STU 2',
            current: true
            }
            ]
            }""", vars), prop(config, "history"));
      }
      @Test
      @DisplayName("importConfiguration #history should use provided values for history where applicable")
      void t173() throws Exception {
        put(minYAML, "title", "HL7 FHIR Implementation Guide: Minimal IG Release 1 - US Realm | STU1");
        put(minYAML, "description", "Minimal IG exercises only required fields in a SUSHI configuration.");
        put(minYAML, "history", js("""
            {
            'package-id': 'fhir.us.other',
            canonical: 'http://hl7.org/fhir/us/other',
            title: 'HL7 FHIR Implementation Guide: Other IG Release 1 - US Realm | STU1',
            introduction: 'Other IG is other than the other IG.',
            current: {
            fhirversion: '4.0.1',
            date: '2020-04-01',
            desc: 'CI Build Release',
            path: 'https://build.fhir.org/ig/HL7/example-ig/',
            status: 'ci-build',
            sequence: 'STU 2',
            current: true
            },
            '1.0.0': {
            fhirversion: '4.0.1',
            date: '2020-03-06',
            desc: 'STU 1 Release',
            path: 'https://hl7.org/fhir/us/example/STU1/',
            status: 'trial-use',
            sequence: 'STU 1',
            current: true
            },
            '0.9.1': {
            fhirversion: '4.0.0',
            date: '2019-06-10',
            desc: 'Initial STU ballot (Sep 2019 Ballot)',
            path: 'https://hl7.org/fhir/us/example/2019Sep/',
            status: 'ballot',
            sequence: 'STU 1'
            }
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            {
            'package-id': 'fhir.us.other',
            canonical: 'http://hl7.org/fhir/us/other',
            title: 'HL7 FHIR Implementation Guide: Other IG Release 1 - US Realm | STU1',
            introduction: 'Other IG is other than the other IG.',
            list: [
            {
            version: 'current',
            fhirversion: '4.0.1',
            date: '2020-04-01',
            desc: 'CI Build Release',
            path: 'https://build.fhir.org/ig/HL7/example-ig/',
            status: 'ci-build',
            sequence: 'STU 2',
            current: true
            },
            {
            version: '1.0.0',
            fhirversion: '4.0.1',
            date: '2020-03-06',
            desc: 'STU 1 Release',
            path: 'https://hl7.org/fhir/us/example/STU1/',
            status: 'trial-use',
            sequence: 'STU 1',
            current: true
            },
            {
            version: '0.9.1',
            fhirversion: '4.0.0',
            date: '2019-06-10',
            desc: 'Initial STU ballot (Sep 2019 Ballot)',
            path: 'https://hl7.org/fhir/us/example/2019Sep/',
            status: 'ballot',
            sequence: 'STU 1'
            }
            ]
            }""", vars), prop(config, "history"));
      }
      @Test
      @DisplayName("importConfiguration #history should report invalid history.current.status code")
      void t174() throws Exception {
        put(minYAML, "history", js("""
            {
            current: {
            path: 'http://build.fhir.org/ig/HL7/example-ig/',
            // @ts-ignore Type '"OK"' is not assignable to type '"ci-build" | "preview" | ... '.
            status: 'OK'
            }
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertMatch(re("Invalid history\\[current\\]\\.status value: 'OK'\\. Must be one of: 'ci-build','preview','ballot','trial-use','update','normative','trial-use\\+normative'\\.\\s*File: test-config\\.yaml"), loggerSpy.getLastMessage("error"));
        assertToEqual(js("""
            {
            version: 'current',
            desc: 'Continuous Integration Build (latest in version control)',
            path: 'http://build.fhir.org/ig/HL7/example-ig/',
            current: true
            }""", vars), prop(config, "history", "list", 0));
      }
      @Test
      @DisplayName("importConfiguration #history should report an error if history.current is an object and path is missing")
      void t175() throws Exception {
        put(minYAML, "history", js("""
            {
            // @ts-ignore Type '...' is not assignable to type 'YAMLConfigurationHistoryItem'.
            current: {
            fhirversion: '4.0.1'
            }
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertMatch(re("Configuration missing required property: history\\[current]\\.path\\s*File: test-config\\.yaml"), loggerSpy.getLastMessage("error"));
        assertToEqual(js("""
            {
            version: 'current',
            desc: 'Continuous Integration Build (latest in version control)',
            status: 'ci-build',
            fhirversion: '4.0.1',
            current: true
            }""", vars), prop(config, "history", "list", 0));
      }
      @Test
      @DisplayName("importConfiguration #history should report invalid history.[version].status code")
      void t176() throws Exception {
        put(minYAML, "history", js("""
            {
            current: 'https://build.fhir.org/ig/HL7/minimal-ig/',
            '1.0.0': {
            fhirversion: '4.0.1',
            date: '2020-03-06',
            desc: 'STU 1 Release',
            path: 'https://hl7.org/fhir/us/example/STU1/',
            // @ts-ignore Type '"ready"' is not assignable to type '"ci-build" | "preview" | ... '.
            status: 'ready',
            sequence: 'STU 1',
            current: true
            }
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertMatch(re("Invalid history\\[1\\.0\\.0\\]\\.status value: 'ready'\\. Must be one of: 'ci-build','preview','ballot','trial-use','update','normative','trial-use\\+normative'\\.\\s*File: test-config\\.yaml"), loggerSpy.getLastMessage("error"));
        assertToEqual(js("""
            {
            version: '1.0.0',
            date: '2020-03-06',
            desc: 'STU 1 Release',
            path: 'https://hl7.org/fhir/us/example/STU1/',
            sequence: 'STU 1',
            fhirversion: '4.0.1',
            current: true
            }""", vars), prop(config, "history", "list", 1));
      }
      @Test
      @DisplayName("importConfiguration #history should report an error if history.[version].date is missing")
      void t177() throws Exception {
        put(minYAML, "history", js("""
            {
            current: 'https://build.fhir.org/ig/HL7/minimal-ig/',
            '1.0.0': {
            fhirversion: '4.0.1',
            desc: 'STU 1 Release',
            path: 'https://hl7.org/fhir/us/example/STU1/',
            status: 'trial-use',
            sequence: 'STU 1',
            current: true
            }
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertMatch(re("Configuration missing required property: history\\[1\\.0\\.0\\]\\.date\\s*File: test-config\\.yaml"), loggerSpy.getLastMessage("error"));
        assertToEqual(js("""
            {
            version: '1.0.0',
            desc: 'STU 1 Release',
            path: 'https://hl7.org/fhir/us/example/STU1/',
            status: 'trial-use',
            sequence: 'STU 1',
            fhirversion: '4.0.1',
            current: true
            }""", vars), prop(config, "history", "list", 1));
      }
      @Test
      @DisplayName("importConfiguration #history should report an error if history.[version].desc is missing")
      void t178() throws Exception {
        put(minYAML, "history", js("""
            {
            current: 'https://build.fhir.org/ig/HL7/minimal-ig/',
            '1.0.0': {
            fhirversion: '4.0.1',
            date: '2020-03-06',
            path: 'https://hl7.org/fhir/us/example/STU1/',
            status: 'trial-use',
            sequence: 'STU 1',
            current: true
            }
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertMatch(re("Configuration missing required property: history\\[1\\.0\\.0\\]\\.desc\\s*File: test-config\\.yaml"), loggerSpy.getLastMessage("error"));
        assertToEqual(js("""
            {
            version: '1.0.0',
            date: '2020-03-06',
            path: 'https://hl7.org/fhir/us/example/STU1/',
            status: 'trial-use',
            sequence: 'STU 1',
            fhirversion: '4.0.1',
            current: true
            }""", vars), prop(config, "history", "list", 1));
      }
      @Test
      @DisplayName("importConfiguration #history should report an error if history.[version].path is missing")
      void t179() throws Exception {
        put(minYAML, "history", js("""
            {
            current: 'https://build.fhir.org/ig/HL7/minimal-ig/',
            // @ts-ignore Type '...' is not assignable to type ...
            '1.0.0': {
            fhirversion: '4.0.1',
            date: '2020-03-06',
            desc: 'STU 1 Release',
            status: 'trial-use',
            sequence: 'STU 1',
            current: true
            }
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertMatch(re("Configuration missing required property: history\\[1\\.0\\.0\\]\\.path\\s*File: test-config\\.yaml"), loggerSpy.getLastMessage("error"));
        assertToEqual(js("""
            {
            version: '1.0.0',
            date: '2020-03-06',
            desc: 'STU 1 Release',
            status: 'trial-use',
            sequence: 'STU 1',
            fhirversion: '4.0.1',
            current: true
            }""", vars), prop(config, "history", "list", 1));
      }
      @Test
      @DisplayName("importConfiguration #history should report an error if history.[version].status is missing")
      void t180() throws Exception {
        put(minYAML, "history", js("""
            {
            current: 'https://build.fhir.org/ig/HL7/minimal-ig/',
            '1.0.0': {
            fhirversion: '4.0.1',
            date: '2020-03-06',
            desc: 'STU 1 Release',
            path: 'https://hl7.org/fhir/us/example/STU1/',
            sequence: 'STU 1',
            current: true
            }
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertMatch(re("Configuration missing required property: history\\[1\\.0\\.0\\]\\.status\\s*File: test-config\\.yaml"), loggerSpy.getLastMessage("error"));
        assertToEqual(js("""
            {
            version: '1.0.0',
            date: '2020-03-06',
            desc: 'STU 1 Release',
            path: 'https://hl7.org/fhir/us/example/STU1/',
            sequence: 'STU 1',
            fhirversion: '4.0.1',
            current: true
            }""", vars), prop(config, "history", "list", 1));
      }
      @Test
      @DisplayName("importConfiguration #history should report an error if history.[version].sequence is missing")
      void t181() throws Exception {
        put(minYAML, "history", js("""
            {
            current: 'https://build.fhir.org/ig/HL7/minimal-ig/',
            '1.0.0': {
            fhirversion: '4.0.1',
            date: '2020-03-06',
            desc: 'STU 1 Release',
            path: 'https://hl7.org/fhir/us/example/STU1/',
            status: 'trial-use',
            current: true
            }
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertMatch(re("Configuration missing required property: history\\[1\\.0\\.0\\]\\.sequence\\s*File: test-config\\.yaml"), loggerSpy.getLastMessage("error"));
        assertToEqual(js("""
            {
            version: '1.0.0',
            date: '2020-03-06',
            desc: 'STU 1 Release',
            path: 'https://hl7.org/fhir/us/example/STU1/',
            status: 'trial-use',
            fhirversion: '4.0.1',
            current: true
            }""", vars), prop(config, "history", "list", 1));
      }
      @Test
      @DisplayName("importConfiguration #history should report an error if history.[version].fhirVersion is missing")
      void t182() throws Exception {
        put(minYAML, "history", js("""
            {
            current: 'https://build.fhir.org/ig/HL7/minimal-ig/',
            '1.0.0': {
            date: '2020-03-06',
            desc: 'STU 1 Release',
            path: 'https://hl7.org/fhir/us/example/STU1/',
            status: 'trial-use',
            sequence: 'STU 1',
            current: true
            }
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertMatch(re("Configuration missing required property: history\\[1\\.0\\.0\\]\\.fhirVersion\\s*File: test-config\\.yaml"), loggerSpy.getLastMessage("error"));
        assertToEqual(js("""
            {
            version: '1.0.0',
            date: '2020-03-06',
            desc: 'STU 1 Release',
            path: 'https://hl7.org/fhir/us/example/STU1/',
            status: 'trial-use',
            sequence: 'STU 1',
            current: true
            }""", vars), prop(config, "history", "list", 1));
      }
    }
    @Nested
    @DisplayName("#indexPageContent")
    class DIndexpagecontent183 {
      @Test
      @DisplayName("importConfiguration #indexPageContent should copy indexPageContent as-is")
      void t184() throws Exception {
        put(minYAML, "indexPageContent", "This is a great index. Really great. The best.");
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual("This is a great index. Really great. The best.", prop(config, "indexPageContent"));
      }
    }
    @Nested
    @DisplayName("#FSHOnly")
    class DFshonly185 {
      @Test
      @DisplayName("importConfiguration #FSHOnly should copy FSHOnly as-is")
      void t186() throws Exception {
        put(minYAML, "FSHOnly", true);
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(true, prop(config, "FSHOnly"));
      }
      @Test
      @DisplayName("importConfiguration #FSHOnly should default FSHOnly to false when not specified")
      void t187() throws Exception {
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(false, prop(config, "FSHOnly"));
      }
      @Test
      @DisplayName("importConfiguration #FSHOnly should report a warning if FSHOnly is true and unused properties are given")
      void t188() throws Exception {
        put(minYAML, "menu", js("""
            { Home: 'index.html' }""", vars));
        put(minYAML, "contained", js("""
            [{ resourceType: 'Patient' }]""", vars));
        put(minYAML, "FSHOnly", true);
        JsObject config = imp(minYAML, "test-config.yaml");
        assertMatch(re("The following properties are unused and only relevant for IG creation: id, name, copyrightYear, releaseLabel, template, menu, contained\\..*File: test-config.yaml", "s"), loggerSpy.getLastMessage("warn"));
        assertToEqual(true, prop(config, "FSHOnly"));
      }
    }
    @Nested
    @DisplayName("#instanceOptions")
    class DInstanceoptions189 {
      @Test
      @DisplayName("importConfiguration #instanceOptions should use default values for instanceOptions where applicable")
      void t190() throws Exception {
        put(minYAML, "instanceOptions", Js.UNDEFINED);
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            {
            setMetaProfile: 'always',
            setId: 'always',
            manualSliceOrdering: false
            }""", vars), prop(config, "instanceOptions"));
      }
      @Test
      @DisplayName("importConfiguration #instanceOptions should use provided values for instanceOptions where applicable")
      void t191() throws Exception {
        put(minYAML, "instanceOptions", js("""
            {
            setMetaProfile: 'never',
            setId: 'standalone-only',
            manualSliceOrdering: true
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            {
            setMetaProfile: 'never',
            setId: 'standalone-only',
            manualSliceOrdering: true
            }""", vars), prop(config, "instanceOptions"));
      }
      @Test
      @DisplayName("importConfiguration #instanceOptions should report invalid instanceOptions.setMetaProfile code")
      void t192() throws Exception {
        put(minYAML, "instanceOptions", js("""
            {
            // @ts-ignore
            setMetaProfile: 'foo',
            setId: 'standalone-only',
            manualSliceOrdering: false
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            {
            setMetaProfile: 'always',
            setId: 'standalone-only',
            manualSliceOrdering: false
            }""", vars), prop(config, "instanceOptions"));
        assertMatch(re("Invalid instanceOptions\\.setMetaProfile value: 'foo'\\. Must be one of: 'always','never','inline-only','standalone-only'\\.\\s*File: test-config\\.yaml"), loggerSpy.getLastMessage("error"));
      }
      @Test
      @DisplayName("importConfiguration #instanceOptions should report invalid instanceOptions.setId code")
      void t193() throws Exception {
        put(minYAML, "instanceOptions", js("""
            {
            setMetaProfile: 'never',
            // @ts-ignore
            setId: 'foo',
            manualSliceOrdering: false
            }""", vars));
        JsObject config = imp(minYAML, "test-config.yaml");
        assertToEqual(js("""
            {
            setMetaProfile: 'never',
            setId: 'always',
            manualSliceOrdering: false
            }""", vars), prop(config, "instanceOptions"));
        assertMatch(re("Invalid instanceOptions\\.setId value: 'foo'\\. Must be one of: 'always','standalone-only'\\.\\s*File: test-config\\.yaml"), loggerSpy.getLastMessage("error"));
      }
    }
  }
}
