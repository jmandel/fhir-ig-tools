package org.hl7.fhir.sushi.export;

import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertJsEqual;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertJsonEqual;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertMatches;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.at;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.json;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.hl7.fhir.sushi.testhelpers.MinimalConfig.minimalConfig;
import static org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.testDefsPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.sushi.fhirtypes.ElementDefinition;
import org.hl7.fhir.sushi.fhirtypes.StructureDefinition;
import org.hl7.fhir.sushi.fshtypes.FshCode;
import org.hl7.fhir.sushi.fshtypes.FshValueSet;
import org.hl7.fhir.sushi.fshtypes.Invariant;
import org.hl7.fhir.sushi.fshtypes.Logical;
import org.hl7.fhir.sushi.fshtypes.Profile;
import org.hl7.fhir.sushi.fshtypes.SourceInfo;
import org.hl7.fhir.sushi.fshtypes.TextLocation;
import org.hl7.fhir.sushi.fshtypes.rules.AddElementRule;
import org.hl7.fhir.sushi.fshtypes.rules.AssignmentRule;
import org.hl7.fhir.sushi.fshtypes.rules.BindingRule;
import org.hl7.fhir.sushi.fshtypes.rules.CardRule;
import org.hl7.fhir.sushi.fshtypes.rules.CaretValueRule;
import org.hl7.fhir.sushi.fshtypes.rules.ContainsRule;
import org.hl7.fhir.sushi.fshtypes.rules.ContainsRuleItem;
import org.hl7.fhir.sushi.fshtypes.rules.FlagRule;
import org.hl7.fhir.sushi.fshtypes.rules.ObeysRule;
import org.hl7.fhir.sushi.fshtypes.rules.OnlyRule;
import org.hl7.fhir.sushi.fshtypes.rules.OnlyRuleType;
import org.hl7.fhir.sushi.importer.FSHDocument;
import org.hl7.fhir.sushi.importer.FSHTank;
import org.hl7.fhir.sushi.js.Js;
import org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions;
import org.hl7.fhir.sushi.testhelpers.TestFisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// port of sushi/test/export/StructureDefinition.LogicalExporter.test.ts (SUSHI 3.21.1), describe blocks
// 'LogicalExporter', '#with-type-characteristics-codes'
// The TS beforeEach is the same as SdeTestBase's; only the beforeAll defs differ (r4-definitions only).
@DisplayName("LogicalExporter")
class StructureDefinitionLogicalExporterTest extends SdeTestBase {

  @Override
  TestFHIRDefinitions loadDefs() {
    return defs(true, testDefsPath("r4-definitions"));
  }

  /** TS: `const r = new AddElementRule(path); r.min = min; r.max = max; r.types = [{ type }]; r.short = short;` */
  private static AddElementRule addElement(String path, double min, String max, String type, String shortText) {
    AddElementRule rule = new AddElementRule(path);
    rule.min = min;
    rule.max = max;
    rule.types = new ArrayList<>(List.of(new OnlyRuleType(type)));
    rule.short_ = shortText;
    return rule;
  }

  /** Jest `expect(list).toContainEqual(<json>)`. */
  private static void assertContainsEqual(String expectedJson, Object actualList) {
    Object expected = json(expectedJson);
    assertTrue(actualList instanceof List<?> l && l.stream().anyMatch(e -> Js.isEqual(expected, e)),
        () -> "expected " + actualList + " to contain " + expectedJson);
  }

  @Test
  @DisplayName("LogicalExporter should output empty results with empty input")
  void emptyInput() {
    List<StructureDefinition> exported = exporter.export().logicals;
    assertJsEqual(List.of(), exported);
  }

  @Test
  @DisplayName("LogicalExporter should export a single logical model")
  void singleLogical() {
    Logical logical = new Logical("Foo");
    doc.logicals.put(logical.name, logical);
    List<StructureDefinition> exported = exporter.export().logicals;
    assertEquals(1, exported.size());
  }

  @Test
  @DisplayName("LogicalExporter should add source info for the exported logical model to the package")
  void sourceInfoInFshMap() {
    Logical logical = new Logical("Toasty").<Logical>withFile("AlwaysLogical.fsh").withLocation(48, 4, 59, 15);
    doc.logicals.put(logical.name, logical);
    List<StructureDefinition> exported = exporter.export().logicals;
    assertEquals(1, exported.size());
    assertEquals(
        new Package.FshMapEntry(new SourceInfo("AlwaysLogical.fsh", new TextLocation(48, 4, 59, 15)), "Toasty",
            "Logical"),
        pkg.fshMap.get("StructureDefinition-Toasty.json"));
  }

  @Test
  @DisplayName("LogicalExporter should export multiple logical models")
  void multipleLogicals() {
    Logical logicalFoo = new Logical("Foo");
    Logical logicalBar = new Logical("Bar");
    doc.logicals.put(logicalFoo.name, logicalFoo);
    doc.logicals.put(logicalBar.name, logicalBar);
    List<StructureDefinition> exported = exporter.export().logicals;
    assertEquals(2, exported.size());
  }

  @Test
  @DisplayName("LogicalExporter should still export logical models if one fails")
  void stillExportIfOneFails() {
    Logical logicalFoo = new Logical("Foo");
    logicalFoo.parent = "Baz"; // invalid parent cause failure
    Logical logicalBar = new Logical("Bar");
    doc.logicals.put(logicalFoo.name, logicalFoo);
    doc.logicals.put(logicalBar.name, logicalBar);
    List<StructureDefinition> exported = exporter.export().logicals;
    assertEquals(1, exported.size());
    assertEquals("Bar", at(exported.get(0), "name"));
  }

