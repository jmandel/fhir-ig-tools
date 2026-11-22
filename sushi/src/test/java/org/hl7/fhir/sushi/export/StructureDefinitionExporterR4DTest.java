package org.hl7.fhir.sushi.export;

import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertJsEqual;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertJsonEqual;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertMatches;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.at;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.sushi.fhirtypes.ElementDefinition;
import org.hl7.fhir.sushi.fhirtypes.StructureDefinition;
import org.hl7.fhir.sushi.fshtypes.Extension;
import org.hl7.fhir.sushi.fshtypes.FshCanonical;
import org.hl7.fhir.sushi.fshtypes.FshCode;
import org.hl7.fhir.sushi.fshtypes.FshCodeSystem;
import org.hl7.fhir.sushi.fshtypes.FshQuantity;
import org.hl7.fhir.sushi.fshtypes.FshReference;
import org.hl7.fhir.sushi.fshtypes.Instance;
import org.hl7.fhir.sushi.fshtypes.InstanceUsage;
import org.hl7.fhir.sushi.fshtypes.Logical;
import org.hl7.fhir.sushi.fshtypes.Profile;
import org.hl7.fhir.sushi.fshtypes.RuleSet;
import org.hl7.fhir.sushi.fshtypes.rules.AssignmentRule;
import org.hl7.fhir.sushi.fshtypes.rules.CardRule;
import org.hl7.fhir.sushi.fshtypes.rules.CaretValueRule;
import org.hl7.fhir.sushi.fshtypes.rules.ConceptRule;
import org.hl7.fhir.sushi.fshtypes.rules.ContainsRule;
import org.hl7.fhir.sushi.fshtypes.rules.ContainsRuleItem;
import org.hl7.fhir.sushi.fshtypes.rules.InsertRule;
import org.hl7.fhir.sushi.fshtypes.rules.OnlyRule;
import org.hl7.fhir.sushi.fshtypes.rules.OnlyRuleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// port of sushi/test/export/StructureDefinitionExporter.test.ts (SUSHI 3.21.1), describe blocks
// 'StructureDefinitionExporter R4' > '#AssignedValueRule' (lines 5652-7122)
@DisplayName("StructureDefinitionExporter R4")
class StructureDefinitionExporterR4DTest extends SdeTestBase {

  private static List<OnlyRuleType> types(String... names) {
    List<OnlyRuleType> list = new ArrayList<>();
    for (String n : names) list.add(new OnlyRuleType(n));
    return list;
  }

  @Nested
  @DisplayName("#AssignedValueRule")
  class AssignedValueRuleTests {
    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should apply a correct AssignmentRule")
    void correctAssignmentRule() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      AssignmentRule rule = new AssignmentRule("code");
      rule.value = new FshCode("foo", "http://foo.com");
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Observation");

      ElementDefinition baseCode = baseStructDef.findElement("Observation.code");
      ElementDefinition assignedCode = sd.findElement("Observation.code");

