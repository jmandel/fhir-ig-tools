package org.hl7.fhir.sushi.ig;

import static org.hl7.fhir.sushi.testhelpers.TsTestUtil.*;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.*;
import static org.hl7.fhir.sushi.testhelpers.NodeTestEnv.*;
import static org.hl7.fhir.sushi.testhelpers.NodeTestEnv.*;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.getTestFHIRDefinitions;
import static org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.testDefsPath;
import static org.junit.jupiter.api.Assertions.*;
import static org.hl7.fhir.sushi.importer.config.ImportConfiguration.importConfiguration;

import java.util.*;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.errors.*;
import org.hl7.fhir.sushi.export.Package;
import org.hl7.fhir.sushi.export.Package.FshMapEntry;
import org.hl7.fhir.sushi.fhirdefs.FHIRDefinitions;
import org.hl7.fhir.sushi.fhirtypes.*;
import org.hl7.fhir.sushi.fshtypes.*;
import org.hl7.fhir.sushi.fshtypes.rules.*;
import org.hl7.fhir.sushi.importer.FSHDocument;
import org.hl7.fhir.sushi.importer.FSHTank;
import org.hl7.fhir.sushi.js.*;
import org.hl7.fhir.sushi.testhelpers.*;
import org.hl7.fhir.sushi.utils.*;
import org.junit.jupiter.api.*;

// port of sushi/test/ig/IGExporter.test.ts (SUSHI 3.21.1)
@DisplayName("IGExporter")
class IGExporterTest {
  static final String __dirname = dirname("ig");

