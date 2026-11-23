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

// port of sushi/test/fhirtypes/ElementDefinition.assignNumber.test.ts (SUSHI 3.21.1)
@DisplayName("ElementDefinition")
class ElementDefinitionAssignNumberTest {
  static FHIRDefinitions defs;
  StructureDefinition observation;
  StructureDefinition riskEvidenceSynthesis;
  StructureDefinition capabilityStatement;
  StructureDefinition appointment;
  static TestFisher fisher;
  @BeforeAll
  static void beforeAll0() {
    defs = r4Defs();
    fisher = new TestFisher().withFHIR(defs);
  }
  @BeforeEach
  void beforeEach1() {
    observation = fisher.fishForStructureDefinition("Observation");
    riskEvidenceSynthesis = fisher.fishForStructureDefinition("RiskEvidenceSynthesis");
    capabilityStatement = fisher.fishForStructureDefinition("CapabilityStatement");
    appointment = fisher.fishForStructureDefinition("Appointment");
  }

  @Nested
  @DisplayName("#assignNumber")
  class AssignNumber {
    // assigning a decimal

    @Test
    @DisplayName("ElementDefinition #assignNumber should assign a decimal to a decimal")
    void t2() {
      ElementDefinition riskEstimateValue = el(riskEvidenceSynthesis, "RiskEvidenceSynthesis.riskEstimate.value");
      riskEstimateValue.assignValue(1.23);
      assertEquals(1.23, at(riskEstimateValue, "patternDecimal"));
    }

    @Test
    @DisplayName("ElementDefinition #assignNumber should assign a decimal to a decimal (exactly)")
    void t3() {
      ElementDefinition riskEstimateValue = el(riskEvidenceSynthesis, "RiskEvidenceSynthesis.riskEstimate.value");
      riskEstimateValue.assignValue(1.23, true);
      assertEquals(1.23, at(riskEstimateValue, "fixedDecimal"));
    }

    @Test
    @DisplayName("ElementDefinition #assignNumber should assign an integer to a decimal")
    void t4() {
      ElementDefinition riskEstimateValue = el(riskEvidenceSynthesis, "RiskEvidenceSynthesis.riskEstimate.value");
      riskEstimateValue.assignValue(java.math.BigInteger.valueOf(123));
      assertEquals(123.0, at(riskEstimateValue, "patternDecimal"));
      assertNull(at(riskEstimateValue, "fixedDecimal"));
    }

    @Test
    @DisplayName("ElementDefinition #assignNumber should assign an integer to a decimal (exactly)")
    void t5() {
      ElementDefinition riskEstimateValue = el(riskEvidenceSynthesis, "RiskEvidenceSynthesis.riskEstimate.value");
      riskEstimateValue.assignValue(java.math.BigInteger.valueOf(123), true);
      assertEquals(123.0, at(riskEstimateValue, "fixedDecimal"));
      assertNull(at(riskEstimateValue, "patternDecimal"));
    }

    @Test
    @DisplayName("ElementDefinition #assignNumber should throw ValueAlreadyAssignedError when assigning an already assigned decimal by pattern[x]")
    void t6() {
      ElementDefinition riskEstimateValue = el(riskEvidenceSynthesis, "RiskEvidenceSynthesis.riskEstimate.value");
      riskEstimateValue.assignValue(1.23);
      assertEquals(1.23, at(riskEstimateValue, "patternDecimal"));
      assertThrowsContaining("Cannot assign 1.24 to this element; a different decimal is already assigned: 1.23.", () -> { riskEstimateValue.assignValue(1.24); });
      assertThrowsContaining("Cannot assign 1.24 to this element; a different decimal is already assigned: 1.23.", () -> { riskEstimateValue.assignValue(1.24, true); });
    }

    @Test
    @DisplayName("ElementDefinition #assignNumber should throw ValueAlreadyAssignedError when assigning an already assigned decimal by fixed[x]")
    void t7() {
      ElementDefinition riskEstimateValue = el(riskEvidenceSynthesis, "RiskEvidenceSynthesis.riskEstimate.value");
      riskEstimateValue.assignValue(1.23, true);
      assertEquals(1.23, at(riskEstimateValue, "fixedDecimal"));
      assertThrowsContaining("Cannot assign 1.24 to this element; a different decimal is already assigned: 1.23.", () -> { riskEstimateValue.assignValue(1.24, true); });
    }