  @Test
  @DisplayName("LogicalExporter should export a single logical model with Base parent when parent not defined")
  void baseParentWhenNotDefined() {
    Logical logical = new Logical("Foo");
    doc.logicals.put(logical.name, logical);
    List<StructureDefinition> exported = exporter.export().logicals;
    assertEquals(1, exported.size());
    assertEquals("4.0.1", at(exported.get(0), "fhirVersion"));
    assertEquals("http://hl7.org/fhir/StructureDefinition/Base", at(exported.get(0), "baseDefinition"));
  }

  @Test
  @DisplayName("LogicalExporter should export a single logical model with Base parent by id")
  void baseParentById() {
    Logical logical = new Logical("Foo");
    logical.parent = "Base";
    doc.logicals.put(logical.name, logical);
    List<StructureDefinition> exported = exporter.export().logicals;
    assertEquals(1, exported.size());
    assertEquals("http://hl7.org/fhir/StructureDefinition/Base", at(exported.get(0), "baseDefinition"));
  }

  @Test
  @DisplayName("LogicalExporter should export a single logical model with Base parent by url")
  void baseParentByUrl() {
    Logical logical = new Logical("Foo");
    logical.parent = "http://hl7.org/fhir/StructureDefinition/Base";
    doc.logicals.put(logical.name, logical);
    List<StructureDefinition> exported = exporter.export().logicals;
    assertEquals(1, exported.size());
    assertEquals("http://hl7.org/fhir/StructureDefinition/Base", at(exported.get(0), "baseDefinition"));
  }

  @Test
  @DisplayName("LogicalExporter should export a single logical model with Element parent by id")
  void elementParentById() {
    Logical logical = new Logical("Foo");
    logical.parent = "Element";
    doc.logicals.put(logical.name, logical);
    List<StructureDefinition> exported = exporter.export().logicals;
    assertEquals(1, exported.size());
    assertEquals("http://hl7.org/fhir/StructureDefinition/Element", at(exported.get(0), "baseDefinition"));
  }

  @Test
  @DisplayName("LogicalExporter should export a single logical model with Element parent by url")
  void elementParentByUrl() {
    Logical logical = new Logical("Foo");
    logical.parent = "http://hl7.org/fhir/StructureDefinition/Element";
    doc.logicals.put(logical.name, logical);
    List<StructureDefinition> exported = exporter.export().logicals;
    assertEquals(1, exported.size());
    assertEquals("http://hl7.org/fhir/StructureDefinition/Element", at(exported.get(0), "baseDefinition"));
  }

  @Test
  @DisplayName("LogicalExporter should export a single logical model with another logical model parent by id")
  void logicalParentById() {
    Logical logical = new Logical("Foo");
    logical.parent = "AlternateIdentification";
    doc.logicals.put(logical.name, logical);
    List<StructureDefinition> exported = exporter.export().logicals;
    assertEquals(1, exported.size());
    assertEquals("http://hl7.org/fhir/cda/StructureDefinition/AlternateIdentification",
        at(exported.get(0), "baseDefinition"));
  }

  @Test
  @DisplayName("LogicalExporter should export a single logical model with another logical model parent by url")
  void logicalParentByUrl() {
    Logical logical = new Logical("Foo");
    logical.parent = "http://hl7.org/fhir/cda/StructureDefinition/AlternateIdentification";
    doc.logicals.put(logical.name, logical);
    List<StructureDefinition> exported = exporter.export().logicals;
    assertEquals(1, exported.size());
    assertEquals("http://hl7.org/fhir/cda/StructureDefinition/AlternateIdentification",
        at(exported.get(0), "baseDefinition"));
  }

  @Test
  @DisplayName("LogicalExporter should export a single logical model with a complex-type parent by id")
  void complexTypeParentById() {
    Logical logical = new Logical("Foo");
    logical.parent = "Address";
    doc.logicals.put(logical.name, logical);
    List<StructureDefinition> exported = exporter.export().logicals;
    assertEquals(1, exported.size());
    assertEquals("http://hl7.org/fhir/StructureDefinition/Address", at(exported.get(0), "baseDefinition"));
  }

  @Test
  @DisplayName("LogicalExporter should export a single logical model with a complex-type parent by url")
  void complexTypeParentByUrl() {
    Logical logical = new Logical("Foo");
    logical.parent = "http://hl7.org/fhir/StructureDefinition/Address";
    doc.logicals.put(logical.name, logical);
    List<StructureDefinition> exported = exporter.export().logicals;
    assertEquals(1, exported.size());
    assertEquals("http://hl7.org/fhir/StructureDefinition/Address", at(exported.get(0), "baseDefinition"));
  }

  @Test
  @DisplayName("LogicalExporter should export a single logical model with a resource parent by id")
  void resourceParentById() {
    Logical logical = new Logical("Foo");
    logical.parent = "Appointment";
    doc.logicals.put(logical.name, logical);
    List<StructureDefinition> exported = exporter.export().logicals;
    assertEquals(1, exported.size());
    assertEquals("http://hl7.org/fhir/StructureDefinition/Appointment", at(exported.get(0), "baseDefinition"));
  }

  @Test
  @DisplayName("LogicalExporter should export a single logical model with a resource parent by url")
  void resourceParentByUrl() {
    Logical logical = new Logical("Foo");
    logical.parent = "http://hl7.org/fhir/StructureDefinition/Appointment";
    doc.logicals.put(logical.name, logical);
    List<StructureDefinition> exported = exporter.export().logicals;
    assertEquals(1, exported.size());
    assertEquals("http://hl7.org/fhir/StructureDefinition/Appointment", at(exported.get(0), "baseDefinition"));
  }

