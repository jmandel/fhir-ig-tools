package org.hl7.fhir.jekyll.liquid;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Port of liquid-4.0.4/lib/liquid/standardfilters.rb. Each Ruby method becomes a {@link Filter}
 * with Ruby's arity check (given/expected count the input), so a wrong argument count produces
 * Ruby's "wrong number of arguments (given 1, expected 2)" Liquid::ArgumentError.
 */
public final class StandardFilters {
  private StandardFilters() {}

  static final Pattern HTML_ESCAPE_ONCE_REGEXP = Pattern.compile("[\"><']|&(?!([a-zA-Z]+|(#\\d+));)", Pattern.UNIX_LINES);
  static final Pattern STRIP_HTML_BLOCKS = Pattern.compile("(?s:<script.*?</script>)|(?s:<!--.*?-->)|(?s:<style.*?</style>)", Pattern.UNIX_LINES);
  static final Pattern STRIP_HTML_TAGS = Pattern.compile("(?s)<.*?>", Pattern.UNIX_LINES);
  static final Pattern NEWLINES = Pattern.compile("\\r?\\n", Pattern.UNIX_LINES);

  /** A filter body after the arity check: args padded with nulls up to max. */
  @FunctionalInterface
  interface Body {
    Object apply(Context ctx, Object input, List<Object> args);
  }

  private static final Object DEFAULT = new Object();

  /** Registers a filter whose Ruby method takes {@code min..max} arguments (input excluded). */
  static Filter def(int min, int max, Body body) {
    return (ctx, input, args) -> {
      int given = args.size();
      if (given < min || max >= 0 && given > max) {
        throw RubyError.arity(given + 1, min + 1, max < 0 ? -1 : max + 1);
      }
      return body.apply(ctx, input, args);
    };
  }

  /** The i-th argument, or the Ruby default when it was not passed. */
  static Object arg(List<Object> args, int i, Object dflt) {
    return i < args.size() ? args.get(i) : dflt;
  }