    @Test
    @DisplayName("ElementDefinition #assignNumber should throw ValueAlreadyAssignedError when assigning a decimal to a different value set in a parent by pattern[x]")
    void t8() {
      ElementDefinition valueQuantity = observation.findElementByPath("valueQuantity", fisher);
      setAt(valueQuantity, "patternQuantity", obj("value", 1.5));
      valueQuantity.unfold(fisher);
      ElementDefinition valueQuantityValue = el(observation, "Observation.value[x]:valueQuantity.value");
      ElementDefinition clone = valueQuantityValue.clone(false);
      assertThrowsContaining("Cannot assign 2.5 to this element; a different decimal is already assigned: 1.5.", () -> { valueQuantityValue.assignValue(2.5); });
      assertThrowsContaining("Cannot assign 2.5 to this element; a different decimal is already assigned: 1.5.", () -> { valueQuantityValue.assignValue(2.5, true); });
      assertSameState(valueQuantityValue, clone);
    }

    @Test
    @DisplayName("ElementDefinition #assignNumber should throw ValueAlreadyAssignedError when assigning a decimal to a different value set in a parent by fixed[x]")
    void t9() {
      ElementDefinition valueQuantity = observation.findElementByPath("valueQuantity", fisher);
      setAt(valueQuantity, "fixedQuantity", obj("value", 1.5));
      valueQuantity.unfold(fisher);
      ElementDefinition valueQuantityValue = el(observation, "Observation.value[x]:valueQuantity.value");
      ElementDefinition clone = valueQuantityValue.clone(false);
      assertThrowsContaining("Cannot assign 2.5 to this element; a different decimal is already assigned: 1.5.", () -> { valueQuantityValue.assignValue(2.5); });
      assertThrowsContaining("Cannot assign 2.5 to this element; a different decimal is already assigned: 1.5.", () -> { valueQuantityValue.assignValue(2.5, true); });
      assertSameState(valueQuantityValue, clone);
    }

    @Test
    @DisplayName("ElementDefinition #assignNumber should throw FixedToPatternError when trying to change fixed[x] to pattern[x]")
    void t10() {
      ElementDefinition riskEstimateValue = el(riskEvidenceSynthesis, "RiskEvidenceSynthesis.riskEstimate.value");
      riskEstimateValue.assignValue(1.23, true);
      assertEquals(1.23, at(riskEstimateValue, "fixedDecimal"));
      assertThrowsContaining("Cannot assign this element using a pattern; as it is already assigned in the StructureDefinition using fixedDecimal.", () -> { riskEstimateValue.assignValue(1.23); });
    }
    // assigning an integer

    @Test
    @DisplayName("ElementDefinition #assignNumber should assign an integer to an integer")
    void t11() {
      ElementDefinition riskEstimateValueNumeratorCount = el(riskEvidenceSynthesis, "RiskEvidenceSynthesis.riskEstimate.numeratorCount");
      riskEstimateValueNumeratorCount.assignValue(java.math.BigInteger.valueOf(123));
      assertEquals(123.0, at(riskEstimateValueNumeratorCount, "patternInteger"));
      assertNull(at(riskEstimateValueNumeratorCount, "fixedInteger"));
    }

    @Test
    @DisplayName("ElementDefinition #assignNumber should assign an integer to an integer (exactly)")
    void t12() {
      ElementDefinition riskEstimateValueNumeratorCount = el(riskEvidenceSynthesis, "RiskEvidenceSynthesis.riskEstimate.numeratorCount");
      riskEstimateValueNumeratorCount.assignValue(java.math.BigInteger.valueOf(123), true);
      assertEquals(123.0, at(riskEstimateValueNumeratorCount, "fixedInteger"));
      assertNull(at(riskEstimateValueNumeratorCount, "patternInteger"));
    }

    @Test
    @DisplayName("ElementDefinition #assignNumber should throw ValueAlreadyAssignedError when assigning an already assigned integer by pattern[x]")
    void t13() {
      ElementDefinition riskEstimateValueNumeratorCount = el(riskEvidenceSynthesis, "RiskEvidenceSynthesis.riskEstimate.numeratorCount");
      riskEstimateValueNumeratorCount.assignValue(java.math.BigInteger.valueOf(123));
      assertEquals(123.0, at(riskEstimateValueNumeratorCount, "patternInteger"));
      assertThrowsContaining("Cannot assign 124 to this element; a different integer is already assigned: 123.", () -> { riskEstimateValueNumeratorCount.assignValue(java.math.BigInteger.valueOf(124)); });
      assertThrowsContaining("Cannot assign 124 to this element; a different integer is already assigned: 123.", () -> { riskEstimateValueNumeratorCount.assignValue(java.math.BigInteger.valueOf(124), true); });
    }

