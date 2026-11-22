package org.hl7.fhir.sushi.fhirtypes;

import static org.hl7.fhir.sushi.fhirtypes.EdTestSupport.*;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.hl7.fhir.sushi.errors.*;
import org.hl7.fhir.sushi.fhirdefs.FHIRDefinitions;
import org.hl7.fhir.sushi.fshtypes.*;
import org.hl7.fhir.sushi.fshtypes.rules.*;
import org.hl7.fhir.sushi.importer.FSHDocument;
import org.hl7.fhir.sushi.importer.FSHTank;
import org.hl7.fhir.sushi.export.Package;
import org.hl7.fhir.sushi.export.StructureDefinitionExporter;
import static org.hl7.fhir.sushi.testhelpers.MinimalConfig.minimalConfig;
import org.hl7.fhir.common.js.Js;
import org.hl7.fhir.common.js.JsObject;
import org.hl7.fhir.sushi.testhelpers.TestFisher;
import org.hl7.fhir.sushi.utils.FishableType;
import org.junit.jupiter.api.*;

// port of sushi/test/fhirtypes/ElementDefinition.assignFshReference.test.ts (SUSHI 3.21.1)
@DisplayName("ElementDefinition")
class ElementDefinitionAssignFshReferenceTest {
  static FHIRDefinitions defs;
  StructureDefinition observation;
  FshReference fshReference1;
  FshReference fshReference2;
  static TestFisher fisher;
  @BeforeAll
  static void beforeAll0() {
    defs = r4Defs();
    fisher = new TestFisher().withFHIR(defs);
  }
  @BeforeEach
  void beforeEach1() {
    observation = fisher.fishForStructureDefinition("Observation");
    fshReference1 = new FshReference("foo", "bar");
    fshReference2 = new FshReference("otherFoo", "otherBar");
  }

  @Nested
  @DisplayName("#assignFshReference")
  class AssignFshReference {

    @Test
    @DisplayName("ElementDefinition #assignFshReference should assign a FshReference to a Reference")
    void t2() {
      ElementDefinition subject = el(observation, "Observation.subject");
      subject.assignValue(fshReference1);
      assertJsEqual(obj("reference", "foo", "display", "bar"), at(subject, "patternReference"));
      assertNull(at(subject, "fixedReference"));
    }

    @Test
    @DisplayName("ElementDefinition #assignFshReference should assign a FshReference to a Reference (exactly)")
    void t3() {
      ElementDefinition subject = el(observation, "Observation.subject");
      subject.assignValue(fshReference1, true);
      assertJsEqual(obj("reference", "foo", "display", "bar"), at(subject, "fixedReference"));
      assertNull(at(subject, "patternReference"));
    }

    @Test
    @DisplayName("ElementDefinition #assignFshReference should assign a FshReference to a Reference allowing a more specific display")
    void t4() {
      ElementDefinition subject = el(observation, "Observation.subject");
      subject.assignValue(new FshReference("foo"));
      assertJsEqual(obj("reference", "foo"), at(subject, "patternReference"));
      subject.assignValue(new FshReference("foo", "bar"));
      assertJsEqual(obj("reference", "foo", "display", "bar"), at(subject, "patternReference"));
      assertNull(at(subject, "fixedReference"));
    }

    @Test
    @DisplayName("ElementDefinition #assignFshReference should assign a FshReference to a Reference allowing a more specific display (exactly)")
    void t5() {
      ElementDefinition subject = el(observation, "Observation.subject");
      subject.assignValue(new FshReference("foo"), true);
      assertJsEqual(obj("reference", "foo"), at(subject, "fixedReference"));
      subject.assignValue(new FshReference("foo", "bar"), true);
      assertJsEqual(obj("reference", "foo", "display", "bar"), at(subject, "fixedReference"));
      assertNull(at(subject, "patternReference"));
    }

