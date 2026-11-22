package org.hl7.fhir.sushi.fhirtypes;

import static org.hl7.fhir.sushi.fhirtypes.EdTestSupport.*;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.hl7.fhir.sushi.errors.*;
import org.hl7.fhir.sushi.fhirdefs.FHIRDefinitions;
import org.hl7.fhir.sushi.fshtypes.*;
import org.hl7.fhir.sushi.fshtypes.rules.*;
import org.hl7.fhir.sushi.js.Js;
import org.hl7.fhir.sushi.js.JsObject;
import org.hl7.fhir.sushi.testhelpers.TestFisher;
import org.hl7.fhir.sushi.utils.FishableType;
import org.junit.jupiter.api.*;

// port of sushi/test/fhirtypes/ElementDefinition.assignFshQuantity.test.ts (SUSHI 3.21.1)
@DisplayName("ElementDefinition")
class ElementDefinitionAssignFshQuantityTest {
  static FHIRDefinitions defs;
  StructureDefinition condition;
  StructureDefinition observation;
  FshQuantity fshQuantity1;
  FshQuantity fshQuantity2;
  FshQuantity fshQuantityAge;
  static TestFisher fisher;
  @BeforeAll
  static void beforeAll0() {
    defs = r4Defs();
    fisher = new TestFisher().withFHIR(defs);
  }
  @BeforeEach
  void beforeEach1() {
    observation = fisher.fishForStructureDefinition("Observation");
    fshQuantity1 = new FshQuantity(1.23, new FshCode("mm", "http://unitsofmeasure.org"));
    fshQuantity2 = new FshQuantity(1.24, new FshCode("mm", "http://unitsofmeasure.org"));
    condition = fisher.fishForStructureDefinition("Condition");
    fshQuantityAge = new FshQuantity(42.0, new FshCode("a", "http://unitsofmeasure.org"));
  }

  @Nested
  @DisplayName("#assignFshQuantity")
  class AssignFshQuantity {

    @Test
    @DisplayName("ElementDefinition #assignFshQuantity should assign a FshQuantity to a Quantity")
    void t2() {
      ElementDefinition referenceRangeLow = el(observation, "Observation.referenceRange.low");
      referenceRangeLow.assignValue(fshQuantity1);
      assertJsEqual(obj("value", 1.23, "code", "mm", "system", "http://unitsofmeasure.org"), at(referenceRangeLow, "patternQuantity"));
      assertNull(at(referenceRangeLow, "fixedQuantity"));
    }

    @Test
    @DisplayName("ElementDefinition #assignFshQuantity should assign a FshQuantity to a Quantity (exactly)")
    void t3() {
      ElementDefinition referenceRangeLow = el(observation, "Observation.referenceRange.low");
      referenceRangeLow.assignValue(fshQuantity1, true);
      assertJsEqual(obj("value", 1.23, "code", "mm", "system", "http://unitsofmeasure.org"), at(referenceRangeLow, "fixedQuantity"));
      assertNull(at(referenceRangeLow, "patternQuantity"));
    }

    @Test
    @DisplayName("ElementDefinition #assignFshQuantity should assign a FshQuantity to a quantity specialization")
    void t4() {
      ElementDefinition onsetX = el(condition, "Condition.onset[x]");
      setAt(onsetX, "type", list(new ElementDefinitionType("Age")));
      onsetX.assignValue(fshQuantityAge, false, fisher);
      assertJsEqual(obj("value", 42.0, "code", "a", "system", "http://unitsofmeasure.org"), at(onsetX, "patternAge"));
      assertNull(at(onsetX, "fixedAge"));
    }

    @Test
    @DisplayName("ElementDefinition #assignFshQuantity should assign a FshQuantity to a quantity specialization (exactly)")
    void t5() {
      ElementDefinition onsetX = el(condition, "Condition.onset[x]");
      setAt(onsetX, "type", list(new ElementDefinitionType("Age")));
      onsetX.assignValue(fshQuantityAge, true, fisher);
      assertJsEqual(obj("value", 42.0, "code", "a", "system", "http://unitsofmeasure.org"), at(onsetX, "fixedAge"));
      assertNull(at(onsetX, "patternAge"));
    }