  @Test
  @DisplayName("LogicalExporter should log an error with source information when the parent is invalid")
  void parentInvalid() {
    Logical logical = new Logical("BadParent").<Logical>withFile("BadParent.fsh").withLocation(2, 9, 4, 23);
    logical.parent = "actualgroup"; // Profile
    doc.logicals.put(logical.name, logical);
    exporter.export();
    assertMatches("File: BadParent\\.fsh.*Line: 2 - 4\\D*", "s", loggerSpy.getLastMessage("error"));
    assertMatches(
        "The parent of a logical model must be Element, Base, another logical model, a resource, or a type.", "s",
        loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("LogicalExporter should log an error with source information when the parent is not found")
  void parentNotFound() {
    Logical logical = new Logical("Bogus").<Logical>withFile("Bogus.fsh").withLocation(2, 9, 4, 23);
    logical.parent = "BogusParent";
    doc.logicals.put(logical.name, logical);
    exporter.export();
    assertMatches("File: Bogus\\.fsh.*Line: 2 - 4\\D*", "s", loggerSpy.getLastMessage("error"));
    assertMatches("Parent BogusParent not found for Bogus", "s", loggerSpy.getLastMessage("error"));
  }

  // Note: the TS tests below write `expect(a.baseDefinition === b.url);` without a matcher, which asserts nothing;
  // those lines are kept as comments.

  @Test
  @DisplayName("LogicalExporter should export logical models with FSHy parents")
  void fshyParents() {
    Logical logicalFoo = new Logical("Foo");
    Logical logicalBar = new Logical("Bar");
    logicalBar.parent = "Foo";
    doc.logicals.put(logicalFoo.name, logicalFoo);
    doc.logicals.put(logicalBar.name, logicalBar);
    List<StructureDefinition> exported = exporter.export().logicals;
    assertEquals(2, exported.size());
    assertEquals("Foo", at(exported.get(0), "name"));
    assertEquals("Bar", at(exported.get(1), "name"));
    // TS: expect(exported[1].baseDefinition === exported[0].url);
  }

  @Test
  @DisplayName("LogicalExporter should export logical models with the same FSHy parents")
  void sameFshyParents() {
    Logical logicalFoo = new Logical("Foo");
    Logical logicalBar = new Logical("Bar");
    logicalBar.parent = "Foo";
    Logical logicalBaz = new Logical("Baz");
    logicalBaz.parent = "Foo";
    doc.logicals.put(logicalFoo.name, logicalFoo);
    doc.logicals.put(logicalBar.name, logicalBar);
    doc.logicals.put(logicalBaz.name, logicalBaz);
    List<StructureDefinition> exported = exporter.export().logicals;
    assertEquals(3, exported.size());
    assertEquals("Foo", at(exported.get(0), "name"));
    assertEquals("Bar", at(exported.get(1), "name"));
    assertEquals("Baz", at(exported.get(2), "name"));
    // TS: expect(exported[1].baseDefinition === exported[0].url);
    // TS: expect(exported[2].baseDefinition === exported[0].url);
  }

  @Test
  @DisplayName("LogicalExporter should export logical models with deep FSHy parents")
  void deepFshyParents() {
    Logical logicalFoo = new Logical("Foo");
    Logical logicalBar = new Logical("Bar");
    logicalBar.parent = "Foo";
    Logical logicalBaz = new Logical("Baz");
    logicalBaz.parent = "Bar";
    doc.logicals.put(logicalFoo.name, logicalFoo);
    doc.logicals.put(logicalBar.name, logicalBar);
    doc.logicals.put(logicalBaz.name, logicalBaz);
    List<StructureDefinition> exported = exporter.export().logicals;
    assertEquals(3, exported.size());
    assertEquals("Foo", at(exported.get(0), "name"));
    assertEquals("Bar", at(exported.get(1), "name"));
    assertEquals("Baz", at(exported.get(2), "name"));
    // TS: expect(exported[1].baseDefinition === exported[0].url);
    // TS: expect(exported[2].baseDefinition === exported[1].url);
  }

  @Test
  @DisplayName("LogicalExporter should export logical models with out-of-order FSHy parents")
  void outOfOrderFshyParents() {
    Logical logicalFoo = new Logical("Foo");
    logicalFoo.parent = "Bar";
    Logical logicalBar = new Logical("Bar");
    logicalBar.parent = "Baz";
    Logical logicalBaz = new Logical("Baz");
    doc.logicals.put(logicalFoo.name, logicalFoo);
    doc.logicals.put(logicalBar.name, logicalBar);
    doc.logicals.put(logicalBaz.name, logicalBaz);
    List<StructureDefinition> exported = exporter.export().logicals;
    assertEquals(3, exported.size());
    assertEquals("Baz", at(exported.get(0), "name"));
    assertEquals("Bar", at(exported.get(1), "name"));
    assertEquals("Foo", at(exported.get(2), "name"));
    // TS: expect(exported[1].baseDefinition === exported[0].url);
    // TS: expect(exported[2].baseDefinition === exported[1].url);
  }

  @Test
  @DisplayName("LogicalExporter should include added element having logical model as datatype when parent is Base without regard to definition order - order Foo then Bar")
  void logicalDatatypeBaseFooThenBar() {
    Logical logicalFoo = new Logical("Foo");
    logicalFoo.rules.add(addElement("bars", 0, "1", "Bar", "short of property bars"));
    doc.logicals.put(logicalFoo.name, logicalFoo);

    Logical logicalBar = new Logical("Bar");
    logicalBar.rules.add(addElement("length", 0, "1", "Quantity", "short of property length"));
    logicalBar.rules.add(addElement("width", 0, "1", "Quantity", "short of property width"));
    doc.logicals.put(logicalBar.name, logicalBar);

    List<StructureDefinition> exported = exporter.export().logicals;
    assertEquals(2, exported.size());
    assertEquals("Foo", at(exported.get(0), "name"));
    assertEquals("Bar", at(exported.get(1), "name"));

    assertEquals(2, exported.get(0).elements.size()); // 1 Base element + 1 added "bars" element
    assertEquals("Foo.bars", at(exported.get(0).elements.get(1), "path"));
    assertEquals("Foo.bars", at(exported.get(0).elements.get(1), "base.path"));
    assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/Bar",
        at(exported.get(0).elements.get(1), "type[0].code"));
  }

  @Test
  @DisplayName("LogicalExporter should include added element having logical model as datatype when parent is Base without regard to definition order - order Bar then Foo")
  void logicalDatatypeBaseBarThenFoo() {
    Logical logicalBar = new Logical("Bar");
    logicalBar.rules.add(addElement("length", 0, "1", "Quantity", "short of property length"));
    logicalBar.rules.add(addElement("width", 0, "1", "Quantity", "short of property width"));
    doc.logicals.put(logicalBar.name, logicalBar);

    Logical logicalFoo = new Logical("Foo");
    logicalFoo.rules.add(addElement("bars", 0, "1", "Bar", "short of property bars"));
    doc.logicals.put(logicalFoo.name, logicalFoo);

    List<StructureDefinition> exported = exporter.export().logicals;
    assertEquals(2, exported.size());
    assertEquals("Bar", at(exported.get(0), "name"));
    assertEquals("Foo", at(exported.get(1), "name"));

    assertEquals(2, exported.get(1).elements.size()); // 1 Base element + 1 added "bars" element
    assertEquals("Foo.bars", at(exported.get(1).elements.get(1), "path"));
    assertEquals("Foo.bars", at(exported.get(1).elements.get(1), "base.path"));
    assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/Bar",
        at(exported.get(1).elements.get(1), "type[0].code"));
  }

