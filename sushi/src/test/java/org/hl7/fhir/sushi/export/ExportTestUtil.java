package org.hl7.fhir.sushi.export;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fhirtypes.Common;
import org.hl7.fhir.sushi.fshtypes.Configuration;
import org.hl7.fhir.sushi.fshtypes.rules.ContainsRuleItem;
import org.hl7.fhir.sushi.fshtypes.rules.OnlyRuleType;
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

  /** Marker for a TS object spread ({@code {...x, k: v}}) in {@link #obj}. */
  static final Object SPREAD = new Object();

  /** A TS object literal with non-literal values: obj("k1", v1, "k2", v2, SPREAD, other, ...). */
  @SuppressWarnings("unchecked")
  static JsObject obj(Object... kv) {
    JsObject o = new JsObject();
    for (int i = 0; i < kv.length; i += 2) {
      if (kv[i] == SPREAD) {
        if (kv[i + 1] instanceof Map<?, ?> m) o.putAll((Map<String, Object>) m);
        if (kv[i + 1] instanceof Configuration.ConfigurationInstanceOptions io) {
          if (io.setMetaProfile != null) o.put("setMetaProfile", io.setMetaProfile);
          if (io.setId != null) o.put("setId", io.setId);
          if (io.manualSliceOrdering != null) o.put("manualSliceOrdering", io.manualSliceOrdering);
        }
      } else if (kv[i + 1] != Js.UNDEFINED) {
        o.put((String) kv[i], kv[i + 1]);
      }
    }
    return o;
  }

  /** TS {@code config.instanceOptions = { ... }}. */
  static Configuration.ConfigurationInstanceOptions instanceOptions(Object o) {
    Configuration.ConfigurationInstanceOptions io = new Configuration.ConfigurationInstanceOptions();
    io.setMetaProfile = (String) JsAssert.at(o, "setMetaProfile");
    io.setId = (String) JsAssert.at(o, "setId");
    io.manualSliceOrdering = (Boolean) JsAssert.at(o, "manualSliceOrdering");
    return io;
  }

  /** A TS ContainsRuleItem literal {@code { name, type? }}. */
  static ContainsRuleItem containsRuleItem(Object o) {
    return new ContainsRuleItem((String) JsAssert.at(o, "name"), (String) JsAssert.at(o, "type"));
  }

  /** A TS OnlyRuleType literal {@code { type, isReference?, isCanonical?, isCodeableReference? }}. */
  static OnlyRuleType onlyRuleType(Object o) {
    OnlyRuleType t = new OnlyRuleType((String) JsAssert.at(o, "type"));
    t.isReference = (Boolean) JsAssert.at(o, "isReference");
    t.isCanonical = (Boolean) JsAssert.at(o, "isCanonical");
    t.isCodeableReference = (Boolean) JsAssert.at(o, "isCodeableReference");
    return t;
  }

  // JS array methods on value-model lists (the callback parameter is untyped, like the TS)
  static boolean some(Object list, java.util.function.Predicate<Object> fn) {
    return ((List<?>) list).stream().anyMatch(fn);
  }

  static boolean every(Object list, java.util.function.Predicate<Object> fn) {
    return ((List<?>) list).stream().allMatch(fn);
  }

  static List<Object> filter(Object list, java.util.function.Predicate<Object> fn) {
    return new java.util.ArrayList<>(((List<?>) list).stream().filter(fn).toList());
  }

  static List<Object> map(Object list, java.util.function.Function<Object, Object> fn) {
    List<Object> out = new java.util.ArrayList<>();
    for (Object o : (List<?>) list) out.add(fn.apply(o));
    return out;
  }

  static Object find(Object list, java.util.function.Predicate<Object> fn) {
    return ((List<?>) list).stream().filter(fn).findFirst().orElse(null);
  }

  static int findIndex(Object list, java.util.function.Predicate<Object> fn) {
    List<?> l = (List<?>) list;
    for (int i = 0; i < l.size(); i++) if (fn.test(l.get(i))) return i;
    return -1;
  }

  static void forEach(Object list, java.util.function.Consumer<Object> fn) {
    ((List<?>) list).forEach(fn);
  }

  /** JS {@code s.match(re)} truthiness: the first match, or null. */
  static Object jsMatch(Object s, Pattern p) {
    if (!(s instanceof String str)) return null;
    java.util.regex.Matcher m = p.matcher(str);
    return m.find() ? m.group() : null;
  }

  /** A TS array literal with non-literal values. */
  static List<Object> arr(Object... xs) {
    return new java.util.ArrayList<>(java.util.Arrays.asList(xs));
  }

  /** Jest {@code expect(fn).toThrow('message')}: throws, and the message contains the text. */
  static void assertThrowsMessage(String text, org.junit.jupiter.api.function.Executable fn) {
    Throwable t = org.junit.jupiter.api.Assertions.assertThrows(Throwable.class, fn);
    assertTrue(t.getMessage() != null && t.getMessage().contains(text), () -> "expected message containing: " + text
        + "\n but was: " + t.getMessage());
  }

  /** Jest {@code expect(fn).toThrow(/regex/)}. */
  static void assertThrowsMatching(String jsRegex, String flags, org.junit.jupiter.api.function.Executable fn) {
    Throwable t = org.junit.jupiter.api.Assertions.assertThrows(Throwable.class, fn);
    assertTrue(t.getMessage() != null && jsRegex(jsRegex, flags).matcher(t.getMessage()).find(),
        () -> "expected message matching /" + jsRegex + "/\n but was: " + t.getMessage());
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

  /** An InstanceDefinition as the TS object (its own keys plus _instanceMeta), for toEqual with _instanceMeta. */
  static Object withMeta(Object instance) {
    if (!(instance instanceof org.hl7.fhir.sushi.fhirtypes.InstanceDefinition inst)) return instance;
    JsObject o = new JsObject();
    JsObject meta = new JsObject();
    var m = inst._instanceMeta;
    if (m.name != null) meta.put("name", m.name);
    if (m.title != null) meta.put("title", m.title);
    if (m.description != null) meta.put("description", m.description);
    if (m.usage != null) meta.put("usage", m.usage);
    if (m.sdType != null) meta.put("sdType", m.sdType);
    if (m.sdKind != null) meta.put("sdKind", m.sdKind);
    if (m.instanceOfUrl != null) meta.put("instanceOfUrl", m.instanceOfUrl);
    o.put("_instanceMeta", meta);
    o.putAll(inst);
    return o;
  }

  /** TS {@code obj.key = value} on a value-model object or PropertyHolder. */
  static void setProp(Object obj, String key, Object value) {
    if (obj instanceof Map || obj instanceof org.hl7.fhir.sushi.fhirtypes.PropertyHolder) {
      Common.setProp(obj, key, value);
      return;
    }
    // a FSH-side Java object (e.g. rule.value.version = ... on a FshCanonical): its public field
    try {
      obj.getClass().getField(key).set(obj, value);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError("cannot set " + key + " on " + obj, e);
    }
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
