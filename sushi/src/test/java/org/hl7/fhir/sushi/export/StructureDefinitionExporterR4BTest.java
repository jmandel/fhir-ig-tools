package org.hl7.fhir.sushi.export;

import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertJsEqual;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertJsonEqual;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertMatches;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.at;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.json;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.hl7.fhir.sushi.fhirtypes.ElementDefinition;
import org.hl7.fhir.sushi.fhirtypes.ElementDefinitionType;
import org.hl7.fhir.sushi.fhirtypes.StructureDefinition;
import org.hl7.fhir.sushi.fshtypes.FshCode;
import org.hl7.fhir.sushi.fshtypes.FshValueSet;
import org.hl7.fhir.sushi.fshtypes.Invariant;
import org.hl7.fhir.sushi.fshtypes.Logical;
import org.hl7.fhir.sushi.fshtypes.Profile;
import org.hl7.fhir.sushi.fshtypes.Resource;
import org.hl7.fhir.sushi.fshtypes.rules.AddElementRule;
import org.hl7.fhir.sushi.fshtypes.rules.BindingRule;
import org.hl7.fhir.sushi.fshtypes.rules.CardRule;
import org.hl7.fhir.sushi.fshtypes.rules.CaretValueRule;
import org.hl7.fhir.sushi.fshtypes.rules.FlagRule;
import org.hl7.fhir.sushi.fshtypes.rules.ObeysRule;
import org.hl7.fhir.sushi.fshtypes.rules.OnlyRuleType;
import org.hl7.fhir.sushi.js.Js;
import org.hl7.fhir.sushi.js.Json;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// port of sushi/test/export/StructureDefinitionExporter.test.ts (SUSHI 3.21.1), describe blocks
// 'StructureDefinitionExporter R4' > '#AddElementRule', '#CardRule', '#FlagRule', '#ValueSetRule' (lines 2474-3878)
@DisplayName("StructureDefinitionExporter R4")
class StructureDefinitionExporterR4BTest extends SdeTestBase {

  private static List<OnlyRuleType> types(OnlyRuleType... t) {
    return new ArrayList<>(Arrays.asList(t));
  }

  private static OnlyRuleType t(String type) {
    return new OnlyRuleType(type);
  }

  private static int len(Object list) {
    return ((List<?>) list).size();
  }

  /** Jest toContainEqual with a JSON-text expectation. */
  private static void assertContainsJson(String expectedJson, Object list) {
    Object expected = json(expectedJson);
    assertNotNull(list);
    for (Object e : (List<?>) list) {
      if (Js.isEqual(expected, Json.parse(Json.stringify(e)))) return;
    }
    fail("expected " + Json.stringify(list) + " to contain " + expectedJson);
  }

  /** The standard ele-1 constraint checks repeated by several AddElementRule tests. */
  private static void assertEle1(Object constraint) {
    assertEquals("ele-1", at(constraint, "key"));
    assertNull(at(constraint, "requirements"));
    assertEquals("error", at(constraint, "severity"));
    assertEquals("All FHIR elements must have a @value or children", at(constraint, "human"));
    assertEquals("hasValue() or (children().count() > id.count())", at(constraint, "expression"));
    assertEquals("@value|f:*|h:div", at(constraint, "xpath"));
    assertEquals("http://hl7.org/fhir/StructureDefinition/Element", at(constraint, "source"));
  }

  private static final String STANDARDS_STATUS_URL =
      "http://hl7.org/fhir/StructureDefinition/structuredefinition-standards-status";

  private static String standardsStatus(String code) {
    return "{ \"url\": \"" + STANDARDS_STATUS_URL + "\", \"valueCode\": \"" + code + "\" }";
  }

