package org.hl7.fhir.sushi.fhirtypes;

import static org.hl7.fhir.sushi.fhirtypes.EdTestSupport.assertJsEqual;
import static org.hl7.fhir.sushi.fhirtypes.EdTestSupport.assertJsonEqual;
import static org.hl7.fhir.sushi.fhirtypes.EdTestSupport.assertThrowsContaining;
import static org.hl7.fhir.sushi.fhirtypes.EdTestSupport.el;
import static org.hl7.fhir.sushi.fhirtypes.EdTestSupport.list;
import static org.hl7.fhir.sushi.fhirtypes.EdTestSupport.obj;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import org.hl7.fhir.sushi.fshtypes.FshCode;
import org.hl7.fhir.sushi.fshtypes.FshQuantity;
import org.hl7.fhir.sushi.testhelpers.TestFisher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// port of sushi/test/fhirtypes/ElementDefinition.setInstancePropertyByPath.test.ts (SUSHI 3.21.1)
@DisplayName("ElementDefinition")
class ElementDefinitionSetInstancePropertyByPathTest {
  static TestFisher fisher;
  StructureDefinition observation;
  ElementDefinition status;

  @BeforeAll
  static void beforeAll() {
    fisher = new TestFisher().withFHIR(EdTestSupport.r4Defs());
  }

  @BeforeEach
  void beforeEach() {
    observation = fisher.fishForStructureDefinition("Observation");
    status = el(observation, "Observation.status");
  }

  /** TS {@code ed.type} as a plain array (elements may be plain objects added by path setting). */
  static List<?> rawTypes(ElementDefinition ed) {
    return (List<?>) ed.getProperty("type");
  }

  static List<?> listProp(ElementDefinition ed, String key) {
    return (List<?>) ed.getProperty(key);
  }

  @Nested
  @DisplayName("#setInstancePropertyByPath")
  class SetInstancePropertyByPath {
    FshCode fooCode;
    FshCode barCode;

    @BeforeEach
    void beforeEach() {
      fooCode = new FshCode("foo", "http://example.com");
      barCode = new FshCode("bar", "http://example.com");
    }

    // Simple values
    @Test
    @DisplayName("ElementDefinition #setInstancePropertyByPath should set an instance property which has a value")
    void existingValue() {
      status.setInstancePropertyByPath("short", "foo", fisher);
      assertEquals("foo", status.getShort());
    }

    @Test
    @DisplayName("ElementDefinition #setInstancePropertyByPath should set an instance property which must be created")
    void created() {
      status.setInstancePropertyByPath("label", "foo", fisher);
      assertEquals("foo", status.getProperty("label"));
    }

    @Test
    @DisplayName("ElementDefinition #setInstancePropertyByPath should set a nested instance property which must be created")
    void nestedCreated() {
      // This isn't how you would normally do this, but it's useful for testing this part of the logic
      ElementDefinition valueX = el(observation, "Observation.value[x]");
      valueX.setInstancePropertyByPath("patternQuantity.value", 1.2, fisher);
      assertEquals(1.2, ((Map<?, ?>) valueX.getProperty("patternQuantity")).get("value"));
    }

    @Test
    @DisplayName("ElementDefinition #setInstancePropertyByPath should set a Quantity on a Quantity specialization instance property (e.g., Age)")
    void quantitySpecialization() {
      // This isn't how you would normally do this, but it's useful for testing this part of the logic
      ElementDefinition valueX = el(observation, "Observation.value[x]");
      valueX.setInstancePropertyByPath("patternAge",
          new FshQuantity(42.0, new FshCode("a", "http://unitsofmeasure.org")), fisher);
      assertJsonEqual("{\"value\": 42.0, \"code\": \"a\", \"system\": \"http://unitsofmeasure.org\"}",
          valueX.getProperty("patternAge"));
    }

    @Test
    @DisplayName("ElementDefinition #setInstancePropertyByPath should not set an instance property which is being assigned incorrectly")
    void incorrect() {
      assertThrowsContaining("Cannot assign number value: 1.2. Value does not match element type: string",
          () -> status.setInstancePropertyByPath("short", 1.2, fisher));
      assertEquals("registered | preliminary | final | amended +", status.getShort());
    }

    // Simple values in an array
    @Test
    @DisplayName("ElementDefinition #setInstancePropertyByPath should add an instance property at the end of an array")
    void arrayEnd() {
      status.setInstancePropertyByPath("type[1].code", "foo", fisher);
      assertEquals(2, rawTypes(status).size());
      assertJsEqual(obj("code", "foo"), rawTypes(status).get(1));
    }

    @Test
    @DisplayName("ElementDefinition #setInstancePropertyByPath should add an instance property in an array that must be empty filled")
    void arrayEmptyFill() {
      status.setInstancePropertyByPath("type[2].code", "foo", fisher);
      assertEquals(3, rawTypes(status).size());
      assertJsEqual(obj("code", "foo"), rawTypes(status).get(2));
      assertNull(rawTypes(status).get(1));
    }

