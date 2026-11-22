package org.hl7.fhir.sushi.testhelpers;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fshtypes.*;
import org.hl7.fhir.sushi.fshtypes.rules.ContainsRuleItem;
import org.hl7.fhir.sushi.fshtypes.rules.OnlyRuleType;
import org.hl7.fhir.sushi.js.JsNumber;
import org.hl7.fhir.sushi.js.JsObject;
import org.hl7.fhir.sushi.js.Json;

/**
 * Canonical, JS-shaped dump of FSH-side objects (fshtypes, FSHDocument, ...), used by the WP-2 gate (TankDump, same
 * format as scripts/oracle/tank-dump.js) and by {@link Asserts#assertToEqual} (Jest toEqual: deep, undefined
 * properties ignored, class types ignored). Objects become sorted-key maps of their instance fields (Java name,
 * trailing '_' dropped) with {@code $class} = TS constructor name, except TS plain objects; null fields are omitted;
 * Map = {@code {$map: [[k, v], ...]}}, BigInteger = {@code {$bigint}}, Pattern = {@code {$regex}}, non-finite
 * Double = {@code {$num}}.
 */
public final class JsDump {
  private JsDump() {}

  /** Classes that are plain objects (object literals / interfaces) in TS: no $class. */
  private static final Set<Class<?>> PLAIN = Set.of(SourceInfo.class, TextLocation.class,
      org.hl7.fhir.sushi.fshtypes.ExtensionContext.class, ValueSetComponentFrom.class, ValueSetFilter.class,
      OnlyRuleType.class, ContainsRuleItem.class);
  /** Java-only bookkeeping fields with no TS counterpart. */
  private static final Set<String> SKIP_FIELDS = Set.of("ruleLookupCache");

  public static Object dump(Object v) {
    return dump(v, true);
  }

  public static Object dump(Object v, boolean withClass) {
    if (v == null) return null;
    if (v instanceof String || v instanceof Boolean) return v;
    if (v instanceof Double d) {
      if (Double.isFinite(d)) return d;
      return obj("$num", JsNumber.toString(d));
    }
    if (v instanceof Integer i) return (double) i;
    if (v instanceof BigInteger b) return obj("$bigint", b.toString());
    if (v instanceof Pattern p) return obj("$regex", p.pattern());
    if (v instanceof Enum<?> e) return e.toString();
    if (v instanceof Map<?, ?> m) {
      List<Object> entries = new ArrayList<>();
      m.forEach((k, x) -> entries.add(new ArrayList<>(Arrays.asList(k, dump(x, withClass)))));
      return obj("$map", entries);
    }
    if (v instanceof List<?> l) {
      List<Object> out = new ArrayList<>();
      for (Object x : l) out.add(dump(x, withClass));
      return out;
    }
    TreeMap<String, Object> fields = new TreeMap<>();
    if (withClass && !PLAIN.contains(v.getClass())) {
      fields.put("$class", v instanceof FshEntity fe ? fe.constructorName() : v.getClass().getSimpleName());
    }
    for (Class<?> c = v.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
      for (Field f : c.getDeclaredFields()) {
        if (Modifier.isStatic(f.getModifiers()) || SKIP_FIELDS.contains(f.getName()) || f.isSynthetic()) continue;
        f.setAccessible(true);
        Object x;
        try {
          x = f.get(v);
        } catch (IllegalAccessException e) {
          throw new IllegalStateException(e);
        }
        Object d = dump(x, withClass);
        String name = f.getName().endsWith("_") ? f.getName().substring(0, f.getName().length() - 1) : f.getName();
        if (d != null && !fields.containsKey(name)) fields.put(name, d);
      }
    }
    JsObject o = new JsObject();
    // $class sorts first in TS too ('$' < letters, and Object.keys(...).sort() puts '$class' before '_id')
    fields.forEach(o::put);
    return o;
  }

  private static JsObject obj(String k, Object v) {
    JsObject o = new JsObject();
    o.put(k, v);
    return o;
  }

  /** Pretty JSON of the dump without $class (Jest toEqual ignores types). */
  public static String canon(Object v) {
    return Json.stringify(dump(v, false), 1);
  }
}