    @Test
    @DisplayName("ElementDefinition #assignNumber should throw ValueAlreadyAssignedError when assigning an already assigned integer by fixed[x]")
    void t14() {
      ElementDefinition riskEstimateValueNumeratorCount = el(riskEvidenceSynthesis, "RiskEvidenceSynthesis.riskEstimate.numeratorCount");
      riskEstimateValueNumeratorCount.assignValue(java.math.BigInteger.valueOf(123), true);
      assertEquals(123.0, at(riskEstimateValueNumeratorCount, "fixedInteger"));
      assertThrowsContaining("Cannot assign 124 to this element; a different integer is already assigned: 123.", () -> { riskEstimateValueNumeratorCount.assignValue(java.math.BigInteger.valueOf(124), true); });
    }

    @Test
    @DisplayName("ElementDefinition #assignNumber should throw FixedToPatternError when trying to change fixed[x] to pattern[x]")
    void t15() {
      ElementDefinition riskEstimateValueNumeratorCount = el(riskEvidenceSynthesis, "RiskEvidenceSynthesis.riskEstimate.numeratorCount");
      riskEstimateValueNumeratorCount.assignValue(java.math.BigInteger.valueOf(123), true);
      assertEquals(123.0, at(riskEstimateValueNumeratorCount, "fixedInteger"));
      assertThrowsContaining("Cannot assign this element using a pattern; as it is already assigned in the StructureDefinition using fixedInteger.", () -> { riskEstimateValueNumeratorCount.assignValue(java.math.BigInteger.valueOf(123)); });
    }

    @Test
    @DisplayName("ElementDefinition #assignNumber should throw MismatchedTypeError when assigning a decimal to an integer value")
    void t16() {
      ElementDefinition riskEstimateValueNumeratorCount = el(riskEvidenceSynthesis, "RiskEvidenceSynthesis.riskEstimate.numeratorCount");
      assertThrowsContaining("Cannot assign number value: 12.3. Value does not match element type: integer", () -> { riskEstimateValueNumeratorCount.assignValue(12.3); });
      assertThrowsContaining("Cannot assign number value: 12.3. Value does not match element type: integer", () -> { riskEstimateValueNumeratorCount.assignValue(12.3, true); });
    }
    // assigning an unsignedInt

    @Test
    @DisplayName("ElementDefinition #assignNumber should assign zero to an unsigned integer")
    void t17() {
      ElementDefinition reliableCache = el(capabilityStatement, "CapabilityStatement.messaging.reliableCache");
      reliableCache.assignValue(java.math.BigInteger.valueOf(0));
      assertEquals(0.0, at(reliableCache, "patternUnsignedInt"));
      assertNull(at(reliableCache, "fixedUnsignedInt"));
    }

    @Test
    @DisplayName("ElementDefinition #assignNumber should assign zero to an unsigned integer (exactly)")
    void t18() {
      ElementDefinition reliableCache = el(capabilityStatement, "CapabilityStatement.messaging.reliableCache");
      reliableCache.assignValue(java.math.BigInteger.valueOf(0), true);
      assertEquals(0.0, at(reliableCache, "fixedUnsignedInt"));
      assertNull(at(reliableCache, "patternUnsignedInt"));
    }

    @Test
    @DisplayName("ElementDefinition #assignNumber should throw MismatchedTypeError when assigning a decimal to an unsignedInt")
    void t19() {
      ElementDefinition reliableCache = el(capabilityStatement, "CapabilityStatement.messaging.reliableCache");
      assertThrowsContaining("Cannot assign number value: 2.4. Value does not match element type: unsignedInt", () -> { reliableCache.assignValue(2.4); });
      assertThrowsContaining("Cannot assign number value: 2.4. Value does not match element type: unsignedInt", () -> { reliableCache.assignValue(2.4, true); });
    }

    @Test
    @DisplayName("ElementDefinition #assignNumber should throw MismatchedTypeError when assigning a negative integer to an unsignedInt")
    void t20() {
      ElementDefinition reliableCache = el(capabilityStatement, "CapabilityStatement.messaging.reliableCache");
      assertThrowsContaining("Cannot assign number value: -24. Value does not match element type: unsignedInt", () -> { reliableCache.assignValue(java.math.BigInteger.valueOf(-24)); });
      assertThrowsContaining("Cannot assign number value: -24. Value does not match element type: unsignedInt", () -> { reliableCache.assignValue(java.math.BigInteger.valueOf(-24), true); });
    }