    @Test
    @DisplayName("ElementDefinition #setInstancePropertyByPath should add an instance property in an array that has been empty filled")
    void arrayFilled() {
      status.setInstancePropertyByPath("type[2].code", "foo", fisher);
      status.setInstancePropertyByPath("type[1].code", "bar", fisher);
      assertEquals(3, rawTypes(status).size());
      assertJsEqual(obj("code", "foo"), rawTypes(status).get(2));
      assertJsEqual(obj("code", "bar"), rawTypes(status).get(1));
    }

    @Test
    @DisplayName("ElementDefinition #setInstancePropertyByPath should change an instance property in an array")
    void arrayChange() {
      status.setInstancePropertyByPath("type[0].code", "foo", fisher);
      assertEquals(1, status.getType().size());
      assertJsEqual(new ElementDefinitionType("foo"), status.getType().get(0));
    }

    @Test
    @DisplayName("ElementDefinition #setInstancePropertyByPath should change a part of an instance property in an array")
    void arrayPart() {
      status.setInstancePropertyByPath("type[0].profile[0]", "foo", fisher);
      assertEquals(1, status.getType().size());
      // setting an element in the profile array creates an empty element at the corresponding index in the _profile array
      ElementDefinitionType expectedType = new ElementDefinitionType("code").withProfiles("foo");
      expectedType.set_profile(list(obj()));
      assertJsEqual(expectedType, status.getType().get(0));
    }

    // Complex values
    @Test
    @DisplayName("ElementDefinition #setInstancePropertyByPath should set a complex instance property on a newly created array")
    void complexNewArray() {
      status.setInstancePropertyByPath("code[0]", fooCode, fisher);
      assertEquals(1, listProp(status, "code").size());
      assertJsEqual(obj("code", "foo", "system", "http://example.com"), listProp(status, "code").get(0));
    }

    @Test
    @DisplayName("ElementDefinition #setInstancePropertyByPath should set a complex instance property on a newly created array, with implied 0 index")
    void complexImpliedIndex() {
      status.setInstancePropertyByPath("code", fooCode, fisher);
      assertEquals(1, listProp(status, "code").size());
      assertJsEqual(obj("code", "foo", "system", "http://example.com"), listProp(status, "code").get(0));
    }

    @Test
    @DisplayName("ElementDefinition #setInstancePropertyByPath should set a complex instance property over a value that already exists")
    void complexOverExisting() {
      status.setInstancePropertyByPath("code[0]", fooCode, fisher);
      status.setInstancePropertyByPath("code[0]", barCode, fisher);
      assertEquals(1, listProp(status, "code").size());
      assertJsEqual(obj("code", "bar", "system", "http://example.com"), listProp(status, "code").get(0));
    }

    @Test
    @DisplayName("ElementDefinition #setInstancePropertyByPath should set a complex instance property on an existing array")
    void complexExistingArray() {
      status.setInstancePropertyByPath("code[0]", fooCode, fisher);
      status.setInstancePropertyByPath("code[1]", barCode, fisher);
      assertEquals(2, listProp(status, "code").size());
      assertJsEqual(obj("code", "foo", "system", "http://example.com"), listProp(status, "code").get(0));
      assertJsEqual(obj("code", "bar", "system", "http://example.com"), listProp(status, "code").get(1));
    }

    // Children of primitives
    @Test
    @DisplayName("ElementDefinition #setInstancePropertyByPath should set a child of a primitive instance property which has a value")
    void primitiveChild() {
      status.setInstancePropertyByPath("short", "foo", fisher);
      status.setInstancePropertyByPath("short.id", "bar", fisher);
      assertEquals("foo", status.getShort());
      assertEquals("bar", ((Map<?, ?>) status.getProperty("_short")).get("id"));
    }

    @Test
    @DisplayName("ElementDefinition #setInstancePropertyByPath should set a child of a primitive instance property array which has a value")
    void primitiveArrayChild() {
      status.setInstancePropertyByPath("alias[0]", "foo", fisher);
      status.setInstancePropertyByPath("alias[0].id", "bar", fisher);
      assertEquals(1, listProp(status, "alias").size());
      assertEquals("foo", listProp(status, "alias").get(0));
      assertEquals("bar", ((Map<?, ?>) listProp(status, "_alias").get(0)).get("id"));
    }

    @Test
    @DisplayName("ElementDefinition #setInstancePropertyByPath should set a child of a primitive instance property array and null fill the array")
    void primitiveArrayNullFill() {
      status.setInstancePropertyByPath("alias[1]", "foo", fisher);
      status.setInstancePropertyByPath("alias[1].id", "bar", fisher);
      assertEquals(2, listProp(status, "alias").size());
      assertNull(listProp(status, "alias").get(0));
      assertEquals("foo", listProp(status, "alias").get(1));
      assertNull(listProp(status, "_alias").get(0));
      assertEquals("bar", ((Map<?, ?>) listProp(status, "_alias").get(1)).get("id"));
    }
  }
}
