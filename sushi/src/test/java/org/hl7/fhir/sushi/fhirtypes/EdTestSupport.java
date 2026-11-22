package org.hl7.fhir.sushi.fhirtypes;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
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

  static Object normalize(Object v) {
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

  /** An object literal: {@code obj("a", 1.0, "b", "x")} (Java null = JS null; use Js.UNDEFINED for undefined). */
  static JsObject obj(Object... kv) {
    JsObject o = new JsObject();
    for (int i = 0; i < kv.length; i += 2) o.put((String) kv[i], kv[i + 1]);
    return o;
  }

  /** A mutable array literal. */
  @SafeVarargs
  static <T> List<T> list(T... v) {
    List<T> l = new ArrayList<>();
    for (T o : v) l.add(o);
    return l;
  }

  /** A mutable string array literal. */
  static List<String> strings(String... v) {
    return new ArrayList<>(List.of(v));
  }

  /** Jest toStrictEqual: deep equality where undefined-valued keys count (lodash isEqual, no normalization). */
  static void assertStrictJsEqual(Object expected, Object actual) {
    if (!Js.isEqual(expected, actual)) {
      fail("expected (strict): " + show(expected) + "\n but was: " + show(actual));
    }
  }

  /** TS {@code sd.elements.find(e => e.path === path)}. */
  static ElementDefinition byPath(StructureDefinition sd, String path) {
    for (ElementDefinition e : sd.elements) if (path.equals(e.getPath())) return e;
    return null;
  }

  /** TS {@code sd.elements.findIndex(e => e.path === path)}. */
  static int indexByPath(StructureDefinition sd, String path) {
    for (int i = 0; i < sd.elements.size(); i++) if (path.equals(sd.elements.get(i).getPath())) return i;
    return -1;
  }

  /** TS {@code sd.elements.findIndex(e => e.id === id)}. */
  static int indexById(StructureDefinition sd, String id) {
    for (int i = 0; i < sd.elements.size(); i++) if (id.equals(sd.elements.get(i).getId())) return i;
    return -1;
  }

  /** TS {@code list.find(e => e.id === id)}. */
  static ElementDefinition findId(List<ElementDefinition> l, String id) {
    for (ElementDefinition e : l) if (id.equals(e.getId())) return e;
    return null;
  }

  private static final Pattern SEGMENT = Pattern.compile("([^.\\[\\]]+)|\\[(\\d+)\\]");

  /**
   * TS {@code obj.a.b[0].c} over the value model and PropertyHolders (ElementDefinition, ElementDefinitionType,
   * StructureDefinition); null as soon as a step is null/undefined (like optional chaining).
   */
  static Object at(Object obj, String path) {
    Matcher m = SEGMENT.matcher(path);
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

  /** TS {@code obj.a.b = value} (intermediate steps must exist). */
  static void setAt(Object obj, String path, Object value) {
    int dot = path.lastIndexOf('.');
    Object target = dot < 0 ? obj : at(obj, path.substring(0, dot));
    String key = dot < 0 ? path : path.substring(dot + 1);
    Matcher idx = Pattern.compile("^(.*)\\[(\\d+)\\]$").matcher(key);
    if (idx.matches()) {
      @SuppressWarnings("unchecked") List<Object> l = (List<Object>) (idx.group(1).isEmpty() ? target
          : Common.getProp(target, idx.group(1)));
      l.set(Integer.parseInt(idx.group(2)), value);
      return;
    }
    Common.setProp(target, key, value);
  }

  /** TS {@code x.length} for arrays and strings. */
  static int size(Object v) {
    if (v instanceof List<?> l) return l.size();
    if (v instanceof String s) return s.length();
    if (v instanceof Map<?, ?> m) return m.size();
    throw new AssertionError("no length: " + v);
  }

  /** TS {@code arr[i]} with a computed index. */
  static Object get(Object list, Object index) {
    int i = ((Number) index).intValue();
    List<?> l = (List<?>) list;
    return i >= 0 && i < l.size() ? l.get(i) : null;
  }

  /** TS {@code sd.elements[i]} with a computed index. */
  static ElementDefinition get(List<ElementDefinition> list, Object index) {
    int i = ((Number) index).intValue();
    return i >= 0 && i < list.size() ? list.get(i) : null;
  }

  /** Jest toContain (=== membership for arrays, substring for strings). */
  static boolean contains(Object haystack, Object needle) {
    if (haystack instanceof String s) return s.contains((String) needle);
    for (Object o : (List<?>) haystack) if (java.util.Objects.equals(o, needle)) return true;
    return false;
  }

  /** Jest toContainEqual. */
  static boolean containsEqual(Object haystack, Object needle) {
    if (haystack == null) return false;
    for (Object o : (List<?>) haystack) if (Js.isEqual(normalize(o), normalize(needle))) return true;
    return false;
  }

  /** Jest toMatch(/re/): find() of a JS regex source. */
  static void assertMatchesJs(String jsRegex, Object actual) {
    assertTrue(actual instanceof String s && Pattern.compile(jsRegex).matcher(s).find(),
        () -> "expected /" + jsRegex + "/ but was: " + actual);
  }

  /** A TS OnlyRuleType object literal {type, isReference?, isCanonical?, isCodeableReference?}. */
  static org.hl7.fhir.sushi.fshtypes.rules.OnlyRuleType ort(String type, Boolean isReference, Boolean isCanonical,
      Boolean isCodeableReference) {
    var t = new org.hl7.fhir.sushi.fshtypes.rules.OnlyRuleType(type);
    t.isReference = isReference;
    t.isCanonical = isCanonical;
    t.isCodeableReference = isCodeableReference;
    return t;
  }

  /** fs.readFileSync(path, {encoding: 'base64'}). */
  static String readBase64(java.nio.file.Path p) {
    try {
      return java.util.Base64.getEncoder().encodeToString(java.nio.file.Files.readAllBytes(p));
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }

  /** JSON.parse(fs.readFileSync(path, 'utf-8')). */
  static Object readJson(String path) {
    try {
      return Json.parse(java.nio.file.Files.readString(java.nio.file.Path.of(path)));
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }

  /** lodash findLastIndex. */
  static <T> int findLastIndex(List<T> l, java.util.function.Predicate<? super T> p) {
    for (int i = l.size() - 1; i >= 0; i--) if (p.test(l.get(i))) return i;
    return -1;
  }

  static double num(Object v) {
    return ((Number) v).doubleValue();
  }

  /** {@code new ElementDefinitionType(code)} list, for toEqual comparisons of ED.type. */
  static List<ElementDefinitionType> types(String... codes) {
    List<ElementDefinitionType> l = new ArrayList<>();
    for (String c : codes) l.add(new ElementDefinitionType(c));
    return l;
  }
}
