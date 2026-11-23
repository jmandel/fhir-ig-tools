package org.hl7.fhir.jekyll.kramdown;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Port of kramdown-2.6.1/lib/kramdown/element.rb.
 *
 * <p>Ruby symbols (element types, option keys, symbolic values such as {@code :mdash}) are Java
 * Strings. {@code attr} keeps insertion order (Ruby Hash order defines HTML attribute order).
 * The {@code ial} option map uses String keys for attributes and {@link #REFS} for {@code :refs}.
 */
public final class Element {
  /** Key used inside IAL maps for Ruby's {@code :refs} (cannot clash with attribute names). */
  public static final String REFS = ":refs";

  public String type;
  public Object value;
  private Map<String, Object> attr;
  private Map<String, Object> options;
  public List<Element> children = new ArrayList<>();

  public Element(String type) {
    this.type = type;
  }

  public Element(String type, Object value) {
    this.type = type;
    this.value = value;
  }

  public Element(String type, Object value, Map<String, Object> attr, Map<String, Object> options) {
    this.type = type;
    this.value = value;
    this.attr = attr;
    this.options = options;
  }

  public Map<String, Object> attr() {
    if (attr == null) {
      attr = new LinkedHashMap<>();
    }
    return attr;
  }

  public boolean hasAttr() {
    return attr != null && !attr.isEmpty();
  }

  void setAttr(Map<String, Object> a) {
    this.attr = a;
  }

  public Map<String, Object> options() {
    if (options == null) {
      options = new HashMap<>();
    }
    return options;
  }

  void setOptions(Map<String, Object> o) {
    this.options = o;
  }

  public Object opt(String k) {
    return options == null ? null : options.get(k);
  }

  public String valueStr() {
    return (String) value;
  }

  /** Builds an options map from alternating key/value arguments. */
  static Map<String, Object> opts(Object... kv) {
    Map<String, Object> m = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }

  static final Set<String> BLOCK = Set.of("blank", "p", "header", "blockquote", "codeblock", "ul", "ol",
      "li", "dl", "dt", "dd", "table", "td", "hr");
  static final Set<String> SPAN = Set.of("text", "a", "br", "img", "codespan", "footnote", "em", "strong",
      "entity", "typographic_sym", "smart_quote", "abbreviation");

  static String category(Element el) {
    if (BLOCK.contains(el.type)) {
      return "block";
    }
    if (SPAN.contains(el.type)) {
      return "span";
    }
    return (String) el.opt("category");
  }

  public boolean isBlock() {
    return "block".equals(category(this));
  }

  public boolean isSpan() {
    return "span".equals(category(this));
  }

  /** Deep copy (Ruby Marshal.load(Marshal.dump(x))). Shared references inside become separate. */
  public Element deepCopy() {
    return deepCopy(new java.util.IdentityHashMap<>());
  }

  private Element deepCopy(java.util.IdentityHashMap<Object, Object> seen) {
    Object s = seen.get(this);
    if (s != null) {
      return (Element) s;
    }
    Element e = new Element(type);
    seen.put(this, e);
    e.value = copyVal(value, seen);
    if (attr != null) {
      e.attr = (Map<String, Object>) copyVal(attr, seen);
    }
    if (options != null) {
      e.options = (Map<String, Object>) copyVal(options, seen);
    }
    for (Element c : children) {
      e.children.add(c.deepCopy(seen));
    }
    return e;
  }

  @SuppressWarnings("unchecked")
  private static Object copyVal(Object v, java.util.IdentityHashMap<Object, Object> seen) {
    if (v == null || v instanceof String || v instanceof Number || v instanceof Boolean || v instanceof Entities.Entity) {
      return v;
    }
    Object s = seen.get(v);
    if (s != null) {
      return s;
    }
    if (v instanceof Element) {
      return ((Element) v).deepCopy(seen);
    }
    if (v instanceof Map) {
      Map<Object, Object> src = (Map<Object, Object>) v;
      Map<Object, Object> m = (v instanceof LinkedHashMap) ? new LinkedHashMap<>() : new HashMap<>();
      seen.put(v, m);
      for (Map.Entry<Object, Object> en : src.entrySet()) {
        m.put(en.getKey(), copyVal(en.getValue(), seen));
      }
      return m;
    }
    if (v instanceof List) {
      List<Object> l = new ArrayList<>();
      seen.put(v, l);
      for (Object o : (List<Object>) v) {
        l.add(copyVal(o, seen));
      }
      return l;
    }
    return v;
  }

  @Override
  public String toString() {
    StringBuilder b = new StringBuilder("<kd:").append(type);
    if (value != null) {
      b.append(" value=").append(value instanceof Element ? "<el>" : value);
    }
    if (attr != null && !attr.isEmpty()) {
      b.append(" attr=").append(attr);
    }
    if (!children.isEmpty()) {
      b.append(" children=").append(children);
    }
    return b.append('>').toString();
  }
}
