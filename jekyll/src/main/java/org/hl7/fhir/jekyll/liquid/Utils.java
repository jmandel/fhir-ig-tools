package org.hl7.fhir.jekyll.liquid;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Port of liquid-4.0.4/lib/liquid/utils.rb. */
public final class Utils {
  private Utils() {}

  /** Optional drop capability: Ruby {@code load_slice(from, to)}. */
  public interface SliceLoadable {
    List<Object> loadSlice(long from, Long to);
  }

  /** Optional drop capability: Ruby {@code to_number}. */
  public interface NumberLike {
    Object toNumber();
  }

  /** Optional capability: an enumerable drop (Ruby {@code respond_to?(:each)}). */
  public interface Enumerable {
    Iterable<Object> each();
  }

  public static List<Object> sliceCollection(Object collection, long from, Long to) {
    if ((from != 0 || to != null) && collection instanceof SliceLoadable) {
      return ((SliceLoadable) collection).loadSlice(from, to);
    }
    return sliceCollectionUsingEach(collection, from, to);
  }

  public static List<Object> sliceCollectionUsingEach(Object collection, long from, Long to) {
    // Maintains Ruby 1.8.7 String#each behaviour on 1.9
    if (collection instanceof String) {
      return ((String) collection).isEmpty() ? new ArrayList<>() : new ArrayList<>(Collections.singletonList(collection));
    }
    Iterable<?> items = each(collection);
    if (items == null) {
      return new ArrayList<>();
    }
    List<Object> segments = new ArrayList<>();
    long index = 0;
    for (Object item : items) {
      if (to != null && to <= index) {
        break;
      }
      if (from <= index) {
        segments.add(item);
      }
      index += 1;
    }
    return segments;
  }

  /** Ruby {@code collection.each}: Array elements, Hash [key, value] pairs, Range integers; null if no #each. */
  public static Iterable<?> each(Object collection) {
    if (collection instanceof List) {
      return (List<?>) collection;
    }
    if (collection instanceof Map) {
      List<Object> pairs = new ArrayList<>();
      for (Map.Entry<?, ?> e : ((Map<?, ?>) collection).entrySet()) {
        List<Object> pair = new ArrayList<>(2);
        pair.add(e.getKey());
        pair.add(e.getValue());
        pairs.add(pair);
      }
      return pairs;
    }
    if (collection instanceof RubyRange) {
      return ((RubyRange) collection).toList();
    }
    if (collection instanceof Enumerable) {
      return ((Enumerable) collection).each();
    }
    return null;
  }

  /** Ruby {@code Utils.to_integer}: Integer or {@code Integer(num.to_s)}, else Liquid::ArgumentError. */
  public static Object toInteger(Object num) {
    if (Values.isInteger(num)) {
      return Values.normInt(num);
    }
    String s = Values.rubyToS(num);
    try {
      return RubyStrings.strictInteger(s);
    } catch (RubyError e) {
      throw LiquidException.argument("invalid integer");
    }
  }

  private static final Pattern DECIMAL = Pattern.compile("\\A-?\\d+\\.\\d+\\z", Pattern.UNIX_LINES);

  /** Ruby {@code Utils.to_number}: Float becomes BigDecimal, decimal strings BigDecimal, others to_i or 0. */
  public static Object toNumber(Object obj) {
    if (Values.isFloat(obj)) {
      double d = ((Number) obj).doubleValue();
      if (Double.isNaN(d) || Double.isInfinite(d)) {
        return d;
      }
      return new BigDecimal(Double.toString(d));
    }
    if (Values.isNumeric(obj)) {
      return Values.normInt(obj);
    }
    if (obj instanceof String) {
      String s = (String) obj;
      if (DECIMAL.matcher(RubyStrings.strip(s)).find()) {
        return new BigDecimal(s.trim());
      }
      return RubyStrings.toI(s);
    }
    if (obj instanceof NumberLike) {
      return ((NumberLike) obj).toNumber();
    }
    return 0L;
  }

  /** Ruby {@code Utils.to_date}; null when not convertible. */
  public static RubyTime toDate(Object obj) {
    if (obj instanceof RubyTime) {
      return (RubyTime) obj;
    }
    if (obj instanceof String) {
      String s = (String) obj;
      if (s.isEmpty()) {
        return null;
      }
      obj = RubyStrings.downcase(s);
    }
    try {
      if ("now".equals(obj) || "today".equals(obj)) {
        return RubyTime.now();
      }
      if (Values.isInteger(obj) || obj instanceof String && ((String) obj).matches("\\d+")) {
        return RubyTime.at(Values.toLong(Values.toI(obj)));
      }
      if (obj instanceof String) {
        return TimeParser.parse((String) obj);
      }
      return null;
    } catch (RubyError e) {
      if (e.isArgumentError()) {
        return null;
      }
      throw e;
    }
  }
}
