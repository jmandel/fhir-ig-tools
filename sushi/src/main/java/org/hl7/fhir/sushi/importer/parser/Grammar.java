package org.hl7.fhir.sushi.importer.parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A parser grammar written in the shape of an ANTLR .g4 file: rules of sequences, (sub)blocks of alternatives, and
 * ?, *, + suffixes on blocks (a suffixed single element is a one-alternative block, as in ANTLR). Like the ANTLR
 * tool, a block whose alternatives are all single tokens becomes a token set. {@link Atn} builds the network and
 * annotates each element with its states; {@link ParserEngine} interprets the elements the way ANTLR's generated
 * code does.
 */
final class Grammar {
  sealed interface Elem permits Tok, TokSet, Ref, Seq, Block {}

  /** A token reference. */
  static final class Tok implements Elem {
    final int type;
    Atn.State state;

    Tok(int type) { this.type = type; }
  }

  /** A block of single-token alternatives (ANTLR SET), or ~(...) (invert). */
  static final class TokSet implements Elem {
    final TokenSet set;
    final boolean invert;
    Atn.State state;

    TokSet(TokenSet set, boolean invert) {
      this.set = set;
      this.invert = invert;
    }
  }

  /** A rule reference. */
  static final class Ref implements Elem {
    final String name;
    int ruleIndex = -1;
    Atn.State state;

    Ref(String name) { this.name = name; }
  }

  static final class Seq implements Elem {
    final List<Elem> items;

    Seq(List<Elem> items) { this.items = items; }
  }

  enum Kind { PLAIN, OPTIONAL, STAR, PLUS }

  /** A block of alternatives with an optional EBNF suffix. */
  static final class Block implements Elem {
    final List<Elem> alts;
    final Kind kind;
    /** decision state: block start (PLAIN/OPTIONAL), loop entry (STAR), loop back (PLUS) */
    Atn.State decisionState;
    /** STAR/PLUS: the inner block start (decision of the alternatives when more than one) */
    Atn.State blockStart;
    Atn.State loopBack;
    /** LL(1) lookahead of the decision (per alternative), null for an adaptive (ALL(*)) decision */
    TokenSet[] ll1;
    /** LL(1) lookahead of the inner block's alternatives (STAR/PLUS with several alternatives) */
    TokenSet[] innerLl1;

    Block(List<Elem> alts, Kind kind) {
      this.alts = alts;
      this.kind = kind;
    }
  }

  record Rule(String name, int index, Elem body) {}

  final List<Rule> rules = new ArrayList<>();
  final Map<String, Integer> ruleIndex = new LinkedHashMap<>();
  final int maxTokenType;
  final String[] literalNames;
  final String[] symbolicNames;
  final String[] ruleNames;

  Grammar(int maxTokenType, String[] literalNames, String[] symbolicNames, String[] ruleNames) {
    this.maxTokenType = maxTokenType;
    this.literalNames = literalNames;
    this.symbolicNames = symbolicNames;
    this.ruleNames = ruleNames;
  }

  /** Adds a rule; its alternatives are the arguments (one argument = a single alternative). */
  void rule(String name, Elem... alts) {
    int idx = rules.size();
    if (!ruleNames[idx].equals(name)) throw new IllegalStateException("rule order: " + name + " vs " + ruleNames[idx]);
    rules.add(new Rule(name, idx, block(Kind.PLAIN, alts)));
    ruleIndex.put(name, idx);
  }

  /** Resolves rule references; call after all rules were added. */
  void resolve() {
    for (Rule r : rules) resolve(r.body());
  }

  private void resolve(Elem e) {
    switch (e) {
      case Ref r -> {
        Integer i = ruleIndex.get(r.name);
        if (i == null) throw new IllegalStateException("undefined rule " + r.name);
        r.ruleIndex = i;
      }
      case Seq s -> s.items.forEach(this::resolve);
      case Block b -> b.alts.forEach(this::resolve);
      default -> {}
    }
  }

  // ---- DSL ----
  static Tok t(int type) { return new Tok(type); }

  static Ref r(String name) { return new Ref(name); }

  static Elem seq(Elem... items) { return items.length == 1 ? items[0] : new Seq(List.of(items)); }

  /** ( a | b | ... ) */
  static Elem alts(Elem... alts) { return block(Kind.PLAIN, alts); }

  /** ( ... )? */
  static Elem opt(Elem... alts) { return block(Kind.OPTIONAL, alts); }

  /** ( ... )* */
  static Elem star(Elem... alts) { return block(Kind.STAR, alts); }

  /** ( ... )+ */
  static Elem plus(Elem... alts) { return block(Kind.PLUS, alts); }

  /** ~( ... ) */
  static Elem not(int... types) { return new TokSet(TokenSet.of(types), true); }

  private static Elem block(Kind kind, Elem... alts) {
    List<Elem> list = List.of(alts);
    // ANTLR's BlockSetTransformer: alternatives that are all single token refs become one set
    if (list.size() > 1 && list.stream().allMatch(a -> a instanceof Tok)) {
      TokenSet set = new TokenSet();
      for (Elem a : list) set.add(((Tok) a).type);
      list = List.of(new TokSet(set, false));
    }
    if (kind == Kind.PLAIN && list.size() == 1) return list.get(0);
    return new Block(list, kind);
  }
}
