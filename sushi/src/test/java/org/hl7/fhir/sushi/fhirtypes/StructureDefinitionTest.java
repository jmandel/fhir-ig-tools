package org.hl7.fhir.sushi.fhirtypes;

import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertJsEqual;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertMatches;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.at;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.json;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.sushi.errors.MissingSnapshotError;
import org.hl7.fhir.sushi.export.FHIRExporter;
import org.hl7.fhir.sushi.export.Package;
import org.hl7.fhir.sushi.export.StructureDefinitionExporter;
import org.hl7.fhir.sushi.fshtypes.Invariant;
import org.hl7.fhir.sushi.fshtypes.Logical;
import org.hl7.fhir.sushi.fshtypes.Mapping;
import org.hl7.fhir.sushi.fshtypes.Profile;
import org.hl7.fhir.sushi.fshtypes.Resource;
import org.hl7.fhir.sushi.fshtypes.rules.AddElementRule;
import org.hl7.fhir.sushi.fshtypes.rules.ObeysRule;
import org.hl7.fhir.sushi.fshtypes.rules.OnlyRule;
import org.hl7.fhir.sushi.fshtypes.rules.OnlyRuleType;
import org.hl7.fhir.sushi.importer.FSHDocument;
import org.hl7.fhir.sushi.importer.FSHTank;
import org.hl7.fhir.sushi.js.Js;
import org.hl7.fhir.sushi.js.JsObject;
import org.hl7.fhir.sushi.js.Json;
import org.hl7.fhir.sushi.testhelpers.MinimalConfig;
import org.hl7.fhir.sushi.testhelpers.TestFisher;
import org.hl7.fhir.sushi.utils.FishableType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// port of sushi/test/fhirtypes/StructureDefinition.test.ts (SUSHI 3.21.1): '#fromJSON', '#toJSON', '#newElement',
// '#addElement', '#findElement'
@DisplayName("StructureDefinition")
class StructureDefinitionTest {
  TestFisher fisher;
  StructureDefinition observation;
  StructureDefinition modifiedObservation;
  StructureDefinition planDefinition;
  StructureDefinition usCoreObservation;
  StructureDefinition resprate;

  @BeforeEach
  void beforeEach() {
    fisher = SdTestSupport.fisher();
    loggerSpy.reset();
    observation = StructureDefinition.fromJSON(SdTestSupport.jsonObservation);
    modifiedObservation = StructureDefinition.fromJSON(SdTestSupport.jsonModifiedObservation);
    planDefinition = StructureDefinition.fromJSON(SdTestSupport.jsonPlanDefinition);
    usCoreObservation = StructureDefinition.fromJSON(SdTestSupport.jsonUsCoreObservation);
    resprate = fisher.fishForStructureDefinition("resprate");
  }

  static ElementDefinition find(StructureDefinition sd, String id) {
    for (ElementDefinition e : sd.elements) if (id.equals(e.getId())) return e;
    return null;
  }

  static int indexOf(StructureDefinition sd, String id) {
    for (int i = 0; i < sd.elements.size(); i++) if (id.equals(sd.elements.get(i).getId())) return i;
    return -1;
  }

  static List<ElementDefinitionType> types(String... codes) {
    List<ElementDefinitionType> l = new ArrayList<>();
    for (String c : codes) l.add(new ElementDefinitionType(c));
    return l;
  }

  @SuppressWarnings("unchecked")
  static List<Object> list(Object o, String path) {
    return (List<Object>) at(o, path);
  }

  /** {@code array.find(e => e.id === id)} over JSON elements. */
  static Object findJson(List<Object> elements, String key, String value) {
    for (Object e : elements) if (value.equals(Common.getProp(e, key))) return e;
    return null;
  }

  /** Jest toStrictEqual over JSON output: same keys (no undefined members), same values. */
  static void assertStrictJson(Object expected, Object actual) {
    assertEquals(Json.stringify(Json.parse(Json.stringify(expected))), Json.stringify(actual));
    assertJsEqual(expected, actual);
  }

  static final List<ElementDefinitionType> VALUE_X_TYPES = types("Quantity", "CodeableConcept", "string", "boolean",
      "integer", "Range", "Ratio", "SampledData", "time", "dateTime", "Period");

  @Nested
  @DisplayName("#fromJSON")
  class FromJSON {
    @Test
    @DisplayName("StructureDefinition #fromJSON should load a resource properly")
    void t1() {
      assertEquals("Observation", observation.getId());
      assertEquals("2019-11-01T09:29:23.356+11:00", at(observation.getMeta(), "lastUpdated"));
      assertEquals(6, observation.getExtension().size());
      assertJsEqual(json("{\"url\": \"http://hl7.org/fhir/StructureDefinition/structuredefinition-category\","
          + " \"valueString\": \"Clinical.Diagnostics\"}"), observation.getExtension().get(0));
      assertEquals(50, observation.elements.size());
      ElementDefinition valueX = observation.elements.get(21);
      assertFalse(valueX.hasDiff());
      assertEquals("Observation.value[x]", valueX.getId());
      assertEquals("Observation.value[x]", valueX.getPath());
      assertEquals(0.0, valueX.getMin());
      assertEquals("1", valueX.getMax());
      assertJsEqual(VALUE_X_TYPES, valueX.getType());
    }

