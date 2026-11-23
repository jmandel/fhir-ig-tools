package org.hl7.fhir.jekyll.kramdown;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Port of kramdown-2.6.1/lib/kramdown/options.rb plus the GFM parser's options
 * (kramdown-parser-gfm-1.1.0/lib/kramdown/parser/gfm/options.rb).
 *
 * <p>Option values: Ruby Symbols are represented as Java Strings, Integers as Long, Booleans as
 * Boolean, arrays as List, hashes as LinkedHashMap. Options maps are keyed by option name.
 */
public final class Options {
  private Options() {}

  enum Type { STRING, INTEGER, FLOAT, SYMBOL, BOOLEAN, OBJECT }

  static final class Definition {
    final String name;
    final Type type;
    final Object dflt;
    final BiFunction<Object, String, Object> validator;

    Definition(String name, Type type, Object dflt, BiFunction<Object, String, Object> validator) {
      this.name = name;
      this.type = type;
      this.dflt = dflt;
      this.validator = validator;
    }
  }

  private static final Map<String, Definition> DEFS = new LinkedHashMap<>();

  private static void define(String name, Type type, Object dflt) {
    define(name, type, dflt, null);
  }

  private static void define(String name, Type type, Object dflt, BiFunction<Object, String, Object> v) {
    DEFS.put(name, new Definition(name, type, dflt, v));
  }

  public static boolean isDefined(String name) {
    return DEFS.containsKey(name);
  }

  static final List<Object> TOC_LEVELS_ARRAY = List.of(1L, 2L, 3L, 4L, 5L, 6L);
  static final List<Object> SMART_QUOTES_ENTITIES = List.of("lsquo", "rsquo", "ldquo", "rdquo");
  static final String SMART_QUOTES_STR = "lsquo,rsquo,ldquo,rdquo";

