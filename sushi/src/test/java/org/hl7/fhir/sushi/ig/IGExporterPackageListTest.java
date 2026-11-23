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
import org.hl7.fhir.common.js.*;
import org.hl7.fhir.sushi.testhelpers.*;
import org.hl7.fhir.sushi.utils.*;
import org.junit.jupiter.api.*;

// port of sushi/test/ig/IGExporter.package-list.test.ts (SUSHI 3.21.1)
@DisplayName("IGExporter")
class IGExporterPackageListTest {

  // Track temp files/folders for cleanup
  static final String __dirname = dirname("ig");
  @DisplayName("#package-list")
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class PackageList {
    String tempOut;
    Configuration config;

    @BeforeEach
    void beforeEach() {
      tempOut = temp.mkdirSync("sushi-test");
      config = MinimalConfig.minimalConfig();
      loggerSpy.reset();
    }

    @AfterEach
    void afterEach() {
      temp.cleanupSync();
    }

    @Test
    @DisplayName("IGExporter #package-list should log an error that \"history\" is not supported when config.history is defined")
    void shouldLogAnErrorThatHistoryIsNotSupportedWhenConfigHistoryIsDefined() {
      config.history = configField("history", JsAssert.json("""
        {
          "package-id": "fhir.us.example",
          "canonical": "http://hl7.org/fhir/us/example",
          "title": "HL7 FHIR Implementation Guide: Example IG Release 1 - US Realm | STU1",
          "introduction": "Example IG exercises many of the fields in a SUSHI configuration.",
          "list": [
            {
              "version": "current",
              "desc": "Continuous Integration Build (latest in version control)",
              "path": "https://build.fhir.org/ig/HL7/example-ig/",
              "status": "ci-build",
              "current": true
            },
            {
              "version": "0.9.1",
              "fhirversion": "4.0.0",
              "date": "2019-06-10",
              "desc": "Initial STU ballot (Sep 2019 Ballot)",
              "path": "https://hl7.org/fhir/us/example/2019Sep/",
              "status": "ballot",
              "sequence": "STU 1"
            }
          ]
        }
        """));
      Package pkg = new Package(config);
      var igDataPath = path.resolve(__dirname, "fixtures", "customized-ig");
      IGExporter exporter = new IGExporter(pkg, null, igDataPath);
      exporter.checkPackageList();
      var pkgListPath = path.join(tempOut, "package-list.json");
      assertFalse(Js.truthy(fs.existsSync(pkgListPath)));
      assertMatches("Detected \"history\" property in configuration. The use of \"history\" is no longer supported.", "", loggerSpy.getLastMessage("error"));
      // TS stringifies its raw history object; the Java Configuration is typed, so the history is printed in
      // importConfiguration's key order (version, date, desc, path, changes, status, sequence, fhirversion, current)
      assertContainsText("""
        {
          "package-id": "fhir.us.example",
          "canonical": "http://hl7.org/fhir/us/example",
          "title": "HL7 FHIR Implementation Guide: Example IG Release 1 - US Realm | STU1",
          "introduction": "Example IG exercises many of the fields in a SUSHI configuration.",
          "list": [
            {
              "version": "current",
              "desc": "Continuous Integration Build (latest in version control)",
              "path": "https://build.fhir.org/ig/HL7/example-ig/",
              "status": "ci-build",
              "current": true
            },
            {
              "version": "0.9.1",
              "date": "2019-06-10",
              "desc": "Initial STU ballot (Sep 2019 Ballot)",
              "path": "https://hl7.org/fhir/us/example/2019Sep/",
              "status": "ballot",
              "sequence": "STU 1",
              "fhirversion": "4.0.0"
            }
          ]
        }""".stripIndent(), loggerSpy.getLastMessage("error"));
    }

  }
}
