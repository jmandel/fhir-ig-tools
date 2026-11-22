package org.hl7.fhir.sushi.fhirtypes;

import static org.hl7.fhir.sushi.fhirtypes.EdTestSupport.assertThrowsContaining;
import static org.hl7.fhir.sushi.fhirtypes.EdTestSupport.el;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.hl7.fhir.sushi.fshtypes.FshCode;
import org.hl7.fhir.sushi.testhelpers.TestFisher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// port of sushi/test/fhirtypes/ElementDefinition.applyMapping.test.ts (SUSHI 3.21.1)
@DisplayName("ElementDefinition")
class ElementDefinitionApplyMappingTest {
  static TestFisher fisher;
  StructureDefinition observation;

  @BeforeAll
  static void beforeAll() {
    fisher = new TestFisher().withFHIR(EdTestSupport.r4Defs());
  }

  @BeforeEach
  void beforeEach() {
    observation = fisher.fishForStructureDefinition("Observation");
  }

  static Map<?, ?> last(ElementDefinition e) {
    return (Map<?, ?>) e.getMapping().get(e.getMapping().size() - 1);
  }

  @Nested
  @DisplayName("#applyMapping")
  class ApplyMapping {
    @Test
    @DisplayName("ElementDefinition #applyMapping should apply a simple mapping")
    void simple() {
      ElementDefinition status = el(observation, "Observation.status");
      int originalLength = status.getMapping().size();
      status.applyMapping("myId", "myMap", "myComment", new FshCode("myLanguageCode"));
      assertEquals(originalLength + 1, status.getMapping().size());
      Map<?, ?> mapping = last(status);
      assertEquals("myId", mapping.get("identity"));
      assertEquals("myMap", mapping.get("map"));
      assertEquals("myComment", mapping.get("comment"));
      assertEquals("myLanguageCode", mapping.get("language"));
    }

    @Test
    @DisplayName("ElementDefinition #applyMapping should apply a mapping on an element which does not have one")
    void noExisting() {
      ElementDefinition id = el(observation, "Observation.id");
      assertNull(id.getMapping());
      id.applyMapping("myId", "myMap", "myComment", new FshCode("myLanguageCode"));
      assertEquals(1, id.getMapping().size());
      Map<?, ?> mapping = (Map<?, ?>) id.getMapping().get(0);
      assertEquals("myId", mapping.get("identity"));
      assertEquals("myMap", mapping.get("map"));
      assertEquals("myComment", mapping.get("comment"));
      assertEquals("myLanguageCode", mapping.get("language"));
    }

    @Test
    @DisplayName("ElementDefinition #applyMapping should apply a mapping without a comment and language")
    void noCommentLanguage() {
      ElementDefinition status = el(observation, "Observation.status");
      int originalLength = status.getMapping().size();
      status.applyMapping("myId", "myMap", null, null);
      assertEquals(originalLength + 1, status.getMapping().size());
      Map<?, ?> mapping = last(status);
      assertEquals("myId", mapping.get("identity"));
      assertEquals("myMap", mapping.get("map"));
      assertNull(mapping.get("comment"));
      assertNull(mapping.get("language"));
    }

    @Test
    @DisplayName("ElementDefinition #applyMapping should throw InvalidMappingError when applying a mapping with no identity")
    void noIdentity() {
      ElementDefinition status = el(observation, "Observation.status");
      int originalLength = status.getMapping().size();
      assertThrowsContaining("Invalid mapping, mapping.identity and mapping.map are 1..1 and must be set.",
          () -> status.applyMapping(null, "myMap", "myComment", new FshCode("myLanguageCode")));
      assertEquals(originalLength, status.getMapping().size());
    }

    @Test
    @DisplayName("ElementDefinition #applyMapping should throw InvalidFHIRIdError when applying a mapping with an invalid id")
    void invalidId() {
      ElementDefinition status = el(observation, "Observation.status");
      int originalLength = status.getMapping().size();
      assertThrowsContaining("The string \"invalid!\" does not represent a valid FHIR id.",
          () -> status.applyMapping("invalid!", "myMap", "myComment", new FshCode("myLanguageCode")));
      assertEquals(originalLength, status.getMapping().size());
    }
  }
}
