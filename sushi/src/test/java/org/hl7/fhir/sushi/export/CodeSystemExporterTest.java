package org.hl7.fhir.sushi.export;

import static org.hl7.fhir.sushi.export.ExportTestUtil.*;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.*;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.getTestFHIRDefinitions;
import static org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.testDefsPath;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.errors.*;
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

// port of sushi/test/export/CodeSystemExporter.test.ts (SUSHI 3.21.1)
@DisplayName("CodeSystemExporter")
class CodeSystemExporterTest {

  /** The TS test's {@code [{ pathArray, value }, ...].map(...)}: code caret rules setting designation[0].value. */
  @SuppressWarnings("unchecked")
  static List<CaretValueRule> designationRules(Object... specs) {
    List<CaretValueRule> rules = new ArrayList<>();
    for (Object spec : specs) {
      CaretValueRule rule = new CaretValueRule("");
      rule.pathArray = new ArrayList<>((List<String>) (List<?>) at(spec, "pathArray"));
      rule.caretPath = "designation[0].value";
      rule.value = at(spec, "value");
      rules.add(rule);
    }
    return rules;
  }
  static FHIRDefinitions defs;
  FSHDocument doc;
  Package pkg;
  CodeSystemExporter exporter;

  @BeforeAll
  static void beforeAll() {
    defs = getTestFHIRDefinitions(true, testDefsPath("r4-definitions"));
  }

  @BeforeEach
  void beforeEach() {
    doc = new FSHDocument("fileName");
    FSHTank input = new FSHTank(new ArrayList<>(Arrays.asList(doc)), MinimalConfig.minimalConfig());
    pkg = new Package(input.config);
    TestFisher fisher = new TestFisher(input, defs, pkg);
    exporter = new CodeSystemExporter(input, pkg, fisher);
    loggerSpy.reset();
  }

  @Test
  @DisplayName("CodeSystemExporter should output empty results with empty input")
  void shouldOutputEmptyResultsWithEmptyInput() {
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertJsonEqual("[]", exported);
  }

