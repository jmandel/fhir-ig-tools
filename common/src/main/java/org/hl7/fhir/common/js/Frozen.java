package org.hl7.fhir.common.js;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * JS {@code Object.freeze} applied deeply (FPL's SafeMode.FREEZE for resources it hands out). A frozen object or
 * array still IS a {@link JsObject} / {@code ArrayList}, so all readers work unchanged, but every mutator throws
 * {@link FrozenMutationError} with V8's strict-mode TypeError text, as TS SUSHI would crash. Callers that need to
 * change a fished resource must copy it first ({@link Js#cloneDeep} returns ordinary mutable objects), exactly as the
 * TS code does.
 */
public final class Frozen {
  private Frozen() {}

  /** V8: "Cannot assign to read only property 'x' of object '#<Object>'" and friends. */
  public static final class FrozenMutationError extends UnsupportedOperationException {
    private static final long serialVersionUID = 1L;

    FrozenMutationError(String message) {
      super(message);
    }
  }

  /** Returns a deeply frozen copy-in-place: maps/lists are rebuilt as frozen ones; other values are shared. */
  public static Object deepFreeze(Object v) {
    if (v instanceof FrozenObject || v instanceof FrozenArray) return v;
    if (v instanceof Map<?, ?> m) {
      FrozenObject o = new FrozenObject();
      for (Map.Entry<?, ?> e : m.entrySet()) o.init((String) e.getKey(), deepFreeze(e.getValue()));
      o.frozen = true;
      return o;
    }
    if (v instanceof List<?> l) {
      FrozenArray a = new FrozenArray(l.size());
      for (Object x : l) a.init(deepFreeze(x));
      a.frozen = true;
      return a;
    }
    return v;
  }

  public static boolean isFrozen(Object v) {
    return v instanceof FrozenObject || v instanceof FrozenArray;
  }

  static FrozenMutationError assign(Object key) {
    return new FrozenMutationError("Cannot assign to read only property '" + key + "' of object '#<Object>'");
  }

  static FrozenMutationError delete(Object key) {
    return new FrozenMutationError("Cannot delete property '" + key + "' of #<Object>");
  }

  static FrozenMutationError extend(Object key) {
    return new FrozenMutationError("Cannot add property " + key + ", object is not extensible");
  }

  /** A frozen JS object. */
  public static final class FrozenObject extends JsObject {
    private static final long serialVersionUID = 1L;
    boolean frozen;

    void init(String k, Object v) {
      super.put(k, v);
    }

    private FrozenMutationError put0(Object k) {
      return containsKey(k) ? assign(k) : extend(k);
    }

    @Override public Object put(String k, Object v) { if (frozen) throw put0(k); return super.put(k, v); }
    @Override public void putAll(Map<? extends String, ?> m) { if (frozen) throw extend(m.keySet()); super.putAll(m); }
    @Override public Object remove(Object k) { throw delete(k); }
    @Override public boolean remove(Object k, Object v) { throw delete(k); }
    @Override public void clear() { throw delete("*"); }
    @Override public Object putIfAbsent(String k, Object v) { throw put0(k); }
    @Override public boolean replace(String k, Object o, Object n) { throw assign(k); }
    @Override public Object replace(String k, Object v) { throw assign(k); }
    @Override public void replaceAll(BiFunction<? super String, ? super Object, ?> f) { throw assign("*"); }
    @Override public Object computeIfAbsent(String k, Function<? super String, ?> f) {
      if (containsKey(k) && get(k) != null) return get(k);
      throw put0(k);
    }
    @Override public Object computeIfPresent(String k, BiFunction<? super String, ? super Object, ?> f) { throw assign(k); }
    @Override public Object compute(String k, BiFunction<? super String, ? super Object, ?> f) { throw put0(k); }
    @Override public Object merge(String k, Object v, BiFunction<? super Object, ? super Object, ?> f) { throw put0(k); }
    @Override public JsObject with(String k, Object v) { throw put0(k); }
    @Override public Set<String> keySet() { return Collections.unmodifiableSet(super.keySet()); }
    @Override public Collection<Object> values() { return Collections.unmodifiableCollection(super.values()); }
    @Override public Set<Map.Entry<String, Object>> entrySet() {
      // unmodifiableMap(...).entrySet() also blocks Entry.setValue
      Set<Map.Entry<String, Object>> raw = super.entrySet();
      return Collections.unmodifiableMap(new java.util.AbstractMap<String, Object>() {
        @Override public Set<Map.Entry<String, Object>> entrySet() { return raw; }
      }).entrySet();
    }
  }

  /** A frozen JS array. */
  public static final class FrozenArray extends ArrayList<Object> {
    private static final long serialVersionUID = 1L;
    boolean frozen;

    FrozenArray(int n) {
      super(n);
    }

    void init(Object v) {
      super.add(v);
    }

    private void check(Object what) {
      if (frozen) throw assign(what);
    }

    @Override public boolean add(Object o) { check(size()); return super.add(o); }
    @Override public void add(int i, Object o) { check(i); super.add(i, o); }
    @Override public boolean addAll(Collection<?> c) { check(size()); return super.addAll(c); }
    @Override public boolean addAll(int i, Collection<?> c) { check(i); return super.addAll(i, c); }
    @Override public Object set(int i, Object o) { check(i); return super.set(i, o); }
    @Override public Object remove(int i) { check(i); return super.remove(i); }
    @Override public boolean remove(Object o) { check("length"); return super.remove(o); }
    @Override public boolean removeAll(Collection<?> c) { check("length"); return super.removeAll(c); }
    @Override public boolean retainAll(Collection<?> c) { check("length"); return super.retainAll(c); }
    @Override public boolean removeIf(Predicate<? super Object> p) { check("length"); return super.removeIf(p); }
    @Override public void replaceAll(UnaryOperator<Object> op) { check(0); super.replaceAll(op); }
    @Override public void sort(Comparator<? super Object> c) { check(0); super.sort(c); }
    @Override public void clear() { check("length"); super.clear(); }
    @Override protected void removeRange(int a, int b) { check("length"); super.removeRange(a, b); }
    @Override public Iterator<Object> iterator() { return Collections.unmodifiableList(this.view()).iterator(); }
    @Override public ListIterator<Object> listIterator() { return Collections.unmodifiableList(this.view()).listIterator(); }
    @Override public ListIterator<Object> listIterator(int i) { return Collections.unmodifiableList(this.view()).listIterator(i); }
    @Override public List<Object> subList(int a, int b) { return Collections.unmodifiableList(super.subList(a, b)); }

    private List<Object> view() {
      return new java.util.AbstractList<>() {
        @Override public Object get(int i) { return FrozenArray.this.get(i); }
        @Override public int size() { return FrozenArray.this.size(); }
      };
    }
  }
}