  public static void register(Environment e) {
    e.registerFilter("size", def(0, 0, (c, input, a) -> size(input)));
    e.registerFilter("downcase", def(0, 0, (c, input, a) -> RubyStrings.downcase(Values.rubyToS(input))));
    e.registerFilter("upcase", def(0, 0, (c, input, a) -> RubyStrings.upcase(Values.rubyToS(input))));
    e.registerFilter("capitalize", def(0, 0, (c, input, a) -> RubyStrings.capitalize(Values.rubyToS(input))));
    e.registerFilter("escape", def(0, 0, (c, input, a) -> escape(input)));
    e.registerFilter("h", def(0, 0, (c, input, a) -> escape(input)));
    e.registerFilter("escape_once", def(0, 0, (c, input, a) -> escapeOnce(input)));
    e.registerFilter("url_encode", def(0, 0, (c, input, a) -> input == null ? null : cgiEscape(Values.rubyToS(input))));
    e.registerFilter("url_decode", def(0, 0, (c, input, a) -> urlDecode(input)));
    e.registerFilter("slice", def(1, 2, (c, input, a) -> slice(input, a.get(0), arg(a, 1, null))));
    e.registerFilter("truncate", def(0, 2, (c, input, a) -> truncate(input, arg(a, 0, 50L), arg(a, 1, "..."))));
    e.registerFilter("truncatewords", def(0, 2, (c, input, a) -> truncatewords(input, arg(a, 0, 15L), arg(a, 1, "..."))));
    e.registerFilter("split", def(1, 1, (c, input, a) -> RubyStrings.split(Values.rubyToS(input), Values.rubyToS(a.get(0)))));
    e.registerFilter("strip", def(0, 0, (c, input, a) -> RubyStrings.strip(Values.rubyToS(input))));
    e.registerFilter("lstrip", def(0, 0, (c, input, a) -> RubyStrings.lstrip(Values.rubyToS(input))));
    e.registerFilter("rstrip", def(0, 0, (c, input, a) -> RubyStrings.rstrip(Values.rubyToS(input))));
    e.registerFilter("strip_html", def(0, 0, (c, input, a) -> stripHtml(input)));
    e.registerFilter("strip_newlines", def(0, 0, (c, input, a) -> NEWLINES.matcher(Values.rubyToS(input)).replaceAll("")));
    e.registerFilter("join", def(0, 1, (c, input, a) -> Values.join(inputIterator(input), Values.rubyToS(arg(a, 0, " ")))));
    e.registerFilter("sort", def(0, 1, (c, input, a) -> sort(input, arg(a, 0, null), false)));
    e.registerFilter("sort_natural", def(0, 1, (c, input, a) -> sort(input, arg(a, 0, null), true)));
    e.registerFilter("where", def(1, 2, (c, input, a) -> where(input, a.get(0), arg(a, 1, null))));
    e.registerFilter("uniq", def(0, 1, (c, input, a) -> uniq(input, arg(a, 0, null))));
    e.registerFilter("reverse", def(0, 0, (c, input, a) -> {
      List<Object> l = new ArrayList<>(inputIterator(input));
      Collections.reverse(l);
      return l;
    }));
    e.registerFilter("map", def(1, 1, (c, input, a) -> map(c, input, a.get(0))));
    e.registerFilter("compact", def(0, 1, (c, input, a) -> compact(input, arg(a, 0, null))));
    e.registerFilter("replace", def(1, 2, (c, input, a) -> RubyStrings.gsub(Values.rubyToS(input), Values.rubyToS(a.get(0)), Values.rubyToS(arg(a, 1, "")))));
    e.registerFilter("replace_first", def(1, 2, (c, input, a) -> RubyStrings.sub(Values.rubyToS(input), Values.rubyToS(a.get(0)), Values.rubyToS(arg(a, 1, "")))));
    e.registerFilter("remove", def(1, 1, (c, input, a) -> RubyStrings.gsubLiteral(Values.rubyToS(input), Values.rubyToS(a.get(0)), "")));
    e.registerFilter("remove_first", def(1, 1, (c, input, a) -> RubyStrings.subLiteral(Values.rubyToS(input), Values.rubyToS(a.get(0)), "")));
    e.registerFilter("append", def(1, 1, (c, input, a) -> Values.rubyToS(input) + Values.rubyToS(a.get(0))));
    e.registerFilter("concat", def(1, 1, (c, input, a) -> concat(input, a.get(0))));
    e.registerFilter("prepend", def(1, 1, (c, input, a) -> Values.rubyToS(a.get(0)) + Values.rubyToS(input)));
    e.registerFilter("newline_to_br", def(0, 0, (c, input, a) -> Values.rubyToS(input).replace("\n", "<br />\n")));
    e.registerFilter("date", def(1, 1, (c, input, a) -> date(input, a.get(0))));
    e.registerFilter("first", def(0, 0, (c, input, a) -> Values.respondsTo(input, "first") ? Values.send(input, "first") : null));
    e.registerFilter("last", def(0, 0, (c, input, a) -> Values.respondsTo(input, "last") ? Values.send(input, "last") : null));
    e.registerFilter("abs", def(0, 0, (c, input, a) -> toF(abs(Utils.toNumber(input)))));
    e.registerFilter("plus", def(1, 1, (c, input, a) -> applyOperation(input, a.get(0), '+')));
    e.registerFilter("minus", def(1, 1, (c, input, a) -> applyOperation(input, a.get(0), '-')));
    e.registerFilter("times", def(1, 1, (c, input, a) -> applyOperation(input, a.get(0), '*')));
    e.registerFilter("divided_by", def(1, 1, (c, input, a) -> zeroDiv(() -> applyOperation(input, a.get(0), '/'))));
    e.registerFilter("modulo", def(1, 1, (c, input, a) -> zeroDiv(() -> applyOperation(input, a.get(0), '%'))));
    e.registerFilter("round", def(0, 1, (c, input, a) -> round(input, arg(a, 0, 0L))));
    e.registerFilter("ceil", def(0, 0, (c, input, a) -> ceilFloor(input, RoundingMode.CEILING)));
    e.registerFilter("floor", def(0, 0, (c, input, a) -> ceilFloor(input, RoundingMode.FLOOR)));
    e.registerFilter("at_least", def(1, 1, (c, input, a) -> atLeastMost(input, a.get(0), true)));
    e.registerFilter("at_most", def(1, 1, (c, input, a) -> atLeastMost(input, a.get(0), false)));
    e.registerFilter("default", def(0, 1, (c, input, a) -> {
      Object dflt = arg(a, 0, "");
      if (!Values.truthy(input) || Values.respondsTo(input, "empty?") && Values.truthy(Values.send(input, "empty?"))) {
        return dflt;
      }
      return input;
    }));
  }