    @Test
    @DisplayName("StructureDefinition #fromJSON should load a resource properly when there are ids and extensions on primitives")
    void t2() {
      assertEquals("Observation", modifiedObservation.getId());
      assertEquals("http://hl7.org/fhir/StructureDefinition/DomainResource", modifiedObservation.getBaseDefinition());
      assertJsEqual(json("{\"id\": \"baseDefinition-extension\", \"extension\": [{\"url\":"
          + " \"http://hl7.org/fhir/StructureDefinition/structuredefinition-codegen-super\","
          + " \"valueString\": \"MetadataResource\"}]}"), modifiedObservation.getProperty("_baseDefinition"));
    }

    @Test
    @DisplayName("StructureDefinition #fromJSON should load a resource properly without capturing originals")
    void t3() {
      observation = StructureDefinition.fromJSON(SdTestSupport.jsonObservation, false);
      assertEquals("Observation", observation.getId());
      assertEquals("2019-11-01T09:29:23.356+11:00", at(observation.getMeta(), "lastUpdated"));
      assertEquals(6, observation.getExtension().size());
      assertJsEqual(json("{\"url\": \"http://hl7.org/fhir/StructureDefinition/structuredefinition-category\","
          + " \"valueString\": \"Clinical.Diagnostics\"}"), observation.getExtension().get(0));
      assertEquals(50, observation.elements.size());
      ElementDefinition valueX = observation.elements.get(21);
      assertTrue(valueX.hasDiff());
      ElementDefinition valueXDiff = valueX.calculateDiff();
      // the diff will not have any element tree relationships that the original may have (toMatchObject)
      for (String k : valueXDiff.propertyKeys()) {
        assertJsEqual(valueXDiff.getProperty(k), valueX.getProperty(k));
      }
      assertEquals("Observation.value[x]", valueX.getId());
      assertEquals("Observation.value[x]", valueX.getPath());
      assertEquals(0.0, valueX.getMin());
      assertEquals("1", valueX.getMax());
      assertJsEqual(VALUE_X_TYPES, valueX.getType());
    }

    @Test
    @DisplayName("StructureDefinition #fromJSON should throw a MissingSnapshotError when the StructureDefinition to load is missing a snapshot")
    void t4() {
      JsObject noSnapshotJsonObservation = (JsObject) Js.cloneDeep(
          SdTestSupport.defs().fishForFHIR("Observation", FishableType.Resource));
      noSnapshotJsonObservation.remove("snapshot");
      MissingSnapshotError e = assertThrows(MissingSnapshotError.class,
          () -> StructureDefinition.fromJSON(noSnapshotJsonObservation));
      assertMatches("http:\\/\\/hl7.org\\/fhir\\/StructureDefinition\\/Observation is missing a snapshot",
          e.getMessage());
    }
  }

  @Nested
  @DisplayName("#toJSON")
  class ToJSON {
    @Test
    @DisplayName("StructureDefinition #toJSON should round trip back to the original JSON (excepting differentials)")
    void t1() {
      JsObject oldJSON = (JsObject) Js.cloneDeep(SdTestSupport.jsonObservation);
      JsObject newJSON = (JsObject) observation.toJSON();
      oldJSON.remove("differential");
      newJSON.remove("differential");
      assertJsEqual(oldJSON, newJSON);
    }

    @Test
    @DisplayName("StructureDefinition #toJSON should round trip back to the original JSON when there are ids and extensions on primitives (excepting differentials)")
    void t2() {
      JsObject oldJSON = (JsObject) Js.cloneDeep(SdTestSupport.jsonModifiedObservation);
      JsObject newJSON = (JsObject) modifiedObservation.toJSON();
      oldJSON.remove("differential");
      newJSON.remove("differential");
      assertJsEqual(oldJSON, newJSON);
    }

    @Test
    @DisplayName("StructureDefinition #toJSON should reflect differentials for elements that changed after capturing originals")
    void t3() {
      find(usCoreObservation, "Observation.code").setShort("Special observation code");
      find(usCoreObservation, "Observation.value[x]").setMin(1.0);
      Object json = usCoreObservation.toJSON();
      List<Object> diff = list(json, "differential.element");
      assertEquals(2, diff.size());
      assertJsEqual(json("{\"id\": \"Observation.code\", \"path\": \"Observation.code\","
          + " \"short\": \"Special observation code\"}"), diff.get(0));
      assertJsEqual(json("{\"id\": \"Observation.value[x]\", \"path\": \"Observation.value[x]\", \"min\": 1}"),
          diff.get(1));
    }

    @Test
    @DisplayName("StructureDefinition #toJSON should reflect differentials for elements that changed after capturing originals without generating snapshot")
    void t4() {
      find(usCoreObservation, "Observation.code").setShort("Special observation code");
      find(usCoreObservation, "Observation.value[x]").setMin(1.0);
      Object json = usCoreObservation.toJSON(false);
      List<Object> diff = list(json, "differential.element");
      assertEquals(2, diff.size());
      assertJsEqual(json("{\"id\": \"Observation.code\", \"path\": \"Observation.code\","
          + " \"short\": \"Special observation code\"}"), diff.get(0));
      assertJsEqual(json("{\"id\": \"Observation.value[x]\", \"path\": \"Observation.value[x]\", \"min\": 1}"),
          diff.get(1));
      assertNull(Common.getProp(json, "snapshot"));
    }

    @Test
    @DisplayName("StructureDefinition #toJSON should generate sparse differentials")
    void t5() {
      find(usCoreObservation, "Observation.component.code").setShort("Special component code");
      Object json = usCoreObservation.toJSON();
      List<Object> diff = list(json, "differential.element");
      assertEquals(1, diff.size());
      assertJsEqual(json("{\"id\": \"Observation.component.code\", \"path\": \"Observation.component.code\","
          + " \"short\": \"Special component code\"}"), diff.get(0));
    }

