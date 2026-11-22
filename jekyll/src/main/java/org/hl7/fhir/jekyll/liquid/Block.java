package org.hl7.fhir.jekyll.liquid;

import java.util.List;

/** Port of liquid-4.0.4/lib/liquid/block.rb. */
public class Block extends Tag {
  public static final int MAX_DEPTH = 100;

  protected BlockBody body;
  protected boolean blank = true;
  private String blockDelimiter;

  public Block(String tagName, String markup, ParseContext parseContext) {
    super(tagName, markup, parseContext);
  }

  @Override
  public void parse(Tokenizer tokens) {
    body = new BlockBody();
    while (parseBody(body, tokens)) {
      // keep parsing
    }
  }

  @Override
  public Object render(Context context) {
    return body.render(context);
  }

  @Override
  public boolean isBlank() {
    return blank;
  }

  public List<Object> nodelist() {
    return body.nodelist();
  }

  /** Called for tags this block doesn't know (else/elsif/when/end...). */
  public void unknownTag(String tag, String params, Tokenizer tokens) {
    I18n locale = parseContext.locale();
    if (tag.equals("else")) {
      throw LiquidException.syntax(locale.t("errors.syntax.unexpected_else", "block_name", blockName()));
    } else if (tag.startsWith("end")) {
      throw LiquidException.syntax(locale.t("errors.syntax.invalid_delimiter", "tag", tag, "block_name", blockName(), "block_delimiter", blockDelimiter()));
    } else {
      throw LiquidException.syntax(locale.t("errors.syntax.unknown_tag", "tag", tag));
    }
  }

  public String blockName() {
    return tagName;
  }

  public String blockDelimiter() {
    if (blockDelimiter == null) {
      blockDelimiter = "end" + blockName();
    }
    return blockDelimiter;
  }

  /** Ruby {@code parse_body(body, tokens)}: false once the end tag was seen. */
  protected boolean parseBody(BlockBody body, Tokenizer tokens) {
    if (parseContext.depth() >= MAX_DEPTH) {
      throw new LiquidException(LiquidException.Kind.STACK_LEVEL, "Nesting too deep");
    }
    parseContext.depth(parseContext.depth() + 1);
    try {
      return body.parse(tokens, parseContext, (endTagName, endTagParams) -> {
        blank = blank && body.isBlank();
        if (blockDelimiter().equals(endTagName)) {
          return false;
        }
        if (endTagName == null) {
          throw LiquidException.syntax(parseContext.locale().t("errors.syntax.tag_never_closed", "block_name", blockName()));
        }
        // this tag is not registered with the system
        // pass it to the current block for special handling or error reporting
        unknownTag(endTagName, endTagParams, tokens);
        return true;
      });
    } finally {
      parseContext.depth(parseContext.depth() - 1);
    }
  }
}
