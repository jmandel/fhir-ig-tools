package org.hl7.fhir.sushi.fhirtypes;

import static org.hl7.fhir.sushi.fhirtypes.EdTestSupport.*;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.hl7.fhir.sushi.errors.*;
import org.hl7.fhir.sushi.fhirdefs.FHIRDefinitions;
import org.hl7.fhir.sushi.fshtypes.*;
import org.hl7.fhir.sushi.fshtypes.rules.*;
import org.hl7.fhir.common.js.Js;
import org.hl7.fhir.common.js.JsObject;
import org.hl7.fhir.sushi.testhelpers.TestFisher;
import org.hl7.fhir.sushi.utils.FishableType;
import org.junit.jupiter.api.*;

// port of sushi/test/fhirtypes/ElementDefinition.assignFshRatio.test.ts (SUSHI 3.21.1)
@DisplayName("ElementDefinition")
class ElementDefinitionAssignFshRatioTest {
  static FHIRDefinitions defs;
  StructureDefinition medication;
  StructureDefinition observation;
  FshRatio fshRatio;
  FshRatio fshRatioNoUnits;
  FshRatio differentFshRatio;
  static TestFisher fisher;
  @BeforeAll
  static void beforeAll0() {
    defs = r4Defs();
    fisher = new TestFisher().withFHIR(defs);
  }
  @BeforeEach
  void beforeEach1() {
    observation = fisher.fishForStructureDefinition("Observation");
    medication = fisher.fishForStructureDefinition("Medication");
    fshRatio = new FshRatio(new FshQuantity(1.2, new FshCode("mm", "http://unitsofmeasure.org")), new FshQuantity(3.4, new FshCode("cm", "http://unitsofmeasure.org")));
    fshRatioNoUnits = new FshRatio(new FshQuantity(1.2), new FshQuantity(3.4));
    differentFshRatio = new FshRatio(new FshQuantity(1.3, new FshCode("mm", "http://unitsofmeasure.org")), new FshQuantity(3.4, new FshCode("cm", "http://unitsofmeasure.org")));
  }

  @Nested
  @DisplayName("#assignFshRatio")
  class AssignFshRatio {

    @Test
    @DisplayName("ElementDefinition #assignFshRatio should assign a FshRatio to a Ratio")
    void t2() {
      ElementDefinition amount = el(medication, "Medication.amount");
      amount.assignValue(fshRatio);
      assertJsEqual(obj("numerator", obj("value", 1.2, "code", "mm", "system", "http://unitsofmeasure.org"), "denominator", obj("value", 3.4, "code", "cm", "system", "http://unitsofmeasure.org")), at(amount, "patternRatio"));
      assertNull(at(amount, "fixedRatio"));
    }

    @Test
    @DisplayName("ElementDefinition #assignFshRatio should assign a FshRatio to a Ratio (exactly)")
    void t3() {
      ElementDefinition amount = el(medication, "Medication.amount");
      amount.assignValue(fshRatio, true);
      assertJsEqual(obj("numerator", obj("value", 1.2, "code", "mm", "system", "http://unitsofmeasure.org"), "denominator", obj("value", 3.4, "code", "cm", "system", "http://unitsofmeasure.org")), at(amount, "fixedRatio"));
      assertNull(at(amount, "patternRatio"));
    }

    @Test
    @DisplayName("ElementDefinition #assignFshRatio should assign a FshRatio without units to a Ratio")
    void t4() {
      ElementDefinition amount = el(medication, "Medication.amount");
      amount.assignValue(fshRatioNoUnits);
      assertJsEqual(obj("numerator", obj("value", 1.2), "denominator", obj("value", 3.4)), at(amount, "patternRatio"));
      assertNull(at(amount, "fixedRatio"));
    }

    @Test
    @DisplayName("ElementDefinition #assignFshRatio should assign a FshRatio without units to a Ratio (exactly)")
    void t5() {
      ElementDefinition amount = el(medication, "Medication.amount");
      amount.assignValue(fshRatioNoUnits, true);
      assertJsEqual(obj("numerator", obj("value", 1.2), "denominator", obj("value", 3.4)), at(amount, "fixedRatio"));
      assertNull(at(amount, "patternRatio"));
    }

