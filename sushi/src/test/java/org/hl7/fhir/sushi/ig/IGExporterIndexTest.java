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

// port of sushi/test/ig/IGExporter.index.test.ts (SUSHI 3.21.1)
@DisplayName("IGExporter")
class IGExporterIndexTest {

  // Track temp files/folders for cleanup
  static final String __dirname = dirname("ig");
  @DisplayName("#index")
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class Index {
    String tempOut;
    String indexMarkdownPageContentPath;
    String indexXMLPageContentPath;
    String indexMarkdownPagesPath;
    String indexXMLPagesPath;
    String igPath;

    @BeforeEach
    void beforeEach() {
      tempOut = temp.mkdirSync("sushi-test");
      indexMarkdownPageContentPath = path.join(tempOut, "input", "pagecontent", "index.md");
      indexXMLPageContentPath = path.join(tempOut, "input", "pagecontent", "index.xml");
      indexMarkdownPagesPath = path.join(tempOut, "input", "pages", "index.md");
      indexXMLPagesPath = path.join(tempOut, "input", "pages", "index.xml");
      igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      loggerSpy.reset();
    }

    @AfterEach
    void afterEach() {
      temp.cleanupSync();
    }

    @Test
    @DisplayName("IGExporter #index should not export index file when config.indexPageContent is not defined and none provided")
    void shouldNotExportIndexFileWhenConfigIndexPageContentIsNotDefinedAndNoneProvided() {
      Package pkg = new Package(MinimalConfig.minimalConfig());
      IGExporter exporter = new IGExporter(pkg, null, "");
      exporter.initIG();
      exporter.addIndex(tempOut);
      exporter.addImplementationGuide(tempOut);
      assertFalse(Js.truthy(fs.existsSync(indexMarkdownPageContentPath)));
      assertFalse(Js.truthy(fs.existsSync(indexXMLPageContentPath)));
      assertFalse(Js.truthy(fs.existsSync(indexMarkdownPagesPath)));
      assertFalse(Js.truthy(fs.existsSync(indexXMLPagesPath)));
      // Checks that the index.md file is not added to IG definition
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertNotContainsEqual(JsAssert.json("""
        {
          "nameUrl": "index.html",
          "title": "Home",
          "generation": "markdown"
        }
        """), at(igContent, "definition.page.page"));
      // No warnings or errors logged
      assertEquals(loggerSpy.getAllMessages("info").size(), loggerSpy.getAllMessages().size());
    }

    @Test
    @DisplayName("IGExporter #index should use user-provided input/pagecontent/index.md when config.indexPageContent is not defined")
    void shouldUseUserProvidedInputPagecontentIndexMdWhenConfigIndexPageContentIsNotDefin() {
      Package pkg = new Package(MinimalConfig.minimalConfig());
      var igDataPath = path.resolve(__dirname, "fixtures", "customized-ig");
      IGExporter exporter = new IGExporter(pkg, null, igDataPath);
      exporter.initIG();
      exporter.addIndex(tempOut);
      exporter.addImplementationGuide(tempOut);
      var indexPath = path.join(tempOut, "input", "pagecontent", "index.md");
      assertFalse(Js.truthy(fs.existsSync(indexPath)));
      // Checks that the index.md file is added to IG definition
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertContainsEqual(JsAssert.json("""
        {
          "nameUrl": "index.html",
          "title": "Home",
          "generation": "markdown"
        }
        """), at(igContent, "definition.page.page"));
      // No warnings or errors logged
      assertEquals(loggerSpy.getAllMessages("info").size(), loggerSpy.getAllMessages().size());
    }

    @Test
    @DisplayName("IGExporter #index should use user-provided input/pagecontent/index.xml when config.indexPageContent is not defined")
    void shouldUseUserProvidedInputPagecontentIndexXmlWhenConfigIndexPageContentIsNotDefi() {
      Package pkg = new Package(MinimalConfig.minimalConfig());
      var igDataPath = path.resolve(__dirname, "fixtures", "customized-ig-with-index-xml");
      IGExporter exporter = new IGExporter(pkg, null, igDataPath);
      exporter.initIG();
      exporter.addIndex(tempOut);
      exporter.addImplementationGuide(tempOut);
      var indexPath = path.join(tempOut, "input", "pagecontent", "index.xml");
      assertFalse(Js.truthy(fs.existsSync(indexPath)));
      // Checks that the index.xml file is added to IG definition
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertJsonEqual("""
        [
          {
            "nameUrl": "index.html",
            "title": "Home",
            "generation": "html"
          }
        ]
        """, at(igContent, "definition.page.page"));
      // No warnings or errors logged
      assertEquals(loggerSpy.getAllMessages("info").size(), loggerSpy.getAllMessages().size());
    }

    @Test
    @DisplayName("IGExporter #index should use user-provided input/pages/index.md when config.indexPageContent is not defined")
    void shouldUseUserProvidedInputPagesIndexMdWhenConfigIndexPageContentIsNotDefined() {
      // Create temp ig fixture for input/pages/index.md set up
      var igDir = temp.mkdirSync("custom-ig-pages-index");
      fs.copySync(path.resolve(__dirname, "fixtures", "customized-ig"), igDir);
      fs.renameSync(path.join(igDir, "input", "pagecontent"), path.join(igDir, "input", "pages"));
      Package pkg = new Package(MinimalConfig.minimalConfig());
      IGExporter exporter = new IGExporter(pkg, null, igDir);
      exporter.initIG();
      exporter.addIndex(tempOut);
      exporter.addImplementationGuide(tempOut);
      var indexPath = path.join(tempOut, "input", "pages", "index.md");
      assertFalse(Js.truthy(fs.existsSync(indexPath)));
      // Checks that the index.xml file is added to IG definition
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertJsonEqual("""
        [
          {
            "nameUrl": "index.html",
            "title": "Home",
            "generation": "markdown"
          }
        ]
        """, at(igContent, "definition.page.page"));
      // No warnings or errors logged
      assertEquals(loggerSpy.getAllMessages("info").size(), loggerSpy.getAllMessages().size());
    }

    @Test
    @DisplayName("IGExporter #index should use user-provided input/pages/index.xml when config.indexPageContent is not defined")
    void shouldUseUserProvidedInputPagesIndexXmlWhenConfigIndexPageContentIsNotDefined() {
      // Create temp ig fixture for input/pages/index.xml set up
      var igDir = temp.mkdirSync("custom-ig-pages-index");
      fs.copySync(path.resolve(__dirname, "fixtures", "customized-ig-with-index-xml"), igDir);
      fs.renameSync(path.join(igDir, "input", "pagecontent"), path.join(igDir, "input", "pages"));
      Package pkg = new Package(MinimalConfig.minimalConfig());
      IGExporter exporter = new IGExporter(pkg, null, igDir);
      exporter.initIG();
      exporter.addIndex(tempOut);
      exporter.addImplementationGuide(tempOut);
      var indexPath = path.join(tempOut, "input", "pages", "index.xml");
      assertFalse(Js.truthy(fs.existsSync(indexPath)));
      // Checks that the index.xml file is added to IG definition
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertJsonEqual("""
        [
          {
            "nameUrl": "index.html",
            "title": "Home",
            "generation": "html"
          }
        ]
        """, at(igContent, "definition.page.page"));
      // No warnings or errors logged
      assertEquals(loggerSpy.getAllMessages("info").size(), loggerSpy.getAllMessages().size());
    }

    @Test
    @DisplayName("IGExporter #index should use config.indexPageContent to generate an index.md file")
    void shouldUseConfigIndexPageContentToGenerateAnIndexMdFile() {
      Configuration config = cfg(SPREAD, MinimalConfig.minimalConfig(), "indexPageContent", "An index file defined in config");
      Package pkg = new Package(config);
      var igDataPath = path.resolve(__dirname, "fixtures", "simple-ig");
      IGExporter exporter = new IGExporter(pkg, null, igDataPath);
      exporter.initIG();
      exporter.addIndex(tempOut);
      exporter.addImplementationGuide(tempOut);
      var indexPath = path.join(tempOut, "input", "pagecontent", "index.md");
      assertTrue(Js.truthy(fs.existsSync(indexPath)));
      var content = fs.readFileSync(indexPath, "utf8");
      assertMatches("^<!-- index.md \\{% comment %\\}$", "m", (String) content);
      assertMatches("^\\*\\s+WARNING: DO NOT EDIT THIS FILE\\s+\\*$", "m", (String) content);
      assertMatches("^\\*\\s+To change the contents of this file, edit the \"indexPageContent\" attribute in the tank sushi-config.yaml file\\s+\\*", "m", (String) content);
      assertMatches("^\\*\\s+or provide your own index file in the input[\\/\\\\]pagecontent or input[\\/\\\\]pages folder.\\s+\\*", "m", (String) content);
      assertContains("An index file defined in config", content);
      // Checks that the index.md file is added to IG definition
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertJsonEqual("""
        [
          {
            "nameUrl": "index.html",
            "title": "Home",
            "generation": "markdown"
          }
        ]
        """, at(igContent, "definition.page.page"));
      // No warnings or errors logged
      assertEquals(loggerSpy.getAllMessages("info").size(), loggerSpy.getAllMessages().size());
      assertMatches("Generated index.md based on \"indexPageContent\" in sushi-config.yaml", "i", loggerSpy.getMessageAtIndex(0, "info"));
    }

    @Test
    @DisplayName("IGExporter #index should log a warning if config.indexPageContent defined and use user provided file but not copy file")
    void shouldLogAWarningIfConfigIndexPageContentDefinedAndUseUserProvidedFileButNotCopy() {
      Configuration config = cfg(SPREAD, MinimalConfig.minimalConfig(), "indexPageContent", "An index file defined in config");
      Package pkg = new Package(config);
      var igDataPath = path.resolve(__dirname, "fixtures", "customized-ig");
      IGExporter exporter = new IGExporter(pkg, null, igDataPath);
      exporter.initIG();
      exporter.addIndex(tempOut);
      exporter.addImplementationGuide(tempOut);
      var indexPath = path.join(tempOut, "input", "pagecontent", "index.md");
      assertFalse(Js.truthy(fs.existsSync(indexPath)));
      // Checks that the index.md file is added to IG definition
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertJsonEqual("""
        [
          {
            "nameUrl": "index.html",
            "title": "Home",
            "generation": "markdown"
          }
        ]
        """, at(igContent, "definition.page.page"));
      // No errors logged, warning logged that both indexPageContent and index file provided
      assertEquals(0, loggerSpy.getAllMessages("error").size());
      assertEquals(1, loggerSpy.getAllMessages("warn").size());
      assertMatches("Found both an \"indexPageContent\" property in sushi-config\\.yaml and an index file.*File: .*index.md", "s", loggerSpy.getLastMessage("warn"));
      assertMatches("the \"indexPageContent\" property in the sushi-config\\.yaml will be ignored", "s", loggerSpy.getLastMessage("warn"));
    }

  }
}
