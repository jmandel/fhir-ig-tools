package org.hl7.fhir.sushi.fhirtypes;

import static org.hl7.fhir.sushi.fhirtypes.EdTestSupport.*;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.hl7.fhir.sushi.errors.*;
import org.hl7.fhir.sushi.fhirdefs.FHIRDefinitions;
import org.hl7.fhir.sushi.fshtypes.*;
import org.hl7.fhir.sushi.fshtypes.rules.*;
import org.hl7.fhir.common.js.Js;
import org.hl7.fhir.common.js.JsObject;
import org.hl7.fhir.sushi.testhelpers.TestFisher;
import org.hl7.fhir.sushi.utils.FishableType;
import org.junit.jupiter.api.*;

// port of sushi/test/fhirtypes/ElementDefinition.assignInstanceDefinition.test.ts (SUSHI 3.21.1)
@DisplayName("ElementDefinition")
class ElementDefinitionAssignInstanceDefinitionTest {
  static FHIRDefinitions defs;
  StructureDefinition patient;
  StructureDefinition observation;
  static TestFisher fisher;
  @BeforeAll
  static void beforeAll0() {
    defs = r4Defs();
    fisher = new TestFisher().withFHIR(defs);
  }
  @BeforeEach
  void beforeEach1() {
    patient = fisher.fishForStructureDefinition("Patient");
    observation = fisher.fishForStructureDefinition("Observation");
  }

  @Nested
  @DisplayName("#assignInstance")
  class AssignInstance {

    @Test
    @DisplayName("ElementDefinition #assignInstance should assign an allowed type of an instance")
    void t2() {
      var addressInstance = new InstanceDefinition();
      addressInstance._instanceMeta.name = "USPostalAddress";
      addressInstance._instanceMeta.sdType = "Address";
      addressInstance._instanceMeta.usage = "Inline";
      setAt(addressInstance, "country", "US");
      setAt(addressInstance, "type", "postal");
      ElementDefinition address = el(patient, "Patient.address");
      address.assignValue(addressInstance);
      assertJsEqual(obj("country", "US", "type", "postal"), at(address, "patternAddress"));
      assertNull(at(address, "fixedAddress"));
    }

    @Test
    @DisplayName("ElementDefinition #assignInstance should assign an allowed type of an instance (exactly)")
    void t3() {
      var addressInstance = new InstanceDefinition();
      addressInstance._instanceMeta.name = "USPostalAddress";
      addressInstance._instanceMeta.sdType = "Address";
      addressInstance._instanceMeta.usage = "Inline";
      setAt(addressInstance, "country", "US");
      setAt(addressInstance, "type", "postal");
      ElementDefinition address = el(patient, "Patient.address");
      address.assignValue(addressInstance, true);
      assertJsEqual(obj("country", "US", "type", "postal"), at(address, "fixedAddress"));
      assertNull(at(address, "patternAddress"));
    }

    @Test
    @DisplayName("ElementDefinition #assignInstance should throw NoSingleTypeError when element has multiple types")
    void t4() {
      var periodInstance = new InstanceDefinition();
      periodInstance._instanceMeta.name = "LastYear";
      periodInstance._instanceMeta.sdType = "Period";
      periodInstance._instanceMeta.usage = "Inline";
      setAt(periodInstance, "start", "2019-08-01");
      setAt(periodInstance, "end", "2020-08-01");
      ElementDefinition valueX = el(observation, "Observation.value[x]");
      assertThrowsContaining("Cannot assign InstanceDefinition value on this element since this element does not have a single type", () -> { valueX.assignValue(periodInstance);
 });
      assertThrowsContaining("Cannot assign InstanceDefinition value on this element since this element does not have a single type", () -> { valueX.assignValue(periodInstance, true);
 });
      assertNull(at(valueX, "patternPeriod"));
      assertNull(at(valueX, "fixedPeriod"));
    }

