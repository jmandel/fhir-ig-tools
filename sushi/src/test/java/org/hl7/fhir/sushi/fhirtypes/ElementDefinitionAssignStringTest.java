package org.hl7.fhir.sushi.fhirtypes;

import static org.hl7.fhir.sushi.fhirtypes.EdTestSupport.*;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.hl7.fhir.sushi.errors.*;
import org.hl7.fhir.sushi.fhirdefs.FHIRDefinitions;
import org.hl7.fhir.sushi.fshtypes.*;
import org.hl7.fhir.sushi.fshtypes.rules.*;
import org.hl7.fhir.sushi.js.Js;
import org.hl7.fhir.sushi.js.JsObject;
import org.hl7.fhir.sushi.testhelpers.TestFisher;
import org.hl7.fhir.sushi.utils.FishableType;
import org.junit.jupiter.api.*;

// port of sushi/test/fhirtypes/ElementDefinition.assignString.test.ts (SUSHI 3.21.1)
@DisplayName("ElementDefinition")
class ElementDefinitionAssignStringTest {
  static FHIRDefinitions defs;
  StructureDefinition observation;
  StructureDefinition medication;
  StructureDefinition patient;
  StructureDefinition riskEvidenceSynthesis;
  StructureDefinition location;
  StructureDefinition capabilityStatement;
  StructureDefinition imagingStudy;
  StructureDefinition device;
  StructureDefinition task;
  StructureDefinition binary;
  static TestFisher fisher;
  @BeforeAll
  static void beforeAll0() {
    defs = r4Defs();
    fisher = new TestFisher().withFHIR(defs);
  }
  @BeforeEach
  void beforeEach1() {
    observation = fisher.fishForStructureDefinition("Observation");
    medication = fisher.fishForStructureDefinition("Medication");
    patient = fisher.fishForStructureDefinition("Patient");
    riskEvidenceSynthesis = fisher.fishForStructureDefinition("RiskEvidenceSynthesis");
    location = fisher.fishForStructureDefinition("Location");
    capabilityStatement = fisher.fishForStructureDefinition("CapabilityStatement");
    imagingStudy = fisher.fishForStructureDefinition("ImagingStudy");
    device = fisher.fishForStructureDefinition("Device");
    task = fisher.fishForStructureDefinition("Task");
    binary = fisher.fishForStructureDefinition("Binary");
  }

  @Nested
  @DisplayName("#assignString")
  class AssignString {
    // Assigning a string

    @Test
    @DisplayName("ElementDefinition #assignString should assign a string to a string")
    void t2() {
      ElementDefinition batchLotNumber = el(medication, "Medication.batch.lotNumber");
      batchLotNumber.assignValue("foo bar");
      assertEquals("foo bar", at(batchLotNumber, "patternString"));
      assertNull(at(batchLotNumber, "fixedString"));
    }

