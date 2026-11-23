package org.hl7.fhir.sushi.fhirtypes;

import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertJsEqual;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertMatches;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.at;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.json;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.sushi.fshtypes.FshCode;
import org.hl7.fhir.sushi.testhelpers.TestFisher;
import org.hl7.fhir.sushi.utils.FishableType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

// port of sushi/test/fhirtypes/StructureDefinition.test.ts (SUSHI 3.21.1): '#setInstancePropertyByPath' and
// '#validateValueAtPath' (up to '#Inline Instances', which is in StructureDefinitionInlineTest)
@DisplayName("StructureDefinition")
class StructureDefinitionSetPropertyTest {
  TestFisher fisher;
  StructureDefinition observation;

  @BeforeEach
  void beforeEach() {
    fisher = SdTestSupport.fisher();
    loggerSpy.reset();
    observation = StructureDefinition.fromJSON(SdTestSupport.jsonObservation);
  }

  static void assertThrowsMessage(String message, Executable e) {
    RuntimeException ex = assertThrows(RuntimeException.class, e);
    assertTrue(ex.getMessage() != null && ex.getMessage().contains(message),
        () -> "expected message containing <" + message + "> but was <" + ex.getMessage() + ">");
  }

  static PathPart part(String base) {
    return new PathPart(base);
  }

  static PathPart prim(String base) {
    return new PathPart(base).withPrimitive(true);
  }

  @Nested
  @DisplayName("#setInstancePropertyByPath")
  class SetInstancePropertyByPath {
    FshCode fooCode;
    FshCode barCode;

    @BeforeEach
    void setUp() {
      fooCode = new FshCode("foo", "http://example.com");
      barCode = new FshCode("bar", "http://example.com");
    }

    @Test
    @DisplayName("StructureDefinition #setInstancePropertyByPath should set an instance property which has a value")
    void t1() {
      observation.setInstancePropertyByPath("version", "1.2.3", fisher);
      assertEquals("1.2.3", observation.getVersion());
    }

    @Test
    @DisplayName("StructureDefinition #setInstancePropertyByPath should set an instance property which must be created")
    void t2() {
      observation.setInstancePropertyByPath("title", "foo", fisher);
      assertEquals("foo", observation.getTitle());
    }

    @Test
    @DisplayName("StructureDefinition #setInstancePropertyByPath should not set an instance property which is being assigned incorrectly")
    void t3() {
      assertThrowsMessage("Cannot assign number value: 1.2. Value does not match element type: string",
          () -> observation.setInstancePropertyByPath("version", 1.2, fisher));
      assertEquals("4.0.1", observation.getVersion());
    }

    @Test
    @DisplayName("StructureDefinition #setInstancePropertyByPath should add an instance property at the end of an array")
    void t4() {
      observation.setInstancePropertyByPath("contact[2].telecom[0].value", "foo", fisher);
      assertEquals(3, observation.getContact().size());
      assertJsEqual(json("{\"telecom\": [{\"value\": \"foo\"}]}"), observation.getContact().get(2));
    }

    @Test
    @DisplayName("StructureDefinition #setInstancePropertyByPath should add an instance property in an array that must be empty filled")
    void t5() {
      observation.setInstancePropertyByPath("contact[4].telecom[0].value", "foo", fisher);
      assertEquals(5, observation.getContact().size());
      assertJsEqual(json("{\"telecom\": [{\"value\": \"foo\"}]}"), observation.getContact().get(4));
      assertNull(observation.getContact().get(3));
      assertNull(observation.getContact().get(2));
    }

    @Test
    @DisplayName("StructureDefinition #setInstancePropertyByPath should add an instance property in an array that has been empty filled")
    void t6() {
      observation.setInstancePropertyByPath("contact[3].telecom[0].value", "foo", fisher);
      observation.setInstancePropertyByPath("contact[2].telecom[0].value", "bar", fisher);
      assertEquals(4, observation.getContact().size());
      assertJsEqual(json("{\"telecom\": [{\"value\": \"foo\"}]}"), observation.getContact().get(3));
      assertJsEqual(json("{\"telecom\": [{\"value\": \"bar\"}]}"), observation.getContact().get(2));
    }

