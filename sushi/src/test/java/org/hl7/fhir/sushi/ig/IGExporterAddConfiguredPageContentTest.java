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

// port of sushi/test/ig/IGExporter.addConfiguredPageContent.test.ts (SUSHI 3.21.1)
@DisplayName("IGExporter")
class IGExporterAddConfiguredPageContentTest {

  // Track temp files/folders for cleanup
  static final String __dirname = dirname("ig");
  @DisplayName("#configured-pagecontent")
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class ConfiguredPagecontent {
    String tempOut;
    Configuration config;
    FHIRDefinitions defs;

    @BeforeAll
    void beforeAll() {
      defs = getTestFHIRDefinitions();
    }

    @BeforeEach
    void beforeEach() {
      tempOut = temp.mkdirSync("sushi-test");
      config = MinimalConfig.minimalConfig();
      config.pages = configField("pages", arr(JsAssert.json("""
        {
          "nameUrl": "index.md",
          "title": "Example Home"
        }
        """), JsAssert.json("""
        {
          "nameUrl": "examples.xml",
          "title": "Examples Overview",
          "page": [
            {
              "nameUrl": "simpleExamples.xml"
            }
          ]
        }
        """), JsAssert.json("""
        {
          "nameUrl": "generated.md",
          "title": "A generated page that does not exist yet"
        }
        """)));
      loggerSpy.reset();
    }

    @AfterEach
    void afterEach() {
      temp.cleanupSync();
    }

    @Test
    @DisplayName("IGExporter #configured-pagecontent should not copy configured page content files")
    void shouldNotCopyConfiguredPageContentFiles() {
      Package pkg = new Package(config);
      var igDataPath = path.resolve(__dirname, "fixtures", "customized-ig-with-pagecontent");
      IGExporter exporter = new IGExporter(pkg, defs, igDataPath);
      exporter.initIG();
      exporter.addConfiguredPageContent();
      var pageContentPath = path.join(tempOut, "input", "pagecontent");
      assertFalse(Js.truthy(fs.existsSync(pageContentPath)));
      // TS: expect(outputFileSyncSpy).not.toHaveBeenCalled() -- nothing was written to the output folder
      assertEquals(0, fs.readdirSync(tempOut).size());
      assertEquals(0, loggerSpy.getAllMessages().size());
    }

  }
}
