package org.hl7.fhir.sushi.fhirtypes;

import static org.hl7.fhir.sushi.fhirtypes.EdTestSupport.list;
import static org.hl7.fhir.sushi.fhirtypes.EdTestSupport.obj;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.errors.ValidationError;
import org.hl7.fhir.sushi.fhirdefs.FHIRDefinitions;
import org.hl7.fhir.sushi.testhelpers.TestFisher;
import org.hl7.fhir.sushi.utils.FishableType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// port of sushi/test/fhirtypes/ElementDefinition.test.ts (SUSHI 3.21.1), describe 'ElementDefinition R5'
@DisplayName("ElementDefinition R5")
class ElementDefinitionR5Test {
  static FHIRDefinitions defs;
  static Map<String, Object> jsonObservation;
  static Map<String, Object> jsonValueX;
  static TestFisher fisher;
  StructureDefinition observation;
  ElementDefinition valueX;

  @SuppressWarnings("unchecked")
  @BeforeAll
  static void beforeAll() {
    defs = EdTestSupport.r5Defs();
    fisher = new TestFisher().withFHIR(defs);
    // resolve observation once to ensure it is present in defs
    fisher.fishForStructureDefinition("Observation");
    jsonObservation = (Map<String, Object>) defs.fishForFHIR("Observation", FishableType.Resource);
    jsonValueX = ElementDefinitionTest.snapshotElement(jsonObservation, 21);
  }

  @BeforeEach
  void beforeEach() {
    observation = StructureDefinition.fromJSON(jsonObservation);
    valueX = ElementDefinition.fromJSON(jsonValueX);
    valueX.structDef = observation;
  }

  @Test
  @DisplayName("ElementDefinition R5 should be valid when an element has a position discriminator, ordered is true, and the FHIR version is R5 is newer")
  void positionOrdered() {
    ElementDefinition clone = valueX.clone(false);
    clone.setSlicing(obj("rules", "open", "ordered", true, "discriminator",
        list(obj("path", "$this", "type", "position"))));
    List<ValidationError> validationErrors = clone.validate();
    assertEquals(0, validationErrors.size());
  }

  @Test
  @DisplayName("ElementDefinition R5 should be invalid when an element has a position discriminator, ordered is not true, and the FHIR version is R5 or newer")
  void positionNotOrdered() {
    ElementDefinition clone = valueX.clone(false);
    clone.structDef.setFhirVersion("5.0.0");
    clone.setSlicing(obj("rules", "open", "discriminator", list(obj("path", "$this", "type", "position"))));
    List<ValidationError> validationErrors = clone.validate();
    assertEquals(1, validationErrors.size());
    assertTrue(Pattern.compile("slicing\\.ordered: Slicing ordering must be true when a position discriminator is used")
        .matcher(validationErrors.get(0).getMessage()).find());
  }
}
