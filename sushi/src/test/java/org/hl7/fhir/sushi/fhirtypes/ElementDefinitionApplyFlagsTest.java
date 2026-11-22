package org.hl7.fhir.sushi.fhirtypes;

import static org.hl7.fhir.sushi.fhirtypes.EdTestSupport.el;
import static org.hl7.fhir.sushi.fhirtypes.EdTestSupport.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.hl7.fhir.sushi.errors.InvalidMustSupportError;
import org.hl7.fhir.sushi.errors.MultipleStandardsStatusError;
import org.hl7.fhir.common.js.Js;
import org.hl7.fhir.sushi.testhelpers.TestFisher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// port of sushi/test/fhirtypes/ElementDefinition.applyFlags.test.ts (SUSHI 3.21.1)
@DisplayName("ElementDefinition")
class ElementDefinitionApplyFlagsTest {
  static TestFisher fisher;
  StructureDefinition observation;
  StructureDefinition obsResource;

  @BeforeAll
  static void beforeAll() {
    fisher = new TestFisher().withFHIR(EdTestSupport.r4Defs());
  }

  @BeforeEach
  void beforeEach() {
    observation = fisher.fishForStructureDefinition("us-core-observation-lab");
    obsResource = fisher.fishForStructureDefinition("Observation");
  }

  static Object status(String code) {
    return json("{\"url\": \"http://hl7.org/fhir/StructureDefinition/structuredefinition-standards-status\", "
        + "\"valueCode\": \"" + code + "\"}");
  }

  /** Jest toContainEqual. */
  static boolean containsEqual(List<Object> l, Object item) {
    if (l == null) return false;
    for (Object o : l) if (Js.isEqual(o, item)) return true;
    return false;
  }

  @Nested
  @DisplayName("#applyFlags()")
  class ApplyFlags {
    @Test
    @DisplayName("ElementDefinition #applyFlags() should allow all the flags to change from falsy to true")
    void allFlags() {
      ElementDefinition note = el(observation, "Observation.note");
      assertFalse(Js.truthy(note.getIsSummary()));
      assertFalse(Js.truthy(note.getIsModifier()));
      assertFalse(Js.truthy(note.getMustSupport()));
      note.applyFlags(true, true, true, null, null, null);
      assertEquals(true, note.getIsSummary());
      assertEquals(true, note.getIsModifier());
      assertEquals(true, note.getMustSupport());
    }

    @Test
    @DisplayName("ElementDefinition #applyFlags() should not change a flag that gets false as its argument")
    void falseArg() {
      ElementDefinition status = el(observation, "Observation.status");
      assertEquals(true, status.getIsModifier());
      status.applyFlags(null, null, false, null, null, null);
      assertEquals(true, status.getIsModifier());
    }

    @Test
    @DisplayName("ElementDefinition #applyFlags() should not change a flag that gets undefined as its argument")
    void undefinedArg() {
      ElementDefinition status = el(observation, "Observation.status");
      assertEquals(true, status.getIsSummary());
      assertEquals(true, status.getIsModifier());
      status.applyFlags(null, null, null, null, null, null);
      assertEquals(true, status.getIsSummary());
      assertEquals(true, status.getIsModifier());
    }

    @Test
    @DisplayName("ElementDefinition #applyFlags() should add a standards status of trial use to an element with no standards status")
    void trialUse() {
      ElementDefinition note = el(observation, "Observation.note");
      assertNull(note.getExtension());
      note.applyFlags(null, null, null, true, null, null);
      assertTrue(containsEqual(note.getExtension(), status("trial-use")));
    }

    @Test
    @DisplayName("ElementDefinition #applyFlags() should add a standards status of normative to an element with no standards status")
    void normative() {
      ElementDefinition note = el(observation, "Observation.note");
      assertNull(note.getExtension());
      note.applyFlags(null, null, null, null, true, null);
      assertTrue(containsEqual(note.getExtension(), status("normative")));
    }

    @Test
    @DisplayName("ElementDefinition #applyFlags() should add a standards status of draft to an element with no standards status")
    void draft() {
      ElementDefinition note = el(observation, "Observation.note");
      assertNull(note.getExtension());
      note.applyFlags(null, null, null, null, null, true);
      assertTrue(containsEqual(note.getExtension(), status("draft")));
    }

    @Test
    @DisplayName("ElementDefinition #applyFlags() should change the standards status when applying a standards status to an element that already has a standards status")
    void changeStatus() {
      ElementDefinition focus = el(observation, "Observation.focus");
      assertTrue(containsEqual(focus.getExtension(), status("trial-use")));
      focus.applyFlags(null, null, null, null, true, null);
      assertFalse(containsEqual(focus.getExtension(), status("trial-use")));
      assertTrue(containsEqual(focus.getExtension(), status("normative")));
    }

    @Test
    @DisplayName("ElementDefinition #applyFlags() should throw an error when applying more than one standards status to an element")
    void multipleStatus() {
      ElementDefinition note = el(observation, "Observation.note");
      assertNull(note.getExtension());
      assertThrows(MultipleStandardsStatusError.class, () -> note.applyFlags(null, null, null, null, true, true));
      assertNull(note.getExtension());
    }

    @Test
    @DisplayName("ElementDefinition #applyFlags() should throw an error when applying mustSupport to an element in a resource")
    void mustSupportInResource() {
      ElementDefinition note = el(obsResource, "Observation.note");
      assertThrows(InvalidMustSupportError.class, () -> note.applyFlags(true, null, null, null, null, null));
    }

    @Test
    @DisplayName("ElementDefinition #applyFlags() should set the mustSupport flag on connected elements")
    void connected() {
      // MS only gets applied to connected elements that are not themselves slices
      // first, let's slice component
      ElementDefinition component = observation.findElement("Observation.component");
      component.sliceIt("pattern", "interpretation");
      component.addSlice("Lab");
      // then apply MS to Observation.component.interpretation, which implies Observation.component:Lab.interpretation MS
      ElementDefinition componentInterpretation = observation.findElement("Observation.component.interpretation");
      componentInterpretation.applyFlags(true, null, null, null, null, null);
      ElementDefinition labInterpretation = observation.findElementByPath("component[Lab].interpretation", fisher);
      assertEquals(true, labInterpretation.getMustSupport());
    }

    @Test
    @DisplayName("ElementDefinition #applyFlags() should not set the mustSupport flag on an element with the same path as the rule, but where the rule path ends with a slice and the other element does not")
    void notOnUnslicedSamePath() {
      // first, let's slice component
      ElementDefinition component = observation.findElement("Observation.component");
      component.sliceIt("pattern", "interpretation");
      component.addSlice("Lab");
      // then, let's slice component.extension
      ElementDefinition componentExtension = observation.findElement("Observation.component.extension");
      componentExtension.sliceIt("value", "url");
      componentExtension.addSlice("Sequel");
      // find the elements now to force unfolding, so they will exist when applying the flags
      ElementDefinition sequel = observation.findElementByPath("component.extension[Sequel]", fisher);
      ElementDefinition labSequel = observation.findElementByPath("component[Lab].extension[Sequel]", fisher);
      ElementDefinition labExtension = observation.findElementByPath("component[Lab].extension", fisher);
      sequel.applyFlags(true, null, null, null, null, null);
      // this should set mustSupport on component[Lab].extension[Sequel]
      assertEquals(true, labSequel.getMustSupport());
      // but it should _not_ set mustSupport on component[Lab].extension
      assertNull(labExtension.getMustSupport());
    }
  }
}
