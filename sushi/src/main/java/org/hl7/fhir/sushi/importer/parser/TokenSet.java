package org.hl7.fhir.sushi.importer.parser;

import java.util.BitSet;
import java.util.function.IntFunction;

/** A set of token types incl. EOF (-1) and EPSILON (-2): ANTLR's IntervalSet as the parser uses it. */
final class TokenSet {
  private static final int OFFSET = 2;
  private final BitSet bits = new BitSet();

  static TokenSet of(int... types) {
    TokenSet s = new TokenSet();
    for (int t : types) s.add(t);
    return s;
  }

  void add(int t) { bits.set(t + OFFSET); }

  void remove(int t) { bits.clear(t + OFFSET); }

  boolean contains(int t) { return t + OFFSET >= 0 && bits.get(t + OFFSET); }

  void addAll(TokenSet o) {
    if (o != null) bits.or(o.bits);
  }

  boolean intersects(TokenSet o) { return bits.intersects(o.bits); }

  boolean isEmpty() { return bits.isEmpty(); }

  int size() { return bits.cardinality(); }

  /** Smallest element (IntervalSet.getMinElement / JS first()); INVALID_TYPE (0) when empty. */
  int min() {
    int i = bits.nextSetBit(0);
    return i < 0 ? Token.INVALID_TYPE : i - OFFSET;
  }

  /** The complement within 1..maxTokenType (NOT_SET labels). */
  TokenSet complement(int maxTokenType) {
    TokenSet r = new TokenSet();
    for (int t = 1; t <= maxTokenType; t++) if (!contains(t)) r.add(t);
    return r;
  }

  TokenSet copy() {
    TokenSet r = new TokenSet();
    r.bits.or(bits);
    return r;
  }

  /**
   * IntervalSet.toString(literalNames, symbolicNames) of the JS runtime: "{}" when empty, the single name, or
   * "{a, b, ...}" in increasing token type order; EOF is "&lt;EOF&gt;", EPSILON "&lt;EPSILON&gt;", other types their
   * literal name, else symbolic name.
   */
  String toString(IntFunction<String> names) {
    if (bits.isEmpty()) return "{}";
    StringBuilder sb = new StringBuilder();
    int n = 0;
    for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i + 1)) {
      if (n++ > 0) sb.append(", ");
      int t = i - OFFSET;
      sb.append(t == Token.EOF ? "<EOF>" : t == Token.EPSILON ? "<EPSILON>" : names.apply(t));
    }
    return n > 1 ? "{" + sb + "}" : sb.toString();
  }

  @Override public boolean equals(Object o) { return o instanceof TokenSet s && s.bits.equals(bits); }

  @Override public int hashCode() { return bits.hashCode(); }

  @Override public String toString() { return bits.toString(); }
}