  @Nested
  @DisplayName("#AddElementRule")
  class AddElementRuleTests {
    @Test
    @DisplayName("StructureDefinitionExporter R4 #AddElementRule should throw an error for an invalid AddElementRule path")
    void invalidPath() {
      Logical logical = new Logical("MyTestModel");
      logical.setId("MyModel");

      AddElementRule addElementRule = new AddElementRule("foo.bar").<AddElementRule>withFile("BadPath.fsh")
          .withLocation(3, 1, 8, 12);
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = types(t("boolean"));
      addElementRule.short_ = "A bar";
      logical.rules.add(addElementRule);

      doc.logicals.put(logical.name, logical);
      exporter.exportStructDef(logical);

      assertMatches("File: BadPath\\.fsh.*Line: 3 - 8\\D*", "s", loggerSpy.getLastMessage("error"));
      assertMatches("The element or path you referenced does not exist: foo.bar", "s",
          loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AddElementRule should add an element with a type and minimum required attributes")
    void typeAndMinimumAttributes() {
      Logical logical = new Logical("MyTestModel");
      logical.setId("MyModel");

      AddElementRule addElementRule = new AddElementRule("prop1");
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = types(t("dateTime"));
      addElementRule.short_ = "brief description";
      logical.rules.add(addElementRule);

      doc.logicals.put(logical.name, logical);
      exporter.exportStructDef(logical);
      StructureDefinition exported = pkg.logicals.get(0);

      assertEquals("MyTestModel", at(exported, "name"));
      assertEquals("MyModel", at(exported, "id"));
      assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/MyModel", at(exported, "type"));
      assertEquals(2, exported.elements.size()); // 1 for parent Base plus 1 for AddElementRule

      ElementDefinition prop1 = exported.findElement("MyModel.prop1");
      assertEquals("MyModel.prop1", at(prop1, "path"));
      assertJsEqual(0, at(prop1, "min"));
      assertEquals("1", at(prop1, "max"));
      ElementDefinitionType expectedType = new ElementDefinitionType("dateTime");
      assertJsEqual(List.of(expectedType), prop1.getType());
      assertNull(at(prop1, "isModifier"));
      assertNull(at(prop1, "mustSupport"));
      assertNull(at(prop1, "isSummary"));
      assertNull(at(prop1, "extension")); // standards flags extensions
      assertEquals("brief description", at(prop1, "short"));
      assertEquals("brief description", at(prop1, "definition"));
      assertNotNull(at(prop1, "base"));
      assertEquals(at(prop1, "path"), at(prop1, "base.path"));
      assertJsEqual(at(prop1, "min"), at(prop1, "base.min"));
      assertEquals(at(prop1, "max"), at(prop1, "base.max"));
      assertNotNull(at(prop1, "constraint"));
      assertEquals(1, len(at(prop1, "constraint")));
      assertEle1(at(prop1, "constraint[0]"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AddElementRule should add an element with a content reference and minimum required attributes")
    void contentReferenceAndMinimumAttributes() {
      Logical logical = new Logical("MyTestModel");
      logical.setId("MyModel");

      AddElementRule addElementRule = new AddElementRule("prop1");
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.contentReference = "http://example.org/StructureDefinition/MyModel#MyModel.propOne";
      addElementRule.short_ = "brief description";
      logical.rules.add(addElementRule);

      doc.logicals.put(logical.name, logical);
      exporter.exportStructDef(logical);
      StructureDefinition exported = pkg.logicals.get(0);

      assertEquals("MyTestModel", at(exported, "name"));
      assertEquals("MyModel", at(exported, "id"));
      assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/MyModel", at(exported, "type"));
      assertEquals(2, exported.elements.size()); // 1 for parent Base plus 1 for AddElementRule

      ElementDefinition prop1 = exported.findElement("MyModel.prop1");
      assertEquals("MyModel.prop1", at(prop1, "path"));
      assertJsEqual(0, at(prop1, "min"));
      assertEquals("1", at(prop1, "max"));
      assertNull(at(prop1, "type"));
      assertEquals("http://example.org/StructureDefinition/MyModel#MyModel.propOne", at(prop1, "contentReference"));
      assertNull(at(prop1, "isModifier"));
      assertNull(at(prop1, "mustSupport"));
      assertNull(at(prop1, "isSummary"));
      assertNull(at(prop1, "extension")); // standards flags extensions
      assertEquals("brief description", at(prop1, "short"));
      assertEquals("brief description", at(prop1, "definition"));
      assertNotNull(at(prop1, "base"));
      assertEquals(at(prop1, "path"), at(prop1, "base.path"));
      assertJsEqual(at(prop1, "min"), at(prop1, "base.min"));
      assertEquals(at(prop1, "max"), at(prop1, "base.max"));
      assertNotNull(at(prop1, "constraint"));
      assertEquals(1, len(at(prop1, "constraint")));
      assertEle1(at(prop1, "constraint[0]"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AddElementRule should add an element with additional constraint attributes")
    void additionalConstraintAttributes() {
      Logical logical = new Logical("MyTestModel");
      logical.setId("MyModel");

      AddElementRule addElementRule = new AddElementRule("prop1");
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = types(t("string"));
      addElementRule.short_ = "brief description";
      logical.rules.add(addElementRule);

      Invariant invariant = new Invariant("TestInvariant");
      doc.invariants.put(invariant.name, invariant);
      ObeysRule constraint = new ObeysRule("prop1");
      constraint.invariant = invariant.getId();
      logical.rules.add(constraint);

      doc.logicals.put(logical.name, logical);
      exporter.exportStructDef(logical);
      StructureDefinition exported = pkg.logicals.get(0);

      assertEquals("MyTestModel", at(exported, "name"));
      assertEquals("MyModel", at(exported, "id"));
      assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/MyModel", at(exported, "type"));
      assertEquals(2, exported.elements.size()); // 1 for parent Base plus 1 for AddElementRule

      ElementDefinition prop1 = exported.findElement("MyModel.prop1");

      assertNotNull(at(prop1, "constraint"));
      assertEquals(2, len(at(prop1, "constraint")));
      assertEle1(at(prop1, "constraint[0]"));

      assertEquals("TestInvariant", at(prop1, "constraint[1].key"));
      assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/MyModel", at(prop1, "constraint[1].source"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AddElementRule should add an element with multiple targetTypes")
    void multipleTargetTypes() {
      Logical logical = new Logical("MyTestModel");
      logical.setId("MyModel");

      AddElementRule addElementRule = new AddElementRule("created[x]");
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = types(t("instant"), t("dateTime"), t("Period"));
      addElementRule.short_ = "short definition";
      logical.rules.add(addElementRule);

      doc.logicals.put(logical.name, logical);
      exporter.exportStructDef(logical);
      StructureDefinition exported = pkg.logicals.get(0);

      assertEquals(2, exported.elements.size());

      ElementDefinition prop1 = exported.findElement("MyModel.created[x]");
      ElementDefinitionType expectedType1 = new ElementDefinitionType("instant");
      ElementDefinitionType expectedType2 = new ElementDefinitionType("dateTime");
      ElementDefinitionType expectedType3 = new ElementDefinitionType("Period");
      assertJsEqual(List.of(expectedType1, expectedType2, expectedType3), prop1.getType());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AddElementRule should add an element with all boolean flags set to true")
    void allBooleanFlagsTrue() {
      Logical logical = new Logical("MyTestModel");
      logical.setId("MyModel");

      AddElementRule addElementRule = new AddElementRule("prop1");
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = types(t("string"));
      addElementRule.short_ = "short definition";
      // MustSupport must be false/undefined for logical models and resources;
      // otherwise, error will be thrown - tested elsewhere
      addElementRule.mustSupport = false;
      addElementRule.summary = true;
      addElementRule.modifier = true;

      logical.rules.add(addElementRule);
      doc.logicals.put(logical.name, logical);
      exporter.exportStructDef(logical);
      StructureDefinition exported = pkg.logicals.get(0);

      assertEquals(2, exported.elements.size());

      ElementDefinition prop1 = exported.findElement("MyModel.prop1");
      assertFalse(Js.truthy(at(prop1, "mustSupport")));
      assertEquals(true, at(prop1, "isSummary"));
      assertEquals(true, at(prop1, "isModifier"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AddElementRule should add an element with all boolean flags set to false")
    void allBooleanFlagsFalse() {
      Logical logical = new Logical("MyTestModel");
      logical.setId("MyModel");

      AddElementRule addElementRule = new AddElementRule("prop1");
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = types(t("string"));
      addElementRule.short_ = "short definition";
      addElementRule.mustSupport = false;
      addElementRule.summary = false;
      addElementRule.modifier = false;

      logical.rules.add(addElementRule);
      doc.logicals.put(logical.name, logical);
      exporter.exportStructDef(logical);
      StructureDefinition exported = pkg.logicals.get(0);

      assertEquals(2, exported.elements.size());

      ElementDefinition prop1 = exported.findElement("MyModel.prop1");
      // When flags are set to false, the attributes are not generated rather than set to false
      assertNull(at(prop1, "mustSupport"));
      assertNull(at(prop1, "isSummary"));
      assertNull(at(prop1, "isModifier"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AddElementRule should add an element with trial use standards flag set to true")
    void trialUseFlag() {
      Logical logical = new Logical("MyTestModel");
      logical.setId("MyModel");

      AddElementRule addElementRule = new AddElementRule("prop1");
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = types(t("string"));
      addElementRule.short_ = "short definition";
      addElementRule.trialUse = true;
      addElementRule.normative = false;
      addElementRule.draft = false;

      logical.rules.add(addElementRule);
      doc.logicals.put(logical.name, logical);
      exporter.exportStructDef(logical);
      StructureDefinition exported = pkg.logicals.get(0);

      assertEquals(2, exported.elements.size());

      ElementDefinition prop1 = exported.findElement("MyModel.prop1");
      // When standards flags are set to true, an extension is created
      assertEquals(1, len(at(prop1, "extension")));
      assertJsonEqual(standardsStatus("trial-use"), at(prop1, "extension[0]"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AddElementRule should add an element with normative standards flag set to true")
    void normativeFlag() {
      Logical logical = new Logical("MyTestModel");
      logical.setId("MyModel");

      AddElementRule addElementRule = new AddElementRule("prop1");
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = types(t("string"));
      addElementRule.short_ = "short definition";
      addElementRule.trialUse = false;
      addElementRule.normative = true;
      addElementRule.draft = false;

      logical.rules.add(addElementRule);
      doc.logicals.put(logical.name, logical);
      exporter.exportStructDef(logical);
      StructureDefinition exported = pkg.logicals.get(0);

      assertEquals(2, exported.elements.size());

      ElementDefinition prop1 = exported.findElement("MyModel.prop1");
      // When standards flags are set to true, an extension is created
      assertEquals(1, len(at(prop1, "extension")));
      assertJsonEqual(standardsStatus("normative"), at(prop1, "extension[0]"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AddElementRule should add an element with draft standards flag set to true")
    void draftFlag() {
      Logical logical = new Logical("MyTestModel");
      logical.setId("MyModel");

      AddElementRule addElementRule = new AddElementRule("prop1");
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = types(t("string"));
      addElementRule.short_ = "short definition";
      addElementRule.trialUse = false;
      addElementRule.normative = false;
      addElementRule.draft = true;

      logical.rules.add(addElementRule);
      doc.logicals.put(logical.name, logical);
      exporter.exportStructDef(logical);
      StructureDefinition exported = pkg.logicals.get(0);

      assertEquals(2, exported.elements.size());

      ElementDefinition prop1 = exported.findElement("MyModel.prop1");
      // When standards flags are set to true, an extension is created
      assertEquals(1, len(at(prop1, "extension")));
      assertJsonEqual(standardsStatus("draft"), at(prop1, "extension[0]"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AddElementRule should add an element with all standards flags set to false")
    void allStandardsFlagsFalse() {
      Logical logical = new Logical("MyTestModel");
      logical.setId("MyModel");

      AddElementRule addElementRule = new AddElementRule("prop1");
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = types(t("string"));
      addElementRule.short_ = "short definition";
      addElementRule.trialUse = false;
      addElementRule.normative = false;
      addElementRule.draft = false;

      logical.rules.add(addElementRule);
      doc.logicals.put(logical.name, logical);
      exporter.exportStructDef(logical);
      StructureDefinition exported = pkg.logicals.get(0);

      assertEquals(2, exported.elements.size());

      ElementDefinition prop1 = exported.findElement("MyModel.prop1");
      // When standards flags are set to false, the underlying extension is not created
      assertNull(at(prop1, "extension"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AddElementRule should log an error when more than one standards flag is set to true")
    void multipleStandardsFlags() {
      Logical logical = new Logical("MyTestModel");
      logical.setId("MyModel");

      AddElementRule addElementRule = new AddElementRule("prop1").<AddElementRule>withFile("MultiFlags.fsh")
          .withLocation(3, 1, 8, 12);
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = types(t("string"));
      addElementRule.short_ = "short definition";
      addElementRule.trialUse = true;
      addElementRule.normative = true;
      addElementRule.draft = true;

      logical.rules.add(addElementRule);
      doc.logicals.put(logical.name, logical);

      exporter.exportStructDef(logical);
      StructureDefinition exported = pkg.logicals.get(0);

      assertEquals(2, exported.elements.size());

      ElementDefinition prop1 = exported.findElement("MyModel.prop1");
      // When multiple standards flags are set to true and an error is logged,
      // the underlying extension is not created
      assertNull(at(prop1, "extension"));
      assertMatches("File: MultiFlags\\.fsh.*Line: 3 - 8\\D*", "s", loggerSpy.getLastMessage("error"));
      assertMatches("multiple standards status", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AddElementRule should add an element with supported doc attributes")
    void supportedDocAttributes() {
      Logical logical = new Logical("MyTestModel");
      logical.setId("MyModel");
      logical.title = "MyTestModel title is here";
      logical.description = "MyTestModel description is here";

      AddElementRule addElementRule = new AddElementRule("prop1");
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = types(t("dateTime"));
      addElementRule.short_ = "short description for prop1";
      addElementRule.definition = "definition for prop1";

      logical.rules.add(addElementRule);
      doc.logicals.put(logical.name, logical);
      exporter.exportStructDef(logical);
      StructureDefinition exported = pkg.logicals.get(0);

      assertEquals(2, exported.elements.size());
      assertEquals(logical.title, at(exported.elements.get(0), "short"));
      assertEquals(logical.description, at(exported.elements.get(0), "definition"));

      ElementDefinition prop1 = exported.findElement("MyModel.prop1");
      assertEquals("short description for prop1", at(prop1, "short"));
      assertEquals("definition for prop1", at(prop1, "definition"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AddElementRule should log an error and add an element when an element name contains a prohibited special character or is more than 64 characters long")
    void prohibitedCharactersOrTooLong() {
      // Resource: MyTestResource
      // Id: MyResource
      // * some!thing 0..1 BackboneElement "some! thing"
      // * some!thing.normal 0..1 string "something normal"
      // * some!thing.strange? 0..1 boolean "something strange"
      // * some!thing.thatIsFarTooLongToBeAReasonableElementNameAccordingToTheSpecifications 0..1 string "this one is long"
      // * another 0..1 BackboneElement "another element"
      // * another.this&that 0..1 boolean "this & that"
      // * accordingToAllKnownLawsOfAviation,ThereIsNoWayABeeShouldBeAbleToFly 1..1 boolean "the bee, of course, flies anyway"
      // * acceptable[x] 0..1 string or boolean "acceptable choice element"
      // * thisSeemsLikeItMightBeTooLongButItIsActuallyJustBarelyAcceptable[x] 0..1 string or boolean "just barely short enough"
      Resource resource = new Resource("MyTestResource");
      resource.setId("MyResource");

      AddElementRule elementSomething = new AddElementRule("some!thing").<AddElementRule>withFile("ErrorElements.fsh")
          .withLocation(3, 1, 3, 47);
      elementSomething.min = 0.0;
      elementSomething.max = "1";
      elementSomething.types = types(t("BackboneElement"));
      elementSomething.short_ = "some! thing";

      AddElementRule elementNormal = new AddElementRule("some!thing.normal")
          .<AddElementRule>withFile("ErrorElements.fsh").withLocation(4, 1, 4, 50);
      elementNormal.min = 0.0;
      elementNormal.max = "1";
      elementNormal.types = types(t("string"));
      elementNormal.short_ = "something normal";

      AddElementRule elementStrange = new AddElementRule("some!thing.strange?")
          .<AddElementRule>withFile("ErrorElements.fsh").withLocation(5, 1, 5, 54);
      elementStrange.min = 0.0;
      elementStrange.max = "1";
      elementStrange.types = types(t("boolean"));
      elementStrange.short_ = "something strange";

      AddElementRule elementTooLong = new AddElementRule(
          "some!thing.thatIsFarTooLongToBeAReasonableElementNameAccordingToTheSpecifications")
          .<AddElementRule>withFile("ErrorElements.fsh").withLocation(6, 1, 6, 114);
      elementTooLong.min = 0.0;
      elementTooLong.max = "1";
      elementTooLong.types = types(t("string"));
      elementTooLong.short_ = "this one is long";

      AddElementRule elementAnother = new AddElementRule("another").<AddElementRule>withFile("ErrorElements.fsh")
          .withLocation(7, 1, 7, 48);
      elementAnother.min = 0.0;
      elementAnother.max = "1";
      elementAnother.types = types(t("BackboneElement"));
      elementAnother.short_ = "another element";

      AddElementRule elementThisAndThat = new AddElementRule("another.this&that")
          .<AddElementRule>withFile("ErrorElements.fsh").withLocation(8, 1, 8, 46);
      elementThisAndThat.min = 0.0;
      elementThisAndThat.max = "1";
      elementThisAndThat.types = types(t("boolean"));
      elementThisAndThat.short_ = "this & that";

      AddElementRule elementBee = new AddElementRule(
          "accordingToAllKnownLawsOfAviation,ThereIsNoWayABeeShouldBeAbleToFly")
          .<AddElementRule>withFile("ErrorElements.fsh").withLocation(9, 1, 9, 117);
      elementBee.min = 1.0;
      elementBee.max = "1";
      elementBee.types = types(t("boolean"));
      elementBee.short_ = "the bee, of course, flies anyway";

      AddElementRule elementAcceptable = new AddElementRule("acceptable[x]")
          .<AddElementRule>withFile("ErrorElements.fsh").withLocation(10, 1, 10, 66);
      elementAcceptable.min = 0.0;
      elementAcceptable.max = "1";
      elementAcceptable.types = types(t("string"), t("boolean"));
      elementAcceptable.short_ = "acceptable choice element";

      AddElementRule elementBarelyAcceptable = new AddElementRule(
          "thisSeemsLikeItMightBeTooLongButItIsActuallyJustBarelyAcceptable[x]")
          .<AddElementRule>withFile("ErrorElements.fsh").withLocation(11, 1, 11, 119);
      elementBarelyAcceptable.min = 0.0;
      elementBarelyAcceptable.max = "1";
      elementBarelyAcceptable.types = types(t("string"), t("boolean"));
      elementBarelyAcceptable.short_ = "just barely short enough";

      resource.rules.addAll(List.of(elementSomething, elementNormal, elementStrange, elementTooLong, elementAnother,
          elementThisAndThat, elementBee, elementAcceptable, elementBarelyAcceptable));

      doc.resources.put(resource.name, resource);
      exporter.exportStructDef(resource);
      StructureDefinition exported = pkg.resources.get(0);

      assertEquals("MyTestResource", at(exported, "name"));
      assertEquals("MyResource", at(exported, "id"));
      assertEquals("MyResource", at(exported, "type"));
      assertEquals(18, exported.elements.size()); // all nine new elements are added
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
      assertEquals(5, loggerSpy.getAllMessages("error").size());
      assertMatches(
          "some!thing.*Element names cannot include some special characters.*File: ErrorElements\\.fsh.*Line: 3\\D*",
          "s", loggerSpy.getMessageAtIndex(0, "error"));
      assertMatches(
          "some!thing\\.strange\\?.*Element names cannot include some special characters.*File: ErrorElements\\.fsh.*Line: 5\\D*",
          "s", loggerSpy.getMessageAtIndex(1, "error"));
      assertMatches(
          "some!thing\\.thatIsFarTooLongToBeAReasonableElementNameAccordingToTheSpecifications.*Element names must be at most 64 characters long.*File: ErrorElements\\.fsh.*Line: 6\\D*",
          "s", loggerSpy.getMessageAtIndex(2, "error"));
      assertMatches(
          "another\\.this&that.*Element names cannot include some special characters.*File: ErrorElements\\.fsh.*Line: 8\\D*",
          "s", loggerSpy.getMessageAtIndex(3, "error"));
      assertMatches(
          "accordingToAllKnownLawsOfAviation,ThereIsNoWayABeeShouldBeAbleToFly.*Element names cannot include some special characters and must be at most 64 characters long.*File: ErrorElements\\.fsh.*Line: 9\\D*",
          "s", loggerSpy.getMessageAtIndex(4, "error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AddElementRule should log a warning and add an element when an element name is not a simple alphanumeric")
    void notSimpleAlphanumeric() {
      // Logical: MyTestModel
      // Id: MyModel
      // * 4 0..1 string "This element's name is four"
      // * 35 0..* BackboneElement "This complex element is three five"
      // * 35.79 1..1 boolean "This is seven nine, contained within three five"
      // * 35.extra 0..1 string "This is an extra string contained within three five"
      // * cookie 1..* BackboneElement "Cookie is mandatory"
      // * cookie.24b 1..1 integer "This is two four bee, contained within cookie"
      // * jalapeño 0..* integer "spicy and delicious"
      // * acceptable[x] 0..1 string or boolean "acceptable choice element"
      Logical logical = new Logical("MyTestModel");
      logical.setId("MyModel");

      AddElementRule elementFour = new AddElementRule("4").<AddElementRule>withFile("NumericElements.fsh")
          .withLocation(3, 1, 3, 45);
      elementFour.min = 0.0;
      elementFour.max = "1";
      elementFour.types = types(t("string"));
      elementFour.short_ = "This element's name is four";

      AddElementRule elementThreeFive = new AddElementRule("35").<AddElementRule>withFile("NumericElements.fsh")
          .withLocation(4, 1, 4, 61);
      elementThreeFive.min = 0.0;
      elementThreeFive.max = "*";
      elementThreeFive.types = types(t("BackboneElement"));
      elementThreeFive.short_ = "This complex element is three five";

      AddElementRule elementSevenNine = new AddElementRule("35.79").<AddElementRule>withFile("NumericElements.fsh")
          .withLocation(5, 1, 5, 69);
      elementSevenNine.min = 1.0;
      elementSevenNine.max = "1";
      elementSevenNine.types = types(t("boolean"));
      elementSevenNine.short_ = "This is seven nine, contained within three five";

      AddElementRule elementExtra = new AddElementRule("35.extra").<AddElementRule>withFile("NumericElements.fsh")
          .withLocation(6, 1, 6, 75);
      elementExtra.min = 0.0;
      elementExtra.max = "1";
      elementExtra.types = types(t("string"));
      elementExtra.short_ = "This is an extra string contained within three five";

      AddElementRule elementCookie = new AddElementRule("cookie").<AddElementRule>withFile("NumericElements.fsh")
          .withLocation(7, 1, 7, 49);
      elementCookie.min = 1.0;
      elementCookie.max = "*";
      elementCookie.types = types(t("BackboneElement"));
      elementCookie.short_ = "Cookie is mandatory";

      AddElementRule elementTwoFourBee = new AddElementRule("cookie.24b")
          .<AddElementRule>withFile("NumericElements.fsh").withLocation(8, 1, 8, 72);
      elementTwoFourBee.min = 1.0;
      elementTwoFourBee.max = "1";
      elementTwoFourBee.types = types(t("integer"));
      elementTwoFourBee.short_ = "This is two four bee, contained within cookie";

      AddElementRule elementSpicy = new AddElementRule("jalapeño").<AddElementRule>withFile("NumericElements.fsh")
          .withLocation(9, 1, 9, 45);
      elementSpicy.min = 1.0;
      elementSpicy.max = "*";
      elementSpicy.types = types(t("integer"));
      elementSpicy.short_ = "spicy and delicious";

      AddElementRule elementAcceptable = new AddElementRule("acceptable[x]")
          .<AddElementRule>withFile("ErrorElements.fsh").withLocation(10, 1, 10, 66);
      elementAcceptable.min = 0.0;
      elementAcceptable.max = "1";
      elementAcceptable.types = types(t("string"), t("boolean"));
      elementAcceptable.short_ = "acceptable choice element";

      logical.rules.addAll(List.of(elementFour, elementThreeFive, elementSevenNine, elementExtra, elementCookie,
          elementTwoFourBee, elementSpicy, elementAcceptable));

      doc.logicals.put(logical.name, logical);
      exporter.exportStructDef(logical);
      StructureDefinition exported = pkg.logicals.get(0);

      assertEquals("MyTestModel", at(exported, "name"));
      assertEquals("MyModel", at(exported, "id"));
      assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/MyModel", at(exported, "type"));
      assertEquals(9, exported.elements.size()); // all eight new elements are be added
      assertEquals(0, loggerSpy.getAllMessages("error").size());
      assertEquals(5, loggerSpy.getAllMessages("warn").size());
      assertMatches("4.*Element names should be simple alphanumerics.*File: NumericElements\\.fsh.*Line: 3\\D*", "s",
          loggerSpy.getMessageAtIndex(0, "warn"));
      assertMatches("35.*Element names should be simple alphanumerics.*File: NumericElements\\.fsh.*Line: 4\\D*", "s",
          loggerSpy.getMessageAtIndex(1, "warn"));
      assertMatches("35\\.79.*Element names should be simple alphanumerics.*File: NumericElements\\.fsh.*Line: 5\\D*",
          "s", loggerSpy.getMessageAtIndex(2, "warn"));
      assertMatches(
          "cookie\\.24b.*Element names should be simple alphanumerics.*File: NumericElements\\.fsh.*Line: 8\\D*", "s",
          loggerSpy.getMessageAtIndex(3, "warn"));
      assertMatches("jalapeño.*Element names should be simple alphanumerics.*File: NumericElements\\.fsh.*Line: 9\\D*",
          "s", loggerSpy.getMessageAtIndex(4, "warn"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AddElementRule should log an error when SDRule added before AddElementRule")
    void sdRuleBeforeAddElementRule() {
      Logical logical = new Logical("MyTestModel");
      logical.setId("MyModel");

      CaretValueRule caretRule = new CaretValueRule("prop1").<CaretValueRule>withFile("BadOrder.fsh")
          .withLocation(3, 1, 3, 45);
      caretRule.caretPath = "comment";
      caretRule.value = "this is a comment";
      logical.rules.add(caretRule);

      AddElementRule addElementRule = new AddElementRule("prop1").<AddElementRule>withFile("BadOrder.fsh")
          .withLocation(4, 1, 9, 12);
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = types(t("string"));
      addElementRule.short_ = "prop1 definition";
      logical.rules.add(addElementRule);

      doc.logicals.put(logical.name, logical);

      exporter.exportStructDef(logical);

      assertMatches("File: BadOrder\\.fsh.*Line: 3\\D*", "s", loggerSpy.getLastMessage("error"));
      assertMatches("No element found at path prop1 for CaretValueRule in MyTestModel, skipping rule", "s",
          loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AddElementRule should log an error when path does not have [x] for multiple data types in AddElementRule")
    void missingChoiceSuffixForMultipleTypes() {
      Logical logical = new Logical("MyTestModel");
      logical.setId("MyModel");

      AddElementRule addElementRule = new AddElementRule("prop1").<AddElementRule>withFile("BadPath.fsh")
          .withLocation(3, 1, 8, 12);
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = types(t("string"), t("Annotation"), OnlyRuleType.reference("DocumentReference"));
      addElementRule.short_ = "prop1 definition";
      logical.rules.add(addElementRule);

      doc.logicals.put(logical.name, logical);

      exporter.exportStructDef(logical);

      assertMatches("File: BadPath\\.fsh.*Line: 3 - 8\\D*", "s", loggerSpy.getLastMessage("error"));
      assertMatches("As a FHIR choice data type, the specified prop1 for AddElementRule must end with '\\[x\\]'", "s",
          loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AddElementRule should not log an error when path does not have [x] for multiple reference types in AddElementRule")
    void noChoiceSuffixNeededForMultipleReferences() {
      Logical logical = new Logical("MyTestModel");
      logical.setId("MyModel");

      AddElementRule addElementRule = new AddElementRule("prop1").<AddElementRule>withFile("GoodPath.fsh")
          .withLocation(3, 1, 8, 12);
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = types(OnlyRuleType.reference("Organization"), OnlyRuleType.reference("Location"));
      addElementRule.short_ = "prop1 definition with multiple references";
      logical.rules.add(addElementRule);

      doc.logicals.put(logical.name, logical);

      exporter.exportStructDef(logical);

      assertNull(loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AddElementRule should not log an error when path does not have [x] for multiple canonical types in AddElementRule")
    void noChoiceSuffixNeededForMultipleCanonicals() {
      Logical logical = new Logical("MyTestModel");
      logical.setId("MyModel");

      AddElementRule addElementRule = new AddElementRule("prop1").<AddElementRule>withFile("GoodPath.fsh")
          .withLocation(3, 1, 8, 12);
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = types(OnlyRuleType.canonical("Organization"), OnlyRuleType.canonical("Location"));
      addElementRule.short_ = "prop1 definition with multiple canonicals";
      logical.rules.add(addElementRule);

      doc.logicals.put(logical.name, logical);

      exporter.exportStructDef(logical);

      assertNull(loggerSpy.getLastMessage("error"));
    }
  }

  @Nested
  @DisplayName("#CardRule")
  class CardRuleTests {
    @Test
    @DisplayName("StructureDefinitionExporter R4 #CardRule should apply a correct card rule")
    void correctCardRule() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      CardRule rule = new CardRule("subject");
      rule.min = 1.0;
      rule.max = "1";
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Observation");

      ElementDefinition baseCard = baseStructDef.findElement("Observation.subject");
      ElementDefinition changedCard = sd.findElement("Observation.subject");

      assertJsEqual(0, at(baseCard, "min"));
      assertEquals("1", at(baseCard, "max"));
      assertJsEqual(1, at(changedCard, "min"));
      assertEquals("1", at(changedCard, "max"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #CardRule should not apply an incorrect card rule")
    void incorrectCardRule() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      CardRule rule = new CardRule("status").<CardRule>withFile("Wrong.fsh").withLocation(5, 4, 5, 11);
      rule.min = 0.0;
      rule.max = "1";
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Observation");

      ElementDefinition baseCard = baseStructDef.findElement("Observation.status");
      ElementDefinition changedCard = sd.findElement("Observation.status");

      assertJsEqual(1, at(baseCard, "min"));
      assertEquals("1", at(baseCard, "max"));
      assertJsEqual(1, at(changedCard, "min"));
      assertEquals("1", at(changedCard, "max"));
      assertMatches("File: Wrong\\.fsh.*Line: 5\\D*", "s", loggerSpy.getLastMessage("error"));

      // This is one of the spots where we debug log a stack trace just in case the thrown error is an unexpected one
      // (Java stack frames are package-qualified: "at org.hl7.fhir.sushi.fhirtypes.ElementDefinition.constrain...")
      assertMatches("at (\\S+\\.)?ElementDefinition\\.constrainCardinality", "s", loggerSpy.getLastMessage("debug"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #CardRule should apply a card rule with only min specified")
    void onlyMinSpecified() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      CardRule rule = new CardRule("category");
      rule.min = 1.0;
      rule.max = "";
      profile.rules.add(rule); // * category 1..

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Observation");

      ElementDefinition baseCard = baseStructDef.findElement("Observation.category");
      ElementDefinition changedCard = sd.findElement("Observation.category");

      assertJsEqual(0, at(baseCard, "min"));
      assertEquals("*", at(baseCard, "max"));
      assertJsEqual(1, at(changedCard, "min")); // Only min cardinality is updated
      assertEquals("*", at(changedCard, "max")); // Max remains same as base
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #CardRule should apply a card rule with only max specified")
    void onlyMaxSpecified() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      CardRule rule = new CardRule("category");
      rule.min = Double.NaN;
      rule.max = "3";
      profile.rules.add(rule); // * category ..3

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Observation");

      ElementDefinition baseCard = baseStructDef.findElement("Observation.category");
      ElementDefinition changedCard = sd.findElement("Observation.category");

      assertJsEqual(0, at(baseCard, "min"));
      assertEquals("*", at(baseCard, "max"));
      assertJsEqual(0, at(changedCard, "min")); // Min cardinality remains unchanged
      assertEquals("3", at(changedCard, "max")); // Only max is changed
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #CardRule should not apply an incorrect min only card rule")
    void incorrectMinOnly() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      CardRule rule = new CardRule("status").<CardRule>withFile("BadCard.fsh").withLocation(3, 4, 3, 11);
      rule.min = 0.0;
      rule.max = "";
      profile.rules.add(rule); // * status 0..

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Observation");

      ElementDefinition baseCard = baseStructDef.findElement("Observation.status");
      ElementDefinition changedCard = sd.findElement("Observation.status");

      assertJsEqual(1, at(baseCard, "min"));
      assertEquals("1", at(baseCard, "max"));
      assertJsEqual(1, at(changedCard, "min"));
      assertEquals("1", at(changedCard, "max")); // Neither card changes
      assertMatches("File: BadCard\\.fsh.*Line: 3\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #CardRule should not apply an incorrect max only card rule")
    void incorrectMaxOnly() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      CardRule rule = new CardRule("status").<CardRule>withFile("BadCard.fsh").withLocation(3, 4, 3, 11);
      rule.min = Double.NaN;
      rule.max = "2";
      profile.rules.add(rule); // * status ..2

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Observation");

      ElementDefinition baseCard = baseStructDef.findElement("Observation.status");
      ElementDefinition changedCard = sd.findElement("Observation.status");

      assertJsEqual(1, at(baseCard, "min"));
      assertEquals("1", at(baseCard, "max"));
      assertJsEqual(1, at(changedCard, "min"));
      assertEquals("1", at(changedCard, "max")); // Neither card changes
      assertMatches("File: BadCard\\.fsh.*Line: 3\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #CardRule should not apply a card rule with no sides specified")
    void noSidesSpecified() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      CardRule rule = new CardRule("status").<CardRule>withFile("BadCard.fsh").withLocation(3, 4, 3, 11);
      rule.min = Double.NaN;
      rule.max = "";
      profile.rules.add(rule); // * status ..

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Observation");

      ElementDefinition baseCard = baseStructDef.findElement("Observation.status");
      ElementDefinition changedCard = sd.findElement("Observation.status");

      assertJsEqual(1, at(baseCard, "min"));
      assertEquals("1", at(baseCard, "max"));
      assertJsEqual(1, at(changedCard, "min"));
      assertEquals("1", at(changedCard, "max")); // Neither card changes. Error logged on import side.
    }
  }

  @Nested
  @DisplayName("#FlagRule")
  class FlagRuleTests {
    @Test
    @DisplayName("StructureDefinitionExporter R4 #FlagRule should apply a valid flag rule")
    void validFlagRule() {
      Profile profile = new Profile("Foo");
      profile.parent = "DiagnosticReport";

      FlagRule rule = new FlagRule("conclusion");
      rule.modifier = false;
      rule.mustSupport = true;
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("DiagnosticReport");

      ElementDefinition baseElement = baseStructDef.findElement("DiagnosticReport.conclusion");
      ElementDefinition changedElement = sd.findElement("DiagnosticReport.conclusion");
      assertFalse(Js.truthy(at(baseElement, "isModifier")));
      assertFalse(Js.truthy(at(baseElement, "mustSupport")));
      assertFalse(Js.truthy(at(baseElement, "isSummary")));
      assertEquals(false, at(changedElement, "isModifier"));
      assertEquals(true, at(changedElement, "mustSupport"));
      assertFalse(Js.truthy(at(baseElement, "isSummary")));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #FlagRule should apply a flag rule that specifies an element is trial use")
    void trialUse() {
      // Profile: HasTrial
      // Parent: Observation
      // * bodySite TU
      Profile profile = new Profile("HasTrial");
      profile.parent = "Observation";
      FlagRule flagRule = new FlagRule("bodySite");
      flagRule.trialUse = true;
      profile.rules.add(flagRule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition bodySite = sd.findElement("Observation.bodySite");
      assertEquals(1, len(at(bodySite, "extension")));
      assertJsonEqual(standardsStatus("trial-use"), at(bodySite, "extension[0]"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #FlagRule should apply a flag rule that specifies an element is normative")
    void normative() {
      // Profile: HasTrial
      // Parent: Observation
      // * method N
      Profile profile = new Profile("HasTrial");
      profile.parent = "Observation";
      FlagRule flagRule = new FlagRule("method");
      flagRule.normative = true;
      profile.rules.add(flagRule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition method = sd.findElement("Observation.method");
      assertEquals(1, len(at(method, "extension")));
      assertJsonEqual(standardsStatus("normative"), at(method, "extension[0]"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #FlagRule should apply a flag rule that specifies an element is a draft")
    void draft() {
      // Profile: HasDraft
      // Parent: DiagnosticReport
      // * media D
      Profile profile = new Profile("HasDraft");
      profile.parent = "DiagnosticReport";
      FlagRule flagRule = new FlagRule("media");
      flagRule.draft = true;
      profile.rules.add(flagRule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition media = sd.findElement("DiagnosticReport.media");
      assertEquals(1, len(at(media, "extension")));
      assertJsonEqual(standardsStatus("draft"), at(media, "extension[0]"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #FlagRule should log an error when more than one standards status flag rule is specified on an element")
    void multipleStandardsStatus() {
      // Profile: HasDraft
      // Parent: DiagnosticReport
      // * media D TU
      Profile profile = new Profile("HasDraft");
      profile.parent = "DiagnosticReport";
      FlagRule flagRule = new FlagRule("media").<FlagRule>withFile("MultiStatus.fsh").withLocation(3, 1, 3, 12);
      flagRule.draft = true;
      flagRule.trialUse = true;
      profile.rules.add(flagRule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition media = sd.findElement("DiagnosticReport.media");
      assertNull(at(media, "extension"));
      assertMatches("File: MultiStatus\\.fsh.*Line: 3\\D*", "s", loggerSpy.getLastMessage("error"));
      assertMatches("multiple standards status", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #FlagRule should apply a flag rule that changes the existing standards status")
    void changesExistingStandardsStatus() {
      // Profile: HasTrial
      // Parent: Observation
      // * focus N
      Profile profile = new Profile("HasTrial");
      profile.parent = "Observation";
      FlagRule flagRule = new FlagRule("focus").<FlagRule>withFile("MultiStatus.fsh").withLocation(8, 3, 8, 14);
      flagRule.normative = true;
      profile.rules.add(flagRule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition focus = sd.findElement("Observation.focus");
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Observation");
      ElementDefinition baseFocus = baseStructDef.findElement("Observation.focus");
      assertContainsJson(standardsStatus("trial-use"), at(baseFocus, "extension"));
      assertContainsJson(standardsStatus("normative"), at(focus, "extension"));
    }
  }

  @Nested
  @DisplayName("#ValueSetRule")
  class ValueSetRuleTests {
    @Test
    @DisplayName("StructureDefinitionExporter R4 #ValueSetRule should apply a correct value set rule to an unbound string")
    void unboundString() {
      Profile profile = new Profile("Junk");
      profile.parent = "Appointment";

      BindingRule vsRule = new BindingRule("description");
      vsRule.valueSet = "http://example.org/fhir/ValueSet/some-valueset";
      vsRule.strength = "extensible";
      profile.rules.add(vsRule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Appointment");
      ElementDefinition baseElement = baseStructDef.findElement("Appointment.description");
      ElementDefinition changedElement = sd.findElement("Appointment.description");
      assertNull(at(baseElement, "binding"));
      assertEquals("http://example.org/fhir/ValueSet/some-valueset", at(changedElement, "binding.valueSet"));
      assertEquals("extensible", at(changedElement, "binding.strength"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ValueSetRule should apply a correct value set rule that overrides a previous binding")
    void overridesPreviousBinding() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      BindingRule vsRule = new BindingRule("category");
      vsRule.valueSet = "http://example.org/fhir/ValueSet/some-valueset";
      vsRule.strength = "extensible";
      profile.rules.add(vsRule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Observation");
      ElementDefinition baseElement = baseStructDef.findElement("Observation.category");
      ElementDefinition changedElement = sd.findElement("Observation.category");
      assertEquals("http://hl7.org/fhir/ValueSet/observation-category", at(baseElement, "binding.valueSet"));
      assertEquals("preferred", at(baseElement, "binding.strength"));
      assertEquals("http://example.org/fhir/ValueSet/some-valueset", at(changedElement, "binding.valueSet"));
      assertEquals("extensible", at(changedElement, "binding.strength"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ValueSetRule should apply a correct value set rule when the VS is referenced by name")
    void vsReferencedByName() {
      FshValueSet customCategoriesVS = new FshValueSet("CustomCategories");
      customCategoriesVS.setId("custom-categories");
      doc.valueSets.put("CustomCategories", customCategoriesVS);

      Profile profile = new Profile("Foo");
      profile.parent = "Observation";
      BindingRule vsRule = new BindingRule("category");
      vsRule.valueSet = "CustomCategories";
      vsRule.strength = "extensible";
      profile.rules.add(vsRule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition element = sd.findElement("Observation.category");
      assertEquals("http://hl7.org/fhir/us/minimal/ValueSet/custom-categories", at(element, "binding.valueSet"));
      assertEquals("extensible", at(element, "binding.strength"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ValueSetRule should apply a correct value set rule when the VS has a rule that sets its name and it is referenced by name")
    void vsWithNameRuleReferencedByName() {
      FshValueSet customCategoriesVS = new FshValueSet("custom-categories");
      CaretValueRule customName = new CaretValueRule("");
      customName.caretPath = "name";
      customName.value = "CustomCategories";
      customCategoriesVS.rules.add(customName);
      doc.valueSets.put(customCategoriesVS.name, customCategoriesVS);

      Profile profile = new Profile("Foo");
      profile.parent = "Observation";
      BindingRule vsRule = new BindingRule("category");
      vsRule.valueSet = "CustomCategories";
      vsRule.strength = "extensible";
      profile.rules.add(vsRule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition element = sd.findElement("Observation.category");
      assertEquals("http://hl7.org/fhir/us/minimal/ValueSet/custom-categories", at(element, "binding.valueSet"));
      assertEquals("extensible", at(element, "binding.strength"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ValueSetRule should apply a correct value set rule when the VS specifies a version")
    void vsWithVersion() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      BindingRule vsRule = new BindingRule("category");
      vsRule.valueSet = "http://hl7.org/fhir/ValueSet/allergyintolerance-clinical|4.0.1";
      vsRule.strength = "extensible";
      profile.rules.add(vsRule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Observation");
      ElementDefinition baseElement = baseStructDef.findElement("Observation.category");
      ElementDefinition changedElement = sd.findElement("Observation.category");
      assertEquals("http://hl7.org/fhir/ValueSet/observation-category", at(baseElement, "binding.valueSet"));
      assertEquals("preferred", at(baseElement, "binding.strength"));
      assertEquals("http://hl7.org/fhir/ValueSet/allergyintolerance-clinical|4.0.1",
          at(changedElement, "binding.valueSet"));
      assertEquals("extensible", at(changedElement, "binding.strength"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ValueSetRule should use the url specified in a CaretValueRule when referencing a named value set")
    void urlFromCaretValueRule() {
      FshValueSet customCategoriesVS = new FshValueSet("CustomCategories");
      customCategoriesVS.setId("custom-categories");
      CaretValueRule caretValueRule = new CaretValueRule("");
      caretValueRule.caretPath = "url";
      caretValueRule.value = "http://different-url.com/ValueSet/custom-categories";
      customCategoriesVS.rules.add(caretValueRule);
      doc.valueSets.put("CustomCategories", customCategoriesVS);

      Profile profile = new Profile("Foo");
      profile.parent = "Observation";
      BindingRule vsRule = new BindingRule("category");
      vsRule.valueSet = "CustomCategories";
      vsRule.strength = "extensible";
      profile.rules.add(vsRule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition element = sd.findElement("Observation.category");
      assertEquals("http://different-url.com/ValueSet/custom-categories", at(element, "binding.valueSet"));
      assertEquals("extensible", at(element, "binding.strength"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ValueSetRule should apply a value set rule on an element that has the #can-bind characteristic")
    void canBindCharacteristic() {
      // Logical: BindableLM
      // Characteristics: #can-bind
      Logical bindableLM = new Logical("BindableLM");
      bindableLM.characteristics = new ArrayList<>(List.of("can-bind"));
      doc.logicals.put(bindableLM.name, bindableLM);

      FshValueSet erasVS = new FshValueSet("ErasVS");
      doc.valueSets.put(erasVS.name, erasVS);

      // Logical: FutureResource
      // * era  0..1 BindableLM "Future era"
      // * era from Eras (extensible)
      Logical logical = new Logical("FutureResource");
      AddElementRule eraElement = new AddElementRule("era");
      eraElement.min = 0.0;
      eraElement.max = "1";
      eraElement.types = types(t("BindableLM"));
      eraElement.short_ = "Future era";
      BindingRule eraBindingRule = new BindingRule("era");
      eraBindingRule.valueSet = "ErasVS";
      eraBindingRule.strength = "extensible";
      logical.rules.addAll(List.of(eraElement, eraBindingRule));
      doc.logicals.put(logical.name, logical);

      exporter.exportStructDef(logical);
      StructureDefinition sd = pkg.logicals.get(0);
      ElementDefinition element = sd.findElement("FutureResource.era");
      assertEquals("http://hl7.org/fhir/us/minimal/ValueSet/ErasVS", at(element, "binding.valueSet"));
      assertEquals("extensible", at(element, "binding.strength"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ValueSetRule should apply a value set rule on an element that has the #can-bind type characteristic extension")
    void canBindExtension() {
      // Logical: BindableLM
      // * ^extension[0].url = "http://hl7.org/fhir/StructureDefinition/structuredefinition-type-characteristics"
      // * ^extension[0].valueCode = #can-bind
      Logical bindableLM = new Logical("BindableLM");
      CaretValueRule typeCharacteristicsUrl = new CaretValueRule("");
      typeCharacteristicsUrl.caretPath = "extension[0].url";
      typeCharacteristicsUrl.value =
          "http://hl7.org/fhir/StructureDefinition/structuredefinition-type-characteristics";
      CaretValueRule typeCharacteristicsValue = new CaretValueRule("");
      typeCharacteristicsValue.caretPath = "extension[0].valueCode";
      typeCharacteristicsValue.value = new FshCode("can-bind");
      bindableLM.rules.addAll(List.of(typeCharacteristicsUrl, typeCharacteristicsValue));
      doc.logicals.put(bindableLM.name, bindableLM);

      FshValueSet erasVS = new FshValueSet("ErasVS");
      doc.valueSets.put(erasVS.name, erasVS);

      // Logical: FutureResource
      // * era  0..1 BindableLM "Future era"
      // * era from Eras (extensible)
      Logical logical = new Logical("FutureResource");
      AddElementRule eraElement = new AddElementRule("era");
      eraElement.min = 0.0;
      eraElement.max = "1";
      eraElement.types = types(t("BindableLM"));
      eraElement.short_ = "Future era";
      BindingRule eraBindingRule = new BindingRule("era");
      eraBindingRule.valueSet = "ErasVS";
      eraBindingRule.strength = "extensible";
      logical.rules.addAll(List.of(eraElement, eraBindingRule));
      doc.logicals.put(logical.name, logical);

      exporter.exportStructDef(logical);
      StructureDefinition sd = pkg.logicals.get(0);
      ElementDefinition element = sd.findElement("FutureResource.era");
      assertEquals("http://hl7.org/fhir/us/minimal/ValueSet/ErasVS", at(element, "binding.valueSet"));
      assertEquals("extensible", at(element, "binding.strength"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ValueSetRule should apply a value set rule on an element that has the #can-bind type characteristic extension using extension path syntax with url")
    void canBindExtensionUrlPathSyntax() {
      // Logical: BindableLM
      // * ^extension[http://hl7.org/fhir/StructureDefinition/structuredefinition-type-characteristics].valueCode = #can-bind
      Logical bindableLM = new Logical("BindableLM");
      CaretValueRule typeCharacteristicsValue = new CaretValueRule("");
      typeCharacteristicsValue.caretPath =
          "extension[http://hl7.org/fhir/StructureDefinition/structuredefinition-type-characteristics].valueCode";
      typeCharacteristicsValue.value = new FshCode("can-bind");
      bindableLM.rules.add(typeCharacteristicsValue);
      doc.logicals.put(bindableLM.name, bindableLM);

      FshValueSet erasVS = new FshValueSet("ErasVS");
      doc.valueSets.put(erasVS.name, erasVS);

      // Logical: FutureResource
      // * era  0..1 BindableLM "Future era"
      // * era from Eras (extensible)
      Logical logical = new Logical("FutureResource");
      AddElementRule eraElement = new AddElementRule("era");
      eraElement.min = 0.0;
      eraElement.max = "1";
      eraElement.types = types(t("BindableLM"));
      eraElement.short_ = "Future era";
      BindingRule eraBindingRule = new BindingRule("era");
      eraBindingRule.valueSet = "ErasVS";
      eraBindingRule.strength = "extensible";
      logical.rules.addAll(List.of(eraElement, eraBindingRule));
      doc.logicals.put(logical.name, logical);

      exporter.exportStructDef(logical);
      StructureDefinition sd = pkg.logicals.get(0);
      ElementDefinition element = sd.findElement("FutureResource.era");
      assertEquals("http://hl7.org/fhir/us/minimal/ValueSet/ErasVS", at(element, "binding.valueSet"));
      assertEquals("extensible", at(element, "binding.strength"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ValueSetRule should log a warning and apply a value set rule on an element that is missing the #can-bind characteristic and extension")
    void missingCanBind() {
      // Logical: NotBindableLM
      Logical notBindableLM = new Logical("NotBindableLM");
      doc.logicals.put(notBindableLM.name, notBindableLM);

      FshValueSet erasVS = new FshValueSet("ErasVS");
      doc.valueSets.put(erasVS.name, erasVS);

      // Logical: FutureResource
      // * era  0..1 NotBindableLM "Future era"
      // * era from Eras (extensible)
      Logical logical = new Logical("FutureResource");
      AddElementRule eraElement = new AddElementRule("era");
      eraElement.min = 0.0;
      eraElement.max = "1";
      eraElement.types = types(t("NotBindableLM"));
      eraElement.short_ = "Future era";
      BindingRule eraBindingRule = new BindingRule("era").<BindingRule>withFile("NoBinding.fsh")
          .withLocation(5, 1, 5, 25);
      eraBindingRule.valueSet = "ErasVS";
      eraBindingRule.strength = "extensible";
      logical.rules.addAll(List.of(eraElement, eraBindingRule));
      doc.logicals.put(logical.name, logical);

      exporter.exportStructDef(logical);
      StructureDefinition sd = pkg.logicals.get(0);
      ElementDefinition element = sd.findElement("FutureResource.era");
      assertEquals("http://hl7.org/fhir/us/minimal/ValueSet/ErasVS", at(element, "binding.valueSet"));
      assertEquals("extensible", at(element, "binding.strength"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
      assertEquals(1, loggerSpy.getAllMessages("warn").size());
      assertMatches(
          "Bindings can only be applied to logical model types with the #can-bind characteristic\\. .*remove the binding from FutureResource\\.era\\.",
          loggerSpy.getLastMessage("warn"));
      assertMatches("File: NoBinding\\.fsh.*Line: 5\\D*", "s", loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ValueSetRule should not apply a value set rule on an element that cannot support it")
    void elementCannotSupportBinding() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      BindingRule vsRule = new BindingRule("note").<BindingRule>withFile("Codeless.fsh").withLocation(6, 9, 6, 25);
      vsRule.valueSet = "http://example.org/fhir/ValueSet/some-valueset";
      vsRule.strength = "extensible";
      profile.rules.add(vsRule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Observation");
      ElementDefinition baseElement = baseStructDef.findElement("Observation.note");
      ElementDefinition changedElement = sd.findElement("Observation.note");
      assertNull(at(baseElement, "binding"));
      assertNull(at(changedElement, "binding"));
      assertMatches("File: Codeless\\.fsh.*Line: 6\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ValueSetRule should not override a binding with a less strict binding")
    void lessStrictBinding() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      BindingRule vsRule = new BindingRule("category").<BindingRule>withFile("Strict.fsh")
          .withLocation(9, 10, 9, 35);
      vsRule.valueSet = "http://example.org/fhir/ValueSet/some-valueset";
      vsRule.strength = "example";
      profile.rules.add(vsRule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Observation");
      ElementDefinition baseElement = baseStructDef.findElement("Observation.category");
      ElementDefinition changedElement = sd.findElement("Observation.category");
      assertEquals("http://hl7.org/fhir/ValueSet/observation-category", at(baseElement, "binding.valueSet"));
      assertEquals("preferred", at(baseElement, "binding.strength"));
      assertEquals("http://hl7.org/fhir/ValueSet/observation-category", at(changedElement, "binding.valueSet"));
      assertEquals("preferred", at(changedElement, "binding.strength"));
      assertMatches("File: Strict\\.fsh.*Line: 9\\D*", "s", loggerSpy.getLastMessage("error"));
    }
  }
}
