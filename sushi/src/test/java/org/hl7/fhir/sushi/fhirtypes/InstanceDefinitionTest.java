package org.hl7.fhir.sushi.fhirtypes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.hl7.fhir.sushi.js.Js;
import org.hl7.fhir.sushi.js.JsObject;
import org.hl7.fhir.sushi.js.Json;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// port of sushi/test/fhirtypes/InstanceDefinition.test.ts (SUSHI 3.21.1)
@DisplayName("InstanceDefinition")
class InstanceDefinitionTest {
  @SuppressWarnings("unchecked")
  static final Map<String, Object> patientInstanceJSON = (Map<String, Object>) Json.parse(
      "{\"resourceType\": \"Patient\", \"id\": \"example\", \"name\": [{\"family\": \"Shaw\", \"given\": [\"Amy\", \"V.\"]}]}");
  InstanceDefinition patientInstance;

  @BeforeEach
  void setUp() {
    patientInstance = InstanceDefinition.fromJSON(patientInstanceJSON);
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> firstName(Map<String, Object> o) {
    return (Map<String, Object>) ((List<Object>) o.get("name")).get(0);
  }

  @Nested
  @DisplayName("#fromJSON")
  class FromJSON {
    @Test
    @DisplayName("InstanceDefinition #fromJSON should load an instance properly")
    void load() {
      assertEquals("Patient", patientInstance.getResourceType());
      assertEquals("example", patientInstance.getId());
      assertTrue(Js.isEqual(Json.parse("[{\"family\": \"Shaw\", \"given\": [\"Amy\", \"V.\"]}]"),
          patientInstance.get("name")));
    }
  }

  @Nested
  @DisplayName("#toJSON")
  class ToJSON {
    @Test
    @DisplayName("InstanceDefinition #toJSON should round trip back to the original JSON")
    void roundTrip() {
      patientInstance._instanceMeta.name = "TestName";
      Object newJSON = patientInstance.toJSON();
      assertTrue(Js.isEqual(patientInstanceJSON, newJSON));
    }

    @Test
    @DisplayName("InstanceDefinition #toJSON should serialize properties such that underscore properties come immediately after their non-underscore counterparts")
    @SuppressWarnings("unchecked")
    void underscores() {
      Object ext = Json.parse("{\"extension\": [{\"url\": \"SomeExtension\", \"valueCode\": \"someCode\"}]}");
      patientInstance.put("_status", ext);
      patientInstance.put("_id", Js.cloneDeep(ext));
      patientInstance.put("status", "active");
      List<String> keys = Js.ownKeys((Map<String, Object>) patientInstance.toJSON());
      assertEquals(keys.indexOf("id") + 1, keys.indexOf("_id"));
      assertEquals(keys.indexOf("status") + 1, keys.indexOf("_status"));
    }

    @Test
    @DisplayName("InstanceDefinition #toJSON should serialize subproperties such that underscore properties come immediately after their non-underscore counterparts")
    @SuppressWarnings("unchecked")
    void subUnderscores() {
      firstName(patientInstance).put("_family", Json.parse(
          "{\"extension\": {\"url\": \"http://question.org/wait/really?\", \"valueBoolean\": false}}"));
      List<String> originalKeys = Js.ownKeys(firstName(patientInstance));
      assertNotEquals(originalKeys.indexOf("family") + 1, originalKeys.indexOf("_family"));
      List<String> nameKeys = Js.ownKeys(firstName((Map<String, Object>) patientInstance.toJSON()));
      assertEquals(nameKeys.indexOf("family") + 1, nameKeys.indexOf("_family"));
    }
  }
}
