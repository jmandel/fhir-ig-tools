package org.hl7.fhir.jekyll.liquid.tags;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.jekyll.liquid.Block;
import org.hl7.fhir.jekyll.liquid.BlockBody;
import org.hl7.fhir.jekyll.liquid.Context;
import org.hl7.fhir.jekyll.liquid.Expression;
import org.hl7.fhir.jekyll.liquid.ForloopDrop;
import org.hl7.fhir.jekyll.liquid.Interrupt;
import org.hl7.fhir.jekyll.liquid.Lexer;
import org.hl7.fhir.jekyll.liquid.Liquid;
import org.hl7.fhir.jekyll.liquid.LiquidException;
import org.hl7.fhir.jekyll.liquid.ParseContext;
import org.hl7.fhir.jekyll.liquid.Parser;
import org.hl7.fhir.jekyll.liquid.RubyRange;
import org.hl7.fhir.jekyll.liquid.Tokenizer;
import org.hl7.fhir.jekyll.liquid.Utils;
import org.hl7.fhir.jekyll.liquid.Values;

/** Port of liquid-4.0.4/lib/liquid/tags/for.rb. */
public class For extends Block {
  static final Pattern Syntax = Pattern.compile(
      "\\A(" + "(?:" + Liquid.VARIABLE_SEGMENT + ")" + "+)\\s+in\\s+(" + "(?:" + Liquid.QUOTED_FRAGMENT + ")" + "+)\\s*(reversed)?", Pattern.UNIX_LINES);
  private static final Object CONTINUE = new Object();

  private String variableName;
  private Object collectionName;
  private String name;
  private boolean reversed;
  private Object from;
  private Object limit;
  private final BlockBody forBlock;
  private BlockBody elseBlock;

  public For(String tagName, String markup, ParseContext options) {
    super(tagName, markup, options);
    from = limit = null;
    parseWithSelectedParser(markup);
    forBlock = new BlockBody();
    elseBlock = null;
  }

  public String variableName() {
    return variableName;
  }

  public Object collectionName() {
    return collectionName;
  }

  @Override
  public void parse(Tokenizer tokens) {
    if (!parseBody(forBlock, tokens)) {
      return;
    }
    parseBody(elseBlock, tokens);
  }

  @Override
  public List<Object> nodelist() {
    List<Object> out = new ArrayList<>();
    out.add(forBlock);
    if (elseBlock != null) {
      out.add(elseBlock);
    }
    return out;
  }

  @Override
  public void unknownTag(String tag, String markup, Tokenizer tokens) {
    if (!tag.equals("else")) {
      super.unknownTag(tag, markup, tokens);
      return;
    }
    elseBlock = new BlockBody();
  }

  @Override
  public Object render(Context context) {
    List<Object> segment = collectionSegment(context);
    if (segment.isEmpty()) {
      return renderElse(context);
    }
    return renderSegment(context, segment);
  }

  @Override
  public Object laxParse(String markup) {
    Matcher m = Syntax.matcher(markup);
    if (m.find()) {
      variableName = m.group(1);
      String collection = m.group(2);
      reversed = m.group(3) != null;
      name = variableName + "-" + collection;
      collectionName = Expression.parse(collection);
      for (String[] kv : Liquid.scanGroups(Liquid.TagAttributes, markup)) {
        setAttribute(kv[0], kv[1]);
      }
    } else {
      throw LiquidException.syntax(options().locale().t("errors.syntax.for"));
    }
    return null;
  }

  @Override
  public Object strictParse(String markup) {
    Parser p = new Parser(markup);
    variableName = p.consume(Lexer.Type.id);
    if (p.idIf("in") == null) {
      throw LiquidException.syntax(options().locale().t("errors.syntax.for_invalid_in"));
    }
    String collection = p.expression();
    name = variableName + "-" + collection;
    collectionName = Expression.parse(collection);
    reversed = p.idIf("reversed") != null;
    while (p.look(Lexer.Type.id) && p.look(Lexer.Type.colon, 1)) {
      String attribute = p.idIf("limit");
      if (attribute == null) {
        attribute = p.idIf("offset");
      }
      if (attribute == null) {
        throw LiquidException.syntax(options().locale().t("errors.syntax.for_invalid_attribute"));
      }
      p.consume();
      setAttribute(attribute, p.expression());
    }
    p.consume(Lexer.Type.end_of_string);
    return null;
  }

  @SuppressWarnings("unchecked")
  private List<Object> collectionSegment(Context context) {
    Map<String, Object> offsets = (Map<String, Object>) context.registers().computeIfAbsent("for", k -> new HashMap<>());
    long fromI;
    if (from == CONTINUE) {
      fromI = Values.toLong(offsets.get(name));
    } else {
      fromI = Values.toLong(context.evaluate(from));
    }
    Object collection = context.evaluate(collectionName);
    if (collection instanceof RubyRange) {
      collection = ((RubyRange) collection).toList();
    }
    Object limitV = context.evaluate(limit);
    Long to = Values.truthy(limitV) ? Values.toLong(limitV) + fromI : null;
    List<Object> segment = Utils.sliceCollection(collection, fromI, to);
    if (reversed) {
      segment = new ArrayList<>(segment);
      Collections.reverse(segment);
    }
    offsets.put(name, fromI + segment.size());
    return segment;
  }

  @SuppressWarnings("unchecked")
  private Object renderSegment(Context context, List<Object> segment) {
    List<Object> forStack = (List<Object>) context.registers().computeIfAbsent("for_stack", k -> new ArrayList<>());
    int length = segment.size();
    StringBuilder result = new StringBuilder();
    context.stack(() -> {
      ForloopDrop loopVars = new ForloopDrop(name, length, forStack.isEmpty() ? null : forStack.get(forStack.size() - 1));
      forStack.add(loopVars);
      try {
        context.set("forloop", loopVars);
        for (Object item : segment) {
          context.set(variableName, item);
          result.append(Values.toS(forBlock.render(context)));
          loopVars.increment();
          // Handle any interrupts if they exist.
          if (context.interrupt()) {
            Interrupt interrupt = context.popInterrupt();
            if (interrupt instanceof Interrupt.BreakInterrupt) {
              break;
            }
          }
        }
      } finally {
        forStack.remove(forStack.size() - 1);
      }
      return null;
    });
    return result.toString();
  }

  private void setAttribute(String key, String expr) {
    switch (key) {
      case "offset":
        from = expr.equals("continue") ? CONTINUE : Expression.parse(expr);
        break;
      case "limit":
        limit = Expression.parse(expr);
        break;
      default:
        break;
    }
  }

  private Object renderElse(Context context) {
    return elseBlock != null ? elseBlock.render(context) : "";
  }
}
