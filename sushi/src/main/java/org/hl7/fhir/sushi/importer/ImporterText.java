package org.hl7.fhir.sushi.importer;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.hl7.fhir.sushi.fshtypes.Common;

// port of the string/number helpers of sushi/src/import/FSHImporter.ts (SUSHI 3.21.1): unescapeQuotedString,
// extractMultilineString, extractNumberValue, plus the JS built-ins they rely on (parseInt, RegExp)
final class ImporterText {
  private ImporterText() {}

  /** JS /\\(u[A-F,a-f,0-9]{4})/g (sic: the class admits ','). */
  private static final Pattern UNICODE_ESCAPE = Pattern.compile("\\\\(u[A-F,a-f,0-9]{4})");
  private static final Pattern LINE_SPLIT = Pattern.compile("\\r?\\n");
  private static final Pattern NON_WS = Pattern.compile("[^" + Common.JS_WS.substring(1));
  private static final Pattern ALL_WS = Pattern.compile("^" + Common.JS_WS + "*\\z");

  /** TS unescapeUnicode: JSON.parse(`"\\${p1}"`). Throws V8's SyntaxError text for a ',' among the hex digits. */
  private static String unescapeUnicode(String p1) {
    String hex = p1.substring(1);
    for (int i = 0; i < hex.length(); i++) {
      if (Character.digit(hex.charAt(i), 16) < 0) {
        int pos = 3 + i;
        throw new JsSyntaxError("Bad Unicode escape in JSON at position " + pos + " (line 1 column " + (pos + 1) + ")");
      }
    }
    return String.valueOf((char) Integer.parseInt(hex, 16));
  }

  /** A JS SyntaxError (e.g. from JSON.parse); its message is what TS logs. */
  static final class JsSyntaxError extends RuntimeException {
    JsSyntaxError(String message) {
      super(message);
    }
  }

  private static String replaceUnicode(String s) {
    Matcher m = UNICODE_ESCAPE.matcher(s);
    if (!m.find()) return s;
    StringBuilder sb = new StringBuilder();
    do {
      m.appendReplacement(sb, Matcher.quoteReplacement(unescapeUnicode(m.group(1))));
    } while (m.find());
    m.appendTail(sb);
    return sb.toString();
  }

  static String unescapeQuotedString(String str) {
    // Strip surrounding quotes: JS str.slice(1, str.length - 1)
    String strNoQuotes = str.length() >= 2 ? str.substring(1, str.length() - 1) : "";
    // Replace escaped characters
    String[] splitBackslash = strNoQuotes.split(Pattern.quote("\\\\"), -1);
    List<String> replaced = new ArrayList<>();
    for (String substrBackslash : splitBackslash) {
      // Replace quote, newline, return, tab characters only if they were not preceded by a backslash
      replaced.add(replaceUnicode(substrBackslash).replace("\\\"", "\"").replace("\\n", "\n").replace("\\r", "\r")
          .replace("\\t", "\t"));
    }
    return String.join("\\", replaced);
  }