  static {
    define("template", Type.STRING, "");
    define("auto_ids", Type.BOOLEAN, true);
    define("auto_id_stripping", Type.BOOLEAN, false);
    define("auto_id_prefix", Type.STRING, "");
    // 2.6.1: false, true (= {content: '', position: :start}) or a hash with :content and :position
    define("header_links", Type.OBJECT, false, (val, name) -> {
      if (val instanceof Boolean && !((Boolean) val)) {
        return val;
      }
      if (val instanceof Boolean) {
        Map<Object, Object> h = new LinkedHashMap<>();
        h.put("content", "");
        h.put("position", "start");
        return h;
      }
      return symbolizeKeys(simpleHashValidator(val, "header_links"));
    });
    define("transliterated_header_ids", Type.BOOLEAN, false);
    define("parse_block_html", Type.BOOLEAN, false);
    define("parse_span_html", Type.BOOLEAN, true);
    define("html_to_native", Type.BOOLEAN, false);
    define("link_defs", Type.OBJECT, new LinkedHashMap<>(), (val, name) -> {
      Map<Object, Object> h = simpleHashValidator(val, "link_defs");
      for (Map.Entry<Object, Object> e : h.entrySet()) {
        Object v = e.getValue();
        if (!(v instanceof List) || ((List<?>) v).size() > 2 || ((List<?>) v).isEmpty()) {
          throw new KramdownError("Invalid structure for hash value of option " + name);
        }
        List<Object> l = new ArrayList<>((List<?>) v);
        if (l.size() == 1) {
          l.add(null);
        }
        e.setValue(l);
      }
      return h;
    });
    define("footnote_nr", Type.INTEGER, 1L);
    define("entity_output", Type.SYMBOL, "as_char");
    define("toc_levels", Type.OBJECT, TOC_LEVELS_ARRAY, (val, name) -> {
      List<Object> res;
      if (val instanceof String) {
        String s = (String) val;
        Matcher m = Pattern.compile("^(\\d)\\.\\.(\\d)$", RubyStr.FLAGS).matcher(s);
        if (m.find()) {
          res = new ArrayList<>();
          for (long i = Long.parseLong(m.group(1)); i <= Long.parseLong(m.group(2)); i++) {
            res.add(i);
          }
        } else if (Pattern.compile("^\\d(?:,\\d)*$", RubyStr.FLAGS).matcher(s).find()) {
          LinkedHashSet<Object> set = new LinkedHashSet<>();
          for (String p : RubyStr.split(s, ",", 0)) {
            set.add(RubyStr.toI(p));
          }
          res = new ArrayList<>(set);
        } else {
          throw new KramdownError("Invalid syntax for option toc_levels");
        }
      } else if (val instanceof List) {
        LinkedHashSet<Object> set = new LinkedHashSet<>();
        for (Object o : (List<?>) val) {
          set.add(toI(o));
        }
        res = new ArrayList<>(set);
      } else {
        throw new KramdownError("Invalid type " + rubyClass(val) + " for option toc_levels");
      }
      for (Object o : res) {
        long i = (Long) o;
        if (i < 1 || i > 6) {
          throw new KramdownError("Level numbers for option toc_levels have to be integers from 1 to 6");
        }
      }
      return res;
    });
    define("line_width", Type.INTEGER, 72L);
    define("latex_headers", Type.OBJECT,
        List.of("section", "subsection", "subsubsection", "paragraph", "subparagraph", "subparagraph"),
        (val, name) -> simpleArrayValidator(val, "latex_headers", 6));
    define("smart_quotes", Type.OBJECT, SMART_QUOTES_ENTITIES, (val, name) -> {
      if (SMART_QUOTES_STR.equals(val) || SMART_QUOTES_ENTITIES.equals(val)) {
        return SMART_QUOTES_ENTITIES;
      }
      List<Object> l = simpleArrayValidator(val, "smart_quotes", 4);
      List<Object> out = new ArrayList<>();
      for (Object v : l) {
        out.add(integerOrSelf(v));
      }
      return out;
    });
    define("typographic_symbols", Type.OBJECT, new LinkedHashMap<>(), (val, name) -> {
      Map<Object, Object> h = simpleHashValidator(val, "typographic_symbols");
      Map<Object, Object> out = new LinkedHashMap<>();
      for (Map.Entry<Object, Object> e : h.entrySet()) {
        Object k = e.getKey() instanceof String ? strToSym((String) e.getKey()) : e.getKey();
        out.put(k, toS(e.getValue()));
      }
      return out;
    });
    define("remove_block_html_tags", Type.BOOLEAN, true);
    define("remove_span_html_tags", Type.BOOLEAN, false);
    define("header_offset", Type.INTEGER, 0L);
    define("syntax_highlighter", Type.SYMBOL, "rouge");
    define("syntax_highlighter_opts", Type.OBJECT, new LinkedHashMap<>(), (val, name) -> symbolizeKeys(
        simpleHashValidator(val, "syntax_highlighter_opts")));
    define("math_engine", Type.SYMBOL, "mathjax");
    define("math_engine_opts", Type.OBJECT, new LinkedHashMap<>(), (val, name) -> symbolizeKeys(
        simpleHashValidator(val, "math_engine_opts")));
    define("footnote_backlink", Type.STRING, "&#8617;");
    define("footnote_backlink_inline", Type.BOOLEAN, false);
    define("footnote_prefix", Type.STRING, "");
    define("footnote_link_text", Type.STRING, "%s", (val, name) -> {
      if (!((String) val).contains("%s")) {
        throw new KramdownError("option footnote_link_text needs to contain a '%s'");
      }
      return val;
    });
    define("remove_line_breaks_for_cjk", Type.BOOLEAN, false);
    define("forbidden_inline_options", Type.OBJECT, List.of("template"), (val, name) -> {
      if (val instanceof List) {
        List<Object> l = new ArrayList<>();
        for (Object o : (List<?>) val) {
          l.add(o instanceof String ? strToSym((String) o) : o);
        }
        val = l;
      } else if (val instanceof String) {
        throw new KramdownError("undefined method 'map!' for an instance of String");
      }
      return simpleArrayValidator(val, "forbidden_inline_options", -1);
    });
    define("list_indent", Type.INTEGER, 2L);
    // kramdown-parser-gfm options
    define("hard_wrap", Type.BOOLEAN, true);
    define("gfm_quirks", Type.OBJECT, List.of("paragraph_end"), (val, name) -> {
      List<Object> l = simpleArrayValidator(val, "gfm_quirks", -1);
      List<Object> out = new ArrayList<>();
      for (Object v : l) {
        out.add(strToSym(toS(v)));
      }
      return out;
    });
  }

  /** Options.defaults (a fresh mutable copy). */
  static Map<String, Object> defaults() {
    Map<String, Object> m = new LinkedHashMap<>();
    for (Definition d : DEFS.values()) {
      m.put(d.name, d.dflt);
    }
    return m;
  }

  /** Options.merge(hash): defaults overlaid with parsed values (unknown keys kept as is). */
  static Map<String, Object> merge(Map<String, Object> hash) {
    Map<String, Object> temp = defaults();
    for (Map.Entry<String, Object> e : hash.entrySet()) {
      String k = e.getKey();
      temp.put(k, DEFS.containsKey(k) ? parse(k, e.getValue()) : e.getValue());
    }
    return temp;
  }

