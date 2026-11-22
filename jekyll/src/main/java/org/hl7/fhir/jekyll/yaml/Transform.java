package org.hl7.fhir.jekyll.yaml;

import java.math.BigInteger;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Port of safe_yaml-1.0.5/lib/safe_yaml/transform.rb and transform/*.rb, parse/*.rb: SafeYAML's
 * scalar typing ({@code Transform.to_proper_type} / {@code to_guessed_type}). Quoted scalars (in
 * libyaml's sense: {@code quoted_implicit}, i.e. untagged and not plain, which includes block
 * scalars) are never transformed; tagged scalars are (SafeYAML ignores tags other than binary).
 *
 * <p>Order: ToSymbol (off: {@code deserialize_symbols} is false) → ToInteger → ToFloat → ToNil →
 * ToBoolean → ToDate. Regexes keep Ruby's semantics: {@code \Z} also matches before a final
 * "\n" (UNIX_LINES), {@code \d}/{@code \s} are ASCII.
 */
final class Transform {
  private Transform() {}

  /** "no match" for transform? (Ruby returns false / [false, ...]) */
  static final Object NO = new Object();

  /** SafeYAML::OPTIONS[:deserialize_symbols] (Jekyll leaves the default, false) */
  static final boolean DESERIALIZE_SYMBOLS = false;