    @Test
    @DisplayName("StructureDefinition #toJSON should reflect basic differential for structure definitions with no changes")
    void t6() {
      Object json = usCoreObservation.toJSON();
      assertJsEqual(json("{\"element\": [{\"id\": \"Observation\", \"path\": \"Observation\"}]}"),
          Common.getProp(json, "differential"));
    }

    @Test
    @DisplayName("StructureDefinition #toJSON should reflect basic differential for structure definitions with no changes without generating snapshot")
    void t7() {
      Object json = usCoreObservation.toJSON(false);
      assertJsEqual(json("{\"element\": [{\"id\": \"Observation\", \"path\": \"Observation\"}]}"),
          Common.getProp(json, "differential"));
      assertNull(Common.getProp(json, "snapshot"));
    }

    static final String TYPE_SLICING = "{\"discriminator\": [{\"type\": \"type\", \"path\": \"$this\"}],"
        + " \"ordered\": false, \"rules\": \"open\"}";

    @Test
    @DisplayName("StructureDefinition #toJSON should properly serialize snapshot and differential for constrained choice type with constraints on specific choices")
    void t8() {
      ElementDefinition valueX = find(usCoreObservation, "Observation.value[x]");
      valueX.sliceIt("type", "$this", false, "open");
      OnlyRule valueConstraint = new OnlyRule("value[x]");
      valueConstraint.types = new ArrayList<>(List.of(new OnlyRuleType("Quantity"), new OnlyRuleType("string")));
      valueX.constrainType(valueConstraint, fisher);
      ElementDefinition valueQuantity = valueX.addSlice("valueQuantity", new ElementDefinitionType("Quantity"));
      valueQuantity.setShort("the quantity choice");
      ElementDefinition valueString = valueX.addSlice("valueString", new ElementDefinitionType("string"));
      valueString.setShort("the string choice");

      Object json = usCoreObservation.toJSON();
      List<Object> snapshot = list(json, "snapshot.element");
      Object valueXSnapshot = findJson(snapshot, "id", "Observation.value[x]");
      assertEquals("Observation.value[x]", at(valueXSnapshot, "id"));
      assertEquals("Observation.value[x]", at(valueXSnapshot, "path"));
      assertJsEqual(json("[{\"code\": \"Quantity\"}, {\"code\": \"string\"}]"), at(valueXSnapshot, "type"));
      assertJsEqual(json(TYPE_SLICING), at(valueXSnapshot, "slicing"));
      Object valueQuantitySnapshot = findJson(snapshot, "id", "Observation.value[x]:valueQuantity");
      assertEquals("Observation.value[x]:valueQuantity", at(valueQuantitySnapshot, "id"));
      assertEquals("Observation.value[x]", at(valueQuantitySnapshot, "path"));
      assertJsEqual(json("[{\"code\": \"Quantity\"}]"), at(valueQuantitySnapshot, "type"));
      assertEquals("valueQuantity", at(valueQuantitySnapshot, "sliceName"));
      assertEquals("the quantity choice", at(valueQuantitySnapshot, "short"));
      Object valueStringSnapshot = findJson(snapshot, "id", "Observation.value[x]:valueString");
      assertEquals("Observation.value[x]:valueString", at(valueStringSnapshot, "id"));
      assertEquals("Observation.value[x]", at(valueStringSnapshot, "path"));
      assertJsEqual(json("[{\"code\": \"string\"}]"), at(valueStringSnapshot, "type"));
      assertEquals("valueString", at(valueStringSnapshot, "sliceName"));
      assertEquals("the string choice", at(valueStringSnapshot, "short"));
      List<Object> diff = list(json, "differential.element");
      assertEquals(3, diff.size());
      assertJsEqual(json("{\"id\": \"Observation.value[x]\", \"path\": \"Observation.value[x]\","
          + " \"type\": [{\"code\": \"Quantity\"}, {\"code\": \"string\"}], \"slicing\": " + TYPE_SLICING + "}"),
          diff.get(0));
      Object valueQuantityDiff = diff.get(1);
      assertEquals("Observation.value[x]:valueQuantity", at(valueQuantityDiff, "id"));
      assertEquals("Observation.value[x]", at(valueQuantityDiff, "path"));
      assertJsEqual(json("[{\"code\": \"Quantity\"}]"), at(valueQuantityDiff, "type"));
      assertEquals("valueQuantity", at(valueQuantityDiff, "sliceName"));
      assertEquals("the quantity choice", at(valueQuantitySnapshot, "short"));
      Object valueStringDiff = diff.get(2);
      assertEquals("Observation.value[x]:valueString", at(valueStringDiff, "id"));
      assertEquals("Observation.value[x]", at(valueStringDiff, "path"));
      assertJsEqual(json("[{\"code\": \"string\"}]"), at(valueStringDiff, "type"));
      assertEquals("valueString", at(valueStringDiff, "sliceName"));
      assertEquals("the string choice", at(valueStringSnapshot, "short"));
    }