    @Test
    @DisplayName("ElementDefinition #assignFshRatio should throw NoSingleTypeError when element has multiple types")
    void t6() {
      ElementDefinition valueX = el(observation, "Observation.value[x]");
      assertThrowsContaining("Cannot assign Ratio value on this element since this element does not have a single type", () -> { valueX.assignValue(fshRatio);
 });
      assertThrowsContaining("Cannot assign Ratio value on this element since this element does not have a single type", () -> { valueX.assignValue(fshRatio, true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignFshRatio should throw RatioAlreadyAssignedError when the value is assigned to a different value by pattern[x]")
    void t7() {
      ElementDefinition amount = el(medication, "Medication.amount");
      amount.assignValue(fshRatio);
      // should be able to assign a Ratio twice in the same way without issue
      amount.assignValue(fshRatio);
      assertJsEqual(obj("numerator", obj("value", 1.2, "code", "mm", "system", "http://unitsofmeasure.org"), "denominator", obj("value", 3.4, "code", "cm", "system", "http://unitsofmeasure.org")), at(amount, "patternRatio"));
      // different value
      assertThrowsContaining("Cannot assign 1.3 'mm' : 3.4 'cm' to this element; a different Ratio is already assigned: {\"numerator\":{\"value\":1.2,\"code\":\"mm\",\"system\":\"http://unitsofmeasure.org\"},\"denominator\":{\"value\":3.4,\"code\":\"cm\",\"system\":\"http://unitsofmeasure.org\"}}.", () -> { amount.assignValue(differentFshRatio);
 });
      assertThrowsContaining("Cannot assign 1.3 'mm' : 3.4 'cm' to this element; a different Ratio is already assigned: {\"numerator\":{\"value\":1.2,\"code\":\"mm\",\"system\":\"http://unitsofmeasure.org\"},\"denominator\":{\"value\":3.4,\"code\":\"cm\",\"system\":\"http://unitsofmeasure.org\"}}.", () -> { amount.assignValue(differentFshRatio, true);
 });
      // different units
      assertThrowsContaining("Cannot assign 1.2 : 3.4 to this element; a different Ratio is already assigned: {\"numerator\":{\"value\":1.2,\"code\":\"mm\",\"system\":\"http://unitsofmeasure.org\"},\"denominator\":{\"value\":3.4,\"code\":\"cm\",\"system\":\"http://unitsofmeasure.org\"}}.", () -> { amount.assignValue(fshRatioNoUnits);
 });
      assertThrowsContaining("Cannot assign 1.2 : 3.4 to this element; a different Ratio is already assigned: {\"numerator\":{\"value\":1.2,\"code\":\"mm\",\"system\":\"http://unitsofmeasure.org\"},\"denominator\":{\"value\":3.4,\"code\":\"cm\",\"system\":\"http://unitsofmeasure.org\"}}.", () -> { amount.assignValue(fshRatioNoUnits, true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignFshRatio should throw RatioAlreadyAssignedError when the value is assigned to a different value by fixed[x]")
    void t8() {
      ElementDefinition amount = el(medication, "Medication.amount");
      amount.assignValue(fshRatio, true);
      // should be able to assign a Ratio twice in the same way without issue
      amount.assignValue(fshRatio, true);
      assertJsEqual(obj("numerator", obj("value", 1.2, "code", "mm", "system", "http://unitsofmeasure.org"), "denominator", obj("value", 3.4, "code", "cm", "system", "http://unitsofmeasure.org")), at(amount, "fixedRatio"));
      // different value
      assertThrowsContaining("Cannot assign 1.3 'mm' : 3.4 'cm' to this element; a different Ratio is already assigned: {\"numerator\":{\"value\":1.2,\"code\":\"mm\",\"system\":\"http://unitsofmeasure.org\"},\"denominator\":{\"value\":3.4,\"code\":\"cm\",\"system\":\"http://unitsofmeasure.org\"}}.", () -> { amount.assignValue(differentFshRatio, true);
 });
      // different units
      assertThrowsContaining("Cannot assign 1.2 : 3.4 to this element; a different Ratio is already assigned: {\"numerator\":{\"value\":1.2,\"code\":\"mm\",\"system\":\"http://unitsofmeasure.org\"},\"denominator\":{\"value\":3.4,\"code\":\"cm\",\"system\":\"http://unitsofmeasure.org\"}}.", () -> { amount.assignValue(fshRatioNoUnits, true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignFshRatio should throw FixedToPatternError when trying to change fixed[x] to pattern[x]")
    void t9() {
      ElementDefinition amount = el(medication, "Medication.amount");
      amount.assignValue(fshRatio, true);
      // different value
      assertThrowsContaining("Cannot assign this element using a pattern; as it is already assigned in the StructureDefinition using fixedRatio.", () -> { amount.assignValue(fshRatio);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignFshRatio should throw MismatchedTypeError when the value is assigned to a non-Ratio")
    void t10() {
      ElementDefinition status = el(observation, "Observation.status");
      // with units
      assertThrowsContaining("Cannot assign Ratio value: 1.2 'mm' : 3.4 'cm'. Value does not match element type: code", () -> { status.assignValue(fshRatio);
 });
      assertThrowsContaining("Cannot assign Ratio value: 1.2 'mm' : 3.4 'cm'. Value does not match element type: code", () -> { status.assignValue(fshRatio, true);
 });
      // without units
      assertThrowsContaining("Cannot assign Ratio value: 1.2 : 3.4. Value does not match element type: code", () -> { status.assignValue(fshRatioNoUnits);
 });
      assertThrowsContaining("Cannot assign Ratio value: 1.2 : 3.4. Value does not match element type: code", () -> { status.assignValue(fshRatioNoUnits, true);
 });
    }
  }
}
