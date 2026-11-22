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
import org.hl7.fhir.jekyll.liquid.Liquid;
import org.hl7.fhir.jekyll.liquid.LiquidException;
import org.hl7.fhir.jekyll.liquid.ParseContext;
import org.hl7.fhir.jekyll.liquid.RubyStrings;
import org.hl7.fhir.jekyll.liquid.Tokenizer;
import org.hl7.fhir.jekyll.liquid.Values;

/** Port of liquid-4.0.4/lib/liquid/tags/case.rb. */
public class Case extends Block {
  static final String QF = "(?:" + Liquid.QUOTED_FRAGMENT + ")";
  static final Pattern Syntax = Pattern.compile("(" + QF + ")", Pattern.UNIX_LINES);
  static final Pattern WhenSyntax = Pattern.compile("(?s)(" + QF + ")(?:(?:\\s+or\\s+|\\s*\\,\\s*)(" + QF + ".*))?", Pattern.UNIX_LINES);

  private final List<Condition> blocks = new ArrayList<>();
  private final Object left;

  public Case(String tagName, String markup, ParseContext options) {
    super(tagName, markup, options);
    Matcher m = Syntax.matcher(markup);
    if (m.find()) {
      left = Expression.parse(m.group(1));
    } else {
      throw LiquidException.syntax(options.locale().t("errors.syntax.case"));
    }
  }

  @Override
  public void parse(Tokenizer tokens) {
    BlockBody b = new BlockBody();
    while (parseBody(b, tokens)) {
      b = blocks.get(blocks.size() - 1).attachment();
    }
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
  public void unknownTag(String tag, String markup, Tokenizer tokens) {
    if (tag.equals("when")) {
      recordWhenCondition(markup);
    } else if (tag.equals("else")) {
      recordElseCondition(markup);
    } else {
      super.unknownTag(tag, markup, tokens);
    }
  }

  @Override
  public Object render(Context context) {
    return context.stack(() -> {
      boolean executeElseBlock = true;
      StringBuilder output = new StringBuilder();
      for (Condition block : blocks) {
        if (block.isElse()) {
          if (executeElseBlock) {
            return block.attachment().render(context);
          }
        } else if (Values.truthy(block.evaluate(context))) {
          executeElseBlock = false;
          output.append(Values.toS(block.attachment().render(context)));
        }
      }
      return output.toString();
    });
  }

  private void recordWhenCondition(String markup) {
    BlockBody b = new BlockBody();
    while (markup != null) {
      Matcher m = WhenSyntax.matcher(markup);
      if (!m.find()) {
        throw LiquidException.syntax(options().locale().t("errors.syntax.case_invalid_when"));
      }
      markup = m.group(2);
      Condition block = new Condition(left, "==", Expression.parse(m.group(1)));
      block.attach(b);
      blocks.add(block);
    }
  }

  private void recordElseCondition(String markup) {
    if (!RubyStrings.strip(markup).isEmpty()) {
      throw LiquidException.syntax(options().locale().t("errors.syntax.case_invalid_else"));
    }
    Condition block = new Condition.ElseCondition();
    block.attach(new BlockBody());
    blocks.add(block);
  }
}