    @Test
    @DisplayName("ElementDefinition #assignNumber should throw ValueAlreadyAssignedError when assigning an already assigned unsignedInt by pattern[x]")
    void t21() {
      ElementDefinition reliableCache = el(capabilityStatement, "CapabilityStatement.messaging.reliableCache");
      reliableCache.assignValue(java.math.BigInteger.valueOf(12));
      assertEquals(12.0, at(reliableCache, "patternUnsignedInt"));
      assertThrowsContaining("Cannot assign 34 to this element; a different unsignedInt is already assigned: 12.", () -> { reliableCache.assignValue(java.math.BigInteger.valueOf(34)); });
      assertThrowsContaining("Cannot assign 34 to this element; a different unsignedInt is already assigned: 12.", () -> { reliableCache.assignValue(java.math.BigInteger.valueOf(34), true); });
    }

    @Test
    @DisplayName("ElementDefinition #assignNumber should throw ValueAlreadyAssignedError when assigning an already assigned unsignedInt by fixed[x]")
    void t22() {
      ElementDefinition reliableCache = el(capabilityStatement, "CapabilityStatement.messaging.reliableCache");
      reliableCache.assignValue(java.math.BigInteger.valueOf(12), true);
      assertEquals(12.0, at(reliableCache, "fixedUnsignedInt"));
      assertThrowsContaining("Cannot assign 34 to this element; a different unsignedInt is already assigned: 12.", () -> { reliableCache.assignValue(java.math.BigInteger.valueOf(34), true); });
    }

    @Test
    @DisplayName("ElementDefinition #assignNumber should throw ValueAlreadyAssignedError when assigning an already assigned unsignedInt by fixed[x]")
    void t23() {
      ElementDefinition reliableCache = el(capabilityStatement, "CapabilityStatement.messaging.reliableCache");
      reliableCache.assignValue(java.math.BigInteger.valueOf(12), true);
      assertEquals(12.0, at(reliableCache, "fixedUnsignedInt"));
      assertThrowsContaining("Cannot assign 34 to this element; a different unsignedInt is already assigned: 12.", () -> { reliableCache.assignValue(java.math.BigInteger.valueOf(34), true); });
    }

    @Test
    @DisplayName("ElementDefinition #assignNumber should throw FixedToPatternError when trying to change fixed[x] to pattern[x]")
    void t24() {
      ElementDefinition reliableCache = el(capabilityStatement, "CapabilityStatement.messaging.reliableCache");
      reliableCache.assignValue(java.math.BigInteger.valueOf(12), true);
      assertEquals(12.0, at(reliableCache, "fixedUnsignedInt"));
      assertThrowsContaining("Cannot assign this element using a pattern; as it is already assigned in the StructureDefinition using fixedUnsignedInt.", () -> { reliableCache.assignValue(java.math.BigInteger.valueOf(12)); });
    }
    // assigning a positiveInt

    @Test
    @DisplayName("ElementDefinition #assignNumber should assign a positive integer to positiveInt")
    void t25() {
      ElementDefinition minutesDuration = el(appointment, "Appointment.minutesDuration");
      minutesDuration.assignValue(java.math.BigInteger.valueOf(12));
      assertEquals(12.0, at(minutesDuration, "patternPositiveInt"));
      assertNull(at(minutesDuration, "fixedPositiveInt"));
    }

    @Test
    @DisplayName("ElementDefinition #assignNumber should assign a positive integer to positiveInt (exactly)")
    void t26() {
      ElementDefinition minutesDuration = el(appointment, "Appointment.minutesDuration");
      minutesDuration.assignValue(java.math.BigInteger.valueOf(12), true);
      assertEquals(12.0, at(minutesDuration, "fixedPositiveInt"));
      assertNull(at(minutesDuration, "patternPositiveInt"));
    }

    @Test
    @DisplayName("ElementDefinition #assignNumber should throw MismatchedTypeError when assigning a decimal to a positiveInt")
    void t27() {
      ElementDefinition minutesDuration = el(appointment, "Appointment.minutesDuration");
      assertThrowsContaining("Cannot assign number value: 1.2. Value does not match element type: positiveInt", () -> { minutesDuration.assignValue(1.2); });
      assertThrowsContaining("Cannot assign number value: 1.2. Value does not match element type: positiveInt", () -> { minutesDuration.assignValue(1.2, true); });
    }

