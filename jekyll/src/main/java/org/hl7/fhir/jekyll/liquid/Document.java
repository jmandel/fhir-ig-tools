package org.hl7.fhir.jekyll.liquid;

/** Port of liquid-4.0.4/lib/liquid/document.rb. */
public class Document extends BlockBody {
  public static Document parse(Tokenizer tokens, ParseContext parseContext) {
    Document doc = new Document();
    doc.parseDocument(tokens, parseContext);
    return doc;
  }

  private void parseDocument(Tokenizer tokens, ParseContext parseContext) {
    try {
      super.parse(tokens, parseContext, (endTagName, endTagParams) -> {
        if (endTagName != null) {
          unknownTag(endTagName, parseContext);
        }
        return false;
      });
    } catch (LiquidException e) {
      if (e.isSyntax() && e.lineNumber() == null) {
        e.lineNumber(parseContext.lineNumber());
      }
      throw e;
    }
  }

  private void unknownTag(String tag, ParseContext parseContext) {
    if (tag.equals("else") || tag.equals("end")) {
      throw LiquidException.syntax(parseContext.locale().t("errors.syntax.unexpected_outer_tag", "tag", tag));
    }
    throw LiquidException.syntax(parseContext.locale().t("errors.syntax.unknown_tag", "tag", tag));
  }
}
