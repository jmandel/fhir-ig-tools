package org.hl7.fhir.sushi.fhirtypes;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.js.Js;
import org.hl7.fhir.sushi.js.JsObject;
import org.hl7.fhir.sushi.js.Json;
import org.hl7.fhir.sushi.js.JsonSerializable;
import org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.function.Executable;

/** Shared helpers for the ElementDefinition test ports (WP-4, test scope). */
final class EdTestSupport {
  private EdTestSupport() {}

  private static TestFHIRDefinitions r4;
  private static TestFHIRDefinitions r5;

  /**
   * TS {@code getTestFHIRDefinitions(true, testDefsPath('r4-definitions'))}, loaded once per JVM (the definitions are
   * frozen in TS, so sharing them between test classes is equivalent). Skips the test when the fixtures are absent.
   */
  static synchronized TestFHIRDefinitions r4Defs() {
    Assumptions.assumeTrue(TestFHIRDefinitions.tsFixturesAvailable(), "SUSHI TS fixtures not available");
    if (r4 == null) {
      r4 = TestFHIRDefinitions.getTestFHIRDefinitions(true, TestFHIRDefinitions.testDefsPath("r4-definitions"));
    }
    return r4;
  }

  /** TS {@code getTestFHIRDefinitions(false, testDefsPath('r5-definitions'))}. */
  static synchronized TestFHIRDefinitions r5Defs() {
    Assumptions.assumeTrue(TestFHIRDefinitions.tsFixturesAvailable(), "SUSHI TS fixtures not available");
    if (r5 == null) {
      r5 = TestFHIRDefinitions.getTestFHIRDefinitions(false, TestFHIRDefinitions.testDefsPath("r5-definitions"));
    }
    return r5;
  }

  /** TS {@code sd.elements.find(e => e.id === id)}. */
  static ElementDefinition el(StructureDefinition sd, String id) {
    for (ElementDefinition e : sd.elements) if (e.getId().equals(id)) return e;
    return null;
  }

  /** Jest {@code expect(fn).toThrow(/re/)}: throws, and the message matches (find) the JS regex. */
  static RuntimeException assertThrowsMatching(String jsRegex, Executable fn) {
    RuntimeException e = assertThrows(RuntimeException.class, fn);
    assertTrue(e.getMessage() != null && Pattern.compile(jsRegex).matcher(e.getMessage()).find(),
        () -> "expected message matching /" + jsRegex + "/ but was: " + e.getMessage());
    return e;
  }

  /** Jest {@code toThrow("text")}: the message contains the text. */
  static RuntimeException assertThrowsContaining(String text, Executable fn) {
    RuntimeException e = assertThrows(RuntimeException.class, fn);
    assertTrue(e.getMessage() != null && e.getMessage().contains(text),
        () -> "expected message containing '" + text + "' but was: " + e.getMessage());
    return e;
  }

  /** JSON text to the value model. */
  static Object json(String text) {
    return Json.parse(text);
  }

  /** Jest toEqual over the value model (undefined members ignored, key order ignored, classes via toJSON). */
  static void assertJsEqual(Object expected, Object actual) {
    if (!Js.isEqual(normalize(expected), normalize(actual))) {
      fail("expected: " + show(expected) + "\n but was: " + show(actual));
    }
  }

  static void assertJsonEqual(String expectedJson, Object actual) {
    assertJsEqual(json(expectedJson), actual);
  }

  private static Object normalize(Object v) {
    if (v == null || v == Js.UNDEFINED) return null;
    if (v instanceof JsonSerializable || v instanceof Map<?, ?> || v instanceof List<?>) {
      String s = Json.stringify(v);
      return s == null ? null : Json.parse(s);
    }
    return v;
  }

  private static String show(Object v) {
    try {
      return Json.stringify(v, 2);
    } catch (RuntimeException e) {
      return String.valueOf(v);
    }
  }

  /**
   * The state Jest's {@code toEqual(omit(ed, ['structDef', 'treeParent', 'treeChildren']))} compares: the own
   * properties of the element (id, FHIR properties, and the stored original, recursively).
   */
  static Object edState(ElementDefinition ed) {
    if (ed == null) return null;
    JsObject s = new JsObject();
    s.put("_privateId", ed.getId());
    for (String k : ed.propertyKeys()) {
      Object v = ed.rawProperties().get(k);
      s.put(k, v instanceof List<?> l ? typesState(l) : v);
    }
    if (ed.getOriginal() != null) s.put("_original", edState(ed.getOriginal()));
    return normalizeState(s);
  }

  private static Object typesState(List<?> l) {
    List<Object> out = new ArrayList<>();
    for (Object o : l) out.add(o instanceof ElementDefinitionType t ? t.rawProperties() : o);
    return out;
  }

  private static Object normalizeState(Object s) {
    return Json.parse(Json.stringify(s));
  }

  static void assertSameState(ElementDefinition expected, ElementDefinition actual) {
    assertJsEqual(edState(expected), edState(actual));
  }

  /** {@code new ElementDefinitionType(code)} list, for toEqual comparisons of ED.type. */
  static List<ElementDefinitionType> types(String... codes) {
    List<ElementDefinitionType> l = new ArrayList<>();
    for (String c : codes) l.add(new ElementDefinitionType(c));
    return l;
  }
}