    @Test
    @DisplayName("ElementDefinition #assignNumber should throw MismatchedTypeError when assigning zero to a positiveInt")
    void t28() {
      ElementDefinition minutesDuration = el(appointment, "Appointment.minutesDuration");
      assertThrowsContaining("Cannot assign number value: 0. Value does not match element type: positiveInt", () -> { minutesDuration.assignValue(java.math.BigInteger.valueOf(0)); });
      assertThrowsContaining("Cannot assign number value: 0. Value does not match element type: positiveInt", () -> { minutesDuration.assignValue(java.math.BigInteger.valueOf(0), true); });
    }

    @Test
    @DisplayName("ElementDefinition #assignNumber should throw MismatchedTypeError when assigning a negative to a positiveInt")
    void t29() {
      ElementDefinition minutesDuration = el(appointment, "Appointment.minutesDuration");
      assertThrowsContaining("Cannot assign number value: -12. Value does not match element type: positiveInt", () -> { minutesDuration.assignValue(java.math.BigInteger.valueOf(-12)); });
      assertThrowsContaining("Cannot assign number value: -12. Value does not match element type: positiveInt", () -> { minutesDuration.assignValue(java.math.BigInteger.valueOf(-12), true); });
    }

    @Test
    @DisplayName("ElementDefinition #assignNumber should throw ValueAlreadyAssignedError when assigning an already assigned positiveInt by pattern[x]")
    void t30() {
      ElementDefinition minutesDuration = el(appointment, "Appointment.minutesDuration");
      minutesDuration.assignValue(java.math.BigInteger.valueOf(12));
      assertJsEqual(12.0, at(minutesDuration, "patternPositiveInt"));
      assertThrowsContaining("Cannot assign 34 to this element; a different positiveInt is already assigned: 12.", () -> { minutesDuration.assignValue(java.math.BigInteger.valueOf(34)); });
      assertThrowsContaining("Cannot assign 34 to this element; a different positiveInt is already assigned: 12.", () -> { minutesDuration.assignValue(java.math.BigInteger.valueOf(34), true); });
    }

    @Test
    @DisplayName("ElementDefinition #assignNumber should throw ValueAlreadyAssignedError when assigning an already assigned positiveInt by fixed[x]")
    void t31() {
      ElementDefinition minutesDuration = el(appointment, "Appointment.minutesDuration");
      minutesDuration.assignValue(java.math.BigInteger.valueOf(12), true);
      assertJsEqual(12.0, at(minutesDuration, "fixedPositiveInt"));
      assertThrowsContaining("Cannot assign 34 to this element; a different positiveInt is already assigned: 12.", () -> { minutesDuration.assignValue(java.math.BigInteger.valueOf(34), true); });
    }

    @Test
    @DisplayName("ElementDefinition #assignNumber should throw FixedToPatternError when trying to change fixed[x] to pattern[x]")
    void t32() {
      ElementDefinition minutesDuration = el(appointment, "Appointment.minutesDuration");
      minutesDuration.assignValue(java.math.BigInteger.valueOf(12), true);
      assertJsEqual(12.0, at(minutesDuration, "fixedPositiveInt"));
      assertThrowsContaining("Cannot assign this element using a pattern; as it is already assigned in the StructureDefinition using fixedPositiveInt.", () -> { minutesDuration.assignValue(java.math.BigInteger.valueOf(12)); });
    }
    // assigning a non-numerical value

    @Test
    @DisplayName("ElementDefinition #assignNumber should throw MismatchedTypeError when assigning an integer to a non-numerical value")
    void t33() {
      ElementDefinition status = el(observation, "Observation.status");
      assertThrowsContaining("Cannot assign number value: 123. Value does not match element type: code", () -> { status.assignValue(java.math.BigInteger.valueOf(123)); });
      assertThrowsContaining("Cannot assign number value: 123. Value does not match element type: code", () -> { status.assignValue(java.math.BigInteger.valueOf(123), true); });
    }

    @Test
    @DisplayName("ElementDefinition #assignNumber should throw NoSingleTypeError when element has multiple types")
    void t34() {
      ElementDefinition valueX = el(observation, "Observation.value[x]");
      assertThrowsContaining("Cannot assign number value on this element since this element does not have a single type", () -> { valueX.assignValue(java.math.BigInteger.valueOf(123)); });
      assertThrowsContaining("Cannot assign number value on this element since this element does not have a single type", () -> { valueX.assignValue(java.math.BigInteger.valueOf(123), true); });
      assertNull(at(valueX, "patternInteger"));
      assertNull(at(valueX, "fixedInteger"));
    }
  }

