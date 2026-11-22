package org.hl7.fhir.sushi.fhirtypes;

import static org.hl7.fhir.sushi.fhirtypes.EdTestSupport.assertJsEqual;
import static org.hl7.fhir.sushi.fhirtypes.EdTestSupport.assertJsonEqual;
import static org.hl7.fhir.sushi.fhirtypes.EdTestSupport.assertSameState;
import static org.hl7.fhir.sushi.fhirtypes.EdTestSupport.assertStrictJsEqual;
import static org.hl7.fhir.sushi.fhirtypes.EdTestSupport.byPath;
import static org.hl7.fhir.sushi.fhirtypes.EdTestSupport.edState;
import static org.hl7.fhir.sushi.fhirtypes.EdTestSupport.el;
import static org.hl7.fhir.sushi.fhirtypes.EdTestSupport.findId;
import static org.hl7.fhir.sushi.fhirtypes.EdTestSupport.indexById;
import static org.hl7.fhir.sushi.fhirtypes.EdTestSupport.indexByPath;
import static org.hl7.fhir.sushi.fhirtypes.EdTestSupport.list;
import static org.hl7.fhir.sushi.fhirtypes.EdTestSupport.obj;
import static org.hl7.fhir.sushi.fhirtypes.EdTestSupport.strings;
import static org.hl7.fhir.sushi.fhirtypes.EdTestSupport.types;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.errors.ValidationError;
import org.hl7.fhir.sushi.fhirdefs.FHIRDefinitions;
import org.hl7.fhir.sushi.fshtypes.FshCode;
import org.hl7.fhir.sushi.fshtypes.Invariant;
import org.hl7.fhir.sushi.fshtypes.rules.OnlyRule;
import org.hl7.fhir.sushi.fshtypes.rules.OnlyRuleType;
import org.hl7.fhir.sushi.js.Js;
import org.hl7.fhir.sushi.js.JsObject;
import org.hl7.fhir.sushi.testhelpers.TestFisher;
import org.hl7.fhir.sushi.utils.FishableType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// port of sushi/test/fhirtypes/ElementDefinition.test.ts (SUSHI 3.21.1), describe 'ElementDefinition'
// (describe 'ElementDefinition R5' is in ElementDefinitionR5Test)
@DisplayName("ElementDefinition")
class ElementDefinitionTest {
  static FHIRDefinitions defs;
  static Map<String, Object> jsonObservation;
  static Map<String, Object> jsonValueX;
  static Map<String, Object> jsonValueId;
  static Map<String, Object> jsonModifiedObservation;
  static Map<String, Object> jsonModifiedStatus;
  static Map<String, Object> jsonModifiedSubject;
  static TestFisher fisher;
  StructureDefinition observation;
  StructureDefinition resprate;
  ElementDefinition valueX;
  ElementDefinition valueId;
  StructureDefinition modifiedObservation;
  ElementDefinition modifiedStatus;
  ElementDefinition modifiedSubject;
  StructureDefinition practitioner;

  @SuppressWarnings("unchecked")
  static Map<String, Object> snapshotElement(Map<String, Object> sd, int i) {
    return (Map<String, Object>) ((List<Object>) ((Map<String, Object>) sd.get("snapshot")).get("element")).get(i);
  }

  @SuppressWarnings("unchecked")
  @BeforeAll
  static void beforeAll() {
    defs = EdTestSupport.r4Defs();
    fisher = new TestFisher().withFHIR(defs);
    // resolve observation once to ensure it is present in defs
    fisher.fishForStructureDefinition("Observation");
    fisher.fishForStructureDefinition("resprate");
    jsonObservation = (Map<String, Object>) defs.fishForFHIR("Observation", FishableType.Resource);
    jsonValueX = snapshotElement(jsonObservation, 21);
    jsonValueId = snapshotElement(jsonObservation, 1);
    jsonModifiedObservation = Js.cloneDeep(jsonObservation);
    jsonModifiedStatus = snapshotElement(jsonModifiedObservation, 12);
    jsonModifiedStatus.put("_short", obj("id", "short-id", "extension",
        obj("url", "http://example.org/extensions/short", "valueString", "bogus")));
    jsonModifiedSubject = snapshotElement(jsonModifiedObservation, 15);
    ((Map<String, Object>) ((List<Object>) jsonModifiedSubject.get("type")).get(0)).put("_targetProfile",
        list(null, null, obj("extension", list(obj("url",
            "http://hl7.org/fhir/StructureDefinition/elementdefinition-type-must-support", "valueBoolean", true))),
            null));
  }

  @BeforeEach
  void beforeEach() {
    observation = StructureDefinition.fromJSON(jsonObservation);
    resprate = fisher.fishForStructureDefinition("resprate");
    valueX = ElementDefinition.fromJSON(jsonValueX);
    valueId = ElementDefinition.fromJSON(jsonValueId);
    valueX.structDef = observation;
    valueId.structDef = observation;
    modifiedObservation = StructureDefinition.fromJSON(jsonModifiedObservation);
    modifiedStatus = ElementDefinition.fromJSON(jsonModifiedStatus);
    modifiedSubject = ElementDefinition.fromJSON(jsonModifiedSubject);
    modifiedStatus.structDef = modifiedObservation;
    modifiedSubject.structDef = modifiedObservation;
    practitioner = fisher.fishForStructureDefinition("Practitioner");
    loggerSpy.reset();
  }

  static final List<ElementDefinitionType> VALUE_X_TYPES = types("Quantity", "CodeableConcept", "string", "boolean",
      "integer", "Range", "Ratio", "SampledData", "time", "dateTime", "Period");

