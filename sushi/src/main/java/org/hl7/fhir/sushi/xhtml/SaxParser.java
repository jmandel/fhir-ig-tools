package org.hl7.fhir.sushi.xhtml;

// port of sax 1.6.1 lib/sax.js (SAXParser, no event handlers attached)
public final class SaxParser {
  final boolean strict;

  SaxParser(boolean strict) {
    this.strict = strict;
  }

  /** Like sax's write(): may throw (e.g. "Cannot write after error...") where sax throws. */
  public SaxParser write(String chunk) {
    throw new UnsupportedOperationException("TODO WP-9");
  }

  /** null when no error; else sax's error message incl. its "\nLine: ..\nColumn: ..\nChar: .." suffix. */
  public String error() {
    throw new UnsupportedOperationException("TODO WP-9");
  }
}
