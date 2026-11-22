package org.hl7.fhir.sushi.xhtml;

// port of html-minifier-terser 5.1.1 src/htmlminifier.js (only the subset SUSHI reaches)
/** Minifies assigned xhtml exactly like html-minifier-terser 5.1.1 with SUSHI's options. */
public final class HtmlMinifier {
  private HtmlMinifier() {}

  /** The options SUSHI passes; every other option is at its 5.1.1 default. */
  public record Options(boolean collapseWhitespace, boolean html5, boolean keepClosingSlash) {
    public static final Options SUSHI = new Options(true, false, true);
  }

  /** @throws MinifyException with html-minifier's message (e.g. "Parse Error: ...") where JS throws */
  public static String minify(String value, Options options) {
    throw new UnsupportedOperationException("TODO WP-9");
  }

  /** An Error thrown by html-minifier-terser; getMessage() is the JS message text. */
  public static final class MinifyException extends RuntimeException {
    public MinifyException(String message) { super(message); }
  }
}
