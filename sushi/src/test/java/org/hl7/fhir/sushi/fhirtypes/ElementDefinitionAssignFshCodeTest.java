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

// port of sushi/test/fhirtypes/ElementDefinition.assignFshCode.test.ts (SUSHI 3.21.1)
@DisplayName("ElementDefinition")
class ElementDefinitionAssignFshCodeTest {
  static FHIRDefinitions defs;
  StructureDefinition observation;
  StructureDefinition condition;
  FshCode fooBarCode;
  FshCode barFooCode;
  FshCode versionedCode;
  FshCode codeWithDisplay;
  static TestFisher fisher;
  @BeforeAll
  static void beforeAll0() {
    defs = r4Defs();
    fisher = new TestFisher().withFHIR(defs);
  }
  @BeforeEach
  void beforeEach1() {
    observation = fisher.fishForStructureDefinition("Observation");
    condition = fisher.fishForStructureDefinition("Condition");
    fooBarCode = new FshCode("bar", "http://foo.com");
    barFooCode = new FshCode("foo", "http://bar.com");
    versionedCode = new FshCode("versioned", "http://versioned.com|7.6.5");
    codeWithDisplay = new FshCode("bar", "http://foo.com", "Foo Bar");
    loggerSpy.reset();
  }

  @Nested
  @DisplayName("#assignFshCode()")
  class AssignFshCode {

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should assign a code to a CodeableConcept")
    void t2() {
      ElementDefinition concept = el(observation, "Observation.code");
      concept.assignValue(fooBarCode);
      assertJsEqual(obj("coding", list(obj("code", "bar", "system", "http://foo.com"))), at(concept, "patternCodeableConcept"));
      assertNull(at(concept, "fixedCodeableConcept"));
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should assign a code to a CodeableConcept (exactly)")
    void t3() {
      ElementDefinition concept = el(observation, "Observation.code");
      concept.assignValue(fooBarCode, true);
      assertJsEqual(obj("coding", list(obj("code", "bar", "system", "http://foo.com"))), at(concept, "fixedCodeableConcept"));
      assertNull(at(concept, "patternCodeableConcept"));
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should assign a code with a version to a CodeableConcept")
    void t4() {
      ElementDefinition concept = observation.findElementByPath("code", fisher);
      concept.assignValue(versionedCode);
      assertJsEqual(obj("coding", list(obj("code", "versioned", "system", "http://versioned.com", "version", "7.6.5"))), at(concept, "patternCodeableConcept"));
      assertNull(at(concept, "fixedCodeableConcept"));
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should assign a code with a version to a CodeableConcept (exactly)")
    void t5() {
      ElementDefinition concept = observation.findElementByPath("code", fisher);
      concept.assignValue(versionedCode, true);
      assertJsEqual(obj("coding", list(obj("code", "versioned", "system", "http://versioned.com", "version", "7.6.5"))), at(concept, "fixedCodeableConcept"));
      assertNull(at(concept, "patternCodeableConcept"));
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should assign a code with a display to a CodeableConcept")
    void t6() {
      ElementDefinition concept = el(observation, "Observation.code");
      concept.assignValue(codeWithDisplay);
      assertJsEqual(obj("coding", list(obj("code", "bar", "system", "http://foo.com", "display", "Foo Bar"))), at(concept, "patternCodeableConcept"));
      assertNull(at(concept, "fixedCodeableConcept"));
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should assign a code with a display to a CodeableConcept (exacty)")
    void t7() {
      ElementDefinition concept = el(observation, "Observation.code");
      concept.assignValue(codeWithDisplay, true);
      assertJsEqual(obj("coding", list(obj("code", "bar", "system", "http://foo.com", "display", "Foo Bar"))), at(concept, "fixedCodeableConcept"));
      assertNull(at(concept, "patternCodeableConcept"));
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should throw ValueAlreadyAssignedError when assigning a code to a CodeableConcept assigned to a different code by pattern[x]")
    void t8() {
      ElementDefinition concept = el(observation, "Observation.code");
      // Setup original assigned code
      concept.assignValue(fooBarCode);
      ElementDefinition clone = concept.clone(false);
      assertThrowsMatching("http:\\/\\/bar.com#foo.*CodeableConcept.*\"code\":\"bar\"", () -> { clone.assignValue(barFooCode);
 });
      assertThrowsMatching("http:\\/\\/bar.com#foo.*CodeableConcept.*\"code\":\"bar\"", () -> { clone.assignValue(barFooCode, true);
 });
      assertSameState(concept, clone);
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should throw ValueAlreadyAssignedError when assigning a code to a CodeableConcept assigned to a different code by fixed[x]")
    void t9() {
      ElementDefinition concept = el(observation, "Observation.code");
      // Setup original assigned code
      concept.assignValue(fooBarCode, true);
      ElementDefinition clone = concept.clone(false);
      assertThrowsMatching("http:\\/\\/bar.com#foo.*CodeableConcept.*\"code\":\"bar\"", () -> { clone.assignValue(barFooCode, true);
 });
      assertSameState(concept, clone);
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should throw ValueAlreadyAssignedError when assigning a code to a CodeableConcept assigned to a different code by a parent by pattern[x]")
    void t10() {
      ElementDefinition rr = el(observation, "Observation.referenceRange");
      // @ts-ignore (technically pattern[x] doesn't allow BackboneElement, but this is ok for the purpose of this test)
      setAt(rr, "patternBackboneElement", obj("type", obj("coding", list(obj("system", "http://foo.com", "code", "bar")))));
      ElementDefinition rrType = el(observation, "Observation.referenceRange.type");
      ElementDefinition clone = rrType.clone(false);
      assertThrowsMatching("http:\\/\\/bar.com#foo.*CodeableConcept.*\"code\":\"bar\"", () -> { rrType.assignValue(barFooCode);
 });
      assertThrowsMatching("http:\\/\\/bar.com#foo.*CodeableConcept.*\"code\":\"bar\"", () -> { rrType.assignValue(barFooCode, true);
 });
      assertSameState(rrType, clone);
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should throw ValueAlreadyAssignedError when assigning a code to a CodeableConcept assigned to a different code by a parent by fixed[x]")
    void t11() {
      ElementDefinition rr = el(observation, "Observation.referenceRange");
      // @ts-ignore (technically fixed[x] doesn't allow BackboneElement, but this is ok for the purpose of this test)
      setAt(rr, "fixedBackboneElement", obj("type", obj("coding", list(obj("system", "http://foo.com", "code", "bar")))));
      ElementDefinition rrType = el(observation, "Observation.referenceRange.type");
      ElementDefinition clone = rrType.clone(false);
      assertThrowsMatching("http:\\/\\/bar.com#foo.*CodeableConcept.*\"code\":\"bar\"", () -> { rrType.assignValue(barFooCode);
 });
      assertThrowsMatching("http:\\/\\/bar.com#foo.*CodeableConcept.*\"code\":\"bar\"", () -> { rrType.assignValue(barFooCode, true);
 });
      assertSameState(rrType, clone);
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should throw FixedToPatternError when trying to change fixed[x] to pattern[x]")
    void t12() {
      ElementDefinition concept = el(observation, "Observation.code");
      // Setup original assigned code
      concept.assignValue(fooBarCode, true);
      ElementDefinition clone = concept.clone(false);
      assertThrowsContaining("Cannot assign this element using a pattern; as it is already assigned in the StructureDefinition using fixedCodeableConcept.", () -> { clone.assignValue(fooBarCode);
 });
      assertSameState(concept, clone);
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should throw InvalidUriError when assigning a code with a non-URI value")
    void t13() {
      ElementDefinition category = el(observation, "Observation.category");
      ElementDefinition clone = category.clone(false);
      assertThrowsMatching("notAUri", () -> { clone.assignValue(new FshCode("code", "notAUri"));
 });
      assertThrowsMatching("notAUri", () -> { clone.assignValue(new FshCode("code", "notAUri"), true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should NOT throw InvalidUriError when assigning a code with a non-URI value to a primitive code")
    void t14() {
      ElementDefinition status = el(observation, "Observation.status");
      ElementDefinition clone = status.clone(false);
      clone.assignValue(new FshCode("code", "notAUri"));
      assertEquals(1, size(loggerSpy.getAllLogs("warn")));
      assertMatchesJs("code notAUri#code is invalid.*specified system is not a URI", loggerSpy.getLastMessage("warn"));
      assertMatchesJs("Observation.status is a code", loggerSpy.getLastMessage("warn"));
      assertMatchesJs("specifying a code only \\(e\\.g\\., #code\\)\\.", loggerSpy.getLastMessage("warn"));
      clone.assignValue(new FshCode("code", "notAUri"), true);
      assertEquals(2, size(loggerSpy.getAllLogs("warn")));
      assertMatchesJs("code notAUri#code is invalid.*specified system is not a URI", loggerSpy.getLastMessage("warn"));
      assertMatchesJs("Observation.status is a code", loggerSpy.getLastMessage("warn"));
      assertMatchesJs("specifying a code only \\(e\\.g\\., #code\\)\\.", loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should throw MismatchedBindingTypeError when assigning a code with a ValueSet as a system")
    void t15() {
      ElementDefinition category = el(observation, "Observation.category");
      ElementDefinition clone = category.clone(false);
      assertThrowsMatching("CodeSystem", () -> { clone.assignValue(new FshCode("code", "http://hl7.org/fhir/ValueSet/observation-category"), false, fisher);
 });
      assertThrowsMatching("CodeSystem", () -> { clone.assignValue(new FshCode("code", "http://hl7.org/fhir/ValueSet/observation-category"), true, fisher);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should throw MismatchedBindingTypeError when assigning a code with a ValueSet as a system when provided with a version")
    void t16() {
      ElementDefinition category = el(observation, "Observation.category");
      ElementDefinition clone = category.clone(false);
      assertThrowsMatching("CodeSystem", () -> { clone.assignValue(new FshCode("code", "http://hl7.org/fhir/ValueSet/observation-category|5.0.0"), false, fisher);
 });
      assertThrowsMatching("CodeSystem", () -> { clone.assignValue(new FshCode("code", "http://hl7.org/fhir/ValueSet/observation-category|5.0.0"), true, fisher);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should NOT throw MismatchedBindingTypeError when assigning a code with a ValueSet as a system to a primitive code")
    void t17() {
      ElementDefinition status = el(observation, "Observation.status");
      ElementDefinition clone = status.clone(false);
      clone.assignValue(new FshCode("final", "http://hl7.org/fhir/ValueSet/observation-status"), false, fisher);
      assertEquals(1, size(loggerSpy.getAllLogs("warn")));
      assertMatchesJs("code http:\\/\\/hl7\\.org\\/fhir\\/ValueSet\\/observation-status#final is invalid.*specified system is a ValueSet", loggerSpy.getLastMessage("warn"));
      assertMatchesJs("Observation.status is a code", loggerSpy.getLastMessage("warn"));
      assertMatchesJs("specifying a code only \\(e\\.g\\., #final\\)\\.", loggerSpy.getLastMessage("warn"));
      clone.assignValue(new FshCode("final", "http://hl7.org/fhir/ValueSet/observation-status"), true, fisher);
      assertEquals(2, size(loggerSpy.getAllLogs("warn")));
      assertMatchesJs("code http:\\/\\/hl7\\.org\\/fhir\\/ValueSet\\/observation-status#final is invalid.*specified system is a ValueSet", loggerSpy.getLastMessage("warn"));
      assertMatchesJs("Observation.status is a code", loggerSpy.getLastMessage("warn"));
      assertMatchesJs("specifying a code only \\(e\\.g\\., #final\\)\\.", loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should assign a code to a Coding")
    void t18() {
      ElementDefinition concept = el(observation, "Observation.code");
      concept.unfold(fisher);
      ElementDefinition coding = el(observation, "Observation.code.coding");
      coding.assignValue(fooBarCode);
      assertJsEqual(obj("code", "bar", "system", "http://foo.com"), at(coding, "patternCoding"));
      assertNull(at(coding, "fixedCoding"));
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should assign a code to a Coding (exactly)")
    void t19() {
      ElementDefinition concept = el(observation, "Observation.code");
      concept.unfold(fisher);
      ElementDefinition coding = el(observation, "Observation.code.coding");
      coding.assignValue(fooBarCode, true);
      assertJsEqual(obj("code", "bar", "system", "http://foo.com"), at(coding, "fixedCoding"));
      assertNull(at(coding, "patternCoding"));
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should assign a code with a version to a Coding")
    void t20() {
      ElementDefinition coding = observation.findElementByPath("code.coding", fisher);
      coding.assignValue(versionedCode);
      assertJsEqual(obj("code", "versioned", "system", "http://versioned.com", "version", "7.6.5"), at(coding, "patternCoding"));
      assertNull(at(coding, "fixedCoding"));
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should assign a code with a version to a Coding (exactly)")
    void t21() {
      ElementDefinition coding = observation.findElementByPath("code.coding", fisher);
      coding.assignValue(versionedCode, true);
      assertJsEqual(obj("code", "versioned", "system", "http://versioned.com", "version", "7.6.5"), at(coding, "fixedCoding"));
      assertNull(at(coding, "patternCoding"));
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should assign a code with a display to a Coding")
    void t22() {
      ElementDefinition concept = el(observation, "Observation.code");
      concept.unfold(fisher);
      ElementDefinition coding = el(observation, "Observation.code.coding");
      coding.assignValue(codeWithDisplay);
      assertJsEqual(obj("code", "bar", "system", "http://foo.com", "display", "Foo Bar"), at(coding, "patternCoding"));
      assertNull(at(coding, "fixedCoding"));
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should assign a code with a display to a Coding (exactly)")
    void t23() {
      ElementDefinition concept = el(observation, "Observation.code");
      concept.unfold(fisher);
      ElementDefinition coding = el(observation, "Observation.code.coding");
      coding.assignValue(codeWithDisplay, true);
      assertJsEqual(obj("code", "bar", "system", "http://foo.com", "display", "Foo Bar"), at(coding, "fixedCoding"));
      assertNull(at(coding, "patternCoding"));
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should throw ValueAlreadyAssignedError when assigning a code to a Coding assigned to a different code by pattern[x]")
    void t24() {
      ElementDefinition concept = el(observation, "Observation.code");
      concept.unfold(fisher);
      ElementDefinition coding = el(observation, "Observation.code.coding");
      // Setup original assigned code
      coding.assignValue(fooBarCode);
      ElementDefinition clone = coding.clone(false);
      assertThrowsMatching("http:\\/\\/bar.com#foo.*Coding.*\"code\":\"bar\"", () -> { clone.assignValue(barFooCode);
 });
      assertThrowsMatching("http:\\/\\/bar.com#foo.*Coding.*\"code\":\"bar\"", () -> { clone.assignValue(barFooCode, true);
 });
      assertSameState(coding, clone);
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should throw ValueAlreadyAssignedError when assigning a code to a Coding assigned to a different code by fixed[x]")
    void t25() {
      ElementDefinition concept = el(observation, "Observation.code");
      concept.unfold(fisher);
      ElementDefinition coding = el(observation, "Observation.code.coding");
      // Setup original assigned code
      coding.assignValue(fooBarCode, true);
      ElementDefinition clone = coding.clone(false);
      assertThrowsMatching("http:\\/\\/bar.com#foo.*Coding.*\"code\":\"bar\"", () -> { clone.assignValue(barFooCode, true);
 });
      assertSameState(coding, clone);
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should throw ValueAlreadyAssignedError when assigning a code to a Coding assigned to a different code by a parent by pattern[x]")
    void t26() {
      ElementDefinition rr = el(observation, "Observation.referenceRange");
      // @ts-ignore (technically pattern[x] doesn't allow BackboneElement, but this is ok for the purpose of this test)
      setAt(rr, "patternBackboneElement", obj("type", obj("coding", obj("system", "http://foo.com", "code", "bar"))));
      ElementDefinition rrType = el(observation, "Observation.referenceRange.type");
      rrType.unfold(fisher);
      ElementDefinition rrTypeCoding = el(observation, "Observation.referenceRange.type.coding");
      ElementDefinition clone = rrTypeCoding.clone(false);
      assertThrowsMatching("http:\\/\\/bar.com#foo.*Coding.*\"code\":\"bar\"", () -> { clone.assignValue(barFooCode);
 });
      assertThrowsMatching("http:\\/\\/bar.com#foo.*Coding.*\"code\":\"bar\"", () -> { clone.assignValue(barFooCode, true);
 });
      assertSameState(rrTypeCoding, clone);
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should throw ValueAlreadyAssignedError when assigning a code to a Coding assigned to a different code by a parent by fixed[x]")
    void t27() {
      ElementDefinition rr = el(observation, "Observation.referenceRange");
      // @ts-ignore (technically fixed[x] doesn't allow BackboneElement, but this is ok for the purpose of this test)
      setAt(rr, "fixedBackboneElement", obj("type", obj("coding", obj("system", "http://foo.com", "code", "bar"))));
      ElementDefinition rrType = el(observation, "Observation.referenceRange.type");
      rrType.unfold(fisher);
      ElementDefinition rrTypeCoding = el(observation, "Observation.referenceRange.type.coding");
      ElementDefinition clone = rrTypeCoding.clone(false);
      assertThrowsMatching("http:\\/\\/bar.com#foo.*Coding.*\"code\":\"bar\"", () -> { clone.assignValue(barFooCode, true);
 });
      assertSameState(rrTypeCoding, clone);
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should throw AssignedToPatternError when trying to change fixed[x] to pattern[x]")
    void t28() {
      ElementDefinition concept = el(observation, "Observation.code");
      concept.unfold(fisher);
      ElementDefinition coding = el(observation, "Observation.code.coding");
      // Setup original assigned code
      coding.assignValue(fooBarCode, true);
      ElementDefinition clone = coding.clone(false);
      assertThrowsContaining("Cannot assign this element using a pattern; as it is already assigned in the StructureDefinition using fixedCoding.", () -> { clone.assignValue(fooBarCode);
 });
      assertSameState(coding, clone);
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should assign a code to a code")
    void t29() {
      ElementDefinition code = el(observation, "Observation.status");
      code.assignValue(fooBarCode);
      assertEquals("bar", at(code, "patternCode"));
      assertNull(at(code, "fixedCode"));
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should assign a code to a code (exactly)")
    void t30() {
      ElementDefinition code = el(observation, "Observation.status");
      code.assignValue(fooBarCode, true);
      assertEquals("bar", at(code, "fixedCode"));
      assertNull(at(code, "patternCode"));
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should throw ValueAlreadyAssignedError when assigning a code to a code assigned to a different code by pattern[x]")
    void t31() {
      ElementDefinition code = el(observation, "Observation.status");
      // Setup original assigned code
      code.assignValue(fooBarCode);
      ElementDefinition clone = code.clone(false);
      assertThrowsMatching("http:\\/\\/bar.com#foo.*bar", () -> { clone.assignValue(barFooCode);
 });
      assertThrowsMatching("http:\\/\\/bar.com#foo.*bar", () -> { clone.assignValue(barFooCode, true);
 });
      assertSameState(code, clone);
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should throw ValueAlreadyAssignedError when assigning a code to a code assigned to a different code by fixed[x]")
    void t32() {
      ElementDefinition code = el(observation, "Observation.status");
      // Setup original assigned code
      code.assignValue(fooBarCode, true);
      ElementDefinition clone = code.clone(false);
      assertThrowsMatching("http:\\/\\/bar.com#foo.*bar", () -> { clone.assignValue(barFooCode, true);
 });
      assertSameState(code, clone);
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should throw ValueAlreadyAssignedError when assigning a code to a code assigned to a different code by a parent by pattern[x]")
    void t33() {
      ElementDefinition code = el(observation, "Observation.code");
      code.unfold(fisher);
      ElementDefinition coding = el(observation, "Observation.code.coding");
      coding.unfold(fisher);
      ElementDefinition codeCodingCode = el(observation, "Observation.code.coding.code");
      // Setup original assigned code
      code.assignValue(fooBarCode);
      ElementDefinition clone = codeCodingCode.clone(false);
      assertThrowsMatching("http:\\/\\/bar.com#foo.*bar", () -> { clone.assignValue(barFooCode);
 });
      assertThrowsMatching("http:\\/\\/bar.com#foo.*bar", () -> { clone.assignValue(barFooCode, true);
 });
      assertSameState(codeCodingCode, clone);
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should throw ValueAlreadyAssignedError when assigning a code to a code assigned to a different code by a parent by fixed[x]")
    void t34() {
      ElementDefinition code = el(observation, "Observation.code");
      code.unfold(fisher);
      ElementDefinition coding = el(observation, "Observation.code.coding");
      coding.unfold(fisher);
      ElementDefinition codeCodingCode = el(observation, "Observation.code.coding.code");
      // Setup original assigned code
      code.assignValue(fooBarCode, true);
      ElementDefinition clone = codeCodingCode.clone(false);
      assertThrowsMatching("http:\\/\\/bar.com#foo.*bar", () -> { clone.assignValue(barFooCode, true);
 });
      assertSameState(codeCodingCode, clone);
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should throw FixedToPatternError when trying to change fixed[x] to pattern[x]")
    void t35() {
      ElementDefinition code = el(observation, "Observation.code");
      code.unfold(fisher);
      ElementDefinition coding = el(observation, "Observation.code.coding");
      coding.unfold(fisher);
      ElementDefinition codeCodingCode = el(observation, "Observation.code.coding.code");
      // Setup original assigned code
      codeCodingCode.assignValue(fooBarCode, true);
      ElementDefinition clone = codeCodingCode.clone(false);
      assertThrowsContaining("Cannot assign this element using a pattern; as it is already assigned in the StructureDefinition using fixedCode.", () -> { clone.assignValue(fooBarCode);
 });
      assertSameState(codeCodingCode, clone);
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should assign a code to a Quantity")
    void t36() {
      ElementDefinition quantity = el(observation, "Observation.referenceRange.low");
      quantity.assignValue(fooBarCode);
      assertJsEqual(obj("code", "bar", "system", "http://foo.com"), at(quantity, "patternQuantity"));
      assertNull(at(quantity, "fixedQuantity"));
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should assign a code to a Quantity (exactly)")
    void t37() {
      ElementDefinition quantity = el(observation, "Observation.referenceRange.low");
      quantity.assignValue(fooBarCode, true);
      assertJsEqual(obj("code", "bar", "system", "http://foo.com"), at(quantity, "fixedQuantity"));
      assertNull(at(quantity, "patternQuantity"));
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should assign a code to an Age")
    void t38() {
      ElementDefinition onsetX = el(condition, "Condition.onset[x]");
      setAt(onsetX, "type", list(new ElementDefinitionType("Age")));
      onsetX.assignValue(fooBarCode, false, fisher);
      assertJsEqual(obj("code", "bar", "system", "http://foo.com"), at(onsetX, "patternAge"));
      assertNull(at(onsetX, "fixedAge"));
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should assign a code to an Age (exactly)")
    void t39() {
      ElementDefinition onsetX = el(condition, "Condition.onset[x]");
      setAt(onsetX, "type", list(new ElementDefinitionType("Age")));
      onsetX.assignValue(fooBarCode, true, fisher);
      assertNull(at(onsetX, "patternAge"));
      assertJsEqual(obj("code", "bar", "system", "http://foo.com"), at(onsetX, "fixedAge"));
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should assign a code with a display to an Age")
    void t40() {
      ElementDefinition onsetX = el(condition, "Condition.onset[x]");
      setAt(onsetX, "type", list(new ElementDefinitionType("Age")));
      onsetX.assignValue(codeWithDisplay, false, fisher);
      assertJsEqual(obj("code", "bar", "system", "http://foo.com", "unit", "Foo Bar"), at(onsetX, "patternAge"));
      assertNull(at(onsetX, "fixedAge"));
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should assign a code with a display to an Age (exactly)")
    void t41() {
      ElementDefinition onsetX = el(condition, "Condition.onset[x]");
      setAt(onsetX, "type", list(new ElementDefinitionType("Age")));
      onsetX.assignValue(codeWithDisplay, true, fisher);
      assertNull(at(onsetX, "patternAge"));
      assertJsEqual(obj("code", "bar", "system", "http://foo.com", "unit", "Foo Bar"), at(onsetX, "fixedAge"));
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should assign a code with a display to a Quantity")
    void t42() {
      ElementDefinition quantity = el(observation, "Observation.referenceRange.low");
      quantity.assignValue(codeWithDisplay);
      assertJsEqual(obj("code", "bar", "system", "http://foo.com", "unit", "Foo Bar"), at(quantity, "patternQuantity"));
      assertNull(at(quantity, "fixedQuantity"));
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should assign a code with a display to a Quantity (exactly)")
    void t43() {
      ElementDefinition quantity = el(observation, "Observation.referenceRange.low");
      quantity.assignValue(codeWithDisplay, true);
      assertJsEqual(obj("code", "bar", "system", "http://foo.com", "unit", "Foo Bar"), at(quantity, "fixedQuantity"));
      assertNull(at(quantity, "patternQuantity"));
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should throw ValueAlreadyAssignedError when assigning a code to a Quantity assigned to a different code by pattern[x]")
    void t44() {
      ElementDefinition quantity = el(observation, "Observation.referenceRange.low");
      // Setup original assigned code
      quantity.assignValue(fooBarCode);
      ElementDefinition clone = quantity.clone(false);
      assertThrowsMatching("http:\\/\\/bar.com#foo.*Quantity.*\"code\":\"bar\"", () -> { clone.assignValue(barFooCode);
 });
      assertThrowsMatching("http:\\/\\/bar.com#foo.*Quantity.*\"code\":\"bar\"", () -> { clone.assignValue(barFooCode, true);
 });
      assertSameState(quantity, clone);
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should throw ValueAlreadyAssignedError when assigning a code to a Quantity assigned to a different code by fixed[x]")
    void t45() {
      ElementDefinition quantity = el(observation, "Observation.referenceRange.low");
      // Setup original assigned code
      quantity.assignValue(fooBarCode, true);
      ElementDefinition clone = quantity.clone(false);
      assertThrowsMatching("http:\\/\\/bar.com#foo.*Quantity.*\"code\":\"bar\"", () -> { clone.assignValue(barFooCode, true);
 });
      assertSameState(quantity, clone);
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should throw ValueAlreadyAssignedError when assigning a code to a Quantity assigned to a different code by a parent be pattern[x]")
    void t46() {
      ElementDefinition rr = el(observation, "Observation.referenceRange");
      // @ts-ignore (technically pattern[x] doesn't allow BackboneElement, but this is ok for the purpose of this test)
      setAt(rr, "patternBackboneElement", obj("low", obj("system", "http://foo.com", "code", "bar")));
      ElementDefinition rrLow = el(observation, "Observation.referenceRange.low");
      ElementDefinition clone = rrLow.clone(false);
      assertThrowsMatching("http:\\/\\/bar.com#foo.*Quantity.*\"code\":\"bar\"", () -> { clone.assignValue(barFooCode);
 });
      assertThrowsMatching("http:\\/\\/bar.com#foo.*Quantity.*\"code\":\"bar\"", () -> { clone.assignValue(barFooCode, true);
 });
      assertSameState(rrLow, clone);
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should throw ValueAlreadyAssignedError when assigning a code to a Quantity assigned to a different code by a parent be fixed[x]")
    void t47() {
      ElementDefinition rr = el(observation, "Observation.referenceRange");
      // @ts-ignore (technically fixed[x] doesn't allow BackboneElement, but this is ok for the purpose of this test)
      setAt(rr, "fixedBackboneElement", obj("low", obj("system", "http://foo.com", "code", "bar")));
      ElementDefinition rrLow = el(observation, "Observation.referenceRange.low");
      ElementDefinition clone = rrLow.clone(false);
      assertThrowsMatching("http:\\/\\/bar.com#foo.*Quantity.*\"code\":\"bar\"", () -> { clone.assignValue(barFooCode, true);
 });
      assertSameState(rrLow, clone);
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should throw AssignedToPatternError when trying to change fixed[x] to pattern[x]")
    void t48() {
      ElementDefinition quantity = el(observation, "Observation.referenceRange.low");
      // Setup original assigned code
      quantity.assignValue(fooBarCode, true);
      ElementDefinition clone = quantity.clone(false);
      assertThrowsContaining("Cannot assign this element using a pattern; as it is already assigned in the StructureDefinition using fixedQuantity.", () -> { clone.assignValue(fooBarCode);
 });
      assertSameState(quantity, clone);
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should assign a code to a string")
    void t49() {
      ElementDefinition string = el(observation, "Observation.referenceRange.text");
      string.assignValue(fooBarCode);
      assertEquals("bar", at(string, "patternString"));
      assertNull(at(string, "fixedString"));
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should assign a code to a string (exactly)")
    void t50() {
      ElementDefinition string = el(observation, "Observation.referenceRange.text");
      string.assignValue(fooBarCode, true);
      assertEquals("bar", at(string, "fixedString"));
      assertNull(at(string, "patternString"));
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should throw ValueAlreadyAssignedError when assigning a code to a string assigned to a different string by pattern[x]")
    void t51() {
      ElementDefinition string = el(observation, "Observation.referenceRange.text");
      // Setup original assigned code
      string.assignValue(fooBarCode);
      ElementDefinition clone = string.clone(false);
      assertThrowsMatching("http:\\/\\/bar.com#foo.*bar", () -> { clone.assignValue(barFooCode);
 });
      assertThrowsMatching("http:\\/\\/bar.com#foo.*bar", () -> { clone.assignValue(barFooCode, true);
 });
      assertSameState(string, clone);
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should throw ValueAlreadyAssignedError when assigning a code to a string assigned to a different string by fixed[x]")
    void t52() {
      ElementDefinition string = el(observation, "Observation.referenceRange.text");
      // Setup original assigned code
      string.assignValue(fooBarCode, true);
      ElementDefinition clone = string.clone(false);
      assertThrowsMatching("http:\\/\\/bar.com#foo.*bar", () -> { clone.assignValue(barFooCode, true);
 });
      assertSameState(string, clone);
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should throw ValueAlreadyAssignedError when assigning a code to a string assigned to a different string by a parent by pattern[x]")
    void t53() {
      ElementDefinition code = el(observation, "Observation.code");
      setAt(code, "patternCodeableConcept", obj("text", "http://foo.com#bar"));
      code.unfold(fisher);
      ElementDefinition string = el(observation, "Observation.code.text");
      // Setup original assigned code
      ElementDefinition clone = string.clone(false);
      assertThrowsMatching("http:\\/\\/bar.com#foo.*#bar", () -> { clone.assignValue(barFooCode);
 });
      assertThrowsMatching("http:\\/\\/bar.com#foo.*#bar", () -> { clone.assignValue(barFooCode, true);
 });
      assertSameState(string, clone);
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should throw ValueAlreadyAssignedError when assigning a code to a string assigned to a different string by a parent by fixed[x]")
    void t54() {
      ElementDefinition code = el(observation, "Observation.code");
      setAt(code, "fixedCodeableConcept", obj("text", "http://foo.com#bar"));
      code.unfold(fisher);
      ElementDefinition string = el(observation, "Observation.code.text");
      // Setup original assigned code
      ElementDefinition clone = string.clone(false);
      assertThrowsMatching("http:\\/\\/bar.com#foo.*#bar", () -> { clone.assignValue(barFooCode, true);
 });
      assertSameState(string, clone);
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should throw FixedToPatternError when trying to change fixed[x] to pattern[x]")
    void t55() {
      ElementDefinition string = el(observation, "Observation.referenceRange.text");
      // Setup original assigned code
      string.assignValue(fooBarCode, true);
      ElementDefinition clone = string.clone(false);
      assertThrowsContaining("Cannot assign this element using a pattern; as it is already assigned in the StructureDefinition using fixedString.", () -> { clone.assignValue(fooBarCode);
 });
      assertSameState(string, clone);
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should assign a code to a uri")
    void t56() {
      ElementDefinition uri = el(observation, "Observation.implicitRules");
      uri.assignValue(fooBarCode);
      assertEquals("bar", at(uri, "patternUri"));
      assertNull(at(uri, "fixedUri"));
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should assign a code to a uri (exactly)")
    void t57() {
      ElementDefinition uri = el(observation, "Observation.implicitRules");
      uri.assignValue(fooBarCode, true);
      assertEquals("bar", at(uri, "fixedUri"));
      assertNull(at(uri, "patternUri"));
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should throw ValueAlreadyAssignedError when assigning a code to a uri assigned to a different uri by pattern[x]")
    void t58() {
      ElementDefinition uri = el(observation, "Observation.implicitRules");
      // Setup original assigned code
      uri.assignValue(fooBarCode);
      ElementDefinition clone = uri.clone(false);
      assertThrowsMatching("http:\\/\\/bar.com#foo.*bar", () -> { clone.assignValue(barFooCode);
 });
      assertThrowsMatching("http:\\/\\/bar.com#foo.*bar", () -> { clone.assignValue(barFooCode, true);
 });
      assertSameState(uri, clone);
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should throw ValueAlreadyAssignedError when assigning a code to a uri assigned to a different uri by fixed[x]")
    void t59() {
      ElementDefinition uri = el(observation, "Observation.implicitRules");
      // Setup original assigned code
      uri.assignValue(fooBarCode, true);
      ElementDefinition clone = uri.clone(false);
      assertThrowsMatching("http:\\/\\/bar.com#foo.*bar", () -> { clone.assignValue(barFooCode, true);
 });
      assertSameState(uri, clone);
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should throw ValueAlreadyAssignedError when assigning a code to a uri assigned to a different uri by a parent by pattern[x]")
    void t60() {
      ElementDefinition rrLow = el(observation, "Observation.referenceRange.low");
      setAt(rrLow, "patternQuantity", obj("system", "http://foo.com#bar"));
      rrLow.unfold(fisher);
      ElementDefinition uri = el(observation, "Observation.referenceRange.low.system");
      ElementDefinition clone = uri.clone(false);
      assertThrowsMatching("http:\\/\\/bar.com#foo.*#bar", () -> { clone.assignValue(barFooCode);
 });
      assertThrowsMatching("http:\\/\\/bar.com#foo.*#bar", () -> { clone.assignValue(barFooCode, true);
 });
      assertSameState(uri, clone);
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should throw ValueAlreadyAssignedError when assigning a code to a uri assigned to a different uri by a parent by fixed[x]")
    void t61() {
      ElementDefinition rrLow = el(observation, "Observation.referenceRange.low");
      setAt(rrLow, "patternQuantity", obj("system", "http://foo.com#bar"));
      rrLow.unfold(fisher);
      ElementDefinition uri = el(observation, "Observation.referenceRange.low.system");
      ElementDefinition clone = uri.clone(false);
      assertThrowsMatching("http:\\/\\/bar.com#foo.*#bar", () -> { clone.assignValue(barFooCode, true);
 });
      assertSameState(uri, clone);
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should throw FixedToPatternError when trying to change fixed[x] to pattern[x]")
    void t62() {
      ElementDefinition uri = el(observation, "Observation.implicitRules");
      // Setup original assigned code
      uri.assignValue(fooBarCode, true);
      ElementDefinition clone = uri.clone(false);
      assertThrowsContaining("Cannot assign this element using a pattern; as it is already assigned in the StructureDefinition using fixedUri.", () -> { clone.assignValue(fooBarCode);
 });
      assertSameState(uri, clone);
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should throw CodedTypeNotFoundError when binding to an unsupported type")
    void t63() {
      ElementDefinition instant = el(observation, "Observation.issued");
      ElementDefinition clone = instant.clone(false);
      assertThrowsMatching("instant", () -> { clone.assignValue(fooBarCode);
 });
      assertThrowsMatching("instant", () -> { clone.assignValue(fooBarCode, true);
 });
      assertSameState(instant, clone);
    }

    @Test
    @DisplayName("ElementDefinition #assignFshCode() should throw NoSingleTypeError when element has multiple types")
    void t64() {
      ElementDefinition valueX = el(observation, "Observation.value[x]");
      assertThrowsContaining("Cannot assign Code value on this element since this element does not have a single type", () -> { valueX.assignValue(fooBarCode);
 });
      assertThrowsContaining("Cannot assign Code value on this element since this element does not have a single type", () -> { valueX.assignValue(fooBarCode, true);
 });
    }

    @Nested
    @DisplayName("R5 CodeableReference")
    class R5CodeableReference {
      static FHIRDefinitions r5Defs;
      static TestFisher r5Fisher;
      static StructureDefinition carePlan;
      @BeforeAll
      static void beforeAll65() {
        r5Defs = r5Defs();
        r5Fisher = new TestFisher().withFHIR(r5Defs);
      }
      @BeforeEach
      void beforeEach66() {
        carePlan = r5Fisher.fishForStructureDefinition("CarePlan");
        loggerSpy.reset();
      }

      @Test
      @DisplayName("ElementDefinition #assignFshCode() R5 CodeableReference should assign a code to a CodeableReference")
      void t67() {
        ElementDefinition addresses = el(carePlan, "CarePlan.addresses");
        addresses.assignValue(fooBarCode);
        assertJsEqual(obj("concept", obj("coding", list(obj("code", "bar", "system", "http://foo.com")))), at(addresses, "patternCodeableReference"));
        assertNull(at(addresses, "fixedCodeableReference"));
      }

      @Test
      @DisplayName("ElementDefinition #assignFshCode() R5 CodeableReference should assign a code to a CodeableReference (exactly)")
      void t68() {
        ElementDefinition addresses = el(carePlan, "CarePlan.addresses");
        addresses.assignValue(fooBarCode, true);
        assertJsEqual(obj("concept", obj("coding", list(obj("code", "bar", "system", "http://foo.com")))), at(addresses, "fixedCodeableReference"));
        assertNull(at(addresses, "patternCodeableReference"));
      }
    }
  }
}