  @Test
  @DisplayName("LogicalExporter should include added element having logical model as datatype when parent is Element")
  void logicalDatatypeElementParent() {
    Logical logicalFoo = new Logical("Foo");
    logicalFoo.rules.add(addElement("bars", 0, "1", "Bar", "short of property bars"));
    doc.logicals.put(logicalFoo.name, logicalFoo);

    Logical logicalBar = new Logical("Bar");
    logicalBar.parent = "Element";
    logicalBar.rules.add(addElement("length", 0, "1", "Quantity", "short of property length"));
    logicalBar.rules.add(addElement("width", 0, "1", "Quantity", "short of property width"));
    doc.logicals.put(logicalBar.name, logicalBar);

    List<StructureDefinition> exported = exporter.export().logicals;
    assertEquals(2, exported.size());
    assertEquals("Foo", at(exported.get(0), "name"));
    assertEquals("Bar", at(exported.get(1), "name"));

    assertEquals(2, exported.get(0).elements.size()); // 1 Base element + 1 added "bars" element
    ElementDefinition barsElement = exported.get(0).findElement("Foo.bars");
    assertEquals("Foo.bars", at(barsElement, "path"));
    assertEquals("Foo.bars", at(barsElement, "base.path"));
    assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/Bar", at(barsElement, "type[0].code"));
  }

  @Test
  @DisplayName("LogicalExporter should include added element having logical model as datatype when parent is another logical model")
  void logicalDatatypeLogicalParent() {
    Logical logicalFoo = new Logical("Foo");
    logicalFoo.rules.add(addElement("bars", 0, "1", "Bar", "short of property bars"));
    doc.logicals.put(logicalFoo.name, logicalFoo);

    Logical logicalBar = new Logical("Bar");
    logicalBar.parent = "AlternateIdentification";
    logicalBar.rules.add(addElement("length", 0, "1", "Quantity", "short of property length"));
    logicalBar.rules.add(addElement("width", 0, "1", "Quantity", "short of property width"));
    doc.logicals.put(logicalBar.name, logicalBar);

    List<StructureDefinition> exported = exporter.export().logicals;
    assertEquals(2, exported.size());
    assertEquals("Foo", at(exported.get(0), "name"));
    assertEquals("Bar", at(exported.get(1), "name"));

    assertEquals(2, exported.get(0).elements.size()); // 1 Base element + 1 added "bars" element
    ElementDefinition barsElement = exported.get(0).findElement("Foo.bars");
    assertEquals("Foo.bars", at(barsElement, "path"));
    assertEquals("Foo.bars", at(barsElement, "base.path"));
    assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/Bar", at(barsElement, "type[0].code"));
  }