    @Test
    @DisplayName("ElementDefinition #assignInstance should throw ValueAlreadyAssignedError when the value is assigned to a different value by pattern[x]")
    void t5() {
      var workAddress = new InstanceDefinition();
      workAddress._instanceMeta.name = "USPostalAddress";
      workAddress._instanceMeta.sdType = "Address";
      workAddress._instanceMeta.usage = "Inline";
      setAt(workAddress, "country", "US");
      setAt(workAddress, "type", "postal");
      setAt(workAddress, "use", "work");
      var homeAddress = new InstanceDefinition();
      homeAddress._instanceMeta.name = "USPostalAddress";
      homeAddress._instanceMeta.sdType = "Address";
      homeAddress._instanceMeta.usage = "Inline";
      setAt(homeAddress, "country", "US");
      setAt(homeAddress, "type", "postal");
      setAt(homeAddress, "use", "home");
      ElementDefinition address = el(patient, "Patient.address");
      address.assignValue(workAddress);
      assertJsEqual(obj("country", "US", "type", "postal", "use", "work"), at(address, "patternAddress"));
      address.assignValue(workAddress);
      assertJsEqual(obj("country", "US", "type", "postal", "use", "work"), at(address, "patternAddress"));
      assertThrowsContaining("Cannot assign {\"country\":\"US\",\"type\":\"postal\",\"use\":\"home\"} to this element; a different Address is already assigned: {\"country\":\"US\",\"type\":\"postal\",\"use\":\"work\"}", () -> { address.assignValue(homeAddress);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignInstance should throw ValueAlreadyAssignedError when the value is assigned to a different value by fixed[x]")
    void t6() {
      var workAddress = new InstanceDefinition();
      workAddress._instanceMeta.name = "USPostalAddress";
      workAddress._instanceMeta.sdType = "Address";
      workAddress._instanceMeta.usage = "Inline";
      setAt(workAddress, "country", "US");
      setAt(workAddress, "type", "postal");
      setAt(workAddress, "use", "work");
      var homeAddress = new InstanceDefinition();
      homeAddress._instanceMeta.name = "USPostalAddress";
      homeAddress._instanceMeta.sdType = "Address";
      homeAddress._instanceMeta.usage = "Inline";
      setAt(homeAddress, "country", "US");
      setAt(homeAddress, "type", "postal");
      setAt(homeAddress, "use", "home");
      ElementDefinition address = el(patient, "Patient.address");
      address.assignValue(workAddress, true);
      assertJsEqual(obj("country", "US", "type", "postal", "use", "work"), at(address, "fixedAddress"));
      address.assignValue(workAddress, true);
      assertJsEqual(obj("country", "US", "type", "postal", "use", "work"), at(address, "fixedAddress"));
      assertThrowsContaining("Cannot assign {\"country\":\"US\",\"type\":\"postal\",\"use\":\"home\"} to this element; a different Address is already assigned: {\"country\":\"US\",\"type\":\"postal\",\"use\":\"work\"}", () -> { address.assignValue(homeAddress, true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignInstance should throw AssignedToPatternError when trying to change fixed[x] to pattern[x]")
    void t7() {
      var addressInstance = new InstanceDefinition();
      addressInstance._instanceMeta.name = "USPostalAddress";
      addressInstance._instanceMeta.sdType = "Address";
      addressInstance._instanceMeta.usage = "Inline";
      setAt(addressInstance, "country", "US");
      setAt(addressInstance, "type", "postal");
      ElementDefinition address = el(patient, "Patient.address");
      address.assignValue(addressInstance, true);
      assertJsEqual(obj("country", "US", "type", "postal"), at(address, "fixedAddress"));
      assertThrowsContaining("Cannot assign this element using a pattern; as it is already assigned in the StructureDefinition using fixedAddress", () -> { address.assignValue(addressInstance);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignInstance should throw MismatchedTypeError when the value is assigned to an unsupported type")
    void t8() {
      var periodInstance = new InstanceDefinition();
      periodInstance._instanceMeta.name = "LastYear";
      periodInstance._instanceMeta.sdType = "Period";
      periodInstance._instanceMeta.usage = "Inline";
      setAt(periodInstance, "start", "2019-08-01");
      setAt(periodInstance, "end", "2020-08-01");
      ElementDefinition address = el(patient, "Patient.address");
      assertThrowsContaining("Cannot assign Period value: {\"start\":\"2019-08-01\",\"end\":\"2020-08-01\"}. Value does not match element type: Address", () -> { address.assignValue(periodInstance);
 });
    }
  }
}