    @Test
    @DisplayName("StructureDefinition #setInstancePropertyByPath should change an instance property in an array")
    void t7() {
      observation.setInstancePropertyByPath("contact[1].telecom[0].value", "foo", fisher);
      assertEquals(2, observation.getContact().size());
      assertJsEqual(json("{\"telecom\": [{\"value\": \"foo\", \"system\": \"url\"}]}"),
          observation.getContact().get(1));
    }

    @Test
    @DisplayName("StructureDefinition #setInstancePropertyByPath should change a part of an instance property in an array")
    void t8() {
      observation.setInstancePropertyByPath("contact[0].telecom[0].period.start", "2019-11-25", fisher);
      assertEquals(2, observation.getContact().size());
      assertJsEqual(json("{\"telecom\": [{\"value\": \"http://hl7.org/fhir\", \"system\": \"url\","
          + " \"period\": {\"start\": \"2019-11-25\"}}]}"), observation.getContact().get(0));
    }

    @Test
    @DisplayName("StructureDefinition #setInstancePropertyByPath should set a complex instance property on a newly created array")
    void t9() {
      observation.setInstancePropertyByPath("jurisdiction[0]", fooCode, fisher);
      assertEquals(1, observation.getJurisdiction().size());
      assertJsEqual(json("{\"coding\": [{\"code\": \"foo\", \"system\": \"http://example.com\"}]}"),
          observation.getJurisdiction().get(0));
    }

    @Test
    @DisplayName("StructureDefinition #setInstancePropertyByPath should set a complex instance property on a newly created array, with implied 0 index")
    void t10() {
      observation.setInstancePropertyByPath("jurisdiction", fooCode, fisher);
      assertEquals(1, observation.getJurisdiction().size());
      assertJsEqual(json("{\"coding\": [{\"code\": \"foo\", \"system\": \"http://example.com\"}]}"),
          observation.getJurisdiction().get(0));
    }

    @Test
    @DisplayName("StructureDefinition #setInstancePropertyByPath should set a complex instance property over a value that already exists")
    void t11() {
      observation.setInstancePropertyByPath("jurisdiction[0]", fooCode, fisher);
      observation.setInstancePropertyByPath("jurisdiction[0]", barCode, fisher);
      assertEquals(1, observation.getJurisdiction().size());
      assertJsEqual(json("{\"coding\": [{\"code\": \"bar\", \"system\": \"http://example.com\"}]}"),
          observation.getJurisdiction().get(0));
    }

    @Test
    @DisplayName("StructureDefinition #setInstancePropertyByPath should set a complex instance property on an existing array")
    void t12() {
      observation.setInstancePropertyByPath("jurisdiction[0]", fooCode, fisher);
      observation.setInstancePropertyByPath("jurisdiction[1]", barCode, fisher);
      assertEquals(2, observation.getJurisdiction().size());
      assertJsEqual(json("{\"coding\": [{\"code\": \"foo\", \"system\": \"http://example.com\"}]}"),
          observation.getJurisdiction().get(0));
      assertJsEqual(json("{\"coding\": [{\"code\": \"bar\", \"system\": \"http://example.com\"}]}"),
          observation.getJurisdiction().get(1));
    }

    @Test
    @DisplayName("StructureDefinition #setInstancePropertyByPath should set a child of a primitive instance property which has a value")
    void t13() {
      observation.setInstancePropertyByPath("version", "foo", fisher);
      observation.setInstancePropertyByPath("version.id", "bar", fisher);
      assertEquals("foo", observation.getVersion());
      assertEquals("bar", at(observation, "_version.id"));
    }

    @Test
    @DisplayName("StructureDefinition #setInstancePropertyByPath should set a child of a primitive instance property array which has a value")
    void t14() {
      observation.setInstancePropertyByPath("contextInvariant[0]", "foo", fisher);
      observation.setInstancePropertyByPath("contextInvariant[0].id", "bar", fisher);
      assertEquals(1, observation.getContextInvariant().size());
      assertEquals("foo", observation.getContextInvariant().get(0));
      assertEquals("bar", at(observation, "_contextInvariant[0].id"));
    }