    @Test
    @DisplayName("StructureDefinition #toJSON should properly serialize snapshot and differential for unconstrained choice type with constraints on specific choices")
    void t9() {
      ElementDefinition valueX = find(usCoreObservation, "Observation.value[x]");
      valueX.sliceIt("type", "$this", false, "open");
      ElementDefinition valueQuantity = valueX.addSlice("valueQuantity", new ElementDefinitionType("Quantity"));
      valueQuantity.setShort("the quantity choice");
      ElementDefinition valueString = valueX.addSlice("valueString", new ElementDefinitionType("string"));
      valueString.setShort("the string choice");

      Object json = usCoreObservation.toJSON();
      List<Object> snapshot = list(json, "snapshot.element");
      Object valueXSnapshot = findJson(snapshot, "id", "Observation.value[x]");
      assertEquals("Observation.value[x]", at(valueXSnapshot, "id"));
      assertEquals("Observation.value[x]", at(valueXSnapshot, "path"));
      assertEquals(11, list(valueXSnapshot, "type").size());
      assertJsEqual(json(TYPE_SLICING), at(valueXSnapshot, "slicing"));
      Object valueQuantitySnapshot = findJson(snapshot, "id", "Observation.value[x]:valueQuantity");
      assertEquals("Observation.value[x]:valueQuantity", at(valueQuantitySnapshot, "id"));
      assertEquals("Observation.value[x]", at(valueQuantitySnapshot, "path"));
      assertJsEqual(json("[{\"code\": \"Quantity\"}]"), at(valueQuantitySnapshot, "type"));
      assertEquals("valueQuantity", at(valueQuantitySnapshot, "sliceName"));
      assertEquals("the quantity choice", at(valueQuantitySnapshot, "short"));
      Object valueStringSnapshot = findJson(snapshot, "id", "Observation.value[x]:valueString");
      assertEquals("Observation.value[x]:valueString", at(valueStringSnapshot, "id"));
      assertEquals("Observation.value[x]", at(valueStringSnapshot, "path"));
      assertJsEqual(json("[{\"code\": \"string\"}]"), at(valueStringSnapshot, "type"));
      assertEquals("valueString", at(valueStringSnapshot, "sliceName"));
      assertEquals("the string choice", at(valueStringSnapshot, "short"));
      List<Object> diff = list(json, "differential.element");
      assertEquals(3, diff.size());
      Object valueXDiff = diff.get(0);
      assertEquals("Observation.value[x]", at(valueXDiff, "id"));
      assertEquals("Observation.value[x]", at(valueXDiff, "path"));
      assertJsEqual(json(TYPE_SLICING), at(valueXDiff, "slicing"));
      Object valueQuantityDiff = diff.get(1);
      assertEquals("Observation.value[x]:valueQuantity", at(valueQuantityDiff, "id"));
      assertEquals("Observation.value[x]", at(valueQuantityDiff, "path"));
      assertJsEqual(json("[{\"code\": \"Quantity\"}]"), at(valueQuantityDiff, "type"));
      assertEquals("valueQuantity", at(valueQuantityDiff, "sliceName"));
      assertEquals("the quantity choice", at(valueQuantityDiff, "short"));
      Object valueStringDiff = diff.get(2);
      assertEquals("Observation.value[x]:valueString", at(valueStringDiff, "id"));
      assertEquals("Observation.value[x]", at(valueStringDiff, "path"));
      assertJsEqual(json("[{\"code\": \"string\"}]"), at(valueStringDiff, "type"));
      assertEquals("valueString", at(valueStringDiff, "sliceName"));
      assertEquals("the string choice", at(valueStringDiff, "short"));
    }

    @Test
    @DisplayName("StructureDefinition #toJSON should properly serialize snapshot and differential for choice type with non-type constraint and with constraints on specific choices")
    void t10() {
      ElementDefinition valueX = find(usCoreObservation, "Observation.value[x]");
      valueX.setShort("a choice of many things");
      valueX.sliceIt("type", "$this", false, "open");
      ElementDefinition valueQuantity = valueX.addSlice("valueQuantity", new ElementDefinitionType("Quantity"));
      valueQuantity.setShort("the quantity choice");
      ElementDefinition valueString = valueX.addSlice("valueString", new ElementDefinitionType("string"));
      valueString.setShort("the string choice");

      Object json = usCoreObservation.toJSON();
      List<Object> snapshot = list(json, "snapshot.element");
      Object valueXSnapshot = findJson(snapshot, "id", "Observation.value[x]");
      assertEquals("Observation.value[x]", at(valueXSnapshot, "id"));
      assertEquals("Observation.value[x]", at(valueXSnapshot, "path"));
      assertEquals(11, list(valueXSnapshot, "type").size());
      assertEquals("a choice of many things", at(valueXSnapshot, "short"));
      assertJsEqual(json(TYPE_SLICING), at(valueXSnapshot, "slicing"));
      Object valueQuantitySnapshot = findJson(snapshot, "id", "Observation.value[x]:valueQuantity");
      assertEquals("Observation.value[x]:valueQuantity", at(valueQuantitySnapshot, "id"));
      assertEquals("Observation.value[x]", at(valueQuantitySnapshot, "path"));
      assertJsEqual(json("[{\"code\": \"Quantity\"}]"), at(valueQuantitySnapshot, "type"));
      assertEquals("valueQuantity", at(valueQuantitySnapshot, "sliceName"));
      assertEquals("the quantity choice", at(valueQuantitySnapshot, "short"));
      Object valueStringSnapshot = findJson(snapshot, "id", "Observation.value[x]:valueString");
      assertEquals("Observation.value[x]:valueString", at(valueStringSnapshot, "id"));
      assertEquals("Observation.value[x]", at(valueStringSnapshot, "path"));
      assertJsEqual(json("[{\"code\": \"string\"}]"), at(valueStringSnapshot, "type"));
      assertEquals("valueString", at(valueStringSnapshot, "sliceName"));
      assertEquals("the string choice", at(valueStringSnapshot, "short"));
      List<Object> diff = list(json, "differential.element");
      assertEquals(3, diff.size());
      assertJsEqual(json("{\"id\": \"Observation.value[x]\", \"path\": \"Observation.value[x]\", \"slicing\": "
          + TYPE_SLICING + ", \"short\": \"a choice of many things\"}"), diff.get(0));
      Object valueQuantityDiff = diff.get(1);
      assertEquals("Observation.value[x]:valueQuantity", at(valueQuantityDiff, "id"));
      assertEquals("Observation.value[x]", at(valueQuantityDiff, "path"));
      assertJsEqual(json("[{\"code\": \"Quantity\"}]"), at(valueQuantityDiff, "type"));
      assertEquals("valueQuantity", at(valueQuantityDiff, "sliceName"));
      assertEquals("the quantity choice", at(valueQuantitySnapshot, "short"));
      Object valueStringDiff = diff.get(2);
      assertEquals("Observation.value[x]:valueString", at(valueStringDiff, "id"));
      assertEquals("Observation.value[x]", at(valueStringDiff, "path"));
      assertJsEqual(json("[{\"code\": \"string\"}]"), at(valueStringDiff, "type"));
      assertEquals("valueString", at(valueStringDiff, "sliceName"));
      assertEquals("the string choice", at(valueStringSnapshot, "short"));
    }

