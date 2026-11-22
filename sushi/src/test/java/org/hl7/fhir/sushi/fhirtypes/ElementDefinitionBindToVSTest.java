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

// port of sushi/test/fhirtypes/ElementDefinition.bindToVS.test.ts (SUSHI 3.21.1)
@DisplayName("ElementDefinition")
class ElementDefinitionBindToVSTest {
  static FHIRDefinitions defs;
  StructureDefinition observation;
  static TestFisher fisher;
  @BeforeAll
  static void beforeAll0() {
    defs = r4Defs();
    fisher = new TestFisher().withFHIR(defs);
  }
  @BeforeEach
  void beforeEach1() {
    observation = fisher.fishForStructureDefinition("Observation");
  }

  @Nested
  @DisplayName("#bindToVS()")
  class BindToVS {

    @Test
    @DisplayName("ElementDefinition #bindToVS() should bind a value set on a CodeableConcept")
    void t2() {
      ElementDefinition concept = el(observation, "Observation.code");
      concept.bindToVS("http://myvaluesets.org/myvs", "required");
      assertEquals("http://myvaluesets.org/myvs", at(concept, "binding.valueSet"));
      assertEquals("required", at(concept, "binding.strength"));
    }

    @Test
    @DisplayName("ElementDefinition #bindToVS() should bind a value set on a Coding")
    void t3() {
      ElementDefinition concept = el(observation, "Observation.code");
      concept.unfold(fisher);
      ElementDefinition coding = el(observation, "Observation.code.coding");
      coding.bindToVS("http://myvaluesets.org/myvs", "required");
      assertEquals("http://myvaluesets.org/myvs", at(coding, "binding.valueSet"));
      assertEquals("required", at(coding, "binding.strength"));
    }

    @Test
    @DisplayName("ElementDefinition #bindToVS() should bind a value set on a code")
    void t4() {
      ElementDefinition code = el(observation, "Observation.status");
      code.bindToVS("http://myvaluesets.org/myvs", "required");
      assertEquals("http://myvaluesets.org/myvs", at(code, "binding.valueSet"));
      assertEquals("required", at(code, "binding.strength"));
    }

    @Test
    @DisplayName("ElementDefinition #bindToVS() should bind a value set on a Quantity")
    void t5() {
      ElementDefinition quantity = el(observation, "Observation.referenceRange.low");
      quantity.bindToVS("http://myvaluesets.org/myvs", "required");
      assertEquals("http://myvaluesets.org/myvs", at(quantity, "binding.valueSet"));
      assertEquals("required", at(quantity, "binding.strength"));
    }

    @Test
    @DisplayName("ElementDefinition #bindToVS() should bind a value set on a string")
    void t6() {
      ElementDefinition string = el(observation, "Observation.referenceRange.text");
      string.bindToVS("http://myvaluesets.org/myvs", "required");
      assertEquals("http://myvaluesets.org/myvs", at(string, "binding.valueSet"));
      assertEquals("required", at(string, "binding.strength"));
    }

    @Test
    @DisplayName("ElementDefinition #bindToVS() should bind a value set on a uri")
    void t7() {
      ElementDefinition uri = el(observation, "Observation.implicitRules");
      uri.bindToVS("http://myvaluesets.org/myvs", "required");
      assertEquals("http://myvaluesets.org/myvs", at(uri, "binding.valueSet"));
      assertEquals("required", at(uri, "binding.strength"));
    }

    @Test
    @DisplayName("ElementDefinition #bindToVS() should bind a value set with a version on a CodeableConcept")
    void t8() {
      ElementDefinition concept = el(observation, "Observation.code");
      concept.bindToVS("http://myvaluesets.org/myvs|1.2.3", "required");
      assertEquals("http://myvaluesets.org/myvs|1.2.3", at(concept, "binding.valueSet"));
      assertEquals("required", at(concept, "binding.strength"));
    }

    @Test
    @DisplayName("ElementDefinition #bindToVS() should bind a value set specified as a fragment")
    void t9() {
      // This can happen when binding to a contained ValueSet
      ElementDefinition concept = el(observation, "Observation.code");
      concept.bindToVS("#contained-vs", "required");
      assertEquals("#contained-vs", at(concept, "binding.valueSet"));
      assertEquals("required", at(concept, "binding.strength"));
    }

    @Test
    @DisplayName("ElementDefinition #bindToVS() should allow a binding with no valueset specified (because it apparently happens)")
    void t10() {
      // See: https://github.com/FHIR/sushi/issues/1312
      ElementDefinition concept = el(observation, "Observation.code");
      concept.bindToVS(null, "required");
      assertNull(at(concept, "binding.valueSet"));
      assertEquals("required", at(concept, "binding.strength"));
    }

