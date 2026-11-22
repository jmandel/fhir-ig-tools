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

// port of sushi/test/fhirtypes/ElementDefinition.assignValue.test.ts (SUSHI 3.21.1)
@DisplayName("ElementDefinition")
class ElementDefinitionAssignValueTest {
  static FHIRDefinitions defs;
  StructureDefinition medicationRequest;
  StructureDefinition medication;
  static TestFisher fisher;
  @BeforeAll
  static void beforeAll0() {
    defs = r4Defs();
    fisher = new TestFisher().withFHIR(defs);
  }
  @BeforeEach
  void beforeEach1() {
    medicationRequest = fisher.fishForStructureDefinition("MedicationRequest");
    medication = fisher.fishForStructureDefinition("Medication");
  }

  @Nested
  @DisplayName("#assignValue")
  class AssignValue {
    // NOTE: Most assignValue tests are in separate type-specific files.  We only test outliers here.

    @Test
    @DisplayName("ElementDefinition #assignValue should throw MismatchedTypeException when attempting to assign a value with an unsupported type")
    void t2() {
      ElementDefinition authoredOn = el(medicationRequest, "MedicationRequest.authoredOn");
      assertThrowsMatching("Cannot assign Date value.*Value does not match element type: dateTime", () -> { // @ts-ignore: Argument of type 'Date' is not assignable to parameter of type 'AssignmentValueType'
authoredOn.assignValue(new Date());
// Date is not a supported type -- only strings are allowed
 });
      assertThrowsMatching("Cannot assign Date value.*Value does not match element type: dateTime", () -> { // @ts-ignore: Argument of type 'Date' is not assignable to parameter of type 'AssignmentValueType'
authoredOn.assignValue(new Date(), true);
// Date is not a supported type -- only strings are allowed
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignValue should throw ValueAlreadyAssignedError when assigning a value assigned via parent pattern")
    void t3() {
      ElementDefinition medicationForm = el(medication, "Medication.form");
      medicationForm.assignValue(new FshCode("foo", "http://thankYouForSettingMe.com"));
      ElementDefinition medicationFormCodingSystem = medication.findElementByPath("form.coding.system", fisher);
      assertThrowsContaining("Cannot assign \"http://ohManIWillNeverGetSet.sad\" to this element; a different uri is already assigned: \"http://thankYouForSettingMe.com\".", () -> { medicationFormCodingSystem.assignValue("http://ohManIWillNeverGetSet.sad");
 });
      assertThrowsContaining("Cannot assign \"http://ohManIWillNeverGetSet.sad\" to this element; a different uri is already assigned: \"http://thankYouForSettingMe.com\".", () -> { medicationFormCodingSystem.assignValue("http://ohManIWillNeverGetSet.sad", true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignValue should ensure that minimum cardinality is 1 when assigning a value mentioned in a parent slice discriminator")
    void t4() {
      ElementDefinition cat = el(medicationRequest, "MedicationRequest.category");
      setAt(cat, "slicing", obj("discriminator", list(obj("type", "value", "path", "coding.code")), "rules", "open"));
      cat.addSlice("mouse");
      ElementDefinition catMouseCodingCode = medicationRequest.findElementByPath("category[mouse].coding.code", fisher);
      assertEquals(0.0, at(catMouseCodingCode, "min"));
      catMouseCodingCode.assignValue(new FshCode("cheese"));
      assertEquals("cheese", at(catMouseCodingCode, "patternCode"));
      assertEquals(1.0, at(catMouseCodingCode, "min"));
    }

    @Test
    @DisplayName("ElementDefinition #assignValue should ensure that minimum cardinality is 1 when assigning a value mentioned in the discriminator of a grandparent slice")
    void t5() {
      ElementDefinition cat = el(medicationRequest, "MedicationRequest.category");
      setAt(cat, "slicing", obj("discriminator", list(obj("type", "value", "path", "coding.code")), "rules", "open"));
      cat.addSlice("mouse");
      ElementDefinition catCoding = medicationRequest.findElementByPath("category.coding", fisher);
      setAt(catCoding, "slicing", obj("discriminator", list(obj("type", "value", "path", "coding")), "rules", "open"));
      catCoding.addSlice("rat");
      ElementDefinition catMouseCodingRatCode = medicationRequest.findElementByPath("category[mouse].coding[rat].code", fisher);
      assertEquals(0.0, at(catMouseCodingRatCode, "min"));
      catMouseCodingRatCode.assignValue(new FshCode("cheese"));
      assertEquals("cheese", at(catMouseCodingRatCode, "patternCode"));
      assertEquals(1.0, at(catMouseCodingRatCode, "min"));
    }

    @Test
    @DisplayName("ElementDefinition #assignValue should not ensure that minimum cardinality is 1 when assigning a value mentioned in a slice discriminator via $this")
    void t6() {
      ElementDefinition inUri = el(medicationRequest, "MedicationRequest.instantiatesUri");
      setAt(inUri, "slicing", obj("discriminator", list(obj("type", "value", "path", "$this")), "rules", "open"));
      inUri.addSlice("mouse");
      ElementDefinition inUriMouse = medicationRequest.findElementByPath("instantiatesUri[mouse]", fisher);
      assertEquals(0.0, at(inUriMouse, "min"));
      inUriMouse.assignValue("http://mice.cheese");
      assertEquals("http://mice.cheese", at(inUriMouse, "patternUri"));
      assertEquals(0.0, at(inUriMouse, "min"));
    }

    @Test
    @DisplayName("ElementDefinition #assignValue should not ensure that minimum cardinality is 1 when assigning a value not mentioned in a slice discriminator")
    void t7() {
      ElementDefinition cat = el(medicationRequest, "MedicationRequest.category");
      setAt(cat, "slicing", obj("discriminator", list(obj("type", "value", "path", "coding.code")), "rules", "open"));
      cat.addSlice("mouse");
      ElementDefinition catMouseCodingSystem = medicationRequest.findElementByPath("category[mouse].coding.system", fisher);
      assertEquals(0.0, at(catMouseCodingSystem, "min"));
      catMouseCodingSystem.assignValue("http://mice.cheese");
      assertEquals("http://mice.cheese", at(catMouseCodingSystem, "patternUri"));
      assertEquals(0.0, at(catMouseCodingSystem, "min"));
    }

    @Test
    @DisplayName("ElementDefinition #assignValue should not ensure that minimum cardinality is 1 when assigning a value mentioned in a non value/pattern discriminator")
    void t8() {
      ElementDefinition cat = el(medicationRequest, "MedicationRequest.category");
      setAt(cat, "slicing", obj("discriminator", list(obj("type", "exists", "path", "coding.code")), "rules", "open"));
      cat.addSlice("mouse");
      ElementDefinition catMouseCodingCode = medicationRequest.findElementByPath("category[mouse].coding.code", fisher);
      assertEquals(0.0, at(catMouseCodingCode, "min"));
      catMouseCodingCode.assignValue(new FshCode("cheese"));
      assertEquals("cheese", at(catMouseCodingCode, "patternCode"));
      assertEquals(0.0, at(catMouseCodingCode, "min"));
    }

    @Test
    @DisplayName("ElementDefinition #assignValue should not ensure that minimum cardinality is 1 when assigning a value with min card > 1 mentioned in a parent slice discriminator")
    void t9() {
      ElementDefinition cat = el(medicationRequest, "MedicationRequest.category");
      setAt(cat, "slicing", obj("discriminator", list(obj("type", "value", "path", "coding")), "rules", "open"));
      cat.addSlice("mouse");
      ElementDefinition catMouseCoding = medicationRequest.findElementByPath("category[mouse].coding", fisher);
      setAt(catMouseCoding, "min", 2.0);
      assertEquals(2.0, at(catMouseCoding, "min"));
      catMouseCoding.assignValue(new FshCode("cheese"));
      assertJsEqual(obj("code", "cheese"), at(catMouseCoding, "patternCoding"));
      assertEquals(2.0, at(catMouseCoding, "min"));
      // We do not try to decrease min to 1
    }

    @Test
    @DisplayName("ElementDefinition #assignValue should not ensure that minimum cardinality is 1 when assigning a value with no parent slice discriminator")
    void t10() {
      ElementDefinition cat = el(medicationRequest, "MedicationRequest.category");
      setAt(cat, "slicing", obj("rules", "open"));
      cat.addSlice("mouse");
      ElementDefinition catMouseCoding = medicationRequest.findElementByPath("category[mouse].coding", fisher);
      assertEquals(0.0, at(catMouseCoding, "min"));
      catMouseCoding.assignValue(new FshCode("cheese"));
      assertJsEqual(obj("code", "cheese"), at(catMouseCoding, "patternCoding"));
      assertEquals(0.0, at(catMouseCoding, "min"));
      // We do not increase min, since no discriminator
    }

    @Nested
    @DisplayName("R5 CodeableReference")
    class R5CodeableReference {
      FSHDocument doc;
      TestFisher r5Fisher;
      StructureDefinition carePlan;
      @BeforeEach
      void beforeEach11() {
        doc = new FSHDocument("Conditions.fsh");
        var input = new FSHTank(list(doc), minimalConfig());
        var r5Defs = r5Defs();
        var pkg = new Package(input.config);
        r5Fisher = new TestFisher(input, r5Defs, pkg);
        carePlan = r5Fisher.fishForStructureDefinition("CarePlan");
        // we need an Instance of Condition to reference
        var condition = new Instance("TestCondition");
        condition.instanceOf = "Condition";
        var assignedIdRule = new AssignmentRule("id");
        assignedIdRule.value = "condition-id";
        condition.rules.add(assignedIdRule);
        doc.instances.put(condition.name, condition);
      }

      @Test
      @DisplayName("ElementDefinition #assignValue R5 CodeableReference should assign a patternCodeableReference with a code on an element with an existing patternCodeableReference with a reference")
      void t12() {
        ElementDefinition addresses = el(carePlan, "CarePlan.addresses");
        addresses.assignValue(new FshReference("condition-id"));
        addresses.assignValue(new FshCode("bar", "http://foo.com"));
        assertJsEqual(obj("concept", obj("coding", list(obj("code", "bar", "system", "http://foo.com"))), "reference", obj("reference", "condition-id")), at(addresses, "patternCodeableReference"));
      }

      @Test
      @DisplayName("ElementDefinition #assignValue R5 CodeableReference should assign a patternCodeableReference with a Reference on an element with an existing patternCodeableReference with a concept")
      void t13() {
        ElementDefinition addresses = el(carePlan, "CarePlan.addresses");
        addresses.assignValue(new FshCode("bar", "http://foo.com"));
        addresses.assignValue(new FshReference("condition-id"));
        assertJsEqual(obj("concept", obj("coding", list(obj("code", "bar", "system", "http://foo.com"))), "reference", obj("reference", "condition-id")), at(addresses, "patternCodeableReference"));
      }

      @Test
      @DisplayName("ElementDefinition #assignValue R5 CodeableReference should assign a fixedCodeableReference with a code on an element with an existing fixedCodeableReference with a reference")
      void t14() {
        ElementDefinition addresses = el(carePlan, "CarePlan.addresses");
        addresses.assignValue(new FshReference("condition-id"), true);
        addresses.assignValue(new FshCode("bar", "http://foo.com"), true);
        assertJsEqual(obj("concept", obj("coding", list(obj("code", "bar", "system", "http://foo.com"))), "reference", obj("reference", "condition-id")), at(addresses, "fixedCodeableReference"));
      }

      @Test
      @DisplayName("ElementDefinition #assignValue R5 CodeableReference should assign a fixedCodeableReference with a Reference on an element with an existing fixedCodeableReference with a concept")
      void t15() {
        ElementDefinition addresses = el(carePlan, "CarePlan.addresses");
        addresses.assignValue(new FshCode("bar", "http://foo.com"), true);
        addresses.assignValue(new FshReference("condition-id"), true);
        assertJsEqual(obj("concept", obj("coding", list(obj("code", "bar", "system", "http://foo.com"))), "reference", obj("reference", "condition-id")), at(addresses, "fixedCodeableReference"));
      }
    }
  }

  @Nested
  @DisplayName("#assignedByDirectParent")
  class AssignedByDirectParent {

    @Test
    @DisplayName("ElementDefinition #assignedByDirectParent should find a fixed[x] value from the parent when it exists")
    void t16() {
      ElementDefinition statusReason = el(medicationRequest, "MedicationRequest.statusReason");
      statusReason.assignValue(new FshCode("foo"), true);
      ElementDefinition statusReasonCoding = medicationRequest.findElementByPath("statusReason.coding", fisher);
      var assignedValue = statusReasonCoding.assignedByDirectParent();
      assertJsEqual(list(obj("code", "foo")), assignedValue);
    }

    @Test
    @DisplayName("ElementDefinition #assignedByDirectParent should find a pattern[x] value from the parent when it exists")
    void t17() {
      ElementDefinition statusReason = el(medicationRequest, "MedicationRequest.statusReason");
      statusReason.assignValue(new FshCode("foo"));
      ElementDefinition statusReasonCoding = medicationRequest.findElementByPath("statusReason.coding", fisher);
      var patternValue = statusReasonCoding.assignedByDirectParent();
      assertJsEqual(list(obj("code", "foo")), patternValue);
    }

    @Test
    @DisplayName("ElementDefinition #assignedByDirectParent should not find a fixed[x] or pattern[x] value from the parent when none is present")
    void t18() {
      ElementDefinition statusReasonCoding = medicationRequest.findElementByPath("statusReason.coding", fisher);
      var patternValue = statusReasonCoding.assignedByDirectParent();
      assertNull(patternValue);
    }

    @Test
    @DisplayName("ElementDefinition #assignedByDirectParent should return undefined when being run on the root element")
    void t19() {
      ElementDefinition root = el(medicationRequest, "MedicationRequest");
      var patternValue = root.assignedByDirectParent();
      assertNull(patternValue);
    }
  }

  @Nested
  @DisplayName("#assignedByAnyParent")
  class AssignedByAnyParent {

    @Test
    @DisplayName("ElementDefinition #assignedByAnyParent should find a fixed[x] value from a direct parent when it exists")
    void t20() {
      ElementDefinition statusReason = el(medicationRequest, "MedicationRequest.statusReason");
      statusReason.assignValue(new FshCode("foo"), true);
      ElementDefinition statusReasonCoding = medicationRequest.findElementByPath("statusReason.coding", fisher);
      var assignedValue = statusReasonCoding.assignedByAnyParent();
      assertJsEqual(list(obj("code", "foo")), assignedValue);
    }

    @Test
    @DisplayName("ElementDefinition #assignedByAnyParent should find a pattern[x] value from a direct parent when it exists")
    void t21() {
      ElementDefinition statusReason = el(medicationRequest, "MedicationRequest.statusReason");
      statusReason.assignValue(new FshCode("foo"));
      ElementDefinition statusReasonCoding = medicationRequest.findElementByPath("statusReason.coding", fisher);
      var patternValue = statusReasonCoding.assignedByAnyParent();
      assertJsEqual(list(obj("code", "foo")), patternValue);
    }

    @Test
    @DisplayName("ElementDefinition #assignedByAnyParent should find a fixed[x] value from a grandparent when it exists")
    void t22() {
      ElementDefinition identifier = el(medicationRequest, "MedicationRequest.identifier");
      setAt(identifier, "max", "1");
      // @ts-ignore
      setAt(identifier, "fixedIdentifier", obj("period", obj("start", "2011-11-11")));
      ElementDefinition identifierPeriodStart = medicationRequest.findElementByPath("identifier.period.start", fisher);
      var assignedValue = identifierPeriodStart.assignedByAnyParent();
      assertEquals("2011-11-11", assignedValue);
    }

    @Test
    @DisplayName("ElementDefinition #assignedByAnyParent should find a pattern value from a grandparent when it exists")
    void t23() {
      ElementDefinition identifier = el(medicationRequest, "MedicationRequest.identifier");
      setAt(identifier, "max", "1");
      // @ts-ignore
      setAt(identifier, "patternIdentifier", obj("period", obj("start", "2011-11-11")));
      ElementDefinition identifierPeriodStart = medicationRequest.findElementByPath("identifier.period.start", fisher);
      var patternValue = identifierPeriodStart.assignedByAnyParent();
      assertEquals("2011-11-11", patternValue);
    }

    @Test
    @DisplayName("ElementDefinition #assignedByAnyParent should find an array fixed[x] value from a grandparent when it exists")
    void t24() {
      ElementDefinition statusReason = el(medicationRequest, "MedicationRequest.statusReason");
      statusReason.assignValue(new FshCode("foo", "http://bar.com"), true);
      ElementDefinition statusReasonCodingSystem = medicationRequest.findElementByPath("statusReason.coding.system", fisher);
      // Single element in array
      var assignedValue = statusReasonCodingSystem.assignedByAnyParent();
      assertEquals("http://bar.com", assignedValue);
      // Multiple not matching array elements
      setAt(statusReason, "fixedCodeableConcept", obj("coding", list(obj("system", "http://foo.com"), obj("system", "http://bar.com"))));
      assignedValue = statusReasonCodingSystem.assignedByAnyParent();
      assertJsEqual(list("http://foo.com", "http://bar.com"), assignedValue);
      // Multiple matching array elements
      setAt(statusReason, "fixedCodeableConcept", obj("coding", list(obj("system", "http://foo.com"), obj("system", "http://foo.com"))));
      assignedValue = statusReasonCodingSystem.assignedByAnyParent();
      assertEquals("http://foo.com", assignedValue);
    }

    @Test
    @DisplayName("ElementDefinition #assignedByAnyParent should find an array pattern value from a grandparent when it exists")
    void t25() {
      ElementDefinition statusReason = el(medicationRequest, "MedicationRequest.statusReason");
      statusReason.assignValue(new FshCode("foo", "http://bar.com"));
      ElementDefinition statusReasonCodingSystem = medicationRequest.findElementByPath("statusReason.coding.system", fisher);
      // Single element in array
      var patternValue = statusReasonCodingSystem.assignedByAnyParent();
      assertEquals("http://bar.com", patternValue);
      // Multiple not matching array elements
      setAt(statusReason, "patternCodeableConcept", obj("coding", list(obj("system", "http://foo.com"), obj("system", "http://bar.com"))));
      patternValue = statusReasonCodingSystem.assignedByAnyParent();
      assertJsEqual(list("http://foo.com", "http://bar.com"), patternValue);
      // Multiple matching array elements
      setAt(statusReason, "patternCodeableConcept", obj("coding", list(obj("system", "http://foo.com"), obj("system", "http://foo.com"))));
      patternValue = statusReasonCodingSystem.assignedByAnyParent();
      assertEquals("http://foo.com", patternValue);
    }

    @Test
    @DisplayName("ElementDefinition #assignedByAnyParent should not find a fixed[x] or pattern[x] value from the parent when none is present")
    void t26() {
      ElementDefinition statusReasonCoding = medicationRequest.findElementByPath("statusReason.coding.version", fisher);
      var value = statusReasonCoding.assignedByAnyParent();
      assertNull(value);
    }

    @Test
    @DisplayName("ElementDefinition #assignedByAnyParent should return undefined when being run on the root element")
    void t27() {
      ElementDefinition root = el(medicationRequest, "MedicationRequest");
      var patternValue = root.assignedByAnyParent();
      assertNull(patternValue);
    }
  }
}
