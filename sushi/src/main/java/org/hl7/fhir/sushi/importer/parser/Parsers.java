package org.hl7.fhir.sushi.importer.parser;

/**
 * Selects the parser backend. Production: the hand-written parser. Tests may run on the ANTLR oracle instead
 * ({@code -Dsushi.parser=antlr}, or {@link #setBackend}); the oracle adapter is the test-scope class
 * {@code org.hl7.fhir.sushi.importer.parser.AntlrCstBackend}.
 *
 * <p>Until the hand-written parser is complete, the default is the ANTLR oracle when it is on the classpath (test
 * runs), so the importer (WP-2) can be developed and tested against the final CST shape.
 */
public final class Parsers {
  private Parsers() {}

  static final String ANTLR_BACKEND = "org.hl7.fhir.sushi.importer.parser.AntlrCstBackend";
  /** Default backend name; becomes "hand" when the hand-written parser lands. */
  static final String DEFAULT = "antlr";

  private static volatile ParserBackend backend;

  public static ParserBackend backend() {
    ParserBackend b = backend;
    if (b == null) {
      synchronized (Parsers.class) {
        if (backend == null) backend = byName(System.getProperty("sushi.parser", DEFAULT));
        b = backend;
      }
    }
    return b;
  }

  /** Installs a backend (tests); null resets to the default. */
  public static void setBackend(ParserBackend b) {
    backend = b;
  }

  public static ParserBackend byName(String name) {
    if ("antlr".equals(name)) {
      try {
        return (ParserBackend) Class.forName(ANTLR_BACKEND).getDeclaredConstructor().newInstance();
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException("ANTLR oracle backend not on the classpath (test scope only); "
            + "the hand-written FSH parser is not complete yet", e);
      }
    }
    throw new IllegalArgumentException("unknown parser backend " + name);
  }
}
