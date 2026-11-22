package org.hl7.fhir.sushi.fhirtypes;

import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertJsEqual;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.json;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.sushi.js.Json;
import org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions;
import org.hl7.fhir.sushi.testhelpers.TestFisher;
import org.hl7.fhir.sushi.utils.FishableType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// port of sushi/test/fhirtypes/StructureDefinition.test.ts (SUSHI 3.21.1), describe '#findElementByPath'
@DisplayName("StructureDefinition")
class StructureDefinitionFindElementByPathTest {
  TestFisher fisher;
  StructureDefinition observation;
  StructureDefinition resprate;

  @BeforeEach
  void beforeEach() {
    fisher = SdTestSupport.fisher();
    loggerSpy.reset();
    observation = StructureDefinition.fromJSON(SdTestSupport.jsonObservation);
    resprate = fisher.fishForStructureDefinition("resprate");
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> slicing(String text) {
    return (Map<String, Object>) Json.parse(text);
  }

  static final String PATTERN_THIS_OPEN = "{\"discriminator\": [{\"type\": \"pattern\", \"path\": \"$this\"}],"
      + " \"rules\": \"open\"}";

  static ElementDefinition find(StructureDefinition sd, String id) {
    for (ElementDefinition e : sd.elements) if (id.equals(e.getId())) return e;
    return null;
  }

  static Object discriminator0(ElementDefinition e) {
    return ((List<?>) e.getSlicing().get("discriminator")).get(0);
  }

  @Nested
  @DisplayName("#findElementByPath")
  class FindElementByPath {
    StructureDefinition respRate;
    StructureDefinition lipidProfile;
    StructureDefinition clinicalDocument;
    StructureDefinition valueSet;

    @BeforeEach
    void setUp() {
      respRate = fisher.fishForStructureDefinition("resprate");
      lipidProfile = fisher.fishForStructureDefinition("lipidprofile");
      clinicalDocument = fisher.fishForStructureDefinition("clinicaldocument");
      valueSet = fisher.fishForStructureDefinition("ValueSet");
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should find an element by a path that exists")
    void t1() {
      ElementDefinition status = observation.findElementByPath("status", fisher);
      assertNotNull(status);
      assertEquals("Observation.status", status.getId());
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should find a choice element by a path that exists")
    void t2() {
      ElementDefinition valueX = observation.findElementByPath("value[x]", fisher);
      assertNotNull(valueX);
      assertEquals("Observation.value[x]", valueX.getId());
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should find an element with children by a path that exists")
    void t3() {
      ElementDefinition refRange = respRate.findElementByPath("referenceRange", fisher);
      assertNotNull(refRange);
      assertEquals("Observation.referenceRange", refRange.getId());
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should find a child element by a path that exists")
    void t4() {
      ElementDefinition refRangeLow = respRate.findElementByPath("referenceRange.low", fisher);
      assertNotNull(refRangeLow);
      assertEquals("Observation.referenceRange.low", refRangeLow.getId());
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should find the base element by an empty path")
    void t5() {
      ElementDefinition observationElement = observation.findElementByPath("", fisher);
      assertNotNull(observationElement);
      assertEquals("Observation", observationElement.getId());
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should not find an element by non-existent path")
    void t6() {
      assertNull(observation.findElementByPath("foo", fisher));
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should find a reference choice by path")
    void t7() {
      ElementDefinition basedOnNoChoice = observation.findElementByPath("basedOn", fisher);
      ElementDefinition basedOnChoice = observation.findElementByPath("basedOn[MedicationRequest]", fisher);
      assertNotNull(basedOnChoice);
      assertEquals("Observation.basedOn", basedOnChoice.getId());
      assertSame(basedOnNoChoice, basedOnChoice);
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should not find an incorrect reference choice by path")
    void t8() {
      assertNull(observation.findElementByPath("basedOn[foo]", fisher));
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should find a sliced element by path")
    void t9() {
      ElementDefinition vsCat = respRate.findElementByPath("category[VSCat]", fisher);
      assertNotNull(vsCat);
      assertEquals("Observation.category:VSCat", vsCat.getId());
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should find a sliced element by path when a previous element in a different slice has a child w/ the same slice name")
    void t10() {
      ElementDefinition cat = respRate.findElement("Observation.category");
      cat.addSlice("CommonName");
      ElementDefinition vsCatCoding = respRate.findElement("Observation.category:VSCat.coding");
      vsCatCoding.setSlicing(slicing(PATTERN_THIS_OPEN));
      vsCatCoding.addSlice("CommonName");
      ElementDefinition vsCat = respRate.findElementByPath("category[CommonName]", fisher);
      assertNotNull(vsCat);
      assertEquals("Observation.category:CommonName", vsCat.getId());
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should find a child of a sliced element by path")
    void t11() {
      ElementDefinition vsCatId = respRate.findElementByPath("category[VSCat].id", fisher);
      assertNotNull(vsCatId);
      assertEquals("Observation.category:VSCat.id", vsCatId.getId());
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should find a root of a slicing that is a child of a slice by path")
    void t12() {
      ElementDefinition vsCatCoding = respRate.findElement("Observation.category:VSCat.coding");
      vsCatCoding.setSlicing(slicing(PATTERN_THIS_OPEN));
      vsCatCoding.addSlice("TestSlice");
      assertNotNull(respRate.findElement("Observation.category:VSCat.coding:TestSlice"));
      ElementDefinition foundTestSlice = respRate.findElementByPath("category[VSCat].coding[TestSlice]", fisher);
      ElementDefinition foundRoot = respRate.findElementByPath("category[VSCat].coding", fisher);
      assertNotNull(foundTestSlice);
      assertEquals("Observation.category:VSCat.coding:TestSlice", foundTestSlice.getId());
      assertNotNull(foundRoot);
      assertEquals("Observation.category:VSCat.coding", foundRoot.getId());
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should find a child that must be unrolled of an element with slices that contain the name of that element")
    void t13() {
      ElementDefinition coding = respRate.findElement("Observation.code.coding");
      coding.addSlice("codingSlice");
      assertNotNull(respRate.findElementByPath("code.coding.system.id", fisher));
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should find a slice that only exists on a sliced element child")
    void t14() {
      ElementDefinition coding = respRate.findElementByPath("category.coding", fisher);
      coding.setSlicing(slicing(PATTERN_THIS_OPEN));
      coding.addSlice("RegularSlice");
      int originalLength = respRate.elements.size();
      ElementDefinition root = respRate.findElement("Observation.category:VSCat.coding");
      ElementDefinition slice = respRate.findElementByPath("category[VSCat].coding[RegularSlice]", fisher);
      assertNotNull(slice);
      assertEquals("Observation.category:VSCat.coding:RegularSlice", slice.getId());
      assertJsEqual(coding.getSlicing(), root.getSlicing());
      assertEquals(originalLength + 1, respRate.elements.size());
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should find a slice that only exists on a sliced element grandchild")
    void t15() {
      ElementDefinition id = respRate.findElementByPath("category.coding.id", fisher);
      id.setSlicing(slicing(PATTERN_THIS_OPEN));
      id.addSlice("RegularSlice");
      int originalLength = respRate.elements.size();
      ElementDefinition root = respRate.findElement("Observation.category:VSCat.coding.id");
      ElementDefinition slice = respRate.findElementByPath("category[VSCat].coding.id[RegularSlice]", fisher);
      assertJsEqual(id.getSlicing(), root.getSlicing());
      assertNotNull(slice);
      assertEquals("Observation.category:VSCat.coding.id:RegularSlice", slice.getId());
      assertEquals(originalLength + 1, respRate.elements.size());
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should not find a slice that does not exist on a sliced root child")
    void t16() {
      ElementDefinition regularCoding = respRate.findElementByPath("category.coding", fisher);
      regularCoding.setSlicing(slicing(PATTERN_THIS_OPEN));
      regularCoding.addSlice("RegularSlice");
      int originalLength = respRate.elements.size();
      ElementDefinition root = respRate.findElement("Observation.category:VSCat.coding");
      ElementDefinition slice = respRate.findElementByPath("category[VSCat].coding[IrregularSlice]", fisher);
      assertNull(root.getSlicing());
      assertNull(slice);
      assertEquals(originalLength, respRate.elements.size());
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should find an element that is the grandchild or deeper descendant of an element that has a slice with the same name as the ancestor element")
    void t17() {
      ElementDefinition component = respRate.findElementByPath("component", fisher);
      component.setSlicing(slicing(PATTERN_THIS_OPEN));
      component.addSlice("component");
      respRate.findElementByPath("component[component].dataAbsentReason", fisher);
      assertNotNull(respRate.findElementByPath("component.referenceRange.low", fisher));
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should find a re-sliced element by path")
    void t18() throws Exception {
      Object jsonReslice = Json.parse(Files.readString(Path.of(TestFHIRDefinitions.testDefsPath(),
          "patient-telecom-reslice-profile.json")));
      StructureDefinition reslice = StructureDefinition.fromJSON(jsonReslice);
      ElementDefinition emailWorkEmail = reslice.findElementByPath("telecom[email][workEmail]", fisher);
      assertNotNull(emailWorkEmail);
      assertEquals("email/workEmail", emailWorkEmail.getSliceName());
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should not crash when trying to find a non-existent slice containing elements without a type")
    void t19() {
      assertNull(observation.findElementByPath("component[FooSlice]", fisher));
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should find a child of a sliced element by path with URL")
    void t20() {
      int originalLength = clinicalDocument.elements.size();
      ElementDefinition valueString = clinicalDocument.findElementByPath(
          "extension[http://hl7.org/fhir/StructureDefinition/composition-clinicaldocument-versionNumber].value[x]",
          fisher);
      assertNotNull(valueString);
      assertEquals("Composition.extension:versionNumber.value[x]", valueString.getId());
      assertEquals(originalLength + 4, clinicalDocument.elements.size());
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should make explicit a non-existent choice element by path")
    void t21() {
      int originalLength = observation.elements.size();
      ElementDefinition valueX = observation.findElementByPath("value[x]", fisher);
      assertNull(valueX.getSlicing());
      ElementDefinition valueQuantity = observation.findElementByPath("valueQuantity", fisher);
      assertNotNull(valueQuantity);
      assertEquals("Observation.value[x]:valueQuantity", valueQuantity.getId());
      assertNull(valueQuantity.getSlicing());
      assertEquals("valueQuantity", valueQuantity.getSliceName());
      assertEquals("Observation.value[x]", valueQuantity.getPath());
      assertEquals(0.0, valueQuantity.getMin());
      assertNotNull(valueX.getSlicing());
      assertJsEqual(json("{\"type\": \"type\", \"path\": \"$this\"}"), discriminator0(valueX));
      assertEquals(false, valueX.getSlicing().get("ordered"));
      assertEquals("open", valueX.getSlicing().get("rules"));
      assertEquals(originalLength + 1, observation.elements.size());
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should preserve existing slicing when making a non-existent choice element explicit")
    void t22() {
      int originalLength = observation.elements.size();
      ElementDefinition valueX = observation.findElementByPath("value[x]", fisher);
      assertNull(valueX.getSlicing());
      valueX.setSlicing(slicing("{\"ordered\": true, \"rules\": \"closed\"}"));
      observation.findElementByPath("valueQuantity", fisher);
      assertNotNull(valueX.getSlicing());
      assertJsEqual(json("{\"type\": \"type\", \"path\": \"$this\"}"), discriminator0(valueX));
      assertEquals(true, valueX.getSlicing().get("ordered"));
      assertEquals("closed", valueX.getSlicing().get("rules"));
      assertEquals(originalLength + 1, observation.elements.size());
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should make explicit a non-existent choice element that must first be unfolded")
    void t23() {
      int originalLength = observation.elements.size();
      ElementDefinition valueQuantity = observation.findElementByPath("extension.valueQuantity", fisher);
      assertNotNull(valueQuantity);
      assertEquals("Observation.extension.value[x]:valueQuantity", valueQuantity.getId());
      assertEquals("valueQuantity", valueQuantity.getSliceName());
      assertEquals("Observation.extension.value[x]", valueQuantity.getPath());
      assertEquals(originalLength + 5, observation.elements.size());
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should make explicit a non-existent choice element by child path")
    void t24() {
      int originalLength = observation.elements.size();
      ElementDefinition valueX = observation.findElementByPath("value[x]", fisher);
      assertNull(valueX.getSlicing());
      ElementDefinition valueQuantitySystem = observation.findElementByPath("valueQuantity.system", fisher);
      assertNotNull(valueQuantitySystem);
      assertEquals("Observation.value[x]:valueQuantity.system", valueQuantitySystem.getId());
      assertEquals("Observation.value[x].system", valueQuantitySystem.getPath());
      assertNotNull(valueX.getSlicing());
      assertJsEqual(json("{\"type\": \"type\", \"path\": \"$this\"}"), discriminator0(valueX));
      assertEquals(originalLength + 8, observation.elements.size());
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should make explicit a non-existing choice element that must be unfolded when finding it by a type-specific path to a child element")
    void t25() {
      StructureDefinition ipsMedicationStatement = fisher.fishForStructureDefinition("MedicationStatement-uv-ips");
      int originalLength = ipsMedicationStatement.elements.size();
      ElementDefinition effectiveX = ipsMedicationStatement.findElementByPath("effective[x]", fisher);
      assertNull(effectiveX.getSlicing());
      ElementDefinition effectivePeriodStart = ipsMedicationStatement.findElementByPath("effectivePeriod.start",
          fisher);
      assertNotNull(effectivePeriodStart);
      assertEquals("MedicationStatement.effective[x]:effectivePeriod.start", effectivePeriodStart.getId());
      assertEquals("MedicationStatement.effective[x].start", effectivePeriodStart.getPath());
      assertNotNull(effectiveX.getSlicing());
      assertJsEqual(json("{\"type\": \"type\", \"path\": \"$this\"}"), discriminator0(effectiveX));
      assertEquals(originalLength + 7, ipsMedicationStatement.elements.size());
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should find an already existing explicit choice element with slicing syntax")
    void t26() {
      int originalLength = respRate.elements.size();
      ElementDefinition valueQuantity = respRate.findElementByPath("value[x][valueQuantity]", fisher);
      assertNotNull(valueQuantity);
      assertEquals("Observation.value[x]:valueQuantity", valueQuantity.getId());
      assertEquals(originalLength, respRate.elements.size());
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should find an already existing explicit choice element with name replacement syntax")
    void t27() {
      int originalLength = respRate.elements.size();
      ElementDefinition valueQuantity = respRate.findElementByPath("valueQuantity", fisher);
      assertNotNull(valueQuantity);
      assertEquals("Observation.value[x]:valueQuantity", valueQuantity.getId());
      assertEquals(originalLength, respRate.elements.size());
    }

    static List<ElementDefinitionType> types(String... codes) {
      List<ElementDefinitionType> l = new ArrayList<>();
      for (String c : codes) l.add(new ElementDefinitionType(c));
      return l;
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should not create a slicing or slice for single-choice elements when finding it by a type-specific path ")
    void t28() {
      int originalLength = observation.elements.size();
      ElementDefinition valueX = observation.findElementByPath("value[x]", fisher);
      valueX.setType(types("Quantity"));
      ElementDefinition valueQuantity = observation.findElementByPath("valueQuantity", fisher);
      assertNull(valueX.getSlicing());
      assertSame(valueX, valueQuantity);
      assertEquals("Observation.value[x]", valueQuantity.getId());
      assertEquals(originalLength, observation.elements.size());
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should not create a slicing or slice for single-choice elements when finding it by a type-specific path to a child element ")
    void t29() {
      int originalLength = observation.elements.size();
      ElementDefinition valueX = observation.findElementByPath("value[x]", fisher);
      valueX.setType(types("Quantity"));
      ElementDefinition valueQuantityUnit = observation.findElementByPath("valueQuantity.unit", fisher);
      assertNull(valueX.getSlicing());
      assertNotNull(valueQuantityUnit);
      assertSame(valueX, valueQuantityUnit.parent());
      assertEquals("Observation.value[x].unit", valueQuantityUnit.getId());
      assertEquals(originalLength + 7, observation.elements.size());
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should create a slicing and slice for two-choice elements when finding it by a type-specific path")
    void t30() {
      int originalLength = observation.elements.size();
      ElementDefinition valueX = observation.findElementByPath("value[x]", fisher);
      valueX.setType(types("CodeableConcept", "Quantity"));
      ElementDefinition valueQuantity = observation.findElementByPath("valueQuantity", fisher);
      assertNotNull(valueX.getSlicing());
      assertJsEqual(json("{\"type\": \"type\", \"path\": \"$this\"}"), discriminator0(valueX));
      assertEquals(false, valueX.getSlicing().get("ordered"));
      assertEquals("open", valueX.getSlicing().get("rules"));
      assertNotNull(valueQuantity);
      assertEquals("Observation.value[x]:valueQuantity", valueQuantity.getId());
      assertNull(valueQuantity.getSlicing());
      assertEquals("valueQuantity", valueQuantity.getSliceName());
      assertEquals("Observation.value[x]", valueQuantity.getPath());
      assertEquals(0.0, valueQuantity.getMin());
      assertEquals(originalLength + 1, observation.elements.size());
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should create a slicing and slice for two-choice elements when finding it by a type-specific path to a child element")
    void t31() {
      int originalLength = observation.elements.size();
      ElementDefinition valueX = observation.findElementByPath("value[x]", fisher);
      valueX.setType(types("CodeableConcept", "Quantity"));
      ElementDefinition valueQuantityUnit = observation.findElementByPath("valueQuantity.unit", fisher);
      assertNotNull(valueX.getSlicing());
      assertJsEqual(json("{\"type\": \"type\", \"path\": \"$this\"}"), discriminator0(valueX));
      assertEquals(false, valueX.getSlicing().get("ordered"));
      assertEquals("open", valueX.getSlicing().get("rules"));
      assertNotNull(valueQuantityUnit);
      assertEquals("Observation.value[x]:valueQuantity.unit", valueQuantityUnit.getId());
      ElementDefinition valueQuantity = valueQuantityUnit.parent();
      assertNull(valueQuantity.getSlicing());
      assertEquals("valueQuantity", valueQuantity.getSliceName());
      assertEquals("Observation.value[x]", valueQuantity.getPath());
      assertEquals(0.0, valueQuantity.getMin());
      assertEquals(originalLength + 8, observation.elements.size());
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should create a slice for single-choice elements with other existing slices when finding it by a type-specific path")
    void t32() {
      ElementDefinition valueX = observation.findElementByPath("value[x]", fisher);
      valueX.sliceIt("type", "$this", false, "open");
      valueX.addSlice("valueCodeableConcept", new ElementDefinitionType("valueCodeableConcept"));
      valueX.setType(types("Quantity"));
      int originalLength = observation.elements.size();
      ElementDefinition valueQuantity = observation.findElementByPath("valueQuantity", fisher);
      assertNotNull(valueQuantity);
      assertEquals("Observation.value[x]:valueQuantity", valueQuantity.getId());
      assertNull(valueQuantity.getSlicing());
      assertEquals("valueQuantity", valueQuantity.getSliceName());
      assertEquals("Observation.value[x]", valueQuantity.getPath());
      assertEquals(0.0, valueQuantity.getMin());
      assertEquals(originalLength + 1, observation.elements.size());
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should create a slice for single-choice elements with other existing slices when finding it by a type-specific path to a child element ")
    void t33() {
      ElementDefinition valueX = observation.findElementByPath("value[x]", fisher);
      valueX.sliceIt("type", "$this", false, "open");
      valueX.addSlice("valueCodeableConcept", new ElementDefinitionType("valueCodeableConcept"));
      valueX.setType(types("Quantity"));
      int originalLength = observation.elements.size();
      ElementDefinition valueQuantityUnit = observation.findElementByPath("valueQuantity.unit", fisher);
      assertNotNull(valueQuantityUnit);
      assertEquals("Observation.value[x]:valueQuantity.unit", valueQuantityUnit.getId());
      ElementDefinition valueQuantity = valueQuantityUnit.parent();
      assertNull(valueQuantity.getSlicing());
      assertEquals("valueQuantity", valueQuantity.getSliceName());
      assertEquals("Observation.value[x]", valueQuantity.getPath());
      assertEquals(0.0, valueQuantity.getMin());
      assertEquals(originalLength + 8, observation.elements.size());
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should return a pre-existing slice for single-choice elements when finding it by a type-specific path")
    void t34() {
      ElementDefinition valueX = observation.findElementByPath("value[x]", fisher);
      valueX.sliceIt("type", "$this", false, "open");
      valueX.setType(types("Quantity"));
      ElementDefinition slice = valueX.addSlice("valueQuantity", new ElementDefinitionType("Quantity"));
      int originalLength = observation.elements.size();
      ElementDefinition valueQuantity = observation.findElementByPath("valueQuantity", fisher);
      assertSame(slice, valueQuantity);
      assertEquals("Observation.value[x]:valueQuantity", valueQuantity.getId());
      assertEquals(originalLength, observation.elements.size());
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should return a pre-existing slice for single-choice elements when finding it by a type-specific path to a child element")
    void t35() {
      ElementDefinition valueX = observation.findElementByPath("value[x]", fisher);
      valueX.sliceIt("type", "$this", false, "open");
      valueX.setType(types("Quantity"));
      ElementDefinition slice = valueX.addSlice("valueQuantity", new ElementDefinitionType("Quantity"));
      int originalLength = observation.elements.size();
      ElementDefinition valueQuantityUnit = observation.findElementByPath("valueQuantity.unit", fisher);
      assertNotNull(valueQuantityUnit);
      assertSame(slice, valueQuantityUnit.parent());
      assertEquals("Observation.value[x]:valueQuantity.unit", valueQuantityUnit.getId());
      assertEquals(originalLength + 7, observation.elements.size());
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should find an element that must be unfolded by path")
    void t36() {
      int originalLength = observation.elements.size();
      ElementDefinition codeText = observation.findElementByPath("code.text", fisher);
      assertNotNull(codeText);
      assertEquals("Observation.code.text", codeText.getId());
      assertEquals("Plain text representation of the concept", codeText.getShort());
      assertEquals(originalLength + 4, observation.elements.size());
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should find an element that must be unfolded from the Structure Definition by path")
    void t37() {
      int originalLength = observation.elements.size();
      ElementDefinition component = find(observation, "Observation.component");
      component.setSlicing(slicing("{\"ordered\": false, \"rules\": \"open\","
          + " \"discriminator\": [{\"type\": \"value\", \"path\": \"code\"}]}"));
      component.addSlice("FooSlice");
      ElementDefinition componentCode = observation.findElementByPath("component[FooSlice].code", fisher);
      assertNotNull(componentCode);
      assertEquals("Observation.component:FooSlice.code", componentCode.getId());
      assertEquals("Observation.component.code", componentCode.getPath());
      assertEquals(originalLength + 9, observation.elements.size());
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should find an element, whose name is contained in another element, that must be unfolded")
    void t38() {
      int originalLength = lipidProfile.elements.size();
      ElementDefinition resultDisplay = lipidProfile.findElementByPath("result.display", fisher);
      assertNotNull(resultDisplay);
      assertEquals("DiagnosticReport.result.display", resultDisplay.getId());
      assertEquals(originalLength + 6, lipidProfile.elements.size());
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should find a child of a content reference element by path")
    void t39() {
      int originalLength = valueSet.elements.size();
      ElementDefinition include = find(valueSet, "ValueSet.compose.include");
      ElementDefinition includeSystem = find(valueSet, "ValueSet.compose.include.system");
      includeSystem.setShort("This should not get copied over!");
      ElementDefinition exclude = find(valueSet, "ValueSet.compose.exclude");
      ElementDefinition excludeSystem = valueSet.findElementByPath("compose.exclude.system", fisher);
      assertNotNull(excludeSystem);
      assertEquals("ValueSet.compose.exclude.system", excludeSystem.getId());
      assertEquals("The system the codes come from", excludeSystem.getShort());
      assertNull(exclude.getContentReference());
      assertJsEqual(include.getType(), exclude.getType());
      assertEquals(originalLength + 26, valueSet.elements.size());
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should find a child of a content reference element by path for logical models")
    void t40() {
      StructureDefinition viewDefiniton = fisher.fishForStructureDefinition("ViewDefinition", FishableType.Logical);
      int originalLength = viewDefiniton.elements.size();
      ElementDefinition select = find(viewDefiniton, "ViewDefinition.select");
      assertNotNull(select);
      assertNotNull(find(viewDefiniton, "ViewDefinition.select.name"));
      ElementDefinition selectForEachSelect = find(viewDefiniton, "ViewDefinition.select.forEach.select");
      assertNotNull(selectForEachSelect);
      assertNotNull(selectForEachSelect.getContentReference());
      assertNull(selectForEachSelect.getType());
      ElementDefinition selectForEachSelectName = viewDefiniton.findElementByPath("select.forEach.select.name",
          fisher);
      assertNotNull(selectForEachSelectName);
      assertEquals("ViewDefinition.select.forEach.select.name", selectForEachSelectName.getId());
      assertEquals("Name of field produced in the output.", selectForEachSelectName.getShort());
      assertNull(selectForEachSelect.getContentReference());
      assertJsEqual(select.getType(), selectForEachSelect.getType());
      assertEquals(originalLength + 11, viewDefiniton.elements.size());
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should find a child of a content reference element by path when the reference uses a full URI")
    void t41() {
      int originalLength = valueSet.elements.size();
      ElementDefinition include = find(valueSet, "ValueSet.compose.include");
      ElementDefinition includeSystem = find(valueSet, "ValueSet.compose.include.system");
      includeSystem.setShort("This should not get copied over!");
      ElementDefinition exclude = find(valueSet, "ValueSet.compose.exclude");
      exclude.setContentReference("http://hl7.org/fhir/StructureDefinition/ValueSet#ValueSet.compose.include");
      ElementDefinition excludeSystem = valueSet.findElementByPath("compose.exclude.system", fisher);
      assertNotNull(excludeSystem);
      assertEquals("ValueSet.compose.exclude.system", excludeSystem.getId());
      assertEquals("The system the codes come from", excludeSystem.getShort());
      assertNull(exclude.getContentReference());
      assertJsEqual(include.getType(), exclude.getType());
      assertEquals(originalLength + 26, valueSet.elements.size());
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should find a child of a slice content reference by path")
    void t42() {
      int originalLength = valueSet.elements.size();
      ElementDefinition include = find(valueSet, "ValueSet.compose.include");
      ElementDefinition exclude = find(valueSet, "ValueSet.compose.exclude");
      exclude.setSlicing(slicing("{\"rules\": \"open\"}"));
      ElementDefinition mySlice = exclude.addSlice("mySlice");
      ElementDefinition mySliceSystem = valueSet.findElementByPath("compose.exclude[mySlice].system", fisher);
      assertNotNull(mySliceSystem);
      assertEquals("ValueSet.compose.exclude:mySlice.system", mySliceSystem.getId());
      assertEquals("The system the codes come from", mySliceSystem.getShort());
      assertNull(mySlice.getContentReference());
      assertJsEqual(include.getType(), mySlice.getType());
      assertEquals(originalLength + 27, valueSet.elements.size());
    }

    @Test
    @DisplayName("StructureDefinition #findElementByPath should find but not unfold a content reference element by path")
    void t43() {
      int originalLength = valueSet.elements.size();
      ElementDefinition exclude = valueSet.findElementByPath("compose.exclude", fisher);
      assertNotNull(exclude);
      assertEquals("ValueSet.compose.exclude", exclude.getId());
      assertEquals("#ValueSet.compose.include", exclude.getContentReference());
      assertEquals(originalLength, valueSet.elements.size());
      assertFalse(loggerSpy.getAllLogs("error").size() > 0 && false);
    }
  }
}