  @SuppressWarnings("unchecked")
  static Map<String, Object> shortOf(ElementDefinition ed) {
    return (Map<String, Object>) ed.getProperty("_short");
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> mustSupportExt(ElementDefinition ed) {
    Map<String, Object> tp2 = (Map<String, Object>) ed.getType().get(0).get_targetProfile().get(2);
    return (Map<String, Object>) ((List<Object>) tp2.get("extension")).get(0);
  }

  @Nested
  @DisplayName("#fromJSON")
  class FromJSON {
    @Test
    @DisplayName("ElementDefinition #fromJSON should load an element properly")
    void loadElement() {
      // Don't test everything, but get a sample anyway
      assertFalse(valueX.hasDiff());
      assertEquals("Observation.value[x]", valueX.getId());
      assertEquals("Observation.value[x]", valueX.getPath());
      assertEquals(0.0, valueX.getMin());
      assertEquals("1", valueX.getMax());
      assertJsEqual(VALUE_X_TYPES, valueX.getType());
    }

    @Test
    @DisplayName("ElementDefinition #fromJSON should load an element properly without capturing original")
    void loadWithoutOriginal() {
      ElementDefinition valueX = ElementDefinition.fromJSON(jsonValueX, false);
      // Don't test everything, but get a sample anyway
      assertTrue(valueX.hasDiff());
      assertSameState(valueX, valueX.calculateDiff());
      assertEquals("Observation.value[x]", valueX.getId());
      assertEquals("Observation.value[x]", valueX.getPath());
      assertEquals(0.0, valueX.getMin());
      assertEquals("1", valueX.getMax());
      assertJsEqual(VALUE_X_TYPES, valueX.getType());
    }

    @Test
    @DisplayName("ElementDefinition #fromJSON should load primitive property ids and extensions")
    void primitiveIdsAndExtensions() {
      assertFalse(modifiedStatus.hasDiff());
      assertEquals("Observation.status", modifiedStatus.getId());
      assertEquals("Observation.status", modifiedStatus.getPath());
      assertEquals("registered | preliminary | final | amended +", modifiedStatus.getShort());
      assertJsonEqual("{\"id\": \"short-id\", \"extension\": {\"url\": \"http://example.org/extensions/short\", "
          + "\"valueString\": \"bogus\"}}", modifiedStatus.getProperty("_short"));
    }

    @Test
    @DisplayName("ElementDefinition #fromJSON should load property ids and extensions on type targetProfiles")
    void targetProfileExtensions() {
      assertFalse(modifiedSubject.hasDiff());
      assertEquals("Observation.subject", modifiedSubject.getId());
      assertEquals("Observation.subject", modifiedSubject.getPath());
      assertEquals("Reference", modifiedSubject.getType().get(0).getCode());
      assertJsEqual(List.of("http://hl7.org/fhir/StructureDefinition/Patient",
          "http://hl7.org/fhir/StructureDefinition/Group", "http://hl7.org/fhir/StructureDefinition/Device",
          "http://hl7.org/fhir/StructureDefinition/Location"), modifiedSubject.getType().get(0).getTargetProfile());
      assertJsonEqual("[null, null, {\"extension\": [{\"url\": "
          + "\"http://hl7.org/fhir/StructureDefinition/elementdefinition-type-must-support\", \"valueBoolean\": true}]}, "
          + "null]", modifiedSubject.getType().get(0).get_targetProfile());
    }
  }

  @Nested
  @DisplayName("#toJSON")
  class ToJSON {
    @Test
    @DisplayName("ElementDefinition #toJSON should round trip back to the original JSON")
    void roundTrip() {
      JsObject newJSON = valueX.toJSON();
      assertJsEqual(jsonValueX, newJSON);
    }

    @Test
    @DisplayName("ElementDefinition #toJSON should round trip back to the original JSON when there are ids and extensions on primitives")
    void roundTripPrimitives() {
      JsObject newJSON = modifiedStatus.toJSON();
      assertJsEqual(jsonModifiedStatus, newJSON);
    }

    @Test
    @DisplayName("ElementDefinition #toJSON should round trip back to the original JSON when there are ids and extensions on type targetProfiles")
    void roundTripTargetProfiles() {
      JsObject newJSON = modifiedSubject.toJSON();
      assertJsEqual(jsonModifiedSubject, newJSON);
    }
  }

  @Nested
  @DisplayName("#id()")
  class Id {
    @Test
    @DisplayName("ElementDefinition #id() should reset the path when you change the id")
    void resetPath() {
      assertEquals("Observation.value[x]", valueX.getPath());
      valueX.setId("Observation.valueString");
      assertEquals("Observation.valueString", valueX.getPath());
    }
  }

  @Nested
  @DisplayName("#getPathWithoutBase()")
  class GetPathWithoutBase {
    @Test
    @DisplayName("ElementDefinition #getPathWithoutBase() should get the path without the base")
    void pathWithoutBase() {
      assertEquals("value[x]", valueX.getPathWithoutBase());
    }
  }

  @Nested
  @DisplayName("#newChildElement")
  class NewChildElement {
    @Test
    @DisplayName("ElementDefinition #newChildElement should create a child element w/ proper id, path, and structdef")
    void child() {
      ElementDefinition child = valueX.newChildElement("sonny");
      assertEquals("Observation.value[x].sonny", child.getId());
      assertEquals("Observation.value[x].sonny", child.getPath());
      assertSame(observation, child.structDef);
    }
  }

  @Nested
  @DisplayName("#captureOriginal()")
  class CaptureOriginal {
    @Test
    @DisplayName("ElementDefinition #captureOriginal() should create a new starting point for diffs")
    void startingPoint() {
      // Note: this is not as true a unit test as it should be since it is intertwined
      // with hasDiff(), but there isn't an easy way around this since _original is private.
      valueX.setMin(1.0);
      assertTrue(valueX.hasDiff());
      valueX.captureOriginal();
      assertFalse(valueX.hasDiff());
      valueX.setMin(2.0);
      assertTrue(valueX.hasDiff());
    }
  }

  @Nested
  @DisplayName("#clearOriginal()")
  class ClearOriginal {
    @Test
    @DisplayName("ElementDefinition #clearOriginal() should remove the starting point for diffs, making everything diff")
    void clear() {
      // Note: this is not as true a unit test as it should be since it is intertwined
      // with hasDiff(), but there isn't an easy way around this since _original is private.
      valueX.setMin(1.0);
      valueX.captureOriginal();
      assertFalse(valueX.hasDiff());
      valueX.clearOriginal();
      assertTrue(valueX.hasDiff());
    }
  }

  static JsObject valueCodeSlicing() {
    return obj("ordered", false, "rules", "open", "discriminator", list(obj("type", "value", "path", "code")));
  }

  @Nested
  @DisplayName("#getSlices")
  class GetSlices {
    @Test
    @DisplayName("ElementDefinition #getSlices should get slices of an element")
    void slices() {
      ElementDefinition component = byPath(observation, "Observation.component");
      component.setSlicing(valueCodeSlicing());
      ElementDefinition fooSlice = component.addSlice("FooSlice");
      List<ElementDefinition> slices = component.getSlices();
      List<ElementDefinition> newElements = fooSlice.unfold(fisher);
      ElementDefinition fooSliceExtension = newElements.get(1);
      assertEquals(1, slices.size());
      assertEquals("Observation.component:FooSlice", slices.get(0).getId());
      assertTrue(fooSliceExtension.getSlices().isEmpty());
    }
  }

  @Nested
  @DisplayName("#applyConstraints")
  class ApplyConstraints {
    @Test
    @DisplayName("ElementDefinition #applyConstraints should apply a constraint based on an invariant with every field")
    void everyField() {
      Invariant invariant = new Invariant("MyInvariant");
      invariant.description = "An invariant with all metadata specified.";
      invariant.expression = "metadata.exists()";
      invariant.xpath = "exists(f:metadata)";
      invariant.severity = new FshCode("error");

      valueX.applyConstraint(invariant, "http://example.org/fhir/StructureDefinition/SomeProfile");

      assertEquals(2, valueX.getConstraint().size()); // Adds an additional constraint
      assertEquals(invariant.name, ((Map<?, ?>) valueX.getConstraint().get(1)).get("key"));
      assertStrictJsEqual(obj("key", invariant.name, "severity", invariant.severity.code, "human",
          invariant.description, "expression", invariant.expression, "xpath", invariant.xpath, "source",
          "http://example.org/fhir/StructureDefinition/SomeProfile"), valueX.getConstraint().get(1));
    }

    @Test
    @DisplayName("ElementDefinition #applyConstraints should apply a constraint with only fields specified on the the invariant")
    void onlySpecified() {
      Invariant invariant = new Invariant("SimpleInvariant");
      invariant.description = "The simplest invariant";
      invariant.severity = new FshCode("error");
      valueX.applyConstraint(invariant);

      assertEquals(2, valueX.getConstraint().size()); // Adds an additional constraint
      assertEquals(invariant.name, ((Map<?, ?>) valueX.getConstraint().get(1)).get("key"));
      assertStrictJsEqual(obj("key", invariant.name, "human", invariant.description, "severity",
          invariant.severity.code), valueX.getConstraint().get(1));
    }

    @Test
    @DisplayName("ElementDefinition #applyConstraints should apply a constraint to an ElementDefinition with no constraint array")
    void noConstraintArray() {
      Invariant invariant = new Invariant("MyInvariant");
      invariant.description = "An invariant with all metadata specified.";
      invariant.expression = "metadata.exists()";
      invariant.xpath = "exists(f:metadata)";
      invariant.severity = new FshCode("error");
      assertNull(valueId.getConstraint()); // constraint initially undefined

      valueId.applyConstraint(invariant, "http://example.org/fhir/StructureDefinition/SomeProfile");

      assertEquals(1, valueId.getConstraint().size()); // Adds an additional constraint
      assertEquals(invariant.name, ((Map<?, ?>) valueId.getConstraint().get(0)).get("key"));
      assertStrictJsEqual(obj("key", invariant.name, "severity", invariant.severity.code, "human",
          invariant.description, "expression", invariant.expression, "xpath", invariant.xpath, "source",
          "http://example.org/fhir/StructureDefinition/SomeProfile"), valueId.getConstraint().get(0));
    }
  }

  @Nested
  @DisplayName("#hasDiff")
  class HasDiff {
    @Test
    @DisplayName("ElementDefinition #hasDiff should always show a diff for brand new elements w/ no original captured")
    void brandNew() {
      ElementDefinition newElement = new ElementDefinition("newElement");
      assertTrue(newElement.hasDiff());
    }

    @Test
    @DisplayName("ElementDefinition #hasDiff should not have a diff if nothing changes after capturing original")
    void noChange() {
      valueX.setMin(1.0);
      valueX.captureOriginal();
      valueX.setMin(1.0);
      assertFalse(valueX.hasDiff());
    }

    @Test
    @DisplayName("ElementDefinition #hasDiff should have a diff if something changes after the original is captured")
    void change() {
      valueX.setMin(1.0);
      valueX.captureOriginal();
      valueX.setMin(2.0);
      assertTrue(valueX.hasDiff());
    }

    @Test
    @DisplayName("ElementDefinition #hasDiff should detect diff correctly with explicit choices")
    void explicitChoices() {
      // Because we don't (yet) capture choice properties in the ElementDefinition class itself,
      // we need special logic around them.  So we should test it.
      valueX.setProperty("fixedInteger", 1.0);
      valueX.captureOriginal();
      valueX.setProperty("fixedInteger", 2.0);
      assertTrue(valueX.hasDiff());
      valueX.setProperty("fixedInteger", 1.0);
      assertFalse(valueX.hasDiff());
    }

    @Test
    @DisplayName("ElementDefinition #hasDiff should detect diff correctly with id and extension on a primitive")
    void primitiveExtension() {
      shortOf(modifiedStatus).put("id", "short-id");
      modifiedStatus.captureOriginal();
      shortOf(modifiedStatus).put("id", "not-as-short-id");
      assertTrue(modifiedStatus.hasDiff());
      shortOf(modifiedStatus).put("id", "short-id");
      assertFalse(modifiedStatus.hasDiff());
    }

    @Test
    @DisplayName("ElementDefinition #hasDiff should detect diff correctly with id and extension on a type targetProfile")
    void targetProfileExtension() {
      mustSupportExt(modifiedSubject).put("valueBoolean", true);
      modifiedSubject.captureOriginal();
      mustSupportExt(modifiedSubject).put("valueBoolean", false);
      assertTrue(modifiedSubject.hasDiff());
      mustSupportExt(modifiedSubject).put("valueBoolean", true);
      assertFalse(modifiedSubject.hasDiff());
    }

    @Test
    @DisplayName("ElementDefinition #hasDiff should detect diffs and non-diffs correctly when elements are unfolded")
    void unfolded() {
      ElementDefinition code = el(observation, "Observation.code");
      code.unfold(fisher);
      ElementDefinition codeCoding = el(observation, "Observation.code.coding");
      ElementDefinition codeText = el(observation, "Observation.code.text");
      // Unfolded elements haven't been changed from their base definitions, so no diff...
      assertFalse(code.hasDiff());
      assertFalse(codeCoding.hasDiff());
      assertFalse(codeText.hasDiff());

      // Change just Observation.code.coding cardinality
      codeCoding.constrainCardinality(1, "*");

      // Only Observation.code.coding should have diff
      assertFalse(code.hasDiff());
      assertTrue(codeCoding.hasDiff());
      assertFalse(codeText.hasDiff());
    }

    @Test
    @DisplayName("ElementDefinition #hasDiff should detect a diff on a slice element with changed children")
    void sliceChangedChildren() {
      ElementDefinition vsCatCoding = el(resprate, "Observation.category:VSCat.coding");
      ElementDefinition vsCat = el(resprate, "Observation.category:VSCat");
      assertFalse(vsCatCoding.hasDiff());
      assertFalse(vsCat.hasDiff());
      vsCatCoding.setMin(2.0);
      assertTrue(vsCatCoding.hasDiff());
      assertTrue(vsCat.hasDiff());
    }

    @Test
    @DisplayName("ElementDefinition #hasDiff should detect a diff on a slice element with changed grandchildren")
    void sliceChangedGrandchildren() {
      ElementDefinition vsCatCodingId = el(resprate, "Observation.category:VSCat.coding.id");
      ElementDefinition vsCat = el(resprate, "Observation.category:VSCat");
      assertFalse(vsCatCodingId.hasDiff());
      assertFalse(vsCat.hasDiff());
      vsCatCodingId.setShort("id");
      assertTrue(vsCatCodingId.hasDiff());
      assertTrue(vsCat.hasDiff());
    }

    @Test
    @DisplayName("ElementDefinition #hasDiff should not detect a diff on a slice element with no changed descendents")
    void sliceNoChange() {
      ElementDefinition vsCatCoding = el(resprate, "Observation.category:VSCat.coding");
      ElementDefinition vsCat = el(resprate, "Observation.category:VSCat");
      assertFalse(vsCatCoding.hasDiff());
      assertFalse(vsCat.hasDiff());
      vsCatCoding.setMin(1.0);
      assertFalse(vsCatCoding.hasDiff());
      assertFalse(vsCat.hasDiff());
    }

    @Test
    @DisplayName("ElementDefinition #hasDiff should detect a diff on a sliced element with changed children")
    void slicedChangedChildren() {
      ElementDefinition catCoding = resprate.findElementByPath("category.coding", fisher);
      ElementDefinition cat = el(resprate, "Observation.category");
      assertFalse(catCoding.hasDiff());
      assertFalse(cat.hasDiff());
      catCoding.setMin(2.0);
      assertTrue(catCoding.hasDiff());
      assertTrue(cat.hasDiff());
    }

    @Test
    @DisplayName("ElementDefinition #hasDiff should detect a diff on a sliced element with changed grandchildren")
    void slicedChangedGrandchildren() {
      ElementDefinition catCodingId = resprate.findElementByPath("category.coding.id", fisher);
      ElementDefinition cat = el(resprate, "Observation.category");
      assertFalse(catCodingId.hasDiff());
      assertFalse(cat.hasDiff());
      catCodingId.setShort("id");
      assertTrue(catCodingId.hasDiff());
      assertTrue(cat.hasDiff());
    }

    @Test
    @DisplayName("ElementDefinition #hasDiff should not detect a diff on a sliced element with no changed descendents")
    void slicedNoChange() {
      ElementDefinition vsCatCoding = el(resprate, "Observation.category:VSCat.coding");
      ElementDefinition cat = el(resprate, "Observation.category");
      assertFalse(vsCatCoding.hasDiff());
      assertFalse(cat.hasDiff());
      vsCatCoding.setMin(2.0);
      assertTrue(vsCatCoding.hasDiff());
      // Changed child of slice, not of sliced element, should have no change
      assertFalse(cat.hasDiff());
    }
  }

  @Nested
  @DisplayName("#calculateDiff")
  class CalculateDiff {
    @Test
    @DisplayName("ElementDefinition #calculateDiff should have diff containing everything when there is no captured original")
    void everything() {
      ElementDefinition newElement = new ElementDefinition("newElement");
      newElement.setMin(0.0);
      newElement.setMax("1");
      newElement.setType(types("string"));
      assertSameState(newElement, newElement.calculateDiff());
    }

    @Test
    @DisplayName("ElementDefinition #calculateDiff should have a diff w/ only id and path when nothing changes after capturing original")
    void onlyIdAndPath() {
      valueX.setMin(1.0);
      valueX.captureOriginal();
      valueX.setMin(1.0);
      ElementDefinition diff = valueX.calculateDiff();
      assertEquals("Observation.value[x]", diff.getId());
      assertEquals("Observation.value[x]", diff.getPath());
      assertEquals(2, diff.toJSON().size());
    }

    @Test
    @DisplayName("ElementDefinition #calculateDiff should have a diff containing changed elements after the original is captured")
    void changed() {
      valueX.setMin(1.0);
      valueX.setMax("2");
      valueX.captureOriginal();
      valueX.setMin(2.0);
      ElementDefinition diff = valueX.calculateDiff();
      assertEquals("Observation.value[x]", diff.getId());
      assertEquals("Observation.value[x]", diff.getPath());
      assertEquals(2.0, diff.getMin());
      assertEquals(3, diff.toJSON().size());
    }

    @Test
    @DisplayName("ElementDefinition #calculateDiff should calculate diff correctly with explicit choices")
    void explicitChoices() {
      // Because we don't (yet) capture choice properties in the ElementDefinition class itself,
      // we need special logic around them.  So we should test it.
      valueX.setProperty("fixedInteger", 1.0);
      valueX.captureOriginal();
      valueX.setProperty("fixedInteger", 2.0);
      ElementDefinition diff = valueX.calculateDiff();
      assertEquals("Observation.value[x]", diff.getId());
      assertEquals("Observation.value[x]", diff.getPath());
      assertEquals(2.0, diff.getProperty("fixedInteger"));
      assertEquals(3, diff.toJSON().size());
    }

    @Test
    @DisplayName("ElementDefinition #calculateDiff should calculate diff correctly with id and extension on a primitive element")
    void primitiveExtension() {
      shortOf(modifiedStatus).put("id", "short-id");
      modifiedStatus.captureOriginal();
      shortOf(modifiedStatus).put("id", "not-as-short-id");
      ElementDefinition diff = modifiedStatus.calculateDiff();
      assertEquals("Observation.status", diff.getId());
      assertEquals("Observation.status", diff.getPath());
      // NOTE: We diff property by property, but diffs are not deep. So diff contains whole _short.
      assertJsonEqual("{\"id\": \"not-as-short-id\", \"extension\": {\"url\": \"http://example.org/extensions/short\", "
          + "\"valueString\": \"bogus\"}}", diff.getProperty("_short"));
      assertEquals(3, diff.toJSON().size());
    }

    @Test
    @DisplayName("ElementDefinition #calculateDiff should calculate diff correctly with id and extension on a type targetProfile")
    void targetProfileExtension() {
      mustSupportExt(modifiedSubject).put("valueBoolean", true);
      modifiedSubject.captureOriginal();
      mustSupportExt(modifiedSubject).put("valueBoolean", false);
      ElementDefinition diff = modifiedSubject.calculateDiff();
      assertEquals("Observation.subject", diff.getId());
      assertEquals("Observation.subject", diff.getPath());
      // NOTE: We diff property by property, but diffs are not deep. So diff contains whole type.
      assertEquals("Reference", diff.getType().get(0).getCode());
      assertJsEqual(List.of("http://hl7.org/fhir/StructureDefinition/Patient",
          "http://hl7.org/fhir/StructureDefinition/Group", "http://hl7.org/fhir/StructureDefinition/Device",
          "http://hl7.org/fhir/StructureDefinition/Location"), diff.getType().get(0).getTargetProfile());
      assertJsonEqual("[null, null, {\"extension\": [{\"url\": "
          + "\"http://hl7.org/fhir/StructureDefinition/elementdefinition-type-must-support\", \"valueBoolean\": false}]}, "
          + "null]", diff.getType().get(0).get_targetProfile());
      assertEquals(3, diff.toJSON().size());
    }

    @Test
    @DisplayName("ElementDefinition #calculateDiff should include only new constraints in a diff when constraints are added")
    void newConstraints() {
      Invariant myInvariant = new Invariant("inv-1");
      myInvariant.severity = new FshCode("warning");
      myInvariant.description = "This is a good idea.";
      valueX.applyConstraint(myInvariant, "http://example.org");
      ElementDefinition diff = valueX.calculateDiff();
      assertTrue(valueX.getConstraint().size() > diff.getConstraint().size());
      assertEquals(1, diff.getConstraint().size());
      assertJsonEqual("{\"key\": \"inv-1\", \"source\": \"http://example.org\", \"severity\": \"warning\", "
          + "\"human\": \"This is a good idea.\"}", diff.getConstraint().get(0));
    }

    @Test
    @DisplayName("ElementDefinition #calculateDiff should not include an empty mapping property in the diff")
    void noEmptyConstraint() {
      valueX.setInstancePropertyByPath("constraint[0].human", "All FHIR elements must have a @value or children",
          fisher);
      ElementDefinition diff = valueX.calculateDiff();
      assertNull(diff.getConstraint());
    }

    @Test
    @DisplayName("ElementDefinition #calculateDiff should include only new mappings in a diff when mappings are added")
    void newMappings() {
      valueX.applyMapping("test1", "test1.value", "Test value mapping", null);
      ElementDefinition diff = valueX.calculateDiff();
      assertTrue(valueX.getMapping().size() > diff.getMapping().size());
      assertEquals(1, diff.getMapping().size());
      assertJsonEqual("{\"identity\": \"test1\", \"map\": \"test1.value\", \"comment\": \"Test value mapping\"}",
          diff.getMapping().get(0));
    }

    @Test
    @DisplayName("ElementDefinition #calculateDiff should not include an empty mapping property in the diff")
    void noEmptyMapping() {
      valueX.setInstancePropertyByPath("mapping[0].identity", "sct-concept", fisher);
      valueX.setInstancePropertyByPath("mapping[0].map", "< 441742003 |Evaluation finding|", fisher);
      ElementDefinition diff = valueX.calculateDiff();
      assertNull(diff.getMapping());
    }

    @Test
    @DisplayName("ElementDefinition #calculateDiff should always include type in the diff for a slice of a choice element")
    void sliceOfChoiceType() {
      // constrain value[x] to Quantity so that, when sliced, the original on the slice will have only that type
      OnlyRule onlyRule = new OnlyRule("value[x]");
      onlyRule.types = List.of(new OnlyRuleType("Quantity"));
      valueX.constrainType(onlyRule, fisher);
      valueX.sliceIt("type", "$this");
      // use the exact ElementDefinitionType on value[x] to ensure deep equality
      ElementDefinitionType matchingType = valueX.getType().get(0);
      ElementDefinition valueQuantity = valueX.addSlice("valueQuantity", matchingType);
      ElementDefinition diff = valueQuantity.calculateDiff();
      assertNotNull(diff.getType());
      assertJsEqual(List.of(matchingType), diff.getType());
    }
  }

  @Nested
  @DisplayName("#parent")
  class Parent {
    @Test
    @DisplayName("ElementDefinition #parent should find the parent element of an element in a BackboneElement")
    void backbone() {
      ElementDefinition child = byPath(observation, "Observation.component.interpretation");
      ElementDefinition parent = child.parent();
      assertEquals("Observation.component", parent.getId());
    }

    @Test
    @DisplayName("ElementDefinition #parent should find the parent element of a top-level element")
    void topLevel() {
      ElementDefinition child = byPath(observation, "Observation.component");
      ElementDefinition parent = child.parent();
      assertEquals("Observation", parent.getId());
    }

    @Test
    @DisplayName("ElementDefinition #parent should find return undefined for the root element")
    void root() {
      ElementDefinition root = byPath(observation, "Observation");
      ElementDefinition parent = root.parent();
      assertNull(parent);
    }
  }

  @Nested
  @DisplayName("#children")
  class Children {
    @Test
    @DisplayName("ElementDefinition #children should find the children of a backbone element")
    void backbone() {
      ElementDefinition parent = byPath(observation, "Observation.component");
      List<ElementDefinition> children = parent.children();
      assertEquals(8, children.size());
      assertEquals("Observation.component.id", children.get(0).getId());
      assertEquals("Observation.component.extension", children.get(1).getId());
      assertEquals("Observation.component.modifierExtension", children.get(2).getId());
      assertEquals("Observation.component.code", children.get(3).getId());
      assertEquals("Observation.component.value[x]", children.get(4).getId());
      assertEquals("Observation.component.dataAbsentReason", children.get(5).getId());
      assertEquals("Observation.component.interpretation", children.get(6).getId());
      assertEquals("Observation.component.referenceRange", children.get(7).getId());
    }

    @Test
    @DisplayName("ElementDefinition #children should find no children for an element that is a leaf node")
    void leaf() {
      ElementDefinition parent = byPath(observation, "Observation.code");
      List<ElementDefinition> children = parent.children();
      assertTrue(children.isEmpty());
    }
  }

  static final String[] COMPONENT_CHILDREN = {"id", "extension", "modifierExtension", "code", "value[x]",
      "dataAbsentReason", "interpretation", "referenceRange"};

  static void assertComponentChildren(String prefix, List<ElementDefinition> l, int start) {
    for (int i = 0; i < COMPONENT_CHILDREN.length; i++) {
      assertEquals(prefix + "." + COMPONENT_CHILDREN[i], l.get(start + i).getId());
    }
  }

  @Nested
  @DisplayName("#unfold")
  class Unfold {
    @Test
    @DisplayName("ElementDefinition #unfold should add children when an element has a single type")
    void singleType() {
      int numOriginalElements = observation.elements.size();
      int codeIdx = indexByPath(observation, "Observation.code");
      ElementDefinition parent = observation.elements.get(codeIdx);
      assertEquals("Observation.subject", observation.elements.get(codeIdx + 1).getId());
      List<ElementDefinition> newElements = parent.unfold(fisher);
      assertEquals(4, newElements.size());
      assertEquals("Observation.code.id", newElements.get(0).getId());
      assertEquals("Observation.code.extension", newElements.get(1).getId());
      assertEquals("Observation.code.coding", newElements.get(2).getId());
      assertEquals("Observation.code.text", newElements.get(3).getId());
      assertEquals(numOriginalElements + 4, observation.elements.size());
      assertEquals("Observation.code.id", observation.elements.get(codeIdx + 1).getId());
      assertEquals("Observation.code.extension", observation.elements.get(codeIdx + 2).getId());
      assertEquals("Observation.code.coding", observation.elements.get(codeIdx + 3).getId());
      assertEquals("Observation.code.text", observation.elements.get(codeIdx + 4).getId());
      assertEquals("Observation.subject", observation.elements.get(codeIdx + 5).getId());
    }

    @Test
    @DisplayName("ElementDefinition #unfold should add children from the Structure Definition when they exist")
    void fromStructureDefinition() {
      int numOriginalElements = observation.elements.size();
      ElementDefinition component = byPath(observation, "Observation.component");
      component.setSlicing(valueCodeSlicing());
      ElementDefinition componentSlice = component.addSlice("FooSlice");
      int componentSliceIdx = indexById(observation, componentSlice.getId());
      List<ElementDefinition> newElements = componentSlice.unfold(fisher);
      assertEquals(8, newElements.size());
      assertComponentChildren("Observation.component:FooSlice", newElements, 0);
      assertEquals(numOriginalElements + 9, observation.elements.size());
      assertComponentChildren("Observation.component:FooSlice", observation.elements, componentSliceIdx + 1);
    }

    @Test
    @DisplayName("ElementDefinition #unfold should add children in the right location when one slicename is a substring of another")
    void substringSliceNames() {
      // Tests bug reported here: https://github.com/FHIR/sushi/issues/122
      int numOriginalElements = observation.elements.size();
      ElementDefinition component = byPath(observation, "Observation.component");
      component.setSlicing(valueCodeSlicing());
      // Set up the slices first
      ElementDefinition fooSlice = component.addSlice("FooSlice");
      ElementDefinition fooSliceBar = component.addSlice("FooSliceBar");
      // Then unfold them
      List<ElementDefinition> newFooSliceElements = fooSlice.unfold(fisher);
      assertEquals(8, newFooSliceElements.size());
      assertComponentChildren("Observation.component:FooSlice", newFooSliceElements, 0);
      List<ElementDefinition> newFooSliceBarElements = fooSliceBar.unfold(fisher);
      assertEquals(8, newFooSliceBarElements.size());
      assertComponentChildren("Observation.component:FooSliceBar", newFooSliceBarElements, 0);
      assertEquals(numOriginalElements + 18, observation.elements.size());
      // Then check they are in right order, starting with plain old Observation.component
      int componentIdx = indexById(observation, "Observation.component");
      assertComponentChildren("Observation.component", observation.elements, componentIdx + 1);
      assertEquals("Observation.component:FooSlice", observation.elements.get(componentIdx + 9).getId());
      assertComponentChildren("Observation.component:FooSlice", observation.elements, componentIdx + 10);
      assertEquals("Observation.component:FooSliceBar", observation.elements.get(componentIdx + 18).getId());
      assertComponentChildren("Observation.component:FooSliceBar", observation.elements, componentIdx + 19);
    }

    @Test
    @DisplayName("ElementDefinition #unfold should not add any children when an element has multiple types")
    void multipleTypes() {
      int numOriginalElements = observation.elements.size();
      int valueIdx = indexByPath(observation, "Observation.value[x]");
      ElementDefinition parent = observation.elements.get(valueIdx);
      assertEquals("Observation.dataAbsentReason", observation.elements.get(valueIdx + 1).getId());
      List<ElementDefinition> newElements = parent.unfold(fisher);
      assertTrue(newElements.isEmpty());
      assertEquals(numOriginalElements, observation.elements.size());
      assertEquals("Observation.dataAbsentReason", observation.elements.get(valueIdx + 1).getId());
    }

    @Test
    @DisplayName("ElementDefinition #unfold should not inherit uninheritable extensions when unfolding a type")
    void uninheritable() {
      StructureDefinition specialObservation = fisher.fishForStructureDefinition("ObservationWithSpecialCode");
      assertNotNull(specialObservation);
      ElementDefinition specialCode = el(specialObservation, "Observation.code");
      assertNotNull(specialCode);
      ElementDefinition specialCodeText = el(specialObservation, "Observation.code.text");
      assertNull(specialCodeText);
      specialCode.unfold(fisher);
      specialCodeText = el(specialObservation, "Observation.code.text");
      assertNotNull(specialCodeText);
      // The following extension should be stripped out as uninherited extension:
      // { url: 'http://hl7.org/fhir/StructureDefinition/structuredefinition-standards-status', valueCode: 'draft' },
      //
      // BUT the following extensions should remain:
      assertJsonEqual("[{\"url\": \"http://hl7.org/fhir/StructureDefinition/elementdefinition-translatable\", "
          + "\"valueBoolean\": true}, {\"url\": \"http://hl7.org/fhir/StructureDefinition/elementdefinition-question\", "
          + "\"valueString\": \"What does the code mean?\"}]", specialCodeText.getExtension());
    }

    @Test
    @DisplayName("ElementDefinition #unfold should add a slice from the structure definition if it exists, while preserving all constraints")
    void sliceFromStructureDefinition() {
      ElementDefinition extension = byPath(observation, "Observation.component.extension");
      extension.setSlicing(obj("discriminator", list(obj("type", "value", "path", "url")), "ordered", false, "rules",
          "open"));
      ElementDefinition extensionSlice = extension.addSlice("Test");
      extensionSlice.getType().get(0).setProfile(strings("http://example.org/fhir/StructureDefinition/ExampleExtension"));
      ElementDefinition component = byPath(observation, "Observation.component");
      component.setSlicing(valueCodeSlicing());
      ElementDefinition componentSlice = component.addSlice("FooSlice");
      List<ElementDefinition> newElements = componentSlice.unfold(fisher);
      ElementDefinition fooSliceExtensionTest = findId(newElements, "Observation.component:FooSlice.extension:Test");
      assertJsEqual(extensionSlice.getType(), fooSliceExtensionTest.calculateDiff().getType());
    }

    @Test
    @DisplayName("ElementDefinition #unfold should unfold an element with a profile that includes a version")
    void profileWithVersion() {
      ElementDefinition referenceRange = el(observation, "Observation.referenceRange.low"); // The element will exist based on the test fixtures

      // Set type with a profile that has a version number
      referenceRange.getType().get(0).setProfile(strings("http://hl7.org/fhir/StructureDefinition/SimpleQuantity|1.0.0"));

      List<ElementDefinition> newElements = referenceRange.unfold(fisher);
      assertEquals(7, newElements.size()); // It should unfold elements on referenceRange.low
      assertEquals(1, loggerSpy.getAllMessages("warn").size());
      assertTrue(Pattern.compile(
          "SimpleQuantity|1\\.0\\.0 is based on SimpleQuantity version 1\\.0\\.0, but SUSHI found version 4\\.0\\.1")
          .matcher(loggerSpy.getLastMessage("warn")).find());
    }

    @Test
    @DisplayName("ElementDefinition #unfold should fall back to the type if a profile cannot be fished while unfolding")
    void profileFallback() {
      ElementDefinition referenceRange = el(observation, "Observation.referenceRange.low"); // The element will exist based on the test fixtures

      // Set type with a profile that doesn't exist in our test definitions
      referenceRange.getType().get(0).setProfile(strings("http://hl7.org/fhir/StructureDefinition/FakeQuantity"));

      List<ElementDefinition> newElements = referenceRange.unfold(fisher);
      assertEquals(7, newElements.size()); // It should unfold elements on referenceRange.low
      assertEquals(1, loggerSpy.getAllMessages("warn").size());
      assertTrue(Pattern.compile(
          "SUSHI tried to find profile .*FakeQuantity but could not find it and instead will try to use Quantity")
          .matcher(loggerSpy.getLastMessage("warn")).find());
    }

    @Test
    @DisplayName("ElementDefinition #unfold should unfold a slice using the profile on a slice when sliced element has been unfolded and has a different profile")
    void sliceDifferentProfile() {
      ElementDefinition telecom = el(practitioner, "Practitioner.telecom");
      // * telecom only parent-contact-point
      OnlyRule baseElementProfile = new OnlyRule("telecom");
      baseElementProfile.types = List.of(new OnlyRuleType("parent-contact-point"));
      telecom.constrainType(baseElementProfile, fisher);
      // telecom contains MySlice 0..*
      telecom.sliceIt("value", "value", false, "open");
      ElementDefinition telecomSlice = telecom.addSlice("MySlice");
      // telecom[MySlice] only child-contact-point
      OnlyRule sliceProfile = new OnlyRule("telecom[MySlice]");
      sliceProfile.types = List.of(new OnlyRuleType("child-contact-point"));
      telecomSlice.constrainType(sliceProfile, fisher);

      telecom.unfold(fisher);
      List<ElementDefinition> newElements = telecomSlice.unfold(fisher);
      // the unfolded elements will include the Local slice from the parent profile,
      ElementDefinition localSlice = findId(newElements, "Practitioner.telecom:MySlice.extension:Local");
      assertNotNull(localSlice);
      // and the Area slice from the child profile.
      ElementDefinition areaSlice = findId(newElements, "Practitioner.telecom:MySlice.extension:Area");
      assertNotNull(areaSlice);
    }

    @Test
    @DisplayName("ElementDefinition #unfold should unfold a slice using the profile on a slice when the sliced element has been unfolded and does not have a profile")
    void sliceProfileNoSlicedProfile() {
      ElementDefinition telecom = el(practitioner, "Practitioner.telecom");
      // telecom contains MySlice 0..*
      telecom.sliceIt("value", "value", false, "open");
      ElementDefinition telecomSlice = telecom.addSlice("MySlice");
      // telecom[MySlice] only parent-contact-point
      OnlyRule sliceProfile = new OnlyRule("telecom[MySlice]");
      sliceProfile.types = List.of(new OnlyRuleType("parent-contact-point"));
      telecomSlice.constrainType(sliceProfile, fisher);

      telecom.unfold(fisher);
      List<ElementDefinition> newElements = telecomSlice.unfold(fisher);
      ElementDefinition localSlice = findId(newElements, "Practitioner.telecom:MySlice.extension:Local");
      assertNotNull(localSlice);
    }

    @Test
    @DisplayName("ElementDefinition #unfold should unfold a slice using the profile on the sliced element when the slice does not have its own profile")
    void slicedElementProfile() {
      ElementDefinition telecom = el(practitioner, "Practitioner.telecom");
      // telecom contains MySlice 0..*
      telecom.sliceIt("value", "value", false, "open");
      ElementDefinition telecomSlice = telecom.addSlice("MySlice");
      // * telecom only parent-contact-point
      OnlyRule baseElementProfile = new OnlyRule("telecom");
      baseElementProfile.types = List.of(new OnlyRuleType("parent-contact-point"));
      telecom.constrainType(baseElementProfile, fisher);

      List<ElementDefinition> newElements = telecomSlice.unfold(fisher);
      ElementDefinition localSlice = findId(newElements, "Practitioner.telecom:MySlice.extension:Local");
      assertNotNull(localSlice);
    }

    @Test
    @DisplayName("ElementDefinition #unfold should not throw an error when unfolding a slice and the sliced element is not found")
    void slicedElementMissing() {
      ElementDefinition telecom = byPath(practitioner, "Practitioner.telecom");
      telecom.setSlicing(obj("ordered", false, "rules", "open", "discriminator",
          list(obj("type", "value", "path", "system"))));
      ElementDefinition telecomSlice = telecom.addSlice("FooSlice");
      int telecomIndex = indexByPath(practitioner, "Practitioner.telecom");
      practitioner.elements.remove(telecomIndex);
      List<ElementDefinition> newElements = telecomSlice.unfold(fisher);
      assertEquals(7, newElements.size());
      assertEquals("Practitioner.telecom:FooSlice.id", newElements.get(0).getId());
      assertEquals("Practitioner.telecom:FooSlice.extension", newElements.get(1).getId());
      assertEquals("Practitioner.telecom:FooSlice.system", newElements.get(2).getId());
      assertEquals("Practitioner.telecom:FooSlice.value", newElements.get(3).getId());
      assertEquals("Practitioner.telecom:FooSlice.use", newElements.get(4).getId());
      assertEquals("Practitioner.telecom:FooSlice.rank", newElements.get(5).getId());
      assertEquals("Practitioner.telecom:FooSlice.period", newElements.get(6).getId());
    }
  }

  @Nested
  @DisplayName("#removeUninheritedExtensions")
  class RemoveUninheritedExtensions {
    @Test
    @DisplayName("ElementDefinition #removeUninheritedExtensions should remove uninherited extensions from an element")
    void remove() {
      StructureDefinition specialCodeableConcept = fisher.fishForStructureDefinition("SpecialCodeableConcept");
      ElementDefinition text = el(specialCodeableConcept, "CodeableConcept.text").clone();
      assertJsonEqual("[{\"url\": \"http://hl7.org/fhir/StructureDefinition/elementdefinition-translatable\", "
          + "\"valueBoolean\": true}, {\"url\": "
          + "\"http://hl7.org/fhir/StructureDefinition/structuredefinition-standards-status\", \"valueCode\": \"draft\"}, "
          + "{\"url\": \"http://hl7.org/fhir/StructureDefinition/elementdefinition-question\", "
          + "\"valueString\": \"What does the code mean?\"}]", text.getExtension());
      text.removeUninheritedExtensions();
      // The following extension should be stripped out as uninherited extension:
      // { url: 'http://hl7.org/fhir/StructureDefinition/structuredefinition-standards-status', valueCode: 'draft' },
      //
      // BUT the following extensions should remain:
      assertJsonEqual("[{\"url\": \"http://hl7.org/fhir/StructureDefinition/elementdefinition-translatable\", "
          + "\"valueBoolean\": true}, {\"url\": \"http://hl7.org/fhir/StructureDefinition/elementdefinition-question\", "
          + "\"valueString\": \"What does the code mean?\"}]", text.getExtension());
    }
  }

  @Nested
  @DisplayName("#clone")
  class Clone {
    @Test
    @DisplayName("ElementDefinition #clone should clone an element so that changes in the clone are not reflected in the original")
    void independent() {
      ElementDefinition clone = valueX.clone(false);
      assertSameState(valueX, clone); // value-based equality (not sameness)
      clone.setDefinition("I am a clone");
      assertFalse(org.hl7.fhir.sushi.js.Js.isEqual(edState(valueX), edState(clone)));
    }

    @Test
    @DisplayName("ElementDefinition #clone should keep the same structdef reference")
    void sameStructDef() {
      ElementDefinition clone = valueX.clone();
      assertSame(valueX.structDef, clone.structDef);
    }

    @Test
    @DisplayName("ElementDefinition #clone should clear original by default")
    void clearsOriginal() {
      valueX.captureOriginal();
      ElementDefinition clone = valueX.clone();
      // Best way to check state of original is by checking hasDiff
      assertFalse(valueX.hasDiff());
      assertTrue(clone.hasDiff());
    }
  }

  @Nested
  @DisplayName("#ElementDefinitionType get code")
  class TypeGetCode {
    @Test
    @DisplayName("ElementDefinition #ElementDefinitionType get code should return valueUrl value when extension of fhir-type (R4)")
    void valueUrl() {
      String code = valueId.getType().get(0).getCode();
      String trueCodeValue = valueId.getType().get(0).getActualCode();
      assertEquals("string", code);
      assertEquals("http://hl7.org/fhirpath/System.String", trueCodeValue);
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("ElementDefinition #ElementDefinitionType get code should return valueUri value when extension of fhir-type (R5)")
    void valueUri() {
      // Create an R5 representation of the valueId (using valueUri instead of valueUrl)
      Map<String, Object> jsonValueIdR5 = Js.cloneDeep(jsonValueId);
      Map<String, Object> ext = (Map<String, Object>) ((List<Object>) ((Map<String, Object>) ((List<Object>)
          jsonValueIdR5.get("type")).get(0)).get("extension")).get(0);
      ext.remove("valueUrl");
      ext.put("valueUri", "string");
      ElementDefinition valueIdR5 = ElementDefinition.fromJSON(jsonValueIdR5);

      String code = valueIdR5.getType().get(0).getCode();
      String trueCodeValue = valueIdR5.getType().get(0).getActualCode();
      assertEquals("string", code);
      assertEquals("http://hl7.org/fhirpath/System.String", trueCodeValue);
    }

    @Test
    @DisplayName("ElementDefinition #ElementDefinitionType get code should return code value when extension is not of fhir-type")
    void plainCode() {
      String code = valueX.getType().get(0).getCode();
      assertEquals("Quantity", code);
    }
  }

  static void assertMessageMatches(String jsRegex, ValidationError e) {
    assertTrue(Pattern.compile(jsRegex).matcher(e.getMessage()).find(),
        () -> "expected /" + jsRegex + "/ but was: " + e.getMessage());
  }

  @Nested
  @DisplayName("#validate")
  class Validate {
    @Test
    @DisplayName("ElementDefinition #validate should be valid when an element has a slicing.rules set to a valid value")
    void validRules() {
      ElementDefinition clone = valueX.clone(false);
      clone.setSlicing(obj("rules", "open"));
      assertEquals(0, clone.validate().size());
    }

    @Test
    @DisplayName("ElementDefinition #validate should be invalid when an element has no slicing.rules")
    void noRules() {
      ElementDefinition clone = valueX.clone(false);
      clone.setSlicing(obj("rules", null));
      List<ValidationError> validationErrors = clone.validate();
      assertEquals(1, validationErrors.size());
      assertMessageMatches("slicing.rules: Missing required value", validationErrors.get(0));
    }

    @Test
    @DisplayName("ElementDefinition #validate should be invalid when an element has slicing.rules set to an invalid value")
    void invalidRules() {
      ElementDefinition clone = valueX.clone(false);
      clone.setSlicing(obj("rules", "foo"));
      List<ValidationError> validationErrors = clone.validate();
      assertEquals(1, validationErrors.size());
      assertMessageMatches(
          "slicing.rules: Invalid value: #foo. Value must be selected from one of the following: #closed, #open, #openAtEnd",
          validationErrors.get(0));
    }

    @Test
    @DisplayName("ElementDefinition #validate should be valid when an element has a valid slicing.discriminator")
    void validDiscriminator() {
      ElementDefinition clone = valueX.clone(false);
      clone.setSlicing(obj("rules", "open", "discriminator", list(obj("path", "test", "type", "value"))));
      List<ValidationError> validationErrors = clone.validate();
      assertEquals(0, validationErrors.size());
    }

    @Test
    @DisplayName("ElementDefinition #validate should be invalid when an element is missing slicing.discriminator.type")
    void missingDiscriminatorType() {
      ElementDefinition clone = valueX.clone(false);
      clone.setSlicing(obj("rules", "open", "discriminator", list(obj("path", "test", "type", Js.UNDEFINED))));
      List<ValidationError> validationErrors = clone.validate();
      assertEquals(1, validationErrors.size());
      assertMessageMatches("slicing.discriminator\\[0\\].type: Missing required value", validationErrors.get(0));
    }

    @Test
    @DisplayName("ElementDefinition #validate should be invalid when an element is missing slicing.discriminator.path")
    void missingDiscriminatorPath() {
      ElementDefinition clone = valueX.clone(false);
      clone.setSlicing(obj("rules", "open", "discriminator", list(obj("path", Js.UNDEFINED, "type", "value"))));
      List<ValidationError> validationErrors = clone.validate();
      assertEquals(1, validationErrors.size());
      assertMessageMatches("slicing.discriminator\\[0\\].path: Missing required value", validationErrors.get(0));
    }

    @Test
    @DisplayName("ElementDefinition #validate should be invalid when an element has slicing.discriminator.type set to an invalid value")
    void invalidDiscriminatorType() {
      ElementDefinition clone = valueX.clone(false);
      clone.setSlicing(obj("rules", "open", "discriminator", list(obj("path", "test", "type", "foo"))));
      List<ValidationError> validationErrors = clone.validate();
      assertEquals(1, validationErrors.size());
      assertMessageMatches(
          "slicing.discriminator\\[0\\].type: Invalid value: #foo. Value must be selected from one of the following: #value, #exists, #pattern, #type, #profile",
          validationErrors.get(0));
    }

    @Test
    @DisplayName("ElementDefinition #validate should be invalid when an element has a position discriminator, ordered is true, and the FHIR version is older than R5")
    void positionR4() {
      ElementDefinition clone = valueX.clone(false);
      clone.setSlicing(obj("rules", "open", "ordered", true, "discriminator",
          list(obj("path", "$this", "type", "position"))));
      List<ValidationError> validationErrors = clone.validate();
      assertEquals(1, validationErrors.size());
      assertMessageMatches(
          "slicing.discriminator\\[0\\].type: Invalid value: #position. Value must be selected from one of the following: #value, #exists, #pattern, #type, #profile",
          validationErrors.get(0));
    }
  }

}