    @Test
    @DisplayName("StructureDefinition #toJSON should properly serialize snapshot and differential for constraints on children of slices")
    void t11() {
      resprate.findElementByPath("category.coding", fisher);
      Object json = resprate.toJSON();
      List<Object> snap = list(json, "snapshot.element");
      JsObject vsCatOriginal = (JsObject) snap.get(18);
      JsObject vsCatCodingOriginal = (JsObject) snap.get(21);
      JsObject catOriginal = (JsObject) snap.get(13);
      JsObject catCodingOriginal = (JsObject) snap.get(16);

      ElementDefinition vsCatCodingSD = find(resprate, "Observation.category:VSCat.coding");
      vsCatCodingSD.setShort("Hello I am a change");
      ElementDefinition catCodingSD = find(resprate, "Observation.category.coding");
      vsCatCodingSD.setShort("Hello I am a change");
      catCodingSD.setShort("Hello I am also a change");

      json = resprate.toJSON();
      snap = list(json, "snapshot.element");
      JsObject vsCat = (JsObject) snap.get(18);
      JsObject vsCatCoding = (JsObject) snap.get(21);
      JsObject cat = (JsObject) snap.get(13);
      JsObject catCoding = (JsObject) snap.get(16);

      assertEquals("Hello I am a change", vsCatCoding.get("short"));
      vsCatCoding.remove("short");
      vsCatCodingOriginal.remove("short");
      assertJsEqual(vsCatCodingOriginal, vsCatCoding);
      assertJsEqual(vsCatOriginal, vsCat);
      assertEquals("Hello I am also a change", catCoding.get("short"));
      catCoding.remove("short");
      catCodingOriginal.remove("short");
      assertJsEqual(catCodingOriginal, catCoding);
      assertJsEqual(catOriginal, cat);

      List<Object> diff = list(json, "differential.element");
      assertJsEqual(json("{\"id\": \"Observation.category:VSCat.coding\", \"path\": \"Observation.category.coding\","
          + " \"short\": \"Hello I am a change\"}"), diff.get(3));
      assertJsEqual(json("{\"id\": \"Observation.category:VSCat\", \"path\": \"Observation.category\","
          + " \"sliceName\": \"VSCat\"}"), diff.get(2));
      assertJsEqual(json("{\"id\": \"Observation.category.coding\", \"path\": \"Observation.category.coding\","
          + " \"short\": \"Hello I am also a change\"}"), diff.get(1));
      assertJsEqual(json("{\"id\": \"Observation.category\", \"path\": \"Observation.category\"}"), diff.get(0));
    }

    StructureDefinition exportMappingProfile(String profileName, boolean withMappings) {
      FSHDocument doc = new FSHDocument("fileName");
      FSHTank input = new FSHTank(List.of(doc), MinimalConfig.minimalConfig());
      Package pkg = new Package(input.config);
      TestFisher fisher = new TestFisher(input, SdTestSupport.defs(), pkg);
      FHIRExporter exporter = new FHIRExporter(input, pkg, fisher);
      Profile observationProfile = new Profile(profileName);
      observationProfile.parent = "Observation";
      doc.profiles.put(observationProfile.name, observationProfile);
      if (withMappings) {
        Mapping mapping = new Mapping("ExtraMapping");
        mapping.source = profileName;
        mapping.title = "Extra Mapping";
        mapping.id = "extra-mapping";
        mapping.target = "http://example.org";
        doc.mappings.put(mapping.name, mapping);
        Mapping updatedMapping = new Mapping("");
        updatedMapping.source = profileName;
        updatedMapping.title = "FiveWs Pattern Mapping";
        updatedMapping.id = "w5";
        updatedMapping.target = "http://hl7.org/fhir/fivews";
        updatedMapping.description = "A new comment on an existing mapping";
        doc.mappings.put(updatedMapping.name, updatedMapping);
      }
      return exporter.export().profiles.get(0);
    }

    @Test
    @DisplayName("StructureDefinition #toJSON should include only new mappings and updated mappings in not-snapshot mode")
    void t12() {
      StructureDefinition exported = exportMappingProfile("OneExtraMapping", true);
      Object exportedJSON = exported.toJSON(false);
      assertJsEqual(json("[{\"identity\": \"w5\", \"name\": \"FiveWs Pattern Mapping\","
          + " \"uri\": \"http://hl7.org/fhir/fivews\", \"comment\": \"A new comment on an existing mapping\"},"
          + " {\"identity\": \"extra-mapping\", \"name\": \"Extra Mapping\", \"uri\": \"http://example.org\"}]"),
          Common.getProp(exportedJSON, "mapping"));
    }

