package org.hl7.fhir.jekyll.liquid;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Port of liquid-4.0.4/lib/liquid/expression.rb. */
public final class Expression {
  private Expression() {}

  /** Ruby {@code Expression::MethodLiteral} ({@code blank} / {@code empty}). */
  public static final class MethodLiteral {
    public final String methodName;

    MethodLiteral(String methodName) {
      this.methodName = methodName;
    }

    public Object toLiquid() {
      return "";
    }

    @Override
    public String toString() {
      return "";
    }
  }

  public static final MethodLiteral BLANK = new MethodLiteral("blank?");
  public static final MethodLiteral EMPTY = new MethodLiteral("empty?");

  private static final Pattern SINGLE_QUOTED_STRING = Pattern.compile("(?s)\\A'(.*)'\\z", Pattern.UNIX_LINES);
  private static final Pattern DOUBLE_QUOTED_STRING = Pattern.compile("(?s)\\A\"(.*)\"\\z", Pattern.UNIX_LINES);
  private static final Pattern INTEGERS_REGEX = Pattern.compile("\\A(-?\\d+)\\z", Pattern.UNIX_LINES);
  private static final Pattern FLOATS_REGEX = Pattern.compile("\\A(-?\\d[\\d\\.]+)\\z", Pattern.UNIX_LINES);
  private static final Pattern RANGES_REGEX = Pattern.compile("\\A\\((\\S+)\\.\\.(\\S+)\\)\\z", Pattern.UNIX_LINES);

  /** Marker for "not a literal". */
  private static final Object NOT_LITERAL = new Object();

  private static Object literal(String markup) {
    if (markup == null) {
      return null;
    }
    switch (markup) {
      case "nil":
      case "null":
      case "":
        return null;
      case "true":
        return Boolean.TRUE;
      case "false":
        return Boolean.FALSE;
      case "blank":
        return BLANK;
      case "empty":
        return EMPTY;
      default:
        return NOT_LITERAL;
    }
  }

  /**
   * Ruby {@code Expression.parse(markup)}: returns null, Boolean, String, Long, Double,
   * {@link MethodLiteral}, {@link RubyRange}, {@link RangeLookup} or {@link VariableLookup}.
   */
  public static Object parse(String markup) {
    Object lit = literal(markup);
    if (lit != NOT_LITERAL) {
      return lit;
    }
    Matcher m;
    if ((m = SINGLE_QUOTED_STRING.matcher(markup)).find() || (m = DOUBLE_QUOTED_STRING.matcher(markup)).find()) {
      return m.group(1);
    }
    if ((m = INTEGERS_REGEX.matcher(markup)).find()) {
      return RubyStrings.toI(m.group(1));
    }
    if ((m = RANGES_REGEX.matcher(markup)).find()) {
      return RangeLookup.parse(m.group(1), m.group(2));
    }
    if ((m = FLOATS_REGEX.matcher(markup)).find()) {
      return RubyStrings.toF(m.group(1));
    }
    return VariableLookup.parse(markup);
  }
}