    @Test
    @DisplayName("StructureDefinition #setInstancePropertyByPath should set a child of a primitive instance property array and null fill the array")
    void t15() {
      observation.setInstancePropertyByPath("contextInvariant[1]", "foo", fisher);
      observation.setInstancePropertyByPath("contextInvariant[1].id", "bar", fisher);
      assertEquals(2, observation.getContextInvariant().size());
      assertNull(observation.getContextInvariant().get(0));
      assertEquals("foo", observation.getContextInvariant().get(1));
      assertNull(((List<?>) observation.getProperty("_contextInvariant")).get(0));
      assertEquals("bar", at(observation, "_contextInvariant[1].id"));
    }

    @Test
    @DisplayName("StructureDefinition #setInstancePropertyByPath should throw an InvalidElementAccessError when trying to access the snapshot")
    void t16() {
      assertThrowsMessage("Cannot directly access differential or snapshot with path: snapshot.element[0]",
          () -> observation.setInstancePropertyByPath("snapshot.element[0]", "foo", fisher));
    }

    @Test
    @DisplayName("StructureDefinition #setInstancePropertyByPath should throw an InvalidElementAccessError when trying to access the differential")
    void t17() {
      assertThrowsMessage("Cannot directly access differential or snapshot with path: differential.element[0]",
          () -> observation.setInstancePropertyByPath("differential.element[0]", "foo", fisher));
    }

    @Test
    @DisplayName("StructureDefinition #setInstancePropertyByPath should throw an InvalidTypeAccessError when trying to override the Parent type with a caret value")
    void t18() {
      assertThrowsMessage(
          "Cannot directly change type. StructureDefinitions will naturally inherit their Parent type.",
          () -> observation.setInstancePropertyByPath("type", "foo", fisher));
    }
  }

  @Nested
  @DisplayName("#validateValueAtPath")
  class ValidateValueAtPath {
    StructureDefinition structureDefinition;
    StructureDefinition respRate;
    StructureDefinition CSSPC;
    StructureDefinition bundle;

    @BeforeEach
    void setUp() {
      structureDefinition = fisher.fishForStructureDefinition("StructureDefinition");
      respRate = fisher.fishForStructureDefinition("resprate");
      bundle = fisher.fishForStructureDefinition("Bundle");
      CSSPC = fisher.fishForStructureDefinition("capabilitystatement-search-parameter-combination");
    }

    ElementDefinition find(StructureDefinition sd, String id) {
      for (ElementDefinition e : sd.elements) if (id.equals(e.getId())) return e;
      return null;
    }

    @Test
    @DisplayName("StructureDefinition #validateValueAtPath should allow assigning an instance value")
    void t1() {
      var r = structureDefinition.validateValueAtPath("version", "4.0.2", fisher);
      assertEquals("4.0.2", r.assignedValue());
      assertEquals(1, r.pathParts().size());
      assertEquals(prim("version"), r.pathParts().get(0));
    }

    @Test
    @DisplayName("StructureDefinition #validateValueAtPath should allow assigning the same instance value over an existing pattern[x]")
    void t2() {
      ElementDefinition method = respRate.findElement("Observation.method");
      method.setProperty("patternCodeableConcept",
          json("{\"coding\": [{\"system\": \"http://system.com\", \"code\": \"foo\"}]}"));
      var r = respRate.validateValueAtPath("method", new FshCode("foo", "http://system.com"), fisher);
      assertJsEqual(json("{\"coding\": [{\"system\": \"http://system.com\", \"code\": \"foo\"}]}"), r.assignedValue());
      assertEquals(1, r.pathParts().size());
      assertEquals(part("method"), r.pathParts().get(0));
    }