    @Test
    @DisplayName("ElementDefinition #assignFshReference should throw NoSingleTypeError when element has multiple types")
    void t6() {
      ElementDefinition valueX = el(observation, "Observation.value[x]");
      assertThrowsContaining("Cannot assign Reference value on this element since this element does not have a single type", () -> { valueX.assignValue(fshReference1);
 });
      assertThrowsContaining("Cannot assign Reference value on this element since this element does not have a single type", () -> { valueX.assignValue(fshReference1, true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignFshReference should throw ValueAlreadyAssignedError when the value is assigned to a different value by pattern[x]")
    void t7() {
      ElementDefinition subject = el(observation, "Observation.subject");
      subject.assignValue(fshReference1);
      // should be able to assign a Reference twice in the same way without issue
      subject.assignValue(fshReference1);
      assertJsEqual(obj("reference", "foo", "display", "bar"), at(subject, "patternReference"));
      // different value
      assertThrowsContaining("Cannot assign Reference(otherFoo) \"otherBar\" to this element; a different Reference is already assigned: {\"reference\":\"foo\",\"display\":\"bar\"}.", () -> { subject.assignValue(fshReference2);
 });
      assertThrowsContaining("Cannot assign Reference(otherFoo) \"otherBar\" to this element; a different Reference is already assigned: {\"reference\":\"foo\",\"display\":\"bar\"}.", () -> { subject.assignValue(fshReference2, true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignFshReference should throw ValueAlreadyAssignedError when the value is assigned to a different value by fixed[x]")
    void t8() {
      ElementDefinition subject = el(observation, "Observation.subject");
      subject.assignValue(fshReference1, true);
      // should be able to assign a Reference twice in the same way without issue
      subject.assignValue(fshReference1, true);
      assertJsEqual(obj("reference", "foo", "display", "bar"), at(subject, "fixedReference"));
      // different value
      assertThrowsContaining("Cannot assign Reference(otherFoo) \"otherBar\" to this element; a different Reference is already assigned: {\"reference\":\"foo\",\"display\":\"bar\"}.", () -> { subject.assignValue(fshReference2, true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignFshReference should throw ValueAlreadyAssignedError when the value is assigned to a different value and a display is added")
    void t9() {
      ElementDefinition subject = el(observation, "Observation.subject");
      subject.assignValue(new FshReference("foo"));
      assertJsEqual(obj("reference", "foo"), at(subject, "patternReference"));
      assertThrowsContaining("Cannot assign Reference(bar) \"bar\" to this element; a different Reference is already assigned: {\"reference\":\"foo\"}.", () -> subject.assignValue(new FshReference("bar", "bar")));
    }

    @Test
    @DisplayName("ElementDefinition #assignFshReference should throw ValueAlreadyAssignedError when the value is assigned to a different value, no display")
    void t10() {
      ElementDefinition subject = el(observation, "Observation.subject");
      subject.assignValue(new FshReference("foo"));
      // should be able to assign a Reference twice in the same way without issue
      subject.assignValue(new FshReference("foo"));
      assertJsEqual(obj("reference", "foo"), at(subject, "patternReference"));
      // different value
      assertThrowsContaining("Cannot assign Reference(bar) to this element; a different Reference is already assigned: {\"reference\":\"foo\"}.", () -> { subject.assignValue(new FshReference("bar"));
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignFshReference should throw FixedToPatternError when trying to change fixed[x] to pattern[x]")
    void t11() {
      ElementDefinition subject = el(observation, "Observation.subject");
      subject.assignValue(fshReference1, true);
      // different value
      assertThrowsContaining("Cannot assign this element using a pattern; as it is already assigned in the StructureDefinition using fixedReference.", () -> { subject.assignValue(fshReference2);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignFshReference should throw MismatchedTypeError when the value is assigned to a non-Reference")
    void t12() {
      ElementDefinition status = el(observation, "Observation.status");
      // with display
      assertThrowsContaining("Cannot assign Reference value: Reference(foo) \"bar\". Value does not match element type: code", () -> { status.assignValue(fshReference1);
 });
      assertThrowsContaining("Cannot assign Reference value: Reference(foo) \"bar\". Value does not match element type: code", () -> { status.assignValue(fshReference1, true);
 });
      // without display
      assertThrowsContaining("Cannot assign Reference value: Reference(foo). Value does not match element type: code", () -> { status.assignValue(new FshReference("foo"));
 });
      assertThrowsContaining("Cannot assign Reference value: Reference(foo). Value does not match element type: code", () -> { status.assignValue(new FshReference("foo"), true);
 });
    }

    @Nested
    @DisplayName("R5 CodeableReference")
    class R5CodeableReference {
      FSHDocument doc;
      TestFisher r5Fisher;
      StructureDefinition carePlan;
      @BeforeEach
      void beforeEach13() {
        doc = new FSHDocument("Conditions.fsh");
        var input = new FSHTank(list(doc), minimalConfig());
        var r5Defs = r5Defs();
        var pkg = new Package(input.config);
        r5Fisher = new TestFisher(input, r5Defs, pkg);
        carePlan = r5Fisher.fishForStructureDefinition("CarePlan");
        // we need an Instance of Condition to reference
        var condition = new Instance("TestCondition");
        condition.instanceOf = "Condition";
        var assignedIdRule = new AssignmentRule("id");
        assignedIdRule.value = "condition-id";
        condition.rules.add(assignedIdRule);
        doc.instances.put(condition.name, condition);
      }

      @Test
      @DisplayName("ElementDefinition #assignFshReference R5 CodeableReference should assign a Reference to a CodeableReference")
      void t14() {
        ElementDefinition addresses = el(carePlan, "CarePlan.addresses");
        addresses.assignValue(new FshReference("condition-id"));
        assertJsEqual(obj("reference", obj("reference", "condition-id")), at(addresses, "patternCodeableReference"));
        assertNull(at(addresses, "fixedCodeableReference"));
      }

      @Test
      @DisplayName("ElementDefinition #assignFshReference R5 CodeableReference should assign a Reference with a display to a CodeableReference")
      void t15() {
        ElementDefinition addresses = el(carePlan, "CarePlan.addresses");
        addresses.assignValue(new FshReference("condition-id", "Condition ID"));
        assertJsEqual(obj("reference", obj("reference", "condition-id", "display", "Condition ID")), at(addresses, "patternCodeableReference"));
        assertNull(at(addresses, "fixedCodeableReference"));
      }

      @Test
      @DisplayName("ElementDefinition #assignFshReference R5 CodeableReference should assign a Reference to a CodeableReference (exactly)")
      void t16() {
        ElementDefinition addresses = el(carePlan, "CarePlan.addresses");
        addresses.assignValue(new FshReference("condition-id"), true);
        assertJsEqual(obj("reference", obj("reference", "condition-id")), at(addresses, "fixedCodeableReference"));
        assertNull(at(addresses, "patternCodeableReference"));
      }

      @Test
      @DisplayName("ElementDefinition #assignFshReference R5 CodeableReference should assign a Reference with a display to a CodeableReference (exactly)")
      void t17() {
        ElementDefinition addresses = el(carePlan, "CarePlan.addresses");
        addresses.assignValue(new FshReference("condition-id", "Condition ID"), true);
        assertJsEqual(obj("reference", obj("reference", "condition-id", "display", "Condition ID")), at(addresses, "fixedCodeableReference"));
        assertNull(at(addresses, "patternCodeableReference"));
      }
    }
  }
}
