package org.hl7.fhir.jekyll.liquid;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Port of liquid-4.0.4/lib/liquid.rb: the shared regex constants, plus small helpers that emulate
 * Ruby's String#=~ / #scan semantics on top of java.util.regex.
 *
 * <p>Ruby interpolates a Regexp into another as {@code (?-mix:...)}, i.e. as a group; we wrap every
 * interpolation in {@code (?:...)} so precedence (e.g. {@code #{QuotedFragment}+}) stays identical.
 */
public final class Liquid {
  private Liquid() {}

  static String g(String re) {
    return "(?:" + re + ")";
  }

  public static final String FILTER_SEPARATOR = "\\|";
  public static final String ARGUMENT_SEPARATOR = ",";
  public static final String FILTER_ARGUMENT_SEPARATOR = ":";
  public static final String VARIABLE_ATTRIBUTE_SEPARATOR = ".";
  public static final String WHITESPACE_CONTROL = "-";
  public static final String TAG_START = "\\{\\%";
  public static final String TAG_END = "\\%\\}";
  public static final String VARIABLE_SIGNATURE = "\\(?[\\w\\-\\.\\[\\]]\\)?";
  public static final String VARIABLE_SEGMENT = "[\\w\\-]";
  public static final String VARIABLE_START = "\\{\\{";
  public static final String VARIABLE_END = "\\}\\}";
  public static final String VARIABLE_INCOMPLETE_END = "\\}\\}?";
  public static final String QUOTED_STRING = "\"[^\"]*\"|'[^']*'";
  public static final String QUOTED_FRAGMENT = g(QUOTED_STRING) + "|(?:[^\\s,\\|'\"]|" + g(QUOTED_STRING) + ")+";
  public static final String TAG_ATTRIBUTES = "(\\w+)\\s*\\:\\s*(" + g(QUOTED_FRAGMENT) + ")";
  public static final String ANY_STARTING_TAG = g(TAG_START) + "|" + g(VARIABLE_START);
  public static final String PARTIAL_TEMPLATE_PARSER =
      "(?s:" + g(TAG_START) + ".*?" + g(TAG_END) + "|" + g(VARIABLE_START) + ".*?" + g(VARIABLE_INCOMPLETE_END) + ")";
  public static final String TEMPLATE_PARSER = "(?s)(" + PARTIAL_TEMPLATE_PARSER + "|" + g(ANY_STARTING_TAG) + ")";
  public static final String VARIABLE_PARSER = "\\[[^\\]]+\\]|" + g(VARIABLE_SEGMENT) + "+\\??";

  public static final Pattern QuotedFragment = Pattern.compile(QUOTED_FRAGMENT, Pattern.UNIX_LINES);
  public static final Pattern TagAttributes = Pattern.compile(TAG_ATTRIBUTES, Pattern.UNIX_LINES);
  public static final Pattern TemplateParser = Pattern.compile(TEMPLATE_PARSER, Pattern.UNIX_LINES);
  public static final Pattern VariableParser = Pattern.compile(VARIABLE_PARSER, Pattern.UNIX_LINES);

  /** Ruby {@code str =~ re}: first match anywhere, or null. */
  public static Matcher match(Pattern p, String s) {
    if (s == null) {
      return null;
    }
    Matcher m = p.matcher(s);
    return m.find() ? m : null;
  }

  /** Ruby {@code str.scan(re)} for a regex without groups: list of whole matches. */
  public static List<String> scan(Pattern p, String s) {
    List<String> out = new ArrayList<>();
    Matcher m = p.matcher(s);
    while (m.find()) {
      out.add(m.group());
    }
    return out;
  }

  /** Ruby {@code str.scan(re)} for a regex with groups: one String[] of the groups per match. */
  public static List<String[]> scanGroups(Pattern p, String s) {
    List<String[]> out = new ArrayList<>();
    Matcher m = p.matcher(s);
    while (m.find()) {
      String[] gs = new String[m.groupCount()];
      for (int k = 0; k < gs.length; k++) {
        gs[k] = m.group(k + 1);
      }
      out.add(gs);
    }
    return out;
  }
}
