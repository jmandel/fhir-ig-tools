package org.hl7.fhir.sushi.fhirdefs.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.common.js.Json;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * fhir-tool 5.0.2 xmlToObj port vs the JS library: a committed sample (corpus predefined XML resources + mutations)
 * of the full differential (scripts/fhirtool/diff.sh: 798 corpus files + 13,331 mutations, 0 diffs).
 */
class FhirXmlToJsTest {
  @Test
  @DisplayName("xmlToObj matches fhir-tool 5.0.2 on the committed vectors")
  @SuppressWarnings("unchecked")
  void vectors() throws Exception {
    List<Object> vectors;
    try (InputStream in = getClass().getResourceAsStream("/fhirdefs/xml/vectors.json")) {
      vectors = (List<Object>) Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    }
    List<String> failures = new ArrayList<>();
    for (Object o : vectors) {
      Map<String, Object> v = (Map<String, Object>) o;
      String res;
      try {
        Object r = FhirXmlToJs.xmlToObj((String) v.get("xml"));
        res = r == null ? "UNDEFINED" : Json.stringify(r);
      } catch (RuntimeException e) {
        res = "ERR:" + e.getMessage();
      }
      if (!res.equals(v.get("out"))) failures.add(v.get("id") + "\n  JS:   " + v.get("out") + "\n  Java: " + res);
    }
    assertEquals(List.of(), failures, failures.size() + " of " + vectors.size() + " differ");
  }

  @Test
  @DisplayName("unknown resource types fail with the message FPL skips silently")
  void unknownType() {
    RuntimeException e = org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
        () -> FhirXmlToJs.xmlToObj("<MolecularDefinition xmlns=\"http://hl7.org/fhir\"><id value=\"x\"/></MolecularDefinition>"));
    assertEquals("Unknown resource type: MolecularDefinition", e.getMessage());
  }
}
