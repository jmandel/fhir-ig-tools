package org.hl7.fhir.jekyll.yaml;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A loaded YAML mapping: a plain {@code LinkedHashMap<Object,Object>} whose {@code hashCode} and
 * {@code equals} survive recursive structures, like Ruby's {@code Hash#hash} / {@code Hash#==}
 * (which detect recursion). SafeYAML can build a map that contains itself, even as a key (an
 * anchored map becomes the pending {@code current_key} and then receives itself), and a key's
 * hash is computed on insertion.
 */
public final class YamlMap extends LinkedHashMap<Object, Object> {
  private static final long serialVersionUID = 1L;

  YamlMap() {}

  @Override
  public int hashCode() {
    return Recursion.hash(this);
  }

  @Override
  public boolean equals(Object o) {
    return Recursion.equal(this, o);
  }

  /** recursion-guarded content hash / equality shared by {@link YamlMap} and {@link YamlList} */
  static final class Recursion {
    private static final ThreadLocal<IdentityHashMap<Object, Boolean>> HASHING = ThreadLocal.withInitial(IdentityHashMap::new);
    private static final ThreadLocal<IdentityHashMap<Object, Object>> COMPARING = ThreadLocal.withInitial(IdentityHashMap::new);

    private Recursion() {}

    static int hash(Object self) {
      IdentityHashMap<Object, Boolean> active = HASHING.get();
      if (active.containsKey(self)) {
        return 0; // a recursive reference contributes a constant (Ruby: rb_exec_recursive)
      }
      active.put(self, Boolean.TRUE);
      try {
        int h = 0;
        if (self instanceof Map) {
          for (Map.Entry<?, ?> e : ((Map<?, ?>) self).entrySet()) {
            h += java.util.Objects.hashCode(e.getKey()) ^ java.util.Objects.hashCode(e.getValue());
          }
        } else {
          h = 1;
          for (Object x : (List<?>) self) {
            h = 31 * h + java.util.Objects.hashCode(x);
          }
        }
        return h;
      } finally {
        active.remove(self);
      }
    }

    static boolean equal(Object self, Object o) {
      if (self == o) {
        return true;
      }
      if (self instanceof Map ? !(o instanceof Map) : !(o instanceof List)) {
        return false;
      }
      IdentityHashMap<Object, Object> active = COMPARING.get();
      if (active.get(self) == o) {
        return true; // recursion on the same pair: treated as equal (Ruby: rb_exec_recursive_paired)
      }
      Object prev = active.put(self, o);
      try {
        if (self instanceof Map) {
          Map<?, ?> a = (Map<?, ?>) self;
          Map<?, ?> b = (Map<?, ?>) o;
          if (a.size() != b.size()) {
            return false;
          }
          for (Map.Entry<?, ?> e : a.entrySet()) {
            Object k = e.getKey();
            if (!b.containsKey(k) || !java.util.Objects.equals(e.getValue(), b.get(k))) {
              return false;
            }
          }
          return true;
        }
        List<?> a = (List<?>) self;
        List<?> b = (List<?>) o;
        if (a.size() != b.size()) {
          return false;
        }
        for (int i = 0; i < a.size(); i++) {
          if (!java.util.Objects.equals(a.get(i), b.get(i))) {
            return false;
          }
        }
        return true;
      } finally {
        if (prev == null) {
          active.remove(self);
        } else {
          active.put(self, prev);
        }
      }
    }
  }
}