  /** Options.parse(name, data). */
  static Object parse(String name, Object data) {
    Definition d = DEFS.get(name);
    if (d == null) {
      throw new IllegalArgumentException("No option named " + name + " defined");
    }
    if (!isType(d.type, data)) {
      String s = toS(data);
      switch (d.type) {
        case STRING:
          data = s;
          break;
        case INTEGER:
          try {
            data = RubyStr.rubyInteger(s);
          } catch (NumberFormatException ex) {
            throw new KramdownError("Invalid integer value for option '" + name + "': '" + s + "'");
          }
          break;
        case FLOAT:
          try {
            data = Double.parseDouble(RubyStr.strip(s));
          } catch (NumberFormatException ex) {
            throw new KramdownError("Invalid float value for option '" + name + "': '" + s + "'");
          }
          break;
        case SYMBOL:
          data = strToSym(s);
          break;
        case BOOLEAN:
          data = !RubyStr.strip(RubyStr.downcase(s)).equals("false") && !s.isEmpty();
          break;
        default:
          break;
      }
    }
    if (d.validator != null) {
      data = d.validator.apply(data, name);
    }
    return data;
  }

  private static boolean isType(Type t, Object data) {
    switch (t) {
      case STRING:
        return data instanceof String;
      case INTEGER:
        return data instanceof Long || data instanceof Integer;
      case FLOAT:
        return data instanceof Double;
      case SYMBOL:
        return false; // JSON/YAML never produce Ruby Symbols; strings are coerced via str_to_sym
      case BOOLEAN:
        return data instanceof Boolean;
      default:
        return true;
    }
  }

  /** Options.str_to_sym */
  static String strToSym(String data) {
    data = RubyStr.strip(data);
    if (data.startsWith(":")) {
      data = data.substring(1);
    }
    return data.isEmpty() || data.equals("nil") ? null : data;
  }

  @SuppressWarnings("unchecked")
  static List<Object> simpleArrayValidator(Object val, String name, int size) {
    List<Object> l;
    if (val instanceof String) {
      l = new ArrayList<>(RubyStr.split((String) val, ",", 0));
    } else if (val instanceof List) {
      l = new ArrayList<>((List<Object>) val);
    } else {
      throw new KramdownError("Invalid type " + rubyClass(val) + " for option " + name);
    }
    if (size >= 0 && l.size() != size) {
      throw new KramdownError("Option " + name + " needs exactly " + size + " values");
    }
    return l;
  }

  @SuppressWarnings("unchecked")
  static Map<Object, Object> simpleHashValidator(Object val, String name) {
    if (val instanceof String) {
      try {
        val = MiniYaml.parse((String) val);
      } catch (RuntimeException e) {
        throw new KramdownError("Invalid YAML value for option " + name);
      }
    }
    if (!(val instanceof Map)) {
      throw new KramdownError("Invalid type " + rubyClass(val) + " for option " + name);
    }
    return new LinkedHashMap<>((Map<Object, Object>) val);
  }

  private static Map<Object, Object> symbolizeKeys(Map<Object, Object> h) {
    Map<Object, Object> out = new LinkedHashMap<>();
    for (Map.Entry<Object, Object> e : h.entrySet()) {
      Object k = e.getKey() instanceof String ? strToSym((String) e.getKey()) : e.getKey();
      out.put(k, e.getValue());
    }
    return out;
  }

  private static Object integerOrSelf(Object v) {
    if (v instanceof Long || v instanceof Integer) {
      return ((Number) v).longValue();
    }
    if (v instanceof String) {
      try {
        return RubyStr.rubyInteger((String) v);
      } catch (NumberFormatException e) {
        return v;
      }
    }
    if (v instanceof Double) {
      return (long) Math.floor((Double) v);
    }
    return v;
  }

  private static Object toI(Object o) {
    if (o instanceof Long || o instanceof Integer) {
      return ((Number) o).longValue();
    }
    if (o instanceof Double) {
      return (long) ((Double) o).doubleValue();
    }
    if (o instanceof String) {
      return RubyStr.toI((String) o);
    }
    if (o == null) {
      return 0L;
    }
    throw new KramdownError("undefined method 'to_i'");
  }

  /** Ruby to_s for JSON-ish values. */
  static String toS(Object o) {
    if (o == null) {
      return "";
    }
    if (o instanceof Double) {
      double d = (Double) o;
      if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 1e16) {
        return String.valueOf((long) d) + ".0";
      }
      return String.valueOf(d);
    }
    return String.valueOf(o);
  }

  static String rubyClass(Object o) {
    if (o == null) {
      return "NilClass";
    }
    if (o instanceof String) {
      return "String";
    }
    if (o instanceof Long || o instanceof Integer) {
      return "Integer";
    }
    if (o instanceof Double) {
      return "Float";
    }
    if (o instanceof Boolean) {
      return ((Boolean) o) ? "TrueClass" : "FalseClass";
    }
    if (o instanceof List) {
      return "Array";
    }
    if (o instanceof Map) {
      return "Hash";
    }
    return o.getClass().getSimpleName();
  }

  static List<Object> listOf(Object... o) {
    return new ArrayList<>(Arrays.asList(o));
  }
}
