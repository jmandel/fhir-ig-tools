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
import static org.hl7.fhir.sushi.ig.MenuXMLContent.*;

// port of sushi/test/ig/IGExporter.menu-xml.test.ts (SUSHI 3.21.1)
@DisplayName("IGExporter")
class IGExporterMenuXmlTest {

  // Track temp files/folders for cleanup
  static final String __dirname = dirname("ig");
  @DisplayName("#menu-xml")
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class MenuXml {
    String tempOut;

    @BeforeEach
    void beforeEach() {
      tempOut = temp.mkdirSync("sushi-test");
      loggerSpy.reset();
    }

    @AfterEach
    void afterEach() {
      temp.cleanupSync();
    }

    @Test
    @DisplayName("IGExporter #menu-xml should log a warning when config.menu is undefined and none provided")
    void shouldLogAWarningWhenConfigMenuIsUndefinedAndNoneProvided() {
      Package pkg = new Package(MinimalConfig.minimalConfig());
      var igDataPath = path.resolve(__dirname, "fixtures", "simple-ig");
      IGExporter exporter = new IGExporter(pkg, null, igDataPath);
      exporter.addMenuXML(tempOut);
      var menuPath = path.join(tempOut, "input", "includes", "menu.xml");
      assertFalse(Js.truthy(fs.existsSync(menuPath)));
      assertEquals(1, loggerSpy.getAllMessages("warn").size());
      assertMatches("No \"menu\" property or file was found\\. .*input[\\/\\\\]includes folder.* or .* in sushi-config\\.yaml", "s", loggerSpy.getLastMessage());
    }

    @Test
    @DisplayName("IGExporter #menu-xml should use but not copy user-provided menu.xml when config.menu is not defined")
    void shouldUseButNotCopyUserProvidedMenuXmlWhenConfigMenuIsNotDefined() {
      Package pkg = new Package(MinimalConfig.minimalConfig());
      var igDataPath = path.resolve(__dirname, "fixtures", "customized-ig");
      IGExporter exporter = new IGExporter(pkg, null, igDataPath);
      exporter.addMenuXML(tempOut);
      var menuPath = path.join(tempOut, "input", "includes", "menu.xml");
      // Since there is no processing of menu.xml, no messages logged, and nothing is copied, the function doesn't do much.
      assertFalse(Js.truthy(fs.existsSync(menuPath)));
      assertEquals(0, loggerSpy.getAllMessages().size());
    }

    @Test
    @DisplayName("IGExporter #menu-xml should use user-provided menu.xml even when config.menu when defined and log a warning")
    void shouldUseUserProvidedMenuXmlEvenWhenConfigMenuWhenDefinedAndLogAWarning() {
      Configuration config = cfg(SPREAD, MinimalConfig.minimalConfig());
      config.menu = configField("menu", JsAssert.json("""
        [
          {
            "name": "Animals",
            "url": "animals.html"
          }
        ]
        """));
      Package pkg = new Package(config);
      var igDataPath = path.resolve(__dirname, "fixtures", "customized-ig");
      IGExporter exporter = new IGExporter(pkg, null, igDataPath);
      exporter.addMenuXML(tempOut);
      var menuPath = path.join(tempOut, "fsh-generated", "includes", "menu.xml");
      assertFalse(Js.truthy(fs.existsSync(menuPath)));
      assertEquals(1, loggerSpy.getAllMessages("warn").size());
      assertMatches("Found both a \"menu\" property in sushi-config\\.yaml and a menu\\.xml file.*File: .*menu.xml", "s", loggerSpy.getLastMessage());
      assertMatches("the \"menu\" property in the sushi-config\\.yaml will be ignored", "s", loggerSpy.getLastMessage());
    }

    @Test
    @DisplayName("IGExporter #menu-xml should build simple menu.xml when provided in config.menu")
    void shouldBuildSimpleMenuXmlWhenProvidedInConfigMenu() {
      Configuration config = cfg(SPREAD, MinimalConfig.minimalConfig());
      config.menu = configField("menu", JsAssert.json("""
        [
          {
            "name": "Animals",
            "url": "animals.html"
          },
          {
            "name": "Plants",
            "url": "plants.html"
          },
          {
            "name": "Other"
          }
        ]
        """));
      Package pkg = new Package(config);
      var igDataPath = path.resolve(__dirname, "fixtures", "simple-ig");
      IGExporter exporter = new IGExporter(pkg, null, igDataPath);
      exporter.addMenuXML(tempOut);
      var menuPath = path.join(tempOut, "fsh-generated", "includes", "menu.xml");
      assertTrue(Js.truthy(fs.existsSync(menuPath)));
      var content = fs.readFileSync(menuPath, "utf8");
      assertMatches("^<!-- menu.xml \\{% comment %\\}$", "m", (String) content);
      assertMatches("^\\*\\s+WARNING: DO NOT EDIT THIS FILE\\s+\\*$", "m", (String) content);
      assertMatches("^\\*\\s+To change the contents of this file, edit the \"menu\" attribute in the tank sushi-config.yaml file\\s+\\*$", "m", (String) content);
      assertMatches("^\\*\\s+or provide your own menu\\.xml in the input[\\/\\\\]includes folder\\s+\\*$", "m", (String) content);
      assertContains(simpleMenuXMLContent, content);
      assertEquals(0, loggerSpy.getAllMessages().size());
    }

    @Test
    @DisplayName("IGExporter #menu-xml should build menu to open in new tab when provided in config.menu")
    void shouldBuildMenuToOpenInNewTabWhenProvidedInConfigMenu() {
      Configuration config = cfg(SPREAD, MinimalConfig.minimalConfig());
      config.menu = configField("menu", JsAssert.json("""
        [
          {
            "name": "Animals",
            "url": "animals.html",
            "openInNewTab": true
          },
          {
            "name": "Plants",
            "url": "plants.html"
          },
          {
            "name": "Other"
          }
        ]
        """));
      Package pkg = new Package(config);
      IGExporter exporter = new IGExporter(pkg, null, "");
      exporter.addMenuXML(tempOut);
      var menuPath = path.join(tempOut, "fsh-generated", "includes", "menu.xml");
      assertTrue(Js.truthy(fs.existsSync(menuPath)));
      var content = fs.readFileSync(menuPath, "utf8");
      assertContains(simpleMenuXMLWithOpenInNewTabContent, content);
      assertEquals(0, loggerSpy.getAllMessages().size());
    }

    @Test
    @DisplayName("IGExporter #menu-xml should build menu with a sub-menu when provided in config.menu")
    void shouldBuildMenuWithASubMenuWhenProvidedInConfigMenu() {
      Configuration config = cfg(SPREAD, MinimalConfig.minimalConfig());
      config.menu = configField("menu", JsAssert.json("""
        [
          {
            "name": "Animals",
            "url": "animals.html"
          },
          {
            "name": "Plants",
            "subMenu": [
              {
                "name": "Trees",
                "url": "plants.html#trees"
              },
              {
                "name": "Flowers",
                "url": "buds.html"
              },
              {
                "name": "Cacti",
                "url": "prickly.com",
                "openInNewTab": true
              }
            ]
          }
        ]
        """));
      Package pkg = new Package(config);
      IGExporter exporter = new IGExporter(pkg, null, "");
      exporter.addMenuXML(tempOut);
      var menuPath = path.join(tempOut, "fsh-generated", "includes", "menu.xml");
      assertTrue(Js.truthy(fs.existsSync(menuPath)));
      var content = fs.readFileSync(menuPath, "utf8");
      assertContains(subMenuXMLContent, content);
      assertEquals(0, loggerSpy.getAllMessages().size());
    }

    @Test
    @DisplayName("IGExporter #menu-xml should build and log a warning when provided menu has sub-menus more than two deep")
    void shouldBuildAndLogAWarningWhenProvidedMenuHasSubMenusMoreThanTwoDeep() {
      Configuration config = cfg(SPREAD, MinimalConfig.minimalConfig());
      config.menu = configField("menu", JsAssert.json("""
        [
          {
            "name": "Animals",
            "url": "animals.html"
          },
          {
            "name": "Plants",
            "subMenu": [
              {
                "name": "Trees",
                "url": "plants.html#trees"
              },
              {
                "name": "Flowers",
                "subMenu": [
                  {
                    "name": "Roses",
                    "url": "buds.html#roses"
                  },
                  {
                    "name": "Tulips"
                  }
                ]
              }
            ]
          }
        ]
        """));
      Package pkg = new Package(config);
      IGExporter exporter = new IGExporter(pkg, null, "");
      exporter.addMenuXML(tempOut);
      var menuPath = path.join(tempOut, "fsh-generated", "includes", "menu.xml");
      assertTrue(Js.truthy(fs.existsSync(menuPath)));
      var content = fs.readFileSync(menuPath, "utf8");
      assertContains(subMenuWithWarningXMLContent, content);
      assertEquals(1, loggerSpy.getAllMessages("warn").size());
      assertMatches("The Flowers menu item specifies a sub-menu", "s", loggerSpy.getLastMessage());
    }

    @Test
    @DisplayName("IGExporter #menu-xml should replace characters in menu item names that need to be escaped in XML")
    void shouldReplaceCharactersInMenuItemNamesThatNeedToBeEscapedInXML() {
      Configuration config = cfg(SPREAD, MinimalConfig.minimalConfig());
      config.menu = configField("menu", JsAssert.json("""
        [
          {
            "name": "Animals <3",
            "url": "animals.html"
          },
          {
            "name": "Plants >:)",
            "subMenu": [
              {
                "name": "Trees and \\"Leaves\\"",
                "url": "trees.html"
              },
              {
                "name": "Flowers' Blossoms",
                "url": "buds.html"
              },
              {
                "name": "Cacti & Succulents",
                "url": "prickly.com",
                "openInNewTab": true
              }
            ]
          }
        ]
        """));
      Package pkg = new Package(config);
      IGExporter exporter = new IGExporter(pkg, null, "");
      exporter.addMenuXML(tempOut);
      var menuPath = path.join(tempOut, "fsh-generated", "includes", "menu.xml");
      assertTrue(Js.truthy(fs.existsSync(menuPath)));
      var content = fs.readFileSync(menuPath, "utf8");
      assertContains(menuWithEscapedCharacters, content);
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
    }

    @Test
    @DisplayName("IGExporter #menu-xml should log a warning if any characters are escaped by the user to indicate SUSHI supports escaping the characters for them")
    void shouldLogAWarningIfAnyCharactersAreEscapedByTheUserToIndicateSUSHISupportsEscapi() {
      Configuration config = cfg(SPREAD, MinimalConfig.minimalConfig());
      config.menu = configField("menu", JsAssert.json("""
        [
          {
            "name": "Animals &lt;3",
            "url": "animals.html"
          },
          {
            "name": "Plants &gt;:)",
            "subMenu": [
              {
                "name": "Trees and &quot;Leaves\\"",
                "url": "trees.html"
              },
              {
                "name": "Flowers&apos; Blossoms",
                "url": "buds.html"
              },
              {
                "name": "Cacti &amp; Succulents",
                "url": "prickly.com",
                "openInNewTab": true
              }
            ]
          }
        ]
        """));
      Package pkg = new Package(config);
      IGExporter exporter = new IGExporter(pkg, null, "");
      exporter.addMenuXML(tempOut);
      var menuPath = path.join(tempOut, "fsh-generated", "includes", "menu.xml");
      assertTrue(Js.truthy(fs.existsSync(menuPath)));
      var content = fs.readFileSync(menuPath, "utf8");
      assertContains(menuWithEscapedCharacters, content);
      assertEquals(5, loggerSpy.getAllMessages("warn").size());
    }

  }
}
