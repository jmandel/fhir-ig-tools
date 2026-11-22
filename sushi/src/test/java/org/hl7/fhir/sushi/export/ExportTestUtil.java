package org.hl7.fhir.sushi.export;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fhirtypes.Common;
import org.hl7.fhir.sushi.fshtypes.TextLocation;
import org.hl7.fhir.sushi.js.Js;
import org.hl7.fhir.sushi.js.JsObject;
import org.hl7.fhir.sushi.js.Json;
import org.hl7.fhir.sushi.js.JsonSerializable;
import org.hl7.fhir.sushi.testhelpers.JsAssert;

/** Jest-matcher helpers for the WP-7 exporter tests, beyond testhelpers.JsAssert. */
final class ExportTestUtil {
  private ExportTestUtil() {}

  /** TS {@code x.length} on a value-model array or string. */
  static int len(Object v) {
    if (v instanceof List<?> l) return l.size();
    if (v instanceof String s) return s.length();
    throw new AssertionError("no length: " + v);
  }

  static void assertNotMatches(String jsRegex, String flags, String actual) {
    int f = 0;
    if (flags.contains("s")) f |= Pattern.DOTALL;
    if (flags.contains("i")) f |= Pattern.CASE_INSENSITIVE;
    if (flags.contains("m")) f |= Pattern.MULTILINE;
    Pattern p = Pattern.compile(jsRegex, f);
    assertFalse(actual != null && p.matcher(actual).find(), () -> "expected not to match /" + jsRegex + "/\n but was: "
        + actual);
  }

  /** TS {@code rule.from = { system, valueSets }} (null = absent). */
  @SuppressWarnings("unchecked")
  static org.hl7.fhir.sushi.fshtypes.ValueSetComponentFrom fromOf(String system, List<?> valueSets) {
    org.hl7.fhir.sushi.fshtypes.ValueSetComponentFrom from = new org.hl7.fhir.sushi.fshtypes.ValueSetComponentFrom();
    from.system = system;
    from.valueSets = valueSets == null ? null : new java.util.ArrayList<>((List<String>) valueSets);
    return from;
  }

  /** TS {@code new RegExp(source, flags)}. */
  static Pattern jsRegex(String source, String flags) {
    int f = 0;
    if (flags.contains("s")) f |= Pattern.DOTALL;
    if (flags.contains("i")) f |= Pattern.CASE_INSENSITIVE;
    if (flags.contains("m")) f |= Pattern.MULTILINE;
    return Pattern.compile(source, f);
  }

  static void assertMatchesPattern(Pattern p, String actual) {
    assertTrue(actual != null && p.matcher(actual).find(), () -> "expected to match /" + p.pattern() + "/\n but was: "
        + actual);
  }

  static void assertNotMatchesPattern(Pattern p, String actual) {
    assertFalse(actual != null && p.matcher(actual).find(), () -> "expected not to match /" + p.pattern()
        + "/\n but was: " + actual);
  }

  static void assertNotContainsText(String s, String actual) {
    assertFalse(actual != null && actual.contains(s), () -> "expected not to contain: " + s + "\n but was: " + actual);
  }

  /** Jest toContain: array membership (===) or substring. */
  static void assertContains(Object expected, Object actual) {
    if (actual instanceof String s) {
      assertTrue(s.contains((String) expected), () -> "expected to contain " + expected + ": " + s);
    } else {
      assertTrue(((List<?>) actual).stream().anyMatch(e -> Js.isEqual(e, expected) && !(e instanceof Map)
          || e == expected), () -> "expected to contain " + expected + ": " + actual);
    }
  }

  static void assertNotContains(Object expected, Object actual) {
    if (actual instanceof String s) {
      assertFalse(s.contains((String) expected));
    } else {
      assertFalse(((List<?>) actual).stream().anyMatch(e -> e == expected || Js.isEqual(e, expected)
          && !(e instanceof Map)));
    }
  }

  /** Jest toContainEqual. */
  static void assertContainsEqual(Object expected, Object actual) {
    Object e = norm(expected);
    assertTrue(((List<?>) actual).stream().anyMatch(x -> Js.isEqual(e, norm(x))),
        () -> "expected to contain equal " + show(expected) + ": " + show(actual));
  }

  static void assertNotContainsEqual(Object expected, Object actual) {
    Object e = norm(expected);
    assertFalse(((List<?>) actual).stream().anyMatch(x -> Js.isEqual(e, norm(x))));
  }

  static void assertNotJsEqual(Object expected, Object actual) {
    assertFalse(Js.isEqual(norm(expected), norm(actual)), () -> "expected not equal: " + show(actual));
  }

  static void assertNotJsonEqual(String expectedJson, Object actual) {
    assertNotJsEqual(JsAssert.json(expectedJson), actual);
  }

  private static Object norm(Object v) {
    if (v instanceof JsonSerializable s) v = s.toJSON();
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
      return Json.stringify(norm(v));
    } catch (RuntimeException e) {
      return String.valueOf(v);
    }
  }

  /** TS {@code obj.key = value} on a value-model object or PropertyHolder. */
  static void setProp(Object obj, String key, Object value) {
    Common.setProp(obj, key, value);
  }

  /** TS {@code delete obj.key}. */
  static void deleteProp(Object obj, String key) {
    Common.deleteProp(obj, key);
  }

  /** A Package.fshMap entry as the TS object ({...sourceInfo, fshName, fshType}), for toEqual comparisons. */
  static Object fshMapJson(Package.FshMapEntry e) {
    if (e == null) return null;
    JsObject o = new JsObject();
    if (e.sourceInfo().file != null) o.put("file", e.sourceInfo().file);
    TextLocation l = e.sourceInfo().location;
    if (l != null) {
      JsObject loc = new JsObject();
      loc.put("startLine", (double) l.startLine);
      loc.put("startColumn", (double) l.startColumn);
      loc.put("endLine", (double) l.endLine);
      loc.put("endColumn", (double) l.endColumn);
      o.put("location", loc);
    }
    o.put("fshName", e.fshName());
    o.put("fshType", e.fshType());
    return o;
  }
}