  // ---------------------------------------------------------------- helpers

  static Object size(Object input) {
    return Values.respondsTo(input, "size") ? Values.send(input, "size") : 0L;
  }

  /** CGI.escapeHTML. */
  public static String escapeHtml(String s) {
    StringBuilder sb = null;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      String rep;
      switch (c) {
        case '&': rep = "&amp;"; break;
        case '<': rep = "&lt;"; break;
        case '>': rep = "&gt;"; break;
        case '"': rep = "&quot;"; break;
        case '\'': rep = "&#39;"; break;
        default: rep = null;
      }
      if (rep != null) {
        if (sb == null) {
          sb = new StringBuilder(s.length() + 16);
          sb.append(s, 0, i);
        }
        sb.append(rep);
      } else if (sb != null) {
        sb.append(c);
      }
    }
    return sb == null ? s : sb.toString();
  }

  static Object escape(Object input) {
    return input == null ? null : escapeHtml(Values.rubyToS(input));
  }

  static String escapeOnce(Object input) {
    Matcher m = HTML_ESCAPE_ONCE_REGEXP.matcher(Values.rubyToS(input));
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      m.appendReplacement(sb, Matcher.quoteReplacement(escapeHtml(m.group())));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  /** CGI.escape: everything but {@code a-zA-Z0-9_.-~} percent-encoded (UTF-8), space as '+'. */
  public static String cgiEscape(String s) {
    StringBuilder sb = new StringBuilder();
    for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
      int c = b & 0xff;
      if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_' || c == '.' || c == '-' || c == '~') {
        sb.append((char) c);
      } else if (c == ' ') {
        sb.append('+');
      } else {
        sb.append('%').append(Character.toUpperCase(Character.forDigit(c >> 4, 16))).append(Character.toUpperCase(Character.forDigit(c & 15, 16)));
      }
    }
    return sb.toString();
  }

  static Object urlDecode(Object input) {
    if (input == null) {
      return null;
    }
    String s = Values.rubyToS(input).replace('+', ' ');
    byte[] src = s.getBytes(StandardCharsets.UTF_8);
    ByteArrayOutputStream out = new ByteArrayOutputStream(src.length);
    for (int i = 0; i < src.length; i++) {
      if (src[i] == '%' && i + 2 < src.length && hex(src[i + 1]) >= 0 && hex(src[i + 2]) >= 0) {
        out.write(hex(src[i + 1]) * 16 + hex(src[i + 2]));
        i += 2;
      } else {
        out.write(src[i]);
      }
    }
    try {
      return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(out.toByteArray())).toString();
    } catch (CharacterCodingException e) {
      throw LiquidException.argument("invalid byte sequence in UTF-8");
    }
  }

  private static int hex(byte b) {
    return Character.digit((char) b, 16);
  }

  static Object slice(Object input, Object offsetArg, Object lengthArg) {
    long offset = Values.toLong(Utils.toInteger(offsetArg));
    long length = Values.truthy(lengthArg) ? Values.toLong(Utils.toInteger(lengthArg)) : 1;
    if (input instanceof List) {
      List<?> l = (List<?>) input;
      int n = l.size();
      long off = offset < 0 ? offset + n : offset;
      if (off < 0 || off > n || length < 0) {
        return new ArrayList<>();
      }
      return new ArrayList<Object>(l.subList((int) off, (int) Math.min(n, off + length)));
    }
    String r = RubyStrings.slice(Values.rubyToS(input), offset, length);
    return r == null ? "" : r;
  }

  static Object truncate(Object input, Object lengthArg, Object truncateString) {
    if (input == null) {
      return null;
    }
    String inputStr = Values.rubyToS(input);
    long length = Values.toLong(Utils.toInteger(lengthArg));
    String ts = Values.rubyToS(truncateString);
    long l = length - RubyStrings.length(ts);
    if (l < 0) {
      l = 0;
    }
    int len = RubyStrings.length(inputStr);
    return len > length ? RubyStrings.substringCp(inputStr, 0, (int) Math.min(l, len)) + ts : inputStr;
  }

  static Object truncatewords(Object input, Object wordsArg, Object truncateString) {
    if (input == null) {
      return null;
    }
    List<Object> wordlist = RubyStrings.awkSplit(Values.rubyToS(input));
    long words = Values.toLong(Utils.toInteger(wordsArg));
    long l = words - 1;
    if (l < 0) {
      l = 0;
    }
    if (wordlist.size() > l) {
      return Values.join(wordlist.subList(0, (int) Math.min(wordlist.size(), l + 1)), " ") + Values.rubyToS(truncateString);
    }
    return input;
  }

  static String stripHtml(Object input) {
    String result = STRIP_HTML_BLOCKS.matcher(Values.rubyToS(input)).replaceAll("");
    return STRIP_HTML_TAGS.matcher(result).replaceAll("");
  }

  /** Ruby {@code InputIterator.new(input)} enumerated, with to_liquid applied. */
  static List<Object> inputIterator(Object input) {
    List<Object> src;
    if (input instanceof List) {
      src = Values.flatten((List<?>) input);
    } else if (input instanceof Map) {
      src = new ArrayList<>(Collections.singletonList(input));
    } else if (input instanceof RubyRange) {
      src = ((RubyRange) input).toList();
    } else if (input instanceof Utils.Enumerable) {
      src = new ArrayList<>();
      for (Object o : ((Utils.Enumerable) input).each()) {
        src.add(o);
      }
    } else if (input == null) {
      src = new ArrayList<>();
    } else {
      src = new ArrayList<>(Collections.singletonList(input));
    }
    List<Object> out = new ArrayList<>(src.size());
    for (Object o : src) {
      out.add(Values.toLiquid(o));
    }
    return out;
  }

  private static RuntimeException propertyError(Object property) {
    return LiquidException.argument("cannot select the property '" + Values.rubyToS(property) + "'");
  }

  /** {@code item[property]}, turning ::TypeError into the property error like the Ruby rescue. */
  private static Object prop(Object item, Object property) {
    try {
      return Values.index(item, property);
    } catch (RubyError e) {
      if ("TypeError".equals(e.rubyClass())) {
        throw propertyError(property);
      }
      throw e;
    }
  }

  private static boolean allRespondToIndex(List<Object> ary) {
    for (Object o : ary) {
      if (!Values.respondsToIndex(o)) {
        return false;
      }
    }
    return true;
  }

  /** Returns null when not comparable; the sort then raises "comparison of A with B failed" for the elements. */
  static Integer nilSafeCompare(Object a, Object b) {
    if (a != null && b != null) {
      return Values.spaceship(a, b);
    }
    return a == null ? 1 : -1;
  }

  static Integer nilSafeCasecmp(Object a, Object b) {
    if (a != null && b != null) {
      return Integer.signum(RubyStrings.casecmp(Values.rubyToS(a), Values.rubyToS(b)));
    }
    return a == null ? 1 : -1;
  }

  static Object sort(Object input, Object property, boolean natural) {
    List<Object> ary = inputIterator(input);
    if (ary.isEmpty()) {
      return new ArrayList<>();
    }
    if (property == null) {
      return msort(ary, natural ? StandardFilters::nilSafeCasecmp : StandardFilters::nilSafeCompare);
    } else if (allRespondToIndex(ary)) {
      return msort(ary, (a, b) -> natural ? nilSafeCasecmp(prop(a, property), prop(b, property)) : nilSafeCompare(prop(a, property), prop(b, property)));
    }
    return null;
  }

  /**
   * Ruby's Array#sort with a block goes to glibc qsort_r, i.e. a top-down merge sort
   * (msort_with_tmp). Reproduced exactly so ties and inconsistent comparators (nil_safe_compare
   * says nil > nil) order elements the same way.
   */
  /** A sort block: Ruby {@code a <=> b} style result, null meaning "not comparable". */
  interface SortBlock {
    Integer compare(Object a, Object b);
  }

  static List<Object> msort(List<Object> list, SortBlock block) {
    Comparator<Object> cmp = (a, b) -> {
      Integer c = block.compare(a, b);
      if (c == null) {
        throw Values.cmpErr(a, b);
      }
      return c;
    };
    Object[] b = list.toArray();
    Object[] tmp = new Object[b.length];
    msort(b, 0, b.length, tmp, cmp);
    List<Object> out = new ArrayList<>(b.length);
    Collections.addAll(out, b);
    return out;
  }

  private static void msort(Object[] b, int off, int n, Object[] tmp, Comparator<Object> cmp) {
    if (n <= 1) {
      return;
    }
    int n1 = n / 2, n2 = n - n1;
    int b1 = off, b2 = off + n1;
    msort(b, b1, n1, tmp, cmp);
    msort(b, b2, n2, tmp, cmp);
    int t = 0;
    while (n1 > 0 && n2 > 0) {
      if (cmp.compare(b[b1], b[b2]) <= 0) {
        tmp[t++] = b[b1++];
        n1--;
      } else {
        tmp[t++] = b[b2++];
        n2--;
      }
    }
    while (n1 > 0) {
      tmp[t++] = b[b1++];
      n1--;
    }
    System.arraycopy(tmp, 0, b, off, n - n2);
  }

  static Object where(Object input, Object property, Object targetValue) {
    List<Object> ary = inputIterator(input);
    if (ary.isEmpty()) {
      return new ArrayList<>();
    }
    if (Values.respondsToIndex(ary.get(0))) {
      List<Object> out = new ArrayList<>();
      for (Object item : ary) {
        Object v = prop(item, property);
        if (targetValue == null ? Values.truthy(v) : Values.rubyEquals(v, targetValue)) {
          out.add(item);
        }
      }
      return out;
    }
    return null;
  }

  static Object uniq(Object input, Object property) {
    List<Object> ary = inputIterator(input);
    if (property == null) {
      return uniqBy(ary, x -> x);
    } else if (ary.isEmpty()) {
      return new ArrayList<>();
    } else if (Values.respondsToIndex(ary.get(0))) {
      return uniqBy(ary, x -> prop(x, property));
    }
    return null;
  }

  private static List<Object> uniqBy(List<Object> ary, java.util.function.Function<Object, Object> key) {
    if (ary.size() <= 1) {
      return new ArrayList<>(ary); // Ruby's Array#uniq doesn't call the block for <= 1 elements
    }
    Map<Values.EqlKey, Boolean> seen = new LinkedHashMap<>();
    List<Object> out = new ArrayList<>();
    for (Object o : ary) {
      if (seen.put(new Values.EqlKey(key.apply(o)), Boolean.TRUE) == null) {
        out.add(o);
      }
    }
    return out;
  }

  static Object map(Context ctx, Object input, Object property) {
    List<Object> out = new ArrayList<>();
    for (Object e : inputIterator(input)) {
      if (e instanceof LiquidProc) {
        e = ((LiquidProc) e).call(ctx);
      }
      if ("to_liquid".equals(property)) {
        out.add(e);
      } else if (Values.respondsToIndex(e)) {
        Object r = prop(e, property);
        out.add(r instanceof LiquidProc ? ((LiquidProc) r).call(ctx) : r);
      } else {
        out.add(null);
      }
    }
    return out;
  }

  static Object compact(Object input, Object property) {
    List<Object> ary = inputIterator(input);
    if (property == null) {
      List<Object> out = new ArrayList<>();
      for (Object o : ary) {
        if (o != null) {
          out.add(o);
        }
      }
      return out;
    } else if (ary.isEmpty()) {
      return new ArrayList<>();
    } else if (Values.respondsToIndex(ary.get(0))) {
      List<Object> out = new ArrayList<>();
      for (Object o : ary) {
        if (prop(o, property) != null) {
          out.add(o);
        }
      }
      return out;
    }
    return null;
  }

  static Object concat(Object input, Object array) {
    if (!(array instanceof List)) {
      throw LiquidException.argument("concat filter requires an array argument");
    }
    List<Object> out = new ArrayList<>(inputIterator(input));
    out.addAll((List<?>) array);
    return out;
  }

  static Object date(Object input, Object format) {
    String f = Values.rubyToS(format);
    if (f.isEmpty()) {
      return input;
    }
    if (input instanceof RubyStrftime) { // Utils.to_date: obj.respond_to?(:strftime) -> obj
      return ((RubyStrftime) input).strftime(f);
    }
    RubyTime d = Utils.toDate(input);
    if (d == null) {
      return input;
    }
    return d.strftime(f);
  }

  // ---------------------------------------------------------------- numbers

  private static final MathContext DIV = new MathContext(40, RoundingMode.HALF_UP);

  /** BigDecimal results become Float, like {@code result.is_a?(BigDecimal) ? result.to_f : result}. */
  static Object toF(Object r) {
    return r instanceof BigDecimal ? ((BigDecimal) r).doubleValue() : r;
  }

  static Object applyOperation(Object input, Object operand, char op) {
    return toF(arith(Utils.toNumber(input), Utils.toNumber(operand), op));
  }

  private interface Op {
    Object run();
  }

  static Object zeroDiv(Op op) {
    try {
      return op.run();
    } catch (RubyError e) {
      if ("ZeroDivisionError".equals(e.rubyClass())) {
        throw new LiquidException(LiquidException.Kind.ZERO_DIVISION, e.getMessage());
      }
      throw e;
    }
  }

  /** Ruby numeric {@code a.send(op, b)} for Integer / BigDecimal (/ Float) operands. */
  static Object arith(Object a, Object b, char op) {
    if (Values.isInteger(a) && Values.isInteger(b)) {
      if (a instanceof Long && b instanceof Long) {
        long x = (Long) a, y = (Long) b;
        try {
          switch (op) {
            case '+': return Math.addExact(x, y);
            case '-': return Math.subtractExact(x, y);
            case '*': return Math.multiplyExact(x, y);
            case '/':
              if (y == 0) {
                throw RubyError.zeroDivision();
              }
              if (x == Long.MIN_VALUE && y == -1) {
                break;
              }
              return Math.floorDiv(x, y);
            case '%':
              if (y == 0) {
                throw RubyError.zeroDivision();
              }
              return Math.floorMod(x, y);
            default:
              throw new IllegalArgumentException();
          }
        } catch (ArithmeticException overflow) {
          // fall through to BigInteger
        }
      }
      BigInteger x = Values.toBigInteger(a), y = Values.toBigInteger(b);
      switch (op) {
        case '+': return Values.normInt(x.add(y));
        case '-': return Values.normInt(x.subtract(y));
        case '*': return Values.normInt(x.multiply(y));
        case '/': {
          if (y.signum() == 0) {
            throw RubyError.zeroDivision();
          }
          BigInteger[] qr = x.divideAndRemainder(y);
          BigInteger q = qr[0];
          if (qr[1].signum() != 0 && qr[1].signum() != y.signum()) {
            q = q.subtract(BigInteger.ONE);
          }
          return Values.normInt(q);
        }
        case '%': {
          if (y.signum() == 0) {
            throw RubyError.zeroDivision();
          }
          BigInteger r = x.mod(y.abs());
          if (y.signum() < 0 && r.signum() != 0) {
            r = r.add(y);
          }
          return Values.normInt(r);
        }
        default:
          throw new IllegalArgumentException();
      }
    }
    if (Values.isFloat(a) || Values.isFloat(b)) {
      double x = ((Number) a).doubleValue(), y = ((Number) b).doubleValue();
      switch (op) {
        case '+': return x + y;
        case '-': return x - y;
        case '*': return x * y;
        case '/': return x / y;
        case '%': {
          double r = x % y;
          if (r != 0 && (r < 0) != (y < 0)) {
            r += y;
          }
          return r;
        }
        default: throw new IllegalArgumentException();
      }
    }
    BigDecimal x = Values.toBigDecimal(a), y = Values.toBigDecimal(b);
    switch (op) {
      case '+': return x.add(y);
      case '-': return x.subtract(y);
      case '*': return x.multiply(y);
      case '/':
        if (y.signum() == 0) {
          // BigDecimal's default mode: x / 0 is +-Infinity (NaN for 0 / 0), as a Float after to_f
          return x.signum() == 0 ? Double.NaN : x.signum() > 0 ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
        }
        return x.divide(y, DIV);
      case '%': {
        if (y.signum() == 0) {
          throw RubyError.zeroDivision();
        }
        BigDecimal r = x.remainder(y);
        if (r.signum() != 0 && r.signum() != y.signum()) {
          r = r.add(y);
        }
        return r;
      }
      default:
        throw new IllegalArgumentException();
    }
  }

  static Object abs(Object n) {
    if (n instanceof Long) {
      long v = (Long) n;
      return v == Long.MIN_VALUE ? BigInteger.valueOf(v).abs() : Math.abs(v);
    }
    if (n instanceof BigInteger) {
      return Values.normInt(((BigInteger) n).abs());
    }
    if (n instanceof BigDecimal) {
      return ((BigDecimal) n).abs();
    }
    if (n instanceof Double) {
      return Math.abs((Double) n);
    }
    return n;
  }

  static Object round(Object input, Object n) {
    Object num = Utils.toNumber(input);
    Object digitsN = Utils.toNumber(n);
    int digits = (int) Values.toLong(digitsN);
    Object result;
    if (Values.isInteger(num)) {
      if (digits >= 0) {
        result = num;
      } else {
        BigDecimal bd = new BigDecimal(Values.toBigInteger(num)).setScale(digits, RoundingMode.HALF_UP);
        result = Values.normInt(bd.toBigInteger());
      }
    } else if (num instanceof BigDecimal) {
      // BigDecimal#round(n) returns an Integer when n < 1
      BigDecimal r = ((BigDecimal) num).setScale(digits, RoundingMode.HALF_UP);
      result = digits < 1 ? Values.normInt(r.toBigInteger()) : r;
      if (digits >= 1 && r.signum() == 0 && ((BigDecimal) num).signum() < 0) {
        result = -0.0; // BigDecimal keeps the sign of a rounded-away negative
      }
    } else {
      double d = ((Number) num).doubleValue();
      if (Double.isNaN(d) || Double.isInfinite(d)) {
        if (digits < 1) {
          throw floatDomain(d);
        }
        result = d;
      } else {
        result = new BigDecimal(d).setScale(digits, RoundingMode.HALF_UP).doubleValue();
      }
    }
    result = toF(result);
    if (Values.isNumeric(n) && Values.rubyEquals(n, 0L)) {
      result = Values.toI(result);
    }
    return result;
  }

  /** BigDecimal's FloatDomainError for converting NaN/Infinity to an Integer. */
  static LiquidException floatDomain(double d) {
    String m = Double.isNaN(d) ? "Computation results in 'NaN' (Not a Number)" : d > 0 ? "Computation results in 'Infinity'" : "Computation results in '-Infinity'";
    return new LiquidException(LiquidException.Kind.FLOAT_DOMAIN, m);
  }

  static Object ceilFloor(Object input, RoundingMode mode) {
    Object num = Utils.toNumber(input);
    if (Values.isInteger(num)) {
      return num;
    }
    if (num instanceof BigDecimal) {
      return Values.normInt(((BigDecimal) num).setScale(0, mode).toBigInteger());
    }
    double d = ((Number) num).doubleValue();
    if (Double.isNaN(d) || Double.isInfinite(d)) {
      throw floatDomain(d);
    }
    return Values.toI(mode == RoundingMode.CEILING ? Math.ceil(d) : Math.floor(d));
  }

  static Object atLeastMost(Object input, Object n, boolean atLeast) {
    Object bound = Utils.toNumber(n);
    Object result = Utils.toNumber(input);
    int c = Values.numCompare(bound, result);
    if (atLeast ? c > 0 : c < 0) {
      result = bound;
    }
    return toF(result);
  }
}
