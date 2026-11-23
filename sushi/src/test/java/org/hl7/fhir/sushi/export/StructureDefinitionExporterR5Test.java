package org.hl7.fhir.sushi.export;

import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertJsEqual;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertJsonEqual;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertMatches;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.at;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.testDefsPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.hl7.fhir.sushi.fhirtypes.ElementDefinition;
import org.hl7.fhir.sushi.fhirtypes.ElementDefinitionType;
import org.hl7.fhir.sushi.fhirtypes.StructureDefinition;
import org.hl7.fhir.sushi.fshtypes.Extension;
import org.hl7.fhir.sushi.fshtypes.FshCode;
import org.hl7.fhir.sushi.fshtypes.FshReference;
import org.hl7.fhir.sushi.fshtypes.Instance;
import org.hl7.fhir.sushi.fshtypes.Logical;
import org.hl7.fhir.sushi.fshtypes.Profile;
import org.hl7.fhir.sushi.fshtypes.rules.AddElementRule;
import org.hl7.fhir.sushi.fshtypes.rules.AssignmentRule;
import org.hl7.fhir.sushi.fshtypes.rules.OnlyRule;
import org.hl7.fhir.sushi.fshtypes.rules.OnlyRuleType;
import org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// port of sushi/test/export/StructureDefinitionExporter.test.ts (SUSHI 3.21.1), describe blocks
// 'StructureDefinitionExporter R5' (lines 11593-11792)
@DisplayName("StructureDefinitionExporter R5")
class StructureDefinitionExporterR5Test extends SdeTestBase {

  /**
   * TS beforeAll: getTestFHIRDefinitions(false, testDefsPath('r5-definitions')). The TS beforeEach (fresh doc, tank,
   * pkg, fisher, exporter; loggerSpy.reset()) is the same as SdeTestBase's.
   */
  @java.lang.Override
  TestFHIRDefinitions loadDefs() {
    return defs(false, testDefsPath("r5-definitions"));
  }

  private static List<OnlyRuleType> types(OnlyRuleType... t) {
    return new ArrayList<>(Arrays.asList(t));
  }

  private static OnlyRuleType cr(String type) {
    return OnlyRuleType.codeableReference(type);
  }

  @Test
  @DisplayName("StructureDefinitionExporter R5 should apply a Reference AssignmentRule and replace the Reference on a CodeableReference")
  void referenceAssignmentOnCodeableReferenceReference() {
    Profile profile = new Profile("Foo");
    profile.parent = "CarePlan";

    Instance instance = new Instance("Bar");
    instance.setId("bar-id");
    instance.instanceOf = "Condition";
    doc.instances.put(instance.name, instance);

    AssignmentRule rule = new AssignmentRule("addresses.reference");
    rule.value = new FshReference("Bar");
    profile.rules.add(rule);

    exporter.exportStructDef(profile);
    StructureDefinition sd = pkg.profiles.get(0);

    ElementDefinition addressesReference = sd.findElement("CarePlan.addresses.reference");

    assertJsonEqual("""
        { "reference": "Condition/bar-id" }""", at(addressesReference, "patternReference"));
  }

  @Test
  @DisplayName("StructureDefinitionExporter R5 should apply a Reference AssignmentRule directly to a CodeableReference element")
  void referenceAssignmentOnCodeableReference() {
    Profile profile = new Profile("Foo");
    profile.parent = "CarePlan";

    Instance instance = new Instance("Bar");
    instance.setId("bar-id");
    instance.instanceOf = "Condition";
    doc.instances.put(instance.name, instance);

    AssignmentRule rule = new AssignmentRule("addresses");
    rule.value = new FshReference("Bar");
    profile.rules.add(rule);

    exporter.exportStructDef(profile);
    StructureDefinition sd = pkg.profiles.get(0);

    ElementDefinition addresses = sd.findElement("CarePlan.addresses");
    assertJsonEqual("""
        { "reference": { "reference": "Condition/bar-id" } }""", at(addresses, "patternCodeableReference"));
  }

  @Test
  @DisplayName("StructureDefinitionExporter R5 should apply a FshCode AssignmentRule directly to a CodeableReference element")
  void codeAssignmentOnCodeableReference() {
    Profile profile = new Profile("Foo");
    profile.parent = "CarePlan";

    AssignmentRule rule = new AssignmentRule("addresses");
    rule.value = new FshCode("toast", "http://bar.com");
    profile.rules.add(rule);

    exporter.exportStructDef(profile);
    StructureDefinition sd = pkg.profiles.get(0);

    ElementDefinition addresses = sd.findElement("CarePlan.addresses");
    assertJsonEqual("""
        { "concept": { "coding": [{ "code": "toast", "system": "http://bar.com" }] } }""",
        at(addresses, "patternCodeableReference"));
  }