    @Test
    @DisplayName("StructureDefinition #validateValueAtPath should allow assigning the same instance value over an existing fixed[x]")
    void t3() {
      ElementDefinition method = respRate.findElement("Observation.method");
      method.setProperty("fixedCodeableConcept",
          json("{\"coding\": [{\"system\": \"http://system.com\", \"code\": \"foo\"}]}"));
      var r = respRate.validateValueAtPath("method", new FshCode("foo", "http://system.com"), fisher);
      assertJsEqual(json("{\"coding\": [{\"system\": \"http://system.com\", \"code\": \"foo\"}]}"), r.assignedValue());
      assertEquals(1, r.pathParts().size());
      assertEquals(part("method"), r.pathParts().get(0));
    }

    @Test
    @DisplayName("StructureDefinition #validateValueAtPath should not allow assigning an instance value with an incorrect path")
    void t4() {
      assertThrowsMessage("The element or path you referenced does not exist: Version",
          () -> structureDefinition.validateValueAtPath("Version", "4.0.2", fisher));
    }

    @Test
    @DisplayName("StructureDefinition #validateValueAtPath should not allow assigning an instance value with an incorrect value")
    void t5() {
      assertThrowsMessage("Cannot assign boolean value: true. Value does not match element type: string",
          () -> structureDefinition.validateValueAtPath("version", true, fisher));
    }

    @Test
    @DisplayName("StructureDefinition #validateValueAtPath should not allow assigning an instance value with a 0 cardinality")
    void t6() {
      find(structureDefinition, "StructureDefinition.version").setMax("0");
      assertThrowsMessage("The element or path you referenced does not exist: version",
          () -> structureDefinition.validateValueAtPath("version", "4.0.2", fisher));
    }

    @Test
    @DisplayName("StructureDefinition #validateValueAtPath should allow assigning an instance value to an element in an array")
    void t7() {
      var r = structureDefinition.validateValueAtPath("identifier[0].value", "foo", fisher);
      assertEquals("foo", r.assignedValue());
      assertEquals(List.of(part("identifier").withBrackets("0"), prim("value")), r.pathParts());
    }

    @Test
    @DisplayName("StructureDefinition #validateValueAtPath should allow assigning an instance value to an element in an array, with implied 0 index")
    void t8() {
      var r = structureDefinition.validateValueAtPath("identifier.value", "foo", fisher);
      assertEquals("foo", r.assignedValue());
      assertEquals(List.of(part("identifier").withBrackets("0"), prim("value")), r.pathParts());
    }

    @Test
    @DisplayName("StructureDefinition #validateValueAtPath should allow assigning an instance value to an element in an array if the element was constrained from an array")
    void t9() {
      var r = respRate.validateValueAtPath("code.coding[RespRateCode].id", "foo", fisher);
      assertEquals("foo", r.assignedValue());
      assertEquals(List.of(part("code"), part("coding").withBrackets("RespRateCode", "0").withSlices("RespRateCode"),
          prim("id")), r.pathParts());
    }

    @Test
    @DisplayName("StructureDefinition #validateValueAtPath should not allow using array brackets when an element is not an array")
    void t10() {
      assertThrowsMessage("The element or path you referenced does not exist: version[0]",
          () -> structureDefinition.validateValueAtPath("version[0]", "foo", fisher));
    }

    @Test
    @DisplayName("StructureDefinition #validateValueAtPath should not add an instance property in an array with negative index")
    void t11() {
      assertThrowsMessage("The element or path you referenced does not exist: contact[-1].telecom[0].value",
          () -> structureDefinition.validateValueAtPath("contact[-1].telecom[0].value", "foo", fisher));
    }

    @Test
    @DisplayName("StructureDefinition #validateValueAtPath should not add an instance property in an array with too large an index")
    void t12() {
      find(structureDefinition, "StructureDefinition.contact").setMax("3");
      assertThrowsMessage("The element or path you referenced does not exist: contact[4].telecom[0].value",
          () -> structureDefinition.validateValueAtPath("contact[4].telecom[0].value", "foo", fisher));
    }

    @Test
    @DisplayName("StructureDefinition #validateValueAtPath should allow assigning an instance value on a slice")
    void t13() {
      var r = respRate.validateValueAtPath("category[VSCat].coding[0].version", "foo", fisher);
      assertEquals("foo", r.assignedValue());
      assertEquals(List.of(part("category").withBrackets("VSCat", "0").withSlices("VSCat"),
          part("coding").withBrackets("0").withSlices("VSCat"), prim("version")), r.pathParts());
    }