    @Test
    @DisplayName("StructureDefinition #toJSON should not include mapping when no new mappings are added in not-snapshot mode")
    void t13() {
      StructureDefinition exported = exportMappingProfile("NoExtraMappings", false);
      Object exportedJSON = exported.toJSON(false);
      assertNull(Common.getProp(exportedJSON, "mapping"));
    }

    @Test
    @DisplayName("StructureDefinition #toJSON should add all mappings (new, updated, and inherited) when in snapshot mode")
    void t14() {
      StructureDefinition exported = exportMappingProfile("OneExtraMapping", true);
      Object exportedJSON = exported.toJSON(true);
      assertEquals(7, list(exportedJSON, "mapping").size());
      assertJsEqual(json("[{\"identity\": \"w5\", \"name\": \"FiveWs Pattern Mapping\","
          + " \"uri\": \"http://hl7.org/fhir/fivews\", \"comment\": \"A new comment on an existing mapping\"},"
          + " {\"identity\": \"extra-mapping\", \"name\": \"Extra Mapping\", \"uri\": \"http://example.org\"},"
          + " {\"identity\": \"workflow\", \"uri\": \"http://hl7.org/fhir/workflow\", \"name\": \"Workflow Pattern\"},"
          + " {\"identity\": \"sct-concept\", \"uri\": \"http://snomed.info/conceptdomain\","
          + " \"name\": \"SNOMED CT Concept Domain Binding\"},"
          + " {\"identity\": \"v2\", \"uri\": \"http://hl7.org/v2\", \"name\": \"HL7 v2 Mapping\"},"
          + " {\"identity\": \"rim\", \"uri\": \"http://hl7.org/v3\", \"name\": \"RIM Mapping\"},"
          + " {\"identity\": \"sct-attr\", \"uri\": \"http://snomed.org/attributebinding\","
          + " \"name\": \"SNOMED CT Attribute Binding\"}]"), Common.getProp(exportedJSON, "mapping"));
    }

    @Nested
    @DisplayName("#toJson - Logicals and Resources")
    class LogicalsAndResources {
      AddElementRule addElementRule1;
      AddElementRule addElementRule2;
      Invariant invariant;
      FSHDocument doc;
      StructureDefinitionExporter exporter;

      @BeforeEach
      void setUp() {
        addElementRule1 = new AddElementRule("prop1");
        addElementRule1.min = 0.0;
        addElementRule1.max = "1";
        addElementRule1.types = new ArrayList<>(List.of(new OnlyRuleType("dateTime")));
        addElementRule1.short_ = "property1 dateTime";

        addElementRule2 = new AddElementRule("prop2");
        addElementRule2.min = 0.0;
        addElementRule2.max = "*";
        addElementRule2.types = new ArrayList<>(List.of(new OnlyRuleType("string")));
        addElementRule2.short_ = "property2 string";

        loggerSpy.reset();
        doc = new FSHDocument("fileName");
        invariant = new Invariant("TestInvariant");
        doc.invariants.put(invariant.name, invariant);
        FSHTank input = new FSHTank(List.of(doc), MinimalConfig.minimalConfig());
        Package pkg = new Package(input.config);
        TestFisher fisher = new TestFisher(input, SdTestSupport.defs(), pkg);
        exporter = new StructureDefinitionExporter(input, pkg, fisher);
      }

      Logical logical(String parent) {
        Logical logical = new Logical("MyTestModel");
        if (parent != null) logical.parent = parent;
        logical.setId("MyModel");
        logical.rules.add(addElementRule1);
        logical.rules.add(addElementRule2);
        return logical;
      }

      Resource resource(String parent, boolean titled) {
        Resource resource = new Resource("MyTestResource");
        resource.parent = parent;
        if (titled) {
          resource.title = "MyTestResource Title";
          resource.description = "MyTestResource description goes here.";
        }
        resource.setId("MyResource");
        resource.rules.add(addElementRule1);
        resource.rules.add(addElementRule2);
        return resource;
      }

      @Test
      @DisplayName("StructureDefinition #toJSON #toJson - Logicals and Resources should properly serialize snapshot and differential root elements for logical model")
      void t1() {
        Logical logical = logical(null);
        doc.logicals.put(logical.name, logical);
        List<StructureDefinition> exported = exporter.export().logicals;
        assertEquals(1, exported.size());
        Object json = exported.get(0).toJSON(true);
        assertNotNull(json);
        assertEquals(3, list(json, "snapshot.element").size());
        assertEquals(3, list(json, "differential.element").size());
        assertStrictJson(json("{\"id\": \"MyModel\", \"path\": \"MyModel\", \"short\": \"MyTestModel\","
            + " \"definition\": \"MyTestModel\", \"min\": 0, \"max\": \"*\","
            + " \"base\": {\"path\": \"MyModel\", \"min\": 0, \"max\": \"*\"},"
            + " \"constraint\": [{\"key\": \"ele-1\", \"severity\": \"error\","
            + " \"human\": \"All FHIR elements must have a @value or children\","
            + " \"expression\": \"hasValue() or (children().count() > id.count())\","
            + " \"source\": \"http://hl7.org/fhir/StructureDefinition/Element\"}],"
            + " \"isModifier\": false, \"mapping\": [{\"identity\": \"rim\", \"map\": \"n/a\"}]}"),
            list(json, "snapshot.element").get(0));
        assertStrictJson(json("{\"id\": \"MyModel\", \"path\": \"MyModel\", \"short\": \"MyTestModel\","
            + " \"definition\": \"MyTestModel\"}"), list(json, "differential.element").get(0));
      }

