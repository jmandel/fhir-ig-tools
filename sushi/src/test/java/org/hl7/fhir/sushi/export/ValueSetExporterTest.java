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
import org.hl7.fhir.common.js.*;
import org.hl7.fhir.sushi.testhelpers.*;
import org.hl7.fhir.sushi.utils.*;
import org.junit.jupiter.api.*;

// port of sushi/test/export/ValueSetExporter.test.ts (SUSHI 3.21.1)
@DisplayName("ValueSetExporter")
class ValueSetExporterTest {
  static FHIRDefinitions defs;
  FSHDocument doc;
  Package pkg;
  ValueSetExporter exporter;

  @BeforeAll
  static void beforeAll() {
    defs = getTestFHIRDefinitions(true, testDefsPath("r4-definitions"));
  }

  @BeforeEach
  void beforeEach() {
    loggerSpy.reset();
    loggerSpy.reset();
    doc = new FSHDocument("fileName");
    FSHTank input = new FSHTank(new ArrayList<>(Arrays.asList(doc)), MinimalConfig.minimalConfig());
    pkg = new Package(input.config);
    TestFisher fisher = new TestFisher(input, defs, pkg);
    exporter = new ValueSetExporter(input, pkg, fisher);
    loggerSpy.reset();
  }

  @Test
  @DisplayName("ValueSetExporter should output empty results with empty input")
  void shouldOutputEmptyResultsWithEmptyInput() {
    List<ValueSet> exported = exporter.export().valueSets;
    assertJsonEqual("[]", exported);
  }