  @DisplayName("#minimal-config")
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class MinimalConfigTests {
    String tempOut;

    @BeforeAll
    void beforeAll() {
      loggerSpy.reset();
      tempOut = temp.mkdirSync("sushi-test");
      var configPath = path.join(__dirname, "..", "import", "fixtures", "minimal-config.yaml");
      var configYaml = fs.readFileSync(configPath, "utf8");
      var config = importConfiguration(configYaml, configPath);
      Package pkg = new Package(config);
      var defs = getTestFHIRDefinitions(true, testDefsPath("r4-definitions"));
      IGExporter exporter = new IGExporter(pkg, defs, __dirname);
      exporter.export(tempOut);
    }

    @AfterAll
    void afterAll() {
      temp.cleanupSync();
    }

    @Test
    @DisplayName("IGExporter #minimal-config should export default files when no configuration values affect those files")
    void shouldExportDefaultFilesWhenNoConfigurationValuesAffectThoseFiles() {
      // defaults are:
      // no package-list.json
      var packageListPath = path.join(tempOut, "package-list.json");
      assertFalse(Js.truthy(fs.existsSync(packageListPath)));
      // no menu.xml
      var menuPath = path.join(tempOut, "input", "includes", "menu.xml");
      assertFalse(Js.truthy(fs.existsSync(menuPath)));
      // no ig.ini
      var igIniPath = path.join(tempOut, "ig.ini");
      assertFalse(Js.truthy(fs.existsSync(igIniPath)));
      // generated implementation guide
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
    }

    @Test
    @DisplayName("IGExporter #minimal-config should put default content into ImplementationGuide file")
    void shouldPutDefaultContentIntoImplementationGuideFile() {
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      var igContent = fs.readJSONSync(igPath);
      assertEquals("fhir.us.minimal", at(igContent, "id"));
      assertNull(at(igContent, "definition.extension"));
      assertEquals(0, len(at(igContent, "definition.resource")));
    }

  }
  @DisplayName("#additional-config")
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class AdditionalConfig {
    String tempOut;

    @BeforeAll
    void beforeAll() {
      loggerSpy.reset();
      tempOut = temp.mkdirSync("sushi-test");
      var configPath = path.join(__dirname, "..", "import", "fixtures", "example-config.yaml");
      var configYaml = fs.readFileSync(configPath, "utf8");
      var config = importConfiguration(configYaml, configPath);
      Package pkg = new Package(config);
      var defs = getTestFHIRDefinitions(true, testDefsPath("r4-definitions"));
      IGExporter exporter = new IGExporter(pkg, defs, __dirname);
      exporter.export(tempOut);
    }

    @AfterAll
    void afterAll() {
      temp.cleanupSync();
    }

    @Test
    @DisplayName("IGExporter #additional-config should add elements to the implementation guide definition based upon configuration")
    void shouldAddElementsToTheImplementationGuideDefinitionBasedUponConfiguration() {
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.example.json");
      var igContent = fs.readJSONSync(igPath);
      assertJsonEqual("""
        [
          {
            "url": "http://example.org/example/ig-definition-ext",
            "valueBoolean": true
          }
        ]
        """, at(igContent, "definition.extension"));
      assertEquals(2, len(at(igContent, "definition.grouping")));
      assertEquals("GroupA", at(igContent, "definition.grouping[0].id"));
      assertEquals("Group A", at(igContent, "definition.grouping[0].name"));
      assertEquals(1, len(at(igContent, "definition.resource")));
      assertEquals("My Example Patient", at(igContent, "definition.resource[0].name"));
      assertEquals(3, len(at(igContent, "definition.page.page")));
      assertEquals("Example Home", at(igContent, "definition.page.page[0].title"));
      assertJsonEqual("""
        [
          {
            "url": "http://example.org/my-extension",
            "valueCode": "example-code"
          }
        ]
        """, at(igContent, "definition.page.page[2].extension"));
      assertJsonEqual("""
        [
          {
            "url": "http://example.org/my-modifier-extension",
            "valueBoolean": true
          }
        ]
        """, at(igContent, "definition.page.page[2].modifierExtension"));
      // one each for releaselabel and copyrightyear, three configured parameters, and one for the history
      assertEquals(6, len(at(igContent, "definition.parameter")));
      assertJsonEqual("""
        {
          "code": "excludettl",
          "value": "true"
        }
        """, at(igContent, "definition.parameter[2]"));
    }

    @Test
    @DisplayName("IGExporter #additional-config should create menu.xml based upon configuration")
    void shouldCreateMenuXmlBasedUponConfiguration() {
      var menuPath = path.join(tempOut, "fsh-generated", "includes", "menu.xml");
      assertTrue(Js.truthy(fs.existsSync(menuPath)));
      var menuContent = fs.readFileSync(menuPath, "utf8");
      assertMatches("Artifacts.*Profiles.*Extensions.*Value Sets.*Downloads.*History", "s", (String) menuContent);
    }

    @Test
    @DisplayName("IGExporter #additional-config should not create package-list.json based upon configuration")
    void shouldNotCreatePackageListJsonBasedUponConfiguration() {
      var packageListPath = path.join(tempOut, "package-list.json");
      assertFalse(Js.truthy(fs.existsSync(packageListPath)));
    }

    @Test
    @DisplayName("IGExporter #additional-config should not create ig.ini based upon configuration")
    void shouldNotCreateIgIniBasedUponConfiguration() {
      var igIniPath = path.join(tempOut, "ig.ini");
      assertFalse(Js.truthy(fs.existsSync(igIniPath)));
    }

  }
  @DisplayName("#ig-name")
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class IgName {
    String tempOut;
    FHIRDefinitions defs;
    String configPath;
    String configYaml;

    @BeforeAll
    void beforeAll() {
      defs = getTestFHIRDefinitions(true, testDefsPath("r4-definitions"));
      configPath = path.join(__dirname, "..", "import", "fixtures", "minimal-config.yaml");
      configYaml = fs.readFileSync(configPath, "utf8");
    }

    @BeforeEach
    void beforeEach() {
      loggerSpy.reset();
      tempOut = temp.mkdirSync("sushi-test");
    }

    @AfterEach
    void afterEach() {
      temp.cleanupSync();
    }

    Object exportIGWithName(String name) {
      var config = importConfiguration(configYaml, configPath);
      config.name = name;
      Package pkg = new Package(config);
      IGExporter exporter = new IGExporter(pkg, defs, __dirname);
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      return fs.readJSONSync(igPath);
    }

    boolean hasNameConstraintWarning() {
      return loggerSpy.getAllMessages("warn").stream().anyMatch(m -> m.contains("[A-Z]([A-Za-z0-9_]){0,254}"));
    }

    @Test
    @DisplayName("IGExporter #ig-name should keep an authored IG name that contains a space and warn about the FHIR name constraint")
    void shouldKeepAnAuthoredIGNameThatContainsASpaceAndWarnAboutTheFHIRNameConstraint() {
      var igContent = exportIGWithName("Minimal IG");
      assertEquals("Minimal IG", at(igContent, "name"));
      assertEquals(true, hasNameConstraintWarning());
    }

    @Test
    @DisplayName("IGExporter #ig-name should keep a valid IG name and not warn about the FHIR name constraint")
    void shouldKeepAValidIGNameAndNotWarnAboutTheFHIRNameConstraint() {
      var igContent = exportIGWithName("MinimalIG");
      assertEquals("MinimalIG", at(igContent, "name"));
      assertEquals(false, hasNameConstraintWarning());
    }

    @Test
    @DisplayName("IGExporter #ig-name should keep an authored IG name with a hyphen or leading lowercase letter and warn about the FHIR name constraint")
    void shouldKeepAnAuthoredIGNameWithAHyphenOrLeadingLowercaseLetterAndWarnAboutTheFHIR() {
      var igContent = exportIGWithName("example-ig");
      assertEquals("example-ig", at(igContent, "name"));
      assertEquals(true, hasNameConstraintWarning());
    }

  }
}
