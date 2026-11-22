package org.hl7.fhir.sushi.fhirtypes;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.sushi.fshtypes.FshEntity;
import org.hl7.fhir.sushi.fshtypes.SourceInfo;
import org.hl7.fhir.sushi.fshtypes.TextLocation;
import org.hl7.fhir.sushi.js.Js;
import org.hl7.fhir.sushi.js.JsObject;

/**
 * lodash cloneDeep of fshtypes objects (rules and their values), which TS clones in replaceReferences and
 * applyInsertRules. TEMPORARY: reflective field copy until fshtypes provides its own deep clone (contract request
 * 2026-11-22, see progress/sushi-port-requests.log); switch the two call sites then.
 */
final class FshClone {
  private FshClone() {}

  @SuppressWarnings("unchecked")
  static <T> T cloneDeep(T v) {
    return (T) clone(v, new IdentityHashMap<>());
  }

  private static Object clone(Object v, IdentityHashMap<Object, Object> seen) {
    if (v == null || v instanceof String || v instanceof Number || v instanceof Boolean || v instanceof Enum<?>
        || v == Js.UNDEFINED) {
      return v;
    }
    Object done = seen.get(v);
    if (done != null) return done;
    if (v instanceof SourceInfo si) {
      SourceInfo c = new SourceInfo(si.file, si.location == null ? null : si.location.copy());
      c.appliedFile = si.appliedFile;
      c.appliedLocation = si.appliedLocation == null ? null : si.appliedLocation.copy();
      seen.put(v, c);
      return c;
    }
    if (v instanceof TextLocation tl) return tl.copy();
    if (v instanceof Js.DeepCloneable) return Js.cloneDeep(v, seen);
    if (v instanceof List<?> l) {
      List<Object> c = new ArrayList<>(l.size());
      seen.put(v, c);
      for (Object o : l) c.add(clone(o, seen));
      return c;
    }
    if (v instanceof Map<?, ?> m) {
      JsObject c = new JsObject();
      seen.put(v, c);
      for (Map.Entry<?, ?> e : m.entrySet()) c.put((String) e.getKey(), clone(e.getValue(), seen));
      return c;
    }
    if (v instanceof FshEntity || v.getClass().getPackageName().startsWith("org.hl7.fhir.sushi.fshtypes")) {
      Object c = newInstance(v.getClass());
      seen.put(v, c);
      for (Class<?> k = v.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
        for (Field f : k.getDeclaredFields()) {
          if (Modifier.isStatic(f.getModifiers())) continue;
          try {
            f.setAccessible(true);
            Object fv = f.get(v);
            if (f.getName().equals("ruleLookupCache")) fv = null;
            f.set(c, clone(fv, seen));
          } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("cannot clone field " + k.getName() + "." + f.getName(), e);
          }
        }
      }
      return c;
    }
    return v;
  }

  private static Object newInstance(Class<?> k) {
    RuntimeException last = null;
    for (Constructor<?> ctor : k.getDeclaredConstructors()) {
      Object[] args = new Object[ctor.getParameterCount()];
      Class<?>[] types = ctor.getParameterTypes();
      for (int i = 0; i < args.length; i++) {
        if (types[i] == boolean.class) args[i] = false;
        else if (types[i] == int.class) args[i] = 0;
        else if (types[i] == long.class) args[i] = 0L;
        else if (types[i] == double.class) args[i] = 0d;
        else if (types[i] == String.class) args[i] = "";
      }
      try {
        ctor.setAccessible(true);
        return ctor.newInstance(args);
      } catch (ReflectiveOperationException | RuntimeException e) {
        last = new IllegalStateException(e);
      }
    }
    throw last != null ? last : new IllegalStateException("no constructor for " + k.getName());
  }
}
