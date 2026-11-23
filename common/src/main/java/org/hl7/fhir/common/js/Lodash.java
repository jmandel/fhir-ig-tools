package org.hl7.fhir.common.js;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * lodash 4.17 merge / mergeWith / isMatch over the JS value model (see {@link Json}). Added by WP-7
 * (InstanceExporter); kept apart from {@link Js} so the contract class stays small.
 *
 * <p>Value model mapping of lodash's type tests: an "object" is a Map or a List; a "plain object" is a Map whose
 * class is exactly {@link JsObject}, {@link LinkedHashMap} or {@link java.util.HashMap} (any other Map subclass,
 * e.g. InstanceDefinition, is a class instance, which lodash merges by reference). A missing key reads as
 * {@link Js#UNDEFINED}; list holes read as null.
 */
public final class Lodash {
  private Lodash() {}

  /**
   * A mergeWith customizer: return {@link Js#UNDEFINED} to fall back to the default merge. objValue is
   * {@link Js#UNDEFINED} when the destination has no such key.
   */
  @FunctionalInterface
  public interface MergeCustomizer {
    Object customize(Object objValue, Object srcValue, String key);
  }

  /** lodash merge(object, source): mutates and returns object. */
  public static <T> T merge(T object, Object source) {
    baseMerge(object, source, null, new IdentityHashMap<>());
    return object;
  }

  /** lodash mergeWith(object, source, customizer): mutates and returns object. */
  public static <T> T mergeWith(T object, Object source, MergeCustomizer customizer) {
    baseMerge(object, source, customizer, new IdentityHashMap<>());
    return object;
  }

  static boolean isObject(Object v) {
    return v instanceof Map || v instanceof List;
  }

  static boolean isPlainObject(Object v) {
    if (!(v instanceof Map)) return false;
    Class<?> c = v.getClass();
    return c == JsObject.class || c == LinkedHashMap.class || c == java.util.HashMap.class;
  }

  /** for..in keys (keysIn): list indices, or Js.ownKeys of a map. */
  private static List<String> keysIn(Object v) {
    if (v instanceof List<?> l) {
      List<String> keys = new ArrayList<>(l.size());
      for (int i = 0; i < l.size(); i++) keys.add(Integer.toString(i));
      return keys;
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> m = (Map<String, Object>) v;
    return Js.ownKeys(m);
  }

  /** object[key]; UNDEFINED when absent (a list index past the end, a missing map key). */
  private static Object get(Object object, String key) {
    if (object instanceof List<?> l) {
      int i = indexOf(key);
      return i >= 0 && i < l.size() ? l.get(i) : Js.UNDEFINED;
    }
    if (object instanceof Map<?, ?> m) {
      return m.containsKey(key) ? m.get(key) : Js.UNDEFINED;
    }
    return Js.UNDEFINED;
  }

  private static boolean has(Object object, String key) {
    if (object instanceof List<?> l) {
      int i = indexOf(key);
      return i >= 0 && i < l.size();
    }
    return object instanceof Map<?, ?> m && m.containsKey(key);
  }

  private static int indexOf(String key) {
    return Js.isArrayIndex(key) && key.length() < 10 ? Integer.parseInt(key) : -1;
  }

  @SuppressWarnings("unchecked")
  private static void set(Object object, String key, Object value) {
    if (object instanceof List<?> l) {
      List<Object> list = (List<Object>) l;
      int i = indexOf(key);
      if (i < 0) return; // a non-index property on an array: not representable in the value model
      while (list.size() < i) list.add(null); // JS holes
      if (i == list.size()) list.add(value);
      else list.set(i, value);
    } else if (object instanceof Map<?, ?> m) {
      ((Map<String, Object>) m).put(key, value);
    }
  }

  /** lodash eq (SameValueZero), on value-model values; maps/lists by identity. */
  private static boolean eq(Object a, Object b) {
    if (a == b) return true;
    if (a instanceof Double x && b instanceof Double y) {
      return x.doubleValue() == y.doubleValue() || (x.isNaN() && y.isNaN());
    }
    if (a instanceof String || a instanceof Boolean || a instanceof java.math.BigInteger) return a.equals(b);
    return false;
  }

  private static void assignMergeValue(Object object, String key, Object value) {
    if ((value != Js.UNDEFINED && !eq(get(object, key), value)) || (value == Js.UNDEFINED && !has(object, key))) {
      set(object, key, value);
    }
  }

  private static void baseMerge(Object object, Object source, MergeCustomizer customizer,
      IdentityHashMap<Object, Object> stack) {
    if (object == source || !isObject(source)) return;
    for (String key : keysIn(source)) {
      Object srcValue = get(source, key);
      if (isObject(srcValue)) {
        baseMergeDeep(object, key, srcValue, customizer, stack);
      } else {
        Object newValue = customizer != null ? customizer.customize(get(object, key), srcValue, key) : Js.UNDEFINED;
        if (newValue == Js.UNDEFINED) newValue = srcValue;
        assignMergeValue(object, key, newValue);
      }
    }
  }

  private static void baseMergeDeep(Object object, String key, Object srcValue, MergeCustomizer customizer,
      IdentityHashMap<Object, Object> stack) {
    Object objValue = get(object, key);
    Object stacked = stack.get(srcValue);
    if (stacked != null) {
      assignMergeValue(object, key, stacked);
      return;
    }
    Object newValue = customizer != null ? customizer.customize(objValue, srcValue, key) : Js.UNDEFINED;
    boolean isCommon = newValue == Js.UNDEFINED;
    if (isCommon) {
      newValue = srcValue;
      if (srcValue instanceof List) {
        if (objValue instanceof List) {
          newValue = objValue;
        } else {
          newValue = new ArrayList<>();
        }
      } else if (isPlainObject(srcValue)) {
        newValue = objValue;
        if (!isObject(objValue)) {
          newValue = new JsObject();
        }
      } else {
        isCommon = false;
      }
    }
    if (isCommon) {
      // Recursively merge objects and arrays
      stack.put(srcValue, newValue);
      baseMerge(newValue, srcValue, customizer, stack);
      stack.remove(srcValue);
    }
    assignMergeValue(object, key, newValue);
  }

  /**
   * lodash isMatch(object, source): a partial deep comparison. Every key of source must be present on object;
   * primitive values compared with ===, object values with partial, unordered (array) deep equality.
   */
  public static boolean isMatch(Object object, Object source) {
    if (!(source instanceof Map<?, ?> src)) {
      return true; // keys(non-object source) is empty (strings would have index keys; not used by SUSHI)
    }
    if (object == null || object == Js.UNDEFINED) return src.isEmpty();
    for (Map.Entry<?, ?> e : src.entrySet()) {
      String key = (String) e.getKey();
      Object srcValue = e.getValue();
      Object objValue = get(object, key);
      if (isStrictComparable(srcValue)) {
        if (!strictEq(srcValue, objValue)) return false;
        if (objValue == Js.UNDEFINED && !has(object, key)) return false;
      } else {
        if (!has(object, key)) return false;
        if (!baseIsEqualPartial(srcValue, objValue)) return false;
      }
    }
    return true;
  }

  /** lodash isStrictComparable: value === value && !isObject(value). */
  private static boolean isStrictComparable(Object v) {
    if (v instanceof Double d && d.isNaN()) return false;
    return !isObject(v);
  }

  /** JS ===, with null and undefined distinct. */
  private static boolean strictEq(Object a, Object b) {
    if (a == b) return true;
    if (a == null || b == null || a == Js.UNDEFINED || b == Js.UNDEFINED) return false;
    if (a instanceof Double x && b instanceof Double y) return x.doubleValue() == y.doubleValue();
    if (a instanceof String || a instanceof Boolean || a instanceof java.math.BigInteger) return a.equals(b);
    return false;
  }

  private static boolean isObjectLike(Object v) {
    return isObject(v);
  }

  /** baseIsEqual(value, other, COMPARE_PARTIAL_FLAG | COMPARE_UNORDERED_FLAG). */
  private static boolean baseIsEqualPartial(Object value, Object other) {
    if (strictEq(value, other)) return true;
    if (value == null || value == Js.UNDEFINED || other == null || other == Js.UNDEFINED
        || (!isObjectLike(value) && !isObjectLike(other))) {
      return value instanceof Double x && x.isNaN() && other instanceof Double y && y.isNaN();
    }
    if (value instanceof List<?> array && other instanceof List<?> oth) {
      int arrLength = array.size(), othLength = oth.size();
      if (arrLength != othLength && !(othLength > arrLength)) return false;
      boolean[] seen = new boolean[othLength];
      for (Object arrValue : array) {
        boolean found = false;
        for (int othIndex = 0; othIndex < othLength; othIndex++) {
          if (!seen[othIndex]) {
            Object othValue = oth.get(othIndex);
            if (strictEq(arrValue, othValue) || baseIsEqualPartial(arrValue, othValue)) {
              seen[othIndex] = true;
              found = true;
              break;
            }
          }
        }
        if (!found) return false;
      }
      return true;
    }
    if (value instanceof Map<?, ?> obj && other instanceof Map<?, ?> oth) {
      for (Object k : obj.keySet()) {
        if (!oth.containsKey(k)) return false;
      }
      for (Map.Entry<?, ?> e : obj.entrySet()) {
        Object othValue = oth.get(e.getKey());
        if (!(strictEq(e.getValue(), othValue) || baseIsEqualPartial(e.getValue(), othValue))) return false;
      }
      return true;
    }
    return false; // different tags (array vs object)
  }
}