      @Test
      @DisplayName("StructureDefinition #toJSON #toJson - Logicals and Resources should properly serialize snapshot and differential elements with constraint for logical model")
      void t2() {
        Logical logical = logical(null);
        ObeysRule obeysRule = new ObeysRule("prop2");
        obeysRule.invariant = invariant.getId();
        logical.rules.add(obeysRule);
        doc.logicals.put(logical.name, logical);
        List<StructureDefinition> exported = exporter.export().logicals;
        assertEquals(1, exported.size());
        Object json = exported.get(0).toJSON(true);
        assertNotNull(json);
        assertEquals(3, list(json, "snapshot.element").size());
        assertEquals(3, list(json, "differential.element").size());
        Object prop2Snap = findJson(list(json, "snapshot.element"), "path", "MyModel.prop2");
        assertEquals(2, list(prop2Snap, "constraint").size());
        Object prop2Diff = findJson(list(json, "differential.element"), "path", "MyModel.prop2");
        assertEquals(1, list(prop2Diff, "constraint").size());
        assertStrictJson(json("{\"key\": \"TestInvariant\","
            + " \"source\": \"http://hl7.org/fhir/us/minimal/StructureDefinition/MyModel\"}"),
            list(prop2Diff, "constraint").get(0));
      }

      @Test
      @DisplayName("StructureDefinition #toJSON #toJson - Logicals and Resources should properly serialize snapshot and differential root elements for resource")
      void t3() {
        Resource resource = resource("Resource", true);
        doc.resources.put(resource.name, resource);
        List<StructureDefinition> exported = exporter.export().resources;
        assertEquals(1, exported.size());
        Object json = exported.get(0).toJSON(true);
        assertNotNull(json);
        assertEquals(7, list(json, "snapshot.element").size());
        assertEquals(3, list(json, "differential.element").size());
        assertStrictJson(json("{\"id\": \"MyResource\", \"path\": \"MyResource\", \"short\": \"MyTestResource Title\","
            + " \"definition\": \"MyTestResource description goes here.\", \"min\": 0, \"max\": \"*\","
            + " \"base\": {\"path\": \"MyResource\", \"min\": 0, \"max\": \"*\"}, \"isModifier\": false,"
            + " \"isSummary\": false, \"mapping\": [{\"identity\": \"rim\", \"map\": \"Entity. Role, or Act\"}]}"),
            list(json, "snapshot.element").get(0));
        assertStrictJson(json("{\"id\": \"MyResource\", \"path\": \"MyResource\", \"short\": \"MyTestResource Title\","
            + " \"definition\": \"MyTestResource description goes here.\"}"),
            list(json, "differential.element").get(0));
      }

      @Test
      @DisplayName("StructureDefinition #toJSON #toJson - Logicals and Resources should properly serialize snapshot and differential elements with constraint for resource")
      void t4() {
        Resource resource = resource("Resource", true);
        ObeysRule obeysRule = new ObeysRule("prop2");
        obeysRule.invariant = invariant.getId();
        resource.rules.add(obeysRule);
        doc.resources.put(resource.name, resource);
        List<StructureDefinition> exported = exporter.export().resources;
        assertEquals(1, exported.size());
        Object json = exported.get(0).toJSON(true);
        assertNotNull(json);
        assertEquals(7, list(json, "snapshot.element").size());
        assertEquals(3, list(json, "differential.element").size());
        Object prop2Snap = findJson(list(json, "snapshot.element"), "path", "MyResource.prop2");
        assertEquals(2, list(prop2Snap, "constraint").size());
        Object prop2Diff = findJson(list(json, "differential.element"), "path", "MyResource.prop2");
        assertEquals(1, list(prop2Diff, "constraint").size());
        assertStrictJson(json("{\"key\": \"TestInvariant\","
            + " \"source\": \"http://hl7.org/fhir/us/minimal/StructureDefinition/MyResource\"}"),
            list(prop2Diff, "constraint").get(0));
      }

      void assertLogicalSizes(String parent, int snapshotSize) {
        Logical logical = logical(parent);
        doc.logicals.put(logical.name, logical);
        List<StructureDefinition> exported = exporter.export().logicals;
        assertEquals(1, exported.size());
        Object json = exported.get(0).toJSON(true);
        assertNotNull(json);
        assertEquals(snapshotSize, list(json, "snapshot.element").size());
        assertEquals(3, list(json, "differential.element").size());
      }

      void assertResourceSizes(String parent, int snapshotSize) {
        Resource resource = resource(parent, false);
        doc.resources.put(resource.name, resource);
        List<StructureDefinition> exported = exporter.export().resources;
        assertEquals(1, exported.size());
        Object json = exported.get(0).toJSON(true);
        assertNotNull(json);
        assertEquals(snapshotSize, list(json, "snapshot.element").size());
        assertEquals(3, list(json, "differential.element").size());
      }

      @Test
      @DisplayName("StructureDefinition #toJSON #toJson - Logicals and Resources should properly serialize snapshot and differential for logical model with parent set to Base")
      void t5() {
        assertLogicalSizes("Base", 3);
      }

      @Test
      @DisplayName("StructureDefinition #toJSON #toJson - Logicals and Resources should properly serialize snapshot and differential for logical model with parent set to Element")
      void t6() {
        assertLogicalSizes("Element", 5);
      }

      @Test
      @DisplayName("StructureDefinition #toJSON #toJson - Logicals and Resources should properly serialize snapshot and differential for logical model with parent set to AlternateIdentification")
      void t7() {
        assertLogicalSizes("AlternateIdentification", 8);
      }

