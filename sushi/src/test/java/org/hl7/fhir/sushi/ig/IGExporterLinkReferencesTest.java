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

// port of sushi/test/ig/IGExporter.link-references.test.ts (SUSHI 3.21.1)
@DisplayName("IGExporter")
class IGExporterLinkReferencesTest {

  static final String __dirname = dirname("ig");
  @DisplayName("#link-references")
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class LinkReferences {
    Package pkg;
    IGExporter exporter;
    TestFHIRDefinitions defs;
    String tempOut;

    @BeforeEach
    void beforeEach() {
      tempOut = temp.mkdirSync("sushi-test");
      defs = getTestFHIRDefinitions();
      var inputPath = path.resolve(__dirname, "fixtures", "customized-ig-with-resources", "input");
      defs.loadCustomResources(inputPath);
      loggerSpy.reset();
    }

    @AfterEach
    void afterEach() {
      temp.cleanupSync();
    }

    @Test
    @DisplayName("IGExporter #link-references should create links for each resource in the IG")
    void shouldCreateLinksForEachResourceInTheIG() {
      pkg = new Package(cfg(SPREAD, MinimalConfig.minimalConfig(), "resources", new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "reference": {
            "reference": "ValueSet/unique-vs"
          },
          "name": "UniqueVS"
        }
        """), JsAssert.json("""
        {
          "reference": {
            "reference": "CodeSystem/nameless-cs"
          }
        }
        """)))));
      // make a little codesystem to add to the package
      CodeSystem littleCodeSystem = new CodeSystem();
      setProp(littleCodeSystem, "id", "little-cs");
      setProp(littleCodeSystem, "name", "LittleCodeSystem");
      setProp(littleCodeSystem, "title", "Little Code System");
      setProp(littleCodeSystem, "description", "A rather small code system");
      pkg.codeSystems.add(littleCodeSystem);
      var igDataPath = path.resolve(__dirname, "fixtures", "customized-ig-with-resources");
      exporter = new IGExporter(pkg, defs, igDataPath);
      exporter.export(tempOut);
      var linkReferencesPath = path.join(tempOut, "fsh-generated", "includes", "fsh-link-references.md");
      assertEquals(true, fs.existsSync(linkReferencesPath));
      var content = fs.readFileSync(linkReferencesPath, "utf-8");
      // we should have links from resources in the package,
      assertMatches("^\\[LittleCodeSystem\\]: CodeSystem-little-cs\\.html$", "m", (String) content);
      // predefined resources,
      assertMatches("^\\[MyPatient\\]: StructureDefinition-MyPatient\\.html$", "m", (String) content);
      assertMatches("^\\[birthPlace\\]: StructureDefinition-patient-birthPlace\\.html$", "m", (String) content);
      // and config-only resources,
      assertMatches("^\\[UniqueVS\\]: ValueSet-unique-vs\\.html$", "m", (String) content);
      // even when the config doesn't give it a name
      assertMatches("^\\[nameless-cs\\]: CodeSystem-nameless-cs\\.html$", "m", (String) content);
    }

    @Test
    @DisplayName("IGExporter #link-references should not create a file if there are no resources in the IG")
    void shouldNotCreateAFileIfThereAreNoResourcesInTheIG() {
      // reset defs so we have no predefined resources
      defs = getTestFHIRDefinitions();
      // configure the little codesystem to be omitted from the IG
      pkg = new Package(cfg(SPREAD, MinimalConfig.minimalConfig(), "resources", new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "reference": {
            "reference": "CodeSystem/little-cs"
          },
          "omit": true
        }
        """)))));
      // make a little codesystem to add to the package
      CodeSystem littleCodeSystem = new CodeSystem();
      setProp(littleCodeSystem, "id", "little-cs");
      setProp(littleCodeSystem, "name", "LittleCodeSystem");
      setProp(littleCodeSystem, "description", "A rather small code system");
      pkg.codeSystems.add(littleCodeSystem);
      var igDataPath = path.resolve(__dirname, "fixtures", "customized-ig-with-resources");
      exporter = new IGExporter(pkg, defs, igDataPath);
      exporter.export(tempOut);
      var linkReferencesPath = path.join(tempOut, "fsh-generated", "includes", "fsh-link-references.md");
      assertEquals(false, fs.existsSync(linkReferencesPath));
    }

  }
}