  @Test
  @DisplayName("LogicalExporter should not re-add elements that are defined on the parent logical model")
  void noReAddParentElements() {
    Logical fishLogical = new Logical("FishLogical");
    fishLogical.rules.add(addElement("fins", 0, "1", "boolean", "notes about fins"));
    doc.logicals.put(fishLogical.name, fishLogical);

    Logical sharkLogical = new Logical("SharkLogical");
    sharkLogical.parent = "FishLogical";
    AddElementRule addElementRuleFins2 = addElement("fins", 0, "1", "boolean", "notes about shark fins")
        .<AddElementRule>withFile("Logicals.fsh").withLocation(3, 3, 3, 8);
    AddElementRule addElementRuleName = addElement("name", 0, "1", "string", "some sharks have names");
    sharkLogical.rules.addAll(List.of(addElementRuleFins2, addElementRuleName));
    doc.logicals.put(sharkLogical.name, sharkLogical);

    List<StructureDefinition> exported = exporter.export().logicals;
    assertEquals(2, exported.size());
    StructureDefinition exportedFish = exported.get(0);
    StructureDefinition exportedShark = exported.get(1);
    assertEquals("FishLogical", at(exportedFish, "name"));
    assertEquals("SharkLogical", at(exportedShark, "name"));

    assertEquals(2, exportedFish.elements.size()); // FishLogical, FishLogical.fins
    assertEquals(3, exportedShark.elements.size()); // SharkLogical, SharkLogical.fins (only once), SharkLogical.name
    assertEquals(1, loggerSpy.getAllMessages("error").size());
    assertMatches("Cannot define element fins on SharkLogical because it has already been defined", "s",
        loggerSpy.getLastMessage("error"));
    assertMatches("File: Logicals\\.fsh.*Line: 3", "s", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("LogicalExporter should not re-add elements that are defined on the parent logical model even when the parent type is overwritten with a caret value rule")
  void noReAddParentElementsWithTypeCaretRule() {
    Logical fishLogical = new Logical("FishLogical");
    fishLogical.rules.add(addElement("fins", 0, "1", "boolean", "notes about fins"));
    doc.logicals.put(fishLogical.name, fishLogical);

    Logical sharkLogical = new Logical("SharkLogical");
    sharkLogical.parent = "FishLogical";
    // Note: the caret rule reassigns the type - this is the only difference compared to the test above this one
    CaretValueRule typeCaretRule = new CaretValueRule("");
    typeCaretRule.caretPath = "type";
    typeCaretRule.value = "http://hl7.org/fhir/us/minimal/StructureDefinition/FishLogical";
    AddElementRule addElementRuleFins2 = addElement("fins", 0, "1", "boolean", "notes about shark fins")
        .<AddElementRule>withFile("Logicals.fsh").withLocation(3, 3, 3, 8);
    AddElementRule addElementRuleName = addElement("name", 0, "1", "string", "some sharks have names");
    sharkLogical.rules.addAll(List.of(typeCaretRule, addElementRuleFins2, addElementRuleName));
    doc.logicals.put(sharkLogical.name, sharkLogical);

    List<StructureDefinition> exported = exporter.export().logicals;
    assertEquals(2, exported.size());
    StructureDefinition exportedFish = exported.get(0);
    StructureDefinition exportedShark = exported.get(1);
    assertEquals("FishLogical", at(exportedFish, "name"));
    assertEquals("SharkLogical", at(exportedShark, "name"));

    assertEquals(2, exportedFish.elements.size()); // FishLogical, FishLogical.fins
    assertEquals(3, exportedShark.elements.size()); // SharkLogical, SharkLogical.fins (only once), SharkLogical.name
    assertEquals(1, loggerSpy.getAllMessages("error").size());
    assertMatches("Cannot define element fins on SharkLogical because it has already been defined", "s",
        loggerSpy.getLastMessage("error"));
    assertMatches("File: Logicals\\.fsh.*Line: 3", "s", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("LogicalExporter should have correct base and types for each nested logical model")
  void nestedLogicalBaseAndTypes() {
    Logical logicalOther = new Logical("Other");
    logicalOther.rules.add(addElement("thing", 1, "1", "boolean", "Is it a thing?"));
    doc.logicals.put(logicalOther.name, logicalOther);

    Logical logicalFoo = new Logical("FooFromOther");
    logicalFoo.parent = "Other";
    logicalFoo.rules.add(addElement("bars", 0, "*", "BarFromOther", "The bars of the foo"));
    doc.logicals.put(logicalFoo.name, logicalFoo);

    Logical logicalBar = new Logical("BarFromOther");
    logicalBar.parent = "Other";
    logicalBar.rules.add(addElement("height", 1, "1", "Quantity", "The height of the bar"));
    doc.logicals.put(logicalBar.name, logicalBar);

    List<StructureDefinition> exported = exporter.export().logicals;
    assertEquals(3, exported.size());
    assertEquals("Other", at(exported.get(0), "name"));
    assertEquals("http://hl7.org/fhir/StructureDefinition/Base", at(exported.get(0), "baseDefinition"));
    assertEquals("FooFromOther", at(exported.get(1), "name"));
    assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/Other", at(exported.get(1), "baseDefinition"));
    assertEquals("BarFromOther", at(exported.get(2), "name"));
    assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/Other", at(exported.get(2), "baseDefinition"));

    ElementDefinition thingElement = exported.get(0).findElement("Other.thing");
    assertEquals("boolean", at(thingElement, "type[0].code"));
    ElementDefinition barsElement = exported.get(1).findElement("FooFromOther.bars");
    assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/BarFromOther", at(barsElement, "type[0].code"));
    ElementDefinition heightElement = exported.get(2).findElement("BarFromOther.height");
    assertEquals("Quantity", at(heightElement, "type[0].code"));
  }

  @Test
  @DisplayName("LogicalExporter should log an error when an inline extension is used")
  void inlineExtension() {
    Logical logical = new Logical("MyModel");
    logical.parent = "Element";
    ContainsRule containsRule = new ContainsRule("extension").<ContainsRule>withFile("MyModel.fsh")
        .withLocation(3, 8, 3, 25);
    containsRule.items.add(new ContainsRuleItem("SomeExtension"));
    logical.rules.add(containsRule);
    doc.logicals.put(logical.name, logical);
    exporter.export();

    assertMatches("File: MyModel\\.fsh.*Line: 3\\D*", "s", loggerSpy.getLastMessage("error"));
    assertMatches("Use of 'ContainsRule' is not permitted for 'Logical'", "s", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("LogicalExporter should allow constraints on newly added elements and sub-elements")
  void constraintsOnNewElements() {
    Logical logical = new Logical("ExampleModel");
    logical.setId("ExampleModel");

    logical.rules.add(addElement("name", 0, "*", "HumanName", "A person's full name"));

    CardRule topLevelCardRule = new CardRule("name");
    topLevelCardRule.min = 1.0;
    topLevelCardRule.max = "1";
    logical.rules.add(topLevelCardRule);

    CardRule subElementCardRule = new CardRule("name.given");
    subElementCardRule.min = 1.0;
    subElementCardRule.max = "1";
    logical.rules.add(subElementCardRule);

    doc.logicals.put(logical.name, logical);
    exporter.export();
    List<String> logs = loggerSpy.getAllMessages("error");
    assertEquals(0, logs.size());
  }

  @Test
  @DisplayName("LogicalExporter should allow constraints on root elements")
  void constraintsOnRoot() {
    Logical logical = new Logical("ExampleModel");
    logical.setId("ExampleModel");

    CaretValueRule rootElementRule = new CaretValueRule(".");
    rootElementRule.caretPath = "alias";
    rootElementRule.value = "ExampleAlias";

    logical.rules.add(rootElementRule);

    doc.logicals.put(logical.name, logical);
    exporter.export();
    List<String> logs = loggerSpy.getAllMessages("error");
    assertEquals(0, logs.size());
  }

  @Test
  @DisplayName("LogicalExporter should allow constraints on inherited elements")
  void constraintsOnInheritedElements() {
    Logical logical = new Logical("MyTestModel");
    logical.parent = "ELTSSServiceModel";
    logical.setId("MyModel");

    logical.rules.add(addElement("backboneProp", 0, "*", "BackboneElement", "short of backboneProp"));
    logical.rules.add(addElement("backboneProp.name", 1, "1", "HumanName", "short of backboneProp.name"));
    logical.rules.add(addElement("backboneProp.address", 0, "*", "Address", "short of backboneProp.address"));

    // FlagRule
    FlagRule flagRule1 = new FlagRule("fundingSource");
    flagRule1.summary = true;
    logical.rules.add(flagRule1);

    // CardRule
    CardRule cardRule1 = new CardRule("fundingSource");
    cardRule1.min = 1.0;
    cardRule1.max = "1";
    logical.rules.add(cardRule1);

    // OnlyRule
    Profile profiledString = new Profile("MyStringProfile");
    profiledString.parent = "string";
    doc.profiles.put(profiledString.name, profiledString);
    OnlyRule onlyRule = new OnlyRule("deliveryAddress");
    onlyRule.types = new ArrayList<>(List.of(new OnlyRuleType(profiledString.name)));
    logical.rules.add(onlyRule);

    // BindingRule
    FshValueSet myValueSet = new FshValueSet("MyValueSet");
    doc.valueSets.put(myValueSet.name, myValueSet);
    BindingRule bindingRule = new BindingRule("unitType");
    bindingRule.valueSet = myValueSet.name;
    bindingRule.strength = "required";
    logical.rules.add(bindingRule);

    // AssignmentRule
    AssignmentRule assignmentRule = new AssignmentRule("name");
    assignmentRule.value = "pet-sitting";
    assignmentRule.exactly = false;
    logical.rules.add(assignmentRule);

    // ObeysRule
    Invariant myInvariant = new Invariant("MyInvariant");
    myInvariant.severity = new FshCode("error");
    myInvariant.description = "Is after 1900";
    doc.invariants.put(myInvariant.name, myInvariant);
    ObeysRule obeysRule = new ObeysRule("startDate");
    obeysRule.invariant = myInvariant.name;
    logical.rules.add(obeysRule);

    // CaretValueRule
    CaretValueRule caretRule = new CaretValueRule("startDate");
    caretRule.caretPath = "comment";
    caretRule.value = "Approximate is OK";
    logical.rules.add(caretRule);

    FlagRule flagRule2 = new FlagRule("backboneProp.address");
    flagRule2.summary = true;
    logical.rules.add(flagRule2);

    CardRule cardRule2 = new CardRule("backboneProp.address");
    cardRule2.min = 1.0;
    cardRule2.max = "100";
    logical.rules.add(cardRule2);

    doc.logicals.put(logical.name, logical);

    StructureDefinition exported = exporter.export().logicals.get(0);

    assertEquals(0, loggerSpy.getAllMessages("error").size());

    assertEquals("MyTestModel", at(exported, "name"));
    assertEquals("MyModel", at(exported, "id"));
    assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/MyModel", at(exported, "type"));
    assertEquals("http://hl7.org/fhir/us/eltss/StructureDefinition/eLTSSServiceModel",
        at(exported, "baseDefinition"));

    // FlagRule
    ElementDefinition fundingSource = exported.findElement("MyModel.fundingSource");
    assertEquals(true, at(fundingSource, "isSummary"));

    // CardRule
    assertJsEqual(1, at(fundingSource, "min"));
    assertEquals("1", at(fundingSource, "max"));

    // OnlyRule
    ElementDefinition deliveryAddress = exported.findElement("MyModel.deliveryAddress");
    assertEquals("string", at(deliveryAddress, "type[0].code"));
    assertJsonEqual("""
        ["http://hl7.org/fhir/us/minimal/StructureDefinition/MyStringProfile"]
        """, at(deliveryAddress, "type[0].profile"));

    // BindingRule
    ElementDefinition unitType = exported.findElement("MyModel.unitType");
    assertEquals("http://hl7.org/fhir/us/minimal/ValueSet/MyValueSet", at(unitType, "binding.valueSet"));
    assertEquals("required", at(unitType, "binding.strength"));

    // AssignmentRule
    ElementDefinition name = exported.findElement("MyModel.name");
    assertEquals("pet-sitting", at(name, "patternString"));

    // ObeysRule
    ElementDefinition startDate = exported.findElement("MyModel.startDate");
    assertEquals("MyInvariant", at(startDate, "constraint[0].key"));
    assertEquals("error", at(startDate, "constraint[0].severity"));
    assertEquals("Is after 1900", at(startDate, "constraint[0].human"));

    // CaretValueRule
    assertEquals("Approximate is OK", at(startDate, "comment"));

    assertEquals(20, exported.elements.size()); // 17 ELTSSServiceModel elements + 3 added elements
  }

  @Test
  @DisplayName("LogicalExporter should add new elements after inherited elements")
  void newElementsAfterInherited() {
    Logical logical = new Logical("MyTestModel");
    logical.parent = "ELTSSServiceModel";
    logical.setId("MyModel");

    logical.rules.add(addElement("backboneProp", 0, "*", "BackboneElement", "short of backboneProp"));
    logical.rules.add(addElement("backboneProp.name", 1, "1", "HumanName", "short of backboneProp.name"));
    logical.rules.add(addElement("backboneProp.address", 0, "*", "Address", "short of backboneProp.address"));

    doc.logicals.put(logical.name, logical);

    StructureDefinition exported = exporter.export().logicals.get(0);
    // ELTSSServiceModel has 17 elements, so the new elements are 18 19 and 20
    ElementDefinition backboneProp = exported.findElement("MyModel.backboneProp");
    assertEquals(17, exported.elements.indexOf(backboneProp));
    ElementDefinition backbonePropName = exported.findElement("MyModel.backboneProp.name");
    assertEquals(18, exported.elements.indexOf(backbonePropName));
    ElementDefinition backbonePropAddress = exported.findElement("MyModel.backboneProp.address");
    assertEquals(19, exported.elements.indexOf(backbonePropAddress));

    assertEquals(0, loggerSpy.getAllMessages("error").size());
  }

  @Test
  @DisplayName("LogicalExporter should log an error when slicing an inherited element")
  void slicingInheritedElement() {
    Logical logical = new Logical("MyModel");
    logical.parent = "ELTSSServiceModel";
    ContainsRule containsRule = new ContainsRule("deliveryAddress").<ContainsRule>withFile("MyModel.fsh")
        .withLocation(3, 8, 3, 25);
    containsRule.items.add(new ContainsRuleItem("home"));
    logical.rules.add(containsRule);
    doc.logicals.put(logical.name, logical);
    StructureDefinition exported = exporter.export().logicals.get(0);

    assertMatches("File: MyModel\\.fsh.*Line: 3\\D*", "s", loggerSpy.getLastMessage("error"));
    assertMatches("Use of 'ContainsRule' is not permitted for 'Logical'", "s", loggerSpy.getLastMessage("error"));
    ElementDefinition deliveryAddress = exported.findElement("MyModel.deliveryAddress");
    assertNull(at(deliveryAddress, "slicing"));
  }

  @Test
  @DisplayName("LogicalExporter should export a logical model with characteristics and warn that they are not verified")
  void characteristicsNotVerified() {
    Logical logical = new Logical("Foo").<Logical>withFile("Logical.fsh").withLocation(1, 3, 8, 28);
    logical.characteristics = new ArrayList<>(List.of("can-be-target", "has-length", "has-range", "is-continuous"));
    doc.logicals.put(logical.name, logical);
    List<StructureDefinition> exported = exporter.export().logicals;
    assertEquals(1, exported.size());
    assertContainsEqual("""
        {
          "url": "http://hl7.org/fhir/StructureDefinition/structuredefinition-type-characteristics",
          "valueCode": "can-be-target"
        }
        """, at(exported.get(0), "extension"));
    assertContainsEqual("""
        {
          "url": "http://hl7.org/fhir/StructureDefinition/structuredefinition-type-characteristics",
          "valueCode": "has-length"
        }
        """, at(exported.get(0), "extension"));
    assertContainsEqual("""
        {
          "url": "http://hl7.org/fhir/StructureDefinition/structuredefinition-type-characteristics",
          "valueCode": "has-range"
        }
        """, at(exported.get(0), "extension"));
    assertContainsEqual("""
        {
          "url": "http://hl7.org/fhir/StructureDefinition/structuredefinition-type-characteristics",
          "valueCode": "is-continuous"
        }
        """, at(exported.get(0), "extension"));
    assertEquals(1, loggerSpy.getAllMessages("warn").size());
    assertMatches(
        "Type characteristics code system not found\\. Skipping validation of characteristics for Foo.*File: Logical\\.fsh.*Line: 1 - 8\\D*",
        "s", loggerSpy.getLastMessage("warn"));
    assertEquals(0, loggerSpy.getAllMessages("error").size());
  }

  @Test
  @DisplayName("LogicalExporter should create Logical root element with short equal to title if short not available AND definition equal to description if definition not available")
  void rootShortFromTitleDefinitionFromDescription() {
    Logical logical = new Logical("MyTestModel");
    logical.setId("MyModel");
    logical.title = "MyTestModel title is here";
    logical.description = "MyTestModel description is here";

    doc.logicals.put(logical.name, logical);
    exporter.exportStructDef(logical);
    StructureDefinition exported = pkg.logicals.get(0);

    assertEquals(1, exported.elements.size());
    assertEquals(logical.title, at(exported.elements.get(0), "short"));
    assertEquals(logical.description, at(exported.elements.get(0), "definition"));
  }

  @Test
  @DisplayName("LogicalExporter should create Logical root element with short equal to name if short and title not available AND definition equal to name if description and definition not available")
  void rootShortAndDefinitionFromName() {
    Logical logical = new Logical("MyTestModel");
    logical.setId("MyModel");

    doc.logicals.put(logical.name, logical);
    exporter.exportStructDef(logical);
    StructureDefinition exported = pkg.logicals.get(0);

    assertEquals(1, exported.elements.size());
    assertEquals(logical.name, at(exported.elements.get(0), "short"));
    assertEquals(logical.name, at(exported.elements.get(0), "definition"));
  }

  @Test
  @DisplayName("LogicalExporter should create Logical root element with short equal to title if short not available AND definition equal to short if description and definition not available")
  void rootShortFromTitleDefinitionFromShort() {
    Logical logical = new Logical("MyTestModel");
    logical.setId("MyModel");
    logical.title = "MyTestModel title is here";

    doc.logicals.put(logical.name, logical);
    exporter.exportStructDef(logical);
    StructureDefinition exported = pkg.logicals.get(0);

    assertEquals(1, exported.elements.size());
    assertEquals(logical.title, at(exported.elements.get(0), "short"));
    assertEquals(at(exported.elements.get(0), "short"), at(exported.elements.get(0), "definition"));
  }

  @Test
  @DisplayName("LogicalExporter should create Logical root element with short equal short caret rule AND definition equal to definition caret rule")
  void rootShortAndDefinitionFromCaretRules() {
    Logical logical = new Logical("MyTestModel");
    logical.setId("MyModel");
    logical.title = "MyTestModel title is here";
    logical.description = "MyTestModel description is here";

    CaretValueRule caretValueRule = new CaretValueRule(".");
    caretValueRule.caretPath = "short";
    caretValueRule.value = "Caret short value is here";
    logical.rules.add(caretValueRule);

    CaretValueRule caretValueRuleTwo = new CaretValueRule(".");
    caretValueRuleTwo.caretPath = "definition";
    caretValueRuleTwo.value = "Caret definition value is here";
    logical.rules.add(caretValueRuleTwo);

    doc.logicals.put(logical.name, logical);
    exporter.exportStructDef(logical);
    StructureDefinition exported = pkg.logicals.get(0);

    assertEquals(1, exported.elements.size());
    assertEquals("Caret short value is here", at(exported.elements.get(0), "short"));
    assertEquals("Caret definition value is here", at(exported.elements.get(0), "definition"));
  }

  @Nested
  @DisplayName("#with-type-characteristics-codes")
  class WithTypeCharacteristicsCodes {
    TestFHIRDefinitions extraDefs;

    @BeforeEach
    void setUp() {
      // TS beforeAll
      extraDefs = defs(true, testDefsPath("CodeSystem-type-characteristics-code.json"),
          testDefsPath("r4-definitions"));
      // TS beforeEach (note: TS declares a local `const pkg`, so the outer pkg is not reassigned)
      loggerSpy.reset();
      doc = new FSHDocument("fileName");
      FSHTank input = new FSHTank(new ArrayList<>(List.of(doc)), minimalConfig());
      Package localPkg = new Package(input.config);
      TestFisher localFisher = new TestFisher(input, extraDefs, localPkg);
      exporter = new StructureDefinitionExporter(input, localPkg, localFisher);
    }

    @Test
    @DisplayName("LogicalExporter #with-type-characteristics-codes should export a logical model with characteristics")
    void characteristics() {
      Logical logical = new Logical("Foo");
      logical.characteristics = new ArrayList<>(List.of("can-be-target", "has-length", "has-range", "is-continuous"));
      doc.logicals.put(logical.name, logical);
      List<StructureDefinition> exported = exporter.export().logicals;
      assertEquals(1, exported.size());
      assertContainsEqual("""
          {
            "url": "http://hl7.org/fhir/tools/StructureDefinition/logical-target",
            "valueBoolean": true
          }
          """, at(exported.get(0), "extension"));
      assertContainsEqual("""
          {
            "url": "http://hl7.org/fhir/StructureDefinition/structuredefinition-type-characteristics",
            "valueCode": "has-length"
          }
          """, at(exported.get(0), "extension"));
      assertContainsEqual("""
          {
            "url": "http://hl7.org/fhir/StructureDefinition/structuredefinition-type-characteristics",
            "valueCode": "has-range"
          }
          """, at(exported.get(0), "extension"));
      assertContainsEqual("""
          {
            "url": "http://hl7.org/fhir/StructureDefinition/structuredefinition-type-characteristics",
            "valueCode": "is-continuous"
          }
          """, at(exported.get(0), "extension"));
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("LogicalExporter #with-type-characteristics-codes should export a logical model with characteristics and warn when a characteristic is not found in the code system")
    void unknownCharacteristic() {
      Logical logical = new Logical("Foo").<Logical>withFile("Logical.fsh").withLocation(1, 3, 9, 29);
      logical.characteristics = new ArrayList<>(List.of("has-range", "is-continuous", "has-hat"));
      doc.logicals.put(logical.name, logical);
      List<StructureDefinition> exported = exporter.export().logicals;
      assertEquals(1, exported.size());
      assertContainsEqual("""
          {
            "url": "http://hl7.org/fhir/StructureDefinition/structuredefinition-type-characteristics",
            "valueCode": "has-range"
          }
          """, at(exported.get(0), "extension"));
      assertContainsEqual("""
          {
            "url": "http://hl7.org/fhir/StructureDefinition/structuredefinition-type-characteristics",
            "valueCode": "is-continuous"
          }
          """, at(exported.get(0), "extension"));
      assertContainsEqual("""
          {
            "url": "http://hl7.org/fhir/StructureDefinition/structuredefinition-type-characteristics",
            "valueCode": "has-hat"
          }
          """, at(exported.get(0), "extension"));
      assertEquals(1, loggerSpy.getAllMessages("warn").size());
      assertMatches("Unrecognized characteristic: has-hat.*File: Logical\\.fsh.*Line: 1 - 9\\D*", "s",
          loggerSpy.getLastMessage("warn"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }
  }
}