      @Test
      @DisplayName("StructureDefinition #toJSON #toJson - Logicals and Resources should properly serialize snapshot and differential for custom resource with parent set to Resource")
      void t8() {
        assertResourceSizes("Resource", 7);
      }

      @Test
      @DisplayName("StructureDefinition #toJSON #toJson - Logicals and Resources should properly serialize snapshot and differential for custom resource with parent set to DomainResource")
      void t9() {
        assertResourceSizes("DomainResource", 11);
      }
    }
  }

  @Nested
  @DisplayName("#newElement")
  class NewElement {
    @Test
    @DisplayName("StructureDefinition #newElement should add a new element to the SD")
    void t1() {
      observation.newElement("extra");
      assertEquals(51, observation.elements.size());
      assertEquals("Observation.extra", observation.elements.get(50).getId());
      assertEquals("Observation.extra", observation.elements.get(50).getPath());
    }
  }

  @Nested
  @DisplayName("#addElement")
  class AddElement {
    @Test
    @DisplayName("StructureDefinition #addElement should add an element in the right place")
    void t1() {
      observation.addElement(new ElementDefinition("Observation.meta.id"));
      assertEquals(51, observation.elements.size());
      assertEquals("Observation.meta.id", observation.elements.get(3).getId());
    }

    @Test
    @DisplayName("StructureDefinition #addElement should add an element in the right place even with substrings involved")
    void t2() {
      ElementDefinition fooBefore = new ElementDefinition("Observation.component:FooBefore");
      fooBefore.setSliceName("FooBefore");
      ElementDefinition foo = new ElementDefinition("Observation.component:Foo");
      foo.setSliceName("Foo");
      ElementDefinition fooAfter = new ElementDefinition("Observation.component:FooAfter");
      fooAfter.setSliceName("FooAfter");
      observation.addElement(fooBefore);
      observation.addElement(foo);
      observation.addElement(fooAfter);
      observation.addElement(new ElementDefinition("Observation.component:Foo.id"));
      observation.addElement(new ElementDefinition("Observation.component:FooAfter.id"));
      observation.addElement(new ElementDefinition("Observation.component:FooBefore.id"));
      assertEquals(56, observation.elements.size());
      assertEquals(41, indexOf(observation, "Observation.component"));
      assertEquals(50, indexOf(observation, "Observation.component:FooBefore"));
      assertEquals(51, indexOf(observation, "Observation.component:FooBefore.id"));
      assertEquals(52, indexOf(observation, "Observation.component:Foo"));
      assertEquals(53, indexOf(observation, "Observation.component:Foo.id"));
      assertEquals(54, indexOf(observation, "Observation.component:FooAfter"));
      assertEquals(55, indexOf(observation, "Observation.component:FooAfter.id"));
    }

    @Test
    @DisplayName("StructureDefinition #addElement should add explicit choice element in the right place")
    void t3() {
      ElementDefinition valueQuantity = new ElementDefinition("Observation.value[x]:valueQuantity");
      valueQuantity.setSliceName("valueQuantity");
      observation.addElement(valueQuantity);
      assertEquals(51, observation.elements.size());
      assertEquals("Observation.value[x]:valueQuantity", observation.elements.get(22).getId());
    }

    @Test
    @DisplayName("StructureDefinition #addElement should add children of sliced elements in the right place")
    void t4() {
      int originalLength = resprate.elements.size();
      resprate.addElement(new ElementDefinition("Observation.category.coding"));
      assertEquals(originalLength + 1, resprate.elements.size());
      assertEquals("Observation.category.coding", resprate.elements.get(14).getId());
    }

    @Test
    @DisplayName("StructureDefinition #addElement should add resliced elements in the right place")
    void t5() {
      int originalLength = resprate.elements.size();
      ElementDefinition vsCatFoo = new ElementDefinition("Observation.category:VSCat/foo");
      vsCatFoo.setSliceName("VSCat/foo");
      resprate.addElement(vsCatFoo);
      assertEquals(originalLength + 1, resprate.elements.size());
      assertEquals("Observation.category:VSCat/foo", resprate.elements.get(26).getId());
    }

    @Test
    @DisplayName("StructureDefinition #addElement should add children of resliced elements in the right place")
    void t6() {
      int originalLength = resprate.elements.size();
      ElementDefinition vsCatFoo = new ElementDefinition("Observation.category:VSCat/foo");
      vsCatFoo.setSliceName("VSCat/foo");
      ElementDefinition vsCatFooBar = new ElementDefinition("Observation.category:VSCat/foo/bar");
      vsCatFooBar.setSliceName("VSCat/foo/bar");
      resprate.addElement(vsCatFoo);
      resprate.addElement(vsCatFooBar);
      resprate.addElement(new ElementDefinition("Observation.category:VSCat/foo.extension"));
      assertEquals(originalLength + 3, resprate.elements.size());
      assertEquals("Observation.category:VSCat/foo", resprate.elements.get(26).getId());
      assertEquals("Observation.category:VSCat/foo.extension", resprate.elements.get(27).getId());
      assertEquals("Observation.category:VSCat/foo/bar", resprate.elements.get(28).getId());
    }
  }

  @Nested
  @DisplayName("#findElement")
  class FindElement {
    @Test
    @DisplayName("StructureDefinition #findElement should find an element by id")
    void t1() {
      ElementDefinition valueX = observation.findElement("Observation.value[x]");
      assertNotNull(valueX);
      assertEquals("Actual result", valueX.getShort());
    }
  }

  @SuppressWarnings("unused")
  private static Map<String, Object> unused() {
    return null;
  }
}
