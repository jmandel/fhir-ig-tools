package org.hl7.fhir.jekyll.rouge;

/** A Ruby exception raised inside Rouge (e.g. "empty stack!", Guesser::Ambiguous, NoMethodError). */
public class RougeError extends RuntimeException {
  public RougeError(String message) {
    super(message);
  }

  /** Rouge::Guesser::Ambiguous */
  public static final class Ambiguous extends RougeError {
    public final java.util.List<LexerClass> alternatives;

    public Ambiguous(java.util.List<LexerClass> alternatives) {
      super("Ambiguous guess: can't decide between " + tags(alternatives));
      this.alternatives = alternatives;
    }

    private static String tags(java.util.List<LexerClass> l) {
      StringBuilder b = new StringBuilder("[");
      for (int k = 0; k < l.size(); k++) {
        b.append(k > 0 ? ", " : "").append('"').append(l.get(k).tag).append('"');
      }
      return b.append(']').toString();
    }
  }
}