    @Test
    @DisplayName("ElementDefinition #bindToVS() should throw CodedTypeNotFoundError when binding to an unsupported type")
    void t11() {
      ElementDefinition instant = el(observation, "Observation.issued");
      ElementDefinition clone = instant.clone(false);
      assertThrowsMatching("instant", () -> { clone.bindToVS("http://myvaluesets.org/myvs", "required");
 });
      assertSameState(instant, clone);
    }

    @Test
    @DisplayName("ElementDefinition #bindToVS() should throw InvalidUriError when binding with a non-URI non-fragment value")
    void t12() {
      ElementDefinition category = el(observation, "Observation.category");
      ElementDefinition clone = category.clone(false);
      assertThrowsMatching("notAUri", () -> { clone.bindToVS("notAUri", "required");
 });
    }

    @Test
    @DisplayName("ElementDefinition #bindToVS() should only allow required to be rebound with required")
    void t13() {
      ElementDefinition status = el(observation, "Observation.status");
      assertEquals("required", at(status, "binding.strength"));
      ElementDefinition clone = status.clone(false);
      clone.bindToVS("http://myvaluesets.org/myvs", "required");
      assertEquals("http://myvaluesets.org/myvs", at(clone, "binding.valueSet"));
      assertEquals("required", at(clone, "binding.strength"));
      ElementDefinition clone2 = status.clone(false);
      assertThrowsMatching("required.*extensible", () -> clone2.bindToVS("http://myvaluesets.org/myvs", "extensible"));
      assertSameState(status, clone2);
      ElementDefinition clone3 = status.clone(false);
      assertThrowsMatching("required.*preferred", () -> clone3.bindToVS("http://myvaluesets.org/myvs", "preferred"));
      assertSameState(status, clone3);
      ElementDefinition clone4 = status.clone(false);
      assertThrowsMatching("required.*example", () -> clone4.bindToVS("http://myvaluesets.org/myvs", "example"));
      clone = clone4;
      assertSameState(status, clone);
    }

    @Test
    @DisplayName("ElementDefinition #bindToVS() should only allow extensible to be rebound with extensible or required")
    void t14() {
      ElementDefinition interpretation = el(observation, "Observation.interpretation");
      assertEquals("extensible", at(interpretation, "binding.strength"));
      ElementDefinition clone = interpretation.clone(false);
      clone.bindToVS("http://myvaluesets.org/myvs", "extensible");
      assertEquals("http://myvaluesets.org/myvs", at(clone, "binding.valueSet"));
      assertEquals("extensible", at(clone, "binding.strength"));
      clone = interpretation.clone(false);
      clone.bindToVS("http://myvaluesets.org/myvs2", "required");
      assertEquals("http://myvaluesets.org/myvs2", at(clone, "binding.valueSet"));
      assertEquals("required", at(clone, "binding.strength"));
      clone = interpretation.clone(false);
      assertThrowsMatching("extensible.*preferred", () -> { interpretation.bindToVS("http://myvaluesets.org/myvs", "preferred");
 });
      assertSameState(interpretation, clone);
      clone = interpretation.clone(false);
      assertThrowsMatching("extensible.*example", () -> { interpretation.bindToVS("http://myvaluesets.org/myvs", "example");
 });
      assertSameState(interpretation, clone);
    }

    @Test
    @DisplayName("ElementDefinition #bindToVS() should only allow preferred to be rebound with preferred, extensible, or required")
    void t15() {
      ElementDefinition category = el(observation, "Observation.category");
      assertEquals("preferred", at(category, "binding.strength"));
      ElementDefinition clone = category.clone(false);
      clone.bindToVS("http://myvaluesets.org/myvs", "preferred");
      assertEquals("http://myvaluesets.org/myvs", at(clone, "binding.valueSet"));
      assertEquals("preferred", at(clone, "binding.strength"));
      clone = category.clone(false);
      clone.bindToVS("http://myvaluesets.org/myvs2", "extensible");
      assertEquals("http://myvaluesets.org/myvs2", at(clone, "binding.valueSet"));
      assertEquals("extensible", at(clone, "binding.strength"));
      clone = category.clone(false);
      clone.bindToVS("http://myvaluesets.org/myvs3", "required");
      assertEquals("http://myvaluesets.org/myvs3", at(clone, "binding.valueSet"));
      assertEquals("required", at(clone, "binding.strength"));
      clone = category.clone(false);
      assertThrowsMatching("preferred.*example", () -> { category.bindToVS("http://myvaluesets.org/myvs", "example");
 });
      assertSameState(category, clone);
    }

