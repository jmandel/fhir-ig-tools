package org.hl7.fhir.sushi.fhirtypes;

import static org.hl7.fhir.sushi.fhirtypes.EdTestSupport.assertSameState;
import static org.hl7.fhir.sushi.fhirtypes.EdTestSupport.assertThrowsContaining;
import static org.hl7.fhir.sushi.fhirtypes.EdTestSupport.el;
import static org.hl7.fhir.sushi.fhirtypes.EdTestSupport.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.hl7.fhir.sushi.testhelpers.TestFisher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// port of sushi/test/fhirtypes/ElementDefinition.assignBoolean.test.ts (SUSHI 3.21.1)
@DisplayName("ElementDefinition")
class ElementDefinitionAssignBooleanTest {
  static TestFisher fisher;
  StructureDefinition observation;
  StructureDefinition location;

  @BeforeAll
  static void beforeAll() {
    fisher = new TestFisher().withFHIR(EdTestSupport.r4Defs());
  }

  @BeforeEach
  void beforeEach() {
    observation = fisher.fishForStructureDefinition("Observation");
    location = fisher.fishForStructureDefinition("Location");
  }

  @Nested
  @DisplayName("#assignBoolean")
  class AssignBoolean {
    @Test
    @DisplayName("ElementDefinition #assignBoolean should assign a boolean to a boolean")
    void assign() {
      ElementDefinition hoursOfOperationAllDay = el(location, "Location.hoursOfOperation.allDay");
      hoursOfOperationAllDay.assignValue(true);
      assertEquals(true, hoursOfOperationAllDay.getProperty("patternBoolean"));
      assertNull(hoursOfOperationAllDay.getProperty("fixedBoolean"));
    }

    @Test
    @DisplayName("ElementDefinition #assignBoolean should assign a boolean to a boolean (exactly)")
    void assignExactly() {
      ElementDefinition hoursOfOperationAllDay = el(location, "Location.hoursOfOperation.allDay");
      hoursOfOperationAllDay.assignValue(true, true);
      assertEquals(true, hoursOfOperationAllDay.getProperty("fixedBoolean"));
      assertNull(hoursOfOperationAllDay.getProperty("patternBoolean"));
    }

    @Test
    @DisplayName("ElementDefinition #assignBoolean should throw NoSingleTypeError when element has multiple types")
    void multipleTypes() {
      ElementDefinition valueX = el(observation, "Observation.value[x]");
      assertThrowsContaining(
          "Cannot assign boolean value on this element since this element does not have a single type",
          () -> valueX.assignValue(true));
      assertThrowsContaining(
          "Cannot assign boolean value on this element since this element does not have a single type",
          () -> valueX.assignValue(true, true));
      assertNull(valueX.getProperty("patternBoolean"));
      assertNull(valueX.getProperty("fixedBoolean"));
    }

    @Test
    @DisplayName("ElementDefinition #assignBoolean should throw ValueAlreadyAssignedError when the value is assigned to a different value by pattern[x]")
    void alreadyByPattern() {
      ElementDefinition hoursOfOperationAllDay = el(location, "Location.hoursOfOperation.allDay");
      hoursOfOperationAllDay.assignValue(true);
      assertEquals(true, hoursOfOperationAllDay.getProperty("patternBoolean"));
      hoursOfOperationAllDay.assignValue(true);
      assertEquals(true, hoursOfOperationAllDay.getProperty("patternBoolean"));
      assertThrowsContaining("Cannot assign false to this element; a different boolean is already assigned: true.",
          () -> hoursOfOperationAllDay.assignValue(false));
      assertThrowsContaining("Cannot assign false to this element; a different boolean is already assigned: true.",
          () -> hoursOfOperationAllDay.assignValue(false, true));
    }

    @Test
    @DisplayName("ElementDefinition #assignBoolean should throw ValueAlreadyAssignedError when the value is assigned to a different value by fixed[x]")
    void alreadyByFixed() {
      ElementDefinition hoursOfOperationAllDay = el(location, "Location.hoursOfOperation.allDay");
      hoursOfOperationAllDay.assignValue(true, true);
      assertEquals(true, hoursOfOperationAllDay.getProperty("fixedBoolean"));
      hoursOfOperationAllDay.assignValue(true, true);
      assertEquals(true, hoursOfOperationAllDay.getProperty("fixedBoolean"));
      assertThrowsContaining("Cannot assign false to this element; a different boolean is already assigned: true.",
          () -> hoursOfOperationAllDay.assignValue(false, true));
    }

    @Test
    @DisplayName("ElementDefinition #assignBoolean should throw ValueAlreadyAssignedError when assigning a boolean to a different value set in a parent by pattern[x]")
    void parentPattern() {
      ElementDefinition code = el(observation, "Observation.code");
      code.setProperty("patternCodeableConcept", json("{\"coding\": [{\"userSelected\": false}]}"));
      code.unfold(fisher);
      ElementDefinition coding = el(observation, "Observation.code.coding");
      coding.unfold(fisher);
      ElementDefinition userSelected = el(observation, "Observation.code.coding.userSelected");
      ElementDefinition clone = userSelected.clone(false);
      assertThrowsContaining("Cannot assign true to this element; a different boolean is already assigned: false.",
          () -> userSelected.assignValue(true));
      assertThrowsContaining("Cannot assign true to this element; a different boolean is already assigned: false.",
          () -> userSelected.assignValue(true, true));
      assertSameState(clone, userSelected);
    }

    @Test
    @DisplayName("ElementDefinition #assignBoolean should throw ValueAlreadyAssignedError when assigning a boolean to a different value set in a parent by fixed[x]")
    void parentFixed() {
      ElementDefinition code = el(observation, "Observation.code");
      code.setProperty("fixedCodeableConcept", json("{\"coding\": [{\"userSelected\": false}]}"));
      code.unfold(fisher);
      ElementDefinition coding = el(observation, "Observation.code.coding");
      coding.unfold(fisher);
      ElementDefinition userSelected = el(observation, "Observation.code.coding.userSelected");
      ElementDefinition clone = userSelected.clone(false);
      assertThrowsContaining("Cannot assign true to this element; a different boolean is already assigned: false.",
          () -> userSelected.assignValue(true, true));
      assertSameState(clone, userSelected);
    }

    @Test
    @DisplayName("ElementDefinition #assignBoolean should throw AssignedToPatternError when trying to change fixed[x] to pattern[x]")
    void fixedToPattern() {
      ElementDefinition hoursOfOperationAllDay = el(location, "Location.hoursOfOperation.allDay");
      hoursOfOperationAllDay.assignValue(true, true);
      assertEquals(true, hoursOfOperationAllDay.getProperty("fixedBoolean"));
      assertThrowsContaining(
          "Cannot assign this element using a pattern; as it is already assigned in the StructureDefinition using fixedBoolean.",
          () -> hoursOfOperationAllDay.assignValue(true));
    }

    @Test
    @DisplayName("ElementDefinition #assignBoolean should throw MismatchedTypeError when the value is assigned to a non-boolean")
    void nonBoolean() {
      ElementDefinition status = el(location, "Location.status");
      assertThrowsContaining("Cannot assign boolean value: true. Value does not match element type: code",
          () -> status.assignValue(true));
      assertThrowsContaining("Cannot assign boolean value: true. Value does not match element type: code",
          () -> status.assignValue(true, true));
    }
  }
}
