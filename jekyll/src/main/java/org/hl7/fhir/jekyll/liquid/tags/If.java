package org.hl7.fhir.jekyll.liquid.tags;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.jekyll.liquid.Block;
import org.hl7.fhir.jekyll.liquid.BlockBody;
import org.hl7.fhir.jekyll.liquid.Condition;
import org.hl7.fhir.jekyll.liquid.Context;
import org.hl7.fhir.jekyll.liquid.Expression;
import org.hl7.fhir.jekyll.liquid.Lexer;
import org.hl7.fhir.jekyll.liquid.Liquid;
import org.hl7.fhir.jekyll.liquid.LiquidException;
import org.hl7.fhir.jekyll.liquid.ParseContext;
import org.hl7.fhir.jekyll.liquid.Parser;
import org.hl7.fhir.jekyll.liquid.RubyStrings;
import org.hl7.fhir.jekyll.liquid.Tokenizer;
import org.hl7.fhir.jekyll.liquid.Values;

/** Port of liquid-4.0.4/lib/liquid/tags/if.rb. */
public class If extends Block {
  static final String QF = "(?:" + Liquid.QUOTED_FRAGMENT + ")";
  static final Pattern Syntax = Pattern.compile("(" + QF + ")\\s*([=!<>a-z_]+)?\\s*(" + QF + ")?", Pattern.UNIX_LINES);
  static final Pattern ExpressionsAndOperators = Pattern.compile(
      "(?:\\b(?:\\s?and\\s?|\\s?or\\s?)\\b|(?:\\s*(?!\\b(?:\\s?and\\s?|\\s?or\\s?)\\b)(?:" + QF + "|\\S+)\\s*)+)", Pattern.UNIX_LINES);

  protected final List<Condition> blocks = new ArrayList<>();

  public If(String tagName, String markup, ParseContext options) {
    super(tagName, markup, options);
    pushBlock("if", markup);
  }

  public List<Condition> blocks() {
    return blocks;
  }

  @Override
  public List<Object> nodelist() {
    List<Object> out = new ArrayList<>();
    for (Condition c : blocks) {
      out.add(c.attachment());
    }
    return out;
  }

  @Override
  public void parse(Tokenizer tokens) {
    while (parseBody(blocks.get(blocks.size() - 1).attachment(), tokens)) {
      // keep going
    }
  }

  @Override
  public void unknownTag(String tag, String markup, Tokenizer tokens) {
    if (tag.equals("elsif") || tag.equals("else")) {
      pushBlock(tag, markup);
    } else {
      super.unknownTag(tag, markup, tokens);
    }
  }

  @Override
  public Object render(Context context) {
    return context.stack(() -> {
      for (Condition block : blocks) {
        if (Values.truthy(block.evaluate(context))) {
          return block.attachment().render(context);
        }
      }
      return "";
    });
  }

  private void pushBlock(String tag, String markup) {
    Condition block;
    if (tag.equals("else")) {
      block = new Condition.ElseCondition();
    } else {
      block = (Condition) parseWithSelectedParser(markup);
    }
    blocks.add(block);
    block.attach(new BlockBody());
  }

  private LiquidException ifError() {
    return LiquidException.syntax(options().locale().t("errors.syntax.if"));
  }

  @Override
  public Object laxParse(String markup) {
    List<String> expressions = Liquid.scan(ExpressionsAndOperators, markup);
    Matcher m = expressions.isEmpty() ? null : Liquid.match(Syntax, expressions.remove(expressions.size() - 1));
    if (m == null) {
      throw ifError();
    }
    Condition condition = new Condition(Expression.parse(m.group(1)), m.group(2), Expression.parse(m.group(3)));
    while (!expressions.isEmpty()) {
      String operator = RubyStrings.strip(expressions.remove(expressions.size() - 1));
      String prev = expressions.isEmpty() ? "" : expressions.remove(expressions.size() - 1);
      m = Liquid.match(Syntax, prev);
      if (m == null) {
        throw ifError();
      }
      Condition newCondition = new Condition(Expression.parse(m.group(1)), m.group(2), Expression.parse(m.group(3)));
      if (!operator.equals("and") && !operator.equals("or")) {
        throw ifError();
      }
      newCondition.link(operator, condition);
      condition = newCondition;
    }
    return condition;
  }

  @Override
  public Object strictParse(String markup) {
    Parser p = new Parser(markup);
    Condition condition = parseBinaryComparisons(p);
    p.consume(Lexer.Type.end_of_string);
    return condition;
  }

  private Condition parseBinaryComparisons(Parser p) {
    Condition condition = parseComparison(p);
    Condition firstCondition = condition;
    String op;
    while ((op = p.idIf("and")) != null || (op = p.idIf("or")) != null) {
      Condition childCondition = parseComparison(p);
      condition.link(op, childCondition);
      condition = childCondition;
    }
    return firstCondition;
  }

  private Condition parseComparison(Parser p) {
    Object a = Expression.parse(p.expression());
    String op = p.consumeIf(Lexer.Type.comparison);
    if (op != null) {
      Object b = Expression.parse(p.expression());
      return new Condition(a, op, b);
    }
    return new Condition(a);
  }
}