  @Test
  @DisplayName("CodeSystemExporter should export a single code system")
  void shouldExportASingleCodeSystem() {
    FshCodeSystem codeSystem = new FshCodeSystem("MyCodeSystem");
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "CodeSystem",
          "name": "MyCodeSystem",
          "id": "MyCodeSystem",
          "status": "draft",
          "content": "complete",
          "url": "http://hl7.org/fhir/us/minimal/CodeSystem/MyCodeSystem"
        }
        """, exported.get(0));
  }

  @Test
  @DisplayName("CodeSystemExporter should add source info for the exported code system to the package")
  void shouldAddSourceInfoForTheExportedCodeSystemToThePackage() {
    FshCodeSystem codeSystem = new FshCodeSystem("MyCodeSystem").withFile("Codes.fsh").withLocation(new int[] {2, 5, 29, 33});
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "file": "Codes.fsh",
          "location": {
            "startLine": 2,
            "startColumn": 5,
            "endLine": 29,
            "endColumn": 33
          },
          "fshName": "MyCodeSystem",
          "fshType": "CodeSystem"
        }
        """, fshMapJson(pkg.fshMap.get("CodeSystem-MyCodeSystem.json")));
  }

  @Test
  @DisplayName("CodeSystemExporter should export a code system with additional metadata")
  void shouldExportACodeSystemWithAdditionalMetadata() {
    FshCodeSystem codeSystem = new FshCodeSystem("MyCodeSystem");
    codeSystem.setId("CodeSystem1");
    codeSystem.title = "My Fancy Code System";
    codeSystem.description = "Lots of important details about my fancy code system";
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "CodeSystem",
          "name": "MyCodeSystem",
          "id": "CodeSystem1",
          "status": "draft",
          "content": "complete",
          "url": "http://hl7.org/fhir/us/minimal/CodeSystem/CodeSystem1",
          "title": "My Fancy Code System",
          "description": "Lots of important details about my fancy code system"
        }
        """, exported.get(0));
  }

  @Test
  @DisplayName("CodeSystemExporter should export a code system with status and version in FSHOnly mode")
  void shouldExportACodeSystemWithStatusAndVersionInFSHOnlyMode() {
    // Create a FSHOnly config with a status and version
    Configuration fshOnlyConfig = MinimalConfig.minimalConfig();
    fshOnlyConfig.FSHOnly = true;
    fshOnlyConfig.version = "0.1.0";
    fshOnlyConfig.status = "active";
    FSHTank input = new FSHTank(new ArrayList<>(Arrays.asList(doc)), fshOnlyConfig);
    pkg = new Package(input.config);
    TestFisher fisher = new TestFisher(input, defs, pkg);
    exporter = new CodeSystemExporter(input, pkg, fisher);
    FshCodeSystem codeSystem = new FshCodeSystem("MyCodeSystem");
    codeSystem.setId("CodeSystem1");
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "CodeSystem",
          "name": "MyCodeSystem",
          "id": "CodeSystem1",
          "status": "active",
          "version": "0.1.0",
          "content": "complete",
          "url": "http://hl7.org/fhir/us/minimal/CodeSystem/CodeSystem1"
        }
        """, exported.get(0));
  }

  @Test
  @DisplayName("CodeSystemExporter should export each code system once, even if export is called more than once")
  void shouldExportEachCodeSystemOnceEvenIfExportIsCalledMoreThanOnce() {
    FshCodeSystem codeSystem = new FshCodeSystem("MyCodeSystem");
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    List<CodeSystem> exportedAgain = exporter.export().codeSystems;
    assertEquals(1, exportedAgain.size());
  }

  @Test
  @DisplayName("CodeSystemExporter should export a code system with a concept with only a code")
  void shouldExportACodeSystemWithAConceptWithOnlyACode() {
    FshCodeSystem codeSystem = new FshCodeSystem("MyCodeSystem");
    ConceptRule myCode = new ConceptRule("myCode");
    ConceptRule anotherCode = new ConceptRule("anotherCode");
    codeSystem.rules = new ArrayList<>(Arrays.asList(myCode, anotherCode));
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "CodeSystem",
          "name": "MyCodeSystem",
          "id": "MyCodeSystem",
          "status": "draft",
          "content": "complete",
          "url": "http://hl7.org/fhir/us/minimal/CodeSystem/MyCodeSystem",
          "count": 2,
          "concept": [
            {
              "code": "myCode"
            },
            {
              "code": "anotherCode"
            }
          ]
        }
        """, exported.get(0));
  }

  @Test
  @DisplayName("CodeSystemExporter should export a code system with a concept with a code, display, and definition")
  void shouldExportACodeSystemWithAConceptWithACodeDisplayAndDefinition() {
    FshCodeSystem codeSystem = new FshCodeSystem("MyCodeSystem");
    ConceptRule myCode = new ConceptRule("myCode", "My code", "This is the formal definition of my code");
    ConceptRule anotherCode = new ConceptRule("anotherCode", "A second code", "More details about this code");
    codeSystem.rules = new ArrayList<>(Arrays.asList(myCode, anotherCode));
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "CodeSystem",
          "name": "MyCodeSystem",
          "id": "MyCodeSystem",
          "status": "draft",
          "content": "complete",
          "url": "http://hl7.org/fhir/us/minimal/CodeSystem/MyCodeSystem",
          "count": 2,
          "concept": [
            {
              "code": "myCode",
              "display": "My code",
              "definition": "This is the formal definition of my code"
            },
            {
              "code": "anotherCode",
              "display": "A second code",
              "definition": "More details about this code"
            }
          ]
        }
        """, exported.get(0));
  }

  @Test
  @DisplayName("CodeSystemExporter should export a code system with hierarchical codes")
  void shouldExportACodeSystemWithHierarchicalCodes() {
    // The concept hierarchy looks like this:
    // topCode
    //   middleCode
    //     bottomCode
    //   otherMiddle
    // unrelatedCode
    FshCodeSystem codeSystem = new FshCodeSystem("HierarchicalCodeSystem");
    ConceptRule topCode = new ConceptRule("topCode", "Top Code", "This is at the top of the hierarchy.");
    ConceptRule middleCode = new ConceptRule("middleCode", "Middle Code", "This is in the middle of the hierarchy.");
    middleCode.hierarchy = new ArrayList<>(Arrays.asList("topCode"));
    ConceptRule bottomCode = new ConceptRule("bottomCode", "Bottom Code", "This is at the bottom of the hierarchy.");
    bottomCode.hierarchy = new ArrayList<>(Arrays.asList("topCode", "middleCode"));
    ConceptRule otherMiddle = new ConceptRule("otherMiddle", "Other Middle", "This is another middle code.");
    otherMiddle.hierarchy = new ArrayList<>(Arrays.asList("topCode"));
    ConceptRule unrelatedCode = new ConceptRule("unrelatedCode", "Unrelated Code", "This is not related to the hierarchy.");
    codeSystem.rules.addAll(Arrays.asList(topCode, middleCode, bottomCode, otherMiddle, unrelatedCode));
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "CodeSystem",
          "name": "HierarchicalCodeSystem",
          "id": "HierarchicalCodeSystem",
          "status": "draft",
          "content": "complete",
          "url": "http://hl7.org/fhir/us/minimal/CodeSystem/HierarchicalCodeSystem",
          "count": 5,
          "concept": [
            {
              "code": "topCode",
              "display": "Top Code",
              "definition": "This is at the top of the hierarchy.",
              "concept": [
                {
                  "code": "middleCode",
                  "display": "Middle Code",
                  "definition": "This is in the middle of the hierarchy.",
                  "concept": [
                    {
                      "code": "bottomCode",
                      "display": "Bottom Code",
                      "definition": "This is at the bottom of the hierarchy."
                    }
                  ]
                },
                {
                  "code": "otherMiddle",
                  "display": "Other Middle",
                  "definition": "This is another middle code."
                }
              ]
            },
            {
              "code": "unrelatedCode",
              "display": "Unrelated Code",
              "definition": "This is not related to the hierarchy."
            }
          ]
        }
        """, exported.get(0));
  }

  @Test
  @DisplayName("CodeSystemExporter should log an error when encountering a duplicate code")
  void shouldLogAnErrorWhenEncounteringADuplicateCode() {
    // CodeSystem: Zoo
    // * #goat "A goat"
    // * #goat "Another goat?"
    FshCodeSystem codeSystem = new FshCodeSystem("Zoo");
    ConceptRule goatCode = new ConceptRule("goat", "A goat");
    ConceptRule anotherGoat = new ConceptRule("goat", "Another goat?").withFile("Zoo.fsh").withLocation(new int[] {3, 0, 3, 23});
    codeSystem.rules.addAll(Arrays.asList(goatCode, anotherGoat));
    CodeSystem exported = exporter.exportCodeSystem(codeSystem);
    assertEquals("goat", at(exported, "concept[0].code"));
    assertEquals("A goat", at(exported, "concept[0].display"));
    assertMatches("CodeSystem Zoo already contains code goat.*File: Zoo\\.fsh.*Line: 3\\D*", "s", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("CodeSystemExporter should not log an error when encountering a duplicate code if the new code has no display or definition")
  void shouldNotLogAnErrorWhenEncounteringADuplicateCodeIfTheNewCodeHasNoDisplayOrDefin() {
    // CodeSystem: Zoo
    // * #goat "A goat"
    // * #goat
    FshCodeSystem codeSystem = new FshCodeSystem("Zoo");
    ConceptRule goatCode = new ConceptRule("goat", "A goat");
    ConceptRule anotherGoat = new ConceptRule("goat");
    codeSystem.rules.addAll(Arrays.asList(goatCode, anotherGoat));
    CodeSystem exported = exporter.exportCodeSystem(codeSystem);
    assertEquals(1, len(at(exported, "concept")));
    assertEquals("goat", at(exported, "concept[0].code"));
    assertEquals("A goat", at(exported, "concept[0].display"));
    assertEquals(0, loggerSpy.getAllMessages("error").size());
  }

  @Test
  @DisplayName("CodeSystemExporter should log an error when encountering a code with an incorrectly defined hierarchy")
  void shouldLogAnErrorWhenEncounteringACodeWithAnIncorrectlyDefinedHierarchy() {
    // CodeSystem: Zoo
    // * #bear "Bear" "A member of family Ursidae."
    // * #bear #sunbear #ursula "Ursula the sun bear"
    FshCodeSystem codeSystem = new FshCodeSystem("Zoo");
    ConceptRule bearCode = new ConceptRule("bear", "Bear", "A member of family Ursidae.");
    ConceptRule ursulaCode = new ConceptRule("ursula", "Ursula the sun bear").withFile("Zoo.fsh").withLocation(new int[] {3, 0, 3, 46});
    ursulaCode.hierarchy = new ArrayList<>(Arrays.asList("bear", "sunbear"));
    codeSystem.rules.addAll(Arrays.asList(bearCode, ursulaCode));
    CodeSystem exported = exporter.exportCodeSystem(codeSystem);
    assertEquals("bear", at(exported, "concept[0].code"));
    assertEquals("Bear", at(exported, "concept[0].display"));
    assertMatches("Could not find sunbear in concept hierarchy to use as ancestor of ursula.*File: Zoo\\.fsh.*Line: 3\\D*", "s", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("CodeSystemExporter should warn when title and/or description is an empty string")
  void shouldWarnWhenTitleAndOrDescriptionIsAnEmptyString() {
    FshCodeSystem codeSystem = new FshCodeSystem("MyCodeSystem");
    codeSystem.title = "";
    codeSystem.description = "";
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertEquals(2, loggerSpy.getAllMessages("warn").size());
    assertContainsText("Code system MyCodeSystem has a title field that should not be empty.", loggerSpy.getFirstMessage("warn"));
    assertContainsText("Code system MyCodeSystem has a description field that should not be empty.", loggerSpy.getLastMessage("warn"));
  }

  @Test
  @DisplayName("CodeSystemExporter should log a message when the code system has an invalid id")
  void shouldLogAMessageWhenTheCodeSystemHasAnInvalidId() {
    FshCodeSystem codeSystem = new FshCodeSystem("StrangeSystem").withFile("Strange.fsh").withLocation(new int[] {2, 4, 6, 23});
    codeSystem.setId("Is this allowed?");
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertEquals("Is this allowed?", at(exported.get(0), "id"));
    assertMatches("does not represent a valid FHIR id", "s", loggerSpy.getLastMessage("error"));
    assertMatches("File: Strange\\.fsh.*Line: 2 - 6\\D*", "s", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("CodeSystemExporter should not log a message when the code system overrides an invalid id with a Caret Rule")
  void shouldNotLogAMessageWhenTheCodeSystemOverridesAnInvalidIdWithACaretRule() {
    FshCodeSystem codeSystem = new FshCodeSystem("StrangeSystem").withFile("Strange.fsh").withLocation(new int[] {2, 4, 6, 23});
    codeSystem.setId("Is this allowed?");
    CaretValueRule idRule = new CaretValueRule("");
    idRule.caretPath = "id";
    idRule.value = "this-is-allowed";
    codeSystem.rules.add(idRule);
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertEquals("this-is-allowed", at(exported.get(0), "id"));
    assertEquals(0, loggerSpy.getAllLogs("warn").size());
  }

  @Test
  @DisplayName("CodeSystemExporter should log a message when the code system overrides an invalid id with an invalid Caret Rule")
  void shouldLogAMessageWhenTheCodeSystemOverridesAnInvalidIdWithAnInvalidCaretRule() {
    FshCodeSystem codeSystem = new FshCodeSystem("StrangeSystem").withFile("Strange.fsh").withLocation(new int[] {2, 4, 6, 23});
    codeSystem.setId("Is this allowed?");
    CaretValueRule idRule = new CaretValueRule("").withFile("Strange.fsh").withLocation(new int[] {4, 4, 4, 4});
    idRule.caretPath = "id";
    idRule.value = "No this is not allowed";
    codeSystem.rules.add(idRule);
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertEquals("No this is not allowed", at(exported.get(0), "id"));
    assertMatches("does not represent a valid FHIR id", "s", loggerSpy.getLastMessage("error"));
    assertMatches("File: Strange\\.fsh.*Line: 4\\D*", "s", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("CodeSystemExporter should log a message when the code system overrides a valid id with an invalid Caret Rule")
  void shouldLogAMessageWhenTheCodeSystemOverridesAValidIdWithAnInvalidCaretRule() {
    FshCodeSystem codeSystem = new FshCodeSystem("StrangeSystem").withFile("Strange.fsh").withLocation(new int[] {2, 4, 6, 23});
    codeSystem.setId("this-is-allowed");
    CaretValueRule idRule = new CaretValueRule("").withFile("Strange.fsh").withLocation(new int[] {4, 4, 4, 4});
    idRule.caretPath = "id";
    idRule.value = "This is not allowed";
    codeSystem.rules.add(idRule);
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertEquals("This is not allowed", at(exported.get(0), "id"));
    assertMatches("does not represent a valid FHIR id", "s", loggerSpy.getLastMessage("error"));
    assertMatches("File: Strange\\.fsh.*Line: 4\\D*", "s", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("CodeSystemExporter should log a message when the code system has an invalid name")
  void shouldLogAMessageWhenTheCodeSystemHasAnInvalidName() {
    FshCodeSystem codeSystem = new FshCodeSystem("Strange.Code.System").withFile("Strange.fsh").withLocation(new int[] {3, 4, 8, 24});
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertEquals("Strange.Code.System", at(exported.get(0), "name"));
    assertMatches("may not be suitable for machine processing applications such as code generation", "s", loggerSpy.getLastMessage("warn"));
    assertMatches("File: Strange\\.fsh.*Line: 3 - 8\\D*", "s", loggerSpy.getLastMessage("warn"));
  }

  @Test
  @DisplayName("CodeSystemExporter should not log a message when the code system overrides an invalid name with a Caret Rule")
  void shouldNotLogAMessageWhenTheCodeSystemOverridesAnInvalidNameWithACaretRule() {
    FshCodeSystem codeSystem = new FshCodeSystem("Strange.Code.System").withFile("Strange.fsh").withLocation(new int[] {3, 4, 8, 24});
    CaretValueRule nameRule = new CaretValueRule("");
    nameRule.caretPath = "name";
    nameRule.value = "StrangeCodeSystem";
    codeSystem.rules.add(nameRule);
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertEquals("StrangeCodeSystem", at(exported.get(0), "name"));
    assertEquals(0, loggerSpy.getAllLogs("warn").size());
  }

  @Test
  @DisplayName("CodeSystemExporter should log a message when the code system overrides an invalid name with an invalid Caret Rule")
  void shouldLogAMessageWhenTheCodeSystemOverridesAnInvalidNameWithAnInvalidCaretRule() {
    FshCodeSystem codeSystem = new FshCodeSystem("Strange.Code.System").withFile("Strange.fsh").withLocation(new int[] {3, 4, 8, 24});
    CaretValueRule nameRule = new CaretValueRule("").withFile("Strange.fsh").withLocation(new int[] {4, 4, 4, 4});
    nameRule.caretPath = "name";
    nameRule.value = "Strange.Code.System";
    codeSystem.rules.add(nameRule);
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertMatches("may not be suitable for machine processing applications such as code generation", "s", loggerSpy.getLastMessage("warn"));
    assertMatches("File: Strange\\.fsh.*Line: 4\\D*", "s", loggerSpy.getLastMessage("warn"));
  }

  @Test
  @DisplayName("CodeSystemExporter should log a message when the code system overrides a valid name with an invalid Caret Rule")
  void shouldLogAMessageWhenTheCodeSystemOverridesAValidNameWithAnInvalidCaretRule() {
    FshCodeSystem codeSystem = new FshCodeSystem("StrangeCodeSystem").withFile("Strange.fsh").withLocation(new int[] {3, 4, 8, 24});
    CaretValueRule nameRule = new CaretValueRule("").withFile("Strange.fsh").withLocation(new int[] {4, 4, 4, 4});
    nameRule.caretPath = "name";
    nameRule.value = "Strange.Code.System";
    codeSystem.rules.add(nameRule);
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertMatches("may not be suitable for machine processing applications such as code generation", "s", loggerSpy.getLastMessage("warn"));
    assertMatches("File: Strange\\.fsh.*Line: 4\\D*", "s", loggerSpy.getLastMessage("warn"));
  }

  @Test
  @DisplayName("CodeSystemExporter should sanitize the id and log a message when a valid name is used to make an invalid id")
  void shouldSanitizeTheIdAndLogAMessageWhenAValidNameIsUsedToMakeAnInvalidId() {
    FshCodeSystem codeSystem = new FshCodeSystem("Not_good_id").withFile("Wrong.fsh").withLocation(new int[] {2, 8, 5, 18});
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals("Not_good_id", at(exported.get(0), "name"));
    assertEquals("Not-good-id", at(exported.get(0), "id"));
    assertMatches("The string \"Not_good_id\" represents a valid FHIR name but not a valid FHIR id.*The id will be exported as \"Not-good-id\"", "s", loggerSpy.getLastMessage("warn"));
    assertMatches("File: Wrong\\.fsh.*Line: 2 - 5\\D*", "s", loggerSpy.getLastMessage("warn"));
  }

  @Test
  @DisplayName("CodeSystemExporter should sanitize the id and log a message when a long valid name is used to make an invalid id")
  void shouldSanitizeTheIdAndLogAMessageWhenALongValidNameIsUsedToMakeAnInvalidId() {
    String longId = "Toolong";
    while (longId.length() < 65) {
      longId += "longer";
    }
    FshCodeSystem codeSystem = new FshCodeSystem(longId).withFile("Wrong.fsh").withLocation(new int[] {2, 8, 5, 18});
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    String expectedId = longId.substring(0, 64);
    assertEquals(longId, at(exported.get(0), "name"));
    assertEquals(expectedId, at(exported.get(0), "id"));
    Pattern warning = jsRegex("The string \"" + Js.toStr(longId) + "\" represents a valid FHIR name but not a valid FHIR id.*The id will be exported as \"" + Js.toStr(expectedId) + "\"", "s");
    assertMatchesPattern(warning, loggerSpy.getLastMessage("warn"));
    assertMatches("File: Wrong\\.fsh.*Line: 2 - 5\\D*", "s", loggerSpy.getLastMessage("warn"));
  }

  @Test
  @DisplayName("CodeSystemExporter should log an error when multiple code systems have the same id")
  void shouldLogAnErrorWhenMultipleCodeSystemsHaveTheSameId() {
    FshCodeSystem firstCodeSystem = new FshCodeSystem("FirstCodeSystem").withFile("CodeSystems.fsh").withLocation(new int[] {2, 8, 6, 15});
    firstCodeSystem.setId("my-code-system");
    FshCodeSystem secondCodeSystem = new FshCodeSystem("SecondCodeSystem").withFile("CodeSystems.fsh").withLocation(new int[] {8, 8, 15, 19});
    secondCodeSystem.setId("my-code-system");
    doc.codeSystems.put(firstCodeSystem.name, firstCodeSystem);
    doc.codeSystems.put(secondCodeSystem.name, secondCodeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(2, exported.size());
    assertMatches("Multiple code systems with id my-code-system", "s", loggerSpy.getLastMessage("error"));
    assertMatches("File: CodeSystems\\.fsh.*Line: 8 - 15\\D*", "s", loggerSpy.getLastMessage("error"));
  }

  // CaretValueRules
  @Test
  @DisplayName("CodeSystemExporter should apply a CaretValueRule")
  void shouldApplyACaretValueRule() {
    FshCodeSystem codeSystem = new FshCodeSystem("CaretCodeSystem");
    CaretValueRule rule = new CaretValueRule("");
    rule.caretPath = "publisher";
    rule.value = "carat";
    codeSystem.rules.add(rule);
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "CodeSystem",
          "id": "CaretCodeSystem",
          "name": "CaretCodeSystem",
          "content": "complete",
          "url": "http://hl7.org/fhir/us/minimal/CodeSystem/CaretCodeSystem",
          "status": "draft",
          "publisher": "carat"
        }
        """, exported.get(0));
  }

  @Test
  @DisplayName("CodeSystemExporter should apply a CaretValueRule on a top-level concept")
  void shouldApplyACaretValueRuleOnATopLevelConcept() {
    FshCodeSystem codeSystem = new FshCodeSystem("CaretCodeSystem");
    ConceptRule someCode = new ConceptRule("someCode", "Some Code");
    CaretValueRule someCaret = new CaretValueRule("");
    someCaret.pathArray = new ArrayList<>(Arrays.asList("#someCode"));
    someCaret.caretPath = "designation[0].value";
    someCaret.value = "Designated value";
    codeSystem.rules.addAll(Arrays.asList(someCode, someCaret));
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "CodeSystem",
          "id": "CaretCodeSystem",
          "name": "CaretCodeSystem",
          "content": "complete",
          "url": "http://hl7.org/fhir/us/minimal/CodeSystem/CaretCodeSystem",
          "count": 1,
          "status": "draft",
          "concept": [
            {
              "code": "someCode",
              "display": "Some Code",
              "designation": [
                {
                  "value": "Designated value"
                }
              ]
            }
          ]
        }
        """, exported.get(0));
  }

  @Test
  @DisplayName("CodeSystemExporter should apply a CaretValueRule on a concept within a hierarchy")
  void shouldApplyACaretValueRuleOnAConceptWithinAHierarchy() {
    FshCodeSystem codeSystem = new FshCodeSystem("CaretCodeSystem");
    ConceptRule someCode = new ConceptRule("someCode", "Some Code");
    ConceptRule otherCode = new ConceptRule("otherCode", "Other Code");
    otherCode.hierarchy = new ArrayList<>(Arrays.asList("someCode"));
    CaretValueRule someCaret = new CaretValueRule("");
    someCaret.pathArray = new ArrayList<>(Arrays.asList("#someCode", "#otherCode"));
    someCaret.caretPath = "designation[0].value";
    someCaret.value = "Other designated value";
    codeSystem.rules.addAll(Arrays.asList(someCode, otherCode, someCaret));
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "CodeSystem",
          "id": "CaretCodeSystem",
          "name": "CaretCodeSystem",
          "content": "complete",
          "url": "http://hl7.org/fhir/us/minimal/CodeSystem/CaretCodeSystem",
          "count": 2,
          "status": "draft",
          "concept": [
            {
              "code": "someCode",
              "display": "Some Code",
              "concept": [
                {
                  "code": "otherCode",
                  "display": "Other Code",
                  "designation": [
                    {
                      "value": "Other designated value"
                    }
                  ]
                }
              ]
            }
          ]
        }
        """, exported.get(0));
  }

  @Test
  @DisplayName("CodeSystemExporter should apply CaretValueRules on concepts that are not the first in their list")
  void shouldApplyCaretValueRulesOnConceptsThatAreNotTheFirstInTheirList() {
    // * #a
    // * #b
    // * #c
    // * #b #b1
    // * #b #b2
    // * #b #b2 #b2x
    // * #c ^designation[0].value = "on c"
    // * #b #b2 ^designation[0].value = "on b2"
    // * #b #b2 #b2x ^designation[0].value = "on b2x"
    FshCodeSystem codeSystem = new FshCodeSystem("CaretCodeSystem");
    ConceptRule a = new ConceptRule("a");
    ConceptRule b = new ConceptRule("b");
    ConceptRule c = new ConceptRule("c");
    ConceptRule b1 = new ConceptRule("b1");
    b1.hierarchy = new ArrayList<>(Arrays.asList("b"));
    ConceptRule b2 = new ConceptRule("b2");
    b2.hierarchy = new ArrayList<>(Arrays.asList("b"));
    ConceptRule b2x = new ConceptRule("b2x");
    b2x.hierarchy = new ArrayList<>(Arrays.asList("b", "b2"));
    List<CaretValueRule> caretRules = designationRules(JsAssert.json("""
        {
          "pathArray": [
            "#c"
          ],
          "value": "on c"
        }
        """), JsAssert.json("""
        {
          "pathArray": [
            "#b",
            "#b2"
          ],
          "value": "on b2"
        }
        """), JsAssert.json("""
        {
          "pathArray": [
            "#b",
            "#b2",
            "#b2x"
          ],
          "value": "on b2x"
        }
        """));
    codeSystem.rules.add(a); codeSystem.rules.add(b); codeSystem.rules.add(c); codeSystem.rules.add(b1); codeSystem.rules.add(b2); codeSystem.rules.add(b2x); codeSystem.rules.addAll(caretRules);
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        [
          {
            "code": "a"
          },
          {
            "code": "b",
            "concept": [
              {
                "code": "b1"
              },
              {
                "code": "b2",
                "designation": [
                  {
                    "value": "on b2"
                  }
                ],
                "concept": [
                  {
                    "code": "b2x",
                    "designation": [
                      {
                        "value": "on b2x"
                      }
                    ]
                  }
                ]
              }
            ]
          },
          {
            "code": "c",
            "designation": [
              {
                "value": "on c"
              }
            ]
          }
        ]
        """, at(exported.get(0), "concept"));
    assertEquals(0, loggerSpy.getAllMessages("error").size());
  }

  @Test
  @DisplayName("CodeSystemExporter should apply a CaretValueRule on a concept that assigns an Instance")
  void shouldApplyACaretValueRuleOnAConceptThatAssignsAnInstance() {
    // CodeSystem: CaretCodeSystem
    // * #someCode "Some Code"
    // * #someCode ^property[0].code = #standard
    // * #someCode ^property[0].valueCoding = AnInlineCoding
    FshCodeSystem codeSystem = new FshCodeSystem("CaretCodeSystem");
    ConceptRule someCode = new ConceptRule("someCode", "Some Code");
    CaretValueRule propertyCode = new CaretValueRule("");
    propertyCode.pathArray = new ArrayList<>(Arrays.asList("#someCode"));
    propertyCode.caretPath = "property[0].code";
    propertyCode.value = new FshCode("standard");
    CaretValueRule propertyValue = new CaretValueRule("");
    propertyValue.pathArray = new ArrayList<>(Arrays.asList("#someCode"));
    propertyValue.caretPath = "property[0].valueCoding";
    propertyValue.value = "AnInlineCoding";
    propertyValue.isInstance = true;
    codeSystem.rules.addAll(Arrays.asList(someCode, propertyCode, propertyValue));
    doc.codeSystems.put(codeSystem.name, codeSystem);
    // Instance: AnInlineCoding
    // InstanceOf: Coding
    // Usage: #inline
    // system = "http://example.org/system"
    Instance assignedInstance = new Instance("AnInlineCoding");
    assignedInstance.instanceOf = "Coding";
    assignedInstance.usage = InstanceUsage.Inline;
    AssignmentRule assignedSystem = new AssignmentRule("system");
    assignedSystem.value = "http://example.org/system";
    assignedInstance.rules.add(assignedSystem);
    doc.instances.put(assignedInstance.name, assignedInstance);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "CodeSystem",
          "id": "CaretCodeSystem",
          "name": "CaretCodeSystem",
          "content": "complete",
          "url": "http://hl7.org/fhir/us/minimal/CodeSystem/CaretCodeSystem",
          "count": 1,
          "status": "draft",
          "concept": [
            {
              "code": "someCode",
              "display": "Some Code",
              "property": [
                {
                  "code": "standard",
                  "valueCoding": {
                    "system": "http://example.org/system"
                  }
                }
              ]
            }
          ]
        }
        """, exported.get(0));
  }

  @Test
  @DisplayName("CodeSystemExporter should apply a CaretValueRule on a concept that assigns an Instance with a numeric id")
  void shouldApplyACaretValueRuleOnAConceptThatAssignsAnInstanceWithANumericId() {
    FshCodeSystem codeSystem = new FshCodeSystem("CaretCodeSystem");
    ConceptRule someCode = new ConceptRule("someCode", "Some Code");
    CaretValueRule propertyCode = new CaretValueRule("");
    propertyCode.pathArray = new ArrayList<>(Arrays.asList("#someCode"));
    propertyCode.caretPath = "property[0].code";
    propertyCode.value = new FshCode("standard");
    CaretValueRule propertyValue = new CaretValueRule("");
    propertyValue.pathArray = new ArrayList<>(Arrays.asList("#someCode"));
    propertyValue.caretPath = "property[0].valueCoding";
    propertyValue.value = 790.0;
    propertyValue.rawValue = "79e1";
    codeSystem.rules.addAll(Arrays.asList(someCode, propertyCode, propertyValue));
    doc.codeSystems.put(codeSystem.name, codeSystem);
    Instance assignedInstance = new Instance("79e1");
    assignedInstance.instanceOf = "Coding";
    assignedInstance.usage = InstanceUsage.Inline;
    AssignmentRule assignedSystem = new AssignmentRule("system");
    assignedSystem.value = "http://example.org/system";
    assignedInstance.rules.add(assignedSystem);
    doc.instances.put(assignedInstance.name, assignedInstance);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "CodeSystem",
          "id": "CaretCodeSystem",
          "name": "CaretCodeSystem",
          "content": "complete",
          "url": "http://hl7.org/fhir/us/minimal/CodeSystem/CaretCodeSystem",
          "count": 1,
          "status": "draft",
          "concept": [
            {
              "code": "someCode",
              "display": "Some Code",
              "property": [
                {
                  "code": "standard",
                  "valueCoding": {
                    "system": "http://example.org/system"
                  }
                }
              ]
            }
          ]
        }
        """, exported.get(0));
  }

  @Test
  @DisplayName("CodeSystemExporter should apply a CaretValueRule on a concept that assigns an Instance with an id that resembles a boolean")
  void shouldApplyACaretValueRuleOnAConceptThatAssignsAnInstanceWithAnIdThatResemblesAB() {
    FshCodeSystem codeSystem = new FshCodeSystem("CaretCodeSystem");
    ConceptRule someCode = new ConceptRule("someCode", "Some Code");
    CaretValueRule propertyCode = new CaretValueRule("");
    propertyCode.pathArray = new ArrayList<>(Arrays.asList("#someCode"));
    propertyCode.caretPath = "property[0].code";
    propertyCode.value = new FshCode("standard");
    CaretValueRule propertyValue = new CaretValueRule("");
    propertyValue.pathArray = new ArrayList<>(Arrays.asList("#someCode"));
    propertyValue.caretPath = "property[0].valueCoding";
    propertyValue.value = false;
    propertyValue.rawValue = "false";
    codeSystem.rules.addAll(Arrays.asList(someCode, propertyCode, propertyValue));
    doc.codeSystems.put(codeSystem.name, codeSystem);
    Instance assignedInstance = new Instance("false");
    assignedInstance.instanceOf = "Coding";
    assignedInstance.usage = InstanceUsage.Inline;
    AssignmentRule assignedSystem = new AssignmentRule("system");
    assignedSystem.value = "http://example.org/system";
    assignedInstance.rules.add(assignedSystem);
    doc.instances.put(assignedInstance.name, assignedInstance);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "CodeSystem",
          "id": "CaretCodeSystem",
          "name": "CaretCodeSystem",
          "content": "complete",
          "url": "http://hl7.org/fhir/us/minimal/CodeSystem/CaretCodeSystem",
          "count": 1,
          "status": "draft",
          "concept": [
            {
              "code": "someCode",
              "display": "Some Code",
              "property": [
                {
                  "code": "standard",
                  "valueCoding": {
                    "system": "http://example.org/system"
                  }
                }
              ]
            }
          ]
        }
        """, exported.get(0));
  }

  @Test
  @DisplayName("CodeSystemExporter should apply CaretValueRules that create a contained resource")
  void shouldApplyCaretValueRulesThatCreateAContainedResource() {
    // CodeSystem: CaretCodeSystem
    // * #someCode "Some Code"
    // * ^contained.resourceType = "Observation"
    // * ^contained.id = "my-observation"
    // * ^contained.status = #draft
    // * ^contained.code = #123
    // * ^contained.valueString = "contained observation"
    FshCodeSystem codeSystem = new FshCodeSystem("CaretCodeSystem");
    ConceptRule someCode = new ConceptRule("someCode", "Some Code");
    CaretValueRule containedResourceType = new CaretValueRule("");
    containedResourceType.caretPath = "contained.resourceType";
    containedResourceType.value = "Observation";
    CaretValueRule containedId = new CaretValueRule("");
    containedId.caretPath = "contained.id";
    containedId.value = "my-observation";
    CaretValueRule containedStatus = new CaretValueRule("");
    containedStatus.caretPath = "contained.status";
    containedStatus.value = new FshCode("draft");
    CaretValueRule containedCode = new CaretValueRule("");
    containedCode.caretPath = "contained.code";
    containedCode.value = new FshCode("123");
    CaretValueRule containedValue = new CaretValueRule("");
    containedValue.caretPath = "contained.valueString";
    containedValue.value = "contained observation";
    codeSystem.rules.addAll(Arrays.asList(someCode, containedResourceType, containedId, containedStatus, containedCode, containedValue));
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "CodeSystem",
          "id": "CaretCodeSystem",
          "name": "CaretCodeSystem",
          "content": "complete",
          "url": "http://hl7.org/fhir/us/minimal/CodeSystem/CaretCodeSystem",
          "count": 1,
          "status": "draft",
          "contained": [
            {
              "resourceType": "Observation",
              "id": "my-observation",
              "status": "draft",
              "code": {
                "coding": [
                  {
                    "code": "123"
                  }
                ]
              },
              "valueString": "contained observation"
            }
          ],
          "concept": [
            {
              "code": "someCode",
              "display": "Some Code"
            }
          ]
        }
        """, exported.get(0));
    assertEquals(0, loggerSpy.getAllMessages("error").size());
  }

  @Test
  @DisplayName("CodeSystemExporter should apply CaretValueRules that modify a contained resource")
  void shouldApplyCaretValueRulesThatModifyAContainedResource() {
    // Instance: MyObservation
    // InstanceOf: Observation
    // Usage: #inline
    // * id = "my-observation"
    // * status = #draft
    // * code = #123
    Instance instance = new Instance("MyObservation");
    instance.instanceOf = "Observation";
    instance.usage = InstanceUsage.Inline;
    AssignmentRule instanceId = new AssignmentRule("id");
    instanceId.value = "my-observation";
    AssignmentRule instanceStatus = new AssignmentRule("status");
    instanceStatus.value = new FshCode("draft");
    AssignmentRule instanceCode = new AssignmentRule("code");
    instanceCode.value = new FshCode("123");
    instance.rules.addAll(Arrays.asList(instanceId, instanceStatus, instanceCode));
    doc.instances.put(instance.name, instance);
    // CodeSystem: CaretCodeSystem
    // * #someCode "Some Code"
    // * ^contained = my-observation
    // * ^contained.valueString = "contained observation"
    FshCodeSystem codeSystem = new FshCodeSystem("CaretCodeSystem");
    ConceptRule someCode = new ConceptRule("someCode", "Some Code");
    CaretValueRule containedInstance = new CaretValueRule("");
    containedInstance.caretPath = "contained";
    containedInstance.value = "my-observation";
    containedInstance.isInstance = true;
    CaretValueRule containedValue = new CaretValueRule("");
    containedValue.caretPath = "contained.valueString";
    containedValue.value = "contained observation";
    codeSystem.rules.addAll(Arrays.asList(someCode, containedInstance, containedValue));
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "CodeSystem",
          "id": "CaretCodeSystem",
          "name": "CaretCodeSystem",
          "content": "complete",
          "url": "http://hl7.org/fhir/us/minimal/CodeSystem/CaretCodeSystem",
          "count": 1,
          "status": "draft",
          "contained": [
            {
              "resourceType": "Observation",
              "id": "my-observation",
              "status": "draft",
              "code": {
                "coding": [
                  {
                    "code": "123"
                  }
                ]
              },
              "valueString": "contained observation"
            }
          ],
          "concept": [
            {
              "code": "someCode",
              "display": "Some Code"
            }
          ]
        }
        """, exported.get(0));
    assertEquals(0, loggerSpy.getAllMessages("error").size());
  }

  @Test
  @DisplayName("CodeSystemExporter should log a warning when applying a CaretValueRule that assigns an example Instance")
  void shouldLogAWarningWhenApplyingACaretValueRuleThatAssignsAnExampleInstance() {
    // Instance: MyObservation
    // InstanceOf: Observation
    // Usage: #example
    // * id = "my-observation"
    // * status = #draft
    // * code = #123
    Instance instance = new Instance("MyObservation");
    instance.instanceOf = "Observation";
    instance.usage = InstanceUsage.Example;
    AssignmentRule instanceId = new AssignmentRule("id");
    instanceId.value = "my-observation";
    AssignmentRule instanceStatus = new AssignmentRule("status");
    instanceStatus.value = new FshCode("draft");
    AssignmentRule instanceCode = new AssignmentRule("code");
    instanceCode.value = new FshCode("123");
    instance.rules.addAll(Arrays.asList(instanceId, instanceStatus, instanceCode));
    doc.instances.put(instance.name, instance);
    // CodeSystem: CaretCodeSystem
    // * #someCode "Some Code"
    // * ^contained = my-observation
    // * ^contained.valueString = "contained observation"
    FshCodeSystem codeSystem = new FshCodeSystem("CaretCodeSystem");
    ConceptRule someCode = new ConceptRule("someCode", "Some Code");
    CaretValueRule containedInstance = new CaretValueRule("").withFile("CodeSystem.fsh").withLocation(new int[] {3, 3, 3, 24});
    containedInstance.caretPath = "contained";
    containedInstance.value = "my-observation";
    containedInstance.isInstance = true;
    CaretValueRule containedValue = new CaretValueRule("");
    containedValue.caretPath = "contained.valueString";
    containedValue.value = "contained observation";
    codeSystem.rules.addAll(Arrays.asList(someCode, containedInstance, containedValue));
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "CodeSystem",
          "id": "CaretCodeSystem",
          "name": "CaretCodeSystem",
          "content": "complete",
          "url": "http://hl7.org/fhir/us/minimal/CodeSystem/CaretCodeSystem",
          "count": 1,
          "status": "draft",
          "contained": [
            {
              "resourceType": "Observation",
              "id": "my-observation",
              "status": "draft",
              "code": {
                "coding": [
                  {
                    "code": "123"
                  }
                ]
              },
              "valueString": "contained observation"
            }
          ],
          "concept": [
            {
              "code": "someCode",
              "display": "Some Code"
            }
          ]
        }
        """, exported.get(0));
    assertEquals(0, loggerSpy.getAllMessages("error").size());
    assertEquals(1, loggerSpy.getAllMessages("warn").size());
    assertMatches("Contained instance \"my-observation\" is an example", "s", loggerSpy.getLastMessage("warn"));
    assertMatches("File: CodeSystem\\.fsh.*Line: 3\\D*", "s", loggerSpy.getLastMessage("warn"));
  }

  @Test
  @DisplayName("CodeSystemExporter should log a warning when applying a CaretValueRule that assigns an example Instance with a numeric id")
  void shouldLogAWarningWhenApplyingACaretValueRuleThatAssignsAnExampleInstanceWithANum() {
    // Instance: MyObservation
    // InstanceOf: Observation
    // Usage: #example
    // * id = "555"
    // * status = #draft
    // * code = #123
    Instance instance = new Instance("MyObservation");
    instance.instanceOf = "Observation";
    instance.usage = InstanceUsage.Example;
    AssignmentRule instanceId = new AssignmentRule("id");
    instanceId.value = "555";
    AssignmentRule instanceStatus = new AssignmentRule("status");
    instanceStatus.value = new FshCode("draft");
    AssignmentRule instanceCode = new AssignmentRule("code");
    instanceCode.value = new FshCode("123");
    instance.rules.addAll(Arrays.asList(instanceId, instanceStatus, instanceCode));
    doc.instances.put(instance.name, instance);
    // CodeSystem: CaretCodeSystem
    // * #someCode "Some Code"
    // * ^contained = 555
    // * ^contained.valueString = "contained observation"
    FshCodeSystem codeSystem = new FshCodeSystem("CaretCodeSystem");
    ConceptRule someCode = new ConceptRule("someCode", "Some Code");
    CaretValueRule containedInstance = new CaretValueRule("").withFile("CodeSystem.fsh").withLocation(new int[] {3, 3, 3, 24});
    containedInstance.caretPath = "contained";
    containedInstance.value = new java.math.BigInteger("555");
    containedInstance.rawValue = "555";
    CaretValueRule containedValue = new CaretValueRule("");
    containedValue.caretPath = "contained.valueString";
    containedValue.value = "contained observation";
    codeSystem.rules.addAll(Arrays.asList(someCode, containedInstance, containedValue));
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "CodeSystem",
          "id": "CaretCodeSystem",
          "name": "CaretCodeSystem",
          "content": "complete",
          "url": "http://hl7.org/fhir/us/minimal/CodeSystem/CaretCodeSystem",
          "count": 1,
          "status": "draft",
          "contained": [
            {
              "resourceType": "Observation",
              "id": "555",
              "status": "draft",
              "code": {
                "coding": [
                  {
                    "code": "123"
                  }
                ]
              },
              "valueString": "contained observation"
            }
          ],
          "concept": [
            {
              "code": "someCode",
              "display": "Some Code"
            }
          ]
        }
        """, exported.get(0));
    assertEquals(0, loggerSpy.getAllMessages("error").size());
    assertEquals(1, loggerSpy.getAllMessages("warn").size());
    assertMatches("Contained instance \"555\" is an example", "s", loggerSpy.getLastMessage("warn"));
    assertMatches("File: CodeSystem\\.fsh.*Line: 3\\D*", "s", loggerSpy.getLastMessage("warn"));
  }

  @Test
  @DisplayName("CodeSystemExporter should replace references when applying a CaretValueRule")
  void shouldReplaceReferencesWhenApplyingACaretValueRule() {
    FshCodeSystem codeSystem = new FshCodeSystem("CaretCodeSystem");
    ConceptRule someCode = new ConceptRule("someCode", "Some Code");
    CaretValueRule propertyCode = new CaretValueRule("");
    propertyCode.pathArray = new ArrayList<>(Arrays.asList("#someCode"));
    propertyCode.caretPath = "property[0].code";
    propertyCode.value = new FshCode("myProperty");
    CaretValueRule propertyValue = new CaretValueRule("");
    propertyValue.pathArray = new ArrayList<>(Arrays.asList("#someCode"));
    propertyValue.caretPath = "property[0].valueCoding";
    propertyValue.value = new FshCode("active", "AllergyIntoleranceClinicalStatusCodes");
    codeSystem.rules.addAll(Arrays.asList(someCode, propertyCode, propertyValue));
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "CodeSystem",
          "id": "CaretCodeSystem",
          "name": "CaretCodeSystem",
          "content": "complete",
          "url": "http://hl7.org/fhir/us/minimal/CodeSystem/CaretCodeSystem",
          "count": 1,
          "status": "draft",
          "concept": [
            {
              "code": "someCode",
              "display": "Some Code",
              "property": [
                {
                  "code": "myProperty",
                  "valueCoding": {
                    "system": "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical",
                    "code": "active"
                  }
                }
              ]
            }
          ]
        }
        """, exported.get(0));
  }

  @Test
  @DisplayName("CodeSystemExporter should resolve soft indexing when applying top level Caret Value rules")
  void shouldResolveSoftIndexingWhenApplyingTopLevelCaretValueRules() {
    FshCodeSystem codeSystem = new FshCodeSystem("CaretCodeSystem");
    CaretValueRule contactRule1 = new CaretValueRule("");
    contactRule1.caretPath = "contact[+].name";
    contactRule1.value = "Example Name";
    codeSystem.rules.add(contactRule1);
    CaretValueRule contactRule2 = new CaretValueRule("");
    contactRule2.caretPath = "contact[=].telecom[+].rank";
    contactRule2.value = 1.0;
    codeSystem.rules.add(contactRule2);
    CaretValueRule contactRule3 = new CaretValueRule("");
    contactRule3.caretPath = "contact[=].telecom[=].value";
    contactRule3.value = "example@email.com";
    codeSystem.rules.add(contactRule3);
    CodeSystem exported = exporter.exportCodeSystem(codeSystem);
    assertJsonEqual("""
        [
          {
            "name": "Example Name",
            "telecom": [
              {
                "rank": 1,
                "value": "example@email.com"
              }
            ]
          }
        ]
        """, at(exported, "contact"));
  }

  @Test
  @DisplayName("CodeSystemExporter should resolve soft indexing when applying CaretValue rules with paths")
  void shouldResolveSoftIndexingWhenApplyingCaretValueRulesWithPaths() {
    FshCodeSystem codeSystem = new FshCodeSystem("CodeCaretCS");
    ConceptRule topCode = new ConceptRule("topCode", "Top Code");
    ConceptRule bottomCode = new ConceptRule("bottomCode", "Bottom Code");
    bottomCode.hierarchy = new ArrayList<>(Arrays.asList("topCode"));
    CaretValueRule firstTop = new CaretValueRule("");
    firstTop.pathArray = new ArrayList<>(Arrays.asList("#topCode"));
    firstTop.caretPath = "designation[+].value";
    firstTop.value = "First top designation";
    CaretValueRule secondTop = new CaretValueRule("");
    secondTop.pathArray = new ArrayList<>(Arrays.asList("#topCode"));
    secondTop.caretPath = "designation[+].value";
    secondTop.value = "Second top designation";
    CaretValueRule firstBottom = new CaretValueRule("");
    firstBottom.pathArray = new ArrayList<>(Arrays.asList("#topCode", "#bottomCode"));
    firstBottom.caretPath = "designation[+].value";
    firstBottom.value = "First bottom designation";
    CaretValueRule secondBottom = new CaretValueRule("");
    secondBottom.pathArray = new ArrayList<>(Arrays.asList("#topCode", "#bottomCode"));
    secondBottom.caretPath = "designation[+].value";
    secondBottom.value = "Second bottom designation";
    codeSystem.rules.addAll(Arrays.asList(topCode, bottomCode, firstTop, secondTop, firstBottom, secondBottom));
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "CodeSystem",
          "id": "CodeCaretCS",
          "name": "CodeCaretCS",
          "content": "complete",
          "url": "http://hl7.org/fhir/us/minimal/CodeSystem/CodeCaretCS",
          "count": 2,
          "status": "draft",
          "concept": [
            {
              "code": "topCode",
              "display": "Top Code",
              "designation": [
                {
                  "value": "First top designation"
                },
                {
                  "value": "Second top designation"
                }
              ],
              "concept": [
                {
                  "code": "bottomCode",
                  "display": "Bottom Code",
                  "designation": [
                    {
                      "value": "First bottom designation"
                    },
                    {
                      "value": "Second bottom designation"
                    }
                  ]
                }
              ]
            }
          ]
        }
        """, exported.get(0));
  }

  @Test
  @DisplayName("CodeSystemExporter should export a code system with extensions")
  void shouldExportACodeSystemWithExtensions() {
    FshCodeSystem codeSystem = new FshCodeSystem("Strange.Code.System").withFile("Strange.fsh").withLocation(new int[] {3, 4, 8, 24});
    CaretValueRule extensionRule = new CaretValueRule("");
    extensionRule.caretPath = "extension[structuredefinition-fmm].valueInteger";
    extensionRule.value = 1.0;
    ConceptRule conceptRule = new ConceptRule("bar", "Bar", "Bar");
    CaretValueRule conceptExtensionRule = new CaretValueRule("bar");
    conceptExtensionRule.pathArray = new ArrayList<>(Arrays.asList("#bar"));
    conceptExtensionRule.caretPath = "extension[structuredefinition-fmm].valueInteger";
    conceptExtensionRule.value = 2.0;
    codeSystem.rules.addAll(Arrays.asList(extensionRule, conceptRule, conceptExtensionRule));
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertContainsEqual(JsAssert.json("""
        {
          "url": "http://hl7.org/fhir/StructureDefinition/structuredefinition-fmm",
          "valueInteger": 1
        }
        """), at(exported.get(0), "extension"));
    assertContainsEqual(JsAssert.json("""
        {
          "url": "http://hl7.org/fhir/StructureDefinition/structuredefinition-fmm",
          "valueInteger": 2
        }
        """), at(exported.get(0), "concept[0].extension"));
  }

  @Test
  @DisplayName("CodeSystemExporter should apply several caret rules to the same extension slice and to a numerically indexed extension")
  void shouldApplySeveralCaretRulesToTheSameExtensionSliceAndToANumericallyIndexedExten() {
    // CodeSystem: ExtensionSystem
    // * ^extension[structuredefinition-fmm].valueInteger = 1
    // * ^extension[structuredefinition-fmm].id = "fmm"
    // * ^extension[1].url = "http://example.org/StructureDefinition/plain"
    // * ^extension[1].valueString = "plain"
    // * #bar "Bar"
    // * #bar ^extension[structuredefinition-fmm].valueInteger = 2
    // * #bar ^extension[structuredefinition-fmm].id = "concept-fmm"
    // * #bar ^extension[1].url = "http://example.org/StructureDefinition/plain"
    // * #bar ^extension[1].valueString = "concept plain"
    FshCodeSystem codeSystem = new FshCodeSystem("ExtensionSystem");
    CaretValueRule fmmRule = new CaretValueRule("");
    fmmRule.caretPath = "extension[structuredefinition-fmm].valueInteger";
    fmmRule.value = 1.0;
    CaretValueRule fmmIdRule = new CaretValueRule("");
    fmmIdRule.caretPath = "extension[structuredefinition-fmm].id";
    fmmIdRule.value = "fmm";
    CaretValueRule plainUrlRule = new CaretValueRule("");
    plainUrlRule.caretPath = "extension[1].url";
    plainUrlRule.value = "http://example.org/StructureDefinition/plain";
    CaretValueRule plainValueRule = new CaretValueRule("");
    plainValueRule.caretPath = "extension[1].valueString";
    plainValueRule.value = "plain";
    ConceptRule conceptRule = new ConceptRule("bar", "Bar");
    CaretValueRule conceptFmmRule = new CaretValueRule("");
    conceptFmmRule.pathArray = new ArrayList<>(Arrays.asList("#bar"));
    conceptFmmRule.caretPath = "extension[structuredefinition-fmm].valueInteger";
    conceptFmmRule.value = 2.0;
    CaretValueRule conceptFmmIdRule = new CaretValueRule("");
    conceptFmmIdRule.pathArray = new ArrayList<>(Arrays.asList("#bar"));
    conceptFmmIdRule.caretPath = "extension[structuredefinition-fmm].id";
    conceptFmmIdRule.value = "concept-fmm";
    CaretValueRule conceptPlainUrlRule = new CaretValueRule("");
    conceptPlainUrlRule.pathArray = new ArrayList<>(Arrays.asList("#bar"));
    conceptPlainUrlRule.caretPath = "extension[1].url";
    conceptPlainUrlRule.value = "http://example.org/StructureDefinition/plain";
    CaretValueRule conceptPlainValueRule = new CaretValueRule("");
    conceptPlainValueRule.pathArray = new ArrayList<>(Arrays.asList("#bar"));
    conceptPlainValueRule.caretPath = "extension[1].valueString";
    conceptPlainValueRule.value = "concept plain";
    codeSystem.rules.addAll(Arrays.asList(fmmRule, fmmIdRule, plainUrlRule, plainValueRule, conceptRule, conceptFmmRule, conceptFmmIdRule, conceptPlainUrlRule, conceptPlainValueRule));
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        [
          {
            "id": "fmm",
            "url": "http://hl7.org/fhir/StructureDefinition/structuredefinition-fmm",
            "valueInteger": 1
          },
          {
            "url": "http://example.org/StructureDefinition/plain",
            "valueString": "plain"
          }
        ]
        """, at(exported.get(0), "extension"));
    assertJsonEqual("""
        [
          {
            "id": "concept-fmm",
            "url": "http://hl7.org/fhir/StructureDefinition/structuredefinition-fmm",
            "valueInteger": 2
          },
          {
            "url": "http://example.org/StructureDefinition/plain",
            "valueString": "concept plain"
          }
        ]
        """, at(exported.get(0), "concept[0].extension"));
    assertEquals(0, loggerSpy.getAllMessages("error").size());
    assertEquals(0, loggerSpy.getAllMessages("warn").size());
  }

  @Test
  @DisplayName("CodeSystemExporter should output an error when a choice element has values assigned to more than one choice type")
  void shouldOutputAnErrorWhenAChoiceElementHasValuesAssignedToMoreThanOneChoiceType() {
    FshCodeSystem codeSystem = new FshCodeSystem("MultiChoiceSystem").withFile("MultipleChoice.fsh").withLocation(new int[] {3, 4, 8, 24});
    CaretValueRule extensionUrl = new CaretValueRule("");
    extensionUrl.caretPath = "extension[0].url";
    extensionUrl.value = "http://example.org/SomeExt";
    CaretValueRule extensionString = new CaretValueRule("");
    extensionString.caretPath = "extension[0].valueString";
    extensionString.value = "multi value";
    CaretValueRule extensionInteger = new CaretValueRule("");
    extensionInteger.caretPath = "extension[0].valueInteger";
    extensionInteger.value = new java.math.BigInteger("24");
    ConceptRule conceptRule = new ConceptRule("bar", "Bar", "Bar");
    codeSystem.rules.addAll(Arrays.asList(extensionUrl, extensionString, extensionInteger, conceptRule));
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "url": "http://example.org/SomeExt",
          "valueString": "multi value",
          "valueInteger": 24
        }
        """, at(exported.get(0), "extension[0]"));
    assertEquals(1, loggerSpy.getAllMessages("error").size());
    assertMatches("MultiChoiceSystem contains multiple choice value assignments for choice element CodeSystem\\.extension\\.value\\[x\\]\\..*File: MultipleChoice\\.fsh.*Line: 3 - 8\\D*", "s", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("CodeSystemExporter should not override count when ^count is provided by user")
  void shouldNotOverrideCountWhenCountIsProvidedByUser() {
    FshCodeSystem codeSystem = new FshCodeSystem("MyCodeSystem");
    CaretValueRule rule = new CaretValueRule("");
    rule.caretPath = "content";
    rule.value = new FshCode("fragment", "http://hl7.org/fhir/codesystem-content-mode");
    codeSystem.rules.add(rule);
    CaretValueRule rule2 = new CaretValueRule("");
    rule2.caretPath = "count";
    rule2.value = 5.0;
    codeSystem.rules.add(rule2);
    codeSystem.rules.addAll(new ArrayList<>(Arrays.asList(new ConceptRule("myCode"), new ConceptRule("anotherCode"))));
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertJsEqual(5.0, at(exported.get(0), "count"));
    assertEquals(0, loggerSpy.getAllLogs("warn").size());
  }

  @Test
  @DisplayName("CodeSystemExporter should warn when ^count does not match number of concepts in #complete CodeSystem")
  void shouldWarnWhenCountDoesNotMatchNumberOfConceptsInCompleteCodeSystem() {
    FshCodeSystem codeSystem = new FshCodeSystem("MyCodeSystem");
    // NOTE: CS defaults to #complete so we don't need to explicitly set it
    CaretValueRule rule = new CaretValueRule("");
    rule.caretPath = "count";
    rule.value = 5.0;
    rule.sourceInfo.file = "test.fsh";
    rule.sourceInfo.location = new TextLocation(2, 1, 2, 12);
    codeSystem.rules.add(rule);
    codeSystem.rules.addAll(new ArrayList<>(Arrays.asList(new ConceptRule("myCode"), new ConceptRule("anotherCode"))));
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertJsEqual(5.0, at(exported.get(0), "count"));
    assertMatches("The user-specified \\^count \\(5\\) does not match the specified number of concepts \\(2\\)\\..*File: test\\.fsh\\s*Line: 2\\D*", "s", loggerSpy.getLastMessage("warn"));
  }

  @Test
  @DisplayName("CodeSystemExporter should warn when ^count is set and concepts is null in #complete CodeSystem")
  void shouldWarnWhenCountIsSetAndConceptsIsNullInCompleteCodeSystem() {
    FshCodeSystem codeSystem = new FshCodeSystem("MyCodeSystem");
    // NOTE: CS defaults to #complete so we don't need to explicitly set it
    CaretValueRule rule = new CaretValueRule("");
    rule.caretPath = "count";
    rule.value = 5.0;
    rule.sourceInfo.file = "test.fsh";
    rule.sourceInfo.location = new TextLocation(2, 1, 2, 12);
    codeSystem.rules.add(rule);
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertJsEqual(5.0, at(exported.get(0), "count"));
    assertMatches("The user-specified \\^count \\(5\\) does not match the specified number of concepts \\(0\\)\\..*File: test\\.fsh\\s*Line: 2\\D*", "s", loggerSpy.getLastMessage("warn"));
  }

  @Test
  @DisplayName("CodeSystemExporter should not set count when ^content is not #complete")
  void shouldNotSetCountWhenContentIsNotComplete() {
    FshCodeSystem codeSystem = new FshCodeSystem("MyCodeSystem");
    CaretValueRule rule = new CaretValueRule("");
    rule.caretPath = "content";
    rule.value = new FshCode("fragment", "http://hl7.org/fhir/codesystem-content-mode");
    codeSystem.rules.add(rule);
    codeSystem.rules.addAll(new ArrayList<>(Arrays.asList(new ConceptRule("myCode"), new ConceptRule("anotherCode"))));
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertNull(at(exported.get(0), "count"));
  }

  @Test
  @DisplayName("CodeSystemExporter should log a message when applying an invalid ConceptRule")
  void shouldLogAMessageWhenApplyingAnInvalidConceptRule() {
    FshCodeSystem codeSystem = new FshCodeSystem("MyCodeSystem");
    ConceptRule topCode = new ConceptRule("top");
    ConceptRule bottomCode = new ConceptRule("bottom");
    bottomCode.hierarchy = new ArrayList<>(Arrays.asList("top"));
    ConceptRule mistakeCode = new ConceptRule("mistake").withFile("InvalidHierarchy.fsh").withLocation(new int[] {8, 3, 8, 25});
    mistakeCode.hierarchy = new ArrayList<>(Arrays.asList("bottom"));
    codeSystem.rules.addAll(Arrays.asList(topCode, bottomCode, mistakeCode));
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertJsEqual(2.0, at(exported.get(0), "count"));
    assertMatches("File: InvalidHierarchy\\.fsh.*Line: 8\\D*", "s", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("CodeSystemExporter should log a message when applying invalid CaretValueRule")
  void shouldLogAMessageWhenApplyingInvalidCaretValueRule() {
    FshCodeSystem codeSystem = new FshCodeSystem("CaretCodeSystem");
    CaretValueRule rule = new CaretValueRule("").withFile("InvalidValue.fsh").withLocation(new int[] {6, 3, 6, 12});
    rule.caretPath = "publisherz";
    rule.value = true;
    codeSystem.rules.add(rule);
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "CodeSystem",
          "id": "CaretCodeSystem",
          "name": "CaretCodeSystem",
          "content": "complete",
          "url": "http://hl7.org/fhir/us/minimal/CodeSystem/CaretCodeSystem",
          "status": "draft"
        }
        """, exported.get(0));
    assertMatches("File: InvalidValue\\.fsh.*Line: 6\\D*", "s", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("CodeSystemExporter should log a message when applying an invalid CaretValueRule")
  void shouldLogAMessageWhenApplyingAnInvalidCaretValueRule() {
    FshCodeSystem codeSystem = new FshCodeSystem("CaretCodeSystem");
    ConceptRule someCode = new ConceptRule("someCode", "Some Code");
    ConceptRule otherCode = new ConceptRule("otherCode", "Other Code");
    otherCode.hierarchy = new ArrayList<>(Arrays.asList("someCode"));
    CaretValueRule someCaret = new CaretValueRule("").withFile("InvalidValue.fsh").withLocation(new int[] {8, 5, 8, 25});
    someCaret.pathArray = new ArrayList<>(Arrays.asList("#someCode", "#wrongCode"));
    someCaret.caretPath = "designation[0].value";
    someCaret.value = "Other designated value";
    codeSystem.rules.addAll(Arrays.asList(someCode, otherCode, someCaret));
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "CodeSystem",
          "id": "CaretCodeSystem",
          "name": "CaretCodeSystem",
          "content": "complete",
          "url": "http://hl7.org/fhir/us/minimal/CodeSystem/CaretCodeSystem",
          "count": 2,
          "status": "draft",
          "concept": [
            {
              "code": "someCode",
              "display": "Some Code",
              "concept": [
                {
                  "code": "otherCode",
                  "display": "Other Code"
                }
              ]
            }
          ]
        }
        """, exported.get(0));
    assertMatches("File: InvalidValue\\.fsh.*Line: 8\\D*", "s", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("CodeSystemExporter should log a message when a CaretValueRule is on a concept that was not added")
  void shouldLogAMessageWhenACaretValueRuleIsOnAConceptThatWasNotAdded() {
    // * #a "A"
    // * #b "B"
    // * #b #a "Duplicate A"
    // * #zzz #o "Orphan"
    // * #a #child ^designation[0].value = "below a leaf"
    // * #b #a ^designation[0].value = "on a rejected duplicate"
    // * #o ^designation[0].value = "on a concept with a missing ancestor"
    // * #a
    // * #a ^designation[0].value = "on a"
    FshCodeSystem codeSystem = new FshCodeSystem("CaretCodeSystem");
    ConceptRule a = new ConceptRule("a", "A");
    ConceptRule b = new ConceptRule("b", "B");
    ConceptRule duplicateA = new ConceptRule("a", "Duplicate A");
    duplicateA.hierarchy = new ArrayList<>(Arrays.asList("b"));
    ConceptRule orphan = new ConceptRule("o", "Orphan");
    orphan.hierarchy = new ArrayList<>(Arrays.asList("zzz"));
    ConceptRule contextA = new ConceptRule("a");
    List<CaretValueRule> designations = designationRules(JsAssert.json("""
        {
          "pathArray": [
            "#a",
            "#child"
          ],
          "value": "below a leaf"
        }
        """), JsAssert.json("""
        {
          "pathArray": [
            "#b",
            "#a"
          ],
          "value": "on a rejected duplicate"
        }
        """), JsAssert.json("""
        {
          "pathArray": [
            "#o"
          ],
          "value": "on a concept with a missing ancestor"
        }
        """), JsAssert.json("""
        {
          "pathArray": [
            "#a"
          ],
          "value": "on a"
        }
        """));
    CaretValueRule belowLeaf = designations.get(0);
    CaretValueRule onDuplicate = designations.get(1);
    CaretValueRule onOrphan = designations.get(2);
    CaretValueRule onA = designations.get(3);
    codeSystem.rules.addAll(Arrays.asList(a, b, duplicateA, orphan, belowLeaf, onDuplicate, onOrphan, contextA, onA));
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        [
          {
            "code": "a",
            "display": "A",
            "designation": [
              {
                "value": "on a"
              }
            ]
          },
          {
            "code": "b",
            "display": "B"
          }
        ]
        """, at(exported.get(0), "concept"));
    List<String> errors = loggerSpy.getAllMessages("error");
    assertEquals(5, errors.size());
    assertMatches("already contains code a", "", errors.get(0));
    assertMatches("Could not find zzz in concept hierarchy", "", errors.get(1));
    assertMatches("does not exist: #a #child", "", errors.get(2));
    assertMatches("does not exist: #b #a", "", errors.get(3));
    assertMatches("does not exist: #o", "", errors.get(4));
  }

  @Test
  @DisplayName("CodeSystemExporter should log a message when a CaretValueRule is on a concept in a CodeSystem without concepts")
  void shouldLogAMessageWhenACaretValueRuleIsOnAConceptInACodeSystemWithoutConcepts() {
    FshCodeSystem codeSystem = new FshCodeSystem("CaretCodeSystem");
    CaretValueRule someCaret = new CaretValueRule("");
    someCaret.pathArray = new ArrayList<>(Arrays.asList("#someCode"));
    someCaret.caretPath = "designation[0].value";
    someCaret.value = "Some designated value";
    codeSystem.rules.add(someCaret);
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertNull(at(exported.get(0), "concept"));
    assertEquals(1, loggerSpy.getAllMessages("error").size());
    assertMatches("does not exist: #someCode", "", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("CodeSystemExporter should not apply a CaretValueRule with an element path to a concept")
  void shouldNotApplyACaretValueRuleWithAnElementPathToAConcept() {
    // a caret rule with an element path can come from a RuleSet, and its path is not a code
    // RuleSet: NameRules
    // * name ^designation[0].value = "Not for a concept"
    FshCodeSystem codeSystem = new FshCodeSystem("CaretCodeSystem");
    // neither the element path nor the element path without its first character (where a # would be) is a code
    ConceptRule someCode = new ConceptRule("ame", "Ame");
    ConceptRule otherCode = new ConceptRule("name", "Name");
    CaretValueRule someCaret = new CaretValueRule("name");
    someCaret.pathArray = new ArrayList<>(Arrays.asList("name"));
    someCaret.caretPath = "designation[0].value";
    someCaret.value = "Not for a concept";
    codeSystem.rules.addAll(Arrays.asList(someCode, otherCode, someCaret));
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        [
          {
            "code": "ame",
            "display": "Ame"
          },
          {
            "code": "name",
            "display": "Name"
          }
        ]
        """, at(exported.get(0), "concept"));
    assertEquals(1, loggerSpy.getAllMessages("error").size());
    assertMatches("does not exist: name", "", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("CodeSystemExporter should find a url set by a CaretValueRule whose path context is dropped during export")
  void shouldFindAUrlSetByACaretValueRuleWhosePathContextIsDroppedDuringExport() {
    // a caret rule inserted with a path context has a path but no codes, and export resets its path
    // RuleSet: UrlRules
    // * ^url = "http://other.example.org/CodeSystem/moved"
    // RuleSet: ContextRules
    // * name insert UrlRules
    // CodeSystem: CaretCodeSystem
    // * insert ContextRules
    // * #someCode
    FshCodeSystem codeSystem = new FshCodeSystem("CaretCodeSystem");
    CaretValueRule urlRule = new CaretValueRule("name");
    urlRule.caretPath = "url";
    urlRule.value = "http://other.example.org/CodeSystem/moved";
    codeSystem.rules.addAll(Arrays.asList(urlRule, new ConceptRule("someCode")));
    doc.codeSystems.put(codeSystem.name, codeSystem);
    // the rule does not set the url until its path is reset, and this lookup is cached
    assertEquals("http://hl7.org/fhir/us/minimal/CodeSystem/CaretCodeSystem", org.hl7.fhir.sushi.fhirtypes.Common.getUrlFromFshDefinition(codeSystem, MinimalConfig.minimalConfig().canonical));
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertEquals("http://other.example.org/CodeSystem/moved", at(exported.get(0), "url"));
    assertEquals("http://other.example.org/CodeSystem/moved", org.hl7.fhir.sushi.fhirtypes.Common.getUrlFromFshDefinition(codeSystem, MinimalConfig.minimalConfig().canonical));
  }

  @Test
  @DisplayName("CodeSystemExporter should log a message when a CaretValueRule assigns an Instance, but the Instance is not found")
  void shouldLogAMessageWhenACaretValueRuleAssignsAnInstanceButTheInstanceIsNotFound() {
    // CodeSystem: CaretCodeSystem
    // * #someCode "Some Code"
    // * #someCode ^property[0].code = #standard
    // * #someCode ^property[0].valueCoding = AnInlineCoding
    FshCodeSystem codeSystem = new FshCodeSystem("CaretCodeSystem");
    ConceptRule someCode = new ConceptRule("someCode", "Some Code");
    CaretValueRule propertyCode = new CaretValueRule("");
    propertyCode.pathArray = new ArrayList<>(Arrays.asList("#someCode"));
    propertyCode.caretPath = "property[0].code";
    propertyCode.value = new FshCode("standard");
    CaretValueRule propertyValue = new CaretValueRule("").withFile("CodeSystems.fsh").withLocation(new int[] {8, 5, 8, 25});
    propertyValue.pathArray = new ArrayList<>(Arrays.asList("#someCode"));
    propertyValue.caretPath = "property[0].valueCoding";
    propertyValue.value = "AnInlineCoding";
    propertyValue.isInstance = true;
    codeSystem.rules.addAll(Arrays.asList(someCode, propertyCode, propertyValue));
    doc.codeSystems.put(codeSystem.name, codeSystem);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "CodeSystem",
          "id": "CaretCodeSystem",
          "name": "CaretCodeSystem",
          "content": "complete",
          "url": "http://hl7.org/fhir/us/minimal/CodeSystem/CaretCodeSystem",
          "count": 1,
          "status": "draft",
          "concept": [
            {
              "code": "someCode",
              "display": "Some Code",
              "property": [
                {
                  "code": "standard"
                }
              ]
            }
          ]
        }
        """, exported.get(0));
    assertMatches("Cannot find definition for Instance: AnInlineCoding. Skipping rule.*File: CodeSystems\\.fsh.*Line: 8\\D*", "s", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("CodeSystemExporter should log a message when a CaretValueRule assigns a value that is numeric and refers to an Instance, but both types are wrong")
  void shouldLogAMessageWhenACaretValueRuleAssignsAValueThatIsNumericAndRefersToAnInsta() {
    FshCodeSystem codeSystem = new FshCodeSystem("CaretCodeSystem");
    ConceptRule someCode = new ConceptRule("someCode", "Some Code");
    CaretValueRule propertyCode = new CaretValueRule("");
    propertyCode.pathArray = new ArrayList<>(Arrays.asList("#someCode"));
    propertyCode.caretPath = "property[0].code";
    propertyCode.value = new FshCode("standard");
    CaretValueRule propertyValue = new CaretValueRule("").withFile("CodeSystems.fsh").withLocation(new int[] {8, 6, 8, 29});
    propertyValue.pathArray = new ArrayList<>(Arrays.asList("#someCode"));
    propertyValue.caretPath = "property[0].valueDateTime";
    propertyValue.value = 790.0;
    propertyValue.rawValue = "79e1";
    codeSystem.rules.addAll(Arrays.asList(someCode, propertyCode, propertyValue));
    doc.codeSystems.put(codeSystem.name, codeSystem);
    Instance assignedInstance = new Instance("79e1");
    assignedInstance.instanceOf = "Coding";
    assignedInstance.usage = InstanceUsage.Inline;
    AssignmentRule assignedSystem = new AssignmentRule("system");
    assignedSystem.value = "http://example.org/system";
    assignedInstance.rules.add(assignedSystem);
    doc.instances.put(assignedInstance.name, assignedInstance);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "CodeSystem",
          "id": "CaretCodeSystem",
          "name": "CaretCodeSystem",
          "content": "complete",
          "url": "http://hl7.org/fhir/us/minimal/CodeSystem/CaretCodeSystem",
          "count": 1,
          "status": "draft",
          "concept": [
            {
              "code": "someCode",
              "display": "Some Code",
              "property": [
                {
                  "code": "standard"
                }
              ]
            }
          ]
        }
        """, exported.get(0));
    assertMatches("Cannot assign number value: 790\\. Value does not match element type: dateTime.*File: CodeSystems\\.fsh.*Line: 8\\D*", "s", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("CodeSystemExporter should log a message when a CaretValueRule assigns a value that is boolean and refers to an Instance, but both types are wrong")
  void shouldLogAMessageWhenACaretValueRuleAssignsAValueThatIsBooleanAndRefersToAnInsta() {
    FshCodeSystem codeSystem = new FshCodeSystem("CaretCodeSystem");
    ConceptRule someCode = new ConceptRule("someCode", "Some Code");
    CaretValueRule propertyCode = new CaretValueRule("");
    propertyCode.pathArray = new ArrayList<>(Arrays.asList("#someCode"));
    propertyCode.caretPath = "property[0].code";
    propertyCode.value = new FshCode("standard");
    CaretValueRule propertyValue = new CaretValueRule("").withFile("CodeSystems.fsh").withLocation(new int[] {8, 6, 8, 29});
    propertyValue.pathArray = new ArrayList<>(Arrays.asList("#someCode"));
    propertyValue.caretPath = "property[0].valueDateTime";
    propertyValue.value = true;
    propertyValue.rawValue = "true";
    codeSystem.rules.addAll(Arrays.asList(someCode, propertyCode, propertyValue));
    doc.codeSystems.put(codeSystem.name, codeSystem);
    Instance assignedInstance = new Instance("true");
    assignedInstance.instanceOf = "Coding";
    assignedInstance.usage = InstanceUsage.Inline;
    AssignmentRule assignedSystem = new AssignmentRule("system");
    assignedSystem.value = "http://example.org/system";
    assignedInstance.rules.add(assignedSystem);
    doc.instances.put(assignedInstance.name, assignedInstance);
    List<CodeSystem> exported = exporter.export().codeSystems;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "CodeSystem",
          "id": "CaretCodeSystem",
          "name": "CaretCodeSystem",
          "content": "complete",
          "url": "http://hl7.org/fhir/us/minimal/CodeSystem/CaretCodeSystem",
          "count": 1,
          "status": "draft",
          "concept": [
            {
              "code": "someCode",
              "display": "Some Code",
              "property": [
                {
                  "code": "standard"
                }
              ]
            }
          ]
        }
        """, exported.get(0));
    assertMatches("Cannot assign boolean value: true\\. Value does not match element type: dateTime.*File: CodeSystems\\.fsh.*Line: 8\\D*", "s", loggerSpy.getLastMessage("error"));
  }

  @DisplayName("#insertRules")
  @Nested
  class InsertRules {
    FshCodeSystem cs;
    RuleSet ruleSet;

    @BeforeEach
    void beforeEach() {
      cs = new FshCodeSystem("Foo");
      doc.codeSystems.put(cs.name, cs);
      ruleSet = new RuleSet("Bar");
      doc.ruleSets.put(ruleSet.name, ruleSet);
    }

    @Test
    @DisplayName("CodeSystemExporter #insertRules should apply rules from an insert rule")
    void shouldApplyRulesFromAnInsertRule() {
      // RuleSet: Bar
      // * ^title = "Wow fancy"
      //
      // CodeSystem: Foo
      // * insert Bar
      CaretValueRule nameRule = new CaretValueRule("");
      nameRule.caretPath = "title";
      nameRule.value = "Wow fancy";
      ruleSet.rules.add(nameRule);
      InsertRule insertRule = new InsertRule("");
      insertRule.ruleSet = "Bar";
      cs.rules.add(insertRule);
      exporter.applyInsertRules();
      CodeSystem exported = exporter.exportCodeSystem(cs);
      assertEquals("Wow fancy", at(exported, "title"));
    }

    @Test
    @DisplayName("CodeSystemExporter #insertRules should resolve soft indexing when inserting an insert rule")
    void shouldResolveSoftIndexingWhenInsertingAnInsertRule() {
      // RuleSet: Bar
      // * ^contact[+].name = Example Name
      // * ^concept[=].telecom[+].rank = 1
      // * ^concept[=].telecom[=].value = example@email.com
      //
      // CodeSystem: Foo
      // * insert Bar
      CaretValueRule contactRule1 = new CaretValueRule("");
      contactRule1.caretPath = "contact[+].name";
      contactRule1.value = "Example Name";
      ruleSet.rules.add(contactRule1);
      CaretValueRule contactRule2 = new CaretValueRule("");
      contactRule2.caretPath = "contact[=].telecom[+].rank";
      contactRule2.value = 1.0;
      ruleSet.rules.add(contactRule2);
      CaretValueRule contactRule3 = new CaretValueRule("");
      contactRule3.caretPath = "contact[=].telecom[=].value";
      contactRule3.value = "example@email.com";
      ruleSet.rules.add(contactRule3);
      InsertRule insertRule = new InsertRule("");
      insertRule.ruleSet = "Bar";
      cs.rules.add(insertRule);
      exporter.applyInsertRules();
      CodeSystem exported = exporter.exportCodeSystem(cs);
      assertJsonEqual("""
        [
          {
            "name": "Example Name",
            "telecom": [
              {
                "rank": 1,
                "value": "example@email.com"
              }
            ]
          }
        ]
        """, at(exported, "contact"));
    }

    @Test
    @DisplayName("CodeSystemExporter #insertRules should insert a rule set at a code path")
    void shouldInsertARuleSetAtACodePath() {
      // RuleSet: Bar
      // * ^designation[+].value = "Bar Value"
      // * #extra
      //
      // CodeSystem: Foo
      // * #bear
      // * #bear insert Bar
      CaretValueRule caretValueRule = new CaretValueRule("");
      caretValueRule.caretPath = "designation[+].value";
      caretValueRule.value = "Bar Value";
      ConceptRule extraConcept = new ConceptRule("extra");
      ruleSet.rules.addAll(Arrays.asList(caretValueRule, extraConcept));
      ConceptRule conceptRule = new ConceptRule("bear");
      InsertRule insertRule = new InsertRule("");
      insertRule.ruleSet = "Bar";
      insertRule.pathArray = new ArrayList<>(Arrays.asList("#bear"));
      cs.rules.addAll(Arrays.asList(conceptRule, insertRule));
      exporter.applyInsertRules();
      CodeSystem exported = exporter.exportCodeSystem(cs);
      assertJsonEqual("""
        {
          "code": "bear",
          "designation": [
            {
              "value": "Bar Value"
            }
          ],
          "concept": [
            {
              "code": "extra"
            }
          ]
        }
        """, at(exported, "concept[0]"));
    }

    @Test
    @DisplayName("CodeSystemExporter #insertRules should update count when applying concepts from an insert rule")
    void shouldUpdateCountWhenApplyingConceptsFromAnInsertRule() {
      // RuleSet: Bar
      // * #lion
      //
      // CodeSystem: Foo
      // * insert Bar
      ConceptRule concept = new ConceptRule("lion");
      ruleSet.rules.add(concept);
      InsertRule insertRule = new InsertRule("");
      insertRule.ruleSet = "Bar";
      cs.rules.add(insertRule);
      exporter.applyInsertRules();
      CodeSystem exported = exporter.exportCodeSystem(cs);
      assertEquals("lion", at(exported, "concept[0].code"));
      assertJsEqual(1.0, at(exported, "count"));
    }

    @Test
    @DisplayName("CodeSystemExporter #insertRules should log an error and not apply rules from an invalid insert rule")
    void shouldLogAnErrorAndNotApplyRulesFromAnInvalidInsertRule() {
      // RuleSet: Bar
      // * experimental = true
      // * ^title = "Wow fancy"
      //
      // CodeSystem: Foo
      // * insert Bar
      AssignmentRule valueRule = new AssignmentRule("experimental").withFile("Value.fsh").withLocation(new int[] {1, 2, 3, 4});
      valueRule.value = true;
      CaretValueRule nameRule = new CaretValueRule("");
      nameRule.caretPath = "title";
      nameRule.value = "Wow fancy";
      ruleSet.rules.addAll(Arrays.asList(valueRule, nameRule));
      InsertRule insertRule = new InsertRule("").withFile("Insert.fsh").withLocation(new int[] {5, 6, 7, 8});
      insertRule.ruleSet = "Bar";
      cs.rules.add(insertRule);
      exporter.applyInsertRules();
      CodeSystem exported = exporter.exportCodeSystem(cs);
      // CaretRule is still applied
      assertEquals("Wow fancy", at(exported, "title"));
      // experimental is not set to true
      assertFalse(Js.truthy(at(exported, "experimental")));
      assertMatches("AssignmentRule.*FshCodeSystem.*File: Value\\.fsh.*Line: 1 - 3.*Applied in File: Insert\\.fsh.*Applied on Line: 5 - 7", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("CodeSystemExporter #insertRules should maintain concept order when adding concepts from an insert rule")
    void shouldMaintainConceptOrderWhenAddingConceptsFromAnInsertRule() {
      // RuleSet: Bar
      // * #lion
      //
      // CodeSystem: Foo
      // * #bear
      // * insert Bar
      // * #alligator
      ConceptRule lionRule = new ConceptRule("lion");
      ruleSet.rules.add(lionRule);
      ConceptRule bearRule = new ConceptRule("bear");
      InsertRule insertRule = new InsertRule("");
      insertRule.ruleSet = "Bar";
      ConceptRule alligatorRule = new ConceptRule("alligator");
      cs.rules.addAll(Arrays.asList(bearRule, insertRule, alligatorRule));
      exporter.applyInsertRules();
      CodeSystem exported = exporter.exportCodeSystem(cs);
      assertEquals("bear", at(exported, "concept[0].code"));
      assertEquals("lion", at(exported, "concept[1].code"));
      assertEquals("alligator", at(exported, "concept[2].code"));
      assertJsEqual(3.0, at(exported, "count"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("CodeSystemExporter #insertRules should add nested concepts from an insert rule")
    void shouldAddNestedConceptsFromAnInsertRule() {
      // RuleSet: Bar
      // * #main "MainCode"
      //
      // RuleSet: AnotherBar
      // * #main #sub "SubCode"
      //
      // CodeSystem: Foo
      // * insert Bar
      // * insert AnotherBar
      ConceptRule mainCode = new ConceptRule("main", "MainCode");
      ruleSet.rules.add(mainCode);
      RuleSet anotherRuleSet = new RuleSet("AnotherBar");
      doc.ruleSets.put(anotherRuleSet.name, anotherRuleSet);
      ConceptRule subCode = new ConceptRule("sub", "SubCode");
      subCode.hierarchy = new ArrayList<>(Arrays.asList("main"));
      anotherRuleSet.rules.add(subCode);
      InsertRule insertBar = new InsertRule("");
      insertBar.ruleSet = "Bar";
      InsertRule insertAnother = new InsertRule("");
      insertAnother.ruleSet = "AnotherBar";
      cs.rules.addAll(Arrays.asList(insertBar, insertAnother));
      exporter.applyInsertRules();
      CodeSystem exported = exporter.exportCodeSystem(cs);
      assertEquals("main", at(exported, "concept[0].code"));
      assertEquals("sub", at(exported, "concept[0].concept[0].code"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("CodeSystemExporter #insertRules should add nested concepts whose hierarchy is created by an insert rule")
    void shouldAddNestedConceptsWhoseHierarchyIsCreatedByAnInsertRule() {
      // RuleSet: Bar
      // * #MyCode "MyCode" "This is my code"
      //
      // CodeSystem: Foo
      // * insert Bar
      // * #MyCode #SubCode "SubCode" "This is my sub-code"
      ConceptRule myCode = new ConceptRule("MyCode", "MyCode", "This is my code");
      ruleSet.rules.add(myCode);
      InsertRule insertBar = new InsertRule("");
      insertBar.ruleSet = "Bar";
      ConceptRule subCode = new ConceptRule("SubCode", "SubCode", "This is my sub-code");
      subCode.hierarchy = new ArrayList<>(Arrays.asList("MyCode"));
      cs.rules.addAll(Arrays.asList(insertBar, subCode));
      exporter.applyInsertRules();
      CodeSystem exported = exporter.exportCodeSystem(cs);
      assertEquals("MyCode", at(exported, "concept[0].code"));
      assertEquals("SubCode", at(exported, "concept[0].concept[0].code"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("CodeSystemExporter #insertRules should not add concepts from an insert rule that are duplicates of existing concepts")
    void shouldNotAddConceptsFromAnInsertRuleThatAreDuplicatesOfExistingConcepts() {
      // RuleSet: Bar
      // * #lion "Lion"
      // * #bear "Extra Bear"
      //
      // CodeSystem: Foo
      // * #bear "Regular Bear"
      // * insert Bar
      // * #alligator "Alligator"
      ConceptRule lionRule = new ConceptRule("lion", "Lion");
      ConceptRule duplicateBear = new ConceptRule("bear", "Extra Bear").withFile("RuleSet.fsh").withLocation(new int[] {3, 0, 3, 20});
      ruleSet.rules.addAll(Arrays.asList(lionRule, duplicateBear));
      ConceptRule bearRule = new ConceptRule("bear", "Regular Bear");
      InsertRule insertRule = new InsertRule("").withFile("CodeSystem.fsh").withLocation(new int[] {4, 0, 4, 12});
      insertRule.ruleSet = "Bar";
      ConceptRule alligatorRule = new ConceptRule("alligator", "Alligator");
      cs.rules.addAll(Arrays.asList(bearRule, insertRule, alligatorRule));
      exporter.applyInsertRules();
      CodeSystem exported = exporter.exportCodeSystem(cs);
      assertEquals("bear", at(exported, "concept[0].code"));
      assertEquals("Regular Bear", at(exported, "concept[0].display"));
      assertEquals("lion", at(exported, "concept[1].code"));
      assertEquals("alligator", at(exported, "concept[2].code"));
      assertJsEqual(3.0, at(exported, "count"));
      assertMatches("CodeSystem Foo already contains code bear.*File: RuleSet\\.fsh.*Line: 3\\D.*Applied in File: CodeSystem\\.fsh.*Applied on Line: 4\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("CodeSystemExporter #insertRules should not add concepts from an insert rule that are duplicates of concepts added by a previous insert rule")
    void shouldNotAddConceptsFromAnInsertRuleThatAreDuplicatesOfConceptsAddedByAPreviousI() {
      // RuleSet: Bar
      // * #bear "Bear"
      //
      // RuleSet: AnotherBar
      // * #bear "Another Bear"
      //
      // CodeSystem: Foo
      // * insert Bar
      // * insert AnotherBar
      ConceptRule bearRule = new ConceptRule("bear", "Bear");
      ruleSet.rules.add(bearRule);
      RuleSet anotherRuleSet = new RuleSet("AnotherBar");
      doc.ruleSets.put(anotherRuleSet.name, anotherRuleSet);
      ConceptRule anotherBearRule = new ConceptRule("bear", "Another Bear").withFile("RuleSet.fsh").withLocation(new int[] {5, 0, 5, 22});
      anotherRuleSet.rules.add(anotherBearRule);
      InsertRule insertBarRule = new InsertRule("");
      insertBarRule.ruleSet = "Bar";
      InsertRule insertAnotherRule = new InsertRule("").withFile("CodeSystem.fsh").withLocation(new int[] {9, 0, 9, 19});
      insertAnotherRule.ruleSet = "AnotherBar";
      cs.rules.addAll(Arrays.asList(insertBarRule, insertAnotherRule));
      exporter.applyInsertRules();
      CodeSystem exported = exporter.exportCodeSystem(cs);
      assertEquals("bear", at(exported, "concept[0].code"));
      assertEquals("Bear", at(exported, "concept[0].display"));
      assertJsEqual(1.0, at(exported, "count"));
      assertMatches("CodeSystem Foo already contains code bear.*File: RuleSet\\.fsh.*Line: 5\\D.*Applied in File: CodeSystem\\.fsh.*Applied on Line: 9\\D*", "s", loggerSpy.getLastMessage("error"));
    }

  }
}