    @Test
    @DisplayName("ElementDefinition #assignString should assign a string to a string (exactly)")
    void t3() {
      ElementDefinition batchLotNumber = el(medication, "Medication.batch.lotNumber");
      batchLotNumber.assignValue("foo bar", true);
      assertEquals("foo bar", at(batchLotNumber, "fixedString"));
      assertNull(at(batchLotNumber, "patternString"));
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw ValueAlreadyAssignedError when assigning an already assigned string by pattern[x]")
    void t4() {
      ElementDefinition batchLotNumber = el(medication, "Medication.batch.lotNumber");
      batchLotNumber.assignValue("foo bar");
      assertEquals("foo bar", at(batchLotNumber, "patternString"));
      assertThrowsContaining("Cannot assign \"bar foo\" to this element; a different string is already assigned: \"foo bar\".", () -> { batchLotNumber.assignValue("bar foo");
 });
      assertThrowsContaining("Cannot assign \"bar foo\" to this element; a different string is already assigned: \"foo bar\".", () -> { batchLotNumber.assignValue("bar foo", true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw ValueAlreadyAssignedError when assigning an already assigned string by fixed[x]")
    void t5() {
      ElementDefinition batchLotNumber = el(medication, "Medication.batch.lotNumber");
      batchLotNumber.assignValue("foo bar", true);
      assertEquals("foo bar", at(batchLotNumber, "fixedString"));
      assertThrowsContaining("Cannot assign \"bar foo\" to this element; a different string is already assigned: \"foo bar\".", () -> { batchLotNumber.assignValue("bar foo", true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw ValueAlreadyAssignedError when assigning a decimal to a different value set in a parent by pattern[x]")
    void t6() {
      ElementDefinition identifier = el(observation, "Observation.identifier");
      // @ts-ignore
      setAt(identifier, "patternIdentifier", obj("value", "Foo"));
      identifier.unfold(fisher);
      ElementDefinition identifierValue = el(observation, "Observation.identifier.value");
      ElementDefinition clone = identifierValue.clone(false);
      assertThrowsContaining("Cannot assign \"Bar\" to this element; a different string is already assigned: \"Foo\".", () -> { identifierValue.assignValue("Bar");
 });
      assertThrowsContaining("Cannot assign \"Bar\" to this element; a different string is already assigned: \"Foo\".", () -> { identifierValue.assignValue("Bar", true);
 });
      assertSameState(identifierValue, clone);
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw ValueAlreadyAssignedError when assigning a decimal to a different value set in a parent by fixed[x]")
    void t7() {
      ElementDefinition identifier = el(observation, "Observation.identifier");
      // @ts-ignore
      setAt(identifier, "fixedIdentifier", obj("value", "Foo"));
      identifier.unfold(fisher);
      ElementDefinition identifierValue = el(observation, "Observation.identifier.value");
      ElementDefinition clone = identifierValue.clone(false);
      assertThrowsContaining("Cannot assign \"Bar\" to this element; a different string is already assigned: \"Foo\".", () -> { identifierValue.assignValue("Bar");
 });
      assertThrowsContaining("Cannot assign \"Bar\" to this element; a different string is already assigned: \"Foo\".", () -> { identifierValue.assignValue("Bar", true);
 });
      assertSameState(identifierValue, clone);
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw FixedToPatternError when trying to change fixed[x] to pattern[x]")
    void t8() {
      ElementDefinition batchLotNumber = el(medication, "Medication.batch.lotNumber");
      batchLotNumber.assignValue("foo bar", true);
      assertEquals("foo bar", at(batchLotNumber, "fixedString"));
      assertThrowsContaining("Cannot assign this element using a pattern; as it is already assigned in the StructureDefinition using fixedString.", () -> { batchLotNumber.assignValue("foo bar");
 });
    }
    // Assigning a URI

    @Test
    @DisplayName("ElementDefinition #assignString should assign a string to a uri")
    void t9() {
      ElementDefinition url = el(riskEvidenceSynthesis, "RiskEvidenceSynthesis.url");
      url.assignValue("http://example.org");
      assertEquals("http://example.org", at(url, "patternUri"));
      assertNull(at(url, "fixedUri"));
    }

    @Test
    @DisplayName("ElementDefinition #assignString should assign a string to a uri (exactly)")
    void t10() {
      ElementDefinition url = el(riskEvidenceSynthesis, "RiskEvidenceSynthesis.url");
      url.assignValue("http://example.org", true);
      assertEquals("http://example.org", at(url, "fixedUri"));
      assertNull(at(url, "patternUri"));
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw ValueAlreadyAssignedError when assigning an already assigned uri by pattern[x]")
    void t11() {
      ElementDefinition url = el(riskEvidenceSynthesis, "RiskEvidenceSynthesis.url");
      url.assignValue("http://example.org");
      assertEquals("http://example.org", at(url, "patternUri"));
      assertThrowsContaining("Cannot assign \"http://newexample.com\" to this element; a different uri is already assigned: \"http://example.org\".", () -> { url.assignValue("http://newexample.com");
 });
      assertThrowsContaining("Cannot assign \"http://newexample.com\" to this element; a different uri is already assigned: \"http://example.org\".", () -> { url.assignValue("http://newexample.com", true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw ValueAlreadyAssignedError when assigning an already assigned uri by fixed[x]")
    void t12() {
      ElementDefinition url = el(riskEvidenceSynthesis, "RiskEvidenceSynthesis.url");
      url.assignValue("http://example.org", true);
      assertEquals("http://example.org", at(url, "fixedUri"));
      assertThrowsContaining("Cannot assign \"http://newexample.com\" to this element; a different uri is already assigned: \"http://example.org\".", () -> { url.assignValue("http://newexample.com", true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw FixedToPatternError when trying to change fixed[x] to pattern[x]")
    void t13() {
      ElementDefinition url = el(riskEvidenceSynthesis, "RiskEvidenceSynthesis.url");
      url.assignValue("http://example.org", true);
      assertEquals("http://example.org", at(url, "fixedUri"));
      assertThrowsContaining("Cannot assign this element using a pattern; as it is already assigned in the StructureDefinition using fixedUri.", () -> { url.assignValue("http://example.com");
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw MismatchedTypeError when assigning a uri to an incorrect value")
    void t14() {
      ElementDefinition url = el(riskEvidenceSynthesis, "RiskEvidenceSynthesis.url");
      assertThrowsContaining("Cannot assign string value:  . Value does not match element type: uri", () -> { url.assignValue(" ");
 });
      assertThrowsContaining("Cannot assign string value:  . Value does not match element type: uri", () -> { url.assignValue(" ", true);
 });
    }
    // Assigning a URL

    @Test
    @DisplayName("ElementDefinition #assignString should assign a string to a url")
    void t15() {
      ElementDefinition url = el(capabilityStatement, "CapabilityStatement.implementation.url");
      url.assignValue("http://example.org");
      assertEquals("http://example.org", at(url, "patternUrl"));
      assertNull(at(url, "fixedUrl"));
    }

    @Test
    @DisplayName("ElementDefinition #assignString should assign a string to a url (exactly)")
    void t16() {
      ElementDefinition url = el(capabilityStatement, "CapabilityStatement.implementation.url");
      url.assignValue("http://example.org", true);
      assertEquals("http://example.org", at(url, "fixedUrl"));
      assertNull(at(url, "patternUrl"));
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw ValueAlreadyAssignedError when assigning an already assigned URL by pattern[x]")
    void t17() {
      ElementDefinition url = el(capabilityStatement, "CapabilityStatement.implementation.url");
      url.assignValue("http://example.org");
      assertEquals("http://example.org", at(url, "patternUrl"));
      assertThrowsContaining("Cannot assign \"http://newexample.com\" to this element; a different url is already assigned: \"http://example.org\".", () -> { url.assignValue("http://newexample.com");
 });
      assertThrowsContaining("Cannot assign \"http://newexample.com\" to this element; a different url is already assigned: \"http://example.org\".", () -> { url.assignValue("http://newexample.com", true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw ValueAlreadyAssignedError when assigning an already assigned URL by fixed[x]")
    void t18() {
      ElementDefinition url = el(capabilityStatement, "CapabilityStatement.implementation.url");
      url.assignValue("http://example.org", true);
      assertEquals("http://example.org", at(url, "fixedUrl"));
      assertThrowsContaining("Cannot assign \"http://newexample.com\" to this element; a different url is already assigned: \"http://example.org\".", () -> { url.assignValue("http://newexample.com", true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw FixedToPatternError when trying to change fixed[x] to pattern[x]")
    void t19() {
      ElementDefinition url = el(capabilityStatement, "CapabilityStatement.implementation.url");
      url.assignValue("http://example.org", true);
      assertEquals("http://example.org", at(url, "fixedUrl"));
      assertThrowsContaining("Cannot assign this element using a pattern; as it is already assigned in the StructureDefinition using fixedUrl.", () -> { url.assignValue("http://newexample.com");
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw MismatchedTypeError when assigning a url to an incorrect value")
    void t20() {
      ElementDefinition url = el(capabilityStatement, "CapabilityStatement.implementation.url");
      assertThrowsContaining("Cannot assign string value:  . Value does not match element type: url", () -> { url.assignValue(" ");
 });
      assertThrowsContaining("Cannot assign string value:  . Value does not match element type: url", () -> { url.assignValue(" ", true);
 });
    }
    // Assigning a canonical

    @Test
    @DisplayName("ElementDefinition #assignString should assign a string to a canonical")
    void t21() {
      ElementDefinition instantiates = el(capabilityStatement, "CapabilityStatement.instantiates");
      instantiates.assignValue("http://example.org");
      assertEquals("http://example.org", at(instantiates, "patternCanonical"));
      assertNull(at(instantiates, "fixedCanonical"));
    }

    @Test
    @DisplayName("ElementDefinition #assignString should assign a string to a canonical (exactly)")
    void t22() {
      ElementDefinition instantiates = el(capabilityStatement, "CapabilityStatement.instantiates");
      instantiates.assignValue("http://example.org", true);
      assertEquals("http://example.org", at(instantiates, "fixedCanonical"));
      assertNull(at(instantiates, "patternCanonical"));
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw ValueAlreadyAssignedError when assigning an already assigned canonical by pattern[x]")
    void t23() {
      ElementDefinition instantiates = el(capabilityStatement, "CapabilityStatement.instantiates");
      instantiates.assignValue("http://example.org");
      assertEquals("http://example.org", at(instantiates, "patternCanonical"));
      assertThrowsContaining("Cannot assign \"http://newexample.com\" to this element; a different canonical is already assigned: \"http://example.org\".", () -> { instantiates.assignValue("http://newexample.com");
 });
      assertThrowsContaining("Cannot assign \"http://newexample.com\" to this element; a different canonical is already assigned: \"http://example.org\".", () -> { instantiates.assignValue("http://newexample.com", true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw ValueAlreadyAssignedError when assigning an already assigned canonical by fixed[x]")
    void t24() {
      ElementDefinition instantiates = el(capabilityStatement, "CapabilityStatement.instantiates");
      instantiates.assignValue("http://example.org", true);
      assertEquals("http://example.org", at(instantiates, "fixedCanonical"));
      assertThrowsContaining("Cannot assign \"http://newexample.com\" to this element; a different canonical is already assigned: \"http://example.org\".", () -> { instantiates.assignValue("http://newexample.com", true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw FixedToPatternError when trying to change fixed[x] to pattern[x]")
    void t25() {
      ElementDefinition instantiates = el(capabilityStatement, "CapabilityStatement.instantiates");
      instantiates.assignValue("http://example.org", true);
      assertEquals("http://example.org", at(instantiates, "fixedCanonical"));
      assertThrowsContaining("Cannot assign this element using a pattern; as it is already assigned in the StructureDefinition using fixedCanonical.", () -> { instantiates.assignValue("http://example.com");
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw MismatchedTypeError when assigning a canonical to an incorrect value")
    void t26() {
      ElementDefinition instantiates = el(capabilityStatement, "CapabilityStatement.instantiates");
      assertThrowsContaining("Cannot assign string value:  . Value does not match element type: canonical", () -> { instantiates.assignValue(" ");
 });
      assertThrowsContaining("Cannot assign string value:  . Value does not match element type: canonical", () -> { instantiates.assignValue(" ", true);
 });
    }
    // Assigning a base64Binary

    @Test
    @DisplayName("ElementDefinition #assignString should assign a string to a base64Binary")
    void t27() {
      ElementDefinition udiCarrierCarrierAIDC = el(device, "Device.udiCarrier.carrierAIDC");
      udiCarrierCarrierAIDC.assignValue("QXJlIHdlIHRoZSBzdWJqZWN0cz8/P+w=");
      assertEquals("QXJlIHdlIHRoZSBzdWJqZWN0cz8/P+w=", at(udiCarrierCarrierAIDC, "patternBase64Binary"));
      assertNull(at(udiCarrierCarrierAIDC, "fixedBase64Binary"));
    }

    @Test
    @DisplayName("ElementDefinition #assignString should assign a string to a base64Binary (exactly)")
    void t28() {
      ElementDefinition udiCarrierCarrierAIDC = el(device, "Device.udiCarrier.carrierAIDC");
      udiCarrierCarrierAIDC.assignValue("QXJlIHdlIHRoZSBzdWJqZWN0cz8/P+w=", true);
      assertEquals("QXJlIHdlIHRoZSBzdWJqZWN0cz8/P+w=", at(udiCarrierCarrierAIDC, "fixedBase64Binary"));
      assertNull(at(udiCarrierCarrierAIDC, "patternBase64Binary"));
    }

    @Test
    @DisplayName("ElementDefinition #assignString should assign a string utilizing binary adjunct to a base64Binary")
    void t29() {
      ElementDefinition dataElement = el(binary, "Binary.data");
      dataElement.assignValue("ig-loader-ExampleFile.txt");
      assertEquals("ig-loader-ExampleFile.txt", at(dataElement, "patternBase64Binary"));
      assertNull(at(dataElement, "fixedBase64Binary"));
    }

    @Test
    @DisplayName("ElementDefinition #assignString should assign a string utilizing binary adjunct to a base64Binary (exactly)")
    void t30() {
      ElementDefinition dataElement = el(binary, "Binary.data");
      dataElement.assignValue("ig-loader-ExampleFile.txt", true);
      assertEquals("ig-loader-ExampleFile.txt", at(dataElement, "fixedBase64Binary"));
      assertNull(at(dataElement, "patternBase64Binary"));
    }

    @Test
    @DisplayName("ElementDefinition #assignString should assign a very long string to a base64Binary")
    void t31() {
      ElementDefinition dataElement = el(binary, "Binary.data");
      var longValue = readBase64(java.nio.file.Path.of(org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.tsDir(),
          "test", "fhirtypes", "fixtures", "imgdata.txt"));
      dataElement.assignValue(longValue);
      assertEquals(longValue, at(dataElement, "patternBase64Binary"));
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw ValueAlreadyAssignedError when assigning an already assigned base64Binary by pattern[x]")
    void t32() {
      ElementDefinition udiCarrierCarrierAIDC = el(device, "Device.udiCarrier.carrierAIDC");
      udiCarrierCarrierAIDC.assignValue("QXJlIHdlIHRoZSBzdWJqZWN0cz8/P+w=");
      assertEquals("QXJlIHdlIHRoZSBzdWJqZWN0cz8/P+w=", at(udiCarrierCarrierAIDC, "patternBase64Binary"));
      assertThrowsContaining("Cannot assign \"dGhpcyB0b28=\" to this element; a different base64Binary is already assigned: \"QXJlIHdlIHRoZSBzdWJqZWN0cz8/P+w=\".", () -> { udiCarrierCarrierAIDC.assignValue("dGhpcyB0b28=");
 });
      assertThrowsContaining("Cannot assign \"dGhpcyB0b28=\" to this element; a different base64Binary is already assigned: \"QXJlIHdlIHRoZSBzdWJqZWN0cz8/P+w=\".", () -> { udiCarrierCarrierAIDC.assignValue("dGhpcyB0b28=", true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw ValueAlreadyAssignedError when assigning an already assigned base64Binary by fixed[x]")
    void t33() {
      ElementDefinition udiCarrierCarrierAIDC = el(device, "Device.udiCarrier.carrierAIDC");
      udiCarrierCarrierAIDC.assignValue("QXJlIHdlIHRoZSBzdWJqZWN0cz8/P+w=", true);
      assertEquals("QXJlIHdlIHRoZSBzdWJqZWN0cz8/P+w=", at(udiCarrierCarrierAIDC, "fixedBase64Binary"));
      assertThrowsContaining("Cannot assign \"dGhpcyB0b28=\" to this element; a different base64Binary is already assigned: \"QXJlIHdlIHRoZSBzdWJqZWN0cz8/P+w=\".", () -> { udiCarrierCarrierAIDC.assignValue("dGhpcyB0b28=", true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw FixedToPatternError when trying to change fixed[x] to pattern[x]")
    void t34() {
      ElementDefinition udiCarrierCarrierAIDC = el(device, "Device.udiCarrier.carrierAIDC");
      udiCarrierCarrierAIDC.assignValue("QXJlIHdlIHRoZSBzdWJqZWN0cz8/P+w=", true);
      assertEquals("QXJlIHdlIHRoZSBzdWJqZWN0cz8/P+w=", at(udiCarrierCarrierAIDC, "fixedBase64Binary"));
      assertThrowsContaining("Cannot assign this element using a pattern; as it is already assigned in the StructureDefinition using fixedBase64Binary.", () -> { udiCarrierCarrierAIDC.assignValue("QXJlIHdlIHRoZSBzdWJqZWN0cz8/P+w=");
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw MismatchedTypeError when assigning a base64Binary to an incorrect value")
    void t35() {
      ElementDefinition udiCarrierCarrierAIDC = el(device, "Device.udiCarrier.carrierAIDC");
      assertThrowsContaining("Cannot assign string value: Not valid. Value does not match element type: base64Binary", () -> { udiCarrierCarrierAIDC.assignValue("Not valid");
 });
      assertThrowsContaining("Cannot assign string value: Not valid. Value does not match element type: base64Binary", () -> { udiCarrierCarrierAIDC.assignValue("Not valid", true);
 });
    }
    // Assigning an instant

    @Test
    @DisplayName("ElementDefinition #assignString should assign a string to an instant")
    void t36() {
      ElementDefinition issued = el(observation, "Observation.issued");
      issued.assignValue("2015-02-07T13:28:17.239+02:00");
      assertEquals("2015-02-07T13:28:17.239+02:00", at(issued, "patternInstant"));
      assertNull(at(issued, "fixedInstant"));
    }

    @Test
    @DisplayName("ElementDefinition #assignString should assign a string to an instant (exactly)")
    void t37() {
      ElementDefinition issued = el(observation, "Observation.issued");
      issued.assignValue("2015-02-07T13:28:17.239+02:00", true);
      assertEquals("2015-02-07T13:28:17.239+02:00", at(issued, "fixedInstant"));
      assertNull(at(issued, "patternInstant"));
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw ValueAlreadyAssignedError when assigning an already assigned instant by pattern[x]")
    void t38() {
      ElementDefinition issued = el(observation, "Observation.issued");
      issued.assignValue("2015-02-07T13:28:17.239+02:00");
      assertEquals("2015-02-07T13:28:17.239+02:00", at(issued, "patternInstant"));
      assertThrowsContaining("Cannot assign \"2016-02-07T13:28:17.239+02:00\" to this element; a different instant is already assigned: \"2015-02-07T13:28:17.239+02:00\".", () -> { issued.assignValue("2016-02-07T13:28:17.239+02:00");
 });
      assertThrowsContaining("Cannot assign \"2016-02-07T13:28:17.239+02:00\" to this element; a different instant is already assigned: \"2015-02-07T13:28:17.239+02:00\".", () -> { issued.assignValue("2016-02-07T13:28:17.239+02:00", true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw ValueAlreadyAssignedError when assigning an already assigned instant by fixed[x]")
    void t39() {
      ElementDefinition issued = el(observation, "Observation.issued");
      issued.assignValue("2015-02-07T13:28:17.239+02:00", true);
      assertEquals("2015-02-07T13:28:17.239+02:00", at(issued, "fixedInstant"));
      assertThrowsContaining("Cannot assign \"2016-02-07T13:28:17.239+02:00\" to this element; a different instant is already assigned: \"2015-02-07T13:28:17.239+02:00\".", () -> { issued.assignValue("2016-02-07T13:28:17.239+02:00", true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw FixedToPatternError when trying to change fixed[x] to pattern[x]")
    void t40() {
      ElementDefinition issued = el(observation, "Observation.issued");
      issued.assignValue("2015-02-07T13:28:17.239+02:00", true);
      assertEquals("2015-02-07T13:28:17.239+02:00", at(issued, "fixedInstant"));
      assertThrowsContaining("Cannot assign this element using a pattern; as it is already assigned in the StructureDefinition using fixedInstant.", () -> { issued.assignValue("2015-02-07T13:28:17.239+02:00");
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw MismatchedTypeError when assigning an instant to an incorrect value")
    void t41() {
      ElementDefinition issued = el(observation, "Observation.issued");
      assertThrowsContaining("Cannot assign string value: 2015-02-07. Value does not match element type: instant", () -> { issued.assignValue("2015-02-07");
 });
      assertThrowsContaining("Cannot assign string value: 2015-02-07. Value does not match element type: instant", () -> { issued.assignValue("2015-02-07", true);
 });
    }
    // Assigning a date

    @Test
    @DisplayName("ElementDefinition #assignString should assign a string to a date")
    void t42() {
      ElementDefinition birthDate = el(patient, "Patient.birthDate");
      birthDate.assignValue("1905-08-23");
      assertEquals("1905-08-23", at(birthDate, "patternDate"));
      assertNull(at(birthDate, "fixedDate"));
    }

    @Test
    @DisplayName("ElementDefinition #assignString should assign a string to a date (exactly)")
    void t43() {
      ElementDefinition birthDate = el(patient, "Patient.birthDate");
      birthDate.assignValue("1905-08-23", true);
      assertEquals("1905-08-23", at(birthDate, "fixedDate"));
      assertNull(at(birthDate, "patternDate"));
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw ValueAlreadyAssignedError when assigning an already assigned date by pattern[x]")
    void t44() {
      ElementDefinition birthDate = el(patient, "Patient.birthDate");
      birthDate.assignValue("1905-08-23");
      assertEquals("1905-08-23", at(birthDate, "patternDate"));
      assertThrowsContaining("Cannot assign \"1905-08-24\" to this element; a different date is already assigned: \"1905-08-23\".", () -> { birthDate.assignValue("1905-08-24");
 });
      assertThrowsContaining("Cannot assign \"1905-08-24\" to this element; a different date is already assigned: \"1905-08-23\".", () -> { birthDate.assignValue("1905-08-24", true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw ValueAlreadyAssignedError when assigning an already assigned date by fixed[x]")
    void t45() {
      ElementDefinition birthDate = el(patient, "Patient.birthDate");
      birthDate.assignValue("1905-08-23", true);
      assertEquals("1905-08-23", at(birthDate, "fixedDate"));
      assertThrowsContaining("Cannot assign \"1905-08-24\" to this element; a different date is already assigned: \"1905-08-23\".", () -> { birthDate.assignValue("1905-08-24", true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw FixedToPatternError when trying to change fixed[x] to pattern[x]")
    void t46() {
      ElementDefinition birthDate = el(patient, "Patient.birthDate");
      birthDate.assignValue("1905-08-23", true);
      assertEquals("1905-08-23", at(birthDate, "fixedDate"));
      assertThrowsContaining("Cannot assign this element using a pattern; as it is already assigned in the StructureDefinition using fixedDate.", () -> { birthDate.assignValue("1905-08-23");
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw MismatchedTypeError when assigning a date to an incorrect value")
    void t47() {
      ElementDefinition birthDate = el(patient, "Patient.birthDate");
      assertThrowsContaining("Cannot assign string value: 2016-02-07T13:28:17.239+02:00. Value does not match element type: date", () -> { birthDate.assignValue("2016-02-07T13:28:17.239+02:00");
 });
      assertThrowsContaining("Cannot assign string value: 2016-02-07T13:28:17.239+02:00. Value does not match element type: date", () -> { birthDate.assignValue("2016-02-07T13:28:17.239+02:00", true);
 });
    }
    // Assigning a dateTime

    @Test
    @DisplayName("ElementDefinition #assignString should assign a string to a dateTime")
    void t48() {
      ElementDefinition date = el(riskEvidenceSynthesis, "RiskEvidenceSynthesis.date");
      date.assignValue("2015-02-07T13:28:17-05:00");
      assertEquals("2015-02-07T13:28:17-05:00", at(date, "patternDateTime"));
      assertNull(at(date, "fixedDateTime"));
    }

    @Test
    @DisplayName("ElementDefinition #assignString should assign a string to a dateTime (exactly)")
    void t49() {
      ElementDefinition date = el(riskEvidenceSynthesis, "RiskEvidenceSynthesis.date");
      date.assignValue("2015-02-07T13:28:17-05:00", true);
      assertEquals("2015-02-07T13:28:17-05:00", at(date, "fixedDateTime"));
      assertNull(at(date, "patternDateTime"));
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw ValueAlreadyAssignedError when assigning an already assigned dateTime by pattern[x]")
    void t50() {
      ElementDefinition date = el(riskEvidenceSynthesis, "RiskEvidenceSynthesis.date");
      date.assignValue("1905-08-23");
      assertEquals("1905-08-23", at(date, "patternDateTime"));
      assertThrowsContaining("Cannot assign \"1905-08-24\" to this element; a different dateTime is already assigned: \"1905-08-23\".", () -> { date.assignValue("1905-08-24");
 });
      assertThrowsContaining("Cannot assign \"1905-08-24\" to this element; a different dateTime is already assigned: \"1905-08-23\".", () -> { date.assignValue("1905-08-24", true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw ValueAlreadyAssignedError when assigning an already assigned dateTime by fixed[x]")
    void t51() {
      ElementDefinition date = el(riskEvidenceSynthesis, "RiskEvidenceSynthesis.date");
      date.assignValue("1905-08-23", true);
      assertEquals("1905-08-23", at(date, "fixedDateTime"));
      assertThrowsContaining("Cannot assign \"1905-08-24\" to this element; a different dateTime is already assigned: \"1905-08-23\".", () -> { date.assignValue("1905-08-24", true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw FixedToPatternError when trying to change fixed[x] to pattern[x]")
    void t52() {
      ElementDefinition date = el(riskEvidenceSynthesis, "RiskEvidenceSynthesis.date");
      date.assignValue("1905-08-23", true);
      assertEquals("1905-08-23", at(date, "fixedDateTime"));
      assertThrowsContaining("Cannot assign this element using a pattern; as it is already assigned in the StructureDefinition using fixedDateTime.", () -> { date.assignValue("1905-08-23");
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw MismatchedTypeError when assigning a dateTime to an incorrect value")
    void t53() {
      ElementDefinition date = el(riskEvidenceSynthesis, "RiskEvidenceSynthesis.date");
      assertThrowsContaining("Cannot assign string value: hello there. Value does not match element type: dateTime", () -> { date.assignValue("hello there");
 });
      assertThrowsContaining("Cannot assign string value: hello there. Value does not match element type: dateTime", () -> { date.assignValue("hello there", true);
 });
    }
    // Assigning a time

    @Test
    @DisplayName("ElementDefinition #assignString should assign a string to a time")
    void t54() {
      ElementDefinition hoursOfOperationClosingTime = el(location, "Location.hoursOfOperation.closingTime");
      hoursOfOperationClosingTime.assignValue("12:34:56");
      assertEquals("12:34:56", at(hoursOfOperationClosingTime, "patternTime"));
      assertNull(at(hoursOfOperationClosingTime, "fixedTime"));
    }

    @Test
    @DisplayName("ElementDefinition #assignString should assign a string to a time (exactly)")
    void t55() {
      ElementDefinition hoursOfOperationClosingTime = el(location, "Location.hoursOfOperation.closingTime");
      hoursOfOperationClosingTime.assignValue("12:34:56", true);
      assertEquals("12:34:56", at(hoursOfOperationClosingTime, "fixedTime"));
      assertNull(at(hoursOfOperationClosingTime, "patternTime"));
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw ValueAlreadyAssignedError when assigning an already assigned time by pattern[x]")
    void t56() {
      ElementDefinition hoursOfOperationClosingTime = el(location, "Location.hoursOfOperation.closingTime");
      hoursOfOperationClosingTime.assignValue("12:34:56");
      assertEquals("12:34:56", at(hoursOfOperationClosingTime, "patternTime"));
      assertThrowsContaining("Cannot assign \"12:34:57\" to this element; a different time is already assigned: \"12:34:56\".", () -> { hoursOfOperationClosingTime.assignValue("12:34:57");
 });
      assertThrowsContaining("Cannot assign \"12:34:57\" to this element; a different time is already assigned: \"12:34:56\".", () -> { hoursOfOperationClosingTime.assignValue("12:34:57", true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw ValueAlreadyAssignedError when assigning an already assigned time by fixed[x]")
    void t57() {
      ElementDefinition hoursOfOperationClosingTime = el(location, "Location.hoursOfOperation.closingTime");
      hoursOfOperationClosingTime.assignValue("12:34:56", true);
      assertEquals("12:34:56", at(hoursOfOperationClosingTime, "fixedTime"));
      assertThrowsContaining("Cannot assign \"12:34:57\" to this element; a different time is already assigned: \"12:34:56\".", () -> { hoursOfOperationClosingTime.assignValue("12:34:57", true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw FixedToPatternError when trying to change fixed[x] to pattern[x]")
    void t58() {
      ElementDefinition hoursOfOperationClosingTime = el(location, "Location.hoursOfOperation.closingTime");
      hoursOfOperationClosingTime.assignValue("12:34:56", true);
      assertEquals("12:34:56", at(hoursOfOperationClosingTime, "fixedTime"));
      assertThrowsContaining("Cannot assign this element using a pattern; as it is already assigned in the StructureDefinition using fixedTime.", () -> { hoursOfOperationClosingTime.assignValue("12:34:56");
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw MismatchedTypeError when assigning a time to an incorrect value")
    void t59() {
      ElementDefinition hoursOfOperationClosingTime = el(location, "Location.hoursOfOperation.closingTime");
      assertThrowsContaining("Cannot assign string value: hello there. Value does not match element type: time", () -> { hoursOfOperationClosingTime.assignValue("hello there");
 });
      assertThrowsContaining("Cannot assign string value: hello there. Value does not match element type: time", () -> { hoursOfOperationClosingTime.assignValue("hello there", true);
 });
    }
    // Assigning an oid

    @Test
    @DisplayName("ElementDefinition #assignString should assign a string to an oid")
    void t60() {
      ElementDefinition inputValueOid = task.findElementByPath("input.valueOid", fisher);
      inputValueOid.assignValue("urn:oid:1.2.3.4.5");
      assertEquals("urn:oid:1.2.3.4.5", at(inputValueOid, "patternOid"));
      assertNull(at(inputValueOid, "fixedOid"));
    }

    @Test
    @DisplayName("ElementDefinition #assignString should assign a string to an oid (exactly)")
    void t61() {
      ElementDefinition inputValueOid = task.findElementByPath("input.valueOid", fisher);
      inputValueOid.assignValue("urn:oid:1.2.3.4.5", true);
      assertEquals("urn:oid:1.2.3.4.5", at(inputValueOid, "fixedOid"));
      assertNull(at(inputValueOid, "patternOid"));
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw ValueAlreadyAssignedError when assigning an already assigned oid by pattern[x]")
    void t62() {
      ElementDefinition inputValueOid = task.findElementByPath("input.valueOid", fisher);
      inputValueOid.assignValue("urn:oid:1.2.3.4.5");
      assertEquals("urn:oid:1.2.3.4.5", at(inputValueOid, "patternOid"));
      assertThrowsContaining("Cannot assign \"urn:oid:1.4.3.2.1\" to this element; a different oid is already assigned: \"urn:oid:1.2.3.4.5\".", () -> { inputValueOid.assignValue("urn:oid:1.4.3.2.1");
 });
      assertThrowsContaining("Cannot assign \"urn:oid:1.4.3.2.1\" to this element; a different oid is already assigned: \"urn:oid:1.2.3.4.5\".", () -> { inputValueOid.assignValue("urn:oid:1.4.3.2.1", true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw ValueAlreadyAssignedError when assigning an already assigned oid by fixed[x]")
    void t63() {
      ElementDefinition inputValueOid = task.findElementByPath("input.valueOid", fisher);
      inputValueOid.assignValue("urn:oid:1.2.3.4.5", true);
      assertEquals("urn:oid:1.2.3.4.5", at(inputValueOid, "fixedOid"));
      assertThrowsContaining("Cannot assign \"urn:oid:1.4.3.2.1\" to this element; a different oid is already assigned: \"urn:oid:1.2.3.4.5\".", () -> { inputValueOid.assignValue("urn:oid:1.4.3.2.1", true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw FixedToPatternError when trying to change fixed[x] to pattern[x]")
    void t64() {
      ElementDefinition inputValueOid = task.findElementByPath("input.valueOid", fisher);
      inputValueOid.assignValue("urn:oid:1.2.3.4.5", true);
      assertEquals("urn:oid:1.2.3.4.5", at(inputValueOid, "fixedOid"));
      assertThrowsContaining("Cannot assign this element using a pattern; as it is already assigned in the StructureDefinition using fixedOid.", () -> { inputValueOid.assignValue("urn:oid:1.2.3.4.5");
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw MismatchedTypeError when assigning an oid to an incorrect value")
    void t65() {
      ElementDefinition inputValueOid = task.findElementByPath("input.valueOid", fisher);
      assertThrowsContaining("Cannot assign string value: invalid oid. Value does not match element type: oid", () -> { inputValueOid.assignValue("invalid oid");
 });
      assertThrowsContaining("Cannot assign string value: invalid oid. Value does not match element type: oid", () -> { inputValueOid.assignValue("invalid oid", true);
 });
    }
    // Assigning an id

    @Test
    @DisplayName("ElementDefinition #assignString should assign a string to an id")
    void t66() {
      ElementDefinition uid = el(imagingStudy, "ImagingStudy.series.uid");
      uid.assignValue("uniqueId123");
      assertEquals("uniqueId123", at(uid, "patternId"));
      assertNull(at(uid, "fixedId"));
    }

    @Test
    @DisplayName("ElementDefinition #assignString should assign a string to an id (exactly)")
    void t67() {
      ElementDefinition uid = el(imagingStudy, "ImagingStudy.series.uid");
      uid.assignValue("uniqueId123", true);
      assertEquals("uniqueId123", at(uid, "fixedId"));
      assertNull(at(uid, "patternId"));
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw ValueAlreadyAssignedError when assigning an already assigned id by pattern[x]")
    void t68() {
      ElementDefinition uid = el(imagingStudy, "ImagingStudy.series.uid");
      uid.assignValue("uniqueId123");
      assertEquals("uniqueId123", at(uid, "patternId"));
      assertThrowsContaining("Cannot assign \"anotherUniqueId321\" to this element; a different id is already assigned: \"uniqueId123\".", () -> { uid.assignValue("anotherUniqueId321");
 });
      assertThrowsContaining("Cannot assign \"anotherUniqueId321\" to this element; a different id is already assigned: \"uniqueId123\".", () -> { uid.assignValue("anotherUniqueId321", true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw ValueAlreadyAssignedError when assigning an already assigned id by fixed[x]")
    void t69() {
      ElementDefinition uid = el(imagingStudy, "ImagingStudy.series.uid");
      uid.assignValue("uniqueId123", true);
      assertEquals("uniqueId123", at(uid, "fixedId"));
      assertThrowsContaining("Cannot assign \"anotherUniqueId321\" to this element; a different id is already assigned: \"uniqueId123\".", () -> { uid.assignValue("anotherUniqueId321", true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw FixedToPatternError when trying to change fixed[x] to pattern[x]")
    void t70() {
      ElementDefinition uid = el(imagingStudy, "ImagingStudy.series.uid");
      uid.assignValue("uniqueId123", true);
      assertEquals("uniqueId123", at(uid, "fixedId"));
      assertThrowsContaining("Cannot assign this element using a pattern; as it is already assigned in the StructureDefinition using fixedId.", () -> { uid.assignValue("uniqueId123");
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw MismatchedTypeError when assigning an id to an incorrect value")
    void t71() {
      ElementDefinition uid = el(imagingStudy, "ImagingStudy.series.uid");
      assertThrowsContaining("Cannot assign string value: invalid id. Value does not match element type: id", () -> { uid.assignValue("invalid id");
 });
      assertThrowsContaining("Cannot assign string value: invalid id. Value does not match element type: id", () -> { uid.assignValue("invalid id", true);
 });
    }
    // Assigning markdown

    @Test
    @DisplayName("ElementDefinition #assignString should assign a string to a markdown")
    void t72() {
      ElementDefinition description = el(capabilityStatement, "CapabilityStatement.description");
      description.assignValue("`This is code`");
      assertEquals("`This is code`", at(description, "patternMarkdown"));
      assertNull(at(description, "fixedMarkdown"));
    }

    @Test
    @DisplayName("ElementDefinition #assignString should assign a string to a markdown (exactly)")
    void t73() {
      ElementDefinition description = el(capabilityStatement, "CapabilityStatement.description");
      description.assignValue("`This is code`", true);
      assertEquals("`This is code`", at(description, "fixedMarkdown"));
      assertNull(at(description, "patternMarkdown"));
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw ValueAlreadyAssignedError when assigning an already assigned markdown by pattern[x]")
    void t74() {
      ElementDefinition description = el(capabilityStatement, "CapabilityStatement.description");
      description.assignValue("some text");
      assertEquals("some text", at(description, "patternMarkdown"));
      assertThrowsContaining("Cannot assign \"other text\" to this element; a different markdown is already assigned: \"some text\".", () -> { description.assignValue("other text");
 });
      assertThrowsContaining("Cannot assign \"other text\" to this element; a different markdown is already assigned: \"some text\".", () -> { description.assignValue("other text", true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw ValueAlreadyAssignedError when assigning an already assigned markdown by fixed[x]")
    void t75() {
      ElementDefinition description = el(capabilityStatement, "CapabilityStatement.description");
      description.assignValue("some text", true);
      assertEquals("some text", at(description, "fixedMarkdown"));
      assertThrowsContaining("Cannot assign \"other text\" to this element; a different markdown is already assigned: \"some text\".", () -> { description.assignValue("other text", true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw FixedToPatternError when trying to change fixed[x] to pattern[x]")
    void t76() {
      ElementDefinition description = el(capabilityStatement, "CapabilityStatement.description");
      description.assignValue("some text", true);
      assertEquals("some text", at(description, "fixedMarkdown"));
      assertThrowsContaining("Cannot assign this element using a pattern; as it is already assigned in the StructureDefinition using fixedMarkdown.", () -> { description.assignValue("some text");
 });
    }
    // Assigning uuid

    @Test
    @DisplayName("ElementDefinition #assignString should assign a string to a uuid")
    void t77() {
      ElementDefinition inputValueUuid = task.findElementByPath("input.valueUuid", fisher);
      inputValueUuid.assignValue("urn:uuid:c757873d-ec9a-4326-a141-556f43239520");
      assertEquals("urn:uuid:c757873d-ec9a-4326-a141-556f43239520", at(inputValueUuid, "patternUuid"));
      assertNull(at(inputValueUuid, "fixedUuid"));
    }

    @Test
    @DisplayName("ElementDefinition #assignString should assign a string to a uuid (exactly)")
    void t78() {
      ElementDefinition inputValueUuid = task.findElementByPath("input.valueUuid", fisher);
      inputValueUuid.assignValue("urn:uuid:c757873d-ec9a-4326-a141-556f43239520", true);
      assertEquals("urn:uuid:c757873d-ec9a-4326-a141-556f43239520", at(inputValueUuid, "fixedUuid"));
      assertNull(at(inputValueUuid, "patternUuid"));
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw ValueAlreadyAssignedError when assigning an already assigned uuid by pattern[x]")
    void t79() {
      ElementDefinition inputValueUuid = task.findElementByPath("input.valueUuid", fisher);
      inputValueUuid.assignValue("urn:uuid:c757873d-ec9a-4326-a141-556f43239520");
      assertEquals("urn:uuid:c757873d-ec9a-4326-a141-556f43239520", at(inputValueUuid, "patternUuid"));
      assertThrowsContaining("Cannot assign \"urn:uuid:c123456d-ec9a-4326-a141-556f43239520\" to this element; a different uuid is already assigned: \"urn:uuid:c757873d-ec9a-4326-a141-556f43239520\".", () -> { inputValueUuid.assignValue("urn:uuid:c123456d-ec9a-4326-a141-556f43239520");
 });
      assertThrowsContaining("Cannot assign \"urn:uuid:c123456d-ec9a-4326-a141-556f43239520\" to this element; a different uuid is already assigned: \"urn:uuid:c757873d-ec9a-4326-a141-556f43239520\".", () -> { inputValueUuid.assignValue("urn:uuid:c123456d-ec9a-4326-a141-556f43239520", true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw ValueAlreadyAssignedError when assigning an already assigned uuid by fixed[x]")
    void t80() {
      ElementDefinition inputValueUuid = task.findElementByPath("input.valueUuid", fisher);
      inputValueUuid.assignValue("urn:uuid:c757873d-ec9a-4326-a141-556f43239520", true);
      assertEquals("urn:uuid:c757873d-ec9a-4326-a141-556f43239520", at(inputValueUuid, "fixedUuid"));
      assertThrowsContaining("Cannot assign \"urn:uuid:c123456d-ec9a-4326-a141-556f43239520\" to this element; a different uuid is already assigned: \"urn:uuid:c757873d-ec9a-4326-a141-556f43239520\".", () -> { inputValueUuid.assignValue("urn:uuid:c123456d-ec9a-4326-a141-556f43239520", true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw FixedToPatternError when trying to change fixed[x] to pattern[x]")
    void t81() {
      ElementDefinition inputValueUuid = task.findElementByPath("input.valueUuid", fisher);
      inputValueUuid.assignValue("urn:uuid:c757873d-ec9a-4326-a141-556f43239520", true);
      assertEquals("urn:uuid:c757873d-ec9a-4326-a141-556f43239520", at(inputValueUuid, "fixedUuid"));
      assertThrowsContaining("Cannot assign this element using a pattern; as it is already assigned in the StructureDefinition using fixedUuid.", () -> { inputValueUuid.assignValue("urn:uuid:c757873d-ec9a-4326-a141-556f43239520");
 });
    }
    // Assigning xhtml

    @Test
    @DisplayName("ElementDefinition #assignString should assign a string to an xhtml")
    void t82() {
      ElementDefinition narrativeDiv = patient.findElementByPath("text.div", fisher);
      narrativeDiv.assignValue("<div xmlns=\"http://www.w3.org/1999/xhtml\">Twas brillig</div>");
      assertEquals("<div xmlns=\"http://www.w3.org/1999/xhtml\">Twas brillig</div>", at(narrativeDiv, "patternXhtml"));
    }

    @Test
    @DisplayName("ElementDefinition #assignString should assign a string to an xhtml (exactly)")
    void t83() {
      ElementDefinition narrativeDiv = patient.findElementByPath("text.div", fisher);
      narrativeDiv.assignValue("<div xmlns=\"http://www.w3.org/1999/xhtml\">Twas brillig</div>", true);
      assertEquals("<div xmlns=\"http://www.w3.org/1999/xhtml\">Twas brillig</div>", at(narrativeDiv, "fixedXhtml"));
    }

    @Test
    @DisplayName("ElementDefinition #assignString should assign a string to an xhtml and collapse whitespace")
    void t84() {
      ElementDefinition narrativeDiv = patient.findElementByPath("text.div", fisher);
      narrativeDiv.assignValue("<div xmlns=\"http://www.w3.org/1999/xhtml\">\n\n        Twas     brillig\n        and the   slithy             toves\n\n\n        </div>");
      assertEquals("<div xmlns=\"http://www.w3.org/1999/xhtml\">Twas brillig and the slithy toves</div>", at(narrativeDiv, "patternXhtml"));
    }

    @Test
    @DisplayName("ElementDefinition #assignString should assign a string to an xhtml and collapse whitespace (exactly)")
    void t85() {
      ElementDefinition narrativeDiv = patient.findElementByPath("text.div", fisher);
      narrativeDiv.assignValue("<div xmlns=\"http://www.w3.org/1999/xhtml\">\n\n        Twas     brillig\n        and the   slithy             toves\n\n\n        </div>", true);
      assertEquals("<div xmlns=\"http://www.w3.org/1999/xhtml\">Twas brillig and the slithy toves</div>", at(narrativeDiv, "fixedXhtml"));
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw ValueAlreadyAssignedError when assigning an already assigned xhtml by pattern[x]")
    void t86() {
      ElementDefinition narrativeDiv = patient.findElementByPath("text.div", fisher);
      narrativeDiv.assignValue("<div xmlns=\"http://www.w3.org/1999/xhtml\">Twas brillig</div>");
      assertEquals("<div xmlns=\"http://www.w3.org/1999/xhtml\">Twas brillig</div>", at(narrativeDiv, "patternXhtml"));
      assertThrowsContaining("Cannot assign \"<div xmlns=\"http://www.w3.org/1999/xhtml\">and the slithy toves</div>\" to this element; a different xhtml is already assigned: \"<div xmlns=\\\"http://www.w3.org/1999/xhtml\\\">Twas brillig</div>\".", () -> { narrativeDiv.assignValue("<div xmlns=\"http://www.w3.org/1999/xhtml\">and the slithy toves</div>");
 });
      assertThrowsContaining("Cannot assign \"<div xmlns=\"http://www.w3.org/1999/xhtml\">and the slithy toves</div>\" to this element; a different xhtml is already assigned: \"<div xmlns=\\\"http://www.w3.org/1999/xhtml\\\">Twas brillig</div>\".", () -> { narrativeDiv.assignValue("<div xmlns=\"http://www.w3.org/1999/xhtml\">and the slithy toves</div>", true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw ValueAlreadyAssignedError when assigning an already assigned xhtml by fixed[x]")
    void t87() {
      ElementDefinition narrativeDiv = patient.findElementByPath("text.div", fisher);
      narrativeDiv.assignValue("<div xmlns=\"http://www.w3.org/1999/xhtml\">Twas brillig</div>", true);
      assertEquals("<div xmlns=\"http://www.w3.org/1999/xhtml\">Twas brillig</div>", at(narrativeDiv, "fixedXhtml"));
      assertThrowsContaining("Cannot assign \"<div xmlns=\"http://www.w3.org/1999/xhtml\">and the slithy toves</div>\" to this element; a different xhtml is already assigned: \"<div xmlns=\\\"http://www.w3.org/1999/xhtml\\\">Twas brillig</div>\".", () -> { narrativeDiv.assignValue("<div xmlns=\"http://www.w3.org/1999/xhtml\">and the slithy toves</div>", true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw FixedToPatternError when trying to change fixed[x] to pattern[x]")
    void t88() {
      ElementDefinition narrativeDiv = patient.findElementByPath("text.div", fisher);
      narrativeDiv.assignValue("<div xmlns=\"http://www.w3.org/1999/xhtml\">Twas brillig</div>", true);
      assertEquals("<div xmlns=\"http://www.w3.org/1999/xhtml\">Twas brillig</div>", at(narrativeDiv, "fixedXhtml"));
      assertThrowsContaining("Cannot assign this element using a pattern; as it is already assigned in the StructureDefinition using fixedXhtml.", () -> { narrativeDiv.assignValue("<div xmlns=\"http://www.w3.org/1999/xhtml\">Twas brillig</div>");
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw MismatchedTypeError when assigning to a value that is not valid xhtml")
    void t89() {
      ElementDefinition narrativeDiv = patient.findElementByPath("text.div", fisher);
      assertThrowsContaining("Cannot assign string value: This is no good. Value does not match element type: xhtml", () -> { narrativeDiv.assignValue("This is no good");
 });
      assertThrowsContaining("Cannot assign string value: This is no good. Value does not match element type: xhtml", () -> { narrativeDiv.assignValue("This is no good", true);
 });
    }

    @Test
    @DisplayName("ElementDefinition #assignString should throw NoSingleTypeError when element has multiple types")
    void t90() {
      ElementDefinition valueX = el(observation, "Observation.value[x]");
      assertThrowsContaining("Cannot assign string value on this element since this element does not have a single type", () -> { valueX.assignValue("hello");
 });
      assertThrowsContaining("Cannot assign string value on this element since this element does not have a single type", () -> { valueX.assignValue("hello", true);
 });
      assertNull(at(valueX, "patternString"));
      assertNull(at(valueX, "fixedString"));
    }

    @Nested
    @DisplayName("#integer64")
    class Integer64 {
      static ElementDefinition valueX;
      static ElementDefinition valueInteger64;
      @BeforeAll
      static void beforeAll91() {
        StructureDefinition r5Extension = StructureDefinition.fromJSON(readJson(
            org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.testDefsPath("r5-definitions", "package",
                "StructureDefinition-Extension.json")));
        valueX = el(r5Extension, "Extension.value[x]");
      }
      @BeforeEach
      void beforeEach92() {
        valueInteger64 = valueX.clone(false);
        valueInteger64.getType().removeIf(t -> !"integer64".equals(t.getCode()));
      }
      // assigning an integer64
      // NOTE: Tests of assigning an integer64 as a number are in ElementDefinition.assignNumber.test.ts

      @Test
      @DisplayName("ElementDefinition #assignString #integer64 should assign an integer string to an integer64")
      void t93() {
        valueInteger64.assignValue("123");
        assertEquals("123", at(valueInteger64, "patternInteger64"));
        assertNull(at(valueInteger64, "fixedInteger64"));
      }

      @Test
      @DisplayName("ElementDefinition #assignString #integer64 should assign an integer string to an integer64 (exactly)")
      void t94() {
        valueInteger64.assignValue("123", true);
        assertNull(at(valueInteger64, "patternInteger64"));
        assertEquals("123", at(valueInteger64, "fixedInteger64"));
      }

      @Test
      @DisplayName("ElementDefinition #assignString #integer64 should throw MismatchedTypeError when assigning a decimal string to an integer64 value")
      void t95() {
        assertThrowsContaining("Cannot assign string value: 12.3. Value does not match element type: integer64", () -> { valueInteger64.assignValue("12.3");
 });
        assertThrowsContaining("Cannot assign string value: 12.3. Value does not match element type: integer64", () -> { valueInteger64.assignValue("12.3", true);
 });
      }

      @Test
      @DisplayName("ElementDefinition #assignString #integer64 should throw MismatchedTypeError when assigning a string to an integer64 value")
      void t96() {
        assertThrowsContaining("Cannot assign string value: foo. Value does not match element type: integer64", () -> { valueInteger64.assignValue("foo");
 });
      }
    }
  }

  @Nested
  @DisplayName("#checkXhtml")
  class CheckXhtml {
    @BeforeEach
    void beforeEach97() {
      loggerSpy.reset();
    }

    @Test
    @DisplayName("ElementDefinition #checkXhtml should warn and throw when path doesn't start and end with div")
    void t98() {
      var didThrow = false;
      ElementDefinition narrativeDiv = patient.findElementByPath("text.div", fisher);
      try {
        narrativeDiv.assignValue("<piv xmlns=\"http://www.w3.org/1999/xhtml\">Twas brillig</div>");
      } catch (RuntimeException e) {
        didThrow = true;
        assertTrue(loggerSpy.getLastMessage("warn").contains(
            "xhtml div elements should start and end with <div> tags for Patient.text.div"));
      }
      assertEquals(true, didThrow);
    }

    @Test
    @DisplayName("ElementDefinition #checkXhtml shouldn't warn when path starts and ends with div")
    void t99() {
      ElementDefinition narrativeDiv = patient.findElementByPath("text.div", fisher);
      narrativeDiv.assignValue("<div xmlns=\"http://www.w3.org/1999/xhtml\">Twas brillig</div>");
      assertEquals(0, size(loggerSpy.getAllLogs("warn")));
    }
  }
}
