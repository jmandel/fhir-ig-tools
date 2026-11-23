package org.hl7.fhir.jekyll.liquid;

/** Port of liquid-4.0.4/lib/liquid/range_lookup.rb. */
public class RangeLookup implements Evaluable {
  private final Object startObj;
  private final Object endObj;

  /** Returns a {@link RubyRange} when both ends are literals, else a RangeLookup. */
  public static Object parse(String startMarkup, String endMarkup) {
    Object start = Expression.parse(startMarkup);
    Object end = Expression.parse(endMarkup);
    if (start instanceof Evaluable || end instanceof Evaluable) {
      return new RangeLookup(start, end);
    }
    return new RubyRange(Values.toLong(start), Values.toLong(end));
  }

  public RangeLookup(Object startObj, Object endObj) {
    this.startObj = startObj;
    this.endObj = endObj;
  }

  @Override
  public Object evaluate(Context context) {
    long startInt = toInteger(context.evaluate(startObj));
    long endInt = toInteger(context.evaluate(endObj));
    return new RubyRange(startInt, endInt);
  }

  private static long toInteger(Object input) {
    if (Values.isInteger(input) || input == null || input instanceof String) {
      return Values.toLong(input);
    }
    return Values.toLong(Utils.toInteger(input));
  }
}
