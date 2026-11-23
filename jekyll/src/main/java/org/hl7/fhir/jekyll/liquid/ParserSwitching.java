package org.hl7.fhir.jekyll.liquid;

/** Port of liquid-4.0.4/lib/liquid/parser_switching.rb (a mixin for Tag and Variable). */
public interface ParserSwitching {
  ParseContext parseContext();

  Integer lineNumber();

  Object strictParse(String markup);

  Object laxParse(String markup);

  default String markupContext(String markup) {
    return "in \"" + RubyStrings.strip(markup) + "\"";
  }

  default Object parseWithSelectedParser(String markup) {
    switch (parseContext().errorMode()) {
      case STRICT:
        return strictParseWithErrorContext(markup);
      case LAX:
        return laxParse(markup);
      case WARN:
      default:
        try {
          return strictParseWithErrorContext(markup);
        } catch (LiquidException e) {
          if (!e.isSyntax()) {
            throw e;
          }
          parseContext().warnings().add(e);
          return laxParse(markup);
        }
    }
  }

  default Object strictParseWithErrorContext(String markup) {
    try {
      return strictParse(markup);
    } catch (LiquidException e) {
      if (e.isSyntax()) {
        e.lineNumber(lineNumber());
        e.markupContext(markupContext(markup));
      }
      throw e;
    }
  }
}