      assertNull(at(baseCode, "patternCodeableConcept"));
      assertJsonEqual("""
          { "coding": [{ "code": "foo", "system": "http://foo.com" }] }""",
          at(assignedCode, "patternCodeableConcept"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should apply a correct AssignmentRule for Quantity w/ value 0")
    void quantityValueZero() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      AssignmentRule rule = new AssignmentRule("valueQuantity");
      rule.value = new FshQuantity(0.0, new FshCode("mm", "http://unitsofmeasure.org", "mm"));
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);

      ElementDefinition assignedValue = sd.findElement("Observation.value[x]:valueQuantity");

      assertJsonEqual("""
          { "value": 0, "code": "mm", "system": "http://unitsofmeasure.org", "unit": "mm" }""",
          at(assignedValue, "patternQuantity"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should apply a Reference AssignmentRule and replace the Reference")
    void referenceReplaced() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      Instance instance = new Instance("Bar");
      instance.setId("bar-id");
      instance.instanceOf = "Patient";
      doc.instances.put(instance.name, instance);

      AssignmentRule rule = new AssignmentRule("subject");
      rule.value = new FshReference("Bar");
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);

      ElementDefinition assignedSubject = sd.findElement("Observation.subject");

      assertJsonEqual("""
          { "reference": "Patient/bar-id" }""", at(assignedSubject, "patternReference"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should apply a Reference AssignmentRule when the instance type is a logical type with the can-be-target characteristic")
    void referenceToCanBeTargetLogical() {
      // Logical: OkayTarget
      // Parent: Basic
      // Characteristics: #can-be-target
      Logical logical = new Logical("OkayTarget");
      logical.parent = "Basic";
      logical.characteristics = new ArrayList<>(List.of("can-be-target"));
      doc.logicals.put(logical.name, logical);

      // Instance: OkayTargetExample
      // InstanceOf: OkayTarget
      // * code = #okay-target
      Instance instance = new Instance("OkayTargetExample");
      instance.instanceOf = "OkayTarget";
      instance.usage = InstanceUsage.Example;
      AssignmentRule instanceCode = new AssignmentRule("code");
      instanceCode.value = new FshCode("okay-target");
      instance.rules.add(instanceCode);
      doc.instances.put(instance.name, instance);

      // Profile: MyObservation
      // Parent: Observation
      // * focus = Reference(OkayTargetExample)
      Profile profile = new Profile("MyObservation");
      profile.parent = "Observation";
      AssignmentRule focusAssignment = new AssignmentRule("focus");
      focusAssignment.value = new FshReference("OkayTargetExample");
      profile.rules.add(focusAssignment);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition assignedFocus = sd.findElement("Observation.focus");
      assertJsonEqual("""
          { "reference": "http://hl7.org/fhir/us/minimal/StructureDefinition/OkayTarget/OkayTargetExample" }""",
          at(assignedFocus, "patternReference"));

      assertEquals(0, loggerSpy.getAllMessages("error").size());
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should apply a Reference AssignmentRule and log a warning when the instance type is a logical type without the can-be-target characteristic")
    void referenceToNonTargetLogical() {
      // Logical: NotTarget
      // Parent: Basic
      // Characteristics: #has-size
      Logical logical = new Logical("NotTarget");
      logical.parent = "Basic";
      logical.characteristics = new ArrayList<>(List.of("has-size"));
      doc.logicals.put(logical.name, logical);

      // Instance: NotTargetExample
      // InstanceOf: NotTarget
      // * code = #not-target
      Instance instance = new Instance("NotTargetExample");
      instance.instanceOf = "NotTarget";
      instance.usage = InstanceUsage.Example;
      AssignmentRule instanceCode = new AssignmentRule("code");
      instanceCode.value = new FshCode("not-target");
      instance.rules.add(instanceCode);
      doc.instances.put(instance.name, instance);

      // Profile: MyObservation
      // Parent: Observation
      // * focus = Reference(NotTargetExample)
      Profile profile = new Profile("MyObservation");
      profile.parent = "Observation";
      AssignmentRule focusAssignment = new AssignmentRule("focus").<AssignmentRule>withFile("Observation.fsh")
          .withLocation(3, 1, 3, 46);
      focusAssignment.value = new FshReference("NotTargetExample");
      profile.rules.add(focusAssignment);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition assignedFocus = sd.findElement("Observation.focus");
      assertJsonEqual("""
          { "reference": "http://hl7.org/fhir/us/minimal/StructureDefinition/NotTarget/NotTargetExample" }""",
          at(assignedFocus, "patternReference"));

      assertEquals(0, loggerSpy.getAllMessages("error").size());
      assertEquals(1, loggerSpy.getAllMessages("warn").size());
      assertMatches(
          "Referenced type NotTarget for logical instance NotTargetExample does not specify that it can be the target of a reference\\.",
          "s", loggerSpy.getLastMessage("warn"));
      assertMatches("File: Observation\\.fsh.*Line: 3\\D*", "s", loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should not apply a Reference AssignmentRule with invalid type and log an error")
    void referenceInvalidType() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      Instance instance = new Instance("Bar");
      instance.setId("bar-id");
      instance.instanceOf = "Condition";
      doc.instances.put(instance.name, instance);

      AssignmentRule rule = new AssignmentRule("subject"); // subject cannot be a reference to condition
      rule.value = new FshReference("Bar");
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);

      ElementDefinition assignedSubject = sd.findElement("Observation.subject");

      assertNull(at(assignedSubject, "patternReference"));
      assertEquals(1, loggerSpy.getAllMessages("error").size());
      assertMatches("The type \"Reference\\(Condition\\)\" does not match any of the allowed types\\D*", "s",
          loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should apply AssignmentRules to different types of a choice element")
    void differentChoiceTypes() {
      // Profile: MyObservation
      // Parent: Observation
      // * valueString = "Hello"
      // * valueCodeableConcept = http://example.org#world
      Profile profile = new Profile("MyObservation");
      profile.parent = "Observation";
      AssignmentRule stringRule = new AssignmentRule("valueString");
      stringRule.value = "Hello";
      AssignmentRule codeableConceptRule = new AssignmentRule("valueCodeableConcept");
      codeableConceptRule.value = new FshCode("world", "http://example.org");
      profile.rules.add(stringRule);
      profile.rules.add(codeableConceptRule);
      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);

      ElementDefinition stringChoice = sd.findElement("Observation.value[x]:valueString");
      assertEquals("Hello", at(stringChoice, "patternString"));
      ElementDefinition codeableConceptChoice = sd.findElement("Observation.value[x]:valueCodeableConcept");
      assertJsonEqual("""
          { "coding": [{ "code": "world", "system": "http://example.org" }] }""",
          at(codeableConceptChoice, "patternCodeableConcept"));

      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should apply a Code AssignmentRule and replace the local complete code system name with its url")
    void localCompleteCodeSystemName() {
      Profile profile = new Profile("LightObservation");
      profile.parent = "Observation";
      AssignmentRule rule = new AssignmentRule("valueCodeableConcept");
      rule.value = new FshCode("bright", "Visible");
      profile.rules.add(rule);

      FshCodeSystem visibleSystem = new FshCodeSystem("Visible");
      visibleSystem.rules.add(new ConceptRule("bright"));
      doc.codeSystems.put(visibleSystem.name, visibleSystem);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition assignedElement = sd.findElement("Observation.value[x]:valueCodeableConcept");
      assertJsonEqual("""
          [{ "code": "bright", "system": "http://hl7.org/fhir/us/minimal/CodeSystem/Visible" }]""",
          at(assignedElement, "patternCodeableConcept.coding"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should apply a Code AssignmentRule that uses a name set by a rule and replace the local complete code system name with its url")
    void localCompleteCodeSystemNameSetByRule() {
      Profile profile = new Profile("LightObservation");
      profile.parent = "Observation";
      AssignmentRule rule = new AssignmentRule("valueCodeableConcept");
      rule.value = new FshCode("bright", "MyVisibleCodes");
      profile.rules.add(rule);

      FshCodeSystem visibleSystem = new FshCodeSystem("Visible");
      CaretValueRule visibleName = new CaretValueRule("");
      visibleName.pathArray = new ArrayList<>();
      visibleName.caretPath = "name";
      visibleName.value = "MyVisibleCodes";
      visibleSystem.rules.add(visibleName);
      visibleSystem.rules.add(new ConceptRule("bright"));
      doc.codeSystems.put(visibleSystem.name, visibleSystem);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition assignedElement = sd.findElement("Observation.value[x]:valueCodeableConcept");
      assertJsonEqual("""
          [{ "code": "bright", "system": "http://hl7.org/fhir/us/minimal/CodeSystem/Visible" }]""",
          at(assignedElement, "patternCodeableConcept.coding"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should apply a Code AssignmentRule and replace the local incomplete code system name with its url when the code is not in the system")
    void localIncompleteCodeSystemName() {
      Profile profile = new Profile("LightObservation");
      profile.parent = "Observation";
      AssignmentRule rule = new AssignmentRule("valueCodeableConcept");
      rule.value = new FshCode("bright", "Visible");
      profile.rules.add(rule);

      FshCodeSystem visibleSystem = new FshCodeSystem("Visible");
      visibleSystem.rules.add(new ConceptRule("disco"));
      CaretValueRule contentRule = new CaretValueRule("");
      contentRule.caretPath = "content";
      contentRule.value = new FshCode("fragment");
      visibleSystem.rules.add(contentRule);
      doc.codeSystems.put(visibleSystem.name, visibleSystem);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition assignedElement = sd.findElement("Observation.value[x]:valueCodeableConcept");
      assertJsonEqual("""
          [{ "code": "bright", "system": "http://hl7.org/fhir/us/minimal/CodeSystem/Visible" }]""",
          at(assignedElement, "patternCodeableConcept.coding"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should apply a Code AssignmentRule and replace the local complete instance of CodeSystem name with its url")
    void localCompleteCodeSystemInstance() {
      Profile profile = new Profile("LightObservation");
      profile.parent = "Observation";
      AssignmentRule rule = new AssignmentRule("valueCodeableConcept");
      rule.value = new FshCode("bright", "Visible");
      profile.rules.add(rule);

      Instance visibleSystem = new Instance("Visible");
      visibleSystem.instanceOf = "CodeSystem";
      visibleSystem.usage = InstanceUsage.Definition;
      AssignmentRule urlRule = new AssignmentRule("url");
      urlRule.value = "http://hl7.org/fhir/us/minimal/Instance/Visible";
      AssignmentRule contentRule = new AssignmentRule("content");
      contentRule.value = new FshCode("complete");
      AssignmentRule brightCode = new AssignmentRule("concept[0].code");
      brightCode.value = new FshCode("bright");
      visibleSystem.rules.addAll(List.of(urlRule, contentRule, brightCode));
      doc.instances.put(visibleSystem.name, visibleSystem);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition assignedElement = sd.findElement("Observation.value[x]:valueCodeableConcept");
      assertJsonEqual("""
          [{ "code": "bright", "system": "http://hl7.org/fhir/us/minimal/Instance/Visible" }]""",
          at(assignedElement, "patternCodeableConcept.coding"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should apply a Code AssignmentRule that uses a name set by a rule and replace the local complete instance of CodeSystem name with its url")
    void localCompleteCodeSystemInstanceNameSetByRule() {
      Profile profile = new Profile("LightObservation");
      profile.parent = "Observation";
      AssignmentRule rule = new AssignmentRule("valueCodeableConcept");
      rule.value = new FshCode("bright", "MyVisibleCodes");
      profile.rules.add(rule);

      Instance visibleSystem = new Instance("Visible");
      visibleSystem.instanceOf = "CodeSystem";
      visibleSystem.usage = InstanceUsage.Definition;
      AssignmentRule urlRule = new AssignmentRule("url");
      urlRule.value = "http://hl7.org/fhir/us/minimal/Instance/Visible";
      AssignmentRule nameRule = new AssignmentRule("name");
      nameRule.value = "MyVisibleCodes";
      AssignmentRule contentRule = new AssignmentRule("content");
      contentRule.value = new FshCode("complete");
      AssignmentRule brightCode = new AssignmentRule("concept[0].code");
      brightCode.value = new FshCode("bright");
      visibleSystem.rules.addAll(List.of(urlRule, nameRule, contentRule, brightCode));
      doc.instances.put(visibleSystem.name, visibleSystem);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition assignedElement = sd.findElement("Observation.value[x]:valueCodeableConcept");
      assertJsonEqual("""
          [{ "code": "bright", "system": "http://hl7.org/fhir/us/minimal/Instance/Visible" }]""",
          at(assignedElement, "patternCodeableConcept.coding"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should apply a Code AssignmentRule and replace the local incomplete instance of CodeSystem name with its url when the code is not in the system")
    void localIncompleteCodeSystemInstance() {
      Profile profile = new Profile("LightObservation");
      profile.parent = "Observation";
      AssignmentRule rule = new AssignmentRule("valueCodeableConcept");
      rule.value = new FshCode("disco", "Visible");
      profile.rules.add(rule);

      Instance visibleSystem = new Instance("Visible");
      visibleSystem.instanceOf = "CodeSystem";
      visibleSystem.usage = InstanceUsage.Definition;
      AssignmentRule urlRule = new AssignmentRule("url");
      urlRule.value = "http://hl7.org/fhir/us/minimal/Instance/Visible";
      AssignmentRule contentRule = new AssignmentRule("content");
      contentRule.value = new FshCode("example");
      AssignmentRule brightCode = new AssignmentRule("concept[0].code");
      brightCode.value = new FshCode("bright");
      visibleSystem.rules.addAll(List.of(urlRule, contentRule, brightCode));
      doc.instances.put(visibleSystem.name, visibleSystem);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition assignedElement = sd.findElement("Observation.value[x]:valueCodeableConcept");
      assertJsonEqual("""
          [{ "code": "disco", "system": "http://hl7.org/fhir/us/minimal/Instance/Visible" }]""",
          at(assignedElement, "patternCodeableConcept.coding"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should apply a Code AssignmentRule and replace the local complete code system name with its url when the code is added by a RuleSet")
    void localCompleteCodeSystemCodeFromRuleSet() {
      Profile profile = new Profile("LightObservation");
      profile.parent = "Observation";
      AssignmentRule rule = new AssignmentRule("valueCodeableConcept");
      rule.value = new FshCode("bright", "Visible");
      profile.rules.add(rule);

      RuleSet ruleSet = new RuleSet("ExtraLightRules");
      ruleSet.rules.add(new ConceptRule("bright"));
      doc.ruleSets.put(ruleSet.name, ruleSet);

      FshCodeSystem visibleSystem = new FshCodeSystem("Visible");
      visibleSystem.rules.add(new ConceptRule("dim"));
      InsertRule insertRule = new InsertRule("");
      insertRule.ruleSet = "ExtraLightRules";
      visibleSystem.rules.add(insertRule);
      doc.codeSystems.put(visibleSystem.name, visibleSystem);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition assignedElement = sd.findElement("Observation.value[x]:valueCodeableConcept");
      assertJsonEqual("""
          [{ "code": "bright", "system": "http://hl7.org/fhir/us/minimal/CodeSystem/Visible" }]""",
          at(assignedElement, "patternCodeableConcept.coding"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should apply a Code AssignmentRule and replace the local complete instance of CodeSystem name with its url when the code is added by a RuleSet")
    void localCompleteCodeSystemInstanceCodeFromRuleSet() {
      Profile profile = new Profile("LightObservation");
      profile.parent = "Observation";
      AssignmentRule rule = new AssignmentRule("valueCodeableConcept");
      rule.value = new FshCode("bright", "Visible");
      profile.rules.add(rule);

      RuleSet ruleSet = new RuleSet("ExtraLightRules");
      AssignmentRule brightCode = new AssignmentRule("concept[+].code");
      brightCode.value = new FshCode("bright");
      ruleSet.rules.add(brightCode);
      doc.ruleSets.put(ruleSet.name, ruleSet);

      Instance visibleSystem = new Instance("Visible");
      visibleSystem.instanceOf = "CodeSystem";
      visibleSystem.usage = InstanceUsage.Definition;
      AssignmentRule urlRule = new AssignmentRule("url");
      urlRule.value = "http://hl7.org/fhir/us/minimal/Instance/Visible";
      AssignmentRule contentRule = new AssignmentRule("content");
      contentRule.value = new FshCode("complete");
      AssignmentRule dimCode = new AssignmentRule("concept[0].code");
      dimCode.value = new FshCode("dim");
      InsertRule insertRule = new InsertRule("");
      insertRule.ruleSet = "ExtraLightRules";
      visibleSystem.rules.addAll(List.of(urlRule, contentRule, dimCode, insertRule));
      doc.instances.put(visibleSystem.name, visibleSystem);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition assignedElement = sd.findElement("Observation.value[x]:valueCodeableConcept");
      assertJsonEqual("""
          [{ "code": "bright", "system": "http://hl7.org/fhir/us/minimal/Instance/Visible" }]""",
          at(assignedElement, "patternCodeableConcept.coding"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should log an error when applying a Code AssignmentRule with a local complete code system name when the code does not exist")
    void localCompleteCodeSystemNameMissingCode() {
      Profile profile = new Profile("LightObservation");
      profile.parent = "Observation";
      AssignmentRule rule = new AssignmentRule("valueCodeableConcept").<AssignmentRule>withFile("Light.fsh")
          .withLocation(8, 1, 8, 25);
      rule.value = new FshCode("disco", "Visible");
      profile.rules.add(rule);

      FshCodeSystem visibleSystem = new FshCodeSystem("Visible");
      visibleSystem.rules.add(new ConceptRule("bright"));
      doc.codeSystems.put(visibleSystem.name, visibleSystem);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition assignedElement = sd.findElement("Observation.value[x]:valueCodeableConcept");
      assertJsonEqual("""
          [{ "code": "disco", "system": "http://hl7.org/fhir/us/minimal/CodeSystem/Visible" }]""",
          at(assignedElement, "patternCodeableConcept.coding"));
      assertEquals(1, loggerSpy.getAllMessages("error").size());
      assertMatches("Code \"disco\" is not defined for system Visible.*File: Light\\.fsh.*Line: 8\\D*", "s",
          loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should log an error when applying a Code AssignmentRule with a local complete instance of CodeSystem name when the code does not exist")
    void localCompleteCodeSystemInstanceMissingCode() {
      Profile profile = new Profile("LightObservation");
      profile.parent = "Observation";
      AssignmentRule rule = new AssignmentRule("valueCodeableConcept").<AssignmentRule>withFile("Light.fsh")
          .withLocation(12, 0, 12, 22);
      rule.value = new FshCode("disco", "Visible");
      profile.rules.add(rule);

      Instance visibleSystem = new Instance("Visible");
      visibleSystem.instanceOf = "CodeSystem";
      visibleSystem.usage = InstanceUsage.Definition;
      AssignmentRule urlRule = new AssignmentRule("url");
      urlRule.value = "http://hl7.org/fhir/us/minimal/Instance/Visible";
      AssignmentRule contentRule = new AssignmentRule("content");
      contentRule.value = new FshCode("complete");
      AssignmentRule brightCode = new AssignmentRule("concept[0].code");
      brightCode.value = new FshCode("bright");
      visibleSystem.rules.addAll(List.of(urlRule, contentRule, brightCode));
      doc.instances.put(visibleSystem.name, visibleSystem);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition assignedElement = sd.findElement("Observation.value[x]:valueCodeableConcept");
      assertJsonEqual("""
          [{ "code": "disco", "system": "http://hl7.org/fhir/us/minimal/Instance/Visible" }]""",
          at(assignedElement, "patternCodeableConcept.coding"));
      assertEquals(1, loggerSpy.getAllMessages("error").size());
      assertMatches("Code \"disco\" is not defined for system Visible.*File: Light\\.fsh.*Line: 12\\D*", "s",
          loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should log an error when applying a Code AssignmentRule with a local complete code system url when the code does not exist")
    void localCompleteCodeSystemUrlMissingCode() {
      Profile profile = new Profile("LightObservation");
      profile.parent = "Observation";
      AssignmentRule rule = new AssignmentRule("valueCodeableConcept").<AssignmentRule>withFile("Light.fsh")
          .withLocation(8, 1, 8, 25);
      rule.value = new FshCode("disco", "http://hl7.org/fhir/us/minimal/CodeSystem/Visible");
      profile.rules.add(rule);

      FshCodeSystem visibleSystem = new FshCodeSystem("Visible");
      visibleSystem.rules.add(new ConceptRule("bright"));
      doc.codeSystems.put(visibleSystem.name, visibleSystem);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition assignedElement = sd.findElement("Observation.value[x]:valueCodeableConcept");
      assertJsonEqual("""
          [{ "code": "disco", "system": "http://hl7.org/fhir/us/minimal/CodeSystem/Visible" }]""",
          at(assignedElement, "patternCodeableConcept.coding"));
      assertEquals(1, loggerSpy.getAllMessages("error").size());
      assertMatches("Code \"disco\" is not defined for system Visible.*File: Light\\.fsh.*Line: 8\\D*", "s",
          loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should apply a Code AssignmentRule and replace the id of code system (from the core version fhir or dependency) with its url")
    void coreCodeSystemId() {
      // allergyintolerance-clinical is the id of a CodeSystem in the R4 definitions
      Profile profile = new Profile("LightObservation");
      profile.parent = "Observation";
      AssignmentRule rule = new AssignmentRule("category");
      rule.value = new FshCode("test-code", "allergyintolerance-clinical"); // id
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition assignedElement = sd.findElement("Observation.category");
      assertJsonEqual("""
          [{ "code": "test-code", "system": "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical" }]""",
          at(assignedElement, "patternCodeableConcept.coding"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should apply a Code AssignmentRule and replace the name of code system (from the core version fhir or dependency) with its url")
    void coreCodeSystemName() {
      // AllergyIntoleranceClinicalStatusCodes is the name of a CodeSystem in the R4 definitions
      Profile profile = new Profile("LightObservation");
      profile.parent = "Observation";
      AssignmentRule rule = new AssignmentRule("category");
      rule.value = new FshCode("test-code", "AllergyIntoleranceClinicalStatusCodes"); // name
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition assignedElement = sd.findElement("Observation.category");
      assertJsonEqual("""
          [{ "code": "test-code", "system": "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical" }]""",
          at(assignedElement, "patternCodeableConcept.coding"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should apply a Code AssignmentRule and keep the url of code system (from the core version fhir or dependency) as the system url")
    void coreCodeSystemUrl() {
      // http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical is the url of a CodeSystem in the R4 definitions
      Profile profile = new Profile("LightObservation");
      profile.parent = "Observation";
      AssignmentRule rule = new AssignmentRule("category");
      rule.value = new FshCode("test-code", "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical"); // url
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition assignedElement = sd.findElement("Observation.category");
      assertJsonEqual("""
          [{ "code": "test-code", "system": "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical" }]""",
          at(assignedElement, "patternCodeableConcept.coding"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should apply an AssignmentRule with a valid Canonical entity defined in FSH")
    void canonicalOfFshEntity() {
      Profile profile = new Profile("MyObservation");
      profile.parent = "Observation";
      AssignmentRule rule = new AssignmentRule("code.coding.system");
      rule.value = new FshCanonical("VeryRealCodeSystem");
      profile.rules.add(rule);

      FshCodeSystem realCodeSystem = new FshCodeSystem("VeryRealCodeSystem");
      doc.codeSystems.put(realCodeSystem.name, realCodeSystem);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition assignedSystem = sd.findElement("Observation.code.coding.system");
      assertEquals("http://hl7.org/fhir/us/minimal/CodeSystem/VeryRealCodeSystem", at(assignedSystem, "patternUri"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should apply an Assignment rule with Canonical of a Questionnaire instance")
    void canonicalOfQuestionnaireInstance() {
      Instance questionnaireInstance = new Instance("MyQuestionnaire");
      questionnaireInstance.usage = InstanceUsage.Definition;
      AssignmentRule urlRule = new AssignmentRule("url");
      urlRule.value = "http://my.awesome.questions.org/Questionnaire/MyQuestionnaire";
      questionnaireInstance.rules.add(urlRule);
      doc.instances.put(questionnaireInstance.name, questionnaireInstance);

      Profile profile = new Profile("MyQuestionnaireResponse");
      profile.parent = "QuestionnaireResponse";
      AssignmentRule assignedValueRule = new AssignmentRule("questionnaire");
      assignedValueRule.value = new FshCanonical("MyQuestionnaire");
      profile.rules.add(assignedValueRule);
      doc.profiles.put(profile.name, profile);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition assignedQ = sd.findElement("QuestionnaireResponse.questionnaire");
      assertEquals("http://my.awesome.questions.org/Questionnaire/MyQuestionnaire", at(assignedQ, "patternCanonical"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should apply an Assignment rule with Canonical of an inline instance")
    void canonicalOfInlineInstance() {
      Profile profile = new Profile("MyObservation");
      profile.parent = "Observation";
      AssignmentRule rule = new AssignmentRule("code.coding.system");
      rule.value = new FshCanonical("MyCodeSystem");
      profile.rules.add(rule);

      Instance inlineInstance = new Instance("MyCodeSystem");
      inlineInstance.usage = InstanceUsage.Inline;
      doc.instances.put(inlineInstance.name, inlineInstance);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition assignedSystem = sd.findElement("Observation.code.coding.system");
      assertEquals("#MyCodeSystem", at(assignedSystem, "patternUri"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should apply an AssignmentRule with Canonical of a FHIR entity")
    void canonicalOfFhirEntity() {
      Profile profile = new Profile("MyObservation");
      profile.parent = "Observation";
      AssignmentRule rule = new AssignmentRule("code.coding.system");
      rule.value = new FshCanonical("MedicationRequest");
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition assignedSystem = sd.findElement("Observation.code.coding.system");
      assertEquals("http://hl7.org/fhir/StructureDefinition/MedicationRequest", at(assignedSystem, "patternUri"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should apply an AssignmentRule with Canonical of a FHIR entity with a given version")
    void canonicalOfFhirEntityWithVersion() {
      Profile profile = new Profile("MyObservation");
      profile.parent = "Observation";
      AssignmentRule rule = new AssignmentRule("code.coding.system");
      FshCanonical canonical = new FshCanonical("MedicationRequest");
      canonical.version = "3.2.1";
      rule.value = canonical;
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition assignedSystem = sd.findElement("Observation.code.coding.system");
      // Use the specified version instead of the version on MedicationRequest
      assertEquals("http://hl7.org/fhir/StructureDefinition/MedicationRequest|3.2.1", at(assignedSystem, "patternUri"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should not apply an AssignmentRule with an invalid Canonical entity and log an error")
    void invalidCanonical() {
      Profile profile = new Profile("MyObservation");
      profile.parent = "Observation";
      AssignmentRule rule = new AssignmentRule("code.coding.system");
      rule.value = new FshCanonical("FakeCodeSystem");
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition assignedSystem = sd.findElement("Observation.code.coding.system");
      assertNull(at(assignedSystem, "patternUri"));
      assertEquals(1, loggerSpy.getAllMessages("error").size());
      assertMatches("Cannot use canonical URL of FakeCodeSystem because it does not exist.\\D*", "s",
          loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should apply an instance AssignmentRule and replace the instance")
    void instanceAssignment() {
      Profile profile = new Profile("USPatient");
      profile.parent = "Patient";
      AssignmentRule rule = new AssignmentRule("address");
      rule.value = "USPostalAddress";
      rule.isInstance = true;
      profile.rules.add(rule); // * address = USPostalAddress
      doc.profiles.put(profile.name, profile);

      Instance instance = new Instance("USPostalAddress");
      instance.instanceOf = "Address";
      instance.usage = InstanceUsage.Inline;
      AssignmentRule assignCountry = new AssignmentRule("country");
      assignCountry.value = "US";
      instance.rules.add(assignCountry); // * country = "US"
      doc.instances.put(instance.name, instance);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition assignedAddress = sd.findElement("Patient.address");
      assertJsonEqual("""
          { "country": "US" }""", at(assignedAddress, "patternAddress"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should log a warning and apply an instance AssignmentRule and replace the instance when the instance is an example")
    void exampleInstanceAssignment() {
      // Instance: example-obs
      // InstanceOf: Observation
      // Usage: #example
      // * status = #draft
      // * code = #123
      Instance instance = new Instance("example-obs");
      instance.instanceOf = "Observation";
      instance.usage = InstanceUsage.Example;
      AssignmentRule instanceStatus = new AssignmentRule("status");
      instanceStatus.value = new FshCode("draft");
      AssignmentRule instanceCode = new AssignmentRule("code");
      instanceCode.value = new FshCode("123");
      instance.rules.add(instanceStatus);
      instance.rules.add(instanceCode);
      doc.instances.put(instance.name, instance);
      // Profile: MyObs
      // Parent: Observation
      // * contained = example-obs
      Profile profile = new Profile("MyObs");
      profile.parent = "Observation";
      AssignmentRule profileContained = new AssignmentRule("contained").<AssignmentRule>withFile("MyObs.fsh")
          .withLocation(5, 3, 5, 18);
      profileContained.value = "example-obs";
      profileContained.isInstance = true;
      profile.rules.add(profileContained);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition assignedResource = sd.findElement("Observation.contained");
      assertJsonEqual("""
          {
            "resourceType": "Observation",
            "id": "example-obs",
            "status": "draft",
            "code": { "coding": [{ "code": "123" }] }
          }""", at(assignedResource, "patternObservation"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
      assertEquals(1, loggerSpy.getAllMessages("warn").size());
      assertMatches("Contained instance \"example-obs\" is an example", "s", loggerSpy.getLastMessage("warn"));
      assertMatches("File: MyObs\\.fsh.*Line: 5\\D*", "s", loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should apply an instance AssignmentRule when the instance has a numeric id")
    void instanceWithNumericId() {
      // Profile: USPatient
      // Parent: Patient
      // address = 00050
      Profile profile = new Profile("USPatient");
      profile.parent = "Patient";
      AssignmentRule rule = new AssignmentRule("address");
      rule.value = 50.0;
      rule.rawValue = "00050";
      profile.rules.add(rule);
      doc.profiles.put(profile.name, profile);
      // Instance: 00050
      // InstanceOf: Address
      // Usage: #inline
      // country = "US"
      Instance instance = new Instance("00050");
      instance.instanceOf = "Address";
      instance.usage = InstanceUsage.Inline;
      AssignmentRule assignCountry = new AssignmentRule("country");
      assignCountry.value = "US";
      instance.rules.add(assignCountry);
      doc.instances.put(instance.name, instance);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition assignedAddress = sd.findElement("Patient.address");
      assertJsonEqual("""
          { "country": "US" }""", at(assignedAddress, "patternAddress"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should log a warning and apply an instance AssignmentRule when the instance has a numeric id")
    void exampleInstanceWithNumericId() {
      // Instance: 765
      // InstanceOf: Observation
      // Usage: #example
      // * status = #draft
      // * code = #123
      Instance instance = new Instance("765");
      instance.instanceOf = "Observation";
      instance.usage = InstanceUsage.Example;
      AssignmentRule instanceStatus = new AssignmentRule("status");
      instanceStatus.value = new FshCode("draft");
      AssignmentRule instanceCode = new AssignmentRule("code");
      instanceCode.value = new FshCode("123");
      instance.rules.add(instanceStatus);
      instance.rules.add(instanceCode);
      doc.instances.put(instance.name, instance);
      // Profile: MyObs
      // Parent: Observation
      // * contained = 765
      Profile profile = new Profile("MyObs");
      profile.parent = "Observation";
      AssignmentRule profileContained = new AssignmentRule("contained").<AssignmentRule>withFile("MyObs.fsh")
          .withLocation(5, 3, 5, 18);
      profileContained.value = BigInteger.valueOf(765);
      profileContained.rawValue = "765";
      profile.rules.add(profileContained);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition assignedResource = sd.findElement("Observation.contained");
      assertJsonEqual("""
          {
            "resourceType": "Observation",
            "id": "765",
            "status": "draft",
            "code": { "coding": [{ "code": "123" }] }
          }""", at(assignedResource, "patternObservation"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
      assertEquals(1, loggerSpy.getAllMessages("warn").size());
      assertMatches("Contained instance \"765\" is an example", "s", loggerSpy.getLastMessage("warn"));
      assertMatches("File: MyObs\\.fsh.*Line: 5\\D*", "s", loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should apply an instance AssignmentRule when the instance has an id that resembles a boolean")
    void instanceWithBooleanLikeId() {
      // Profile: USPatient
      // Parent: Patient
      // address = true
      Profile profile = new Profile("USPatient");
      profile.parent = "Patient";
      AssignmentRule rule = new AssignmentRule("address");
      rule.value = true;
      rule.rawValue = "true";
      profile.rules.add(rule);
      doc.profiles.put(profile.name, profile);
      // Instance: true
      // InstanceOf: Address
      // Usage: #inline
      // id = "true"
      // country = "US"
      Instance instance = new Instance("TrueAddress");
      instance.instanceOf = "Address";
      instance.usage = InstanceUsage.Inline;
      AssignmentRule assignId = new AssignmentRule("id");
      assignId.value = "true";
      AssignmentRule assignCountry = new AssignmentRule("country");
      assignCountry.value = "US";
      instance.rules.add(assignId);
      instance.rules.add(assignCountry);
      doc.instances.put(instance.name, instance);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition assignedAddress = sd.findElement("Patient.address");
      assertJsonEqual("""
          { "id": "true", "country": "US" }""", at(assignedAddress, "patternAddress"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should not apply an instance AssignmentRule when the instance cannot be found")
    void instanceNotFound() {
      Profile profile = new Profile("USPatient");
      profile.parent = "Patient";
      AssignmentRule rule = new AssignmentRule("address");
      rule.value = "FakeInstance";
      rule.isInstance = true;
      profile.rules.add(rule); // * address = FakeInstance
      doc.profiles.put(profile.name, profile);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition assignedAddress = sd.findElement("Patient.address");
      assertNull(at(assignedAddress, "patternAddress"));
      assertEquals(1, loggerSpy.getAllMessages("error").size());
      assertMatches("Cannot assign Instance at path address to element of type Address.*FakeInstance\\D*", "s",
          loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should use the url specified in a CaretValueRule when referencing a named code system")
    void caretUrlOfNamedCodeSystem() {
      Profile profile = new Profile("LightObservation");
      profile.parent = "Observation";
      AssignmentRule rule = new AssignmentRule("valueCodeableConcept");
      rule.value = new FshCode("bright", "Visible");
      profile.rules.add(rule);

      FshCodeSystem visibleSystem = new FshCodeSystem("Visible");
      CaretValueRule caretValueRule = new CaretValueRule("");
      caretValueRule.caretPath = "url";
      caretValueRule.value = "http://special-domain.com/CodeSystem/Visible";
      visibleSystem.rules.add(caretValueRule);
      doc.codeSystems.put(visibleSystem.name, visibleSystem);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition assignedElement = sd.findElement("Observation.value[x]:valueCodeableConcept");
      assertJsonEqual("""
          [{ "code": "bright", "system": "http://special-domain.com/CodeSystem/Visible" }]""",
          at(assignedElement, "patternCodeableConcept.coding"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should apply an AssignmentRule on the child of a choice element with constrained choices that share a type")
    void choiceChildSharedType() {
      // Quantity is the first ancestor of Duration and Age

      // * value[x] only Duration or Age
      // * value[x].comparator = #>=

      Extension extension = new Extension("QuantifiedExtension");

      OnlyRule onlyRule = new OnlyRule("value[x]");
      onlyRule.types = types("Duration", "Age");
      extension.rules.add(onlyRule);

      AssignmentRule assignedValueRule = new AssignmentRule("value[x].comparator");
      assignedValueRule.value = new FshCode(">=");
      extension.rules.add(assignedValueRule);

      exporter.exportStructDef(extension);
      StructureDefinition sd = pkg.extensions.get(0);
      ElementDefinition assignedElement = sd.findElement("Extension.value[x].comparator");
      assertEquals(">=", at(assignedElement, "patternCode"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should apply an AssignmentRule on the child of a choice element with constrained choices that share a profile")
    void choiceChildSharedProfile() {
      // Profile: CustomizedTiming
      // Parent: Timing
      // * extension contains customField 0..1
      Profile customizedTiming = new Profile("CustomizedTiming");
      customizedTiming.parent = "Timing";
      ContainsRule containsRule = new ContainsRule("extension");
      containsRule.items = new ArrayList<>(List.of(new ContainsRuleItem("customField")));
      CardRule cardRule = new CardRule("extension[customField]");
      cardRule.min = 0.0;
      cardRule.max = "1";
      customizedTiming.rules.add(containsRule);
      customizedTiming.rules.add(cardRule);
      doc.profiles.put(customizedTiming.name, customizedTiming);

      // Profile: VerifiedTiming
      // Parent: CustomizedTiming
      Profile verifiedTiming = new Profile("VerifiedTiming");
      verifiedTiming.parent = customizedTiming.name;
      doc.profiles.put(verifiedTiming.name, verifiedTiming);

      // Profile: ConsensusTiming
      // Parent: CustomizedTiming
      Profile consensusTiming = new Profile("ConsensusTiming");
      consensusTiming.parent = customizedTiming.name;
      doc.profiles.put(consensusTiming.name, consensusTiming);

      // Profile: SpecialTimingObservation
      // Parent: Observation
      // * effective[x] only VerifiedTiming or ConsensusTiming
      // * effective[x].extension[customField].id = my-special-id
      Profile specialTimingObservation = new Profile("SpecialTimingObservation");
      specialTimingObservation.parent = "Observation";
      OnlyRule onlyRule = new OnlyRule("effective[x]");
      onlyRule.types = types("VerifiedTiming", "ConsensusTiming");
      AssignmentRule assignedValueRule = new AssignmentRule("effective[x].extension[customField].id");
      assignedValueRule.value = "my-special-id";
      specialTimingObservation.rules.add(onlyRule);
      specialTimingObservation.rules.add(assignedValueRule);
      doc.profiles.put(specialTimingObservation.name, specialTimingObservation);

      exporter.exportStructDef(specialTimingObservation);
      StructureDefinition specialTimingObservationSd = pkg.profiles.stream()
          .filter(resource -> "SpecialTimingObservation".equals(resource.getId()))
          .findFirst()
          .orElse(null);
      assertNotNull(specialTimingObservationSd);
      ElementDefinition assignedId = specialTimingObservationSd.findElement(
          "Observation.effective[x].extension:customField.id");
      assertEquals("my-special-id", at(assignedId, "patternString"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should not apply an incorrect AssignmentRule")
    void incorrectAssignmentRule() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      AssignmentRule rule = new AssignmentRule("code").<AssignmentRule>withFile("Assigned.fsh")
          .withLocation(4, 18, 4, 28);
      rule.value = true; // Incorrect boolean
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Observation");

      ElementDefinition baseCode = baseStructDef.findElement("Observation.code");
      ElementDefinition assignedCode = sd.findElement("Observation.code");

      assertNull(at(baseCode, "patternCodeableConcept"));
      assertNull(at(assignedCode, "patternCodeableConcept")); // Code remains unset
      assertMatches("File: Assigned\\.fsh.*Line: 4\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should not apply an AssignmentRule when the value is refers to an Instance that is not found")
    void referredInstanceNotFound() {
      // Profile: USPatient
      // Parent: Patient
      // telecom = MyTelecom
      Profile profile = new Profile("USPatient");
      profile.parent = "Patient";
      AssignmentRule rule = new AssignmentRule("telecom").<AssignmentRule>withFile("Assigned.fsh")
          .withLocation(5, 6, 5, 28);
      rule.value = "MyTelecom";
      rule.isInstance = true;
      profile.rules.add(rule);
      doc.profiles.put(profile.name, profile);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition assignedTelecom = sd.findElement("Patient.telecom");
      assertNull(at(assignedTelecom, "patternTelecom"));
      assertMatches(
          "Cannot assign Instance at path telecom to element of type ContactPoint. Definition not found for Instance: MyTelecom.*File: Assigned\\.fsh.*Line: 5\\D*",
          "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should not apply an AssignmentRule when the value is numeric and refers to an Instance, but both types are wrong")
    void numericValueBothTypesWrong() {
      // Profile: USPatient
      // Parent: Patient
      // telecom = 00050
      Profile profile = new Profile("USPatient");
      profile.parent = "Patient";
      AssignmentRule rule = new AssignmentRule("telecom").<AssignmentRule>withFile("Assigned.fsh")
          .withLocation(5, 6, 5, 21);
      rule.value = 50.0;
      rule.rawValue = "00050";
      profile.rules.add(rule);
      doc.profiles.put(profile.name, profile);
      // Instance: 00050
      // InstanceOf: Address
      // Usage: #inline
      // country = "US"
      Instance instance = new Instance("00050");
      instance.instanceOf = "Address";
      instance.usage = InstanceUsage.Inline;
      AssignmentRule assignCountry = new AssignmentRule("country");
      assignCountry.value = "US";
      instance.rules.add(assignCountry);
      doc.instances.put(instance.name, instance);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition assignedTelecom = sd.findElement("Patient.telecom");
      assertNull(at(assignedTelecom, "patternTelecom"));
      assertMatches(
          "Cannot assign number value: 50\\. Value does not match element type: ContactPoint.*File: Assigned\\.fsh.*Line: 5\\D*",
          "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should not apply an AssignmentRule when the value is boolean and refers to an Instance, but both types are wrong")
    void booleanValueBothTypesWrong() {
      // Profile: USPatient
      // Parent: Patient
      // birthDate = true
      Profile profile = new Profile("USPatient");
      profile.parent = "Patient";
      AssignmentRule rule = new AssignmentRule("birthDate").<AssignmentRule>withFile("Assigned.fsh")
          .withLocation(5, 6, 5, 21);
      rule.value = true;
      rule.rawValue = "true";
      profile.rules.add(rule);
      doc.profiles.put(profile.name, profile);
      // Instance: true
      // InstanceOf: Address
      // Usage: #inline
      // country = "US"
      Instance instance = new Instance("true");
      instance.instanceOf = "Address";
      instance.usage = InstanceUsage.Inline;
      AssignmentRule assignCountry = new AssignmentRule("country");
      assignCountry.value = "US";
      instance.rules.add(assignCountry);
      doc.instances.put(instance.name, instance);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition assignedTelecom = sd.findElement("Patient.birthDate");
      assertNull(at(assignedTelecom, "patternDate"));
      assertMatches(
          "Cannot assign boolean value: true\\. Value does not match element type: date.*File: Assigned\\.fsh.*Line: 5\\D*",
          "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should not apply an AssignmentRule when the value is numeric and refers to an Instance, but it conflicts with an existing value")
    void numericValueConflictsWithExisting() {
      // Profile: USPatient
      // Parent: Patient
      // address.country = "US"
      // address = 00050
      Profile profile = new Profile("USPatient");
      profile.parent = "Patient";
      AssignmentRule countryRule = new AssignmentRule("address.country");
      countryRule.value = "US";
      AssignmentRule addressRule = new AssignmentRule("address").<AssignmentRule>withFile("Assigned.fsh")
          .withLocation(6, 3, 6, 18);
      addressRule.value = 50.0;
      addressRule.rawValue = "00050";
      profile.rules.add(countryRule);
      profile.rules.add(addressRule);
      doc.profiles.put(profile.name, profile);
      // Instance: 00050
      // InstanceOf: Address
      // Usage: #inline
      // country = "France"
      Instance instance = new Instance("00050");
      instance.instanceOf = "Address";
      instance.usage = InstanceUsage.Inline;
      AssignmentRule assignCountry = new AssignmentRule("country");
      assignCountry.value = "France";
      instance.rules.add(assignCountry);
      doc.instances.put(instance.name, instance);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition assignedAddress = sd.findElement("Patient.address");
      assertNull(at(assignedAddress, "patternAddress"));
      ElementDefinition assignedCountry = sd.findElement("Patient.address.country");
      assertEquals("US", at(assignedCountry, "patternString"));
      assertMatches(
          "Cannot assign France to this element; a different string is already assigned: \"US\".*File: Assigned\\.fsh.*Line: 6\\D*",
          "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should not apply a AssignmentRule to a parent element when it would conflict with a child element")
    void parentConflictsWithChild() {
      // Profile: MyObs
      // Parent: Observation
      // * valueQuantity.value = 20
      // * valueQuantity = 10 'mm'
      Profile profile = new Profile("MyObs");
      profile.parent = "Observation";
      AssignmentRule valueRule = new AssignmentRule("valueQuantity.value").<AssignmentRule>withFile("Assigned.fsh")
          .withLocation(3, 8, 3, 29);
      valueRule.value = 20.0;
      AssignmentRule quantityRule = new AssignmentRule("valueQuantity").<AssignmentRule>withFile("Assigned.fsh")
          .withLocation(4, 8, 4, 27);
      quantityRule.value = new FshQuantity(10.0, new FshCode("mm", "http://unitsofmeasure.org"));
      profile.rules.add(valueRule);
      profile.rules.add(quantityRule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition childElement = sd.findElement("Observation.value[x]:valueQuantity.value");
      ElementDefinition parentElement = sd.findElement("Observation.value[x]:valueQuantity");

      assertJsEqual(20, at(childElement, "patternDecimal"));
      assertNull(at(parentElement, "patternQuantity"));
      assertMatches("Cannot assign 10 to this element.*File: Assigned\\.fsh.*Line: 4\\D*", "s",
          loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should not apply a AssignmentRule to a complex typed element when it would conflict with a child element present in an array in the type")
    void complexConflictsWithArrayChild() {
      // Profile: MyObs
      // Parent: Observation
      // * valueCodeableConcept.coding.code = #pancake
      // * valueCodeableConcept = #waffle // this rule should not be applied
      Profile profile = new Profile("MyObs");
      profile.parent = "Observation";
      AssignmentRule innerRule = new AssignmentRule("valueCodeableConcept.coding.code");
      innerRule.value = new FshCode("pancake");
      AssignmentRule outerRule = new AssignmentRule("valueCodeableConcept").<AssignmentRule>withFile("Assigned.fsh")
          .withLocation(4, 9, 4, 33);
      outerRule.value = new FshCode("waffle");
      profile.rules.add(innerRule);
      profile.rules.add(outerRule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition innerElement = sd.findElement("Observation.value[x]:valueCodeableConcept.coding.code");
      ElementDefinition outerElement = sd.findElement("Observation.value[x]:valueCodeableConcept");
      assertEquals("pancake", at(innerElement, "patternCode"));
      assertNull(at(outerElement, "patternCodeableConcept"));
      assertMatches("Cannot assign waffle to this element.*File: Assigned\\.fsh.*Line: 4\\D*", "s",
          loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should not apply a AssignmentRule to a slice when it would conflict with a child of the list element")
    void sliceConflictsWithListChild() {
      // Instance: CustomPostalAddress
      // InstanceOf: Address
      // Usage: #inline
      // * period.start = "2020-04-01"
      Instance customPostalAddress = new Instance("CustomPostalAddress");
      customPostalAddress.instanceOf = "Address";
      customPostalAddress.usage = InstanceUsage.Inline;
      AssignmentRule customStart = new AssignmentRule("period.start");
      customStart.value = "2020-04-01";

      customPostalAddress.rules.add(customStart);
      doc.instances.put(customPostalAddress.name, customPostalAddress);

      // Profile: MovingPatient
      // Parent: Patient
      // * address.period.start = "1998-07-04"
      // * address ^slicing.discriminator[0].type = #pattern
      // * address ^slicing.discriminator[0].path = "$this"
      // * address ^slicing.rules = #open
      // * address contains RecentAddress 1..1
      // * address[RecentAddress] = CustomPostalAddress // this rule should not be applied
      Profile movingPatient = new Profile("MovingPatient");
      movingPatient.parent = "Patient";
      AssignmentRule movingStart = new AssignmentRule("address.period.start");
      movingStart.value = "1998-07-04";
      CaretValueRule slicingType = new CaretValueRule("address");
      slicingType.caretPath = "slicing.discriminator[0].type";
      slicingType.value = new FshCode("pattern");
      CaretValueRule slicingPath = new CaretValueRule("address");
      slicingPath.caretPath = "slicing.discriminator[0].path";
      slicingPath.value = "code";
      CaretValueRule slicingRules = new CaretValueRule("address");
      slicingRules.caretPath = "slicing.rules";
      slicingRules.value = new FshCode("open");
      ContainsRule recentAddress = new ContainsRule("address");
      recentAddress.items.add(new ContainsRuleItem("RecentAddress"));
      CardRule recentCard = new CardRule("address[RecentAddress]");
      recentCard.min = 1.0;
      recentCard.max = "1";
      AssignmentRule recentInstance = new AssignmentRule("address[RecentAddress]")
          .<AssignmentRule>withFile("Assigned.fsh").withLocation(8, 9, 8, 54);
      recentInstance.value = "CustomPostalAddress";
      recentInstance.isInstance = true;

      movingPatient.rules.addAll(List.of(
          movingStart,
          slicingType,
          slicingPath,
          slicingRules,
          recentAddress,
          recentCard,
          recentInstance));

      exporter.exportStructDef(movingPatient);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition periodStartElement = sd.findElement("Patient.address.period.start");
      ElementDefinition addressSliceElement = sd.findElement("Patient.address:RecentAddress");

      assertEquals("1998-07-04", at(periodStartElement, "patternDateTime"));
      assertNull(at(addressSliceElement, "patternAddress"));
      assertMatches("Cannot assign 2020-04-01 to this element.*File: Assigned\\.fsh.*Line: 8\\D*", "s",
          loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should resolve soft indexing within Caret Paths on profiles")
    void softIndexingInCaretPaths() {
      // Profile: TestPatient
      // Parent: Patient
      // * address.line ^slicing.discriminator[+].type = #pattern
      // * address.line ^slicing.discriminator[=].path = "$this"
      // * address.line ^slicing.rules = #open
      // * address.line contains ApartmentName 0..1

      Profile testPatient = new Profile("TestPatient");
      testPatient.parent = "Patient";

      CaretValueRule slicingTypeLine = new CaretValueRule("address.line");
      slicingTypeLine.caretPath = "slicing.discriminator[+].type";
      slicingTypeLine.value = new FshCode("pattern");
      CaretValueRule slicingPathLine = new CaretValueRule("address.line");
      slicingPathLine.caretPath = "slicing.discriminator[=].path";
      slicingPathLine.value = "code";
      CaretValueRule slicingRulesLine = new CaretValueRule("address.line");
      slicingRulesLine.caretPath = "slicing.rules";
      slicingRulesLine.value = new FshCode("open");
      ContainsRule containsAddressLine = new ContainsRule("address.line");
      containsAddressLine.items.add(new ContainsRuleItem("ApartmentName"));
      CardRule apartmentNameCard = new CardRule("address.line[ApartmentName]");
      apartmentNameCard.min = 0.0;
      apartmentNameCard.max = "1";

      testPatient.rules.addAll(List.of(
          slicingTypeLine,
          slicingPathLine,
          slicingRulesLine,
          containsAddressLine,
          apartmentNameCard));

      exporter.exportStructDef(testPatient);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition addressLineElement = sd.findElement("Patient.address.line");
      assertEquals("pattern", at(addressLineElement, "slicing.discriminator[0].type"));
      assertEquals("code", at(addressLineElement, "slicing.discriminator[0].path"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should not change slice cardinality when an AssignmentRule is applied directly on the slice")
    void sliceCardinalityUnchanged() {
      // See: https://github.com/FHIR/sushi/issues/810
      //
      // Profile: RespRateFragment
      // Parent: Observation
      // * code.coding ^slicing.discriminator[0].type = #pattern
      // * code.coding ^slicing.discriminator[0].path = "$this"
      // * code.coding ^slicing.rules = #open
      // * code.coding contains snomed 0..*
      // * code.coding[snomed] = http://snomed.info/sct#86290005
      Profile profile = new Profile("RespRateFragment");
      profile.parent = "Observation";
      CaretValueRule slicingTypeLine = new CaretValueRule("code.coding");
      slicingTypeLine.caretPath = "slicing.discriminator[0].type";
      slicingTypeLine.value = new FshCode("pattern");
      CaretValueRule slicingPathLine = new CaretValueRule("code.coding");
      slicingPathLine.caretPath = "slicing.discriminator[0].path";
      slicingPathLine.value = "$this";
      CaretValueRule slicingRulesLine = new CaretValueRule("code.coding");
      slicingRulesLine.caretPath = "slicing.rules";
      slicingRulesLine.value = new FshCode("open");
      ContainsRule containsSnomed = new ContainsRule("code.coding");
      containsSnomed.items.add(new ContainsRuleItem("snomed"));
      CardRule snomedCard = new CardRule("code.coding[snomed]");
      snomedCard.min = 0.0;
      snomedCard.max = "*";
      AssignmentRule snomedAssignment = new AssignmentRule("code.coding[snomed]");
      snomedAssignment.value = new FshCode("86290005", "http://snomed.info/sct");

      profile.rules.addAll(List.of(
          slicingTypeLine,
          slicingPathLine,
          slicingRulesLine,
          containsSnomed,
          snomedCard,
          snomedAssignment));

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition snomedElement = sd.findElement("Observation.code.coding:snomed");
      assertJsEqual(0, at(snomedElement, "min"));
      assertEquals("*", at(snomedElement, "max"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #AssignedValueRule should not apply a AssignmentRule to a slice when it would conflict with a child slice of the list element")
    void sliceConflictsWithListChildSlice() {
      // Instance: CustomPostalAddress
      // InstanceOf: Address
      // Usage: #inline
      // * line[0] = "First part of address"
      Instance customPostalAddress = new Instance("CustomPostalAddress");
      customPostalAddress.instanceOf = "Address";
      customPostalAddress.usage = InstanceUsage.Inline;
      AssignmentRule customLine = new AssignmentRule("line[0]");
      customLine.value = "First part of address";
      customPostalAddress.rules.add(customLine);
      doc.instances.put(customPostalAddress.name, customPostalAddress);

      // Profile: CustomPatient
      // Parent: Patient
      // * address.line ^slicing.discriminator[0].type = #pattern
      // * address.line ^slicing.discriminator[0].path = "$this"
      // * address.line ^slicing.rules = #closed
      // * address.line contains SpecificLine 1..1
      // * address.line[SpecificLine] = "Specific part of address"
      // * address ^slicing.discriminator[0].type = #pattern
      // * address ^slicing.discriminator[0].path = "$this"
      // * address ^slicing.rules = #open
      // * address contains RecentAddress 1..1
      // * address[RecentAddress] = CustomPostalAddress // this rule should not be applied
      Profile customPatient = new Profile("CustomPatient");
      customPatient.parent = "Patient";

      CaretValueRule slicingTypeLine = new CaretValueRule("address.line");
      slicingTypeLine.caretPath = "slicing.discriminator[0].type";
      slicingTypeLine.value = new FshCode("pattern");
      CaretValueRule slicingPathLine = new CaretValueRule("address.line");
      slicingPathLine.caretPath = "slicing.discriminator[0].path";
      slicingPathLine.value = "code";
      CaretValueRule slicingRulesLine = new CaretValueRule("address.line");
      slicingRulesLine.caretPath = "slicing.rules";
      slicingRulesLine.value = new FshCode("closed");
      ContainsRule containsSpecificLine = new ContainsRule("address.line");
      containsSpecificLine.items.add(new ContainsRuleItem("SpecificLine"));
      CardRule specificLineCard = new CardRule("address.line[SpecificLine]");
      specificLineCard.min = 1.0;
      specificLineCard.max = "1";
      AssignmentRule specificLine = new AssignmentRule("address.line[SpecificLine]");
      specificLine.value = "Specific part of address";

      CaretValueRule slicingType = new CaretValueRule("address");
      slicingType.caretPath = "slicing.discriminator[0].type";
      slicingType.value = new FshCode("pattern");
      CaretValueRule slicingPath = new CaretValueRule("address");
      slicingPath.caretPath = "slicing.discriminator[0].path";
      slicingPath.value = "code";
      CaretValueRule slicingRules = new CaretValueRule("address");
      slicingRules.caretPath = "slicing.rules";
      slicingRules.value = new FshCode("open");
      ContainsRule recentAddress = new ContainsRule("address");
      recentAddress.items.add(new ContainsRuleItem("RecentAddress"));
      CardRule recentCard = new CardRule("address[RecentAddress]");
      recentCard.min = 1.0;
      recentCard.max = "1";
      AssignmentRule recentInstance = new AssignmentRule("address[RecentAddress]")
          .<AssignmentRule>withFile("Assigned.fsh").withLocation(12, 9, 12, 54);
      recentInstance.value = "CustomPostalAddress";
      recentInstance.isInstance = true;

      customPatient.rules.addAll(List.of(
          slicingTypeLine,
          slicingPathLine,
          slicingRulesLine,
          containsSpecificLine,
          specificLineCard,
          specificLine,
          slicingType,
          slicingPath,
          slicingRules,
          recentAddress,
          recentCard,
          recentInstance));

      exporter.exportStructDef(customPatient);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition addressLineElement = sd.findElement("Patient.address.line:SpecificLine");
      ElementDefinition addressSliceElement = sd.findElement("Patient.address:RecentAddress");
      assertNotNull(at(addressLineElement, "patternString"));
      assertNull(at(addressSliceElement, "patternAddress"));
      assertMatches("Cannot assign.*First part of address.*to this element.*File: Assigned\\.fsh.*Line: 12\\D*", "s",
          loggerSpy.getLastMessage("error"));
    }
  }
}