    @Test
    @DisplayName("ElementDefinition #bindToVS() should only allow example to be rebound with any strength")
    void t16() {
      ElementDefinition code = el(observation, "Observation.code");
      assertEquals("example", at(code, "binding.strength"));
      ElementDefinition clone = code.clone(false);
      clone.bindToVS("http://myvaluesets.org/myvs", "example");
      assertEquals("http://myvaluesets.org/myvs", at(clone, "binding.valueSet"));
      assertEquals("example", at(clone, "binding.strength"));
      clone = code.clone(false);
      clone.bindToVS("http://myvaluesets.org/myvs2", "preferred");
      assertEquals("http://myvaluesets.org/myvs2", at(clone, "binding.valueSet"));
      assertEquals("preferred", at(clone, "binding.strength"));
      clone = code.clone(false);
      clone.bindToVS("http://myvaluesets.org/myvs3", "extensible");
      assertEquals("http://myvaluesets.org/myvs3", at(clone, "binding.valueSet"));
      assertEquals("extensible", at(clone, "binding.strength"));
      clone = code.clone(false);
      clone.bindToVS("http://myvaluesets.org/myvs4", "required");
      assertEquals("http://myvaluesets.org/myvs4", at(clone, "binding.valueSet"));
      assertEquals("required", at(clone, "binding.strength"));
    }
  }

  @Test
  @DisplayName("ElementDefinition should still follow strength rules even when no value set is supplied (because it apparently happens)")
  void t17() {
    // See: https://github.com/FHIR/sushi/issues/1312
    ElementDefinition interpretation = el(observation, "Observation.interpretation");
    assertEquals("extensible", at(interpretation, "binding.strength"));
    ElementDefinition clone = interpretation.clone(false);
    clone.bindToVS(null, "extensible");
    assertNull(at(clone, "binding.valueSet"));
    assertEquals("extensible", at(clone, "binding.strength"));
    clone = interpretation.clone(false);
    clone.bindToVS(null, "required");
    assertNull(at(clone, "binding.valueSet"));
    assertEquals("required", at(clone, "binding.strength"));
    clone = interpretation.clone(false);
    assertThrowsMatching("extensible.*preferred", () -> { interpretation.bindToVS(null, "preferred");
 });
    assertSameState(interpretation, clone);
    clone = interpretation.clone(false);
    assertThrowsMatching("extensible.*example", () -> { interpretation.bindToVS(null, "example");
 });
    assertSameState(interpretation, clone);
  }
}
@DisplayName("ElementDefinition R5")
class ElementDefinitionBindToVSR5Test {
  static FHIRDefinitions defs;
  StructureDefinition r5CarePlan;
  static TestFisher fisher;
  @BeforeAll
  static void beforeAll18() {
    defs = r5Defs();
    fisher = new TestFisher().withFHIR(defs);
  }
  @BeforeEach
  void beforeEach19() {
    r5CarePlan = fisher.fishForStructureDefinition("CarePlan");
    loggerSpy.reset();
  }

  @Nested
  @DisplayName("#bindToVS()")
  class BindToVS {

    @Test
    @DisplayName("ElementDefinition R5 #bindToVS() should bind a value set on a CodeableReference")
    void t20() {
      ElementDefinition addresses = el(r5CarePlan, "CarePlan.addresses");
      addresses.bindToVS("http://myvaluesets.org/myvs", "required");
      assertEquals("http://myvaluesets.org/myvs", at(addresses, "binding.valueSet"));
      assertEquals("required", at(addresses, "binding.strength"));
    }

    @Test
    @DisplayName("ElementDefinition R5 #bindToVS() should log an error when trying to bind the concept portion of a CodeableReference element directly")
    void t21() {
      ElementDefinition addresses = el(r5CarePlan, "CarePlan.addresses");
      addresses.unfold(fisher);
      ElementDefinition concept = el(r5CarePlan, "CarePlan.addresses.concept");
      var ruleSourceInfo = new SourceInfo("fishy.fsh", new TextLocation(6, 1, 6, 10));
      concept.bindToVS("http://myvaluesets.org/myvs", "required", ruleSourceInfo);
      assertEquals("http://myvaluesets.org/myvs", at(concept, "binding.valueSet"));
      assertEquals("required", at(concept, "binding.strength"));
      assertEquals(1, size(loggerSpy.getAllMessages("error")));
      assertMatchesJs("Applying value set bindings to a CodeableReference element's underlying \\.concept path is not allowed.* directly to the CodeableReference element", loggerSpy.getLastMessage("error"));
      assertMatchesJs("(?s)File: fishy\\.fsh.*Line: 6\\D*", loggerSpy.getLastMessage("error"));
      assertEquals(0, size(loggerSpy.getAllMessages("warn")));
    }
  }
}