  @Nested
  @DisplayName("#integer64")
  class Integer64 {
    ElementDefinition valueX;
    ElementDefinition valueInteger64;
    @BeforeEach
    void beforeEach36() throws java.io.IOException {
      if (valueX == null) {
        StructureDefinition r5Extension = StructureDefinition.fromJSON(org.hl7.fhir.common.js.Json.parse(
            java.nio.file.Files.readString(java.nio.file.Path.of(org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions
                .testDefsPath("r5-definitions", "package", "StructureDefinition-Extension.json")))));
        valueX = el(r5Extension, "Extension.value[x]");
      }
      valueInteger64 = valueX.clone(false);
      valueInteger64.getType().removeIf(t -> !"integer64".equals(t.getCode()));
    }
    // assigning an integer64
    // NOTE: Tests of assigning an integer64 as a string are in ElementDefinition.assignString.test.ts

    @Test
    @DisplayName("ElementDefinition #integer64 should assign an integer to an integer64")
    void t37() {
      valueInteger64.assignValue(java.math.BigInteger.valueOf(123));
      assertEquals("123", at(valueInteger64, "patternInteger64"));
      assertNull(at(valueInteger64, "fixedInteger64"));
    }

    @Test
    @DisplayName("ElementDefinition #integer64 should assign an integer to an integer64 (exactly)")
    void t38() {
      valueInteger64.assignValue(java.math.BigInteger.valueOf(123), true);
      assertNull(at(valueInteger64, "patternInteger64"));
      assertEquals("123", at(valueInteger64, "fixedInteger64"));
    }

    @Test
    @DisplayName("ElementDefinition #integer64 should assign a large integer to an integer64 without losing precision")
    void t39() {
      valueInteger64.assignValue(new java.math.BigInteger("12345678901234567890"));
      assertEquals("12345678901234567890", at(valueInteger64, "patternInteger64"));
      assertNull(at(valueInteger64, "fixedInteger64"));
    }

    @Test
    @DisplayName("ElementDefinition #integer64 should throw ValueAlreadyAssignedError when assigning an already assigned integer by pattern[x]")
    void t40() {
      valueInteger64.assignValue(java.math.BigInteger.valueOf(123));
      assertEquals("123", at(valueInteger64, "patternInteger64"));
      assertThrowsContaining("Cannot assign 124 to this element; a different integer64 is already assigned: \"123\".", () -> { valueInteger64.assignValue(java.math.BigInteger.valueOf(124)); });
      assertThrowsContaining("Cannot assign 124 to this element; a different integer64 is already assigned: \"123\".", () -> { valueInteger64.assignValue(java.math.BigInteger.valueOf(124), true); });
    }

    @Test
    @DisplayName("ElementDefinition #integer64 should throw ValueAlreadyAssignedError when assigning an already assigned integer by fixed[x]")
    void t41() {
      valueInteger64.assignValue(java.math.BigInteger.valueOf(123), true);
      assertEquals("123", at(valueInteger64, "fixedInteger64"));
      assertThrowsContaining("Cannot assign 124 to this element; a different integer64 is already assigned: \"123\".", () -> { valueInteger64.assignValue(java.math.BigInteger.valueOf(124), true); });
    }

    @Test
    @DisplayName("ElementDefinition #integer64 should throw FixedToPatternError when trying to change fixed[x] to pattern[x]")
    void t42() {
      valueInteger64.assignValue(java.math.BigInteger.valueOf(123), true);
      assertEquals("123", at(valueInteger64, "fixedInteger64"));
      assertThrowsContaining("Cannot assign this element using a pattern; as it is already assigned in the StructureDefinition using fixedInteger64.", () -> { valueInteger64.assignValue(java.math.BigInteger.valueOf(123)); });
    }

    @Test
    @DisplayName("ElementDefinition #integer64 should throw MismatchedTypeError when assigning a decimal to an integer64 value")
    void t43() {
      assertThrowsContaining("Cannot assign number value: 12.3. Value does not match element type: integer64", () -> { valueInteger64.assignValue(12.3); });
      assertThrowsContaining("Cannot assign number value: 12.3. Value does not match element type: integer64", () -> { valueInteger64.assignValue(12.3, true); });
    }
  }
}
