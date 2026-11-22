package org.hl7.fhir.jekyll.site;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;
import org.hl7.fhir.jekyll.kramdown.Kramdown;
import org.hl7.fhir.jekyll.liquid.Condition;
import org.hl7.fhir.jekyll.liquid.Context;
import org.hl7.fhir.jekyll.liquid.Environment;
import org.hl7.fhir.jekyll.liquid.ErrorMode;
import org.hl7.fhir.jekyll.liquid.Expression;
import org.hl7.fhir.jekyll.liquid.Lexer;
import org.hl7.fhir.jekyll.liquid.LiquidException;
import org.hl7.fhir.jekyll.liquid.ParseContext;
import org.hl7.fhir.jekyll.liquid.ParseOptions;
import org.hl7.fhir.jekyll.liquid.Parser;
import org.hl7.fhir.jekyll.liquid.RubyTime;
import org.hl7.fhir.jekyll.liquid.Utils;
import org.hl7.fhir.jekyll.liquid.Values;
import org.hl7.fhir.jekyll.liquid.Variable;

/**
 * Port of Jekyll::Filters (jekyll-4.4.1/lib/jekyll/filters.rb + filters/{url,date,grouping}_filters.rb),
 * registered over Liquid's standard filters (Jekyll's `sort` replaces Liquid's).
 */
final class JekyllFilters {
  private JekyllFilters() {}

  private static final Pattern FLOAT_LIKE = Pattern.compile("\\A\\s*-?(?:\\d+\\.?\\d*|\\.\\d+)\\s*\\Z", Pattern.UNIX_LINES);
  private static final Pattern INTEGER_LIKE = Pattern.compile("\\A\\s*-?\\d+\\s*\\Z", Pattern.UNIX_LINES);
  private static final Pattern STRIP_INDEX = Pattern.compile("/index\\.html?$", Pattern.MULTILINE | Pattern.UNIX_LINES);
  private static final Pattern ABSOLUTE_URI = Pattern.compile("\\A[A-Za-z][A-Za-z0-9+.-]*:", Pattern.UNIX_LINES);
  private static final Random RANDOM = new Random();