    @Test
    @DisplayName("StructureDefinition #validateValueAtPath should allow assigning an instance value on a slice array")
    void t14() {
      var r = CSSPC.validateValueAtPath("extension[required][3].value[x]", "foo", fisher);
      assertEquals("foo", r.assignedValue());
      assertEquals(List.of(part("extension").withBrackets("required", "3").withSlices("required"),
          prim("value[x]")), r.pathParts());
    }

    @Test
    @DisplayName("StructureDefinition #validateValueAtPath should allow setting values directly on extensions by accessing indexes")
    void t15() {
      var r = CSSPC.validateValueAtPath("extension[2].url", "foo", fisher);
      assertEquals("foo", r.assignedValue());
      assertEquals(List.of(part("extension").withBrackets("2"), prim("url")), r.pathParts());
    }

    @Test
    @DisplayName("StructureDefinition #validateValueAtPath should allow setting arbitrary defined extensions")
    void t16() {
      int originalLength = respRate.elements.size();
      var r = respRate.validateValueAtPath("extension[patient-mothersMaidenName].value[x]", "foo", fisher);
      assertEquals("foo", r.assignedValue());
      assertEquals(2, r.pathParts().size());
      assertEquals(part("extension").withBrackets("patient-mothersMaidenName", "0")
          .withSlices("patient-mothersMaidenName"), r.pathParts().get(0));
      assertEquals(originalLength + 5, respRate.elements.size());
    }

    @Test
    @DisplayName("StructureDefinition #validateValueAtPath should allow setting abritrary defined extensions by url")
    void t17() {
      int originalLength = respRate.elements.size();
      var r = respRate.validateValueAtPath(
          "extension[http://hl7.org/fhir/StructureDefinition/patient-mothersMaidenName].value[x]", "foo", fisher);
      assertEquals("foo", r.assignedValue());
      assertEquals(2, r.pathParts().size());
      assertEquals(part("extension").withBrackets("patient-mothersMaidenName", "0")
          .withSlices("patient-mothersMaidenName"), r.pathParts().get(0));
      assertEquals(originalLength + 5, respRate.elements.size());
    }

    @Test
    @DisplayName("StructureDefinition #validateValueAtPath should allow setting nested arbitrary defined extensions")
    void t18() {
      int originalLength = respRate.elements.size();
      var r = respRate.validateValueAtPath("extension[0].extension[patient-mothersMaidenName].value[x]", "foo",
          fisher);
      assertEquals("foo", r.assignedValue());
      assertEquals(3, r.pathParts().size());
      assertEquals(part("extension").withBrackets("patient-mothersMaidenName", "0")
          .withSlices("patient-mothersMaidenName"), r.pathParts().get(1));
      assertEquals(originalLength + 9, respRate.elements.size());
    }

    @Test
    @DisplayName("StructureDefinition #validateValueAtPath should not allow setting arbitrary undefined extensions")
    void t19() {
      assertThrowsMessage("The element or path you referenced does not exist: extension[fake-extension].value[x]",
          () -> respRate.validateValueAtPath("extension[fake-extension].value[x]", "foo", fisher));
    }

    @Test
    @DisplayName("StructureDefinition #validateValueAtPath should rename extensions when they are referred to by name instead of sliceName")
    void t20() {
      int originalLength = respRate.elements.size();
      ElementDefinition extension = respRate.findElementByPath("extension", fisher);
      extension.sliceIt("type", "$this", false, "open");
      ElementDefinition slice = extension.addSlice("maiden-name");
      slice.getType().get(0).setProfile(
          new ArrayList<>(List.of("http://hl7.org/fhir/StructureDefinition/patient-mothersMaidenName")));
      var r = respRate.validateValueAtPath("extension[patient-mothersMaidenName].valueString", "foo", fisher);
      assertEquals("foo", r.assignedValue());
      assertEquals(2, r.pathParts().size());
      assertEquals(part("extension").withBrackets("maiden-name", "0").withSlices("maiden-name"),
          r.pathParts().get(0));
      assertEquals(originalLength + 5, respRate.elements.size());
    }

