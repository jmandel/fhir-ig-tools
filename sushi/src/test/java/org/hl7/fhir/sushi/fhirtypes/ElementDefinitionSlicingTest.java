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
import org.hl7.fhir.sushi.js.Js;
import org.hl7.fhir.sushi.js.JsObject;
import org.hl7.fhir.sushi.testhelpers.TestFisher;
import org.hl7.fhir.sushi.utils.FishableType;
import org.junit.jupiter.api.*;

// port of sushi/test/fhirtypes/ElementDefinition.slicing.test.ts (SUSHI 3.21.1)
@DisplayName("ElementDefinition")
class ElementDefinitionSlicingTest {
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
    loggerSpy.reset();
  }

  @Nested
  @DisplayName("#sliceIt()")
  class SliceIt {

    @Test
    @DisplayName("ElementDefinition #sliceIt() should slice an array, defaulting to unordered and open")
    void t2() {
      ElementDefinition component = el(observation, "Observation.component");
      var slicing = component.sliceIt("pattern", "code");
      assertEquals(1, size(at(slicing, "discriminator")));
      assertEquals("pattern", at(slicing, "discriminator[0].type"));
      assertEquals("code", at(slicing, "discriminator[0].path"));
      assertEquals(false, at(slicing, "ordered"));
      assertEquals("open", at(slicing, "rules"));
      assertJsEqual(slicing, at(component, "slicing"));
    }

    @Test
    @DisplayName("ElementDefinition #sliceIt() should slice an array with specified ordered and rules values")
    void t3() {
      ElementDefinition component = el(observation, "Observation.component");
      var slicing = component.sliceIt("pattern", "code", true, "closed");
      assertEquals(1, size(at(slicing, "discriminator")));
      assertEquals("pattern", at(slicing, "discriminator[0].type"));
      assertEquals("code", at(slicing, "discriminator[0].path"));
      assertEquals(true, at(slicing, "ordered"));
      assertEquals("closed", at(slicing, "rules"));
      assertJsEqual(slicing, at(component, "slicing"));
    }

    @Test
    @DisplayName("ElementDefinition #sliceIt() should not do anything if the slicing is the same as what already exists - no ordered/rules specified")
    void t4() {
      ElementDefinition component = el(observation, "Observation.component");
      // Setup original slicing
      component.sliceIt("pattern", "code");
      // Constrain slicing
      var slicing = component.sliceIt("pattern", "code");
      assertEquals(1, size(at(slicing, "discriminator")));
      assertEquals("pattern", at(slicing, "discriminator[0].type"));
      assertEquals("code", at(slicing, "discriminator[0].path"));
      assertEquals(false, at(slicing, "ordered"));
      assertEquals("open", at(slicing, "rules"));
      assertJsEqual(slicing, at(component, "slicing"));
    }

    @Test
    @DisplayName("ElementDefinition #sliceIt() should not do anything if the slicing is the same as what already exists with ordered/rules specified")
    void t5() {
      ElementDefinition component = el(observation, "Observation.component");
      // Setup original slicing
      component.sliceIt("pattern", "code", true, "closed");
      // Constrain slicing
      var slicing = component.sliceIt("pattern", "code", true, "closed");
      assertEquals(1, size(at(slicing, "discriminator")));
      assertEquals("pattern", at(slicing, "discriminator[0].type"));
      assertEquals("code", at(slicing, "discriminator[0].path"));
      assertEquals(true, at(slicing, "ordered"));
      assertEquals("closed", at(slicing, "rules"));
      assertJsEqual(slicing, at(component, "slicing"));
    }

    @Test
    @DisplayName("ElementDefinition #sliceIt() should add another discriminator if the type is different from what already exists")
    void t6() {
      ElementDefinition component = el(observation, "Observation.component");
      // Setup original slicing
      component.sliceIt("pattern", "code", true, "closed");
      // Constrain slicing
      var slicing = component.sliceIt("value", "code", true, "closed");
      assertEquals(2, size(at(slicing, "discriminator")));
      assertEquals("pattern", at(slicing, "discriminator[0].type"));
      assertEquals("code", at(slicing, "discriminator[0].path"));
      assertEquals("value", at(slicing, "discriminator[1].type"));
      assertEquals("code", at(slicing, "discriminator[1].path"));
      assertEquals(true, at(slicing, "ordered"));
      assertEquals("closed", at(slicing, "rules"));
      assertJsEqual(slicing, at(component, "slicing"));
    }

    @Test
    @DisplayName("ElementDefinition #sliceIt() should add another discriminator if the path is different from what already exists")
    void t7() {
      ElementDefinition component = el(observation, "Observation.component");
      // Setup original slicing
      component.sliceIt("pattern", "code", true, "closed");
      // Constrain slicing
      var slicing = component.sliceIt("pattern", "interpretation", true, "closed");
      assertEquals(2, size(at(slicing, "discriminator")));
      assertEquals("pattern", at(slicing, "discriminator[0].type"));
      assertEquals("code", at(slicing, "discriminator[0].path"));
      assertEquals("pattern", at(slicing, "discriminator[1].type"));
      assertEquals("interpretation", at(slicing, "discriminator[1].path"));
      assertEquals(true, at(slicing, "ordered"));
      assertEquals("closed", at(slicing, "rules"));
      assertJsEqual(slicing, at(component, "slicing"));
    }

    @Test
    @DisplayName("ElementDefinition #sliceIt() should allow slicing ordered to be constrained from false to true")
    void t8() {
      ElementDefinition component = el(observation, "Observation.component");
      // Setup original slicing
      component.sliceIt("pattern", "code", false, "open");
      // Constrain slicing
      var slicing = component.sliceIt("pattern", "code", true, "open");
      assertEquals(1, size(at(slicing, "discriminator")));
      assertEquals("pattern", at(slicing, "discriminator[0].type"));
      assertEquals("code", at(slicing, "discriminator[0].path"));
      assertEquals(true, at(slicing, "ordered"));
      assertEquals("open", at(slicing, "rules"));
      assertJsEqual(slicing, at(component, "slicing"));
    }

    @Test
    @DisplayName("ElementDefinition #sliceIt() should throw when slicing ordered is constrained from true to false")
    void t9() {
      ElementDefinition component = el(observation, "Observation.component");
      // Setup original slicing
      component.sliceIt("pattern", "code", true, "open");
      // Constrain slicing
      assertThrowsMatching("'ordered' from true to false", () -> { component.sliceIt("pattern", "code", false, "open");
 });
    }

    @Test
    @DisplayName("ElementDefinition #sliceIt() should allow slicing rules to be constrained from open to open")
    void t10() {
      ElementDefinition component = el(observation, "Observation.component");
      // Setup original slicing
      component.sliceIt("pattern", "code", false, "open");
      // Constrain slicing
      var slicing = component.sliceIt("pattern", "code", false, "open");
      assertEquals(1, size(at(slicing, "discriminator")));
      assertEquals("pattern", at(slicing, "discriminator[0].type"));
      assertEquals("code", at(slicing, "discriminator[0].path"));
      assertEquals(false, at(slicing, "ordered"));
      assertEquals("open", at(slicing, "rules"));
      assertJsEqual(slicing, at(component, "slicing"));
    }

    @Test
    @DisplayName("ElementDefinition #sliceIt() should allow slicing rules to be constrained from open to openAtEnd")
    void t11() {
      ElementDefinition component = el(observation, "Observation.component");
      // Setup original slicing
      component.sliceIt("pattern", "code", false, "open");
      // Constrain slicing
      var slicing = component.sliceIt("pattern", "code", false, "openAtEnd");
      assertEquals(1, size(at(slicing, "discriminator")));
      assertEquals("pattern", at(slicing, "discriminator[0].type"));
      assertEquals("code", at(slicing, "discriminator[0].path"));
      assertEquals(false, at(slicing, "ordered"));
      assertEquals("openAtEnd", at(slicing, "rules"));
      assertJsEqual(slicing, at(component, "slicing"));
    }

    @Test
    @DisplayName("ElementDefinition #sliceIt() should allow slicing rules to be constrained from open to closed")
    void t12() {
      ElementDefinition component = el(observation, "Observation.component");
      // Setup original slicing
      component.sliceIt("pattern", "code", false, "open");
      // Constrain slicing
      var slicing = component.sliceIt("pattern", "code", false, "closed");
      assertEquals(1, size(at(slicing, "discriminator")));
      assertEquals("pattern", at(slicing, "discriminator[0].type"));
      assertEquals("code", at(slicing, "discriminator[0].path"));
      assertEquals(false, at(slicing, "ordered"));
      assertEquals("closed", at(slicing, "rules"));
      assertJsEqual(slicing, at(component, "slicing"));
    }

    @Test
    @DisplayName("ElementDefinition #sliceIt() should allow slicing rules to be constrained from openAtEnd to openAtEnd")
    void t13() {
      ElementDefinition component = el(observation, "Observation.component");
      // Setup original slicing
      component.sliceIt("pattern", "code", false, "openAtEnd");
      // Constrain slicing
      var slicing = component.sliceIt("pattern", "code", false, "openAtEnd");
      assertEquals(1, size(at(slicing, "discriminator")));
      assertEquals("pattern", at(slicing, "discriminator[0].type"));
      assertEquals("code", at(slicing, "discriminator[0].path"));
      assertEquals(false, at(slicing, "ordered"));
      assertEquals("openAtEnd", at(slicing, "rules"));
      assertJsEqual(slicing, at(component, "slicing"));
    }

    @Test
    @DisplayName("ElementDefinition #sliceIt() should allow slicing rules to be constrained from openAtEnd to closed")
    void t14() {
      ElementDefinition component = el(observation, "Observation.component");
      // Setup original slicing
      component.sliceIt("pattern", "code", false, "openAtEnd");
      // Constrain slicing
      var slicing = component.sliceIt("pattern", "code", false, "closed");
      assertEquals(1, size(at(slicing, "discriminator")));
      assertEquals("pattern", at(slicing, "discriminator[0].type"));
      assertEquals("code", at(slicing, "discriminator[0].path"));
      assertEquals(false, at(slicing, "ordered"));
      assertEquals("closed", at(slicing, "rules"));
      assertJsEqual(slicing, at(component, "slicing"));
    }

    @Test
    @DisplayName("ElementDefinition #sliceIt() should throw when slicing rules is constrained from openAtEnd to open")
    void t15() {
      ElementDefinition component = el(observation, "Observation.component");
      // Setup original slicing
      component.sliceIt("pattern", "code", false, "openAtEnd");
      // Constrain slicing
      assertThrowsMatching("'rules' from openAtEnd to open", () -> { component.sliceIt("pattern", "code", false, "open");
 });
    }

    @Test
    @DisplayName("ElementDefinition #sliceIt() should allow slicing rules to be constrained from closed to closed")
    void t16() {
      ElementDefinition component = el(observation, "Observation.component");
      // Setup original slicing
      component.sliceIt("pattern", "code", false, "closed");
      // Constrain slicing
      var slicing = component.sliceIt("pattern", "code", false, "closed");
      assertEquals(1, size(at(slicing, "discriminator")));
      assertEquals("pattern", at(slicing, "discriminator[0].type"));
      assertEquals("code", at(slicing, "discriminator[0].path"));
      assertEquals(false, at(slicing, "ordered"));
      assertEquals("closed", at(slicing, "rules"));
      assertJsEqual(slicing, at(component, "slicing"));
    }

    @Test
    @DisplayName("ElementDefinition #sliceIt() should throw when slicing rules is constrained from closed to openAtEnd")
    void t17() {
      ElementDefinition component = el(observation, "Observation.component");
      // Setup original slicing
      component.sliceIt("pattern", "code", false, "closed");
      // Constrain slicing
      assertThrowsMatching("'rules' from closed to openAtEnd", () -> { component.sliceIt("pattern", "code", false, "openAtEnd");
 });
    }

    @Test
    @DisplayName("ElementDefinition #sliceIt() should throw when slicing rules is constrained from closed to open")
    void t18() {
      ElementDefinition component = el(observation, "Observation.component");
      // Setup original slicing
      component.sliceIt("pattern", "code", false, "closed");
      // Constrain slicing
      assertThrowsMatching("'rules' from closed to open", () -> { component.sliceIt("pattern", "code", false, "open");
 });
    }
  }

  @Nested
  @DisplayName("#addSlice")
  class AddSlice {

    @Test
    @DisplayName("ElementDefinition #addSlice should add slices when a slicing is present")
    void t19() {
      ElementDefinition component = el(observation, "Observation.component");
      var lastComponentIndex = findLastIndex(observation.elements, (e) -> ((String) at(e, "id")).startsWith("Observation.component."));
      component.sliceIt("pattern", "code", false, "open");
      ElementDefinition systolicBP = component.addSlice("SystolicBP");
      ElementDefinition diastolicBP = component.addSlice("DiastolicBP");
      assertEquals("Observation.component:SystolicBP", at(systolicBP, "id"));
      assertEquals("Observation.component", at(systolicBP, "path"));
      assertNull(at(systolicBP, "slicing"));
      assertEquals("SystolicBP", at(systolicBP, "sliceName"));
      assertEquals(0.0, at(systolicBP, "min"));
      assertEquals("*", at(systolicBP, "max"));
      assertEquals(1, size(at(systolicBP, "type")));
      assertJsEqual(new ElementDefinitionType("BackboneElement"), at(systolicBP, "type[0]"));
      var systolicDiff = systolicBP.calculateDiff();
      assertEquals(0.0, at(systolicDiff, "min"));
      assertEquals("*", at(systolicDiff, "max"));
      assertEquals("Observation.component:DiastolicBP", at(diastolicBP, "id"));
      assertEquals("Observation.component", at(diastolicBP, "path"));
      assertNull(at(diastolicBP, "slicing"));
      assertEquals("DiastolicBP", at(diastolicBP, "sliceName"));
      assertEquals(0.0, at(diastolicBP, "min"));
      assertEquals("*", at(diastolicBP, "max"));
      assertEquals(1, size(at(diastolicBP, "type")));
      assertJsEqual(new ElementDefinitionType("BackboneElement"), at(diastolicBP, "type[0]"));
      assertJsEqual(systolicBP, get(observation.elements, lastComponentIndex + 1.0));
      assertJsEqual(diastolicBP, get(observation.elements, lastComponentIndex + 2.0));
      var diastolicDiff = diastolicBP.calculateDiff();
      assertEquals(0.0, at(diastolicDiff, "min"));
      assertEquals("*", at(diastolicDiff, "max"));
    }

    @Test
    @DisplayName("ElementDefinition #addSlice should add slices for specific types in a choice")
    void t20() {
      var valueXIdx = indexByPath(observation, "Observation.value[x]");
      var valueX = get(observation.elements, valueXIdx);
      valueX.sliceIt("type", "$this", false, "open");
      ElementDefinition quantity = valueX.addSlice("valueQuantity", new ElementDefinitionType("Quantity"));
      ElementDefinition integer = valueX.addSlice("valueInteger", new ElementDefinitionType("integer"));
      assertEquals("Observation.value[x]:valueQuantity", at(quantity, "id"));
      assertEquals("Observation.value[x]", at(quantity, "path"));
      assertNull(at(quantity, "slicing"));
      assertEquals("valueQuantity", at(quantity, "sliceName"));
      assertEquals(0.0, at(quantity, "min"));
      assertEquals("1", at(quantity, "max"));
      assertEquals(1, size(at(quantity, "type")));
      assertJsEqual(new ElementDefinitionType("Quantity"), at(quantity, "type[0]"));
      assertEquals("Observation.value[x]:valueInteger", at(integer, "id"));
      assertEquals("Observation.value[x]", at(integer, "path"));
      assertNull(at(integer, "slicing"));
      assertEquals("valueInteger", at(integer, "sliceName"));
      assertEquals(0.0, at(integer, "min"));
      assertEquals("1", at(integer, "max"));
      assertEquals(1, size(at(integer, "type")));
      assertJsEqual(new ElementDefinitionType("integer"), at(integer, "type[0]"));
      assertJsEqual(quantity, get(observation.elements, valueXIdx + 1.0));
      assertJsEqual(integer, get(observation.elements, valueXIdx + 2.0));
    }

    @Test
    @DisplayName("ElementDefinition #addSlice should reduce min to 0 when addings slices for specific types in a choice of types")
    void t21() {
      ElementDefinition valueX = byPath(observation, "Observation.value[x]");
      // set value[x] min to 1 to test that slice mins are reduced to 0 (to allow other choices)
      setAt(valueX, "min", 1.0);
      valueX.sliceIt("type", "$this", false, "open");
      ElementDefinition quantity = valueX.addSlice("valueQuantity", new ElementDefinitionType("Quantity"));
      ElementDefinition integer = valueX.addSlice("valueInteger", new ElementDefinitionType("integer"));
      assertEquals(0.0, at(quantity, "min"));
      assertEquals("1", at(quantity, "max"));
      assertEquals(0.0, at(integer, "min"));
      assertEquals("1", at(integer, "max"));
    }

    @Test
    @DisplayName("ElementDefinition #addSlice should NOT reduce min to 0 when adding a slice for specific types in a choice of only one type")
    void t22() {
      // NOTE: It's up for debate whether we should make explicit choices when there only is one option anyway,
      // but for now, we do.  So... we need to test this use case.
      ElementDefinition valueX = byPath(observation, "Observation.value[x]");
      // Remove all but the Quantity choice from value[x]
      valueX.setType(new ArrayList<>(valueX.getType().stream().filter(t -> "Quantity".equals(t.getCode())).toList()));
      // set value[x] min to 1 to test that slice min remains at 1
      setAt(valueX, "min", 1.0);
      valueX.sliceIt("type", "$this", false, "open");
      ElementDefinition quantity = valueX.addSlice("valueQuantity", new ElementDefinitionType("Quantity"));
      assertEquals(1.0, at(quantity, "min"));
      assertEquals("1", at(quantity, "max"));
    }

    @Test
    @DisplayName("ElementDefinition #addSlice should reslice when slicing a slice")
    void t23() {
      ElementDefinition component = el(observation, "Observation.component");
      var lastComponentIndex = findLastIndex(observation.elements, (e) -> ((String) at(e, "id")).startsWith("Observation.component."));
      component.sliceIt("pattern", "code", false, "open");
      ElementDefinition systolicBP = component.addSlice("SystolicBP");
      ElementDefinition diastolicBP = component.addSlice("DiastolicBP");
      ElementDefinition fooSlice = systolicBP.addSlice("Foo");
      assertEquals("Observation.component:SystolicBP/Foo", at(fooSlice, "id"));
      assertEquals("Observation.component", at(fooSlice, "path"));
      assertNull(at(fooSlice, "slicing"));
      assertEquals("SystolicBP/Foo", at(fooSlice, "sliceName"));
      assertEquals(0.0, at(fooSlice, "min"));
      assertEquals("*", at(fooSlice, "max"));
      assertEquals(1, size(at(fooSlice, "type")));
      assertJsEqual(new ElementDefinitionType("BackboneElement"), at(fooSlice, "type[0]"));
      var fooDiff = fooSlice.calculateDiff();
      assertEquals(0.0, at(fooDiff, "min"));
      assertEquals("*", at(fooDiff, "max"));
      assertJsEqual(systolicBP, get(observation.elements, lastComponentIndex + 1.0));
      assertJsEqual(fooSlice, get(observation.elements, lastComponentIndex + 2.0));
      assertJsEqual(diastolicBP, get(observation.elements, lastComponentIndex + 3.0));
      assertEquals(0, size(loggerSpy.getAllMessages()));
    }

    @Test
    @DisplayName("ElementDefinition #addSlice should reslice when slicing a reslice")
    void t24() {
      ElementDefinition component = el(observation, "Observation.component");
      var lastComponentIndex = findLastIndex(observation.elements, (e) -> ((String) at(e, "id")).startsWith("Observation.component."));
      component.sliceIt("pattern", "code", false, "open");
      ElementDefinition systolicBP = component.addSlice("SystolicBP");
      ElementDefinition diastolicBP = component.addSlice("DiastolicBP");
      component.sliceIt("pattern", "interpretation", false, "open");
      ElementDefinition fooSlice = systolicBP.addSlice("Foo");
      component.sliceIt("pattern", "dataAbsentReason", false, "open");
      ElementDefinition barSlice = fooSlice.addSlice("Bar");
      assertEquals("Observation.component:SystolicBP/Foo/Bar", at(barSlice, "id"));
      assertEquals("Observation.component", at(barSlice, "path"));
      assertNull(at(barSlice, "slicing"));
      assertEquals("SystolicBP/Foo/Bar", at(barSlice, "sliceName"));
      assertEquals(0.0, at(barSlice, "min"));
      assertEquals("*", at(barSlice, "max"));
      assertEquals(1, size(at(barSlice, "type")));
      assertJsEqual(new ElementDefinitionType("BackboneElement"), at(barSlice, "type[0]"));
      var barDiff = barSlice.calculateDiff();
      assertEquals(0.0, at(barDiff, "min"));
      assertEquals("*", at(barDiff, "max"));
      assertJsEqual(systolicBP, get(observation.elements, lastComponentIndex + 1.0));
      assertJsEqual(fooSlice, get(observation.elements, lastComponentIndex + 2.0));
      assertJsEqual(barSlice, get(observation.elements, lastComponentIndex + 3.0));
      assertJsEqual(diastolicBP, get(observation.elements, lastComponentIndex + 4.0));
      assertEquals(0, size(loggerSpy.getAllMessages()));
    }

    @Test
    @DisplayName("ElementDefinition #addSlice should log a warning when trying to slice a slice")
    void t25() {
      // see also https://chat.fhir.org/#narrow/stream/179252-IG-creation/topic/Reslicing.20extensions.20causes.20validator.20errors/near/360153471
      ElementDefinition component = el(observation, "Observation.component");
      component.sliceIt("pattern", "code", false, "open");
      ElementDefinition systolicBP = component.addSlice("SystolicBP");
      assertNull(at(systolicBP, "slicing"));
      var slicing = systolicBP.sliceIt("pattern", "interpretation", false, "open");
      assertEquals(1, size(at(slicing, "discriminator")));
      assertEquals("pattern", at(slicing, "discriminator[0].type"));
      assertEquals("interpretation", at(slicing, "discriminator[0].path"));
      assertEquals(false, at(slicing, "ordered"));
      assertEquals("open", at(slicing, "rules"));
      assertJsEqual(slicing, at(systolicBP, "slicing"));
      assertEquals(1, size(loggerSpy.getAllMessages("warn")));
      assertMatchesJs("Observation.component:SystolicBP is a slice. Slices should not have slicing info added to them.", loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("ElementDefinition #addSlice should throw when no slicing is present")
    void t26() {
      ElementDefinition component = el(observation, "Observation.component");
      // NOTE: intentionally not adding a slicing
      assertThrowsMatching("Cannot create SystolicBP slice", () -> { component.addSlice("SystolicBP");
 });
    }
  }
}
