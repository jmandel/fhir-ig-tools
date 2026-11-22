package org.hl7.fhir.jekyll.liquid.tags;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.jekyll.liquid.Block;
import org.hl7.fhir.jekyll.liquid.Context;
import org.hl7.fhir.jekyll.liquid.Liquid;
import org.hl7.fhir.jekyll.liquid.LiquidException;
import org.hl7.fhir.jekyll.liquid.ParseContext;
import org.hl7.fhir.jekyll.liquid.Tokenizer;

/** Port of liquid-4.0.4/lib/liquid/tags/raw.rb. */
public class Raw extends Block {
  static final Pattern Syntax = Pattern.compile("\\A\\s*\\z", Pattern.UNIX_LINES);
  static final Pattern FullTokenPossiblyInvalid = Pattern.compile(
      "(?s)\\A(.*)" + "(?:" + Liquid.TAG_START + ")" + "\\s*(\\w+)\\s*(.*)?" + "(?:" + Liquid.TAG_END + ")" + "\\z", Pattern.UNIX_LINES);

  private StringBuilder rawBody;

  public Raw(String tagName, String markup, ParseContext parseContext) {
    super(tagName, markup, parseContext);
    if (!Syntax.matcher(markup).find()) {
      throw LiquidException.syntax(parseContext.locale().t("errors.syntax.tag_unexpected_args", "tag", tagName));
    }
  }

  @Override
  public void parse(Tokenizer tokens) {
    rawBody = new StringBuilder();
    String token;
    while ((token = tokens.shift()) != null) {
      // Necessary conditions for FullTokenPossiblyInvalid (/\A(.*){%\s*(\w+)\s*(.*)?%}\z/m): skip the
      // regex for plain text tokens, where java.util.regex's backtracking on (.*) is very costly.
      boolean candidate = token.endsWith("%}") && token.contains("{%");
      Matcher m = candidate ? FullTokenPossiblyInvalid.matcher(token) : null;
      if (m != null && m.find()) {
        if (!m.group(1).isEmpty()) {
          rawBody.append(m.group(1));
        }
        if (blockDelimiter().equals(m.group(2))) {
          return;
        }
      }
      if (!token.isEmpty()) {
        rawBody.append(token);
      }
    }
    throw LiquidException.syntax(parseContext.locale().t("errors.syntax.tag_never_closed", "block_name", blockName()));
  }

  @Override
  public Object render(Context context) {
    return rawBody.toString();
  }

  @Override
  public List<Object> nodelist() {
    return Collections.singletonList(rawBody.toString());
  }

  @Override
  public boolean isBlank() {
    return rawBody.length() == 0;
  }
}
