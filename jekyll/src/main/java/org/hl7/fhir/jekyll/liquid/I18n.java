package org.hl7.fhir.jekyll.liquid;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Port of liquid-4.0.4/lib/liquid/i18n.rb with locales/en.yml inlined. */
public class I18n {
  private static final Map<String, String> EN = new HashMap<>();

  static {
    EN.put("errors.syntax.tag_unexpected_args", "Syntax Error in '%{tag}' - Valid syntax: %{tag}");
    EN.put("errors.syntax.assign", "Syntax Error in 'assign' - Valid syntax: assign [var] = [source]");
    EN.put("errors.syntax.capture", "Syntax Error in 'capture' - Valid syntax: capture [var]");
    EN.put("errors.syntax.case", "Syntax Error in 'case' - Valid syntax: case [condition]");
    EN.put("errors.syntax.case_invalid_when", "Syntax Error in tag 'case' - Valid when condition: {% when [condition] [or condition2...] %}");
    EN.put("errors.syntax.case_invalid_else", "Syntax Error in tag 'case' - Valid else condition: {% else %} (no parameters) ");
    EN.put("errors.syntax.cycle", "Syntax Error in 'cycle' - Valid syntax: cycle [name :] var [, var2, var3 ...]");
    EN.put("errors.syntax.for", "Syntax Error in 'for loop' - Valid syntax: for [item] in [collection]");
    EN.put("errors.syntax.for_invalid_in", "For loops require an 'in' clause");
    EN.put("errors.syntax.for_invalid_attribute", "Invalid attribute in for loop. Valid attributes are limit and offset");
    EN.put("errors.syntax.if", "Syntax Error in tag 'if' - Valid syntax: if [expression]");
    EN.put("errors.syntax.include", "Error in tag 'include' - Valid syntax: include '[template]' (with|for) [object|collection]");
    EN.put("errors.syntax.unknown_tag", "Unknown tag '%{tag}'");
    EN.put("errors.syntax.invalid_delimiter", "'%{tag}' is not a valid delimiter for %{block_name} tags. use %{block_delimiter}");
    EN.put("errors.syntax.unexpected_else", "%{block_name} tag does not expect 'else' tag");
    EN.put("errors.syntax.unexpected_outer_tag", "Unexpected outer '%{tag}' tag");
    EN.put("errors.syntax.tag_termination", "Tag '%{token}' was not properly terminated with regexp: %{tag_end}");
    EN.put("errors.syntax.variable_termination", "Variable '%{token}' was not properly terminated with regexp: %{tag_end}");
    EN.put("errors.syntax.tag_never_closed", "'%{block_name}' tag was never closed");
    EN.put("errors.syntax.meta_syntax_error", "Liquid syntax error: #{e.message}");
    EN.put("errors.syntax.table_row", "Syntax Error in 'table_row loop' - Valid syntax: table_row [item] in [collection] cols=3");
    EN.put("errors.argument.include", "Argument error in tag 'include' - Illegal template name");
  }

  private static final Pattern INTERP = Pattern.compile("%\\{(\\w+)\\}", Pattern.UNIX_LINES);
  private final Map<String, String> table;

  public I18n() {
    this(EN);
  }

  public I18n(Map<String, String> table) {
    this.table = table;
  }

  /** Ruby {@code t(name, vars)}; vars alternate key, value. */
  public String t(String name, Object... vars) {
    String s = table.get(name);
    if (s == null) {
      throw new IllegalStateException("Translation for " + name + " does not exist in locale");
    }
    Map<String, Object> v = new HashMap<>();
    for (int k = 0; k + 1 < vars.length; k += 2) {
      v.put((String) vars[k], vars[k + 1]);
    }
    Matcher m = INTERP.matcher(s);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      m.appendReplacement(sb, Matcher.quoteReplacement(Values.rubyToS(v.get(m.group(1)))));
    }
    m.appendTail(sb);
    return sb.toString();
  }
}
