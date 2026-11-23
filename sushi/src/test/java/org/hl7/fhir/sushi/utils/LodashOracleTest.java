package org.hl7.fhir.sushi.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.common.js.Js;
import org.hl7.fhir.common.js.Json;
import org.hl7.fhir.sushi.utils.ValidUrl;
import org.junit.jupiter.api.Test;

/** Oracle tests (contributed by WP-7) for Js.merge/mergeWith/isMatch and utils.ValidUrl (vectors: scripts/oracle/wp7-vectors.js). */
class LodashOracleTest {
  @SuppressWarnings("unchecked")
  static Map<String, Object> vectors() throws IOException {
    try (InputStream in = LodashOracleTest.class.getResourceAsStream("/wp7/vectors.json")) {
      return (Map<String, Object>) Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    }
  }

  @SuppressWarnings("unchecked")
  @Test void isUriMatchesValidUrl() throws IOException {
    for (Object o : (List<Object>) vectors().get("uriCases")) {
      List<Object> c = (List<Object>) o;
      assertEquals(c.get(1), ValidUrl.isUriValue((String) c.get(0)), "isUri(" + c.get(0) + ")");
    }
  }

  @SuppressWarnings("unchecked")
  @Test void mergeMatchesLodash() throws IOException {
    for (Object o : (List<Object>) vectors().get("mergeCases")) {
      List<Object> c = (List<Object>) o;
      Object result = Js.merge(c.get(0), c.get(1));
      assertEquals(Json.stringify(c.get(2)), Json.stringify(result), "merge " + Json.stringify(c.get(1)));
    }
  }

  /** The customizer InstanceExporter uses to merge duplicate meta.profile entries. */
  static Object metaCustomizer(Object a, Object b, String key) {
    if (a instanceof List<?> x && b instanceof List<?> y) {
      List<Object> r = new java.util.ArrayList<>(x);
      r.addAll(y);
      return r;
    } else if (!(a == null || a instanceof Map || a instanceof List) || !(b == null || b instanceof Map
        || b instanceof List)) {
      return a != null && a != Js.UNDEFINED ? a : b;
    }
    return Js.UNDEFINED;
  }

  @SuppressWarnings("unchecked")
  @Test void mergeWithMatchesLodash() throws IOException {
    for (Object o : (List<Object>) vectors().get("mergeWithCases")) {
      List<Object> c = (List<Object>) o;
      Object result = Js.mergeWith(c.get(0), c.get(1), LodashOracleTest::metaCustomizer);
      assertEquals(Json.stringify(c.get(2)), Json.stringify(result), "mergeWith " + Json.stringify(c.get(1)));
    }
  }

  @SuppressWarnings("unchecked")
  @Test void isMatchMatchesLodash() throws IOException {
    for (Object o : (List<Object>) vectors().get("matchCases")) {
      List<Object> c = (List<Object>) o;
      assertEquals(c.get(2), Js.isMatch(c.get(0), c.get(1)),
          "isMatch(" + Json.stringify(c.get(0)) + ", " + Json.stringify(c.get(1)) + ")");
    }
  }
}
