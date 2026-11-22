package org.hl7.fhir.sushi.ig;

import static org.hl7.fhir.sushi.testhelpers.TsTestUtil.*;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.*;
import static org.hl7.fhir.sushi.testhelpers.NodeTestEnv.*;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.getTestFHIRDefinitions;
import static org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.testDefsPath;
import static org.junit.jupiter.api.Assertions.*;

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

// port of sushi/test/ig/IGExporter.ig-ini.test.ts (SUSHI 3.21.1)
@DisplayName("IGExporter")
class IGExporterIgIniTest {

  // Track temp files/folders for cleanup
  static final String __dirname = dirname("ig");
  @DisplayName("#ig-ini")
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class IgIni {
    Configuration configWithTemplate;
    String tempOut;

    @BeforeEach
    void beforeEach() {
      tempOut = temp.mkdirSync("sushi-test");
      configWithTemplate = MinimalConfig.minimalConfig();
      configWithTemplate.template = "hl7.fhir.template#0.0.5";
      loggerSpy.reset();
    }

    @AfterEach
    void afterEach() {
      temp.cleanupSync();
    }

    @Test
    @DisplayName("IGExporter #ig-ini should do nothing when template is undefined and ig.ini is not provided")
    void shouldDoNothingWhenTemplateIsUndefinedAndIgIniIsNotProvided() {
      Package pkg = new Package(MinimalConfig.minimalConfig());
      var igDataPath = path.resolve(__dirname, "fixtures", "simple-ig");
      IGExporter exporter = new IGExporter(pkg, null, igDataPath);
      exporter.checkIgIni();
      var igIniPath = path.join(tempOut, "ig.ini");
      assertFalse(Js.truthy(fs.existsSync(igIniPath)));
      assertEquals(0, loggerSpy.getAllMessages().size());
    }

    @Test
    @DisplayName("IGExporter #ig-ini should error if there is an ig.ini file and template is defined in the config and not generate or copy an ig.ini")
    void shouldErrorIfThereIsAnIgIniFileAndTemplateIsDefinedInTheConfigAndNotGenerateOrCo() {
      Package pkg = new Package(configWithTemplate);
      var igDataPath = path.resolve(__dirname, "fixtures", "customized-ig");
      IGExporter exporter = new IGExporter(pkg, null, igDataPath);
      exporter.checkIgIni();
      var igIniPath = path.join(tempOut, "ig.ini");
      assertFalse(Js.truthy(fs.existsSync(igIniPath)));
      assertEquals(1, loggerSpy.getAllMessages().size());
      assertContainsText("Found both a \"template\" property in sushi-config.yaml and an ig.ini file at customized-ig" + path.sep + "ig.ini.", loggerSpy.getLastMessage("error"));
      assertContainsText("The \"template\" property in sushi-config.yaml is no longer supported and will be ignored", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("IGExporter #ig-ini should error if there is no ig.ini file and template is defined in the config (and not generate an ig.ini)")
    void shouldErrorIfThereIsNoIgIniFileAndTemplateIsDefinedInTheConfigAndNotGenerateAnIg() {
      Package pkg = new Package(configWithTemplate);
      var igDataPath = path.resolve(__dirname, "fixtures", "customized-ig-with-resources");
      IGExporter exporter = new IGExporter(pkg, null, igDataPath);
      exporter.checkIgIni();
      var igIniPath = path.join(tempOut, "ig.ini");
      assertFalse(Js.truthy(fs.existsSync(igIniPath)));
      assertEquals(1, loggerSpy.getAllMessages().size());
      assertContainsText("create an ig.ini file in your project folder", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("IGExporter #ig-ini should use user-provided ig.ini when template is not defined")
    void shouldUseUserProvidedIgIniWhenTemplateIsNotDefined() {
      Package pkg = new Package(MinimalConfig.minimalConfig());
      var igDataPath = path.resolve(__dirname, "fixtures", "customized-ig");
      IGExporter exporter = new IGExporter(pkg, null, igDataPath);
      exporter.checkIgIni();
      var igIniPath = path.join(tempOut, "ig.ini");
      assertFalse(Js.truthy(fs.existsSync(igIniPath)));
      assertEquals(0, loggerSpy.getAllMessages().size());
    }

    @Test
    @DisplayName("IGExporter #ig-ini should use user-provided ig.ini with local template when template is not defined")
    void shouldUseUserProvidedIgIniWithLocalTemplateWhenTemplateIsNotDefined() {
      Package pkg = new Package(MinimalConfig.minimalConfig());
      var igDataPath = path.resolve(__dirname, "fixtures", "customized-ig-with-local-template");
      IGExporter exporter = new IGExporter(pkg, null, igDataPath);
      exporter.checkIgIni();
      var igIniPath = path.join(tempOut, "ig.ini");
      assertFalse(Js.truthy(fs.existsSync(igIniPath)));
      assertEquals(0, loggerSpy.getAllMessages().size());
    }

    @Test
    @DisplayName("IGExporter #ig-ini should log an error when ig.ini does not have an [IG] section")
    void shouldLogAnErrorWhenIgIniDoesNotHaveAnIGSection() {
      Package pkg = new Package(MinimalConfig.minimalConfig());
      var igDataPath = path.resolve(__dirname, "fixtures", "ig-ini-missing-ig-section");
      IGExporter exporter = new IGExporter(pkg, null, igDataPath);
      exporter.checkIgIni();
      var igIniPath = path.join(tempOut, "ig.ini");
      assertFalse(Js.truthy(fs.existsSync(igIniPath)));
      assertEquals(1, loggerSpy.getAllMessages().size());
      assertContainsText("The ig.ini file must contain an [IG] section.", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("IGExporter #ig-ini should not log an error or warning when ig.ini has additional sections")
    void shouldNotLogAnErrorOrWarningWhenIgIniHasAdditionalSections() {
      Package pkg = new Package(MinimalConfig.minimalConfig());
      var igDataPath = path.resolve(__dirname, "fixtures", "ig-ini-with-extra-sections");
      IGExporter exporter = new IGExporter(pkg, null, igDataPath);
      exporter.checkIgIni();
      var igIniPath = path.join(tempOut, "ig.ini");
      assertFalse(Js.truthy(fs.existsSync(igIniPath)));
      assertEquals(0, loggerSpy.getAllMessages().size());
    }

    @Test
    @DisplayName("IGExporter #ig-ini should log a warning when ig property in ig.ini does not point to SUSHI IG JSON")
    void shouldLogAWarningWhenIgPropertyInIgIniDoesNotPointToSUSHIIGJSON() {
      Package pkg = new Package(MinimalConfig.minimalConfig());
      var igDataPath = path.resolve(__dirname, "fixtures", "ig-ini-with-non-generated-ig");
      IGExporter exporter = new IGExporter(pkg, null, igDataPath);
      exporter.checkIgIni();
      var igIniPath = path.join(tempOut, "ig.ini");
      assertFalse(Js.truthy(fs.existsSync(igIniPath)));
      assertEquals(1, loggerSpy.getAllMessages().size());
      assertContainsText("Your ig.ini file does NOT point to the Implementation Guide resource that SUSHI generates.", loggerSpy.getLastMessage("warn"));
      assertMatches("'ig = fsh-generated\\/resources\\/ImplementationGuide-fhir\\.us\\.minimal\\.json'", "", loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("IGExporter #ig-ini should not log a warning when ig property in ig.ini is full path to SUSHI IG JSON")
    void shouldNotLogAWarningWhenIgPropertyInIgIniIsFullPathToSUSHIIGJSON() {
      Package pkg = new Package(MinimalConfig.minimalConfig());
      var igDataPath = path.resolve(__dirname, "fixtures", "ig-ini-with-full-ig-path");
      IGExporter exporter = new IGExporter(pkg, null, igDataPath);
      exporter.checkIgIni();
      var igIniPath = path.join(tempOut, "ig.ini");
      assertFalse(Js.truthy(fs.existsSync(igIniPath)));
      assertEquals(0, loggerSpy.getAllMessages().size());
    }

    @Test
    @DisplayName("IGExporter #ig-ini should not log a warning when ig property in ig.ini is full windows path to SUSHI IG JSON")
    void shouldNotLogAWarningWhenIgPropertyInIgIniIsFullWindowsPathToSUSHIIGJSON() {
      Package pkg = new Package(MinimalConfig.minimalConfig());
      var igDataPath = path.resolve(__dirname, "fixtures", "ig-ini-with-full-win-ig-path");
      IGExporter exporter = new IGExporter(pkg, null, igDataPath);
      exporter.checkIgIni();
      var igIniPath = path.join(tempOut, "ig.ini");
      assertFalse(Js.truthy(fs.existsSync(igIniPath)));
      assertEquals(0, loggerSpy.getAllMessages().size());
    }

    @Test
    @DisplayName("IGExporter #ig-ini should log an error when missing required properties and not copy provided ig.ini when template is not defined")
    void shouldLogAnErrorWhenMissingRequiredPropertiesAndNotCopyProvidedIgIniWhenTemplate() {
      Package pkg = new Package(MinimalConfig.minimalConfig());
      var igDataPath = path.resolve(__dirname, "fixtures", "ig-ini-missing-properties");
      IGExporter exporter = new IGExporter(pkg, null, igDataPath);
      exporter.checkIgIni();
      var igIniPath = path.join(tempOut, "ig.ini");
      assertFalse(Js.truthy(fs.existsSync(igIniPath)));
      assertEquals(2, loggerSpy.getAllMessages().size());
      assertContainsText("The ig.ini file must have an \"ig\" property", loggerSpy.getMessageAtIndex(-2, "error"));
      assertContainsText("The ig.ini file must have a \"template\" property", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("IGExporter #ig-ini should report unsupported properties in user-provided ig.ini and not copy file when template is not defined")
    void shouldReportUnsupportedPropertiesInUserProvidedIgIniAndNotCopyFileWhenTemplateIs() {
      Package pkg = new Package(MinimalConfig.minimalConfig());
      var igDataPath = path.resolve(__dirname, "fixtures", "ig-ini-with-deprecated-properties");
      IGExporter exporter = new IGExporter(pkg, null, igDataPath);
      exporter.checkIgIni();
      var igIniPath = path.join(tempOut, "ig.ini");
      assertFalse(Js.truthy(fs.existsSync(igIniPath)));
      assertEquals(1, loggerSpy.getAllMessages().size());
      assertContainsText("Your ig-ini-with-deprecated-properties" + path.sep + "ig.ini file contains the following unsupported properties: " + "copyrightyear, license, version, ballotstatus, fhirspec, excludexml, excludejson, excludettl, excludeMaps.", loggerSpy.getLastMessage("warn"));
    }

  }
}
