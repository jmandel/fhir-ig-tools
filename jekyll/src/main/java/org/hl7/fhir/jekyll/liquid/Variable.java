package org.hl7.fhir.jekyll.liquid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Port of liquid-4.0.4/lib/liquid/variable.rb. */
public class Variable implements Node, ParserSwitching {
  static final Pattern FilterMarkupRegex = Pattern.compile("(?s)" + Liquid.FILTER_SEPARATOR + "\\s*(.*)", Pattern.UNIX_LINES);
  static final Pattern FilterParser = Pattern.compile(
      "(?:\\s+|" + Liquid.g(Liquid.QUOTED_FRAGMENT) + "|" + Liquid.ARGUMENT_SEPARATOR + ")+", Pattern.UNIX_LINES);
  static final Pattern FilterArgsRegex = Pattern.compile(
      "(?:" + Liquid.FILTER_ARGUMENT_SEPARATOR + "|" + Liquid.ARGUMENT_SEPARATOR + ")\\s*((?:\\w+\\s*\\:\\s*)?" + Liquid.g(Liquid.QUOTED_FRAGMENT) + ")", Pattern.UNIX_LINES);
  static final Pattern JustTagAttributes = Pattern.compile("\\A" + Liquid.g(Liquid.TAG_ATTRIBUTES) + "\\z", Pattern.UNIX_LINES);
  static final Pattern MarkupWithQuotedFragment = Pattern.compile("(?s)(" + Liquid.g(Liquid.QUOTED_FRAGMENT) + ")(.*)", Pattern.UNIX_LINES);
  private static final Pattern WORD = Pattern.compile("\\w+", Pattern.UNIX_LINES);

  /** One {@code | name: args} element; kwargs is null when there are none. */
  public static final class FilterCall {
    public final String name;
    public final List<Object> args;
    public final Map<String, Object> kwargs;

    FilterCall(String name, List<Object> args, Map<String, Object> kwargs) {
      this.name = name;
      this.args = args;
      this.kwargs = kwargs;
    }
  }

  private final String markup;
  private Object name;
  private List<FilterCall> filters = Collections.emptyList();
  private final Integer lineNumber;
  private final ParseContext parseContext;

  public Variable(String markup, ParseContext parseContext) {
    this.markup = markup;
    this.name = null;
    this.parseContext = parseContext;
    this.lineNumber = parseContext.lineNumber();
    parseWithSelectedParser(markup);
  }

  public String raw() {
    return markup;
  }

  public Object name() {
    return name;
  }

  public List<FilterCall> filters() {
    return filters;
  }

  @Override
  public Integer lineNumber() {
    return lineNumber;
  }

  @Override
  public ParseContext parseContext() {
    return parseContext;
  }

  @Override
  public String markupContext(String markup) {
    return "in \"{{" + markup + "}}\"";
  }

  @Override
  public Object laxParse(String markup) {
    filters = new ArrayList<>();
    Matcher m = MarkupWithQuotedFragment.matcher(markup);
    if (!m.find()) {
      return null;
    }
    String nameMarkup = m.group(1);
    String filterMarkup = m.group(2);
    name = Expression.parse(nameMarkup);
    Matcher fm = FilterMarkupRegex.matcher(filterMarkup);
    if (fm.find()) {
      for (String f : Liquid.scan(FilterParser, fm.group(1))) {
        Matcher w = WORD.matcher(f);
        if (!w.find()) {
          continue;
        }
        String filtername = w.group();
        List<String> filterargs = new ArrayList<>();
        for (String[] gs : Liquid.scanGroups(FilterArgsRegex, f)) {
          filterargs.add(gs[0]);
        }
        filters.add(parseFilterExpressions(filtername, filterargs));
      }
    }
    return null;
  }

  @Override
  public Object strictParse(String markup) {
    filters = new ArrayList<>();
    Parser p = new Parser(markup);
    name = Expression.parse(p.expression());
    while (p.consumeIf(Lexer.Type.pipe) != null) {
      String filtername = p.consume(Lexer.Type.id);
      List<String> filterargs = p.consumeIf(Lexer.Type.colon) != null ? parseFilterargs(p) : new ArrayList<>();
      filters.add(parseFilterExpressions(filtername, filterargs));
    }
    p.consume(Lexer.Type.end_of_string);
    return null;
  }

  private List<String> parseFilterargs(Parser p) {
    // first argument
    List<String> filterargs = new ArrayList<>();
    filterargs.add(p.argument());
    // followed by comma separated others
    while (p.consumeIf(Lexer.Type.comma) != null) {
      filterargs.add(p.argument());
    }
    return filterargs;
  }

  @Override
  public Object render(Context context) {
    Object output = context.evaluate(name);
    for (FilterCall f : filters) {
      List<Object> args = evaluateFilterExpressions(context, f.args, f.kwargs);
      output = context.invoke(f.name, output, args);
    }
    return context.applyGlobalFilter(output);
  }

  private static FilterCall parseFilterExpressions(String filterName, List<String> unparsedArgs) {
    List<Object> filterArgs = new ArrayList<>();
    KeywordArgs keywordArgs = null;
    for (String a : unparsedArgs) {
      Matcher m = JustTagAttributes.matcher(a);
      if (m.find()) {
        if (keywordArgs == null) {
          keywordArgs = new KeywordArgs();
        }
        keywordArgs.put(m.group(1), Expression.parse(m.group(2)));
      } else {
        filterArgs.add(Expression.parse(a));
      }
    }
    return new FilterCall(filterName, filterArgs, keywordArgs);
  }

  private static List<Object> evaluateFilterExpressions(Context context, List<Object> filterArgs, Map<String, Object> filterKwargs) {
    List<Object> parsedArgs = new ArrayList<>(filterArgs.size() + 1);
    for (Object expr : filterArgs) {
      parsedArgs.add(context.evaluate(expr));
    }
    if (filterKwargs != null) {
      KeywordArgs parsedKwargs = new KeywordArgs();
      for (Map.Entry<String, Object> e : filterKwargs.entrySet()) {
        parsedKwargs.put(e.getKey(), context.evaluate(e.getValue()));
      }
      parsedArgs.add(parsedKwargs);
    }
    return parsedArgs;
  }
}