  @Test
  @DisplayName("StructureDefinitionExporter R5 should not apply a Reference AssignmentRule with invalid type constraints on a parent CodeableReference")
  void invalidReferenceAssignmentOnCodeableReference() {
    Profile profile = new Profile("Foo");
    profile.parent = "CarePlan";

    Instance instance = new Instance("Bar");
    instance.setId("bar-id");
    instance.instanceOf = "Patient";
    doc.instances.put(instance.name, instance);

    AssignmentRule rule = new AssignmentRule("addresses.reference");
    rule.value = new FshReference("Bar");
    profile.rules.add(rule);

    exporter.exportStructDef(profile);
    StructureDefinition sd = pkg.profiles.get(0);

    ElementDefinition addressesReference = sd.findElement("CarePlan.addresses.reference");

    assertNull(at(addressesReference, "patternReference"));
    assertEquals(1, loggerSpy.getAllMessages("error").size());
    assertMatches("The type \"Reference\\(Patient\\)\" does not match any of the allowed types\\D*", "s",
        loggerSpy.getLastMessage("error"));
  }

  @Nested
  @DisplayName("#AddElementRule")
  class AddElementRuleTests {
    @Test
    @DisplayName("StructureDefinitionExporter R5 #AddElementRule should not log an error when path does not have [x] for multiple CodeableReference types in AddElementRule")
    void multipleCodeableReferenceTypesWithoutChoicePath() {
      Logical logical = new Logical("MyTestModel");
      logical.setId("MyModel");

      AddElementRule addElementRule = new AddElementRule("prop1").<AddElementRule>withFile("GoodPath.fsh")
          .withLocation(3, 1, 8, 12);
      addElementRule.min = 0.0;
      addElementRule.max = "1";
      addElementRule.types = types(cr("Organization"), cr("Group"));
      addElementRule.short_ = "prop1 definition with multiple references";
      logical.rules.add(addElementRule);

      doc.logicals.put(logical.name, logical);

      exporter.exportStructDef(logical);

      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }
  }

  @Nested
  @DisplayName("#OnlyRule")
  class OnlyRuleTests {
    @Test
    @DisplayName("StructureDefinitionExporter R5 #OnlyRule should apply a correct OnlyRule on a CodeableReference")
    void onlyOnCodeableReference() {
      Profile profile = new Profile("Foo");
      profile.parent = "MedicationRequest";

      OnlyRule rule = new OnlyRule("reason");
      rule.types = types(cr("Condition"));
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("MedicationRequest");

      ElementDefinition baseSubject = baseStructDef.findElement("MedicationRequest.reason");
      ElementDefinition constrainedSubject = sd.findElement("MedicationRequest.reason");

      assertEquals(1, baseSubject.getType().size());
      assertJsEqual(new ElementDefinitionType("CodeableReference").withTargetProfiles(
          "http://hl7.org/fhir/StructureDefinition/Condition",
          "http://hl7.org/fhir/StructureDefinition/Observation"), baseSubject.getType().get(0));

      assertEquals(1, constrainedSubject.getType().size());
      assertJsEqual(new ElementDefinitionType("CodeableReference").withTargetProfiles(
          "http://hl7.org/fhir/StructureDefinition/Condition"), constrainedSubject.getType().get(0));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R5 #OnlyRule should apply a correct OnlyRule on a CodeableReference reference to Any")
    void onlyOnCodeableReferenceToAny() {
      Extension extension = new Extension("Foo");

      OnlyRule rule = new OnlyRule("value[x]");
      rule.types = types(cr("Observation"), cr("Condition"));
      extension.rules.add(rule);

      exporter.exportStructDef(extension);
      StructureDefinition sd = pkg.extensions.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Extension");

      ElementDefinition baseValueX = baseStructDef.findElement("Extension.value[x]");
      ElementDefinition constrainedValueX = sd.findElement("Extension.value[x]");

      assertEquals(54, baseValueX.getType().size());
      assertJsEqual(new ElementDefinitionType("CodeableReference"), baseValueX.getType().stream()
          .filter(t -> "CodeableReference".equals(t.getCode())).findFirst().orElse(null));

      assertEquals(1, constrainedValueX.getType().size());
      assertJsEqual(new ElementDefinitionType("CodeableReference").withTargetProfiles(
          "http://hl7.org/fhir/StructureDefinition/Observation",
          "http://hl7.org/fhir/StructureDefinition/Condition"), constrainedValueX.getType().get(0));
    }
  }
}