    @Test
    @DisplayName("ElementDefinition #assignFshQuantity should throw NoSingleTypeError when element has multiple types")
    void t6() {
      ElementDefinition valueX = el(observation, "Observation.value[x]");
      assertThrowsContaining("Cannot assign Quantity value on this element since this element does not have a single type", () -> { valueX.assignValue(fshQuantity1);
 });
      assertThrowsContaining("Cannot assign Quantity value on this element since this element does not have a single type", () -> { valueX.assignValue(fshQuantity1, true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignFshQuantity should throw ValueAlreadyAssignedError when the value is assigned to a different value by pattern[x]")
    void t7() {
      ElementDefinition referenceRangeLow = el(observation, "Observation.referenceRange.low");
      referenceRangeLow.assignValue(fshQuantity1);
      // should be able to assign a Quantity twice in the same way without issue
      referenceRangeLow.assignValue(fshQuantity1);
      assertJsEqual(obj("value", 1.23, "code", "mm", "system", "http://unitsofmeasure.org"), at(referenceRangeLow, "patternQuantity"));
      // different value
      assertThrowsContaining("Cannot assign 1.24 'mm' to this element; a different Quantity is already assigned: {\"value\":1.23,\"code\":\"mm\",\"system\":\"http://unitsofmeasure.org\"}.", () -> { referenceRangeLow.assignValue(fshQuantity2);
 });
      assertThrowsContaining("Cannot assign 1.24 'mm' to this element; a different Quantity is already assigned: {\"value\":1.23,\"code\":\"mm\",\"system\":\"http://unitsofmeasure.org\"}.", () -> { referenceRangeLow.assignValue(fshQuantity2, true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignFshQuantity should throw ValueAlreadyAssignedError when the value is assigned to a different value by fixed[x]")
    void t8() {
      ElementDefinition referenceRangeLow = el(observation, "Observation.referenceRange.low");
      referenceRangeLow.assignValue(fshQuantity1, true);
      // should be able to assign a Quantity twice in the same way without issue
      referenceRangeLow.assignValue(fshQuantity1, true);
      assertJsEqual(obj("value", 1.23, "code", "mm", "system", "http://unitsofmeasure.org"), at(referenceRangeLow, "fixedQuantity"));
      assertThrowsContaining("Cannot assign 1.24 'mm' to this element; a different Quantity is already assigned: {\"value\":1.23,\"code\":\"mm\",\"system\":\"http://unitsofmeasure.org\"}.", () -> { referenceRangeLow.assignValue(fshQuantity2, true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignFshQuantity should throw ValueAlreadyAssignedError when assigning a Quantity to a different value set in a parent by pattern[x]")
    void t9() {
      ElementDefinition valueRange = observation.findElementByPath("valueRange", fisher);
      // @ts-ignore
      setAt(valueRange, "patternRange", obj("low", obj("value", 1.5)));
      valueRange.unfold(fisher);
      ElementDefinition valueRangeLow = el(observation, "Observation.value[x]:valueRange.low");
      ElementDefinition clone = valueRangeLow.clone(false);
      assertThrowsContaining("Cannot assign 2.5 to this element; a different Quantity is already assigned: {\"value\":1.5}.", () -> { valueRangeLow.assignValue(new FshQuantity(2.5));
 });
      assertThrowsContaining("Cannot assign 2.5 to this element; a different Quantity is already assigned: {\"value\":1.5}.", () -> { valueRangeLow.assignValue(new FshQuantity(2.5), true);
 });
      assertSameState(valueRangeLow, clone);
    }

    @Test
    @DisplayName("ElementDefinition #assignFshQuantity should throw ValueAlreadyAssignedError when assigning a Quantity to a different value set in a parent by fixed[x]")
    void t10() {
      ElementDefinition valueRange = observation.findElementByPath("valueRange", fisher);
      // @ts-ignore
      setAt(valueRange, "fixedRange", obj("low", obj("value", 1.5)));
      valueRange.unfold(fisher);
      ElementDefinition valueRangeLow = el(observation, "Observation.value[x]:valueRange.low");
      ElementDefinition clone = valueRangeLow.clone(false);
      assertThrowsContaining("Cannot assign 2.5 to this element; a different Quantity is already assigned: {\"value\":1.5}.", () -> { valueRangeLow.assignValue(new FshQuantity(2.5));
 });
      assertThrowsContaining("Cannot assign 2.5 to this element; a different Quantity is already assigned: {\"value\":1.5}.", () -> { valueRangeLow.assignValue(new FshQuantity(2.5), true);
 });
      assertSameState(valueRangeLow, clone);
    }

    @Test
    @DisplayName("ElementDefinition #assignFshQuantity should throw ValueAlreadyAssignedError when the value is assigned to a different value, no units by pattern[x]")
    void t11() {
      ElementDefinition referenceRangeLow = el(observation, "Observation.referenceRange.low");
      referenceRangeLow.assignValue(new FshQuantity(1.23));
      // should be able to assign a Quantity twice in the same way without issue
      referenceRangeLow.assignValue(new FshQuantity(1.23));
      assertJsEqual(obj("value", 1.23), at(referenceRangeLow, "patternQuantity"));
      // different value
      assertThrowsContaining("Cannot assign 3.21 to this element; a different Quantity is already assigned: {\"value\":1.23}.", () -> { referenceRangeLow.assignValue(new FshQuantity(3.21));
 });
      assertThrowsContaining("Cannot assign 3.21 to this element; a different Quantity is already assigned: {\"value\":1.23}.", () -> { referenceRangeLow.assignValue(new FshQuantity(3.21), true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignFshQuantity should throw ValueAlreadyAssignedError when the value is assigned to a different value, no units by fixed[x]")
    void t12() {
      ElementDefinition referenceRangeLow = el(observation, "Observation.referenceRange.low");
      referenceRangeLow.assignValue(new FshQuantity(1.23), true);
      // should be able to assign a Quantity twice in the same way without issue
      referenceRangeLow.assignValue(new FshQuantity(1.23), true);
      assertJsEqual(obj("value", 1.23), at(referenceRangeLow, "fixedQuantity"));
      // different value
      assertThrowsContaining("Cannot assign 3.21 to this element; a different Quantity is already assigned: {\"value\":1.23}.", () -> { referenceRangeLow.assignValue(new FshQuantity(3.21), true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignFshQuantity should throw AssignedToPatternError when trying to change fixed[x] to pattern[x]")
    void t13() {
      ElementDefinition referenceRangeLow = el(observation, "Observation.referenceRange.low");
      referenceRangeLow.assignValue(fshQuantity1, true);
      assertThrowsContaining("Cannot assign this element using a pattern; as it is already assigned in the StructureDefinition using fixedQuantity.", () -> { referenceRangeLow.assignValue(fshQuantity1);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignFshQuantity should throw MismatchedTypeError when the value is assigned to a non-Quantity")
    void t14() {
      ElementDefinition status = el(observation, "Observation.status");
      // with units
      assertThrowsContaining("Cannot assign Quantity value: 1.24 'mm'. Value does not match element type: code", () -> { status.assignValue(fshQuantity2);
 });
      assertThrowsContaining("Cannot assign Quantity value: 1.24 'mm'. Value does not match element type: code", () -> { status.assignValue(fshQuantity2, true);
 });
      // without units
      assertThrowsContaining("Cannot assign Quantity value: 1.24. Value does not match element type: code", () -> { status.assignValue(new FshQuantity(1.24));
 });
      assertThrowsContaining("Cannot assign Quantity value: 1.24. Value does not match element type: code", () -> { status.assignValue(new FshQuantity(1.24), true);
 });
    }
  }
}