  static void register(Environment env, JekyllRenderer r) {
    env.registerFilter("markdownify", (ctx, in, a) -> Kramdown.toHtml(Values.toS(in), r.kramdownConfig));
    env.registerFilter("smartify", (ctx, in, a) -> {
      throw new JekyllTags.JekyllError("smartify is not supported by the shim (no IG in the corpus uses it)");
    });
    env.registerFilter("sassify", (ctx, in, a) -> {
      throw new JekyllTags.JekyllError("sassify is not supported by the shim");
    });
    env.registerFilter("scssify", (ctx, in, a) -> {
      throw new JekyllTags.JekyllError("scssify is not supported by the shim");
    });
    env.registerFilter("slugify", (ctx, in, a) -> in == null ? null : JekyllStrings.slugify(Values.toS(in), a.isEmpty() || a.get(0) == null ? null : Values.toS(a.get(0))));
    env.registerFilter("xml_escape", (ctx, in, a) -> JekyllStrings.xmlEscape(Values.toS(in)));
    env.registerFilter("cgi_escape", (ctx, in, a) -> JekyllStrings.cgiEscape(Values.toS(in)));
    env.registerFilter("uri_escape", (ctx, in, a) -> in == null ? null : JekyllStrings.uriEscape(Values.toS(in)));
    env.registerFilter("normalize_whitespace", (ctx, in, a) -> JekyllStrings.normalizeWhitespace(Values.toS(in)));
    env.registerFilter("number_of_words", (ctx, in, a) -> numberOfWords(Values.toS(in), a.isEmpty() ? null : Values.toS(a.get(0))));
    env.registerFilter("array_to_sentence_string", (ctx, in, a) -> arrayToSentence(in, a.isEmpty() ? "and" : Values.toS(a.get(0))));
    env.registerFilter("jsonify", (ctx, in, a) -> toJson(asLiquid(in)));
    env.registerFilter("where", (ctx, in, a) -> where(in, arg(a, 0), arg(a, 1)));
    env.registerFilter("where_exp", (ctx, in, a) -> whereExp(ctx, in, Values.toS(arg(a, 0)), Values.toS(arg(a, 1))));
    env.registerFilter("find", (ctx, in, a) -> find(in, arg(a, 0), arg(a, 1)));
    env.registerFilter("find_exp", (ctx, in, a) -> findExp(ctx, in, Values.toS(arg(a, 0)), Values.toS(arg(a, 1))));
    env.registerFilter("to_integer", (ctx, in, a) -> in == Boolean.TRUE ? (Object) 1L : in == Boolean.FALSE ? (Object) 0L : Values.toI(in));
    env.registerFilter("sort", (ctx, in, a) -> sort(in, arg(a, 0), a.size() > 1 ? Values.toS(a.get(1)) : "first"));
    env.registerFilter("pop", (ctx, in, a) -> arrayOp(in, "pop", a.isEmpty() ? 1L : a.get(0)));
    env.registerFilter("push", (ctx, in, a) -> arrayOp(in, "push", arg(a, 0)));
    env.registerFilter("shift", (ctx, in, a) -> arrayOp(in, "shift", a.isEmpty() ? 1L : a.get(0)));
    env.registerFilter("unshift", (ctx, in, a) -> arrayOp(in, "unshift", arg(a, 0)));
    env.registerFilter("sample", (ctx, in, a) -> sample(in, a.isEmpty() ? 1L : a.get(0)));
    env.registerFilter("inspect", (ctx, in, a) -> JekyllStrings.xmlEscape(Values.inspect(in)));
    env.registerFilter("absolute_url", (ctx, in, a) -> in == null ? null : absoluteUrl(r, in));
    env.registerFilter("relative_url", (ctx, in, a) -> in == null ? null : relativeUrl(r, in));
    env.registerFilter("strip_index", (ctx, in, a) -> in == null || Values.toS(in).isEmpty() ? null
        : STRIP_INDEX.matcher(Values.toS(in)).replaceFirst("/"));
    env.registerFilter("date_to_string", (ctx, in, a) -> stringifyDate(in, "%b", strArg(a, 0), strArg(a, 1)));
    env.registerFilter("date_to_long_string", (ctx, in, a) -> stringifyDate(in, "%B", strArg(a, 0), strArg(a, 1)));
    env.registerFilter("date_to_xmlschema", (ctx, in, a) -> Values.toS(in).isEmpty() ? in
        : localTime(in).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx", Locale.ROOT)));
    env.registerFilter("date_to_rfc822", (ctx, in, a) -> Values.toS(in).isEmpty() ? in
        : localTime(in).format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss xx", Locale.ROOT)));
    env.registerFilter("group_by", (ctx, in, a) -> groupBy(in, arg(a, 0)));
    env.registerFilter("group_by_exp", (ctx, in, a) -> groupByExp(ctx, in, Values.toS(arg(a, 0)), Values.toS(arg(a, 1))));
  }

  /** Ruby Object#to_s (Array/Hash -> inspect), as opposed to Liquid's output conversion (Values.toS). */
  static String rubyToS(Object o) {
    return o instanceof List || o instanceof Map ? Values.inspect(o) : Values.toS(o);
  }

  private static Object arg(List<Object> a, int i) {
    return a.size() > i ? a.get(i) : null;
  }

  private static String strArg(List<Object> a, int i) {
    return a.size() > i && a.get(i) != null ? Values.toS(a.get(i)) : null;
  }

  // ------------------------------------------------------------------ where / find / sort helpers

  /** Ruby `input.respond_to?(:select)` for our value model + `input = input.values if Hash`. */
  @SuppressWarnings("unchecked")
  private static List<Object> selectable(Object input) {
    if (input instanceof Map) {
      return new ArrayList<>(((Map<String, Object>) input).values());
    }
    if (input instanceof List) {
      return (List<Object>) input;
    }
    return null;
  }

  static Object where(Object input, Object property, Object value) {
    if (property == null || value instanceof List || value instanceof Map) {
      return input;
    }
    List<Object> items = selectable(input);
    if (items == null) {
      return input;
    }
    List<Object> out = new ArrayList<>();
    for (Object o : items) {
      if (compare(itemProperty(o, property), value)) {
        out.add(o);
      }
    }
    return out;
  }

  static Object find(Object input, Object property, Object value) {
    if (property == null || value instanceof List || value instanceof Map) {
      return input;
    }
    List<Object> items = selectable(input);
    if (items == null) {
      return input;
    }
    for (Object o : items) {
      if (compare(itemProperty(o, property), value)) {
        return o;
      }
    }
    return null;
  }

  /** compare_property_vs_target. */
  @SuppressWarnings("unchecked")
  private static boolean compare(Object property, Object target) {
    if (target == null) {
      return property == null;
    }
    if (target instanceof Expression.MethodLiteral) { // `empty` / `blank`: to_s is ""
      if ("".equals(property)) {
        return true;
      }
      StringBuilder joined = new StringBuilder();
      for (Object p : rubyArray(property)) {
        joined.append(rubyToS(p));
      }
      return joined.length() == 0;
    }
    String t = rubyToS(target);
    if (property instanceof String) {
      return property.equals(t);
    }
    for (Object p : rubyArray(property)) {
      if (rubyToS(p).equals(t)) {
        return true;
      }
    }
    return false;
  }

  /** Ruby Kernel#Array(x). */
  @SuppressWarnings("unchecked")
  private static List<Object> rubyArray(Object x) {
    if (x == null) {
      return List.of();
    }
    if (x instanceof List) {
      return (List<Object>) x;
    }
    if (x instanceof Map) {
      List<Object> pairs = new ArrayList<>();
      for (Map.Entry<String, Object> e : ((Map<String, Object>) x).entrySet()) {
        List<Object> pair = new ArrayList<>();
        pair.add(e.getKey());
        pair.add(e.getValue());
        pairs.add(pair);
      }
      return pairs;
    }
    return List.of(x);
  }

  /** item_property: read_liquid_attribute(item.to_liquid, property.to_s) then parse_sort_input. */
  static Object itemProperty(Object item, Object property) {
    String p = Values.toS(property);
    Object v;
    if (!p.contains(".")) {
      v = rubyIndex(Values.toLiquid(item), p);
    } else {
      Object data = Values.toLiquid(item);
      for (String key : p.split("\\.", -1)) {
        // data.respond_to?(:[]) && data[key]
        if (data == null || data instanceof Boolean || data instanceof Number) {
          data = Boolean.FALSE;
          break;
        }
        data = rubyIndex(data, key);
        if (data == null || data == Boolean.FALSE) {
          break;
        }
      }
      v = data;
    }
    return parseSortInput(v);
  }

  /** Ruby obj[key] with a String key for Hash/String/Array/drops. */
  @SuppressWarnings("unchecked")
  private static Object rubyIndex(Object obj, String key) {
    if (obj instanceof Map) {
      return ((Map<String, Object>) obj).get(key);
    }
    if (obj instanceof String) {
      return ((String) obj).contains(key) ? key : null;
    }
    if (obj instanceof List) {
      throw new JekyllTags.JekyllError("Error accessing object (" + truncate(Values.toS(obj), 20)
          + ") with given key. Expected an integer but got " + Values.inspect(key) + " instead.");
    }
    if (obj == null) {
      throw new JekyllTags.JekyllError("undefined method `[]' for nil:NilClass");
    }
    if (Values.respondsToIndex(obj)) {
      return Values.index(obj, key);
    }
    throw new JekyllTags.JekyllError("undefined method `[]' for " + Values.className(obj));
  }

  private static String truncate(String s, int n) {
    return s.length() <= n ? s : s.substring(0, n);
  }

  /** parse_sort_input: numeric-looking values become numbers for sorting/comparison. */
  private static Object parseSortInput(Object property) {
    String s = rubyToS(property);
    if (INTEGER_LIKE.matcher(s).find()) {
      return Values.toI(property instanceof String ? s : property);
    }
    if (FLOAT_LIKE.matcher(s).find()) {
      return Double.parseDouble(s.strip());
    }
    return property;
  }

  static Object sort(Object input, Object property, String nils) {
    if (input == null) {
      throw LiquidException.argument("Cannot sort a null object.");
    }
    List<Object> items;
    if (input instanceof List) {
      items = new ArrayList<>(selectable(input));
    } else if (input instanceof Map) {
      items = new ArrayList<>(rubyArray(input));
    } else {
      throw new JekyllTags.JekyllError("undefined method `sort' for " + Values.className(input));
    }
    if (property == null) {
      RubySort.msort(items, (a, b) -> {
        Integer c = Values.spaceship(a, b);
        if (c == null) {
          throw Values.cmpErr(a, b);
        }
        return c;
      });
      return items;
    }
    int order;
    if ("first".equals(nils)) {
      order = -1;
    } else if ("last".equals(nils)) {
      order = 1;
    } else {
      throw LiquidException.argument("Invalid nils order: '" + nils + "' is not a valid nils order. It must be 'first' or 'last'.");
    }
    List<Object[]> keyed = new ArrayList<>();
    for (Object o : items) {
      keyed.add(new Object[] {itemProperty(o, property), o});
    }
    final int ord = order;
    RubySort.msort(keyed, (x, y) -> {
      Object a = x[0], b = y[0];
      if (a != null && b == null) {
        return -ord;
      }
      if (a == null && b != null) {
        return ord;
      }
      Integer c = Values.spaceship(a, b);
      return c != null ? c : Values.compareStrings(rubyToS(a), rubyToS(b));
    });
    List<Object> out = new ArrayList<>();
    keyed.forEach(k -> out.add(k[1]));
    return out;
  }

  @SuppressWarnings("unchecked")
  private static Object arrayOp(Object array, String op, Object arg) {
    if (!(array instanceof List)) {
      return array;
    }
    List<Object> a = new ArrayList<>((List<Object>) array);
    switch (op) {
      case "push": a.add(arg); return a;
      case "unshift": a.add(0, arg); return a;
      case "pop": {
        long n = Values.toLong(Utils.toInteger(arg));
        for (long i = 0; i < n && !a.isEmpty(); i++) {
          a.remove(a.size() - 1);
        }
        return a;
      }
      case "shift": {
        long n = Values.toLong(Utils.toInteger(arg));
        for (long i = 0; i < n && !a.isEmpty(); i++) {
          a.remove(0);
        }
        return a;
      }
      default: throw new IllegalStateException(op);
    }
  }

  @SuppressWarnings("unchecked")
  private static Object sample(Object input, Object num) {
    if (!(input instanceof List)) {
      return input;
    }
    List<Object> a = new ArrayList<>((List<Object>) input);
    long n;
    try {
      n = Values.toLong(Utils.toInteger(num));
    } catch (RuntimeException e) {
      n = 1;
    }
    if (n == 1) {
      return a.isEmpty() ? null : a.get(RANDOM.nextInt(a.size()));
    }
    java.util.Collections.shuffle(a, RANDOM);
    return a.subList(0, (int) Math.min(n, a.size()));
  }

  // ------------------------------------------------------------------ *_exp filters (Liquid conditions)

  /** parse_condition / parse_binary_comparison / parse_comparison. */
  private static Condition parseCondition(String exp) {
    Parser parser = new Parser(exp);
    Condition condition = parseComparison(parser);
    Condition first = condition;
    String op;
    while ((op = parser.idIf("and")) != null || (op = parser.idIf("or")) != null) {
      Condition child = parseComparison(parser);
      if (op.equals("and")) {
        condition.and(child);
      } else {
        condition.or(child);
      }
      condition = child;
    }
    parser.consume(Lexer.Type.end_of_string);
    return first;
  }

  private static Condition parseComparison(Parser parser) {
    Object left = Expression.parse(parser.expression());
    String operator = parser.consumeIf(Lexer.Type.comparison);
    if (operator == null) {
      return new Condition(left);
    }
    return new Condition(left, operator, Expression.parse(parser.expression()));
  }

  private static Object whereExp(Context ctx, Object input, String variable, String expression) {
    List<Object> items = selectable(input);
    if (items == null) {
      return input;
    }
    Condition condition = parseCondition(expression);
    return ctx.stack(() -> {
      List<Object> out = new ArrayList<>();
      for (Object o : items) {
        ctx.set(variable, o);
        if (Values.truthy(condition.evaluate(ctx))) {
          out.add(o);
        }
      }
      return out;
    });
  }

  private static Object findExp(Context ctx, Object input, String variable, String expression) {
    List<Object> items = selectable(input);
    if (items == null) {
      return input;
    }
    Condition condition = parseCondition(expression);
    return ctx.stack(() -> {
      for (Object o : items) {
        ctx.set(variable, o);
        if (Values.truthy(condition.evaluate(ctx))) {
          return o;
        }
      }
      return null;
    });
  }

  // ------------------------------------------------------------------ grouping_filters.rb

  private static Object groupBy(Object input, Object property) {
    List<Object> items = groupable(input);
    if (items == null) {
      return input;
    }
    Map<String, List<Object>> groups = new LinkedHashMap<>();
    for (Object o : items) {
      groups.computeIfAbsent(rubyToS(itemProperty(o, property)), k -> new ArrayList<>()).add(o);
    }
    List<Object> out = new ArrayList<>();
    groups.forEach((k, v) -> out.add(group(k, v)));
    return out;
  }

  private static Object groupByExp(Context ctx, Object input, String variable, String expression) {
    List<Object> items = groupable(input);
    if (items == null) {
      return input;
    }
    Variable expr = new Variable(expression, new ParseContext(JekyllRenderer.parseOptions(ctx)));
    return ctx.stack(() -> {
      // Ruby Hash#group_by keys by eql?/hash on the raw rendered value
      List<Object> keys = new ArrayList<>();
      List<List<Object>> vals = new ArrayList<>();
      for (Object o : items) {
        ctx.set(variable, o);
        Object k = expr.render(ctx);
        int idx = -1;
        for (int i = 0; i < keys.size(); i++) {
          if (Values.rubyEql(keys.get(i), k)) {
            idx = i;
            break;
          }
        }
        if (idx < 0) {
          keys.add(k);
          vals.add(new ArrayList<>());
          idx = keys.size() - 1;
        }
        vals.get(idx).add(o);
      }
      List<Object> out = new ArrayList<>();
      for (int i = 0; i < keys.size(); i++) {
        out.add(group(keys.get(i), vals.get(i)));
      }
      return out;
    });
  }

  @SuppressWarnings("unchecked")
  private static List<Object> groupable(Object input) {
    if (input instanceof List) {
      return (List<Object>) input;
    }
    if (input instanceof Map) {
      return rubyArray(input); // Hash#group_by yields [key, value] pairs
    }
    return null;
  }

  private static Map<String, Object> group(Object name, List<Object> items) {
    Map<String, Object> g = new LinkedHashMap<>();
    g.put("name", name);
    g.put("items", items);
    g.put("size", (long) items.size());
    return g;
  }

  // ------------------------------------------------------------------ misc

  private static long numberOfWords(String s, String mode) {
    Pattern cjk = Pattern.compile("[\\p{IsHan}\\p{IsKatakana}\\p{IsHiragana}\\p{IsHangul}]", Pattern.UNIX_LINES);
    Pattern word = Pattern.compile("[^\\p{IsHan}\\p{IsKatakana}\\p{IsHiragana}\\p{IsHangul}\\s]+", Pattern.UNIX_LINES);
    if ("cjk".equals(mode)) {
      return cjk.matcher(s).results().count() + word.matcher(s).results().count();
    }
    if ("auto".equals(mode)) {
      long c = cjk.matcher(s).results().count();
      return c == 0 ? JekyllStrings.numberOfWords(s) : c + word.matcher(s).results().count();
    }
    return JekyllStrings.numberOfWords(s);
  }

  @SuppressWarnings("unchecked")
  private static String arrayToSentence(Object in, String connector) {
    List<Object> a = in instanceof List ? (List<Object>) in : rubyArray(in);
    switch (a.size()) {
      case 0: return "";
      case 1: return Values.toS(a.get(0));
      case 2: return Values.toS(a.get(0)) + " " + connector + " " + Values.toS(a.get(1));
      default: {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < a.size() - 1; i++) {
          if (i > 0) {
            b.append(", ");
          }
          b.append(Values.toS(a.get(i)));
        }
        return b + ", " + connector + " " + Values.toS(a.get(a.size() - 1));
      }
    }
  }

  /** as_liquid: hashes/arrays recursively, drops via to_liquid. */
  @SuppressWarnings("unchecked")
  private static Object asLiquid(Object item) {
    if (item instanceof Map) {
      Map<String, Object> out = new LinkedHashMap<>();
      ((Map<String, Object>) item).forEach((k, v) -> out.put(k, asLiquid(v)));
      return out;
    }
    if (item instanceof List) {
      List<Object> out = new ArrayList<>();
      ((List<Object>) item).forEach(v -> out.add(asLiquid(v)));
      return out;
    }
    Object liquidated = Values.toLiquid(item);
    return liquidated == item ? item : asLiquid(liquidated);
  }

  /** Ruby #to_json (JSON generator defaults: no escaping of "/" or non-ASCII). */
  @SuppressWarnings("unchecked")
  static String toJson(Object v) {
    StringBuilder b = new StringBuilder();
    toJson(v, b);
    return b.toString();
  }

  @SuppressWarnings("unchecked")
  private static void toJson(Object v, StringBuilder b) {
    if (v == null) {
      b.append("null");
    } else if (v instanceof String) {
      jsonQuote((String) v, b);
    } else if (v instanceof Boolean || v instanceof Long || v instanceof Integer || v instanceof java.math.BigInteger) {
      b.append(v);
    } else if (v instanceof Double) {
      double d = (Double) v;
      if (Double.isNaN(d) || Double.isInfinite(d)) {
        throw new JekyllTags.JekyllError(Values.floatToS(d) + " not allowed in JSON");
      }
      b.append(Values.floatToS(d));
    } else if (v instanceof Map) {
      b.append('{');
      boolean first = true;
      for (Map.Entry<String, Object> e : ((Map<String, Object>) v).entrySet()) {
        if (!first) {
          b.append(',');
        }
        first = false;
        jsonQuote(e.getKey(), b);
        b.append(':');
        toJson(e.getValue(), b);
      }
      b.append('}');
    } else if (v instanceof List) {
      b.append('[');
      boolean first = true;
      for (Object o : (List<Object>) v) {
        if (!first) {
          b.append(',');
        }
        first = false;
        toJson(o, b);
      }
      b.append(']');
    } else {
      jsonQuote(Values.toS(v), b);
    }
  }

  private static void jsonQuote(String s, StringBuilder b) {
    b.append('"');
    for (int k = 0; k < s.length(); k++) {
      char c = s.charAt(k);
      switch (c) {
        case '"': b.append("\\\""); break;
        case '\\': b.append("\\\\"); break;
        case '\n': b.append("\\n"); break;
        case '\r': b.append("\\r"); break;
        case '\t': b.append("\\t"); break;
        case '\b': b.append("\\b"); break;
        case '\f': b.append("\\f"); break;
        default:
          if (c < 0x20) {
            b.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
          } else {
            b.append(c);
          }
      }
    }
    b.append('"');
  }

  // ------------------------------------------------------------------ url_filters.rb (approximation, see TRACKER)

  private static Object urlOf(Object input) {
    if (input instanceof Map && ((Map<?, ?>) input).containsKey("url")) {
      return ((Map<?, ?>) input).get("url");
    }
    return input;
  }

  private static String relativeUrl(JekyllRenderer r, Object input) {
    String s = Values.toS(urlOf(input));
    if (ABSOLUTE_URI.matcher(s).find()) {
      return s;
    }
    Object baseurl = r.site.config.get("baseurl");
    String base = baseurl == null ? "" : chomp(Values.toS(baseurl), "/");
    return ensureLeadingSlash(base) + ensureLeadingSlash(s);
  }

  private static String absoluteUrl(JekyllRenderer r, Object input) {
    String s = Values.toS(urlOf(input));
    if (ABSOLUTE_URI.matcher(s).find()) {
      return s;
    }
    Object siteUrl = r.site.config.get("url");
    if (siteUrl == null || Values.toS(siteUrl).isEmpty()) {
      return relativeUrl(r, input);
    }
    return Values.toS(siteUrl) + relativeUrl(r, input);
  }

  private static String chomp(String s, String suffix) {
    return s.endsWith(suffix) ? s.substring(0, s.length() - suffix.length()) : s;
  }

  private static String ensureLeadingSlash(String s) {
    return s.isEmpty() || s.startsWith("/") ? s : "/" + s;
  }

  // ------------------------------------------------------------------ date_filters.rb

  private static ZonedDateTime localTime(Object input) {
    if (input instanceof org.hl7.fhir.jekyll.yaml.RubyDate) { // Date#to_time: local midnight
      return ((org.hl7.fhir.jekyll.yaml.RubyDate) input).toTime().instant().atZone(ZoneId.systemDefault());
    }
    RubyTime t = Utils.toDate(input);
    if (t == null) {
      throw new JekyllTags.JekyllError("Invalid Date: '" + Values.inspect(input) + "' is not a valid datetime.");
    }
    return t.instant().atZone(ZoneId.systemDefault());
  }

  private static Object stringifyDate(Object date, String monthType, String type, String style) {
    if (Values.toS(date).isEmpty()) {
      return date;
    }
    RubyTime time = RubyTime.of(localTime(date));
    if ("ordinal".equals(type)) {
      int day = localTime(date).getDayOfMonth();
      String ordinalDay = day + ordinal(day);
      if ("US".equals(style)) {
        return time.strftime(monthType + " " + ordinalDay + ", %Y");
      }
      return time.strftime(ordinalDay + " " + monthType + " %Y");
    }
    return time.strftime("%d " + monthType + " %Y");
  }

  private static String ordinal(int n) {
    if (n >= 11 && n <= 13) {
      return "th";
    }
    switch (n % 10) {
      case 1: return "st";
      case 2: return "nd";
      case 3: return "rd";
      default: return "th";
    }
  }

  /** Unused helper kept for symmetry with Ruby's sort_input comparator shape. */
  static Comparator<Object> rubyComparator() {
    return (a, b) -> {
      Integer c = Values.spaceship(a, b);
      return c == null ? 0 : c;
    };
  }
}
