package org.hl7.fhir.sushi.fhirtypes;

import static org.hl7.fhir.sushi.fhirtypes.EdTestSupport.assertThrowsContaining;
import static org.hl7.fhir.sushi.fhirtypes.EdTestSupport.el;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.hl7.fhir.sushi.testhelpers.TestFisher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// port of sushi/test/fhirtypes/ElementDefinition.checkAssignInlineInstance.test.ts (SUSHI 3.21.1)
@DisplayName("ElementDefinition")
class ElementDefinitionCheckAssignInlineInstanceTest {
  static TestFisher fisher;
  StructureDefinition observation;
  InstanceDefinition inlineInstance;
  InstanceDefinition inlineCodeable;

  @BeforeAll
  static void beforeAll() {
    fisher = new TestFisher().withFHIR(EdTestSupport.r4Defs());
  }

  @BeforeEach
  void beforeEach() {
    observation = fisher.fishForStructureDefinition("Observation");
    inlineInstance = new InstanceDefinition();
    inlineInstance.setResourceType("Patient");
    inlineInstance.setId("MyInlineInstance");
    inlineInstance._instanceMeta.sdType = "Patient";

    inlineCodeable = new InstanceDefinition();
    inlineCodeable.setResourceType("CodeableConcept");
    inlineCodeable.setId("MyCodeable");
    inlineCodeable._instanceMeta.sdType = "CodeableConcept";
  }

  @Nested
  @DisplayName("#checkAssignInlineInstance")
  class CheckAssignInlineInstance {
    @Test
    @DisplayName("ElementDefinition #checkAssignInlineInstance should return a resource when it can be assigned")
    void assignable() {
      ElementDefinition contained = el(observation, "Observation.contained");
      InstanceDefinition value = contained.checkAssignInlineInstance(inlineInstance, fisher);
      assertEquals("Patient", value.getResourceType());
    }

    @Test
    @DisplayName("ElementDefinition #checkAssignInlineInstance should throw MismatchedTypeError when a Resource is assigned on a non-Resource element")
    void mismatched() {
      ElementDefinition status = el(observation, "Observation.status");
      assertThrowsContaining(
          "Cannot assign Patient value: MyInlineInstance. Value does not match element type: code",
          () -> status.checkAssignInlineInstance(inlineInstance, fisher));
    }
  }
}
