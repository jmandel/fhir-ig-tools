package org.hl7.fhir.jekyll.liquid;

/**
 * Port of liquid-4.0.4/lib/liquid/tag.rb. Subclasses do their parsing in the constructor
 * (Ruby {@code initialize}) and, for blocks, in {@link #parse(Tokenizer)}.
 */
public class Tag implements Node, ParserSwitching {
  protected final String tagName;
  protected final String markup;
  protected final ParseContext parseContext;
  protected final Integer lineNumber;

  public Tag(String tagName, String markup, ParseContext parseContext) {
    this.tagName = tagName;
    this.markup = markup;
    this.parseContext = parseContext;
    this.lineNumber = parseContext.lineNumber();
  }

  /** Ruby {@code parse(tokens)}; plain tags consume nothing. */
  public void parse(Tokenizer tokens) {}

  public String tagName() {
    return tagName;
  }

  public String markup() {
    return markup;
  }

  public String raw() {
    return tagName + " " + markup;
  }

  public String name() {
    return getClass().getName().toLowerCase(java.util.Locale.ROOT);
  }

  @Override
  public ParseContext parseContext() {
    return parseContext;
  }

  /** Ruby {@code options} (alias of parse_context). */
  public ParseContext options() {
    return parseContext;
  }

  @Override
  public Integer lineNumber() {
    return lineNumber;
  }

  @Override
  public Object render(Context context) {
    return "";
  }

  public boolean isBlank() {
    return false;
  }

  @Override
  public Object strictParse(String markup) {
    throw new UnsupportedOperationException("strict_parse");
  }

  @Override
  public Object laxParse(String markup) {
    throw new UnsupportedOperationException("lax_parse");
  }
}