  @Test
  @DisplayName("ValueSetExporter should export a single value set")
  void shouldExportASingleValueSet() {
    FshValueSet valueSet = new FshValueSet("BreakfastVS");
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "name": "BreakfastVS",
          "id": "BreakfastVS",
          "status": "draft",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/BreakfastVS"
        }
        """, exported.get(0));
  }

  @Test
  @DisplayName("ValueSetExporter should add source info for the exported value set to the package")
  void shouldAddSourceInfoForTheExportedValueSetToThePackage() {
    FshValueSet valueSet = new FshValueSet("MyValueSet").withFile("VS.fsh").withLocation(new int[] {3, 6, 30, 34});
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "file": "VS.fsh",
          "location": {
            "startLine": 3,
            "startColumn": 6,
            "endLine": 30,
            "endColumn": 34
          },
          "fshName": "MyValueSet",
          "fshType": "ValueSet"
        }
        """, fshMapJson(pkg.fshMap.get("ValueSet-MyValueSet.json")));
  }

  @Test
  @DisplayName("ValueSetExporter should export multiple value sets")
  void shouldExportMultipleValueSets() {
    FshValueSet breakfast = new FshValueSet("BreakfastVS");
    FshValueSet lunch = new FshValueSet("LunchVS");
    doc.valueSets.put(breakfast.name, breakfast);
    doc.valueSets.put(lunch.name, lunch);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(2, exported.size());
  }

  @Test
  @DisplayName("ValueSetExporter should export a value set with additional metadata")
  void shouldExportAValueSetWithAdditionalMetadata() {
    FshValueSet valueSet = new FshValueSet("BreakfastVS");
    valueSet.title = "Breakfast Values";
    valueSet.description = "A value set for breakfast items";
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "name": "BreakfastVS",
          "id": "BreakfastVS",
          "status": "draft",
          "title": "Breakfast Values",
          "description": "A value set for breakfast items",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/BreakfastVS"
        }
        """, exported.get(0));
  }

  @Test
  @DisplayName("ValueSetExporter should export a value set with status and version in FSHOnly mode")
  void shouldExportAValueSetWithStatusAndVersionInFSHOnlyMode() {
    // Create a FSHOnly config with a status and version
    Configuration fshOnlyConfig = MinimalConfig.minimalConfig();
    fshOnlyConfig.FSHOnly = true;
    fshOnlyConfig.version = "0.1.0";
    fshOnlyConfig.status = "active";
    FSHTank input = new FSHTank(new ArrayList<>(Arrays.asList(doc)), fshOnlyConfig);
    pkg = new Package(input.config);
    TestFisher fisher = new TestFisher(input, defs, pkg);
    exporter = new ValueSetExporter(input, pkg, fisher);
    FshValueSet valueSet = new FshValueSet("BreakfastVS");
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "name": "BreakfastVS",
          "id": "BreakfastVS",
          "status": "active",
          "version": "0.1.0",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/BreakfastVS"
        }
        """, exported.get(0));
  }

  @Test
  @DisplayName("ValueSetExporter should warn when title and/or description is an empty string")
  void shouldWarnWhenTitleAndOrDescriptionIsAnEmptyString() {
    FshValueSet valueSet = new FshValueSet("BreakfastVS");
    valueSet.title = "";
    valueSet.description = "";
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertEquals(2, loggerSpy.getAllMessages("warn").size());
    assertContainsText("Value set BreakfastVS has a title field that should not be empty.", loggerSpy.getFirstMessage("warn"));
    assertContainsText("Value set BreakfastVS has a description field that should not be empty.", loggerSpy.getLastMessage("warn"));
  }

  @Test
  @DisplayName("ValueSetExporter should log a message when the value set has an invalid id")
  void shouldLogAMessageWhenTheValueSetHasAnInvalidId() {
    FshValueSet valueSet = new FshValueSet("BreakfastVS").withFile("Breakfast.fsh").withLocation(new int[] {3, 7, 7, 12});
    valueSet.setId("Delicious!");
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertEquals("Delicious!", at(exported.get(0), "id"));
    assertMatches("does not represent a valid FHIR id", "s", loggerSpy.getLastMessage("error"));
    assertMatches("File: Breakfast\\.fsh.*Line: 3 - 7\\D*", "s", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("ValueSetExporter should not log a message when the value set overrides an invalid id with a Caret Rule")
  void shouldNotLogAMessageWhenTheValueSetOverridesAnInvalidIdWithACaretRule() {
    FshValueSet valueSet = new FshValueSet("BreakfastVS").withFile("Breakfast.fsh").withLocation(new int[] {3, 7, 7, 12});
    valueSet.setId("Delicious!");
    CaretValueRule idRule = new CaretValueRule("");
    idRule.caretPath = "id";
    idRule.value = "delicious";
    valueSet.rules.add(idRule);
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertEquals("delicious", at(exported.get(0), "id"));
    assertEquals(0, loggerSpy.getAllLogs("warn").size());
  }

  @Test
  @DisplayName("ValueSetExporter should log a message when the value set overrides an invalid id with an invalid Caret Rule")
  void shouldLogAMessageWhenTheValueSetOverridesAnInvalidIdWithAnInvalidCaretRule() {
    FshValueSet valueSet = new FshValueSet("BreakfastVS").withFile("Breakfast.fsh").withLocation(new int[] {3, 7, 7, 12});
    valueSet.setId("Delicious!");
    CaretValueRule idRule = new CaretValueRule("").withFile("Breakfast.fsh").withLocation(new int[] {4, 4, 4, 4});
    idRule.caretPath = "id";
    idRule.value = "StillDelicious!";
    valueSet.rules.add(idRule);
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertEquals("StillDelicious!", at(exported.get(0), "id"));
    assertMatches("does not represent a valid FHIR id", "s", loggerSpy.getLastMessage("error"));
    assertMatches("File: Breakfast\\.fsh.*Line: 4\\D*", "s", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("ValueSetExporter should log a message when the value set overrides a valid id with an invalid Caret Rule")
  void shouldLogAMessageWhenTheValueSetOverridesAValidIdWithAnInvalidCaretRule() {
    FshValueSet valueSet = new FshValueSet("BreakfastVS").withFile("Breakfast.fsh").withLocation(new int[] {3, 7, 7, 12});
    valueSet.setId("this-is-valid");
    CaretValueRule idRule = new CaretValueRule("").withFile("Breakfast.fsh").withLocation(new int[] {4, 4, 4, 4});
    idRule.caretPath = "id";
    idRule.value = "Oh No!";
    valueSet.rules.add(idRule);
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertEquals("Oh No!", at(exported.get(0), "id"));
    assertMatches("does not represent a valid FHIR id", "s", loggerSpy.getLastMessage("error"));
    assertMatches("File: Breakfast\\.fsh.*Line: 4\\D*", "s", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("ValueSetExporter should log a message when the value set has an invalid name")
  void shouldLogAMessageWhenTheValueSetHasAnInvalidName() {
    FshValueSet valueSet = new FshValueSet("All-you-can-eat").withFile("Breakfast.fsh").withLocation(new int[] {2, 4, 8, 23});
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertEquals("All-you-can-eat", at(exported.get(0), "name"));
    assertMatches("may not be suitable for machine processing applications such as code generation", "s", loggerSpy.getLastMessage("warn"));
    assertMatches("File: Breakfast\\.fsh.*Line: 2 - 8\\D*", "s", loggerSpy.getLastMessage("warn"));
  }

  @Test
  @DisplayName("ValueSetExporter should not log a message when the value set overrides an invalid name with a Caret Rule")
  void shouldNotLogAMessageWhenTheValueSetOverridesAnInvalidNameWithACaretRule() {
    FshValueSet valueSet = new FshValueSet("All-you-can-eat").withFile("Strange.fsh").withLocation(new int[] {3, 4, 8, 24});
    CaretValueRule nameRule = new CaretValueRule("");
    nameRule.caretPath = "name";
    nameRule.value = "AllYouCanEat";
    valueSet.rules.add(nameRule);
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertEquals("AllYouCanEat", at(exported.get(0), "name"));
    assertEquals(0, loggerSpy.getAllLogs("warn").size());
  }

  @Test
  @DisplayName("ValueSetExporter should log a message when the value set overrides an invalid name with an invalid Caret Rule")
  void shouldLogAMessageWhenTheValueSetOverridesAnInvalidNameWithAnInvalidCaretRule() {
    FshValueSet valueSet = new FshValueSet("All-you-can-eat").withFile("Strange.fsh").withLocation(new int[] {3, 4, 8, 24});
    CaretValueRule nameRule = new CaretValueRule("").withFile("Strange.fsh").withLocation(new int[] {4, 4, 4, 4});
    nameRule.caretPath = "name";
    nameRule.value = "All-you-can-eat";
    valueSet.rules.add(nameRule);
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertEquals("All-you-can-eat", at(exported.get(0), "name"));
    assertMatches("may not be suitable for machine processing applications such as code generation", "s", loggerSpy.getLastMessage("warn"));
    assertMatches("File: Strange\\.fsh.*Line: 4\\D*", "s", loggerSpy.getLastMessage("warn"));
  }

  @Test
  @DisplayName("ValueSetExporter should log a message when the value set overrides a valid name with an invalid Caret Rule")
  void shouldLogAMessageWhenTheValueSetOverridesAValidNameWithAnInvalidCaretRule() {
    FshValueSet valueSet = new FshValueSet("AllYouCanEat").withFile("Strange.fsh").withLocation(new int[] {3, 4, 8, 24});
    CaretValueRule nameRule = new CaretValueRule("").withFile("Strange.fsh").withLocation(new int[] {4, 4, 4, 4});
    nameRule.caretPath = "name";
    nameRule.value = "All-you-can-eat";
    valueSet.rules.add(nameRule);
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertEquals("All-you-can-eat", at(exported.get(0), "name"));
    assertMatches("may not be suitable for machine processing applications such as code generation", "s", loggerSpy.getLastMessage("warn"));
    assertMatches("File: Strange\\.fsh.*Line: 4\\D*", "s", loggerSpy.getLastMessage("warn"));
  }

  @Test
  @DisplayName("ValueSetExporter should sanitize the id and log a message when a valid name is used to make an invalid id")
  void shouldSanitizeTheIdAndLogAMessageWhenAValidNameIsUsedToMakeAnInvalidId() {
    FshValueSet valueSet = new FshValueSet("Not_good_id").withFile("Wrong.fsh").withLocation(new int[] {2, 8, 5, 18});
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals("Not_good_id", at(exported.get(0), "name"));
    assertEquals("Not-good-id", at(exported.get(0), "id"));
    assertMatches("The string \"Not_good_id\" represents a valid FHIR name but not a valid FHIR id.*The id will be exported as \"Not-good-id\"", "s", loggerSpy.getLastMessage("warn"));
    assertMatches("File: Wrong\\.fsh.*Line: 2 - 5\\D*", "s", loggerSpy.getLastMessage("warn"));
  }

  @Test
  @DisplayName("ValueSetExporter should sanitize the id and log a message when a long valid name is used to make an invalid id")
  void shouldSanitizeTheIdAndLogAMessageWhenALongValidNameIsUsedToMakeAnInvalidId() {
    String longId = "Toolong";
    while (longId.length() < 65) {
      longId += "longer";
    }
    FshValueSet valueSet = new FshValueSet(longId).withFile("Wrong.fsh").withLocation(new int[] {2, 8, 5, 18});
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    String expectedId = longId.substring(0, 64);
    assertEquals(longId, at(exported.get(0), "name"));
    assertEquals(expectedId, at(exported.get(0), "id"));
    Pattern warning = jsRegex("The string \"" + Js.toStr(longId) + "\" represents a valid FHIR name but not a valid FHIR id.*The id will be exported as \"" + Js.toStr(expectedId) + "\"", "s");
    assertMatchesPattern(warning, loggerSpy.getLastMessage("warn"));
    assertMatches("File: Wrong\\.fsh.*Line: 2 - 5\\D*", "s", loggerSpy.getLastMessage("warn"));
  }

  @Test
  @DisplayName("ValueSetExporter should log an error when multiple value sets have the same id")
  void shouldLogAnErrorWhenMultipleValueSetsHaveTheSameId() {
    FshValueSet firstValueSet = new FshValueSet("FirstVS").withFile("ValueSets.fsh").withLocation(new int[] {2, 8, 5, 15});
    firstValueSet.setId("my-value-set");
    FshValueSet secondValueSet = new FshValueSet("SecondVS").withFile("ValueSets.fsh").withLocation(new int[] {7, 8, 9, 19});
    secondValueSet.setId("my-value-set");
    doc.valueSets.put(firstValueSet.name, firstValueSet);
    doc.valueSets.put(secondValueSet.name, secondValueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(2, exported.size());
    assertMatches("Multiple value sets with id my-value-set", "s", loggerSpy.getLastMessage("error"));
    assertMatches("File: ValueSets\\.fsh.*Line: 7 - 9\\D*", "s", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("ValueSetExporter should export each value set once, even if export is called more than once")
  void shouldExportEachValueSetOnceEvenIfExportIsCalledMoreThanOnce() {
    FshValueSet breakfast = new FshValueSet("BreakfastVS");
    doc.valueSets.put(breakfast.name, breakfast);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
  }

  @Test
  @DisplayName("ValueSetExporter should export a value set that includes a component from a system")
  void shouldExportAValueSetThatIncludesAComponentFromASystem() {
    FshValueSet valueSet = new FshValueSet("DinnerVS");
    ValueSetConceptComponentRule component = new ValueSetConceptComponentRule(true);
    component.from = fromOf("http://food.org/food", null);
    valueSet.rules.add(component);
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "name": "DinnerVS",
          "id": "DinnerVS",
          "status": "draft",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/DinnerVS",
          "compose": {
            "include": [
              {
                "system": "http://food.org/food"
              }
            ]
          }
        }
        """, exported.get(0));
  }

  @Test
  @DisplayName("ValueSetExporter should export a value set that includes a component from a named system")
  void shouldExportAValueSetThatIncludesAComponentFromANamedSystem() {
    FshValueSet valueSet = new FshValueSet("DinnerVS");
    ValueSetConceptComponentRule component = new ValueSetConceptComponentRule(true);
    component.from = fromOf("FoodCS", null);
    valueSet.rules.add(component);
    doc.valueSets.put(valueSet.name, valueSet);
    FshCodeSystem foodCS = new FshCodeSystem("FoodCS");
    foodCS.setId("food");
    doc.codeSystems.put(foodCS.name, foodCS);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "name": "DinnerVS",
          "id": "DinnerVS",
          "status": "draft",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/DinnerVS",
          "compose": {
            "include": [
              {
                "system": "http://hl7.org/fhir/us/minimal/CodeSystem/food"
              }
            ]
          }
        }
        """, exported.get(0));
  }

  @Test
  @DisplayName("ValueSetExporter should export a value set that includes a component from a contained inline instance of code system and add the valueset-system extension")
  void shouldExportAValueSetThatIncludesAComponentFromAContainedInlineInstanceOfCodeSys() {
    // Instance: example-codesystem
    // InstanceOf: CodeSystem
    // Usage: #inline
    // * url = "http://example.org/codesystem"
    // * version = "1.0.0"
    // * status = #active
    // * content = #complete
    Instance inlineCodeSystem = new Instance("example-codesystem");
    inlineCodeSystem.instanceOf = "CodeSystem";
    inlineCodeSystem.usage = InstanceUsage.Inline;
    AssignmentRule urlRule = new AssignmentRule("url");
    urlRule.value = "http://example.org/codesystem";
    AssignmentRule versionRule = new AssignmentRule("version");
    versionRule.value = "1.0.0";
    AssignmentRule statusRule = new AssignmentRule("status");
    statusRule.value = new FshCode("active");
    AssignmentRule contentRule = new AssignmentRule("content");
    contentRule.value = new FshCode("complete");
    inlineCodeSystem.rules.addAll(Arrays.asList(urlRule, versionRule, statusRule, contentRule));
    doc.instances.put(inlineCodeSystem.name, inlineCodeSystem);
    // ValueSet: ExampleValueset
    // Id: example-valueset
    // * ^contained = example-codesystem
    // * include codes from system example-codesystem
    FshValueSet valueSet = new FshValueSet("ExampleValueset");
    valueSet.setId("example-valueset");
    CaretValueRule containedSystem = new CaretValueRule("");
    containedSystem.caretPath = "contained";
    containedSystem.value = "example-codesystem";
    containedSystem.isInstance = true;
    ValueSetConceptComponentRule component = new ValueSetConceptComponentRule(true);
    component.from = fromOf("example-codesystem", null);
    valueSet.rules.addAll(Arrays.asList(containedSystem, component));
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "name": "ExampleValueset",
          "id": "example-valueset",
          "status": "draft",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/example-valueset",
          "contained": [
            {
              "resourceType": "CodeSystem",
              "id": "example-codesystem",
              "url": "http://example.org/codesystem",
              "version": "1.0.0",
              "status": "active",
              "content": "complete"
            }
          ],
          "compose": {
            "include": [
              {
                "system": "http://example.org/codesystem",
                "_system": {
                  "extension": [
                    {
                      "url": "http://hl7.org/fhir/StructureDefinition/valueset-system",
                      "valueCanonical": "#example-codesystem"
                    }
                  ]
                }
              }
            ]
          }
        }
        """, exported.get(0));
    assertEquals(0, loggerSpy.getAllMessages("error").size());
  }

  @Test
  @DisplayName("ValueSetExporter should log an error and not add the component when attempting to reference an inline instance of code system that is not contained")
  void shouldLogAnErrorAndNotAddTheComponentWhenAttemptingToReferenceAnInlineInstanceOf() {
    // Instance: example-codesystem
    // InstanceOf: CodeSystem
    // Usage: #inline
    // * url = "http://example.org/codesystem"
    // * version = "1.0.0"
    // * status = #active
    // * content = #complete
    Instance inlineCodeSystem = new Instance("example-codesystem");
    inlineCodeSystem.instanceOf = "CodeSystem";
    inlineCodeSystem.usage = InstanceUsage.Inline;
    AssignmentRule urlRule = new AssignmentRule("url");
    urlRule.value = "http://example.org/codesystem";
    AssignmentRule versionRule = new AssignmentRule("version");
    versionRule.value = "1.0.0";
    AssignmentRule statusRule = new AssignmentRule("status");
    statusRule.value = new FshCode("active");
    AssignmentRule contentRule = new AssignmentRule("content");
    contentRule.value = new FshCode("complete");
    inlineCodeSystem.rules.addAll(Arrays.asList(urlRule, versionRule, statusRule, contentRule));
    doc.instances.put(inlineCodeSystem.name, inlineCodeSystem);
    // ValueSet: ExampleValueset
    // Id: example-valueset
    // * include codes from system example-codesystem
    // * include codes from system http://hl7.org/fhir/us/minimal/CodeSystem/food
    FshValueSet valueSet = new FshValueSet("ExampleValueset");
    valueSet.setId("example-valueset");
    ValueSetConceptComponentRule exampleComponent = new ValueSetConceptComponentRule(true).withFile("ExampleVS.fsh").withLocation(new int[] {5, 3, 5, 48});
    exampleComponent.from = fromOf("example-codesystem", null);
    ValueSetConceptComponentRule foodComponent = new ValueSetConceptComponentRule(true);
    foodComponent.from = fromOf("http://hl7.org/fhir/us/minimal/CodeSystem/food", null);
    valueSet.rules.addAll(Arrays.asList(exampleComponent, foodComponent));
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "name": "ExampleValueset",
          "id": "example-valueset",
          "status": "draft",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/example-valueset",
          "compose": {
            "include": [
              {
                "system": "http://hl7.org/fhir/us/minimal/CodeSystem/food"
              }
            ]
          }
        }
        """, exported.get(0));
    assertEquals(1, loggerSpy.getAllMessages("error").size());
    assertMatches("Can not reference CodeSystem example-codesystem", "s", loggerSpy.getLastMessage("error"));
    assertMatches("File: ExampleVS\\.fsh.*Line: 5\\D*", "s", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("ValueSetExporter should log a warning and export the value set when containing an example instance of code system")
  void shouldLogAWarningAndExportTheValueSetWhenContainingAnExampleInstanceOfCodeSystem() {
    // Instance: example-codesystem
    // InstanceOf: CodeSystem
    // Usage: #example
    // * url = "http://example.org/codesystem"
    // * version = "1.0.0"
    // * status = #active
    // * content = #complete
    Instance inlineCodeSystem = new Instance("example-codesystem");
    inlineCodeSystem.instanceOf = "CodeSystem";
    inlineCodeSystem.usage = InstanceUsage.Example;
    AssignmentRule urlRule = new AssignmentRule("url");
    urlRule.value = "http://example.org/codesystem";
    AssignmentRule versionRule = new AssignmentRule("version");
    versionRule.value = "1.0.0";
    AssignmentRule statusRule = new AssignmentRule("status");
    statusRule.value = new FshCode("active");
    AssignmentRule contentRule = new AssignmentRule("content");
    contentRule.value = new FshCode("complete");
    inlineCodeSystem.rules.addAll(Arrays.asList(urlRule, versionRule, statusRule, contentRule));
    doc.instances.put(inlineCodeSystem.name, inlineCodeSystem);
    // ValueSet: ExampleValueset
    // Id: example-valueset
    // * ^contained = example-codesystem
    // * include codes from system example-codesystem
    FshValueSet valueSet = new FshValueSet("ExampleValueset");
    valueSet.setId("example-valueset");
    CaretValueRule containedSystem = new CaretValueRule("").withFile("ExampleVS.fsh").withLocation(new int[] {3, 3, 3, 48});
    containedSystem.caretPath = "contained";
    containedSystem.value = "example-codesystem";
    containedSystem.isInstance = true;
    ValueSetConceptComponentRule exampleComponent = new ValueSetConceptComponentRule(true);
    exampleComponent.from = fromOf("example-codesystem", null);
    valueSet.rules.addAll(Arrays.asList(containedSystem, exampleComponent));
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "name": "ExampleValueset",
          "id": "example-valueset",
          "status": "draft",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/example-valueset",
          "contained": [
            {
              "resourceType": "CodeSystem",
              "id": "example-codesystem",
              "url": "http://example.org/codesystem",
              "version": "1.0.0",
              "status": "active",
              "content": "complete"
            }
          ],
          "compose": {
            "include": [
              {
                "system": "http://example.org/codesystem",
                "_system": {
                  "extension": [
                    {
                      "url": "http://hl7.org/fhir/StructureDefinition/valueset-system",
                      "valueCanonical": "#example-codesystem"
                    }
                  ]
                }
              }
            ]
          }
        }
        """, exported.get(0));
    assertEquals(0, loggerSpy.getAllMessages("error").size());
    assertEquals(1, loggerSpy.getAllMessages("warn").size());
    assertMatches("Contained instance \"example-codesystem\" is an example", "s", loggerSpy.getLastMessage("warn"));
    assertMatches("File: ExampleVS\\.fsh.*Line: 3\\D*", "s", loggerSpy.getLastMessage("warn"));
  }

  @Test
  @DisplayName("ValueSetExporter should export a value set that includes a component from a value set")
  void shouldExportAValueSetThatIncludesAComponentFromAValueSet() {
    FshValueSet valueSet = new FshValueSet("DinnerVS");
    ValueSetConceptComponentRule component = new ValueSetConceptComponentRule(true);
    component.from = fromOf(null, new ArrayList<>(Arrays.asList("http://food.org/food/ValueSet/hot-food", "http://food.org/food/ValueSet/cold-food")));
    valueSet.rules.add(component);
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "id": "DinnerVS",
          "name": "DinnerVS",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/DinnerVS",
          "status": "draft",
          "compose": {
            "include": [
              {
                "valueSet": [
                  "http://food.org/food/ValueSet/hot-food",
                  "http://food.org/food/ValueSet/cold-food"
                ]
              }
            ]
          }
        }
        """, exported.get(0));
  }

  @Test
  @DisplayName("ValueSetExporter should export a value set that includes a component from a value set with a version")
  void shouldExportAValueSetThatIncludesAComponentFromAValueSetWithAVersion() {
    FshValueSet valueSet = new FshValueSet("DinnerVS");
    ValueSetConceptComponentRule component = new ValueSetConceptComponentRule(true);
    component.from = fromOf(null, new ArrayList<>(Arrays.asList("http://food.org/food/ValueSet/hot-food|1.2.3")));
    valueSet.rules.add(component);
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "id": "DinnerVS",
          "name": "DinnerVS",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/DinnerVS",
          "status": "draft",
          "compose": {
            "include": [
              {
                "valueSet": [
                  "http://food.org/food/ValueSet/hot-food|1.2.3"
                ]
              }
            ]
          }
        }
        """, exported.get(0));
  }

  @Test
  @DisplayName("ValueSetExporter should export a value set that includes a component from a local value set with a version")
  void shouldExportAValueSetThatIncludesAComponentFromALocalValueSetWithAVersion() {
    FshValueSet valueSet = new FshValueSet("DinnerVS");
    ValueSetConceptComponentRule component = new ValueSetConceptComponentRule(true);
    component.from = fromOf(null, new ArrayList<>(Arrays.asList("http://food.org/food/ValueSet/hot-food|1.2.3")));
    valueSet.rules.add(component);
    doc.valueSets.put(valueSet.name, valueSet);
    FshValueSet hotFoodVS = new FshValueSet("HotFoodVS");
    hotFoodVS.setId("hot-food");
    CaretValueRule setVsUrlRule = new CaretValueRule("");
    setVsUrlRule.caretPath = "url";
    setVsUrlRule.value = "http://food.org/food/ValueSet/hot-food";
    hotFoodVS.rules.add(setVsUrlRule);
    CaretValueRule setVsVersionRule = new CaretValueRule("");
    setVsVersionRule.caretPath = "version";
    setVsVersionRule.value = "1.2.3";
    hotFoodVS.rules.add(setVsVersionRule);
    doc.valueSets.put(hotFoodVS.name, hotFoodVS);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(2, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "id": "DinnerVS",
          "name": "DinnerVS",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/DinnerVS",
          "status": "draft",
          "compose": {
            "include": [
              {
                "valueSet": [
                  "http://food.org/food/ValueSet/hot-food|1.2.3"
                ]
              }
            ]
          }
        }
        """, exported.get(0));
  }

  @Test
  @DisplayName("ValueSetExporter should export a value set that includes a component from a named value set")
  void shouldExportAValueSetThatIncludesAComponentFromANamedValueSet() {
    FshValueSet valueSet = new FshValueSet("DinnerVS");
    ValueSetConceptComponentRule component = new ValueSetConceptComponentRule(true);
    component.from = fromOf(null, new ArrayList<>(Arrays.asList("HotFoodVS", "ColdFoodVS")));
    valueSet.rules.add(component);
    doc.valueSets.put(valueSet.name, valueSet);
    FshValueSet hotFoodVS = new FshValueSet("HotFoodVS");
    hotFoodVS.setId("hot-food");
    doc.valueSets.put(hotFoodVS.name, hotFoodVS);
    FshValueSet coldFoodVS = new FshValueSet("ColdFoodVS");
    coldFoodVS.setId("cold-food");
    doc.valueSets.put(coldFoodVS.name, coldFoodVS);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(3, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "id": "DinnerVS",
          "name": "DinnerVS",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/DinnerVS",
          "status": "draft",
          "compose": {
            "include": [
              {
                "valueSet": [
                  "http://hl7.org/fhir/us/minimal/ValueSet/hot-food",
                  "http://hl7.org/fhir/us/minimal/ValueSet/cold-food"
                ]
              }
            ]
          }
        }
        """, exported.get(0));
  }

  @Test
  @DisplayName("ValueSetExporter should export a value set that includes a component from a named versioned value set")
  void shouldExportAValueSetThatIncludesAComponentFromANamedVersionedValueSet() {
    FshValueSet valueSet = new FshValueSet("DinnerVS");
    ValueSetConceptComponentRule component = new ValueSetConceptComponentRule(true);
    component.from = fromOf(null, new ArrayList<>(Arrays.asList("HotFoodVS|1.2.3", "ColdFoodVS")));
    valueSet.rules.add(component);
    doc.valueSets.put(valueSet.name, valueSet);
    FshValueSet hotFoodVS = new FshValueSet("HotFoodVS");
    hotFoodVS.setId("hot-food");
    CaretValueRule setHotVsVersionRule = new CaretValueRule("");
    setHotVsVersionRule.caretPath = "version";
    setHotVsVersionRule.value = "1.2.3";
    hotFoodVS.rules.add(setHotVsVersionRule);
    doc.valueSets.put(hotFoodVS.name, hotFoodVS);
    FshValueSet coldFoodVS = new FshValueSet("ColdFoodVS");
    coldFoodVS.setId("cold-food");
    doc.valueSets.put(coldFoodVS.name, coldFoodVS);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(3, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "id": "DinnerVS",
          "name": "DinnerVS",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/DinnerVS",
          "status": "draft",
          "compose": {
            "include": [
              {
                "valueSet": [
                  "http://hl7.org/fhir/us/minimal/ValueSet/hot-food|1.2.3",
                  "http://hl7.org/fhir/us/minimal/ValueSet/cold-food"
                ]
              }
            ]
          }
        }
        """, exported.get(0));
    assertEquals(0, loggerSpy.getAllMessages("error").size());
    assertEquals(0, loggerSpy.getAllMessages("warn").size());
  }

  @Test
  @DisplayName("ValueSetExporter should export a value set that includes a component from a named versioned value set and warn on version mismatch")
  void shouldExportAValueSetThatIncludesAComponentFromANamedVersionedValueSetAndWarnOnV() {
    FshValueSet valueSet = new FshValueSet("DinnerVS");
    ValueSetConceptComponentRule component = new ValueSetConceptComponentRule(true);
    component.from = fromOf(null, new ArrayList<>(Arrays.asList("HotFoodVS|4.5.6", "ColdFoodVS")));
    valueSet.rules.add(component);
    doc.valueSets.put(valueSet.name, valueSet);
    FshValueSet hotFoodVS = new FshValueSet("HotFoodVS");
    hotFoodVS.setId("hot-food");
    CaretValueRule setHotVsVersionRule = new CaretValueRule("");
    setHotVsVersionRule.caretPath = "version";
    setHotVsVersionRule.value = "1.2.3";
    hotFoodVS.rules.add(setHotVsVersionRule);
    doc.valueSets.put(hotFoodVS.name, hotFoodVS);
    FshValueSet coldFoodVS = new FshValueSet("ColdFoodVS");
    coldFoodVS.setId("cold-food");
    doc.valueSets.put(coldFoodVS.name, coldFoodVS);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(3, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "id": "DinnerVS",
          "name": "DinnerVS",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/DinnerVS",
          "status": "draft",
          "compose": {
            "include": [
              {
                "valueSet": [
                  "http://hl7.org/fhir/us/minimal/ValueSet/hot-food|4.5.6",
                  "http://hl7.org/fhir/us/minimal/ValueSet/cold-food"
                ]
              }
            ]
          }
        }
        """, exported.get(0));
    assertEquals(0, loggerSpy.getAllMessages("error").size());
    assertEquals(1, loggerSpy.getAllMessages("warn").size());
    assertEquals("HotFoodVS|4.5.6 was requested, but SUSHI found HotFoodVS|1.2.3", loggerSpy.getLastMessage("warn"));
  }

  // TODO: as part of a later task, confirm that this is in fact correct. it seems to be what the IG publisher expects,
  // but doesn't quite fit the spec for ValueSet.compose.include.valueSet
  @Test
  @DisplayName("ValueSetExporter should export a value set that includes a component from a contained inline instance of value set")
  @Disabled("it.skip")
  void shouldExportAValueSetThatIncludesAComponentFromAContainedInlineInstanceOfValueSe() {
    // Instance: inline-valueset
    // InstanceOf: ValueSet
    // Usage: #inline
    // * url = "http://example.org/inline-value-set"
    // * status = #draft
    // * compose.include[0].system = "http://example.org/SomeCS"
    Instance inlineValueSet = new Instance("inline-valueset");
    inlineValueSet.instanceOf = "ValueSet";
    inlineValueSet.usage = InstanceUsage.Inline;
    AssignmentRule urlRule = new AssignmentRule("url");
    urlRule.value = "http://example.org/inline-value-set";
    AssignmentRule statusRule = new AssignmentRule("status");
    statusRule.value = new FshCode("draft");
    AssignmentRule systemRule = new AssignmentRule("compose.include[0].system");
    systemRule.value = "http://example.org/SomeCS";
    inlineValueSet.rules.addAll(Arrays.asList(urlRule, statusRule, systemRule));
    doc.instances.put(inlineValueSet.name, inlineValueSet);
    // ValueSet: ExampleValueset
    // Id: example-valueset
    // * ^contained = inline-valueset
    // * include codes from valueset inline-valueset
    FshValueSet valueSet = new FshValueSet("ExampleValueset");
    valueSet.setId("example-valueset");
    CaretValueRule containedVS = new CaretValueRule("");
    containedVS.caretPath = "contained";
    containedVS.value = "inline-valueset";
    containedVS.isInstance = true;
    ValueSetConceptComponentRule component = new ValueSetConceptComponentRule(true);
    component.from = fromOf(null, new ArrayList<>(Arrays.asList("inline-valueset")));
    valueSet.rules.addAll(Arrays.asList(containedVS, component));
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "id": "example-valueset",
          "name": "ExampleValueset",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/example-valueset",
          "status": "draft",
          "contained": [
            {
              "resourceType": "ValueSet",
              "id": "inline-valueset",
              "url": "http://example.org/inline-value-set",
              "status": "draft",
              "compose": {
                "include": [
                  {
                    "system": "http://example.org/SomeCS"
                  }
                ]
              }
            }
          ],
          "compose": {
            "include": [
              {
                "valueSet": [
                  "#inline-valueset"
                ]
              }
            ]
          }
        }
        """, exported.get(0));
  }

  @Test
  @DisplayName("ValueSetExporter should throw error for caret rule on valueset compose component without any concept")
  void shouldThrowErrorForCaretRuleOnValuesetComposeComponentWithoutAnyConcept() {
    // ValueSet: SomeVS
    // * include codes from system http://example.org/CS
    // * http://example.org/CS#"some-code" ^designation.value = "some value"
    FshValueSet valueSet = new FshValueSet("SomeVS");
    ValueSetConceptComponentRule component = new ValueSetConceptComponentRule(true);
    setProp(component.from, "system", "http://example.org/CS");
    CaretValueRule cvRule = new CaretValueRule("");
    cvRule.pathArray = new ArrayList<>(Arrays.asList("http://example.org/CS#\"some-code\""));
    cvRule.caretPath = "designation.value";
    cvRule.value = "some value";
    valueSet.rules.addAll(Arrays.asList(component, cvRule));
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertEquals(1, loggerSpy.getAllMessages("error").size());
    assertEquals("Cannot process caret assignment rule for code http://example.org/CS#\"some-code\" " + "because this value set does not explicitly include or exclude this code in its " + "rules. To fix this error, add a rule that specifically includes or excludes this " + "code for the value set.", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("ValueSetExporter should export a value set with a contained resource created on the value set")
  void shouldExportAValueSetWithAContainedResourceCreatedOnTheValueSet() {
    // ValueSet: DinnerVS
    // * ^contained.resourceType = "Observation"
    // * ^contained.id = "my-observation"
    // * ^contained.status = #draft
    // * ^contained.code = #123
    // * ^contained.valueString = "contained observation"
    // * include codes from system http://food.org/food
    FshValueSet valueSet = new FshValueSet("DinnerVS");
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
    ValueSetConceptComponentRule foodCodes = new ValueSetConceptComponentRule(true);
    foodCodes.from = fromOf("http://food.org/food", null);
    valueSet.rules.addAll(Arrays.asList(containedResourceType, containedId, containedStatus, containedCode, containedValue, foodCodes));
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "name": "DinnerVS",
          "id": "DinnerVS",
          "status": "draft",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/DinnerVS",
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
          "compose": {
            "include": [
              {
                "system": "http://food.org/food"
              }
            ]
          }
        }
        """, exported.get(0));
  }

  @Test
  @DisplayName("ValueSetExporter should export a value set with a contained resource modified on the value set")
  void shouldExportAValueSetWithAContainedResourceModifiedOnTheValueSet() {
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
    // ValueSet: DinnerVS
    // * ^contained = MyObservation
    // * ^contained.valueString = "contained observation"
    // * include codes from system http://food.org/food
    FshValueSet valueSet = new FshValueSet("DinnerVS");
    CaretValueRule containedInstance = new CaretValueRule("");
    containedInstance.caretPath = "contained";
    containedInstance.value = "MyObservation";
    containedInstance.isInstance = true;
    CaretValueRule containedValue = new CaretValueRule("");
    containedValue.caretPath = "contained.valueString";
    containedValue.value = "contained observation";
    ValueSetConceptComponentRule foodCodes = new ValueSetConceptComponentRule(true);
    foodCodes.from = fromOf("http://food.org/food", null);
    valueSet.rules.addAll(Arrays.asList(containedInstance, containedValue, foodCodes));
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "name": "DinnerVS",
          "id": "DinnerVS",
          "status": "draft",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/DinnerVS",
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
          "compose": {
            "include": [
              {
                "system": "http://food.org/food"
              }
            ]
          }
        }
        """, exported.get(0));
  }

  @Test
  @DisplayName("ValueSetExporter should log a warning and export a value set with a contained example resource with a numeric id modified on the value set")
  void shouldLogAWarningAndExportAValueSetWithAContainedExampleResourceWithANumericIdMo() {
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
    // ValueSet: DinnerVS
    // * ^contained = 555
    // * ^contained.valueString = "contained observation"
    // * include codes from system http://food.org/food
    FshValueSet valueSet = new FshValueSet("DinnerVS");
    CaretValueRule containedInstance = new CaretValueRule("").withFile("ValueSet.fsh").withLocation(new int[] {2, 3, 2, 24});
    containedInstance.caretPath = "contained";
    containedInstance.value = new java.math.BigInteger("555");
    containedInstance.rawValue = "555";
    CaretValueRule containedValue = new CaretValueRule("");
    containedValue.caretPath = "contained.valueString";
    containedValue.value = "contained observation";
    ValueSetConceptComponentRule foodCodes = new ValueSetConceptComponentRule(true);
    foodCodes.from = fromOf("http://food.org/food", null);
    valueSet.rules.addAll(Arrays.asList(containedInstance, containedValue, foodCodes));
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "name": "DinnerVS",
          "id": "DinnerVS",
          "status": "draft",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/DinnerVS",
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
          "compose": {
            "include": [
              {
                "system": "http://food.org/food"
              }
            ]
          }
        }
        """, exported.get(0));
    assertEquals(0, loggerSpy.getAllMessages("error").size());
    assertEquals(1, loggerSpy.getAllMessages("warn").size());
    assertMatches("Contained instance \"555\" is an example", "s", loggerSpy.getLastMessage("warn"));
    assertMatches("File: ValueSet\\.fsh.*Line: 2\\D*", "s", loggerSpy.getLastMessage("warn"));
  }

  @Test
  @DisplayName("ValueSetExporter should export a value set that includes a component from a contained code system created on the value set and referenced by id")
  void shouldExportAValueSetThatIncludesAComponentFromAContainedCodeSystemCreatedOnTheV() {
    // ValueSet: ExampleValueset
    // Id: example-valueset
    // * ^contained.resourceType = "CodeSystem"
    // * ^contained.id = "example-codesystem"
    // * ^contained.name = "ExampleCodesystem"
    // * ^contained.url = "http://example.org/codesystem"
    // * ^contained.content = #complete
    // * ^contained.concept[0].code = #example-code-1
    // * ^contained.concept[0].display = "Example Code 1"
    // * include codes from system example-codesystem
    FshValueSet valueSet = new FshValueSet("ExampleValueset");
    valueSet.setId("example-valueset");
    CaretValueRule containedResourceType = new CaretValueRule("");
    containedResourceType.caretPath = "contained.resourceType";
    containedResourceType.value = "CodeSystem";
    CaretValueRule containedId = new CaretValueRule("");
    containedId.caretPath = "contained.id";
    containedId.value = "example-codesystem";
    CaretValueRule containedName = new CaretValueRule("");
    containedName.caretPath = "contained.name";
    containedName.value = "ExampleCodesystem";
    CaretValueRule containedUrl = new CaretValueRule("");
    containedUrl.caretPath = "contained.url";
    containedUrl.value = "http://example.org/codesystem";
    CaretValueRule containedContent = new CaretValueRule("");
    containedContent.caretPath = "contained.content";
    containedContent.value = new FshCode("complete");
    CaretValueRule containedCode = new CaretValueRule("");
    containedCode.caretPath = "contained.concept[0].code";
    containedCode.value = new FshCode("example-code-1");
    CaretValueRule containedDisplay = new CaretValueRule("");
    containedDisplay.caretPath = "contained.concept[0].display";
    containedDisplay.value = "Example Code 1";
    ValueSetConceptComponentRule includeCodes = new ValueSetConceptComponentRule(true);
    includeCodes.from = fromOf("example-codesystem", null);
    valueSet.rules.addAll(Arrays.asList(containedResourceType, containedId, containedName, containedUrl, containedContent, containedCode, containedDisplay, includeCodes));
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "name": "ExampleValueset",
          "id": "example-valueset",
          "status": "draft",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/example-valueset",
          "contained": [
            {
              "resourceType": "CodeSystem",
              "id": "example-codesystem",
              "name": "ExampleCodesystem",
              "url": "http://example.org/codesystem",
              "content": "complete",
              "concept": [
                {
                  "code": "example-code-1",
                  "display": "Example Code 1"
                }
              ]
            }
          ],
          "compose": {
            "include": [
              {
                "system": "http://example.org/codesystem",
                "_system": {
                  "extension": [
                    {
                      "url": "http://hl7.org/fhir/StructureDefinition/valueset-system",
                      "valueCanonical": "#example-codesystem"
                    }
                  ]
                }
              }
            ]
          }
        }
        """, exported.get(0));
    assertEquals(0, loggerSpy.getAllMessages("error").size());
  }

  @Test
  @DisplayName("ValueSetExporter should export a value set that includes a component from a contained code system created on the value set and referenced by name")
  void shouldExportAValueSetThatIncludesAComponentFromAContainedCodeSystemCreatedOnTheV2() {
    // ValueSet: ExampleValueset
    // Id: example-valueset
    // * ^contained.resourceType = "CodeSystem"
    // * ^contained.id = "example-codesystem"
    // * ^contained.name = "ExampleCodesystem"
    // * ^contained.url = "http://example.org/codesystem"
    // * ^contained.content = #complete
    // * ^contained.concept[0].code = #example-code-1
    // * ^contained.concept[0].display = "Example Code 1"
    // * include codes from system ExampleCodesystem
    FshValueSet valueSet = new FshValueSet("ExampleValueset");
    valueSet.setId("example-valueset");
    CaretValueRule containedResourceType = new CaretValueRule("");
    containedResourceType.caretPath = "contained.resourceType";
    containedResourceType.value = "CodeSystem";
    CaretValueRule containedId = new CaretValueRule("");
    containedId.caretPath = "contained.id";
    containedId.value = "example-codesystem";
    CaretValueRule containedName = new CaretValueRule("");
    containedName.caretPath = "contained.name";
    containedName.value = "ExampleCodesystem";
    CaretValueRule containedUrl = new CaretValueRule("");
    containedUrl.caretPath = "contained.url";
    containedUrl.value = "http://example.org/codesystem";
    CaretValueRule containedContent = new CaretValueRule("");
    containedContent.caretPath = "contained.content";
    containedContent.value = new FshCode("complete");
    CaretValueRule containedCode = new CaretValueRule("");
    containedCode.caretPath = "contained.concept[0].code";
    containedCode.value = new FshCode("example-code-1");
    CaretValueRule containedDisplay = new CaretValueRule("");
    containedDisplay.caretPath = "contained.concept[0].display";
    containedDisplay.value = "Example Code 1";
    ValueSetConceptComponentRule includeCodes = new ValueSetConceptComponentRule(true);
    includeCodes.from = fromOf("ExampleCodesystem", null);
    valueSet.rules.addAll(Arrays.asList(containedResourceType, containedId, containedName, containedUrl, containedContent, containedCode, containedDisplay, includeCodes));
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "name": "ExampleValueset",
          "id": "example-valueset",
          "status": "draft",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/example-valueset",
          "contained": [
            {
              "resourceType": "CodeSystem",
              "id": "example-codesystem",
              "name": "ExampleCodesystem",
              "url": "http://example.org/codesystem",
              "content": "complete",
              "concept": [
                {
                  "code": "example-code-1",
                  "display": "Example Code 1"
                }
              ]
            }
          ],
          "compose": {
            "include": [
              {
                "system": "http://example.org/codesystem",
                "_system": {
                  "extension": [
                    {
                      "url": "http://hl7.org/fhir/StructureDefinition/valueset-system",
                      "valueCanonical": "#example-codesystem"
                    }
                  ]
                }
              }
            ]
          }
        }
        """, exported.get(0));
    assertEquals(0, loggerSpy.getAllMessages("error").size());
  }

  @Test
  @DisplayName("ValueSetExporter should export a value set that includes a component from a contained code system created on the value set and referenced by url")
  void shouldExportAValueSetThatIncludesAComponentFromAContainedCodeSystemCreatedOnTheV3() {
    // ValueSet: ExampleValueset
    // Id: example-valueset
    // * ^contained.resourceType = "CodeSystem"
    // * ^contained.id = "example-codesystem"
    // * ^contained.name = "ExampleCodesystem"
    // * ^contained.url = "http://example.org/codesystem"
    // * ^contained.content = #complete
    // * ^contained.concept[0].code = #example-code-1
    // * ^contained.concept[0].display = "Example Code 1"
    // * include codes from system http://example.org/codesystem
    FshValueSet valueSet = new FshValueSet("ExampleValueset");
    valueSet.setId("example-valueset");
    CaretValueRule containedResourceType = new CaretValueRule("");
    containedResourceType.caretPath = "contained.resourceType";
    containedResourceType.value = "CodeSystem";
    CaretValueRule containedId = new CaretValueRule("");
    containedId.caretPath = "contained.id";
    containedId.value = "example-codesystem";
    CaretValueRule containedName = new CaretValueRule("");
    containedName.caretPath = "contained.name";
    containedName.value = "ExampleCodesystem";
    CaretValueRule containedUrl = new CaretValueRule("");
    containedUrl.caretPath = "contained.url";
    containedUrl.value = "http://example.org/codesystem";
    CaretValueRule containedContent = new CaretValueRule("");
    containedContent.caretPath = "contained.content";
    containedContent.value = new FshCode("complete");
    CaretValueRule containedCode = new CaretValueRule("");
    containedCode.caretPath = "contained.concept[0].code";
    containedCode.value = new FshCode("example-code-1");
    CaretValueRule containedDisplay = new CaretValueRule("");
    containedDisplay.caretPath = "contained.concept[0].display";
    containedDisplay.value = "Example Code 1";
    ValueSetConceptComponentRule includeCodes = new ValueSetConceptComponentRule(true);
    includeCodes.from = fromOf("http://example.org/codesystem", null);
    valueSet.rules.addAll(Arrays.asList(containedResourceType, containedId, containedName, containedUrl, containedContent, containedCode, containedDisplay, includeCodes));
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "name": "ExampleValueset",
          "id": "example-valueset",
          "status": "draft",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/example-valueset",
          "contained": [
            {
              "resourceType": "CodeSystem",
              "id": "example-codesystem",
              "name": "ExampleCodesystem",
              "url": "http://example.org/codesystem",
              "content": "complete",
              "concept": [
                {
                  "code": "example-code-1",
                  "display": "Example Code 1"
                }
              ]
            }
          ],
          "compose": {
            "include": [
              {
                "system": "http://example.org/codesystem",
                "_system": {
                  "extension": [
                    {
                      "url": "http://hl7.org/fhir/StructureDefinition/valueset-system",
                      "valueCanonical": "#example-codesystem"
                    }
                  ]
                }
              }
            ]
          }
        }
        """, exported.get(0));
    assertEquals(0, loggerSpy.getAllMessages("error").size());
  }

  @Test
  @DisplayName("ValueSetExporter should not use a contained resource created on the value set as a component system when that resource is not a CodeSystem")
  void shouldNotUseAContainedResourceCreatedOnTheValueSetAsAComponentSystemWhenThatReso() {
    // ValueSet: ExampleValueset
    // Id: example-valueset
    // * ^contained.resourceType = "Observation"
    // * ^contained.id = "my-observation"
    // * ^contained.status = #draft
    // * ^contained.code = #123
    // * ^contained.valueString = "contained observation"
    // * include codes from system my-observation
    FshValueSet valueSet = new FshValueSet("ExampleValueset").withFile("ValueSet.fsh").withLocation(new int[] {1, 3, 9, 29});
    valueSet.setId("example-valueset");
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
    ValueSetConceptComponentRule observationCodes = new ValueSetConceptComponentRule(true);
    observationCodes.from = fromOf("my-observation", null);
    valueSet.rules.addAll(Arrays.asList(containedResourceType, containedId, containedStatus, containedCode, containedValue, observationCodes));
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(0, exported.size());
    assertMatches("Resolved value \"my-observation\" is not a valid URI", "s", loggerSpy.getLastMessage("error"));
    assertMatches("File: ValueSet\\.fsh.*Line: 1 - 9\\D*", "s", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("ValueSetExporter should remove and log error when exporting a value set that includes a component from a self referencing value set")
  void shouldRemoveAndLogErrorWhenExportingAValueSetThatIncludesAComponentFromASelfRefe() {
    FshValueSet valueSet = new FshValueSet("DinnerVS");
    valueSet.setId("dinner-vs");
    ValueSetConceptComponentRule component = new ValueSetConceptComponentRule(true);
    component.from = fromOf("http://food.org/food1", new ArrayList<>(Arrays.asList("http://food.org/food/ValueSet/hot-food", "http://food.org/food/ValueSet/cold-food", "DinnerVS", "http://hl7.org/fhir/us/minimal/ValueSet/dinner-vs", "dinner-vs")));
    ValueSetConceptComponentRule component2 = new ValueSetConceptComponentRule(true);
    component2.from = fromOf("http://food.org/food2", new ArrayList<>(Arrays.asList("DinnerVS", "http://hl7.org/fhir/us/minimal/ValueSet/dinner-vs", "dinner-vs")));
    valueSet.rules.add(component);
    valueSet.rules.add(component2);
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "id": "dinner-vs",
          "name": "DinnerVS",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/dinner-vs",
          "status": "draft",
          "compose": {
            "include": [
              {
                "system": "http://food.org/food1",
                "valueSet": [
                  "http://food.org/food/ValueSet/hot-food",
                  "http://food.org/food/ValueSet/cold-food"
                ]
              },
              {
                "system": "http://food.org/food2"
              }
            ]
          }
        }
        """, exported.get(0));
    assertEquals(6, loggerSpy.getAllMessages("error").size());
    assertEquals("Value set with id dinner-vs has component rule with self-referencing value set (by id, value set name, or url). Removing self-reference.", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("ValueSetExporter should export a value set that includes a concept component with at least one concept")
  void shouldExportAValueSetThatIncludesAConceptComponentWithAtLeastOneConcept() {
    FshValueSet valueSet = new FshValueSet("DinnerVS");
    ValueSetConceptComponentRule component = new ValueSetConceptComponentRule(true);
    component.from = fromOf("http://food.org/food", null);
    component.concepts.add(new FshCode("Pizza", "http://food.org/food", "Delicious pizza to share."));
    component.concepts.add(new FshCode("Salad", "http://food.org/food", "Plenty of fresh vegetables."));
    component.concepts.add(new FshCode("Mulch", "http://food.org/food"));
    valueSet.rules.add(component);
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "id": "DinnerVS",
          "name": "DinnerVS",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/DinnerVS",
          "status": "draft",
          "compose": {
            "include": [
              {
                "system": "http://food.org/food",
                "concept": [
                  {
                    "code": "Pizza",
                    "display": "Delicious pizza to share."
                  },
                  {
                    "code": "Salad",
                    "display": "Plenty of fresh vegetables."
                  },
                  {
                    "code": "Mulch"
                  }
                ]
              }
            ]
          }
        }
        """, exported.get(0));
  }

  @Test
  @DisplayName("ValueSetExporter should export a value set that includes a concept component from a local complete code system name with at least one concept")
  void shouldExportAValueSetThatIncludesAConceptComponentFromALocalCompleteCodeSystemNa() {
    FshValueSet valueSet = new FshValueSet("DinnerVS");
    ValueSetConceptComponentRule component = new ValueSetConceptComponentRule(true);
    component.from = fromOf("FoodCS", null);
    component.concepts.add(new FshCode("Pizza", "FoodCS", "Delicious pizza to share."));
    component.concepts.add(new FshCode("Salad", "FoodCS", "Plenty of fresh vegetables."));
    valueSet.rules.add(component);
    doc.valueSets.put(valueSet.name, valueSet);
    FshCodeSystem foodCS = new FshCodeSystem("FoodCS");
    foodCS.setId("food");
    foodCS.rules.add(new ConceptRule("Pizza", "Delicious pizza to share."));
    foodCS.rules.add(new ConceptRule("Salad", "Plenty of fresh vegetables."));
    doc.codeSystems.put(foodCS.name, foodCS);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "name": "DinnerVS",
          "id": "DinnerVS",
          "status": "draft",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/DinnerVS",
          "compose": {
            "include": [
              {
                "system": "http://hl7.org/fhir/us/minimal/CodeSystem/food",
                "concept": [
                  {
                    "code": "Pizza",
                    "display": "Delicious pizza to share."
                  },
                  {
                    "code": "Salad",
                    "display": "Plenty of fresh vegetables."
                  }
                ]
              }
            ]
          }
        }
        """, exported.get(0));
    assertEquals(0, loggerSpy.getAllMessages("error").size());
  }

  @Test
  @DisplayName("ValueSetExporter should export a value set that includes a concept component from a local complete code system name with concepts added by CaretValueRules")
  void shouldExportAValueSetThatIncludesAConceptComponentFromALocalCompleteCodeSystemNa2() {
    FshValueSet valueSet = new FshValueSet("DinnerVS");
    ValueSetConceptComponentRule component = new ValueSetConceptComponentRule(true);
    component.from = fromOf("FoodCS", null);
    component.concepts.add(new FshCode("Pizza", "FoodCS", "Delicious pizza to share."));
    component.concepts.add(new FshCode("Salad", "FoodCS", "Plenty of fresh vegetables."));
    valueSet.rules.add(component);
    doc.valueSets.put(valueSet.name, valueSet);
    FshCodeSystem foodCS = new FshCodeSystem("FoodCS");
    foodCS.setId("food");
    CaretValueRule pizzaCode = new CaretValueRule("");
    pizzaCode.caretPath = "concept[0].code";
    pizzaCode.value = new FshCode("Pizza");
    CaretValueRule pizzaDisplay = new CaretValueRule("");
    pizzaDisplay.caretPath = "concept[0].display";
    pizzaDisplay.value = "Delicious pizza to share.";
    CaretValueRule saladCode = new CaretValueRule("");
    saladCode.caretPath = "concept[ 1 ].code";
    saladCode.value = new FshCode("Salad");
    CaretValueRule saladDisplay = new CaretValueRule("");
    saladDisplay.caretPath = "concept[1].display";
    saladDisplay.value = "Plenty of fresh vegetables.";
    foodCS.rules.addAll(Arrays.asList(pizzaCode, pizzaDisplay, saladCode, saladDisplay));
    doc.codeSystems.put(foodCS.name, foodCS);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "name": "DinnerVS",
          "id": "DinnerVS",
          "status": "draft",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/DinnerVS",
          "compose": {
            "include": [
              {
                "system": "http://hl7.org/fhir/us/minimal/CodeSystem/food",
                "concept": [
                  {
                    "code": "Pizza",
                    "display": "Delicious pizza to share."
                  },
                  {
                    "code": "Salad",
                    "display": "Plenty of fresh vegetables."
                  }
                ]
              }
            ]
          }
        }
        """, exported.get(0));
    assertEquals(0, loggerSpy.getAllMessages("error").size());
  }

  @Test
  @DisplayName("ValueSetExporter should export a value set that includes a concept component from a local complete code system name with at least one concept added by a RuleSet")
  void shouldExportAValueSetThatIncludesAConceptComponentFromALocalCompleteCodeSystemNa3() {
    FshValueSet valueSet = new FshValueSet("DinnerVS");
    ValueSetConceptComponentRule component = new ValueSetConceptComponentRule(true);
    component.from = fromOf("FoodCS", null);
    component.concepts.add(new FshCode("Pizza", "FoodCS", "Delicious pizza to share."));
    component.concepts.add(new FshCode("Salad", "FoodCS", "Plenty of fresh vegetables."));
    valueSet.rules.add(component);
    doc.valueSets.put(valueSet.name, valueSet);
    RuleSet ruleSet = new RuleSet("ExtraFoodRules");
    ruleSet.rules.add(new ConceptRule("Salad", "Plenty of fresh vegetables."));
    doc.ruleSets.put(ruleSet.name, ruleSet);
    FshCodeSystem foodCS = new FshCodeSystem("FoodCS");
    foodCS.setId("food");
    foodCS.rules.add(new ConceptRule("Pizza", "Delicious pizza to share."));
    foodCS.rules.add(new ConceptRule("Fruit", "Get that good fruit."));
    InsertRule insertRule = new InsertRule("");
    insertRule.ruleSet = "ExtraFoodRules";
    foodCS.rules.add(insertRule);
    doc.codeSystems.put(foodCS.name, foodCS);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "name": "DinnerVS",
          "id": "DinnerVS",
          "status": "draft",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/DinnerVS",
          "compose": {
            "include": [
              {
                "system": "http://hl7.org/fhir/us/minimal/CodeSystem/food",
                "concept": [
                  {
                    "code": "Pizza",
                    "display": "Delicious pizza to share."
                  },
                  {
                    "code": "Salad",
                    "display": "Plenty of fresh vegetables."
                  }
                ]
              }
            ]
          }
        }
        """, exported.get(0));
    assertEquals(0, loggerSpy.getAllMessages("error").size());
  }

  @Test
  @DisplayName("ValueSetExporter should export a value set that includes a concept component from a local complete CodeSystem instance name with at least one concept")
  void shouldExportAValueSetThatIncludesAConceptComponentFromALocalCompleteCodeSystemIn() {
    FshValueSet valueSet = new FshValueSet("DinnerVS");
    ValueSetConceptComponentRule component = new ValueSetConceptComponentRule(true);
    component.from = fromOf("FoodCS", null);
    component.concepts.add(new FshCode("Pizza", "FoodCS", "Delicious pizza to share."));
    component.concepts.add(new FshCode("Salad", "FoodCS", "Plenty of fresh vegetables."));
    valueSet.rules.add(component);
    doc.valueSets.put(valueSet.name, valueSet);
    Instance foodCS = new Instance("FoodCS");
    foodCS.instanceOf = "CodeSystem";
    foodCS.usage = InstanceUsage.Definition;
    AssignmentRule urlRule = new AssignmentRule("url");
    urlRule.value = "http://hl7.org/fhir/us/minimal/Instance/food";
    AssignmentRule contentRule = new AssignmentRule("content");
    contentRule.value = new FshCode("supplement");
    AssignmentRule pizzaCode = new AssignmentRule("concept[0].code");
    pizzaCode.value = new FshCode("Pizza");
    AssignmentRule saladCode = new AssignmentRule("concept[1].code");
    saladCode.value = new FshCode("Salad");
    foodCS.rules.addAll(Arrays.asList(urlRule, contentRule, pizzaCode, saladCode));
    doc.instances.put(foodCS.name, foodCS);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "name": "DinnerVS",
          "id": "DinnerVS",
          "status": "draft",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/DinnerVS",
          "compose": {
            "include": [
              {
                "system": "http://hl7.org/fhir/us/minimal/Instance/food",
                "concept": [
                  {
                    "code": "Pizza",
                    "display": "Delicious pizza to share."
                  },
                  {
                    "code": "Salad",
                    "display": "Plenty of fresh vegetables."
                  }
                ]
              }
            ]
          }
        }
        """, exported.get(0));
    assertEquals(0, loggerSpy.getAllMessages("error").size());
  }

  @Test
  @DisplayName("ValueSetExporter should export a value set that includes a concept component from a local complete CodeSystem instance name with at least one concept added by a RuleSet")
  void shouldExportAValueSetThatIncludesAConceptComponentFromALocalCompleteCodeSystemIn2() {
    FshValueSet valueSet = new FshValueSet("DinnerVS");
    ValueSetConceptComponentRule component = new ValueSetConceptComponentRule(true);
    component.from = fromOf("FoodCS", null);
    component.concepts.add(new FshCode("Pizza", "FoodCS", "Delicious pizza to share."));
    component.concepts.add(new FshCode("Salad", "FoodCS", "Plenty of fresh vegetables."));
    valueSet.rules.add(component);
    doc.valueSets.put(valueSet.name, valueSet);
    RuleSet ruleSet = new RuleSet("ExtraFoodRules");
    AssignmentRule saladCode = new AssignmentRule("concept[+].code");
    saladCode.value = new FshCode("Salad");
    ruleSet.rules.add(saladCode);
    doc.ruleSets.put(ruleSet.name, ruleSet);
    Instance foodCS = new Instance("FoodCS");
    foodCS.instanceOf = "CodeSystem";
    foodCS.usage = InstanceUsage.Definition;
    AssignmentRule urlRule = new AssignmentRule("url");
    urlRule.value = "http://hl7.org/fhir/us/minimal/Instance/food";
    AssignmentRule contentRule = new AssignmentRule("content");
    contentRule.value = new FshCode("complete");
    AssignmentRule pizzaCode = new AssignmentRule("concept[0].code");
    pizzaCode.value = new FshCode("Pizza");
    AssignmentRule fruitCode = new AssignmentRule("concept[1].code");
    fruitCode.value = new FshCode("Fruit");
    InsertRule insertRule = new InsertRule("");
    insertRule.ruleSet = "ExtraFoodRules";
    foodCS.rules.addAll(Arrays.asList(urlRule, contentRule, pizzaCode, fruitCode, insertRule));
    doc.instances.put(foodCS.name, foodCS);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "name": "DinnerVS",
          "id": "DinnerVS",
          "status": "draft",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/DinnerVS",
          "compose": {
            "include": [
              {
                "system": "http://hl7.org/fhir/us/minimal/Instance/food",
                "concept": [
                  {
                    "code": "Pizza",
                    "display": "Delicious pizza to share."
                  },
                  {
                    "code": "Salad",
                    "display": "Plenty of fresh vegetables."
                  }
                ]
              }
            ]
          }
        }
        """, exported.get(0));
    assertEquals(0, loggerSpy.getAllMessages("error").size());
  }

  @Test
  @DisplayName("ValueSetExporter should export a value set that includes a concept component from a local incomplete CodeSystem when the concept is not in the system")
  void shouldExportAValueSetThatIncludesAConceptComponentFromALocalIncompleteCodeSystem() {
    FshValueSet valueSet = new FshValueSet("DinnerVS");
    ValueSetConceptComponentRule component = new ValueSetConceptComponentRule(true);
    component.from = fromOf("FoodCS", null);
    component.concepts.add(new FshCode("Pizza", "FoodCS", "Delicious pizza to share."));
    component.concepts.add(new FshCode("Salad", "FoodCS", "Plenty of fresh vegetables."));
    valueSet.rules.add(component);
    doc.valueSets.put(valueSet.name, valueSet);
    FshCodeSystem foodCS = new FshCodeSystem("FoodCS");
    foodCS.setId("food");
    foodCS.rules.add(new ConceptRule("Pizza", "Delicious pizza to share."));
    CaretValueRule contentRule = new CaretValueRule("");
    contentRule.caretPath = "content";
    contentRule.value = new FshCode("fragment");
    foodCS.rules.add(contentRule);
    doc.codeSystems.put(foodCS.name, foodCS);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "name": "DinnerVS",
          "id": "DinnerVS",
          "status": "draft",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/DinnerVS",
          "compose": {
            "include": [
              {
                "system": "http://hl7.org/fhir/us/minimal/CodeSystem/food",
                "concept": [
                  {
                    "code": "Pizza",
                    "display": "Delicious pizza to share."
                  },
                  {
                    "code": "Salad",
                    "display": "Plenty of fresh vegetables."
                  }
                ]
              }
            ]
          }
        }
        """, exported.get(0));
    assertEquals(0, loggerSpy.getAllMessages("error").size());
  }

  @Test
  @DisplayName("ValueSetExporter should export a value set that includes a concept component from a local incomplete CodeSystem instance when the concept is not in the system")
  void shouldExportAValueSetThatIncludesAConceptComponentFromALocalIncompleteCodeSystem2() {
    FshValueSet valueSet = new FshValueSet("DinnerVS");
    ValueSetConceptComponentRule component = new ValueSetConceptComponentRule(true);
    component.from = fromOf("FoodCS", null);
    component.concepts.add(new FshCode("Pizza", "FoodCS", "Delicious pizza to share."));
    component.concepts.add(new FshCode("Salad", "FoodCS", "Plenty of fresh vegetables."));
    valueSet.rules.add(component);
    doc.valueSets.put(valueSet.name, valueSet);
    Instance foodCS = new Instance("FoodCS");
    foodCS.instanceOf = "CodeSystem";
    foodCS.usage = InstanceUsage.Definition;
    AssignmentRule urlRule = new AssignmentRule("url");
    urlRule.value = "http://hl7.org/fhir/us/minimal/Instance/food";
    AssignmentRule contentRule = new AssignmentRule("content");
    contentRule.value = new FshCode("supplement");
    AssignmentRule pizzaCode = new AssignmentRule("concept[0].code");
    pizzaCode.value = new FshCode("Pizza");
    foodCS.rules.addAll(Arrays.asList(urlRule, contentRule, pizzaCode));
    doc.instances.put(foodCS.name, foodCS);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "name": "DinnerVS",
          "id": "DinnerVS",
          "status": "draft",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/DinnerVS",
          "compose": {
            "include": [
              {
                "system": "http://hl7.org/fhir/us/minimal/Instance/food",
                "concept": [
                  {
                    "code": "Pizza",
                    "display": "Delicious pizza to share."
                  },
                  {
                    "code": "Salad",
                    "display": "Plenty of fresh vegetables."
                  }
                ]
              }
            ]
          }
        }
        """, exported.get(0));
    assertEquals(0, loggerSpy.getAllMessages("error").size());
  }

  @Test
  @DisplayName("ValueSetExporter should log an error when exporting a value set that includes a concept component from a local complete code system name when the concept is not in the system")
  void shouldLogAnErrorWhenExportingAValueSetThatIncludesAConceptComponentFromALocalCom() {
    FshValueSet valueSet = new FshValueSet("DinnerVS");
    ValueSetConceptComponentRule component = new ValueSetConceptComponentRule(true).withFile("ValueSets.fsh").withLocation(new int[] {12, 0, 13, 22});
    component.from = fromOf("FoodCS", null);
    component.concepts.add(new FshCode("Pizza", "FoodCS", "Delicious pizza to share."));
    component.concepts.add(new FshCode("Salad", "FoodCS", "Plenty of fresh vegetables."));
    valueSet.rules.add(component);
    doc.valueSets.put(valueSet.name, valueSet);
    FshCodeSystem foodCS = new FshCodeSystem("FoodCS");
    foodCS.setId("food");
    foodCS.rules.add(new ConceptRule("Pizza", "Delicious pizza to share."));
    doc.codeSystems.put(foodCS.name, foodCS);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "name": "DinnerVS",
          "id": "DinnerVS",
          "status": "draft",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/DinnerVS",
          "compose": {
            "include": [
              {
                "system": "http://hl7.org/fhir/us/minimal/CodeSystem/food",
                "concept": [
                  {
                    "code": "Pizza",
                    "display": "Delicious pizza to share."
                  },
                  {
                    "code": "Salad",
                    "display": "Plenty of fresh vegetables."
                  }
                ]
              }
            ]
          }
        }
        """, exported.get(0));
    assertEquals(1, loggerSpy.getAllMessages("error").size());
    assertMatches("Code \"Salad\" is not defined for system FoodCS.*File: ValueSets\\.fsh.*Line: 12 - 13\\D*", "s", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("ValueSetExporter should log an error when exporting a value set that includes a concept component from a local complete CodeSystem instance name when the concept is not in the system")
  void shouldLogAnErrorWhenExportingAValueSetThatIncludesAConceptComponentFromALocalCom2() {
    FshValueSet valueSet = new FshValueSet("DinnerVS");
    ValueSetConceptComponentRule component = new ValueSetConceptComponentRule(true).withFile("ValueSets.fsh").withLocation(new int[] {15, 0, 16, 22});
    component.from = fromOf("FoodCS", null);
    component.concepts.add(new FshCode("Pizza", "FoodCS", "Delicious pizza to share."));
    component.concepts.add(new FshCode("Salad", "FoodCS", "Plenty of fresh vegetables."));
    valueSet.rules.add(component);
    doc.valueSets.put(valueSet.name, valueSet);
    Instance foodCS = new Instance("FoodCS");
    foodCS.instanceOf = "CodeSystem";
    foodCS.usage = InstanceUsage.Definition;
    AssignmentRule urlRule = new AssignmentRule("url");
    urlRule.value = "http://hl7.org/fhir/us/minimal/Instance/food";
    AssignmentRule contentRule = new AssignmentRule("content");
    contentRule.value = new FshCode("complete");
    AssignmentRule pizzaCode = new AssignmentRule("concept[0].code");
    pizzaCode.value = new FshCode("Pizza");
    AssignmentRule fruitCode = new AssignmentRule("concept[1].code");
    fruitCode.value = new FshCode("Fruit");
    foodCS.rules.addAll(Arrays.asList(urlRule, contentRule, pizzaCode, fruitCode));
    doc.instances.put(foodCS.name, foodCS);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "name": "DinnerVS",
          "id": "DinnerVS",
          "status": "draft",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/DinnerVS",
          "compose": {
            "include": [
              {
                "system": "http://hl7.org/fhir/us/minimal/Instance/food",
                "concept": [
                  {
                    "code": "Pizza",
                    "display": "Delicious pizza to share."
                  },
                  {
                    "code": "Salad",
                    "display": "Plenty of fresh vegetables."
                  }
                ]
              }
            ]
          }
        }
        """, exported.get(0));
    assertEquals(1, loggerSpy.getAllMessages("error").size());
    assertMatches("Code \"Salad\" is not defined for system FoodCS.*File: ValueSets\\.fsh.*Line: 15 - 16\\D*", "s", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("ValueSetExporter should log an error when exporting a value set that includes a concept component from a local complete code system url when the concept is not in the system")
  void shouldLogAnErrorWhenExportingAValueSetThatIncludesAConceptComponentFromALocalCom3() {
    FshValueSet valueSet = new FshValueSet("DinnerVS");
    ValueSetConceptComponentRule component = new ValueSetConceptComponentRule(true).withFile("ValueSets.fsh").withLocation(new int[] {12, 0, 13, 22});
    component.from = fromOf("FoodCS", null);
    component.concepts.add(new FshCode("Pizza", "http://food.org/food", "Delicious pizza to share."));
    component.concepts.add(new FshCode("Salad", "http://food.org/food", "Plenty of fresh vegetables."));
    component.concepts.add(new FshCode("Mulch", "http://food.org/food", "Somebody likes to eat mulch."));
    valueSet.rules.add(component);
    doc.valueSets.put(valueSet.name, valueSet);
    FshCodeSystem foodCS = new FshCodeSystem("FoodCS");
    foodCS.setId("food");
    CaretValueRule systemUrl = new CaretValueRule("");
    systemUrl.caretPath = "url";
    systemUrl.value = "http://food.org/food";
    foodCS.rules.add(systemUrl);
    foodCS.rules.add(new ConceptRule("Pizza", "Delicious pizza to share."));
    doc.codeSystems.put(foodCS.name, foodCS);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "name": "DinnerVS",
          "id": "DinnerVS",
          "status": "draft",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/DinnerVS",
          "compose": {
            "include": [
              {
                "system": "http://food.org/food",
                "concept": [
                  {
                    "code": "Pizza",
                    "display": "Delicious pizza to share."
                  },
                  {
                    "code": "Salad",
                    "display": "Plenty of fresh vegetables."
                  },
                  {
                    "code": "Mulch",
                    "display": "Somebody likes to eat mulch."
                  }
                ]
              }
            ]
          }
        }
        """, exported.get(0));
    assertEquals(1, loggerSpy.getAllMessages("error").size());
    assertMatches("Codes \"Salad\", \"Mulch\" are not defined for system FoodCS.*File: ValueSets\\.fsh.*Line: 12 - 13\\D*", "s", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("ValueSetExporter should export a value set that includes a concept component where the concept system includes a version")
  void shouldExportAValueSetThatIncludesAConceptComponentWhereTheConceptSystemIncludesA() {
    FshValueSet valueSet = new FshValueSet("BreakfastVS");
    ValueSetConceptComponentRule toastComponent = new ValueSetConceptComponentRule(true);
    toastComponent.from = fromOf("http://food.org/food|2.0.1", null);
    toastComponent.concepts.add(new FshCode("Toast", "http://food.org/food|2.0.1"));
    valueSet.rules.add(toastComponent);
    ValueSetConceptComponentRule juiceComponent = new ValueSetConceptComponentRule(true);
    juiceComponent.from = fromOf("http://food.org/beverage|1.1|x", null);
    juiceComponent.concepts.add(new FshCode("Orange juice", "http://food.org/beverage|1.1|x"));
    valueSet.rules.add(juiceComponent);
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "id": "BreakfastVS",
          "name": "BreakfastVS",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/BreakfastVS",
          "status": "draft",
          "compose": {
            "include": [
              {
                "system": "http://food.org/food",
                "concept": [
                  {
                    "code": "Toast"
                  }
                ],
                "version": "2.0.1"
              },
              {
                "system": "http://food.org/beverage",
                "concept": [
                  {
                    "code": "Orange juice"
                  }
                ],
                "version": "1.1|x"
              }
            ]
          }
        }
        """, exported.get(0));
  }

  @Test
  @DisplayName("ValueSetExporter should export a value set that includes a filter component with a regex filter")
  void shouldExportAValueSetThatIncludesAFilterComponentWithARegexFilter() {
    FshValueSet valueSet = new FshValueSet("BreakfastVS");
    ValueSetFilterComponentRule component = new ValueSetFilterComponentRule(true);
    component.from = fromOf("http://food.org/food", null);
    component.filters.add(new ValueSetFilter("display", VsOperator.REGEX, Pattern.compile("pancakes|flapjacks")));
    valueSet.rules.add(component);
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "id": "BreakfastVS",
          "name": "BreakfastVS",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/BreakfastVS",
          "status": "draft",
          "compose": {
            "include": [
              {
                "system": "http://food.org/food",
                "filter": [
                  {
                    "property": "display",
                    "op": "regex",
                    "value": "pancakes|flapjacks"
                  }
                ]
              }
            ]
          }
        }
        """, exported.get(0));
  }

  @Test
  @DisplayName("ValueSetExporter should export a value set that includes a filter component with a code filter")
  void shouldExportAValueSetThatIncludesAFilterComponentWithACodeFilter() {
    FshValueSet valueSet = new FshValueSet("BreakfastVS");
    ValueSetFilterComponentRule component = new ValueSetFilterComponentRule(true);
    component.from = fromOf("http://food.org/food", null);
    component.filters.add(new ValueSetFilter("concept", VsOperator.DESCENDENT_OF, new FshCode("Potatoes", "http://food.org/food")));
    valueSet.rules.add(component);
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "id": "BreakfastVS",
          "name": "BreakfastVS",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/BreakfastVS",
          "status": "draft",
          "compose": {
            "include": [
              {
                "system": "http://food.org/food",
                "filter": [
                  {
                    "property": "concept",
                    "op": "descendent-of",
                    "value": "Potatoes"
                  }
                ]
              }
            ]
          }
        }
        """, exported.get(0));
  }

  @Test
  @DisplayName("ValueSetExporter should export a value set that includes a filter component with a code filter where the value is from a local complete system")
  void shouldExportAValueSetThatIncludesAFilterComponentWithACodeFilterWhereTheValueIsF() {
    FshValueSet valueSet = new FshValueSet("BreakfastVS");
    ValueSetFilterComponentRule component = new ValueSetFilterComponentRule(true);
    component.from = fromOf("FoodCS", null);
    component.filters.add(new ValueSetFilter("concept", VsOperator.GENERALIZES, new FshCode("Pizza", "FoodCS")));
    valueSet.rules.add(component);
    doc.valueSets.put(valueSet.name, valueSet);
    FshCodeSystem foodCS = new FshCodeSystem("FoodCS");
    foodCS.setId("food");
    foodCS.rules.add(new ConceptRule("Pizza", "Delicious pizza to share."));
    doc.codeSystems.put(foodCS.name, foodCS);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "id": "BreakfastVS",
          "name": "BreakfastVS",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/BreakfastVS",
          "status": "draft",
          "compose": {
            "include": [
              {
                "system": "http://hl7.org/fhir/us/minimal/CodeSystem/food",
                "filter": [
                  {
                    "property": "concept",
                    "op": "generalizes",
                    "value": "Pizza"
                  }
                ]
              }
            ]
          }
        }
        """, exported.get(0));
    assertEquals(0, loggerSpy.getAllMessages("error").size());
  }

  @Test
  @DisplayName("ValueSetExporter should export a value set that includes a filter component with a code filter where the value is from a local incomplete system and the code is not in the system")
  void shouldExportAValueSetThatIncludesAFilterComponentWithACodeFilterWhereTheValueIsF2() {
    FshValueSet valueSet = new FshValueSet("BreakfastVS");
    ValueSetFilterComponentRule component = new ValueSetFilterComponentRule(true);
    component.from = fromOf("FoodCS", null);
    component.filters.add(new ValueSetFilter("concept", VsOperator.GENERALIZES, new FshCode("Cookie", "FoodCS")));
    valueSet.rules.add(component);
    doc.valueSets.put(valueSet.name, valueSet);
    FshCodeSystem foodCS = new FshCodeSystem("FoodCS");
    foodCS.setId("food");
    foodCS.rules.add(new ConceptRule("Pizza", "Delicious pizza to share."));
    CaretValueRule contentRule = new CaretValueRule("");
    contentRule.caretPath = "content";
    contentRule.value = new FshCode("fragment");
    foodCS.rules.add(contentRule);
    doc.codeSystems.put(foodCS.name, foodCS);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "id": "BreakfastVS",
          "name": "BreakfastVS",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/BreakfastVS",
          "status": "draft",
          "compose": {
            "include": [
              {
                "system": "http://hl7.org/fhir/us/minimal/CodeSystem/food",
                "filter": [
                  {
                    "property": "concept",
                    "op": "generalizes",
                    "value": "Cookie"
                  }
                ]
              }
            ]
          }
        }
        """, exported.get(0));
    assertEquals(0, loggerSpy.getAllMessages("error").size());
  }

  @Test
  @DisplayName("ValueSetExporter should export a value set that includes a filter component with a code filter where the value is from a local complete Instance of CodeSystem")
  void shouldExportAValueSetThatIncludesAFilterComponentWithACodeFilterWhereTheValueIsF3() {
    FshValueSet valueSet = new FshValueSet("BreakfastVS");
    ValueSetFilterComponentRule component = new ValueSetFilterComponentRule(true);
    component.from = fromOf("FoodCS", null);
    component.filters.add(new ValueSetFilter("concept", VsOperator.GENERALIZES, new FshCode("Pizza", "FoodCS")));
    valueSet.rules.add(component);
    doc.valueSets.put(valueSet.name, valueSet);
    Instance foodCS = new Instance("FoodCS");
    foodCS.instanceOf = "CodeSystem";
    foodCS.usage = InstanceUsage.Definition;
    AssignmentRule urlRule = new AssignmentRule("url");
    urlRule.value = "http://hl7.org/fhir/us/minimal/Instance/food";
    AssignmentRule contentRule = new AssignmentRule("content");
    contentRule.value = new FshCode("complete");
    AssignmentRule pizzaCode = new AssignmentRule("concept[0].code");
    pizzaCode.value = new FshCode("Pizza");
    AssignmentRule fruitCode = new AssignmentRule("concept[1].code");
    fruitCode.value = new FshCode("Fruit");
    foodCS.rules.addAll(Arrays.asList(urlRule, contentRule, pizzaCode, fruitCode));
    doc.instances.put(foodCS.name, foodCS);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "id": "BreakfastVS",
          "name": "BreakfastVS",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/BreakfastVS",
          "status": "draft",
          "compose": {
            "include": [
              {
                "system": "http://hl7.org/fhir/us/minimal/Instance/food",
                "filter": [
                  {
                    "property": "concept",
                    "op": "generalizes",
                    "value": "Pizza"
                  }
                ]
              }
            ]
          }
        }
        """, exported.get(0));
    assertEquals(0, loggerSpy.getAllMessages("error").size());
  }

  @Test
  @DisplayName("ValueSetExporter should export a value set that includes a filter component with a code filter where the value is from a local incomplete Instance of CodeSystem and the code is not in the system")
  void shouldExportAValueSetThatIncludesAFilterComponentWithACodeFilterWhereTheValueIsF4() {
    FshValueSet valueSet = new FshValueSet("BreakfastVS");
    ValueSetFilterComponentRule component = new ValueSetFilterComponentRule(true);
    component.from = fromOf("FoodCS", null);
    component.filters.add(new ValueSetFilter("concept", VsOperator.GENERALIZES, new FshCode("Cookie", "FoodCS")));
    valueSet.rules.add(component);
    doc.valueSets.put(valueSet.name, valueSet);
    Instance foodCS = new Instance("FoodCS");
    foodCS.instanceOf = "CodeSystem";
    foodCS.usage = InstanceUsage.Definition;
    AssignmentRule urlRule = new AssignmentRule("url");
    urlRule.value = "http://hl7.org/fhir/us/minimal/Instance/food";
    AssignmentRule contentRule = new AssignmentRule("content");
    contentRule.value = new FshCode("example");
    AssignmentRule pizzaCode = new AssignmentRule("concept[0].code");
    pizzaCode.value = new FshCode("Pizza");
    AssignmentRule fruitCode = new AssignmentRule("concept[1].code");
    fruitCode.value = new FshCode("Fruit");
    foodCS.rules.addAll(Arrays.asList(urlRule, contentRule, pizzaCode, fruitCode));
    doc.instances.put(foodCS.name, foodCS);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "id": "BreakfastVS",
          "name": "BreakfastVS",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/BreakfastVS",
          "status": "draft",
          "compose": {
            "include": [
              {
                "system": "http://hl7.org/fhir/us/minimal/Instance/food",
                "filter": [
                  {
                    "property": "concept",
                    "op": "generalizes",
                    "value": "Cookie"
                  }
                ]
              }
            ]
          }
        }
        """, exported.get(0));
    assertEquals(0, loggerSpy.getAllMessages("error").size());
  }

  @Test
  @DisplayName("ValueSetExporter should log an error when exporting a value set that includes a filter component with a code filter where the value is from a local complete system, but is not present in the system")
  void shouldLogAnErrorWhenExportingAValueSetThatIncludesAFilterComponentWithACodeFilte() {
    FshValueSet valueSet = new FshValueSet("BreakfastVS");
    ValueSetFilterComponentRule component = new ValueSetFilterComponentRule(true).withFile("Breakfast.fsh").withLocation(new int[] {8, 0, 10, 22});
    component.from = fromOf("FoodCS", null);
    component.filters.add(new ValueSetFilter("concept", VsOperator.GENERALIZES, new FshCode("Potato", "FoodCS")));
    valueSet.rules.add(component);
    doc.valueSets.put(valueSet.name, valueSet);
    FshCodeSystem foodCS = new FshCodeSystem("FoodCS");
    foodCS.setId("food");
    foodCS.rules.add(new ConceptRule("Pizza", "Delicious pizza to share."));
    doc.codeSystems.put(foodCS.name, foodCS);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "id": "BreakfastVS",
          "name": "BreakfastVS",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/BreakfastVS",
          "status": "draft",
          "compose": {
            "include": [
              {
                "system": "http://hl7.org/fhir/us/minimal/CodeSystem/food",
                "filter": [
                  {
                    "property": "concept",
                    "op": "generalizes",
                    "value": "Potato"
                  }
                ]
              }
            ]
          }
        }
        """, exported.get(0));
    assertEquals(1, loggerSpy.getAllMessages("error").size());
    assertMatches("Code \"Potato\" is not defined for system FoodCS.*File: Breakfast\\.fsh.*Line: 8 - 10\\D*", "s", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("ValueSetExporter should log an error when exporting a value set that includes a filter component with a code filter where the value is from a local complete Instance of CodeSystem, but is not present in the system")
  void shouldLogAnErrorWhenExportingAValueSetThatIncludesAFilterComponentWithACodeFilte2() {
    FshValueSet valueSet = new FshValueSet("BreakfastVS");
    ValueSetFilterComponentRule component = new ValueSetFilterComponentRule(true).withFile("Breakfast.fsh").withLocation(new int[] {9, 0, 12, 22});
    component.from = fromOf("FoodCS", null);
    component.filters.add(new ValueSetFilter("concept", VsOperator.GENERALIZES, new FshCode("Potato", "FoodCS")));
    valueSet.rules.add(component);
    doc.valueSets.put(valueSet.name, valueSet);
    Instance foodCS = new Instance("FoodCS");
    foodCS.instanceOf = "CodeSystem";
    foodCS.usage = InstanceUsage.Definition;
    AssignmentRule urlRule = new AssignmentRule("url");
    urlRule.value = "http://hl7.org/fhir/us/minimal/Instance/food";
    AssignmentRule contentRule = new AssignmentRule("content");
    contentRule.value = new FshCode("complete");
    AssignmentRule pizzaCode = new AssignmentRule("concept[0].code");
    pizzaCode.value = new FshCode("Pizza");
    AssignmentRule fruitCode = new AssignmentRule("concept[1].code");
    fruitCode.value = new FshCode("Fruit");
    foodCS.rules.addAll(Arrays.asList(urlRule, contentRule, pizzaCode, fruitCode));
    doc.instances.put(foodCS.name, foodCS);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "id": "BreakfastVS",
          "name": "BreakfastVS",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/BreakfastVS",
          "status": "draft",
          "compose": {
            "include": [
              {
                "system": "http://hl7.org/fhir/us/minimal/Instance/food",
                "filter": [
                  {
                    "property": "concept",
                    "op": "generalizes",
                    "value": "Potato"
                  }
                ]
              }
            ]
          }
        }
        """, exported.get(0));
    assertEquals(1, loggerSpy.getAllMessages("error").size());
    assertMatches("Code \"Potato\" is not defined for system FoodCS.*File: Breakfast\\.fsh.*Line: 9 - 12\\D*", "s", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("ValueSetExporter should export a value set that includes a filter component with a string filter")
  void shouldExportAValueSetThatIncludesAFilterComponentWithAStringFilter() {
    FshValueSet valueSet = new FshValueSet("BreakfastVS");
    ValueSetFilterComponentRule component = new ValueSetFilterComponentRule(true);
    component.from = fromOf("http://food.org/food", null);
    component.filters.add(new ValueSetFilter("version", VsOperator.EQUALS, "3.0.0"));
    valueSet.rules.add(component);
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "id": "BreakfastVS",
          "name": "BreakfastVS",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/BreakfastVS",
          "status": "draft",
          "compose": {
            "include": [
              {
                "system": "http://food.org/food",
                "filter": [
                  {
                    "property": "version",
                    "op": "=",
                    "value": "3.0.0"
                  }
                ]
              }
            ]
          }
        }
        """, exported.get(0));
  }

  @Test
  @DisplayName("ValueSetExporter should export a value set that excludes a component")
  void shouldExportAValueSetThatExcludesAComponent() {
    FshValueSet valueSet = new FshValueSet("DinnerVS");
    ValueSetFilterComponentRule includedComponent = new ValueSetFilterComponentRule(true);
    includedComponent.from = fromOf("http://food.org/food", new ArrayList<>(Arrays.asList("http://food.org/food/ValueSet/baked", "http://food.org/food/ValueSet/grilled")));
    ValueSetConceptComponentRule excludedComponent = new ValueSetConceptComponentRule(false);
    excludedComponent.from = fromOf("http://food.org/food", null);
    excludedComponent.concepts.add(new FshCode("Cake", "http://food.org/food", "A delicious treat for special occasions."));
    valueSet.rules.add(includedComponent);
    valueSet.rules.add(excludedComponent);
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "id": "DinnerVS",
          "name": "DinnerVS",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/DinnerVS",
          "status": "draft",
          "compose": {
            "include": [
              {
                "system": "http://food.org/food",
                "valueSet": [
                  "http://food.org/food/ValueSet/baked",
                  "http://food.org/food/ValueSet/grilled"
                ]
              }
            ],
            "exclude": [
              {
                "system": "http://food.org/food",
                "concept": [
                  {
                    "code": "Cake",
                    "display": "A delicious treat for special occasions."
                  }
                ]
              }
            ]
          }
        }
        """, exported.get(0));
  }

  @Test
  @DisplayName("ValueSetExporter should log a message when a value set has a logical definition without inclusions")
  void shouldLogAMessageWhenAValueSetHasALogicalDefinitionWithoutInclusions() {
    FshValueSet valueSet = new FshValueSet("BreakfastVS").withFile("Breakfast.fsh").withLocation(new int[] {2, 8, 4, 25});
    ValueSetFilterComponentRule candyFilter = new ValueSetFilterComponentRule(false);
    candyFilter.from = fromOf(null, new ArrayList<>(Arrays.asList("CandyVS")));
    valueSet.rules.add(candyFilter);
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(0, exported.size());
    assertMatches("File: Breakfast\\.fsh.*Line: 2 - 4\\D*", "s", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("ValueSetExporter should log a message when a value set from system is not a URI")
  void shouldLogAMessageWhenAValueSetFromSystemIsNotAURI() {
    FshValueSet valueSet = new FshValueSet("BreakfastVS").withFile("Breakfast.fsh").withLocation(new int[] {2, 8, 4, 25});
    ValueSetComponentRule component = new ValueSetComponentRule(true);
    component.from = fromOf("notAUri", null);
    valueSet.rules.add(component);
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(0, exported.size());
    assertMatches("notAUri.*File: Breakfast\\.fsh.*Line: 2 - 4\\D*", "s", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("ValueSetExporter should log a message when a value set from is not a URI")
  void shouldLogAMessageWhenAValueSetFromIsNotAURI() {
    FshValueSet valueSet = new FshValueSet("BreakfastVS").withFile("Breakfast.fsh").withLocation(new int[] {2, 8, 4, 25});
    ValueSetComponentRule component = new ValueSetComponentRule(true);
    component.from = fromOf(null, new ArrayList<>(Arrays.asList("notAUri")));
    valueSet.rules.add(component);
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(0, exported.size());
    assertMatches("notAUri.*File: Breakfast\\.fsh.*Line: 2 - 4\\D*", "s", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("ValueSetExporter should log a message and not add the concept again when a specific concept is included more than once")
  void shouldLogAMessageAndNotAddTheConceptAgainWhenASpecificConceptIsIncludedMoreThanO() {
    FshValueSet valueSet = new FshValueSet("DinnerVS");
    ValueSetConceptComponentRule pizzaComponent = new ValueSetConceptComponentRule(true).withFile("Dinner.fsh").withLocation(new int[] {2, 0, 2, 15});
    pizzaComponent.from = fromOf("http://food.org/food", null);
    pizzaComponent.concepts.add(new FshCode("Pizza", "http://food.org/food", "Delicious pizza to share."));
    ValueSetConceptComponentRule multiComponent = new ValueSetConceptComponentRule(true).withFile("Dinner.fsh").withLocation(new int[] {3, 0, 4, 16});
    multiComponent.from = fromOf("http://food.org/food", null);
    multiComponent.concepts.addAll(Arrays.asList(new FshCode("Pizza", "http://food.org/food", "Delicious pizza to share."), new FshCode("Salad", "http://food.org/food", "Plenty of fresh vegetables."), new FshCode("Toast", "http://food.org/food")));
    ValueSetConceptComponentRule toastComponent = new ValueSetConceptComponentRule(true).withFile("Dinner.fsh").withLocation(new int[] {5, 0, 5, 17});
    toastComponent.from = fromOf("http://food.org/food|2.0.1", null);
    toastComponent.concepts.add(new FshCode("Toast", "http://food.org/food|2.0.1"));
    ValueSetConceptComponentRule saladComponent = new ValueSetConceptComponentRule(true).withFile("Dinner.fsh").withLocation(new int[] {6, 0, 6, 18});
    saladComponent.from = fromOf("http://food.org/food", null);
    saladComponent.concepts.add(new FshCode("Salad", "http://food.org/food", "Plenty of fresh vegetables."));
    ValueSetConceptComponentRule versionMultiComponent = new ValueSetConceptComponentRule(true).withFile("Dinner.fsh").withLocation(new int[] {7, 0, 9, 19});
    versionMultiComponent.from = fromOf("http://food.org/food|2.0.1", null);
    versionMultiComponent.concepts.addAll(Arrays.asList(new FshCode("Toast", "http://food.org/food|2.0.1"), new FshCode("Waffles", "http://food.org/food|2.0.1")));
    valueSet.rules.addAll(Arrays.asList(pizzaComponent, multiComponent, toastComponent, saladComponent, versionMultiComponent));
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    Object inclusions = at(exported.get(0), "compose.include");
    assertEquals(2, len(inclusions));
    assertJsonEqual("""
        {
          "system": "http://food.org/food",
          "concept": [
            {
              "code": "Pizza",
              "display": "Delicious pizza to share."
            },
            {
              "code": "Salad",
              "display": "Plenty of fresh vegetables."
            },
            {
              "code": "Toast"
            }
          ]
        }
        """, at(inclusions, "[0]"));
    assertJsonEqual("""
        {
          "system": "http://food.org/food",
          "version": "2.0.1",
          "concept": [
            {
              "code": "Toast"
            },
            {
              "code": "Waffles"
            }
          ]
        }
        """, at(inclusions, "[1]"));
    List<String> warnings = loggerSpy.getAllMessages("warn");
    assertEquals(3, warnings.size());
    assertMatches("ValueSet DinnerVS already includes http:\\/\\/food\\.org\\/food#Pizza.*File: Dinner\\.fsh.*Line: 3 - 4\\D*", "s", warnings.get(0));
    assertMatches("ValueSet DinnerVS already includes http:\\/\\/food\\.org\\/food#Salad.*File: Dinner\\.fsh.*Line: 6\\D*", "s", warnings.get(1));
    assertMatches("ValueSet DinnerVS already includes http:\\/\\/food\\.org\\/food\\|2\\.0\\.1#Toast.*File: Dinner\\.fsh.*Line: 7 - 9\\D*", "s", warnings.get(2));
  }

  // CaretValueRules
  @Test
  @DisplayName("ValueSetExporter should apply a CaretValueRule")
  void shouldApplyACaretValueRule() {
    FshValueSet valueSet = new FshValueSet("DinnerVS");
    CaretValueRule rule = new CaretValueRule("");
    rule.caretPath = "publisher";
    rule.value = "Carrots";
    valueSet.rules.add(rule);
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "id": "DinnerVS",
          "name": "DinnerVS",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/DinnerVS",
          "status": "draft",
          "publisher": "Carrots"
        }
        """, exported.get(0));
  }

  @Test
  @DisplayName("ValueSetExporter should apply a CaretValueRule with soft indexing")
  void shouldApplyACaretValueRuleWithSoftIndexing() {
    FshValueSet valueSet = new FshValueSet("AppleVS");
    CaretValueRule caretRule1 = new CaretValueRule("");
    caretRule1.caretPath = "contact[+].name";
    caretRule1.value = "Johnny Appleseed";
    valueSet.rules.add(caretRule1);
    CaretValueRule caretRule2 = new CaretValueRule("");
    caretRule2.caretPath = "contact[=].telecom[+].rank";
    caretRule2.value = 1.0;
    valueSet.rules.add(caretRule2);
    CaretValueRule caretRule3 = new CaretValueRule("");
    caretRule3.caretPath = "contact[=].telecom[=].value";
    caretRule3.value = "email.email@email.com";
    valueSet.rules.add(caretRule3);
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "id": "AppleVS",
          "name": "AppleVS",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/AppleVS",
          "status": "draft",
          "contact": [
            {
              "name": "Johnny Appleseed",
              "telecom": [
                {
                  "rank": 1,
                  "value": "email.email@email.com"
                }
              ]
            }
          ]
        }
        """, exported.get(0));
  }

  @Test
  @DisplayName("ValueSetExporter should apply a CaretValueRule with extension slices in the correct order")
  void shouldApplyACaretValueRuleWithExtensionSlicesInTheCorrectOrder() {
    FshValueSet valueSet = new FshValueSet("SliceVS");
    CaretValueRule caretRule1 = new CaretValueRule("");
    caretRule1.caretPath = "extension[http://hl7.org/fhir/StructureDefinition/structuredefinition-fmm].valueInteger";
    caretRule1.value = 0.0;
    CaretValueRule caretRule2 = new CaretValueRule("");
    caretRule2.caretPath = "extension[http://hl7.org/fhir/StructureDefinition/structuredefinition-standards-status].valueCode";
    caretRule2.value = new FshCode("draft");
    valueSet.rules.addAll(Arrays.asList(caretRule1, caretRule2));
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        [
          {
            "url": "http://hl7.org/fhir/StructureDefinition/structuredefinition-fmm",
            "valueInteger": 0
          },
          {
            "url": "http://hl7.org/fhir/StructureDefinition/structuredefinition-standards-status",
            "valueCode": "draft"
          }
        ]
        """, at(exported.get(0), "extension"));
    assertEquals(0, loggerSpy.getAllMessages("error").size());
    assertEquals(0, loggerSpy.getAllMessages("warn").size());
  }

  @Test
  @DisplayName("ValueSetExporter should apply several caret rules to the same extension slice and to a numerically indexed extension")
  void shouldApplySeveralCaretRulesToTheSameExtensionSliceAndToANumericallyIndexedExten() {
    // ValueSet: ExtensionVS
    // * ^extension[structuredefinition-fmm].valueInteger = 1
    // * ^extension[structuredefinition-fmm].id = "fmm"
    // * ^extension[1].url = "http://example.org/StructureDefinition/plain"
    // * ^extension[1].valueString = "plain"
    FshValueSet valueSet = new FshValueSet("ExtensionVS");
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
    valueSet.rules.addAll(Arrays.asList(fmmRule, fmmIdRule, plainUrlRule, plainValueRule));
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
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
    assertEquals(0, loggerSpy.getAllMessages("error").size());
    assertEquals(0, loggerSpy.getAllMessages("warn").size());
  }

  @Test
  @DisplayName("ValueSetExporter should apply a CaretValueRule that assigns an inline Instance")
  void shouldApplyACaretValueRuleThatAssignsAnInlineInstance() {
    // ValueSet: BreakfastVS
    // Title: "Breakfast Values"
    // * ^contact = BreakfastMachine
    FshValueSet valueSet = new FshValueSet("BreakfastVS");
    valueSet.title = "Breakfast Values";
    CaretValueRule contactRule = new CaretValueRule("");
    contactRule.caretPath = "contact";
    contactRule.value = "BreakfastMachine";
    contactRule.isInstance = true;
    valueSet.rules.add(contactRule);
    doc.valueSets.put(valueSet.name, valueSet);
    // Instance: BreakfastMachine
    // InstanceOf: ContactDetail
    // Usage: #inline
    // * name = "The Breakfast Machine"
    Instance breakfastMachine = new Instance("BreakfastMachine");
    breakfastMachine.instanceOf = "ContactDetail";
    breakfastMachine.usage = InstanceUsage.Inline;
    AssignmentRule breakfastName = new AssignmentRule("name");
    breakfastName.value = "The Breakfast Machine";
    breakfastMachine.rules.add(breakfastName);
    doc.instances.put(breakfastMachine.name, breakfastMachine);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "name": "The Breakfast Machine"
        }
        """, at(exported.get(0), "contact[0]"));
  }

  @Test
  @DisplayName("ValueSetExporter should apply a CaretValueRule that assigns an inline Instance with a numeric id")
  void shouldApplyACaretValueRuleThatAssignsAnInlineInstanceWithANumericId() {
    // ValueSet: BreakfastVS
    // Title: "Breakfast Values"
    // * ^contact = 1024
    FshValueSet valueSet = new FshValueSet("BreakfastVS");
    valueSet.title = "Breakfast Values";
    CaretValueRule contactRule = new CaretValueRule("");
    contactRule.caretPath = "contact";
    contactRule.value = 1024.0;
    contactRule.rawValue = "1024";
    valueSet.rules.add(contactRule);
    doc.valueSets.put(valueSet.name, valueSet);
    // Instance: 1024
    // InstanceOf: ContactDetail
    // Usage: #inline
    // * name = "The Breakfast Machine"
    Instance breakfastMachine = new Instance("1024");
    breakfastMachine.instanceOf = "ContactDetail";
    breakfastMachine.usage = InstanceUsage.Inline;
    AssignmentRule breakfastName = new AssignmentRule("name");
    breakfastName.value = "The Breakfast Machine";
    breakfastMachine.rules.add(breakfastName);
    doc.instances.put(breakfastMachine.name, breakfastMachine);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "name": "The Breakfast Machine"
        }
        """, at(exported.get(0), "contact[0]"));
  }

  @Test
  @DisplayName("ValueSetExporter should apply a CaretValueRule that assigns an inline Instance with an id that resembles a boolean")
  void shouldApplyACaretValueRuleThatAssignsAnInlineInstanceWithAnIdThatResemblesABoole() {
    // ValueSet: BreakfastVS
    // Title: "Breakfast Values"
    // * ^contact = false
    FshValueSet valueSet = new FshValueSet("BreakfastVS");
    valueSet.title = "Breakfast Values";
    CaretValueRule contactRule = new CaretValueRule("");
    contactRule.caretPath = "contact";
    contactRule.value = false;
    contactRule.rawValue = "false";
    valueSet.rules.add(contactRule);
    doc.valueSets.put(valueSet.name, valueSet);
    // Instance: false
    // InstanceOf: ContactDetail
    // Usage: #inline
    // * name = "The Breakfast Machine"
    Instance breakfastMachine = new Instance("false");
    breakfastMachine.instanceOf = "ContactDetail";
    breakfastMachine.usage = InstanceUsage.Inline;
    AssignmentRule breakfastName = new AssignmentRule("name");
    breakfastName.value = "The Breakfast Machine";
    breakfastMachine.rules.add(breakfastName);
    doc.instances.put(breakfastMachine.name, breakfastMachine);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "name": "The Breakfast Machine"
        }
        """, at(exported.get(0), "contact[0]"));
  }

  @Test
  @DisplayName("ValueSetExporter should log a message when trying to assign an Instance, but the Instance is not found")
  void shouldLogAMessageWhenTryingToAssignAnInstanceButTheInstanceIsNotFound() {
    // ValueSet: BreakfastVS
    // Title: "Breakfast Values"
    // * ^contact = BreakfastMachine
    FshValueSet valueSet = new FshValueSet("BreakfastVS");
    valueSet.title = "Breakfast Values";
    CaretValueRule contactRule = new CaretValueRule("").withFile("ValueSets.fsh").withLocation(new int[] {9, 3, 9, 28});
    contactRule.caretPath = "contact";
    contactRule.value = "BreakfastMachine";
    contactRule.isInstance = true;
    valueSet.rules.add(contactRule);
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertNull(at(exported.get(0), "contact"));
    assertMatches("Cannot find definition for Instance: BreakfastMachine. Skipping rule.*File: ValueSets\\.fsh.*Line: 9\\D*", "s", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("ValueSetExporter should log a message when trying to assign a value that is numeric and refers to an Instance, but both types are wrong")
  void shouldLogAMessageWhenTryingToAssignAValueThatIsNumericAndRefersToAnInstanceButBo() {
    // ValueSet: BreakfastVS
    // Title: "Breakfast Values"
    // * ^identifier = 1024
    FshValueSet valueSet = new FshValueSet("BreakfastVS");
    valueSet.title = "Breakfast Values";
    CaretValueRule identifierRule = new CaretValueRule("").withFile("ValueSets.fsh").withLocation(new int[] {8, 5, 8, 29});
    identifierRule.caretPath = "identifier";
    identifierRule.value = 1024.0;
    identifierRule.rawValue = "1024";
    valueSet.rules.add(identifierRule);
    doc.valueSets.put(valueSet.name, valueSet);
    // Instance: 1024
    // InstanceOf: ContactDetail
    // Usage: #inline
    // * name = "The Breakfast Machine"
    Instance breakfastMachine = new Instance("1024");
    breakfastMachine.instanceOf = "ContactDetail";
    breakfastMachine.usage = InstanceUsage.Inline;
    AssignmentRule breakfastName = new AssignmentRule("name");
    breakfastName.value = "The Breakfast Machine";
    breakfastMachine.rules.add(breakfastName);
    doc.instances.put(breakfastMachine.name, breakfastMachine);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertMatches("Cannot assign number value: 1024\\. Value does not match element type: Identifier.*File: ValueSets\\.fsh.*Line: 8\\D*", "s", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("ValueSetExporter should log a message when trying to assign a value that is boolean and refers to an Instance, but both types are wrong")
  void shouldLogAMessageWhenTryingToAssignAValueThatIsBooleanAndRefersToAnInstanceButBo() {
    // ValueSet: BreakfastVS
    // Title: "Breakfast Values"
    // * ^identifier = true
    FshValueSet valueSet = new FshValueSet("BreakfastVS");
    valueSet.title = "Breakfast Values";
    CaretValueRule identifierRule = new CaretValueRule("").withFile("ValueSets.fsh").withLocation(new int[] {8, 5, 8, 29});
    identifierRule.caretPath = "identifier";
    identifierRule.value = true;
    identifierRule.rawValue = "true";
    valueSet.rules.add(identifierRule);
    doc.valueSets.put(valueSet.name, valueSet);
    // Instance: true
    // InstanceOf: ContactDetail
    // Usage: #inline
    // * name = "The Breakfast Machine"
    Instance breakfastMachine = new Instance("true");
    breakfastMachine.instanceOf = "ContactDetail";
    breakfastMachine.usage = InstanceUsage.Inline;
    AssignmentRule breakfastName = new AssignmentRule("name");
    breakfastName.value = "The Breakfast Machine";
    breakfastMachine.rules.add(breakfastName);
    doc.instances.put(breakfastMachine.name, breakfastMachine);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertMatches("Cannot assign boolean value: true\\. Value does not match element type: Identifier.*File: ValueSets\\.fsh.*Line: 8\\D*", "s", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("ValueSetExporter should export a value set with an extension")
  void shouldExportAValueSetWithAnExtension() {
    FshValueSet valueSet = new FshValueSet("BreakfastVS");
    valueSet.title = "Breakfast Values";
    CaretValueRule extensionRule = new CaretValueRule("");
    extensionRule.caretPath = "extension[structuredefinition-fmm].valueInteger";
    extensionRule.value = 1.0;
    valueSet.rules.add(extensionRule);
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertContainsEqual(JsAssert.json("""
        {
          "url": "http://hl7.org/fhir/StructureDefinition/structuredefinition-fmm",
          "valueInteger": 1
        }
        """), at(exported.get(0), "extension"));
  }

  @Test
  @DisplayName("ValueSetExporter should log a message when applying invalid CaretValueRule")
  void shouldLogAMessageWhenApplyingInvalidCaretValueRule() {
    FshValueSet valueSet = new FshValueSet("DinnerVS");
    CaretValueRule rule = new CaretValueRule("").withFile("InvalidValue.fsh").withLocation(new int[] {6, 3, 6, 12});
    rule.caretPath = "publisherz";
    rule.value = true;
    valueSet.rules.add(rule);
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "id": "DinnerVS",
          "name": "DinnerVS",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/DinnerVS",
          "status": "draft"
        }
        """, exported.get(0));
    assertMatches("File: InvalidValue\\.fsh.*Line: 6\\D*", "s", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("ValueSetExporter should use the url specified in a CaretValueRule when referencing a named value set")
  void shouldUseTheUrlSpecifiedInACaretValueRuleWhenReferencingANamedValueSet() {
    FshValueSet lunchVS = new FshValueSet("LunchVS");
    ValueSetFilterComponentRule lunchFilterComponent = new ValueSetFilterComponentRule(true);
    lunchFilterComponent.from = fromOf(null, new ArrayList<>(Arrays.asList("SandwichVS")));
    lunchVS.rules.add(lunchFilterComponent);
    FshValueSet sandwichVS = new FshValueSet("SandwichVS");
    CaretValueRule sandwichRule = new CaretValueRule("");
    sandwichRule.caretPath = "url";
    sandwichRule.value = "http://sandwich.org/ValueSet/SandwichVS";
    sandwichVS.rules.add(sandwichRule);
    doc.valueSets.put(lunchVS.name, lunchVS);
    doc.valueSets.put(sandwichVS.name, sandwichVS);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(2, exported.size());
    assertEquals("http://sandwich.org/ValueSet/SandwichVS", at(exported.get(0), "compose.include[0].valueSet[0]"));
  }

  @Test
  @DisplayName("ValueSetExporter should use the url specified in a CaretValueRule when referencing a named code system")
  void shouldUseTheUrlSpecifiedInACaretValueRuleWhenReferencingANamedCodeSystem() {
    FshValueSet lunchVS = new FshValueSet("LunchVS");
    ValueSetFilterComponentRule lunchFilterComponent = new ValueSetFilterComponentRule(true);
    lunchFilterComponent.from = fromOf("FoodCS", null);
    lunchVS.rules.add(lunchFilterComponent);
    FshCodeSystem foodCS = new FshCodeSystem("FoodCS");
    CaretValueRule foodRule = new CaretValueRule("");
    foodRule.caretPath = "url";
    foodRule.value = "http://food.net/CodeSystem/FoodCS";
    foodCS.rules.add(foodRule);
    doc.valueSets.put(lunchVS.name, lunchVS);
    doc.codeSystems.put(foodCS.name, foodCS);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertEquals("http://food.net/CodeSystem/FoodCS", at(exported.get(0), "compose.include[0].system"));
  }

  @Test
  @DisplayName("ValueSetExporter should apply a CaretValueRule at an included concept")
  void shouldApplyACaretValueRuleAtAnIncludedConcept() {
    FshValueSet valueSet = new FshValueSet("DinnerVS");
    ValueSetConceptComponentRule component = new ValueSetConceptComponentRule(true);
    component.from = fromOf("http://food.org/food", null);
    component.concepts.add(new FshCode("Pizza", "http://food.org/food", "Delicious pizza to share."));
    component.concepts.add(new FshCode("Salad", "http://food.org/food", "Plenty of fresh vegetables."));
    component.concepts.add(new FshCode("Mulch", "http://food.org/food"));
    CaretValueRule designation = new CaretValueRule("");
    designation.caretPath = "designation.value";
    designation.pathArray = new ArrayList<>(Arrays.asList("http://food.org/food#Salad"));
    designation.value = "ensalada";
    valueSet.rules.addAll(Arrays.asList(component, designation));
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "id": "DinnerVS",
          "name": "DinnerVS",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/DinnerVS",
          "status": "draft",
          "compose": {
            "include": [
              {
                "system": "http://food.org/food",
                "concept": [
                  {
                    "code": "Pizza",
                    "display": "Delicious pizza to share."
                  },
                  {
                    "code": "Salad",
                    "display": "Plenty of fresh vegetables.",
                    "designation": [
                      {
                        "value": "ensalada"
                      }
                    ]
                  },
                  {
                    "code": "Mulch"
                  }
                ]
              }
            ]
          }
        }
        """, exported.get(0));
    assertEquals(true, designation.isCodeCaretRule);
    assertEquals(0, loggerSpy.getAllMessages("error").size());
  }

  @Test
  @DisplayName("ValueSetExporter should apply a CaretValueRule at an included concept when there is a compose rule for a filter on the system first")
  void shouldApplyACaretValueRuleAtAnIncludedConceptWhenThereIsAComposeRuleForAFilterOn() {
    // See: https://github.com/FHIR/sushi/issues/1582
    FshValueSet valueSet = new FshValueSet("DinnerVS");
    ValueSetFilterComponentRule component1 = new ValueSetFilterComponentRule(true);
    component1.from = fromOf("http://food.org/food", null);
    component1.filters.add(new ValueSetFilter("display", VsOperator.REGEX, Pattern.compile("pancakes|flapjacks")));
    ValueSetConceptComponentRule component2 = new ValueSetConceptComponentRule(true);
    component2.from = fromOf("http://food.org/food", null);
    component2.concepts.add(new FshCode("Pizza", "http://food.org/food", "Delicious pizza to share."));
    component2.concepts.add(new FshCode("Salad", "http://food.org/food", "Plenty of fresh vegetables."));
    component2.concepts.add(new FshCode("Mulch", "http://food.org/food"));
    CaretValueRule designation = new CaretValueRule("");
    designation.caretPath = "designation.value";
    designation.pathArray = new ArrayList<>(Arrays.asList("http://food.org/food#Salad"));
    designation.value = "ensalada";
    valueSet.rules.addAll(Arrays.asList(component1, component2, designation));
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "id": "DinnerVS",
          "name": "DinnerVS",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/DinnerVS",
          "status": "draft",
          "compose": {
            "include": [
              {
                "system": "http://food.org/food",
                "filter": [
                  {
                    "property": "display",
                    "op": "regex",
                    "value": "pancakes|flapjacks"
                  }
                ]
              },
              {
                "system": "http://food.org/food",
                "concept": [
                  {
                    "code": "Pizza",
                    "display": "Delicious pizza to share."
                  },
                  {
                    "code": "Salad",
                    "display": "Plenty of fresh vegetables.",
                    "designation": [
                      {
                        "value": "ensalada"
                      }
                    ]
                  },
                  {
                    "code": "Mulch"
                  }
                ]
              }
            ]
          }
        }
        """, exported.get(0));
    assertEquals(true, designation.isCodeCaretRule);
    assertEquals(0, loggerSpy.getAllMessages("error").size());
  }

  @Test
  @DisplayName("ValueSetExporter should apply a CaretValueRule at a concept from a code system defined in FSH identified by name")
  void shouldApplyACaretValueRuleAtAConceptFromACodeSystemDefinedInFSHIdentifiedByName() {
    FshCodeSystem foodCS = new FshCodeSystem("FoodCS");
    foodCS.setId("food");
    foodCS.rules.add(new ConceptRule("Pizza", "Delicious pizza to share."));
    foodCS.rules.add(new ConceptRule("Salad", "Plenty of fresh vegetables."));
    doc.codeSystems.put(foodCS.name, foodCS);
    FshValueSet valueSet = new FshValueSet("DinnerVS");
    ValueSetConceptComponentRule component = new ValueSetConceptComponentRule(true);
    component.from = fromOf("FoodCS", null);
    component.concepts.add(new FshCode("Pizza", "FoodCS", "Delicious pizza to share."));
    component.concepts.add(new FshCode("Salad", "FoodCS", "Plenty of fresh vegetables."));
    CaretValueRule designation = new CaretValueRule("");
    designation.caretPath = "designation.value";
    designation.pathArray = new ArrayList<>(Arrays.asList("FoodCS#Salad"));
    designation.value = "ensalada";
    valueSet.rules.addAll(Arrays.asList(component, designation));
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "id": "DinnerVS",
          "name": "DinnerVS",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/DinnerVS",
          "status": "draft",
          "compose": {
            "include": [
              {
                "system": "http://hl7.org/fhir/us/minimal/CodeSystem/food",
                "concept": [
                  {
                    "code": "Pizza",
                    "display": "Delicious pizza to share."
                  },
                  {
                    "code": "Salad",
                    "display": "Plenty of fresh vegetables.",
                    "designation": [
                      {
                        "value": "ensalada"
                      }
                    ]
                  }
                ]
              }
            ]
          }
        }
        """, exported.get(0));
    assertEquals(true, designation.isCodeCaretRule);
    assertEquals(0, loggerSpy.getAllMessages("error").size());
  }

  @Test
  @DisplayName("ValueSetExporter should apply a CaretValueRule at a concept from a code system defined in FSH identified by id")
  void shouldApplyACaretValueRuleAtAConceptFromACodeSystemDefinedInFSHIdentifiedById() {
    FshCodeSystem foodCS = new FshCodeSystem("FoodCS");
    foodCS.setId("food");
    foodCS.rules.add(new ConceptRule("Pizza", "Delicious pizza to share."));
    foodCS.rules.add(new ConceptRule("Salad", "Plenty of fresh vegetables."));
    doc.codeSystems.put(foodCS.name, foodCS);
    FshValueSet valueSet = new FshValueSet("DinnerVS");
    ValueSetConceptComponentRule component = new ValueSetConceptComponentRule(true);
    component.from = fromOf("FoodCS", null);
    component.concepts.add(new FshCode("Pizza", "FoodCS", "Delicious pizza to share."));
    component.concepts.add(new FshCode("Salad", "FoodCS", "Plenty of fresh vegetables."));
    CaretValueRule designation = new CaretValueRule("");
    designation.caretPath = "designation.value";
    designation.pathArray = new ArrayList<>(Arrays.asList("food#Salad"));
    designation.value = "ensalada";
    valueSet.rules.addAll(Arrays.asList(component, designation));
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "id": "DinnerVS",
          "name": "DinnerVS",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/DinnerVS",
          "status": "draft",
          "compose": {
            "include": [
              {
                "system": "http://hl7.org/fhir/us/minimal/CodeSystem/food",
                "concept": [
                  {
                    "code": "Pizza",
                    "display": "Delicious pizza to share."
                  },
                  {
                    "code": "Salad",
                    "display": "Plenty of fresh vegetables.",
                    "designation": [
                      {
                        "value": "ensalada"
                      }
                    ]
                  }
                ]
              }
            ]
          }
        }
        """, exported.get(0));
    assertEquals(true, designation.isCodeCaretRule);
    assertEquals(0, loggerSpy.getAllMessages("error").size());
  }

  @Test
  @DisplayName("ValueSetExporter should apply a CaretValueRule at an excluded concept")
  void shouldApplyACaretValueRuleAtAnExcludedConcept() {
    FshValueSet valueSet = new FshValueSet("DinnerVS");
    ValueSetConceptComponentRule included = new ValueSetConceptComponentRule(true);
    included.from = fromOf("http://food.org/food", null);
    included.concepts.add(new FshCode("Pizza", "http://food.org/food", "Delicious pizza to share."));
    included.concepts.add(new FshCode("Salad", "http://food.org/food", "Plenty of fresh vegetables."));
    ValueSetConceptComponentRule excluded = new ValueSetConceptComponentRule(false);
    excluded.from = fromOf("http://food.org/food", null);
    excluded.concepts.add(new FshCode("Mulch", "http://food.org/food"));
    CaretValueRule designation = new CaretValueRule("");
    designation.caretPath = "designation.value";
    designation.pathArray = new ArrayList<>(Arrays.asList("http://food.org/food#Mulch"));
    designation.value = "mantillo";
    valueSet.rules.addAll(Arrays.asList(included, excluded, designation));
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "id": "DinnerVS",
          "name": "DinnerVS",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/DinnerVS",
          "status": "draft",
          "compose": {
            "include": [
              {
                "system": "http://food.org/food",
                "concept": [
                  {
                    "code": "Pizza",
                    "display": "Delicious pizza to share."
                  },
                  {
                    "code": "Salad",
                    "display": "Plenty of fresh vegetables."
                  }
                ]
              }
            ],
            "exclude": [
              {
                "system": "http://food.org/food",
                "concept": [
                  {
                    "code": "Mulch",
                    "designation": [
                      {
                        "value": "mantillo"
                      }
                    ]
                  }
                ]
              }
            ]
          }
        }
        """, exported.get(0));
    assertEquals(true, designation.isCodeCaretRule);
    assertEquals(0, loggerSpy.getAllMessages("error").size());
  }

  @Test
  @DisplayName("ValueSetExporter should apply a CaretValueRule at an excluded concept when there is a compose rule for a filter on the system first")
  void shouldApplyACaretValueRuleAtAnExcludedConceptWhenThereIsAComposeRuleForAFilterOn() {
    // See: https://github.com/FHIR/sushi/issues/1582
    FshValueSet valueSet = new FshValueSet("DinnerVS");
    ValueSetConceptComponentRule included = new ValueSetConceptComponentRule(true);
    included.from = fromOf("http://food.org/food", null);
    included.concepts.add(new FshCode("Pizza", "http://food.org/food", "Delicious pizza to share."));
    included.concepts.add(new FshCode("Salad", "http://food.org/food", "Plenty of fresh vegetables."));
    ValueSetFilterComponentRule excluded1 = new ValueSetFilterComponentRule(false);
    excluded1.from = fromOf("http://food.org/food", null);
    excluded1.filters.add(new ValueSetFilter("display", VsOperator.REGEX, Pattern.compile("pancakes|flapjacks")));
    ValueSetConceptComponentRule excluded2 = new ValueSetConceptComponentRule(false);
    excluded2.from = fromOf("http://food.org/food", null);
    excluded2.concepts.add(new FshCode("Mulch", "http://food.org/food"));
    CaretValueRule designation = new CaretValueRule("");
    designation.caretPath = "designation.value";
    designation.pathArray = new ArrayList<>(Arrays.asList("http://food.org/food#Mulch"));
    designation.value = "mantillo";
    valueSet.rules.addAll(Arrays.asList(included, excluded1, excluded2, designation));
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "id": "DinnerVS",
          "name": "DinnerVS",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/DinnerVS",
          "status": "draft",
          "compose": {
            "include": [
              {
                "system": "http://food.org/food",
                "concept": [
                  {
                    "code": "Pizza",
                    "display": "Delicious pizza to share."
                  },
                  {
                    "code": "Salad",
                    "display": "Plenty of fresh vegetables."
                  }
                ]
              }
            ],
            "exclude": [
              {
                "system": "http://food.org/food",
                "filter": [
                  {
                    "property": "display",
                    "op": "regex",
                    "value": "pancakes|flapjacks"
                  }
                ]
              },
              {
                "system": "http://food.org/food",
                "concept": [
                  {
                    "code": "Mulch",
                    "designation": [
                      {
                        "value": "mantillo"
                      }
                    ]
                  }
                ]
              }
            ]
          }
        }
        """, exported.get(0));
    assertEquals(true, designation.isCodeCaretRule);
    assertEquals(0, loggerSpy.getAllMessages("error").size());
  }

  @Test
  @DisplayName("ValueSetExporter should apply a CaretValueRule that assigns an instance at a concept")
  void shouldApplyACaretValueRuleThatAssignsAnInstanceAtAConcept() {
    FshValueSet valueSet = new FshValueSet("DinnerVS");
    ValueSetConceptComponentRule component = new ValueSetConceptComponentRule(true);
    component.from = fromOf("http://food.org/food", null);
    component.concepts.add(new FshCode("Pizza", "http://food.org/food", "Delicious pizza to share."));
    component.concepts.add(new FshCode("Salad", "http://food.org/food", "Plenty of fresh vegetables."));
    component.concepts.add(new FshCode("Mulch", "http://food.org/food"));
    CaretValueRule designationValue = new CaretValueRule("");
    designationValue.caretPath = "designation.value";
    designationValue.pathArray = new ArrayList<>(Arrays.asList("http://food.org/food#Salad"));
    designationValue.value = "ensalada";
    CaretValueRule designationExtension = new CaretValueRule("");
    designationExtension.caretPath = "designation.extension";
    designationExtension.pathArray = new ArrayList<>(Arrays.asList("http://food.org/food#Salad"));
    designationExtension.value = "SomeDesignation";
    designationExtension.isInstance = true;
    valueSet.rules.addAll(Arrays.asList(component, designationValue, designationExtension));
    doc.valueSets.put(valueSet.name, valueSet);
    // Instance: SomeDesignation
    // InstanceOf: data-absent-reason
    // Usage: #inline
    // * valueCode = #as-text
    Instance designationInstance = new Instance("SomeDesignation");
    designationInstance.instanceOf = "data-absent-reason";
    designationInstance.usage = InstanceUsage.Inline;
    AssignmentRule extensionCode = new AssignmentRule("valueCode");
    extensionCode.value = new FshCode("as-text");
    designationInstance.rules.add(extensionCode);
    doc.instances.put(designationInstance.name, designationInstance);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "id": "DinnerVS",
          "name": "DinnerVS",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/DinnerVS",
          "status": "draft",
          "compose": {
            "include": [
              {
                "system": "http://food.org/food",
                "concept": [
                  {
                    "code": "Pizza",
                    "display": "Delicious pizza to share."
                  },
                  {
                    "code": "Salad",
                    "display": "Plenty of fresh vegetables.",
                    "designation": [
                      {
                        "value": "ensalada",
                        "extension": [
                          {
                            "url": "http://hl7.org/fhir/StructureDefinition/data-absent-reason",
                            "valueCode": "as-text"
                          }
                        ]
                      }
                    ]
                  },
                  {
                    "code": "Mulch"
                  }
                ]
              }
            ]
          }
        }
        """, exported.get(0));
    assertEquals(true, designationValue.isCodeCaretRule);
    assertEquals(true, designationExtension.isCodeCaretRule);
    assertEquals(0, loggerSpy.getAllMessages("error").size());
  }

  @Test
  @DisplayName("ValueSetExporter should log an error when a CaretValueRule is applied at a concept that is neither included nor excluded")
  void shouldLogAnErrorWhenACaretValueRuleIsAppliedAtAConceptThatIsNeitherIncludedNorEx() {
    FshValueSet valueSet = new FshValueSet("DinnerVS");
    ValueSetConceptComponentRule component = new ValueSetConceptComponentRule(true);
    component.from = fromOf("http://food.org/food", null);
    component.concepts.add(new FshCode("Pizza", "http://food.org/food", "Delicious pizza to share."));
    component.concepts.add(new FshCode("Salad", "http://food.org/food", "Plenty of fresh vegetables."));
    component.concepts.add(new FshCode("Mulch", "http://food.org/food"));
    CaretValueRule designation = new CaretValueRule("").withFile("ValueSet.fsh").withLocation(new int[] {4, 3, 4, 29});
    designation.caretPath = "designation.value";
    designation.pathArray = new ArrayList<>(Arrays.asList("http://food.org/food#Bread"));
    designation.value = "pan";
    valueSet.rules.addAll(Arrays.asList(component, designation));
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "id": "DinnerVS",
          "name": "DinnerVS",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/DinnerVS",
          "status": "draft",
          "compose": {
            "include": [
              {
                "system": "http://food.org/food",
                "concept": [
                  {
                    "code": "Pizza",
                    "display": "Delicious pizza to share."
                  },
                  {
                    "code": "Salad",
                    "display": "Plenty of fresh vegetables."
                  },
                  {
                    "code": "Mulch"
                  }
                ]
              }
            ]
          }
        }
        """, exported.get(0));
    assertEquals(true, designation.isCodeCaretRule);
    assertEquals(1, loggerSpy.getAllMessages("error").size());
    assertContainsText("Could not find concept http://food.org/food#Bread, skipping rule.", loggerSpy.getLastMessage("error"));
    assertMatches("File: ValueSet\\.fsh.*Line: 4\\D*", "s", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("ValueSetExporter should not throw an error when caret rules are applied to a code from a specific version of a codeSystem")
  void shouldNotThrowAnErrorWhenCaretRulesAreAppliedToACodeFromASpecificVersionOfACodeS() {
    FshValueSet valueSet = new FshValueSet("DinnerVS");
    ValueSetConceptComponentRule unversionedComponent = new ValueSetConceptComponentRule(true);
    unversionedComponent.from = fromOf("http://food.org/food", null);
    unversionedComponent.concepts.add(new FshCode("Pizza", "http://food.org/food", "Delicious pizza to share."));
    ValueSetConceptComponentRule versionComponent = new ValueSetConceptComponentRule(true);
    versionComponent.from = fromOf("http://food.org/food|2.0.1", null);
    versionComponent.concepts.add(new FshCode("Salad", "http://food.org/food|2.0.1", "Plenty of fresh vegetables."));
    CaretValueRule designationValue = new CaretValueRule("");
    designationValue.caretPath = "designation.value";
    designationValue.pathArray = new ArrayList<>(Arrays.asList("http://food.org/food|2.0.1#Salad"));
    designationValue.value = "Salat";
    valueSet.rules.addAll(Arrays.asList(unversionedComponent, versionComponent, designationValue));
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "resourceType": "ValueSet",
          "id": "DinnerVS",
          "name": "DinnerVS",
          "url": "http://hl7.org/fhir/us/minimal/ValueSet/DinnerVS",
          "status": "draft",
          "compose": {
            "include": [
              {
                "system": "http://food.org/food",
                "concept": [
                  {
                    "code": "Pizza",
                    "display": "Delicious pizza to share."
                  }
                ]
              },
              {
                "system": "http://food.org/food",
                "version": "2.0.1",
                "concept": [
                  {
                    "code": "Salad",
                    "display": "Plenty of fresh vegetables.",
                    "designation": [
                      {
                        "value": "Salat"
                      }
                    ]
                  }
                ]
              }
            ]
          }
        }
        """, exported.get(0));
    assertEquals(true, designationValue.isCodeCaretRule);
    assertEquals(0, loggerSpy.getAllMessages("error").size());
  }

  @Test
  @DisplayName("ValueSetExporter should output an error when a choice element has values assigned to more than one choice type")
  void shouldOutputAnErrorWhenAChoiceElementHasValuesAssignedToMoreThanOneChoiceType() {
    FshValueSet valueSet = new FshValueSet("BreakfastVS").withFile("Breakfast.fsh").withLocation(new int[] {8, 3, 25, 33});
    valueSet.title = "Breakfast Values";
    CaretValueRule extensionUrl = new CaretValueRule("");
    extensionUrl.caretPath = "extension[0].url";
    extensionUrl.value = "http://example.org/SomeExt";
    CaretValueRule extensionString = new CaretValueRule("");
    extensionString.caretPath = "extension[0].valueString";
    extensionString.value = "string value";
    CaretValueRule extensionInteger = new CaretValueRule("");
    extensionInteger.caretPath = "extension[0].valueInteger";
    extensionInteger.value = new java.math.BigInteger("7");
    valueSet.rules.addAll(Arrays.asList(extensionUrl, extensionString, extensionInteger));
    doc.valueSets.put(valueSet.name, valueSet);
    List<ValueSet> exported = exporter.export().valueSets;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "url": "http://example.org/SomeExt",
          "valueString": "string value",
          "valueInteger": 7
        }
        """, at(exported.get(0), "extension[0]"));
    assertEquals(1, loggerSpy.getAllMessages("error").size());
    assertMatches("BreakfastVS contains multiple choice value assignments for choice element ValueSet\\.extension\\.value\\[x\\]\\..*File: Breakfast\\.fsh.*Line: 8 - 25\\D*", "s", loggerSpy.getLastMessage("error"));
  }

  @DisplayName("#insertRules")
  @Nested
  class InsertRules {
    FshValueSet vs;
    RuleSet ruleSet;

    @BeforeEach
    void beforeEach() {
      vs = new FshValueSet("Foo");
      doc.valueSets.put(vs.name, vs);
      ruleSet = new RuleSet("Bar");
      doc.ruleSets.put(ruleSet.name, ruleSet);
    }

    @Test
    @DisplayName("ValueSetExporter #insertRules should apply rules from an insert rule")
    void shouldApplyRulesFromAnInsertRule() {
      // RuleSet: Bar
      // * ^title = "Wow fancy"
      //
      // ValueSet: Foo
      // * insert Bar
      CaretValueRule nameRule = new CaretValueRule("");
      nameRule.caretPath = "title";
      nameRule.value = "Wow fancy";
      ruleSet.rules.add(nameRule);
      InsertRule insertRule = new InsertRule("");
      insertRule.ruleSet = "Bar";
      vs.rules.add(insertRule);
      exporter.applyInsertRules();
      ValueSet exported = exporter.exportValueSet(vs);
      assertEquals("Wow fancy", at(exported, "title"));
    }

    @Test
    @DisplayName("ValueSetExporter #insertRules should apply a CaretValueRule from a rule set with soft indexing")
    void shouldApplyACaretValueRuleFromARuleSetWithSoftIndexing() {
      CaretValueRule caretRule1 = new CaretValueRule("");
      caretRule1.caretPath = "contact[+].name";
      caretRule1.value = "Johnny Appleseed";
      ruleSet.rules.add(caretRule1);
      CaretValueRule caretRule2 = new CaretValueRule("");
      caretRule2.caretPath = "contact[=].telecom[+].rank";
      caretRule2.value = 1.0;
      ruleSet.rules.add(caretRule2);
      CaretValueRule caretRule3 = new CaretValueRule("");
      caretRule3.caretPath = "contact[=].telecom[=].value";
      caretRule3.value = "email.email@email.com";
      ruleSet.rules.add(caretRule3);
      InsertRule insertRule = new InsertRule("");
      insertRule.ruleSet = "Bar";
      vs.rules.add(insertRule);
      exporter.applyInsertRules();
      ValueSet exported = exporter.exportValueSet(vs);
      assertJsonEqual("""
        [
          {
            "name": "Johnny Appleseed",
            "telecom": [
              {
                "rank": 1,
                "value": "email.email@email.com"
              }
            ]
          }
        ]
        """, at(exported, "contact"));
    }

    @Test
    @DisplayName("ValueSetExporter #insertRules should apply concept-creating rules from a rule set and combine concepts from the same system")
    void shouldApplyConceptCreatingRulesFromARuleSetAndCombineConceptsFromTheSameSystem() {
      // RuleSet: Bar
      // * http://food.org/food#bread "bread"
      // * #granola from system http://food.org/food
      // * http://food.org/food#toast "toast"
      //
      // ValueSet: Foo
      // * insert Bar
      // due to rule precedence, a RuleSet will sometimes create a ConceptRule,
      // even when the eventual use is a VsConceptComponentRule.
      // that's fine, though. we can handle that.
      ConceptRule breadRule = new ConceptRule("bread", "bread");
      breadRule.system = "http://food.org/food";
      ValueSetConceptComponentRule granolaRule = new ValueSetConceptComponentRule(true);
      setProp(granolaRule.from, "system", "http://food.org/food");
      granolaRule.concepts.add(new FshCode("granola", "http://food.org/food"));
      ConceptRule toastRule = new ConceptRule("toast", "toast");
      toastRule.system = "http://food.org/food";
      ruleSet.rules.addAll(Arrays.asList(breadRule, granolaRule, toastRule));
      InsertRule breakfastInsert = new InsertRule("");
      breakfastInsert.ruleSet = "Bar";
      vs.rules.add(breakfastInsert);
      exporter.applyInsertRules();
      ValueSet exported = exporter.exportValueSet(vs);
      Object inclusions = at(exported, "compose.include");
      assertEquals(1, len(inclusions));
      assertJsonEqual("""
        {
          "system": "http://food.org/food",
          "concept": [
            {
              "code": "bread",
              "display": "bread"
            },
            {
              "code": "granola"
            },
            {
              "code": "toast",
              "display": "toast"
            }
          ]
        }
        """, at(inclusions, "[0]"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("ValueSetExporter #insertRules should apply concept-creating rules from a rule set and combine concepts from the same system and valuesets")
    void shouldApplyConceptCreatingRulesFromARuleSetAndCombineConceptsFromTheSameSystemAn() {
      // RuleSet: Bar
      // * http://food.org/food#bread from valueset http://food.org/BakeryVS
      // * #granola from system http://food.org/food and valueset http://food.org/CerealVS
      // * http://food.org/food#toast from valueset http://food.org/BakeryVS
      // * #oatmeal from system http://food.org/food and valueset http://food.org/CerealVS and http://food.org/BakeryVS
      // * #porridge from system http://food.org/food and valueset http://food.org/BakeryVS and http://food.org/CerealVS
      //
      // ValueSet: Foo
      // * insert Bar
      ValueSetConceptComponentRule breadRule = new ValueSetConceptComponentRule(true);
      breadRule.from = fromOf("http://food.org/food", new ArrayList<>(Arrays.asList("http://food.org/BakeryVS")));
      breadRule.concepts.add(new FshCode("bread", "http://food.org/food"));
      ValueSetConceptComponentRule granolaRule = new ValueSetConceptComponentRule(true);
      granolaRule.from = fromOf("http://food.org/food", new ArrayList<>(Arrays.asList("http://food.org/CerealVS")));
      granolaRule.concepts.add(new FshCode("granola", "http://food.org/food"));
      ValueSetConceptComponentRule toastRule = new ValueSetConceptComponentRule(true);
      toastRule.from = fromOf("http://food.org/food", new ArrayList<>(Arrays.asList("http://food.org/BakeryVS")));
      toastRule.concepts.add(new FshCode("toast", "http://food.org/food"));
      ValueSetConceptComponentRule oatmealRule = new ValueSetConceptComponentRule(true);
      oatmealRule.from = fromOf("http://food.org/food", new ArrayList<>(Arrays.asList("http://food.org/CerealVS", "http://food.org/BakeryVS")));
      oatmealRule.concepts.add(new FshCode("oatmeal", "http://food.org/food"));
      ValueSetConceptComponentRule porridgeRule = new ValueSetConceptComponentRule(true);
      porridgeRule.from = fromOf("http://food.org/food", new ArrayList<>(Arrays.asList("http://food.org/BakeryVS", "http://food.org/CerealVS")));
      porridgeRule.concepts.add(new FshCode("porridge", "http://food.org/food"));
      ruleSet.rules.addAll(Arrays.asList(breadRule, granolaRule, toastRule, oatmealRule, porridgeRule));
      InsertRule breakfastInsert = new InsertRule("");
      breakfastInsert.ruleSet = "Bar";
      vs.rules.add(breakfastInsert);
      exporter.applyInsertRules();
      ValueSet exported = exporter.exportValueSet(vs);
      Object inclusions = at(exported, "compose.include");
      assertEquals(3, len(inclusions));
      assertJsonEqual("""
        {
          "system": "http://food.org/food",
          "valueSet": [
            "http://food.org/BakeryVS"
          ],
          "concept": [
            {
              "code": "bread"
            },
            {
              "code": "toast"
            }
          ]
        }
        """, at(inclusions, "[0]"));
      assertJsonEqual("""
        {
          "system": "http://food.org/food",
          "valueSet": [
            "http://food.org/CerealVS"
          ],
          "concept": [
            {
              "code": "granola"
            }
          ]
        }
        """, at(inclusions, "[1]"));
      assertJsonEqual("""
        {
          "system": "http://food.org/food",
          "valueSet": [
            "http://food.org/CerealVS",
            "http://food.org/BakeryVS"
          ],
          "concept": [
            {
              "code": "oatmeal"
            },
            {
              "code": "porridge"
            }
          ]
        }
        """, at(inclusions, "[2]"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("ValueSetExporter #insertRules should apply concept-creating rules from a rule set and combine excluded concepts from the same system and valuesets")
    void shouldApplyConceptCreatingRulesFromARuleSetAndCombineExcludedConceptsFromTheSame() {
      // RuleSet: Bar
      // * http://food.org/food#bread from valueset http://food.org/BakeryVS
      // * #granola from system http://food.org/food and valueset http://food.org/CerealVS
      // * http://food.org/food#toast from valueset http://food.org/BakeryVS
      // * exclude #oatmeal from system http://food.org/food and valueset http://food.org/CerealVS and http://food.org/BakeryVS
      // * exclude #porridge from system http://food.org/food and valueset http://food.org/BakeryVS and http://food.org/CerealVS
      //
      // ValueSet: Foo
      // * insert Bar
      ValueSetConceptComponentRule breadRule = new ValueSetConceptComponentRule(true);
      breadRule.from = fromOf("http://food.org/food", new ArrayList<>(Arrays.asList("http://food.org/BakeryVS")));
      breadRule.concepts.add(new FshCode("bread", "http://food.org/food"));
      ValueSetConceptComponentRule granolaRule = new ValueSetConceptComponentRule(true);
      granolaRule.from = fromOf("http://food.org/food", new ArrayList<>(Arrays.asList("http://food.org/CerealVS")));
      granolaRule.concepts.add(new FshCode("granola", "http://food.org/food"));
      ValueSetConceptComponentRule toastRule = new ValueSetConceptComponentRule(true);
      toastRule.from = fromOf("http://food.org/food", new ArrayList<>(Arrays.asList("http://food.org/BakeryVS")));
      toastRule.concepts.add(new FshCode("toast", "http://food.org/food"));
      ValueSetConceptComponentRule oatmealRule = new ValueSetConceptComponentRule(false);
      oatmealRule.from = fromOf("http://food.org/food", new ArrayList<>(Arrays.asList("http://food.org/CerealVS", "http://food.org/BakeryVS")));
      oatmealRule.concepts.add(new FshCode("oatmeal", "http://food.org/food"));
      ValueSetConceptComponentRule porridgeRule = new ValueSetConceptComponentRule(false);
      porridgeRule.from = fromOf("http://food.org/food", new ArrayList<>(Arrays.asList("http://food.org/BakeryVS", "http://food.org/CerealVS")));
      porridgeRule.concepts.add(new FshCode("porridge", "http://food.org/food"));
      ruleSet.rules.addAll(Arrays.asList(breadRule, granolaRule, toastRule, oatmealRule, porridgeRule));
      InsertRule breakfastInsert = new InsertRule("");
      breakfastInsert.ruleSet = "Bar";
      vs.rules.add(breakfastInsert);
      exporter.applyInsertRules();
      ValueSet exported = exporter.exportValueSet(vs);
      Object inclusions = at(exported, "compose.include");
      assertEquals(2, len(inclusions));
      assertJsonEqual("""
        {
          "system": "http://food.org/food",
          "valueSet": [
            "http://food.org/BakeryVS"
          ],
          "concept": [
            {
              "code": "bread"
            },
            {
              "code": "toast"
            }
          ]
        }
        """, at(inclusions, "[0]"));
      assertJsonEqual("""
        {
          "system": "http://food.org/food",
          "valueSet": [
            "http://food.org/CerealVS"
          ],
          "concept": [
            {
              "code": "granola"
            }
          ]
        }
        """, at(inclusions, "[1]"));
      Object exclusions = at(exported, "compose.exclude");
      assertEquals(1, len(exclusions));
      assertJsonEqual("""
        {
          "system": "http://food.org/food",
          "valueSet": [
            "http://food.org/CerealVS",
            "http://food.org/BakeryVS"
          ],
          "concept": [
            {
              "code": "oatmeal"
            },
            {
              "code": "porridge"
            }
          ]
        }
        """, at(exclusions, "[0]"));
    }

    @Test
    @DisplayName("ValueSetExporter #insertRules should log an error and not apply rules from an invalid insert rule")
    void shouldLogAnErrorAndNotApplyRulesFromAnInvalidInsertRule() {
      // RuleSet: Bar
      // * experimental = true
      // * ^title = "Wow fancy"
      //
      // ValueSet: Foo
      // * insert Bar
      AssignmentRule valueRule = new AssignmentRule("experimental").withFile("Value.fsh").withLocation(new int[] {1, 2, 3, 4});
      valueRule.value = true;
      CaretValueRule nameRule = new CaretValueRule("");
      nameRule.caretPath = "title";
      nameRule.value = "Wow fancy";
      ruleSet.rules.addAll(Arrays.asList(valueRule, nameRule));
      InsertRule insertRule = new InsertRule("").withFile("Insert.fsh").withLocation(new int[] {5, 6, 7, 8});
      insertRule.ruleSet = "Bar";
      vs.rules.add(insertRule);
      exporter.applyInsertRules();
      ValueSet exported = exporter.exportValueSet(vs);
      // CaretRule is still applied
      assertEquals("Wow fancy", at(exported, "title"));
      // experimental is not set to true
      assertFalse(Js.truthy(at(exported, "experimental")));
      assertMatches("AssignmentRule.*FshValueSet.*File: Value\\.fsh.*Line: 1 - 3.*Applied in File: Insert\\.fsh.*Applied on Line: 5 - 7", "s", loggerSpy.getLastMessage("error"));
    }

  }
}