  /**
   * Multiline strings receive special handling: a first or last line containing only whitespace is dropped, other
   * whitespace-only lines are truncated, and the shortest common indentation of the other lines is removed.
   */
  static String extractMultilineString(String text) {
    // remove leading/trailing """ (JS slice(3, -3))
    String mlstr = text.length() >= 6 ? text.substring(3, text.length() - 3) : "";
    // split into lines so we can process them to determine what leading spaces to trim
    List<String> lines = new ArrayList<>();
    for (String l : LINE_SPLIT.split(mlstr, -1)) {
      lines.add(replaceUnicode(l).replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t"));
    }
    // if the first line is only whitespace, remove it
    if (!NON_WS.matcher(lines.get(0)).find()) lines.remove(0);
    // if the last line is only whitespace, remove it
    if (!lines.isEmpty() && !NON_WS.matcher(lines.get(lines.size() - 1)).find()) lines.remove(lines.size() - 1);
    lines.replaceAll(l -> ALL_WS.matcher(l).find() ? "" : l);
    // find the minimum number of spaces before the first char (ignore zero-length lines); lodash min skips nulls
    Integer minSpaces = null;
    for (String line : lines) {
      if (line.isEmpty()) continue;
      Matcher m = NON_WS.matcher(line);
      int firstNonSpace = m.find() ? m.start() : line.length();
      if (minSpaces == null || firstNonSpace < minSpaces) minSpaces = firstNonSpace;
    }
    // consistently remove the common leading spaces and join the lines back together
    List<String> out = new ArrayList<>();
    for (String l : lines) out.add(minSpaces != null && l.length() >= minSpaces ? l.substring(minSpaces) : l);
    return String.join("\n", out);
  }

  private static final Pattern NUMBER_SPLITTER = Pattern.compile("([-+]?\\d+)(\\.\\d+)?([eE][-+]?\\d+)?");
  private static final Pattern DECIMAL_TRIMMER = Pattern.compile("\\.(\\d*[1-9])0*");
  private static final Pattern WHOLE_ZERO_CATCHER = Pattern.compile("[-+]?\\d*[1-9](0*)");

  /**
   * TS extractNumberValue: an integer (also 1.0, 1.5e3, 100e-2) becomes a BigInteger (FHIR integer64 may exceed
   * 2^53), anything else a Double via parseFloat.
   */
  static Object extractNumberValue(String numberString) {
    Matcher m = NUMBER_SPLITTER.matcher(numberString);
    String wholePart = null;
    String decimalPart = null;
    String exponentPart = null;
    if (m.find()) {
      wholePart = m.group(1);
      decimalPart = m.group(2);
      exponentPart = m.group(3);
    }
    long exponentValue = exponentPart != null ? Long.parseLong(stripPlus(exponentPart.substring(1))) : 0;
    if (decimalPart != null) {
      Matcher dt = DECIMAL_TRIMMER.matcher(decimalPart);
      String trimmedDecimal = dt.find() ? dt.group(1) : null;
      if (trimmedDecimal != null) {
        if (trimmedDecimal.length() <= exponentValue) {
          return new BigInteger(wholePart + trimmedDecimal)
              .multiply(BigInteger.TEN.pow(Math.toIntExact(exponentValue - trimmedDecimal.length())));
        } else {
          return Double.parseDouble(numberString);
        }
      }
    }
    // there's no decimal part (or it is all zeroes). a negative exponent might make this a float, but it might not.
    Matcher wz = WHOLE_ZERO_CATCHER.matcher(wholePart);
    String wholeZeroes = wz.find() ? wz.group(1) : null;
    long wholeZeroCount = wholeZeroes != null ? wholeZeroes.length() : 0;
    long remainingZeroes = wholeZeroCount + exponentValue;
    if (remainingZeroes >= 0) {
      if (exponentValue < 0) {
        return new BigInteger(wholePart).divide(BigInteger.TEN.pow(Math.toIntExact(-exponentValue)));
      } else {
        return new BigInteger(wholePart).multiply(BigInteger.TEN.pow(Math.toIntExact(exponentValue)));
      }
    }
    return Double.parseDouble(numberString);
  }

  private static String stripPlus(String s) {
    return s.startsWith("+") ? s.substring(1) : s;
  }

  /** JS parseInt(s) (radix 10) as a double: leading whitespace, optional sign, digits; NaN when none. */
  static Double jsParseInt(String s) {
    if (s == null) return Double.NaN;
    int i = 0;
    while (i < s.length() && org.hl7.fhir.sushi.js.Js.isJsWhitespace(s.charAt(i))) i++;
    boolean neg = false;
    if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
      neg = s.charAt(i) == '-';
      i++;
    }
    int start = i;
    while (i < s.length() && s.charAt(i) >= '0' && s.charAt(i) <= '9') i++;
    if (i == start) return Double.NaN;
    double v = new java.math.BigDecimal(s.substring(start, i)).doubleValue();
    return neg ? -v : v;
  }

  /**
   * JS RegExp(source) for a value set regex filter. Only the source text is used downstream; a source Java can't
   * compile (JS-only syntax) is kept as a literal pattern so {@code pattern()} still returns the source.
   */
  static Pattern jsRegExp(String source) {
    try {
      return Pattern.compile(source);
    } catch (PatternSyntaxException e) {
      return Pattern.compile(source, Pattern.LITERAL);
    }
  }

  static List<String> list(String... s) {
    return new ArrayList<>(Arrays.asList(s));
  }
}
