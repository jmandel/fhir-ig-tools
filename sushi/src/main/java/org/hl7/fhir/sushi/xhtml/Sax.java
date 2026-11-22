package org.hl7.fhir.sushi.xhtml;

// port of sax 1.6.1 lib/sax.js (entry point)
/** {@code Sax.parser(true).write(value).error() == null} is SUSHI's xhtml well-formedness check. */
public final class Sax {
  private Sax() {}

  public static SaxParser parser(boolean strict) {
    return new SaxParser(strict);
  }
}