  /** Transform.to_proper_type */
  static Object toProperType(Object value, boolean quoted, String tag) {
    if ("tag:yaml.org,2002:binary".equals(tag) || "x-private:binary".equals(tag) || "!binary".equals(tag)) {
      // Base64.decode64(value).force_encoding(value.encoding)
      byte[] decoded = RubyBase64.decode64(((String) value).getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return BinaryString.utf8(decoded);
    }
    return toGuessedType(value, quoted);
  }

  /** Transform.to_guessed_type */
  static Object toGuessedType(Object value, boolean quoted) {
    if (quoted) {
      return value;
    }
    if (value instanceof BinaryString) {
      // an alias to !binary data that is not valid UTF-8: ToInteger's value.gsub raises on the broken string
      throw new YamlException("ArgumentError", "invalid byte sequence in UTF-8");
    }
    if (value instanceof String) {
      String s = (String) value;
      Object r;
      if ((r = ToSymbol.transform(s)) != NO
          || (r = ToInteger.transform(s)) != NO
          || (r = ToFloat.transform(s)) != NO
          || (r = ToNil.transform(s)) != NO
          || (r = ToBoolean.transform(s)) != NO
          || (r = ToDate.transform(s)) != NO) {
        return r;
      }
    }
    return value;
  }

  private static Pattern re(String regex) {
    return Pattern.compile(regex, Pattern.UNIX_LINES);
  }

  /** Ruby String#length (characters) */
  private static int rubyLength(String s) {
    return s.codePointCount(0, s.length());
  }

  /** transform/to_symbol.rb (inactive unless deserialize_symbols) */
  static final class ToSymbol {
    private static final Pattern START = re("\\A:.");
    private static final Pattern QUOTED = re("\\A:([\"'])(.*)\\1\\Z");

    static Object transform(String value) {
      if (DESERIALIZE_SYMBOLS && START.matcher(value).find()) {
        java.util.regex.Matcher m = QUOTED.matcher(value);
        return new RubySymbol(m.find() ? m.group(2).replaceFirst("(?m)^:", "") : value.replaceFirst("(?m)^:", ""));
      }
      return NO;
    }
  }

  /** transform/to_integer.rb (+ parse/hexadecimal.rb, the integer half of parse/sexagesimal.rb) */
  static final class ToInteger {
    private static final Pattern[] MATCHERS = {
      re("\\A[-+]?(0|([1-9][0-9_,]*))\\Z"), // decimal
      re("\\A0[0-7]+\\Z"), // octal
      Pattern.compile("\\A0x[0-9a-f]+\\Z", Pattern.UNIX_LINES | Pattern.CASE_INSENSITIVE), // hexadecimal
      re("\\A0b[01_]+\\Z"), // binary
    };
    private static final Pattern UNDERSCORE_COMMA = Pattern.compile("[_,]");

    static Object transform(String value) {
      for (int idx = 0; idx < MATCHERS.length; idx++) {
        if (idx == 0) {
          value = UNDERSCORE_COMMA.matcher(value).replaceAll("");
        }
        if (MATCHERS[idx].matcher(value).find()) {
          return RubyNumbers.integer(value);
        }
      }
      return tryEdgeCases(value);
    }

    static Object tryEdgeCases(String value) {
      if (Parse.HEXADECIMAL.matcher(value).find()) {
        return RubyNumbers.integer(value.replace("_", ""));
      }
      if (Parse.SEXAGESIMAL_INTEGER.matcher(value).find()) {
        return Parse.sexagesimal(value);
      }
      return NO;
    }
  }

  /** transform/to_float.rb (+ the float half of parse/sexagesimal.rb) */
  static final class ToFloat {
    private static final Map<String, Double> PREDEFINED_VALUES = Map.of(
        ".inf", Double.POSITIVE_INFINITY, ".Inf", Double.POSITIVE_INFINITY, ".INF", Double.POSITIVE_INFINITY,
        "-.inf", Double.NEGATIVE_INFINITY, "-.Inf", Double.NEGATIVE_INFINITY, "-.INF", Double.NEGATIVE_INFINITY,
        ".nan", Double.NaN, ".NaN", Double.NaN, ".NAN", Double.NaN);
    private static final Pattern MATCHER = re("\\A[-+]?(?:\\d[\\d_]*)?\\.[\\d_]+(?:[eE][-+][\\d]+)?\\Z");

    static Object transform(String value) {
      if (MATCHER.matcher(value).find()) {
        return RubyNumbers.kernelFloat(value);
      }
      return tryEdgeCases(value);
    }

    static Object tryEdgeCases(String value) {
      Double d = PREDEFINED_VALUES.get(value);
      if (d != null) {
        return d;
      }
      if (Parse.SEXAGESIMAL_FLOAT.matcher(value).find()) {
        return Parse.sexagesimal(value);
      }
      return NO;
    }
  }

  /** transform/to_nil.rb (CaseAgnosticMap: keys compared downcased) */
  static final class ToNil {
    static Object transform(String value) {
      if (rubyLength(value) > 4) {
        return NO;
      }
      String k = value.toLowerCase(Locale.ROOT);
      return k.isEmpty() || k.equals("~") || k.equals("null") ? null : NO;
    }
  }

  /** transform/to_boolean.rb (CaseAgnosticMap) */
  static final class ToBoolean {
    static Object transform(String value) {
      if (rubyLength(value) > 5) {
        return NO;
      }
      switch (value.toLowerCase(Locale.ROOT)) {
        case "yes":
        case "on":
        case "true":
          return Boolean.TRUE;
        case "no":
        case "off":
        case "false":
          return Boolean.FALSE;
        default:
          return NO;
      }
    }
  }

  /** transform/to_date.rb (+ parse/date.rb): Date.parse, or DateTime.parse(value).to_time */
  static final class ToDate {
    static Object transform(String value) {
      try {
        if (Parse.DATE_MATCHER.matcher(value).find()) {
          return DateParse.dateParse(value);
        }
        if (Parse.TIME_MATCHER.matcher(value).find()) {
          return DateParse.dateTimeParseToTime(value);
        }
      } catch (YamlException e) {
        if (e.rubyClass().equals("ArgumentError")) {
          return value; // rescue ArgumentError: return true, value
        }
        throw e;
      }
      return NO;
    }
  }

  /** parse/hexadecimal.rb, parse/sexagesimal.rb, parse/date.rb (the matchers and sexagesimal value) */
  static final class Parse {
    static final Pattern HEXADECIMAL = re("\\A[-+]?0x[0-9a-fA-F_]+\\Z");
    static final Pattern SEXAGESIMAL_INTEGER = re("\\A[-+]?[0-9][0-9_]*(:[0-5]?[0-9])+\\Z");
    static final Pattern SEXAGESIMAL_FLOAT = re("\\A[-+]?[0-9][0-9_]*(:[0-5]?[0-9])+\\.[0-9_]*\\Z");
    static final Pattern DATE_MATCHER = re("\\A(\\d{4})-(\\d{2})-(\\d{2})\\Z");
    static final Pattern TIME_MATCHER = re(
        "\\A\\d{4}-\\d{1,2}-\\d{1,2}(?:[Tt]|\\s+)\\d{1,2}:\\d{2}:\\d{2}(?:\\.\\d*)?\\s*(?:Z|[-+]\\d{1,2}(?::?\\d{2})?)?\\Z");

    /** Parse::Sexagesimal.value: always a Float (the parts go through Float()). */
    static Object sexagesimal(String value) {
      // before_decimal, after_decimal = value.split(".")
      String[] split = RubyValues.split(value, ".");
      String beforeDecimal = split.length > 0 ? split[0] : null;
      String afterDecimal = split.length > 1 ? split[1] : null;
      double wholePart = 0;
      BigInteger multiplier = BigInteger.ONE;
      String[] parts = RubyValues.split(beforeDecimal == null ? "" : beforeDecimal, ":");
      for (int k = parts.length - 1; k >= 0; k--) {
        wholePart += RubyNumbers.kernelFloat(parts[k]) * multiplier.doubleValue();
        multiplier = multiplier.multiply(BigInteger.valueOf(60));
      }
      double result = wholePart;
      if (afterDecimal != null) {
        result += RubyNumbers.kernelFloat("." + afterDecimal);
      }
      if (value.startsWith("-")) {
        result *= -1;
      }
      return result;
    }
  }
}
