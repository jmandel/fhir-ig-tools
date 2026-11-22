package org.hl7.fhir.sushi.testhelpers;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fhirtypes.Common;
import org.hl7.fhir.sushi.js.Js;
import org.hl7.fhir.sushi.js.Json;
import org.hl7.fhir.sushi.js.JsonSerializable;

/**
 * Jest-matcher equivalents over the JS value model, so ported tests read like the TS and do not depend on typed
 * accessor names: {@code at(ed, "patternCodeableConcept.coding[0].code")} reads a property path through
 * Common.getProp (Maps, Lists, PropertyHolders such as StructureDefinition / ElementDefinition), {@code json("...")}
 * builds an expected value from JSON text (numbers become Double), {@code assertJsEqual} is toEqual (lodash-style deep
 * equality, key order ignored), {@code assertMatches} is toMatch with a JS regex.
 */
public final class JsAssert {
  private JsAssert() {}

  private static final Pattern SEGMENT = Pattern.compile("([^.\\[\\]]+)|\\[(\\d+)\\]");

  /** TS {@code obj.a.b[0].c} (null as soon as a step is null/undefined, like optional chaining). */
  public static Object at(Object obj, String path) {
    var m = SEGMENT.matcher(path);
    Object cur = obj;
    while (m.find()) {
      if (cur == null) return null;
      if (m.group(1) != null) {
        cur = Common.getProp(cur, m.group(1));
      } else {
        int i = Integer.parseInt(m.group(2));
        if (cur instanceof List<?> l) {
          cur = i < l.size() ? l.get(i) : null;
        } else {
          cur = Common.getProp(cur, m.group(2));
        }
      }
    }
    return cur == Js.UNDEFINED ? null : cur;
  }

  /** JSON text -> JS value model (JsObject / List / Double / String / Boolean / null). */
  public static Object json(String text) {
    return Json.parse(text);
  }

  /** Jest toEqual: deep equality, key order ignored; class instances compared through toJSON. */
  public static void assertJsEqual(Object expected, Object actual) {
    if (!Js.isEqual(normalize(expected), normalize(actual))) {
      fail("expected: " + show(expected) + "\n but was: " + show(actual));
    }
  }

  /** Jest toEqual for a JSON-text expectation. */
  public static void assertJsonEqual(String expectedJson, Object actual) {
    assertJsEqual(json(expectedJson), actual);
  }

  private static Object normalize(Object v) {
    if (v instanceof JsonSerializable s) return Json.parse(Json.stringify(s.toJSON()));
    if (v instanceof Map<?, ?> || v instanceof List<?>) {
      try {
        return Json.parse(Json.stringify(v));
      } catch (RuntimeException e) {
        return v;
      }
    }
    return v;
  }

  private static String show(Object v) {
    try {
      return Json.stringify(v instanceof JsonSerializable s ? s.toJSON() : v, 2);
    } catch (RuntimeException e) {
      return String.valueOf(v);
    }
  }

  /** Jest {@code toMatch("substring")}: the string contains the substring. */
  public static void assertContainsText(String expectedSubstring, String actual) {
    assertTrue(actual != null && actual.contains(expectedSubstring),
        () -> "expected to contain: " + expectedSubstring + "\n but was: " + actual);
  }

  /**
   * Jest {@code toMatch(/regex/flags)}: find() of a JS regex. flags: "s" = DOTALL, "i" = CASE_INSENSITIVE, "m" =
   * MULTILINE. (Write the regex as in TS; JS and Java syntax agree for the patterns SUSHI tests use.)
   */
  public static void assertMatches(String jsRegex, String flags, String actual) {
    int f = 0;
    if (flags.contains("s")) f |= Pattern.DOTALL;
    if (flags.contains("i")) f |= Pattern.CASE_INSENSITIVE;
    if (flags.contains("m")) f |= Pattern.MULTILINE;
    Pattern p = Pattern.compile(jsRegex, f);
    assertTrue(actual != null && p.matcher(actual).find(), () -> "expected to match /" + jsRegex + "/" + flags
        + "\n but was: " + actual);
  }

  public static void assertMatches(String jsRegex, String actual) {
    assertMatches(jsRegex, "", actual);
  }
}
