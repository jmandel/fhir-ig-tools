package org.hl7.fhir.jekyll.liquid;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.jekyll.liquid.tags.Break;
import org.hl7.fhir.jekyll.liquid.tags.Continue;

/** Port of liquid-4.0.4/lib/liquid/block_body.rb. */
public class BlockBody {
  static final Pattern FullToken = Pattern.compile(
      "(?s)\\A" + Liquid.g(Liquid.TAG_START) + "-?\\s*(\\w+)\\s*(.*?)-?" + Liquid.g(Liquid.TAG_END) + "\\z", Pattern.UNIX_LINES);
  static final Pattern ContentOfVariable = Pattern.compile(
      "(?s)\\A" + Liquid.g(Liquid.VARIABLE_START) + "-?(.*?)-?" + Liquid.g(Liquid.VARIABLE_END) + "\\z", Pattern.UNIX_LINES);
  static final Pattern WhitespaceOrNothing = Pattern.compile("\\A\\s*\\z", Pattern.UNIX_LINES);
  static final String TAGSTART = "{%";
  static final String VARSTART = "{{";

  /** The block passed to Ruby's {@code parse} ({@code yield tag_name, markup}). */
  @FunctionalInterface
  public interface EndTagHandler {
    boolean handle(String tagName, String markup);
  }

  private final List<Object> nodelist = new ArrayList<>();
  private boolean blank = true;

  public List<Object> nodelist() {
    return nodelist;
  }

  public boolean isBlank() {
    return blank;
  }

  public boolean parse(Tokenizer tokenizer, ParseContext parseContext, EndTagHandler handler) {
    parseContext.lineNumber(tokenizer.lineNumber());
    String token;
    while ((token = tokenizer.shift()) != null) {
      if (token.isEmpty()) {
        continue;
      }
      if (token.startsWith(TAGSTART)) {
        whitespaceHandler(token, parseContext);
        Matcher m = FullToken.matcher(token);
        if (!m.find()) {
          throw LiquidException.syntax(parseContext.locale().t("errors.syntax.tag_termination", "token", token, "tag_end", "/\\%\\}/"));
        }
        String tagName = m.group(1);
        String markup = m.group(2);
        // fetch the tag from registered blocks
        TagFactory tag = parseContext.environment().tag(tagName);
        if (tag == null) {
          // end parsing if we reach an unknown tag and let the caller decide
          // determine how to proceed
          return handler.handle(tagName, markup);
        }
        Tag newTag = tag.create(tagName, markup, parseContext);
        newTag.parse(tokenizer);
        blank = blank && newTag.isBlank();
        nodelist.add(newTag);
      } else if (token.startsWith(VARSTART)) {
        whitespaceHandler(token, parseContext);
        nodelist.add(createVariable(token, parseContext));
        blank = false;
      } else {
        if (parseContext.trimWhitespace()) {
          token = RubyStrings.lstrip(token);
        }
        parseContext.trimWhitespace(false);
        nodelist.add(token);
        blank = blank && WhitespaceOrNothing.matcher(token).find();
      }
      parseContext.lineNumber(tokenizer.lineNumber());
    }
    return handler.handle(null, null);
  }

  private void whitespaceHandler(String token, ParseContext parseContext) {
    if (token.length() > 2 && token.charAt(2) == '-') {
      int last = nodelist.size() - 1;
      if (last >= 0 && nodelist.get(last) instanceof String) {
        nodelist.set(last, RubyStrings.rstrip((String) nodelist.get(last)));
      }
    }
    parseContext.trimWhitespace(token.length() >= 3 && token.charAt(token.length() - 3) == '-');
  }

  public Object render(Context context) {
    StringBuilder output = new StringBuilder();
    context.resourceLimits().renderScore += nodelist.size();
    int n = nodelist.size();
    for (int idx = 0; idx < n; idx++) {
      Object node = nodelist.get(idx);
      if (node instanceof String) {
        checkResources(context, (String) node);
        output.append((String) node);
      } else if (node instanceof Variable) {
        renderNodeToOutput((Node) node, output, context, false);
      } else if (node instanceof Block) {
        renderNodeToOutput((Node) node, output, context, ((Block) node).isBlank());
        if (context.interrupt()) {
          break; // might have happened in a for-block
        }
      } else if (node instanceof Continue) {
        context.pushInterrupt(((Continue) node).interrupt());
        break;
      } else if (node instanceof Break) {
        context.pushInterrupt(((Break) node).interrupt());
        break;
      } else {
        // Other non-Block tags
        renderNodeToOutput((Node) node, output, context, false);
        if (context.interrupt()) {
          break; // might have happened through an include
        }
      }
    }
    return output.toString();
  }

  private void renderNodeToOutput(Node node, StringBuilder output, Context context, boolean skipOutput) {
    try {
      Object nodeOutput = node.render(context);
      String s = Values.toS(nodeOutput);
      checkResources(context, s);
      if (!skipOutput) {
        output.append(s);
      }
    } catch (LiquidException e) {
      switch (e.kind()) {
        case MEMORY:
          throw e;
        case UNDEFINED_VARIABLE:
        case UNDEFINED_DROP_METHOD:
        case UNDEFINED_FILTER:
          context.handleError(e, node.lineNumber());
          break;
        default:
          output.append(context.handleError(e, node.lineNumber()));
      }
    } catch (RuntimeException e) {
      output.append(context.handleError(e, node.lineNumber()));
    }
  }

  private void checkResources(Context context, String nodeOutput) {
    ResourceLimits rl = context.resourceLimits();
    rl.renderLength += nodeOutput.length();
    if (rl.reached()) {
      throw new LiquidException(LiquidException.Kind.MEMORY, "Memory limits exceeded");
    }
  }

  private Variable createVariable(String token, ParseContext parseContext) {
    Matcher m = ContentOfVariable.matcher(token);
    if (m.find()) {
      return new Variable(m.group(1), parseContext);
    }
    throw LiquidException.syntax(parseContext.locale().t("errors.syntax.variable_termination", "token", token, "tag_end", "/\\}\\}/"));
  }
}