    @Test
    @DisplayName("StructureDefinition #validateValueAtPath should match a version-pinned extension slice via its unversioned canonical URL without warning")
    void t21() {
      String mmnUrl = "http://hl7.org/fhir/StructureDefinition/patient-mothersMaidenName";
      ElementDefinition extension = respRate.findElementByPath("extension", fisher);
      extension.sliceIt("type", "$this", false, "open");
      ElementDefinition slice = extension.addSlice("maiden-name");
      slice.getType().get(0).setProfile(new ArrayList<>(List.of(mmnUrl + "|9.9.9")));
      loggerSpy.reset();
      ElementDefinition matched = respRate.findElementByPath("extension[" + mmnUrl + "]", fisher);
      assertNotNull(matched);
      assertEquals("maiden-name", matched.getSliceName());
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinition #validateValueAtPath should warn when a path requests a version that differs from the version pinned on the matched slice")
    void t22() {
      String mmnUrl = "http://hl7.org/fhir/StructureDefinition/patient-mothersMaidenName";
      Object mmn = fisher.fishForFHIR(mmnUrl, FishableType.Extension);
      Object version = Common.getProp(mmn, "version");
      assertNotNull(version);
      ElementDefinition extension = respRate.findElementByPath("extension", fisher);
      extension.sliceIt("type", "$this", false, "open");
      ElementDefinition slice = extension.addSlice("maiden-name");
      slice.getType().get(0).setProfile(new ArrayList<>(List.of(mmnUrl + "|9.9.9")));
      loggerSpy.reset();
      ElementDefinition matched = respRate.findElementByPath("extension[" + mmnUrl + "|" + version + "]", fisher);
      assertNotNull(matched);
      assertEquals("maiden-name", matched.getSliceName());
      assertMatches("Extension slice maiden-name on Observation\\.extension pins version 9\\.9\\.9 of " + mmnUrl
          + ", but version " + version + " was requested\\.", loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("StructureDefinition #validateValueAtPath should not warn when the requested version matches the version pinned on the matched slice")
    void t23() {
      String mmnUrl = "http://hl7.org/fhir/StructureDefinition/patient-mothersMaidenName";
      Object mmn = fisher.fishForFHIR(mmnUrl, FishableType.Extension);
      Object version = Common.getProp(mmn, "version");
      ElementDefinition extension = respRate.findElementByPath("extension", fisher);
      extension.sliceIt("type", "$this", false, "open");
      ElementDefinition slice = extension.addSlice("maiden-name");
      slice.getType().get(0).setProfile(new ArrayList<>(List.of(mmnUrl + "|" + version)));
      loggerSpy.reset();
      ElementDefinition matched = respRate.findElementByPath("extension[" + mmnUrl + "|" + version + "]", fisher);
      assertNotNull(matched);
      assertEquals("maiden-name", matched.getSliceName());
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
    }

    @Test
    @DisplayName("StructureDefinition #validateValueAtPath should rename modifierExtensions when they are referred to by name instead of sliceName")
    void t24() {
      int originalLength = respRate.elements.size();
      ElementDefinition modExtension = respRate.findElementByPath("modifierExtension", fisher);
      modExtension.sliceIt("type", "$this", false, "open");
      ElementDefinition slice = modExtension.addSlice("maiden-name");
      slice.getType().get(0).setProfile(
          new ArrayList<>(List.of("http://hl7.org/fhir/StructureDefinition/patient-mothersMaidenName")));
      var r = respRate.validateValueAtPath("modifierExtension[patient-mothersMaidenName].valueString", "foo",
          fisher);
      assertEquals("foo", r.assignedValue());
      assertEquals(2, r.pathParts().size());
      assertEquals(part("modifierExtension").withBrackets("maiden-name", "0").withSlices("maiden-name"),
          r.pathParts().get(0));
      assertEquals(originalLength + 5, respRate.elements.size());
    }
  }
}
